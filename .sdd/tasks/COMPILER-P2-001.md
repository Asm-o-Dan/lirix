## Задача COMPILER-P2-001: Реализовать CompilationDiagnostic и модель локализации

**Модуль:** `:pipeline:compiler`  
**Целевой файл:** `pipeline/compiler/src/main/kotlin/com/example/npc/pipeline/compiler/diagnostic/CompilationDiagnostic.kt`  
**Спецификация:** `.sdd/specs/pipeline-compiler/overview.md#22-модель-диагностик-compilationdiagnostic-и-локализация-в-ui`  
**Контракт:** `.sdd/contracts/pipeline-dsl__compiler.md#1-архитектурный-контекст-и-границы`

### 1. Сигнатура (Signatures / Types):
```kotlin
package com.example.npc.pipeline.compiler.diagnostic

enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    INFO
}

enum class Target {
    VALUE,
    KEY
}

data class TextSpan(
    val start: Int,
    val end: Int
) {
    init {
        require(start >= 0) { "Start must be non-negative: $start" }
        require(end >= start) { "End ($end) must be >= start ($start)" }
    }
}

data class SourceLocation(
    val jsonPath: String,
    val target: Target = Target.VALUE,
    val stageId: String? = null,
    val span: TextSpan? = null
)

data class QuickFix(
    val id: String,
    val title: String,
    val targetPath: String,
    val replacementJson: String
)

data class CompilationDiagnostic(
    val code: String,
    val severity: DiagnosticSeverity,
    val message: String,
    val messageKey: String,
    val messageArgs: List<String> = emptyList(),
    val location: SourceLocation,
    val relatedLocations: List<SourceLocation> = emptyList(),
    val hint: String? = null,
    val quickFixes: List<QuickFix> = emptyList()
)
```

### 2. Поведение и алгоритм (Behavior & Algorithm):
1. Структура представляет иммутабельное диагностическое сообщение компилятора и статического анализатора.
2. `TextSpan`: представляет полуоткрытый интервал `[start, end)` в UTF-16 code units декодированной строки. Соответствует `String` в Kotlin и `TextRange` в Compose.
3. `SourceLocation`:
   - `jsonPath`: формируется строго в нормализованной нотации RFC 9535 (например, `$['stages'][2]['condition']['conditions'][1]['pattern']`).
   - `target`: указывает, что именно вызвало ошибку — значение поля (`Target.VALUE`) или неподдерживаемое имя ключа (`Target.KEY`).
   - `stageId`: содержит стабильный строковый ID ближайшего этапа-предка для сохранения привязки при редактировании списка этапов в UI.
4. `CompilationDiagnostic`: содержит стабильный машиночитаемый код (например, `P1001`, `P2002`, `P3001`), локализационный ключ `messageKey`, параметры подстановки `messageArgs`, человекочитаемое сообщение `message` на английском языке (дефолтная локализация) и опциональные автоисправления `quickFixes`.

### 3. Ошибки и валидация (Errors & Diagnostics):
- `TextSpan.init`: выбрасывает `IllegalArgumentException`, если `start < 0` или `end < start`.
- Валидация входных аргументов `CompilationDiagnostic`: `code` не должен быть пустым (`code.isNotBlank()`), `message` не должно быть пустым.

### 4. Граничные случаи (Edge Cases):
- Нулевой диапазон `start == end`: допустим для обозначения курсора вставки символа в строке.
- Ошибка верхнего уровня конвейера (не внутри этапа): `stageId == null`.
- Отсутствие смещения внутри строки: `span == null`.
- Пустые списки `messageArgs`, `relatedLocations`, `quickFixes`: инициализируются `emptyList()` по умолчанию.

### 5. Запрещено (Constraints / Anti-patterns):
- ЗАПРЕЩЕНО использовать зависимости `android.*`, `androidx.*` или Compose `TextRange` (модуль является чистым JVM).
- ЗАПРЕЩЕНО использовать произвольный формат путей (например, `/stages/0/id` или `stages.0.id`). Использовать строго нормализованный RFC 9535 (`$['stages'][0]['id']`).
- ЗАПРЕЩЕНО использовать мутабельные коллекции (`ArrayList`, `MutableList`) в свойствах данных классов.

### 6. Критерии приемки (DoD):
- [ ] Файл `CompilationDiagnostic.kt` скомпилирован в модуле `:pipeline:compiler`.
- [ ] Все типы (`DiagnosticSeverity`, `Target`, `TextSpan`, `SourceLocation`, `QuickFix`, `CompilationDiagnostic`) объявлены и иммутабельны.
- [ ] Unit-тесты проверяют валидацию `TextSpan`, значения по умолчанию и форматирование.
