package com.lirix.app

import android.content.Context
import android.net.ConnectivityManager
import com.lirix.app.feature.lyrics.LookupNetworkMonitor
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

/** Observes the real OS callback; root supplies explicit both-radio toggle evidence separately. */
class LookupNetworkMonitorDeviceTest {
    @Test fun actualInitialSnapshotMatchesValidatedDefaultNetwork() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val snapshot = withTimeout(10_000) { LookupNetworkMonitor(context).observe().first() }
        val actual = LookupNetworkMonitor.isUsable(manager.activeNetwork?.let(manager::getNetworkCapabilities))
        assertEquals(actual, snapshot.usable)
        assertEquals(actual, LookupNetworkMonitor.usable(context))
        // first() cancels the flow and executes callbackFlow awaitClose; no radio changes/test-only callbacks.
    }
}
