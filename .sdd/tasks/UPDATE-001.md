# UPDATE-001 — Android GitHub delivery

Status: implementation, seven unit tests, debug APK and release lint PASS; physical update acceptance pending (ADB empty).

Contract: ../specs/android-ui/github_updates_v1.md

Acceptance: stable numeric release selection, reject draft/preview/foreign URL/ambiguous APK/missing digest; verify actual byte count and SHA-256 before package/version/signer preflight; scoped content URI and Android consent; manual RU/EN update screen; daily foreground check; cancellation deletes partial download. APK compilation and release lint must pass. A complete real installation acceptance requires a higher versionCode APK with the installed certificate, never uninstalling the app to force success.
