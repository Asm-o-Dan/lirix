package com.example.npc.ingest.media.observer

import android.support.v4.media.session.MediaControllerCompat

/**
 * Internal holder for a single player's [MediaControllerCompat] and its registered callback.
 *
 * Provides [register] / [unregister] lifecycle methods to safely attach and detach
 * the callback from the controller without risking null-pointer or duplicate registration.
 *
 * Thread-safety: instances are NOT thread-safe individually; the outer [MediaSessionObserver]
 * is responsible for single-threaded access.
 */
internal class MediaControllerHolder(
    val packageName: String,
    val controller: MediaControllerCompat,
    val callback: MediaControllerCompat.Callback
) {
    private var registered = false

    /** Registers the callback on the controller. Idempotent — safe to call multiple times. */
    fun register() {
        if (!registered) {
            controller.registerCallback(callback)
            registered = true
        }
    }

    /** Unregisters the callback from the controller. Idempotent — safe to call multiple times. */
    fun unregister() {
        if (registered) {
            try {
                controller.unregisterCallback(callback)
            } catch (_: Throwable) {
                // Controller may already be dead — swallow to prevent cascade crash
            }
            registered = false
        }
    }
}
