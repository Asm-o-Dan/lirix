# Android device verification — notes and library

Date: 2026-10-30. Device connected via ADB; installed Lirix 1.5.1, last updated 2026-10-28. The exact source revision of the installed package was not established; results apply to this installation only.

- PASS: In the Russian portrait notes view one Done action and the useful examples hint are visible.
- PASS: A temporary note survived leaving the player for the library and reopening the same track. Its originally empty value was restored and checked after reopening.
- PASS: A previously non-favorite track's menu changed to the remove-from-favorites action after adding it, then back to add after reversal.
- PASS: Removing that track lowered the library count by one; restoring it from the removed list returned the original count and the track. A second removal followed immediately by Undo also restored it.
- PASS: Horizontal swipes navigated library -> listening summary -> library. Vertical list scrolling remained in the library without triggering removal.
- Initially NOT RUN: English, landscape, large font, Back, persistence after restart and horizontal filter gestures. See additional device checks below.
- NOT RUN: Comparison of installed APK identity to the latest repository revision. No APK was built or installed for this verification.

The two backlog cards remain open until their remaining acceptance criteria are checked against the intended build.

## Additional device checks

- PASS: After entering a temporary note, pressing Android Back to dismiss the keyboard and Back again to exit editing, the text was still present after leaving for the library and reopening the track. The empty original note was restored.
- PASS: English portrait view has one Done action, a distinct title and a useful examples hint. Russian language setting restored afterwards.
- PASS: At font scale 1.3 the Russian notes title, one Done action and hint were visible in portrait and actual landscape rotation 3. Original 1.0 font scale restored.
- PASS: At default font scale the same Russian notes elements were visible in actual landscape orientation.
- PASS: Adding a track to favorites placed it in the favorites filter, and it remained there after force-stopping and relaunching Lirix; the temporary favorite was removed and the filter count returned to its initial value.
- PASS: A track removed from the library remained absent after force-stopping and relaunching; it remained available for restoration in the removed list. Restored it and confirmed the count and track returned.
- PASS: A horizontal gesture in the library filter row scrolled the filters while remaining in the library.
- LIMITATION: Not all Back/focus or gesture combinations and landscape English were individually exercised. Installed APK source revision still unproven; device results cannot establish the current source revision is installed.

## Current-source build and interrupted follow-up

- PASS: `:app:assembleDebug` completed on the isolated snapshot. The player, library and swipe sources correspond to HEAD except for whitespace in the player file. APK and previously installed package use the same signing certificate, and `adb install -r` succeeded on 2026-10-30; the installed version remains 1.5.1 / code 19.
- PASS: After the update, the library opened and the original test track and its empty note were present. A temporary note was saved, reopened, removed and confirmed empty again.
- INVESTIGATE: When first reopening a temporary note on the new build, its visible text also contained the track's lyrics. The origin was not established. A second temporary probe was started to confirm the saved value, but the device disconnected before it could be checked.
- ACTION REQUIRED: The temporary note `LirixProbe` may still be saved on track Of the Abyss (Lorna Shore). Restore its originally empty state as soon as the device reconnects; do not mark the notes task complete until this is done and the anomaly is investigated.
