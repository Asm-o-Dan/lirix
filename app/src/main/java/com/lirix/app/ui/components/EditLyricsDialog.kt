package com.lirix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.lirix.app.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.lirix.app.feature.lyrics.LrcSyncEngine
import com.lirix.app.ui.theme.FontFeature
import com.lirix.app.ui.theme.GolosTextFontFamily
import com.lirix.app.ui.theme.LirixThemeTokens
import com.lirix.app.ui.theme.LiterataFontFamily

/**
 * Interactive fullscreen modal dialog to view, edit, reformat, and clean song lyrics.
 *
 * Provides smart helper tools:
 * - "Разбить на строки": automates line breaking & ungluing chords via [LrcSyncEngine.sanitizePlainText].
 * - "Удалить аккорды": strips guitar chords, leaving clean singable text.
 * - "Вставить": one-tap paste from system clipboard.
 *
 * Spec: TASK-UI-EDIT-LYRICS-01
 */
@Composable
fun EditLyricsDialog(
    trackTitle: String,
    artist: String,
    initialLyrics: String,
    onDismissRequest: () -> Unit,
    onSaveLyrics: (String) -> Unit
) {
    val tokens = LirixThemeTokens.colors
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    var lyricsDraft by remember(initialLyrics) { mutableStateOf(initialLyrics) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
            }
            onDispose {}
        }

        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val safeBottomPadding = maxOf(navBarBottom, 20.dp)

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
            color = tokens.appBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = safeBottomPadding)
            ) {
                // 1. Top Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDismissRequest()
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.edit_lyrics_close_cd),
                            tint = tokens.textSecondary
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.edit_lyrics_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = GolosTextFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            ),
                            color = tokens.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${artist.ifBlank { stringResource(R.string.edit_lyrics_fallback_artist) }} — ${trackTitle.ifBlank { stringResource(R.string.edit_lyrics_fallback_track) }}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = GolosTextFontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp
                            ),
                            color = tokens.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSaveLyrics(lyricsDraft.trim())
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.accentPrimary,
                            contentColor = tokens.appBackground
                        ),
                        shape = RoundedCornerShape(tokens.pillCornerRadius),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.edit_lyrics_done),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = GolosTextFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Format Toolbar (Smart Assistants)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Smart Line Break & Chord Unsticking
                    Surface(
                        color = tokens.surfaceElevated,
                        shape = RoundedCornerShape(tokens.pillCornerRadius),
                        border = androidx.compose.foundation.BorderStroke(1.dp, tokens.borderSubtle),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            lyricsDraft = LrcSyncEngine.sanitizePlainText(lyricsDraft)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = tokens.accentPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.edit_lyrics_tool_split_lines),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = GolosTextFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                color = tokens.textPrimary
                            )
                        }
                    }

                    // Remove Chords
                    Surface(
                        color = tokens.surfaceElevated,
                        shape = RoundedCornerShape(tokens.pillCornerRadius),
                        border = androidx.compose.foundation.BorderStroke(1.dp, tokens.borderSubtle),
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val chordPattern = Regex("""\b[A-H][#b]?(?:m|maj|min|dim|aug|sus|add|\d)*\b""")
                            val cleaned = lyricsDraft.lines().map { line ->
                                line.replace(chordPattern, "").trim()
                            }.filter { it.isNotBlank() }.joinToString("\n")
                            lyricsDraft = cleaned
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = tokens.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.edit_lyrics_tool_remove_chords),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = GolosTextFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                color = tokens.textSecondary
                            )
                        }
                    }

                    // Paste from Clipboard
                    Surface(
                        color = tokens.surfaceElevated,
                        shape = RoundedCornerShape(tokens.pillCornerRadius),
                        border = androidx.compose.foundation.BorderStroke(1.dp, tokens.borderSubtle),
                        modifier = Modifier.clickable {
                            val clipText = clipboard.getText()?.text.orEmpty()
                            if (clipText.isNotBlank()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                lyricsDraft = if (lyricsDraft.isBlank()) clipText else "$lyricsDraft\n$clipText"
                            }
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = null,
                                tint = tokens.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.edit_lyrics_tool_paste),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = GolosTextFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                color = tokens.textSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Multi-line Lyric Text Field
                OutlinedTextField(
                    value = lyricsDraft,
                    onValueChange = { lyricsDraft = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    textStyle = TextStyle(
                        fontFamily = LiterataFontFamily,
                        fontSize = 15.sp,
                        lineHeight = 23.sp,
                        color = tokens.textPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = tokens.surfaceCard,
                        unfocusedContainerColor = tokens.surfaceCard,
                        focusedBorderColor = tokens.accentPrimary,
                        unfocusedBorderColor = tokens.borderSubtle,
                        cursorColor = tokens.accentPrimary
                    ),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.edit_lyrics_placeholder),
                            style = TextStyle(
                                fontFamily = GolosTextFontFamily,
                                fontSize = 14.sp,
                                color = tokens.textMuted
                            )
                        )
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 4. Footer stats badge
                val nonBlankLines = remember(lyricsDraft) { lyricsDraft.lines().count { it.isNotBlank() } }
                val charCount = lyricsDraft.length
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.edit_lyrics_lines_count, nonBlankLines),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = GolosTextFontFamily,
                            fontFeatureSettings = FontFeature.tabularFigures(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = tokens.accentPrimary
                    )
                    Text(
                        text = stringResource(R.string.edit_lyrics_chars_count, charCount),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = GolosTextFontFamily,
                            fontFeatureSettings = FontFeature.tabularFigures(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = tokens.textSecondary
                    )
                }
            }
        }
    }
}
