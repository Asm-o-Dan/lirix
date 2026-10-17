package com.lirix.app.desktop

import androidx.compose.ui.graphics.toArgb
import com.lirix.app.analytics.*
import com.lirix.app.desktop.share.*
import com.lirix.app.storage.TrackEntity
import com.lirix.app.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class DesktopPosterTest {
    @Test fun allThemeAndFormatCombinationsRenderRealBitmaps() {
        val stats = WrappedStatsEngine.calculateStats(emptyList(), emptyList())
        val track = TrackEntity("a", "Длинное название песни ".repeat(20), "Исполнитель", sourcePackage = "test")
        for (theme in AppThemeType.entries) for (format in PosterFormat.entries) {
            val colors = ColorPalettes.getTokens(theme)
            for (bitmap in listOf(DesktopPosterRenderer.track(track, "Строка\n".repeat(20), colors, format),
                DesktopPosterRenderer.wrapped(stats, AnalyticsTimeframe.MONTH, colors, format))) {
                assertEquals(1080, bitmap.width)
                assertEquals(format.height, bitmap.height)
                assertEquals(colors.appBackground.toArgb(), bitmap.getRGB(0, 0))
            }
        }
    }
}
