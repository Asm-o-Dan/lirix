# Android v1.5.0 release preparation

User explicitly requested Android only, leaving PC unchanged. Branch: codex/release-1.5.0. No Windows build/version change or Windows release assets.

Version: com.lirix.app, versionName 1.5.0, versionCode 18, minSdk 26, targetSdk 35. Final APK is not debuggable. apksigner verification succeeds for APK v2 and v3.

Build: :app:testDebugUnitTest :shared:desktopTest :app:assembleRelease --offline --no-daemon --no-configuration-cache --max-workers=2 succeeded. Existing Android204 and shared173 tests up-to-date, zero failures/errors/skips. After correcting the signing environment variable to the actual RELEASE_KEYSTORE_PASSWORD contract, :app:assembleRelease succeeded with the permanent private key. Intermediate debug-signed APK was rejected and is not the release asset.

Final artifact: build/release150/lirix-v1.5.0.apk, 19114007 bytes.
SHA-256: 46a936596d5fe1168bfbff565bc2841e68de52a4731ca2c17a89b8e087b75811.
Public certificate SHA-256: 3473248e71212d53481eb0296c258e250c86cb82a9d2352020ef6572ba16bdeb.

Downloaded published v1.4.0 and verified its Android Debug certificate SHA-256 4e8d4ac0844e4aab6bf20da18e39ba40526ee8f9140f4c74426575de75563306. It differs from the local debug key and permanent new release key. User reported old key unavailable/unknown and then instructed continue after the incompatibility explanation. Notes explicitly warn that in-place installation from differently signed builds is impossible, and uninstall deletes local data. No uninstall, installation or data deletion performed.

Private .signing key and credentials generated locally, access restricted, ignored and never staged/uploaded. Legacy tracked keystore removed from tracking and retained locally. No private signing values printed. Public certificate/asset hashes are intentionally recorded.

v1.5.0 workflow skips Windows and automated release uploads so CI cannot replace the locally verified APK with a generated debug signature. Future automated Android releases require explicit permanent signing secrets and refuse a missing-key fallback. Existing v1.4.0 tag/assets/status preserved.

Physical phone UI/audio acceptance not performed. Advanced timeline studio, UI localization, romaji and translation timing alignment are not claimed in this release.

Publication is performed through the authenticated GitHub release UI after source commit/tag push. Verify published APK/checksum asset names, hash and absence of Windows assets before declaring completion.
