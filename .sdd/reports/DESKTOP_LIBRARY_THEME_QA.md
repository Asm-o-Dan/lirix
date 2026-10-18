# Desktop library/theme QA — 2026-10-18

Approved scope: `desktop_library_theme_corrections.md` v1, DESK-UX-101–105. Baseline commit `f115df5`; no product edits by QA.

Eight pure tests added in `DesktopLibraryViewStateTest.kt` against the specified production `buildDesktopLibraryViewState` API. Coverage includes history-empty versus filtered-empty, null stale details, trimmed whitespace, literal case-insensitive matching across title/artist/album/plain text, exclusion of key/timed-only text from search, favorites/search/artist intersection, exact artist identity, valid versus malformed/blank LRC, honest four-filter labels, input-order stability, retained/fallback selection and refreshed selected entity data.

The notes-draft implementation will use the actual hoisted Workspace map and callbacks; no mirror helper was invented solely for a test. Its lifetime and DB-confirmed clearing require source review and native acceptance. Theme selection and native rendering remain manual gates; pure tests do not establish them.

Initial command: `:desktop:test --tests '*DesktopLibraryViewStateTest' --offline --no-daemon --no-configuration-cache --max-workers=2`.

RED result: `compileTestKotlin` failed in 31 seconds on the absent view-state/filter/empty APIs and cascading property references. Existing product compilation was up to date. No test body executed; this is an absent-contract compile result, not a behavioral reproduction. `git diff --check` passed before the build freeze. No tests changed while Gradle ran. Implementation was explicitly released only after the process exited and owns subsequent full tests.

## Independent source review

Reviewed the implementation's actual Workspace, Deck, TopBar and new view-state function before its full build. Bounded contract source review PASS: the UI consumes the production view-state result, empty results cannot retain old details, reset clears all four restrictions, and the four labels match the available data. Notes use the real Workspace-owned map outside the page branch; selection/filter/page changes do not write to Room or clear drafts. Matching repository track-flow notes clear only the corresponding draft; failed writes leave it present. This fallback intentionally does not protect application exit.

The Deck theme action only opens the existing shared six-theme selection dialog; opening/dismissing does not invoke persistence. Choosing an enum passes the same callback used by the existing application theme path. Palette description and tooltip reflect the selected enum. No additional production changes requested by QA.

This source review does not establish native layout, the long search label's fit in the 320dp library column, manual dirty-draft navigation, or persisted theme after application restart. Those remain separate acceptance gates. No Gradle run was started by QA after the exclusive handoff.
