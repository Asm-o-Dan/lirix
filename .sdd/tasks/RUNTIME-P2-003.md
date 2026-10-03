## Задача RUNTIME-P2-003: Реализовать безаллокационный TraceRing

**Модуль:** `:pipeline:runtime`  
**Целевой файл:** `pipeline/runtime/src/main/kotlin/com/example/npc/pipeline/runtime/trace/TraceRing.kt`  
**Спецификация:** `.sdd/specs/pipeline-runtime/overview.md#62-высокоскоростной-безаллокационный-кольцевой-буфер-трасс-tracering`  
**Контракт:** `.sdd/contracts/pipeline-runtime__compiler.md#2-модель-исполнения-compiledpipeline-и-сигналы-signal`  

---

### Сигнатура (НЕ МЕНЯТЬ):
```kotlin
package com.example.npc.pipeline.runtime.trace

/**
 * Распакованное представление записи трассировки для экрана UI и отчетов диагностики.
 */
data class TraceRecord(
    val timestampNanos: Long,
    val type: Int,
    val stageId: Int,
    val revision: Int,
    val arg: Int
)

/**
 * Высокоскоростной безаллокационный кольцевой буфер трассировки на фиксированное число слотов (128).
 * Хранит трассы в плоском непрерывном примитивном массиве LongArray(256) (2 КБ RAM).
 */
class TraceRing(val capacity: Int = 128) {

    init {
        require(capacity > 0 && (capacity and (capacity - 1)) == 0) {
            "Capacity must be a positive power of two, got $capacity"
        }
    }

    companion object {
        const val DEFAULT_CAPACITY: Int = 128

        // Типы событий трассировки
        const val TYPE_EVENT_START: Int = 1
        const val TYPE_STAGE_PASS: Int = 2
        const val TYPE_STAGE_FAIL: Int = 3
        const val TYPE_STAGE_BYPASS: Int = 4
        const val TYPE_EVENT_COMMIT: Int = 5
        const val TYPE_EVENT_DROP: Int = 6
        const val TYPE_BREAKER_TRIP: Int = 7
    }

    /**
     * Запись события трассы на горячем пути.
     * Строго O(1), 0 аллокаций памяти, неблокирующая запись.
     */
    fun record(type: Int, stageId: Int, revision: Int, arg: Int, timestampNanos: Long)

    /**
     * Снятие мгновенного сырого снимка кольцевого буфера в предоставленный массив.
     * @param destination массив размером не менее capacity * 2.
     * @return монотонное значение head счетчика на момент снятия снимка.
     */
    fun dumpSnapshot(destination: LongArray): Long

    /**
     * Декодирует накопленные записи трассы в список TraceRecord в хронологическом порядке.
     * Вызывается за пределами горячего пути (в потоке UI или фоновом экспортере).
     */
    fun getDecodedSnapshot(): List<TraceRecord>
}
```

---

### Поведение:
1. **Структура хранения данных:**
   - Буфер представляет собой плоский массив `private val buffer = LongArray(capacity * 2)`.
   - Для емкости `capacity = 128` выделяется ровно 256 `Long` ячеек (2048 байт в куче JVM).
   - Каждая запись кодируется двумя последовательными 64-битными словами `Long`:
     * `Word 0`: метка времени `timestampNanos: Long`.
     * `Word 1`: битовая упаковка полей `[type: 8 bit | stageId: 12 bit | revision: 12 bit | arg: 32 bit]`:
       `(type.toLong() shl 56) or ((stageId.toLong() and 0xFFF) shl 44) or ((revision.toLong() and 0xFFF) shl 32) or (arg.toLong() and 0xFFFFFFFFL)`.
2. **Метод `record`:**
   - Вычисляет позицию через побитовую маску: `val idx = ((head++ and mask).toInt()) shl 1`.
   - Записывает `buffer[idx] = timestampNanos` и `buffer[idx + 1] = packedWord`.
   - Не выполняет синхронизаций и блокировок (однопоточный писатель — Single-Consumer Worker).
   - Строго 0 аллокаций памяти.
3. **Метод `dumpSnapshot(destination)`:**
   - Выполняет `System.arraycopy(buffer, 0, destination, 0, buffer.size)`.
   - Возвращает текущее значение `head`.
4. **Метод `getDecodedSnapshot()`:**
   - Снимает локальную копию массива буфера и текущий `head`.
   - Определяет реальное количество доступных записей `val count = minOf(head, capacity.toLong()).toInt()`.
   - Восстанавливает хронологический порядок от самой старой к самой новой записи:
     индекс старта `startIndex = (head - count)`.
   - Распаковывает битовые поля каждого слота в объект `TraceRecord`.

---

### Ошибки:
- В конструкторе при передаче `capacity`, не являющегося степенью двойки или $\le 0$, выбрасывать `IllegalArgumentException`.
- В `dumpSnapshot` при `destination.size < capacity * 2` выбрасывать `IllegalArgumentException`.

---

### Граничные случаи:
- **Буфер пуст (`head == 0L`):** `getDecodedSnapshot()` возвращает пустой список.
- **Буфер заполнен частично (`head < capacity`):** возвращается ровно `head` записей от индекса `0` до `head - 1`.
- **Буфер переполнен (`head >= capacity`):** возвращается ровно `capacity` последних записей с корректным циклическим вытеснением старых.
- **Переполнение `head` за пределы `Long.MAX_VALUE`:** операция `head and mask` корректно обрабатывает переполнение без исключений.

---

### Запрещено:
- Выделять объекты или коллекции внутри `record()`.
- Использовать операцию деления по модулю `%` (только побитовое `and mask`).
- Использовать Android SDK (`android.*`). Модуль — чистый JVM Kotlin.

---

### Критерии приёмки (DoD):
- [ ] Класс `TraceRing` скомпилирован в модуле `:pipeline:runtime`.
- [ ] Unit-тесты (`TraceRingTest.kt`):
  * Проверка валидации степени двойки в конструкторе.
  * Точность упаковки и распаковки битовых полей в `Word 1` (граничные значения `type`, `stageId`, `revision`, `arg`).
  * Корректный хронологический порядок в `getDecodedSnapshot()` до и после переполнения емкости (FIFO вытеснение).
  * Корректная работа `dumpSnapshot()`.
- [ ] Замер аллокаций: 100,000 вызовов `record()` выполняются за $< 5$ мс и дают строго 0 байт аллокаций.
