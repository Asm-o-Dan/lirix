package com.lirix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.R
import com.lirix.app.ui.theme.AppColors
import com.lirix.app.ui.theme.LirixThemeTokens
import com.lirix.app.ui.NowPlayingMode

/** One rendered policy for production and UI tests; switching context discards an open popup. */
@Composable
fun PlayerContextOverflow(
    mode: NowPlayingMode,
    trackKey: String?,
    landscape: Boolean,
    isOverlayActive: Boolean,
    onToggleOverlay: () -> Unit,
    onEditLyrics: () -> Unit,
    onSync: () -> Unit,
    onTranslations: () -> Unit,
    onSaveNotes: () -> Unit,
    onCalibrate: () -> Unit,
    onTeach: (() -> Unit)?,
    onTrackInfo: (() -> Unit)?,
    onRomanization: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    /** Non-null only while the shown words / chords come from an automatic source. */
    onWrongLyrics: (() -> Unit)? = null,
    onWrongChords: (() -> Unit)? = null,
    /** The text area already shows its own edit / studio button; the menu does not repeat it. */
    inlineEditVisible: Boolean = false,
    inlineStudioVisible: Boolean = false
) {
    var expanded by remember(mode, trackKey, landscape) { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = onSettings ?: { com.lirix.app.ui.settings.SettingsActivity.open(context) }
    val lyrics = trackKey != null && (mode == NowPlayingMode.LYRICS || mode == NowPlayingMode.KARAOKE)
    PlayerOverflowAnchor(onOpen = { expanded = true }) {
        NowPlayingOverflowMenu(expanded, { expanded = false }, isOverlayActive, onToggleOverlay,
            onEditLyricsClick = onEditLyrics.takeIf { lyrics && !inlineEditVisible },
            onSyncClick = onSync.takeIf { lyrics && !inlineStudioVisible },
            onTranslationClick = onTranslations.takeIf { trackKey != null && mode == NowPlayingMode.LYRICS },
            onTrackInfoClick = onTrackInfo.takeIf { trackKey != null },
            onCalibrateClick = onCalibrate.takeIf { lyrics },
            onTeachModeClick = onTeach.takeIf { trackKey != null && mode != NowPlayingMode.NOTES },
            onRomanizationClick = onRomanization.takeIf { lyrics },
            onWrongLyricsClick = onWrongLyrics.takeIf { lyrics },
            onWrongChordsClick = onWrongChords.takeIf { trackKey != null && mode == NowPlayingMode.CHORDS },
            onSettingsClick = settings)
    }
}

/** The menu and trigger share this exact anchor, independent of the deck bounds. */
@Composable
fun PlayerOverflowAnchor(onOpen: () -> Unit, menu: @Composable () -> Unit) {
    Box {
        IconButton(onClick = onOpen, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu_overflow), tint = LirixThemeTokens.colors.textSecondary)
        }
        menu()
    }
}

@Composable
fun NowPlayingOverflowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    isOverlayActive: Boolean = false,
    onToggleOverlay: () -> Unit = {},
    onEditLyricsClick: (() -> Unit)? = null,
    onSyncClick: (() -> Unit)? = null,
    onTranslationClick: (() -> Unit)? = null,
    onSaveNotesClick: (() -> Unit)? = null,
    onTrackInfoClick: (() -> Unit)? = null,
    onCalibrateClick: (() -> Unit)? = null,
    onTeachModeClick: (() -> Unit)? = null,
    onRomanizationClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    onWrongLyricsClick: (() -> Unit)? = null,
    onWrongChordsClick: (() -> Unit)? = null
) {
    val tokens = LirixThemeTokens.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(max = 320.dp)
            .background(tokens.surfaceElevated, RoundedCornerShape(16.dp))
            .border(1.dp, tokens.borderSubtle, RoundedCornerShape(16.dp))
    ) {
        @Composable
        fun Action(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, callback: () -> Unit) {
            DropdownMenuItem(
                text = { Text(label, color = tokens.textPrimary, style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(icon, null, tint = tokens.accentPrimary) },
                onClick = { onDismissRequest(); callback() },
                modifier = Modifier.heightIn(min = 48.dp)
            )
        }
        onEditLyricsClick?.let { Action(stringResource(R.string.menu_edit_lyrics), Icons.Default.Edit, it) }
        onSyncClick?.let { Action(stringResource(R.string.menu_sync_studio), Icons.Default.GraphicEq, it) }
        onTranslationClick?.let { Action(stringResource(R.string.menu_translations), Icons.Default.Translate, it) }
        onCalibrateClick?.let { Action(stringResource(R.string.menu_text_settings), Icons.Default.Tune, it) }
        onWrongLyricsClick?.let { Action(stringResource(R.string.menu_wrong_lyrics), Icons.Default.Autorenew, it) }
        onWrongChordsClick?.let { Action(stringResource(R.string.menu_wrong_chords), Icons.Default.Autorenew, it) }
        onTeachModeClick?.let { Action(stringResource(R.string.menu_teach_mode), Icons.Default.School, it) }
        onRomanizationClick?.let { Action(stringResource(R.string.menu_romanization_reading), Icons.Default.Subtitles, it) }
        onTrackInfoClick?.let { Action(stringResource(R.string.menu_track_info), Icons.Default.Info, it) }
        HorizontalDivider(color = tokens.borderSubtle)
        Action(stringResource(if (isOverlayActive) R.string.menu_floating_overlay_enabled else R.string.menu_floating_overlay), Icons.Default.PictureInPictureAlt, onToggleOverlay)
        Action(stringResource(R.string.settings_title), Icons.Default.Settings, onSettingsClick)
    }
}

@Composable
fun TimingCalibrationDialog(
    currentOffsetMs: Long,
    onOffsetChange: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onDismissRequest: () -> Unit,
    textScale: Float? = null,
    onTextScaleChange: (Float) -> Unit = {},
    showTiming: Boolean = true
) {
    val tokens = LirixThemeTokens.colors
    // Shown and typed as lyrics delay: minus = text earlier. Storage keeps the raw offset.
    val currentDelayMs = lyricsDelayMs(currentOffsetMs)
    val onDelayChange: (Long) -> Unit = { delay -> onOffsetChange(offsetForLyricsDelay(delay)) }
    var numericOffset by remember { mutableStateOf(currentDelayMs.toString()) }
    LaunchedEffect(currentDelayMs) { numericOffset = currentDelayMs.toString() }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = stringResource(if (textScale == null) R.string.calibration_title else R.string.menu_text_settings),
                style = MaterialTheme.typography.titleMedium,
                color = tokens.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (textScale != null) {
                    TextSizeControls(textScale, onTextScaleChange)
                    Spacer(Modifier.height(16.dp))
                }
                if (showTiming) {
                    Text(
                        text = stringResource(R.string.timing_delay_value, formatLyricsDelay(currentDelayMs)),
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (currentOffsetMs == 0L) tokens.accentSecondary else AppColors.AmberGold,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.calibration_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    TimingCorrectionControls(currentOffsetMs, onOffsetChange, {})
                    OutlinedTextField(
                        value = numericOffset,
                        onValueChange = { numericOffset = it },
                        label = { Text(stringResource(R.string.calibration_offset_label)) },
                        isError = numericOffset.toLongOrNull() == null,
                        supportingText = {
                            if (numericOffset.toLongOrNull() == null) Text(stringResource(R.string.calibration_offset_error))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        onClick = {
                            numericOffset.toLongOrNull()?.let {
                                val bounded = boundedSyncOffset(it)
                                numericOffset = bounded.toString()
                                onDelayChange(bounded)
                            }
                        },
                        enabled = numericOffset.toLongOrNull() != null
                    ) { Text(stringResource(R.string.calibration_apply)) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onDelayChange(currentDelayMs - 50) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.textPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("-50ms", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { onDelayChange(currentDelayMs - 10) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.textPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("-10ms", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onDelayChange(currentDelayMs + 10) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.textPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("+10ms", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { onDelayChange(currentDelayMs + 50) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.textPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("+50ms", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = onResetOffset,
                        colors = ButtonDefaults.textButtonColors(contentColor = AppColors.ExpenseRed)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.calibration_reset))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
                colors = ButtonDefaults.textButtonColors(contentColor = tokens.accentPrimary)
            ) {
                Text(stringResource(R.string.action_done), fontWeight = FontWeight.Bold)
            }
        },
        containerColor = tokens.surfaceElevated,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(1.dp, tokens.borderSubtle, RoundedCornerShape(20.dp))
    )
}
