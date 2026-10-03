## Задача COMPILER-P2-005: Реализовать Pass3DataflowAnalyzer и типизацию

**Модуль:** `:pipeline:compiler`  
**Целевой файл:** `pipeline/compiler/src/main/kotlin/com/example/npc/pipeline/compiler/pass/Pass3DataflowAnalyzer.kt`  
**Спецификация:** `.sdd/specs/pipeline-compiler/overview.md#43-pass-3-проверка-типов-контрактов-данных-и-dataflow-p3xxx`  
**Контракт:** `.sdd/contracts/pipeline-dsl__compiler.md#43-узлы-трансформации-данных-transformdefinition`

### 1. Сигнатура (Signatures / Types):
```kotlin
package com.example.npc.pipeline.compiler.pass

import com.example.npc.pipeline.compiler.diagnostic.CompilationDiagnostic
import com.example.npc.pipeline.dsl.PipelineDefinition

enum class DataType {
    STRING,
    LONG,
    DOUBLE,
    BOOLEAN,
    OBJECT_TRANSACTION,
    POISON
}

data class VariableSymbol(
    val name: String,
    val type: DataType,
    val definedAtStageId: String,
    val slotIndex: Int
)

data class DataflowAnalysisResult(
    val diagnostics: List<CompilationDiagnostic>,
    val symbolTable: Map<String, VariableSymbol>,
    val requiredInputMask: Long
)

interface Pass3DataflowAnalyzer {
    /**
     * Выполняет статический анализ потока данных (Dataflow Definite Assignment),
     * проверку контрактов узлов, контроль типизации переменных и расчет битовой маски входов.
     *
     * @param definition Исходное декларативное описание конвейера.
     * @return [DataflowAnalysisResult] с таблицей символов, маской входов и списком диагностик (P3xxx).
     */
    fun analyze(definition: PipelineDefinition): DataflowAnalysisResult

    companion object {
        fun create(): Pass3DataflowAnalyzer
    }
}
```

### 2. Поведение и алгоритм (Behavior & Algorithm):
1. **Пространства имен и таблица символов (`SymbolTable`):**
   - Системные поля `input.*` (`packageName`, `title`, `text`, `sender`, `postTime`, `channelId`) регистрируются как доступные только для чтения. При попытке записи в них формируется ошибка `P3003` (ERROR).
   - Пользовательские контекстные переменные `ctx.*`: тип фиксируется при первой записи (Single-Type Discipline). Последующие записи должны совпадать по типу (`DataType`), иначе формируется ошибка `P3004` (Type Mismatch).
2. **Анализ определенности значений (Definite Assignment Forward Must-Analysis):**
   - Проход по графу стадий в топологическом порядке.
   - Состояние инициализации кодируется битовой маской `assignedMask: LongArray`.
   - В точках ветвления и пропуска стадий (условные этапы с `gate condition`):
     * Пересечение масок: $M_{\text{merge}} = M_1 \cap M_2$.
     * Если переменная читается, но отсутствует в $M_1 \cup M_2$ — формируется ошибка `P3001` (Use Before Definition).
     * Если переменная определена на одном пути, но пропущена на другом ($M_1 \cup M_2 \neq M_1 \cap M_2$) — формируется ошибка `P3002` с указанием этапа, на котором отсутствует инициализация.
3. **Проверка контрактов встроенных узлов (Node Contracts):**
   - `AmountParse`: требует, чтобы `sourceVar` имел тип `STRING`. Объявляет `targetVar` с типом `LONG`.
   - `CurrencyResolve`: требует `sourceVar` типа `STRING`. Объявляет `targetVar` с типом `STRING`.
   - `FinanceExtract`: объявляет `targetVar` с типом `OBJECT_TRANSACTION`.
   - `CreateTransaction`: проверяет, что `amountVar` имеет тип `LONG`, а `currencyVar` имеет тип `STRING`. При отсутствии переменных или несовпадении типов формируется `P3004`/`P3005`.
   - `SetCategory`: проверяет `confidence in 0.0..1.0`.
4. **Анализ мертвого кода переменных (Liveness Backward May-Analysis):**
   - Если переменная записана трансформацией, но нигде не читается в последующих этапах — предупреждение `P3101` (Dead Store).
   - Если переменная объявлена, но не передана ни в одно действие — предупреждение `P3102` (Unused Variable).
5. **Расчет `requiredInputMask`:**
   - Компилятор побитово объединяет флаги обращений к полям `input.*` (Бит 0: Title, Бит 1: Text, Бит 2: Sender, Бит 3: PostTime, Бит 4: ChannelId, Бит 5: PackageName).

### 3. Ошибки и валидация (Errors & Diagnostics):
Генерируемые диагностические коды:
- `P3001` (ERROR): `Variable '{0}' is used before being defined`.
- `P3002` (ERROR): `Variable '{0}' is not defined on all execution paths reaching this stage`.
- `P3003` (ERROR): `Cannot write to read-only input field '{0}'`.
- `P3004` (ERROR): `Type mismatch for variable '{0}'. Expected {1}, but got {2}`.
- `P3005` (ERROR): `Node contract violation: {0}`.
- `P3101` (WARNING): `Value assigned to variable '{0}' is never read (Dead Store)`.
- `P3102` (WARNING): `Variable '{0}' is declared but never consumed in actions (Unused Variable)`.

### 4. Граничные случаи (Edge Cases):
- Обращение к переменной, объявленной на безусловном предшествующем этапе: валидно.
- Обращение к переменной, объявленной внутри условного этапа: ошибка `P3002`.
- Ошибочный узел помечается типом `DataType.POISON` — дальнейшие зависимые этапы не порождают лавину повторных ошибок (Poisoning).
- Конвейер без трансформаций: анализ проходит корректно с пустой таблицей символов.

### 5. Запрещено (Constraints / Anti-patterns):
- ЗАПРЕЩЕНО использовать динамическую типизацию (Union Types) в рамках v1.
- ЗАПРЕЩЕНО использовать runtime map-lookup для переменных — каждой переменной обязан быть сопоставлен статический `slotIndex`.
- ЗАПРЕЩЕНО игнорировать неинициализированные ветви в точках слияния.

### 6. Критерии приемки (DoD):
- [ ] Интерфейс и класс `Pass3DataflowAnalyzerImpl` скомпилированы в `:pipeline:compiler`.
- [ ] 100% покрытие Unit-тестами:
  - Корректное распознавание Use-Before-Definition (P3001).
  - Распознавание частично определенных переменных на путях слияния (P3002).
  - Запрет записи в `input.*` (P3003).
  - Валидация контрактов `AmountParse`, `CurrencyResolve`, `CreateTransaction` (P3004, P3005).
  - Генерация предупреждений о мертвом коде (P3101, P3102).
  - Корректный расчет битовой маски `requiredInputMask`.
