# Karaoke studio acceptance review — 2026-10-21

Read-only independent review of the new line studio, shared reducers/tests, Android waveform/playback/draft components, two NowPlaying integration changes and backup rules/tests. No Gradle, device interaction or product/test edits performed. Test counts were supplied by the coordinator, not independently rerun.

## Result

One confirmed P2 must be corrected before accepting offline/local-wave editing. No confirmed P0/P1 data-loss issue found in the reviewed paths. Physical MediaCodec, source seek/loop, process restoration and small-screen acceptance remain unverified.

## P2 — replacing a local file retains the old file's editing bound

Location: app/src/main/java/com/lirix/app/ui/components/KaraokeStudioDialog.kt:188; warning condition at :284.

Reproduction:
1. No matching active source with known duration. Open a studio whose initial duration is unknown.
2. Select a 12-second local file. Decoding assigns state.durationMs = 12000.
3. Replace it with a 20-second file. The new waveform appears and says 20 seconds, but the conditional only assigns duration when the existing duration is zero.
4. Try a line time of 00:13.000: the reducer rejects it; timeline panning also remains bounded at 12 seconds. No mismatch warning appears without playback duration.
5. Removing the wave does not clear its duration; the obsolete bound is persisted in the draft and survives reopening.

Consequence: the explicit “Другой аудиофайл” recovery path cannot correct an accidentally selected short file; it leaves contradictory displayed and editable durations. Selecting a shorter replacement likewise permits marks outside the current file.

## One bounded correction contract: studio duration ownership

Keep line timestamps, undo/redo content and original lyrics untouched. Derive the current editing bound from identified evidence, in this order:

1. Known positive duration of the currently matching source/controller.
2. Positive duration of the currently selected file after its current decode succeeds.
3. Original known song/source duration retained separately from file-derived duration.
4. Zero (unknown/unbounded), if no valid evidence remains.

The persisted effective duration alone must not be mistaken for an original/source duration. Persist enough provenance to restore the original known duration, or conservatively regard ambiguous old draft values as unknown until current source/file evidence is available. Restore old draft content without deleting or truncating marks.

Required behavior:
- Replace/retry: invalidate old decoded-file duration immediately with the old waveform; never use that file as fallback while the new URI is decoding or has failed. Recompute from the above priority when the new result arrives.
- Remove: discard only the file association/wave/evidence. Fall back to matching source or original known source duration, otherwise zero.
- Reopen: restore the file association and original known duration. Revalidate the file through decode; do not temporarily apply its old effective duration as an original song limit.
- Source change/disappearance: another track's duration must never participate. Recompute from the remaining current-file/original evidence.
- A shorter new effective bound must preserve all existing marks and history. Existing validation must visibly block Apply until out-of-bound marks are corrected; no automatic clipping.
- A genuine matching source remains authoritative even when a chosen file differs; retain the existing mismatch warning.

Regression scenarios for the same limited fix:
1. Unknown source, 12s file → 20s replacement: 13s mark accepted.
2. Unknown source, 20s file → 12s replacement: existing 18s mark retained, validation fails, no silent truncation.
3. Unknown source + file → remove → close/reopen: old file duration is not retained as a song limit.
4. Original known duration 30s, no current matching controller: file12s bounds at12s; removing it restores30s.
5. Matching source30s + file12s: effective bound30s and mismatch warning; replace/remove do not override30s.
6. Decode cancellation/failure and a late superseded result cannot reintroduce old-file duration.
7. New and backward-compatible draft reopen preserve line/original content and resolve duration provenance conservatively.

## Non-blocking observation

KaraokeStudioTimeline.kt:56–60 chooses the first nearest occurrence when multiple markers have equal time. Even if a later equal-time line is selected from the list, dragging the overlapping marker selects/moves the first occurrence. Numeric time and ±50ms still permit editing the desired occurrence, so this does not block the current release; prefer the selected occurrence as the tie-breaker in a later polish.

## Checks supported by code inspection

- Quick and precise modes share one captured document; exact edits do not add reaction compensation again.
- Ordered occurrence IDs and equal timestamps are retained; chronology/range validation gates export.
- Draft writes use AtomicFile and preserve corrupt payloads before explicit restart; Room Apply checks the saved baseline and preserves auxiliary fields.
- Real waveform values derive from PCM with channel/timestamp aggregation, bounded decode and cancellation.
- Source commands revalidate metadata and capabilities; loop state prevents repeated outstanding seeks.
- The added backup path is narrowly scoped to the private studio draft directory. External audio is not included.

## Follow-up review of P2 correction — 2026-10-21

Verdict: **P2 resolved by source inspection. No residual blocker found within the duration correction.** This is a review verdict, not a claim that the active build or physical phone acceptance has passed.

Reviewed new StudioDurationEvidence (KaraokeStudioDuration.kt), modified dialog/draft integration, and all seven KaraokeStudioDurationTest cases. No Gradle/device invocation or product/test edits performed during this follow-up.

Evidence against the correction contract:

| Scenario | Implementation evidence | Review |
|---|---|---|
| 12s → 20s permits a 13s mark | Picker invalidates file evidence immediately (dialog:118); current decode replaces file duration (dialog:207–212); helper effectiveMs resolves current file | Satisfied |
| 20s → 12s preserves existing 18s mark and invalidates export | updateDuration copies only durationMs (dialog:92–94); reducer validation still rejects out-of-bound content; matching regression checks lines, original and undo/redo | Satisfied |
| Remove/reopen does not retain old file duration | Remove calls invalidateAudio before clearing URI (dialog:313); draft decoder rebuilds duration from independent originalDurationMs (DraftStore:129–141) | Satisfied |
| Original 30s returns after file12s is removed | Helper priority is matching source → decoded current file → original; invalidateFile clears only file evidence | Satisfied |
| Matching source30s wins over file12s, including replace/remove | source evidence is revalidated on invalidate and successful decode; playback poll refreshes it (dialog:100–101,211–212,225); warning requires matching source (dialog:308) | Satisfied |
| Cancellation/failure/late result cannot restore old evidence | Generation increment on invalidation; decode is keyed by URI/generation; ensureActive plus URI/generation checks gate result; helper rejects superseded generation; failed attempt supplies no duration | Satisfied |
| New/old draft reopen preserves content and provenance | New drafts persist independent originalDurationMs; missing field defaults to zero; effective persisted duration is not trusted; restoreDraft refreshes matching source before fresh decode (dialog:103–111) | Satisfied |

The seven regression tests target the requested scenarios, including ambiguity in old drafts. They are meaningful source-level coverage of the helper, reducer and draft store. Composition cancellation/picker wiring and actual MediaCodec still require device acceptance; those integration behaviors are supported here only by inspection.

The earlier equal-time marker selection observation is unchanged and remains non-blocking.
