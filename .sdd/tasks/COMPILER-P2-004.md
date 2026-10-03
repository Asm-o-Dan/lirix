## Задача COMPILER-P2-004: Реализовать Pass2RegexValidator и ReDoS Guard

**Модуль:** `:pipeline:compiler`  
**Целевой файл:** `pipeline/compiler/src/main/kotlin/com/example/npc/pipeline/compiler/pass/Pass2RegexValidator.kt`  
**Спецификация:** `.sdd/specs/pipeline-compiler/overview.md#42-pass-2-валидация-регулярных-выражений-re2j-и-redos-guard-p2xxx`  
**Контракт:** `.sdd/contracts/pipeline-dsl__compiler.md#42-логические-предикаты-conditiondefinition`

### 1. Сигнатура (Signatures / Types):
```kotlin
package com.example.npc.pipeline.compiler.pass

import com.example.npc.pipeline.compiler.diagnostic.CompilationDiagnostic
import com.example.npc.pipeline.dsl.PipelineDefinition
import com.google.re2j.Pattern

data class RegexValidationResult(
    val diagnostics: List<CompilationDiagnostic>,
    val compiledPatterns: Map<String, Pattern>,
    val literalLoweringCandidates: Set<String>
)

interface Pass2RegexValidator {
    /**
     * Выполняет прекомпиляцию регулярных выражений через RE2/J и проверку устойчивости к ReDoS.
     * Запрещает бэктрекинг-конструкции, проверяет длину и раздувание автоматов.
     *
     * @param definition Исходное декларативное описание конвейера.
     * @return [RegexValidationResult] с накопленными диагностиками, прекомпилированными RE2/J паттернами и оптимизациями.
     */
    fun validate(definition: PipelineDefinition): RegexValidationResult

    companion object {
        fun create(): Pass2RegexValidator
        const val MAX_REGEX_LENGTH = 256
        const val MAX_REPETITION_EXPANSION = 1000
    }
}
```

### 2. Поведение и алгоритм (Behavior & Algorithm):
1. Валидатор обходит все условия `ConditionDefinition` и трансформации в AST, извлекая экземпляры `ConditionDefinition.TextRegexMatch`.
2. Для каждого регулярного выражения:
   - **Проверка длины:** если `pattern.length > 256`, формируется `P2001` (ERROR) на путь свойства `$['stages'][s]['condition']...['pattern']`.
   - **Токенизация / Pre-scan на запрещенные бэктрекинг-конструкции:**
     * Поиск опережающих и ретроспективных проверок: `(?=`, `(?!`, `(?<=`, `(?<!` -> ошибка `P2003` (ERROR) с точным `TextSpan` расположения токена.
     * Поиск обратных ссылок: `\1` .. `\9` -> ошибка `P2004` (ERROR) с `TextSpan`.
     * Поиск ревнивых/притяжательных квантификаторов: `*+`, `++`, `?+` -> ошибка `P2003` (ERROR) с `TextSpan`.
   - **Прекомпиляция через RE2/J:**
     * Вызывается `com.google.re2j.Pattern.compile(pattern, flags)`. Флаги: если `caseSensitive == false`, применяется `Pattern.CASE_INSENSITIVE`.
     * При возникновении `com.google.re2j.PatternSyntaxException`: перехватывается, извлекается индекс ошибки `e.index` и формируется `P2002` (ERROR) с `TextSpan(e.index, min(e.index + 1, pattern.length))`.
   - **Анализ вложенных повторений (Repetition Expansion Guard):**
     * Вычисляется произведение вложенных квантификаторов (например, `{100}` внутри `{100}` дает $10\,000$).
     * Если произведение $> 1000$, формируется ошибка `P2005` (ERROR).
   - **Детекция литералов (Literal Lowering):**
     * Если строка не содержит специальных символов регулярных выражений (`.*+?^$()[]{}|\\`), генерируется информационная диагностика `P2101` (INFO) и паттерн добавляется в `literalLoweringCandidates` для замены на быстрый строковый поиск в фазе Lowering.
3. Успешно скомпилированные `Pattern` сохраняются в результирующей карте `compiledPatterns` по ключу нормализованного пути узла.

### 3. Ошибки и валидация (Errors & Diagnostics):
Генерируемые диагностические коды:
- `P2001` (ERROR): `Regex pattern length ({0}) exceeds maximum allowed 256 characters`.
- `P2002` (ERROR): `Invalid RE2 syntax: {0}`.
- `P2003` (ERROR): `Unsupported backtracking regex syntax '{0}' (lookaround or possessive quantifier). RE2 requires linear-time constructs`.
- `P2004` (ERROR): `Backreferences are not supported in RE2 regex: '{0}'`.
- `P2005` (ERROR): `Repetition expansion limit exceeded ({0} > 1000). Risk of state explosion`.
- `P2101` (INFO): `Pattern '{0}' contains no regex metacharacters and will be lowered to direct string search`.

### 4. Граничные случаи (Edge Cases):
- Пайплайн без регулярных выражений: валидатор отрабатывает мгновенно, возвращая пустую карту паттернов и пустой список диагностик.
- Паттерн ровно 256 символов: допустимо. 257 символов: `P2001`.
- Символы экранирования: `\(` или `\[` считаются литералами экранированных символов, а не открытием группы/класса.
- Флаг `caseSensitive == false`: транслируется в флаг компилятора RE2/J `Pattern.CASE_INSENSITIVE`.

### 5. Запрещено (Constraints / Anti-patterns):
- СТРОЖАЙШЕ ЗАПРЕЩЕНО импортировать или использовать `java.util.regex.*` и `kotlin.text.Regex`. Только `com.google.re2j.*`.
- ЗАПРЕЩЕНО падать по `PatternSyntaxException` — исключение обязано перехватываться и преобразовываться в `P2002`.
- ЗАПРЕЩЕНО производить матчинг на строках на фазе валидации (только синтаксический разбор и построение автомата DFA/NFA).

### 6. Критерии приемки (DoD):
- [ ] Интерфейс и класс `Pass2RegexValidatorImpl` скомпилированы в `:pipeline:compiler`.
- [ ] Интеграция с библиотекой RE2/J (`com.google.re2j:re2j`).
- [ ] 100% покрытие Unit-тестами:
  - Корректная компиляция допустимых RE2 паттернов.
  - Перехват синтаксических ошибок (P2002) с валидным `TextSpan`.
  - Отклонение lookahead `(?=...)` и lookbehind `(?<=...)` (P2003).
  - Отклонение обратных ссылок `\1` (P2004).
  - Отклонение повторов $> 1000$ (P2005).
  - Выявление строковых литералов (P2101).
- [ ] Отсутствие упоминаний `java.util.regex` в байткоде класса.
