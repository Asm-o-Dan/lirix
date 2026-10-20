# Android 1.5.1 — published, 2026-10-20

Release: https://github.com/Asm-o-Dan/lirix/releases/tag/v1.5.1

Public API and authenticated browser UI confirm draft=false, prerelease=false and Latest. Uploaded assets are exactly lirix-v1.5.1.apk and SHA256SUMS.txt; GitHub additionally exposes its generated source archives. No Windows installer was uploaded; the tag workflow Windows job is skipped. Published v1.4.0 release ID, prerelease flag and all asset IDs/names/sizes/digests match the pre-publication snapshot.

## Artifact identity

- APK size: 19116225 bytes.
- SHA-256: 2042609e23ae7345db6f5b5e8ef2395c02ea847a977f06a92f8176a199e29b9a.
- Package com.lirix.app, versionName 1.5.1, versionCode 19, minSdk 26, targetSdk 35; not debuggable.
- Public signing certificate SHA-256: 3473248e71212d53481eb0296c258e250c86cb82a9d2352020ef6572ba16bdeb.
- Frozen source/tag commit: f21f94e7c5fd0f2059c5424e1a52c105b7c0202f, also verified in META-INF/version-control-info.textproto.

apksigner verification passes. The optimized release APK shortens resource file names; aapt resource-table resolution verified default/v28 backup_rules and modern data_extraction_rules, exact nine paths per policy and the modern cloud encryption/device-transfer configuration. Checking literal source resource filenames in an optimized APK was replaced by resolving compiled resource IDs, without changing product source.

After publication, both files were downloaded again from their public GitHub asset URLs. APK hash and checksum-file contents match the local verified artifact. Public GitHub digest also matches.

## Tests and limits

Full Android209 tests pass; shared173 unchanged UP-TO-DATE, no failures/errors/skips. Release lint passes after removing redundant exclusions without any suppression or expanded include scope. Independent policy-delta source review PASS. Frozen-source release assembly succeeds after refreshing commit metadata. No PC source or package version changed for this release.

Browser upload permission was granted for these two assets; the earlier denied 1.5.0 upload was not bypassed. Final browser page visibly shows Latest, v1.5.1 and both asset links.

No uninstall, user-data deletion, phone installation, actual cloud backup or OS restore round trip was performed. Release notes explicitly explain the old-signature incompatibility, absent manual archive UI and system-backup limitations. No private key or credentials were committed/uploaded.

An unrelated untracked .sdd/WORKFLOW.md appeared during publication and was preserved rather than included in the release work. This follow-up documentation commit does not move the frozen release tag.
