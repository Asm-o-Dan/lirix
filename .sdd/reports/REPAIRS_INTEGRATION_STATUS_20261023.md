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
## Superseding results — 2026-10-24
User explicitly permitted root product-code edits and testing after executor limits. All listed compilation boundaries were repaired in a clean snapshot without unfinished analytics/source expansion. Chinese tests now follow the approved lexical-tone contract.

- Full shared suite: 247 tests, zero failures/errors/skips. Full Android unit suite: 313 tests, zero failures/errors/skips. Results saved before focused reruns.
- Desktop: 127 tests, zero failures/errors, six existing opt-in/live tests skipped.
- Combined APK + instrumentation APK + release lint + desktop tests passed (repairs-integration-final.log).
- Physical Android: real FileProvider exact-byte/read-grant test and 1080x1920 24fps four-second MP4 test passed. MP4: 96 decoded frames, monotonic PTS, 48 red/48 blue frames, positive measured bitrate. First combined run was interrupted by a user swipe-away; only completed cases count.
- Subsequent physical Compose run: OK (4 tests), actual unsaved editor RU/EN retention and resource strings, JA document context/readings and qualified partial results. OEM test host uses explicit shell foreground start in debug only; no production permission widening.
- Device tree confirms header top padding reduced by 110 pixels. Later screenshot/taps encountered concurrent phone interactions and are excluded as manual Gold/favorite acceptance.
- Pending: controlled manual Gold chord transposition/favorite restore, post-install DB snapshot. Phone absent from ADB at latest check. Do not claim these checks passed. No uninstall/data wipe or release publication performed.
