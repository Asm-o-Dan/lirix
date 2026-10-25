# Android offline lookup recovery v1 — revised contract

Status: APPROVED, revision3 (2026-10-25). Process level2 (OS/network and concurrent persistence). Replaces the first draft's optional provider-health framework. Independent DoR review PASS: `.sdd/reports/ANDROID_OFFLINE_LOOKUP_SPEC_REVIEW_20261025.md`. Coordinator authorized sequential implementation of this bounded contract only; changed scope returns to review.

## 1. Scope and observed behavior

An uncached song opened offline must recover automatically when usable Internet returns without changing the song. Missing lyrics or chords also offer one compact explicit Retry. Preserve saved lyrics/chords, exact notes, unsaved studio drafts, source rejections, playback and the foreign main source/clock/session work. Only this queued task is active.

No transport-library refactor, new provider taxonomy, source cooldown scheduler, reference-counted cancellation framework, catalogue-miss certification, translation/Desktop work, schema migration or whole cache clearing. Null from any legacy provider is unresolved, never proof that a song is absent. A timeout alone does not prove device connectivity loss.

## 2. Target-success predicate (R1)

`LookupTarget = LYRICS | CHORDS`.

- Lyrics target succeeds only with nonblank actual plain/synced lyric content; `isUserNote` must be false and result sourceId must not be USER_NOTE or NONE. USER_NOTE fallback is never target success and must never be persisted as lyric content by this automatic path, regardless of hasLyrics=true.
- Chord target succeeds with nonblank actual chords or existing recognized inline chords in real lyric content. It does not require hasLyrics=true. Notes are not passed to inline chord detection as a substitute lyric source.
- Fresh accepted cache/current TrackEntity lyric fields satisfy lyrics, except rows whose provider identifies note fallback. Fresh manual-provider content is authoritative: no automatic modification, even if the requested target remains missing. Show applicable unresolved status/manual search instead of replacing manual text.
- Automatic requests for a missing target bypass the aggregator's USER_NOTE shortcut, preserving original notes untouched. Add a narrowly scoped automatic acquisition entry point/options rather than changing legacy `getLyrics` fallback behavior for other callers. `forceNetwork=true` alone is insufficient: notes shortcut still executes today.
- CHORDS acquisition bypasses only a missing-chord cache shortcut. It must use existing chord-source priority even when plain lyrics already exist and leave those lyrics visible and unchanged. A network lyric response cannot overwrite existing accepted lyrics as a side effect of finding chords.

## 3. Network lifecycle / actual mobile policy

An Android application-context monitor exposes `Flow<LookupNetworkSnapshot(usable, generation)>`. Implement via callbackFlow with registerDefaultNetworkCallback, initial active snapshot after registration and unregister in awaitClose. Foreground collects only while STARTED; no Activity/View references retained. `usable` requires active default network with INTERNET+VALIDATED. Re-read active network/capabilities rather than trusting onAvailable or an old network's onLost. Duplicate equivalent callbacks do not increment generation; unavailable->usable or validated default-network replacement is one new opportunity. Snapshot/registration SecurityException becomes unavailable without crash.

The current NetworkConditionManager transport enum is not a complete connectivity monitor. Historical `.sdd/specs/lyrics-engine/overview.md` describes DISCONNECTED/CELLULAR gates which do not match executable APIs/callers. Actual Engine prefetch only excludes OFFLINE_ONLY; foreground does not enforce ON_DEMAND_ONLY. Therefore allow foreground recovery and explicit Retry for the already-open track on validated cellular as well as Wi-Fi/Ethernet. Preserve prefetch's existing gate; no unrelated mobile-data-policy correction or new setting.

## 4. Minimal foreground state and recovery rules

`LookupState(trackKey, target, requestGeneration, phase, cachedContent)` where phase is only `Loading | Available | WaitingForNetwork | Incomplete` (Idle may represent no active track). Waiting means observed unusable network; opaque source null/error while usable means Incomplete. Do not show existing categorical lyrics-not-found copy for an opaque miss. No need to introduce ConfirmedNotFound.

`request(trackKey, target, trigger=INITIAL|MANUAL|NETWORK_RECOVERY)` coalesces through the shared gate below. Foreground state owns visible generation and a pending network opportunity; it does not own shared HTTP job lifetime.

1. Re-read current cache/track/rejections before acquisition. If target satisfied, publish Available, no network. Without usable Internet, publish WaitingForNetwork and make no HTTP requests.
2. Auto-retry only a still-missing target whose attempt was started/blocked without usable network or observed an outage while pending. Use at most one automatic recovery per current track/target/network generation. An unexplained null/IOException on steady usable network does not arm recovery; explicit Retry remains available.
3. If network returns during Loading, remember that generation. On unsuccessful completion after the observed outage, launch the one pending recovery if still current and usable. Successful target cancels it. Do not lose the event by simply ignoring callbacks while Loading, and do not enqueue duplicate completed work.
4. Manual Retry during Loading is disabled/coalesced. After terminal incomplete state another explicit tap may acquire again, respecting existing provider cooldowns. No periodic retries, timers, cooldown bypasses or global reset.
5. Track switch/disposal cancels screen subscriptions and invalidates UI generation/pending opportunities. Shared finite job may finish safely under its original trackKey; it never publishes into the new key/generation. Check identity immediately before every visible state update, including failure/finally. CancellationException must propagate, not clear newer state.
6. Observe Room cache per active key, so background/manual changes satisfy the target without redundant HTTP. Unsaved editor/notes input and studio drafts are not reset by state/network changes. Display useful cached lyrics while chords are unresolved/loading.
7. Foreground re-entry rechecks actual connectivity/cache. A previously Waiting unresolved active track can use the current usable snapshot as its one recovery opportunity; lifecycle pause must not permanently lose the reconnection. No retry after every recomposition or repeated resume of a completed Incomplete-on-usable attempt.

## 5. Shared finite in-flight gate: explicit join rule

One application-owned automatic lookup gate is used by foreground, MusicFeatureEngine.prefetchLyricsAsync and FloatingLyricsService automatic acquisition. Keep only executing jobs in memory; no persistent work queue and no Activity references. Job body uses applicationContext/DAO/provider snapshots only, a finite total coroutine timeout plus existing HTTP timeouts, and removes its entry in finally. Use a90-second **logical coroutine deadline** (inject a shorter budget in tests), not a hard physical runtime bound. Existing HttpURLConnection blocking sections may take their existing connect/read timeout to unwind after cancellation. Call ensureActive before entering every next provider/source operation and immediately before guarded commit; timed-out work cannot proceed to a later source or save. No claim of immediate socket cancellation or guaranteed physical completion at90s. UI stops observing promptly. No ref counting or last-subscriber socket-close contract.

Registry primary key is `(trackKey, rejectedSourceIdsSnapshot)` — **target is not part of the registry key**. Each active entry records its operation coverage (`LYRICS_WITH_CHORD_ENRICHMENT` or `MISSING_CHORDS`). Re-read cache/rejections on entry and serialize create/join decision under the gate.

- Same key with active LYRICS_WITH_CHORD_ENRICHMENT: all three callers join it, including CHORDS caller. After it finishes, the chord caller rechecks target. Only if still missing and its current attempt remains allowed may it acquire a single MISSING_CHORDS job. This is required work for the unresolved target, not duplicate lyric search.
- Active MISSING_CHORDS: same-key chord callers join. A lyrics caller waits for it, rechecks fresh cache, and starts one lyric operation only if actual lyrics remain missing. There are never concurrent automatic provider chains for the same key.
- Different rejection snapshots invalidate old automatic authority; do not join an old-source request. A stale job may finish under its original key but transactional authorization below rejects newly forbidden fields. This preserves source-replacement semantics without wiping rejections. Track changes alone do not authorize cancellation of another caller's shared work.
- Every awaited covering operation consumes that caller's coverage for its current trigger, including null, miss, exception or timeout. Fresh cache recheck alone must never restart that operation. Awaiting MISSING_CHORDS is terminal for the chord attempt even if cache is still missing.
- A parent attempt handle retains its completed result/coverage and at most one shared follow-up reference per genuinely uncovered opposite operation. All waiters holding that parent handle join/create the same follow-up under the gate mutex; after that follow-up is served, their trigger terminates even on all-miss. In particular a lyric+enrichment parent permits at most one explicit missing-chord follow-up, not one per waiter; an initial chord parent permits at most one missing-lyrics operation. No recursive chain: a follow-up cannot spawn a second opposite follow-up.
- Remove completed registry entries in finally, but keep their handle alive only through current waiter references (including the completed parent/follow-up reference). This is not a persistent miss cache. A new explicit manual trigger or new eligible network generation may start a new attempt; old waiters cannot reacquire solely because the registry entry was removed. Successful Room content still prevents unnecessary later attempts.

All participating automatic callers use this gate's result/commit path and bypass old unguarded discovery callbacks and duplicate full-row saves. Explicit reject/change-source/manual editing operations remain existing deliberate operations, with current rejection authority honored.

## 6. Transactional persistence / R2

Introduce one separate Room `fillMissingAutomaticLookup(...)` transaction, preserving existing saveAutomaticIfNotUser semantics where present. Main has that helper; canonical does not. Do not transplant either full DAO file. No schema changes.

Within **the same Room transaction** as both lyrics_cache write and music_tracks update:

1. Read current cache row, relevant current music_tracks fields and current source rejections.
2. Candidate carries a source origin for each acquired field (contract below). Reject candidate fields whose actual originating source is currently rejected; reject any USER_NOTE/NONE content; reject all automatic changes if current authoritative row is manual-provider content.
3. Fill only absent requested fields from valid target results. Never overwrite fresh nonblank lyrics/synced/chords or exact existing notes. Preserve note-only row notes. A source rejected while HTTP was suspended cannot be persisted in either table. Atomicity with rejection insertion must follow Room transaction serialization, not a preflight rejection check outside transaction.
4. Preserve provenance for untouched fields. **sourceAttributionJson exists in main, canonical and HEAD3b25fb3 MusicEntities.kt:92**; no schema change is needed. The newer main attribution helper may differ, but canonical JSON must not be stripped. Merge attribution only for fields actually filled. Filling chords must not replace provider/provenance of retained lyrics. Return the fresh row rather than stale candidate.
5. Dual-write only newly filled lyric fields into music_tracks, preserving fresh existing track values. Chord-only recovery does not rewrite track lyrics. If nothing authorized is filled, no mutation. Shared job may persist old-track data only when this transaction still authorizes that track/source; old UI identity is not database authority.

Existing automatic provider callback `onLyricsDiscovered` must not write before the guarded transaction. Canonical Engine installs an unguarded musicDao writer; scoped automatic gate callers use a side-effect-free provider instance/path. Main callback was already independently changed: preserve it without resurrecting the canonical writer.

### Deterministic clarified helper signatures

New internal shared DTO file `shared/src/commonMain/kotlin/com/lirix/app/storage/AutomaticLookupCandidate.kt` (no entity/table):

```kotlin
data class AutomaticLookupField(
    val text: String,
    val sourceId: String,
    val sourceUrl: String? = null,
    val author: String? = null
)
data class AutomaticLookupCandidate(
    val plain: AutomaticLookupField? = null,
    val synced: AutomaticLookupField? = null,
    val chords: AutomaticLookupField? = null
)
```

Each field's sourceId is the *actual origin*, not the aggregate result source. Preserve AmDm/MyChords identity through chord fetch/enrichment; do not label their chords LRCLIB merely because LRCLIB supplied words. Unknown/blank/USER_NOTE/NONE origin is not eligible for automatic persistence. Content with unverifiable origin remains unresolved, not relabelled. Existing custom-rule origin is its exact `rule:<id>`.

Android `AggregatedLyricsProvider` scoped entry/helper (leave existing getLyrics API intact):

```kotlin
suspend fun acquireAutomatic(
    track: TrackEntity,
    target: LookupTarget,
    rejectedSourceIds: Set<String>
): AutomaticLookupCandidate
private suspend fun fetchAutomaticChordTabs(
    track: TrackEntity,
    rejectedSourceIds: Set<String>
): AutomaticLookupField?
```

The helper returns AmDm/MyChords field+origin at the point each source succeeds. Automatic entry bypasses notes and discovery writes; it returns only actual acquired fields. Additional-source hits retain their own existing origin. A missing candidate field is not a claim of confirmed absence.

Room signatures in existing LyricsDao:

```kotlin
@Query("SELECT * FROM music_tracks WHERE trackKey = :trackKey LIMIT 1")
suspend fun getAutomaticLookupTrack(trackKey: String): TrackEntity?
@Transaction
suspend fun fillMissingAutomaticLookup(
    trackKey: String,
    candidate: AutomaticLookupCandidate
): LyricsCacheEntity?
```

Use current getLyrics/getRejectedSourceIds and minimum conditional update queries inside this transaction; return fresh cache, including unchanged row when candidate not authorized. Candidate DTO has no notes argument and cannot overwrite notes. Preserve/merge existing sourceAttributionJson in both source variants with equivalent bounded helper semantics, reusing main helper where present and adding only necessary compatible merge logic to canonical.

Android in-flight handle signatures (internal types; no persistent completion store):

```kotlin
enum class LookupOperation { LYRICS_WITH_CHORD_ENRICHMENT, MISSING_CHORDS }
data class AutomaticAttemptResult(
    val candidate: AutomaticLookupCandidate,
    val servedOperation: LookupOperation,
    val timedOut: Boolean
)
internal class AutomaticAttemptHandle(
    val id: Long,
    val operation: LookupOperation,
    val completion: Deferred<AutomaticAttemptResult>,
    val followUps: MutableMap<LookupOperation, Deferred<AutomaticAttemptResult>>
)
suspend fun acquireOrJoin(
    trackKey: String, rejected: Set<String>, operation: LookupOperation
): AutomaticAttemptHandle
suspend fun awaitTargetAttempt(
    parent: AutomaticAttemptHandle, trackKey: String,
    rejected: Set<String>, target: LookupTarget
): AutomaticAttemptResult
```

`followUps` creation/access is mutex-protected and parent-scoped. `awaitTargetAttempt` awaits parent exactly once, marks its served coverage for that call even on miss/timeout, rechecks target, and may create/join only the one permitted opposite follow-up using the parent reference. It never calls itself recursively on a follow-up and never re-enters acquireOrJoin after its coverage is exhausted. Terminal provider errors become empty candidate/Incomplete result within this finite attempt, except CancellationException propagates; the top-level logical timeout returns timedOut=true without authorizing commit. These signatures express served coverage and shared follow-up identity, not a provider-health framework.

## 7. Compact production UI and resources

Add optional retry/status parameters to existing EmptyPlayerStateView and thread current-target state/callback through reading content. Keep existing Teach/manual search secondary and distinct. Exactly one Retry for unresolved lyrics/Karaoke or missing chords; none for Notes, no track or satisfied target. Retain cached reading content; chord retry uses local status rather than blanking lyrics. During actual request disable/coalesce Retry and show existing progress appropriately.

Dedicated `values/lookup_retry.xml` and `values-ru/lookup_retry.xml` avoid foreign-dirty general strings files:
- `lookup_retry`: “Try again” / «Попробовать ещё раз».
- `lookup_waiting_network`: “No Internet connection” / «Нет подключения к интернету».
- `lookup_incomplete`: “Search could not be completed” / «Поиск не завершён».

Use these truthful states for unknown source failures. No unsupported “not found”, “connection failed” or reason inferred from nullable return. Compact TextButton/OutlinedButton, min48dp touch area, wrapping text, existing theme tokens; scroll existing empty region when required at font2. Do not restructure player/deck/transport/menu.

## 8. Permitted source/functions (bounded implementation inventory)

New names may be adjusted at review without introducing extra architecture:
- Android `feature/lyrics/AutomaticLookupCoordinator.kt`: request/acquire-and-join, target predicate, bounded job lifecycle and guarded commit delegation.
- Android `feature/lyrics/LookupNetworkMonitor.kt`: snapshots/callback lifecycle only. Keep existing NetworkConditionManager API/gate unchanged.
- Android `feature/LyricsProvider.kt`: narrowly scoped automatic missing-target entry point and chord acquisition; preserve normal getLyrics and foreign source priorities/attribution. No rewrite of shared source HTTP/parsers.
- Android `feature/MusicFeatureEngine.kt`: only prefetchLyricsAsync and narrowly required constructor/gate injection; preserve monotonic recorder/session methods.
- Android `service/FloatingLyricsService.kt`: replace only automatic acquisition/write block with shared gate; preserve overlay/render/seek lifecycle.
- Android `ui/NowPlayingScreen.kt`: existing lyric load effect/state, Room observation and retry callback/status plumbing; preserve foreign PlaybackUiClock/source-info and all existing player behavior.
- Android `ui/components/EmptyPlayerStateView.kt`: optional status/retry view only; new two resource files above.
- Shared `storage/AutomaticLookupCandidate.kt`: plain internal DTOs above; no entity/schema.
- Shared `storage/LyricsDao.kt`: separate transactional fill-missing method and minimal supporting queries only, compatible with current main/canonical respective schema. Preserve existing methods.
- Corresponding focused unit/native tests and fixtures; intake/spec/report. No broad general strings edit, transport library edit, translation/cache purge or next-backlog work.

Main-vs-canonical guard: root baseline `.gradle/offline-recovery-preservation/source-baseline.json` and `main/` are immutable. Review diff against both baseline main and clean commit3b25fb3. Apply equivalent scoped changes per checkout, not wholesale copies across different source/DAO versions.

## 9. Independent acceptance plan

Tests must exercise actual coordinator/provider flow and persistence rather than return preassembled success state. Network state-machine tests use controlled events/scheduling/HTTP counts; they are not parser/word dictionary tests. Existing fixture roots: app/src/test/resources/fixtures/audio and shared/src/commonTest/resources/fixtures. Use real complete supported-source response fixtures where parsing is exercised, including unseen metadata, malformed and empty responses; do not change parsers solely to expand tests.

A. Target/aggregator: note-only track and note+chord requests through real automatic aggregator flow, offline->online -> actual lyrics/chords; exact notes unchanged, never copied into lyrics. Legacy getLyrics USER_NOTE fallback unchanged. Cached lyric+missing chords bypasses only chord shortcut; recognized inline chords no HTTP.

B. Coordinator/monitor: add an adversarial three-waiter all-miss test: all await one lyric parent; nullable/miss/timeout completion yields exactly1 lyric chain plus at most1 shared missing-chord follow-up, never one follow-up per waiter. Assert reverse chord-parent/lyric-follow-up ordering similarly. Then offline0HTTP -> validated recovery1;100 duplicate callbacks still1; validated cellular recovery allowed; captive/unvalidated0HTTP; steady usable null is Incomplete/manual only. Recovery during Loading retained, success consumes pending, repeat taps coalesce, timeout cleans gate for next explicit retry. A->B delayedA never changesB. Three actual automatic caller paths produce one shared chain and a single necessary chord follow-up; opposite active-chord/lyric waiter ordering also deduplicates. No Activity refs and callback unregistration on cancellation.

C. Real Android Room: suspend LRCLIB lyrics + AmDm chords candidate, insert AmDm rejection, resume; permitted lyric fields persist, AmDm chord field/provenance does not, retained fields/provenance remain exact. Repeat MyChords-origin case (parent lyric source never authorizes chord source). Then manual save while HTTP suspended; source rejection inserted before completion; note-only row; stale fresh automatic row with partial fields; existing full row; independent music_tracks fresh content. Assert exact resulting rows in both tables, notes and per-field provenance preserved, rejected fields absent, manual content never changed, chord-only never rewrites lyrics. Do not assert only DAO method invocation. Do not clear database to make retry pass. Unsaved studio draft remains unchanged across network events.

D. Production native UI: RU/EN font1/2 portrait/landscape; one Retry reachable>=48dp; current localized Waiting/Incomplete copy; real controlled request leads to content without track switch; no Retry for Notes/no-track/satisfied view; Teach separate; useful lyrics stay while chords retry. Test actual resulting resources, not prefs writes or only callbacks.

E. Actual API35 app network: both Wi-Fi and mobile offline (Wi-Fi alone falls back cellular), open uncached track, restore validated connection without song change; verify actual snapshots, bounded request count and rendered real response. Network flap during loading, manual retry after incomplete, source rejection/manual save race, track switch. Root baseline `offline-recovery-connectivity-baseline.txt` confirms Wi-Fi+cellular available; no radio changes by this spec. Emulator evidence is not phone evidence. No live-provider stability claim from mocks; disclose source cooldowns and unavailable phone.

F. Targeted unit/native/Room regressions, Android debug/lint and shared compile compatibility if DAO method changes; independent final diff/data-preservation review; private preview + checklist, no public release/version bump. Original whole UI/studio objective stays open.

## 10. Sequential execution

One executor performs dependency slices: monitor/target+bounded gate tests; Room merge/caller wiring; compact UI; OS/native acceptance. One current task, no parallel streams/new agents needed. Atomic cards only if separately assigned units become necessary. Any changed scope or data contract returns to review before code.
