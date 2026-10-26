# LOOKUP-RETRY-009: automatic overlay lookup block

Status: ASSIGNED — offline_recovery_finish; sequential dependency order.

Function/owned slice: `app/service/FloatingLyricsService.kt` — `automatic overlay lookup block`. Frozen contract: [.sdd/specs/android-ui/offline_lookup_recovery_v1.md](../specs/android-ui/offline_lookup_recovery_v1.md), approved revision3.

Acceptance/test oracle: Join common automatic gate without duplicate save; preserve service state.

Independent review/DoR constraints apply. No other backlog, schema, transport rewrite or whole-file main/canonical transplant. Root owns builds/runtime/Git; executor writes targeted code/tests only.
