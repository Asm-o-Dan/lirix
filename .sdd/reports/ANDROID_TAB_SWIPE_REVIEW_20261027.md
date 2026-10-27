# Android tab swipe review — 2026-10-27

**Source review: no confirmed pointer/Back blocker**, user requested swipe navigation; note non-interactive release-only behavior below. Source review only. No build/APK, no pointer-dispatch/device proof. Main and canonical NowPlaying have separate existing playback implementations; keep their deltas separate.

## Contract distinction — release-only swipe instead of finger-follow

`.sdd/specs/android-ui/library_removal_and_navigation_20261027.md` §2 requires interactive movement between tabs. `AppScaffold.kt:205–235` observes and accumulates gesture but changes `currentTab` only on release (`:220–224`); `AnimatedContent` transition starts after that (`:237–244`). Neither page follows the finger, reversal has no visual feedback, and no gesture-driven progress/settle exists. This implements a swipe shortcut, not finger-follow motion. Coordinator clarified user requested swipe navigation and did not require finger-follow. Record the implementation precisely and avoid claiming interactive finger-follow; a green classifier unit test cannot certify interactive motion.

## P2 — existing main-tree track info modal absent from blocking condition

Main `NowPlayingScreen.kt:475–507` contains `infoTrack` from foreign existing source; condition omits `infoTrack != null`, while `:1064–1067` renders its dialog. Canonical lacks the foreign infoTrack path, so ensure main keeps the existing UI protected when integrating. Dialog uses its own window and may prevent interception naturally, but no device proof; source does not satisfy explicit modal gating.

## Other observations and verification limits

- The detector observes first down at Initial, then moves at Final after children; it cancels on consumed movement, vertical dominance, multiple pointers, protected filter bounds, IME/known player modal/parent overlay and system left/right edges. `TabSwipeGesture` covers arithmetic, LTR/RTL indices and guard predicates only. It does not test actual dispatch or event consumption, native inset coordinates or screens.
- At release `AppScaffold.kt:220–224` ignores `change.isConsumed` and any final up-position delta, while it unconditionally consumes up after prior dragging. Whether a child can capture/consume up after unconsumed movement needs a real Compose test; do not call this a reproduced bug.
- Removing offscreen precomposed pager avoids the anticipated hidden-player `BackHandler` and lifecycle regressions. Existing single `currentTab` and SaveableStateHolder preserve original external navigation path. Actual duplicate child composition during AnimatedContent transitions and one-shot lifecycle behavior still need test.
- Player blocks Notes, IME, focused notes, focus mode, sheet and most modal states; parent protects share/Teach/blacklist. Library modal/dropdown are separate windows, but the parent has no explicit library-block flag. Real modal dismissal and child scrollbar/seekbar/vertical gestures need actual touch tests.
- Verify first/last page, drag reversal and thresholds, 0..system gesture inset on both edges, RTL, filter LazyRow, sliders/scrubbers, popup/dialog/IME, vertical/diagonal, orientation, tabs and external intents under touch, accessibility and reduced motion. No runtime PASS claimed.


## Clarification and independent follow-up

The initial interactive finger-follow wording was coordinator-authored, not a user requirement; approved contract now specifies change after swipe release. A separate read-only follow-up found no confirmed blocker under that contract and confirmed main-only infoTrack guard. Native pointer dispatch and conflict cases remain unverified.
