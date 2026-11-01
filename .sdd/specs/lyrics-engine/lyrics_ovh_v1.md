# lyrics.ovh plain fallback v1

Статус: контракт адаптера; wiring согласуется с общей integration spec. Дата: 2026-10-21.

## Доказательства и границы

Документация: https://github.com/NTag/lyrics.ovh — `GET https://api.lyrics.ovh/v1/{artist}/{title}`, JSON `lyrics`, отсутствие — HTTP 404. Текущий README описывает LRCLIB как первый upstream самого сервиса; это дополнительный plain fallback, не независимый LRC backend.

Нативные `Invoke-WebRequest` проверки (тела/тексты не сохранены): Adele / Hello — HTTP 200, 1854 символа, 404ms; The Beatles / Yesterday — HTTP 200, 711 символов, 309ms; LirixNoSuchArtist9fb31 / LirixNoSuchSong9fb31 — HTTP 404, 0 символов lyrics, 777ms. Root отдельно подтвердил Coldplay / Yellow — 200, 1089 символов. Первые попытки в sandbox вернули HttpRequestException; результаты выше получены с разрешенным сетевым доступом. API возвращает только текст, без artist/title identity: независимо подтвердить соответствие ответа невозможно; используем точный endpoint пары, без fuzzy search.

## Файлы и функции

- `shared/src/jvmShared/kotlin/com/lirix/app/feature/lyrics/AdditionalLyricsResult.kt`: DTO `AdditionalLyricsResult(sourceId, sourceName, plainLyrics?, syncedLyricsLrc?, chordsText?)`; только данные, никакой записи в БД.
- `shared/src/jvmShared/kotlin/com/lirix/app/feature/lyrics/LyricsSourceHttp.kt`: общий ограниченный HTTP transport; согласованный владелец — исполнитель lyrics.ovh. Политика на конкретный источник передается явно.
- `shared/src/jvmShared/kotlin/com/lirix/app/feature/lyrics/LyricsOvhProvider.kt`: `class LyricsOvhProvider(fetch: suspend (String) -> String? = nativeBoundedFetch)`; `suspend fun find(title: String, artist: String): AdditionalLyricsResult?`.
- `desktop/src/test/kotlin/com/lirix/app/desktop/LyricsOvhProviderTest.kt`: offline synthetic fixtures, без copyrighted full lyrics и без сети.
- Integration owner: Android `app/src/main/java/com/lirix/app/feature/LyricsProvider.kt` (`LyricsSourceIds`, `AggregatedLyricsProvider.getLyrics`); Desktop `desktop/src/main/kotlin/com/lirix/app/desktop/lyrics/DesktopLyricsService.kt` (`lookupAutomatic`, `canonicalSourceId`). До взаимного review integration plan эти файлы адаптером не меняются.

## Контракт адаптера

1. Trim artist/title; отсутствие любого — null, ноль HTTP. Сохранять версию/live/remaster и Unicode, не применять агрессивный generic cleaner.
2. URI path segments кодируются отдельно UTF-8 (включая `/`, `?`, `#`, `%`, `+`, пробел). Host фиксирован `api.lyrics.ovh`, HTTPS. Один GET `/v1/{artist}/{title}`, без `/suggest` и поисковых вариантов.
3. Только JSON object с непустым строковым `lyrics`; trim по краям, нормализовать CRLF. Отсутствующее поле/null/число/blank/HTML/ошибка JSON — null. Ответ с `error` не принимать как lyrics.
4. Успех: `sourceId="builtin:lyrics-ovh"`, `sourceName="lyrics.ovh"`, `plainLyrics=text`, `syncedLyricsLrc=null`, `chordsText=null`. Даже строки вида `[00:10]` внутри ответа не объявляются LRC. Никаких искусственных таймкодов.
5. Не кэшировать и не сохранять из adapter. Не изменять заметки, переводы, USER/USER:EDIT, LRC и аккорды.

## Бюджет, ошибки, отмена

- Один HTTP request на вызов; no retries и no parallel hedging. Для ovh: total deadline 4s, connect <=1500ms, read <=2500ms, decoded body <=256KiB; HTTP policy общего helper может быть шире для HTML, но ovh использует stricter policy.
- 404 — обычный miss. 403/429 — miss + общий per-host cooldown >=60s; subsequent calls в cooldown не выполняют HTTP. 5xx/IO/timeout — miss без retries. Не логировать body и полный текст.
- Отмена caller распространяется как CancellationException, не превращается в miss; native transport закрывает connection/input stream и останавливает IO worker. `runInterruptible` без disconnect не считать гарантией прерывания HttpURLConnection. Редиректы только HTTPS и в allowlist, <=3; для ovh предпочтительно не следовать редиректам.

## Integration gates

Новый источник вызывается только после промаха более приоритетного LRC lookup; найденные synced lyrics никогда не заменяются plain. Custom sources и manual selection сохраняют прежний приоритет. Disabled/отклоненный `builtin:lyrics-ovh` — ноль HTTP, даже при force refresh. Стабильный id проходит через результат, cache, rejection и Desktop canonicalSourceId, без свертки в builtin:web. В текущем коде нет универсального persisted enable flag для hardcoded builtins: integration owner обязан определить явный доступный gate, а не обещать работу существующего rule toggle для unrelated builtin adapter.

Перед записью caller повторно сверяет текущий trackKey и существующий manual provider; adapter не решает гонки БД. Android сейчас в NowPlayingScreen:492–510 читает кеш и затем сохраняет fetched result отдельным шагом; это не атомарная защита concurrent manual save. Новый путь не должен обходить согласованную manual-safe persistence.

## Обязательные offline тесты

Exact endpoint/segment encoding; Unicode/slashes; missing artist/title -> zero fetch; valid plain success + correct id; null/empty/non-string/HTML/malformed/error JSON -> miss; no fake LRC/chords; injected CancellationException escapes; timeout -> miss; HTTP byte cap, allowlist/redirect cap/cooldown/cancel disconnect в transport tests. Integration: disabled/rejected -> zero requests; LRCLIB hit skips ovh; miss advances fallback; USER:EDIT/concurrent manual save preserved; returned ovh provenance survives cache/rejection на обеих платформах. Live smoke отделен от unit tests и не доказывает покрытие каталога.
