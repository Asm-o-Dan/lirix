# Combined repairs verification — 2026-10-23

Status: INCOMPLETE. No repaired APK installed. Public release unchanged.

## Verified
- Chord repair: 7 new regression tests PASS; old implementation failed 6/7. Source committed/pushed separately as ac2f53c.
- Core combined shared suite: 247 tests, 246 pass, 1 fails. Remaining ChinesePinyinTest.testMultiCharacterPhrases expects neutral `dui bu qi`, backend returns lexical `dui bu4 qi3` (exact Unicode in XML). Contract must distinguish dictionary lexical tones from spoken neutral-tone rules before changing assertions.
- Shared Android and desktop production compilation succeeded.
- Clean DB14 generated Room identity matches installed device: af51698a84bb8d10ace2e9856ddf8742. Read-only backup available locally.
- Earlier MP4 unit suite: 44 tests, 42 pass, 2 fail. Later targeted diagnostics fixed declared exception case; remaining real FileProvider preflight fails on Windows canonical path separators. Actual Android provider test remains required; production roots must not be widened and URI checks must not be weakened.

## Combined Android compilation failures
- LyricsViewerDialog.kt:316 missing TrackInfoDialog prerequisite.
- NowPlayingScreen.kt:1755 expects TranslationPanel originalLineContent parameter absent in canonical delivered panel.
- WrappedScreen.kt:88/89/283 references calendarPeriodChanges, calendarPeriodTick, hasLegacyListeningData absent from clean baseline. Resolve dependency boundary without including unrelated unfinished analytics work.

## Outstanding acceptance
- Repair integration and Chinese lexical-tone contract/test expectation.
- Full Android tests, APK build, desktop compile result, release lint.
- Actual FileProvider URI exact-byte/intent grant test and MP4 container test on physical Android.
- Gold transposition, observed favorite state, insets, RU/EN unsaved editor preservation, romanization on phone.
- Device favorite flag was changed by controlled test from 0 to 1 while UI stayed outlined; restore original 0 through repaired UI, not SQL.

## Constraints requiring user input
- All source executors and independent reviewer reached account usage limit. Latest AGENTS.md prohibits coordinator from editing product code. A role-override question has been sent; no override received yet.
- ADB now lists no devices. User asked to reconnect phone. No uninstall or data wipe was attempted.

Evidence logs live locally in ignored .gradle/karaoke-private-verification; clean frozen deliverables and test XML in ignored build/repairs-baseline. Do not equate frozen source with accepted runtime behavior.