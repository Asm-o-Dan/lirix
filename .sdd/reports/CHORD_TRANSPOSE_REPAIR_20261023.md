# Chord transposition repair — 2026-10-23

Approved contract: `.sdd/specs/lyrics-engine/chord_transpose_repair_v1.md`; atomic cards CHORD-FIX-001..009. Scope: shared ChordTransposer only plus dedicated repair tests and three resource fixtures.

Confirmed original defect: `Intro: Am (Em) F (C) }x2` has 2/6 anchored-regex matches and fails the 60% ratio; parenthesized/extended tokens also fail independently. The repair uses whole-token chord parsing with balanced outer wrappers, explicit suffix grammar, slash bass, Unicode accidentals and overflow-safe pitch arithmetic. Strict chord rows allow recognized headers/bar/repeat control tokens; any unknown prose token vetoes bare conversion. Explicit [chord] markup still converts inside prose. Wrapper text, gaps, headers, suffixes and original newlines remain exact. Zero and octave shifts preserve spelling exactly; other shifts promise pitch equivalence rather than arbitrary enharmonic round-trip spelling.

Test-only RED snapshot prepared before source mutation under build/repairs-baseline/chords/red-input; original source captured as baseline-ChordTransposer.kt. Seven new tests load the checked-in Gold structural corpus and independently authored expected +2 output, test OOV compound suffix/bass/wrappers/Unicode, malformed tokens and English prose, inline context, CRLF/octave identity, an independent root+bass pitch matrix and Int extremes. Fixture contains no copyrighted lyric text.

Current GREEN delivery is frozen under build/repairs-baseline/chords/head-output, approved-product-files.json, sha256.json and chord-delivery.patch. No Gradle, ADB or Git mutation by this owner. RED/GREEN test execution, caller compilation, actual device transpose behavior and final review remain coordinator-owned and unverified at this freeze.

## Coordinator verification — 2026-10-23
- RED old ecce372 implementation: 7 new tests, 6 failures, 0 errors. Actual result saved in ignored build/repairs-baseline/chords/red-result.xml.
- GREEN repaired implementation: all 7 new tests pass in full shared desktopTest execution. Shared Android and desktop production compilation also succeeded in integration. Existing chord tests passed in the same suite.
- Independent review found no confirmed production defect in the frozen chord repair (.sdd/reports/REPAIRS_FINAL_REVIEW_20261023.md).
- Full combined repairs are NOT ready: one unrelated Chinese lexical-tone expectation fails, Android UI integration has missing contract/prerequisite references. No repaired APK installed. Gold device acceptance remains pending; ADB currently reports no devices.