# LOOKUP-RETRY-001: fillMissingAutomaticLookup

Status: ASSIGNED — offline_recovery_finish; sequential dependency order.

Function/owned slice: `shared/storage/LyricsDao.kt` — `fillMissingAutomaticLookup`. Frozen contract: [.sdd/specs/android-ui/offline_lookup_recovery_v1.md](../specs/android-ui/offline_lookup_recovery_v1.md), approved revision3.

Acceptance/test oracle: Atomic manual/rejections/fresh-field authority and two-table fill; Room negative race tests.

Independent review/DoR constraints apply. No other backlog, schema, transport rewrite or whole-file main/canonical transplant. Root owns builds/runtime/Git; executor writes targeted code/tests only.
