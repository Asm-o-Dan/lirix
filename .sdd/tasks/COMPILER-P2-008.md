## Задача COMPILER-P2-008: Реализовать фасад компилятора PipelineCompilerImpl

**Модуль:** `:pipeline:compiler`  
**Целевой файл:** `pipeline/compiler/src/main/kotlin/com/example/npc/pipeline/compiler/PipelineCompilerImpl.kt`  
**Спецификация:** `.sdd/specs/pipeline-compiler/overview.md#2-публичный-контракт-компилятора-pipelinecompiler`  
**Контракт:** `.sdd/contracts/pipeline-dsl__compiler.md#1-архитектурный-контекст-и-границы`

### 1. Сигнатура (Signatures / Types):
```kotlin
package com.example.npc.pipeline.compiler

import com.example.npc.pipeline.compiler.pass.Pass1StructuralValidator
import com.example.npc.pipeline.compiler.pass.Pass2RegexValidator
import com.example.npc.pipeline.compiler.pass.Pass3DataflowAnalyzer
import com.example.npc.pipeline.compiler.pass.Pass4ControlFlowAnalyzer
import com.example.npc.pipeline.compiler.result.CompilationResult
import com.example.npc.pipeline.dsl.PipelineDefinition

interface PipelineCompiler {
    fun compile(definition: PipelineDefinition): CompilationResult

    companion object {
        fun create(
            pass1: Pass1StructuralValidator = Pass1StructuralValidator.create(),
            pass2: Pass2RegexValidator = Pass2RegexValidator.create(),
            pass3: Pass3DataflowAnalyzer = Pass3DataflowAnalyzer.create(),
            pass4: Pass4ControlFlowAnalyzer = Pass4ControlFlowAnalyzer.create()
        ): PipelineCompiler {
            return PipelineCompilerImpl(pass1, pass2, pass3, pass4)
        }
    }
}

class PipelineCompilerImpl(
    private val pass1: Pass1StructuralValidator,
    private val pass2: Pass2RegexValidator,
    private val pass3: Pass3DataflowAnalyzer,
    private val pass4: Pass4ControlFlowAnalyzer
) : PipelineCompiler {
    override fun compile(definition: PipelineDefinition): CompilationResult
}
```

### 2. Поведение и алгоритм (Behavior & Algorithm):
1. **Глобальный перехват сбоев (No Unhandled Exceptions):**
   - Тело `compile()` обернуто в блок `try-catch (t: Throwable)`.
   - Любое непредвиденное исключение преобразуется в `CompilationDiagnostic` с кодом `E0000` (Internal Compiler Error), и метод возвращает `CompilationResult.Failure`, не давая процессу упасть.
2. **Последовательное выполнение 4 пассов валидации:**
   - Выполняется `val p1Diags = pass1.validate(definition)`.
   - Выполняется `val p2Result = pass2.validate(definition)`.
   - Выполняется `val p3Result = pass3.analyze(definition)`.
   - Выполняется `val p4Result = pass4.analyze(definition)`.
   - Все полученные диагностики объединяются: `val allDiags = p1Diags + p2Result.diagnostics + p3Result.diagnostics + p4Result.diagnostics`.
3. **Гейт ошибок:**
   - Если `allDiags.any { it.severity == DiagnosticSeverity.ERROR }`:
     * Фаза Lowering **не запускается**.
     * Диагностики сортируются детерминированно: по `jsonPath`, затем по `severity`, затем по `code`.
     * Возвращается `CompilationResult.Failure(sortedDiags)`.
4. **Фаза Lowering (понижение AST в исполняемый граф):**
   - Вычисляется канонический SHA-256 хеш исходного конвейера через `PipelineJsonCodec`.
   - Формируется `FrameLayout` на основе данных из `Pass3` и `Pass4` (`refSlots`, `primSlots`, `matcherSlots = p2Result.compiledPatterns.size`).
   - Трансляция стадий `definition.stages` в массив `CompiledStage`:
     * Условные переходы связываются через индексы `PC`.
     * Паттерны `TextRegexMatch` связываются с индексами матчеров в `Frame.matchers`.
     * Литеральные паттерны (`literalLoweringCandidates`) заменяются на быстрые вызовы `ContainsConst` / `StartsWithConst`.
     * Терминальные флаги (`terminateOnMatch`) транслируются в переходы на `TerminateStage(Signal.PASS)`.
   - Собирается `DebugInfo` с человекочитаемыми именами для UI и логов.
   - Создается экземпляр `CompiledPipeline`.
   - Возвращается `CompilationResult.Success(compiledPipeline, allDiags.filter { it.severity != ERROR })`.

### 3. Ошибки и валидация (Errors & Diagnostics):
- `E0000` (ERROR): `Internal compiler error during processing: {0}` при возникновении непредвиденного `Throwable`.
- Аккумулирует и детерминированно сортирует все ошибки `P1xxx`, `P2xxx`, `P3xxx`, `P4xxx`.

### 4. Граничные случаи (Edge Cases):
- Входной AST содержит только нефатальные предупреждения (например, `P3101 DeadStore` или `P4001 DeadStage`): возвращается `CompilationResult.Success` со списком предупреждений.
- Входной AST содержит хотя бы одну ошибку `ERROR`: гарантированно возвращается `CompilationResult.Failure`.
- Исключение `OutOfMemoryError` при раздувании AST: перехватывается с генерацией `E0000`.

### 5. Запрещено (Constraints / Anti-patterns):
- ЗАПРЕЩЕНО выбрасывать любые непроверяемые исключения наружу из `compile()`.
- ЗАПРЕЩЕНО запускать Lowering при наличии ошибок с уровнем `ERROR`.
- ЗАПРЕЩЕНО использовать рефлексию или Android SDK зависимости.

### 6. Критерии приемки (DoD):
- [ ] Интерфейс `PipelineCompiler` и класс `PipelineCompilerImpl` скомпилированы.
- [ ] Golden-тест: компиляция эталонного пресета `preset-legacy-1.1.json` завершается `CompilationResult.Success` с 0 ошибок и 0 предупреждений.
- [ ] Тест изоляции исключений: искусственное исключение в одном из пассов корректно перехватывается и возвращается как `E0000` в `CompilationResult.Failure`.
- [ ] Тест бюджета времени: компиляция `preset-legacy-1.1.json` на JVM выполняется за время $< 5$ мс.
