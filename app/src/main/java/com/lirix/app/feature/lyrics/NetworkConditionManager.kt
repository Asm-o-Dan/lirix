package com.lirix.app.feature.lyrics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Lyrics network policy defining prefetching and download permissions.
 * Spec: TASK-LYR-01 / .sdd/specs/lyrics-engine/overview.md#getNetworkPolicy (v1)
 */
enum class LyricsNetworkPolicy {
    PREFETCH_ALLOWED, // Wi-Fi / Ethernet - auto-prefetch lyrics & chords
    ON_DEMAND_ONLY,   // Cellular mobile data - download only on explicit user tap
    OFFLINE_ONLY      // No connection or error - offline cache only
}

/**
 * Evaluates active network transport to choose appropriate lyrics caching policy.
 * Protects user mobile data while maximizing offline availability on Wi-Fi/Ethernet.
 */
object NetworkConditionManager {

    fun getNetworkPolicy(context: Context?): LyricsNetworkPolicy {
        if (context == null) return LyricsNetworkPolicy.OFFLINE_ONLY

        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return@runCatching LyricsNetworkPolicy.OFFLINE_ONLY

            val network = cm.activeNetwork ?: return@runCatching LyricsNetworkPolicy.OFFLINE_ONLY
            val caps = cm.getNetworkCapabilities(network) ?: return@runCatching LyricsNetworkPolicy.OFFLINE_ONLY

            evaluateCaps(caps)
        }.getOrDefault(LyricsNetworkPolicy.OFFLINE_ONLY)
    }

    internal fun evaluateCaps(caps: NetworkCapabilities?): LyricsNetworkPolicy {
        if (caps == null) return LyricsNetworkPolicy.OFFLINE_ONLY
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                LyricsNetworkPolicy.PREFETCH_ALLOWED
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                LyricsNetworkPolicy.ON_DEMAND_ONLY
            }
            else -> {
                LyricsNetworkPolicy.OFFLINE_ONLY
            }
        }
    }
}

/**
 * Top-level signature per TASK-LYR-01 specification.
 */
fun getNetworkPolicy(context: Context): LyricsNetworkPolicy =
    NetworkConditionManager.getNetworkPolicy(context)
