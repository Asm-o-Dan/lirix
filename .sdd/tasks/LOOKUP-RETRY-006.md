# LOOKUP-RETRY-006: observe

Status: ASSIGNED — offline_recovery_finish; sequential dependency order.

Function/owned slice: `app/feature/lyrics/LookupNetworkMonitor.kt` — `observe`. Frozen contract: [.sdd/specs/android-ui/offline_lookup_recovery_v1.md](../specs/android-ui/offline_lookup_recovery_v1.md), approved revision3.

Acceptance/test oracle: Initial/default validated snapshot, callback cleanup; network event tests.

Independent review/DoR constraints apply. No other backlog, schema, transport rewrite or whole-file main/canonical transplant. Root owns builds/runtime/Git; executor writes targeted code/tests only.
