## Задача REPLAY-P2-001: Реализовать ReplayCriteria, TimeRange и ReplayStatus

**Файл:** `feature/replay/src/main/kotlin/com/example/npc/feature/replay/engine/ReplayModels.kt` (создать/обновить)  
**Модуль:** `:feature:replay`  
**Спека:** `.sdd/specs/pipeline-replay/overview.md#22-модель-критериев-выборки-replaycriteria`  
**Контракт:** `.sdd/contracts/pipeline-replay__runtime.md#3-модели-критериев-выборки-и-статуса-исполнения`  

**Сигнатура (НЕ МЕНЯТЬ):**
```kotlin
package com.example.npc.feature.replay.engine

import com.example.npc.feature.replay.report.ReplayDiffReport
import java.time.Instant

sealed interface TimeRange {
    data object Last24Hours : TimeRange
    data object Last7Days : TimeRange
    data object AllTime : TimeRange
    data class Custom(val start: Instant, val end: Instant) : TimeRange
}

data class ReplayCriteria(
    val timeRange: TimeRange = TimeRange.Last7Days,
    val packageFilter: Set<String>? = null,
    val maxEventsLimit: Int = 1000,
    val chunkSize: Int = 150
) {
    companion object {
        val Default = ReplayCriteria()
    }
}

sealed interface ReplayStatus {
    data object Idle : ReplayStatus

    data class Running(
        val processedCount: Int,
        val totalCount: Int,
        val percentProgress: Float,
        val currentEventId: Long
    ) : ReplayStatus

    data class Completed(
        val report: ReplayDiffReport
    ) : ReplayStatus

    data class Failed(
        val throwable: Throwable,
        val processedBeforeFailure: Int
    ) : ReplayStatus
}
```

**Поведение:**
1. Предоставляет типизированные структуры критериев выборки исторических событий (`ReplayCriteria`), временных интервалов (`TimeRange`) и потокового статуса выполнения симуляции (`ReplayStatus`).
2. `TimeRange`:
   - `Last24Hours` — интервал за последние 24 часа от текущего времени.
   - `Last7Days` — интервал за последние 7 суток (типовой dogfooding датасет Poco M7).
   - `AllTime` — без ограничений по времени (от epoch 0 до `Long.MAX_VALUE`).
   - `Custom(start, end)` — произвольный пользовательский диапазон меток `Instant`.
3. `ReplayCriteria`:
   - `timeRange` — выбранный интервал времени.
   - `packageFilter` — опциональный белый список пакетов (`null` или пустой — оценивать все приложения).
   - `maxEventsLimit` — максимальное число событий для симуляции (дефолт 1000 для удержания SLA $\le 3$ с на Poco M7).
   - `chunkSize` — размер батча для Keyset pagination Room (дефолт 150).
4. `ReplayStatus`:
   - `Idle` — симулятор не запущен.
   - `Running` — эмиттится по ходу симуляции после каждого чанка с указанием обработанных, общего числа, прогресса (0.0f..1.0f) и `currentEventId`.
   - `Completed` — терминальный статус успешного завершения с итоговым дифференциальным отчетом `ReplayDiffReport`.
   - `Failed` — терминальный статус при фатальной ошибке чтения БД или рантайма с сохранением количества обработанных до сбоя событий.

**Ошибки:**
- В `TimeRange.Custom` при `start > end` выбрасывает `IllegalArgumentException("start must be <= end")`.
- В `ReplayCriteria` при `maxEventsLimit <= 0` или `chunkSize <= 0` выбрасывает `IllegalArgumentException`.

**Граничные случаи:**
- `packageFilter = emptySet()` или `null` -> выборка не фильтруется по именам пакетов.
- `totalCount = 0` в `Running` -> `percentProgress = 0f` (исключение деления на ноль).
- `processedCount == totalCount` -> `percentProgress = 1.0f`.

**Запрещено:**
- Использовать Android SDK зависимости (`android.*`, `androidx.*`).
- Добавлять мутабельные свойства (`var`) — все классы строго иммутабельны.
- Менять имена свойств и типов, зафиксированных в замороженном контракте FROZEN v3.

**Критерий приёмки:**
- Файл скомпилирован в модуле `:feature:replay`.
- Проходят юнит-тесты:
  * Проверка фабрики по умолчанию `ReplayCriteria.Default`.
  * Валидация диапазонов `TimeRange.Custom(start, end)`.
  * Расчет `percentProgress` для граничных значений `totalCount = 0`, `totalCount = 1000`.
