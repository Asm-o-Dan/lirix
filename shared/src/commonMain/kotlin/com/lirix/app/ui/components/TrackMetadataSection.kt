package com.lirix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lirix.app.storage.TrackMetadataEntity
import com.lirix.app.storage.TrackMetadataStatus
import com.lirix.app.ui.i18n.LirixStrings
import com.lirix.app.ui.theme.LirixThemeTokens
import kotlin.math.roundToInt

/** What the track info dialog shows about stored metadata; pure, so it can be tested without UI. */
data class TrackMetadataView(
    /** Plain "label: value" lines, already localized. */
    val facts: List<String>,
    /** Label to percent 0..100. */
    val meters: List<Pair<String, Int>>,
    /** Why there is nothing to show, or where the data came from. */
    val note: String?
)

object TrackMetadataPresenter {
    private val keys = listOf("C", "C♯", "D", "E♭", "E", "F", "F♯", "G", "A♭", "A", "B♭", "B")
    private val sourceNames = mapOf("itunes" to "iTunes", "deezer" to "Deezer", "reccobeats" to "ReccoBeats", "lastfm" to "Last.fm", "musicbrainz" to "MusicBrainz")

    fun keyName(key: Int?, mode: Int?): String? {
        val name = keys.getOrNull(key ?: return null) ?: return null
        return when (mode) {
            1 -> LirixStrings.message("{0} мажор", name)
            0 -> LirixStrings.message("{0} минор", name)
            else -> name
        }
    }

    fun sourceList(sources: String): List<String> = sources.split(';').flatMap { it.substringAfter('=', "").split('+') }
        .mapNotNull { sourceNames[it.trim()] }.distinct()

    fun view(entity: TrackMetadataEntity?, enabled: Boolean): TrackMetadataView {
        fun empty(text: String) = TrackMetadataView(emptyList(), emptyList(), LirixStrings.message(text))
        if (entity == null) return empty(
            if (enabled) "Данные о треке появятся после 20 секунд прослушивания"
            else "Загрузка данных о треке выключена. Включить её можно в настройках."
        )
        when (entity.status) {
            TrackMetadataStatus.NOT_FOUND.name -> return empty("В каталогах этот трек не найден")
            TrackMetadataStatus.AMBIGUOUS.name -> return empty("Найдено несколько разных записей с таким названием; данные не взяты")
        }
        val facts = buildList {
            entity.genreList().takeIf { it.isNotEmpty() }?.let { add(LirixStrings.message("Жанр: {0}", it.joinToString(", "))) }
            entity.releaseYear?.let { add(LirixStrings.message("Год выпуска: {0}", it)) }
            entity.durationMs?.takeIf { it > 0 }?.let { ms ->
                val seconds = (ms + 500) / 1000
                add(LirixStrings.message("Длительность: {0}", "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"))
            }
            entity.bpm?.let { add(LirixStrings.message("Темп: {0} уд/мин", it.roundToInt())) }
            keyName(entity.musicalKey, entity.musicalMode)?.let { add(LirixStrings.message("Тональность: {0}", it)) }
            entity.tagList().takeIf { it.isNotEmpty() }?.let { add(LirixStrings.message("Теги слушателей: {0}", it.joinToString(", "))) }
        }
        val meters = listOf(
            "Энергия" to entity.energy, "Позитивность" to entity.valence, "Танцевальность" to entity.danceability,
            "Акустичность" to entity.acousticness, "Инструментальность" to entity.instrumentalness,
            "Живое исполнение" to entity.liveness, "Речь" to entity.speechiness
        ).mapNotNull { (label, value) -> value?.let { LirixStrings.message(label) to (it * 100).roundToInt().coerceIn(0, 100) } }
        val names = sourceList(entity.sources)
        return TrackMetadataView(facts, meters,
            names.takeIf { it.isNotEmpty() }?.let { LirixStrings.message("Данные каталогов: {0}. Оценки звучания рассчитаны автоматически и могут ошибаться.", it.joinToString(", ")) })
    }
}

@Composable
fun TrackMetadataSection(entity: TrackMetadataEntity?, enabled: Boolean, modifier: Modifier = Modifier) {
    val c = LirixThemeTokens.colors
    val view = TrackMetadataPresenter.view(entity, enabled)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(LirixStrings.message("Данные о треке"), color = c.textPrimary, fontWeight = FontWeight.SemiBold)
        view.facts.forEach { Text(it, color = c.textSecondary) }
        view.meters.forEach { (label, percent) ->
            Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$label: $percent/100" },
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("$label: $percent/100", color = c.textSecondary)
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.borderSubtle)) {
                    Box(Modifier.fillMaxWidth(percent / 100f).fillMaxHeight().background(c.accentPrimary))
                }
            }
        }
        view.note?.let { Text(it, color = c.textSecondary, modifier = Modifier.semantics { }) }
    }
}
