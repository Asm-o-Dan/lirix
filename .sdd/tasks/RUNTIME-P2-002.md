## Задача RUNTIME-P2-002: Реализовать ExecutionContext и ExecutionContextPool

**Модуль:** `:pipeline:runtime`  
**Целевой файл:** `pipeline/runtime/src/main/kotlin/com/example/npc/pipeline/runtime/execution/ExecutionContextPool.kt`  
**Спецификация:** `.sdd/specs/pipeline-runtime/overview.md#32-безаллокационный-пул-контекстов-исполнения-executioncontextpool`  
**Контракт:** `.sdd/contracts/pipeline-compiler__spi.md`  

---

### Сигнатура (НЕ МЕНЯТЬ):
```kotlin
package com.example.npc.pipeline.runtime.execution

import com.example.npc.pipeline.compiler.Frame
import com.example.npc.pipeline.compiler.FrameLayout
import com.example.npc.pipeline.nodes.api.effect.EffectBuffer
import com.example.npc.pipeline.nodes.api.frame.TextRegister

/**
 * Переиспользуемый контекст исполнения одного события в рабочем потоке конвейера.
 * Полностью инкапсулирует предвыделенный Frame, EffectBuffer и TextRegister.
 */
class ExecutionContext(
    val maxRefSlots: Int = 128,
    val maxPrimSlots: Int = 64,
    val maxMatcherSlots: Int = 32,
    val maxTextSlots: Int = 8,
    val maxTextCapacity: Int = 2048,
    val effectCapacity: Int = 32
) {
    @JvmField val frame: Frame = Frame(
        layout = FrameLayout(maxRefSlots, maxPrimSlots, maxMatcherSlots),
        patterns = emptyArray()
    )

    @JvmField val effectBuffer: EffectBuffer = EffectBuffer(capacity = effectCapacity)

    @JvmField val textRegisters: Array<TextRegister> = Array(maxTextSlots) {
        TextRegister(capacity = maxTextCapacity)
    }

    @JvmField var inUse: Boolean = false

    /**
     * Сброс всех регистров и буферов перед обработкой следующего события.
     * Строго 0 аллокаций в Heap.
     */
    fun reset() {
        frame.reset()
        effectBuffer.clear()
        for (i in 0 until textRegisters.size) {
            textRegisters[i].clear()
        }
        inUse = false
    }
}

/**
 * Безаллокационный пул предвыделенных контекстов исполнения.
 * Оптимизирован под single-consumer воркер (Dispatchers.Default.limitedParallelism(1)).
 */
class ExecutionContextPool(
    val capacity: Int = 4
) {
    private val items: Array<ExecutionContext> = Array(capacity) { ExecutionContext() }
    private var top: Int = capacity

    /** Количество переполнений пула, приведших к аллокации внеочередного контекста. */
    var overflowAllocationsCount: Int = 0
        private set

    /**
     * Захватывает контекст из пула.
     * При наличии свободного элемента — 0 аллокаций памяти.
     */
    fun acquire(): ExecutionContext

    /**
     * Очищает и возвращает контекст обратно в пул.
     * Защищен от повторного возврата через проверку флага inUse.
     */
    fun release(context: ExecutionContext)
}
```

---

### Поведение:
1. `ExecutionContext`:
   - Содержит предвыделенный `Frame` с плоскими массивами ссылок и примитивов.
   - Содержит предвыделенный `EffectBuffer` на 32 эффекта (двухфазный коммит).
   - Содержит предвыделенные `TextRegister` для безаллокационной обработки строк.
   - Метод `reset()`:
     * Очищает ссылки `frame.refs` (`Arrays.fill(refs, null)`), предотвращая утечки памяти GC.
     * Обнуляет примитивы `frame.prims` (`Arrays.fill(prims, 0L)`).
     * Очищает буфер эффектов `effectBuffer.clear()`.
     * Сбрасывает текстовые регистры `textRegisters[i].clear()`.
     * Снимает флаг `inUse = false`.
2. `ExecutionContextPool`:
   - Реализован как плоский безаллокационный стек на базе массива `items`.
   - `acquire()`:
     * Если `top > 0`: извлекает контекст `val ctx = items[--top]`, выставляет `ctx.inUse = true` и возвращает его. 0 аллокаций.
     * Если `top == 0` (исчерпание емкости из-за вложенного вызова или перегрузки): инкрементирует `overflowAllocationsCount`, аллоцирует новый резервный `ExecutionContext()`, выставляет `ctx.inUse = true` и возвращает.
   - `release(context)`:
     * Проверяет `check(context.inUse) { "Attempt to release context that is not marked inUse" }`.
     * Вызывает `context.reset()`.
     * Если `top < capacity`: сохраняет в `items[top++] = context`. Иначе позволяет GC утилизировать переполненный объект.

---

### Ошибки:
- При попытке вызвать `release()` для контекста, у которого `inUse == false` (двойной release или context не из пула), выбрасывать `IllegalStateException`.
- Конструктор `ExecutionContextPool` проверяет `require(capacity > 0)`.

---

### Граничные случаи:
- **Штатный режим:** 1 контекст захватывается в начале обработки события и освобождается в блоке `finally`. `overflowAllocationsCount == 0`.
- **Всплеск/вложенность:** при одновременном захвате более `capacity` контекстов создаются временные экземпляры, метрика `overflowAllocationsCount` растет.
- **Очистка ссылок:** ссылки на тяжелые DTO хоста гарантированно зануляются в `reset()`, исключая утечки через долгоживущий пул.

---

### Запрещено:
- Использовать динамические списки (`ArrayList`, `LinkedList`) с боксингом.
- Аллоцировать новые объекты в штатном режиме работы `acquire()` и `release()`.
- Использовать Android SDK (`android.*`). Модуль — чистый JVM Kotlin.

---

### Критерии приёмки (DoD):
- [ ] Классы `ExecutionContext` и `ExecutionContextPool` скомпилированы в модуле `:pipeline:runtime`.
- [ ] Unit-тесты (`ExecutionContextPoolTest.kt`):
  * Выдача контекста с установкой `inUse = true`.
  * Автоматический вызов `reset()` при `release()` (проверка очистки `refs` и `effectBuffer`).
  * Выброс `IllegalStateException` при двойном `release()`.
  * Корректная обработка переполнения пула с инкрементом `overflowAllocationsCount`.
- [ ] Тест на отсутствие аллокаций: цикл 10,000 повторений `acquire()` / `release()` не выделяет объектов в куче JVM.
