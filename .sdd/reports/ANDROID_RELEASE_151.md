# Android 1.5.1 release

User selected Android 1.5.1 to include system backup without changing the existing v1.5.0 tag. Branch codex/release-1.5.1; versionCode 19, versionName 1.5.1. No PC source/version/build changes or Windows release assets.

Permanent private signing key is reused from local .signing; no private material is committed or uploaded. Expected public certificate SHA-256: 3473248e71212d53481eb0296c258e250c86cb82a9d2352020ef6572ba16bdeb.

## Release gate

The first release assembly caught FullBackupContent lint errors in the backup XML: explicit exclusions outside an exact include allowlist were redundant and rejected. The earlier debug build and unit tests had not executed release lint. Fix the policy by keeping only the exact allowlist; do not expand scope or suppress lint. Assert effective denial of Connect, derived artwork and unknown data.

Fixed by removing only 12 redundant exclusions, preserving nine exact included paths and encryption attributes. Focused five policy tests and lintVitalRelease pass without suppression. Independent final-delta review PASS. Final :app:testDebugUnitTest :shared:desktopTest :app:assembleRelease succeeds: Android209 tests executed successfully; shared173 unchanged UP-TO-DATE, zero failures/errors/skips. Intermediate APKs from failed builds are not release assets. Final commit metadata is refreshed before packaging, then signature/version/policy/hash and public upload are verified. Asset hash and publication evidence are recorded in build/release151 and a follow-up publication report.

## Publication constraints

Publish only lirix-v1.5.1.apk and SHA256SUMS.txt as stable Android release. Existing v1.4.0 tag/assets/status and v1.5.0 tag remain unchanged. System backup is future protection under Android settings, not a migration across incompatible signing keys. No user device uninstall, data wipe, backup upload or destructive restore test is performed.

GitHub Actions skips Windows and automatic release uploads for both v1.5.0 and v1.5.1. Manual UI upload must receive browser permission; a denied upload is not bypassed. Verify public release assets, hashes and stable/latest status before declaring publication complete.
