# LyricsTranslate multilingual provider: implementation brief
Date: 2026-10-19
Status: PARSER PROTOTYPE; automatic integration and language selection not implemented.

## Goal
Automatically discover existing authored song translations from LyricsTranslate on Android and Windows. Initial target choices: ru, en, es, pt, zh. Translation language is independent of UI locale and original song language. Availability is per song, not guaranteed per language.

## Observed primary sources
- https://lyricstranslate.com/en/songs : separate title, artist, original-language and target-language search fields.
- https://lyricstranslate.com/en/languages.html : multilingual catalog.
- https://lyricstranslate.com/en/danza-kuduro-dancing-kuduro-official-translation.html : mixed original languages, multiple English/Russian versions, translator attribution, proofreading/formatting warnings, separate original and translation views.
- Browser-readable pages are evidence of discovery feasibility, not proof of a stable native HTTP parser or a public API.

## Required architecture deliverable
Review existing automatic translation report and current model/provider/coordinator/settings/reader contracts. Identify functions affected by Russian-only validation and storage queries. Propose exact signatures and one-function task cards; freeze contracts before product changes.

## Required behavior
1. Select target language explicitly; default ru preserves current behavior. Persist preference on each device.
2. Discovery follows observed search/catalog/song links. Do not construct guessed song slugs. Confirm actual HTTP form parameters from HTML before freezing the parser contract.
3. Verify artist, title, recording/version and target language against fetched page identity. Search snippets alone never authorize binding.
4. Multiple translations of the same verified song are variants, not different tracks. Present author/source and warnings for user selection; no silent arbitrary variant choice.
5. Preserve paragraph structure and independent source/translation texts. Row-number alignment is unverified; do not fabricate paired lines or karaoke timestamps. Existing Amalgama pairing remains supported.
6. Preserve selected offline documents. Automatic lookup cannot overwrite a manual selection. Reads, in-flight work, negative caches and statuses must distinguish track plus target language.
7. Switching language/track must reject late responses from the previous request. An unavailable chosen language must not silently show Russian as though it were the chosen translation.
8. Keep Amalgama as Russian provider. Define deterministic provider fallback and distinct unavailable, malformed, ambiguous, rate-limited and transport-error outcomes.
9. Retain canonical source URL, translator, target language, original languages and available quality warnings. Chinese catalog language does not by itself guarantee simplified Chinese; preserve source script and do not relabel it.
10. Bound concurrency, request count, response bytes, redirects, retries and caches. HTTPS source allowlist; honor rate limiting. No broad crawling or bypassing access controls.

## Acceptance evidence
- Synthetic HTML fixtures without copyrighted song text: relative links, multiple authors, source and target locales, mixed original languages, missing fields, invalid markup and warnings.
- Native HTTP smoke: metadata-only discovery and read for all five target languages, using multiple source languages. Record IDs/counts/status only; no full lyrics in committed fixtures or logs.
- Regression: manual selection race, separate language caches, late cancellation, offline reopen, older Russian documents, migration if required, source fallback and ambiguity choice.
- Both platform builds and relevant tests pass. Real reader previews show source, translator, language and honest unpaired layout.
- Package checks and clean committed tree. Physical acceptance and GitHub release remain separate gates.

## Scope boundary
No machine translation, no invented bilingual timing, no automatic script conversion. Archive/merge format changes require an explicit contract if included; otherwise document the existing export limitation.

## Current gate
User clarified the coordinator rules belong to Antigravity and authorized Codex to implement directly. No agents launched. Browser DOM inspection succeeded, but direct native HTTPS received HTTP403. Parser prototype is separate from working automatic discovery; see reports/LYRICSTRANSLATE_PARSER_PROTOTYPE.md.
