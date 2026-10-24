# ROMAN-FIX-003 — classifyReading

Status: proposed, await root contract approval.
Spec: .sdd/specs/lyrics-engine/romanization_repair_v2.md
Zone: common
Owner: romanization_audit
Depends on: 002
Single target function: classifyReading

## Contract
Emit COMPLETE/PARTIAL/UNAVAILABLE/NEEDS_LANGUAGE from backend evidence and residual target script; original unchanged.

## RED gate
Before implementation add focused assertions from spec section6 for this function. Record failing assertions against old behavior; root schedules test commands. No dependency installation before root approves section3.

## Done
Function contract/tests green; source snapshots preserved; no unrelated hunks; report precise files and validation. Runtime/backend functions require exact-artifact JVM and Android evidence; compile alone insufficient. No release/stage/commit by this owner.
