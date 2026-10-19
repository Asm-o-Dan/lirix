# Multilingual automatic translations v3
Date: 2026-10-19. Status: implemented; automated/live-provider validation passed. Physical acceptance pending.

Target language ru/en/es/pt/zh is independent of UI locale, defaults to ru and persists per device. Desktop Java preferences and Android private preferences back a shared reactive selection. Shared reader resets its document/observer when target changes.

Existing schema v12 already keys documents by language; DAO reads and observations now take language, default ru. Transactional automatic insertion checks track plus language. Coordinator statuses and positive/negative lookup history distinguish track plus language; Russian state keys retain compatibility. Reject wrong-language provider results; selected offline documents win. Cancellation must never bind a late result to a different track or language.

Russian searches Amalgama first; Missing or ordinary failure falls back to LyricsTranslate. Preserve Amalgama ambiguity and cancellation. Other targets use LyricsTranslate. Manual load dispatches by source host; save requires matching selected target.

LyricsTranslate transport identifies itself as Lirix, accepts HTML, reads the actual /en/songs POST form, copies only form identity fields and dynamically resolves target language option labels. Follow source-provided links only. Exact normalized title, artist and recording version are required in result rows and loaded page identity. Multiple authors remain explicit candidates. No guessed song slugs, external search API, browser cookies, CAPTCHA solving, proxy or impersonation.

Bound responses to2MB, redirects to4 per request, connect8s/read12s; serialize provider searches. Validate HTTPS/source hosts/search paths/song paths on every redirect. HTTP403/429 surface errors and at least60s source backoff, including explicit retry. Existing coordinator timeout and bounded history/negative TTL remain in force. Do not relabel source failure as missing.

Preserve independent complete texts, translator, source and warnings. Do not infer paired lines, karaoke timestamps or Chinese script conversion. Actual available translations determine coverage.

Acceptance: synthetic form/redirect/identity/backoff tests, real Room language separation/manual protection, persisted selection, real HTTP search/read across five targets without lyric logging, detached actual reader switching/narrow-wide previews, Android/shared/desktop tests and fresh packages. Physical acceptance and release publication remain separate.
