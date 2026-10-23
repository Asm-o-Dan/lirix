# CHORD-FIX-008: golden corpus gate

Status: approved by coordinator; implementation pending verification.
Contract: `.sdd/specs/lyrics-engine/chord_transpose_repair_v1.md`.
One function / gate: `golden corpus gate`. Load checked-in chord corpus and independent expected output; run old RED/new GREEN plus OOV/pitch/context invariant tests.
Allowed scope: shared ChordTransposer.kt and dedicated ChordTransposerRepairTest.kt + fixtures/chords resources only.
DoR: coordinator approves exact contract, baseline captured. DoD: independent expected values and negative/OOV invariants per contract; no mock-only or implementation-mirroring tests; root records real test result and reviews isolated patch. Unknown symbols must not be partially consumed. Do not edit caller UI or another worker's files.
