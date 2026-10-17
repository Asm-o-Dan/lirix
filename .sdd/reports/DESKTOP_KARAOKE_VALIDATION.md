# Desktop karaoke validation — 2026-10-17

Status: implementation, automated verification and independent source review PASS. User confirmed working desktop karaoke on 2026-10-17: «работает караоке». Agent visual capture remains unavailable; detailed native edge cases are not implied by this confirmation.

User clarification: Karaoke mode is missing from the desktop UI. The previous Text tab contained an implicit timed/plain branch. The corrected UI has explicit Text / Karaoke / Chords / About modes and defaults to Karaoke. Static text stays selectable; untimed content explains missing timing and links to the existing Tap-to-Sync dialog. No artificial timestamps are introduced.

## Implementation and review

- Architect: karaoke_architect; specification `desktop_karaoke_fix.md`, cards KARAOKE-001–013.
- Product executor: karaoke_coder. Independent QA: karaoke_qa. Independent source reviewer: karaoke_reviewer, PASS in `DESKTOP_KARAOKE_REVIEW.md`.
- Lifecycle-bound playback sampling at 50 ms, first-line boundary, offset-aware seek, shared observable offset Deck/HUD, captured Compose coroutine scope for hotkeys.
- HUD native dragging remains unchanged. HUD effective lyrics time can be negative before the first timestamp; it no longer incorrectly activates timestamp-zero lyrics early with a positive offset.
- The isolated Preferences-backed offset store preserves the public singleton, key and default. Regression tests use in-memory Preferences and never mutate user settings.

## Automated evidence

1. QA first confirmed missing production APIs with an expected compile failure; this was an absent-contract check, not behavioral RED against implemented helpers.
2. Isolated focused run: 11 tests PASS, including clock cancellation and both offset tests.
3. Final root command: `:desktop:test :shared:desktopTest :desktop:createDistributable --offline --no-daemon --no-configuration-cache --max-workers=2` — BUILD SUCCESSFUL in 31s.
4. Desktop XML: 51 tests, zero failures/errors/skips. Shared desktop XML: 173 tests, zero failures/errors/skips; task UP-TO-DATE from the preceding successful shared run because shared code did not change.
5. `git diff --check` passed.

## Artifact

`desktop/build/compose/binaries/main/app/Lirix/Lirix.exe`

Created by the standard Compose desktop packager; not a published release or installer. SHA256 comparison verified packaged project jars match the current Gradle outputs:
- `desktop-2a79319363987468c331b3619db9a.jar`
- `shared-desktop-5114d2ca34cfbf601a45fe45924b7a4a.jar`

The original packaging directory was not used by running processes before packaging. Installed Lirix and a previous separate HUD validation image remained running; neither was terminated or overwritten. The new image has not been launched. An already running copy retains the old modes until the user exits it normally and opens the new image.

## Native acceptance / follow-ups

User acceptance: «работает караоке» after delivery of the new build. Core karaoke smoke PASS by user observation. This does not independently verify all seek/offset, pause/resume, theme and hotkey cases below.

Capture of the current HUD-validation main window again failed with `FrameArrived timed out: timed out waiting on channel`. No screenshot-based input followed. Automated helpers and source review do not establish visible tab layout, native scrolling/highlighting or real hotkey execution. Do not mark those PASS without observation.

Saved non-USER offline LRC hydration and source ranking remain separately documented follow-ups. Broader desktop feature parity and archive merge acceptance remain unfinished; no release tag or GitHub release was changed.
