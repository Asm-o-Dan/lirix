package com.lirix.app.feature.share

import androidx.compose.ui.graphics.toArgb
import com.lirix.app.ui.theme.LirixColors

/** Captured before background rendering; no access to Compose locals from the renderer. */
data class PosterThemeSnapshot(
    val background: Int,
    val surface: Int,
    val primary: Int,
    val secondary: Int,
    val border: Int,
    val accent: Int
) {
    companion object {
        fun fromColors(colors: LirixColors) = PosterThemeSnapshot(
            colors.appBackground.toArgb(), colors.surfaceCard.toArgb(),
            colors.textPrimary.toArgb(), colors.textSecondary.toArgb(),
            colors.borderSubtle.toArgb(), colors.accentPrimary.toArgb()
        )
    }
}
