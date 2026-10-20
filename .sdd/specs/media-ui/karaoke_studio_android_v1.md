# Android karaoke editing UI v1

Status: DEFERRED for current delivery; existing foundations fixes precede advanced Studio.

Default remains Tap-to-Sync. Its `Открыть в студии` action switches the same captured edit session. Karaoke menu also offers `Настроить тайминги` opening Studio. Back switches modes without losing markers; close flushes draft, does not imply applying to the song.

Studio is full-screen with system-bar/IME insets: header captured song title + close; preview selected/neighbor lines; timeline; selected line list and editing controls in scrollable available space; persistent transport and explicit `Применить к песне`. Compact phone height must not force text outside its region; controls have >=48 dp interaction areas, accessible labels and numeric editing alternative to dragging. Use existing theme tokens and typography.

Timeline has fixed center cursor, draggable horizontal time ruler, selectable draggable line markers, zoom +/- (bounded 2..120 seconds visible), and explicit current time. Formula `x = center + (markerTime - cursorTime) * pxPerMs`; pan adjusts cursor with inverse delta and clamps 0..known duration (or max marked time +30 s if unknown). Dragging a marker edits that marker; dragging background pans. They must not both consume the same gesture. During drag stop follow-playhead; `К воспроизведению` resumes. No automatic seek on every pointer sample. Release ruler may seek only if captured track matches current source and source supports seek; otherwise it remains an editing cursor with explanatory label.

Selected line actions: set at cursor, -50 ms, +50 ms, editable numeric time, shift selected and following by explicit delta. Show chronology error without changing content. Undo/redo and restore-original confirmation are reachable. Quick mode retains large next-line stamp and reaction-lag setting; Studio direct edits ignore reaction lag.

Loop uses explicit A and B from cursor; require B-A >=500 ms, valid bounds and supported seek. At B issue one seek to A, rearm after observed playback position returns inside loop; timeout/discontinuity disables loop with explanation rather than repeated seek storms. Leaving editor or changing captured track disables loop. No playback control sent to a different song. Without a seek-capable external source markers remain editable, transport/loop unavailable with reason. Do not infer seek capability merely from positive duration.

Timing correction UI: same reusable component in portrait/landscape, bounded source-of-truth values, labelled text earlier/later, +/-50 ms, press-and-hold after 400 ms repeating every 100 ms (release/cancel stops), numeric input and reset. Per-song correction and device compensation have separate labels. Never duplicate offset into LRC.

Keep one Connect entry in portrait header; remove redundant transport copy. Karaoke row keys must support equal timestamps, geometry must remain stable across active-row transitions, and first/last full multiline entries must be reachable. Test 360x640 portrait and 800x360 landscape at font scale 1.0/1.3, 200% karaoke text, long Cyrillic and Latin lines, both light/dark theme and IME.

Waveform is explicitly deferred; v1 time ruler is honest empty-background timeline with markers, not synthetic sound bars. Future local audio waveform requires real decoded samples and separately authorized media access. No external stream downloads added here.
