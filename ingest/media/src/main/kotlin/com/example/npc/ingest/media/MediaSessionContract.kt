package com.example.npc.ingest.media

import java.time.Instant

/**
 * Cross-zone contract DTO for :ingest:media → :core:storage integration.
 *
 * This DTO is the public API surface of the module for inter-zone communication.
 * [sessionEndedAt] == null means this is a live/active session snapshot.
 */
data class MediaSessionContract(
    val packageName: String,
    val trackTitle: String?,
    val artist: String?,
    val sessionStartedAt: Instant,
    val sessionEndedAt: Instant?
)
