# Automatic Russian translations v2: verification
Date: 2026-10-19. Direct implementation/self-review by root, no subagents or independent review claimed.

## Outcome
The primary path is metadata -> catalog discovery -> verified song -> automatic offline save. It does not require URL input, preview confirmation or Save. Desktop Main starts work when playback metadata changes; Android NowPlaying starts it independently of the selected reader mode. The shared reader also resolves a selected library track, defaults to Together with automation, and exposes searching/missing/error/candidate states. Manual link import remains a fallback.

Actual Amalgama #songs_nav artist/song links are followed; sidebars and guessed slugs are excluded. Its .bands_list popular-artist links cover some name/bucket differences. Unicode/channel/article/number/presentation normalization is shared. Live/Remix/Acoustic differences never become exact title matches. Exact catalog ambiguity or wrong page identity requires user choice. Heading-only tolerance for one substituted character in long artist names is restricted to a unique exact catalog match and exact title, after observing the source's Ozzy Ozbourne typo; it never affects finding artists in catalogs.

Saved selections always win. Cache lookup precedes HTTP; duplicate view requests coalesce under the coordinator, source catalog links cache24h (96 documents), misses/candidates15min, errors1min, status history128 entries. Explicit retry bypasses negative/catalog cache. Cancellation rejects a late result. The automatic DB write is transactional and occurs only if no translation was selected meanwhile. No schema bump beyond v12. Original lyrics, offsets, chords, notes and archive v1 are unchanged.

## Automated evidence
Final Gradle command: :desktop:test :shared:desktopTest :app:testDebugUnitTest :app:assembleDebug :desktop:createDistributable --offline --no-daemon --no-configuration-cache --max-workers=2.
BUILD SUCCESSFUL in4m13s.
- Desktop109 discovered:108 passed,0 failed/errors,1 unrelated opt-in workspace-matrix skip.
- Shared173 passed,0 failures/errors/skips.
- Android198 passed,0 failures/errors/skips, including Android Room automatic insert/decline to overwrite inside migrated/reopened database.
- Twelve new discovery/coordinator tests cover relative real links, sidebar exclusion, source heading identity, unique vs duplicate titles, version qualifiers, channel/article/accent/number forms, source-bucket fallback, source-format/redirect errors, catalog reuse/explicit refresh, saved-first automatic persistence, negative TTL, ambiguous result without save, concurrent views, noncooperative late cancellation and actual Room manual-selection race.
- One added detached actual Compose test performs NO clicks and NO SetText: renders Searching, releases a synthetic provider result, asserts persisted translation and ready paired UI with no input field at360x740 and900x720. Four automatic PNGs under desktop/build/translation-auto-previews; inspected narrow searching/ready and wide ready, readable stacking/columns. Existing manual-flow/charset/storage tests also pass.
- Live packaged-provider probe supplied only artist/title metadata, never a song URL: Joseph70 paired rows; Hellraiser32; Hurricane292. All3 returned automatic Found. Joseph had official-video/featured-artist noise; Hellraiser had the user's Anniversary/Official Animated title and OzzyOsbourneVEVO channel; Hurricane exercised Thirty/30 source-bucket fallback. No lyrics were printed and no actual-song HTML fixture committed.

## Package identity
Built and packaged SHA256 values match:
- desktop jar4FF94FFF3953E918EA3E4B84B79655C7280DD8750454BC5821BEC3E10FF8A0A6
- shared jar4D7697D56BADBC9D2F638364BDC5238AB7DA5A096373AB700804971BA119EB35
- unchanged native bridge99B820142E4A741DDC20B8DD4AACCAAA3F33B27E6020EAFA76CF66C986D28C78
APK signature verified, signer971ecda293eb39469b2c9419f9e04b5db2515276de433869914eb73179774192 matches prior local release. git diff --check PASS.

## Limits
Physical phone/app interaction is pending; no user process was stopped and no APK installed. Source coverage depends on available metadata and the catalog; unsupported aliases can still return missing/choice. Popular-index fallback is bounded, not a full-site crawl. Existing selected translations are not silently refreshed/overwritten. Karaoke alignment/subtitles and translation archive/merge inclusion remain future work; no new GitHub release was published.
