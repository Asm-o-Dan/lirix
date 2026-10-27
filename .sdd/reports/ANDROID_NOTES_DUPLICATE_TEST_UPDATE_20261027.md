# Notes duplicate actions: test maintenance and review — 2026-10-27

Two previously existing instrumentation tests had stale expectations after removal of the overflow Save Notes action. Only the following test edits were made in both main and canonical:

- `app/src/androidTest/java/com/lirix/app/ui/PlayerSurfaceUiTest.kt`: in Notes mode assert `menu_save_notes` absent while asserting track info remains available; preserve other context assertions. SHA256 main/canonical: `312326e3c1a05127dce187ab9ecafe880f603752766404846c18b20205f6afa6`.
- `app/src/androidTest/java/com/lirix/app/ui/PlayerContextUiTest.kt`: assert removed Save Notes absent rather than click it, assert its unused callback count remains zero, then switch track to exercise popup dismissal and remaining menu checks. SHA256 main/canonical: `17a1600d9bbf1a5cc8bd6193b9c35bb2105534ba2d627b746ac551792824e6d8`.

Review of canonical diff against HEAD `6ba8c1f`: header duplicate removed; editor `Done` remains and still hides keyboard, clears focus and calls save; unchanged BackHandler retains save on focused notes. Placeholder has a distinct examples resource and existing heading stays. Notes overflow entry is absent. The menu renderer is only called by PlayerContextOverflow in current Android tree; `onSaveNotes` and `onSaveNotesClick` are retained but unused API parameters. No claim that prior async persistence is reliable, or that actual large-text/keyboard visibility and track switching were verified at runtime. No source blocker identified by static review.

Test edits were inspected against HEAD; no Gradle, APK, device or UI test run. Updated assertions require compilation/runtime confirmation by root.


2026-10-27: isolated canonical :app:compileDebugKotlin and :app:compileDebugAndroidTestKotlin BUILD SUCCESSFUL. UI tests compiled, not run; no updated APK created. Device/visual/Room reopen acceptance remains unverified.
