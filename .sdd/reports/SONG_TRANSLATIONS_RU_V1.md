# Russian translations v1: implementation and verification
Date: 2026-10-19. Direct implementation and self-review by root; no subagents or independent review claimed.

## Delivered
- Shared Amalgama URL provider, serializable paired document and Room table. Additive schema v11 -> v12 migration registered in Android and desktop production builders.
- Desktop Now Playing / Text uses the shared Original / Russian / Together reader; Library has a Translation action for offline tracks. Android NowPlaying Text uses the same panel, keeping edit and synchronization actions.
- URL -> explicit preview -> song/version checkbox -> save. Saved documents read locally without HTTP. Original player lyrics/LRC/chords/notes are independent. Missing paired side remains an explicit placeholder. Source and credited translator remain visible.
- Distinct source URLs are retained as variants, reimport updates only the same variant on explicit confirmation. Latest successful import is the default; changing the viewed variant is currently session-local. An unsuccessful request does not erase a saved document.
- Phone/narrow paired rows stack; wide standalone panel uses two columns. No inferred timestamps or bilingual karaoke are shipped.

## Tests and artifacts
Final command: :desktop:test :shared:desktopTest :app:testDebugUnitTest :app:assembleDebug :desktop:createDistributable --offline --no-daemon --no-configuration-cache --max-workers=2.
BUILD SUCCESSFUL, 2m57s.
- Desktop: 96 discovered, 95 passed, 1 skipped, 0 failures/errors. The skipped case is the unrelated opt-in whole-workspace matrix generator. Translation tests and translation renderer ran.
- Shared: 173 passed, zero failures/errors/skips.
- Android: 198 passed, zero failures/errors/skips, including actual v11 migration and offline reopen through Android Room.
- Seven parser/transport/storage regressions cover Windows1251/UTF8, entities/breaks/metadata, sections/repetition/missing sides, malformed/oversize pages, URL and redirect validation, HTTP errors, interrupted cancellation, real v11 Room migration/reopen and unchanged history/lyrics/chords/notes. Same-URL import does not duplicate; other variants and track isolation verified.
- Detached actual Compose panel action test uses real SetText/OnClick/checkbox actions, asserts no persistence before confirmation and saved paired content after explicit save. Eight PNGs under desktop/build/translation-previews. Inspected narrow empty/preview/together/Russian and wide Together frames: readable wrapping, narrow stacking, wide paired columns. These are shared Compose renders, not a physical Android screenshot or full native-window acceptance.
- User-provided inspection copy parsed successfully (UTF8 on disk after prior HTTP decoding). A separate live HTTP probe executed the actual packaged Amalgama provider against the supplied Joseph URL: 70 paired rows, translated heading Cyrillic and translator JaneTheDestroyer verified. No song lyrics printed and no copyrighted full-page fixture committed. Live probe writes no user database.

## Build identity
Packaged hashes match built artifacts:
- desktop jar: 5379737156361DD1EAA8F5FA5B1ED3556E7A27B9DA8EFED3862A12465F2F62E8
- shared jar: 47B98252187C2CB16CCFC6752F225B69D90C7C64DA9C60884144E947720B260B
- native bridge unchanged: 99B820142E4A741DDC20B8DD4AACCAAA3F33B27E6020EAFA76CF66C986D28C78
APK signature verifies; SHA256 signer 971ecda293eb39469b2c9419f9e04b5db2515276de433869914eb73179774192 matches the previous local release APK. No phone installation or user application restart performed.

## Remaining scope
Automatic discovery, manual translation editing, exact-original alignment/bilingual karaoke, translated HUD and archive/merge inclusion remain separate steps. Archive v1 does not include translations. Preview import is not a translation draft editor or crash recovery feature. No paid translation API or source API was invented. Native phone/desktop interactions and physical browser playback acceptance remain open; no new release was published.

## Iteration evidence
First compile found Room is not usable with the test's Closeable.use shortcut; replaced with explicit finally-close, without weakening migration assertions. Detached UI test initially targeted a FilterChip as checkbox; the confirmation now has a distinct accessibility label and the test uses it. Save handler independently rejects unconfirmed and duplicate in-flight actions. The saved HTTP inspection copy is UTF8; its probe supplies that charset rather than blindly using its original page meta. Synthetic transport tests still verify actual Windows1251 decoding.
