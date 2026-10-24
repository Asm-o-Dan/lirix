package com.lirix.app.desktop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.ui.player.formatDurationMs
import com.lirix.app.ui.theme.ContrastClampHelper
import com.lirix.app.ui.theme.LirixThemeTokens
import com.lirix.app.ui.theme.LocalLirixTypography
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.prefs.Preferences

/**
 * Persistence store for prompter sync offset using standard Java Preferences.
 * Persists locally to Windows Registry HKCU\Software\JavaSoft\Prefs.
 */
object DesktopSyncOffsetStore {
    const val DEFAULT_OFFSET_MS = -300L // -0.3 s default offset for Windows GSMTC
    private val storage = DesktopSyncOffsetPreferences(Preferences.userNodeForPackage(DesktopSyncOffsetStore::class.java))
    val offsetMs: StateFlow<Long> = storage.offsetMs

    fun getOffsetMs(): Long = storage.getOffsetMs()

    fun setOffsetMs(offsetMs: Long) = storage.setOffsetMs(offsetMs)
}

internal class DesktopSyncOffsetPreferences(private val preferences: Preferences) {
    private val currentOffset = MutableStateFlow(preferences.getLong("teleprompter_sync_offset_ms", -300L))
    val offsetMs: StateFlow<Long> = currentOffset.asStateFlow()

    fun getOffsetMs(): Long = currentOffset.value

    fun setOffsetMs(offsetMs: Long) {
        preferences.putLong("teleprompter_sync_offset_ms", offsetMs)
        currentOffset.value = offsetMs
    }
}

/**
 * DesktopTransportBar: Teleprompter lower transport deck.
 *
 * Spec: TASK-UI-PROMPTER-FIX-01 §2 & §3
 *
 * Vertical structure:
 * 1. Center: Transport buttons (Prev 36dp, Play/Pause 48dp, Next 36dp) with vector Material icons.
 * 2. Full-width Trackbar Slider.
 * 3. Sub-row: Left = Timecodes (00:00 / 00:00 with tnum); Right = Sync offset regulator "Сдвиг текста -0,3 с [-] [+]".
 */
@Composable
fun DesktopTransportBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    modifier: Modifier = Modifier,
    initialOffsetMs: Long = DesktopSyncOffsetStore.getOffsetMs(),
    onOffsetChanged: (Long) -> Unit = {},
    accentColor: Color? = null,
    isDark: Boolean = LirixThemeTokens.colors.isDark
) {
    val colors = LirixThemeTokens.colors
    val typography = LocalLirixTypography.current

    val rawAccent = accentColor ?: colors.accentPrimary
    val effectiveAccent = remember(rawAccent, isDark) {
        ContrastClampHelper.clampAccentContrast(rawAccent, isDark)
    }

    // Sync offset state (step: 100 ms = 0.1 s)
    var currentOffsetMs by remember(initialOffsetMs) { mutableLongStateOf(initialOffsetMs) }

    fun updateOffset(deltaMs: Long) {
        val newOffset = currentOffsetMs + deltaMs
        currentOffsetMs = newOffset
        DesktopSyncOffsetStore.setOffsetMs(newOffset)
        onOffsetChanged(newOffset)
    }

    // Trackbar dragging state
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val safeDuration = durationMs.coerceAtLeast(0L)
    val displayPosition = if (isDragging) {
        (dragFraction * safeDuration).toLong()
    } else {
        positionMs.coerceIn(0L, if (safeDuration > 0) safeDuration else Long.MAX_VALUE)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // =====================================================================
        // 1. Row: Transport Buttons (Centered, Vector Icons)
        // =====================================================================
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Prev button (36x36 dp)
            Surface(
                shape = CircleShape,
                color = if (isDark) LirixThemeTokens.Slate else LirixThemeTokens.Vellum,
                border = BorderStroke(
                    1.dp,
                    if (isDark) LirixThemeTokens.Hairline else LirixThemeTokens.HairlineLight
                ),
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onSkipPrevious() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = com.lirix.app.ui.i18n.LirixStrings.message("Предыдущий трек"),
                        tint = if (isDark) LirixThemeTokens.Chalk else LirixThemeTokens.Ink,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Play / Pause button (48x48 dp)
            Button(
                onClick = onTogglePlayPause,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = effectiveAccent,
                    contentColor = if (isDark) LirixThemeTokens.Graphite else LirixThemeTokens.Parchment
                ),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) com.lirix.app.ui.i18n.LirixStrings.message("Пауза") else com.lirix.app.ui.i18n.LirixStrings.message("Воспроизведение"),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Next button (36x36 dp)
            Surface(
                shape = CircleShape,
                color = if (isDark) LirixThemeTokens.Slate else LirixThemeTokens.Vellum,
                border = BorderStroke(
                    1.dp,
                    if (isDark) LirixThemeTokens.Hairline else LirixThemeTokens.HairlineLight
                ),
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onSkipNext() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = com.lirix.app.ui.i18n.LirixStrings.message("Следующий трек"),
                        tint = if (isDark) LirixThemeTokens.Chalk else LirixThemeTokens.Ink,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // =====================================================================
        // 2. Full-Width Trackbar Slider
        // =====================================================================
        val sliderValue = if (safeDuration > 0) {
            if (isDragging) dragFraction else (displayPosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
        } else 0f

        Slider(
            value = sliderValue,
            onValueChange = { frac ->
                isDragging = true
                dragFraction = frac
            },
            onValueChangeFinished = {
                val targetMs = (dragFraction * safeDuration).toLong()
                onSeekTo(targetMs)
                isDragging = false
            },
            enabled = safeDuration > 0,
            colors = SliderDefaults.colors(
                thumbColor = effectiveAccent,
                activeTrackColor = effectiveAccent,
                inactiveTrackColor = if (isDark) LirixThemeTokens.Hairline else LirixThemeTokens.HairlineLight
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
        )

        // =====================================================================
        // 3. Sub-Row: Timers (tnum) on Left, Sync Offset Regulator on Right
        // =====================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Timecodes with tnum (e.g. 01:15 / 06:12)
            Text(
                text = "${formatDurationMs(displayPosition)} / ${formatDurationMs(safeDuration)}",
                style = typography.timecodeAndBadges,
                color = if (isDark) LirixThemeTokens.Mist else LirixThemeTokens.Pencil
            )

            // Sync Offset Regulator: "Сдвиг текста −0,3 с [ − ] [ + ]"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = com.lirix.app.ui.i18n.LirixStrings.message("Сдвиг текста {0}", formatOffsetString(currentOffsetMs)),
                    style = typography.timecodeAndBadges,
                    color = if (isDark) LirixThemeTokens.Mist else LirixThemeTokens.Pencil
                )

                // [ - ] step -0.1s
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isDark) LirixThemeTokens.Slate else LirixThemeTokens.Vellum,
                    border = BorderStroke(
                        1.dp,
                        if (isDark) LirixThemeTokens.Hairline else LirixThemeTokens.HairlineLight
                    ),
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { updateOffset(-100L) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = com.lirix.app.ui.i18n.LirixStrings.message("Уменьшить сдвиг"),
                            tint = if (isDark) LirixThemeTokens.Chalk else LirixThemeTokens.Ink,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // [ + ] step +0.1s
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isDark) LirixThemeTokens.Slate else LirixThemeTokens.Vellum,
                    border = BorderStroke(
                        1.dp,
                        if (isDark) LirixThemeTokens.Hairline else LirixThemeTokens.HairlineLight
                    ),
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { updateOffset(100L) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = com.lirix.app.ui.i18n.LirixStrings.message("Увеличить сдвиг"),
                            tint = if (isDark) LirixThemeTokens.Chalk else LirixThemeTokens.Ink,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Formats offset milliseconds as formatted seconds with sign: "−0,3 с", "+0,1 с", "0,0 с".
 */
internal fun formatOffsetString(offsetMs: Long): String {
    val sec = offsetMs / 1000.0
    val absSec = kotlin.math.abs(sec)
    val sign = if (offsetMs < 0) "−" else if (offsetMs > 0) "+" else ""
    val locale = if (com.lirix.app.ui.i18n.LirixStrings.currentLanguage == com.lirix.app.ui.i18n.InterfaceLanguage.RU)
        Locale.forLanguageTag("ru") else Locale.US
    val formatted = String.format(locale, "%.1f", absSec)
    return com.lirix.app.ui.i18n.LirixStrings.message("{0}{1} с", sign, formatted)
}
