# LOOKUP-RETRY-008: prefetchLyricsAsync

Status: ASSIGNED — offline_recovery_finish; sequential dependency order.

Function/owned slice: `app/feature/MusicFeatureEngine.kt` — `prefetchLyricsAsync`. Frozen contract: [.sdd/specs/android-ui/offline_lookup_recovery_v1.md](../specs/android-ui/offline_lookup_recovery_v1.md), approved revision3.

Acceptance/test oracle: Join common automatic gate; preserve existing network policy/session work.

Independent review/DoR constraints apply. No other backlog, schema, transport rewrite or whole-file main/canonical transplant. Root owns builds/runtime/Git; executor writes targeted code/tests only.
