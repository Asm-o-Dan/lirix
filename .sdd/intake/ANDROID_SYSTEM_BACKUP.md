# Android system backup — accepted intake, 2026-10-20

User explicitly requested system backup after identifying that the Android app has no full user-facing database export/restore. Implement Android Auto Backup and device-transfer configuration for supported OS versions; no PC change or new manual archive UI in this package.

## Required outcomes
- User-owned library data (history, favorites, notes, lyrics/timings, saved translations and source rules) and persistent user settings are eligible for backup.
- Exclude Connect identities, pairing/trust information, remembered device addresses, regenerable artwork, temporary/share files and caches.
- Use actual storage paths verified by architect; account for SQLite WAL/sidecar consistency under framework backup lifecycle.
- Configure Android 8–11 rules and Android 12+ cloud/device-transfer rules with platform-appropriate encryption conditions.
- No status that falsely says a backup exists; system schedule, user enablement, transport support and cloud quota remain external conditions.
- No device data wipe, uninstall, restore over the user's library or forced cloud upload for testing.
- Test real configuration/manifest resources and build an APK; distinguish these checks from an actual OS backup/restore round trip.

## Release and migration boundaries
The published v1.4.0 had allowBackup=false and a different signing key. Enabling backup in a later build does not retroactively create a backup for that app and does not bypass restore signature checks. Manual archival migration remains a separate unresolved need.

The remote v1.5.0 tag already exists, but no release assets have been published. Preserve existing tags and v1.4.0 assets; do not upload the earlier APK and imply it includes these changes. Version/tag integration must be resolved explicitly before eventual publication.

Implementation owner: android_system_backup. Root coordinates documentation, gates and commit. Source/build ownership remains exclusive.
