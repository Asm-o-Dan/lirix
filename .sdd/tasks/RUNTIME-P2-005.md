## Задача RUNTIME-P2-005: Реализовать PipelineRuntimeEngine и PipelineRuntimeEngineImpl

**Модуль:** `:pipeline:runtime`  
**Целевые файлы:**  
- `pipeline/runtime/src/main/kotlin/com/example/npc/pipeline/runtime/engine/PipelineRuntimeEngine.kt`  
- `pipeline/runtime/src/main/kotlin/com/example/npc/pipeline/runtime/engine/PipelineRuntimeEngineImpl.kt`  
**Спецификация:** `.sdd/specs/pipeline-runtime/overview.md#3-горячий-цикл-обработки-событий-event-execution-loop`  
**Контракт:** `.sdd/contracts/pipeline-runtime__compiler.md`  

---

### Сигнатура (НЕ МЕНЯТЬ):
```kotlin
package com.example.npc.pipeline.runtime.engine

import com.example.npc.core.storage.StorageGateway
import com.example.npc.pipeline.runtime.hotswap.ActivePipelineProvider
import com.example.npc.pipeline.runtime.execution.ExecutionContextPool
import com.example.npc.pipeline.runtime.resilience.NodeCircuitBreaker
import com.example.npc.pipeline.runtime.trace.TraceRing
import kotlinx.coroutines.Job

/**
 * Статус и результат обработки одного входящего события.
 */
data class EventExecutionResult(
    val eventId: Long,
    val pipelineId: String,
    val revision: Long,
    val signal: Int,
    val durationUs: Long,
    val success: Boolean,
    val errorMessage: String? = null
)

/**
 * Метрики исполнения конвейера в реальном времени.
 */
data class PipelineRuntimeMetrics(
    val totalSubmitted: Long,
    val totalClaimed: Long,
    val totalCompleted: Long,
    val totalDropped: Long,
    val totalFailed: Long,
    val currentQueueDepth: Int
)

/**
 * Главный исполнительный движок сконструированного конвейера уведомлений.
 */
interface PipelineRuntimeEngine {

    /** Запускает рабочий цикл воркера обработки очереди. */
    fun start()

    /**
     * Ставит идентификатор события в горячую очередь обработки.
     * Неблокирующий вызов (SLA < 1 мс).
     */
    fun submit(eventId: Long): Boolean

    /**
     * Синхронная обработка одного события (для тестов, прямого вызова и Replay).
     */
    suspend fun processSingleEvent(eventId: Long): EventExecutionResult

    /**
     * Запускает фоновое восстановление подвисших событий из SQLite WAL.
     */
    fun triggerRecoverySweep(): Job

    /**
     * Останавливает конвейер с опустошением очереди (Draining).
     */
    fun stop()

    /** Текущие метрики работы конвейера. */
    fun getMetrics(): PipelineRuntimeMetrics
}
```

---

### Поведение:
1. **Очередь и воркер:**
   - Очередь событий: `Channel<Long>(Channel.UNLIMITED)`. В канал передаются строго примитивные `eventId`.
   - Воркер запускается в `SupervisorJob + Dispatchers.Default.limitedParallelism(1)`. Single-consumer гарантирует отсутствие конкуренции за SQLite локи.
2. **Алгоритм `processSingleEvent(eventId)`:**
   - **Шаг 1: Атомарный Claim.** Вызывает `storageGateway.tryClaimEvent(eventId)`. Если `false`, возвращает результат со статусом пропуска (событие уже занято или обработано).
   - **Шаг 2: Pinned Generation.** Захватывает ссылку на активный конвейер `val pipeline = activePipelineProvider.current()`. Все последующие шаги исполняются строго на этой ревизии (`pipeline.revision`).
   - **Шаг 3: Чтение данных.** Запрашивает событие из БД `storageGateway.getEventWithPackage(eventId)`. Читаются только поля, указанные в `pipeline.requiredInputMask`.
   - **Шаг 4: Контекст.** Извлекает `context` из `executionContextPool.acquire()`.
   - **Шаг 5: Инициализация фрейма.** Заполняет регистры `context.frame` входными данными события.
   - **Шаг 6: Исполнение и 3-уровневая изоляция.**
     * Вызывает `pipeline.execute(context.frame)` в защищенном блоке `try/catch`.
     * Исключения `CancellationException` и `VirtualMachineError` пробрасываются дальше без перехвата.
     * Прочие исключения `Throwable` перехватываются (Level 1 Catch): буфер эффектов сбрасывается, фиксируется ошибка в `circuitBreaker` (Level 2), формируется `Signal.FAULT`.
   - **Шаг 7: Двухфазный коммит.**
     * При `Signal.PASS`: вызывает `storageGateway.completeEventProcessing(eventId, classification, transaction, pipeline.revision)`.
     * При `Signal.DROP`: помечает событие `DROPPED` без создания побочных сущностей.
     * При `Signal.FAULT`: помечает событие `FAILED`, сохраняет `errorMessage`.
   - **Шаг 8: Трассировка и возврат.** Записывает метку в `TraceRing.record()`. В блоке `finally` возвращает `executionContextPool.release(context)`.
3. **`triggerRecoverySweep()`:**
   - В `Dispatchers.IO` запрашивает зависшие события (`UNCLASSIFIED` или `PROCESSING` старше 60 секунд).
   - Чанками по 1000 элементов отправляет `submit(id)` в очередь.

---

### Ошибки:
- При сбое в узле конвейера рабочий поток **не должен завершаться аварийно**. Исключение изолируется, событие переводится в `FAILED`, очередь продолжает обрабатываться.
- Исключение `CancellationException` обязательно пробрасывается наружу для корректного завершения корутины при `stop()`.

---

### Граничные случаи:
- **Горячая подмена во время обработки:** событие $E_1$ продолжает выполняться на старой ревизии `R_1`, следующее событие $E_2$ берет новую ревизию `R_2`.
- **Событие не найдено в хранилище:** `storageGateway.getEventWithPackage` вернул `null` $\to$ возвращается результат со статусом сбоя/пропуска без краша.
- **Всплеск входящих событий (Flooding):** очередь `UNLIMITED` принимает миллионы ID без `OutOfMemoryError`, так как хранит только примитивные `Long`.
- **Пустой Recovery Sweep:** если зависших событий нет, задача завершается без лишних операций.

---

### Запрещено:
- Выполнять прямые мутации Room / SQLite внутри узлов конвейера (только через `EffectBuffer` и двухфазный коммит).
- Блокировать поток воркера синхронными паузами `Thread.sleep()`.
- Использовать Reflection при маппинге данных события во фрейм.

---

### Критерии приёмки (DoD):
- [ ] Интерфейс `PipelineRuntimeEngine` и класс `PipelineRuntimeEngineImpl` скомпилированы в `:pipeline:runtime`.
- [ ] Unit- и интеграционные тесты (`PipelineRuntimeEngineTest.kt`):
  * Сквозной прогон события через тестовый пайплайн с фиксацией в `StorageGateway`.
  * Проверка Pinned Generation: неизменность ревизии во время параллельного вызова `activePipelineProvider.swap()`.
  * Изоляция сбоев: искусственный бросок исключения в этапе приводит к `Signal.FAULT`, очистке `EffectBuffer` и сохранению статуса `FAILED`, воркер не падает и продолжает обрабатывать следующие события.
  * Проверка вызова `triggerRecoverySweep()`.
  * Корректный подсчет всех метрик в `PipelineRuntimeMetrics`.
- [ ] Дифференциальный тест паритета: запуск пресета «Legacy 1.1» через `PipelineRuntimeEngine` на 137 догфуд-событиях дает 100% идентичный результат старому `EventProcessingOrchestratorImpl`.
