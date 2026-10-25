# Android global settings — 2026-10-25

## Audit and contract

Android had no general settings screen. It already had shared ThemeSelectionDialog, Android LanguageSelectionDialog, and BlacklistSettingsScreen reached through an AppScaffold overlay. The player overflow mixed four application-wide actions (theme, interface language, updates, blacklist) with track actions.

The Android overflow now contains one Settings action in every mode, including no active track and landscape. Track editing, sync, translations, notes, calibration, Teach, reading/romanization and overlay retain their existing context policy. Common/desktop source is untouched.

SettingsActivity is a non-exported, lightweight native host over the existing player activity. It creates no music engine or media session. Back finishes only this settings host, returning to the underlying player state; locale/layoutDirection retention matches the existing activities. LocalizedAppContent and ThemePreferencesRepository.selectedTheme supply the persisted language/theme; Android LirixTheme and edge-to-edge/system-bar contrast match the repaired updater.

The screen has a Back toolbar and two simple sections: Appearance (Theme and Language with current values), Application (Lirix updates with installed version and Library blacklist). Rows are >=56 dp, wrap fully, use existing typography and readable foreground tokens, and scroll inside once-consumed safe insets. Content width is capped at 600 dp. No nested cards, palette changes or decorative animations were added.

## Existing behavior reused

Theme and language rows open the original dialogs and use the original persistent stores; selection is reflected in the actual row subtitle/resources. SettingsContent owns their presentation flags rather than leaving duplicate dialogs/state in NowPlayingScreen.

Updates opens the unchanged UpdateActivity; its verification, certificate, network, installation and permission policy are untouched. Blacklist opens the original BlacklistSettingsScreen on an internal route. Both its toolbar Back and Android Back return to the settings list, not directly to the player; list scroll is retained. Its existing factory is hosted in the activity ViewModelStore so repeated route visits reuse one existing BlacklistViewModel and its jobs are cleared with the host. Existing blacklist apply/purge logic and the separate library entry remain unchanged. The former NowPlayingScreen blacklist callback parameter is retained for source compatibility with AppScaffold, but is no longer a player-menu action.

## Exact files

New, identical in main and canonical checkout:
- app/src/main/java/com/lirix/app/ui/settings/SettingsActivity.kt
- app/src/main/java/com/lirix/app/ui/settings/SettingsScreen.kt
- app/src/main/res/values/settings.xml
- app/src/main/res/values-ru/settings.xml
- app/src/androidTest/java/com/lirix/app/ui/SettingsScreenUiTest.kt

Modified with scoped equivalent hunks in both trees:
- app/src/main/AndroidManifest.xml (one non-exported activity declaration)
- app/src/main/java/com/lirix/app/ui/components/NowPlayingOverflowMenu.kt (Android-only API/action)
- app/src/main/java/com/lirix/app/ui/NowPlayingScreen.kt (obsolete global menu callbacks/dialog state/imports only; main clock/source edits preserved)
- app/src/androidTest/java/com/lirix/app/ui/PlayerContextUiTest.kt
- app/src/androidTest/java/com/lirix/app/ui/PlayerSurfaceUiTest.kt
- app/src/androidTest/java/com/lirix/app/ui/LandscapePlayerPanelUiTest.kt

Report, main only: .sdd/reports/ANDROID_SETTINGS_SCREEN_20261025.md

General strings.xml, shared/desktop UI, updater source, dependencies, version and signing material were not changed. No whole-file overwrite from canonical into the dirty main NowPlayingScreen/manifest was performed.

## Focused native acceptance and outstanding gates

Class `com.lirix.app.ui.SettingsScreenUiTest`:
1. `globalActionsCollapseToOneSettingsForEmptyPortraitAndLandscapeInBothLanguages`: renders production overflow at 200% font under actual LanguageManager RU/EN choices; empty/track/landscape policy variants show exactly one Settings, none of the four global actions, and preserve applicable track actions/overlay. Settings click fires once per variant.
2. `realSettingsDialogsUpdateLabelsAndRoutesReturnAtTwoHundredPercentInBothLanguages`: renders production SettingsContent at 320 dp and 200% font, uses actual text layout no-overflow assertions, verifies >=48 dp fully reachable rows including the last item, and installed-version metadata. It opens existing theme/language dialogs, changes persisted values and checks the resulting subtitle and localized settings title. It dispatches the real UpdateActivity intent under an Instrumentation ActivityMonitor that blocks launching network work, and opens the actual blacklist screen before Android Back returns to Settings. Settings toolbar Back reaches its host callback. Original theme/language values are restored afterward.

Existing PlayerContext/PlayerSurface/Landscape tests migrate to the new Android onSettings callback and dismissal expectations; track-context assertions remain. The old RTL large-font menu test now reaches Settings rather than a removed direct blacklist action. This is a changed user contract, not removal of reachability coverage.

This executor did not invoke Gradle, operate the system emulator, commit or claim runtime success. Pending coordinator gates: build + AndroidTest APK + release lint; focused new tests and context/landscape regression tests; real empty Main → Settings → Theme/Language/Updates/Blacklist → Back in RU/EN, portrait/landscape and 200% font; visual contrast/insets review. The monitored updater intent is not proof of a live updater visit/return, and component back callbacks do not alone prove player-state retention. Verify that on the real activity stack. Blacklist destructive application logic is reused and is not re-executed by these UI navigation tests.

Real entry: existing player overflow → Settings, or from a target process `SettingsActivity.open(context)` / component com.lirix.app/.ui.settings.SettingsActivity (external ordinary shell is intentionally denied because exported=false).

## Coordinator verification

- Canonical debug APK, instrumentation APK and release lint: PASS, 3m34s. Log: `.gradle/karaoke-private-verification/settings-screen-build.log`.
- Initial native run: 1/2 passed. The version assertion used exact-node matching for the substring `1.5.1`, while the real subtitle was `Lirix 1.5.1`. Corrected only that test to explicit `substring = true`; the displayed installed-version requirement is preserved. Original failure retained in `settings-screen-native.log`.
- Instrumentation-only rebuild: PASS, 35s. New settings tests: PASS, `OK (2 tests)`, 18.638s, including both languages and 200% font. Log: `.gradle/karaoke-private-verification/settings-screen-native-fixed.log`.
- Player context/surface/landscape regressions: PASS, `OK (10 tests)`, 41.326s. Log: `.gradle/karaoke-private-verification/settings-player-regression.log`.
- Actual empty-player activity-stack check, RU portrait: PASS. Menu contains Overlay + Settings, real Settings → UpdateActivity → Android Back returns to Settings; real Blacklist → Back returns to Settings; another Back returns to the original empty player. Blacklist rules were not changed. UIAutomator XML evidence and visually inspected `settings-home-ru.png`: `build/android-settings-screen/`.
- Actual live external-media state retention, actual activity landscape/large-font visual matrix, private preview delivery and phone acceptance remain pending. The new component tests cover both languages/200% font but do not substitute for all actual-activity checks. No release delivery claimed.

## Final host correction and actual runtime matrix

The review risk was reproduced: the old blacklist host placed the create-rule CTA under three-button navigation and exposed white gutters with white status icons. The bounded host correction paints the existing theme background outside all-side safeDrawing padding. Reused blacklist rules/storage/VM logic remains unchanged. Independent review and coder handoff: ANDROID_SETTINGS_INDEPENDENT_REVIEW_20261025.md and ANDROID_SETTINGS_BLACKLIST_HOST_FIX_20261025.md.

Final product APK + instrumentation APK + release lint: PASS, 3m11s, `settings-host-fix-build.log`. APK signer SHA256 remains `971ecda293eb39469b2c9419f9e04b5db2515276de433869914eb73179774192`; APK size 43,911,160 bytes.

Actual API35 production SettingsActivity -> blacklist matrix: eight states (Pastel Serenity/EN and Original Lirix/RU, fontScale1/2, portrait/landscape), three-button navigation. All screenshots were inspected. Each recorded Activity Configuration, XML root and PNG dimensions agree after waiting for the actual configuration change. Evidence: `build/android-settings-screen/blacklist-light-verified-matrix.json`, `blacklist-dark-ru-verified-matrix.json`, their `blacklist-verified-*.png/.xml` and WindowManager dumps.

Clickable CTA is entirely outside real navigation frames in every state: portrait right/bottom `[1025,2109]`, nav begins at y2148; landscape CTA right2093, nav begins at x2148. At RU font2 portrait its full clickable bounds are `[55,1890][1025,2109]`. Themed system gutters are readable in both themes. Back still returns blacklist -> Settings -> original empty player (`settings-final-back.xml`, `settings-final-back-player.xml`), without creating/deleting rules.

Early `blacklist-fixed-*` captures raced orientation changes; they are diagnostic only, not acceptance proof. Actual main Settings landscape proof uses `settings-landscape-large-real.xml` and matching top/bottom screenshots. Real RU->EN theme/language change and process restart preserved English/Pastel (`settings-home-en-light.png`, `settings-persistence.xml`).

The first combined post-host test run correctly exposed four old hard-coded `Меню` selectors under persisted English (`settings-host-final-native.log`); Settings2 and six other tests passed. The scoped test correction now derives overflow expectations from current resource strings. Existing legacy transport RU semantics and geometry/callback assertions are retained, explicitly not claimed as localization coverage. Final selector build/runs are pending at this checkpoint.

Known pre-existing limitations remain open under the full UI objective: blacklist RU font2 squeezes the total-rules badge and clips/hides empty-state explanatory content at short heights; legacy transport accessibility descriptions are not fully localized. Physical/live-media retention and broader studio acceptance remain unproven. This settings delivery must not be cited as completion of the full player/studio goal.

## Final automated gate

- Final instrumentation-only rebuild: PASS, 35s (`settings-selectors-final-build.log`). The product APK is unchanged from the host-fix build.
- Final same 12 native checks with persisted Russian: `OK (12 tests)`, 57.412s (`settings-final-native-ru.log`).
- Final same 12 native checks after selecting English in the real settings UI: `OK (12 tests)`, 58.556s (`settings-final-native-en.log`). The original combined four-failure log is retained, not overwritten.
- Independent final scoped review and isolated staged-source comparison: PASS. No foreign clock/source/desktop edits were included. Final source/test manifest is in the ignored delivery directory.
- APK SHA256: `135455cbd9386baa1eb3d13a01090bba61135f6e20377252afef9edb18f2d4db`. Package `com.lirix.app`, versionName1.5.1/versionCode19; debug certificate above. Private-preview publication is the remaining delivery step; the public stable release/version is unchanged.

## Private delivery completed

- Source commit `773434b` pushed to `codex/release-1.5.1`, including the reviewed11 source/test files, scoped process notes and five explicitly requested backlog items. Foreign dirty source/clock/desktop/other process edits were preserved outside the commit.
- Private repository verified `private=true`: https://github.com/Asm-o-Dan/lirix-testing/releases/tag/android-preview-2026-10-25-settings . Published as prerelease; public stable1.5.1 was not modified.
- Seven release assets verified against local byte sizes and GitHub server SHA256 digests: APK, SHA256SUMS, CHECKLIST-RU, RU settings screenshot, EN light settings screenshot, actual corrected dark/RU large-font blacklist screenshot, source manifest. Receipt: `build/android-settings-screen/private-release.json`.
- Root private-repository CHECKLIST-RU.md also updated. Physical/live external-player acceptance remains requested through that checklist. No phone installation, uninstall, data wipe or destructive blacklist operation was performed. No next backlog item started as part of delivery.
