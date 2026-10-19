package com.lirix.app.translations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI

class AmalgamaTranslationProvider(private val openConnection: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection }) {
    companion object {
        const val MAX_BYTES = 2_000_000
        fun canonicalUrl(input: String): String {
            val uri = try { URI(input.trim()) } catch (_: Exception) { throw IllegalArgumentException("Некорректная ссылка") }
            require(uri.scheme.equals("https", true) && uri.host?.lowercase() in setOf("www.amalgama-lab.com", "amalgama-lab.com") &&
                uri.port == -1 && uri.rawUserInfo == null && uri.rawQuery == null &&
                uri.rawPath?.matches(Regex("/songs/[a-zA-Z0-9_/-]+\\.html")) == true && !uri.path.contains("//")) {
                "Нужна HTTPS-ссылка на страницу песни amalgama-lab.com/songs/…"
            }
            return "https://www.amalgama-lab.com${uri.rawPath}"
        }

        fun parse(bytes: ByteArray, url: String, charset: String? = null): SongTranslation {
            require(bytes.size <= MAX_BYTES) { "Страница слишком большая" }
            val canonical = canonicalUrl(url)
            val doc = Jsoup.parse(ByteArrayInputStream(bytes), charset, canonical)
            val originalHeading = doc.selectFirst("h2.original") ?: error("На странице не найден заголовок песни")
            val translatedHeading = doc.selectFirst("h2.translate") ?: error("На странице не найден перевод")
            val title = originalHeading.ownText().trim()
            val artist = originalHeading.selectFirst("span")?.text().orEmpty().removePrefix("(").removeSuffix(")").removePrefix("оригинал ").trim()
            val translator = translatedHeading.selectFirst("a")?.text()?.trim().orEmpty().ifBlank {
                translatedHeading.selectFirst("span")?.text().orEmpty().removePrefix("(").removeSuffix(")").removePrefix("перевод ").trim()
            }
            val blocks = doc.select("#click_area .string_container").map { row ->
                val original = row.selectFirst(".original")?.wholeText()?.trim().orEmpty()
                val translated = row.selectFirst(".translate")?.wholeText()?.trim().orEmpty()
                TranslationBlock(original, translated, original.startsWith("[") && original.endsWith("]"))
            }.filter { it.original.isNotBlank() || it.translated.isNotBlank() }
            return SongTranslation(canonical, title, artist, translatedHeading.ownText().trim(), translator, blocks)
                .also { TranslationDocuments.encode(it) }
        }
    }

    suspend fun load(url: String): SongTranslation = runInterruptible(Dispatchers.IO) {
        var next = canonicalUrl(url)
        repeat(4) {
            val connection = openConnection(URI(next))
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8_000
                connection.readTimeout = 12_000
                connection.setRequestProperty("User-Agent", "Lirix/1.4 song-translation-import")
                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location") ?: error("Некорректное перенаправление")
                    next = canonicalUrl(URI(next).resolve(location).toString())
                } else {
                    require(status == 200) { "Источник недоступен (HTTP $status)" }
                    require(connection.contentType.orEmpty().contains("text/html", true)) { "Источник вернул не HTML-страницу" }
                    require(connection.contentLengthLong <= MAX_BYTES) { "Страница слишком большая" }
                    val bytes = connection.inputStream.use { stream ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            if (Thread.currentThread().isInterrupted) throw InterruptedException()
                            val count = stream.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= MAX_BYTES) { "Страница слишком большая" }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    val charset = Regex("charset\\s*=\\s*[\"']?([a-zA-Z0-9_-]+)", RegexOption.IGNORE_CASE)
                        .find(connection.contentType.orEmpty())?.groupValues?.get(1)
                    return@runInterruptible parse(bytes, next, charset)
                }
            } finally { connection.disconnect() }
        }
        error("Слишком много перенаправлений")
    }
}
