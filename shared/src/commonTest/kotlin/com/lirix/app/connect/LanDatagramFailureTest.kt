package com.lirix.app.connect

import com.lirix.app.connect.transport.LanConnectTransport
import com.lirix.app.connect.transport.ConnectState
import com.lirix.app.connect.model.Bytes
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertNull

class LanDatagramFailureTest {
    @Test fun disconnectedTransportDoesNotReportSuccessfulDatagramSend() = runBlocking {
        val transport = LanConnectTransport()
        assertFailsWith<IllegalStateException> { transport.sendDatagram(byteArrayOf(1, 2, 3, 4)) }
        Unit
    }

    @Test fun controlEofClosesDatagramSocketAndAllowsReconnect() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val transport = LanConnectTransport(scope)
        val server = ServerSocket(0)
        try {
            val accept = async(Dispatchers.IO) { server.accept() }
            assertTrue(transport.connectPinned(Bytes.EMPTY, listOf("127.0.0.1:${server.localPort}"), null).isSuccess)
            withTimeout(5_000) { accept.await() }.close()
            withTimeout(5_000) { transport.state.first { it == ConnectState.DISCONNECTED } }
            assertFailsWith<IllegalStateException> { transport.sendDatagram(byteArrayOf(1)) }
            val udp = LanConnectTransport::class.java.getDeclaredField("udpSocket").apply { isAccessible = true }
            assertNull(udp.get(transport))

            val reconnect = async(Dispatchers.IO) { server.accept() }
            assertTrue(transport.connectPinned(Bytes.EMPTY, listOf("127.0.0.1:${server.localPort}"), null).isSuccess)
            val peer = withTimeout(5_000) { reconnect.await() }
            try {
                assertTrue(transport.state.value == ConnectState.CONNECTED)
                transport.sendDatagram(byteArrayOf(2))
            } finally { peer.close() }
        } finally {
            transport.disconnect("Test complete")
            scope.cancel()
            server.close()
        }
        Unit
    }
}
