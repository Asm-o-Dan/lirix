# ROMAN-FIX-008 — presentReadingResult

Status: proposed, await root contract approval.
Spec: .sdd/specs/lyrics-engine/romanization_repair_v2.md
Zone: Android/component
Owner: romanization_audit
Depends on: 003,007
Single target function: presentReadingResult

## Contract
Presentation-only RomanizedKaraokeLine; safe partial fallback, scaled reading font, no backend calls during composition.

## RED gate
Before implementation add focused assertions from spec section6 for this function. Record failing assertions against old behavior; root schedules test commands. No dependency installation before root approves section3.

## Done
Function contract/tests green; source snapshots preserved; no unrelated hunks; report precise files and validation. Runtime/backend functions require exact-artifact JVM and Android evidence; compile alone insufficient. No release/stage/commit by this owner.
