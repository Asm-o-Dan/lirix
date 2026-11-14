package com.lirix.app.ui

import androidx.annotation.StringRes
import com.lirix.app.R

/** Missing/loading/error feedback belongs beside its retry action in the content area. */
@StringRes
internal fun playerHeaderStatus(
    mode: NowPlayingMode,
    timed: Boolean,
    plain: Boolean,
    chords: Boolean,
    unsyncedBadge: Boolean
): Int? = when (mode) {
    NowPlayingMode.NOTES -> null
    NowPlayingMode.CHORDS -> if (chords) R.string.player_chords_available else null
    NowPlayingMode.KARAOKE -> when {
        timed -> R.string.lyrics_status_synced
        unsyncedBadge -> null
        plain -> R.string.lyrics_status_static
        else -> null
    }
    NowPlayingMode.LYRICS -> if ((plain || timed) && !unsyncedBadge) R.string.lyrics_status_static else null
}
