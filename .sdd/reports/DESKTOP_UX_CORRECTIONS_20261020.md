# Seven desktop UX corrections
Date: 2026-10-20. Direct implementation/self-review by Codex; no agents or independent review claimed.

## Delivered against the audit
1. Available original lyrics remain readable while automatic translation is missing/searching/failed. Existing automatic save and paired/unpaired translation readers remain available. Real shared-panel preview asserts original text before search completion.
2. Workspace owns one persistent transport across Now Playing, Library, Wrapped and Settings. Play/pause, previous/next and seeking delegate to existing ingress. Unknown session/duration disables unavailable controls. Offset adjustment/reset and audio levels remain in transport options. Standalone deck retains optional transport compatibility.
3. Fixed360dp full-height metadata column replaced by compact48dp cover/metadata header. Reading surface takes full workspace width. Karaoke seek/timing and HUD/Connect/mini controls preserved. First bounded package committed as e94990c after focused checks.
4. Source label explicitly distinguishes pause/playing/no session. Removed literal successful Discord/WinRT rows; About now reports observed lyric source and availability. Regression cases cover labels.
5. Wrapped poster creation is reachable near timeframe selection. Real renderer preview supports format/section changes; Save uses exactly the ready displayed BufferedImage, never stale request output. Stats+timeframe are frozen on opening, separate from live history recalculation. Busy export prevents changes/close, file-picker cancellation and export errors retain settings/preview. Achievements are expandable.
6. Source dialog defaults to paste/edit/save for the named song. Link discovery and advanced CSS/rules are independently expandable. Candidate labels use variant numbers and readable snippets. Extraction/save actions are serialized, errors retain input, editing/close is disabled while saving. Source dialog binds to its captured TrackEntity, not a later current track; after playback changes, refresh/reject is disabled and the save target is explained. Rules/import/export remain available.
7. Library reads nonblank plain lyrics or parsed LRC content, without timestamp/metadata tags. Stored data unchanged. Regression covers synced-only, blank plain, plain priority and metadata-only input.

## Verification
Focused six-theme/two-size actual Compose workspace matrix passed, plus actual translation and poster previews. Workspace navigation invokes each real sidebar action and verifies its real playback button reaches the ingress seam on every destination. Shared translation test reads original during search, saves automatic result and selects Spanish through the compact popup. Poster content test opens actual format/section menus, waits for matching preview semantics and checks enabled export in Paper Ink and Obsidian Pulse. These are detached real composables, not hand-drawn mockups or native window acceptance.

Broad command: :desktop:test :shared:desktopTest :app:testDebugUnitTest :app:assembleDebug :desktop:createDistributable --offline --no-daemon --no-configuration-cache --max-workers=2. BUILD SUCCESSFUL in5m48s. Following the final desktop-only source-track binding guard, :desktop:test :desktop:createDistributable repeated successfully in2m22s; no further product changes.
- Desktop125 discovered,123 passed,0 failures/errors,2 optional skips: live multilingual network probe and user-supplied Amalgama HTML probe.
- Shared173 passed,0 failures/errors/skips.
- Android198 passed,0 failures/errors/skips.
- Total494 passed. Earlier five Connect socket-start failures did not recur in the final broad or final desktop run; Connect production/test code was not changed by this UX package. No permanent environmental root cause is asserted.
- git diff --check passed.

Actual screenshots: desktop/build/compose-previews (six-theme matrix; focused pages refreshed in final run), desktop/build/translation-previews, desktop/build/wrapped-ux-previews. Light/dark main, library, Wrapped and poster frames inspected.

## Package identity
Source and packaged hashes match:
- shared-desktop.jar SHA2568219651ef8d60c3f3f44476f672f55d6017062b7b419928a9eb8ef54f611f198
- desktop.jar SHA2562aa043977abb55fb9c74cfe3c894cc615613ddb229bb20345473c2d95d3dbf99
PC: desktop/build/compose/binaries/main/app/Lirix/Lirix.exe.
APK: app/build/outputs/apk/debug/app-debug.apk,27957866 bytes.

## Limits
Phone not present in ADB; new APK not installed. No user process stopped/restarted. Native dialog focus, actual file-picker/export workflow, monitor/DPI/HUD behavior and physical audio acceptance are not certified by CPU previews. No release published and no published1.4.0 artifacts replaced. Seven code findings are implemented; this report does not certify the whole application release-ready.
