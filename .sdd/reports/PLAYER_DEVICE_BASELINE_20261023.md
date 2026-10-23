# Device baseline: Gold player regressions — 2026-10-23

User authorized a hands-off ADB test window. Device 2440cbe2; existing app 1.5.1 code19. No APK installed during this baseline.

## Observations
- Chords opened for Gold / Imagine Dragons. Before: key Em, intro `Am (Em) F (C)`.
- After one sharp press: key label Fm (+1); intro unchanged. This confirms a visible mismatch between the selected transpose and rendered chord content. Evidence: `build/repairs-device/chords-before.xml`, `chords-plus.xml`.
- Heart press produced a pressed visual but the heart remained outlined in the screenshot. Read-only database snapshots confirm favorite flag changed 0 to 1 despite the unchanged outline. Evidence: `heart-before.png`, `heart-after.png`, `heart-after.xml`.
- Toolbar bounds y220..355 with status area near y100 confirm substantial space above the toolbar; exact cause awaits source owner diagnosis.
- Current chords overflow has Teach, track/source information, overlay, theme, blacklist; no interface-language option. Current APK is not acceptance evidence for the new picker.

## Restoration and limits
- Returned key to Em with offset zero, confirmed in `chords-restored.xml`.
- Toggled chord spelling control back to its original state after identifying it as a spelling toggle rather than reset. Heart state remains visually unchanged, but persisted flag is now 1; restore original 0 through the fixed UI after installation (do not modify device SQL).
- Earlier Library-navigation trial is invalid and excluded.
- Phone DB backup and metadata remain local under ignored build/repairs-device. Schema14 compatibility is a mandatory installation gate; never uninstall or downgrade destructively.

## Pending
Source diagnosis, deterministic regression tests, safe candidate build, post-fix device checks (including favorite persistence and language resource changes), independent review.