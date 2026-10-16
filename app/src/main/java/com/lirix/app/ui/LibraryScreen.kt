package com.lirix.app.ui

import android.content.Context
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.TrackEntity
import com.lirix.app.ui.components.AlbumArtImage
import com.lirix.app.ui.components.TrackActionBottomSheet
import com.lirix.app.ui.theme.FontFeature
import com.lirix.app.ui.theme.GolosTextFontFamily
import com.lirix.app.ui.theme.LirixThemeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Filter chips for Media Library.
 */
enum class LibraryFilterChip(val title: String) {
    ALL("Все"),
    FAVORITES("Избранное"),
    KARAOKE("Караоке"),
    CHORDS("Аккорды"),
    OFFLINE("Офлайн")
}

/**
 * Segment modes: Tracks vs Artists.
 */
enum class LibrarySegmentMode(val title: String) {
    TRACKS("Треки"),
    ARTISTS("Артисты")
}

/**
 * Redesigned LibraryScreen conforming to SPEC-UI-MOBILE-2.0 / TASK-UI-MOB-04:
 *
 * Requirements:
 * 1. Stable AccentFallback (#7FA6F0 dark / #29549E light).
 * 2. Gesture separation: 0..28dp passed freely to system Back (prohibition of systemGestureExclusion on list!).
 *    Swipe right (>28dp, scaledTouchSlop) marks favorite.
 *    Swipe left ("Игнорировать") hides track with Snackbar + 'Отменить'.
 * 3. Accessible alternatives: Long-press opens TrackActionBottomSheet; TalkBack customActions.
 * 4. Unified adaptive Snackbar timeout (6-8s via AccessibilityManager).
 * 5. Track row (64dp) with 44x44 cover, title (15sp SemiBold), artist/album (13sp Mist), playcount, badges.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onTrackSelected: (TrackEntity) -> Unit = {},
    onOpenBlacklistSettings: () -> Unit = {}
) {
    val lirixColors = LirixThemeTokens.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Stable AccentFallback
    val stableAccent = if (lirixColors.isDark) LirixThemeTokens.AccentFallbackDark else LirixThemeTokens.AccentFallbackLight

    val recentTracks by db.musicDao().observeRecentTracks().collectAsStateWithLifecycle(initialValue = emptyList())
    val hiddenTrackKeys = remember { mutableStateListOf<String>() }

    var selectedFilter by rememberSaveable { mutableStateOf(LibraryFilterChip.ALL) }
    var selectedSegment by rememberSaveable { mutableStateOf(LibrarySegmentMode.TRACKS) }
    var searchQuery by remember { mutableStateOf("") }
    var activeBottomSheetTrack by remember { mutableStateOf<TrackEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Filtered track list
    val visibleTracks = remember(recentTracks, hiddenTrackKeys) {
        recentTracks.filter { it.trackKey !in hiddenTrackKeys }
    }

    val filteredTracks = remember(visibleTracks, selectedFilter, searchQuery) {
        var list = when (selectedFilter) {
            LibraryFilterChip.ALL -> visibleTracks
            LibraryFilterChip.FAVORITES -> visibleTracks.filter { it.isFavorite }
            LibraryFilterChip.KARAOKE -> visibleTracks.filter { !it.syncedLyrics.isNullOrBlank() }
            LibraryFilterChip.CHORDS -> visibleTracks.filter { !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
            LibraryFilterChip.OFFLINE -> visibleTracks
        }

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                    it.artist.lowercase().contains(q) ||
                    it.album.lowercase().contains(q) ||
                    it.plainLyrics?.lowercase()?.contains(q) == true ||
                    it.syncedLyrics?.lowercase()?.contains(q) == true
            }
        }
        list
    }

    // Unique artists count
    val uniqueArtists = remember(visibleTracks) {
        visibleTracks.map { it.artist.ifBlank { "Неизвестный исполнитель" } }.distinct()
    }

    // Adaptive Snackbar timeout (6-8s) via AccessibilityManager
    val accessibilityManager = remember {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
    val recommendedTimeoutMs = remember(accessibilityManager) {
        accessibilityManager?.getRecommendedTimeoutMillis(7000, AccessibilityManager.FLAG_CONTENT_CONTROLS) ?: 7000
    }

    // Function to hide track with undoable snackbar
    val hideTrack: (TrackEntity) -> Unit = { track ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        hiddenTrackKeys.add(track.trackKey)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Трек «${track.title}» скрыт из медиатеки",
                actionLabel = "Отменить",
                duration = SnackbarDuration.Indefinite
            )
            if (result == SnackbarResult.ActionPerformed) {
                hiddenTrackKeys.remove(track.trackKey)
            }
        }
        // Auto-dismiss indefinite snackbar after recommendedTimeoutMs
        scope.launch {
            delay(recommendedTimeoutMs.toLong())
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(lirixColors.appBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp)
        ) {
            // 1. Поисковая строка (48dp) с кнопкой перехода в Чёрный список [ 🚫 ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    placeholder = {
                        Text(
                            text = "Поиск по названию, артисту или словам...",
                            style = MaterialTheme.typography.bodySmall,
                            color = lirixColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = stableAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Очистить", tint = lirixColors.textSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(lirixColors.cardCornerRadius),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = lirixColors.surfacePrimary,
                        unfocusedContainerColor = lirixColors.surfacePrimary,
                        focusedBorderColor = stableAccent,
                        unfocusedBorderColor = lirixColors.borderSubtle,
                        focusedTextColor = lirixColors.textPrimary,
                        unfocusedTextColor = lirixColors.textPrimary
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onOpenBlacklistSettings,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(lirixColors.cardCornerRadius))
                        .background(lirixColors.surfacePrimary)
                        .border(1.dp, lirixColors.borderSubtle, RoundedCornerShape(lirixColors.cardCornerRadius))
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "Чёрный список",
                        tint = lirixColors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Горизонтальные фильтры (высота плашки 36dp, зона клика 48dp)
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(LibraryFilterChip.entries) { chip ->
                    val isSelected = selectedFilter == chip
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(lirixColors.pillCornerRadius))
                            .background(if (isSelected) stableAccent else lirixColors.surfaceCard)
                            .border(
                                1.dp,
                                if (isSelected) stableAccent else lirixColors.borderSubtle,
                                RoundedCornerShape(lirixColors.pillCornerRadius)
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedFilter = chip
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chip.title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) lirixColors.appBackground else lirixColors.textPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Сегмент списка: [ Треки (N) ] [ Артисты (M) ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(lirixColors.cardCornerRadius))
                    .background(lirixColors.surfacePrimary)
                    .border(1.dp, lirixColors.borderSubtle, RoundedCornerShape(lirixColors.cardCornerRadius))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LibrarySegmentMode.entries.forEach { mode ->
                    val isSelected = selectedSegment == mode
                    val count = if (mode == LibrarySegmentMode.TRACKS) filteredTracks.size else uniqueArtists.size
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(lirixColors.cardCornerRadius - 2.dp))
                            .background(if (isSelected) lirixColors.surfaceCard else Color.Transparent)
                            .clickable { selectedSegment = mode },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${mode.title} ($count)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) lirixColors.textPrimary else lirixColors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Список треков / артистов
            Crossfade(targetState = selectedSegment, label = "LibrarySegmentCrossfade") { segment ->
                when (segment) {
                    LibrarySegmentMode.TRACKS -> {
                        if (filteredTracks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isBlank()) "Медиатека пуста" else "Ничего не найдено",
                                    color = lirixColors.textSecondary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredTracks, key = { it.trackKey }) { track ->
                                    LibraryTrackItemRow(
                                        track = track,
                                        stableAccent = stableAccent,
                                        onClick = { onTrackSelected(track) },
                                        onLongClick = { activeBottomSheetTrack = track },
                                        onToggleFavorite = {
                                            scope.launch(Dispatchers.IO) {
                                                db.musicDao().setFavorite(track.trackKey, !track.isFavorite)
                                            }
                                        },
                                        onHideTrack = { hideTrack(track) }
                                    )
                                }
                            }
                        }
                    }

                    LibrarySegmentMode.ARTISTS -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uniqueArtists, key = { it }) { artistName ->
                                val artistTracks = visibleTracks.filter { it.artist == artistName }
                                Surface(
                                    color = lirixColors.surfaceCard,
                                    shape = RoundedCornerShape(lirixColors.cardCornerRadius),
                                    border = BorderStroke(1.dp, lirixColors.borderSubtle),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .clickable {
                                            searchQuery = artistName
                                            selectedSegment = LibrarySegmentMode.TRACKS
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = artistName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = lirixColors.textPrimary
                                        )
                                        Text(
                                            text = "${artistTracks.size} треков",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = lirixColors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 76.dp)
        )

        // Track Action Bottom Sheet for Long-Press alternative
        activeBottomSheetTrack?.let { track ->
            TrackActionBottomSheet(
                track = track,
                onDismissRequest = { activeBottomSheetTrack = null },
                onToggleFavorite = { t ->
                    scope.launch(Dispatchers.IO) {
                        db.musicDao().setFavorite(t.trackKey, !t.isFavorite)
                    }
                },
                onHideTrack = { t ->
                    hideTrack(t)
                }
            )
        }
    }
}

/**
 * 64dp track item row conforming to SPEC-UI-MOBILE-2.0 / TASK-UI-MOB-04.
 *
 * Safe gesture separation:
 * - 0..28dp passed freely to system Back (prohibition of systemGestureExclusion on list!).
 * - Swipe right (> 28dp, scaledTouchSlop) toggles Favorite.
 * - Swipe left ("Игнорировать") hides track from library.
 * - Long press opens TrackActionBottomSheet.
 * - TalkBack customActions implemented.
 */
@Composable
private fun LibraryTrackItemRow(
    track: TrackEntity,
    stableAccent: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onHideTrack: () -> Unit
) {
    val tokens = LirixThemeTokens.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val systemBackThresholdPx = with(density) { 28.dp.toPx() }
    val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    var touchStartX by remember { mutableFloatStateOf(0f) }
    var dragDeltaX by remember { mutableFloatStateOf(0f) }
    var isEligibleForSwipe by remember { mutableStateOf(false) }

    Surface(
        color = tokens.surfaceCard,
        shape = RoundedCornerShape(tokens.cardCornerRadius),
        border = BorderStroke(1.dp, tokens.borderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(if (track.isFavorite) "Убрать из избранного" else "В избранное") {
                        onToggleFavorite()
                        true
                    },
                    CustomAccessibilityAction("Скрыть трек из медиатеки") {
                        onHideTrack()
                        true
                    }
                )
            }
            .pointerInput(track.trackKey) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                )
            }
            .pointerInput(track.trackKey) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        touchStartX = offset.x
                        dragDeltaX = 0f
                        // Touch starting inside 0..28dp belongs to system Back!
                        isEligibleForSwipe = touchStartX > systemBackThresholdPx
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (isEligibleForSwipe) {
                            change.consume()
                            dragDeltaX += dragAmount
                        }
                    },
                    onDragEnd = {
                        if (isEligibleForSwipe) {
                            if (dragDeltaX > touchSlopPx * 4f) {
                                // Swipe right: Toggle favorite
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleFavorite()
                            } else if (dragDeltaX < -touchSlopPx * 4f) {
                                // Swipe left: Hide track ("Игнорировать")
                                onHideTrack()
                            }
                        }
                        dragDeltaX = 0f
                        isEligibleForSwipe = false
                    },
                    onDragCancel = {
                        dragDeltaX = 0f
                        isEligibleForSwipe = false
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Мини-обложка 44x44 (R=6dp)
            AlbumArtImage(
                artUri = track.albumArtUri,
                size = 44.dp,
                shape = RoundedCornerShape(6.dp),
                contentDescription = track.title
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Название (15sp, SemiBold) и Артист/Альбом (13sp, Mist)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontFamily = GolosTextFontFamily,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = tokens.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${track.artist}${if (track.album.isNotBlank()) " • ${track.album}" else ""}",
                    fontFamily = GolosTextFontFamily,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = tokens.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Правый блок метаданных: дата, счетчик ×18, бейджи
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (track.playCount > 1) {
                        Text(
                            text = "×${track.playCount}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = GolosTextFontFamily,
                                fontFeatureSettings = FontFeature.tabularFigures()
                            ),
                            color = stableAccent
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    if (track.isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "В избранном",
                            tint = stableAccent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!track.syncedLyrics.isNullOrBlank()) {
                        BorderBadge(text = "Синхронно", borderColor = stableAccent)
                    } else if (!track.plainLyrics.isNullOrBlank()) {
                        BorderBadge(
                            text = "Текст",
                            borderColor = tokens.borderSubtle,
                            backgroundColor = tokens.surfaceElevated,
                            textColor = tokens.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BorderBadge(
    text: String,
    borderColor: Color,
    backgroundColor: Color = Color.Transparent,
    textColor: Color = borderColor
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
            color = textColor,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}
