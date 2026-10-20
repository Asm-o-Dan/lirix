# Android release signing

Starting with Android v1.5.0, the permanent private release key is stored locally under `.signing/android-release.jks`. Its credentials are in `.signing/android-release.credentials.json`. This entire directory is ignored by Git and access is restricted to the current Windows user and SYSTEM. Back up both files securely; neither file belongs in a commit, release asset, log or public message.

Release builds accept `RELEASE_STORE_FILE`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_PASSWORD` and `RELEASE_KEY_ALIAS`. The local release procedure reads the private credential file into process environment variables, builds, verifies the APK, and clears those variables. Do not print secret values. The legacy app/lirix-release.jks is untracked, retained locally and not used for v1.5.0.

The published v1.4.0 APK uses a different Android Debug certificate whose private key is unavailable. An in-place Android update from that APK to v1.5.0 is not possible. The release notes explicitly warn about preserving data before any reinstallation. No automatic uninstall or data deletion is performed.

The v1.5.0 tag runs Android checks in GitHub Actions but skips Windows packaging and automatic release uploads. The locally signed and verified APK is published with its SHA-256 checksum. This prevents a fresh CI debug key from replacing the release signature.
