# LOOKUP-RETRY-004: acquireOrJoin

Status: ASSIGNED — offline_recovery_finish; sequential dependency order.

Function/owned slice: `app/feature/lyrics/AutomaticLookupCoordinator.kt` — `acquireOrJoin`. Frozen contract: [.sdd/specs/android-ui/offline_lookup_recovery_v1.md](../specs/android-ui/offline_lookup_recovery_v1.md), approved revision3.

Acceptance/test oracle: Finite single-flight with cleanup; concurrency/timeout tests.

Independent review/DoR constraints apply. No other backlog, schema, transport rewrite or whole-file main/canonical transplant. Root owns builds/runtime/Git; executor writes targeted code/tests only.
