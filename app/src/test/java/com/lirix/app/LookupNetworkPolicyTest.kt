package com.lirix.app

import android.net.NetworkCapabilities
import com.lirix.app.feature.lyrics.LookupNetworkMonitor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities

/** SDK capabilities are read-only; official Robolectric Shadows supply controlled capability facts. */
@RunWith(RobolectricTestRunner::class)
class LookupNetworkPolicyTest {
    @Test fun cellularNeedsValidationAndWifiTransportAloneIsNotInternet() {
        fun snapshot(transport: Int, internet: Boolean, validated: Boolean): NetworkCapabilities {
            val caps = ShadowNetworkCapabilities.newInstance()
            val shadow = shadowOf(caps)
            shadow.addTransportType(transport)
            if (internet) shadow.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            else shadow.removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (validated) shadow.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            else shadow.removeCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            return caps
        }
        assertFalse(LookupNetworkMonitor.isUsable(null))
        val cellular = NetworkCapabilities.TRANSPORT_CELLULAR
        assertFalse(LookupNetworkMonitor.isUsable(snapshot(cellular, internet = true, validated = false)))
        assertTrue(LookupNetworkMonitor.isUsable(snapshot(cellular, internet = true, validated = true)))
        assertFalse(LookupNetworkMonitor.isUsable(snapshot(cellular, internet = false, validated = true)))
        assertFalse(LookupNetworkMonitor.isUsable(snapshot(NetworkCapabilities.TRANSPORT_WIFI,
            internet = false, validated = false)))
    }
}
