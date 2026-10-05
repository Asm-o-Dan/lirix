# Задача TASK-LYR-04-C: Декларативный движок правил CustomRuleLyricsProvider и интеграция в каскад

- **ID задачи:** `TASK-LYR-04-C`
- **Роль исполнителя:** Кодер
- **Зона:** `lyrics-engine`
- **Файлы:**
  1. `app/src/main/java/com/eventengine/app/feature/lyrics/CustomRuleLyricsProvider.kt` (парсинг JSON-правила, HTTP-поиск и извлечение текста без eval)
  2. `app/src/main/java/com/eventengine/app/feature/LyricsProvider.kt` (интеграция каскада пользовательских правил в `AggregatedLyricsProvider`)
  3. `app/src/test/java/com/eventengine/app/CustomRuleLyricsProviderTest.kt` (unit-тесты парсинга, селекторов и каскада)
- **Спека:** [.sdd/architecture_lyr_community.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/architecture_lyr_community.md) (Раздел 4.4, 3.2), [.sdd/intake/LYR-COMMUNITY.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/intake/LYR-COMMUNITY.md) (FR-3, FR-4)
- **Приоритет:** HIGH (Движок исполнения пользовательских правил)

---

## 1. Назначение и контекст

Пользовательские правила генерируются в режиме обучения (Teach Mode) или импортируются из комьюнити. Правило представляет собой **строго декларативный JSON** (без исполняемого JavaScript, без `eval`, безопасность по умолчанию).
Движок `CustomRuleLyricsProvider` должен:
1. Десериализовать JSON-манифест правила.
2. Выполнить поиск песни на сайте (по шаблону `searchUrlTemplate` и селектору ссылок).
3. Извлечь контент целевой страницы по селектору `contentSelector`.
4. Удалить мусорные блоки по списку `stripSelectors` (баннеры, кнопки, скрипты).
5. Нормализовать переводы строк (`<br>`, `</p>` $\to$ `\n`) и декодировать HTML-сущности.
6. Встроиться в `AggregatedLyricsProvider` перед встроенными провайдерами LRCLIB и AmDm с учетом отклонений (`rejectedSourceIds`).

---

## 2. Спецификация изменений

### 2.1 Конфигурация правила `CustomRuleLyricsProvider.kt`

```kotlin
package com.lirix.app.feature.lyrics

import com.lirix.app.feature.HttpTextClient
import com.lirix.app.feature.JsonHelper
import com.lirix.app.feature.LyricsProvider
import com.lirix.app.feature.LyricsResult
import com.lirix.app.feature.LyricsSourceIds
import com.lirix.app.storage.CustomLyricsRuleEntity
import com.lirix.app.storage.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder

/**
 * Parsed configuration of a declarative JSON lyrics rule.
 */
data class CustomRuleConfig(
    val domain: String,
    val name: String,
    val searchUrlTemplate: String? = null,
    val searchResultSelector: String? = null,
    val contentSelector: String,
    val stripSelectors: List<String> = emptyList(),
    val chordsSelector: String? = null,
    val lineBreakStrategy: String = "PRESERVE_BR"
)

/**
 * Provider that executes a declarative JSON rule against web endpoints.
 * Strictly non-executable: interprets only CSS selectors, tags, and string replacements.
 */
class CustomRuleLyricsProvider(
    val ruleEntity: CustomLyricsRuleEntity,
    private val httpGet: suspend (String) -> String? = { url -> HttpTextClient.get(url) }
) : LyricsProvider {

    val config: CustomRuleConfig = parseConfig(ruleEntity.ruleJson, ruleEntity.domain, ruleEntity.name)

    override suspend fun getLyrics(track: TrackEntity): LyricsResult {
        return getLyrics(track, emptySet(), false)
    }

    override suspend fun getLyrics(
        track: TrackEntity,
        rejectedSourceIds: Set<String>,
        forceNetwork: Boolean
    ): LyricsResult {
        val canonicalId = "rule:${ruleEntity.id}"
        if (canonicalId in rejectedSourceIds) {
            return LyricsResult(false, "", ruleEntity.name, sourceId = canonicalId)
        }

        val searchTemplate = config.searchUrlTemplate ?: return LyricsResult(false, "", ruleEntity.name, sourceId = canonicalId)
        val query = "${track.artist} ${track.title}".trim()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = searchTemplate
            .replace("{query}", encodedQuery)
            .replace("{artist}", URLEncoder.encode(track.artist, "UTF-8"))
            .replace("{title}", URLEncoder.encode(track.title, "UTF-8"))

        val searchHtml = httpGet(searchUrl) ?: return LyricsResult(false, "Ошибка сетевого запроса к ${config.domain}", ruleEntity.name, sourceId = canonicalId)

        val candidateUrl = resolveSongUrl(searchHtml, config.searchResultSelector, track.title, track.artist, config.domain)
            ?: return LyricsResult(false, "Песня не найдена на ${config.domain}", ruleEntity.name, sourceId = canonicalId)

        val songHtml = httpGet(candidateUrl) ?: return LyricsResult(false, "Ошибка загрузки страницы песни", ruleEntity.name, sourceId = canonicalId)

        val extractedText = extractContent(songHtml, config.contentSelector, config.stripSelectors)
        if (extractedText.isNullOrBlank() || extractedText.length < 30) {
            return LyricsResult(false, "Текст не извлечен по селектору", ruleEntity.name, sourceId = canonicalId)
        }

        return LyricsResult(
            hasLyrics = true,
            lyricsText = extractedText,
            source = ruleEntity.name,
            plainLyrics = extractedText,
            sourceId = canonicalId
        )
    }

    companion object {
        fun parseConfig(jsonStr: String, defaultDomain: String, defaultName: String): CustomRuleConfig {
            val json = JSONObject(jsonStr)
            val searchObj = json.optJSONObject("search")
            val contentObj = json.optJSONObject("content") ?: json

            val stripList = mutableListOf<String>()
            val stripArray = contentObj.optJSONArray("stripSelectors")
            if (stripArray != null) {
                for (i in 0 until stripArray.length()) {
                    stripList.add(stripArray.getString(i))
                }
            }

            return CustomRuleConfig(
                domain = json.optString("domain", defaultDomain),
                name = json.optString("name", defaultName),
                searchUrlTemplate = searchObj?.optString("urlTemplate"),
                searchResultSelector = searchObj?.optString("resultListSelector"),
                contentSelector = contentObj.optString("selector"),
                stripSelectors = stripList,
                chordsSelector = contentObj.optString("chordsSelector").takeIf { it.isNotBlank() },
                lineBreakStrategy = contentObj.optString("lineBreakStrategy", "PRESERVE_BR")
            )
        }

        fun extractContent(html: String, contentSelector: String, stripSelectors: List<String>): String? {
            // 1. Извлечение контейнера по селектору (поддержка tag#id, tag.class, itemprop)
            var block = extractContainerBySelector(html, contentSelector) ?: return null

            // 2. Удаление блоков stripSelectors (реклама, скрипты, кнопки)
            for (strip in stripSelectors) {
                block = removeStripElements(block, strip)
            }

            // 3. Нормализация переносов строк и тегов
            return cleanHtmlToPlainLyrics(block)
        }

        fun cleanHtmlToPlainLyrics(rawHtml: String): String {
            return rawHtml
                .replace(Regex("""(?i)<br\s*/?>"""), "\n")
                .replace(Regex("""(?i)</p>"""), "\n\n")
                .replace(Regex("""(?i)</div>"""), "\n")
                .replace(Regex("""<[^>]+>"""), "") // Удаление остальных тегов
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replace(Regex("""\n{3,}"""), "\n\n")
                .trim()
        }

        fun extractContainerBySelector(html: String, selector: String): String? {
            // Эвристика извлечения блока по селектору
            val cleanSel = selector.trim()
            if (cleanSel.startsWith("#")) {
                val id = cleanSel.removePrefix("#")
                return FallbackLyricsScraper.extractBalancedTags(html, "id=\"$id\"").firstOrNull()
            }
            if (cleanSel.startsWith(".")) {
                val cls = cleanSel.removePrefix(".")
                return FallbackLyricsScraper.extractBalancedTags(html, "class=\"$cls\"").firstOrNull()
                    ?: FallbackLyricsScraper.extractBalancedTags(html, cls).firstOrNull()
            }
            if (cleanSel.contains("itemprop=")) {
                val marker = cleanSel.replace("\"", "")
                return FallbackLyricsScraper.extractBalancedTags(html, marker).firstOrNull()
            }
            return FallbackLyricsScraper.extractBalancedTags(html, cleanSel).firstOrNull()
        }

        fun removeStripElements(html: String, stripSelector: String): String {
            val tag = stripSelector.replace(Regex("""[^a-zA-Z0-9_-]"""), "")
            return if (tag.isNotBlank()) {
                html.replace(Regex("""(?si)<$tag\b[^>]*>.*?</$tag>"""), "")
            } else {
                html
            }
        }

        fun resolveSongUrl(
            html: String,
            selector: String?,
            title: String,
            artist: String,
            domain: String
        ): String? {
            // Поиск ссылок, содержащих совпадение по названию/артисту
            val linkRegex = Regex("""<a\s+[^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)
            val matches = linkRegex.findAll(html)
            for (match in matches) {
                val href = match.groupValues[1]
                val text = match.groupValues[2]
                if (text.contains(title, ignoreCase = true) || text.contains(artist, ignoreCase = true)) {
                    return if (href.startsWith("http")) href else "https://$domain${if (href.startsWith("/")) "" else "/"}$href"
                }
            }
            return matches.firstOrNull()?.groupValues?.get(1)?.let { href ->
                if (href.startsWith("http")) href else "https://$domain${if (href.startsWith("/")) "" else "/"}$href"
            }
        }
    }
}
```

### 2.2 Интеграция в `AggregatedLyricsProvider.kt`

В класс `AggregatedLyricsProvider` передавать `lyricsDao: LyricsDao? = null`:
- В методе `getLyrics(track, rejectedSourceIds, forceNetwork)`:
  - Перед опросом LRCLIB (шаг 3) добавить шаг **2.5: Опрос активных пользовательских правил**:
    ```kotlin
    val activeRules = lyricsDao?.getActiveRules().orEmpty()
    for (rule in activeRules) {
        val ruleSourceId = "rule:${rule.id}"
        if (ruleSourceId !in rejectedSourceIds) {
            val ruleProvider = CustomRuleLyricsProvider(rule)
            val ruleResult = ruleProvider.getLyrics(track, rejectedSourceIds, forceNetwork)
            if (ruleResult.hasLyrics && ruleResult.sourceId !in rejectedSourceIds) {
                onLyricsDiscovered?.invoke(track.trackKey, ruleResult.plainLyrics, ruleResult.syncedLyrics)
                return ruleResult
            }
        }
    }
    ```

---

## 3. Критерии приемки (DoD)

1. **Unit-тесты в `CustomRuleLyricsProviderTest.kt`:**
   - `test_parse_valid_json_config`: корректная десериализация всех полей `CustomRuleConfig`.
   - `test_extract_content_and_strip_ads`: контейнер `#lyrics` извлекается, теги `<script>`, `.ads` удаляются, `<br>` преобразуются в переводы строк.
   - `test_cascade_executes_custom_rule_before_lrclib`: если активное правило находит текст, оно возвращается с `sourceId = "rule:<id>"` без вызова LRCLIB.
   - `test_rejected_rule_is_skipped`: если `rule:<id>` входит в `rejectedSourceIds`, движок пропускает его и переходит к следующему правилу или LRCLIB.
2. Все тесты (`.\gradlew.bat test`) завершаются со статусом SUCCESS.
