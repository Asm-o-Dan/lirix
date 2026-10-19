package com.lirix.app.translations

import org.jsoup.Jsoup
import java.net.URI

/** Parses verified page identity without inferring line alignment. */
object LyricsTranslateParser {
    private val languages = mapOf("Russian" to "ru", "English" to "en", "Spanish" to "es", "Portuguese" to "pt", "Chinese" to "zh")

    fun canonicalUrl(input: String): String {
        val uri = URI(input.trim())
        require(uri.scheme == "https" && uri.host in setOf("lyricstranslate.com", "www.lyricstranslate.com") &&
            uri.port == -1 && uri.rawUserInfo == null && uri.rawQuery == null &&
            uri.path.matches(Regex("/en/[\\p{L}\\p{N}_-]+(?:\\.html(?:-[0-9]+)?)?")) &&
            uri.path.substringAfterLast('/') !in setOf("songs", "artists", "languages.html", "faq")) { "Нужна HTTPS-ссылка на перевод LyricsTranslate" }
        return URI("https://lyricstranslate.com${uri.rawPath}").toASCIIString()
    }

    fun parse(bytes: ByteArray, url: String, expectedLanguage: String? = null): SongTranslation {
        require(bytes.size <= 2_000_000) { "Страница слишком большая" }
        require(expectedLanguage == null || expectedLanguage in languages.values) { "Язык перевода не поддерживается" }
        val canonical = canonicalUrl(url)
        val dom = Jsoup.parse(bytes.inputStream(), null, canonical)
        val heading = dom.selectFirst("h1")?.text().orEmpty()
        val languageName = Regex(" - ([A-Za-z]+) translation$").find(heading)?.groupValues?.get(1)
        val language = languages[languageName] ?: error("Не удалось подтвердить язык перевода")
        require(expectedLanguage == null || language == expectedLanguage) { "Язык страницы не совпадает с выбранным" }
        val title = dom.selectFirst("[data-panel=original] .translate__title")?.text().orEmpty()
        val artist = dom.selectFirst(".top__artists a.top__tag")?.text().orEmpty()
        require(title.isNotBlank() && artist.isNotBlank()) { "Не удалось подтвердить песню и исполнителя" }
        val translated = dom.selectFirst("#translation-body")?.wholeText()?.trim().orEmpty()
        require(translated.isNotBlank()) { "На странице нет текста перевода" }
        val original = dom.selectFirst("#original-lyrics")?.wholeText()?.trim().orEmpty()
        val translator = dom.select(".details__flex-block").firstOrNull {
            it.selectFirst(".details__title")?.text()?.trim() == "Translation details"
        }?.selectFirst(".row-details__tag")?.text().orEmpty()
        val warnings = dom.select(".notice-warning .notice-warning__text").map { it.text() }.filter { it.isNotBlank() }.distinct()
        // Independent complete texts: the source does not guarantee matching line counts.
        return SongTranslation(canonical, title, artist,
            dom.selectFirst("[data-panel=translation] .translate__title")?.text().orEmpty(),
            translator, listOf(TranslationBlock(original, translated)), language,
            sourceName = "LyricsTranslate", aligned = false, warnings = warnings)
            .also { TranslationDocuments.encode(it) }
    }
}
