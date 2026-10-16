package com.lirix.app.ui

import android.graphics.BitmapFactory
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lirix.app.analytics.AnalyticsTimeframe
import com.lirix.app.analytics.GamificationEngine
import com.lirix.app.analytics.TimeOfDaySlot
import com.lirix.app.analytics.WrappedStats
import com.lirix.app.analytics.WrappedStatsEngine
import com.lirix.app.feature.share.PosterStyle
import com.lirix.app.feature.share.PosterThemeSnapshot
import com.lirix.app.feature.share.PosterArtworkLoader
import com.lirix.app.feature.share.ShareCardGenerator
import com.lirix.app.feature.share.ShareManager
import com.lirix.app.feature.share.WrappedShareSection
import com.lirix.app.ingestion.MediaSessionCollector
import com.lirix.app.storage.AchievementEntity
import com.lirix.app.storage.AppDatabase
import com.lirix.app.ui.components.ShareFormatDialog
import com.lirix.app.ui.theme.GolosTextFontFamily
import com.lirix.app.ui.theme.LirixMobileTypography
import com.lirix.app.ui.theme.LirixThemeTokens
import com.lirix.app.ui.theme.LiterataFontFamily
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Editorial WrappedScreen (SPEC-UI-MOBILE-2.0 / ADR-023 / TASK-UI-MOB-05).
 *
 * Replaces generic SaaS tiles with literary editorial typography:
 * 1. Header (48dp): Title "Итоги прослушиваний" + period dropdown [ За месяц ▾ ].
 * 2. Typography Essay: Lead sentence in Literata Italic (18sp); rest of essay in upright Literata Regular/Medium.
 * 3. Honest facts list: Music time, unique tracks, viewed with lyrics, favorite track.
 * 4. Export CTA (44dp): Adaptive share poster matching active dark/light theme (Graphite/Paper).
 * 5. Minimalist achievements: Compact progress list without pseudo-gold gradients.
 */
@Composable
fun WrappedScreen() {
    val lirixColors = LirixThemeTokens.colors
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }

    val tracks by db.musicDao().observeRecentTracks().collectAsStateWithLifecycle(initialValue = emptyList())
    val sessions by db.musicDao().observeAllSessions().collectAsStateWithLifecycle(initialValue = emptyList())
    val rawAchievements by db.achievementDao().observeAchievements().collectAsStateWithLifecycle(initialValue = emptyList())
    val livePlayback by MediaSessionCollector.livePlaybackFlow.collectAsStateWithLifecycle(initialValue = null)

    var selectedTimeframe by remember { mutableStateOf(AnalyticsTimeframe.MONTH) }
    var calculatedStats by remember { mutableStateOf<Pair<AnalyticsTimeframe, WrappedStats>?>(null) }
    val stats = calculatedStats?.takeIf { it.first == selectedTimeframe }?.second
    var evaluatedAchievements by remember { mutableStateOf<List<AchievementEntity>>(emptyList()) }
    var showShareDialog by remember { mutableStateOf(false) }
    var isPeriodDropdownExpanded by remember { mutableStateOf(false) }

    if (showShareDialog && stats != null) {
        val currentStats = stats!!
        ShareFormatDialog(
            onDismissRequest = { showShareDialog = false },
            onFormatSelected = { format ->
                val exportTimeframe = selectedTimeframe
                val exportTheme = PosterThemeSnapshot.fromColors(lirixColors)
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
                    artworkByTrackKey = artwork
                ) }
                check(ShareManager.shareBitmap(
                    context = context,
                    bitmap = cardBitmap,
                    chooserTitle = "Итоги прослушиваний (${exportTimeframe.labelRu.lowercase()})"
                )) { "Не удалось открыть меню отправки" }
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
                referenceTimestampMs = System.currentTimeMillis(),
                livePlayback = livePlayback
            )
        }
        calculatedStats = period to result
    }

    LaunchedEffect(livePlayback?.isPlaying, selectedTimeframe, tracks, sessions) {
        val period = selectedTimeframe
        if (livePlayback?.isPlaying == true) {
            while (isActive) {
                delay(3000L)
                val result = withContext(Dispatchers.Default) {
                    WrappedStatsEngine.calculateStats(
                        tracks = tracks,
                        sessions = sessions,
                        timeframe = period,
                        referenceTimestampMs = System.currentTimeMillis(),
                        livePlayback = livePlayback
                    )
                }
                calculatedStats = period to result
            }
        }
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
                    text = "Итоги прослушиваний",
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
                        val periodLabel = when (selectedTimeframe) {
                            AnalyticsTimeframe.TODAY -> "За сегодня"
                            AnalyticsTimeframe.WEEK -> "За 7 дней"
                            AnalyticsTimeframe.MONTH -> "За 30 дней"
                            AnalyticsTimeframe.ALL_TIME -> "За всё время"
                        }
                        Text(
                            text = periodLabel,
                            style = LirixMobileTypography.labelMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = lirixColors.textPrimary
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Выбрать период",
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
                            val tfLabel = when (tf) {
                                AnalyticsTimeframe.TODAY -> "За сегодня"
                                AnalyticsTimeframe.WEEK -> "За 7 дней"
                                AnalyticsTimeframe.MONTH -> "За 30 дней"
                                AnalyticsTimeframe.ALL_TIME -> "За всё время"
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = tfLabel,
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

        // 2. Typography Essay: Lead sentence in Literata Italic, rest in upright Literata Regular
        item {
            val essayText = buildEssayAnnotatedString(stats, selectedTimeframe, lirixColors.textPrimary)
            Text(
                text = essayText,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 3. Honest Facts Section (Typography list, no SaaS cards)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(lirixColors.cardCornerRadius))
                    .background(lirixColors.surfaceCard)
                    .border(1.dp, lirixColors.borderSubtle, RoundedCornerShape(lirixColors.cardCornerRadius))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val totalMinutes = (stats?.totalListeningTimeMs ?: 0L) / 60000L
                val totalHours = totalMinutes / 60L
                val remMinutes = totalMinutes % 60L
                val timeFormatted = if (totalHours > 0) {
                    val hStr = pluralizeWord(totalHours.toInt(), "час", "часа", "часов")
                    val mStr = pluralizeWord(remMinutes.toInt(), "минута", "минуты", "минут")
                    "$totalHours $hStr $remMinutes $mStr"
                } else {
                    val mStr = pluralizeWord(remMinutes.toInt(), "минута", "минуты", "минут")
                    "$remMinutes $mStr"
                }

                val uniqueTracks = stats?.uniqueTracksCount ?: tracks.size
                val coverageRatio = stats?.lyricsCoverageRatio ?: 0f
                val viewedWithLyrics = (uniqueTracks * coverageRatio).roundToInt()
                val songsWord = pluralizeWord(viewedWithLyrics, "песня", "песни", "песен")

                val topTrack = stats?.topTracks?.firstOrNull()
                val favoriteTrackFormatted = if (topTrack != null) {
                    "${topTrack.artist} — ${topTrack.title} (×${topTrack.playCount})"
                } else {
                    "Пока нет данных"
                }

                FactRow(label = "Время в музыке", value = timeFormatted)
                HorizontalDivider(color = lirixColors.borderSubtle.copy(alpha = 0.5f), thickness = 1.dp)
                FactRow(label = "Уникальных треков", value = "$uniqueTracks")
                HorizontalDivider(color = lirixColors.borderSubtle.copy(alpha = 0.5f), thickness = 1.dp)
                FactRow(label = "С сохранённым текстом", value = "$viewedWithLyrics $songsWord")
                HorizontalDivider(color = lirixColors.borderSubtle.copy(alpha = 0.5f), thickness = 1.dp)
                FactRow(label = "Любимый трек", value = favoriteTrackFormatted)
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
                        text = "Поделиться карточкой итогов",
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
                    text = "Достижения ($unlockedCount из $totalCount)",
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
                    contentDescription = "Выполнено",
                    tint = lirixColors.accentPrimary,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Заблокировано",
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

/**
 * Builds the typographic essay:
 * - First lead sentence: Literata Italic (18sp).
 * - Rest of the text: Upright Literata Regular (16sp, line height 24sp).
 */
private fun buildEssayAnnotatedString(
    stats: WrappedStats?,
    timeframe: AnalyticsTimeframe,
    primaryColor: Color
): androidx.compose.ui.text.AnnotatedString {
    if (stats == null) return buildAnnotatedString { append("Считаем итоги…") }
    val totalHours = (stats?.totalListeningTimeMs ?: 0L) / 3600000L
    val periodStr = when (timeframe) {
        AnalyticsTimeframe.TODAY -> "сегодня"
        AnalyticsTimeframe.WEEK -> "за последнюю неделю"
        AnalyticsTimeframe.MONTH -> "за последние 30 дней"
        AnalyticsTimeframe.ALL_TIME -> "за всё время"
    }

    val hoursWord = pluralizeWord(totalHours.toInt(), "час", "часа", "часов")
    val leadSentence = if (totalHours > 0) {
        "За ${periodStr.removePrefix("за ")} вы провели в музыке $totalHours $hoursWord."
    } else {
        if ((stats?.totalListeningTimeMs ?: 0L) > 0L) {
            "Вы провели в музыке ${(stats?.totalListeningTimeMs ?: 0L) / 60000L} минут $periodStr."
        } else {
            "За выбранный период пока нет прослушиваний."
        }
    }

    // Time-of-day peak analysis (honest facts)
    val distribution = stats?.timeOfDayDistribution.orEmpty()
    val peakEntry = distribution.maxByOrNull { it.value }
    val peakPercent = ((peakEntry?.value ?: 0f) * 100f).roundToInt()
    val peakDesc = if (peakEntry != null && peakEntry.value > 0.50f) {
        when (peakEntry.key) {
            TimeOfDaySlot.NIGHT -> "Чаще всего вы включали музыку ночью ($peakPercent% запусков)."
            TimeOfDaySlot.MORNING -> "Чаще всего вы включали музыку утром ($peakPercent% запусков)."
            TimeOfDaySlot.AFTERNOON -> "Чаще всего вы включали музыку днём ($peakPercent% запусков)."
            TimeOfDaySlot.EVENING -> "Чаще всего вы включали музыку вечером ($peakPercent% запусков)."
        }
    } else {
        if (distribution.isEmpty()) "" else "Выраженного пика по числу запусков нет."
    }

    // Top artists
    val topArtists = stats?.topArtists.orEmpty().take(2).map { it.artist }
    val artistCompanion = if (topArtists.isNotEmpty()) {
        "Главные исполнители — ${topArtists.joinToString(" и ")}."
    } else {
        ""
    }

    val remainder = if (artistCompanion.isNotEmpty()) " $peakDesc $artistCompanion" else " $peakDesc"

    return buildAnnotatedString {
        withStyle(
            style = SpanStyle(
                fontFamily = LiterataFontFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = primaryColor
            )
        ) {
            append(leadSentence)
        }
        withStyle(
            style = SpanStyle(
                fontFamily = LiterataFontFamily,
                fontStyle = FontStyle.Normal,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = primaryColor
            )
        ) {
            append(remainder)
        }
    }
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
