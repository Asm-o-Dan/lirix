# CHORD-FIX-009: isControl

Status: approved contract grammar extracted into private helper; implementation pending coordinator verification.
Contract: `.sdd/specs/lyrics-engine/chord_transpose_repair_v1.md` isChordLine/transposeChordLine control-token rules.
Single function returns true only for bar punctuation or repeat markers, never chord roots or prose. Shared by classification and replacement to prevent disagreement. Covered by the same real fixture and negative prose/OOV gates; root executes tests. No UI changes.
