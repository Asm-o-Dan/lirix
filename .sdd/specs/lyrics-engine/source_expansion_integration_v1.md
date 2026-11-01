# SOURCE-EXPANSION-INTEGRATION-01

2026-10-21. Scope: lyrics.ovh plain, KuGou LRC, Akkords.pro chords/plain, Lyrsense RU translation. Существующие caches, manual edits и rejection сохраняются. Нового UI/registry/DSL нет.

## API и ownership

- energy_antithesis: новые shared JVM `AdditionalLyricsResult.kt`, `LyricsSourceHttp.kt`, `LyricsOvhProvider.kt`, их deterministic tests. Package `com.lirix.app.feature.lyrics`.
- DTO: `AdditionalLyricsResult(sourceId:String,sourceName:String,plainLyrics:String?=null,syncedLyricsLrc:String?=null,chordsText:String?=null,sourceUrl:String?=null,author:String?=null)`.
- HTTP: `LyricsSourceHttp.get(url:String,allowedHosts:Set<String>,policy:Policy=Policy()):String?`. Policy maxBytes2MiB,totalTimeout12s,connect4s/read6s,maxRedirects2; OVH overrides256KiB/4s/1.5s/2.5s/0redirects. HTTPS exact hosts, redirects validated, no cookie/auth. Null unavailable; CancellationException rethrow. 403/429 host cooldown>=60s/Retry-After. Injection adapters: `fetch:suspend(String)->String?`.
- energy_review: новые `AkkordsProProvider.find(title,artist):AdditionalLyricsResult?` и `LyrsenseTranslationProvider.load/find`, parsers и tests. Lyrsense returns existing `SongTranslation`/`TranslationSearchResult`, только RU.
- energy_thesis: новый `KugouLyricsProvider.find(title,artist,durationMs:Long?=null)`, adapters wiring в Android `LyricsProvider.kt`, desktop `DesktopLyricsService.kt`, `SongTranslationProviders.kt`; integration tests и env-gated `SourceExpansionLiveTest`. Gradle запускает root.

## KuGou

Публичный HTTPS `lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword=...&duration=...`; затем `/download?ver=1&client=pc&id=...&accesskey=...&fmt=lrc&charset=utf8`. Accesskey только из ответа публичного поиска, не хранится и не выводится. Никаких private cookie/static token/challenge bypass.

Live обычным Lirix UA: Coldplay/Yellow266s → HTTP200 JSONstatus200,44 timestamps; Radiohead/Creep238s →42 timestamps; NoSuchArtistLirixAudit/NoSuchSongLirixAudit →HTTP200,candidates0. Эти проверки не заменяют Kotlin smoke.

Поиск1GET+максимум2download. Принимать только exact normalized artist/title с сохранением live/remix/version qualifiers; duration difference<=max(3s,2%) при известной длительности. Без duration нельзя брать неопределенный вариант по одному совпадению title. Candidate dedup по id; при нескольких разных длительностях/версиях без reliable match возвращать null. JSON status200,fmt=lrc,base64 validUTF8,≥2timestamped lyric lines; не принимать HTML/ошибку/одни metadata tags/пустые timestamps. Не синтезировать времена. Missing/bad download→next bounded candidate→null. No fake timestamps from plain.

TrackEntity.totalDurationMs — accumulated listening, НЕ duration трека. Его нельзя передавать KuGou. Android берет optional duration только из matching LivePlaybackSnapshot; desktop добавляет optional durationMs в onTrackChanged, Main передает live duration; callers без duration остаются совместимы.

## Порядок интеграции

Android: cache/user/custom rules прежние → LRCLIB (plain-only обогащается genuine KuGou LRC) → KuGou при отсутствии synced LRC (включая LRCLIB plain-only; при miss сохранить plain) → старые fallback +lyrics.ovh. Chords enrichment: AmDm→MyChords→Akkords. Новые ids `builtin:lyrics-ovh`, `builtin:kugou`, `builtin:akkords-pro`. Rejection проверять ДО запроса. Встроенных global enable gates пока нет: не изобретать их и не смешивать с disabled custom rule.

Desktop: saved USER→valid caches→custom rules→LRCLIB→KuGou→chords AmDm/MyChords/Akkords→existing Textpesni→lyrics.ovh. Сохранить source rejection для каждого нового id, не сводить их к legacy `builtin:web`. Поля LRC/lyrics/chords передавать через существующий acceptAutomatic с current track/cancellation/manual USER guard. Adapter никогда не пишет DB.

Translations: `SongTranslationProviders.load` явно маршрутизирует lyrsense.com/www.lyrsense.com. `findLanguage` ru: Amalgama→Lyrsense→LyricsTranslate; для других языков без изменения. Saved selection wins; Candidates не превращать в первый Found.

## Атрибуция

`sourceUrl`/`author` nullable у adapter. Существующий provider column — stable source ID, URL в него или в body не вставлять. У lyrics_cache нет metadata columns; предложено additive nullable `sourceAttributionJson` с независимыми lyrics/chords attribution entries, Room13→14, archive optional backward-compatible field; решение одобрено root, owner — energy_thesis. Переводы уже хранят URL/translator в SongTranslation JSON без migration.

## Проверки

Deterministic: fixtures shape/identity/version/duration/badbase64/badLRC/missing, rejection skips HTTP, failed new source continues fallback, cache roundtrip attribution, delayed automatic result cannot overwrite USER, no stale track publish. HTTP helper redirect/body/status/cancel tests. SourceExpansionLiveTest запускается только при `LIRIX_SOURCE_LIVE=1`, actual Kotlin adapters минимум2known+1missing per source, prints metadata/counts only, checks content/timing/pairs rather than HTTP200. Никаких copyrighted full live fixtures/accesskeys в логи. Root запускает после source freeze.

## Gate review

Root и energy_antithesis приняли контракт. API IDs и provenance согласованы с владельцами адаптеров. Multi-request provider find ограничен общим15s deadline; deadline кооперативный, освобождение системного DNS/nativeIO может занять больше времени. Код готовится к final build/tests; plan approval не означает runtime acceptance.
