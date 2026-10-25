# Independent review: Android Settings — 2026-10-25

Scope: frozen canonical `.gradle/repairs-integration-ecce372`, compared against main repository `ef538e8`. No product source modified; no builds, ADB operations, or additional agents run by reviewer.

## Verdict

**PASS for the scoped menu/settings implementation and the reported 2 + 10 native test results. Full acceptance remains conditional on the explicitly pending runtime/layout checks and delivery.** No confirmed source blocker found. One concrete inset risk requires targeted runtime verification before claiming safe layouts in every navigation mode.

## Checked implementation

- `app/src/main/AndroidManifest.xml:53`: SettingsActivity non-exported; only locale/layoutDirection handled without recreation, consistent with MainActivity. Other configuration changes follow normal Activity lifecycle.
- `app/src/main/java/com/lirix/app/ui/settings/SettingsActivity.kt:19-36`: ComponentActivity host collects persisted theme, applies localized context and theme, and delegates Back to Activity dispatcher. It creates no media engine/session. Opening normally retains the existing underlying Activity; this is not a guarantee against OS process death and does not prove live external-player retention.
- `app/src/main/java/com/lirix/app/ui/settings/SettingsScreen.kt:44-68`: original persisted theme/language stores and dialogs reused. Dialog flags, blacklist route and list scroll are saveable. Blacklist Back is intercepted only while that route is open; toolbar uses same route dismissal. Returning from Updates keeps the Settings instance below it.
- `SettingsScreen.kt:56-61`: existing BlacklistViewModel factory uses host Activity ViewModelStore, so repeated route visits do not create new independent long-lived jobs; no destructive blacklist action is invoked simply by navigation.
- `SettingsScreen.kt:76-100,114-132`: list uses safe insets, bounded 600dp width, vertical scrolling, wrapping labels and >=56dp settings rows. Both new resource files have equal five-key sets and positional version placeholder.
- `app/src/main/java/com/lirix/app/ui/components/NowPlayingOverflowMenu.kt:39-56,119-132`: empty/track/mode policies preserved; Settings is the single replacement for four global actions, the same trigger anchors the popup, callback dismisses before action. Common/desktop API untouched.
- Canonical NowPlayingScreen diff removes only old theme/language state, subscription, dialogs and global menu callbacks (plus whitespace); retained legacy blacklist parameter avoids unrelated caller changes. Main foreign-dirty tree was not treated as canonical.

## Evidence strength and tests

Read actual logs: `settings-screen-build.log` ends BUILD SUCCESSFUL in 3m34s, including lintRelease; `settings-screen-native-fixed.log` ends OK (2 tests), 18.638s; `settings-player-regression.log` ends OK (10 tests), 41.326s. Instrumentation code -1 is normal successful instrumentation completion here; the explicit JUnit summaries are the evidence, not shell exit status.

`SettingsScreenUiTest.kt:59-95`: real production overflow, RU/EN changes, 200% density, empty/track/landscape policy variants, one Settings/no four old globals, existing applicable edit/overlay entry, callback once. The landscape boolean is policy coverage, not actual landscape Activity geometry.

`SettingsScreenUiTest.kt:98-143`: real SettingsContent, constrained 320dp/200%, actual TextLayoutResult overflow checks, fully reachable row bounds, original dialogs, changed resulting localized title/subtitles and persisted store. Version substring assertion compares against actual PackageManager version and is appropriate for `Lirix <version>`; this is not a weakened requirement.

`SettingsScreenUiTest.kt:144-157`: monitored UpdateActivity intent deliberately blocks launch; proves routing intent only. Real blacklist route and Android Back are exercised; Settings Back tests callback only. The documented real empty-player stack check supplements this for RU portrait. It does not prove live media, rotation/recreation, all font/navigation modes, or cold restart.

Existing context/surface/landscape tests retain assertions; callback substitutions and replacing obsolete blacklist menu target with Settings match the changed contract. No test was removed, ignored, or replaced by merely asserting preference writes.

## Targeted unresolved risk

`SettingsScreen.kt:59-60` pads blacklist host with safeDrawing Top + Horizontal, excluding Bottom. Reused `app/src/main/java/com/lirix/app/ui/blacklist/BlacklistSettingsScreen.kt:182-219` has a custom bottomBar without navigation-bar padding; Scaffold contentWindowInsets does not automatically pad a custom bottomBar. The archived `build/android-settings-screen/settings-blacklist.xml` places its clickable create-rule target at [55,2098][1025,2241] in a 1080x2280 window. The exact safe navigation bound is not present in that XML, so overlap is a **risk, not a confirmed visual failure**. Check gesture and especially three-button navigation before declaring the route inset-safe; a host-level bottom inset fix can avoid changing reused blacklist internals if overlap is reproduced. Current test only asserts blacklist route existence and Back (`SettingsScreenUiTest.kt:150-153`), not CTA safe-area bounds.

## Remaining acceptance limits

Actual landscape/large-font Activity screenshots, light/dark settings/dialog contrast, live player/editor retention across settings/theme/language return, and private preview artifact delivery are not proven by this review. The file named `settings-landscape-large.xml` currently has portrait-shaped 1080x2280 content bounds and must not be cited as landscape proof without checking actual display/configuration evidence. Phone acceptance remains separate and unavailable; no destructive blacklist purge is requested or required by this navigation change.


## Runtime follow-up: bounded correction contract

Coordinator supplied confirming runtime evidence: `build/android-settings-screen/settings-blacklist-large-portrait-ru.png` (API35, three-button navigation, fontScale2) shows bottom CTA entering the system navigation area and a white status gutter beneath white system icons. `settings-blacklist-landscape-ru.png` shows the same white system gutters. The earlier inset risk is therefore **confirmed and blocks final acceptance of the new Settings blacklist host**. The source/menu review above remains valid; its conditional verdict is not permission to close the task with this regression.

### Minimal host correction

Only the blacklist branch of `SettingsContent`, `app/src/main/java/com/lirix/app/ui/settings/SettingsScreen.kt:55-62`, needs a product change:

1. Paint the full available Activity content, including system-bar gutters, with an opaque existing theme background (`LirixThemeTokens.colors.appBackground`, or the exact equivalent token already used by BlacklistSettingsScreen). Use a full-size outer Surface/Box whose background is applied **outside/before** inset padding. Do not introduce a palette, change app-wide theme tokens, or set window flags from the child route.
2. Inset the hosted BlacklistSettingsScreen on **all four safeDrawing sides**, including Bottom, with standard consuming `windowInsetsPadding(WindowInsets.safeDrawing)`. Insets must be consumed once at this host boundary, so the reused child's Scaffold sees zero already-consumed safeDrawing values. Do not add a second bottom padding to the child's bottomBar or use hard-coded status/navigation heights. A full-size painted outer container plus an inset child container is explicit and avoids accidentally painting only the already-padded content.
3. Retain existing SettingsActivity theme-dependent system-icon styles. With opaque theme background now painted behind the bars, dark theme has readable light icons and light theme has readable dark icons. No separate status-strip colour should disagree with theme changes while on this route.
4. Preserve exact Back handler, route/dialog saveable state, Activity-owned ViewModelProvider, database/factory, blacklist callbacks and Library entry. No Activity/session recreation, database mutation, rule changes or duplicated toolbar.

The custom blacklist bottomBar currently lacks its own navigation inset; applying/consuming it at the new host fixes the newly introduced route without modifying that reused screen or unrelated library navigation. IME/constructor behaviour remains existing behaviour; do not introduce extra IME padding or alter constructor contracts in this patch.

### Required proof for this correction

- Production SettingsContent -> real blacklist route on API35, portrait and landscape, three-button navigation, fontScale1 and2: the create-rule clickable bounds must lie entirely above/away from the actual safe navigation region. The top toolbar and system icon gutters must be readable and visibly themed.
- At least the reproducing dark theme and one light theme; switch theme, revisit blacklist, verify gutters update with existing icon appearance. Screenshots must be actual route rendering, not only placeholder callbacks.
- Keep existing 2 settings +10 player regressions; add targeted regression for real blacklist host geometry or record precise runtime inset/bounds evidence. Do not replace it with checking that `windowInsetsPadding` exists in source.
- Back from blacklist still returns to Settings and then player, and no rule count/data is changed by opening/closing. No destructive purge test is necessary.

### What stays outside this bounded patch

The squeezed total-rules badge is explained by the existing unweighted sibling Row/Surface in `BlacklistSettingsScreen.kt:324-361`. The clipped empty description is explained by a non-scrollable empty branch `:258-262` and fixed padding in `EmptyStateView :622-666`, following a fixed dashboard/filter header. Both are pre-existing reused-blacklist layout defects, rather than new settings-host behaviour.

They **do not require redesigning the entire blacklist screen to accept moving global settings**, provided the new host regression is corrected and the report/checklist explicitly discloses these known large-font blacklist limitations. They remain open under the broader original objective to eliminate clipping and must be recorded for a later sequential task; this Settings fix cannot be cited as completion of that whole objective. Do not lower the font scale or remove the explanatory text/badge merely to make screenshots pass. If users cannot reach the create-rule action after the host correction, that is part of the host acceptance and remains blocking; inner explanatory copy clipping alone is a separately scoped follow-up.

### Corrected orientation evidence

Coordinator identifies `settings-landscape-large-real.xml` (2280x1080), `settings-landscape-large-ru.png`, and `settings-landscape-large-bottom-ru.png` as actual landscape Settings evidence. Use those and the matching actual configuration; discard the old portrait-shaped `settings-landscape-large.xml` as landscape proof. This resolves that earlier evidence warning for the main settings list, not the separate blacklist gutter failure.


## Host fix re-review (before coordinator rebuild/runtime)

**PASS — source patch matches the bounded correction contract.** Main and canonical SettingsScreen.kt are byte-equivalent after normalized line endings. New `background`/LirixThemeTokens imports support the only product change in the blacklist branch:

- `app/src/main/java/com/lirix/app/ui/settings/SettingsScreen.kt:61`: full-size outer Box paints existing appBackground before any inset padding, including gutters. Existing palettes use opaque background tokens (for example ColorPalettes.kt:12,34,56,78), so no default white Activity surface should show through this host region.
- `SettingsScreen.kt:62-64`: nested full-size Box applies standard `windowInsetsPadding(WindowInsets.safeDrawing)` to every side. Compose consumes the applied insets for descendants; the reused BlacklistSettingsScreen Scaffold receives already-consumed safeDrawing values, preventing a second application. No hard-coded heights, child bottom-bar padding, or separate IME handling added.
- `SettingsScreen.kt:52-60,64,67-72`: existing saveable flags, scroll state, Back handler, Activity ViewModelStore ownership/factory, database and callbacks unchanged. Theme/language/update routes and settings list are unchanged. No blacklist rules/purge/library entry/business logic modified by this patch.

The earlier confirmed gutter/CTA issue is **addressed in source, pending coordinator runtime confirmation**; this review does not promote pre-fix screenshots or test logs into proof for the changed APK. Rebuild plus the reproducing dark/light, three-button portrait/landscape matrix and existing 2+10 regression tests remain the verification gate. The coder handoff `.sdd/reports/ANDROID_SETTINGS_BLACKLIST_HOST_FIX_20261025.md` correctly distinguishes those gates from completed source work. No new issue requiring expansion of this bounded patch was found.


## Locale-sensitive test-selector re-review

**FAIL for the complete test-selector patch as currently frozen; overflow part PASS.** Main/canonical PlayerContextUiTest mirrors match. No runtime/build operations performed by this reviewer.

`app/src/androidTest/java/com/lirix/app/ui/PlayerContextUiTest.kt:29-32` introduces exact Activity resource expectations. Fixtures use raw Activity context and LirixTheme does not replace it. Therefore conversions for production PlayerContextOverflow stringResource labels are correct and preserve semantic matcher kinds, callback counts, positive/negative context assertions, popup bounds and RTL scroll tests. No preferences/locale are forced, no test ignored, and no geometric tolerance is relaxed.

**Blocking selector mismatch:** `PlayerContextUiTest.kt:123-128` now expects transport descriptions from localized R.string values, but canonical `app/src/main/java/com/lirix/app/ui/components/PlayerConnectButton.kt:22,43,50,55,59` still supplies hard-coded Russian descriptions for Connect, Previous, Play/Pause, Next and Share. Under the EN Activity configuration used by the reproduced run, for example the test expects `Connect device (PC)` while production renders `Подключить устройство (ПК)`. Thus the test-only conversion is not a faithful expectation for these unchanged transport fixtures and can cause a new EN failure after repairing Menu lookups.

Bounded remedy without expanding product scope: retain/restore exact current production Russian transport expectations for this geometry/callback-only test and clearly state that its descriptions are not localization coverage; apply localized resource selectors only where production actually uses resources. Do not weaken into generic tags/OR matchers or force RU preferences to conceal the mismatch. Product transport localization is a separate existing issue unless coordinator explicitly brings the five-description resource conversion into this patch, in which case the product change requires its own reviewed scope and tests. No edit performed by reviewer.

Earlier 4 Menu selector failures remain diagnostic evidence. Final 12-test acceptance requires the corrected instrumentation APK and actual post-fix logs, not a successful instrumentation rebuild alone.


## Final selector and runtime-evidence re-review

**PASS for the corrected final PlayerContextUiTest selectors and new-host navigation clearance.** This supersedes the selector-patch FAIL above: `PlayerContextUiTest.kt:123-130` now restores the exact original fixed-Russian transport descriptions with an explicit geometry/callback-only comment. Resource-aware expectations remain only for production-localized overflow labels. Count=1, enabled/disabled checks, center tolerance1f, callback counts, exact selectors and original assertions remain intact; no locale/preference mutation, OR matcher or product transport change was introduced. Main/canonical mirrors are byte-identical. This accepts the bounded Settings change while keeping existing transport localization deficiency open.

Independently checked both `build/android-settings-screen/blacklist-light-verified-matrix.json` and `blacklist-dark-ru-verified-matrix.json` against their eight actual XML, PNG header dimensions and window dumps. Every matrix row matches the XML root dimensions and actual clickable CTA node, and its navigation frame is present in the corresponding window dump. All CTA rectangles are disjoint from actual navigation regions: portrait bottom2109 < nav-top2148; landscape right2093 < nav-left2148. Font2 RU portrait target grows upward to1890 without entering navigation. Matrix configurations represent font1/2, portrait/landscape, lightEN/darkRU.

Visually inspected the darkRU/font2/portrait and lightEN/font2/landscape PNGs directly: gutters are correctly themed and system icons readable, CTA outside system navigation. Coordinator inspected all eight images. Known preexisting badge/empty-copy limitations are visible and remain disclosed, not relabelled as fixed. No unsupported claim about all typography/layout issues is made.

`settings-final-back.xml` contains actual Settings rows; `settings-final-back-player.xml` contains the original empty Lirix player. Together with the coordinator's real navigation sequence these support Settings -> blacklist -> Settings -> player Back behavior. Final rebuilt 12-test RU/EN runs remain pending at time of this re-review; successful source/instrumentation compilation is not substituted for those logs. Private delivery and live external-player retention are still separate gates/limits.


## Staged-source gate

Read-only Git index review: exactly the11 approved Android Settings source/test files staged,403 insertions/80 deletions. Independently compared every staged blob with reviewed canonical files: all equal after LF normalization and the documented NowPlaying trailing-space cleanup. NowPlaying cached diff is32 lines of obsolete global settings imports/state/callbacks/dialog removal only. No foreign clock/source, desktop, common, blacklist rule logic, signing/dependency/version changes staged. Index was not changed by reviewer.

Final RU instrumentation log `.gradle/karaoke-private-verification/settings-final-native-ru.log` inspected: **OK (12 tests),57.412s**. EN run was still at test12 start when index gate was inspected; no EN success claim yet.


## Final independent gate

**PASS — scoped Android Settings source, new-host runtime correction, and final native regression gates.** EN terminal log `.gradle/karaoke-private-verification/settings-final-native-en.log` independently inspected: **OK (12 tests),58.556s**; RU is **12/12,57.412s** as above. Thus the actual post-fix APK/test selectors, not just pre-fix runs, satisfy the12-test gate in both actual language states.

Approved for the coordinator's isolated scoped commit and private preview delivery. Physical phone/live external-player state retention remains a disclosed checklist item; existing blacklist large-font badge/empty-copy and legacy fixed-RU transport semantics remain separate open defects. Private delivery is not performed or claimed by reviewer. This verdict completes only the reviewed Settings gate and **does not mark the broad player/studio objective complete**.
