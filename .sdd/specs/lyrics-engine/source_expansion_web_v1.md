# SOURCE-EXPANSION-WEB-01: Lyrsense и Akkords.pro

Статус: исследование HTTP/DOM завершено; 2026-10-21, Europe/Moscow. Владелец отдельных адаптеров: energy_review. Общая интеграция Android/desktop: energy_thesis. Root согласует итог.

## Наблюдения live HTTP

Обычные HTTPS GET без cookie, ключей, выполнения JavaScript и обхода блокировок вернули HTTP 200. Повторная проверка страниц с User-Agent `Lirix/1.4 source-probe` также вернула 200. Это текущая доступность, не гарантия дальнейшей доступности и не лицензия на перепубликацию. В артефактах исследования сохраняются только метаданные, количества строк и URL.

| Источник | Запрос artist + title | Найденный URL | Проверка страницы |
| --- | --- | --- | --- |
| Lyrsense | Radiohead Creep | https://lyrsense.com/radiohead/creep | artist Radiohead, title Creep; 34 пары строк, автор Инна Заикина |
| Lyrsense | Adele Hello | https://lyrsense.com/adele/hello_adele | artist Adele, title Hello; 58 пар строк, автор t-ro |
| Akkords.pro | Radiohead Creep | https://akkords.pro/songs/radiohead/creep/ | breadcrumb artist Radiohead; title Creep; 33 chord spans, 72 непустых строки |
| Akkords.pro | Кино Группа крови | https://akkords.pro/songs/kino/gruppa-krovi/ | breadcrumb artist Кино (Виктор Цой); title Группа крови; 34 chord spans, 64 непустых строки |

Отрицательный запрос для каждого источника: `ZZZLirixNoSuchArtist726 QQQNoSuchSong726`, HTTP 200 и 0 song-кандидатов. Результат — Missing, не сетевой сбой.

`https://lyrsense.com/robots.txt` запрещает /admin, /moderator, /api/getPageState; исследованный поиск и страницы песен не перечислены. `https://akkords.pro/robots.txt` содержит User-agent:* и sitemap. Непубличные API не используются.

## Контракты Lyrsense

Новые файлы: `shared/src/jvmShared/kotlin/com/lirix/app/translations/LyrsenseTranslationProvider.kt` и при необходимости отдельный `LyrsenseTranslationParser.kt`. Публичные функции соответствуют существующим Amalgama/LyricsTranslate: `canonicalUrl`, `parse(bytes,url,charset)`, `load(url)`, `find(title,artist,refresh=false): TranslationSearchResult`. Inject HTTP reader/connection factory для детерминированных тестов.

1. `buildSearchUrl(title,artist)` формирует `https://lyrsense.com/search?s=` + UTF-8 URL-encoded `primaryArtist + " " + cleanedTitle`. Не угадывать slug. Оба поля обязательны; unknown artist → Missing.
2. `parseSearchCandidates(html,url)` выбирает `li.element.radioFilterElement`. Название — прямой `a[href]`, исполнитель — прямой `span`. URL должен после resolve пройти same-host allowlist и иметь два непустых path segment. Удалять дубликаты URL. В live поиске Adele результат: `<li class="element radioFilterElement radioElem_en"><a href="/adele/hello_adele">Hello</a><span>Adele</span></li>`.
3. `parse` проверяет artist в `.brArtistLink [itemprop=name]`, original title в `#fr_title`, translated title в `#ru_title`, canonical link в `link[rel=canonical]`. Нельзя брать первый произвольный `[itemprop=name]`: на странице есть также album/schema элементы.
4. Строки — `#songFlexLines > .songTextLine`; внутри каждой брать `.songTextLine__original` и `.songTextLine__translation`, сохраняя DOM порядок. Не zip двух независимо отфильтрованных массивов. Пустые строки допустимы как разделители (Creep: 42 row nodes, 34 непустых пар; Hello: 69/58). Не включать notes/footnotes/author/sidebar. `sup` footnote markers удалять из клона строки до текста. Отсутствие контейнера/перевода → parse failure, не lyrics body fallback.
5. Автор — `#author_var1 > span`, ссылка — `#author_var1 a[href^='/authors/']`. В live: Инна Заикина `/authors/inna_zaikina`; t-ro `/authors/t_ro`. Значение author не заменять словом «Страница автора». `SongTranslation`: language=ru, sourceName=Lyrsense, sourceUrl=validated canonical, translator=имя, blocks=пары, aligned=true только для структурно параллельных строк. Не объявлять LRC: timestamps отсутствуют.
6. При нескольких вариантах перевода парсить только видимый исходный блок var1 и его автора. Не загружать скрытые API. Не соединять строки/авторов различных версий.

## Контракты Akkords.pro

Новые файлы: `shared/src/jvmShared/kotlin/com/lirix/app/feature/lyrics/AkkordsProProvider.kt`, `AkkordsProParser.kt`. Предлагаемые типы: `AkkordsProCandidate(title,artist,url)` и `AkkordsProDocument(title,artist,sourceUrl,author,plainLyrics,chords)`. Документ содержит раздельные plain/chords и provenance, не timestamps. Общая интеграция выбирает стабильный ID `builtin:akkords-pro`.

1. `buildSearchUrl(title,artist)` → `https://akkords.pro/?s=` + UTF-8 query. Поиск серверный, не WordPress API.
2. `parseSearchCandidates` ограничивает область каждой `.apx-song-card`: исполнитель `.apx-song-card__artist`, название/URL `.apx-song-card__post-link`. Удалить только известный терминальный суффикс `: аккорды для гитары`. URL песни имеет ровно `/songs/{artist}/{song}/`, каталоги `/songs/{artist}/` исключаются.
3. `parse` повторно проверяет title из `h1.entry-title`, artist из `#kadence-breadcrumbs a` с href path ровно `/songs/{artist}/`. Не выбирать каталог `/songs/` и не доверять artist из search. Сверить canonical link; редирект к другой песне не становится совпадением.
4. Контент — `article .entry-content p.chords`. Live страницы используют реальные newline text nodes, 0 `br`, 0 `pre`. Нельзя использовать `.text()` с collapse whitespace: это разрушит строки и позиции аккордов. Использовать `wholeText()` либо DOM traversal TextNode/getWholeText + br→newline, сохранять пробелы/NBSP→space и LF. Chords включают chord spans и текст/section labels. Для plain исключать только `span`, чей полный текст валидируется как аккорд/последовательность аккордов; исключать оставшиеся chord-only строки. Не удалять весь текст generic span без проверки. Не смешивать изображения аппликатур, комментарии и author box с документом.
5. Атрибуция: `.entry-author-name` (обе страницы: Диванный гитарист), sourceName=Akkords.pro и URL страницы. Текущий LyricsResult не имеет отдельного sourceUrl/author: integration owner обязан сохранить provenance в согласованном контракте; не прятать URL внутрь lyrics/chords и не терять при Room cache.

## Совпадения, отказы, лимиты

- Переиспользовать `TranslationMetadata.primaryArtist/title/artistKey/titleKey`, чтобы убрать presentation suffixes и сравнить пунктуацию/регистр. Музыкальные версии live/acoustic/remix сохраняются: `Creep (Live)` не auto-match студийной Creep. Artist aliases вроде Кино (Виктор Цой) допустимы через существующий artistKey, не substring/fuzzy match произвольных артистов.
- Единственный точный search artist+title допускает загрузку страницы, но Found возможен только после повторного exact page artist+title. Страница другого исполнителя с тем же title отвергается. Несколько точных вариантов — Candidates для translation либо отсутствие auto-selection для chords; не брать первый.
- Для ru translation Lyrsense добавляется отдельным провайдером `SongTranslationProviders`; не преобразовывать перевод в оригинальный plainLyrics. Для иных target languages Lyrsense не запускается.
- Искать один раз; максимум 3 HTTP GET на источник за запрос: search + не более 2 detail pages. Не обходить пагинацию автоматически. Общий deadline ≤15s, connect ≤4s/read ≤6s, body ≤2MB, максимум 2 same-host HTTPS redirects. Каждую redirect destination валидировать до запроса. Host exact, port default, userInfo отсутствует; запретить URL fragment/query у song pages. Не использовать cookie/auth/bypass.
- 404/пустой exact search → Missing; 403/429/challenge, timeout, unexpected markup → source unavailable с bounded fallback. CancellationException всегда прокидывать. Не повторять тот же источник внутри одного lookup. 429 уважать Retry-After; negative cache на нормализованную пару 15 минут, недоступность источника cooldown минимум 60s, bounded LRU. force refresh не должен обходить Retry-After.

## Атомарные функции и проверки для кодера

1. Lyrsense URL validator и search URL builder; unit fixtures на host/path/redirect rejection.
2. Lyrsense search parser; fixtures двух положительных примеров, empty/malformed и mismatch artist/title/version.
3. Lyrsense page parser; синтетические строки вместо сохранения полного copyrighted текста; проверить author, schema selector, пустые строки, missing translated column, footnotes, var1 boundary.
4. Lyrsense HTTP loader/find, injectable reader; unique verified Found, ambiguous Candidates, ru-only, timeout/cancel/body cap/429.
5. Akkords search/page URL validators/builders/parsers; две метаданные positive fixtures, empty search, catalog link rejection, wrong page artist/title, version mismatch.
6. Akkords text extraction; synthetic chord spans, LF, indentation, NBSP, section names, plain/chords separation, author/provenance. Полный текст live песен в fixtures не сохранять.
7. Integration owner: SongTranslationProviders routing/fallback + Android/desktop chord source ordering/rejected IDs/cache attribution. Адаптеры не меняют существующие файлы интеграции самостоятельно.

Ограничение: проверены две актуальные страницы на источник; старые шаблоны/нестандартные версии и наличие всех исполнителей не доказаны. Лицензирование/условия коммерческого использования необходимо оценить отдельно до массовой перепубликации; сетевой доступ и robots.txt этого не решают.
