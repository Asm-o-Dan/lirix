package com.lirix.app.connect.cast

import kotlin.math.abs
import kotlin.math.max

/**
 * Элемент вывода из адаптивного джиттер-буфера (SPEC-CONNECT-01 §4.7, TASK-CON-CAST-09).
 */
sealed interface JitterBufferItem {
    /**
     * Полноценный аудио-фрейм, готовый к декодированию.
     */
    data class Frame(val datagram: CastDatagram) : JitterBufferItem

    /**
     * Маркер потери пакета для запуска алгоритма Packet Loss Concealment (PLC).
     */
    data class LossConcealment(val expectedSeq: Int, val timestamp48k: Long) : JitterBufferItem
}

/**
 * Адаптивный джиттер-буфер для Live Audio Cast (SPEC-CONNECT-01 §4.7, TASK-CON-CAST-09).
 *
 * Инварианты:
 * - Целевая задержка буферизации: 40–80 мс (адаптируется по измеренному сетевому джиттеру).
 * - Восстановление правильного порядка пакетов (reordering) по 16-битному `seq`.
 * - Устранение дубликатов (de-duplication).
 * - Детектирование потерянных пакетов с генерацией маркера [JitterBufferItem.LossConcealment].
 * - Расчёт джиттера по алгоритму RFC 3550.
 */
class AdaptiveJitterBuffer(
    val minBufferMs: Int = DEFAULT_MIN_BUFFER_MS,
    val maxBufferMs: Int = DEFAULT_MAX_BUFFER_MS,
    val frameDurationMs: Int = DEFAULT_FRAME_DURATION_MS
) {
    companion object {
        const val DEFAULT_MIN_BUFFER_MS = 40
        const val DEFAULT_MAX_BUFFER_MS = 80
        const val DEFAULT_FRAME_DURATION_MS = 10 // 10 мс фреймы Opus (480 сэмплов при 48 кГц)

        /**
         * Разность циклических 16-битных порядковых номеров:
         * > 0 если a следует за b
         * < 0 если a предшествует b
         * = 0 если a == b
         */
        fun sequenceDiff(seqA: Int, seqB: Int): Int {
            return (seqA - seqB).toShort().toInt()
        }
    }

    private val packetQueue = mutableListOf<CastDatagram>()

    var targetDelayMs: Int = minBufferMs
        private set

    var measuredJitterMs: Double = 0.0
        private set

    private var lastTransitMs: Long? = null
    private var expectedSeq: Int? = null
    private var lastTimestamp48k: Long = 0L
    private var isPlaying: Boolean = false

    var receivedPackets: Long = 0L
        private set
    var lostPackets: Long = 0L
        private set
    var droppedLatePackets: Long = 0L
        private set
    var duplicatePackets: Long = 0L
        private set

    val bufferedFramesCount: Int get() = packetQueue.size
    val currentBufferMs: Int get() = bufferedFramesCount * frameDurationMs

    /**
     * Добавление поступившей QUIC-датаграммы в джиттер-буфер.
     *
     * @param datagram Декодированная датаграмма
     * @param arrivalMonoMs Локальное монотонное время приёма в мс (для расчёта джиттера)
     * @return true если пакет успешно добавлен в буфер, false если отброшен (дубликат или опоздавший)
     */
    fun insert(datagram: CastDatagram, arrivalMonoMs: Long): Boolean {
        // 1. Оценка сетевого джиттера по алгоритму RFC 3550
        val transit = arrivalMonoMs - (datagram.timestamp48k / 48L)
        val prevTransit = lastTransitMs
        if (prevTransit != null) {
            val d = abs(transit - prevTransit).toDouble()
            measuredJitterMs += (d - measuredJitterMs) / 16.0
            val calculatedTarget = (minBufferMs + (measuredJitterMs * 2.0).toInt())
            targetDelayMs = calculatedTarget.coerceIn(minBufferMs, maxBufferMs)
        }
        lastTransitMs = transit

        // 2. Инициализация начальной точки отсчёта последовательности
        if (expectedSeq == null) {
            expectedSeq = datagram.seq
            lastTimestamp48k = datagram.timestamp48k
        }

        val curExpected = expectedSeq!!

        // 3. Проверка на устаревший/опоздавший пакет
        if (sequenceDiff(datagram.seq, curExpected) < 0) {
            droppedLatePackets++
            return false
        }

        // 4. Проверка на дубликат
        if (packetQueue.any { it.seq == datagram.seq }) {
            duplicatePackets++
            return false
        }

        // 5. Вставка с сохранением сортировки по seq (reordering)
        var insertIndex = 0
        while (insertIndex < packetQueue.size && sequenceDiff(packetQueue[insertIndex].seq, datagram.seq) < 0) {
            insertIndex++
        }
        packetQueue.add(insertIndex, datagram)
        receivedPackets++

        // Если набрали достаточный буфер, активируем playout
        if (!isPlaying && currentBufferMs >= targetDelayMs) {
            isPlaying = true
        }

        return true
    }

    /**
     * Извлечение очередного аудио-фрейма для воспроизведения.
     *
     * @param forcePlay форсировать извлечение даже при неполном пре-буферинге
     * @return [JitterBufferItem.Frame] если фрейм готов,
     *         [JitterBufferItem.LossConcealment] если фрейм потерян,
     *         null если буфер ещё накапливает начальную задержку либо полностью пуст.
     */
    fun popNextFrame(forcePlay: Boolean = false): JitterBufferItem? {
        if (!isPlaying && !forcePlay) {
            return null
        }

        if (expectedSeq == null) {
            return null
        }

        val curExpected = expectedSeq!!

        // Ищем в очереди пакет с ожидаемым номером
        val matchIndex = packetQueue.indexOfFirst { it.seq == curExpected }
        if (matchIndex != -1) {
            val packet = packetQueue.removeAt(matchIndex)
            expectedSeq = (curExpected + 1) and 0xFFFF
            lastTimestamp48k = packet.timestamp48k
            return JitterBufferItem.Frame(packet)
        }

        // Если точного совпадения нет, проверяем, есть ли более новые пакеты
        val hasNewerPackets = packetQueue.any { sequenceDiff(it.seq, curExpected) > 0 }
        if (hasNewerPackets) {
            // Ожидаемый пакет потерян! Сообщаем приемнику о необходимости PLC
            val lostSeq = curExpected
            expectedSeq = (curExpected + 1) and 0xFFFF
            lostPackets++

            val approxTimestamp = lastTimestamp48k + (frameDurationMs * 48L)
            lastTimestamp48k = approxTimestamp
            return JitterBufferItem.LossConcealment(expectedSeq = lostSeq, timestamp48k = approxTimestamp)
        }

        // Очередь пуста или исчерпана (Buffer Underrun)
        if (packetQueue.isEmpty()) {
            isPlaying = false
        }
        return null
    }

    /**
     * Процент потерь пакетов: lost / (received + lost).
     */
    fun calculateLossPercentage(): Float {
        val total = receivedPackets + lostPackets
        if (total <= 0L) return 0.0f
        return ((lostPackets.toDouble() / total.toDouble()) * 100.0).toFloat()
    }

    /**
     * Полный сброс буфера.
     */
    fun reset() {
        packetQueue.clear()
        expectedSeq = null
        lastTransitMs = null
        lastTimestamp48k = 0L
        isPlaying = false
        receivedPackets = 0L
        lostPackets = 0L
        droppedLatePackets = 0L
        duplicatePackets = 0L
        measuredJitterMs = 0.0
        targetDelayMs = minBufferMs
    }
}
