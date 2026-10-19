# Draft safety verification — 2026-10-19

Direct implementation by root under explicit user authorization; no subagents and no independent review claimed.

Application-owned store keeps lyrics/LRC/chords, Tap-to-Sync lines and notes across studio disposal. Reopened editors reuse these objects; pending draft entries allow returning to a track. Native editor close and Cancel ask save/discard/continue. Invalid LRC or incomplete sync cannot bypass validation through close confirmation. Async save failure is shown inside the retained editor; duplicate writes are blocked. Cancellation keeps the draft dirty. Chord-load completion only initializes chord content/baseline, preserves earlier text/LRC edits, cannot overwrite a subsequent chord edit; editing chords and saving wait for successful load. Failed load offers retry.

Explicit tray exit offers return-to-work or exit without saving when dirty/in-flight work exists. Hiding the window retains drafts without a prompt. No disk autosave, crash recovery, forced-process protection or cross-device draft sync is implemented. Notes acknowledgement still follows the repository stream; failures retain draft text and existing repository error indication.

Six regression tests exercise failure/retry, duplicate concurrent save, cancellation, edit-after-save-snapshot, late chord initialization and reuse of application-owned drafts. Native modal interactions and screen rendering are not asserted by these model tests. Existing playback and native bridge code is unchanged.

Final verification: :desktop:test :shared:desktopTest :desktop:createDistributable succeeded (1m11s). Desktop 87 tests, zero failures/errors; shared 173, zero failures/errors (UP-TO-DATE). Built desktop jar and packaged jar SHA256 EEC9CB92438285C09E2446D224A137615401C7097A8C05F5C6BDF7B0F0C5EC59 match. Native resource and packaged bridge SHA256 99B820142E4A741DDC20B8DD4AACCAAA3F33B27E6020EAFA76CF66C986D28C78 match. git diff --check passed. No runtime launch or release publication.
