package com.lirix.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lirix.app.analytics.AchievementTier
import com.lirix.app.analytics.AnalyticsTimeframe
import com.lirix.app.analytics.GamificationEngine
import com.lirix.app.analytics.ListeningArchetype
import com.lirix.app.analytics.TimeOfDaySlot
import com.lirix.app.analytics.TopObsessionItem
import com.lirix.app.analytics.TopTrackItem
import com.lirix.app.analytics.WrappedStats
import com.lirix.app.analytics.WrappedStatsEngine
import com.lirix.app.storage.AchievementEntity
import com.lirix.app.storage.AppDatabase
import com.lirix.app.feature.share.ShareCardGenerator
import com.lirix.app.feature.share.ShareManager
import com.lirix.app.feature.share.WrappedShareSection
import androidx.compose.ui.window.Dialog
import com.lirix.app.ingestion.MediaSessionCollector
import com.lirix.app.ui.components.AlbumArtThumbnail
import com.lirix.app.ui.components.ShareFormatDialog
import com.lirix.app.ui.theme.LirixThemeTokens
import android.graphics.BitmapFactory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WrappedScreen() {
    val lirixColors = LirixThemeTokens.colors
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }

    val tracks by db.musicDao().observeRecentTracks().collectAsStateWithLifecycle(initialValue = emptyList())
    val sessions by db.musicDao().observeAllSessions().collectAsStateWithLifecycle(initialValue = emptyList())
    val rawAchievements by db.achievementDao().observeAchievements().collectAsStateWithLifecycle(initialValue = emptyList())
    val livePlayback by MediaSessionCollector.livePlaybackFlow.collectAsStateWithLifecycle(initialValue = null)

    var selectedTimeframe by remember { mutableStateOf(AnalyticsTimeframe.ALL_TIME) }
    var selectedStatusFilter by remember { mutableStateOf("ALL") } // "ALL", "UNLOCKED", "IN_PROGRESS"
    var selectedTierFilter by remember { mutableStateOf<AchievementTier?>(null) }

    var stats by remember { mutableStateOf<WrappedStats?>(null) }
    var evaluatedAchievements by remember { mutableStateOf<List<AchievementEntity>>(emptyList()) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showSectionPicker by remember { mutableStateOf(false) }
    var selectedShareSection by remember { mutableStateOf(WrappedShareSection.ARCHETYPE) }

    if (showSectionPicker && stats != null) {
        ShareSectionPickerDialog(
            onDismiss = { showSectionPicker = false },
            onPick = { section ->
                selectedShareSection = section
                showSectionPicker = false
                showShareDialog = true
            }
        )
    }

    if (showShareDialog && stats != null) {
        val currentStats = stats!!
        ShareFormatDialog(
            onDismissRequest = { showShareDialog = false },
            onFormatSelected = { format ->
                val obsessionBmp = currentStats.topObsession?.albumArtUri?.let { uri ->
                    try {
                        val path = uri.removePrefix("file://")
                        val file = File(path)
                        if (file.exists() && file.length() > 0L) BitmapFactory.decodeFile(file.absolutePath) else null
                    } catch (e: Exception) {
                        null
                    }
                }
                val coverBitmaps = currentStats.topTracks.mapNotNull { track ->
                    track.albumArtUri?.let { uri ->
                        try {
                            val path = uri.removePrefix("file://")
                            val file = File(path)
                            if (file.exists() && file.length() > 0L) BitmapFactory.decodeFile(file.absolutePath) else null
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                val cardBitmap = ShareCardGenerator.generateWrappedCard(
                    context = context,
                    stats = currentStats,
                    timeframe = selectedTimeframe,
                    format = format,
                    obsessionBitmap = obsessionBmp,
                    topCoverBitmaps = coverBitmaps,
                    section = selectedShareSection,
                    posterStyle = com.lirix.app.feature.share.PosterStyle.random()
                )
                ShareManager.shareBitmap(
                    context = context,
                    bitmap = cardBitmap,
                    chooserTitle = "Музыкальный Wrapped (${selectedTimeframe.labelRu})"
                )
            }
        )
    }

    LaunchedEffect(selectedTimeframe, tracks, sessions, livePlayback?.isPlaying) {
        withContext(Dispatchers.Default) {
            stats = WrappedStatsEngine.calculateStats(
                tracks = tracks,
                sessions = sessions,
                timeframe = selectedTimeframe,
                referenceTimestampMs = System.currentTimeMillis(),
                livePlayback = livePlayback
            )
        }
    }

    // Live real-time ticker when music is playing so listening time increments smoothly
    LaunchedEffect(livePlayback?.isPlaying, selectedTimeframe, tracks, sessions) {
        if (livePlayback?.isPlaying == true) {
            while (isActive) {
                delay(3000L)
                withContext(Dispatchers.Default) {
                    stats = WrappedStatsEngine.calculateStats(
                        tracks = tracks,
                        sessions = sessions,
                        timeframe = selectedTimeframe,
                        referenceTimestampMs = System.currentTimeMillis(),
                        livePlayback = livePlayback
                    )
                }
            }
        }
    }

    LaunchedEffect(rawAchievements, tracks, sessions) {
        withContext(Dispatchers.Default) {
            evaluatedAchievements = GamificationEngine.checkAchievements(rawAchievements, tracks, sessions)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(lirixColors.appBackground)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header with dynamic badge and prominent share CTA
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(lirixColors.accentPrimary)
                        )
                        Text(
                            text = "ИТОГИ // REPLAY '26",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = lirixColors.accentPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Музыкальный Wrapped",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = lirixColors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(lirixColors.cardCornerRadius))
                        .background(
                            Brush.horizontalGradient(
                                listOf(lirixColors.accentPrimary, lirixColors.accentSecondary)
                            )
                        )
                        .clickable { showSectionPicker = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Поделиться постером",
                            tint = if (lirixColors.isDark) Color.Black else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "ПОСТЕР",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            softWrap = false,
                            color = if (lirixColors.isDark) Color.Black else Color.White
                        )
                    }
                }
            }
        }

        // 2. Segmented Timeframe Switcher
        item {
            TimeframeSwitcher(
                selectedTimeframe = selectedTimeframe,
                onSelectTimeframe = { selectedTimeframe = it }
            )
        }

        // 3. Holographic Archetype Banner
        item {
            val archetype = stats?.archetype ?: ListeningArchetype.CASUAL_LISTENER
            ArchetypeBanner(archetype = archetype)
        }

        // 4. KPI Grid (2x2)
        item {
            KpiGridSection(stats = stats, tracksCount = tracks.size)
        }

        // 5. Top Obsession (if repeats >= 3)
        item {
            val obsession = stats?.topObsession
            if (obsession != null) {
                TopObsessionCard(obsession = obsession)
            }
        }

        // 6. Pulse & Compass (Daily & Weekly Activity)
        item {
            ActivityPulseSection(stats = stats)
        }

        // 7. Top 5 Tracks
        item {
            val topTracks = stats?.topTracks.orEmpty()
            if (topTracks.isNotEmpty()) {
                TopTracksSection(topTracks = topTracks)
            }
        }

        // 8. Top 5 Artists
        item {
            val topArtists = stats?.topArtists.orEmpty()
            if (topArtists.isNotEmpty()) {
                TopArtistsSection(topArtists = topArtists)
            }
        }

        // 9. Achievements Showcase Header & Progress
        item {
            AchievementsHeaderSection(
                achievements = evaluatedAchievements,
                selectedStatus = selectedStatusFilter,
                onStatusChange = { selectedStatusFilter = it },
                selectedTier = selectedTierFilter,
                onTierChange = { selectedTierFilter = it }
            )
        }

        // 10. Achievements Grid (2-column, filtered)
        item {
            val filtered = evaluatedAchievements.filter { ach ->
                val statusMatches = when (selectedStatusFilter) {
                    "UNLOCKED" -> ach.isUnlocked
                    "IN_PROGRESS" -> !ach.isUnlocked
                    else -> true
                }
                val tierMatches = if (selectedTierFilter == null) {
                    true
                } else {
                    ach.tier.equals(selectedTierFilter?.name, ignoreCase = true)
                }
                statusMatches && tierMatches
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Нет достижений в выбранной категории",
                        style = MaterialTheme.typography.bodyMedium,
                        color = lirixColors.textMuted
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val pairs = filtered.chunked(2)
                    for (pair in pairs) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (ach in pair) {
                                TieredAchievementCard(
                                    achievement = ach,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TimeframeSwitcher(
    selectedTimeframe: AnalyticsTimeframe,
    onSelectTimeframe: (AnalyticsTimeframe) -> Unit
) {
    val lirixColors = LirixThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(lirixColors.pillCornerRadius))
            .background(lirixColors.surfaceCard)
            .border(1.dp, lirixColors.borderSubtle, RoundedCornerShape(lirixColors.pillCornerRadius))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (tf in AnalyticsTimeframe.entries) {
            val isSelected = tf == selectedTimeframe
            val activeContentColor = if (lirixColors.isDark) Color.Black else Color.White
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(lirixColors.cardCornerRadius))
                    .background(if (isSelected) lirixColors.accentPrimary else Color.Transparent)
                    .border(
                        1.dp,
                        if (isSelected) lirixColors.accentPrimary else Color.Transparent,
                        RoundedCornerShape(lirixColors.cardCornerRadius)
                    )
                    .clickable { onSelectTimeframe(tf) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tf.labelRu,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isSelected) activeContentColor else lirixColors.textSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ArchetypeBanner(archetype: ListeningArchetype) {
    val lirixColors = LirixThemeTokens.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(lirixColors.cardCornerRadius))
            .background(
                Brush.linearGradient(
                    listOf(
                        lirixColors.surfaceCard,
                        lirixColors.surfaceElevated
                    )
                )
            )
            .border(
                1.5.dp,
                lirixColors.accentPrimary.copy(alpha = 0.5f),
                RoundedCornerShape(lirixColors.cardCornerRadius)
            )
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = lirixColors.accentPrimary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, lirixColors.accentPrimary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "★",
                            style = MaterialTheme.typography.labelSmall,
                            color = lirixColors.accentPrimary
                        )
                        Text(
                            text = "ВАШ МУЗЫКАЛЬНЫЙ АРХЕТИП",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            ),
                            color = lirixColors.accentPrimary
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = lirixColors.accentSecondary,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = archetype.titleRu.uppercase(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                ),
                color = lirixColors.textPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = archetype.descriptionRu,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                ),
                color = lirixColors.textSecondary
            )
        }
    }
}

@Composable
private fun KpiGridSection(stats: WrappedStats?, tracksCount: Int) {
    val lirixColors = LirixThemeTokens.colors
    val totalMinutes = (stats?.totalListeningTimeMs ?: 0L) / 60000
    val totalHours = totalMinutes / 60
    val remMinutes = totalMinutes % 60
    val timeFormatted = if (totalHours > 0) "${totalHours}ч ${remMinutes}м" else "${remMinutes}м"

    val topArtistName = stats?.topArtists?.firstOrNull()?.artist ?: "—"
    val karaokeCoveragePercent = ((stats?.lyricsCoverageRatio ?: 0f) * 100).toInt()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiMetricBox(
                title = "Общее время",
                value = timeFormatted,
                accentColor = lirixColors.accentPrimary,
                modifier = Modifier.weight(1f)
            )
            KpiMetricBox(
                title = "Уникальных треков",
                value = "${stats?.uniqueTracksCount ?: tracksCount}",
                accentColor = lirixColors.accentPrimary,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiMetricBox(
                title = "Главный артист",
                value = topArtistName,
                accentColor = lirixColors.accentSecondary,
                modifier = Modifier.weight(1f)
            )
            KpiMetricBox(
                title = "С текстами (LRC)",
                value = "$karaokeCoveragePercent%",
                accentColor = lirixColors.accentSecondary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun KpiMetricBox(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val lirixColors = LirixThemeTokens.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(lirixColors.cardCornerRadius))
            .background(lirixColors.surfaceCard)
            .border(1.dp, lirixColors.borderSubtle, RoundedCornerShape(lirixColors.cardCornerRadius))
            .padding(14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = lirixColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp
                ),
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TopObsessionCard(obsession: TopObsessionItem) {
    val lirixColors = LirixThemeTokens.colors
    val durationMinutes = obsession.durationMsInPeriod / 60000

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(lirixColors.cardCornerRadius))
            .background(
                Brush.linearGradient(
                    listOf(lirixColors.surfaceCard, lirixColors.surfaceElevated)
                )
            )
            .border(1.dp, lirixColors.accentPrimary.copy(alpha = 0.6f), RoundedCornerShape(lirixColors.cardCornerRadius))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔥 Главная одержимость периода",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.4.sp
                    ),
                    color = lirixColors.accentPrimary
                )

                Surface(
                    color = lirixColors.accentPrimary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, lirixColors.accentPrimary.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "x${obsession.playCountInPeriod} повторов",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = lirixColors.accentPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArtThumbnail(
                    artUri = obsession.albumArtUri,
                    size = 56.dp,
                    shape = RoundedCornerShape(12.dp),
                    borderColor = lirixColors.accentPrimary.copy(alpha = 0.4f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = obsession.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = lirixColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = obsession.artist,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp
                        ),
                        color = lirixColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (durationMinutes > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Суммарно: $durationMinutes мин за период",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = lirixColors.textMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityPulseSection(stats: WrappedStats?) {
    val lirixColors = LirixThemeTokens.colors
    val distribution = stats?.timeOfDayDistribution.orEmpty()
    val weekly = stats?.weeklyActivity.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(lirixColors.cardCornerRadius))
            .background(lirixColors.surfaceCard)
            .border(1.dp, lirixColors.borderSubtle, RoundedCornerShape(lirixColors.cardCornerRadius))
            .padding(14.dp)
    ) {
        Text(
            text = "Компас активности",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            ),
            color = lirixColors.textPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Time of Day distribution rows
        val slots = listOf(
            Triple(TimeOfDaySlot.NIGHT, "Ночь (00-06)", lirixColors.accentSecondary),
            Triple(TimeOfDaySlot.MORNING, "Утро (06-12)", lirixColors.accentPrimary),
            Triple(TimeOfDaySlot.AFTERNOON, "День (12-18)", lirixColors.accentSecondary),
            Triple(TimeOfDaySlot.EVENING, "Вечер (18-24)", lirixColors.accentPrimary)
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((slot, label, color) in slots) {
                val fraction = distribution[slot] ?: 0f
                val percent = (fraction * 100).toInt()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = lirixColors.textSecondary,
                        modifier = Modifier.width(96.dp)
                    )
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = color,
                        trackColor = lirixColors.surfaceElevated
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = lirixColors.textPrimary,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
        }

        // Weekly Micro-bars
        if (weekly.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Дни недели",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = lirixColors.textMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            val maxSessions = weekly.maxOfOrNull { it.sessionCount }?.coerceAtLeast(1) ?: 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                for (day in weekly) {
                    val heightRatio = day.sessionCount.toFloat() / maxSessions.toFloat()
                    val barHeightDp = (heightRatio * 36).coerceAtLeast(4f).dp

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(28.dp)
                    ) {
                        Text(
                            text = if (day.sessionCount > 0) "${day.sessionCount}" else "",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = lirixColors.accentPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(barHeightDp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    if (day.sessionCount > 0) lirixColors.accentPrimary else lirixColors.surfaceElevated
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = day.dayName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = lirixColors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopTracksSection(topTracks: List<TopTrackItem>) {
    val lirixColors = LirixThemeTokens.colors
    val maxPlays = topTracks.maxOfOrNull { it.playCount }?.coerceAtLeast(1) ?: 1

    Column {
        Text(
            text = "Топ треков периода",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            ),
            color = lirixColors.textPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            topTracks.forEachIndexed { index, track ->
                RankedTrackRow(
                    rank = index + 1,
                    track = track,
                    fraction = track.playCount.toFloat() / maxPlays.toFloat()
                )
            }
        }
    }
}

@Composable
private fun RankedTrackRow(
    rank: Int,
    track: TopTrackItem,
    fraction: Float
) {
    val lirixColors = LirixThemeTokens.colors
    val rankColor = when (rank) {
        1 -> lirixColors.accentPrimary
        2 -> lirixColors.accentSecondary
        3 -> lirixColors.accentGlow
        else -> lirixColors.textSecondary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(lirixColors.cardCornerRadius))
            .background(lirixColors.surfaceCard)
            .border(1.dp, lirixColors.borderSubtle, RoundedCornerShape(lirixColors.cardCornerRadius))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                ),
                color = rankColor,
                modifier = Modifier.width(28.dp)
            )

            AlbumArtThumbnail(
                artUri = track.albumArtUri,
                size = 36.dp,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = lirixColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp
                    ),
                    color = lirixColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "${track.playCount}x",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                color = rankColor
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { fraction.coerceIn(0.05f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = rankColor,
            trackColor = lirixColors.surfaceElevated
        )
    }
}

@Composable
private fun TopArtistsSection(topArtists: List<com.lirix.app.analytics.TopArtistItem>) {
    val lirixColors = LirixThemeTokens.colors
    val maxPlays = topArtists.maxOfOrNull { it.playCount }?.coerceAtLeast(1) ?: 1

    Column {
        Text(
            text = "Топ исполнителей",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            ),
            color = lirixColors.textPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            topArtists.forEachIndexed { index, item ->
                RankedArtistRow(
                    rank = index + 1,
                    title = item.artist,
                    playCount = item.playCount,
                    fraction = item.playCount.toFloat() / maxPlays.toFloat()
                )
            }
        }
    }
}

@Composable
private fun RankedArtistRow(
    rank: Int,
    title: String,
    playCount: Int,
    fraction: Float
) {
    val lirixColors = LirixThemeTokens.colors
    val rankColor = when (rank) {
        1 -> lirixColors.accentPrimary
        2 -> lirixColors.accentSecondary
        3 -> lirixColors.accentGlow
        else -> lirixColors.textSecondary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(lirixColors.cardCornerRadius))
            .background(lirixColors.surfaceCard)
            .border(1.dp, lirixColors.borderSubtle, RoundedCornerShape(lirixColors.cardCornerRadius))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                ),
                color = rankColor,
                modifier = Modifier.width(32.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = lirixColors.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$playCount треков",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                ),
                color = lirixColors.textMuted
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { fraction.coerceIn(0.05f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = rankColor,
            trackColor = lirixColors.surfaceElevated
        )
    }
}

@Composable
private fun AchievementsHeaderSection(
    achievements: List<AchievementEntity>,
    selectedStatus: String,
    onStatusChange: (String) -> Unit,
    selectedTier: AchievementTier?,
    onTierChange: (AchievementTier?) -> Unit
) {
    val lirixColors = LirixThemeTokens.colors
    val unlockedCount = achievements.count { it.isUnlocked }
    val totalCount = achievements.size.coerceAtLeast(1)
    val unlockedPercent = (unlockedCount.toFloat() / totalCount.toFloat() * 100).toInt()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Достижения ($unlockedCount / $totalCount)",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = lirixColors.textPrimary
            )

            Text(
                text = "$unlockedPercent%",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                ),
                color = lirixColors.accentPrimary
            )
        }

        LinearProgressIndicator(
            progress = { unlockedCount.toFloat() / totalCount.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = lirixColors.accentPrimary,
            trackColor = lirixColors.surfaceElevated
        )

        // Status Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                label = "Все ($totalCount)",
                isSelected = selectedStatus == "ALL",
                onClick = { onStatusChange("ALL") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                label = "Открытые ($unlockedCount)",
                isSelected = selectedStatus == "UNLOCKED",
                onClick = { onStatusChange("UNLOCKED") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                label = "В процессе",
                isSelected = selectedStatus == "IN_PROGRESS",
                onClick = { onStatusChange("IN_PROGRESS") },
                modifier = Modifier.weight(1f)
            )
        }

        // Tier Filter Chips (Horizontal Scrollable)
        val tierScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(tierScrollState),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SmallFilterChip(
                label = "Все тиры",
                isSelected = selectedTier == null,
                onClick = { onTierChange(null) }
            )
            SmallFilterChip(
                label = "🥉 Бронза",
                isSelected = selectedTier == AchievementTier.BRONZE,
                onClick = { onTierChange(AchievementTier.BRONZE) }
            )
            SmallFilterChip(
                label = "🥈 Серебро",
                isSelected = selectedTier == AchievementTier.SILVER,
                onClick = { onTierChange(AchievementTier.SILVER) }
            )
            SmallFilterChip(
                label = "🥇 Золото",
                isSelected = selectedTier == AchievementTier.GOLD,
                onClick = { onTierChange(AchievementTier.GOLD) }
            )
            SmallFilterChip(
                label = "💎 Платина",
                isSelected = selectedTier == AchievementTier.PLATINUM,
                onClick = { onTierChange(AchievementTier.PLATINUM) }
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lirixColors = LirixThemeTokens.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) lirixColors.accentPrimary.copy(alpha = 0.2f) else lirixColors.surfaceCard)
            .border(
                1.dp,
                if (isSelected) lirixColors.accentPrimary else lirixColors.borderSubtle,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (isSelected) lirixColors.accentPrimary else lirixColors.textSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun SmallFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val lirixColors = LirixThemeTokens.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) lirixColors.accentPrimary.copy(alpha = 0.2f) else lirixColors.surfaceCard)
            .border(
                1.dp,
                if (isSelected) lirixColors.accentPrimary else lirixColors.borderSubtle,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (isSelected) lirixColors.accentPrimary else lirixColors.textSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun TieredAchievementCard(
    achievement: AchievementEntity,
    modifier: Modifier = Modifier
) {
    val lirixColors = LirixThemeTokens.colors
    val isUnlocked = achievement.isUnlocked
    val tierColor = when (achievement.tier.uppercase()) {
        "BRONZE" -> Color(0xFFCD7F32)
        "SILVER" -> Color(0xFFC0C0C0)
        "GOLD" -> Color(0xFFFFD700)
        "PLATINUM" -> Color(0xFFE5E4E2)
        else -> lirixColors.accentPrimary
    }

    val dateFormat = remember { SimpleDateFormat("dd.MM.yy", Locale.getDefault()) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(lirixColors.cardCornerRadius))
            .background(
                if (isUnlocked) {
                    Brush.verticalGradient(
                        listOf(tierColor.copy(alpha = 0.20f), lirixColors.surfaceCard)
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(lirixColors.surfaceCard, lirixColors.surfaceCard)
                    )
                }
            )
            .border(
                1.dp,
                if (isUnlocked) tierColor.copy(alpha = 0.8f) else lirixColors.borderSubtle,
                RoundedCornerShape(lirixColors.cardCornerRadius)
            )
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (isUnlocked) tierColor.copy(alpha = 0.3f) else lirixColors.surfaceElevated
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isUnlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isUnlocked) tierColor else lirixColors.textMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }

                Surface(
                    color = tierColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, tierColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = achievement.tier.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.4.sp
                        ),
                        color = tierColor,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = achievement.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = if (isUnlocked) lirixColors.textPrimary else lirixColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = achievement.description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                ),
                color = lirixColors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (!isUnlocked && achievement.maxProgress > 1) {
                val fraction = (achievement.currentProgress.toFloat() / achievement.maxProgress.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = tierColor,
                    trackColor = lirixColors.surfaceElevated
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "${achievement.currentProgress} / ${achievement.maxProgress}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = lirixColors.textMuted
                    )
                }
            } else if (isUnlocked) {
                val unlockedDateStr = achievement.unlockedAt?.let { dateFormat.format(Date(it)) }
                Surface(
                    color = tierColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, tierColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = if (unlockedDateStr != null) "Открыто $unlockedDateStr ✓" else "Разблокировано ✓",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = tierColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareSectionPickerDialog(
    onDismiss: () -> Unit,
    onPick: (WrappedShareSection) -> Unit
) {
    val lirixColors = LirixThemeTokens.colors
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = lirixColors.surfaceCard,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Brush.horizontalGradient(listOf(lirixColors.accentPrimary, lirixColors.accentSecondary))
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Чем поделиться?",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = lirixColors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Одна тема на карточку — чище и читаемее",
                    style = MaterialTheme.typography.bodySmall,
                    color = lirixColors.textSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                val accents = listOf(
                    lirixColors.accentPrimary, lirixColors.accentSecondary, lirixColors.accentGlow,
                    lirixColors.accentPrimary, lirixColors.accentSecondary, lirixColors.textSecondary
                )
                WrappedShareSection.values().forEachIndexed { i, section ->
                    val accent = accents[i % accents.size]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onPick(section) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 4.dp, height = 36.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(accent)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = section.titleRu,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = lirixColors.textPrimary
                            )
                            Text(
                                text = section.hintRu,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = lirixColors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}