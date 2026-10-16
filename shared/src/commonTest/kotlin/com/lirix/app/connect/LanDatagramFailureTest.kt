package com.lirix.app.connect

import com.lirix.app.connect.transport.LanConnectTransport
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith

class LanDatagramFailureTest {
    @Test fun disconnectedTransportDoesNotReportSuccessfulDatagramSend() = runBlocking {
        val transport = LanConnectTransport()
        assertFailsWith<IllegalStateException> { transport.sendDatagram(byteArrayOf(1, 2, 3, 4)) }
        Unit
    }
}
