# Player and line studio: polish contract

Goal: remove clipped text and awkward spacing, keep contextual menus adjacent to their triggers, make screen/mode transitions smooth, and deliver precise line timing with a real waveform while preserving phone Tap-to-Sync.

## Current-state audit (2026-10-24)

Source audit, not a new device screenshot review. Preserve the existing Material 3 components, theme tokens, Golos interface text, Literata lyrics, three main tabs and four player modes. The web design skill explicitly excludes native mobile, so its web palette/layout rules do not apply. No branding or navigation rename.

Confirmed source problems: library search row and Material TextField constrained to 48 dp; player metadata constrained to 64 dp despite scaled three-line text; portrait mode labels constrained to one 48 dp band; studio action rows do not wrap; timeline scrolls away with the line list; zoomed ruler discards fractional seconds; equal-time marker hit testing always chooses the first occurrence; reduced-motion preference cached at first composition. The existing popup is correctly anchored in its trigger Box and filters actions by mode, but physical bounds/scroll/RTL acceptance remains unproven.

## Required behavior

- Use existing native typography/themes. Search, metadata, mode labels and studio controls adapt to font scaling and narrow screens. Lyrics and errors remain reachable without silently truncating their content. No additional top/bottom safe-area padding duplication.
- Mode selector retains equal-width targets and existing names; each target is at least 48 dp, has selected/tab semantics and can grow to fit wrapped labels.
- Menus dismiss on track/mode/orientation changes; Text exposes edit/sync/translations, Karaoke exposes sync/calibration, Chords excludes lyric-only commands, Notes exposes save. No duplicate Connect button. Native popup scrolling and proximity to the trigger require actual UI acceptance.
- Main tabs retain saved state. Player modes crossfade gently; disabled system animations take effect in an already open app and after return from Settings. Do not use animations to hide data loading or introduce delayed taps.
- Precise studio keeps the timeline visible while scrolling on a tall portrait display at normal font scale, and uses ordinary scrolling on short/large-font layouts. Controls wrap rather than overflow. Zoom states have accessible names and limits.
- Ruler drawing, hit testing and dragging share one millisecond coordinate model. Selected occurrence wins exact marker ties; actual nearest marker wins otherwise. Tick labels retain fractional seconds at close zoom and space themselves for their real measured width. Drag displacement starts at the first pointer down, including movement spent crossing touch slop. Drag cancellation never commits; completed drag creates one content edit. Numeric entry/nudging remain alternatives to dragging.
- Preserve captured document, raw timestamps, undo/redo, tail shift, draft recovery and validated LRC application. Studio export retains all three millisecond digits; legacy one/two-digit imports remain valid. Playback parser accepts 100+ minute timestamps and rejects overflow rather than wrapping or changing them to zero. Keep quick stamping and precise mode on the same document/history.
- Waveform is the real decoded selected local file; no fabricated bars. File mismatch/access/decode errors remain visible. A checked-in original two-second opposite-phase stereo envelope fixture tests exact timeline/amplitude rather than nonempty files. Decode on a physical Android remains a required gate.

## Evidence gates

1. Android build and release lint; studio reducer/loop/draft/duration/PCM regressions, coordinate/tie/label/spacing tests and actual system-setting reads.
2. Physical narrow portrait/landscape, RU/EN, two themes, 140%/200% fonts: full line scrolling, menu anchor/scroll/context, unique Connect, mode targets and insets.
3. Physical real-file extraction/decoding with known duration/silent/low/high regions; selected song/file mismatch warning; seek/loop and drag/cancel/undo, exact time entry and save/reopen/process restart. Preserve text/notes/chords and quick stamping.
4. Actual transitions with animations enabled/disabled, including toggling while app remains alive. A compiled test or source inspection does not count as a runtime pass.

Do not mark the overarching goal complete until every gate has current authoritative evidence. Keep existing unrelated working edits outside scoped commits.
