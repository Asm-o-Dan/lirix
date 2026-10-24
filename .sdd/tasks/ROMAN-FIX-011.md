# ROMAN-FIX-011 — bindReadingDocument

Status: proposed, await root contract approval.
Spec: .sdd/specs/lyrics-engine/romanization_repair_v2.md
Zone: Android/root-integration
Owner: root/localization
Depends on: 007,008,009,010
Single target function: bindReadingDocument

## Contract
Root-owned function binds both text/karaoke to same current document; generation/cancellation guard; separate lyrics-language choice UI.

## RED gate
Before implementation add focused assertions from spec section6 for this function. Record failing assertions against old behavior; root schedules test commands. No dependency installation before root approves section3.

## Done
Function contract/tests green; source snapshots preserved; no unrelated hunks; report precise files and validation. Runtime/backend functions require exact-artifact JVM and Android evidence; compile alone insufficient. No release/stage/commit by this owner.
