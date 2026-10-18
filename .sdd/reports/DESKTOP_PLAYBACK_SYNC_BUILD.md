# Desktop playback synchronization: build handoff

Date: 2026-10-18. Status: implementation, independent review and package checks passed; physical browser acceptance pending.

## Verification

- Final native source compiled with 24 passing tests, 0 failed/skipped (17 new behavior regressions and 7 existing protocol cases). Independent review has no remaining blocking findings in this scope.
- NativeAOT `dotnet publish native/lirix-media-bridge/LirixMediaBridge.csproj -c Release -r win-x64 --self-contained true --no-restore -o desktop/build/playback-sync-native` succeeded. Default publish output was occupied by an existing running bridge; no process was terminated. Separate output avoided modifying that running executable.
- Final `:desktop:test :shared:desktopTest :desktop:createDistributable --offline --no-daemon --no-configuration-cache --max-workers=2` succeeded in 30 seconds. JVM tests were UP-TO-DATE; existing result XML records 81 desktop and 173 shared tests, 0 failures/errors/skips. Native-only product changes did not require rerendering the prior 61 preview artifacts.
- `git diff --check` passed (only existing environment/config permissions and line-ending warnings).

## Shipping defect caught and corrected

The first successful application image omitted the native bridge: the resource root contained a loose executable, while Compose consumes platform resource subdirectories. Such an image could fall back to an old installed/development executable, so a successful JVM package alone did not prove shipping the fix.

`prepareBridgeResources` now stages the tracked executable into `build/generated/bridgeResources/windows`; `prepareAppResources` depends on it. Independent source review verified the existing consumer lookup. Final image contains `Lirix/app/resources/lirix-media-bridge.exe`; `Lirix.cfg` sets `-Dcompose.application.resources.dir=$APPDIR\resources`, matching `BridgeProcessManager`'s first bundled lookup. No generated application image was patched manually.

## SHA256 evidence

Published native executable, tracked `desktop/resources/lirix-media-bridge.exe` and packaged `Lirix/app/resources/lirix-media-bridge.exe` all match:

`99B820142E4A741DDC20B8DD4AACCAAA3F33B27E6020EAFA76CF66C986D28C78`

Built desktop jar and packaged `desktop-a68633e2b4bf5d1557ebc84aec3a.jar` both match:

`FA3B07FC54AD15759DB9804FAD977308640739AAAC678A3A6C6D97A3A06FBE3B`

Built shared desktop jar and packaged `shared-desktop-5114d2ca34cfbf601a45fe45924b7a4a.jar` both match:

`12E7C001805F57BE3470234FACD2515E898042715E85D1CFF6B540AE53AB905D`

## Fresh application and remaining acceptance

`C:\Users\DaniilTuT\Documents\antigravity\quick-nobel\desktop\build\compose\binaries\main\app\Lirix\Lirix.exe`

No application launch, old process termination or release publication was performed. Existing running copies continue using their old bridge until normally closed and replaced. Check karaoke click seek, external browser seek, backward seek, paused seek/resume and track/artwork changes using the fresh build. Undated timeline sources have a documented 1500 ms best-effort grace; aggregate artwork cache pruning is deferred to a reference-aware lifecycle. Historical artwork already saved as the old mutable temporary path is not recoverable from that reference alone.
