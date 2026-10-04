package com.eventengine.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eventengine.app.storage.AppDatabase
import com.eventengine.app.storage.TrackEntity
import com.eventengine.app.ui.components.AlbumArtThumbnail
import com.eventengine.app.ui.theme.AppColors

enum class LibraryTabFilter(val title: String) {
    ALL("Все"),
    FAVORITES("Любимые"),
    WITH_LYRICS("С текстом"),
    WITH_NOTES("С заметками")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onTrackSelected: (TrackEntity) -> Unit = {}
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val haptic = LocalHapticFeedback.current

    val recentTracks by db.musicDao().observeRecentTracks().collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedFilter by remember { mutableStateOf(LibraryTabFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredTracks = remember(recentTracks, selectedFilter, searchQuery) {
        var list = when (selectedFilter) {
            LibraryTabFilter.ALL -> recentTracks
            LibraryTabFilter.FAVORITES -> recentTracks.filter { it.isFavorite }
            LibraryTabFilter.WITH_LYRICS -> recentTracks.filter { !it.plainLyrics.isNullOrBlank() || !it.syncedLyrics.isNullOrBlank() }
            LibraryTabFilter.WITH_NOTES -> recentTracks.filter { it.userNotes.isNotBlank() }
        }

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                    it.artist.lowercase().contains(q)
            }
        }
        list
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.AmoledBlack)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Screen Header: Bold Editorial Style
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(AppColors.HyperViolet)
                    )
                    Text(
                        text = "ФОНОТЕКА // АРХИВ",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        ),
                        color = AppColors.HyperViolet
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Медиатека",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    ),
                    color = AppColors.TextPrimary
                )
            }

            Surface(
                color = AppColors.SurfaceLevel2,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.CyberCyan.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "[ ${filteredTracks.size} ТРЕКОВ ]",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = AppColors.CyberCyan,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "Поиск по медиатеке...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextTertiary
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = AppColors.HyperViolet,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = AppColors.SurfaceLevel1,
                unfocusedContainerColor = AppColors.SurfaceLevel1,
                focusedBorderColor = AppColors.HyperViolet,
                unfocusedBorderColor = AppColors.BorderSubtle,
                focusedTextColor = AppColors.TextPrimary,
                unfocusedTextColor = AppColors.TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Fluid Filter Chips Row with Dynamic Counters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LibraryTabFilter.entries.forEach { filter ->
                val isSelected = selectedFilter == filter
                val count = when (filter) {
                    LibraryTabFilter.ALL -> recentTracks.size
                    LibraryTabFilter.FAVORITES -> recentTracks.count { it.isFavorite }
                    LibraryTabFilter.WITH_LYRICS -> recentTracks.count { !it.plainLyrics.isNullOrBlank() || !it.syncedLyrics.isNullOrBlank() }
                    LibraryTabFilter.WITH_NOTES -> recentTracks.count { it.userNotes.isNotBlank() }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) {
                                Brush.horizontalGradient(
                                    listOf(AppColors.HyperViolet, AppColors.HyperVioletDark)
                                )
                            } else {
                                androidx.compose.ui.graphics.SolidColor(AppColors.SurfaceLevel1)
                            }
                        )
                        .border(
                            1.dp,
                            if (isSelected) AppColors.HyperViolet else AppColors.BorderSubtle,
                            RoundedCornerShape(20.dp)
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedFilter = filter
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = filter.title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            ),
                            color = if (isSelected) AppColors.AmoledBlack else AppColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) AppColors.AmoledBlack.copy(alpha = 0.25f) else AppColors.SurfaceLevel2
                        ) {
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                ),
                                color = if (isSelected) AppColors.AmoledBlack else AppColors.CyberCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (filteredTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = null,
                        tint = AppColors.TextTertiary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Нет треков в данной категории",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredTracks, key = { it.trackKey }) { track ->
                    LibraryTrackCard(
                        track = track,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTrackSelected(track)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryTrackCard(
    track: TrackEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.SurfaceLevel1)
            .border(
                1.dp,
                if (track.isFavorite) AppColors.AmberGold.copy(alpha = 0.5f) else AppColors.BorderSubtle,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // High-Fidelity Album Art Thumbnail
        AlbumArtThumbnail(
            artUri = track.albumArtUri,
            size = 50.dp,
            shape = RoundedCornerShape(12.dp),
            borderColor = if (track.isFavorite) AppColors.AmberGold.copy(alpha = 0.6f) else AppColors.BorderSubtle,
            contentDescription = "${track.title} library artwork"
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist.ifBlank { "Неизвестный исполнитель" },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = AppColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!track.syncedLyrics.isNullOrBlank()) {
                    BadgeChip(text = "LRC", color = AppColors.HyperViolet)
                }
                if (!track.plainLyrics.isNullOrBlank()) {
                    BadgeChip(text = "Текст", color = AppColors.CyberCyan)
                }
                if (track.userNotes.isNotBlank()) {
                    BadgeChip(text = "Заметка", color = AppColors.ElectricMint)
                }
                if (track.isFavorite) {
                    BadgeChip(text = "★ Любимое", color = AppColors.AmberGold)
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(5.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            ),
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
