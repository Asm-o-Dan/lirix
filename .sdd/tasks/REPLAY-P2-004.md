## Задача REPLAY-P2-004: Реализовать VirtualEffectEvaluator

**Файл:** `feature/replay/src/main/kotlin/com/example/npc/feature/replay/engine/VirtualEffectEvaluator.kt` (создать)  
**Модуль:** `:feature:replay`  
**Спека:** `.sdd/specs/pipeline-replay/overview.md#32-виртуальный-перехват-сайд-эффектов-virtualeffectevaluator`  
**Контракт:** `.sdd/contracts/pipeline-replay__runtime.md#1-архитектурный-контекст-и-песочница-zero-side-effects`  

**Сигнатура (НЕ МЕНЯТЬ):**
```kotlin
package com.example.npc.feature.replay.engine

import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.pipeline.compiler.Signal
import com.example.npc.pipeline.nodes.api.effect.EffectBuffer
import com.example.npc.pipeline.nodes.api.effect.EffectKindId
import com.example.npc.pipeline.nodes.api.effect.EffectView

data class EvaluationOutcome(
    val signal: Int,
    val category: Category,
    val confidence: Double,
    val transaction: FinancialTransaction?,
    val executionDurationNanos: Long
) {
    val isDropped: Boolean get() = signal == Signal.DROP
    val isFault: Boolean get() = signal == Signal.FAULT
    val isPassed: Boolean get() = signal == Signal.PASS
}

class VirtualEffectEvaluator {

    /**
     * Преобразует сырое содержимое буфера побочных эффектов в виртуальный результат в памяти
     * без вызова StorageGateway и без мутаций базы данных SQLite.
     *
     * @param signal Терминальный сигнал выполнения конвейера (Signal.PASS, Signal.DROP, Signal.FAULT).
     * @param effectBuffer Предвыделенный плоский буфер эффектов узлов конвейера.
     * @param durationNanos Измеренное время выполнения конвейера в наносекундах.
     * @return [EvaluationOutcome] с извлеченной категорией, уверенностью и транзакцией.
     */
    fun evaluate(
        signal: Int,
        effectBuffer: EffectBuffer,
        durationNanos: Long
    ): EvaluationOutcome {
        if (signal == Signal.DROP) {
            return EvaluationOutcome(
                signal = Signal.DROP,
                category = Category.UNCLASSIFIED,
                confidence = 0.0,
                transaction = null,
                executionDurationNanos = durationNanos
            )
        }

        var resolvedCategory = Category.UNCLASSIFIED
        var resolvedConfidence = 0.0
        var resolvedTransaction: FinancialTransaction? = null

        val view = EffectView()
        effectBuffer.openView(view)

        while (view.hasNext()) {
            view.next()
            when (view.kindId) {
                EffectKindId.SET_CATEGORY -> {
                    val categoryOrdinal = view.getIntArg(0)
                    resolvedCategory = Category.entries.getOrElse(categoryOrdinal) { Category.UNCLASSIFIED }
                    resolvedConfidence = view.getDoubleArg(1)
                }
                EffectKindId.CREATE_FINANCIAL_TRANSACTION -> {
                    resolvedTransaction = view.getRefArg(0) as? FinancialTransaction
                }
            }
        }

        return EvaluationOutcome(
            signal = signal,
            category = resolvedCategory,
            confidence = resolvedConfidence,
            transaction = resolvedTransaction,
            executionDurationNanos = durationNanos
        )
    }
}
```

**Поведение:**
1. Предоставляет безаллокационный вычислитель побочных эффектов в оперативной памяти (Sandbox Interceptor).
2. Принимает терминальный сигнал (`Signal.PASS`, `Signal.DROP`, `Signal.FAULT`), буфер `EffectBuffer` и замер времени `durationNanos`.
3. При `signal == Signal.DROP`:
   - Немедленно возвращает `EvaluationOutcome` со статусом подавления (`isDropped = true`), дефолтной категорией `UNCLASSIFIED` и отсутствующей транзакцией `null` (буфер игнорируется).
4. При нормальном завершении (`Signal.PASS`):
   - Открывает итератор `EffectView` над `EffectBuffer`.
   - Декодирует аргументы `SET_CATEGORY`: индекс категории (Int ordinal) преобразует в `Category`, читает `confidence` (Double).
   - Декодирует аргументы `CREATE_FINANCIAL_TRANSACTION`: читает ссылку `FinancialTransaction` из REF-слота буфера.
5. Формирует и возвращает иммутабельный `EvaluationOutcome` для последующего дифференциального сравнения в `ReplayEngineImpl`.

**Ошибки:**
- Не выбрасывает проверяемых исключений наружу.
- Если в `SET_CATEGORY` передан некорректный ordinal (выходящий за пределы `Category.entries`), выполняет fallback на `Category.UNCLASSIFIED`.
- Если объект в REF-слоте `CREATE_FINANCIAL_TRANSACTION` не приводится к `FinancialTransaction`, `transaction` остается `null`.

**Граничные случаи:**
- Буфер `effectBuffer` пуст (`count == 0`) -> `category = Category.UNCLASSIFIED`, `confidence = 0.0`, `transaction = null`.
- Несколько вызовов `SET_CATEGORY` в буфере -> побеждает последний эффект (`LAST_WINS`), в соответствии со стандартной политикой слияния SPI.
- Сигнал `Signal.FAULT` (авария стадии) -> буфер эффектов сбрасывается, `EvaluationOutcome.isFault = true`.

**Запрещено:**
- Обращаться к классам Room, DAO, репозиториям или выполнять I/O операции.
- Мутировать переданный экземпляр `EffectBuffer`.
- Использовать Java Reflection (`Class.forName`, `getDeclaredMethod` и т.д.).

**Критерий приёмки:**
- Файл скомпилирован в модуле `:feature:replay`.
- Проходят юнит-тесты:
  * Обработка `Signal.DROP` (проверка нулевых значений).
  * Извлечение `SET_CATEGORY` и `CREATE_FINANCIAL_TRANSACTION` из наполненного `EffectBuffer`.
  * Корректная обработка пустого буфера и неизвестного `kindId`.
