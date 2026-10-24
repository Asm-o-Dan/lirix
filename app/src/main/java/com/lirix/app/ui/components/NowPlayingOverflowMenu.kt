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
    onTheme: () -> Unit,
    onBlacklist: () -> Unit,
    onLanguage: () -> Unit = {},
    onRomanization: (() -> Unit)? = null
) {
    var expanded by remember(mode, trackKey, landscape) { mutableStateOf(false) }
    val lyrics = trackKey != null && (mode == NowPlayingMode.LYRICS || mode == NowPlayingMode.KARAOKE)
    PlayerOverflowAnchor(onOpen = { expanded = true }) {
        NowPlayingOverflowMenu(expanded, { expanded = false }, isOverlayActive, onToggleOverlay,
            onEditLyricsClick = onEditLyrics.takeIf { lyrics },
            onSyncClick = onSync.takeIf { lyrics },
            onTranslationClick = onTranslations.takeIf { trackKey != null && mode == NowPlayingMode.LYRICS },
            onSaveNotesClick = onSaveNotes.takeIf { trackKey != null && mode == NowPlayingMode.NOTES },
            onTrackInfoClick = onTrackInfo.takeIf { trackKey != null },
            onCalibrateClick = onCalibrate.takeIf { trackKey != null && mode == NowPlayingMode.KARAOKE },
            onTeachModeClick = onTeach.takeIf { trackKey != null && mode != NowPlayingMode.NOTES },
            onRomanizationClick = onRomanization.takeIf { lyrics },
            onThemeSelectClick = onTheme, onOpenLanguageDialog = onLanguage, onOpenBlacklistSettings = onBlacklist)
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
    onThemeSelectClick: () -> Unit = {},
    onOpenLanguageDialog: () -> Unit = {},
    onOpenBlacklistSettings: () -> Unit = {},
    modifier: Modifier = Modifier
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
        onSaveNotesClick?.let { Action(stringResource(R.string.menu_save_notes), Icons.Default.Check, it) }
        onCalibrateClick?.let { Action(stringResource(R.string.menu_calibrate_timing), Icons.Default.Tune, it) }
        onTeachModeClick?.let { Action(stringResource(R.string.menu_teach_mode), Icons.Default.School, it) }
        onRomanizationClick?.let { Action(stringResource(R.string.menu_romanization_reading), Icons.Default.Subtitles, it) }
        onTrackInfoClick?.let { Action(stringResource(R.string.menu_track_info), Icons.Default.Info, it) }
        HorizontalDivider(color = tokens.borderSubtle)
        Action(stringResource(if (isOverlayActive) R.string.menu_floating_overlay_enabled else R.string.menu_floating_overlay), Icons.Default.PictureInPictureAlt, onToggleOverlay)
        Action(stringResource(R.string.menu_theme), Icons.Default.Palette, onThemeSelectClick)
        Action(stringResource(R.string.menu_language), Icons.Default.Language, onOpenLanguageDialog)
        Action(stringResource(R.string.menu_blacklist), Icons.Default.Shield, onOpenBlacklistSettings)
    }
}

@Composable
fun TimingCalibrationDialog(
    currentOffsetMs: Long,
    onOffsetChange: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val tokens = LirixThemeTokens.colors
    var numericOffset by remember { mutableStateOf(currentOffsetMs.toString()) }
    LaunchedEffect(currentOffsetMs) { numericOffset = currentOffsetMs.toString() }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = stringResource(R.string.calibration_title),
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
                Text(
                    text = "${if (currentOffsetMs >= 0) "+$currentOffsetMs" else currentOffsetMs} ms",
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
                            onOffsetChange(bounded)
                        }
                    },
                    enabled = numericOffset.toLongOrNull() != null
                ) { Text(stringResource(R.string.calibration_apply)) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onOffsetChange(currentOffsetMs - 50) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.textPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("-50ms", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { onOffsetChange(currentOffsetMs - 10) },
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
                        onClick = { onOffsetChange(currentOffsetMs + 10) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.textPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("+10ms", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { onOffsetChange(currentOffsetMs + 50) },
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
