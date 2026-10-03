# Спецификация: zone/ui-features

Архитектурная зона: `zone/ui-features` (`:feature:editor`, `:feature:analytics`, `:feature:diagnostics`)  
Контракты: [architecture_phase3.md](file:///.sdd/architecture_phase3.md), [core-model/overview.md](file:///.sdd/specs/core-model/overview.md)  
Статус зоны: **FROZEN**

---

## Модуль: MVI Store  Зона: zone/ui-features  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Предоставляет базовый реактивный контейнер однонаправленного потока данных (Unidirectional Data Flow / MVI), управляющий состоянием `StateFlow<ViewState>`, однократными событиями `SharedFlow<ViewEffect>` и диспетчеризацией пользовательских намерений `send(intent: ViewIntent)`. НЕ содержит ссылок на Android Context/View и не производит отрисовку пользовательского интерфейса.

### Типы данных
```kotlin
package com.example.npc.feature.common.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

interface MviStore<State, Intent, Effect> {
    val state: StateFlow<State>
    val effects: Flow<Effect>
    fun send(intent: Intent)
}

abstract class BaseMviStore<State, Intent, Effect>(
    initialState: State,
    private val scope: CoroutineScope
) : MviStore<State, Intent, Effect> {
    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<State> = _state.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    override val effects: Flow<Effect> = _effects.receiveAsFlow()

    protected fun updateState(reducer: (State) -> State) {
        _state.update(reducer)
    }

    protected fun emitEffect(effect: Effect) {
        scope.launch { _effects.send(effect) }
    }

    abstract fun handleIntent(intent: Intent)

    override fun send(intent: Intent) {
        handleIntent(intent)
    }
}
```
- **Инварианты:**
  - Иммутабельность состояния: `State` является неизменяемым `data class`.
  - Потокобезопасность: обновление состояния производится через CAS-цикл `MutableStateFlow.update`.
  - Доставка эффектов: буферизованный канал предотвращает потерю однократных событий навигации при повороте экрана.

### Публичный API

#### `MviStore.send`
```kotlin
fun send(intent: Intent)
```
- **Предусловия:** `intent` не `null`.
- **Постусловия:** Намерение передается в обработчик `handleIntent`, порождая изменение `state` или эмиссию в `effects`.
- **Ошибки:** Не выбрасывает исключений в UI-поток (все внутренние ошибки обрабатываются и переводятся в `State.Error` или `Effect.ShowError`).
- **Побочные эффекты:** Запуск асинхронных корутин в привязанном `scope`.
- **Граничные случаи:** Высокая частота кликов (намерения обрабатываются последовательно).
- **Примеры:**
  1. *Вход:* `send(AssignRole(tokenIndex = 2, role = SlotRole.TX_AMOUNT))`.  
     *Выход:* `state.value.chips[2].role` обновлен на `TX_AMOUNT`.
  2. *Вход:* `send(ConfirmSave)`.  
     *Выход:* Запуск сохранения, эмиссия `Effect.CloseSheet`.
  3. *Вход:* Повторный клик по уже активному фильтру.  
     *Выход:* Сброс фильтра в `null`.

### Внутренние функции
- `protected abstract fun handleIntent(intent: Intent)`

### Зависимости
- Kotlinx Coroutines, Kotlinx Flow.

### Вне скоупа
- Жизненный цикл Android Activity/Fragment (управляется на уровне Jetpack ViewModel).

---

## Модуль: TokenGridView (чипы)  Зона: zone/ui-features  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Специализированный Compose-компонент интерактивной токенной сетки (`FlowRow`), отображающий нормализованный текст банковского сообщения в виде кликабельных чипов-токенов с цветовой маркировкой назначенных ролей слотов (`TX_AMOUNT`, `CURRENCY`, `CARD_MASK`, `BALANCE`, `MERCHANT`, `LITERAL`). НЕ производит разбор строк, индукцию регулярных выражений и валидацию шаблонов.

### Типы данных
```kotlin
package com.example.npc.feature.editor.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.example.npc.extract.universal.SlotRole

@Immutable
data class TokenChipState(
    val tokenIndex: Int,
    val text: String,
    val role: SlotRole?,
    val isSelectable: Boolean,
    val isHighlighted: Boolean
) {
    val roleColor: Color
        get() = when (role) {
            SlotRole.TX_AMOUNT -> Color(0xFF4CAF50) // Зеленый
            SlotRole.BALANCE   -> Color(0xFF2196F3) // Синий
            SlotRole.FEE       -> Color(0xFFFF9800) // Оранжевый
            SlotRole.OTHER     -> Color(0xFF9E9E9E) // Серый
            null               -> Color(0xFFE0E0E0) // Нейтральный фон
        }
}
```
- **Инварианты:**
  - Отрисовка не аллоцирует новые объекты в recomposition loop (компонент помечен `@Immutable`).
  - Время отрисовки кадра $\le 16.6$ мс (60 FPS на Poco M7).

### Публичный API

#### `TokenGridView`
```kotlin
@Composable
fun TokenGridView(
    chips: List<TokenChipState>,
    onChipClick: (tokenIndex: Int) -> Unit,
    modifier: Modifier = Modifier
)
```
- **Предусловия:** `chips` содержит упорядоченный список токенов сообщения.
- **Постусловия:** Отрисовывает адаптивную сетку чипов с горизонтальным и вертикальным переносом (`FlowRow`). При тапе на чип инициирует обратный вызов `onChipClick(chip.tokenIndex)`.
- **Пошаговое поведение:**
  1. Обернуть в `ExperimentalLayoutApi.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp))`.
  2. Для каждого `TokenChipState`:
     - Отрисовать `FilterChip` / `Surface` с закругленными углами 8.dp.
     - Фон определяется `chip.roleColor`, текст чипа отображает `chip.text`.
     - Назначить модификатор клика `clickable(enabled = chip.isSelectable) { onChipClick(chip.tokenIndex) }`.
- **Ошибки:** Отсутствуют.
- **Побочные эффекты:** Отрисовка UI в Compose Canvas.
- **Граничные случаи:** Длинный текст токена (> 30 символов) переносится без обрезки; пустой список чипов отображает пустой контейнер.
- **Примеры:**
  1. *Вход:* 4 чипа `["Restituire", "245,90", "MDL", "TEMU.COM"]`.  
     *Выход:* Чип "245,90" подсвечен зеленым (TX_AMOUNT), "MDL" зеленым, "TEMU.COM" фиолетовым.
  2. *Вход:* Тап по чипу с индексом 1.  
     *Выход:* Срабатывание `onChipClick(1)`.
  3. *Вход:* Чип пробела/переноса строки -> отображается как визуальный разделитель.

### Внутренние функции
- `private fun resolveChipBorder(isHighlighted: Boolean): BorderStroke?`

### Зависимости
- Jetpack Compose Foundation, Material 3.

### Вне скоупа
- Модальные выпадающие меню (обрабатываются в `TemplateEditorSheet`).

---

## Модуль: EditorViewModel  Зона: zone/ui-features  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Управляет MVI-состоянием One-Tap редактора шаблонов: связывает разметку токенов, реактивную фоновую индукцию регулярных выражений с дебаунсом 150 мс, компиляцию в RE2/J через `PipelineCompiler`, запуск Replay по выборке 500 локальных событий и атомарное сохранение валидированного шаблона в `TemplateBankManager`. НЕ взаимодействует напрямую с UI-элементами.

### Типы данных
```kotlin
package com.example.npc.feature.editor

import androidx.lifecycle.ViewModel
import com.example.npc.extract.universal.SlotRole
import com.example.npc.feature.common.mvi.BaseMviStore
import com.example.npc.feature.editor.ui.TokenChipState

data class EditorState(
    val eventId: String,
    val sourcePackage: String,
    val rawText: String,
    val chips: List<TokenChipState>,
    val slotAssignments: Map<Int, SlotRole>,
    val candidatePattern: String?,
    val lintErrors: List<String>,
    val replayMatchesCount: Int,
    val replayConflictsCount: Int,
    val isReplayRunning: Boolean,
    val isSaving: Boolean,
    val canSave: Boolean
)

sealed interface EditorIntent {
    data class AssignTokenRole(val tokenIndex: Int, val role: SlotRole?) : EditorIntent
    object ConfirmAndSave : EditorIntent
    object Dismiss : EditorIntent
}

sealed interface EditorEffect {
    object CloseSheet : EditorEffect
    data class ShowToast(val message: String) : EditorEffect
}
```
- **Инварианты:**
  - `canSave == true` тогда и только тогда, когда:
    1. Назначена ровно 1 сумма `TX_AMOUNT`.
    2. `candidatePattern != null` и `lintErrors.isEmpty()`.
    3. `replayConflictsCount == 0` (нет конфликтов с существующими правилами).
    4. `isSaving == false`.

### Публичный API

#### `EditorViewModel.send`
```kotlin
fun send(intent: EditorIntent)
```
- **Предусловия:** ViewModel инициализирована `eventId`.
- **Постусловия:** Обновляет состояние редактора.
- **Пошаговое поведение при смене слота (`AssignTokenRole`):**
  1. Обновить словарь `slotAssignments`.
  2. Пересчитать цвета чипов в `chips`.
  3. Запустить цепочку индукции с `debounce(150.milliseconds)` в фоновом `Dispatchers.Default`:
     - Вызвать `TokenSegmenter.segment(tokens, slots)`.
     - Сгенерировать шаблон через `TemplateBuilder.build`.
     - Применить `RightBoundedRule.enforce`.
     - Запустить `TemplateLint.lint`.
     - Если линт пройден: запустить `RoundTripValidator.validate` на исходном тексте и фоновый Replay по последним 500 событиям.
  4. Обновить поля `candidatePattern`, `lintErrors`, `replayMatchesCount`, `canSave`.
- **Пошаговое поведение при сохранении (`ConfirmAndSave`):**
  1. Установить `isSaving = true`.
  2. Сконструировать `DynamicTemplateDraft(origin = "USER", state = "ACTIVE")`.
  3. Вызвать `templateBankManager.registerDraft(draft)`.
  4. Вызвать `templateBankManager.activateTemplate(templateId)`.
  5. Сэмитировать `EditorEffect.ShowToast("Шаблон активирован")` и `EditorEffect.CloseSheet`.
- **Ошибки:** Ошибки линта и компиляции отображаются в `state.lintErrors`, не краша приложение.
- **Побочные эффекты:** Запись в базу Room, смена поколения в `RuntimeGenerationHolder`.
- **Граничные случаи:** Быстрое изменение нескольких токенов пользователем (дебаунс объединяет операции в один проход индукции).
- **Примеры:**
  1. *Вход:* Нажатие кнопки «Сохранить и активировать» при валидном шаблоне MAIB.  
     *Выход:* Шаблон записан в Room со статусом `ACTIVE`, банк получил версию 1, диалог закрыт.
  2. *Вход:* Пользователь снял роль с суммы.  
     *Выход:* `canSave = false`, подсказка «Выберите сумму операции».
  3. *Вход:* Конфликт с существующим шаблоном на Replay.  
     *Выход:* `replayConflictsCount = 1`, отображение предупреждения.

### Внутренние функции
- `private suspend fun runInductionPipeline(slots: Map<Int, SlotRole>)`
- `private suspend fun runReplaySafetyCheck(pattern: String): ReplayStats`

### Зависимости
- `TemplateBuilder`, `TemplateLint`, `RoundTripValidator`, `TemplateBankManager`, `PipelineReplayGateway`.

### Вне скоупа
- Синхронизация черновиков с сервером.

---

## Модуль: TemplateEditorSheet  Зона: zone/ui-features  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Предоставляет всплывающий модальный диалог Compose (`ModalBottomSheet`), реализующий Human-in-the-Loop интерфейс: отображение текста сообщения в виде токенов `TokenGridView`, выбор ролей во всплывающем меню и сохранение шаблона в рантайм в один клик (One-Tap HITL, ADR-377). НЕ содержит прямой бизнес-логики индукции и проверки регулярок.

### Типы данных
```kotlin
package com.example.npc.feature.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.npc.feature.editor.EditorViewModel

@Composable
fun TemplateEditorSheet(
    eventId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel
)
```

### Публичный API

#### `TemplateEditorSheet`
```kotlin
@Composable
fun TemplateEditorSheet(
    eventId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel
)
```
- **Предусловия:** `eventId` соответствует существующему событию в БД.
- **Постусловия:** Отображает экран редактирования. При `ConfirmAndSave` активирует шаблон и вызывает `onDismiss()`.
- **Пошаговое поведение:**
  1. Подписаться на `state = viewModel.state.collectAsStateWithLifecycle()`.
  2. Подписаться на `viewModel.effects`: при `CloseSheet` вызвать `onDismiss()`.
  3. Отрисовать заголовок с именем пакета приложения-источника.
  4. Отрисовать карточку исходного сообщения с `TokenGridView`.
  5. При тапе по чипу открыть `DropdownMenu` с вариантами:
     - `Сумма операции (TX_AMOUNT)`
     - `Валюта (CURRENCY)`
     - `Маска карты (CARD_MASK)`
     - `Остаток (BALANCE)`
     - `Мерчант (MERCHANT)`
     - `Очистить роль`
  6. Отобразить блок статуса верификации:
     - Индикатор компиляции (зеленый чекбокс при успехе, красный список ошибок `lintErrors`).
     - Результат Replay («Совпадает с 14 событиями в истории, 0 конфликтов»).
  7. Отрисовать закрепленную нижнюю панель с кнопкой **«Сохранить и активировать»** (`enabled = state.canSave`).
- **Ошибки:** Отсутствуют.
- **Побочные эффекты:** Отображение UI на экране Android устройства.
- **Граничные случаи:** Поворот экрана (состояние сохраняется через ViewModel).
- **Примеры:**
  1. *Вход:* Открытие диалога для инцидента MAIB TEMU.  
     *Выход:* Токены уже предразмечены Universal Extractor'ом, кнопка «Сохранить и активировать» активна сразу. Одно нажатие закрывает диалог.
  2. *Вход:* Нераспознанное SMS.  
     *Выход:* Токены нейтральные, пользователь тапает на число -> выбирает «Сумма», тапает на мерчанта -> выбирает «Мерчант», кнопка активируется.
  3. *Вход:* Пользователь нажал крестик закрытия -> вызов `onDismiss()`.

### Внутренние функции
- `private fun RoleSelectionMenu(...)`

### Зависимости
- `EditorViewModel`, `TokenGridView`, Material 3 Compose.

### Вне скоупа
- Редактирование регулярного выражения вручную текстом (только визуальный выбор ролей).

---

## Модуль: FinancialAnalyticsScreen  Зона: zone/ui-features  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Главный экран финансовой аналитики, отображающий изолированные агрегированные карточки оборотов и остатков по валютам региона (MDL, RUP, USD, EUR, RUB согласно ADR-305 без кросс-конвертации), тумблер режима учета возвратов (ADR-306), Canvas-графики динамики трат и постраничный список транзакций Paging 3. НЕ выполняет сетевых запросов к валютным курсам и не изменяет данные транзакций в БД.

### Типы данных
```kotlin
package com.example.npc.feature.analytics

import androidx.compose.runtime.Immutable
import com.example.npc.core.model.CurrencyCode
import com.example.npc.core.model.FinancialTransaction
import kotlinx.coroutines.flow.Flow

enum class AnalyticsTimePeriod {
    TODAY,
    THIS_WEEK,
    THIS_MONTH,
    CUSTOM
}

@Immutable
data class CurrencyAggregate(
    val currency: CurrencyCode,
    val totalExpenseMinor: Long,
    val totalIncomeMinor: Long,
    val latestBalanceMinor: Long?,
    val transactionCount: Int
)

@Immutable
data class AnalyticsState(
    val selectedPeriod: AnalyticsTimePeriod,
    val isRefundSubtractedFromExpense: Boolean, // ADR-306: по умолчанию true
    val activeCurrencyFilter: CurrencyCode?,
    val aggregates: Map<CurrencyCode, CurrencyAggregate>,
    val isLoading: Boolean
)
```
- **Инварианты:**
  - Никакой автоматической конвертации между валютами (MDL, RUP, USD, EUR, RUB считаются строго раздельно).
  - При `isRefundSubtractedFromExpense == true`: сумма возвратов вычитается из `totalExpenseMinor`.
  - При `isRefundSubtractedFromExpense == false`: сумма возвратов прибавляется к `totalIncomeMinor`.

### Публичный API

#### `FinancialAnalyticsScreen`
```kotlin
@Composable
fun FinancialAnalyticsScreen(
    viewModel: FinancialAnalyticsViewModel,
    onTransactionClick: (transactionId: String) -> Unit,
    modifier: Modifier = Modifier
)
```
- **Предусловия:** `viewModel` подключен к `StorageGateway`.
- **Постусловия:** Отрисовывает интерфейс аналитики со стабильными 60 FPS на Poco M7.
- **Пошаговое поведение:**
  1. Подписаться на состояние `analyticsState = viewModel.state.collectAsStateWithLifecycle()`.
  2. Отрисовать верхнюю панель фильтра периода (Сегодня / Неделя / Месяц).
  3. Отрисовать переключатель (Switch) «Возвраты уменьшают расход».
  4. Отрисовать горизонтальную карусель мультивалютных карточек (`LazyRow`):
     - Карточка каждой валюты (MDL, RUP, USD...) показывает расходы, доходы и последний баланс.
     - Клик по карточке фильтрует нижний список по выбранной валюте.
  5. Отрисовать аппаратный Canvas-график распределения расходов по дням.
  6. Отрисовать список транзакций через `LazyColumn` с Paging 3. Каждая строка отображает мерчанта, дату, статус (`CONFIRMED` vs `SUGGESTED`), тип и сумму.
- **Ошибки:** Отсутствуют.
- **Побочные эффекты:** Отрисовка Jetpack Compose.
- **Граничные случаи:** Отсутствие транзакций за период (отображается заглушка «Нет операций»); только одна валюта в базе (карточки остальных валют скрываются).
- **Примеры:**
  1. *Вход:* 1 покупка 100 MDL и 1 возврат 40 MDL при `isRefundSubtractedFromExpense = true`.  
     *Выход:* В карточке MDL расход отображается как 60 MDL.
  2. *Вход:* Переключение тумблера в `false`.  
     *Выход:* В карточке MDL расход 100 MDL, доход 40 MDL.
  3. *Вход:* Транзакции в RUP и MDL.  
     *Выход:* Две независимые карточки без попыток пересчета курса.

### Внутренние функции
- `private fun DrawExpenseChart(canvas: DrawScope, dataPoints: List<Long>)`

### Зависимости
- `StorageGateway`, Jetpack Compose Material 3, AndroidX Paging Compose.

### Вне скоупа
- Интеграция с внешними банковскими API.

---

## Модуль: OrchestratorDiagnosticsScreen  Зона: zone/ui-features  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Предоставляет инженерный экран мониторинга и отладки рантайма конвейера в реальном времени: чтение кольцевого буфера трейсов `TraceRing` без блокировки горячего пути (lock-free seqlock), инспекция очередей событий, алерты `financeWithoutPayload` и отображение статусов предохранителей `NodeCircuitBreaker` с функцией ручного сброса (Reset Breaker). НЕ изменяет логику исполнения конвейера и не сохраняет приватные тексты сообщений (PII) в незашифрованном виде.

### Типы данных
```kotlin
package com.example.npc.feature.diagnostics

import androidx.compose.runtime.Immutable

enum class CircuitBreakerStatus {
    CLOSED,     // Нормальное исполнение
    OPEN,       // Узел отключен из-за сбоев (карантин)
    HALF_OPEN   // Пробный прогон
}

@Immutable
data class NodeCircuitBreakerState(
    val nodeId: String,
    val status: CircuitBreakerStatus,
    val failureCount: Int,
    val lastFailureTimestamp: Long?,
    val failureReason: String?
)

@Immutable
data class DiagnosticsTraceEntry(
    val seq: Long,
    val eventId: String,
    val sourcePackage: String,
    val durationMs: Float,
    val terminalStatus: String, // PASS | DROP | DEGRADED
    val activePipelineRevision: Long,
    val activeBankVersion: Long,
    val executedNodes: List<String>
)

@Immutable
data class DiagnosticsState(
    val queueDepth: Int,
    val throughputEventsPerMinute: Double,
    val totalProcessedEvents: Long,
    val financeWithoutPayloadCount: Long,
    val circuitBreakers: List<NodeCircuitBreakerState>,
    val recentTraces: List<DiagnosticsTraceEntry>,
    val isAutoRefreshEnabled: Boolean
)
```
- **Инварианты:**
  - Чтение трейсов выполняется через lock-free probe без блокировки потока `NotificationListenerService`.
  - В отображаемых трейсах полностью отсутствуют PII-данные (номера карт, пароли, сырой текст).

### Публичный API

#### `OrchestratorDiagnosticsScreen`
```kotlin
@Composable
fun OrchestratorDiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    onResetCircuitBreaker: (nodeId: String) -> Unit,
    modifier: Modifier = Modifier
)
```
- **Предусловия:** `viewModel` инициализирован `OrchestratorProbe`.
- **Постусловия:** Отрисовывает экран диагностики с автообновлением каждые 1000 мс.
- **Пошаговое поведение:**
  1. Подписаться на `diagnosticsState`.
  2. Отрисовать информационную панель здоровья конвейера:
     - Длина очереди событий `queueDepth` (зеленая, если $< 10$, красная при переполнении).
     - Скорость обработки событий / мин.
     - Счетчик аномалий `financeWithoutPayloadCount` (пуши, помеченные как финансы, но оставшиеся без полезной нагрузки).
  3. Отрисовать сетку предохранителей узлов `circuitBreakers`:
     - Статус каждого узла (`CLOSED` = зеленый, `OPEN` = красный).
     - Если статус `OPEN`: отобразить кнопку **«Сбросить предохранитель»** (вызывает `onResetCircuitBreaker(nodeId)`).
  4. Отрисовать список последних 128 трейсов `TraceRing` с детализацией пути исполнения события, ревизии конвейера и версии банка шаблонов.
- **Ошибки:** Отсутствуют.
- **Побочные эффекты:** Сброс внутренних предохранителей `NodeCircuitBreaker` при явном действии пользователя.
- **Граничные случаи:** Буфер трейсов пуст при холодном старте; аварийное срабатывание предохранителя универсального экстрактора.
- **Примеры:**
  1. *Вход:* Сбой динамического шаблона привел к срабатыванию предохранителя `extract.template_bank`.  
     *Выход:* Карточка узла окрашена в красный цвет, отображена кнопка «Сбросить».
  2. *Вход:* Нажатие кнопки «Сбросить предохранитель».  
     *Выход:* Статус узла переходит в `HALF_OPEN`, счетчик сбоев обнуляется.
  3. *Вход:* Всплеск пушей -> отображение роста длины очереди.

### Внутренние функции
- `private fun readTraceRingSnapshot(probe: OrchestratorProbe): List<DiagnosticsTraceEntry>`

### Зависимости
- `OrchestratorProbe`, `NodeCircuitBreaker`, Jetpack Compose Material 3.

### Вне скоупа
- Удаление системных логов Android (Logcat).
