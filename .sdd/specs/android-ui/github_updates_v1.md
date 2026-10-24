# Android: GitHub update delivery

User request (2026-10-24): deliver Android updates from GitHub without manually downloading/installing an APK. Desktop remains out of scope.

- Source: public Asm-o-Dan/lirix releases, first 30 releases. Select numerically greatest stable x.y.z tag newer than installed version, excluding drafts/previews, releases without exactly one uploaded APK and assets without a SHA-256 digest. No embedded API token.
- Check at foreground launch at most once per 24 hours. Failure stays quiet; manual check remains available in player overflow → Lirix updates.
- Show available version. Download starts only on explicit action; show progress, cancellation and retry. Download is tied to the activity lifecycle; interruption deletes partial data and retry restarts from zero. No claim of background/resumable transfer.
- Restrict original asset URL to this repository/tag. Bound metadata to 2 MB and APK to 300 MB. Require exact asset size and GitHub SHA-256; validate package ID, strictly increasing versionCode and identical nonempty signer sets before installation.
- Store only in private cache/updates; grant installer temporary content URI read access through scoped FileProvider. REQUEST_INSTALL_PACKAGES is declared. If needed, open Android's per-app unknown sources permission and retry after returning.
- Android installer owns final consent and signature verification. Never uninstall, downgrade, clear data or promise silent installation. Debug/release signer mismatch must explain why updating this build is impossible and preserve data.
- UI copy RU/EN, existing theme/localization wrapper. No app version bump or release publishing in this task.

Known limits: strict signer equality deliberately rejects key rotation until a lineage-aware policy is implemented; no delta patches, process-death download restoration or unattended installation. A future stable release must retain the permanent release signer, increase both versionName and versionCode and contain exactly one Android APK with GitHub digest.
