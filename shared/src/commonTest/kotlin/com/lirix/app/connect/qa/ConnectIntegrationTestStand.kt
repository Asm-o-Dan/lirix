package com.lirix.app.connect.qa

import com.lirix.app.connect.cast.AdaptiveJitterBuffer
import com.lirix.app.connect.cast.CastDatagram
import com.lirix.app.connect.cast.JitterBufferItem
import com.lirix.app.connect.handoff.HandoffState
import com.lirix.app.connect.handoff.HandoffStateMachine
import com.lirix.app.connect.model.Bytes
import com.lirix.app.connect.model.DeviceType
import com.lirix.app.connect.model.PairRequest
import com.lirix.app.connect.model.PairingPayload
import com.lirix.app.connect.model.asBytes
import com.lirix.app.connect.proto.DeviceRole
import com.lirix.app.connect.proto.LirixConnectMsg
import com.lirix.app.connect.proto.TrackRef
import com.lirix.app.connect.security.PairingSecurityManager
import com.lirix.app.connect.security.TrustedDevice
import com.lirix.app.connect.security.TrustedDeviceStore
import com.lirix.app.connect.security.VerificationResult
import com.lirix.app.connect.session.ConnectSessionManager
import com.lirix.app.connect.time.ClockSyncEngine
import com.lirix.app.connect.time.PlaybackPositionInterpolator
import com.lirix.app.connect.transport.ConnectState
import com.lirix.app.connect.transport.PairingExpiredException
import com.lirix.app.connect.transport.PairingPayloadCodec
import com.lirix.app.connect.transport.ReconnectPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Автоматизированный интеграционный тест-стенд CI для Lirix Connect (SPEC-CONNECT-01 §7 NFR, TASK-CON-QA-13).
 *
 * Проверяет 5 ключевых нефункциональных требований:
 * - NFR-1: Скорость Handoff в локальной сети <= 3000 мс (DoD: переход источника в SOURCE_MUTED).
 * - NFR-2: Точность синхронизации позиции караоке <= +-100 мс при сетевом джиттере и RTT до 80 мс.
 * - NFR-3: Надёжность связи и реконнект <= 10 с при смене сети (Wi-Fi <-> LTE).
 * - NFR-4: Безопасность: TTL 120 с, отсечка неверных HMAC-proof и блокировка после 3 попыток.
 * - NFR-5: Устойчивость Audio Cast к потерям 5%: генерация PLC-маркеров без срыва джиттер-буфера.
 */
class ConnectIntegrationTestStand {

    // ── NFR-1: Скорость Handoff (LAN <= 3 с) ──────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testNfr1HandoffSpeedWithin3SecondsInLan() = runTest {
        val netSimulator = NetworkAnomalySimulator(
            baseLatencyMs = 15L,
            jitterMs = 5L,
            packetLossRate = 0.0
        )

        val senderSession = ConnectSessionManager(initialRole = DeviceRole.PLAYER)
        val receiverSession = ConnectSessionManager(initialRole = DeviceRole.CONTROLLER)

        val senderFsm = HandoffStateMachine(
            scope = this,
            crossfadeDurationMs = 300L,
            sessionManager = senderSession
        )

        var simulatedTimelineMs = 0L

        // 1. Формирование HandoffRequest на телефоне
        val track = TrackRef(
            provider = "local",
            trackId = "track_nfr1",
            title = "Starboy",
            artist = "The Weeknd"
        )
        val request = LirixConnectMsg.HandoffRequest(
            sessionId = "sess_nfr_1",
            newEpoch = 1L,
            track = track,
            positionMs = 45_000L,
            positionAtMonoMs = 1000L,
            durationMs = 230_000L,
            isPlaying = true
        )

        senderFsm.initiateHandoff(request)
        assertEquals(HandoffState.REQUESTED, senderFsm.state.value)

        // 2. Передача HandoffRequest по сети на ПК
        val reqPacket = netSimulator.transmit(request)
        assertNotNull(reqPacket)
        simulatedTimelineMs += reqPacket.delayMs

        // 3. Обработка на целевом ПК: подхват трека и отправка HandoffAck
        val ack = receiverSession.handleHandoffRequest(reqPacket.payload)
        assertTrue(ack.accepted)
        assertEquals(DeviceRole.PLAYER, receiverSession.currentRole)

        // 4. Передача HandoffAck обратно на телефон
        val ackPacket = netSimulator.transmit(ack, isDownlink = true)
        assertNotNull(ackPacket)
        simulatedTimelineMs += ackPacket.delayMs

        assertTrue(senderFsm.onHandoffAck(ackPacket.payload))
        assertEquals(HandoffState.TARGET_STARTING, senderFsm.state.value)

        // 5. ПК запускает локальный плеер и шлёт RemoteStateUpdate(isPlaying=true)
        val stateUpdate = LirixConnectMsg.RemoteStateUpdate(
            epoch = 1L,
            trackId = "track_nfr1",
            trackTitle = "Starboy",
            trackArtist = "The Weeknd",
            durationMs = 230_000L,
            positionMs = 45_050L,
            positionAtMonoMs = 1050L,
            isPlaying = true
        )
        val statePacket = netSimulator.transmit(stateUpdate, isDownlink = true)
        assertNotNull(statePacket)
        simulatedTimelineMs += statePacket.delayMs

        senderFsm.onTargetStateUpdate(statePacket.payload)
        assertEquals(HandoffState.TARGET_PLAYING, senderFsm.state.value)

        // 6. Кроссфейд приглушения звука источника 300 мс
        advanceTimeBy(300L)
        runCurrent()
        simulatedTimelineMs += 300L

        // Телефон перешёл в режим Пульт ДУ (SOURCE_MUTED)
        assertEquals(HandoffState.SOURCE_MUTED, senderFsm.state.value)
        assertFalse(senderFsm.isLocalPlayback.value)
        assertEquals(DeviceRole.CONTROLLER, senderSession.currentRole)

        // Валидация NFR-1: общее время перехода в LAN <= 3000 мс
        assertTrue(
            simulatedTimelineMs <= 3000L,
            "Handoff duration $simulatedTimelineMs ms exceeded NFR-1 budget of 3000 ms"
        )
    }

    // ── NFR-2: Точность синхронизации позиции (<= +-100 мс) ───────────────────

    @Test
    fun testNfr2PositionSyncAccuracyWithin100msUnderJitterAndAsymmetry() {
        val netSimulator = NetworkAnomalySimulator(
            baseLatencyMs = 25L,
            jitterMs = 15L, // RTT до 80 мс
            isAsymmetric = true,
            downlinkMultiplier = 1.3
        )

        val syncEngine = ClockSyncEngine()
        val trueServerClockOffsetMs = 8400L // Хост спешит на 8400 мс

        // 1. Выполняем серию из 8 NTP-замеров Ping/Pong
        var clientClock = 10_000L
        for (i in 1..8) {
            val roundtrip = netSimulator.simulateNtpExchange(
                clientMonoMs = clientClock,
                trueServerClockOffsetMs = trueServerClockOffsetMs,
                serverProcessingMs = 3L
            )
            assertNotNull(roundtrip)
            syncEngine.addSample(
                t0 = roundtrip.t0,
                t1 = roundtrip.t1,
                t2 = roundtrip.t2,
                t3 = roundtrip.t3
            )
            clientClock += 500L
        }

        assertTrue(syncEngine.isSynchronized)
        val estimatedOffset = syncEngine.estimatedOffset

        // Ошибка оценки смещения часов должна быть существенно лучше 100 мс (в пределах 15 мс)
        val offsetError = abs(estimatedOffset - trueServerClockOffsetMs)
        assertTrue(offsetError <= 20L, "Offset estimation error $offsetError ms is too large")

        // 2. Инициализируем интерполятор позиции на пульте
        val interpolator = PlaybackPositionInterpolator()
        val anchorTrackPosMs = 30_000L
        val anchorServerMonoMs = clientClock + trueServerClockOffsetMs

        // Опорная засечка от сервера
        interpolator.setAnchor(
            positionMs = anchorTrackPosMs,
            positionAtMonoMs = anchorServerMonoMs,
            durationMs = 240_000L,
            playbackRate = 1.0f,
            isPlaying = true,
            clockOffset = estimatedOffset
        )

        // 3. Проверяем интерполяцию в течение 10 секунд воспроизведения с шагом 1 с
        for (sec in 0..10) {
            val evalClientTime = clientClock + sec * 1000L
            val trueServerTime = evalClientTime + trueServerClockOffsetMs
            val groundTruthTrackPos = anchorTrackPosMs + (trueServerTime - anchorServerMonoMs)

            val interpolatedPos = interpolator.interpolate(evalClientTime)
            val posError = abs(interpolatedPos - groundTruthTrackPos)

            // Валидация NFR-2: погрешность позиции <= +-100 мс
            assertTrue(
                posError <= 100L,
                "Position error at $sec s was $posError ms, exceeding NFR-2 threshold of 100 ms"
            )
        }
    }

    // ── NFR-3: Надёжность связи и реконнект (<= 10 с) ─────────────────────────

    @Test
    fun testNfr3NetworkChangeAndReconnectRecoveryWithin10Seconds() {
        val reconnectPolicy = ReconnectPolicy(
            initialDelayMs = 500L,
            multiplier = 1.5,
            maxDelayMs = 5000L,
            maxAttempts = 5,
            totalTimeoutMs = 10_000L
        )

        // Симулируем активное подключение
        reconnectPolicy.onConnecting()
        reconnectPolicy.onConnected()
        assertEquals(ConnectState.CONNECTED, reconnectPolicy.currentState)

        // Обрыв связи из-за смены сети Wi-Fi <-> LTE
        reconnectPolicy.onConnectionLost()
        assertEquals(ConnectState.RECONNECTING, reconnectPolicy.currentState)

        // Срабатывает событие NetworkChanged: сброс задержки на 0 мс (мгновенная попытка)
        val immediateDelay = reconnectPolicy.onNetworkChanged()
        assertEquals(0L, immediateDelay)
        assertEquals(0, reconnectPolicy.attempt)
        assertEquals(0L, reconnectPolicy.accumulatedDelayMs)
        assertEquals(ConnectState.RECONNECTING, reconnectPolicy.currentState)

        // Первые попытки не удаются (переподключение сокета)
        var cumulativeWaitMs = 0L

        // Попытка 1: 500 мс
        val d1 = reconnectPolicy.nextDelayMs()
        assertNotNull(d1)
        cumulativeWaitMs += d1
        assertEquals(500L, d1)

        // Попытка 2: 750 мс
        val d2 = reconnectPolicy.nextDelayMs()
        assertNotNull(d2)
        cumulativeWaitMs += d2
        assertEquals(750L, d2)

        // Попытка 3: 1125 мс
        val d3 = reconnectPolicy.nextDelayMs()
        assertNotNull(d3)
        cumulativeWaitMs += d3
        assertEquals(1125L, d3)

        // На 4 попытке соединение успешно восстанавливается
        reconnectPolicy.onConnected()
        assertEquals(ConnectState.CONNECTED, reconnectPolicy.currentState)
        assertEquals(0L, reconnectPolicy.accumulatedDelayMs)

        // Валидация NFR-3: восстановление связи произошло быстрее 10 с
        assertTrue(
            cumulativeWaitMs <= 10_000L,
            "Total reconnect recovery time $cumulativeWaitMs ms exceeded NFR-3 budget of 10000 ms"
        )
    }

    // ── NFR-4: Безопасность, TTL и защита от подбора ───────────────────────────

    @Test
    fun testNfr4PairingSecurityTtlHmacAndBruteforceProtection() {
        val securityManager = PairingSecurityManager()
        val trustStore = TrustedDeviceStore.inMemory()

        val hostEndpoint = ByteArray(32) { 0x11 }.asBytes()
        val clientEndpoint = ByteArray(32) { 0x22 }.asBytes()
        val creationEpochSec = 1700000000L

        // 1. Генерация QR со стандартным TTL 120 с
        val payload = securityManager.generatePairingPayload(
            endpointId = hostEndpoint,
            deviceName = "Host Windows PC",
            currentEpochSec = creationEpochSec,
            ttlSec = 120L
        )
        assertEquals(120L, payload.expiresAtEpochSec - creationEpochSec)

        val qrUri = PairingPayloadCodec.encode(payload)
        assertTrue(qrUri.startsWith("lirix://pair?d="))

        // Проверка истечения TTL: в момент 121 с кодек и менеджер обязаны отклонить сопряжение
        val expiredEpochSec = creationEpochSec + 121L
        assertFailsWith<PairingExpiredException> {
            PairingPayloadCodec.decode(qrUri, currentEpochSec = expiredEpochSec)
        }

        // Проверка в securityManager на истечение TTL
        val nonce = ByteArray(16) { 0x05 }.asBytes()
        val clientProof = securityManager.createProof(
            secret = payload.pairingSecret,
            nonce = nonce,
            clientEndpointId = clientEndpoint,
            serverEndpointId = hostEndpoint
        )
        val expiredReq = PairRequest(nonce, clientProof, "Pixel 8", DeviceType.ANDROID_PHONE)
        val expiredResult = securityManager.verifyPairRequest(expiredReq, clientEndpoint, currentEpochSec = expiredEpochSec)
        assertEquals(VerificationResult.Expired, expiredResult)

        // 2. Защита от неверного HMAC proof и перебора (brute-force) на активном секрете
        val freshPayload = securityManager.generatePairingPayload(
            endpointId = hostEndpoint,
            deviceName = "Host Windows PC",
            currentEpochSec = creationEpochSec,
            ttlSec = 120L
        )
        val freshClientProof = securityManager.createProof(
            secret = freshPayload.pairingSecret,
            nonce = nonce,
            clientEndpointId = clientEndpoint,
            serverEndpointId = hostEndpoint
        )

        val tamperedProof = ByteArray(32) { 0xFF.toByte() }.asBytes()
        val badReq = PairRequest(nonce, tamperedProof, "Attacker", DeviceType.ANDROID_PHONE)
        val badResult = securityManager.verifyPairRequest(badReq, clientEndpoint, currentEpochSec = creationEpochSec + 10L)
        assertEquals(VerificationResult.InvalidProof, badResult)
        assertEquals(1, securityManager.failedAttempts)

        // 3. Защита от перебора: после 3 неверных попыток QR аннулируется
        val badResult2 = securityManager.verifyPairRequest(badReq, clientEndpoint, currentEpochSec = creationEpochSec + 20L)
        assertEquals(VerificationResult.InvalidProof, badResult2)
        assertEquals(2, securityManager.failedAttempts)

        val badResult3 = securityManager.verifyPairRequest(badReq, clientEndpoint, currentEpochSec = creationEpochSec + 30L)
        assertEquals(VerificationResult.InvalidProof, badResult3)
        assertEquals(3, securityManager.failedAttempts)
        assertNull(securityManager.activePayload) // Секрет аннулирован!

        // Любая следующая попытка (даже с правильным proof) отклоняется
        val lateGoodResult = securityManager.verifyPairRequest(
            PairRequest(nonce, freshClientProof, "Pixel 8", DeviceType.ANDROID_PHONE),
            clientEndpoint,
            currentEpochSec = creationEpochSec + 40L
        )
        assertEquals(VerificationResult.TooManyAttempts, lateGoodResult)

        // 4. Pinning доверенных устройств
        val trusted = TrustedDevice(
            endpointId = clientEndpoint,
            deviceName = "Pixel 8",
            deviceType = DeviceType.ANDROID_PHONE,
            pairedAtEpochSec = creationEpochSec,
            lastSeenEpochSec = creationEpochSec
        )
        trustStore.addTrusted(trusted)
        assertTrue(trustStore.isTrusted(clientEndpoint))

        val untrustedEndpoint = ByteArray(32) { 0x99.toByte() }.asBytes()
        assertFalse(trustStore.isTrusted(untrustedEndpoint))
    }

    // ── NFR-5: Устойчивость Audio Cast к потерям 5% (PLC маркеров) ─────────────

    @Test
    fun testNfr5AudioCastResilienceUnder5PercentPacketLossWithPlc() {
        val jitterBuffer = AdaptiveJitterBuffer(minBufferMs = 40, maxBufferMs = 80, frameDurationMs = 10)

        // 1. Создаём 100 аудио-пакетов Opus (10 мс каждый = 1000 мс звука)
        val totalPackets = 100
        val datagrams = (0 until totalPackets).map { seq ->
            CastDatagram(
                seq = seq,
                timestamp48k = seq * 480L,
                flags = 0,
                opusPayload = ByteArray(120) { (it + seq).toByte() }
            )
        }

        // 2. Детерминированно симулируем ровно 5% потерь (5 пакетов потеряны: 15, 32, 54, 71, 89)
        val droppedIndices = setOf(15, 32, 54, 71, 89)
        assertEquals(5, droppedIndices.size) // Ровно 5% от 100

        var arrivalTimeMs = 100L
        for (dg in datagrams) {
            arrivalTimeMs += 10L
            if (dg.seq !in droppedIndices) {
                jitterBuffer.insert(dg, arrivalMonoMs = arrivalTimeMs)
            }
        }

        // 3. Вычитываем все элементы из джиттер-буфера
        val outputItems = mutableListOf<JitterBufferItem>()
        var item = jitterBuffer.popNextFrame(forcePlay = true)
        while (item != null) {
            outputItems.add(item)
            item = jitterBuffer.popNextFrame(forcePlay = true)
        }

        // 4. Анализ результатов вывода
        val receivedFrames = outputItems.filterIsInstance<JitterBufferItem.Frame>()
        val plcLossMarkers = outputItems.filterIsInstance<JitterBufferItem.LossConcealment>()

        // Получено 95 фреймов и ровно 5 PLC маркеров для потерянных пакетов
        assertEquals(95, receivedFrames.size)
        assertEquals(5, plcLossMarkers.size)
        assertEquals(100, outputItems.size) // Непрерывность потока 1000 мс сохранена!

        // Номера потерянных пакетов строго соответствуют droppedIndices
        val lostSequences = plcLossMarkers.map { it.expectedSeq }.toSet()
        assertEquals(droppedIndices, lostSequences)
    }
}
