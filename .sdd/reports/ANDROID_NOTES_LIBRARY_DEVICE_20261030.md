# Android device verification — notes and library

Date: 2026-10-30. Device connected via ADB; installed Lirix 1.5.1, last updated 2026-10-28. The exact source revision of the installed package was not established; results apply to this installation only.

- PASS: In the Russian portrait notes view one Done action and the useful examples hint are visible.
- PASS: A temporary note survived leaving the player for the library and reopening the same track. Its originally empty value was restored and checked after reopening.
- PASS: A previously non-favorite track's menu changed to the remove-from-favorites action after adding it, then back to add after reversal.
- PASS: Removing that track lowered the library count by one; restoring it from the removed list returned the original count and the track. A second removal followed immediately by Undo also restored it.
- PASS: Horizontal swipes navigated library -> listening summary -> library. Vertical list scrolling remained in the library without triggering removal.
- NOT RUN: English, landscape, large font, physical Back persistence across all editor states, favorite persistence after app restart and its filter, removal persistence after restart, gesture conflicts with horizontally scrollable controls.
- NOT RUN: Comparison of installed APK identity to the latest repository revision. No APK was built or installed for this verification.

The two backlog cards remain open until their remaining acceptance criteria are checked against the intended build.
