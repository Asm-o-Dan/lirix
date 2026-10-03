## Задача COMPILER-P2-003: Реализовать Pass1StructuralValidator

**Модуль:** `:pipeline:compiler`  
**Целевой файл:** `pipeline/compiler/src/main/kotlin/com/example/npc/pipeline/compiler/pass/Pass1StructuralValidator.kt`  
**Спецификация:** `.sdd/specs/pipeline-compiler/overview.md#41-pass-1-структурная-валидация-и-проверка-ограничений-схемы-p1xxx`  
**Контракт:** `.sdd/contracts/pipeline-dsl__compiler.md#2-модели-схемы-dsl-pipelinedefinition-stagedefinition`

### 1. Сигнатура (Signatures / Types):
```kotlin
package com.example.npc.pipeline.compiler.pass

import com.example.npc.pipeline.compiler.diagnostic.CompilationDiagnostic
import com.example.npc.pipeline.dsl.PipelineDefinition

interface Pass1StructuralValidator {
    /**
     * Выполняет первичную структурную валидацию AST-дерева без глубокого семантического анализа.
     * Проверяет ограничения схемы DSL v1, уникальность ID, лимиты сложности и вложенности.
     *
     * @param definition Исходное декларативное описание конвейера.
     * @return Список обнаруженных диагностик (P1001..P1008, P4003, P4004).
     */
    fun validate(definition: PipelineDefinition): List<CompilationDiagnostic>

    companion object {
        fun create(): Pass1StructuralValidator
    }
}
```

### 2. Поведение и алгоритм (Behavior & Algorithm):
1. Валидатор проверяет верхнеуровневые свойства `PipelineDefinition`:
   - `schemaVersion == 1`: если не совпадает, генерирует `P1001` (ERROR) на путь `$['schemaVersion']`.
   - `id`: проверяется на регулярное выражение `^[a-z0-9_-]{3,64}$`. При нарушении — `P1002` (ERROR) на `$['id']`.
   - `name`: проверяется `name.isNotBlank()` и `name.length in 1..128`. При нарушении — `P1008` (ERROR) на `$['name']`.
   - `description`: если не null, проверяется `length <= 1024`.
   - `priority`: проверяется диапазон `0..1000`.
   - `triggers`: проверяется `triggers.isNotEmpty()`. При пустом списке — `P1004` (ERROR) на `$['triggers']`.
   - `stages`: проверяется `stages.isNotEmpty()` (иначе `P1005` на `$['stages']`) и `stages.size <= 50` (иначе `P1006` на `$['stages']`).
2. Проверка каждого этапа `stages[i]`:
   - Идентификатор `stage.id`: формат `^[a-z0-9_-]{3,64}$` (`P1002`).
   - Уникальность: поддерживается карта `seenStageIds: MutableMap<String, Int>`. При обнаружении дубликата генерируется `P1003` (ERROR) на `$['stages'][i]['id']` с `relatedLocations` на первый объявленный этап `$['stages'][seenStageIds[id]]['id']`.
   - `stage.name`: проверяется `isNotBlank()` и `length <= 128` (`P1008`).
   - `transforms` и `actions`: этап обязан содержать хотя бы одну трансформацию или действие (`transforms.isNotEmpty() || actions.isNotEmpty()`), иначе генерируется `P1007` (ERROR) на `$['stages'][i]`.
3. Рекурсивная проверка условий `stage.condition`:
   - Вычисление максимальной глубины логического дерева. Если глубина $> 5$, генерируется `P4003` (ERROR) на узел превышения с точным `jsonPath`.
   - Проверка арности `LogicalAnd` и `LogicalOr`: количество дочерних условий обязано быть в диапазоне $2..16$. При нарушении генерируется `P4004` (ERROR).

### 3. Ошибки и валидация (Errors & Diagnostics):
Генерируемые диагностические коды:
- `P1001` (ERROR): `Unsupported schemaVersion: {0}. Expected: 1`.
- `P1002` (ERROR): `Invalid ID format '{0}'. Must match ^[a-z0-9_-]{3,64}$`.
- `P1003` (ERROR): `Duplicate stage ID '{0}'. Stage IDs must be unique within pipeline`.
- `P1004` (ERROR): `Pipeline must declare at least one trigger`.
- `P1005` (ERROR): `Pipeline must contain at least one stage`.
- `P1006` (ERROR): `Pipeline exceeds maximum allowed stages count (50), got: {0}`.
- `P1007` (ERROR): `Stage '{0}' must declare at least one transform or action`.
- `P1008` (ERROR): `Name cannot be blank and must not exceed 128 chars`.
- `P4003` (ERROR): `Condition nesting depth exceeds maximum allowed limit (5)`.
- `P4004` (ERROR): `Logical operator '{0}' requires between 2 and 16 operands, but got: {1}`.

### 4. Граничные случаи (Edge Cases):
- Этап без условий (`condition == null`): допустимо, проверка условий пропускается.
- Этап с пустым списком трансформаций, но непустым списком действий: валидно.
- Этап с пустым списком действий, но непустым списком трансформаций: валидно.
- Глубина условий ровно 5: валидно. Глубина 6: ошибка `P4003`.
- Арность логических операторов ровно 2 или 16: валидно. 1 или 17: ошибка `P4004`.

### 5. Запрещено (Constraints / Anti-patterns):
- ЗАПРЕЩЕНО прерывать валидацию на первой ошибке (fail-fast). Все структурные дефекты должны быть собраны за один проход.
- ЗАПРЕЩЕНО выбрасывать исключения (`Exception`/`Throwable`) при невалидном входе — возвращать строго список `CompilationDiagnostic`.
- ЗАПРЕЩЕНО использовать зависимости Android OS или рефлексию.

### 6. Критерии приемки (DoD):
- [ ] Интерфейс и класс `Pass1StructuralValidatorImpl` скомпилированы в `:pipeline:compiler`.
- [ ] 100% покрытие Unit-тестами всех проверок (P1001..P1008, P4003, P4004).
- [ ] Тест на `preset-legacy-1.1.json` возвращает пустой список ошибок.
- [ ] Проверка детерминизма порядка возвращаемых диагностик (по порядку обхода в документе).
