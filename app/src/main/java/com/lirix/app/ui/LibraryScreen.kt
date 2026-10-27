package com.lirix.app.ui

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.lirix.app.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.TrackEntity
import com.lirix.app.ui.library.LibraryExclusions
import com.lirix.app.ui.components.AlbumArtImage
import com.lirix.app.ui.components.TrackActionBottomSheet
import com.lirix.app.ui.theme.FontFeature
import com.lirix.app.ui.theme.GolosTextFontFamily
import com.lirix.app.ui.theme.LirixThemeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Filter chips for Media Library.
 */
enum class LibraryFilterChip(@StringRes val titleRes: Int) {
    ALL(R.string.library_filter_all),
    FAVORITES(R.string.library_filter_favorites),
    KARAOKE(R.string.library_filter_karaoke),
    CHORDS(R.string.library_filter_chords),
    OFFLINE(R.string.library_filter_offline)
}

/**
 * Segment modes: Tracks vs Artists.
 */
enum class LibrarySegmentMode(@StringRes val titleRes: Int) {
    TRACKS(R.string.library_segment_tracks),
    ARTISTS(R.string.library_segment_artists)
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
    onOpenBlacklistSettings: () -> Unit = {},
    onFilterBoundsChanged: (Rect) -> Unit = {}
) {
    val lirixColors = LirixThemeTokens.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Dynamic theme accent
    val accentPrimary = lirixColors.accentPrimary

    val recentTracks by db.musicDao().observeRecentTracks().collectAsStateWithLifecycle(initialValue = emptyList())
    val exclusions = remember(context) { LibraryExclusions.get(context) }
    val removedTrackKeys by exclusions.removed.collectAsStateWithLifecycle()
    var showRemovedTracks by rememberSaveable { mutableStateOf(false) }

    var selectedFilter by rememberSaveable { mutableStateOf(LibraryFilterChip.ALL) }
    var selectedSegment by rememberSaveable { mutableStateOf(LibrarySegmentMode.TRACKS) }
    var searchQuery by remember { mutableStateOf("") }
    var activeBottomSheetTrack by remember { mutableStateOf<TrackEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarQueue = remember { Mutex() }

    // Filtered track list
    val visibleTracks = remember(recentTracks, removedTrackKeys, exclusions.initializationFailed) {
        if (exclusions.initializationFailed) emptyList()
        else recentTracks.filter { it.trackKey !in removedTrackKeys }
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
    val uniqueArtists = remember(visibleTracks, context) {
        visibleTracks.map { it.artist.ifBlank { context.getString(R.string.unknown_artist) } }.distinct()
    }

    // Adaptive Snackbar timeout (6-8s) via AccessibilityManager
    val accessibilityManager = remember {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
    val recommendedTimeoutMs = remember(accessibilityManager) {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
        accessibilityManager?.getRecommendedTimeoutMillis(7000, AccessibilityManager.FLAG_CONTENT_CONTROLS) ?: 7000
        } else 7000
    }

    val showMessage: suspend (String, String?) -> SnackbarResult? = { message, action ->
        snackbarQueue.withLock {
            withTimeoutOrNull(recommendedTimeoutMs.toLong()) {
                snackbarHostState.showSnackbar(message, actionLabel = action, duration = SnackbarDuration.Indefinite)
            }
        }
    }
    val restoreTrack: (String) -> Unit = { key ->
        scope.launch {
            if (!exclusions.restore(key)) showMessage(context.getString(R.string.library_remove_error), null)
        }
    }
    val removalInFlight = remember { mutableSetOf<String>() }
    val hideTrack: (TrackEntity) -> Unit = hide@ { track ->
        if (track.trackKey in exclusions.removed.value || !removalInFlight.add(track.trackKey)) return@hide
        scope.launch {
            val removed = try { exclusions.remove(track.trackKey) } finally { removalInFlight.remove(track.trackKey) }
            if (removed != null) {
                val result = showMessage(context.getString(R.string.library_removed_message, track.title), context.getString(R.string.action_undo))
                if (result == SnackbarResult.ActionPerformed && !exclusions.restore(track.trackKey, removed)) {
                    showMessage(context.getString(R.string.library_remove_error), null)
                }
            } else showMessage(context.getString(R.string.library_remove_error), null)
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
            if (exclusions.initializationFailed) {
                Text(stringResource(R.string.library_remove_error), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            // 1. Поисковая строка (48dp) с кнопкой перехода в Чёрный список [ 🚫 ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.library_search_placeholder),
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
                            tint = accentPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear), tint = lirixColors.textSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(lirixColors.cardCornerRadius),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = lirixColors.surfacePrimary,
                        unfocusedContainerColor = lirixColors.surfacePrimary,
                        focusedBorderColor = accentPrimary,
                        unfocusedBorderColor = lirixColors.borderSubtle,
                        focusedTextColor = lirixColors.textPrimary,
                        unfocusedTextColor = lirixColors.textPrimary
                    )
                )

                IconButton(onClick = { showRemovedTracks = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.library_removed_title), tint = lirixColors.textSecondary)
                }
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
                        contentDescription = stringResource(R.string.library_blacklist_button),
                        tint = lirixColors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Горизонтальные фильтры (высота плашки 36dp, зона клика 48dp)
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth().onGloballyPositioned { onFilterBoundsChanged(it.boundsInWindow()) },
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
                            .background(if (isSelected) accentPrimary else lirixColors.surfaceCard)
                            .border(
                                1.dp,
                                if (isSelected) accentPrimary else lirixColors.borderSubtle,
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
                            text = stringResource(chip.titleRes),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else lirixColors.textPrimary
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
                            text = "${stringResource(mode.titleRes)} ($count)",
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
                                    text = if (exclusions.initializationFailed) stringResource(R.string.library_remove_error)
                                           else if (searchQuery.isBlank()) stringResource(R.string.library_empty) else stringResource(R.string.library_no_results),
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
                                        accentPrimary = accentPrimary,
                                        onClick = { onTrackSelected(track) },
                                        onLongClick = { activeBottomSheetTrack = track },
                                        onToggleFavorite = {
                                            scope.launch(Dispatchers.IO) {
                                                db.musicDao().invertFavorite(track.trackKey)
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
                                            text = stringResource(R.string.library_artist_track_count, artistTracks.size),
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

        if (showRemovedTracks) {
            AlertDialog(
                onDismissRequest = { showRemovedTracks = false },
                title = { Text(stringResource(R.string.library_removed_title)) },
                text = {
                    if (removedTrackKeys.isEmpty()) Text(stringResource(R.string.library_removed_empty))
                    else LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        val byKey = recentTracks.associateBy { it.trackKey }
                        items(removedTrackKeys.sorted(), key = { it }) { key ->
                            val track = byKey[key]
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(track?.title ?: context.getString(R.string.library_removed_missing), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (track != null) Text(track.artist, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { restoreTrack(key) }) { Text(stringResource(R.string.library_restore)) }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showRemovedTracks = false }) { Text(stringResource(R.string.action_close)) } }
            )
        }

        // Track Action Bottom Sheet for Long-Press alternative
        activeBottomSheetTrack?.let { track ->
            TrackActionBottomSheet(
                track = track,
                onDismissRequest = { activeBottomSheetTrack = null },
                onToggleFavorite = { t ->
                    scope.launch(Dispatchers.IO) {
                        db.musicDao().invertFavorite(t.trackKey)
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
    accentPrimary: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onHideTrack: () -> Unit
) {
    val tokens = LirixThemeTokens.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    var menuExpanded by remember(track.trackKey) { mutableStateOf(false) }

    Surface(
        color = tokens.surfaceCard,
        shape = RoundedCornerShape(tokens.cardCornerRadius),
        border = BorderStroke(1.dp, tokens.borderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(if (track.isFavorite) context.getString(R.string.action_remove_favorite) else context.getString(R.string.action_add_favorite)) {
                        onToggleFavorite()
                        true
                    },
                    CustomAccessibilityAction(context.getString(R.string.library_remove)) {
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
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.library_track_actions, track.title), tint = tokens.textSecondary)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (track.isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite)) },
                        onClick = { menuExpanded = false; onToggleFavorite() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_remove)) },
                        onClick = { menuExpanded = false; onHideTrack() }
                    )
                }
            }

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
                            color = accentPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    if (track.isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = stringResource(R.string.action_favorite),
                            tint = tokens.accentSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!track.syncedLyrics.isNullOrBlank()) {
                        BorderBadge(text = stringResource(R.string.badge_synced), borderColor = accentPrimary)
                    } else if (!track.plainLyrics.isNullOrBlank()) {
                        BorderBadge(
                            text = stringResource(R.string.badge_lyrics),
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
