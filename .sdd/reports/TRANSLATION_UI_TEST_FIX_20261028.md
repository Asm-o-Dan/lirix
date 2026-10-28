# Partial translation UI test checkpoint — 2026-10-28

`translation-targeted-fourth-20261028.log`: both failed scenarios reached line 124, checking enabled Save immediately after checkbox semantic click and exactly one frame. The assertion gives no evidence by itself whether Compose schedules recomposition after that one frame or product state remains disabled. No product change made.

Targeted test-only adjustment in `desktop/src/test/kotlin/com/lirix/app/desktop/PartialTranslationPanelTest.kt` in both main and canonical: after real checkbox action, render/poll up to 240 frames with a short wait until the actual Save semantics becomes enabled. The test still requires disabled before confirmation, enabled after confirmation, actual click, persisted document and source attribution; timeout fails. This removes a fragile single-frame timing assumption without accepting an unchanged disabled button. SHA256 both: `49f1d244531f9a2aededd3b64a745d62f70f0c9b371980b6bf3c31b544721608`.

No tests, Gradle, APK or device commands run by this executor. Root must rerun targeted tests; if the same failure persists, inspect runtime checkbox/busy state and change actual behavior rather than extending the deadline. Independent review of candidate identity still required.


## Follow-up after failed retry

The 240-frame wait alone still failed. Inspection identified the selector as the likely issue: it clicked the first semantics node with Role.Checkbox, but noncompact UI contains three FilterChips above the confirmation checkbox. Thus it can click the Original/Translation/Together chip instead of the required confirmation. The updated test chooses the unique actionable Checkbox whose own, ancestor, or descendant semantics has the exact localized confirmation contentDescription. It still checks disabled → action → enabled → actual persisted document, keeps the bounded recomposition wait, and does not alter product behavior. The surrounding UI has been independently inspected; only rerunning the test can confirm this diagnosis.

Updated test raw SHA256 in main/canonical: `97867f415f8d8a09418f18ba7891870962843f42a1fc9a6d4ef88e5723b96f81`. Gradle/APK not run by executor.
