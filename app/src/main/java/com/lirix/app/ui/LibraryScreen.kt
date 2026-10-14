package com.lirix.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.ThemePreferencesRepository
import com.lirix.app.storage.TrackEntity
import com.lirix.app.ui.components.AlbumArtThumbnail
import com.lirix.app.ui.components.ImportRuleDialog
import com.lirix.app.ui.components.ThemeSelectionDialog
import com.lirix.app.ui.theme.AppColors
import com.lirix.app.ui.theme.LirixThemeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sub-tabs for unified Library screen: [ Избранное ] | [ История ]
 */
enum class LibrarySubTab(
    val title: String,
    val icon: ImageVector
) {
    FAVORITES("Избранное", Icons.Default.Favorite),
    HISTORY("История", Icons.Default.History)
}

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
    val lirixColors = LirixThemeTokens.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val haptic = LocalHapticFeedback.current

    val recentTracks by db.musicDao().observeRecentTracks().collectAsStateWithLifecycle(initialValue = emptyList())
    var activeSubTab by rememberSaveable { mutableStateOf(LibrarySubTab.FAVORITES) }
    var selectedFilter by rememberSaveable { mutableStateOf(LibraryTabFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    val themeRepo = remember { ThemePreferencesRepository.getInstance(context) }
    val currentTheme by themeRepo.selectedTheme.collectAsStateWithLifecycle()

    // Filtered tracks for Favorites/Collection view
    val filteredLibraryTracks = remember(recentTracks, selectedFilter, searchQuery) {
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
                    it.artist.lowercase().contains(q) ||
                    it.album.lowercase().contains(q) ||
                    it.plainLyrics?.lowercase()?.contains(q) == true ||
                    it.syncedLyrics?.lowercase()?.contains(q) == true ||
                    it.userNotes.lowercase().contains(q)
            }
        }
        list
    }

    // Filtered tracks for History/Timeline view
    val filteredHistoryTracks = remember(recentTracks, searchQuery) {
        if (searchQuery.isBlank()) {
            recentTracks
        } else {
            val q = searchQuery.trim().lowercase()
            recentTracks.filter {
                it.title.lowercase().contains(q) ||
                    it.artist.lowercase().contains(q) ||
                    it.album.lowercase().contains(q) ||
                    it.plainLyrics?.lowercase()?.contains(q) == true ||
                    it.syncedLyrics?.lowercase()?.contains(q) == true ||
                    it.userNotes.lowercase().contains(q)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(lirixColors.appBackground)
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
                            .background(if (activeSubTab == LibrarySubTab.FAVORITES) lirixColors.accentPrimary else lirixColors.accentSecondary)
                    )
                    Text(
                        text = if (activeSubTab == LibrarySubTab.FAVORITES) "ФОНОТЕКА // АРХИВ" else "ХРОНИКА // ТАЙМЛАЙН",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        ),
                        color = if (activeSubTab == LibrarySubTab.FAVORITES) lirixColors.accentPrimary else lirixColors.accentSecondary
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
                    color = lirixColors.textPrimary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = { showThemeDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Тема оформления",
                        tint = lirixColors.accentPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { showImportDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Импорт правила парсера",
                        tint = lirixColors.accentSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Surface(
                    color = lirixColors.surfacePrimary,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (activeSubTab == LibrarySubTab.FAVORITES) lirixColors.accentPrimary.copy(alpha = 0.4f) else lirixColors.accentSecondary.copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        text = if (activeSubTab == LibrarySubTab.FAVORITES) "[ ${filteredLibraryTracks.size} ТРЕКОВ ]" else "[ ${recentTracks.size} В КЭШЕ ]",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = if (activeSubTab == LibrarySubTab.FAVORITES) lirixColors.accentPrimary else lirixColors.accentSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Sub-Tabs Segmented Control ([ Избранное ] | [ История ])
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(lirixColors.pillCornerRadius))
                .background(lirixColors.surfacePrimary)
                .border(1.dp, lirixColors.borderSubtle, RoundedCornerShape(lirixColors.pillCornerRadius))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LibrarySubTab.entries.forEach { subTab ->
                val isSelected = activeSubTab == subTab
                val tabActiveTextColor = if (lirixColors.isDark) Color.Black else Color.White
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(lirixColors.cardCornerRadius))
                        .background(
                            if (isSelected) {
                                Brush.horizontalGradient(
                                    if (subTab == LibrarySubTab.FAVORITES) {
                                        listOf(lirixColors.accentPrimary, lirixColors.accentPrimary.copy(alpha = 0.8f))
                                    } else {
                                        listOf(lirixColors.accentSecondary, lirixColors.accentSecondary.copy(alpha = 0.8f))
                                    }
                                )
                            } else {
                                androidx.compose.ui.graphics.SolidColor(Color.Transparent)
                            }
                        )
                        .clickable {
                            if (activeSubTab != subTab) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                activeSubTab = subTab
                            }
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = subTab.icon,
                            contentDescription = null,
                            tint = if (isSelected) tabActiveTextColor else lirixColors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = subTab.title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                            ),
                            color = if (isSelected) tabActiveTextColor else lirixColors.textSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = if (activeSubTab == LibrarySubTab.FAVORITES) "Поиск по медиатеке..." else "Поиск по архиву истории...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = lirixColors.textMuted
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = if (activeSubTab == LibrarySubTab.FAVORITES) lirixColors.accentPrimary else lirixColors.accentSecondary,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = lirixColors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(lirixColors.cardCornerRadius),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = lirixColors.surfacePrimary,
                unfocusedContainerColor = lirixColors.surfacePrimary,
                focusedBorderColor = if (activeSubTab == LibrarySubTab.FAVORITES) lirixColors.accentPrimary else lirixColors.accentSecondary,
                unfocusedBorderColor = lirixColors.borderSubtle,
                focusedTextColor = lirixColors.textPrimary,
                unfocusedTextColor = lirixColors.textPrimary
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Crossfade between Sub-tabs ([ Избранное ] vs [ История ])
        Crossfade(targetState = activeSubTab, animationSpec = tween(180), label = "SubTabSwitch") { subTab ->
            when (subTab) {
                LibrarySubTab.FAVORITES -> {
                    Column(modifier = Modifier.fillMaxSize()) {
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
                                val chipActiveTextColor = if (lirixColors.isDark) Color.Black else Color.White

                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 44.dp)
                                        .clip(RoundedCornerShape(lirixColors.pillCornerRadius))
                                        .background(
                                            if (isSelected) {
                                                Brush.horizontalGradient(
                                                    listOf(lirixColors.accentPrimary, lirixColors.accentPrimary.copy(alpha = 0.8f))
                                                )
                                            } else {
                                                androidx.compose.ui.graphics.SolidColor(lirixColors.surfacePrimary)
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) lirixColors.accentPrimary else lirixColors.borderSubtle,
                                            RoundedCornerShape(lirixColors.pillCornerRadius)
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
                                            color = if (isSelected) chipActiveTextColor else lirixColors.textSecondary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isSelected) chipActiveTextColor.copy(alpha = 0.25f) else lirixColors.surfaceCard
                                        ) {
                                            Text(
                                                text = "$count",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                ),
                                                color = if (isSelected) chipActiveTextColor else lirixColors.accentSecondary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (filteredLibraryTracks.isEmpty()) {
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
                                        tint = lirixColors.textMuted,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Нет треков в данной категории",
                                        color = lirixColors.textSecondary,
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
                                items(filteredLibraryTracks, key = { it.trackKey }) { track ->
                                    LibraryTrackCard(
                                        track = track,
                                        searchQuery = searchQuery,
                                        onFavoriteToggle = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            scope.launch(Dispatchers.IO) {
                                                db.musicDao().setFavorite(track.trackKey, !track.isFavorite)
                                            }
                                        },
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

                LibrarySubTab.HISTORY -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // High-Contrast Audio DNA Strip
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(lirixColors.cardCornerRadius))
                                .background(lirixColors.surfacePrimary)
                                .border(
                                    1.dp,
                                    Brush.horizontalGradient(
                                        listOf(
                                            lirixColors.accentPrimary.copy(alpha = 0.35f),
                                            lirixColors.accentSecondary.copy(alpha = 0.35f)
                                        )
                                    ),
                                    RoundedCornerShape(lirixColors.cardCornerRadius)
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = lirixColors.accentPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${recentTracks.size} ТРЕКОВ В КЭШЕ",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = lirixColors.textPrimary
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "⚡",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${recentTracks.sumOf { it.playCount }} ПЛЕЕВ",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    ),
                                    color = lirixColors.accentSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (filteredHistoryTracks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = lirixColors.textMuted,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = if (searchQuery.isBlank()) "История прослушиваний пуста" else "Ничего не найдено по запросу",
                                        color = lirixColors.textSecondary,
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
                                items(filteredHistoryTracks, key = { it.trackKey }) { track ->
                                    UnifiedHistoryTrackCard(
                                        track = track,
                                        searchQuery = searchQuery,
                                        onFavoriteClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            scope.launch(Dispatchers.IO) {
                                                db.musicDao().setFavorite(track.trackKey, !track.isFavorite)
                                            }
                                        },
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
            }
        }
    }

    if (showImportDialog) {
        ImportRuleDialog(
            onDismiss = { showImportDialog = false }
        )
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = currentTheme,
            onThemeSelected = { newTheme ->
                themeRepo.setTheme(newTheme)
            },
            onDismissRequest = { showThemeDialog = false }
        )
    }
}

@Composable
private fun LibraryTrackCard(
    track: TrackEntity,
    searchQuery: String = "",
    onFavoriteToggle: () -> Unit,
    onClick: () -> Unit
) {
    val lirixColors = LirixThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(lirixColors.cardCornerRadius))
            .background(lirixColors.surfaceCard)
            .border(
                1.dp,
                if (track.isFavorite) AppColors.AmberGold.copy(alpha = 0.5f) else lirixColors.borderSubtle,
                RoundedCornerShape(lirixColors.cardCornerRadius)
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
            borderColor = if (track.isFavorite) AppColors.AmberGold.copy(alpha = 0.6f) else lirixColors.borderSubtle,
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
                color = lirixColors.textPrimary,
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
                color = lirixColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!track.syncedLyrics.isNullOrBlank()) {
                    BadgeChip(text = "LRC", color = lirixColors.accentPrimary)
                }
                if (!track.plainLyrics.isNullOrBlank()) {
                    BadgeChip(text = "Текст", color = lirixColors.accentSecondary)
                }
                if (track.userNotes.isNotBlank()) {
                    BadgeChip(text = "Заметка", color = AppColors.ElectricMint)
                }
                if (track.isFavorite) {
                    BadgeChip(text = "★ Любимое", color = AppColors.AmberGold)
                }
            }

            if (searchQuery.isNotBlank()) {
                val lyricsSnippet = extractSearchSnippet(track.plainLyrics ?: track.syncedLyrics, searchQuery)
                val notesSnippet = extractSearchSnippet(track.userNotes, searchQuery)
                if (lyricsSnippet != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🎵 $lyricsSnippet",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        ),
                        color = lirixColors.accentSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (notesSnippet != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "📝 $notesSnippet",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        ),
                        color = AppColors.ElectricMint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Quick favorite toggle (>=48dp touch target)
        IconButton(
            onClick = onFavoriteToggle,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Избранное",
                tint = if (track.isFavorite) AppColors.ExpenseRed else lirixColors.textSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun UnifiedHistoryTrackCard(
    track: TrackEntity,
    searchQuery: String = "",
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit
) {
    val lirixColors = LirixThemeTokens.colors
    val dateFormat = remember { SimpleDateFormat("HH:mm • dd MMM", Locale.getDefault()) }
    val formattedTime = remember(track.lastPlayedAt) { dateFormat.format(Date(track.lastPlayedAt)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(lirixColors.cardCornerRadius))
            .background(lirixColors.surfaceCard)
            .border(
                1.dp,
                if (track.isFavorite) AppColors.AmberGold.copy(alpha = 0.5f) else lirixColors.borderSubtle,
                RoundedCornerShape(lirixColors.cardCornerRadius)
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
            borderColor = if (track.isFavorite) AppColors.AmberGold.copy(alpha = 0.6f) else lirixColors.borderSubtle,
            contentDescription = "${track.title} artwork"
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = lirixColors.textPrimary,
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
                color = lirixColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Play count pill
                Surface(
                    color = lirixColors.accentSecondary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(5.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, lirixColors.accentSecondary.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "⚡ x${track.playCount}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        ),
                        color = lirixColors.accentSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "[ $formattedTime ]",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = lirixColors.textSecondary
                    )
                )
            }

            if (searchQuery.isNotBlank()) {
                val lyricsSnippet = extractSearchSnippet(track.plainLyrics ?: track.syncedLyrics, searchQuery)
                val notesSnippet = extractSearchSnippet(track.userNotes, searchQuery)
                if (lyricsSnippet != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🎵 $lyricsSnippet",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        ),
                        color = lirixColors.accentSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (notesSnippet != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "📝 $notesSnippet",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        ),
                        color = AppColors.ElectricMint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Favorite Button (>=48dp touch target)
        IconButton(
            onClick = onFavoriteClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (track.isFavorite) AppColors.ExpenseRed else lirixColors.textSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Extracts a compact surrounding text snippet around the search query for contextual preview.
 */
private fun extractSearchSnippet(text: String?, query: String): String? {
    if (text.isNullOrBlank() || query.isBlank()) return null
    val idx = text.indexOf(query, ignoreCase = true)
    if (idx < 0) return null
    val start = (idx - 16).coerceAtLeast(0)
    val end = (idx + query.length + 24).coerceAtMost(text.length)
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < text.length) "…" else ""
    return prefix + text.substring(start, end).replace('\n', ' ').trim() + suffix
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
                fontFamily = FontFamily.Monospace
            ),
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
