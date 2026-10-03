package com.example.npc.ingest.media.recovery

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.npc.core.model.Event
import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.ThreadKey
import com.example.npc.core.model.normalize.EventNormalizer
import com.example.npc.core.storage.StorageGateway
import com.example.npc.ingest.media.mapper.MediaPayloadMapper
import com.example.npc.ingest.media.model.ActiveMediaSession
import com.example.npc.ingest.media.model.MediaMetadataSnapshot
import com.example.npc.ingest.media.model.MediaSessionEndReason
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MediaSessionRecoveryManagerTest {

    private val storageGateway: StorageGateway = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var dataStore: InMemoryDataStore
    private lateinit var recoveryManager: MediaSessionRecoveryManager

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0

        dataStore = InMemoryDataStore()
        recoveryManager = MediaSessionRecoveryManager(
            dataStore = dataStore,
            storageGateway = storageGateway,
            ioDispatcher = testDispatcher
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `saveActiveSessionSnapshot saves json snapshot in DataStore under package key`() = runTest {
        val start = Instant.parse("2026-09-26T10:00:00Z")
        val session = ActiveMediaSession(
            sessionId = "com.spotify.music:1790416800000",
            packageName = "com.spotify.music",
            metadata = MediaMetadataSnapshot(
                title = "Blinding Lights",
                artist = "The Weeknd",
                album = "After Hours",
                durationMs = 200_000L
            ),
            sessionStartedAt = start,
            lastActivePlayStartedAt = start,
            accumulatedPlayTimeMs = 0L,
            lastState = 3,
            lastEventAt = start
        )

        recoveryManager.saveActiveSessionSnapshot(session)

        val prefs = dataStore.data.first()
        val key = stringPreferencesKey("active_session_com.spotify.music")
        val savedJson = prefs[key]

        savedJson.shouldNotBeNull()
        savedJson.contains("\"packageName\":\"com.spotify.music\"") shouldBe true
        savedJson.contains("\"sessionId\":\"com.spotify.music:1790416800000\"") shouldBe true
        savedJson.contains("\"trackTitle\":\"Blinding Lights\"") shouldBe true
        savedJson.contains("\"artist\":\"The Weeknd\"") shouldBe true
        savedJson.contains("\"album\":\"After Hours\"") shouldBe true
        savedJson.contains("\"lastState\":3") shouldBe true
    }

    @Test
    fun `clearActiveSessionSnapshot removes snapshot for package name`() = runTest {
        val key = stringPreferencesKey("active_session_com.spotify.music")
        dataStore.edit { prefs ->
            prefs[key] = "{\"packageName\":\"com.spotify.music\"}"
        }

        dataStore.data.first()[key].shouldNotBeNull()

        recoveryManager.clearActiveSessionSnapshot("com.spotify.music")

        dataStore.data.first()[key].shouldBeNull()
    }

    @Test
    fun `recoverDanglingSessions recovers session with duration at least 5000ms and inserts RawEvent and Event`() = runTest {
        val tStart = Instant.parse("2026-09-26T10:00:00Z")
        val tLastEvent = tStart.plusSeconds(30) // Played for 30s before process kill
        val appStartedAt = Instant.parse("2026-09-26T10:05:00Z") // App restarted 5 min later

        val session = ActiveMediaSession(
            sessionId = "com.spotify.music:1790416800000",
            packageName = "com.spotify.music",
            metadata = MediaMetadataSnapshot(
                title = "Starboy",
                artist = "The Weeknd",
                album = "Starboy",
                durationMs = 230_000L
            ),
            sessionStartedAt = tStart,
            lastActivePlayStartedAt = tStart,
            accumulatedPlayTimeMs = 0L,
            lastState = 3,
            lastEventAt = tLastEvent
        )
        recoveryManager.saveActiveSessionSnapshot(session)

        val rawEventSlot = slot<RawEvent>()
        val domainEventSlot = slot<Event>()

        coEvery { storageGateway.insertRawEvent(capture(rawEventSlot)) } returns 101L
        coEvery { storageGateway.insertEvent(capture(domainEventSlot)) } returns 201L

        val recoveredCount = recoveryManager.recoverDanglingSessions(appStartedAt)

        recoveredCount shouldBe 1

        // Verify RawEvent
        coVerify(exactly = 1) { storageGateway.insertRawEvent(any()) }
        val raw = rawEventSlot.captured
        raw.source shouldBe SourceId.MEDIA
        raw.packageName shouldBe "com.spotify.music"
        raw.receivedAt shouldBe appStartedAt
        raw.seq shouldBe 0L

        val expectedDedupKey = EventNormalizer.computeDeduplicationKey(
            SourceId.MEDIA,
            "com.spotify.music",
            raw.payloadJson
        )
        raw.hash shouldBe expectedDedupKey

        val payload = MediaPayloadMapper.fromPayloadJson(raw.payloadJson)
        payload.packageName shouldBe "com.spotify.music"
        payload.trackTitle shouldBe "Starboy"
        payload.artist shouldBe "The Weeknd"
        payload.sessionStartedAtEpochMs shouldBe tStart.toEpochMilli()
        payload.sessionEndedAtEpochMs shouldBe appStartedAt.toEpochMilli()
        payload.effectiveDurationMs shouldBe 30_000L
        payload.isMicroSession shouldBe false
        payload.endReason shouldBe MediaSessionEndReason.APP_RESTART
        payload.lastError shouldBe "process_killed_unexpectedly"

        // Verify domain Event
        coVerify(exactly = 1) { storageGateway.insertEvent(any()) }
        val event = domainEventSlot.captured
        event.rawId shouldBe 101L
        event.title shouldBe "Starboy"
        event.text shouldBe "The Weeknd - Starboy"
        event.threadKey shouldBe ThreadKey("media:com.spotify.music")

        // Verify DataStore entry removed
        val key = stringPreferencesKey("active_session_com.spotify.music")
        dataStore.data.first()[key].shouldBeNull()

        // Verify SourceHealth updated
        coVerify(atLeast = 1) {
            storageGateway.upsertSourceHealth(
                match { health ->
                    health.source == SourceId.MEDIA &&
                        health.lastEventAt == appStartedAt &&
                        health.events24h == 1 &&
                        health.lastError == "recovered 1 dangling session(s) after APP_RESTART"
                }
            )
        }
    }

    @Test
    fun `recoverDanglingSessions skips Event insertion for recovered micro-session`() = runTest {
        val tStart = Instant.parse("2026-09-26T10:00:00Z")
        val tLastEvent = tStart.plusSeconds(3) // Played for only 3 seconds before crash
        val appStartedAt = Instant.parse("2026-09-26T10:01:00Z")

        val session = ActiveMediaSession(
            sessionId = "com.spotify.music:1790416800000",
            packageName = "com.spotify.music",
            metadata = MediaMetadataSnapshot(
                title = "Quick Skip",
                artist = "Artist",
                album = "Album",
                durationMs = 180_000L
            ),
            sessionStartedAt = tStart,
            lastActivePlayStartedAt = tStart,
            accumulatedPlayTimeMs = 0L,
            lastState = 3,
            lastEventAt = tLastEvent
        )
        recoveryManager.saveActiveSessionSnapshot(session)

        coEvery { storageGateway.insertRawEvent(any()) } returns 102L

        val recoveredCount = recoveryManager.recoverDanglingSessions(appStartedAt)

        recoveredCount shouldBe 1
        coVerify(exactly = 1) { storageGateway.insertRawEvent(any()) }
        // For micro-sessions (< 5000ms), domain Event must NOT be inserted
        coVerify(exactly = 0) { storageGateway.insertEvent(any()) }

        // DataStore snapshot must still be cleaned up
        val key = stringPreferencesKey("active_session_com.spotify.music")
        dataStore.data.first()[key].shouldBeNull()
    }

    @Test
    fun `recoverDanglingSessions returns zero when DataStore has no dangling sessions`() = runTest {
        val appStartedAt = Instant.now()
        val count = recoveryManager.recoverDanglingSessions(appStartedAt)

        count shouldBe 0
        coVerify(exactly = 0) { storageGateway.insertRawEvent(any()) }
        coVerify(exactly = 0) { storageGateway.insertEvent(any()) }
    }

    @Test
    fun `recoverDanglingSessions deletes corrupt json snapshot and returns zero without crashing`() = runTest {
        val corruptKey = stringPreferencesKey("active_session_corrupt.package")
        dataStore.edit { prefs ->
            prefs[corruptKey] = "{ this is not valid json : [[["
        }

        val appStartedAt = Instant.now()
        val count = recoveryManager.recoverDanglingSessions(appStartedAt)

        count shouldBe 0
        coVerify(exactly = 0) { storageGateway.insertRawEvent(any()) }
        coVerify(exactly = 0) { storageGateway.insertEvent(any()) }

        // Corrupt key should be removed
        dataStore.data.first()[corruptKey].shouldBeNull()
    }

    private class InMemoryDataStore(initialPreferences: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val flow = MutableStateFlow(initialPreferences)
        override val data: Flow<Preferences> = flow.asStateFlow()

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(flow.value)
            flow.value = updated
            return updated
        }
    }
}
