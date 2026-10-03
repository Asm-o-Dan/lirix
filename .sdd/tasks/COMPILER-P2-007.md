## Задача COMPILER-P2-007: Реализовать CompiledPipeline, CompiledStage и Signal

**Модуль:** `:pipeline:compiler`  
**Целевые файлы:** `pipeline/compiler/src/main/kotlin/com/example/npc/pipeline/compiler/`  
- `Signal.kt`
- `CompiledStage.kt`
- `CompiledPipeline.kt`
- `DebugInfo.kt`  
**Спецификация:** `.sdd/specs/pipeline-compiler/overview.md#5-модель-исполнения-compiledpipeline-иммутабельный-граф`  
**Контракт:** `.sdd/contracts/pipeline-runtime__compiler.md#2-модель-исполнения-compiledpipeline-и-сигналы-signal`

### 1. Сигнатура (Signatures / Types):
```kotlin
package com.example.npc.pipeline.compiler

import com.example.npc.pipeline.nodes.api.frame.Frame
import com.example.npc.pipeline.nodes.api.frame.FrameLayout
import com.google.re2j.Pattern

object Signal {
    const val PASS: Int = -1
    const val DROP: Int = -2
    const val FAULT: Int = -3
}

interface CompiledStage {
    val stageId: String
    val stageIndex: Int
    fun execute(frame: Frame): Int
}

data class DebugInfo(
    val stageNames: Map<String, String>,
    val variableNames: Map<Int, String>
)

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
    val stagesCount: Int get() = stages.size

    fun execute(frame: Frame): Int {
        val stageArray = stages
        var pc = 0
        while (pc >= 0) {
            pc = stageArray[pc].execute(frame)
        }
        return pc
    }
}
```

### 2. Поведение и алгоритм (Behavior & Algorithm):
1. **`Signal`:** фиксирует целочисленные константы терминальных сигналов для горячего пути:
   - `PASS (-1)`: конвейер завершился успешно, буферизованные эффекты коммитятся.
   - `DROP (-2)`: событие отфильтровано или подавлено, эффекты сбрасываются.
   - `FAULT (-3)`: изолированный сбой этапа.
2. **`CompiledStage`:** базовый интерфейс скомпилированного шага:
   - Метод `execute(frame): Int` принимает предвыделенный `Frame` текущего потока и возвращает либо индекс следующей стадии (`PC >= 0`), либо терминальный сигнал (`< 0`).
   - Реализации этапов (`BranchStage`, `AssignRefStage`, `EmitEffectStage`, `TerminateStage`) являются финальными мономорфными классами без динамической диспетчеризации `when (type)`.
3. **`CompiledPipeline`:**
   - Иммутабельный контейнер скомпилированного графа.
   - Метод `execute(frame): Int` исполняет цикл `while (pc >= 0) pc = stageArray[pc].execute(frame)`.
   - **0 аллокаций памяти** в Heap в процессе всего исполнения.
   - **Zero Reflection** — никаких `Method.invoke()` или `KCallable`.
4. **`DebugInfo`:** внешняя вспомогательная таблица (Side-Table) для маппинга номеров стадий и слотов на имена из DSL для отладки и UI, не нагружающая горячий кеш процессора.

### 3. Ошибки и валидация (Errors & Diagnostics):
- Конструктор `CompiledPipeline`: проверяет, что `stages.isNotEmpty()`, `revision >= 1L`, `canonicalHash.isNotBlank()`.
- Выход за границы массива `stages`: невозможен благодаря статической верификации Pass 4 (все PC строго в диапазоне `0 until stages.size` либо `< 0`).

### 4. Граничные случаи (Edge Cases):
- Пайплайн с одним этапом, завершающимся `Signal.PASS`: цикл делает 1 итерацию и выходит.
- Завершение на раннем этапе (Early Exit / Drop): цикл немедленно завершается при получении `Signal.DROP`.
- Пакет события отсутствует в `packageWhitelistSet`: проверка выполняется на уровне рантайма до вызова `execute(frame)`.

### 5. Запрещено (Constraints / Anti-patterns):
- ЗАПРЕЩЕНО выделять объекты (new / constructors), лямбды с захватом или коллекции внутри `execute(frame)`.
- ЗАПРЕЩЕНО использовать `java.util.regex.Pattern` вместо `com.google.re2j.Pattern`.
- ЗАПРЕЩЕНО использовать мутабельные поля (`var`) в свойствах `CompiledPipeline`.

### 6. Критерии приемки (DoD):
- [ ] Все 4 файла (`Signal.kt`, `CompiledStage.kt`, `CompiledPipeline.kt`, `DebugInfo.kt`) скомпилированы в `:pipeline:compiler`.
- [ ] Полное соответствие межзонному контракту `.sdd/contracts/pipeline-runtime__compiler.md`.
- [ ] Unit-тест выполнения графа стадий:
  - Корректная смена `pc` и возврат `Signal.PASS`.
  - Корректная обработка условного ветвления и раннего выхода с `Signal.DROP`.
  - Замер аллокаций: строго 0 байт аллокаций в Heap за один прогон `execute(frame)`.
