# Android player context actions review — 2026-10-21

Status: product-freeze read-only review complete; both identified P2 findings corrected. Runtime and physical UI evidence remain pending.

Compared the current product files against build/player-ui-baseline snapshots, which include concurrent work predating this task. Reviewed only the approved player-context UI scope. No Gradle/device invocation and no product/test edits.

## Initial findings (both corrected at product freeze; evidence below)

### P2 — Track information command has no mounted dialog

NowPlayingScreen.kt:452 retains infoTrack and :549 offers “О треке и источниках” by assigning it. The previous infoTrack?.let { TrackInfoDialog(...) } composition was removed alongside the standalone overflow and has no replacement.

Scenario: open any song → Menu → About track and sources. The menu closes but no dialog opens. Existing track/source information is no longer reachable. Restore the dialog mount and preserve its current language/source dependencies.

### P2 — Poster sharing is unreachable in landscape

NowPlayingScreen.kt:767–837 retains four landscape transport controls: Connect, Previous, Play/Pause, Next. Share was removed from the overflow, and the only remaining action setting showShareDialog = true is portrait PlayerTransportRow.

Scenario: play a song, rotate to landscape, attempt to share its poster. There is no action until rotating back. The approved spec requires poster sharing to remain reachable through transport Share. Add the missing landscape Share without introducing another Connect.

## Supported by current inspection

- All three actual player header branches call the same PlayerContextOverflow; trigger and DropdownMenu share PlayerOverflowAnchor's Box, fixing the prior deck-level anchor.
- Overflow expansion is keyed by mode, trackKey and orientation. Translation settings reset on the same parent context changes.
- Nullable menu callbacks enforce the specified lyric/karaoke/chords/notes/empty action matrix. Source rejection and Connect are removed from overflow.
- Empty player has a single primary Connect CTA; portrait has one five-slot Connect/Previous/Play/Next/Share transport. Landscape retains one Connect, with Share omission reported above.
- Android compact TranslationPanel removes the repeated title, red editing toolbar and white original/translation explanation from the reading surface.
- A single compact attribution action opens full author/source/title/URL/warnings and a real Open source action.
- Translation language, display mode and URL import live in the bounded settings sheet; state belongs to the panel rather than the sheet, so closing/reopening it retains selection/input.
- Search errors/retry/candidate selection, preview confirmation and offline save remain in the reading panel.
- New shared presentation options default to compact=false; desktop callers omit them. Default control/title/explanation behavior is preserved by inspection.

## Pending evidence

Instrumentation files and their independent review after freeze. Actual popup geometry in small portrait/landscape/RTL, large-font scrolling and callback execution require rendered tests/device evidence. Root owns all builds/test execution. Physical phone acceptance remains pending.

## Product-freeze follow-up

Verdict: **the two identified P2 regressions are corrected; no remaining confirmed product blocker found in the bounded UI change.** Runtime/instrumentation execution and physical acceptance are separate gates.

- TrackInfoDialog is mounted again at NowPlayingScreen.kt:1365–1368 with the same DAO/language/dismiss arguments as the baseline.
- Landscape adds a 48dp Share button at NowPlayingScreen.kt:846–848 inside its existing transport, keeping one Connect.
- Final product inspection covered all three header anchors, portrait/landscape/empty Connect placement, compact attribution/settings, actual source URL opening, contextual callbacks and default desktop presentation.

Instrumentation source review:
- PlayerContextUiTest.kt uses the actual production PlayerContextOverflow and PlayerTransportRow. It checks translation/save callback invocation; lyric/karaoke/chords/notes/empty reachability; mode-change dismissal; single Connect; centered Play despite disabled skip controls; and RTL 1.8x-font scrolling/callback access.
- TranslationCompactUiTest.kt uses the actual shared TranslationPanel with a fake DAO. It checks absence of repeated title/explanation/inline settings, full long-author attribution details, source action presence, original display selection, import/settings state across closing and reopening, and absence of provider reload/extra DAO subscription merely from opening the sheet.
- These tests were read, not run by this reviewer.

Remaining evidence limits (not confirmed product failures):
- PlayerContextUiTest currently has no popup-bounds assertions against viewport/trigger, so it does not yet prove the approved small portrait/landscape anchor geometry gate.
- Track/orientation-triggered popup dismissal and parent translation-sheet context reset are supported by implementation inspection, but are not exercised by the current instrumentation source.
- TranslationCompactUiTest checks source action presence without executing LocalUriHandler, and does not exercise language switching, full import-confirm-save or search retry/candidate flows.
- No current test composes the complete NowPlayingScreen to prove all layout integration paths. The corrected TrackInfo and landscape Share regressions were found and verified by baseline comparison.
- Device/UI screenshots, large-font actual layout, native popup placement and physical phone acceptance remain pending root verification.

No Gradle/device commands or product/test edits were performed during this review. Only this report was created/updated.

## Final seven-file freeze / clean-HEAD patch review

Reviewed the final PlayerContextUiTest changes and the seven source-path versions under build/player-ui-baseline/head-ui (excluding temporary base/after/root snapshots). Root is building clean HEAD 679c37b plus the isolated UI patch; this reviewer did not run Gradle.

Verdict: **no confirmed regression/blocker found in the isolated UI patch.**

- Geometry tests now request portrait and landscape on the Android test activity, read popup/trigger/viewport in screen coordinates across their separate Compose windows, and assert popup containment plus trigger adjacency/overlap.
- The same actual-context test now changes track identity and orientation state while the menu is open and asserts dismissal. This closes the earlier missing assertion for the production component's remember keys.
- These geometry tests use the executing device's actual viewport; “small screen” coverage still depends on running them on an appropriately small emulator/device. Their presence is not evidence of a successful runtime result.
- Compact translation, anchored menu and five-slot portrait transport implementations in the clean snapshot retain the reviewed behavior. Landscape Share is present.
- Clean HEAD has no TrackInfoDialog feature. The clean patch passes onTrackInfo=null, hiding an unsupported command; it does not introduce the earlier no-op regression. The main working tree's independently added TrackInfo mount remains intact.
- The clean shared TranslationPanel's noncompact source attribution/Open source remain unconditional, matching committed HEAD 679c37b. The main working tree's pre-existing importing-only conditions were correctly excluded rather than copied into the Android UI commit.
- Independent source comparison found no unexplained behavior change in the clean shared panel, overflow or transport beyond the scoped UI presentation.
- Instrumentation host is already provided: app/build.gradle.kts:19 sets AndroidJUnitRunner; existing androidTest dependencies include AndroidX test JUnit/Espresso and Compose ui-test-junit4, and :149 declares debugImplementation(libs.androidx.ui.test.manifest). The alias resolves to androidx.compose.ui:ui-test-manifest, supplying the ComponentActivity manifest used by createComposeRule and createAndroidComposeRule<ComponentActivity>. No added dependency or host replacement is needed by inspection.

Remaining execution gates: root's clean build/tests, instrumentation on an Android device (including suitable viewport sizes), and physical UI acceptance. The full NowPlayingScreen integration, source-launch callback execution and complete translation language/import/retry flows still have the inspection-only limits recorded above. The foreign untracked LyricsSourceHttp compilation failure is outside this patch and was not modified.
