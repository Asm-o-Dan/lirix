# Android updates from GitHub

Lirix checks public releases of `Asm-o-Dan/lirix` on foreground launch at most once every 24 hours. A manual check is available from the player overflow menu → **Lirix updates**. An available update opens its own screen; downloading requires a tap. Android then asks for installation consent, and may first require allowing installations from Lirix.

Downloads use app-private cache, show progress and can be cancelled. Activity destruction cancels the transfer; a retry starts from zero. Partial files are removed. The app checks the GitHub SHA-256 digest, exact byte size, package ID, increasing Android version code and matching signing certificates. The installer performs Android's final validation. No uninstall, data clearing or downgrade is attempted.

To publish a discoverable update:

1. Increase `versionName` and `versionCode` in `app/build.gradle.kts`.
2. Sign with the same permanent release key as the installed stable version. Never replace it with a CI-generated debug key.
3. Publish a stable GitHub Release tagged `vMAJOR.MINOR.PATCH` with exactly one uploaded `.apk`. Desktop assets and a checksum text file can coexist. GitHub must expose the APK's `sha256:` asset digest.
4. Verify an actual in-place update on a phone containing the preceding stable build and representative user data before announcing rollout.

Drafts, prereleases, malformed tags, multiple APK variants, missing digests and foreign download URLs are excluded. Release selection examines the first 30 public releases and compares numeric version components. Key rotation is conservatively rejected; supporting it requires a separate signing-lineage policy.

Local debug builds may have a different certificate from the public stable APK. The app explains this mismatch and preserves data; disabling signature checks cannot make Android accept an incompatible update.

References: [GitHub release API](https://docs.github.com/en/rest/releases/releases), [Android installation permission](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()).
