# Android system backup implementation — 2026-10-20

Implemented against `media-core/android_system_backup_v1.md`. Android only;
no desktop source changes, no schema/version/signing changes, no publication.

## Inventory and policy

Room's `event_engine.db` contains user tracks/favorites, listening history,
lyrics and timing markup, notes, rules, blacklist, achievements and saved
translations. Android factory uses WRITE_AHEAD_LOGGING. Each policy includes
the DB plus `-wal`, `-shm`, `-journal`; framework full-backup process isolation
is used without a custom agent or manual live-database copying.

Five backed-up preference stores: `lirix_preferences`, `lirix_translations`,
`lirix_ui_prompter_prefs`, `lirix_sync_prefs`, `media_sync_offsets`.

Denied by omission from the allowlist: `lirix_trusted_devices.xml`, `lirix_device_addrs.xml`,
`files/album_art/`. Pairing needs to be performed again after restore; artwork
is derived and may reload. Only nine explicit paths are included per transport.
No default wildcard includes, broad root exclusions, future credential stores,
external music files, caches or shared poster files enter this allowlist.
No Android Keystore-backed persisted store was found in current Android code.

API 26–27: base legacy XML without unsupported encryption flags. Transport /
account protection applies; no end-to-end encryption capability guarantee.
API 28–30: xml-v28 legacy variant requires clientSideEncryption on every include;
older D2D transports without that flag can omit this data. API 31+: cloud has
disableIfNoEncryptionCapabilities=true and a separate unrestricted-by-cloud-
capability device-transfer section with identical explicit data scope.

## Verification

Command: `gradlew.bat :app:testDebugUnitTest :shared:desktopTest :app:assembleDebug
--offline --no-daemon --no-configuration-cache --max-workers=2`.
BUILD SUCCESSFUL, 2m 6s. Fresh Android unit results: **209 tests, 0 failures,
0 errors, 0 skipped**. Five new AndroidBackupPolicyTest tests passed. Shared
desktop tests were UP-TO-DATE, not re-executed. Independent source review PASS.

Policy tests inspect actual manifest/resources, compare all four transport /
API policy sections, verify effective DB-sidecar coverage, deny secrets and unknown data,
check physical include/exclude overlap, API-specific encryption, and inventory
the current Android app/shared preference names.

Actual APK `aapt dump xmltree` confirms allowBackup=true, fullBackupContent
resource 0x7f0f0000 and dataExtractionRules resource 0x7f0f0001. APK list contains
base and v28 backup_rules plus data_extraction_rules. Binary v28 policy has nine
clientSideEncryption flags. Binary modern policy has cloud encryption flag true,
separate device-transfer and the expected database paths. The earlier debug APK
also contained redundant explicit exclusions; release lint subsequently rejected
those out-of-allowlist rules. They have been removed without expanding backup
eligibility. The earlier debug build did not run lintVitalRelease and was not
sufficient release validation. Focused policy tests now check effective physical
path eligibility, rather than requiring redundant exclude elements.

Release-lint correction verification on Android version 1.5.1/code 19:
`gradlew.bat :app:testDebugUnitTest --tests '*AndroidBackupPolicyTest'
:app:lintVitalRelease --offline --no-daemon --no-configuration-cache
--max-workers=2` completed **BUILD SUCCESSFUL in 1m 15s**. All five focused
policy tests passed; lintVitalAnalyzeRelease, lintVitalReportRelease and
lintVitalRelease completed successfully without suppressions or a baseline.
Independent review of this final delta passed. The exact nine-path eligibility
scope and API encryption predicates are unchanged. Final signed release APK
assembly and publication remain owned by the parent release task.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
SHA256: `32d874e6b7d2ad05423d3e7986efb1858dbc21ce995f59bb2d970de1f5f1628a`.
This hash describes the earlier debug-validation artifact before the release-lint
correction, not a final release candidate. The existing signed `build/release150` artifact was not touched and does not
contain this new implementation.

## Operational limits

OS/account/user settings control whether and when backups occur; cloud has a
25 MB per-app quota. There is no app-reported success status. No phone install,
cloud upload, destructive restore or physical device transfer was performed.
Permissions and external URI grants are not restored by this policy.

This cannot retroactively back up 1.4.0 (allowBackup=false), bypass restore
signature matching or permit an APK signed with a different key to update the
old installation. Manual archive import/export is outside this scope. Before
releasing a new signed build, root must reconcile the already-pushed immutable
v1.5.0 tag with these later source changes; nothing here moves that tag.

References: Android Auto Backup documentation and framework signature rules:
https://developer.android.com/identity/data/autobackup
https://android.googlesource.com/platform/frameworks/base/+/master/services/backup/java/com/android/server/backup/utils/BackupEligibilityRules.java
