package com.eventengine.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eventengine.app.storage.AppDatabase
import com.eventengine.app.storage.TrackEntity
import com.eventengine.app.ui.theme.AppColors

/**
 * AMOLED modal dialog allowing the user to attach shared lyrics text
 * to the currently playing track or select from recent tracks.
 *
 * Spec: TASK-LYR-04-B / .sdd/tasks/TASK-LYR-04-B.md
 */
@Composable
fun ShareLyricsAttachDialog(
    lyricsText: String,
    onDismissRequest: () -> Unit,
    onTrackSelected: (TrackEntity) -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val recentTracks by db.musicDao().observeRecentTracks().collectAsStateWithLifecycle(initialValue = emptyList())
    val topTrack = recentTracks.firstOrNull()
    val otherTracks = if (recentTracks.size > 1) recentTracks.drop(1).take(4) else emptyList()

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = AppColors.SurfaceLevel2,
            border = BorderStroke(1.dp, AppColors.BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Привязать текст из буфера",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = AppColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Текстовое превью
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AppColors.SurfaceLevel1,
                    border = BorderStroke(1.dp, AppColors.BorderSubtle.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = lyricsText.lines().take(4).joinToString("\n").ifBlank { "Пустой текст" },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        ),
                        color = AppColors.TextSecondary,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Быстрый выбор текущего/последнего трека
                if (topTrack != null) {
                    Text(
                        text = "Текущий трек:",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.CyberCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AppColors.SurfaceLevel3,
                        border = BorderStroke(1.dp, AppColors.HyperViolet.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackSelected(topTrack) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = AppColors.HyperViolet,
                                modifier = Modifier.padding(end = 10.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = topTrack.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = AppColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = topTrack.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onTrackSelected(topTrack) },
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.ElectricMint),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Привязать", color = AppColors.AmoledBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Список других недавних треков
                if (otherTracks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Или выбрать из недавних:",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextTertiary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(otherTracks) { track ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AppColors.SurfaceLevel1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onTrackSelected(track) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = AppColors.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = track.artist,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AppColors.TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Отмена", color = AppColors.TextSecondary)
                    }
                }
            }
        }
    }
}
