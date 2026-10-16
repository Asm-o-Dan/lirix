package com.lirix.app.connect.transport

import com.lirix.app.connect.model.Bytes
import com.lirix.app.connect.model.PairingPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Локальный LAN транспорт для Lirix Connect (SPEC-CONNECT-01 §4.1).
 *
 * Обеспечивает:
 * 1. Надежный bi-stream канал управления на базе TCP (порты по умолчанию 4433).
 * 2. Ультранизколатентный датаграммный канал для передачи Audio Cast на базе UDP.
 * 3. Полную совместимость с интерфейсом [ConnectTransport].
 */
class LanConnectTransport(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : ConnectTransport {

    private val _state = MutableStateFlow(ConnectState.DISCONNECTED)
    override val state: StateFlow<ConnectState> = _state.asStateFlow()

    private val incomingControlFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    private val incomingDatagramFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 256)

    private var tcpSocket: Socket? = null
    private var tcpOutput: DataOutputStream? = null
    private var tcpReaderJob: Job? = null

    private var udpSocket: DatagramSocket? = null
    private var udpReaderJob: Job? = null

    private var targetHost: InetAddress? = null
    private var targetPort: Int = 4433

    override suspend fun connect(target: PairingPayload): Result<Unit> {
        return connectInternal(target.addrs)
    }

    override suspend fun connectPinned(
        endpointId: Bytes,
        knownAddrs: List<String>,
        relayUrl: String?
    ): Result<Unit> {
        return connectInternal(knownAddrs)
    }

    private suspend fun connectInternal(addrs: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        if (_state.value == ConnectState.CONNECTED) {
            return@withContext Result.success(Unit)
        }

        _state.value = ConnectState.CONNECTING
        disconnectInternal("Reconnecting")

        var lastException: Exception? = null

        val candidateAddrs = (if (addrs.isEmpty()) listOf("127.0.0.1:4433") else addrs).sortedByDescending { addr ->
            when {
                addr.startsWith("192.168.") -> 3
                addr.startsWith("10.") -> 2
                else -> 1
            }
        }

        for (addrStr in candidateAddrs) {
            try {
                val parts = addrStr.split(":")
                val host = parts[0]
                val port = if (parts.size > 1) parts[1].toIntOrNull() ?: 4433 else 4433

                val inetAddr = InetAddress.getByName(host)
                val socket = Socket()
                socket.tcpNoDelay = true
                socket.soTimeout = 0
                socket.connect(InetSocketAddress(inetAddr, port), 2000)

                tcpSocket = socket
                tcpOutput = DataOutputStream(socket.getOutputStream())
                targetHost = inetAddr
                targetPort = port

                // Инициализация UDP сокета
                val datagramSocket = DatagramSocket()
                datagramSocket.receiveBufferSize = 65536
                datagramSocket.sendBufferSize = 65536
                udpSocket = datagramSocket

                startReaders(socket, datagramSocket)

                _state.value = ConnectState.CONNECTED
                return@withContext Result.success(Unit)
            } catch (e: Exception) {
                lastException = e
            }
        }

        _state.value = ConnectState.FAILED
        Result.failure(lastException ?: IllegalStateException("Failed to connect to any address in $addrs"))
    }

    private fun startReaders(socket: Socket, dSocket: DatagramSocket) {
        // Чтение TCP управляющих сообщений (4 байта LE длина + payload)
        tcpReaderJob = scope.launch {
            try {
                val input = DataInputStream(socket.getInputStream())
                val lenBuf = ByteArray(4)

                while (isActive && !socket.isClosed) {
                    input.readFully(lenBuf)
                    val len = ByteBuffer.wrap(lenBuf).order(ByteOrder.LITTLE_ENDIAN).int
                    if (len in 1..131072) {
                        val body = ByteArray(len)
                        input.readFully(body)
                        incomingControlFlow.tryEmit(body)
                    }
                }
            } catch (_: Exception) {
            } finally {
                if (_state.value == ConnectState.CONNECTED) {
                    _state.value = ConnectState.DISCONNECTED
                }
            }
        }

        // Чтение UDP датаграмм
        udpReaderJob = scope.launch {
            val buf = ByteArray(2048)
            val packet = DatagramPacket(buf, buf.size)
            try {
                while (isActive && !dSocket.isClosed) {
                    dSocket.receive(packet)
                    val chunk = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
                    incomingDatagramFlow.tryEmit(chunk)
                }
            } catch (_: Exception) {
            }
        }
    }

    override suspend fun sendControlMessage(data: ByteArray): Unit = withContext(Dispatchers.IO) {
        val out = tcpOutput ?: throw IllegalStateException("Transport not connected")
        synchronized(this@LanConnectTransport) {
            val lenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.size).array()
            out.write(lenBytes)
            out.write(data)
            out.flush()
        }
    }

    override fun incomingControlMessages(): Flow<ByteArray> = incomingControlFlow.asSharedFlow()

    override suspend fun sendDatagram(datagram: ByteArray): Unit = withContext(Dispatchers.IO) {
        val dSocket = udpSocket ?: throw IllegalStateException("Transport not connected")
        val host = targetHost ?: throw IllegalStateException("Transport not connected")
        val packet = DatagramPacket(datagram, datagram.size, host, targetPort)
        dSocket.send(packet)
    }

    override fun incomingDatagrams(): Flow<ByteArray> = incomingDatagramFlow.asSharedFlow()

    override suspend fun disconnect(reason: String) {
        withContext(Dispatchers.IO) {
            disconnectInternal(reason)
            _state.value = ConnectState.DISCONNECTED
        }
    }

    private fun disconnectInternal(reason: String) {
        tcpReaderJob?.cancel()
        tcpReaderJob = null
        udpReaderJob?.cancel()
        udpReaderJob = null

        try {
            tcpOutput?.close()
        } catch (_: Exception) {}
        tcpOutput = null

        try {
            tcpSocket?.close()
        } catch (_: Exception) {}
        tcpSocket = null

        try {
            udpSocket?.close()
        } catch (_: Exception) {}
        udpSocket = null
    }
}
