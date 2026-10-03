## Задача RUNTIME-P2-001: Реализовать ActivePipelineProvider и ActivePipelineProviderImpl

**Модуль:** `:pipeline:runtime`  
**Целевые файлы:**  
- `pipeline/runtime/src/main/kotlin/com/example/npc/pipeline/runtime/hotswap/ActivePipelineProvider.kt`  
- `pipeline/runtime/src/main/kotlin/com/example/npc/pipeline/runtime/hotswap/ActivePipelineProviderImpl.kt`  
**Спецификация:** `.sdd/specs/pipeline-runtime/overview.md#2-горячая-подмена-hot-swap-и-activepipelineprovider`  
**Контракт:** `.sdd/contracts/pipeline-runtime__compiler.md#3-контракт-горячей-подмены-activepipelineprovider`  

---

### Сигнатура (НЕ МЕНЯТЬ):
```kotlin
package com.example.npc.pipeline.runtime.hotswap

import com.example.npc.pipeline.compiler.CompiledPipeline
import kotlinx.coroutines.flow.StateFlow

data class PipelineSnapshotInfo(
    val pipelineId: String,
    val revision: Long,
    val canonicalHash: String,
    val stagesCount: Int,
    val swappedAtTimestamp: Long
)

/**
 * Поставщик активного скомпилированного конвейера с поддержкой атомарной горячей подмены.
 */
interface ActivePipelineProvider {

    /**
     * Возвращает текущий активный скомпилированный конвейер.
     * Строго 0 аллокаций, одно чтение volatile-ссылки (O(1)).
     */
    fun current(): CompiledPipeline

    /**
     * Атомарно подменяет активный конвейер на новую скомпилированную версию.
     *
     * @param next Валидированный, скомпилированный и прогретый конвейер следующей ревизии.
     * @return Предыдущая замещенная версия конвейера [CompiledPipeline].
     * @throws IllegalArgumentException если next.revision <= current.revision (защита от гонок и регрессий).
     */
    fun swap(next: CompiledPipeline): CompiledPipeline

    /**
     * Поток метаданных текущей активной ревизии для UI редактора и мониторинга.
     */
    val activeRevisionFlow: StateFlow<PipelineSnapshotInfo>

    companion object {
        fun create(initial: CompiledPipeline): ActivePipelineProvider = ActivePipelineProviderImpl(initial)
    }
}
```

---

### Поведение:
1. Хранит текущий экземпляр `CompiledPipeline` в приватном `AtomicReference<CompiledPipeline>`.
2. `current()`: выполняет прямое чтение `atomicRef.get()`. Гарантирует $O(1)$, время выполнения $< 1$ мкс, строго 0 аллокаций в куче JVM.
3. `swap(next)`:
   - Считывает текущий `current = atomicRef.get()`.
   - Проверяет инвариант монотонности ревизий: `require(next.revision > current.revision)`.
   - В цикле атомарного обновления `compareAndSet(current, next)` производит безопасную замену ссылки без блокировок. При коллизии с параллельным `swap` перечитывает `current` и повторяет проверку ревизии.
   - После успешной подмены формирует `PipelineSnapshotInfo` и обновляет внутренний `MutableStateFlow`.
   - Возвращает вытесненный экземпляр `current`.
4. Старая версия пайплайна не закрывает ресурсы принудительно: in-flight события дорабатывают на ней благодаря Pinned Generation, после чего сборщик мусора ART освобождает память.

---

### Ошибки:
- При передаче `next`, у которого `next.revision <= current.revision`, метод `swap` обязан немедленно выбросить `IllegalArgumentException("New revision (${next.revision}) must be strictly greater than current revision (${current.revision})")`.
- Ссылка `atomicRef` никогда не должна содержать `null`. При инициализации `ActivePipelineProviderImpl` передача `null` в качестве `initial` запрещена (`requireNotNull`).

---

### Граничные случаи:
- **Параллельные вызовы `swap` из нескольких потоков:** строго потокобезопасно благодаря CAS-циклу. Поток с меньшей ревизией падает с `IllegalArgumentException`, побеждает поток с наибольшей ревизией.
- **Миллионы вызовов `current()` во время вызова `swap()`:** ни один читающий поток не блокируется и не получает неконсистентного состояния (чтение одного volatile-указателя).
- **Первая ревизия после холодного старта:** `initial` имеет ревизию `1L` (или полученную из базы данных Room).

---

### Запрещено:
- Использовать блокирующие примитивы синхронизации (`synchronized`, `ReentrantLock`, `Mutex`).
- Использовать Android SDK (`android.*`). Модуль является чистым Kotlin/JVM кодом.
- Выделять объекты или аллоцировать память внутри `current()`.
- Допускать регрессию номера ревизии назад или перезапись той же ревизии.

---

### Критерии приёмки (DoD):
- [ ] Интерфейс `ActivePipelineProvider` и реализация `ActivePipelineProviderImpl` скомпилированы.
- [ ] Unit-тесты (`ActivePipelineProviderTest.kt`):
  * Возврат `initial` при старте.
  * Успешная горячая подмена `swap()` с возвратом предыдущей версии.
  * Выброс `IllegalArgumentException` при попытке подмены на версию с равной или меньшей ревизией.
  * Эмиссия корректных метаданных в `activeRevisionFlow` при каждом `swap()`.
  * Стресс-тест конкурентности: 100 параллельных потоков вызывают `current()` во время активного `swap()` без сбоев и зависаний.
- [ ] Отсутствие аллокаций памяти в методе `current()`.
