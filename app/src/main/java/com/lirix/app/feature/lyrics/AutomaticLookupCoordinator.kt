package com.lirix.app.feature.lyrics

import com.lirix.app.feature.AggregatedLyricsProvider
import com.lirix.app.feature.lyrics.AmDmChordParser
import com.lirix.app.storage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

enum class LookupTarget { LYRICS, CHORDS }
enum class LookupPhase { Loading, Available, WaitingForNetwork, Incomplete }
enum class LookupOperation { LYRICS_WITH_CHORD_ENRICHMENT, MISSING_CHORDS }
data class LookupNetworkSnapshot(val usable: Boolean, val generation: Long)
data class AutomaticAttemptResult(val candidate: AutomaticLookupCandidate, val servedOperation: LookupOperation, val timedOut: Boolean = false)
internal class AutomaticAttemptHandle(val id: Long, val operation: LookupOperation,
    val completion: Deferred<AutomaticAttemptResult>,
    val followUps: MutableMap<LookupOperation, Deferred<AutomaticAttemptResult>> = mutableMapOf())

interface AutomaticLookupStore {
    suspend fun track(key: String): TrackEntity?
    suspend fun cache(key: String): LyricsCacheEntity?
    suspend fun rejected(key: String): Set<String>
    suspend fun merge(key: String, candidate: AutomaticLookupCandidate): LyricsCacheEntity?
}

/** No notes/unknown source can satisfy the automatic target. */
fun automaticLookupSatisfied(target: LookupTarget, track: TrackEntity?, cache: LyricsCacheEntity?): Boolean {
    val note = cache?.provider == "note:user"
    val plain = cache?.plainLyrics.takeUnless { note }?.takeIf { it.isNotBlank() } ?: automaticLookupAcceptedTrackField(track?.plainLyrics, track, cache)
    val synced = cache?.syncedLyricsLrc.takeUnless { note }?.takeIf { it.isNotBlank() } ?: automaticLookupAcceptedTrackField(track?.syncedLyrics, track, cache)
    return when (target) {
        LookupTarget.LYRICS -> !plain.isNullOrBlank() || !synced.isNullOrBlank()
        LookupTarget.CHORDS -> !cache?.chordsAmDm.isNullOrBlank() || !AmDmChordParser.detectAndFormatInlineChords(plain.orEmpty()).isNullOrBlank()
    }
}

/** Executing entries only. Parent handles, not the registry, retain completed attempt coverage. */
class AutomaticLookupCoordinator(
    private val store: AutomaticLookupStore,
    private val acquire: suspend (TrackEntity, LookupTarget, Set<String>) -> AutomaticLookupCandidate,
    private val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val deadlineMs: Long = 90_000L
) {
    private data class Key(val trackKey: String, val rejected: Set<String>)
    private val mutex = Mutex()
    private val jobs = mutableMapOf<Key, AutomaticAttemptHandle>()
    private val sequence = AtomicLong()

    suspend fun satisfied(key: String, target: LookupTarget): Boolean =
        automaticLookupSatisfied(target, store.track(key), store.cache(key))

    internal suspend fun acquireOrJoin(trackKey: String, rejected: Set<String>, operation: LookupOperation): AutomaticAttemptHandle = mutex.withLock {
        val key = Key(trackKey, rejected.toSet())
        jobs[key]?.let { return@withLock it }
        val id = sequence.incrementAndGet()
        val deferred = applicationScope.async(start = CoroutineStart.LAZY) {
            try { runOperation(trackKey, rejected, operation) }
            finally { withContext(NonCancellable) { mutex.withLock { if (jobs[key]?.id == id) jobs.remove(key) } } }
        }
        AutomaticAttemptHandle(id, operation, deferred).also { jobs[key] = it; deferred.start() }
    }

    private suspend fun runOperation(key: String, rejectedSnapshot: Set<String>, operation: LookupOperation): AutomaticAttemptResult {
        val target = if (operation == LookupOperation.MISSING_CHORDS) LookupTarget.CHORDS else LookupTarget.LYRICS
        val result = try { withTimeoutOrNull(deadlineMs) {
            currentCoroutineContext().ensureActive()
            val track = store.track(key) ?: return@withTimeoutOrNull AutomaticLookupCandidate()
            val cache = store.cache(key)
            val provider = cache?.provider.orEmpty()
            if (provider.equals("USER", true) || provider.startsWith("USER:", true) || automaticLookupSatisfied(target, track, cache))
                return@withTimeoutOrNull AutomaticLookupCandidate()
            val rejected = store.rejected(key)
            if (rejected != rejectedSnapshot) return@withTimeoutOrNull AutomaticLookupCandidate()
            val candidate = acquire(track, target, rejected)
            currentCoroutineContext().ensureActive()
            store.merge(key, candidate)
            candidate
        } } catch (e: CancellationException) { throw e }
        catch (_: Exception) { AutomaticLookupCandidate() }
        return AutomaticAttemptResult(result ?: AutomaticLookupCandidate(), operation, result == null)
    }

    internal suspend fun awaitTargetAttempt(parent: AutomaticAttemptHandle, trackKey: String, rejected: Set<String>, target: LookupTarget): AutomaticAttemptResult {
        val served = parent.completion.await()
        if (satisfied(trackKey, target)) return served
        val needed = if (target == LookupTarget.CHORDS) LookupOperation.MISSING_CHORDS else LookupOperation.LYRICS_WITH_CHORD_ENRICHMENT
        if (parent.operation == needed) return served // Miss/timeout also consumes covering operation.
        val follow = mutex.withLock {
            parent.followUps.getOrPut(needed) {
                applicationScope.async(start = CoroutineStart.LAZY) {
                    // Join any intervening work, but never recurse after this one follow-up.
                    withTimeoutOrNull(deadlineMs) {
                        var handle = acquireOrJoin(trackKey, rejected, needed)
                        var result = handle.completion.await()
                        while (handle.operation != needed && !satisfied(trackKey, target)) {
                            currentCoroutineContext().ensureActive()
                            handle = acquireOrJoin(trackKey, rejected, needed)
                            result = handle.completion.await()
                        }
                        result
                    } ?: AutomaticAttemptResult(AutomaticLookupCandidate(), needed, timedOut = true)
                }.also { it.start() }
            }
        }
        return follow.await()
    }

    suspend fun lookup(key: String, target: LookupTarget, usable: Boolean): LookupPhase {
        if (satisfied(key, target)) return LookupPhase.Available
        if (!usable) return LookupPhase.WaitingForNetwork
        // Awaiting an application-owned Deferred is cancellable independently of blocking provider unwind.
        // A subscriber deadline never cancels or removes the shared job/coverage handle.
        return withTimeoutOrNull(deadlineMs) {
            val rejected = store.rejected(key)
            val operation = if (target == LookupTarget.CHORDS) LookupOperation.MISSING_CHORDS else LookupOperation.LYRICS_WITH_CHORD_ENRICHMENT
            val handle = acquireOrJoin(key, rejected, operation)
            awaitTargetAttempt(handle, key, rejected, target)
            if (satisfied(key, target)) LookupPhase.Available else LookupPhase.Incomplete
        } ?: LookupPhase.Incomplete
    }

    companion object {
        private val instances = java.util.WeakHashMap<AppDatabase, AutomaticLookupCoordinator>()
        @Synchronized fun get(db: AppDatabase): AutomaticLookupCoordinator = instances.getOrPut(db) {
            AutomaticLookupCoordinator(object : AutomaticLookupStore {
                override suspend fun track(key: String) = db.musicDao().getTrackByKey(key)
                override suspend fun cache(key: String) = db.lyricsDao().getLyrics(key)
                override suspend fun rejected(key: String) = db.lyricsDao().getRejectedSourceIds(key).toSet()
                override suspend fun merge(key: String, candidate: AutomaticLookupCandidate) = db.lyricsDao().fillMissingAutomaticLookup(key, candidate)
            }, { track, target, rejected -> AggregatedLyricsProvider(lyricsDao = db.lyricsDao()).acquireAutomatic(track, target, rejected) })
        }
    }
}

/** Foreground identity is independent of shared job lifetime. No Activity/View is retained. */
class ForegroundLookupRecovery(
    private val key: String,
    private val target: LookupTarget,
    private val coordinator: AutomaticLookupCoordinator,
    private val scope: CoroutineScope
) {
    private val mutablePhase = MutableStateFlow(LookupPhase.Loading)
    val phase: StateFlow<LookupPhase> = mutablePhase.asStateFlow()
    private var network = LookupNetworkSnapshot(false, 0)
    private var initialized = false
    private var outage = false
    private var consumed = Long.MIN_VALUE
    private var pending: Long? = null
    private var running: Job? = null
    private var generation = 0L
    private var disposed = false

    fun onNetwork(snapshot: LookupNetworkSnapshot) {
        if (disposed) return
        network = snapshot
        if (!snapshot.usable) outage = true
        if (!initialized) { initialized = true; launchAttempt(); return }
        if (snapshot.usable && outage && snapshot.generation != consumed) {
            if (running?.isActive == true) pending = snapshot.generation
            else { consumed = snapshot.generation; outage = false; launchAttempt() }
        }
    }
    fun retry() { if (!disposed && running?.isActive != true) launchAttempt() }
    fun contentAvailable() { if (!disposed) { mutablePhase.value = LookupPhase.Available; pending = null; outage = false } }
    fun dispose() { disposed = true; generation++; running?.cancel(); pending = null }

    private fun launchAttempt() {
        val token = ++generation
        val usable = network.usable
        mutablePhase.value = LookupPhase.Loading
        running = scope.launch {
            val acquiredOutcome = try { coordinator.lookup(key, target, usable) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { if (network.usable) LookupPhase.Incomplete else LookupPhase.WaitingForNetwork }
            if (disposed || token != generation) return@launch
            val outcome = if (acquiredOutcome != LookupPhase.Available && !network.usable)
                LookupPhase.WaitingForNetwork else acquiredOutcome
            mutablePhase.value = outcome
            val opportunity = pending
            pending = null
            if (outcome == LookupPhase.Available) outage = false
            running = null
            if (outcome != LookupPhase.Available && network.usable && outage && opportunity != null && opportunity != consumed) {
                consumed = opportunity; outage = false; launchAttempt()
            }
        }
    }
}
