## Задача REPLAY-P2-002: Реализовать ReplayDiffReport, DiffMetrics и EventDiscrepancy

**Файл:** `feature/replay/src/main/kotlin/com/example/npc/feature/replay/report/ReplayDiffReport.kt` (создать/обновить)  
**Модуль:** `:feature:replay`  
**Спека:** `.sdd/specs/pipeline-replay/overview.md#4-модель-сравнительного-дифференциального-отчёта-replaydiffreport`  
**Контракт:** `.sdd/contracts/pipeline-replay__runtime.md#4-дифференциальный-отчет-replaydiffreport`  

**Сигнатура (НЕ МЕНЯТЬ):**
```kotlin
package com.example.npc.feature.replay.report

import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.finance.FinancialTransaction

enum class DiscrepancyType {
    CATEGORY_MISMATCH,
    CONFIDENCE_DROP,
    TRANSACTION_EXTRACTED_VS_NONE,
    TRANSACTION_MISSED,
    TRANSACTION_AMOUNT_DIFF,
    TRANSACTION_CURRENCY_DIFF,
    EXECUTION_FAILED,
    DROPPED_VS_PASSED
}

data class ConfidenceShift(
    val averageDraft: Double,
    val averageActive: Double,
    val delta: Double
)

data class EventDiscrepancy(
    val eventId: Long,
    val packageName: String,
    val notificationTitle: String,
    val notificationText: String,
    val discrepancyType: DiscrepancyType,
    val baselineCategory: Category,
    val draftCategory: Category,
    val baselineTransaction: FinancialTransaction?,
    val draftTransaction: FinancialTransaction?,
    val baselineLatencyUs: Long,
    val draftLatencyUs: Long
)

data class DiffMetrics(
    val totalProcessedEvents: Int,
    val identicalOutcomesCount: Int,
    val discrepancyCount: Int,
    val matchRatePercent: Float,
    val avgDraftLatencyUs: Long,
    val avgActiveLatencyUs: Long,
    val p95DraftLatencyUs: Long,
    val p95ActiveLatencyUs: Long,
    val confidenceShift: ConfidenceShift
)

data class ReplayDiffReport(
    val metrics: DiffMetrics,
    val discrepancies: List<EventDiscrepancy>,
    val executionDurationMs: Long,
    val isTruncated: Boolean = false
)
```

**Поведение:**
1. Предоставляет типизированные модели и структуры данных для фиксации сравнительного дифференциального отчета симулятора.
2. `DiscrepancyType`:
   - `CATEGORY_MISMATCH` — расхождение в результирующей категории (`Category`) события между черновиком и эталоном.
   - `CONFIDENCE_DROP` — снижение уверенности классификации ниже допустимого порога.
   - `TRANSACTION_EXTRACTED_VS_NONE` — новая транзакция, извлеченная черновиком, отсутствовавшая в эталоне.
   - `TRANSACTION_MISSED` — транзакция была в эталоне, но черновик ее пропустил.
   - `TRANSACTION_AMOUNT_DIFF` — расхождение в сумме финансовой транзакции.
   - `TRANSACTION_CURRENCY_DIFF` — расхождение в коде валюты транзакции.
   - `EXECUTION_FAILED` — внутренняя ошибка выполнения стадии в конвейере-черновике.
   - `DROPPED_VS_PASSED` — изменение политики пропуска: событие отброшено (`Signal.DROP`) вместо пропуска (`Signal.PASS`) или наоборот.
3. `EventDiscrepancy`:
   - Детальный слепок расхождения для одного конкретного исторического события.
   - Содержит заголовок, текст, исходную и новую категории, транзакции и микросекундные замеры латентности.
4. `DiffMetrics`:
   - Сводные агрегированные показатели: общее число событий, совпадений, расхождений, процент совпадений (`matchRatePercent`), p95 и средние задержки в микросекундах.
   - `confidenceShift`: средний сдвиг уверенности между моделями.
5. `ReplayDiffReport`:
   - Корневой контейнер отчета, передаваемый в `ReplayStatus.Completed`.
   - `isTruncated = true`, если число расхождений превысило максимальный UI-буфер (например, > 500 записей).

**Ошибки:**
- Не выбрасывает исключений в runtime при корректных значениях полей.
- Валидация в `DiffMetrics`: `matchRatePercent` должен находиться в диапазоне `0.0f..100.0f`.
- Если `totalProcessedEvents == 0`, `matchRatePercent` равен `100.0f`.

**Граничные случаи:**
- `totalProcessedEvents = 0` -> `discrepancies = emptyList()`, `matchRatePercent = 100f`, все задержки = 0.
- `discrepancyCount == 0` -> `matchRatePercent = 100.0f`, `identicalOutcomesCount == totalProcessedEvents`.
- В `EventDiscrepancy` поля `baselineTransaction` и `draftTransaction` могут быть `null`.

**Запрещено:**
- Использовать платформенные Android-библиотеки (`android.*`, `androidx.*`).
- Использовать мутабельные коллекции (`ArrayList`, `MutableList`) в публичных свойствах.
- Менять имена свойств и enum элементов, утвержденных контрактом FROZEN v3.

**Критерий приёмки:**
- Файл скомпилирован в модуле `:feature:replay`.
- Проходят юнит-тесты:
  * Корректность создания `EventDiscrepancy` со всеми вариантами `DiscrepancyType`.
  * Расчет `matchRatePercent` при 0, частичных и 100% совпадениях.
  * Формирование пустого и заполненного `ReplayDiffReport`.
