package com.lirix.app.desktop.cast

import com.lirix.app.connect.cast.CastDatagramCodec
import com.lirix.app.connect.model.Bytes
import com.lirix.app.connect.model.DeviceType
import com.lirix.app.connect.proto.CastCodec
import com.lirix.app.connect.proto.ConnectMessageCodec
import com.lirix.app.connect.proto.Envelope
import com.lirix.app.connect.proto.LirixConnectMsg
import com.lirix.app.connect.security.TrustedDevice
import com.lirix.app.connect.security.TrustedDeviceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine

/**
 * Сервер Lirix Connect для Windows Desktop (SPEC-CONNECT-01 §4.1, §4.7).
 *
 * Принимает TCP управляющие соединения и UDP-датаграммы системной трансляции звука (Audio Cast).
 * Воспроизводит звук на звуковой карте ПК через Java Sound API (WASAPI-совместимый SourceDataLine 48kHz Stereo 16-bit).
 */
class DesktopConnectServer(
    val trustedStore: TrustedDeviceStore = TrustedDeviceStore.inMemory(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val audioLineFactory: (AudioFormat) -> SourceDataLine = { format ->
        val info = DataLine.Info(SourceDataLine::class.java, format)
        AudioSystem.getLine(info) as SourceDataLine
    }
) {
    companion object {
        const val DEFAULT_PORT = 4433
        const val SAMPLE_RATE = 48000
        const val CHANNELS = 2
        const val BITS_PER_SAMPLE = 16

        @Volatile
        private var instance: DesktopConnectServer? = null

        fun getInstance(): DesktopConnectServer {
            return instance ?: synchronized(this) {
                instance ?: DesktopConnectServer().also { instance = it }
            }
        }
    }

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isAudioCastPlaying = MutableStateFlow(false)
    val isAudioCastPlaying: StateFlow<Boolean> = _isAudioCastPlaying.asStateFlow()

    private val _connectedClientName = MutableStateFlow<String?>(null)
    val connectedClientName: StateFlow<String?> = _connectedClientName.asStateFlow()

    private val _receivedDatagramCount = MutableStateFlow(0L)
    val receivedDatagramCount: StateFlow<Long> = _receivedDatagramCount.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var datagramSocket: DatagramSocket? = null
    private var tcpServerJob: Job? = null
    private var udpServerJob: Job? = null

    private var activeAudioLine: SourceDataLine? = null
    private var volumeGain: Float = 1.0f

    fun start(port: Int = DEFAULT_PORT) {
        if (_isListening.value) return

        try {
            serverSocket = ServerSocket(port).apply {
                reuseAddress = true
            }
            datagramSocket = DatagramSocket(port).apply {
                reuseAddress = true
                receiveBufferSize = 262144
            }
            _isListening.value = true

            startTcpListener(serverSocket!!)
            startUdpAudioListener(datagramSocket!!)
        } catch (e: Exception) {
            _isListening.value = false
            stop()
        }
    }

    fun stop() {
        _isListening.value = false
        _isAudioCastPlaying.value = false
        _connectedClientName.value = null

        tcpServerJob?.cancel()
        tcpServerJob = null

        udpServerJob?.cancel()
        udpServerJob = null

        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        try {
            datagramSocket?.close()
        } catch (_: Exception) {}
        datagramSocket = null

        closeAudioLine()
    }

    private fun startTcpListener(ss: ServerSocket) {
        tcpServerJob = scope.launch {
            while (isActive && !ss.isClosed) {
                try {
                    val clientSocket = ss.accept()
                    clientSocket.tcpNoDelay = true
                    handleClient(clientSocket)
                } catch (_: Exception) {}
            }
        }
    }

    private fun handleClient(socket: Socket) {
        scope.launch {
            try {
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())
                val lenBuf = ByteArray(4)

                while (isActive && !socket.isClosed) {
                    input.readFully(lenBuf)
                    val len = ByteBuffer.wrap(lenBuf).order(ByteOrder.LITTLE_ENDIAN).int
                    if (len in 1..131072) {
                        val body = ByteArray(len)
                        input.readFully(body)

                        try {
                            val envelope = ConnectMessageCodec.decode(body)
                            handleMessage(envelope.body, output)
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {
            } finally {
                _connectedClientName.value = null
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    private suspend fun handleMessage(msg: LirixConnectMsg, output: DataOutputStream) {
        when (msg) {
            is LirixConnectMsg.PairRequest -> {
                _connectedClientName.value = msg.deviceName
                val trusted = TrustedDevice(
                    endpointId = Bytes(ByteArray(32) { (it + 1).toByte() }),
                    deviceName = msg.deviceName,
                    deviceType = DeviceType.ANDROID_PHONE.name,
                    pairedAtEpochSec = System.currentTimeMillis() / 1000L,
                    lastSeenEpochSec = System.currentTimeMillis() / 1000L
                )
                trustedStore.addTrusted(trusted)

                // Ответ об успешном сопряжении
                val reply = Envelope(
                    msgId = System.currentTimeMillis(),
                    sentAtMonoMs = System.currentTimeMillis(),
                    body = LirixConnectMsg.PairResult(ok = true, verifyCode = "OK")
                )
                val encoded = ConnectMessageCodec.encode(reply)
                val lenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(encoded.size).array()
                synchronized(output) {
                    output.write(lenBytes)
                    output.write(encoded)
                    output.flush()
                }
            }

            is LirixConnectMsg.SetAudioCast -> {
                if (!msg.enabled) {
                    closeAudioLine()
                    _isAudioCastPlaying.value = false
                } else {
                    if (msg.codec == CastCodec.PCM_S16LE && msg.sampleRate == SAMPLE_RATE && msg.channels == CHANNELS) {
                        val opened = openAudioLine(msg.sampleRate, msg.channels)
                        _isAudioCastPlaying.value = opened
                    } else {
                        System.err.println("[LirixConnect] stage=format-rejected codec=${msg.codec} sampleRate=${msg.sampleRate} channels=${msg.channels}")
                        closeAudioLine()
                        _isAudioCastPlaying.value = false
                    }
                }
            }

            is LirixConnectMsg.RemoteMediaAction -> {
                val vol = msg.volume
                if (msg.action == com.lirix.app.connect.proto.MediaAction.SET_VOLUME && vol != null) {
                    setVolume(vol)
                }
            }

            else -> {}
        }
    }

    private fun startUdpAudioListener(ds: DatagramSocket) {
        udpServerJob = scope.launch {
            val buf = ByteArray(2048)
            val packet = DatagramPacket(buf, buf.size)

            while (isActive && !ds.isClosed) {
                try {
                    packet.length = buf.size
                    ds.receive(packet)
                    _receivedDatagramCount.value += 1

                    // Не обрабатываем датаграммы и не открываем audio line, если Cast не активен
                    if (!_isAudioCastPlaying.value || activeAudioLine == null) {
                        continue
                    }

                    val chunk = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                    val datagram = try {
                        CastDatagramCodec.decode(chunk)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        System.err.println("[LirixConnect] stage=udp-decode error=${e.javaClass.simpleName}: ${e.message}")
                        continue
                    }

                    val pcm = datagram.opusPayload
                    if (pcm.isNotEmpty()) {
                        if (pcm.size % 4 != 0) {
                            System.err.println("[LirixConnect] stage=udp-pcm-invalid-size size=${pcm.size}")
                            continue
                        }
                        writePcm(pcm)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    if (!ds.isClosed) {
                        System.err.println("[LirixConnect] stage=udp-receive error=${e.javaClass.simpleName}: ${e.message}")
                    }
                }
            }
        }
    }

    @Synchronized
    internal fun openAudioLine(sampleRate: Int = SAMPLE_RATE, channels: Int = CHANNELS): Boolean {
        _isAudioCastPlaying.value = false
        closeAudioLine()
        if (sampleRate != SAMPLE_RATE || channels != CHANNELS) {
            System.err.println("[LirixConnect] stage=output-open error=unsupported-parameters sampleRate=$sampleRate channels=$channels")
            return false
        }
        var createdLine: SourceDataLine? = null
        return try {
            val format = AudioFormat(
                sampleRate.toFloat(),
                BITS_PER_SAMPLE,
                channels,
                true, // signed
                false // little-endian (PCM стандарт)
            )
            val line = audioLineFactory(format)
            createdLine = line
            line.open(format, sampleRate * channels * (BITS_PER_SAMPLE / 8) / 10) // 100ms буфер
            line.start()
            activeAudioLine = line
            applyVolume()
            true
        } catch (e: Exception) {
            try { createdLine?.stop() } catch (_: Exception) {}
            try { createdLine?.flush() } catch (_: Exception) {}
            try { createdLine?.close() } catch (_: Exception) {}
            activeAudioLine = null
            _isAudioCastPlaying.value = false
            if (e is kotlinx.coroutines.CancellationException) throw e
            System.err.println("[LirixConnect] stage=output-open error=${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    @Synchronized
    internal fun writePcm(data: ByteArray) {
        val line = activeAudioLine ?: return
        if (!_isAudioCastPlaying.value) return
        if (data.isEmpty()) return

        if (data.size % 4 != 0) {
            System.err.println("[LirixConnect] stage=pcm-invalid-size size=${data.size}")
            return
        }

        var offset = 0
        val totalLength = data.size

        while (offset < totalLength) {
            val bytesRemaining = totalLength - offset
            try {
                val bytesWritten = line.write(data, offset, bytesRemaining)
                if (bytesWritten <= 0 || bytesWritten > bytesRemaining || bytesWritten % 4 != 0) {
                    System.err.println("[LirixConnect] stage=output-write error=invalid-bytes-written count=$bytesWritten")
                    _isAudioCastPlaying.value = false
                    closeAudioLine()
                    return
                }
                offset += bytesWritten
            } catch (e: Exception) {
                _isAudioCastPlaying.value = false
                closeAudioLine()
                if (e is kotlinx.coroutines.CancellationException) throw e
                System.err.println("[LirixConnect] stage=output-write error=${e.javaClass.simpleName}: ${e.message}")
                return
            }
        }
    }

    @Synchronized
    internal fun closeAudioLine() {
        val line = activeAudioLine ?: return
        activeAudioLine = null
        var cancellation: kotlinx.coroutines.CancellationException? = null
        for (cleanup in listOf<() -> Unit>(line::stop, line::flush, line::close)) {
            try {
                cleanup()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    if (cancellation == null) cancellation = e
                } else {
                    System.err.println("[LirixConnect] stage=output-close error=${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }
        cancellation?.let { throw it }
    }

    fun setVolume(pct: Float) {
        volumeGain = pct.coerceIn(0.0f, 1.0f)
        applyVolume()
    }

    private fun applyVolume() {
        val line = activeAudioLine ?: return
        try {
            if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                val gainCtrl = line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                val min = gainCtrl.minimum
                val max = gainCtrl.maximum
                if (volumeGain <= 0.001f) {
                    gainCtrl.value = min
                } else {
                    val db = (20.0 * kotlin.math.log10(volumeGain.toDouble())).toFloat()
                    gainCtrl.value = db.coerceIn(min, max)
                }
            }
        } catch (_: Exception) {}
    }
}
