# Android player design handoff implementation

Scope: D01–D05 in LIRIX_PLAYER_DESIGN_HANDOFF_20261029.md, using existing theme and callbacks. No changes to source selection, persistence or network contracts. Status labels must reflect the active mode and accepted content; incomplete lookup is not a confirmed absence.

Acceptance: centered controls with accessible names and 48 dp targets; mode-specific noncontradictory status; readable, wrapping actions; metadata opens existing track info independently of favorite; search hint, semantics and clear control; RU/EN resources. Device, orientation, font, themes, TalkBack, error cases and comparative screenshots form separate runtime acceptance evidence. Preserve existing user changes.

Implementation: inspect and verify controls; adapt player status and actions, metadata click, then library search. Run Android compilation and focused checks. Do not claim device or visual acceptance without actual execution.
