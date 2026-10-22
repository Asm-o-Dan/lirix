# Romanization audit — 2026-10-22

Scope: read-only review of Antigravity JA/ZH engines, coordinator, Android karaoke/text integration and their existing tests. No product/test code edited. Scratch tools under ignored `build/romanization-audit/` compile the actual four production Kotlin sources using the already cached Kotlin compiler; no Gradle, dependency downloads, phone changes or release operations.

## Executive finding

The mixed Latin/Han shown in the screenshot is produced by the current engine, not a font, karaoke timing or translation-source problem. Japanese is implemented as a handwritten kana map plus a small `JPOP_KANJI_DICT`, not the whole-phrase morphological reading engine proposed in the intake. Unknown Han characters pass through verbatim. The UI declares any differing output to be a successful reading and displays this partial result without qualification.

## Confirmed findings

### P1 — Unknown kanji are displayed inside a purported Romaji/Pinyin reading

- `shared/src/commonMain/kotlin/com/lirix/app/feature/lyrics/romanization/JapaneseRomajiEngine.kt:137`: handwritten phrase list; lookup at 419–438 only handles exact listed prefixes. Fallback 508–512 appends every unmatched character verbatim.
- `.../ChinesePinyinEngine.kt:350–358` explicitly retains unmapped Han characters too; dictionary is handwritten and primarily simplified Hanzi.
- `app/src/main/java/com/lirix/app/ui/components/RomanizedKaraokeLine.kt:70–74`: success criterion is only `resolvedRomanized != originalText`; lines 88–97 and 113–123 then show incomplete output as reading / romanized-only text. Model has no completeness/unsupported-span status.
- Actual production results: `失くす → 失kusu`, `彷徨う → 彷徨u`, `立ち止まる → 立chi止maru`; Chinese `愛你 → 愛 nǐ`, `發光 → 發 guāng`, `女绿 → 女 lǜ`. All have residual Han.
- These minimal non-song phrases reproduce exactly the mechanism in the supplied screenshot (`失kushite`, `彷徨tteiruyo`, `立chi止...`). Adding those few words would hide only the sampled failure and leave all other dictionary misses.
- Recommendation: real Japanese whole-phrase tokenizer/readings + kana-to-Hepburn stage; coverage-tested Chinese phrase/dictionary backend with traditional support. Result must report unresolved spans rather than claim completed romanization. Preserve the original; do not erase unknown characters to manufacture Latin-only output. Add explicit fallback/manual correction UX.

### P1 — Android ignores song language and switches Japanese kanji-only lines to Mandarin

- `RomanizedKaraokeLine.kt:48,70–71` defaults to AUTO and calls `romanizeLine` independently for each line.
- `app/src/main/java/com/lirix/app/ui/NowPlayingScreen.kt:1871–1881,2001–2010` supplies neither target language nor precomputed song-level readings.
- `RomanizationCoordinator.kt:26–40,54` treats any Han-only line as Chinese; `romanizeLrc` song-level detection at 82–98 is not called from the Android integration.
- Actual results: AUTO `世界 → shì jiè`; explicit JAPANESE `世界 → sekai`; AUTO `世界へ → sekaihe`. Thus adjacent lines in a Japanese song can change transcription system merely because a line lacks kana.
- `detectDominantLanguage` is not a full language detector: one kana anywhere forces Japanese; all-Han Japanese text remains ambiguous. No language selector is exposed in the current display-mode dialog.
- Recommendation: determine/persist song/document source language, ask when Han-only or conflicting, pass that language through both text/karaoke render paths. Never use application UI locale as lyrics language. Make per-line mixed-language exceptions explicit.

### P2 — Chinese polyphones are silently overwritten and wrong in ordinary phrases

- `ChinesePinyinEngine.kt:138,149`: `只` is first `zhǐ`, then overwritten as `zhī`; 157,164: `乐` first `lè`, then `yuè`; 208,216: `少` first `shǎo`, then `shào`. `buildMap` keeps the last value, not alternatives.
- `ChinesePinyinEngine.kt:191` has only `行 → xíng`, with no bank-context override. `PHRASE_PINYIN_MAP` lacks such ordinary contexts.
- Actual production results: `少了 → shào le` (ordinary “less/fewer” sense needs shǎo), `乐了 → yuè le` (ordinary happy/laugh sense needs lè), `银行 → yín xíng` (bank is yín háng), `重庆 → zhòng 庆` (place-name needs Chóngqìng).
- Recommendation: phrase-aware dictionary/tokenizer with contextual polyphones and explicit manual overrides. Add tests for both readings of the same Hanzi in contrasting short contexts; a per-character single-reading map cannot satisfy that contract.

### P2 — Ordinary Japanese particles are incorrect outside hardcoded phrase prefixes

- `JapaneseRomajiEngine.kt:443–454` changes は/へ only when the character is surrounded by whitespace/punctuation. Japanese prose ordinarily has no such spaces. Some special noun+particle dictionary entries mask this only for enumerated expressions.
- Actual `君へ → kimihe` (particle e), `わたしは学生 → watashiha学生` (particle wa, plus missing 学生).
- Recommendation: use tokenizer grammatical information for particles; do not expand an ad hoc regex or list of noun prefixes as a general grammar solution.

### P2 — No Unicode normalization before conversion; visually equivalent kana can remain unconverted

- `JapaneseRomajiEngine.kt:380–516` indexes original UTF-16 chars against exact fullwidth precomposed kana maps, without normalizing kana width or combining marks.
- Actual halfwidth `ｶﾀｶﾅ → ｶﾀｶﾅ` unchanged; decomposed `か + U+3099 → ka + U+3099`, instead of a reading of が. AUTO also does not detect halfwidth kana, because coordinator checks only 3040–309F/30A0–30FF.
- Recommendation: normalize a separate analysis view, retain the exact source text and offset mapping; test NFC/NFD dakuten, halfwidth kana, supplementary Han, emoji and mixed Latin. Do not rewrite stored lyrics.

## Existing green tests do not establish reading quality

Executed existing unmodified suites outside Gradle against actual freshly compiled engines:

```
com.lirix.app.JapaneseRomajiTest         13 tests
com.lirix.app.ChinesePinyinTest           8 tests
com.lirix.app.RomanizationCoordinatorTest 8 tests
JUnit: OK (29 tests), 0.113 seconds
```

- Repro command: `& build/romanization-audit/run-probe.ps1`; results written as UTF-8 to `build/romanization-audit/probe-output.txt`.
- Existing-suite command: `& build/romanization-audit/run-existing-tests.ps1`; captured result in `build/romanization-audit/existing-tests-output.txt`.
- Japanese assertions mostly match keys already present in the handwritten dictionary or kana-only examples. No assertion of conversion coverage/completeness on unknown kanji. Its performance corpus even contains unhandled characters, but only checks elapsed time.
- Chinese tests exercise known simplified dictionary entries and tone-removal formatting. No traditional forms, contrasting polyphones, or completeness assertion.
- Dominant-language test asserts the detection helper only, not Android integration or that a kanji-only line is actually rendered using that song language.
- Therefore the same code simultaneously passes all 29 existing tests and produces the displayed mixed-script defect. This audit does not verify the other streams or the summary claim that every app/desktop test is green.

## Contract and integration notes

Against `.sdd/intake/LYRICS_ROMANIZATION_JA_ZH.md`:

- Whole-phrase Japanese readings backend and Chinese contextual dictionary were proposed; neither backend dependency is present in the audited engines/build configuration.
- Explicit handling for ambiguous Han-only language, manual corrections, cancellable background conversion and engine/dictionary-versioned cache are absent.
- Cache currently keys exact text plus resolved language (`RomanizationCoordinator.kt:59`), with in-memory LRU only. This correctly separates explicit JA/CHINESE and does not explain the current screenshot by stale persisted data. Missing version/options would become a correctness issue with hot-swapped dictionaries or selectable schemes; currently those are not implemented. Do not overstate this as the present root cause.
- Conversion happens synchronously in Compose `remember`; cheap current dictionaries hide potential main-thread cost. A real tokenizer must be moved to a cancellable background path before replacing it.
- “Ruby” is a separate whole-line 11sp text above original, not character/word-aligned furigana (`RomanizedKaraokeLine.kt:113–132`). This matches the intake's whole-line-first restriction; no word alignment or timings should be claimed. Reading font does not follow the app's custom karaoke font multiplier, unlike original. System font scaling still applies.
- Display mode is `remember` state (`NowPlayingScreen.kt:475`), not a persisted preference. Its restart retention is not implemented.
- `rg` found no production RomanizationCoordinator/DisplayMode calls under desktop; shared library availability alone does not establish desktop reading UI integration. The supplied summary's explicit Android/desktop parity claim concerns localization, so this is not evidence that its localization claim is false.

## Bounded repair order

1. Add regression cases above and explicit partial/unsupported result semantics; retain source intact.
2. Carry source-language context through both Android renderer call sites; test Japanese song with a kanji-only line, Chinese song, and ambiguous input requiring choice.
3. Select/benchmark actual offline Japanese reading and Chinese contextual backends per intake. Keep dictionaries lazy and background work cancellable; respect licenses and measure APK/memory cost. Do not hide failures by deleting Han characters.
4. Test full semantic outputs on an independently chosen short corpus, normalization, tones/polyphones and traditional forms; include behavior for unresolvable names/lyrics-specific readings.
5. Verify on the supplied song and then unfamiliar songs, including romanized-only mode, large font and seek/timing invariants. Device acceptance of the photographed reading was premature; no phone operations were performed in this audit.
