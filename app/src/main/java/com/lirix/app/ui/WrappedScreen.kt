package com.lirix.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.lirix.app.R
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lirix.app.analytics.AnalyticsTimeframe
import com.lirix.app.analytics.GamificationEngine
import com.lirix.app.analytics.WrappedStats
import com.lirix.app.analytics.WrappedStatsEngine
import com.lirix.app.feature.share.PosterThemeSnapshot
import com.lirix.app.feature.share.PosterArtworkLoader
import com.lirix.app.feature.share.ShareCardGenerator
import com.lirix.app.feature.share.WrappedShareSection
import com.lirix.app.storage.AchievementEntity
import com.lirix.app.storage.AppDatabase
import com.lirix.app.ui.components.ShareFormatDialog
import com.lirix.app.ui.theme.LirixMobileTypography
import com.lirix.app.ui.theme.LirixThemeTokens
import com.lirix.app.ui.theme.LiterataFontFamily
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Theme-aware listening summary with a screen-owned, system-scaled reveal animation. */
@Composable
fun WrappedScreen() {
    val lirixColors = LirixThemeTokens.colors
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }

    val tracks by db.musicDao().observeRecentTracks().collectAsStateWithLifecycle(initialValue = emptyList())
    val sessions by db.musicDao().observeAllSessions().collectAsStateWithLifecycle(initialValue = emptyList())
    val rawAchievements by db.achievementDao().observeAchievements().collectAsStateWithLifecycle(initialValue = emptyList())
    val livePlayback by com.lirix.app.ingestion.MediaSessionCollector.livePlaybackFlow.collectAsStateWithLifecycle(initialValue = null)

    var selectedTimeframe by rememberSaveable { mutableStateOf(AnalyticsTimeframe.MONTH) }
    var calculatedStats by remember { mutableStateOf<Pair<AnalyticsTimeframe, WrappedStats>?>(null) }
    val stats = calculatedStats?.takeIf { it.first == selectedTimeframe }?.second
    val reveal = remember(selectedTimeframe) { Animatable(0f) }
    // Screen-owned: scrolling, theme changes and live statistics must not replay the reveal.
    LaunchedEffect(selectedTimeframe, stats != null) {
        if (stats != null) {
            reveal.snapTo(0f)
            // Compose applies the system animation duration scale, including immediate completion at 0.
            reveal.animateTo(1f, tween(durationMillis = 1500, easing = LinearEasing))
        }
    }
    var evaluatedAchievements by remember { mutableStateOf<List<AchievementEntity>>(emptyList()) }
    var showShareDialog by remember { mutableStateOf(false) }
    var isPeriodDropdownExpanded by remember { mutableStateOf(false) }

    if (showShareDialog && stats != null) {
        val currentStats = remember { stats!! }
        val exportTimeframe = remember { selectedTimeframe }
        val exportTheme = remember { PosterThemeSnapshot.fromColors(lirixColors) }
        val posterLabels = remember { com.lirix.app.feature.share.PosterTextSnapshot.from(context) }
        val exportLabels = remember {
            com.lirix.app.feature.share.animation.CounterRollLabels(
                header = context.getString(R.string.animated_summary_header),
                minutes = context.getString(R.string.animated_summary_minutes),
                tracks = context.getString(R.string.animated_summary_tracks),
                artists = context.getString(R.string.animated_summary_artists),
                topTracks = context.getString(R.string.animated_summary_top_tracks),
                plays = context.getString(R.string.animated_summary_plays)
            )
        }
        ShareFormatDialog(
            onDismissRequest = { showShareDialog = false },
            animatorProvider = {
                val palette = com.lirix.app.feature.share.animation.ThemeAnimationPalette.fromColors(lirixColors)
                val timeframeStr = when (exportTimeframe) {
                    com.lirix.app.analytics.AnalyticsTimeframe.WEEK -> context.getString(R.string.wrapped_period_week)
                    com.lirix.app.analytics.AnalyticsTimeframe.MONTH -> context.getString(R.string.wrapped_period_month)
                    com.lirix.app.analytics.AnalyticsTimeframe.TODAY -> context.getString(R.string.wrapped_period_today)
                    com.lirix.app.analytics.AnalyticsTimeframe.ALL_TIME -> context.getString(R.string.wrapped_period_all_time)
                }
                com.lirix.app.feature.share.animation.CounterRollAnimator(
                    palette = palette,
                    totalMinutes = (currentStats.totalListeningTimeMs / 60000).toInt(),
                    uniqueTracks = currentStats.uniqueTracksCount,
                    uniqueArtists = currentStats.uniqueArtistsCount,
                    periodLabel = timeframeStr,
                    topTracks = currentStats.topTracks.take(3).map { Triple(it.title, it.artist, it.playCount) },
                    labels = exportLabels
                )
            },
            onFormatSelected = { format ->
                val artwork = currentStats.topTracks.associate { track ->
                    track.trackKey to PosterArtworkLoader.load(context, track.albumArtUri)
                }
                val cardBitmap = withContext(Dispatchers.Default) { ShareCardGenerator.generateWrappedCard(
                    context = context,
                    stats = currentStats,
                    timeframe = exportTimeframe,
                    format = format,
                    section = WrappedShareSection.FULL,
                    theme = exportTheme,
                    artworkByTrackKey = artwork,
                    labels = posterLabels
                ) }
                cardBitmap
            }
        )
    }

    LaunchedEffect(selectedTimeframe, tracks, sessions, livePlayback?.isPlaying) {
        val period = selectedTimeframe
        val result = withContext(Dispatchers.Default) {
            WrappedStatsEngine.calculateStats(
                tracks = tracks,
                sessions = sessions,
                timeframe = period,
                referenceTimestampMs = System.currentTimeMillis()
            )
        }
        calculatedStats = period to result
    }

    LaunchedEffect(rawAchievements, tracks, sessions) {
        withContext(Dispatchers.Default) {
            evaluatedAchievements = GamificationEngine.checkAchievements(rawAchievements, tracks, sessions)
        }
    }

    val unlockedCount = evaluatedAchievements.count { it.isUnlocked }
    val totalCount = evaluatedAchievements.size.coerceAtLeast(1)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(lirixColors.appBackground)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Header (48dp): Title + Period Selector Dropdown
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.wrapped_screen_title),
                    style = LirixMobileTypography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = lirixColors.textPrimary
                )

                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(lirixColors.pillCornerRadius))
                            .background(lirixColors.surfaceCard)
                            .border(1.dp, lirixColors.borderSubtle, RoundedCornerShape(lirixColors.pillCornerRadius))
                            .clickable { isPeriodDropdownExpanded = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = selectedTimeframe.localizedLabel(),
                            style = LirixMobileTypography.labelMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = lirixColors.textPrimary
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(R.string.wrapped_period_select_cd),
                            tint = lirixColors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = isPeriodDropdownExpanded,
                        onDismissRequest = { isPeriodDropdownExpanded = false },
                        modifier = Modifier.background(lirixColors.surfaceElevated)
                    ) {
                        for (tf in AnalyticsTimeframe.entries) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = tf.localizedLabel(),
                                        style = LirixMobileTypography.bodyMedium,
                                        color = if (tf == selectedTimeframe) lirixColors.accentPrimary else lirixColors.textPrimary
                                    )
                                },
                                onClick = {
                                    selectedTimeframe = tf
                                    isPeriodDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Native motion prototype: reveal real facts without changing their values or layout.
        item {
            val currentStats = stats
            when {
                currentStats == null -> Text(
                    text = stringResource(R.string.wrapped_gathering_stats),
                    style = LirixMobileTypography.bodyMedium,
                    color = lirixColors.textSecondary
                )
                currentStats.totalListeningTimeMs == 0L && currentStats.topTracks.isEmpty() -> Text(
                    text = stringResource(R.string.wrapped_empty_stats),
                    style = LirixMobileTypography.bodyMedium,
                    color = lirixColors.textSecondary
                )
                else -> WrappedMotionSummary(
                    stats = currentStats,
                    progress = { reveal.value }
                )
            }
        }

        // 3. Honest Facts Section (Typography list, no SaaS cards)
        if (stats != null) item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(lirixColors.cardCornerRadius))
                    .background(lirixColors.surfaceCard)
                    .border(1.dp, lirixColors.borderSubtle, RoundedCornerShape(lirixColors.cardCornerRadius))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val uniqueTracks = stats?.uniqueTracksCount ?: 0
                val coverageRatio = stats?.lyricsCoverageRatio ?: 0f
                val viewedWithLyrics = (uniqueTracks * coverageRatio).roundToInt()
                // lyrics count formatted with stringResource

                FactRow(label = stringResource(R.string.wrapped_fact_unique_tracks), value = "$uniqueTracks")
                HorizontalDivider(color = lirixColors.borderSubtle.copy(alpha = 0.5f), thickness = 1.dp)
                FactRow(label = stringResource(R.string.wrapped_fact_saved_lyrics), value = stringResource(R.string.wrapped_saved_lyrics_count, viewedWithLyrics))
            }
        }

        // 4. Export CTA Button (44dp high, adaptive styling)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(lirixColors.cardCornerRadius))
                    .background(lirixColors.accentPrimary)
                    .clickable(enabled = stats != null) { showShareDialog = true }
                    .semantics { role = Role.Button },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = if (lirixColors.isDark) Color(0xFF181B20) else Color(0xFFF2EFE9),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.wrapped_share_button),
                        style = LirixMobileTypography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (lirixColors.isDark) Color(0xFF181B20) else Color(0xFFF2EFE9)
                    )
                }
            }
        }

        // 5. Minimalist Achievements Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.wrapped_achievements_title, unlockedCount, totalCount),
                    style = LirixMobileTypography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = lirixColors.textPrimary
                )
            }
        }

        // 6. Compact Achievements List (48dp row height, thin progress, no gold gradients)
        items(evaluatedAchievements) { ach ->
            MinimalAchievementRow(achievement = ach)
        }
    }
}

@Composable
private fun WrappedMotionSummary(
    stats: WrappedStats,
    progress: () -> Float
) {
    val colors = LirixThemeTokens.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            modifier = Modifier.graphicsLayer { alpha = (progress() * 3f).coerceIn(0f, 1f) },
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(stringResource(R.string.wrapped_time_in_music), style = LirixMobileTypography.labelLarge, color = colors.textSecondary)
            Text(
                text = "${stats.totalListeningTimeMs / 60000L}",
                style = LirixMobileTypography.titleLarge.copy(
                    fontFamily = LiterataFontFamily, fontSize = 56.sp, lineHeight = 68.sp
                ),
                color = colors.accentPrimary
            )
            Text(stringResource(R.string.wrapped_minutes_listening), style = LirixMobileTypography.bodyMedium, color = colors.textPrimary)
        }
        Column(
            modifier = Modifier.graphicsLayer { alpha = (progress() * 3f - 1f).coerceIn(0f, 1f) },
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(stringResource(R.string.wrapped_top_artist), style = LirixMobileTypography.labelLarge, color = colors.textSecondary)
            Text(
                text = stats.topArtists.firstOrNull()?.artist ?: stringResource(R.string.wrapped_artist_unknown),
                style = LirixMobileTypography.titleLarge.copy(fontFamily = LiterataFontFamily),
                color = colors.textPrimary
            )
        }
        Column(
            modifier = Modifier.graphicsLayer { alpha = (progress() * 3f - 2f).coerceIn(0f, 1f) },
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.wrapped_top_tracks), style = LirixMobileTypography.labelLarge, color = colors.textSecondary)
            if (stats.topTracks.isEmpty()) {
                Text(stringResource(R.string.wrapped_no_tracks), style = LirixMobileTypography.bodyMedium, color = colors.textSecondary)
            }
            stats.topTracks.take(3).forEachIndexed { index, track ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "0${index + 1}",
                        style = LirixMobileTypography.titleMedium,
                        color = colors.accentPrimary,
                        modifier = Modifier.width(32.dp)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(track.title, style = LirixMobileTypography.titleMedium, color = colors.textPrimary)
                        Text(track.artist, style = LirixMobileTypography.bodyMedium, color = colors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun FactRow(
    label: String,
    value: String
) {
    val lirixColors = LirixThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = LirixMobileTypography.bodyMedium,
            color = lirixColors.textSecondary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            style = LirixMobileTypography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = lirixColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MinimalAchievementRow(achievement: AchievementEntity) {
    val lirixColors = LirixThemeTokens.colors
    val isUnlocked = achievement.isUnlocked

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(lirixColors.cardCornerRadius))
            .background(lirixColors.surfaceCard)
            .border(1.dp, lirixColors.borderSubtle, RoundedCornerShape(lirixColors.cardCornerRadius))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status Icon: Check or Lock
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isUnlocked) lirixColors.accentPrimary.copy(alpha = 0.15f)
                    else lirixColors.surfaceElevated
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isUnlocked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.wrapped_achievement_unlocked_cd),
                    tint = lirixColors.accentPrimary,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = stringResource(R.string.wrapped_achievement_locked_cd),
                    tint = lirixColors.textMuted,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        // Title and description
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = achievement.title,
                    style = LirixMobileTypography.titleSmall.copy(
                        fontWeight = if (isUnlocked) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = if (isUnlocked) lirixColors.textPrimary else lirixColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!isUnlocked && achievement.maxProgress > 1) {
                    Text(
                        text = "${achievement.currentProgress} / ${achievement.maxProgress}",
                        style = LirixMobileTypography.labelSmall,
                        color = lirixColors.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = achievement.description,
                style = LirixMobileTypography.bodySmall,
                color = lirixColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!isUnlocked && achievement.maxProgress > 1) {
                Spacer(modifier = Modifier.height(3.dp))
                val fraction = (achievement.currentProgress.toFloat() / achievement.maxProgress.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp)),
                    color = lirixColors.accentPrimary,
                    trackColor = lirixColors.surfaceElevated
                )
            }
        }
    }
}

@Composable
private fun AnalyticsTimeframe.localizedLabel(): String = when (this) {
    AnalyticsTimeframe.TODAY -> stringResource(R.string.wrapped_period_today)
    AnalyticsTimeframe.WEEK -> stringResource(R.string.wrapped_period_week)
    AnalyticsTimeframe.MONTH -> stringResource(R.string.wrapped_period_month)
    AnalyticsTimeframe.ALL_TIME -> stringResource(R.string.wrapped_period_all_time)
}

private fun pluralizeWord(count: Int, one: String, few: String, many: String): String {
    val n = kotlin.math.abs(count) % 100
    val n1 = n % 10
    return when {
        n in 11..19 -> many
        n1 == 1 -> one
        n1 in 2..4 -> few
        else -> many
    }
}
