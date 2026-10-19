# Desktop draft safety v1 — 2026-10-19

Status: approved implementation scope. User authorized direct implementation without subagents.

Session-owned drafts for lyrics/LRC/chords, Tap-to-Sync and notes outlive Workspace destruction (tray hide or compact mode). No disk autosave or crash recovery is claimed. Editor and sync close requests confirm dirty changes with save/discard/cancel; failed save retains content and displays an error inside the dialog. Duplicate saves and edits while saving are blocked. Chords load asynchronously once: text edits survive completion, chord editing/save waits for successful load, failed load offers retry. Invalid/incomplete LRC cannot be saved from confirmation. Explicit application exit with unsaved or in-flight changes requires returning to work or explicit discard. App hide is safe without prompting because draft objects live above its window.

`DesktopDraft<T>.save(writer: suspend (T)->Unit): Boolean` captures current value, prevents duplicate calls, clears dirty only on success; failure stores error and retains draft; cancellation propagates and releases busy. `loadChords` initializes only chord baseline/content, preserving existing plain/LRC edits.

Tests cover failed and duplicate save, cancellation, successful acknowledgement, undo-to-baseline, delayed chord loading, draft reuse across reconstructed views, notes and Tap-to-Sync retention. Native close/tray/exit and visual confirmation remain separate acceptance gates.
