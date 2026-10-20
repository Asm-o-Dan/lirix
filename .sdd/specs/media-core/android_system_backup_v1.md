# Android system backup v1

Status: FROZEN for implementation, 2026-10-20. Scope: Android only.

## Contract

Enable Android Auto Backup using an explicit allowlist. API 26–30 uses
fullBackupContent; API 31+ uses dataExtractionRules with separate cloud and
device-transfer sections. Cloud requires client-side encryption when the
platform supports enforcing it: API 28–30 uses an xml-v28 legacy variant with
requireFlags="clientSideEncryption". API 26–27 has no such flag and retains
platform/account transport encryption without an end-to-end guarantee. Legacy
D2D eligibility depends on the transport advertising client-side encryption;
API 31+ has a distinct D2D section without that cloud restriction. Flags are not
combined as an OR expression. Android controls execution, account, quota and restore;
the application must not claim a backup succeeded or offer a fake backup button.

Allow only the Room database event_engine.db and SQLite sidecars (-wal, -shm,
-journal), plus five SharedPreferences: lirix_preferences, lirix_translations,
lirix_ui_prompter_prefs, lirix_sync_prefs, media_sync_offsets. This preserves
library/history, favorites, lyrics/timings/notes, rules, blacklist, achievements,
translations and user presentation preferences. Explicit sidecars preserve WAL
data with the platform full-backup process isolation; do not manually copy an
open SQLite database or instantiate Room in a custom backup agent.

Do not include general database/root/file/sharedpref directories. Deny Connect
trusted devices and saved network addresses by omitting them from the allowlist,
requiring pairing again. Do not add redundant exclude nodes for files outside
the allowlist: Android FullBackupContent release lint rejects these rules. No signing
keys, Android Keystore material, device identity, network pairing secrets, cache,
temporary poster shares, unknown future files or device-protected storage are
eligible. files/album_art is a derived cover cache and excluded to protect the
25 MB cloud quota. Album art may reload after restore; source music files and
external URI permissions are not transferred by these rules.

## Limits

This does not create a backup retroactively for 1.4.0 with allowBackup=false,
does not make differently signed APKs update-compatible, and does not bypass
Android restore signature checks. Data migration from the old signing identity
still needs an independently authorized strategy. OS permissions/pairing may
need to be granted again. No manual archive exporter or desktop changes here.

## Acceptance

Manifest points at both valid policy resources, enables backup and does not
enable restoreAnyVersion. Unit tests check actual manifest/XML, identical
allowlists across three policies, API-qualified encryption predicates, current persisted preference coverage,
unknown-file denial, Connect denial and complete SQLite sidecars. Existing
Android/shared tests, APK assembly and lintVitalRelease pass. Debug assembly alone
does not run the release backup-policy lint gate. Device/cloud restore remains an
explicit unverified acceptance step unless tested on a disposable device.
