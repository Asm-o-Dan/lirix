# Межзонный контракт: Pipeline Runtime ↔ Pipeline Compiler

**Версия:** FROZEN v3  
**Дата заморозки:** 2026-09-28  
**Статус:** FROZEN (GATE 3 PASSED)  
**Стороны контракта:**
- Провайдер скомпилированного артефакта: `zone/pipeline-compiler` (`:pipeline:compiler`)
- Потребитель и горячий рантайм: `zone/pipeline-runtime` (`:pipeline:runtime`)
- Дополнительные участники: `zone/pipeline-store` (`:core:storage`), `zone/pipeline-replay` (`:feature:replay`)

---

### 1. Архитектурный контекст и принцип Hot Swap

Контракт фиксирует интерфейс передачи скомпилированного конвейера (`CompiledPipeline`) в рабочий контур приложения (`PipelineRuntimeEngine`) и регламентирует поведение горячей подмены (Hot Swap) без перезапуска системной службы Android `NotificationListenerService` в Xiaomi HyperOS (Poco M7).

```
┌────────────────────────────────────────────────────────┐
│         zone/pipeline-compiler (:pipeline:compiler)    │
│  - PipelineCompiler.compile(definition): Result        │
│  - Формирует CompiledPipeline, CompiledStage, Signal  │
│  - Подготавливает JIT-прогретый граф                   │
└───────────────────────────┬────────────────────────────┘
                            │ CompiledPipeline (Immutable)
                            ▼
┌────────────────────────────────────────────────────────┐
│          zone/pipeline-runtime (:pipeline:runtime)     │
│  - ActivePipelineProvider (AtomicReference Hot Swap)   │
│  - Pinned Generation для in-flight событий             │
│  - ExecutionContextPool (0 allocations / event)        │
│  - 3-уровневая изоляция сбоев (Try-Catch, CB, Alerts) │
│  - TraceRing (кольцевой буфер 128 событий)             │
└────────────────────────────────────────────────────────┘
```

---

### 2. Модель исполнения `CompiledPipeline` и сигналы `Signal`

```kotlin
package com.example.npc.pipeline.compiler

import com.example.npc.pipeline.nodes.api.frame.Frame
import com.example.npc.pipeline.nodes.api.frame.FrameLayout
import com.google.re2j.Pattern

/**
 * Терминальные сигналы завершения выполнения конвейера.
 */
object Signal {
    /** Конвейер успешно завершен штатно. Эффекты фиксируются в БД. */
    const val PASS: Int = -1

    /** Событие отфильтровано или явно подавлено (DropEvent). Побочные эффекты отменяются. */
    const val DROP: Int = -2

    /** Аварийная остановка этапа. Изолируется сбоем без краша службы. */
    const val FAULT: Int = -3
}

/**
 * Иммутабельный скомпилированный граф конвейера.
 * Безопасен для многопоточного параллельного чтения.
 */
class CompiledPipeline(
    val pipelineId: String,
    val revision: Long,
    val canonicalHash: String,
    val compiledAtTimestamp: Long,
    val dslVersion: Int,
    val compilerVersion: Int,
    val packageWhitelistSet: Set<String>,
    val requiredInputMask: Long,
    val layout: FrameLayout,
    @JvmField internal val stages: Array<CompiledStage>,
    @JvmField internal val patterns: Array<Pattern>,
    val debugInfo: DebugInfo
) {
    /**
     * Точка входа горячего исполнения в рантайме.
     * Не выполняет аллокаций памяти, не использует рефлексию.
     *
     * @param frame Предвыделенный контекст рабочего потока.
     * @return [Signal.PASS], [Signal.DROP] или [Signal.FAULT].
     */
    fun execute(frame: Frame): Int {
        val stageArray = stages
        var pc = 0
        while (pc >= 0) {
            pc = stageArray[pc].execute(frame)
        }
        return pc
    }
}

/**
 * Скомпилированный этап конвейера.
 */
interface CompiledStage {
    val stageId: String
    val stageIndex: Int

    /**
     * Исполняет этап над фреймом.
     * @return Индекс следующего шага (pc + 1) либо отрицательный сигнал ([Signal.PASS], [Signal.DROP], [Signal.FAULT]).
     */
    fun execute(frame: Frame): Int
}

/**
 * Необязательные метаданные отладки (Side-table).
 * Не удерживаются на горячем пути процессора.
 */
data class DebugInfo(
    val stageNames: Map<String, String>,
    val variableNames: Map<Int, String>
)
```

---

### 3. Контракт горячей подмены (`ActivePipelineProvider`)

```kotlin
package com.example.npc.pipeline.runtime.hotswap

import com.example.npc.pipeline.compiler.CompiledPipeline
import kotlinx.coroutines.flow.StateFlow

data class PipelineSnapshotInfo(
    val pipelineId: String,
    val revision: Long,
    val canonicalHash: String,
    val stagesCount: Int,
    val swappedAtTimestamp: Long
)

/**
 * Поставщик активного скомпилированного конвейера с атомарной подменой.
 */
interface ActivePipelineProvider {

    /**
     * Возвращает текущий активный скомпилированный конвейер.
     * Строго 0 аллокаций, одно чтение volatile-ссылки (O(1)).
     */
    fun current(): CompiledPipeline

    /**
     * Атомарно заменяет активный экземпляр конвейера.
     *
     * @param next Валидированный и прогретый конвейер следующей ревизии.
     * @return Предыдущая замещенная версия конвейера.
     * @throws IllegalArgumentException если next.revision <= current.revision.
     */
    fun swap(next: CompiledPipeline): CompiledPipeline

    /**
     * Поток метаданных активной ревизии для UI и мониторинга.
     */
    val activeRevisionFlow: StateFlow<PipelineSnapshotInfo>
}
```

---

### 4. Семантика Pinned Generation и In-Flight изоляция

1. **Гарантия фиксации версии:** Каждое входящее событие в начале обработки захватывает ссылку на активный конвейер:
   ```kotlin
   val active = activePipelineProvider.current()
   val activeRevision = active.revision
   ```
2. **Изоляция:** Смена глобальной ссылки в `ActivePipelineProvider` во время работы этапов события не затрагивает его выполнение. Событие гарантированно завершается на той версии конвейера, на которой оно стартовало.
3. **Provenance Stamping:** При коммите в Room поле `event.pipeline_revision_id` маркируется значением `activeRevision`.

---

### 5. 3-Уровневая изоляция сбоев (Crash Isolation SLA)

1. **Level 1 — Per-event Catch:**
   Любое необработанное исключение `Throwable` внутри `execute(frame)` перехватывается. Событие помечается статусом `FAILED` (или `UNCLASSIFIED`), буфер эффектов `EffectBuffer.reset()` очищается, очередь событий не останавливается.
2. **Level 2 — Per-node Circuit Breaker:**
   При фиксации 3 последовательных падений одного узла за 5 минут узел переводится в состояние `OPEN` (обход/bypass узла). Конвейер продолжает функционировать.
3. **Level 3 — Runtime Alert Notification:**
   Критический сбой или переход Circuit Breaker фиксируется в таблице `runtime_alert` Room v3 и отправляет уведомление в UI таймлайна без падения фонового процесса.
