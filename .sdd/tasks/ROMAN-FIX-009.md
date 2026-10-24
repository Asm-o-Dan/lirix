# ROMAN-FIX-009 — loadLyricsLanguage

Status: proposed, await root contract approval.
Spec: .sdd/specs/lyrics-engine/romanization_repair_v2.md
Zone: Android/preference
Owner: romanization_audit
Depends on: 001
Single target function: loadLyricsLanguage

## Contract
Read isolated per-track lyrics-language override; no global UI-locale coupling or library schema mutation.

## RED gate
Before implementation add focused assertions from spec section6 for this function. Record failing assertions against old behavior; root schedules test commands. No dependency installation before root approves section3.

## Done
Function contract/tests green; source snapshots preserved; no unrelated hunks; report precise files and validation. Runtime/backend functions require exact-artifact JVM and Android evidence; compile alone insufficient. No release/stage/commit by this owner.
