## Задача RUNTIME-P2-004: Реализовать NodeCircuitBreaker

**Модуль:** `:pipeline:runtime`  
**Целевой файл:** `pipeline/runtime/src/main/kotlin/com/example/npc/pipeline/runtime/resilience/NodeCircuitBreaker.kt`  
**Спецификация:** `.sdd/specs/pipeline-runtime/overview.md#42-уровень-2-per-node-circuit-breaker-безаллокационный-bypass-контроллер`  
**Контракт:** `.sdd/contracts/pipeline-runtime__compiler.md#5-3-уровневая-изоляция-сбоев-crash-isolation-sla`  

---

### Сигнатура (НЕ МЕНЯТЬ):
```kotlin
package com.example.npc.pipeline.runtime.resilience

import java.util.concurrent.atomic.AtomicLong

/**
 * Состояния автомата защиты узла конвейера.
 */
enum class CircuitState {
    /** Узел работает штатно. Вызовы разрешены. */
    CLOSED,
    /** Узел изолирован из-за сбоев. Вызовы пропускаются (Bypass mode). */
    OPEN,
    /** Период cooldown истек. Выполняется одиночный пробный вызов. */
    HALF_OPEN
}

/**
 * Потокобезопасный безаллокационный предохранитель узла конвейера.
 * Битовая упаковка состояния в AtomicLong (state: 2 bits, openUntilMs: 62 bits).
 */
class NodeCircuitBreaker(
    val stageId: String,
    val failureThreshold: Int = 3,
    val cooldownPeriodMs: Long = 5 * 60 * 1000L // 5 минут
) {
    /**
     * Быстрая безаллокационная проверка на горячем пути: нужно ли пропустить узел?
     *
     * @param nowMonotonicMs текущее монотонное время в миллисекундах.
     * @return true, если узел изолирован и должен быть пропущен; false, если узел готов к исполнению.
     */
    fun shouldBypass(nowMonotonicMs: Long): Boolean

    /**
     * Фиксирует успешное исполнение узла. Сбрасывает счетчик сбоев и возвращает автомат в CLOSED.
     */
    fun recordSuccess()

    /**
     * Фиксирует сбой узла. При достижении failureThreshold переводит автомат в OPEN.
     *
     * @param nowMonotonicMs текущее монотонное время в миллисекундах.
     * @param onTrip коллбэк оповещения о срабатывании предохранителя для RuntimeAlertEngine.
     * @return текущее состояние автомата после фиксации ошибки.
     */
    fun recordFailure(
        nowMonotonicMs: Long,
        onTrip: ((stageId: String, consecutiveFailures: Int) -> Unit)? = null
    ): CircuitState

    /**
     * Возвращает текущее состояние автомата.
     */
    fun getState(nowMonotonicMs: Long): CircuitState

    /**
     * Принудительный сброс автомата в CLOSED.
     */
    fun reset()
}
```

---

### Поведение:
1. **Битовая упаковка состояния (`AtomicLong`):**
   - Биты 62..63: Состояние (`0L = CLOSED`, `1L = OPEN`, `2L = HALF_OPEN`).
   - Биты 0..61: Метка времени истечения изоляции `openUntilMonotonicMs`.
   - Маска состояния: `3L shl 62`. Маска времени: `(1L shl 62) - 1L`.
2. **`shouldBypass(nowMonotonicMs)`:**
   - Читает слово `val w = stateWord.get()`.
   - Если состояние `CLOSED` (0): возвращает `false` (вызов разрешен). Строго 0 аллокаций.
   - Если состояние `OPEN` (1):
     * Если `nowMonotonicMs < (w and TIME_MASK)`: возвращает `true` (узел изолирован).
     * Если cooldown истек (`nowMonotonicMs >= openUntil`): пытается атомарно перевести в `HALF_OPEN` через `compareAndSet(w, HALF_OPEN shl 62)`.
     * Поток, выигравший CAS, получает право на тестовый вызов и возвращает `false`. Все остальные параллельные потоки возвращают `true`.
   - Если состояние `HALF_OPEN` (2): возвращает `true` (пробный вызов уже выполняется).
3. **`recordSuccess()`:**
   - Обнуляет счетчик последовательных сбоев `consecutiveFailures = 0`.
   - Атомарно устанавливает `stateWord.set(0L)` (состояние `CLOSED`).
4. **`recordFailure(nowMonotonicMs, onTrip)`:**
   - Инкрементирует `consecutiveFailures`.
   - Если `consecutiveFailures >= failureThreshold`:
     * Рассчитывает время окончания изоляции `val openUntil = (nowMonotonicMs + cooldownPeriodMs) and TIME_MASK`.
     * Записывает `stateWord.set((1L shl 62) or openUntil)`.
     * Вызывает `onTrip?.invoke(stageId, consecutiveFailures)` для формирования алерта.
     * Возвращает `CircuitState.OPEN`.
   - Иначе возвращает текущее состояние.
5. **`reset()`:**
   - Обнуляет счетчики и переводит `stateWord.set(0L)`.

---

### Ошибки:
- В конструкторе при `failureThreshold <= 0` или `cooldownPeriodMs <= 0` выбрасывать `IllegalArgumentException`.
- Метод `shouldBypass` никогда не выбрасывает исключений на горячем пути.

---

### Граничные случаи:
- **Одиночный сбой:** узел упал 1 или 2 раза, затем выполнился успешно $\to$ счетчик `consecutiveFailures` сбрасывается в 0, состояние остается `CLOSED`.
- **3 сбоя подряд:** на 3-й сбой автомат мгновенно переходит в `OPEN`, вызывается `onTrip`.
- **Истечение cooldown в 5 минут:** первый же вызов переводит состояние в `HALF_OPEN` и допускает ровно 1 пробный запрос.
- **Провал пробного запроса в `HALF_OPEN`:** вызов `recordFailure` немедленно возвращает автомат в `OPEN` еще на 5 минут.
- **Успех пробного запроса в `HALF_OPEN`:** вызов `recordSuccess` переводит автомат в `CLOSED`.

---

### Запрещено:
- Использовать `System.currentTimeMillis()` (подвержен скачкам при NTP-синхронизации и смене часового пояса). Использовать только монотонные метрики времени.
- Использовать блокировки `synchronized` или `ReentrantLock` внутри `shouldBypass`.
- Выделять объекты в Heap внутри `shouldBypass` и `recordSuccess`.

---

### Критерии приёмки (DoD):
- [ ] Класс `NodeCircuitBreaker` скомпилирован в модуле `:pipeline:runtime`.
- [ ] Unit-тесты (`NodeCircuitBreakerTest.kt`):
  * Переход `CLOSED -> OPEN` ровно после `failureThreshold` вызовов `recordFailure()`.
  * Возврат `true` из `shouldBypass()` во время периода `cooldown`.
  * Переход в `HALF_OPEN` по истечении `cooldown` и допуск ровно одного пробного вызова.
  * Возврат в `CLOSED` при вызове `recordSuccess()` из `HALF_OPEN`.
  * Повторный переход в `OPEN` при вызове `recordFailure()` из `HALF_OPEN`.
  * Корректный вызов `onTrip` коллбэка.
- [ ] 0 аллокаций памяти в методе `shouldBypass()`.
