# Independent source integration review — 2026-10-21

Scope: Android AggregatedLyricsProvider + persistence callers, DesktopLyricsService/repository, shared attribution DTO/Room14/archive, translation routing. Read-only review of implementation; tests run only by root.

## Findings and verification

1. **P1 — USER:EDIT bypassed a USER-only automatic-save guard.** Fixed: common `isManualLyricsProvider` recognizes USER and USER:* case-insensitively. Applied in LyricsDao transaction, Desktop repository/service, archive manual preference/conflict. Reviewed `SourceAttributionPersistenceTest.automaticResultAfterConcurrentManualEditCannotOverwriteEitherTable`: a fetched automatic result waits while USER:EDIT is saved; DAO must return false and preserve both cache and track. Runtime result pending root.
2. **P2 — fresh Akkords adapter per lookup discarded negative cache/cooldown.** Fixed: Android default adapters are top-level lazy reused instances, Desktop adapters are retained fields. Lyrsense is retained by remembered SongTranslationProviders. HTTP 403/429 cooldown is independently retained in the shared singleton client.
3. **P2 — overlay parsed the automatic network LRC after its save was rejected by the manual guard.** Fixed: `currentCachedLyrics` reloads accepted DB state and `cachedLrcLines` derives from that reloaded state's syncedLyricsLrc. No rejected network LRC is used for the lyric index.

Canonical manual helper also revealed a nullable Desktop `stored` call to savedState; verified corrected with `stored != null && ...`. Compile validation belongs to root.

## Persistence acceptance

- Both Android/desktop migrations13→14 add `sourceAttributionJson TEXT DEFAULT NULL`. Entity nullable/String? + ColumnInfo NULL agrees. Listening intervals13 remain unchanged. No destructive change added by this migration.
- Attribution is independently stored for lyrics/chords. Enriching LRCLIB/KuGou with Akkords chords keeps the primary lyrics source id and attaches Akkords sourceURL/author only to chord attribution.
- Room cache and Desktop memory/disk cache preserve the JSON. Archive default null field is omitted via EncodeDefault.NEVER, preserving old payload checksum shape; merge chooses attribution with the selected lyrics and selected chord arrangement.
- Reviewed `SourceAttributionPersistenceTest`: actual rawSQLite13→Room14 migration plus reopen, manual lyrics/notes/listening intervals preserved, metadata starts NULL then roundtrips; archive roundtrip/default omission/mixed-source merge/USER:EDIT precedence. These are meaningful test cases; no pass claim until root executes them.
- `AdditionalLyricsCascadeTest` verifies KuGou enriches LRCLIB plain, new source rejection skips invocation, KuGou miss preserves LRCLIB plain, OVH stays plain and rejection skips its lookup. Actual new sources use separate stable ids instead of builtin:web.
- Reviewed `DesktopAdditionalLyricsIntegrationTest`: legacy HTTP is injected while new adapter results are synthetic; production Desktop cascade enriches LRCLIB plain with timed LRC and separately attributed chords, then rehydrates the metadata from disk. A second test keeps LRCLIB plain without fabricated timing when KuGou misses. These are integration-contract tests, not live provider/network tests.

## Remaining limits

Native HttpURLConnection deadline cancels/disconnects but OS DNS/native calls may release later; structured worker is retained, not detached. Fake cancellation tests prove disconnect mechanics, not a universal OS wall-clock guarantee. Live metadata-only smoke does not prove the full catalog or identity for OVH, whose response lacks artist/title metadata. Real Android/desktop compilation, persistence tests and opt-in Kotlin live smoke remain root-owned. User requested attribution only in menu → Track Info; UI is independently owned by energy_review and not changed in this review.
