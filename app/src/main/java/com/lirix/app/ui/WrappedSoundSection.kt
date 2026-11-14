package com.lirix.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lirix.app.R
import com.lirix.app.analytics.PeriodMood
import com.lirix.app.analytics.SoundRecord
import com.lirix.app.analytics.SoundRecordKind
import com.lirix.app.analytics.SoundSummary
import com.lirix.app.analytics.SoundSummaryEngine
import com.lirix.app.analytics.SoundTrait
import com.lirix.app.ui.theme.LirixMobileTypography
import com.lirix.app.ui.theme.LirixThemeTokens
import com.lirix.app.ui.theme.LiterataFontFamily

/**
 * "How the period sounded": mood, sound meters, genres, decades and records of the selected period.
 * Shows only what [SoundSummaryEngine] could conclude; otherwise explains what is missing.
 */
@Composable
fun WrappedSoundSection(summary: SoundSummary, metadataEnabled: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("wrapped-sound"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (summary.isEmpty) {
            SoundCard(stringResource(R.string.wrapped_sound_title)) {
                Text(
                    text = when {
                        !metadataEnabled && summary.tracksWithData == 0 -> stringResource(R.string.wrapped_sound_disabled)
                        summary.tracksListened == 0 -> stringResource(R.string.wrapped_sound_no_listening)
                        else -> stringResource(R.string.wrapped_sound_few, summary.tracksWithData, summary.tracksListened, SoundSummaryEngine.MIN_TRACKS)
                    },
                    style = LirixMobileTypography.bodyMedium, color = LirixThemeTokens.colors.textSecondary
                )
            }
            return@Column
        }
        if (summary.mood != null || summary.meters.isNotEmpty() || summary.averageBpm != null || summary.majorPercent != null) {
            SoundCard(stringResource(R.string.wrapped_sound_title)) {
                val colors = LirixThemeTokens.colors
                summary.mood?.let { mood ->
                    Text(mood.label(), style = LirixMobileTypography.titleLarge.copy(fontFamily = LiterataFontFamily), color = colors.textPrimary)
                }
                summary.meters.forEach { meter -> ShareBar(meter.trait.label(), meter.percent) }
                summary.averageBpm?.let { SoundFact(stringResource(R.string.wrapped_sound_tempo), stringResource(R.string.wrapped_sound_tempo_value, it)) }
                summary.majorPercent?.let { SoundFact(stringResource(R.string.wrapped_sound_major), stringResource(R.string.wrapped_percent, it)) }
                Text(stringResource(R.string.wrapped_sound_note, summary.tracksWithData, summary.tracksListened),
                    style = LirixMobileTypography.bodySmall, color = colors.textMuted)
            }
        }
        if (summary.genres.isNotEmpty()) SoundCard(stringResource(R.string.wrapped_genres_title)) {
            summary.genres.forEach { ShareBar(it.genre, it.percent) }
            Text(stringResource(R.string.profile_genre_coverage, summary.tracksWithGenre, summary.tracksListened),
                style = LirixMobileTypography.bodySmall, color = LirixThemeTokens.colors.textMuted)
        }
        if (summary.decades.isNotEmpty()) SoundCard(stringResource(R.string.wrapped_decades_title)) {
            summary.decades.forEach { ShareBar(stringResource(R.string.wrapped_decade, it.decade), it.percent) }
        }
        if (summary.records.isNotEmpty()) SoundCard(stringResource(R.string.wrapped_records_title)) {
            summary.records.forEach { RecordRow(it) }
        }
    }
}

@Composable
private fun SoundCard(title: String, content: @Composable () -> Unit) {
    val colors = LirixThemeTokens.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(colors.cardCornerRadius)).background(colors.surfaceCard)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(colors.cardCornerRadius)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = LirixMobileTypography.labelLarge, color = colors.textSecondary, modifier = Modifier.semantics { heading() })
        content()
    }
}

/** A labelled share; read by TalkBack as one phrase "label, N percent". */
@Composable
private fun ShareBar(label: String, percent: Int) {
    val colors = LirixThemeTokens.colors
    val value = stringResource(R.string.wrapped_percent, percent)
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$label, $value" }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = LirixMobileTypography.bodyMedium, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(value, style = LirixMobileTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.surfaceElevated)) {
            Box(Modifier.fillMaxWidth(percent.coerceIn(0, 100) / 100f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(colors.accentPrimary))
        }
    }
}

@Composable
private fun SoundFact(label: String, value: String) {
    val colors = LirixThemeTokens.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = LirixMobileTypography.bodyMedium, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = LirixMobileTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
    }
}

@Composable
private fun RecordRow(record: SoundRecord) {
    val colors = LirixThemeTokens.colors
    val value = if (record.kind == SoundRecordKind.FASTEST) stringResource(R.string.wrapped_sound_tempo_value, record.value)
    else stringResource(R.string.wrapped_percent, record.value)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(record.kind.label(), style = LirixMobileTypography.bodySmall, color = colors.textSecondary)
            Text(record.title, style = LirixMobileTypography.titleSmall, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(displayArtist(record.artist, stringResource(R.string.unknown_artist)), style = LirixMobileTypography.bodySmall,
                color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, style = LirixMobileTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.accentPrimary)
    }
}

@Composable
private fun PeriodMood.label(): String = stringResource(when (this) {
    PeriodMood.BRIGHT_DRIVE -> R.string.wrapped_mood_bright_drive
    PeriodMood.DARK_DRIVE -> R.string.wrapped_mood_dark_drive
    PeriodMood.BRIGHT_CALM -> R.string.wrapped_mood_bright_calm
    PeriodMood.DARK_CALM -> R.string.wrapped_mood_dark_calm
})

@Composable
private fun SoundTrait.label(): String = stringResource(when (this) {
    SoundTrait.ENERGY -> R.string.wrapped_trait_energy
    SoundTrait.VALENCE -> R.string.wrapped_trait_valence
    SoundTrait.DANCEABILITY -> R.string.wrapped_trait_danceability
    SoundTrait.ACOUSTICNESS -> R.string.wrapped_trait_acousticness
})

@Composable
private fun SoundRecordKind.label(): String = stringResource(when (this) {
    SoundRecordKind.FASTEST -> R.string.wrapped_record_fastest
    SoundRecordKind.MOST_ENERGETIC -> R.string.wrapped_record_energetic
    SoundRecordKind.CALMEST -> R.string.wrapped_record_calmest
    SoundRecordKind.HAPPIEST -> R.string.wrapped_record_happiest
})

/** Section title of an achievement category; unknown categories fall into "special". */
@Composable
fun achievementCategoryLabel(category: String): String = stringResource(when (category) {
    "LISTENING" -> R.string.achievement_category_listening
    "EXPLORATION" -> R.string.achievement_category_exploration
    "LYRICS" -> R.string.achievement_category_lyrics
    "SOUND" -> R.string.achievement_category_sound
    else -> R.string.achievement_category_special
})

val achievementCategoryOrder = listOf("LISTENING", "EXPLORATION", "SOUND", "LYRICS", "SPECIAL")
