# Karaoke studio — accepted intake, 2026-10-20

Status update: user subsequently requested existing karaoke corrections FIRST. Studio feature delivery is DEFERRED until text offset, seek, timestamp persistence, clipping and duplicate Connect fixes are verified. This document remains the future roadmap, not the active implementation package.

User approved implementation after the proposed plan: retain mobile Tap-to-Sync and add a separate precise studio using the same song markup.

## Delivery order
1. Establish correct offset/seek/save behavior and correct karaoke clipping and duplicate device actions.
2. Add studio entry from Tap-to-Sync and karaoke actions, retaining current line and draft.
3. Line-level timeline: select/drag markers, fixed center cursor and scroll/zoom, precise +/-50 ms and time entry, stamp selected line, shift selected and subsequent lines, loop region, undo/redo.
4. Persist drafts bound to a captured song; explicit Apply updates saved lyrics; original is recoverable. Track changes must not retarget edits. Invalid ordering is visible and cannot silently corrupt lyrics.
5. Real waveform for accessible local audio is a later increment. Timeline remains useful without waveform; external-source seek limitations must be explicit.

## Acceptance
- Long intro, correction of one late line, shift of a chorus and reopen-after-close workflows.
- Small screen, long lines, light/dark themes; controls remain reachable.
- Existing Tap-to-Sync and desktop/native audio integrations preserved.
- Automated checks and actual visual evidence distinguished from physical playback acceptance.
- No published release changes; no unrelated files or user attachments committed.

## Scope exclusions for first studio increment
Word timing, romaji generation, translation-to-timestamp alignment, fabricated audio waveforms.

## Process
Latest supplied AGENTS.md assigns root coordinator duties. Architect owns source analysis and function-level specifications before implementation. No invoke_subagent. Coordinator records gates and routes implementation/review. Existing user attachments are untracked inputs, not generated product changes.
