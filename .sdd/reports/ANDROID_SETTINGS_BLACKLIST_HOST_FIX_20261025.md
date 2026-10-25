# Settings blacklist host correction — 2026-10-25

## Bounded implementation

Changed only the blacklist branch of `SettingsContent` and its two imports. A full-size outer Box paints the existing opaque `LirixThemeTokens.colors.appBackground`, including the system-bar gutters. The nested full-size Box applies and consumes all four `WindowInsets.safeDrawing` sides before hosting the unchanged blacklist screen. No hard-coded system-bar heights, extra IME padding or child bottom-bar padding were introduced.

The main and canonical SettingsScreen.kt files are identical after the fix. Existing ViewModel ownership, database, routes, Back callbacks, saveable state, dialogs and settings list remain unchanged. No reused blacklist, library, theme-token or window-icon source was edited.

## Verification handoff

No Gradle or ADB execution was performed by this coder. Compilation and native regression execution remain coordinator gates.

The existing SettingsScreenUiTest checks the real blacklist route and Back, but its SettingsContent is intentionally constrained to a 320 x 560dp Box and the test Activity is not the production edge-to-edge SettingsActivity. Adding assertions against that Box or checking for the source modifier would not prove real system-navigation clearance. No synthetic Insets or test-only mechanism was added, and no existing test was weakened.

Required coordinator runtime proof: production SettingsActivity -> blacklist on API35 with three-button navigation, portrait/landscape and fontScale1/2; compare the actual create-rule clickable bounds to real navigation insets; inspect full-window screenshots in the reproducing dark theme and a light theme, including theme switch/revisit. Confirm Back to Settings then player without rule changes. Retain the existing 2 Settings + 10 player regressions.

The known inner blacklist large-font badge/empty-description clipping remains unchanged and must be tracked separately under the broader UI objective. This host fix does not establish whole-goal completion.

## Follow-up: locale-aware overflow expectations; unchanged legacy transport

The post-fix native run exposed hard-coded Russian menu accessibility expectations while the production overflow rendered English. Overflow selectors now resolve the exact production resource keys through the current host Activity; those fixtures inherit that Activity context directly. No language preference is changed or forced. All original context/dismissal assertions, callback counts, popup screen-coordinate constraints and RTL/large-font reachability are retained. The absent legacy Connect assertion resolves its existing text resource (`player_empty_connect_pc`). No alternative-language OR matcher or weakened assertion is used.

Correction after independent re-review: the first test edit incorrectly inferred canonical transport localization from the foreign-dirty main PlayerConnectButton.kt. Canonical transport still has fixed Russian descriptions. Restored the original Russian selectors only in transportHasOneConnectAndCenteredPlayWithDisabledSkipSlots; all count, disabled-skip, centred-play geometry and callback assertions remain intact. A brief test comment states that this fixture verifies geometry/callbacks, not transport localization. Product transport code was not modified or imported from the main checkout.

The corrected test source is identical in main and canonical. Overflow locale correctness still requires the coordinator's persisted-RU and persisted-EN native runs; transport localization is not claimed by this patch. No Gradle, ADB or Git execution was performed by this coder.
