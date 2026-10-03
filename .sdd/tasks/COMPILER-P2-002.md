## Задача COMPILER-P2-002: Реализовать CompilationResult и иерархию результатов

**Модуль:** `:pipeline:compiler`  
**Целевой файл:** `pipeline/compiler/src/main/kotlin/com/example/npc/pipeline/compiler/result/CompilationResult.kt`  
**Спецификация:** `.sdd/specs/pipeline-compiler/overview.md#21-интерфейс-pipelinecompiler-и-результат-компиляции`  
**Контракт:** `.sdd/contracts/pipeline-dsl__compiler.md#1-архитектурный-контекст-и-границы`

### 1. Сигнатура (Signatures / Types):
```kotlin
package com.example.npc.pipeline.compiler.result

import com.example.npc.pipeline.compiler.diagnostic.CompilationDiagnostic
import com.example.npc.pipeline.compiler.diagnostic.DiagnosticSeverity
import com.example.npc.pipeline.compiler.model.CompiledPipeline

sealed interface CompilationResult {
    val diagnostics: List<CompilationDiagnostic>

    val hasErrors: Boolean
        get() = diagnostics.any { it.severity == DiagnosticSeverity.ERROR }

    val hasWarnings: Boolean
        get() = diagnostics.any { it.severity == DiagnosticSeverity.WARNING }

    val errors: List<CompilationDiagnostic>
        get() = diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }

    val warnings: List<CompilationDiagnostic>
        get() = diagnostics.filter { it.severity == DiagnosticSeverity.WARNING }

    data class Success(
        val pipeline: CompiledPipeline,
        override val diagnostics: List<CompilationDiagnostic> = emptyList()
    ) : CompilationResult {
        init {
            require(diagnostics.none { it.severity == DiagnosticSeverity.ERROR }) {
                "CompilationResult.Success cannot contain ERROR diagnostics, but found: ${diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }}"
            }
        }
    }

    data class Failure(
        override val diagnostics: List<CompilationDiagnostic>
    ) : CompilationResult {
        init {
            require(diagnostics.any { it.severity == DiagnosticSeverity.ERROR }) {
                "CompilationResult.Failure must contain at least one ERROR diagnostic, but got none"
            }
        }
    }
}
```

### 2. Поведение и алгоритм (Behavior & Algorithm):
1. `CompilationResult`: корневой запечатанный интерфейс для возврата результата из `PipelineCompiler.compile()`.
2. `Success`: инкапсулирует валидированный и скомпилированный `CompiledPipeline`. Может содержать список нефатальных замечаний (`DiagnosticSeverity.WARNING` и `DiagnosticSeverity.INFO`), отсортированных детерминированно в порядке следования узлов в документе.
3. `Failure`: сигнализирует о невозможности построения исполняемого графа из-за фатальных нарушений спецификации или семантики. Содержит полный список ошибок и сопутствующих предупреждений.
4. Вычисляемые свойства: `hasErrors`, `hasWarnings`, `errors`, `warnings` обеспечивают удобный доступ к отфильтрованным спискам диагностик без дублирования фильтрации в клиентском коде (UI, CLI, Replay).

### 3. Ошибки и валидация (Errors & Diagnostics):
- Инвариант `Success`: конструктор выбрасывает `IllegalArgumentException`, если среди переданных диагностик присутствует хотя бы одна с уровнем `DiagnosticSeverity.ERROR`.
- Инвариант `Failure`: конструктор выбрасывает `IllegalArgumentException`, если передан пустой список или список, не содержащий ни одной диагностики с уровнем `DiagnosticSeverity.ERROR`.

### 4. Граничные случаи (Edge Cases):
- `Success` с пустым списком диагностик (`diagnostics = emptyList()`): штатный сценарий при идеальном исходном коде конвейера.
- `Failure` с множественными ошибками (`diagnostics.count { it.severity == ERROR } > 1`): корректный сценарий; компилятор аккумулирует все ошибки без преждевременного fail-fast.
- `Failure` со смешанными ошибками и предупреждениями (`ERROR` + `WARNING` + `INFO`): все диагностики сохраняются в исходном порядке.

### 5. Запрещено (Constraints / Anti-patterns):
- ЗАПРЕЩЕНО создавать инстанс `Success` с ошибками `ERROR`.
- ЗАПРЕЩЕНО создавать инстанс `Failure` без единой ошибки `ERROR`.
- ЗАПРЕЩЕНО использовать мутируемые списки в `diagnostics`.

### 6. Критерии приемки (DoD):
- [ ] Файл `CompilationResult.kt` скомпилирован в модуле `:pipeline:compiler`.
- [ ] Реализованы классы `Success` и `Failure` со строгими проверками инвариантов.
- [ ] Unit-тесты проверяют:
  - Успешное создание `Success` с пустым списком диагностик и с предупреждениями.
  - Выброс `IllegalArgumentException` при попытке создать `Success` с ошибкой `ERROR`.
  - Успешное создание `Failure` со списком, содержащим `ERROR`.
  - Выброс `IllegalArgumentException` при попытке создать `Failure` без ошибок.
  - Корректность вычисляемых свойств `hasErrors`, `hasWarnings`, `errors`, `warnings`.
