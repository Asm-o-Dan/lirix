## Задача REPLAY-P2-005: Реализовать ReplayEngineImpl

**Файл:** `feature/replay/src/main/kotlin/com/example/npc/feature/replay/engine/ReplayEngineImpl.kt` (создать)  
**Модуль:** `:feature:replay`  
**Спека:** `.sdd/specs/pipeline-replay/overview.md#6-полная-реализация-ядра-симулятора-replayengineimpl`  
**Контракт:** `.sdd/contracts/pipeline-replay__runtime.md#2-публичный-контракт-replayengine`  

**Сигнатура (НЕ МЕНЯТЬ):**
```kotlin
package com.example.npc.feature.replay.engine

import com.example.npc.core.storage.dao.ReplayEventSourceDao
import com.example.npc.feature.replay.report.*
import com.example.npc.pipeline.compiler.CompiledPipeline
import kotlinx.coroutines.flow.Flow

interface ReplayEngine {

    /**
     * Запускает симуляцию черновика конвейера на исторических данных.
     *
     * @param draftPipeline Скомпилированный конвейер-черновик.
     * @param activePipeline Текущий активный конвейер (для парного сравнения) либо null (сравнение с историей в БД).
     * @param criteria Критерии выборки событий (временной диапазон, фильтр пакетов, лимит).
     * @return Холодный [Flow], транслирующий статус выполнения и итоговый [ReplayDiffReport].
     */
    fun runSimulation(
        draftPipeline: CompiledPipeline,
        activePipeline: CompiledPipeline? = null,
        criteria: ReplayCriteria = ReplayCriteria.Default
    ): Flow<ReplayStatus>
}

class ReplayEngineImpl(
    private val eventSourceDao: ReplayEventSourceDao,
    private val effectEvaluator: VirtualEffectEvaluator = VirtualEffectEvaluator()
) : ReplayEngine {

    override fun runSimulation(
        draftPipeline: CompiledPipeline,
        activePipeline: CompiledPipeline?,
        criteria: ReplayCriteria
    ): Flow<ReplayStatus>
}
```

**Поведение:**
1. Предоставляет реактивное ядро симуляции конвейеров уведомлений поверх исторических событий базы догфудинга.
2. Возвращает холодный `Flow<ReplayStatus>`, запускаемый при начале сбора (`collect`):
   - Ограничивает контекст исполнения пулом фоновых потоков `Dispatchers.Default`.
   - Запрашивает начальное количество записей `countEvents(startTime, endTime)` и вычисляет `totalCount = minOf(count, criteria.maxEventsLimit)`.
3. Реализует постраничную итерацию Keyset Pagination:
   - В цикле запрашивает чанки событий `eventSourceDao.getEventsAfterId(lastSeenId, startTime, endTime, limit = criteria.chunkSize)`.
   - По завершении каждого чанка обновляет `lastSeenId = batch.last().eventId` и уступает квант времени процессора через `kotlinx.coroutines.yield()`.
   - Эмитит промежуточный статус `ReplayStatus.Running(processedCount, totalCount, percentProgress, currentEventId)`.
4. Изолированное исполнение конвейера:
   - Предвыделяет 2 переиспользуемых экземпляра `Frame` (для черновика и активной версии) с обнулением регистров (`reset()`) перед каждым событием.
   - Измеряет время исполнения каждого события в микросекундах / наносекундах (`System.nanoTime()`).
   - Декодирует побочные эффекты через `effectEvaluator.evaluate(signal, effectBuffer, duration)`.
5. Дифференциальный анализ:
   - Сопоставляет полученную категорию и извлеченную транзакцию черновика с эталоном (активный конвейер или исторические данные Room).
   - При несовпадении формирует `EventDiscrepancy` с соответствующим типом `DiscrepancyType` (`CATEGORY_MISMATCH`, `TRANSACTION_EXTRACTED_VS_NONE`, `TRANSACTION_MISSED`, `TRANSACTION_AMOUNT_DIFF`, `DROPPED_VS_PASSED`).
6. Формирование итогового отчета:
   - Рассчитывает средние задержки и квантили $p95$ (через сортировку предвыделенного массива замеров).
   - Вычисляет `matchRatePercent` и `ConfidenceShift`.
   - Эмитит финальный терминальный статус `ReplayStatus.Completed(report)`.
7. Обработка прерывания:
   - Регулярно вызывает `currentCoroutineContext().ensureActive()`.
   - При корутинной отмене (`CancellationException`) немедленно прерывает выполнение и пробрасывает исключение дальше.
   - При непредвиденных ошибках эмитит `ReplayStatus.Failed(throwable, processedCount)`.

**Ошибки:**
- При фатальном исключении чтения SQLite оборачивает ошибку в `ReplayStatus.Failed` с сохранением `processedBeforeFailure`.
- `CancellationException` перехватывается только для логирования и обязательно пробрасывается дальше для корректной остановки корутины.

**Граничные случаи:**
- Выборка пуста (`totalCount == 0`) -> мгновенный эмит `ReplayStatus.Completed` с пустым отчетом (0 событий, 100% match rate).
- `activePipeline == null` -> в качестве эталона используются поля `historicalCategory`, `historicalConfidence` и `historicalTransactionJson` из объекта `ReplayHistoricalEvent`.
- Число событий превышает `maxEventsLimit` -> цикл останавливается строго по достижении лимита.

**Запрещено:**
- Вызывать мутирующие методы SQLite/Room (строгий инвариант 100% Sandbox Isolation).
- Выделять новые экземпляры `Frame`, `EffectBuffer` внутри цикла по событиям (Zero Allocation per event).
- Блокировать поток через `Thread.sleep` (разрешен только неблокирующий `yield()`).

**Критерий приёмки:**
- Файл скомпилирован в модуле `:feature:replay`.
- Проходят юнит-тесты:
  * Прогон симуляции на mock `ReplayEventSourceDao` с генерацией 100 событий.
  * Проверка вычисления квантилей $p50$, $p95$ латентности.
  * Реакция на корутинную отмену `Job.cancel()` (прекращение чтения и эмиссий).
  * Корректная детекция расхождений `DiscrepancyType.CATEGORY_MISMATCH` и `TRANSACTION_AMOUNT_DIFF`.
