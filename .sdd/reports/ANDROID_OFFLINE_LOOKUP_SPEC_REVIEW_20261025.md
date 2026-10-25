# Offline lookup recovery — independent spec review 2026-10-25

## Verdict

**FAIL / revise before implementation.** The user-visible direction, cellular clarification and preservation intent are sound. The plan needs one correctness amendment around note-as-lyrics fallback, explicit atomic rejection checking, and a smaller agreed implementation contract before code is authorized. This is a specification review only: no source/test edits, Gradle, ADB, new agents or network toggles.

Reviewed final intake/spec/backlog and read current main, canonical `.gradle/repairs-integration-ecce372`, and clean commit3b25fb3. Canonical LyricsProvider, MusicFeatureEngine, NetworkConditionManager and LyricsDao match that commit by LF-normalized bytes; main Provider/Engine/DAO differ. Main and canonical cannot be treated as interchangeable.

## Verified causes and API facts

- Canonical `app/src/main/java/com/lirix/app/ui/NowPlayingScreen.kt:513-558`: effect depends only on trackKey; cache plain/synced short-circuits; failures collapse to null; no connectivity observation/retry key. Main contains equivalent problem at its cited490-538, so intake cause is credible despite differing line offsets.
- `app/src/main/java/com/lirix/app/feature/lyrics/NetworkConditionManager.kt:40-50`: maps transport only, no INTERNET/VALIDATED callback. Its comments describe cellular on-demand restriction, but canonical `MusicFeatureEngine.kt:475-486` and main `:226-240` only skip OFFLINE_ONLY. The final spec correctly permits recovery for an already-open track on validated cellular and preserves the implemented prefetch gate. Do not introduce Wi-Fi-only or apply PREFETCH_ALLOWED to foreground recovery.
- Canonical `LyricsProvider.kt:947-974`: aggregator returns existing plain/synced Room fields, then notes, before network; chord fetch is private and typically enrichment after lyric success. CHORDS needs an explicit bounded missing-chord acquisition path; forceNetwork alone does not remove the notes shortcut.
- Main HttpTextClient erases non2xx/exceptions into null; legacy/shared provider null cannot prove a catalogue miss. Generic incomplete status is honest without a complete new transport evidence framework.
- Canonical `MusicFeatureEngine.kt:44` installs an onLyricsDiscovered writer; its prefetch `:511` saves complete cache entities. Canonical FloatingLyricsService `:238-255` independently fetches and saves, so foreground deduplication without automatic-caller participation is insufficient.
- Main `shared/src/commonMain/kotlin/com/lirix/app/storage/LyricsDao.kt:40-45` has saveAutomaticIfNotUser, but canonical has only saveLyrics at:29. Main helper protects only manual-provider replacement and then replaces the whole automatic row. The new fill-missing transaction must be separately added to canonical; do not transplant the foreign main file or change existing helper semantics wholesale.

## Required correctness amendments

### R1 — notes cannot satisfy the lyric/chord target or suppress recovery

Canonical `LyricsProvider.kt:78-85` OfflineLyricsAdapter returns hasLyrics=true, lyricsText/plainLyrics=userNotes, isUserNote=true, USER_NOTE source. Aggregator notes shortcut at:969-973 returns it before network. Current spec says a positive result wins/accepted nonblank fields are Available, but never defines exclusion of this fallback. Merely preserving notes in the merge is insufficient: a note-only track can be classified successful and its note copied into cached lyrics instead of fetching actual lyrics.

Specify a target-success predicate: lyrics success requires actual lyric content with `isUserNote != true` and source not USER_NOTE/NONE; chords success requires actual chord content or existing recognized inline chords, independent of hasLyrics. Automatic missing-target requests must bypass the USER_NOTE shortcut while leaving original notes untouched and retaining the legacy getLyrics behavior for callers outside this scoped automatic path. Test a note-only row through real aggregator control flow, not just a fake finished coordinator result: offline -> online yields actual lyrics, original notes exact, no note-as-lyrics persistence. Include chords+notes case.

### R2 — rejection/freshness authority must be checked inside the write transaction

Spec:61 says fresh row transaction and respect rejections before commit. Make rejection check explicitly part of the **same Room transaction** as cache/music_tracks fill-missing writes, alongside manual-provider/current-field checks. Checking rejections just outside the transaction leaves a reject-during-completion race. A delayed job may persist under its own old track key only if it remains authorized for that key; UI generation validity is separate from database source authority. Test source rejection added while HTTP suspended, then finish HTTP, assert no new rejected fields in either table.

Attribution merge remains per filled field: keep existing provenance for untouched fields; never assign a new overall provider/source attribution to retained lyrics merely because chords were filled. Correction after direct entity inspection: sourceAttributionJson already exists in canonical and HEAD3b25fb3 MusicEntities.kt:92 as well as main. Preserve existing JSON in both checkouts; no schema change is needed. The earlier schema-difference assertion was incorrect.

## Minimum agreed scope, avoiding a retry framework

Keep the following necessary mechanisms:

1. One foreground-lifecycle application-context connectivity flow with initial snapshot and safe default callback cleanup. Use active validated Internet and observed false->true opportunity for the same open unresolved track; capability duplicates do not produce work. Already-open cellular allowed.
2. Small current-track/current-target state and one recovery opportunity per connectivity generation, plus explicit coalesced Retry. Keep pending recovery if network returns during the original attempt. No retries for successful content, no spin on steady connectivity, no state publication for obsolete key/generation.
3. One application-owned keyed in-flight gate used by the three actual automatic callers (foreground, Engine prefetch, floating overlay), re-reading cache/rejections when entering. Reuse/await the active lyric operation that already includes chords; only start a missing-chord request if its unresolved target remains after that result. Define this join rule explicitly instead of the ambiguous combination of a target-keyed registry and mandatory cross-target joining in spec:46.
4. One fresh transactional fill-missing persistence path for participating automatic calls; remove/bypass their provider side-effect callback. Preserve existing fields/notes/manual content and dual-write only newly filled lyric fields. Room observation supplies concurrent writes to the current screen without network.
5. One compact retry/status action in actual unresolved lyric/chord views, correct RU/EN resources, reachable fonts/orientations, existing Teach separate. Notes/no-track/satisfied view has no Retry. Keep useful content displayed.

Simplify these proposed extras unless a concrete test proves necessity:

- Full per-provider evidence taxonomy and seven failure phases are unnecessary to deliver honest offline recovery. Minimum: found target, waiting for observed unavailable network, unresolved/incomplete; distinguish transport/source errors only where evidence is already directly available. A nullable legacy miss remains unresolved, never ConfirmedNotFound. New ConfirmedNotFound can be omitted rather than proving every fallback/custom backend absent. Do not refactor every parser/transport to make this state reachable.
- Socket timeout does not prove device connectivity loss; do not turn all transport IO into an auto-recovery cause. Observed monitor outage suffices for the user reproduction, while explicit Retry covers unknown source failures. Retain directly available transport evidence if cheap, but not as a prerequisite for this task.
- Reference-counted subscriber cancellation and immediate actual-HTTP close on last subscriber are optional complexity. A finite-timeout shared application job can finish under its own key and remove its in-flight entry in finally, while UI subscriptions cancel and never publish stale results. Keep no Activity references/unbounded retained jobs. If immediate HTTP cancellation remains mandatory, justify it with an existing API and bounded design; HttpURLConnection blocking IO does not become promptly cancellable simply by rethrowing CancellationException.
- Source cooldown deadline scheduling is explicitly excluded; preserve existing cooldowns and disclose unknown/unavailable results. Do not add a new scheduler or wipe source caches.

These reductions retain user guarantees and deduplication/data safety while avoiding a general provider-health, subscriber-lifetime or background-job framework. They do not postpone the requested missing-chord retry, cellular support, truthful error display or race protections.

## Appropriate acceptance after revision

Retain real Room fill-missing/concurrent-manual/rejection tests; controlled true provider network/cancellation/count oracles; target separation; initial offline + actual validated connection without track change;100 duplicate callbacks; A->B stale result; concurrent automatic callers; manual repeated taps; Notes/drafts/provenance preservation; actual native Retry -> controlled content, RU/EN font2 both orientations. A missing network snapshot must not be labelled confirmed catalogue absence.

The large loopback HTTP classification matrix is required only for status classification actually implemented; do not add transport-refactoring tests merely to justify unnecessary interfaces. Keep actual OS both-radios offline/online evidence and disclose emulator versus phone limits. Baseline diff against clean canonical plus foreign-main preservation review remains mandatory. No other backlog/studio/translation work is authorized by this review.


## Revision2 DoR re-review

**FAIL pending three narrow contract clarifications; the reduced implementation scope itself is approved.** Revision2 adequately resolves R1 note fallback, R2 same-transaction authority, mobile foreground policy, UI generation/pending recovery, lifecycle re-entry, and truthful unresolved status. No requested offline->online or Retry behaviour was deferred. The fresh-cache/rejection queries and two-table writes are implementable as a Room default @Transaction method with minimal DAO queries, without schema migration. Finite shared application jobs and cancelled UI observations are reasonable; the90s logical deadline must propagate cancellation between provider operations, while existing blocking IO may take its existing timeout to unwind, as disclosed.

Before declaring READY_FOR_IMPLEMENTATION, address:

1. **Misses must consume served coverage.** Section5's cache recheck cannot alone deduplicate unsuccessful follow-ups: after MISSING_CHORDS finishes null, the cache is still missing and each waiting subscriber could launch another chord job. State explicitly that awaiting a covering operation consumes that caller's attempt coverage even on null/error/timeout. CHORDS callers waiting on a lyric+enrichment operation may join/create at most one missing-chord follow-up; awaiting that follow-up then terminates this trigger's attempt even on miss. Multiple waiters join the same follow-up; they do not reacquire merely because the completed entry was removed. A new manual tap or new eligible network generation is the next permitted attempt. Add adversarial three-waiter all-miss count assertion (one lyric chain + at most one chord follow-up), not only positive Room-cached success. Same rule applies to initial MISSING_CHORDS followed by one genuinely missing-lyrics operation.

2. **Explicit field-origin candidate.** Section6 requires checking actual originating source, but existing canonical LyricsResult:24-32 has one sourceId and fetchChordTabs:1056-1088 returns String? and discards AmDm/MyChords origin. Add a narrowly scoped automatic acquisition result/candidate carrying origin for each newly acquired lyric/synced/chord field, with source IDs preserved through enrichment. Do not authorize chords using the parent LRCLIB lyric source. Unknown origin cannot be falsely labelled a known authorized source. This can remain internal to the automatic path without changing the legacy public nullable provider API. Test LRCLIB lyrics + AmDm chords, reject AmDm during suspension: retain permitted lyric fields, reject chord field in both actual cache/provenance, and do not discard otherwise permitted content.

3. **Correct canonical schema fact.** Section6 currently says canonical lacks sourceAttributionJson. Actual canonical and clean HEAD3b25fb3 `shared/src/commonMain/kotlin/com/lirix/app/storage/MusicEntities.kt:92` already contain it (nullable column with NULL default). This review's first version repeated that inaccurate claim; direct entity/git-show inspection corrects it. No schema addition is needed. Preserve existing JSON in canonical as well as main; add only the required field-fill attribution merge path/helper if a newer helper exists only in main. Do not silently strip preexisting provenance from canonical fixtures/rows.

Once these three points are explicit, no remaining design blocker is identified; no new framework is requested. Product implementation is still not authorized by this reviewer while the spec remains ambiguous on negative-result deduplication and field origins.


## Revision3 final DoR gate

**PASS / READY_FOR_IMPLEMENTATION for this bounded offline-lookup-recovery task.** Re-read final revision3 intake/spec, restricted to previously identified clarifications and deadline wording. This supersedes the revision2 DoR FAIL; it does not claim implementation/runtime acceptance.

- Spec§5 now makes served coverage terminal even for null/miss/error/timeout, keeps a parent-scoped shared follow-up handle, forbids recursive opposite follow-ups/reacquisition after coverage exhaustion, and retains handles only through current waiters. The explicit acquireOrJoin/awaitTargetAttempt signatures and §9B three-waiter all-miss/reverse-order oracle address the identified negative-result deduplication hole.
- Spec§6 now defines AutomaticLookupField per-field actual sourceId/url/author, AutomaticLookupCandidate and side-effect-free automatic acquisition/chord helper. Unknown/blank/USER_NOTE/NONE origins cannot be silently relabelled; chord-origin rejection is checked in the same Room merge transaction as both-table writes. §9C includes LRCLIB words+AmDm/MyChords rejection races. Notes cannot enter candidate payload or satisfy lyrics. Canonical+main existing sourceAttributionJson is correctly preserved without schema migration.
- Spec§5 now calls90s a logical coroutine deadline, explicitly permits existing bounded blocking IO unwind, requires ensureActive before every subsequent provider/source and immediately before guarded commit, and disclaims hard physical90s completion/socket-close promises. This is implementable with existing coroutine/HTTP primitives without a cancellation framework. Tests must demonstrate timeout cannot progress to another source/commit; finite logical deadline is not a licence to swallow cancellation.
- Previously approved user behaviour remains intact: same-song offline->online recovery including cellular, manual Retry, pending reconnection during Loading and lifecycle re-entry, missing chords with visible cached lyrics, truthful unresolved status, current-key UI identity, shared automatic caller acquisition, fresh guarded fill-missing persistence and foreign-main preservation. No requested behaviour is deferred by the scope reduction.

Room queries/default @Transaction method and existing two tables support the stated atomic checks; practical implementation correctness still requires the specified real Room races and data assertions. Root OS radio-disabled/restored baseline is preparation evidence only, not proof that the eventual app lookup recovery works. Product implementation may now begin sequentially within this reviewed inventory; no other backlog task is authorized by this gate.
