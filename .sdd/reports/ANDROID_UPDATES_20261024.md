# Android GitHub updates — verification 2026-10-24

Implementation follows ../specs/android-ui/github_updates_v1.md.

- Build: updater-final.log, BUILD SUCCESSFUL in 3m32s; :app:assembleDebug, :app:lintRelease pass.
- Seven updater tests pass, zero failures/errors/skips: numeric versions; stable release selection; rejected foreign URLs/missing digests/ambiguous assets; standard SHA-256 abc vector and exact output/progress; truncated/oversized/same-length corrupt transfers; actual coroutine cancellation before further bytes; wrong package/equal or older version/wrong signer rejection and compatible candidate acceptance. Package policy test uses controlled PackageManager metadata, not a claim of real APK installation.
- Live public API inspected: v1.5.1 stable, one APK, 19,116,225 bytes, SHA-256 digest exposed by GitHub. v1.4.0 prerelease is excluded. No higher stable Android release exists at verification time, so installed 1.5.1 has no offered update. No release/tag changed.
- Preview APK: build/updates/lirix-github-updates-preview.apk
- APK SHA-256: 1d1a1bc2cd6ad7c59ba97dd20374b183a1ed8432a99aa0ccfe2b67e4ad321ddb
- Device absent from adb devices. Installation, per-app unknown-source permission, OEM installer handoff and complete real in-place update remain physical acceptance gates. The preceding phone build has debug signing and cannot accept a differently signed public APK; no uninstall or data deletion performed.
- Android always owns final installation consent. Transfer cancellation restarts from zero; not a resumable background downloader.
