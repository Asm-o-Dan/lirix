# Аудит расширения источников Lirix — 2026-10-21

Проверены текущие исходники Android, Desktop и общих переводов. Продуктовый код не менялся, сборки и тесты не запускались. Наличие реализации ниже не означает доступность сайта сегодня; отдельные endpoint/DOM нужно проверять живыми запросами и сохранять обезличенные fixtures.

## Реально подключенные источники

| Категория | Android | Desktop |
| --- | --- | --- |
| LRC | `lrclib.net/api/get`, `/api/search`; иных встроенных LRC backend нет | те же LRCLIB endpoints; smart search pairs и raw query fallback |
| Тексты | LRCLIB, `textpesni.com`, `amdm.ru`, `mychords.net`, `vse-pesni.com`, `lyrics.lyricfind.com`, `www.amalgama-lab.com`, `genius.com` | LRCLIB, AmDm/MyChords как текст аккордов, Textpesni; комментарий «Genius / Textpesni» шире фактической реализации `fetchFallbackLyrics`, которая вызывает только Textpesni |
| Аккорды | `amdm.ru`, `mychords.net` | `amdm.ru`, `mychords.net` |
| Переводы | общие JVM: `www.amalgama-lab.com` для RU, `lyricstranslate.com` для RU/EN/ES/PT/ZH | те же общие JVM провайдеры |
| Пользовательские источники | активные `CustomLyricsRuleEntity`, ручной Teach Mode | активные `CustomLyricsRuleEntity`, `DesktopCustomSources.inspect/extract/resolve` |

Android: `app/src/main/java/com/lirix/app/feature/LyricsProvider.kt:144` (`LrcLibLyricsProvider`), `:303` (`FallbackLyricsScraper.getLyrics`), `:934` (`AggregatedLyricsProvider`). Desktop: `desktop/src/main/kotlin/com/lirix/app/desktop/lyrics/DesktopLyricsService.kt:227` (`lookupAutomatic`), `:386`/`:408` (LRCLIB), `:429` (`fetchChords`), `:687` (`fetchFallbackLyrics`). Переводы: `shared/src/jvmShared/kotlin/com/lirix/app/translations/SongTranslationProviders.kt:6`.

## Порядок поиска и HTTP/DOM

- Android `AggregatedLyricsProvider.getLyrics` (`LyricsProvider.kt:947`): track cache → user notes → активные declarative rules → LRCLIB → fallback scraper → offline adapter. После rule/LRCLIB дополнительно получает аккорды AmDm/MyChords через `fetchChordTabs` (`:1060`). `FallbackLyricsScraper.getLyrics` (`:303`) последовательно пробует Textpesni, AmDm, MyChords, Vse-Pesni, LyricFind, Amalgama, Genius; прекращает после первого успеха.
- Android `HttpTextClient.get` (`LyricsProvider.kt:107`): `HttpURLConnection`, GET, connect 6s/read 8s, redirects, UTF-8, custom headers. Универсального автоматического browser/JS fallback нет. Ограничения размера ответа и общей длительности каскада в этом helper нет. При расширении не размножать длинные последовательные таймауты.
- `CustomRuleLyricsProvider.getLyrics` (`app/src/main/java/com/lirix/app/feature/lyrics/CustomRuleLyricsProvider.kt:42`) использует тот же HTTP, `extractContent` (`:161`) и собственный ограниченный разбор selectors/balanced tags (`:192`). Это не полный CSS DOM parser. `resolveSongUrl` (`:283`) фактически не использует аргумент `selector`, допускает совпадение title ИЛИ artist и fallback на первую ссылку. `chordsSelector`/`lineBreakStrategy` парсятся в config, но `getLyrics` не извлекает отдельные аккорды и не возвращает LRC. Новые сложные встроенные источники через этот механизм без доработки не подключать.
- Android `app/src/main/java/com/lirix/app/ui/TeachModeScreen.kt:397` создает интерактивный WebView; JS inspector инжектируется в `onPageFinished` (`:428`). Это ручное обучение/выбор DOM, не headless fallback обычного `getLyrics`. Не включать WebView для каждого фонового поиска.
- Desktop `DesktopLyricsService.httpGetWithHeaders` (`:779`): `HttpURLConnection`, connect 7s/read 8s, redirects, UTF-8. Встроенные AmDm/MyChords парсеры проверяют соответствие названия/артиста и `verifyChordsAgainstLyrics` (`:524`). Не терять эту валидацию при добавлении новых сайтов.
- Desktop `desktop/src/main/kotlin/com/lirix/app/desktop/lyrics/DesktopCustomSources.kt:19` (`document`) использует Jsoup, timeout 10s, max 2 MB. `extractHtml` (`:32`) поддерживает CSS/strip selectors; `resolve` (`:59`) ищет same-domain ссылку по title; без search template требует точного `trackTitle`/`trackArtist`. Полноценного JS browser fallback нет.
- Общие переводы уже используют Jsoup и более строгую сеть: `LyricsTranslateProvider.readPage` (`shared/src/jvmShared/kotlin/com/lirix/app/translations/LyricsTranslateProvider.kt:111`) ограничивает host/redirects, HTML type, 2 MB, connect 8s/read 12s; 403/429 включает минутный cooldown. `find` (`:55`) получает POST форму поиска с hidden fields и фильтрует точный artist/title, неоднозначные результаты возвращает candidates. `AmalgamaTranslationProvider.find` (`.../AmalgamaTranslationProvider.kt:87`) ищет каталоги, `readPage` (`:116`) ограничивает загрузки. `SongTranslationProviders.load` (`:10`) маршрутизирует LyricsTranslate явно, прочие URL направляет Amalgama; новый домен должен получить явную ветку и свой canonical URL validator.

## Сохранение, отказы и ручные правки

- `shared/src/commonMain/kotlin/com/lirix/app/storage/MusicEntities.kt:80`: один `LyricsCacheEntity` на trackKey, поля plain/LRC/chords/notes и один `provider`. Уже можно хранить новые источники без миграции, но сейчас нельзя независимо выразить provenance текстов и аккордов разных сайтов. Не подменять provider аккордов provider-ом текста без явного решения.
- `LyricsDao.saveLyrics` (`shared/src/commonMain/kotlin/com/lirix/app/storage/LyricsDao.kt:31`) — REPLACE без встроенной защиты `USER`. Android callers должны сохранять собственные проверки; нельзя заявлять, что USER защищен на уровне DAO. `MusicFeatureEngine.prefetchLyricsAsync` (`app/src/main/java/com/lirix/app/feature/MusicFeatureEngine.kt:227`) учитывает кеш и rejected ids; на уровне этого пути требуется сохранить проверки при расширении.
- Desktop `DesktopLyricsService.onTrackChanged` (`:113`) отдает USER приоритет даже при forceRefresh. `saveUserLyrics` (`:185`) отменяет и дожидается активного lookup; `acceptAutomatic` (`:126`) проверяет актуальность track и повторно сверяет USER после сохранения. `DesktopLibraryRepository.saveLyrics` дополнительно защищает USER. Не обходить эти точки прямой записью нового провайдера в БД.
- Desktop также имеет memory/disk cache (`saveToDiskCache :724`, `loadFromDiskCache :743`); provenance и rejection нужно сохранять в каждом слое. `canonicalSourceId` (`:210`) сейчас объединяет AmDm/MyChords в `builtin:amdm`, а Textpesni помечается `builtin:web`. Для каждого нового источника использовать отдельный стабильный id; иначе «не тот текст» начнет отключать несколько несвязанных backend.
- `CustomLyricsRuleEntity` (`MusicEntities.kt:163`) уже содержит enabled/priority/builtIn/version, активные правила сортируются в `LyricsDao.getActiveRules` (`LyricsDao.kt:64`). Codec Android: `app/src/main/java/com/lirix/app/feature/lyrics/ScraperRuleBundleCodec.kt:178` и `:327`; Desktop: `desktop/.../lyrics/DesktopRuleBundles.kt:18`/`:26`. Импортированная конфигурация — данные, не исполняемый код.
- Переводы хранятся отдельно: `shared/src/commonMain/kotlin/com/lirix/app/translations/SongTranslation.kt:23`, ключ track+language+sourceUrl, JSON document с sourceName/translator/blocks. `SongTranslationDao.saveAutomaticIfEmpty` (`:39`) не затирает ручной выбор. `AutomaticSongTranslations.resolve` (`.../AutomaticSongTranslations.kt:49`) проверяет выбранный язык, ограничивает поиск 35s, кеширует missing 15min/errors 1min и сохраняет автоматический результат только при отсутствии пользовательского.
- LRC нельзя синтезировать из plain или перевода. Для нового backend сохранять только действительные timestamps и проверять формат через существующий `LrcSyncEngine.parseLrcLinesStrict`.

## Тесты, которые нужно расширить

| Зона | Существующие тесты |
| --- | --- |
| Android цепочки, parsing и rejection | `app/src/test/java/com/lirix/app/LyricsAndTrackParserTest.kt`, `LyricsRejectionCascadeTest.kt`, `CustomRuleLyricsProviderTest.kt`, `ScraperRuleBundleCodecTest.kt` |
| Общие аккорды | `shared/src/commonTest/kotlin/com/lirix/app/MyChordsParserTest.kt`, `ChordsTabContentTest.kt` |
| Desktop match/verification | `desktop/src/test/kotlin/com/lirix/app/desktop/DesktopLyricsServiceTest.kt` (в т.ч. exact artist+title, mismatched text rejection), `DesktopCustomSourcesTest.kt` |
| Сохранение и гонки Desktop | `desktop/src/test/kotlin/com/lirix/app/desktop/DesktopOfflineLyricsTest.kt` (USER vs force refresh, rejection across caches, cancellation on switch, concurrent manual save) |
| Переводы | `desktop/src/test/kotlin/com/lirix/app/desktop/SongTranslationTest.kt`, `SongTranslationPreviewTest.kt`, `AutomaticTranslationTest.kt`, `MultilingualTranslationTest.kt`, `LyricsTranslateParserTest.kt`; Android `SongTranslationMigrationTest.kt` |

Новый backend: fixtures search/page, точный hit, чужой artist с таким же title, empty/anti-bot HTML, UTF-8 entities/line breaks, rejected source skipped without HTTP, cancellation/stale track, USER preserved, plain never becomes fake LRC. Проверять fallback, когда новый сайт недоступен. Сетевые smoke проверки отделять от offline unit tests.

## Минимальный путь расширения

1. Сначала добиться паритета Desktop с уже работающими Android источниками, если их DOM/HTTP проверен: Desktop реально имеет только Textpesni в fallback. Это дает расширение без придумывания еще одного общего framework.
2. Для 1–2 новых простых сайтов добавить компактные HTTP+parse adapters в существующий `jvmShared` (его уже используют обе платформы для переводов), передавать HTTP function для fixtures. Тонко включить в обе текущие цепочки; возвращать содержимое+стабильный source id, не писать в БД из провайдера. Не создавать новый registry/DSL/DI framework для двух адаптеров.
3. Для перевода добавить отдельный provider рядом с Amalgama/LyricsTranslate и явную маршрутизацию load/findLanguage. Использовать существующие `SongTranslation` и candidates вместо конвертации перевода в plain lyrics.
4. Ограничить число кандидатов/запросов и общий бюджет поиска, учитывать 403/429. Пользовательские rules остаются прежде встроенных. До принятия результата проверить identity и пригодность контента; никаких догадок по первой ссылке.

## Дополняющие кандидаты (не обещание доступности endpoint)

- **Lyrsense — переводы на русский:** официальный FAQ описывает типы переводов и языковые разделы: https://lyrsense.com/faq . Дополняет RU Amalgama, потребует отдельного parser оригинал/перевод+translator. Поиск/страница/условия автоматического получения еще требуют проверки root.
- **lyrics.ovh — обычные тексты через HTTP API:** официальная документация https://lyricsovh.docs.apiary.io/ . Подходит как небольшой plain fallback после LRCLIB; не источник аккордов или LRC. Наличие документации подтверждено, live API response пока не проверен.
- **HolyChords.pro — нишевые христианские песни и аккорды:** сайт прямо описывает каталог, поиск и транспонирование: https://holychords.pro/about . Полезен только для соответствующего репертуара; HTTP search/page и artist identity надо проверить до включения.

Для **дополнительного независимого LRC backend** в этом аудите не найден подтвержденный интеграционный контракт без неофициальных клиентских API/ключей. В первой итерации разумно улучшить match/search LRCLIB (duration, версия/live/remaster, строгая проверка artist/title) и импорт реального LRC. Сайт с plain lyrics не объявлять LRC источником ради количества источников.

## Последующее исследование и реализация

Предыдущие разделы фиксируют исходное состояние до расширения. После отдельного задания root подтвержден публичный HTTPS KuGou search/download без аккаунта/cookies/статического ключа: Coldplay/Yellow266s →44 timestamped lines, Radiohead/Creep238s →42; отсутствующая пара →0candidates. Временный accesskey выдается самим публичным поиском, используется только для download и не сохраняется/не логируется. Это genuine LRC backend; lyrics.ovh, в отличие от него, возвращает только plain.

Реализованы четыре дополнения по `.sdd/specs/lyrics-engine/source_expansion_integration_v1.md`: lyrics.ovh, KuGou, Akkords.pro, Lyrsense. Стабильные IDs: `builtin:lyrics-ovh`, `builtin:kugou`, `builtin:akkords-pro`; перевод Lyrsense хранится в существующем SongTranslation. LRCLIB plain-only допускает дополнение реальными KuGou timestamps, а при miss остается plain. Добавлены Room13→14 nullable attribution JSON и защита manual `USER`/`USER:EDIT` при автоматической записи. Независимый review: `.sdd/reports/SOURCE_INTEGRATION_REVIEW_20261021.md`.

Runtime проверки выполняет root после общего freeze: deterministic suites и opt-in `SourceExpansionLiveTest` (`LIRIX_SOURCE_LIVE=1`), фактические Kotlin adapters по2known+1missing для каждого источника. До результатов root не считать native Kotlin live проверенным. По уточнению пользователя author/source links показываются только через меню «Инфо о треке», не в главном плеере/reading UI.
