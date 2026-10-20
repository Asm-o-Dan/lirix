# ANDROID-BACKUP-004 — persistence coverage gate
Status: DONE. Spec: ../specs/media-core/android_system_backup_v1.md
Add tests of real manifest/resources and persisted preference inventory. Unknown
new preference stores must force explicit review, not silently enter backup.
Verify Android tests, shared desktop tests and debug APK compilation offline.
No cloud/phone erase or restore action is authorized by this card.
