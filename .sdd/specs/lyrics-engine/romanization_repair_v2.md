# Romanization repair v2 — bounded implementation contract

Status: proposed for root approval before dependency/product edits. Owner: romanization_audit. Date: 2026-10-22.
Supersedes the correctness assumptions in lyrics_romanization_v1.md; follows LYRICS_ROMANIZATION_JA_ZH.md and ROMANIZATION_AUDIT_20261022.md.

## 1. Outcome and boundary

Offline Japanese word readings and Mandarin phrase-context Pinyin. Original lyric bytes, line occurrence IDs, timestamps, translations, studio markers and source data are unchanged. Whole-line reading above text is supported; no character-aligned furigana, syllable timing, Cantonese/Korean pronunciation or claimed knowledge of how an artist sings a proper name.

A full corpus backend replaces the handmade JPOP/HANZI tables. Adding screenshot words to a small table is not an accepted repair. Unsupported or unresolved text remains recoverable and visibly qualified; never delete Han to create a false Latin-only success.

## 2. Public data and functions (commonMain)

Add source language `AUTO/JAPANESE/MANDARIN/UNSUPPORTED`; AUTO resolution result is `Resolved(language)/NeedsChoice/NotApplicable`. AUTO sees normalized analysis text over the WHOLE document: kana supports Japanese; Han without kana is NeedsChoice, never automatically Mandarin; conflicting explicit metadata is NeedsChoice. User override wins; interface RU/EN never chooses lyrics language.

`ReadingDocumentKey(trackKey, exactLyricsDigest)` scopes user source-language selection. A changed document revision invalidates generated readings; language override may survive within the same track if explicitly chosen. Per-line records include stable occurrence index/ID and exact timestamp, so repeated chorus rows remain distinct.

`ReadingOptions(scheme=HEPBURN, tones=MARKS)` immutable, versioned. Japanese long vowels use deterministic kana-based spelling, not ambiguous dictionary guesses; Pinyin preserves ü in no-tone mode. Options not exposed by UI may remain fixed but are included in cache identity.

`ReadingResult(originalText, readingText?, status, unresolvedRanges, backendId, language)` statuses COMPLETE/PARTIAL/NEEDS_LANGUAGE/NOT_APPLICABLE/UNAVAILABLE. Unresolved ranges index ORIGINAL UTF-16. COMPLETE is forbidden when unconverted Japanese kana/Han remain, or backend reports unknown Han tokens; legitimate Latin, punctuation, numbers, emoji are preserved. A dictionary reading is not a guarantee of a name's sung pronunciation. PARTIAL may include known readings but UI clearly labels incomplete conversion and always shows original. ROMANIZED_ONLY falls back to original plus warning for PARTIAL, never presents mixed text as a completed Latin reading.

`resolveDocumentLanguage(lines, override)`, `normalizeAnalysis(text)`, `classifyReading(source, conversion)`, `buildReadingCacheKey(...)`, `suspend romanizeDocument(document, language, options)` are separable functions with focused tests.

## 3. Backend selection and compatibility gate

Proposed locked artifacts in existing `shared` jvmShared dependencies only:
- `com.atilika.kuromoji:kuromoji-ipadic:0.9.0` (transitive kuromoji-core). Full Japanese tokenizer/readings/POS; IPADIC supports contextual particles. Code Apache-2.0; distribution MUST include IPADIC NAIST/ICOT notice, not claim dictionary is merely Apache.
- `dev.esnault.wanakana:wanakana-core:1.1.1` (MIT), kana readings -> Hepburn. No Android binding required. Normalize kana analysis first; assert sokuon/n'/long vowels and particles in golden tests.
- `com.github.houbb:pinyin:0.4.0` (Apache-2.0), dictionary + segmentation, traditional/simplified handling, polyphones, MARKS/NUM_LAST/NORMAL styles. Transitives `heaven:0.2.0`, `nlp-common:0.0.5` must be pinned/recorded and licenses inspected before shipping. Do not assume marketing claims pass the golden corpus.

Official source inspection confirms Kuromoji Java target 1.7 and houbb Java1.7+, while this project uses Android min26 and JVM17 with a pre-existing jvmShared source set. This supports a compatibility hypothesis, NOT a runtime validation: class-resource dictionary loading, transitive APIs, packaging duplicate notices and Android runtime must be tested with exact downloaded artifacts after approval. No dependency has been installed for this proposal.

Implementation arrangement: common interface/results/coordinator; JVM backend implementation in `shared/src/jvmShared/.../romanization/`. Inject the backend into the common service; small Android/desktop actual factories or explicit platform construction delegate to the same JVM backend. Do not put Java imports in commonMain.

Backend initializes lazily, on a worker, once per language; serialize tokenizer access until thread safety is established. Each document request checks cancellation before/after initialization and between lines; noninterruptible dictionary initialization may finish but cancelled output must never publish. No main-thread Compose remember conversion. Invalid/missing resources return UNAVAILABLE with original intact.

### Japanese rules
Whole normalized phrase -> Kuromoji tokens (surface, reading, POS, unknown). Use reading for known tokens, preserve Latin/non-target runs, mark unknown Han token unresolved. For grammatical particles は/へ use wa/e only if token POS identifies particle; を -> o. Kana words containing は/へ must retain ha/he. Katakana -> Hepburn follows one deterministic scheme. Preserve original display; analysis normalization cannot replace lyrics.

### Chinese rules
Run full target-script phrase through contextual backend, not independent per-character conversion. Separate/preserve non-target runs (Latin and numbers are not spaced character-by-character). Accept simplified/traditional equivalent reading. Contrast 银行/行走, 重庆/重量, 少了/少年, 快乐/音乐. Backend that fails context cases must be rejected or augmented via an explicit versioned user/phrase dictionary mechanism, not overwrite a global character reading. Unmapped supplementary Han is PARTIAL, not silently COMPLETE.

## 4. Normalization and cache

Build analysis view using Unicode normalization appropriate for kana (NFKC on target-script runs only, NFC combining dakuten), preserving original Latin width/case, punctuation, spaces and emoji. Maintain source ranges through any normalization expansion/contraction; unresolved span maps back to entire original grapheme/run if exact per-char mapping is unavailable. No invented Ruby alignment is exposed.

Cache key contains exact original text/digest + effective source language + scheme/tones + backend AND dictionary version + normalization version. Bounded LRU, safe under service concurrency; no cache stores a cancelled/incomplete request as COMPLETE. Text update, option or language change cannot reuse wrong result. Original line occurrence identity/timing is not collapsed by deduplicated conversion cache.

## 5. Android integration contour (root owns shared UI files)

Root/localization owner integrates only after contract approval:
- NowPlayingScreen text and karaoke both consume SAME `ReadingDocumentState` keyed track + exact lyric document + selected source language + options, via LaunchedEffect/service off-main. `collectLatest`/request generation prevents late previous-song results. Convert original raw line, never sentence-case transformed source.
- RomanizedKaraokeLine becomes presentation-only, receives ReadingResult, no coordinator calls. ORIGINAL_ONLY does not initialize engines. Reading font follows custom karaoke font scale plus system scale. No fixed-height clipping.
- Reading-mode dialog gains a separate lyrics-language choice: Automatic / Japanese / Mandarin / Original only. Names localized via localization owner; language choice is separate from app RU/EN/System picker and stored per track in an isolated private preference file (no schema migration or lyrics mutation). Ambiguous AUTO explicitly asks; can safely keep original until selected.
- Existing global mode can be persisted independently; it must not overwrite source-language choice. Partial/unavailable labels stay compact and readable, offer original rather than data destruction.
- No edit to media clock/energy/studio/capture logic; no changes to library DB schema.

Desktop public backend is available and covered by JVM tests. A new desktop reading UI is not required to resolve photographed Android defect unless root expands scope. Do not claim desktop UI feature parity based on shared backend alone.

## 6. RED-before-implementation acceptance

First add focused regression tests that demonstrate current failure, commit/run them separately before changing engines (root schedules Gradle). Existing 29 tests remain compatibility checks but their hardcoded wrong expectations must be updated only with an explicit scheme contract.

Mandatory short corpus, not full copyrighted lyrics:
- JA 失くす, 彷徨う, 立ち止まる: no unresolved Han and coherent readings.
- JA 君へ includes particle e; わたしは学生 has wa; はし retains ha; まっちゃ -> matcha; かんい -> kan'i; ｶﾀｶﾅ equals fullwidth kana reading; decomposed が equals NFC reading. Mixed Latin survives unchanged.
- Context: Japanese document with kana line + 世界 uses Japanese consistently in both render paths; Han-only document NeedsChoice; explicit Mandarin 世界 -> shì jiè; application locale never changes source language.
- ZH 愛你 and 爱你 equivalent; 發光/发光 equivalent; 银行 includes háng and 行走 xíng; 重庆 starts chóng, 重量 zhòng; 少了 shǎo vs 少年 shào; 快乐 lè vs 音乐 yuè; neutral-tone behavior documented, no-tone 女 retains nü.
- Unknown supplementary Han U+2FFFF / arbitrary proper name: unresolved status with original ranges, never deletion/false COMPLETE. Known dictionary name may be complete but no “verified artist reading” claim. Unknown tests inspect actual token classification, not only Latin output regex.
- Service: cancelled old request cannot replace new song; original bytes and timestamps unchanged; duplicate lines retain occurrence IDs; cache misses on language/options/backend-version changes; error/no resources preserves original.
- UI: ambiguous and partial statuses visible; ROMANIZED_ONLY fallback safe; both karaoke/text feed same document context; large-font wrapping; no synchronous tokenization in composition.

RED evidence already exists in standalone probe output against old production code; new acceptance tests must record actual failing assertions before replacement. No all-green claim from compiler-only checks.

## 7. Performance, packaging, licenses and device gate

Record baseline vs candidate debug/release APK sizes, individual dependency/resource sizes, first-use initialization time, warm 100-line conversion time, JVM heap delta and Android PSS before/after first use. No invented “<5ms” promise. Initial review targets: compressed APK delta <=30MiB, incremental Android memory <=120MiB, first initialization <=5s with visible nonblocking state, warm 100 lines <=500ms on reference phone. Exceeding target triggers documented decision/optional pack design, not silent release. Do not initialize dictionaries while original-only mode is used.

Package notices in a dedicated third-party notice asset/doc, handle duplicate META-INF per exact artifacts without deleting required license texts. Re-run compile + targeted tests + lint; root owns final build. Physical verification on phone, source changes, seek and recreation still required. No GitHub release until those gates; no uninstall/data wipe.

## 8. File ownership and isolation

Baseline snapshot made before product edits at `build/repairs-baseline/romanization/manifest.json`: 216 dirty source/docs/assets + clean shared/build.gradle.kts and libs catalog included. A foreign Word document was locked for hashing; no edits to it. Baseline contains exact Antigravity untracked romanization files/tests and all other currently visible dirty working files; it is ignored and not staged. Root should preserve this snapshot independently before other owners edit integration files.

Owner romanization: all commonMain feature/lyrics/romanization files, new jvmShared romanization package, new backend factory files if needed, dedicated romanization tests, RomanizedKaraokeLine.kt AFTER root freeze (currently mixed display+dialog; root may own dialog). shared/build.gradle.kts dependency hunk only AFTER root approval, notify root before edit.
Root/localization: NowPlayingScreen.kt integration, NowPlayingOverflowMenu.kt, all app string resources, AppScaffold/locale code, app build config. Romanization sends exact adapter contract and keys; never independently edits these shared files. MP4 owner controls export classes; no cross edits.

Required Antigravity originals in baseline: JapaneseRomajiEngine.kt, ChinesePinyinEngine.kt, RomanizationCoordinator.kt, RomanizationModels.kt, JapaneseRomajiTest.kt, ChinesePinyinTest.kt, RomanizationCoordinatorTest.kt, RomanizedKaraokeLine.kt; integration depends on existing changed NowPlayingScreen.kt/overflow/strings but final changes must retain their unrelated hunks.

## 9. Primary sources inspected (read-only 2026-10-22)

- https://github.com/atilika/kuromoji — tokenizer/POS/readings; official source is evidence of APIs, not Android runtime success.
- https://raw.githubusercontent.com/atilika/kuromoji/0.9.0/pom.xml — locked release Java1.7/code license.
- https://raw.githubusercontent.com/atilika/kuromoji/master/kuromoji-ipadic/NOTICE.md — IPADIC NAIST/ICOT notice must ship; inspect 0.9.0 artifact copy as well.
- https://raw.githubusercontent.com/atilika/kuromoji/master/kuromoji-ipadic/src/main/java/com/atilika/kuromoji/ipadic/Token.java — reading/POS APIs; verify exact0.9.0 artifact at spike.
- https://github.com/esnaultdev/wanakana-kt — core1.1.1, MIT, published JVM/Kotlin and Android example.
- https://github.com/houbb/pinyin — Java1.7+, polyphones/segmentation/traditional/script styles.
- https://raw.githubusercontent.com/houbb/pinyin/master/pom.xml —0.4.0/transitives/Apache license.
- https://raw.githubusercontent.com/houbb/pinyin/master/src/main/java/com/github/houbb/pinyin/bs/PinyinBs.java — instance configuration and initialization, avoid recreating helper for every line.
