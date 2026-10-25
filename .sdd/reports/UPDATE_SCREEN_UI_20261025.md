# Android update screen UI — 2026-10-25

## Problem and contract

The former activity used an unscrollable top-aligned Column with text buttons and no navigation toolbar or installed-version context. Its current-version copy incorrectly described Android instead of Lirix. At large font sizes, long permission/signature errors and controls had no guaranteed reachable overflow path.

The replacement is a native Material 3 settings screen: compact Updates back toolbar, installed Lirix version, concise update status, optional version/error explanation and one filled primary action. Downloading exposes real determinate progress and cancellation. A single vertical scroll surface keeps every description/action reachable at large font sizes; content is capped at 560 dp and the status and action form one vertically centred group when content is short. The themed 72 dp status circle contains a 48 dp status icon; a 24 sp headline, compact Lirix version line and 16 sp explanation form one hierarchy. The action is capped at 360 dp and follows the status block by 32 dp, rather than separating at opposite viewport edges. No stacked cards, decorative motion, custom branding or fixed dark palette were introduced.

`UpdateScreen` renders the actual activity state and callbacks without network/installer behavior. Material progress semantics report the supplied amount; status changes use a polite live region. Text and buttons wrap without maxLines truncation. Toolbar and content consume safe insets once; the activity enables edge-to-edge and selects system-bar contrast from the stored theme's isDark flag. It reads the same ThemePreferencesRepository.selectedTheme flow as MainActivity and LocalizedAppContent for RU/EN. The Android LirixTheme supplies existing mobile typography and the same palettes.

The download, cancellation, digest/package/version/certificate verification, unknown-sources round trip, installer intent and throttled foreground check remain in UpdateActivity/GitHubUpdates. GitHubUpdates, manifest export policy, dependencies, app version and signing material are untouched. A signature mismatch presents check-again instead of inviting another install of the same incompatible file. Back replaces the redundant large Close action.

## Exact scoped files (mirrored in main tree and canonical checkout)

- app/src/main/java/com/lirix/app/update/UpdateActivity.kt
- app/src/main/java/com/lirix/app/update/UpdateScreen.kt (new)
- app/src/main/res/values/updates.xml
- app/src/main/res/values-ru/updates.xml
- app/src/androidTest/java/com/lirix/app/update/UpdateScreenUiTest.kt (new)
- .sdd/reports/UPDATE_SCREEN_UI_20261025.md (this report, main tree only)

No general strings.xml, engine source or unrelated dirty files were changed.

## Verification and limits

`com.lirix.app.update.UpdateScreenUiTest#updateStatesKeepActionsReachableAndLabelsUnclippedAtLargeFont` renders the production renderer at 320 dp / fontScale 2. It checks actual text layouts, reachable >=48 dp primary controls, installed version, back, checking-disabled state, available/download callback, 42% progress semantics, cancellation, error/retry callback and signature-mismatch actions. It does not mock successful network/download/installation or weaken business-policy tests. Run under real RU and EN resources.

This executor did not run Gradle or claim runtime acceptance. Coordinator gates: assembleDebug + assembleDebugAndroidTest + lintRelease; focused native test in RU/EN; real current-version screenshot; narrow/landscape and light/dark screenshots; actual update-engine regression suite. Error/signature/detail/action text must remain fully reachable at 200% font. Native installation/permission end-to-end is unchanged but is not proved by renderer tests.

Actual screen launch from an in-app/target-process context: `context.startActivity(Intent(context, UpdateActivity::class.java))` (component `com.lirix.app/.update.UpdateActivity`). This activity is intentionally not exported. On a rooted emulator, privileged `adb shell am start -n com.lirix.app/.update.UpdateActivity` can open it; ordinary external shell must use the existing in-app updates menu or a target-context instrumentation launch instead. No test-only update state extras were added to production intents.

## Visual QA correction

The first real screenshot (`build/updater-ui-polish/after-en.png`) showed a redundant three-part version heading/divider and a primary action isolated across a nearly empty viewport. This was a visual failure despite the structural accessibility improvements. The renderer now centres a single connected status/version/explanation/action group, removes the divider and duplicate version headings, and uses the themed status icon and compact toolbar named above. All previous actual overflow, >=48 dp, reachability and callback assertions remain; only the installed-version label contract changes to `Lirix %1$s`. The coordinator must capture the revised real screen and rerun native tests before acceptance.

## Local action contrast correction

The real Pastel Serenity screenshot sampled primary #D9777F with a white button label: contrast 3.045:1, below 4.5:1 for its ordinary-size text. The updater's enabled primary button now keeps the theme onPrimary only when it meets 4.5:1 against the actual primary; otherwise it chooses the higher-contrast black or white ink (black is approximately 6.90:1 for this sampled accent). The correction is local to UpdateScreen: palettes, other screens and Material disabled-button colors are unchanged. This is a source-level correction pending the coordinator's final screenshot/runtime verification.

## Coordinator acceptance evidence
- Final composition reviewed on actual API35 emulator in persisted Pastel Serenity and Original Lirix themes, RU, portrait and landscape. Native Back returned to the real main screen. Installed version/status/primary action are visible and system bars follow theme. Screenshots and XML are in ignored build/updater-ui-polish/after-light-ru.*, after-dark-ru.*, landscape-top.*.
- Final large-font renderer test PASS under actual RU and EN app resources: updater-ui-ru-final.log (7.077s) and updater-ui-en-final.log. It checks current/checking/available/downloading/error/signature states, real 42% progress semantics, callbacks, no text overflow and reachable 48dp actions at 320dp/fontScale2. English transition independently verified in the rendered main screen.
- GitHubUpdatesTest: 7 tests, zero failures/errors/skips. Debug app/test build and release lint PASS in updater-ui-final-build.log (4m11s). These checks precede the final contrast-only button change; final app/test build PASS again in updater-ui-contrast-build.log (1m32s), followed by the final RU/EN device tests above.
- Final screenshot pixels confirm Pastel Serenity button uses black text on #D9777F, approx6.90:1 contrast; the earlier white text was3.045:1. This correction is local to updater and does not alter global palettes.
- Preview APK SHA-256: c28efd3ab267e6e7605d89c1445b93a4ee959ea1e6d3886ef0d28c5540eb4142. Local debug signature, app version unchanged1.5.1. Public stable release unchanged. Actual installer/unknown-sources end-to-end was not repeated; business code is unchanged and its regression suite passes. This scoped UI correction does not complete the overarching player/studio goal.
