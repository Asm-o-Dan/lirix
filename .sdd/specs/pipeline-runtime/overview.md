# Спецификация: zone/pipeline-runtime

## Модуль: `:pipeline:runtime` · Зона: `zone/pipeline-runtime` · Фаза 2: «Конструктор конвейеров» · Статус: `PROPOSED`

---

### 1. Назначение, архитектурный контекст и границы модуля

Модуль `:pipeline:runtime` реализует ядро выполнения сконструированного конвейера уведомлений (Notification Pipeline Runtime). Он является прямым преемником и архитектурной эволюцией жестко зашитого оркестратора Фазы 1.1 (`com.example.npc.app.pipeline.EventProcessingOrchestratorImpl`).

Рантайм обеспечивает **горячую подмену (Hot Swap)** активной версии конвейера на лету без перезапуска Android-сервиса `NotificationListenerService` (что критично для сохранения привилегий захвата уведомлений в Xiaomi HyperOS), сверхбыстрое **безаллокационное исполнение (Zero Allocation Hot Path)** с SLA $p95 \le 15$ мс на аппаратной платформе Poco M7, **двухфазный коммит эффектов (Two-Phase Commit)** и **трёхуровневую изоляцию сбоев (3-Tier Crash Isolation & Circuit Breaker)**.

```
┌────────────────────────────────────────────────────────────────────────────────┐
│                           АРХИТЕКТУРНЫЙ КОНТЕКСТ                               │
│                                                                                │
│   [Android OS: NLS]                                                            │
│         │                                                                      │
│         │ SBN capture (SLA < 5ms)                                              │
│         ▼                                                                      │
│   ┌────────────────────────────────────────────────────────┐                   │
│   │ :service:listener (PipelineNotificationListenerService)│                   │
│   └───────────┬────────────────────────────────────────────┘                   │
│               │ 1. RawEvent + Event(UNCLASSIFIED) to SQLite WAL                │
│               │ 2. submit(eventId)                                             │
│               ▼                                                                │
│   ┌────────────────────────────────────────────────────────┐                   │
│   │ :pipeline:runtime (PipelineRuntime / ActivePipeline)   │◀── Atomic Hot Swap│
│   │  - Single-Consumer Coroutine Worker                    │    from Compiler  │
│   │  - Zero-Alloc ExecutionContext Pool (Frame, Effects)   │    (<= 100ms)     │
│   │  - Two-Phase Commit to StorageGateway                  │                   │
│   │  - 3-Tier Crash Isolation & Circuit Breaker            │                   │
│   │  - In-Memory Ring Buffer Traces (128 slots)            │                   │
│   └───────────┬────────────────────────────────────────────┘                   │
│               │ completeEventProcessing(eventId, ...)                          │
│               ▼                                                                │
│   ┌────────────────────────────────────────────────────────┐                   │
│   │ :core:database / :core:storage (Room StorageGateway)   │                   │
│   └────────────────────────────────────────────────────────┘                   │
└────────────────────────────────────────────────────────────────────────────────┘
```

#### 1.1. Роль в архитектуре Фазы 2 (Смена парадигмы: от монолитного хардкода к динамическому рантайму)

В Фазе 1.1 логика обработки уведомлений была монолитно скомпонована внутри `EventProcessingOrchestratorImpl`: вычисление отпечатка контента (`Fingerprinter`), поиск пользовательских прототипов в Room, семантическая классификация (`SemanticClassifier`) и финансовая экстракция (`IsolatedExtractorRunner`). Любое изменение логики требовало перекомпиляции APK-пакета и редеплоя.

В Фазе 2 конвейер разделяется на три слабосвязанных слоя:
1. **Декларативное AST (`:pipeline:dsl`):** описание графа в JSON/Room.
2. **Компилятор (`:pipeline:compiler`):** статическая верификация (RE2/J, ReDoS Guard, Dataflow) и сборка в иммутабельный `CompiledPipeline`.
3. **Исполняющий рантайм (`:pipeline:runtime`):** исполнение скомпилированного байткода над событиями из очереди с гарантией изоляции сбоев и целостности транзакций.

#### 1.2. Границы модуля и зависимости

1. **Входящие зависимости:**
   - `:pipeline:compiler` — типы `CompiledPipeline`, `Signal`, `FrameLayout`, `Frame`.
   - `:pipeline:nodes-api` — типы `Bank`, `EffectBuffer`, `EffectView`, `StepResult`.
   - `:core:model` — сущности `NotificationEvent`, `Category`, `ClassificationResult`, `FinancialTransaction`.
   - `:core:storage` — интерфейс `StorageGateway` (атомарный `tryClaimEvent`, `getEventWithPackage`, `completeEventProcessing`, `getPendingUnprocessedEventIds`).
   - `kotlinx.coroutines` — примитивы асинхронности (`Channel`, `SupervisorJob`, `CoroutineScope`).
2. **Изоляция от UI и Android Framework:**
   - Модуль `:pipeline:runtime` не зависит от Jetpack Compose, Views, Android Activities/Fragments.
   - Допускаются только легковесные платформенные сервисы Android: `android.os.SystemClock` (для монотонных меток `elapsedRealtimeNanos`) и `android.util.Log`. Для юнит-тестов на JVM предоставляется абстракция `ClockProvider`.
3. **Целевое устройство и аппаратные условия:**
   - **Устройство:** Poco M7 (`2440cbe2`), чипсет MediaTek Dimensity 6100+ (2x Cortex-A76 @ 2.2 GHz + 6x Cortex-A55 @ 2.0 GHz), 4/6 GB LPDDR4X.
   - **ОС:** Xiaomi HyperOS (Android 14) с агрессивным фоновым энергосбережением и контролем службы доступа к уведомлениям (NLS).
   - **SLA горячего пути:** $p95 \le 15$ мс на событие при установившейся нагрузке.
   - **Hot Swap SLA:** замена конвейера $\le 100$ мс без рестарта Android-процесса и без сброса прав `NotificationListenerService`.

---

### 2. Горячая подмена (Hot Swap) и `ActivePipelineProvider`

#### 2.1. Проблема выживания NotificationListenerService в HyperOS

В операционной системе Xiaomi HyperOS (MIUI 15) отзыв и повторная выдача системного разрешения `BIND_NOTIFICATION_LISTENER_SERVICE` происходят с высокой вероятностью при:
1. Перезапуске процесса приложения (Process Kill / Cold Restart).
2. Задержках в обработке IPC-вызовов Binder свыше допустимого порога ($> 20$ мс в потоке листенера).
3. Переподключении сервиса через `requestRebind()` во время активного поступления уведомлений.

Для предотвращения потери доступа к уведомлениям обновление конфигурации конвейера должно производиться **строго на лету (in-process hot swap)**, без остановки сервиса листенера и без разрыва очереди событий.

#### 2.2. Публичный контракт `ActivePipelineProvider`

Интерфейс `ActivePipelineProvider` предоставляет потокобезопасный доступ к активной версии конвейера и обеспечивает атомарную публикацию новых скомпилированных версий:

```kotlin
package com.example.npc.pipeline.runtime.hotswap

import com.example.npc.pipeline.compiler.CompiledPipeline
import kotlinx.coroutines.flow.StateFlow

/**
 * Поставщик активного скомпилированного конвейера с поддержкой атомарной горячей подмены.
 */
interface ActivePipelineProvider {

    /**
     * Возвращает текущий активный скомпилированный конвейер.
     * Вызов выполняется на горячем пути: строго 0 аллокаций, одно чтение volatile-ссылки (O(1)).
     */
    fun current(): CompiledPipeline

    /**
     * Атомарно подменяет активный конвейер на новую скомпилированную версию.
     * Вызывается из фонового потока (Off-the-hot-path).
     *
     * @param next Валидированный, скомпилированный и прогретый конвейер.
     * @return Предыдущая версия конвейера [CompiledPipeline], которая была замещена.
     * @throws IllegalArgumentException если ревизия [next] не строго больше текущей (защита от гонок и отката).
     */
    fun swap(next: CompiledPipeline): CompiledPipeline

    /**
     * Наблюдаемый поток метаданных текущей активной ревизии для UI редактора и мониторинга.
     */
    val activeRevisionFlow: StateFlow<PipelineSnapshotInfo>
}

/**
 * Легковесный слепок метаданных активной ревизии.
 */
data class PipelineSnapshotInfo(
    val pipelineId: String,
    val revision: Long,
    val canonicalHash: String,
    val stagesCount: Int,
    val swappedAtTimestamp: Long
)
```

#### 2.3. Семантика in-flight событий (Pinned Generation)

Главный инвариант надежности горячей подмены: **событие обрабатывается ровно на одной версии конвейера от начала до конца.**

```
Время t ───────▶
Поток Worker:
  [Event A] ──Захват ref (Gen 1)──▶ [Stage 1] ──▶ [Stage 2] ──▶ [Commit Gen 1] ──▶ Завершено
                   ▲
                   │  t_swap: provider.swap(Gen 2)  [AtomicReference.set]
                   │
  [Event B] ───────┴──────────────▶ Захват ref (Gen 2) ──▶ [Stage 1] ──▶ [Commit Gen 2] ──▶ Завершено
```

1. **Захват поколения при входе (Generation Pinning):**
   Воркер при извлечении `eventId` из очереди захватывает локальную ссылку на `CompiledPipeline`:
   ```kotlin
   val activePipeline = pipelineProvider.current() // Один volatile-read
   val revisionId = activePipeline.revision
   ```
2. **Изоляция исполнения:**
   Все стадии, условия, трансформации и действия данного события исполняются исключительно в контексте захваченного экземпляра `activePipeline`. Смена глобальной ссылки в `ActivePipelineProvider` во время исполнения события $A$ не затрагивает его работу.
3. **Штамп ревизии в хранилище (Provenance Stamping):**
   При сохранении результатов обработки события через `StorageGateway.completeEventProcessing` в таблицу `event` Room/SQLite явно записывается `pipeline_revision_id = revisionId`. Это обеспечивает 100% аудит: для каждого исторического события в БД точно известно, какая именно версия правил присвоила ему категорию и выделила транзакцию.
4. **Безопасная утилизация (Retire & Garbage Collection):**
   Так как `CompiledPipeline` не удерживает тяжелых нативных файловых дескрипторов или сетевых сокетов, утилизация старой версии конвейера передается стандартному Garbage Collector виртуальной машины Android ART сразу после того, как все in-flight события поколения завершат свою работу.

#### 2.4. Жизненный цикл горячей подмены (Hot Swap Lifecycle)

Процесс горячей подмены выполняется по 4-этапному протоколу **Prepare-Warm-Swap-Commit**:

1. **Compile & Validate (фоновый пул `Dispatchers.Default`):**
   `PipelineCompiler.compile(definition)` формирует новый `CompiledPipeline`. Проверяются все инварианты схемы и RE2/J.
2. **Warmup Phase (прогрев JIT/DEX):**
   Выполняется фиктивный холостой прогон (`warmupRun`) скомпилированного пайплайна на предвыделенном тестовом фрейме с пустыми строками для инициализации внутренних структур RE2/J автомата.
3. **Atomic Swap (`ActivePipelineProvider.swap`):**
   Атомарная операция `ref.compareAndSet(...)` в памяти. Длительность: $< 1$ мкс.
4. **DB Transaction (`PipelineRepository`):**
   Активация новой ревизии в SQLite Room (`is_active = 1`).
   **Суммарное время переключения от клика пользователя в UI до первого события на новом пайплайне:** $\le 100$ мс.

---

### 3. Горячий цикл обработки событий (Event Execution Loop)

Контур исполнения спроектирован с учетом экстремальных требований по безаллокационности на JVM/Android.

#### 3.1. Архитектура очереди и диспетчеризации

- **Очередь входящих событий:** `kotlinx.coroutines.channels.Channel<Long>(Channel.UNLIMITED)`.
  Передаются строго 64-битные примитивные идентификаторы `eventId`. Тяжелые объекты `StatusBarNotification`, строковые тексты и битмапы **никогда не буферизуются в памяти очереди**, что гарантирует защиту от `OutOfMemoryError` при штормах уведомлений (Notification Flooding).
- **Single-Consumer Coroutine Worker:**
  Для исключения конкуренции за ресурсы базы данных SQLite (WAL-режим допускает параллельное чтение, но сериализует запись через один эксклюзивный lock) горячий цикл конвейера обслуживается **одним** воркером:
  ```kotlin
  val orchestratorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
  ```
  Привязка к `limitedParallelism(1)` дает колоссальное преимущество: **все внутренние структуры контекста исполнения фрейма, буфера эффектов и кольцевой трассы могут быть однопоточными и не требуют блокировок (`synchronized`), мьютексов или атомиков на горячем пути.**

#### 3.2. Безаллокационный пул контекстов исполнения (`ExecutionContextPool`)

В установившемся режиме горячий цикл обработки **не выполняет ни одной аллокации памяти в Heap (0 bytes / event)**.

```kotlin
package com.example.npc.pipeline.runtime.execution

import com.example.npc.pipeline.compiler.Frame
import com.example.npc.pipeline.compiler.FrameLayout
import com.example.npc.pipeline.nodes.api.effect.EffectBuffer
import com.example.npc.pipeline.nodes.api.frame.TextRegister
import com.google.re2j.Pattern

/**
 * Монолитный переиспользуемый контекст исполнения одного рабочего потока.
 * Не содержит динамических коллекций, все структуры предвыделены.
 */
class ExecutionContext(
    maxRefSlots: Int = 128,
    maxPrimSlots: Int = 64,
    maxMatcherSlots: Int = 32,
    maxTextSlots: Int = 8,
    effectCapacity: Int = 32
) {
    /** Регистровый контекст для виртуальной машины пайплайна. */
    @JvmField val frame: Frame = Frame(
        layout = FrameLayout(maxRefSlots, maxPrimSlots, maxMatcherSlots),
        patterns = emptyArray() // Паттерны перепривязываются по слотам
    )

    /** Буфер отложенных побочных эффектов двухфазного коммита. */
    @JvmField val effectBuffer: EffectBuffer = EffectBuffer(capacity = effectCapacity)

    /** Текстовые регистры для in-place трансформаций без создания строк. */
    @JvmField val textRegisters: Array<TextRegister> = Array(maxTextSlots) { TextRegister(capacity = 2048) }

    /** Флаг активного использования для защиты от повторного взятия / утечек. */
    @JvmField var inUse: Boolean = false

    /**
     * Полная безаллокационная очистка состояния перед приемом нового события.
     */
    fun reset() {
        frame.reset()
        effectBuffer.clear()
        for (i in 0 until textRegisters.size) {
            textRegisters[i].len = 0
            textRegisters[i].isTruncated = false
        }
        inUse = false
    }
}
```

##### Контракт отсутствия аллокаций:
1. Запрет создания лямбд и функциональных типов внутри цикла исполнения узлов.
2. Интерфейс исполнения узла является строго **не-suspend**: `fun execute(frame: Frame, buffer: EffectBuffer): StepResult`. Вызовы `suspend` выделяют структуры `Continuation` в куче, поэтому весь горячий прогон конвейера (все $N$ стадий) выполняется как чистый синхронный CPU-код.
3. Корутинное приостановление (`suspend`) происходит только на внешних границах: извлечение из `Channel` и I/O вызовы в `StorageGateway` (Room/SQLite).

#### 3.3. Двухфазный коммит (Two-Phase Effect Buffering & Commit)

Узлы конвейера спроектированы как изолированные вычислительные блоки. Они **не имеют права выполнять прямые I/O-операции**: обновлять Room, посылать системные интенты, показывать нотификации или вызывать HTTP-запросы.

```
       ФАЗА 1: Вычисление (Чистый CPU)              ФАЗА 2: Атомарный коммит (Room WAL)
  ┌────────────────────────────────────────┐       ┌─────────────────────────────────────┐
  │ Узел 1 (Sanitize) ──▶ Frame.refs       │       │ StorageGateway:                     │
  │ Узел 2 (Classify) ──▶ EffectBuffer:    │       │ 1. UPDATE event                     │
  │     [SET_CATEGORY(FINANCE)]            │ ────▶ │ 2. INSERT transaction               │
  │ Узел 3 (Extract)  ──▶ EffectBuffer:    │ PASS  │ 3. UPDATE prototype                 │
  │     [CREATE_TX(Amount=500, Curr=RUP)]  │       │ В единой SQLite WAL @Transaction    │
  └────────────────────────────────────────┘       └─────────────────────────────────────┘
                      │
                      ▼ При DROP / FAULT
  ┌────────────────────────────────────────┐
  │ EffectBuffer.clear()                   │
  │ Никаких изменений в БД не вносится     │
  └────────────────────────────────────────┘
```

1. **Фаза 1: Буферизация эффектов (Buffering Phase):**
   При исполнении действий (`ActionNodeExecutor`) параметры действий сериализуются в плоский массив `EffectBuffer`. Буфер поддерживает до 32 эффектов без реаллокаций.
2. **Терминальная оценка (Evaluation):**
   - Если конвейер вернул `Signal.PASS`: выполняется переход к Фазе 2.
   - Если конвейер вернул `Signal.DROP`: событие считается отброшенным (спам, игнорируемый пакет), буфер очищается, в базу пишется только статус `DROPPED` без создания транзакций.
   - Если конвейер завершился аварийно (`Signal.FAULT`): буфер эффектов немедленно сбрасывается (`rollback`).
3. **Фаза 2: Атомарный коммит (Commit Phase):**
   Оркестратор извлекает структурированный итератор `EffectView` из буфера и транслирует накопленные эффекты в единую транзакцию `StorageGateway.completeEventProcessing`:
   - Назначенная категория (`Category`, `Engine`, `Confidence`).
   - Финансовая транзакция (`FinancialTransaction`).
   - Подтверждение пользовательского прототипа (`confirmPrototype`).
   - Кастомные флаги и теги события.

---

### 4. Трёхуровневая система защиты от сбоев (Crash Isolation & Circuit Breaker)

Ни при каких обстоятельствах сбой пользовательского или встроенного узла не должен приводить к падению фонового процесса приложения (Crash Loop), блокировке очереди событий или зависанию системного сервиса `NotificationListenerService`.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        3-TIER CRASH ISOLATION                          │
│                                                                        │
│  [Входящее событие]                                                    │
│         │                                                              │
│         ▼                                                              │
│   ┌──────────────────────────────────────────────────────────────┐     │
│   │ УРОВЕНЬ 1: Per-Event & Per-Stage Catch                       │     │
│   │ - Перехват Throwable внутри цикла стадии                    │     │
│   │ - Откат буфера эффектов до последней точки сохранения        │     │
│   │ - Запись ошибки в статус события (FAILED / UNCLASSIFIED)     │     │
│   └──────────────────────────────┬───────────────────────────────┘     │
│                                  │ Сбой узла >= 3 раз за 5 минут       │
│                                  ▼                                     │
│   ┌──────────────────────────────────────────────────────────────┐     │
│   │ УРОВЕНЬ 2: Per-Node Circuit Breaker (Zero-Alloc Bypass)      │     │
│   │ - Перевод узла в состояние OPEN (Bypass Mode)                │     │
│   │ - Конвейер продолжает работать, пропуская сбойный узел       │     │
│   │ - Cooldown 5 минут -> Probe (Half-Open)                      │     │
│   └──────────────────────────────┬───────────────────────────────┘     │
│                                  │ Триггер срабатывания Breaker        │
│                                  ▼                                     │
│   ┌──────────────────────────────────────────────────────────────┐     │
│   │ УРОВЕНЬ 3: Runtime Alert Engine                              │     │
│   │ - Постановка алерта в RuntimeAlertQueue                      │     │
│   │ - Отображение предупреждающей плашки в UI ленты              │     │
│   │ - Отправка диагностического репорта в локальную БД           │     │
│   └──────────────────────────────────────────────────────────────┘     │
└────────────────────────────────────────────────────────────────────────┘
```

#### 4.1. Уровень 1: Per-Event & Per-Stage Catch

Любой вызов метода `execute` узла обернут в защищенный блок с гранулярным разграничением исключений:

```kotlin
try {
    val result = stage.execute(frame)
    // Обработка нормального перехода
} catch (ce: CancellationException) {
    // Корутинная отмена ДОЛЖНА пробрасываться дальше для корректного shutdown
    throw ce
} catch (vme: VirtualMachineError) {
    // Фатальные ошибки JVM (OOM, StackOverflow) не перехватываются локально
    throw vme
} catch (t: Throwable) {
    // Изоляция ошибки узла
    handleStageCrash(stageIndex, stage.id, t, frame)
}
```

- **Семантика при сбое стадии:**
  1. Буфер эффектов сбрасывается (`effectBuffer.clear()`).
  2. Событие в БД помечается статусом `EventProcessingStatus.FAILED` с сохранением `error_message` в метаданных (или остается в `UNCLASSIFIED` в зависимости от политики `FailurePolicy`).
  3. Очередь `Channel` **не блокируется**, воркер немедленно переходит к следующему событию.
  4. Общий процесс приложения **не падает**.

#### 4.2. Уровень 2: Per-Node Circuit Breaker (Безаллокационный bypass-контроллер)

Если конкретный узел конвейера (например, сторонний или сложный regex-экстрактор) падает **3 раза подряд в течение скользящего окна 5 минут**, он представляет системную угрозу производительности конвейера.

Автоматический Circuit Breaker изолирует данный узел:
- **Состояния автомата:**
  - `CLOSED (0)`: нормальное исполнение узла.
  - `OPEN (1)`: узел отключен (режим обхода — Bypass). Запросы минуют исполнение узла с маршрутизацией по пути по умолчанию (`fallbackPc`).
  - `HALF_OPEN (2)`: тестовый зонд (Probe) по истечении 5 минут. Одно событие допускается к исполнению для проверки стабилизации.

##### Безаллокационная упаковка состояния в `AtomicLong`:
Для обеспечения нулевого оверхеда на горячем пути состояние автомата упаковано в 64-битное слово:
- Биты 62..63: Состояние (`0 = CLOSED`, `1 = OPEN`, `2 = HALF_OPEN`).
- Биты 0..61: Метка времени окончания cooldown (`openUntilElapsedRealtimeMs`).

```kotlin
package com.example.npc.pipeline.runtime.breaker

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

class NodeCircuitBreaker(
    val stageId: String,
    val failureThreshold: Int = 3,
    val cooldownPeriodMs: Long = 5 * 60 * 1000L // 5 минут
) {
    companion object {
        private const val STATE_CLOSED = 0L
        private const val STATE_OPEN = 1L
        private const val STATE_HALF_OPEN = 2L
        private const val STATE_MASK = 3L shl 62
        private const val TIME_MASK = (1L shl 62) - 1L
    }

    private val stateWord = AtomicLong(0L)
    private var consecutiveFailures: Int = 0

    /**
     * Быстрая проверка на горячем пути: нужно ли пропустить узел?
     * Строго 0 аллокаций, одно чтение volatile Long.
     */
    fun shouldBypass(): Boolean {
        val w = stateWord.get()
        val state = (w ushr 62)
        if (state == STATE_CLOSED) return false

        val now = SystemClock.elapsedRealtime()
        val openUntil = (w and TIME_MASK)

        if (state == STATE_OPEN) {
            if (now < openUntil) {
                return true // Узел изолирован, обходим
            }
            // Cooldown истек: ровно один поток переводит в HALF_OPEN
            val halfOpenWord = (STATE_HALF_OPEN shl 62)
            return !stateWord.compareAndSet(w, halfOpenWord)
        }

        // HALF_OPEN: пробный вызов уже выполняется другим событием
        return true
    }

    fun recordSuccess() {
        consecutiveFailures = 0
        stateWord.set(0L) // CLOSED
    }

    fun recordFailure(onTripAlert: (stageId: String, failures: Int) -> Unit) {
        consecutiveFailures++
        if (consecutiveFailures >= failureThreshold) {
            val now = SystemClock.elapsedRealtime()
            val openWord = (STATE_OPEN shl 62) or ((now + cooldownPeriodMs) and TIME_MASK)
            stateWord.set(openWord)
            onTripAlert(stageId, consecutiveFailures)
        }
    }
}
```

#### 4.3. Уровень 3: Runtime Alert Engine

При переходе Circuit Breaker узла в состояние `OPEN` рантайм формирует системный алерт для пользователя и разработчика:

```kotlin
package com.example.npc.pipeline.runtime.alert

import kotlinx.coroutines.flow.SharedFlow

data class RuntimeAlert(
    val alertId: String,
    val stageId: String,
    val stageName: String,
    val failureCount: Int,
    val lastErrorMessage: String,
    val trippedAtTimestamp: Long,
    val autoResumeAtTimestamp: Long,
    val status: AlertStatus
)

enum class AlertStatus {
    ACTIVE,
    RESOLVED_AUTOMATICALLY,
    DISMISSED_BY_USER
}

interface RuntimeAlertEngine {
    val alertsFlow: SharedFlow<RuntimeAlert>
    fun postAlert(alert: RuntimeAlert)
    fun dismissAlert(alertId: String)
}
```

- **Поведение в UI:**
  На главном экране ленты событий (`ui-timeline`) отображается предупреждающая плашка (Banner):
  *«Внимание: узел «Финансовый экстрактор SMS» временно отключен из-за повторяющихся ошибок (3 сбоя). Конвейер продолжает работать в безопасном режиме. Автоповтор через 4 мин.»*
- При успешном прохождении зонда в `HALF_OPEN` алерт автоматически переводится в `RESOLVED_AUTOMATICALLY` и плашка скрывается.

---

### 5. Холодный старт и самовосстановление (Recovery Sweep)

#### 5.1. Уязвимость мобильного процесса к убийству системой (Low Memory Killer)

В условиях Android 14 / HyperOS приложение может быть в любой момент принудительно вытеснено из оперативной памяти механизмом LMK (например, при запуске пользователем ресурсоемкой 3D-игры или камеры).

Если уведомление уже было записано в Room листером (`RawEvent` + `Event(UNCLASSIFIED)`), но воркер был убит до вызова `completeEventProcessing`, событие зависает в статусе `PROCESSING` или `UNCLASSIFIED`.

#### 5.2. Алгоритм `triggerRecoverySweep()`

Самовосстановление запускается автоматически в двух ключевых точках жизненного цикла:
1. При старте процесса приложения (`Application.onCreate()`).
2. При каждом обратном переподключении сервиса листенера (`PipelineNotificationListenerService.onListenerConnected()`).

```kotlin
fun triggerRecoverySweep(): Job {
    return orchestratorScope.launch(Dispatchers.IO) {
        if (!isRecoveryActive.compareAndSet(false, true)) {
            Log.d(TAG, "Recovery sweep is already running, skipping trigger")
            return@launch
        }
        try {
            Log.i(TAG, "Starting Recovery Sweep for dangling events...")

            // 1. Поиск зависших событий: UNCLASSIFIED или PROCESSING старше 60 секунд
            val staleTimestampThreshold = System.currentTimeMillis() - 60_000L
            val danglingIds = storageGateway.getStaleUnprocessedEventIds(
                staleBeforeTimestamp = staleTimestampThreshold,
                limit = 1000
            )

            Log.i(TAG, "Recovery sweep detected ${danglingIds.size} dangling events")

            // 2. Чанкованная постановка в горячую очередь
            for (eventId in danglingIds) {
                // Атомарный сброс зависшего статуса в UNCLASSIFIED
                storageGateway.resetEventToUnclassified(eventId)
                submit(eventId)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Fatal error during recovery sweep execution", t)
        } finally {
            isRecoveryActive.set(false)
        }
    }
}
```

##### Гарантия идемпотентности и отсутствия дубликатов:
Благодаря атомарному вызову `storageGateway.tryClaimEvent(eventId)` на первой фазе обработки каждого события, повторная постановка одного и того же `eventId` не приведет к дублированию финансовых транзакций или двойному учету метрик. Если событие уже успело обработаться, `tryClaim` вернет `false` и событие будет детерминированно пропущено.

---

### 6. Метрики и диагностика в реальном времени

#### 6.1. Агрегированные метрики производительности (`PipelineMetrics`)

Рантайм накапливает статистику в потокобезопасных счетчиках без блокировок:

```kotlin
package com.example.npc.pipeline.runtime.metrics

import java.time.Instant

data class RuntimeMetricsSnapshot(
    val totalSubmitted: Long,
    val totalClaimed: Long,
    val totalCompleted: Long,
    val totalDropped: Long,
    val totalFailed: Long,
    val currentQueueDepth: Int,
    val p50LatencyMs: Double,
    val p95LatencyMs: Double,
    val throughputEventsPerSec: Double,
    val activeCircuitBreakersCount: Int,
    val lastProcessedEventId: Long?,
    val lastProcessedTimestamp: Instant?,
    val overflowAllocationsCount: Int
)
```

- **Расчет Latency квантилей (p50 / p95):**
  Используется безаллокационный кольцевой буфер последних 1000 временных замеров (`HdrHistogram` или плоский циклический массив `LongArray(1024)`). Обновление значений выполняется за $O(1)$ без выделения объектов.

#### 6.2. Высокоскоростной безаллокационный кольцевой буфер трасс (`TraceRing`)

Для отображения подробной трассировки последних 100 событий на экране системной диагностики (`:feature:diagnostics`) используется кольцевой буфер `TraceRing` емкостью 128 записей (степень двойки для побитовой индексации через `index and 127`).

Буфер размещается в непрерывном блоке примитивной памяти `LongArray(128 * 2)` (всего **2048 байт** в куче JVM):

```
Каждая запись занимает ровно два 64-битных слова (16 байт):
Word 0: [ timestampNanos (SystemClock.elapsedRealtimeNanos)                                     ]
Word 1: [ type (8 bit) | stageId (12 bit) | revisionId (12 bit) | payload/durationMicros (32 bit) ]
```

```kotlin
package com.example.npc.pipeline.runtime.diagnostics

import android.os.SystemClock

/**
 * Безаллокационный кольцевой буфер трассировки на 128 событий.
 * Однопоточный писатель (Worker Thread), неблокирующее чтение среза (Diagnostic Screen).
 */
class TraceRing {
    companion object {
        const val CAPACITY = 128
        private const val MASK = (CAPACITY - 1).toLong()
        
        // Типы событий трассы
        const val TYPE_EVENT_START = 1
        const val TYPE_STAGE_PASS = 2
        const val TYPE_STAGE_FAIL = 3
        const val TYPE_STAGE_BYPASS = 4
        const val TYPE_EVENT_COMMIT = 5
        const val TYPE_EVENT_DROP = 6
    }

    private val buffer = LongArray(CAPACITY * 2)
    private var head = 0L // Монотонный счетчик записей

    /**
     * Запись события трассировки на горячем пути: строго O(1), 0 аллокаций.
     */
    fun record(type: Int, stageIdInt: Int, revisionId: Int, payload: Int) {
        val idx = ((head++ and MASK).toInt()) shl 1
        buffer[idx] = SystemClock.elapsedRealtimeNanos()
        buffer[idx + 1] = (type.toLong() shl 56) or
                ((stageIdInt.toLong() and 0xFFFL) shl 44) or
                ((revisionId.toLong() and 0xFFFL) shl 32) or
                (payload.toLong() and 0xFFFFFFFFL)
    }

    /**
     * Снятие мгновенного слепка трассы для UI экрана диагностики.
     * Выполняется за пределами горячего потока.
     */
    fun dumpSnapshot(destination: LongArray): Long {
        System.arraycopy(buffer, 0, destination, 0, buffer.size)
        return head
    }
}
```

---

### 7. Тест-план и стратегии верификации

Тестирование зоны `:pipeline:runtime` включает в себя строгие автоматизированные проверки на стандартной JVM и инструментированные тесты на аппаратном стенде Poco M7.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ТЕСТОВАЯ МАТРИЦА РАНТАЙМА                       │
├────────────────────────────┬───────────────────────────────────────────┤
│ Направление проверки       │ Методология и критерии приемки            │
├────────────────────────────┼───────────────────────────────────────────┤
│ 1. Parity Differential     │ Golden Corpus (137 dogfood событий).      │
│    (Legacy 1.1 vs Runtime) │ 100% идентичность категорий и транзакций. │
├────────────────────────────┼───────────────────────────────────────────┤
│ 2. Concurrency Stress      │ 10,000 событий параллельно с 50 заменами  │
│    (Hot Swap under load)   │ конвейера. 0 потерянных событий.          │
├────────────────────────────┼───────────────────────────────────────────┤
│ 3. 3-Tier Fault Injection  │ Искусственные сбои узлов: изолирование    │
│    (Resilience & Breaker)  │ через 3 сбоя, разблокировка через 5 мин.  │
├────────────────────────────┼───────────────────────────────────────────┤
│ 4. Zero Allocation Audit   │ ThreadMXBean: 0 bytes/event в установив-  │
│    (Hot Path Purity)       │ шемся режиме.                             │
├────────────────────────────┼───────────────────────────────────────────┤
│ 5. Crash Recovery Sweep    │ Симуляция LMK: дообработка 1000 зависших  │
│    (WAL Consistency)       │ событий из SQLite при холодном старте.    │
└────────────────────────────┴───────────────────────────────────────────┘
```

#### 7.1. Дифференциальный тест паритета (Legacy 1.1 Parity Test)

**Главный гейт приемки рантайма:**
Сконструированный в DSL пресет «Legacy 1.1 Preset», скомпилированный компилятором и запущенный в `PipelineRuntime`, обязан выдать **100% побитово идентичный результат** старому классу `EventProcessingOrchestratorImpl` на анонимизированной базе догфудинга (`event_engine.db`, 137 реальных событий).

- **Вход:** Список реальных входящих событий `List<TestRawNotificationEvent>`.
- **Прогон А:** Исполнение через исходный `EventProcessingOrchestratorImpl`.
- **Прогон Б:** Исполнение через `PipelineRuntime` со скомпилированным пресетом Фазы 1.1.
- **Сравнение:** Проверяются поля:
  * `category` (100% совпадение enum).
  * `confidence` (дельта $< 0.0001$).
  * `transaction.amount`, `transaction.currency`, `transaction.merchant`, `transaction.status`.
  * При малейшем расхождении тест падает с указанием первого расхождения (`First Divergence Assertion`).

#### 7.2. Стресс-тест конкурентной горячей подмены (Concurrent Hot Swap Test)

- Запускается $N = 10,000$ событий через генератор с частотой 200 событий/сек.
- В параллельных фоновых потоках каждые 50 мс инициируется $M = 50$ горячих подмен версий конвейера (`swap(v1) -> swap(v2) -> swap(v3)...`).
- **Критерии успеха:**
  1. Ровно 10,000 событий завершены успешно со статусом `COMPLETED`.
  2. 0 потерянных событий (`lostEvents == 0`).
  3. Каждое событие в базе имеет валидный `pipeline_revision_id`, соответствующий существовавшей в момент его старта версии.
  4. Ни одного случая смешивания стадий из разных версий в рамках одного события.

#### 7.3. Тест отказоустойчивости и срабатывания Circuit Breaker (Resilience Test)

- Регистрируется тестовый узел-вредитель `CrashingTestNode`, который выбрасывает `IllegalStateException("Simulated crash")`.
- Подаются 10 последовательных событий.
- **Ожидаемое поведение:**
  1. События 1, 2, 3 падают в Уровне 1 (Per-event Catch), очередь продолжает двигаться.
  2. После события 3 Circuit Breaker переходит в `OPEN` (Bypass Mode).
  3. Формируется `RuntimeAlert` в `RuntimeAlertEngine`.
  4. События 4..10 мгновенно обходят сбойный узел и успешно завершаются конвейером по ветке `fallback`.
  5. Процесс приложения и сервис уведомлений остаются полностью работоспособными.

#### 7.4. Проверка отсутствия аллокаций памяти (Zero-Allocation Audit)

```kotlin
@Test
fun verifyZeroAllocationsOnHotPath() {
    val runtime = createPrewarmedPipelineRuntime()
    val threadBean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    val threadId = Thread.currentThread().id

    // Прогрев JIT (10,000 прогонов)
    repeat(10_000) { runtime.executeSync(testEventId) }

    // Контрольный замер
    val bytesBefore = threadBean.getThreadAllocatedBytes(threadId)
    repeat(1_000) {
        runtime.executeSync(testEventId)
    }
    val bytesAfter = threadBean.getThreadAllocatedBytes(threadId)
    val bytesPerEvent = (bytesAfter - bytesBefore) / 1_000

    assertEquals(0L, bytesPerEvent, "Hot path MUST allocate 0 bytes in steady state!")
}
```

---

### 8. Аппаратные бюджеты и целевые показатели (SLA)

Для обеспечения непрерывной и плавной работы на целевом устройстве Poco M7 (HyperOS) устанавливаются следующие жесткие ограничения:

| Метрика | Бюджет / SLA | Инструмент контроля |
|---|---|---|
| **Latency горячего пути ($p50$)** | $\le 4$ мс | Кольцевой буфер `TraceRing` / Android Log |
| **Latency горячего пути ($p95$)** | $\le 15$ мс | In-Memory Histogram / CI Benchmark |
| **Latency горячей подмены (Hot Swap)** | $\le 100$ мс | Unit-тест с замером `System.nanoTime()` |
| **Аллокации в куче (Steady State)** | **0 байт** на событие | `ThreadMXBean` / Android Studio Profiler |
| **Память ring buffer трасс** | $\le 4$ КБ RAM | Фиксированный размер `LongArray(256)` |
| **Время выполнения Recovery Sweep** | $\le 500$ мс (на 1000 событий) | Room SQLite WAL batch measurement |
| **Размер очереди событий** | До 10,000 `Long` ID ($< 80$ КБ) | Мониторинг `queueDepth` |
| **Потери прав NLS в HyperOS** | **0 инцидентов** | Soak-тест 48 часов на устройстве Poco M7 |

---

### 9. План миграции и внедрения (Rollout Strategy)

Внедрение нового рантайма происходит поэтапно в соответствии с архитектурным мастер-планом Фазы 2:

1. **Фаза 2.1 (Скрытый параллельный запуск / Dark Launch):**
   - Модуль `:pipeline:runtime` подключается параллельно со старым `EventProcessingOrchestratorImpl`.
   - Включается теневой дифференциальный режим: события обрабатываются старым оркестратором, а копия параллельно прогоняется через новый `PipelineRuntime` в sandbox-режиме (без записи в БД) для валидации метрик и паритета на реальном потоке уведомлений.
2. **Фаза 2.2 (Переключение флага):**
   - Переключение `FeatureFlag.USE_PIPELINE_CONSTRUCTOR_RUNTIME = true`.
   - `PipelineRuntime` становится основным обработчиком очереди событий.
   - Старый `EventProcessingOrchestratorImpl` сохраняется в кодовой базе как аварийный fallback.
3. **Фаза 2.5 (Вывод из эксплуатации legacy-кода):**
   - После успешного завершения 48-часового soak-теста на Poco M7 старый класс `EventProcessingOrchestratorImpl` удаляется.
