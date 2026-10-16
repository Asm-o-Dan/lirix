package com.lirix.app.desktop.cast

import com.lirix.app.connect.proto.CastCodec
import com.lirix.app.connect.proto.ConnectMessageCodec
import com.lirix.app.connect.proto.Envelope
import com.lirix.app.connect.proto.LirixConnectMsg
import com.lirix.app.connect.transport.ConnectTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * QUIC-датаграмма для Audio Cast трансляции (SPEC-CONNECT-01 §4.7).
 *
 * Формат заголовка (7 байт):
 * - seq: u16 (2 байта, Big-Endian)
 * - timestamp48k: u32 (4 байта, Big-Endian, такты сэмплирования 48 кГц)
 * - flags: u8 (1 байт: 0x01 = FEC, 0x02 = DTX / Silence)
 * - payload: до 1193 байт (общий размер <= 1200 байт MTU).
 */
data class CastDatagram(
    val seq: Int,
    val timestamp48k: Long,
    val flags: Byte = 0,
    val payload: ByteArray
) {
    companion object {
        const val HEADER_SIZE = 7
        const val MAX_DATAGRAM_SIZE = 1200
        const val MAX_PAYLOAD_SIZE = MAX_DATAGRAM_SIZE - HEADER_SIZE // 1193 байта
        const val FLAG_FEC: Byte = 0x01
        const val FLAG_DTX: Byte = 0x02
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CastDatagram) return false
        return seq == other.seq &&
                timestamp48k == other.timestamp48k &&
                flags == other.flags &&
                payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = seq
        result = 31 * result + timestamp48k.hashCode()
        result = 31 * result + flags.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/**
 * Бинарный кодек QUIC-датаграмм Audio Cast.
 */
object CastDatagramCodec {

    fun encode(datagram: CastDatagram): ByteArray {
        val payloadSize = datagram.payload.size
        val totalSize = CastDatagram.HEADER_SIZE + payloadSize
        require(totalSize <= CastDatagram.MAX_DATAGRAM_SIZE) {
            "Datagram size $totalSize exceeds maximum allowed limit of ${CastDatagram.MAX_DATAGRAM_SIZE} bytes"
        }

        val buffer = ByteBuffer.allocate(totalSize)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.putShort((datagram.seq and 0xFFFF).toShort())
        buffer.putInt((datagram.timestamp48k and 0xFFFFFFFFL).toInt())
        buffer.put(datagram.flags)
        buffer.put(datagram.payload)
        return buffer.array()
    }

    fun decode(bytes: ByteArray): CastDatagram {
        require(bytes.size >= CastDatagram.HEADER_SIZE) {
            "Datagram too short: ${bytes.size} bytes < minimum header size ${CastDatagram.HEADER_SIZE}"
        }
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.BIG_ENDIAN)
        val seq = buffer.short.toInt() and 0xFFFF
        val timestamp48k = buffer.int.toLong() and 0xFFFFFFFFL
        val flags = buffer.get()
        val payload = ByteArray(bytes.size - CastDatagram.HEADER_SIZE)
        buffer.get(payload)

        return CastDatagram(
            seq = seq,
            timestamp48k = timestamp48k,
            flags = flags,
            payload = payload
        )
    }
}

/**
 * DesktopCastSourceManager: Управление трансляцией системного звука Windows на удалённый приёмник (SPEC §4.7).
 *
 * Возможности:
 * 1. Активация сессии Audio Cast через управляющий bi-stream (`SetAudioCast`).
 * 2. Захват системного звука через [AudioCaptureEngine] и нарезка на фреймы.
 * 3. Формирование датаграмм с монотонным `seq (u16)` и `timestamp48k (u32)` с флагом DTX при тишине.
 * 4. Отправка в канал датаграмм QUIC (`ConnectTransport.sendDatagram`).
 * 5. Учёт отчётов удалённого узла `CastStatus` (задержка приемника, джиттер, потери пакетов).
 */
class DesktopCastSourceManager(
    val audioCaptureEngine: AudioCaptureEngine = WindowsWasapiAudioCapture(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    private val _currentLatencyMs = MutableStateFlow(0)
    val currentLatencyMs: StateFlow<Int> = _currentLatencyMs.asStateFlow()

    private val _currentLossPct = MutableStateFlow(0.0f)
    val currentLossPct: StateFlow<Float> = _currentLossPct.asStateFlow()

    private val _sentDatagramCount = MutableStateFlow(0L)
    val sentDatagramCount: StateFlow<Long> = _sentDatagramCount.asStateFlow()

    private var activeTransport: ConnectTransport? = null
    private var seqCounter: Int = 0
    private var timestampCounter: Long = 0L
    private var incomingMessagesJob: Job? = null

    /**
     * Запуск сессии трансляции Audio Cast на подключённое устройство.
     */
    suspend fun startCast(
        transport: ConnectTransport,
        bitrateKbps: Int = 192
    ): Boolean {
        if (_isCasting.value) return true

        activeTransport = transport
        seqCounter = 0
        timestampCounter = 0L
        _sentDatagramCount.value = 0L

        // Отправка запроса на запуск стрима на стороне приёмника
        val setCastMsg = LirixConnectMsg.SetAudioCast(
            enabled = true,
            codec = CastCodec.OPUS,
            sampleRate = audioCaptureEngine.format.sampleRate,
            channels = audioCaptureEngine.format.channels,
            bitrateKbps = bitrateKbps
        )
        val envelope = Envelope(
            msgId = 1L,
            sentAtMonoMs = System.currentTimeMillis(),
            body = setCastMsg
        )
        transport.sendControlMessage(ConnectMessageCodec.encode(envelope))

        // Запуск мониторинга входящих отчётов CastStatus от приёмника
        incomingMessagesJob = scope.launch {
            try {
                transport.incomingControlMessages().collect { frameBytes ->
                    try {
                        val env = ConnectMessageCodec.decode(frameBytes)
                        val body = env.body
                        if (body is LirixConnectMsg.CastStatus) {
                            handleCastStatus(body)
                        }
                    } catch (_: Exception) {
                        // ignore malformed control frames
                    }
                }
            } catch (_: Exception) {
                // transport closed
            }
        }

        // Запуск захвата звука и отправки датаграмм
        val captureStarted = audioCaptureEngine.startCapture { chunk ->
            if (!_isCasting.value) return@startCapture
            val currentSeq = seqCounter
            seqCounter = (seqCounter + 1) and 0xFFFF

            val currentTs = timestampCounter
            timestampCounter += chunk.format.samplesPerFrame

            val flags: Byte = if (chunk.isSilent) CastDatagram.FLAG_DTX else 0

            // Упаковка PCM в датаграмму (при превышении MTU обрезается до MAX_PAYLOAD_SIZE)
            val payload = if (chunk.pcmData.size > CastDatagram.MAX_PAYLOAD_SIZE) {
                chunk.pcmData.copyOf(CastDatagram.MAX_PAYLOAD_SIZE)
            } else {
                chunk.pcmData
            }

            val datagram = CastDatagram(
                seq = currentSeq,
                timestamp48k = currentTs,
                flags = flags,
                payload = payload
            )

            val encoded = CastDatagramCodec.encode(datagram)
            scope.launch {
                try {
                    transport.sendDatagram(encoded)
                    _sentDatagramCount.value += 1
                } catch (_: Exception) {
                    // UDP packet drop is non-fatal
                }
            }
        }

        if (captureStarted) {
            _isCasting.value = true
        } else {
            incomingMessagesJob?.cancel()
            activeTransport = null
        }

        return captureStarted
    }

    /**
     * Обработка отчёта CastStatus от удалённого узла.
     */
    fun handleCastStatus(status: LirixConnectMsg.CastStatus) {
        _currentLatencyMs.value = status.receiverLatencyMs
        _currentLossPct.value = status.lossPct
    }

    /**
     * Остановка трансляции Audio Cast.
     */
    suspend fun stopCast() {
        if (!_isCasting.value) return

        _isCasting.value = false
        audioCaptureEngine.stopCapture()
        incomingMessagesJob?.cancel()
        incomingMessagesJob = null

        val transport = activeTransport
        if (transport != null) {
            try {
                val setCastOff = LirixConnectMsg.SetAudioCast(enabled = false)
                val envelope = Envelope(
                    msgId = 2L,
                    sentAtMonoMs = System.currentTimeMillis(),
                    body = setCastOff
                )
                transport.sendControlMessage(ConnectMessageCodec.encode(envelope))
            } catch (_: Exception) {
                // ignore
            }
        }
        activeTransport = null
    }
}
