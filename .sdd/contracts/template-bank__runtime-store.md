# Межзонный контракт: Template Bank ↔ Runtime Engine & Storage

**Версия:** DRAFT v4 (Фаза 3)  
**Дата:** 2026-09-28  
**Статус:** REVIEW / PROPOSED  
**Стороны контракта:**
- Провайдер управления банком шаблонов: `zone/pipeline-store` (`:core:storage`)
- Потребитель рантайма: `zone/pipeline-runtime` (`:pipeline:runtime`)
- Потребитель компилятора: `zone/pipeline-compiler` (`:pipeline:compiler`)

---

### 1. Архитектурный контекст и границы

Согласно **ADR-303**, динамические шаблоны не вызывают создание новой ревизии `PipelineDefinition`. Они живут в отдельной версионируемой сущности **TemplateBank** внутри базы данных Room v4.
Рантайм конвейера через узел `extract.template_bank` обращается к скомпилированному представлению банка `CompiledTemplateBank`.
Смена версии банка или конвейера происходит согласованно через атомарный контейнер `RuntimeGeneration` (**ADR-304**).

```
┌────────────────────────────────────────────────────────┐
│               zone/pipeline-store                      │
│  - DynamicTemplateDao, TemplateBankDao                 │
│  - TemplateBankManager: FSM (ACTIVE, SHADOW, QUARANTINE│
└───────────────────────────┬────────────────────────────┘
                            │ BankVersion + TemplateEntities
                            ▼
┌────────────────────────────────────────────────────────┐
│             zone/pipeline-compiler                     │
│  - PipelineCompiler.compileBank(templates)             │
│  - Lowers to CompiledTemplateBank (fast literals map)  │
└───────────────────────────┬────────────────────────────┘
                            │ CompiledTemplateBank
                            ▼
┌────────────────────────────────────────────────────────┐
│             zone/pipeline-runtime                      │
│  - AtomicReference<RuntimeGeneration>                  │
│  - NodeExecutor: "extract.template_bank"               │
│  - OrchestratorProbe: lock-free snapshot               │
└────────────────────────────────────────────────────────┘
```

---

### 2. Спецификация типов данных и сущностей

```kotlin
package com.example.npc.pipeline.store.template

import com.example.npc.pipeline.compiler.CompiledPipeline
import kotlinx.coroutines.flow.StateFlow

enum class TemplateTier {
    OVERRIDE,   // Выполняется ДО статических банковских экстракторов
    FALLBACK    // Выполняется ПОСЛЕ статических банковских экстракторов
}

enum class TemplateOrigin {
    USER,           // Создан или подтвержден человеком в One-Tap UI
    AUTO,           // Синтезирован эвристикой без прямого подтверждения
    AUTO_REFINED    // Автоматически уточнен по near-miss событиям
}

enum class TemplateState {
    DRAFT,
    ACTIVE,
    SHADOW,         // Сопоставляется в фоне, собирает метрики согласованности
    QUARANTINED,    // Отключен из-за ошибок парсинга или срабатывания CircuitBreaker
    DISABLED,       // Отключен пользователем вручную
    SUPERSEDED      // Заменен более новой версией шаблона
}

data class TemplateBankInfo(
    val version: Long,
    val activeCount: Int,
    val shadowCount: Int,
    val quarantinedCount: Int,
    val lastUpdatedAt: Long
)

/**
 * Единый контейнер поколения рантайма (ADR-304).
 */
data class RuntimeGeneration(
    val pipeline: CompiledPipeline,
    val bank: CompiledTemplateBank,
    val generationId: Long,
    val activatedAtTimestamp: Long
)
```

---

### 3. Контракты интерфейсов

```kotlin
package com.example.npc.pipeline.store.template

import com.example.npc.induction.TemplateSpec
import com.example.npc.pipeline.runtime.hotswap.DiagnosticsSnapshot
import kotlinx.coroutines.flow.StateFlow

interface TemplateBankManager {
    /**
     * Текущая активная версия банка шаблонов.
     */
    val currentBankFlow: StateFlow<TemplateBankInfo>

    /**
     * Сохраняет новый шаблон, перекомпилирует банк и атомарно активирует его в рантайме.
     */
    suspend fun saveAndActivate(
        spec: TemplateSpec,
        origin: TemplateOrigin,
        tier: TemplateTier,
        sampleEventId: Long?
    ): BankActivationResult

    /**
     * Отключает шаблон и переводит его в состояние DISABLED.
     */
    suspend fun disableTemplate(templateId: String): BankActivationResult

    /**
     * Переводит шаблон в карантин при повторяющихся сбоях.
     */
    suspend fun quarantineTemplate(templateId: String, reason: String): BankActivationResult

    /**
     * Повышает статус шаблона из SHADOW в ACTIVE (авто-промоушен или подтверждение).
     */
    suspend fun promoteShadowTemplate(templateId: String): BankActivationResult
}

sealed interface BankActivationResult {
    data class Success(val newBankVersion: Long) : BankActivationResult
    data class CompilationFailed(val diagnostics: List<String>) : BankActivationResult
    data class StorageError(val throwable: Throwable) : BankActivationResult
}

/**
 * Интерфейс мониторинга состояния рантайма без блокировки горячего пути.
 */
interface OrchestratorProbe {
    /**
     * Считывает консистентный снэпшот рантайма за O(128).
     */
    fun takeSnapshot(): DiagnosticsSnapshot

    /**
     * Ручной сброс состояния предохранителя узла.
     */
    fun resetCircuitBreaker(nodeId: String): Boolean
}
```

---

### 4. Инварианты исполнения
1. **Атомарность подмены поколения (ADR-304):** Метод обновления рантайма обязан гарантировать, что ни одно событие не увидит `CompiledPipeline` от одной версии и `CompiledTemplateBank` от другой.
2. **Изоляция сбоев (Автокарантин):** Если шаблон вызывает более 3 последовательных ошибок разбора сумм или провоцирует открытие `NodeCircuitBreaker`, он автоматически изолируется в `QUARANTINED`, а банк немедленно перекомпилируется без сбойного узла.
3. **Защита пользовательских данных:** События, размеченные как `USER_EDITED`, никогда не перезаписываются фоновым Backfill-процессом при добавлении новых шаблонов.
