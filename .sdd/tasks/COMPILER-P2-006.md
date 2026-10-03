## Задача COMPILER-P2-006: Реализовать Pass4ControlFlowAnalyzer и анализ достижимости

**Модуль:** `:pipeline:compiler`  
**Целевой файл:** `pipeline/compiler/src/main/kotlin/com/example/npc/pipeline/compiler/pass/Pass4ControlFlowAnalyzer.kt`  
**Спецификация:** `.sdd/specs/pipeline-compiler/overview.md#44-pass-4-поток-управления-достижимость-и-бюджеты-сложности-p4xxx`  
**Контракт:** `.sdd/contracts/pipeline-dsl__compiler.md#3-схема-этапа-stagedefinition-линейный-шаг-конвейера`

### 1. Сигнатура (Signatures / Types):
```kotlin
package com.example.npc.pipeline.compiler.pass

import com.example.npc.pipeline.compiler.diagnostic.CompilationDiagnostic
import com.example.npc.pipeline.dsl.PipelineDefinition

data class ControlFlowAnalysisResult(
    val diagnostics: List<CompilationDiagnostic>,
    val deadStageIds: Set<String>,
    val estimatedRefSlots: Int,
    val estimatedPrimSlots: Int
)

interface Pass4ControlFlowAnalyzer {
    /**
     * Выполняет анализ графа потока управления (Control Flow Graph / CFG),
     * проверку ацикличности (DAG), обнаружение недостижимых этапов (Dead Stages),
     * свертку тривиальных условий и проверку лимитов слотов памяти.
     *
     * @param definition Исходное декларативное описание конвейера.
     * @return [ControlFlowAnalysisResult] с результатами анализа и предупреждениями.
     */
    fun analyze(definition: PipelineDefinition): ControlFlowAnalysisResult

    companion object {
        fun create(): Pass4ControlFlowAnalyzer
        const val MAX_REF_SLOTS = 128
        const val MAX_PRIM_SLOTS = 64
    }
}
```

### 2. Поведение и алгоритм (Behavior & Algorithm):
1. **Построение CFG и проверка ацикличности (DAG Invariant):**
   - Построение графа переходов между этапами.
   - Проверка, что все переходы направлены строго вперед: `targetIndex > currentIndex`.
   - Если обнаруживается переход назад или цикл, формируется ошибка `P4005` (ERROR).
2. **Анализ достижимости (Reachability & Dead Stage Detection):**
   - Трассировка графа от входного этапа `stages[0]`.
   - Если этап содержит безусловное раннее завершение (`terminateOnMatch == true` при `condition == null` или `AlwaysTrue`), либо безусловное действие `DropEvent` / `StopProcessing`:
     * Управление никогда не перейдет к следующим этапам.
     * Все последующие этапы помечаются как недостижимые (`deadStageIds`).
     * Для каждого недостижимого этапа генерируется предупреждение `P4001` (WARNING) на `$['stages'][i]`.
3. **Свертка тривиальных условий (Constant Folding):**
   - Анализ условий `ConditionDefinition`:
     * `LogicalAnd` содержащий `AlwaysFalse` -> ветка никогда не выполнится, предупреждение `P4002` (WARNING).
     * `LogicalOr` содержащий `AlwaysTrue` -> ветка выполняется всегда, предупреждение `P4002` (WARNING).
     * `LogicalNot(AlwaysTrue)` -> сворачивается в `AlwaysFalse` (`P4002`).
4. **Контроль бюджетов регистровой памяти:**
   - Подсчет общего количества ссылочных переменных (String, DTO) и примитивных (Long, Double, Boolean).
   - Если `estimatedRefSlots > 128` или `estimatedPrimSlots > 64`, формируется ошибка `P4006` (ERROR).

### 3. Ошибки и валидация (Errors & Diagnostics):
Генерируемые диагностические коды:
- `P4001` (WARNING): `Stage '{0}' is unreachable due to preceding unconditional termination`.
- `P4002` (WARNING): `Condition trivially evaluates to constant ({0}) at compile time`.
- `P4005` (ERROR): `Non-DAG control flow detected. Cycles and backward jumps are prohibited`.
- `P4006` (ERROR): `Memory slot complexity limit exceeded. Ref slots: {0} (max 128), Prim slots: {1} (max 64)`.

### 4. Граничные случаи (Edge Cases):
- Терминирующий этап с условием (`terminateOnMatch == true` при `condition != null`): последующие этапы достижимы (если условие вернет false), ошибка `P4001` НЕ генерируется.
- Безусловная терминация на последнем этапе конвейера: допустимо, `P4001` не создается, так как последующих этапов нет.
- Все этапы достижимы: `deadStageIds` пуст, предупреждений нет.

### 5. Запрещено (Constraints / Anti-patterns):
- ЗАПРЕЩЕНО допускать циклические переходы в графе выполнения.
- ЗАПРЕЩЕНО игнорировать безусловные `terminateOnMatch: true`.
- ЗАПРЕЩЕНО выделять бесконечное количество слотов без проверки лимитов `MAX_REF_SLOTS` / `MAX_PRIM_SLOTS`.

### 6. Критерии приемки (DoD):
- [ ] Интерфейс и класс `Pass4ControlFlowAnalyzerImpl` скомпилированы в `:pipeline:compiler`.
- [ ] 100% покрытие Unit-тестами:
  - Детекция мертвых стадий после безусловного `terminateOnMatch: true` (P4001).
  - Детекция мертвых стадий после безусловного `DropEvent` (P4001).
  - Обнаружение тривиальных условий (P4002).
  - Проверка соблюдения лимитов слотов (P4006).
  - Тест на эталонном пресете `preset-legacy-1.1.json` не содержит предупреждений о мертвых стадиях.
