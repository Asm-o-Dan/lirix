# Offline lookup recovery — coordinator QA, 2026-10-25

## Baseline and preservation

The preceding Settings task was delivered from source773434b, receipt3b25fb3, with24 final UI executions (12 RU +12 EN). The current task is only offline-lookup-recovery; broader studio/UI completion and other backlog items remain open. Approved recovery contract revision3 was committed/pushed as87bc36f before implementation.

Ten existing main-tree files were preserved, without overwrite, under ignored `.gradle/offline-recovery-preservation/main/`; hashes and canonical/HEAD comparisons are in source-baseline.json there. All10 main versions still matched that preserved baseline when implementation was authorized. Existing foreign source/clock/session/DAO changes must survive. Canonical pre-change files match committed source by LF except the documented trailing-whitespace normalization in NowPlayingScreen.

## Actual OS preparation (not application acceptance)

- Isolated emulator-5556, Android15/API35, remains available. No physical phone is used. Initial default Wi-Fi was INTERNET+VALIDATED, with a validated cellular fallback: `.gradle/karaoke-private-verification/offline-recovery-connectivity-baseline.txt`.
- Root disabled both emulator radios and actually observed `Active default network: none`; both were restored in finally and actual default102 again had INTERNET+VALIDATED. Logs: `offline-os-radios-disabled.txt`, `offline-os-radios-restored.txt` in the same verification directory. This proves the OS test mechanism, not that the new app recovery works.
- The system com.android.music package resolves audio VIEW to AudioPreview and has no MAIN/LAUNCHER activity. An original silent120s WAV with RIFF title/artist metadata was prepared in ignored `build/android-offline-recovery/fixtures/` and opened offline. AudioPreview did not supply a usable Music MediaSession; UIAutomator did not reach idle. That attempt is not accepted as a user-bug reproduction or recovery proof. Root closed it and restored Wi-Fi/mobile data; no host/phone network was changed.

## Gates still pending

Executor is implementing the approved slices and meaningful unit/Room/native tests. No recovery code build, unit pass, native pass or app-level reconnect acceptance is claimed at this checkpoint. Wait for source READY before serial Gradle/builds; do not compile a moving canonical tree.

Required follow-through: actual target/notes/provider flow, bounded concurrent negative requests, same-transaction manual/rejection/fresh-field protection in both tables, current-key UI publication and actual retry rendering; runtime OS monitor/reconnection plus controlled real provider/coordinator response. Disclose any remaining live external-player/phone gap. Final data-preservation diff review, private preview and checksum receipt follow testing; do not substitute the old Settings logs.

## Resume — 2026-10-26

- Current HEAD remains87bc36f; no implementation commit or recovery build/test pass exists yet. The former executor is absent from the live agent tree, not merely waiting. A new assigned coder resumed the existing files rather than starting again.
- Source inspection by the resumed coder found Engine/overlay/NowPlaying/retry/native-test integration already saved beyond the old report checkpoint. Reported remaining fixes concern missing Text-mode retry, retained words during missing-chord recovery and network-generation handling across STARTED re-entry. These are implementation findings, not runtime passes.
- ADB initially had no devices and no emulator/qemu process existed. Root started the same LirixQA35 AVD without wiping it, PID17476, with hidden/no-audio settings. It appeared as emulator-5556; boot-completion check was still pending. Logs: `offline-emulator-20261026-stdout.log` and `offline-emulator-20261026-stderr.log` in the verification directory.
- Wait for a frozen READY handoff before serial build/native execution. No other backlog item was started.

## First frozen validation — 2026-10-26

- Twenty main/canonical file hashes matched the coder's READY report before root started Gradle. Preserved manifest: `build/android-offline-recovery/source-manifest-first-build.json`.
- First serial build terminal FAIL in41s, before tests: Room/KSP MissingType on canonical LyricsDao. Log: `.gradle/karaoke-private-verification/offline-recovery-first-build-20261026.log`. Coder identified missing `androidx.room.Transaction` import and missing `observeLyrics` Flow-query which already existed in main; canonical-only additive fix supplied. No schema/dependency/cache change. This fixed source still requires compilation.
- Independent review reported three behavioral issues: known legacy note-origin track duplicates could satisfy lyrics; unsuccessful completion after network loss could show Incomplete instead of Waiting; subscriber wait could remain Loading during blocking shared-job unwind after its logical deadline. Coder is correcting these and adding adversarial tests before the next build; the old freeze is superseded. No unit/native PASS is claimed.
- Emulator boot completed (`sys.boot_completed=1`). The requested local HTML fixture loaded in stock WebView124 and explicitly reported MediaSession API unsupported, so root did not press Start or treat it as an external-session test. Page closed; owned loopback-only Python server PID18868 stopped after checking its command line/path. No radio changes occurred in this attempt.
- User requested one additional deferred item, multilingual translation parsers including Russian→English. Recorded and pushed separately as0f22f4b; it is not active implementation and does not interrupt this task.


## Final checkpoint — 2026-10-26

Implementation accepted for source delivery. Full isolated build: Android 346 and shared 249 unit tests, release lint and debug/test compilation PASS. Final native run: OK (19 tests), 37.009 seconds; log offline-recovery-native-host-fixed-20261026.log. The test-only host correction settles locale/orientation before composition and passes actual LocalConfiguration orientation to the renderer. Earlier failed host runs are superseded, not product crashes.

Actual Main/external synthetic framework MediaSession: both radios disabled, Believer remained selected with Waiting/Retry; restoring validated default network loaded synced lyrics without changing track. Both Room tables contain lyrics; cached LRC contains 46 unique timestamps, chords also persisted. Receipts are under build/android-offline-recovery/main-*. Physical phone/player acceptance and visual screenshot review are not claimed.

User requested commit and next task, explicitly no APK production/delivery. No additional APK, private release, version bump or public release is part of this checkpoint. Product static review passed; latest changes after that review are confined to the UI test harness.
