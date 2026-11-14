package com.lirix.app.feature.metadata

import android.content.Context
import android.content.SharedPreferences
import com.lirix.app.feature.lyrics.AutomaticLookupEligibility
import com.lirix.app.feature.lyrics.LookupNetworkMonitor
import com.lirix.app.feature.lyrics.LyricsNetworkPolicy
import com.lirix.app.feature.lyrics.NetworkConditionManager
import com.lirix.app.metadata.MetadataOutcome
import com.lirix.app.metadata.ResolvedMetadata
import com.lirix.app.metadata.TrackMetadataResolver
import com.lirix.app.metadata.TrackQuery
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.TrackEntity
import com.lirix.app.storage.TrackMetadataDao
import com.lirix.app.storage.TrackMetadataEntity
import com.lirix.app.storage.TrackMetadataStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * User consent and the optional personal Last.fm key for track metadata.
 * Off until the user turns it on: a lookup tells outside services what is being played.
 */
class TrackMetadataSettings(private val preferences: SharedPreferences) {
    private val _enabled = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled

    fun setEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }

    var lastFmKey: String
        get() = preferences.getString(KEY_LASTFM, "").orEmpty()
        set(value) {
            val clean = value.trim()
            require(clean.isEmpty() || TrackMetadataResolver.lastFmKeyPattern.matches(clean)) { "Invalid Last.fm key" }
            preferences.edit().putString(KEY_LASTFM, clean).apply()
        }

    companion object {
        const val PREFS_NAME = "lirix_track_metadata"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LASTFM = "lastfm_key"
        @Volatile private var instance: TrackMetadataSettings? = null

        /** One instance per process: the switch in Settings must reach the loader at once. */
        fun of(context: Context): TrackMetadataSettings = instance ?: synchronized(this) {
            instance ?: TrackMetadataSettings(context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)).also { instance = it }
        }
    }
}

/**
 * Loads metadata for the track that is actually being listened to.
 * Spec: .sdd/specs/media-core/track_metadata_and_avatar_v1.md, section "Загрузка".
 *
 * One track at a time, only after it has played for [listenDelayMs], at most [maxPerHour] lookups
 * an hour, never for the whole library. A stored answer is final for a found song; "not found"
 * and "ambiguous" are asked again after [retryAfterMs]. A source failure stores nothing.
 */
class TrackMetadataCoordinator(
    private val dao: TrackMetadataDao,
    private val enabled: () -> Boolean,
    private val lastFmKey: () -> String,
    private val resolve: suspend (TrackQuery, String?) -> MetadataOutcome,
    private val clock: () -> Long = System::currentTimeMillis,
    private val listenDelayMs: Long = 20_000L,
    private val maxPerHour: Int = 30,
    private val retryAfterMs: Long = 7L * 24 * 60 * 60 * 1000
) {
    private val recent = ArrayDeque<Long>()

    /** Returns the stored row when a lookup was made and answered, null when it was skipped or failed. */
    suspend fun lookup(track: TrackEntity, durationMs: Long, networkAllowed: Boolean): TrackMetadataEntity? {
        if (!enabled() || !networkAllowed) return null
        // "09 / Unknown Artist" cannot identify a song (AGENTS 7.4).
        if (track.title.isBlank() || track.artist.isBlank() || !AutomaticLookupEligibility.isEligible(track.title, track.artist)) return null
        if (com.lirix.app.analytics.WrappedStatsEngine.isUnknownArtist(track.artist)) return null
        val stored = dao.get(track.trackKey)
        if (stored != null && (stored.status == TrackMetadataStatus.FOUND.name || clock() - stored.fetchedAt < retryAfterMs)) return null
        if (!takeBudget()) return null
        val outcome = resolve(TrackQuery(track.title.trim(), track.artist.trim(), durationMs.coerceAtLeast(0)), lastFmKey().takeIf { it.isNotBlank() })
        // Consent may have been withdrawn while the request was in flight: do not keep its result.
        if (!enabled()) return null
        val entity = when (outcome) {
            is MetadataOutcome.Found -> outcome.metadata.toEntity(track.trackKey, clock())
            MetadataOutcome.NotFound -> TrackMetadataEntity(track.trackKey, TrackMetadataStatus.NOT_FOUND.name, fetchedAt = clock())
            MetadataOutcome.Ambiguous -> TrackMetadataEntity(track.trackKey, TrackMetadataStatus.AMBIGUOUS.name, fetchedAt = clock())
            MetadataOutcome.Unavailable -> return null
        }
        dao.save(entity)
        return entity
    }

    @Synchronized
    private fun takeBudget(): Boolean {
        val now = clock()
        while (recent.isNotEmpty() && now - recent.first() >= 60 * 60_000L) recent.removeFirst()
        if (recent.size >= maxPerHour) return false
        recent.addLast(now)
        return true
    }

    private var pending: Job? = null

    /** Called when a track starts playing; a newer track cancels the wait of the previous one. */
    @Synchronized
    fun onTrackPlaying(context: Context, scope: CoroutineScope, track: TrackEntity, durationMs: Long) {
        pending?.cancel()
        if (!enabled()) return
        val appContext = context.applicationContext
        pending = scope.launch(Dispatchers.IO) {
            try {
                delay(listenDelayMs)
                if (NetworkConditionManager.getNetworkPolicy(appContext) == LyricsNetworkPolicy.OFFLINE_ONLY) return@launch
                lookup(track, durationMs, LookupNetworkMonitor.usable(appContext))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Timber.tag("TrackMetadata").w("Metadata lookup failed: %s", e.javaClass.simpleName) }
        }
    }

    companion object {
        @Volatile private var instance: TrackMetadataCoordinator? = null

        fun get(context: Context): TrackMetadataCoordinator = instance ?: synchronized(this) {
            instance ?: run {
                val settings = TrackMetadataSettings.of(context)
                val resolver = TrackMetadataResolver()
                TrackMetadataCoordinator(
                    dao = AppDatabase.getInstance(context.applicationContext).trackMetadataDao(),
                    enabled = { settings.enabled.value },
                    lastFmKey = { settings.lastFmKey },
                    resolve = { query, key -> resolver.resolve(query, key) }
                ).also { instance = it }
            }
        }

        fun ResolvedMetadata.toEntity(trackKey: String, now: Long) = TrackMetadataEntity(
            trackKey = trackKey, status = TrackMetadataStatus.FOUND.name,
            genres = TrackMetadataEntity.join(genres), releaseYear = releaseYear, durationMs = durationMs, isrc = isrc,
            bpm = features?.bpm, musicalKey = features?.musicalKey, musicalMode = features?.musicalMode,
            energy = features?.energy, valence = features?.valence, danceability = features?.danceability,
            acousticness = features?.acousticness, instrumentalness = features?.instrumentalness,
            liveness = features?.liveness, speechiness = features?.speechiness, loudnessDb = features?.loudnessDb,
            tags = TrackMetadataEntity.join(tags),
            sources = sources.entries.joinToString(";") { "${it.key}=${it.value}" }, fetchedAt = now
        )
    }
}
