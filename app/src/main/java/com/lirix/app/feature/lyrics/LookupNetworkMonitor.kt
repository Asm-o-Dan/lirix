package com.lirix.app.feature.lyrics

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Validated default Internet only; cellular is permitted for an already-open track. */
class LookupNetworkMonitor(context: Context) {
    private val applicationContext = context.applicationContext
    private val lock = Any()
    private var previous: Pair<Network?, Boolean>? = null
    private var generation = 0L

    fun observe(): Flow<LookupNetworkSnapshot> = callbackFlow {
        val manager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (manager == null) { trySend(LookupNetworkSnapshot(false, 0)); close(); return@callbackFlow }
        var initial = true
        fun publish() = synchronized(lock) {
            val current = runCatching { manager.activeNetwork }.getOrNull()
            val usable = runCatching { isUsable(current?.let(manager::getNetworkCapabilities)) }.getOrDefault(false)
            val next = current to usable
            if (previous == next && !initial) return@synchronized
            initial = false
            if (usable && (previous?.second != true || previous?.first != current)) generation++
            previous = next
            trySend(LookupNetworkSnapshot(usable, generation))
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { publish() }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { publish() }
            override fun onLost(network: Network) { publish() }
        }
        val registered = runCatching { manager.registerDefaultNetworkCallback(callback); true }.getOrDefault(false)
        if (registered) publish() else trySend(LookupNetworkSnapshot(false, 0))
        awaitClose { if (registered) runCatching { manager.unregisterNetworkCallback(callback) } }
    }
    companion object {
        fun isUsable(caps: NetworkCapabilities?): Boolean = caps != null &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        fun usable(context: Context): Boolean = runCatching {
            val manager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            isUsable(manager?.activeNetwork?.let { manager.getNetworkCapabilities(it) })
        }.getOrDefault(false)
    }
}
