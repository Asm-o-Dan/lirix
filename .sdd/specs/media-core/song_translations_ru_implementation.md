# Russian translation import v1 — implementation contract
Date: 2026-10-19. User approved continuing direct implementation. No subagents.

Scope: static Russian translations on Android NowPlaying Text and desktop Text/library. Source: explicit Amalgama song URL. Shared HTML provider/model/Room table/Compose panel; paired source text retained. Original lyrics, chords, notes and timestamps remain independent.

Import: HTTPS song URL -> bounded HTTP/validated redirects -> charset-aware Jsoup DOM -> preview metadata and pairs -> user confirms the song/version -> explicit save. No automatic association by title or invented karaoke alignment. Error keeps last saved document. Offline display does not call the provider. Different source URLs remain separate saved variants; repeated import of the same URL replaces that variant only after confirmation. Original/Russian/Together modes; source+translator attribution and open-source action.

Schema: v12 adds song_translations(trackKey,language,sourceUrl,documentJson,selectedAt), composite PK. Additive migration11to12 registered for both platform builders. Existing history/lyrics unchanged. Translations are not part of archive v1; future cross-device merge requires separately versioned extension. No translation editing, automatic search, synchronized subtitles or translated HUD in v1.

Provider: accepts only HTTPS amalgama-lab.com/www song .html paths; no credentials/port/query; fragments ignored. At most four requests, all redirects revalidated before opening. 2 MB transport cap, 8s connect/12s read, 25s UI timeout. Cancellation disconnects the request; no scripts run. Charset from HTTP or HTML meta. Dedicated h2 and #click_area .string_container selectors; missing side retained, repetitions preserved, section markers flagged. Malformed/error pages rejected.

Tests: synthetic Windows1251/UTF8 HTML, metadata/entities/breaks/sections/incomplete rows/repetitions, URL/redirect/errors/limits/cancellation; actual v11 schema migrated/reopened, existing lyrics/chords/notes/history retained, duplicate imports idempotent, variants and track isolation. Detached visual artifacts use synthetic content only. Physical Android/PC acceptance remains separate.
