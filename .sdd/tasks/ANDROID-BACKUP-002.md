# ANDROID-BACKUP-002 — legacy full-backup policy
Status: DONE. Spec: ../specs/media-core/android_system_backup_v1.md
Implement exact database/sidecar and preference allowlist, API26 base and API28
clientSideEncryption variant. Deny Connect and derived artwork by omission from
the allowlist; do not add redundant out-of-allowlist exclude nodes.
Done when deny/allow policy tests pass.
