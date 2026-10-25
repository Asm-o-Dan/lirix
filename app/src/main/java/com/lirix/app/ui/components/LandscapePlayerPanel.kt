package com.lirix.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.R
import com.lirix.app.ui.NowPlayingMode
import com.lirix.app.ui.formatMs
import com.lirix.app.ui.theme.LirixThemeTokens

/** Fixed landscape deck. Only the optional full-metadata dialog scrolls, never the panel. */
@Composable
fun LandscapePlayerPanel(
    title: String, artist: String, albumArtUri: String?, sourceLabel: String,
    isPlaying: Boolean, isRemotePlaying: Boolean, selectedMode: NowPlayingMode,
    onSelectMode: (NowPlayingMode) -> Unit, onBack: () -> Unit,
    onConnect: () -> Unit, onPrevious: () -> Unit, onTogglePlaying: () -> Unit,
    onNext: () -> Unit, onShare: () -> Unit,
    overflow: @Composable () -> Unit, seek: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LirixThemeTokens.colors
    var showMetadata by remember(title, artist) { mutableStateOf(false) }
    var showModes by remember { mutableStateOf(false) }
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxHeight().testTag("landscape-player-panel")
        .background(colors.surfacePrimary).border(BorderStroke(1.dp, colors.borderSubtle))
        .padding(horizontal = 12.dp, vertical = 4.dp)) {
        val compact = maxHeight < 300.dp || fontScale > 1.3f
        val showArtist = !compact || fontScale <= 1.3f
        val playSize = if (maxHeight >= 320.dp) 56.dp else 48.dp
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("landscape-back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = colors.textPrimary)
                }
                Text(stringResource(R.string.player_in_source, sourceLabel), modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge, color = colors.accentPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                overflow()
            }
            Row(Modifier.fillMaxWidth().weight(1f).heightIn(min = 48.dp).testTag("landscape-metadata")
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.menu_track_info)) { showMetadata = true },
                verticalAlignment = Alignment.CenterVertically) {
                AlbumArtImage(albumArtUri, size = if (compact) 32.dp else 48.dp,
                    shape = RoundedCornerShape(6.dp), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
                        color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (showArtist) Text(artist, modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            seek()
            Row(Modifier.fillMaxWidth().height(playSize), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onConnect, modifier = Modifier.size(48.dp).testTag("landscape-connect")) {
                    Icon(Icons.Default.DevicesOther, stringResource(R.string.action_play_on_device),
                        tint = if (isRemotePlaying) colors.accentPrimary else colors.textSecondary)
                }
                IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp).testTag("landscape-previous")) {
                    Icon(Icons.Default.SkipPrevious, stringResource(R.string.action_previous), tint = colors.textPrimary)
                }
                FilledIconButton(onClick = onTogglePlaying, modifier = Modifier.size(playSize).testTag("landscape-play"),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.accentPrimary,
                        contentColor = colors.appBackground)) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if (isPlaying) R.string.action_pause else R.string.action_play))
                }
                IconButton(onClick = onNext, modifier = Modifier.size(48.dp).testTag("landscape-next")) {
                    Icon(Icons.Default.SkipNext, stringResource(R.string.action_next), tint = colors.textPrimary)
                }
                IconButton(onClick = onShare, modifier = Modifier.size(48.dp).testTag("landscape-share")) {
                    Icon(Icons.Default.Share, stringResource(R.string.action_share_card), tint = colors.textSecondary)
                }
            }
            if (compact) {
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { showModes = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("landscape-mode"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        Text(stringResource(selectedMode.labelRes), modifier = Modifier.weight(1f).fillMaxWidth().testTag("landscape-mode-label"),
                            style = MaterialTheme.typography.labelLarge)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = showModes, onDismissRequest = { showModes = false }) {
                        NowPlayingMode.entries.forEach { mode ->
                            DropdownMenuItem(text = { Text(stringResource(mode.labelRes)) },
                                modifier = Modifier.semantics { selected = selectedMode == mode; role = Role.Tab },
                                onClick = { showModes = false; onSelectMode(mode) },
                                trailingIcon = { if (selectedMode == mode) Icon(Icons.Default.Check, null) })
                        }
                    }
                }
            } else PlayerModeSelector(selectedMode, onSelectMode)
        }
    }
    if (showMetadata) AlertDialog(onDismissRequest = { showMetadata = false },
        title = { Text(stringResource(R.string.menu_track_info)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, modifier = Modifier.fillMaxWidth().testTag("landscape-full-title"))
            Text(artist, modifier = Modifier.fillMaxWidth().testTag("landscape-full-artist"))
            Text(stringResource(R.string.player_in_source, sourceLabel), modifier = Modifier.fillMaxWidth())
        } }, confirmButton = { TextButton(onClick = { showMetadata = false }) { Text(stringResource(R.string.action_close)) } })
}

/** Raw media times and seek callback stay owned by the existing playback clock. */
@Composable
fun LandscapePlayerSeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    val colors = LirixThemeTokens.colors
    val position = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
    val timeText = formatMs(position)
    val durationText = if (durationMs > 0) formatMs(durationMs) else "--:--"
    val timeStyle = LocalTextStyle.current.merge(MaterialTheme.typography.labelSmall)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Allocate each complete native text layout, rather than allowing glyph-bound shrink wrap.
    // Only the slider takes the remaining width; neither time label loses its measured width.
    val timeWidth = with(density) { measurer.measure(timeText, style = timeStyle).size.width.toDp() }
    val durationWidth = with(density) { measurer.measure(durationText, style = timeStyle).size.width.toDp() }
    Row(Modifier.fillMaxWidth().height(48.dp).testTag("landscape-seek-row"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(timeText, modifier = Modifier.width(timeWidth).testTag("landscape-time"),
            style = timeStyle, color = colors.textSecondary)
        // Expose one accessible seek control on the complete 48 dp interactive container.
        // Material Slider's internal visual/semantics layout is 44 dp; its normal touch
        // handling remains intact, including the minimum interactive area at both edges.
        Box(Modifier.weight(1f).height(48.dp).testTag("landscape-seek")
            .semantics(mergeDescendants = true) {
                contentDescription = "${formatMs(position)} / ${formatMs(durationMs)}"
                progressBarRangeInfo = ProgressBarRangeInfo(position.toFloat(), 0f..durationMs.coerceAtLeast(1L).toFloat())
                if (durationMs <= 0) disabled()
                setProgress { requested ->
                    if (durationMs > 0) {
                        onSeek(requested.coerceIn(0f, durationMs.toFloat()).toLong())
                        true
                    } else false
                }
            }, contentAlignment = Alignment.Center) {
            Slider(value = position.toFloat(), onValueChange = { onSeek(it.toLong()) },
                enabled = durationMs > 0, valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                modifier = Modifier.fillMaxSize().clearAndSetSemantics {},
                colors = SliderDefaults.colors(thumbColor = colors.accentPrimary, activeTrackColor = colors.accentPrimary,
                    inactiveTrackColor = colors.borderSubtle))
        }
        Text(durationText, modifier = Modifier.width(durationWidth).testTag("landscape-duration"),
            style = timeStyle, color = colors.textSecondary)
    }
}
