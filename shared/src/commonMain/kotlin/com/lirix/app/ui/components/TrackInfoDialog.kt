package com.lirix.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lirix.app.storage.*
import com.lirix.app.translations.*
import com.lirix.app.ui.theme.LirixThemeTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine

data class TrackInfoSource(val kind: String, val source: String, val author: String?, val url: String?)

/** Presentation only: no source lookup or network request is performed by track information. */
fun trackInfoSources(cache: LyricsCacheEntity?, translations: List<SongTranslationEntity>): List<TrackInfoSource> {
    fun name(id: String): String = when (id.lowercase()) {
        "builtin:lrclib", "lrclib" -> "LRCLIB"
        "builtin:amdm", "amdm" -> "AmDm"
        "builtin:mychords", "mychords" -> "MyChords"
        "builtin:akkords-pro" -> "Akkords.pro"
        "builtin:lyrics-ovh" -> "lyrics.ovh"
        "builtin:kugou" -> "KuGou"
        else -> if (isManualLyricsProvider(id)) com.lirix.app.ui.i18n.LirixStrings.message("Сохранено вами") else id
    }
    val attribution = LyricsAttribution.decode(cache?.sourceAttributionJson)
    val sources = mutableListOf<TrackInfoSource>()
    fun add(kind: String, value: LyricsSourceAttribution?, hasContent: Boolean) {
        val id = value?.sourceId ?: cache?.provider?.takeIf { hasContent && it.isNotBlank() } ?: return
        val knownChordProvider = id.lowercase() in setOf("builtin:amdm", "amdm", "builtin:mychords", "mychords",
            "builtin:akkords-pro", "akkords.pro", "amdm / mychords") || isManualLyricsProvider(id)
        val sourceName = if (kind == com.lirix.app.ui.i18n.LirixStrings.message("Аккорды") && value == null && !knownChordProvider) com.lirix.app.ui.i18n.LirixStrings.message("Источник не сохранён") else name(id)
        sources += TrackInfoSource(kind, sourceName, value?.author?.takeIf { it.isNotBlank() }, value?.sourceUrl)
    }
    add(com.lirix.app.ui.i18n.LirixStrings.message("Текст"), attribution.lyrics, !cache?.plainLyrics.isNullOrBlank() || !cache?.syncedLyricsLrc.isNullOrBlank())
    add(com.lirix.app.ui.i18n.LirixStrings.message("Аккорды"), attribution.chords, !cache?.chordsAmDm.isNullOrBlank())
    translations.take(8).forEach { row ->
        val document = runCatching { TranslationDocuments.decode(row.documentJson) }.getOrNull() ?: return@forEach
        sources += TrackInfoSource(com.lirix.app.ui.i18n.LirixStrings.message("Перевод · {0}", TranslationLanguages.labels[document.language] ?: document.language),
            document.sourceName, document.translator.takeIf { it.isNotBlank() }, document.sourceUrl)
    }
    return sources.distinct()
}

fun trackInfoPublicUrl(value: String?): String? = value?.takeIf {
    it.length <= 4000 && Regex("https://[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?(?::443)?(?:/[^\\s]*)?").matches(it) &&
        !Regex("[?&](?:accesskey|token|secret)=", RegexOption.IGNORE_CASE).containsMatchIn(it)
}

@Composable
fun TrackInfoDialog(track: TrackEntity, lyricsDao: LyricsDao, translationsDao: SongTranslationDao,
                    language: String = "ru", onDismiss: () -> Unit) {
    val c = LirixThemeTokens.colors
    val uri = LocalUriHandler.current
    var sources by remember(track.trackKey, language) { mutableStateOf<List<TrackInfoSource>>(emptyList()) }
    var loading by remember(track.trackKey, language) { mutableStateOf(true) }
    var error by remember(track.trackKey, language) { mutableStateOf<String?>(null) }
    LaunchedEffect(track.trackKey, lyricsDao, translationsDao, language) {
        try {
            combine(lyricsDao.observeLyrics(track.trackKey), translationsDao.observe(track.trackKey, language)) { cache, saved ->
                trackInfoSources(cache, saved)
            }.collect { currentSources ->
                sources = currentSources
                loading = false
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { loading = false; error = com.lirix.app.ui.i18n.LirixStrings.message("Не удалось прочитать информацию об источниках") }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(com.lirix.app.ui.i18n.LirixStrings.message("Инфо о треке"), color = c.textPrimary) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(track.title, color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                Text(track.artist, color = c.textSecondary)
                if (track.album.isNotBlank()) Text(track.album, color = c.textSecondary)
                HorizontalDivider(color = c.borderSubtle)
                when {
                    loading -> Text(com.lirix.app.ui.i18n.LirixStrings.message("Загружаем информацию…"), color = c.textSecondary)
                    sources.isEmpty() -> Text(com.lirix.app.ui.i18n.LirixStrings.message("Сведения об источниках пока не сохранены"), color = c.textSecondary)
                }
                sources.forEach { source ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(source.kind, color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                        Text(com.lirix.app.ui.i18n.LirixStrings.message("Источник: {0}", source.source), color = c.textSecondary)
                        source.author?.let { Text(com.lirix.app.ui.i18n.LirixStrings.message("Автор: {0}", it), color = c.textSecondary) }
                        trackInfoPublicUrl(source.url)?.let { url ->
                            SelectionContainer { Text(url, color = c.textSecondary) }
                            TextButton(onClick = {
                                runCatching { uri.openUri(url) }.onFailure { error = com.lirix.app.ui.i18n.LirixStrings.message("Не удалось открыть источник") }
                            }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Открыть источник")) }
                        }
                    }
                }
                error?.let { Text(it, color = c.textSecondary) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Закрыть")) } },
        containerColor = c.surfaceElevated
    )
}
