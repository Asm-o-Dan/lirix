package com.example.npc.ingest.sms.poller

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.normalize.EventNormalizer
import com.example.npc.core.storage.StorageGateway
import com.example.npc.ingest.sms.controller.SmsIngestController
import com.example.npc.ingest.sms.mapper.SmsMapper
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SmsPollerTest {

    private val context: Context = mockk(relaxed = true)
    private val contentResolver: ContentResolver = mockk(relaxed = true)
    private val storageGateway: StorageGateway = mockk(relaxed = true)
    private val smsIngestController: SmsIngestController = mockk(relaxed = true)

    private lateinit var poller: SmsPoller

    @BeforeEach
    fun setUp() {
        mockkStatic(Uri::class)
        mockkStatic(ContextCompat::class)
        mockkStatic(Log::class)

        val mockUri = mockk<Uri>(relaxed = true)
        every { Uri.parse(any()) } returns mockUri
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        every { context.contentResolver } returns contentResolver
        every { ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) } returns PackageManager.PERMISSION_GRANTED
        every { smsIngestController.nextSeq() } returns 1L
        every { smsIngestController.queueDepth } returns 0

        poller = SmsPoller(
            context = context,
            storageGateway = storageGateway,
            smsIngestController = smsIngestController
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `pollNewMessages returns 0 when READ_SMS permission is not granted`() = runTest {
        every { ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) } returns PackageManager.PERMISSION_DENIED

        val imported = poller.pollNewMessages(sinceTimestamp = 1_000L)

        imported shouldBe 0
        coVerify(exactly = 0) { contentResolver.query(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `pollNewMessages_returnZeroWhenNoDuplicateKeyConflicts when cursor is empty`() = runTest {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToNext() } returns false
        every {
            contentResolver.query(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns cursor

        val imported = poller.pollNewMessages(sinceTimestamp = 1_774_567_000_000L)

        imported shouldBe 0
        coVerify(exactly = 0) { storageGateway.insertRawEvent(any()) }
        coVerify(exactly = 0) { storageGateway.insertEvent(any()) }
    }

    @Test
    fun `pollNewMessages_skipsDuplicates when findDuplicate returns existing id`() = runTest {
        val originAddress = "+79991112233"
        val body = "Уже импортированное сообщение"
        val timestamp = 1_774_567_890_000L

        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToNext() } returnsMany listOf(true, false)
        every { cursor.getColumnIndex(Telephony.Sms.ADDRESS) } returns 0
        every { cursor.getColumnIndex(Telephony.Sms.BODY) } returns 1
        every { cursor.getColumnIndex(Telephony.Sms.DATE) } returns 2
        every { cursor.isNull(any()) } returns false
        every { cursor.getString(0) } returns originAddress
        every { cursor.getString(1) } returns body
        every { cursor.getLong(2) } returns timestamp

        every {
            contentResolver.query(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns cursor

        val payloadJson = SmsMapper.toPayloadJson(
            originAddress = originAddress,
            body = body,
            timestampMillis = timestamp
        )
        val expectedDedupKey = EventNormalizer.computeDeduplicationKey(
            SourceId.SMS,
            "android.telephony.sms",
            payloadJson
        )

        coEvery { storageGateway.findDuplicate(expectedDedupKey) } returns 99L

        val imported = poller.pollNewMessages(sinceTimestamp = 1_774_567_000_000L)

        imported shouldBe 0
        coVerify(exactly = 1) { storageGateway.findDuplicate(expectedDedupKey) }
        coVerify(exactly = 0) { storageGateway.insertRawEvent(any()) }
        coVerify(exactly = 0) { storageGateway.insertEvent(any()) }
    }

    @Test
    fun `pollNewMessages_insertsNewSms when findDuplicate returns null`() = runTest {
        val originAddress = "+79995554433"
        val body = "Свежее SMS сообщение"
        val timestamp = 1_774_567_900_000L

        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToNext() } returnsMany listOf(true, false)
        every { cursor.getColumnIndex(Telephony.Sms.ADDRESS) } returns 0
        every { cursor.getColumnIndex(Telephony.Sms.BODY) } returns 1
        every { cursor.getColumnIndex(Telephony.Sms.DATE) } returns 2
        every { cursor.isNull(any()) } returns false
        every { cursor.getString(0) } returns originAddress
        every { cursor.getString(1) } returns body
        every { cursor.getLong(2) } returns timestamp

        every {
            contentResolver.query(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns cursor

        val payloadJson = SmsMapper.toPayloadJson(
            originAddress = originAddress,
            body = body,
            timestampMillis = timestamp
        )
        val expectedDedupKey = EventNormalizer.computeDeduplicationKey(
            SourceId.SMS,
            "android.telephony.sms",
            payloadJson
        )

        coEvery { storageGateway.findDuplicate(expectedDedupKey) } returns null
        coEvery { storageGateway.insertRawEvent(any()) } returns 101L
        coEvery { storageGateway.insertEvent(any()) } returns 201L

        val imported = poller.pollNewMessages(sinceTimestamp = 1_774_567_000_000L)

        imported shouldBe 1
        coVerify(exactly = 1) { storageGateway.findDuplicate(expectedDedupKey) }
        coVerify(exactly = 1) {
            storageGateway.insertRawEvent(
                match { rawEvent ->
                    rawEvent.seq == 1L &&
                        rawEvent.source == SourceId.SMS &&
                        rawEvent.packageName == "android.telephony.sms" &&
                        rawEvent.hash == expectedDedupKey
                }
            )
        }
        coVerify(exactly = 1) {
            storageGateway.insertEvent(
                match { event ->
                    event.rawId == 101L &&
                        event.title == originAddress &&
                        event.text == body
                }
            )
        }
    }

    @Test
    fun `pollNewMessages handles SecurityException and records error in SourceHealth`() = runTest {
        every {
            contentResolver.query(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } throws SecurityException("Permission denied by OS")

        coEvery { storageGateway.upsertSourceHealth(any()) } returns Unit

        val imported = poller.pollNewMessages(sinceTimestamp = 1_774_567_000_000L)

        imported shouldBe 0
        coVerify(exactly = 1) {
            storageGateway.upsertSourceHealth(
                match { health ->
                    health.source == SourceId.SMS &&
                        health.lastError == "Permission denied by OS"
                }
            )
        }
    }
}
