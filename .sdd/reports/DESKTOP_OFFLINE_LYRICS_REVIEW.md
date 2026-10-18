# Desktop offline lyrics — independent review

Date: 2026-10-18. Scope: desktop_offline_lyrics.md and DSK-READY-001–005; current uncommitted service/repository patch and DesktopOfflineLyricsTest. Reviewer did not run Gradle, change product/tests, or launch/terminate applications.

## Decision

No blocking source finding against the bounded approved contract. Acceptance remains conditional on the coordinator's actual build/test results. This is source review, not native UI acceptance or release readiness.

## Source evidence

- `onTrackChanged` reads persisted lyrics and canonical rejections before memory, custom rules, disk or built-in lookup. Nonempty non-USER Room content now hydrates without write/network. USER, including intentional empty edits, wins even explicit refresh. Non-USER refresh bypasses saved/memory/disk.
- Room, memory and disk acceptance share canonical rejection filtering: LrcLib variants/LRCLIB, AmDm/MyChords/AMDM, Web Scraper, rule identifiers and unknown identities. Empty/NONE automatic providers are misses.
- Plain/LRC/chords and requested key/title/artist flow through one state parser. Plain text does not receive invented timing. Built-in extraction retains the prior provider search and matching logic.
- Both rule and built-in automatic results pass through `acceptAutomatic`. It checks cancellation/current identity before persistence, after persistence and after the subsequent authoritative repository read. Memory/disk writes occur after these checks. An effective stored USER replaces the automatic candidate. Saved-read, rule-list and disk publication branches also check current identity after suspension.
- `saveUserLyrics` captures only the job belonging to the edited current key, cancels and joins it before persistence, and publishes only while that key is current. An unrelated library edit does not cancel current playback lookup. A later track switch does not discard a successful durable edit.
- Repository USER protection reads and selects the effective value inside the existing mutex; both cache and track lyrics receive that effective value. This is a repository-instance invariant, not a cross-process transaction guarantee. The approved scope does not claim cross-process isolation.
- Constructor seams are trailing optional parameters. Production Main still constructs `DesktopLyricsService(coroutineScope, repository)`, therefore defaults use real Room, default filesystem cache and extracted real built-in lookup. Injected automatic results use the production normalization/persistence/publication path.

## Test assessment and nonblocking gaps

The 11 new real-Room tests exercise reopen hydration, genuine LRC/chords/plain content, force refresh, aliases, rejected memory/disk entries, cancelled saved reads, USER cancellation-before-write, late automatic persistence, held USER saves, unrelated edits and durable USER protection. Deferred barriers replace timing sleeps; fixture jobs join before database close. No live HTTP or user cache paths are needed.

Optional hardening: `automaticSaveReturningAfterTrackSwitchCannotPublishPreviousTrack` asserts final UI but not absence of stale memory/disk cache. Add a revisit/cache observation if changing cache order later; current source checks cover that path. There is also no injected failure test proving a repository read/write error becomes visible rather than a network fallback, although the outer error path currently does exactly that. These are coverage improvements, not observed product defects.

## Read-only isolated runtime configuration review

`shared/src/desktopMain/kotlin/com/lirix/app/storage/DatabaseFactory.kt` selects `System.getenv("APPDATA")`, falling back to `user.home`, then appends `Lirix/lirix.db`. A unique process-local APPDATA directory under a fresh workspace audit directory therefore isolates SQLite. This must be a process environment override, never an OS/user environment modification. `LOCALAPPDATA` separately controls service disk lyrics cache and should also point at a unique audit directory. Use no copied user database.

These two overrides are **insufficient for a fully isolated audit**. Theme, HUD geometry, karaoke offset and host identity use `java.util.prefs.Preferences`; the Windows backend shares the user's registry and is not redirected by APPDATA or user.home. Main additionally starts global hotkeys, Discord RPC, media ingress and Connect's fixed TCP/UDP port 4433. A second copy can fail hotkey/port registration and observe current playback; it is not equivalent to a clean single-instance run.

Recommendation for coordinator approval: isolate APPDATA plus LOCALAPPDATA for database/cache protection, but do not test settings/HUD persistence or claim complete isolation until a dedicated audit configuration supplies isolated Preferences nodes and disables or explicitly isolates startup integrations. No such runtime flag currently exists in the reviewed Main. Merely adding Java prefs.userRoot on Windows is not an established solution; do not assume it redirects the Windows registry implementation.

No further launch was attempted. The rejected second-copy launch and existing processes were not bypassed or terminated. Native visual audit remains unverified.
