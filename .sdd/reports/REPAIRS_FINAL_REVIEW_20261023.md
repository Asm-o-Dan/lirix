# Independent final repair review — 2026-10-23

Reviewer: repairs_final_review (not an implementation author). Read-only product review; no Gradle, ADB, Git mutation or product edits performed.

## Inputs and verification boundary

Reviewed frozen product/test snapshots:
- `build/repairs-baseline/roman-final-core/manifest.json` (16 files).
- `build/repairs-baseline/roman-final-ui/manifest.json` (14 files).
- `build/repairs-baseline/chords/head-output`.
- `build/repairs-baseline/mp4/head-output` and approved-product-files.json.
- `build/repairs-baseline/i18n-clean/manifest.txt` (locale owner still completing freeze).

Contracts: romanization_repair_v2.md, mp4_repair_v2.md, chord_transpose_repair_v1.md, locale_repair_v2.md; user Anti-Green-Mirage rules apply. Review is not evidence of executed tests or physical codec/locale correctness. Root owns current RED/GREEN and device verification. No confirmed P1 found in reviewed frozen production code. Two P2 findings below block claiming all gates complete.

## Findings

### P2 — cancellation test never starts the request

`shared/src/commonTest/kotlin/com/lirix/app/ReadingDocumentServiceTest.kt:104` creates `launch(start = CoroutineStart.LAZY)` then immediately cancels it. The backend is never entered, so `published == false` and `calls == 0` are guaranteed even if all in-flight cancellation checks were removed. This cannot prove a canceled old song cannot publish after a newer selection, especially across synchronous dictionary initialization/conversion.

Required repair: separate the original-only initialization test from an actual started-request cancellation test. Use a backend entry barrier and explicit release; cancel after entry, release its blocking work, then assert the canceled request never publishes and a subsequent document completes with its own identity/results. Cover cache/publication as appropriate. The production service checks cancellation before/after read and is not itself proven faulty by this test finding.

### P2 — API33 locale recreation drops actual unsaved editor state

`app/src/main/java/com/lirix/app/ui/i18n/LanguageManager.kt:92` writes `LocaleManager.applicationLocales`; `AndroidManifest.xml:35` does not handle locale configuration changes, so framework locale updates may recreate MainActivity. Framework recreation is allowed by the contract, but all UI state must survive it.

Canonical `NowPlayingScreen.kt:348`/`:349` stores notes edit mode/text using ordinary `remember`; `saveNotes` at 398–409 persists only on its explicit action. The edited notes and open editor/dialog selections are therefore lost on recreation. Meanwhile `LocaleSwitchUiTest.kt:26` checks only a synthetic `rememberSaveable` counter, which masks the actual state-loss path. Player mode/transpose and AppScaffold currentTab are already saveable, so those are not findings.

Required repair: retain the active unsaved edit state through the supported framework lifecycle, or explicitly handle the locale configuration update without destroying the host. Add a test using a real unsaved editor state and locale/lifecycle transition, with original track/user text unchanged. Do not turn this into an unconditional save or discard of user drafts.

## Reviewed behavior without additional confirmed findings

- Romanization now uses a complete IPADIC backend plus a declared versioned lexical supplement and contextual Mandarin backend; explicit document language flows into child lines, Han-only AUTO requires selection, unknown output classifies PARTIAL, and presentation does not expose mixed Han/Latin as complete green Romaji. Source text/occurrences/timestamps are retained in the document service. Cancellation, bounded cache and background execution require runtime proof in addition to this review.
- Japanese and Mandarin public-domain full-song fixtures are checked in with provenance; OOV/unknown/missing-backend and source-range cases exist. Fixture tests assert no unresolved target script for COMPLETE, rather than only dictionary-mapped example equality.
- Chord parser consumes complete musical tokens, transposes wrapped roots and slash bass, conservatively rejects prose, preserves newline/gap structure, and reduces extreme shifts before arithmetic. Chord-only Gold structure fixture avoids copying copyrighted song lyrics. Independent pitch oracle and negative malformed/prose cases materially test behavior. Await root RED/GREEN and caller verification.
- MP4 uses the same production pump in fake backpressure/EOS tests, confirms muxed/submitted frame counts, propagates errors, aborts partial output, checks cancellation, writes flexible YUV image strides, and has real device extraction/decode/color/container assertions plus first-party fixtures. No device pass is inferred from file existence or unit tests. Physical codec execution remains a root gate.
- Locale authority is separated across legacy preferences and API33 framework state; copied Configuration retains density/font/orientation; ContextWrapper helper recovers host activity; shared/desktop language is observable/persisted; static poster captions use an immutable language snapshot. These architecture improvements do not waive physical resources/state verification or current failing share tests.

## Outstanding gates

Root must resolve the two findings, complete meaningful Android/shared/desktop runs, inspect current share-test failures, validate actual device MP4 and locale/romanization UI behavior, and confirm schema14/Room identity/data preservation before any app replacement. This report does not approve a release or declare all Antigravity claims implemented/tested.
## Root verification after findings (2026-10-24)
The two P2 findings were addressed: cancellation test now waits for actual backend entry before cancellation and checks a later document; locale host retains locale/layoutDirection changes and the device test edits the real unsaved lyrics editor before RU/EN switch. Four physical Compose tests passed, plus real FileProvider/MP4 completed cases. Full shared247/Android313 unit suites pass; desktop127 has zero failures and six pre-existing skips. Schema14 identity checked against read-only pre-install backup before successful adb install -r. Details and remaining manual checks: REPAIRS_INTEGRATION_STATUS_20261023.md, superseding results. These are root verification results, not a new independent review.
