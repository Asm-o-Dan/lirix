# LOOKUP-RETRY-004: acquireOrJoin

Status: COMPLETE — source committed as 02b6dc9; automated/emulator verification PASS.

Evidence: ANDROID_OFFLINE_LOOKUP_QA_20261025.md, final checkpoint 2026-10-26. Android346/shared249 and native19 passed. Physical phone/visual acceptance is not claimed; user explicitly cancelled APK delivery.

Function/owned slice: `app/feature/lyrics/AutomaticLookupCoordinator.kt` — `acquireOrJoin`. Frozen contract: [.sdd/specs/android-ui/offline_lookup_recovery_v1.md](../specs/android-ui/offline_lookup_recovery_v1.md), approved revision3.

Acceptance/test oracle: Finite single-flight with cleanup; concurrency/timeout tests.

Independent review/DoR constraints apply. No other backlog, schema, transport rewrite or whole-file main/canonical transplant. Root owns builds/runtime/Git; executor writes targeted code/tests only.
