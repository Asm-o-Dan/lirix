package com.lirix.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.feature.lyrics.LyricsNetworkPolicy
import com.lirix.app.feature.lyrics.NetworkConditionManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities

import org.robolectric.annotation.Config

/**
 * Unit-tests for NetworkConditionManager (TASK-LYR-01).
 * Validates PREFETCH_ALLOWED (Wi-Fi/Ethernet), ON_DEMAND_ONLY (Cellular), and OFFLINE_ONLY policies.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class NetworkConditionManagerTest {

    @Test
    fun test_network_policy_wifi_returnsPrefetchAllowed() {
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)

        val policy = NetworkConditionManager.evaluateCaps(caps)
        assertEquals(LyricsNetworkPolicy.PREFETCH_ALLOWED, policy)
    }

    @Test
    fun test_network_policy_ethernet_returnsPrefetchAllowed() {
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)

        val policy = NetworkConditionManager.evaluateCaps(caps)
        assertEquals(LyricsNetworkPolicy.PREFETCH_ALLOWED, policy)
    }

    @Test
    fun test_network_policy_cellular_returnsOnDemandOnly() {
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)

        val policy = NetworkConditionManager.evaluateCaps(caps)
        assertEquals(LyricsNetworkPolicy.ON_DEMAND_ONLY, policy)
    }

    @Test
    fun test_network_policy_offline_returnsOfflineOnly() {
        val policy = NetworkConditionManager.evaluateCaps(null)
        assertEquals(LyricsNetworkPolicy.OFFLINE_ONLY, policy)

        val nullContextPolicy = NetworkConditionManager.getNetworkPolicy(null)
        assertEquals(LyricsNetworkPolicy.OFFLINE_ONLY, nullContextPolicy)
    }
}
