# Karaoke studio acceptance scenarios

Status: acceptance requirements, not executed evidence. Date: 2026-10-20.

| Scenario | Expected result |
|---|---|
| Existing phone Tap-to-Sync | Fast sequential stamping remains available without entering studio. |
| Open studio after stamping three lines | Same timestamps and selected line; no reset or duplicate song data. |
| Song starts after long intro | First marker can move to actual first vocal; preview stays inactive before it. |
| Adjust one line by 50 ms | Exactly the selected timestamp changes; neighboring timestamps retain values. |
| Move marker past neighbor | Conflict is visible; cannot silently save reversed timestamp ordering. |
| Shift selected line and following lines | Earlier markers unchanged; affected markers retain spacing; out-of-range result is rejected atomically. |
| Enter invalid time | Visible error, no mutation of saved song or draft timestamps. |
| Undo then redo grouped shift | Whole operation reverses/restores together, including selected line identity. |
| Play a short loop | Playback seeks within selected range only if current source supports seeking; unsupported action is explicit. |
| Background track changes | Draft still belongs to original song; new playback cannot be used to stamp wrong-song positions. |
| Close and reopen editor | Draft and selection recover for same song; original saved lyrics remain unchanged until Apply. |
| Save fails | All edits remain available and retryable; no false success. |
| Restore original | User can recover saved baseline without losing the only copy through implicit overwrite. |
| No accessible source audio | Useful timeline and line markers, no fake waveform. |
| Narrow phone, large font, multiline lyrics | Current line and editing controls readable/reachable without text clipped by transport. |
| Offset reset/repeated changes/reopen | Stable direction and persisted value for same track; correct inverse seek mapping. |
| Desktop playback and Connect | Existing playback/seek/audio paths retained; no physical acceptance inferred from JVM checks. |

Do not mark a scenario PASS based only on presence of a control. Record test names or actual observed steps and limitations in the implementation report. Native player behavior and physical phone screenshots remain distinct from pure state and rendering tests.
