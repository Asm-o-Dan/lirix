# Desktop karaoke missing mode — 2026-10-17

User first reported that karaoke did not work. Clarification: the PC has a Text tab but no Karaoke tab. Treat this as a missing user-visible mode, not evidence of a reproduced clock failure.

Acceptance intent: a clearly named Karaoke mode on desktop, timed highlighting and following the current playback for synchronized lyrics, plain lyrics remaining readable in Text. Unsynchronized lyrics must explain missing timing and offer the existing Tap-to-Sync workflow; never invent timestamps. Preserve theme, seeking, offset and existing chords behavior.

Architect/specifier: karaoke_architect. QA: karaoke_qa. Root coordinates per latest AGENTS.md. Product implementation follows an approved specification and atomic cards.

Additional runtime evidence, separate from the user symptom: `desktop/build/parity-smoke/stderr.log` updated 2026-10-17 10:54:54 reports missing Main coroutine dispatcher in Main.kt:68 / GlobalHotkeysManager.kt:46. Determine whether this is a current code/package defect; do not attribute missing tab to the exception without evidence.

Native UI capture failed twice during the preceding task. Existing automated test passes are not proof of visible karaoke behavior. Require explicit remaining native acceptance if capture remains unavailable.
