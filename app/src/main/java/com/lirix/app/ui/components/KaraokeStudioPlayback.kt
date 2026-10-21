package com.lirix.app.ui.components

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import android.view.KeyEvent
import com.lirix.app.feature.MusicFeatureEngine
import com.lirix.app.ingestion.MediaSessionCollector

data class StudioPlaybackReading(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playing: Boolean = false,
    val canSeek: Boolean = false,
    val canToggle: Boolean = false,
    val matchesTrack: Boolean = false
)

/** Guard against the actual controller metadata, including after a source changed tracks. */
internal fun studioController(trackKey: String, sourcePackage: String): MediaController? = runCatching {
    val controller = MediaSessionCollector.getActiveController(sourcePackage.ifBlank { null }) ?: return@runCatching null
    if (sourcePackage.isNotBlank() && controller.packageName != sourcePackage) return@runCatching null
    val metadata = controller.metadata ?: return@runCatching null
    val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
    val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty().ifBlank { "Unknown Artist" }
    val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
    controller.takeIf { MusicFeatureEngine.computeTrackKey(title, artist, album) == trackKey }
}.getOrNull()

internal fun readStudioPlayback(trackKey: String, sourcePackage: String): StudioPlaybackReading = runCatching {
    val controller = studioController(trackKey, sourcePackage) ?: return@runCatching StudioPlaybackReading()
    val playback = controller.playbackState ?: return@runCatching StudioPlaybackReading(matchesTrack = true)
    val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0
    val playing = playback.state == PlaybackState.STATE_PLAYING
    val elapsed = if (playing && playback.lastPositionUpdateTime > 0)
        ((SystemClock.elapsedRealtime() - playback.lastPositionUpdateTime).coerceAtLeast(0) * playback.playbackSpeed).toLong() else 0
    val raw = (playback.position + elapsed).coerceAtLeast(0)
    StudioPlaybackReading(
        if (duration > 0) raw.coerceAtMost(duration) else raw, duration, playing,
        playback.actions and PlaybackState.ACTION_SEEK_TO != 0L,
        playback.actions and (PlaybackState.ACTION_PLAY_PAUSE or (if (playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY)) != 0L,
        matchesTrack = true
    )
}.getOrDefault(StudioPlaybackReading())

internal fun seekStudioPlayback(trackKey: String, sourcePackage: String, positionMs: Long): Boolean {
    val controller = studioController(trackKey, sourcePackage) ?: return false
    if ((controller.playbackState?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO == 0L) return false
    return runCatching {
        val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0
        val position = positionMs.coerceAtLeast(0).let { if (duration > 0) it.coerceAtMost(duration) else it }
        controller.transportControls.seekTo(position)
        true
    }.getOrDefault(false)
}

internal fun toggleStudioPlayback(trackKey: String, sourcePackage: String): Boolean {
    val controller = studioController(trackKey, sourcePackage) ?: return false
    val playback = controller.playbackState ?: return false
    val playing = playback.state == PlaybackState.STATE_PLAYING
    val required = if (playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
    if (playback.actions and (required or PlaybackState.ACTION_PLAY_PAUSE) == 0L) return false
    return runCatching {
        if (playback.actions and required != 0L) {
            if (playing) controller.transportControls.pause() else controller.transportControls.play()
        } else {
            check(controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)))
            controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        }
        true
    }.getOrDefault(false)
}
