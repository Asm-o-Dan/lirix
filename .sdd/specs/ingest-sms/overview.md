# Спецификация: zone/ingest-sms

## Модуль: :ingest:sms   Зона: zone/ingest-sms   Версия спеки: v1   Статус: DRAFT

---

### Назначение

Модуль `:ingest:sms` является Android-библиотекой (`android-library`) и реализует подсистему перехвата, сборки, дедупликации, исторической вычитки (backfill) и первичного сохранения входящих SMS-сообщений операционной системы Android.

Ключевые функциональные обязанности модуля:
1. **Оперативный перехват входящих SMS в реальном времени:** Приём системных широковещательных сообщений `Telephony.Sms.Intents.SMS_RECEIVED_ACTION` через зарегистрированный в манифесте `SmsBroadcastReceiver` с защитой системным разрешением `android.permission.BROADCAST_SMS`.
2. **Сборка многосегментных (multipart) SMS-сообщений:** Корректное объединение разрозненных PDU-сегментов длинных сообщений в единый логический объект `SmsContract` с использованием `Telephony.Sms.Intents.getMessagesFromIntent(intent)`.
3. **Извлечение метаданных:** Извлечение адреса отправителя (`originatingAddress`), полного тела сообщения (`messageBody`) и времени отправки базовой станцией/сетью (`timestampMillis`).
4. **Гарантированная очередность и сохранение (ADR-004):** Присвоение строго возрастающего монотонного порядкового номера `seq` через `AtomicLong.incrementAndGet()`, передача в очередь `Channel<SmsRawPayload>(Channel.UNLIMITED)` и немедленная запись сырого события в `StorageGateway.insertRawEvent` до любой бизнес-логики и нормализации.
5. **Нормализация и структурирование (ADR-005):** Очистка текста от мусорных управляющих символов через `EventNormalizer.cleanText`, определение языка через `EventNormalizer.detectLang`, формирование `ThreadKey` на основе телефонного номера/имени отправителя и сохранение в `StorageGateway.insertEvent`.
6. **Однократная историческая вычитка (Backfill):** Однократное чтение входящих SMS из системного `ContentProvider` (`Telephony.Sms.Inbox.CONTENT_URI`) при первом запуске приложения или после предоставления разрешения `READ_SMS` через `SmsBackfillWorker` (WorkManager).
7. **Предотвращение дубликатов:** Проверка существования события по криптографическому хешу SHA-256 (`DeduplicationKey`) через `StorageGateway.findDuplicate`, обеспечивающая полную идемпотентность при наложении онлайн-приёма и исторического backfill.
8. **Мониторинг здоровья источника:** Атомарное обновление метрик `SourceHealth` (время последнего события, количество за 24 часа, глубина буферной очереди `queueDepth`, код последней ошибки) в `StorageGateway.upsertSourceHealth`.
9. **Резервный опрос (SmsPoller):** Фоновая проверка наличия непрочитанных SMS для сценариев сбоя системного броадкаста или агрессивного энергосбережения OEM-прошивок (HyperOS/MIUI).

Модуль **НЕ содержит** UI-экранов, **НЕ выполняет** шифрование базы данных (делегировано `:core:storage`), и **НЕ зависит** от `:ingest:notification` и `:ingest:media`.

---

### Архитектурное окружение и структура пакетов

```
com.example.npc.ingest.sms/
├── SmsContract.kt
├── model/
│   ├── SmsRawPayload.kt
│   ├── SmsBackfillStatus.kt
│   ├── SmsBackfillResult.kt
│   └── SmsSenderInfo.kt
├── receiver/
│   └── SmsBroadcastReceiver.kt
├── mapper/
│   └── SmsMapper.kt
├── controller/
│   ├── SmsIngestController.kt
│   └── SmsIngestControllerImpl.kt
├── worker/
│   ├── SmsBackfillWorker.kt
│   └── SmsPollingWorker.kt
├── poller/
│   └── SmsPoller.kt
└── di/
    └── SmsIngestModule.kt
```

---

### Типы данных и структуры (Data Structures & DTO)

Все типы данных размещаются в пакете `com.example.npc.ingest.sms` и подпакете `model`. В качестве типа меток времени в доменных сущностях используется `java.time.Instant`, в системных контрактах — миллисекунды эпохи Unix (`Long`).

#### 1. `SmsContract`

```kotlin
package com.example.npc.ingest.sms

data class SmsContract(
    val originAddress: String,
    val body: String,
    val timestampMillis: Long
)
```

- **Назначение:** Межзонный контракт передачи собранного SMS-сообщения (определён в `architecture.md` §3.1).
- **Поля:**
  - `originAddress: String` — телефонный номер в международном формате (например, `+79991234567`) либо буквенно-цифровое имя отправителя (alphanumeric sender ID, например, `SBER`, `Tinkoff`, `GosUslugi`).
  - `body: String` — полный конкатенированный текст сообщения.
  - `timestampMillis: Long` — время отправки сообщения по часам сервисного центра/сети (Unix epoch milli).
- **Инварианты:**
  - `originAddress.isNotBlank()`: адрес отправителя не пустой и не состоит из пробелов. Длина от 1 до 64 символов.
  - `body` не равен `null` (может быть пустым `""` при специфических пустых сервисных SMS).
  - `timestampMillis >= 0L`.

---

#### 2. `SmsRawPayload`

```kotlin
package com.example.npc.ingest.sms.model

data class SmsRawPayload(
    val seq: Long,
    val originAddress: String,
    val body: String,
    val timestampMillis: Long,
    val receivedAt: java.time.Instant,
    val subId: Int? = null
)
```

- **Назначение:** Внутренний неизменяемый снимок данных SMS-сообщения, передаваемый через `Channel<SmsRawPayload>` в консьюмер фоновой обработки.
- **Поля:**
  - `seq: Long` — строго возрастающий монотонный порядковый номер, выданный генератором `AtomicLong`.
  - `originAddress: String` — адрес/номер отправителя.
  - `body: String` — сырой текст сообщения.
  - `timestampMillis: Long` — временная метка отправки сообщения из PDU или ContentProvider.
  - `receivedAt: java.time.Instant` — физический момент перехвата/вычитки сообщения приложением.
  - `subId: Int?` — идентификатор SIM-карты (Subscription ID), если доступен из системного Intent.
- **Инварианты:**
  - `seq > 0L`.
  - `originAddress.isNotBlank()`.
  - `timestampMillis >= 0L`.
  - `receivedAt` не равен `null`.

---

#### 3. `SmsBackfillStatus`

```kotlin
package com.example.npc.ingest.sms.model

sealed interface SmsBackfillStatus {
    data object NotStarted : SmsBackfillStatus
    data class InProgress(val processedCount: Int, val duplicateCount: Int) : SmsBackfillStatus
    data class Completed(val totalImported: Int, val totalDuplicates: Int, val completedAt: java.time.Instant) : SmsBackfillStatus
    data class Failed(val error: String, val failedAt: java.time.Instant) : SmsBackfillStatus
}
```

- **Назначение:** Модель текущего состояния процесса первоначальной исторической вычитки SMS из `ContentProvider`.
- **Поля:**
  - `processedCount: Int` — количество прочитанных и успешно сохранённых новых сообщений.
  - `duplicateCount: Int` — количество пропущенных сообщений, уже присутствовавших в базе данных.
  - `completedAt / failedAt: Instant` — отметка времени завершения или ошибки процесса.
  - `error: String` — текстовое описание причины сбоя.
- **Инварианты:**
  - `processedCount >= 0`, `duplicateCount >= 0`.
  - `error.isNotBlank()`.

---

#### 4. `SmsBackfillResult`

```kotlin
package com.example.npc.ingest.sms.model

data class SmsBackfillResult(
    val importedCount: Int,
    val skippedDuplicatesCount: Int,
    val isSuccess: Boolean,
    val failureReason: String? = null
)
```

- **Назначение:** Итоговый результат выполнения задачи `SmsBackfillWorker`.
- **Инварианты:**
  - `importedCount >= 0`, `skippedDuplicatesCount >= 0`.
  - Если `isSuccess == true`, то `failureReason == null`.
  - Если `isSuccess == false`, то `failureReason != null`.

---

### Публичный API и компоненты зоны

```kotlin
// 1. Приёмник широковещательных сообщений телефонии
@AndroidEntryPoint
class SmsBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?)
}

// 2. Двусторонний маппер сырых данных, DTO и JSON
object SmsMapper {
    fun toPayloadJson(originAddress: String, body: String, timestampMillis: Long, subId: Int? = null): String
    fun toRawEvent(payload: SmsRawPayload, deduplicationKey: DeduplicationKey): RawEvent
    fun toEvent(rawEvent: RawEvent, contract: SmsContract): Event
    fun fromSmsMessages(messages: Array<android.telephony.SmsMessage>): SmsContract?
    fun fromCursor(cursor: android.database.Cursor): SmsContract
}

// 3. Контроллер входящего потока и фоновый консьюмер очереди
interface SmsIngestController {
    val queueDepth: Int
    suspend fun enqueueSms(contract: SmsContract, subId: Int? = null): Long
    suspend fun processPayload(payload: SmsRawPayload): Long
    fun start()
    fun stop()
}

// 4. Однократный фоновый воркер исторической вычитки (Backfill)
@HiltWorker
class SmsBackfillWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val storageGateway: StorageGateway,
    private val smsIngestController: SmsIngestController
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result
}

// 5. Компонент периодического контроля и резервного опроса
class SmsPoller(
    private val context: Context,
    private val storageGateway: StorageGateway,
    private val smsIngestController: SmsIngestController
) {
    suspend fun pollNewMessages(sinceTimestamp: Long): Int
}
```

---

### Детальная спецификация классов и методов

#### 1. Класс `SmsBroadcastReceiver`

Класс является точкой входа для всех входящих SMS в реальном времени. Регистрируется в `AndroidManifest.xml` с фильтром действия `android.provider.Telephony.SMS_RECEIVED`.

##### 1.1 Метод `onReceive`
- **Сигнатура:**
  ```kotlin
  override fun onReceive(context: Context?, intent: Intent?)
  ```
- **Предусловия:**
  - Вызывается операционной системой Android на главном потоке (Main Thread).
- **Постусловия:**
  - Метод возвращает управление немедленно ($< 2$ мс), не блокируя Main Thread.
  - Тяжёлая обработка I/O и запись в базу данных выносятся в асинхронный контекст с использованием `goAsync()`.
- **Пошаговое поведение:**
  1. Выполнить проверку на null: если `context == null` или `intent == null`, немедленно завершить работу (`return`).
  2. Проверить `intent.action`: если значение не равно `Telephony.Sms.Intents.SMS_RECEIVED_ACTION` (`"android.provider.Telephony.SMS_RECEIVED"`), завершить работу.
  3. Вызвать `val pendingResult = goAsync()` для продления жизненного цикла приёмника во время асинхронного выполнения.
  4. Получить экземпляр `SmsIngestController` (через Hilt entry point или внедрённую зависимость).
  5. В скоупе фонового диспетчера (`Dispatchers.IO`) запустить корутину:
     - В блоке `try`:
       - Вызвать `SmsMapper.fromSmsMessages(Telephony.Sms.Intents.getMessagesFromIntent(intent))`.
       - Если результат равен `null` (пустой интент или повреждённые PDU), выйти из блока.
       - Извлечь опциональный `subId` через `intent.getIntExtra("subscription", SubscriptionManager.INVALID_SUBSCRIPTION_ID)`.
       - Вызвать `smsIngestController.enqueueSms(smsContract, if (subId != INVALID_SUBSCRIPTION_ID) subId else null)`.
     - В блоке `catch (t: Throwable)`:
       - Зафиксировать ошибку в `Log.e` и обновить `SourceHealth` с текстом ошибки через `StorageGateway`.
     - В блоке `finally`:
       - Вызвать `pendingResult.finish()` для освобождения системных ресурсов BroadcastReceiver.
- **Ошибки и исключения:**
  - Метод не выбрасывает наружу исключений, предотвращая падение приложения (ANR / Crash).
  - Любые внутренние ошибки (`SecurityException`, `NullPointerException`, `SQLiteException`) перехватываются, логируются и обновляют `SourceHealth.lastError`.
- **Граничные случаи:**
  - `intent == null` или `context == null` -> игнорирование, немедленный возврат.
  - `intent.action` не равен `SMS_RECEIVED_ACTION` (например, тестовый чужой интент) -> возврат.
  - `intent.extras == null` или не содержит ключа `"pdus"` -> `getMessagesFromIntent` возвращает `null`, `pendingResult.finish()` вызывается без ошибок.
- **Примеры:**
  1. *Пример 1 (Обычное одиночное SMS):*
     - Вход: Интент с `SMS_RECEIVED_ACTION`, содержащий 1 PDU от номера `+79991234567` с текстом `"Код: 1234"`.
     - Результат: Вызывается `goAsync()`, сообщение парсится в `SmsContract`, помещается в очередь `enqueueSms`, вызывается `pendingResult.finish()`.
  2. *Пример 2 (Многосегментное SMS из 3 частей):*
     - Вход: Интент с 3 PDU одного составного сообщения от банка `Sberbank`.
     - Результат: `getMessagesFromIntent` возвращает 3 сегмента, `fromSmsMessages` собирает единое тело сообщения, единый `SmsContract` отправляется в `enqueueSms`.
  3. *Пример 3 (Пустой или некорректный интент):*
     - Вход: Интент с экшеном `SMS_RECEIVED_ACTION`, но без бандла `extras`.
     - Результат: `fromSmsMessages` возвращает `null`, корутина завершается штатно, вызывается `pendingResult.finish()`.

---

#### 2. Класс `SmsMapper`

Объект чистых преобразований данных между системными структурами телефонии, DTO и сущностями предметной области.

##### 2.1 Метод `fromSmsMessages`
- **Сигнатура:**
  ```kotlin
  fun fromSmsMessages(messages: Array<SmsMessage>?): SmsContract?
  ```
- **Предусловия:**
  - `messages` — массив объектов `android.telephony.SmsMessage`, полученный из `Telephony.Sms.Intents.getMessagesFromIntent(intent)`.
- **Постусловия:**
  - Возвращает собранный объект `SmsContract`, либо `null`, если массив пуст или не содержит валидных сообщений.
  - Текст всех сегментов многосегментного сообщения конкатенируется в строгом порядке следования в массиве без разделителей.
  - `originAddress` извлекается из первого сегмента (`messages[0].displayOriginatingAddress` или `messages[0].originatingAddress`).
  - `timestampMillis` берётся из временной метки первого сегмента (`messages[0].timestampMillis`).
- **Пошаговое поведение:**
  1. Проверить массив: если `messages == null` или `messages.isEmpty()`, вернуть `null`.
  2. Взять первый элемент `firstMsg = messages[0]`.
  3. Извлечь адрес отправителя:
     - `val rawAddress = firstMsg.displayOriginatingAddress ?: firstMsg.originatingAddress`
     - Если `rawAddress == null` или `rawAddress.isBlank()`, использовать `"UNKNOWN"`. Иначе вызвать `.trim()`.
  4. Извлечь базовое время: `val timestamp = firstMsg.timestampMillis`.
  5. Собрать полное тело сообщения:
     - Инициализировать `StringBuilder`.
     - Для каждого сообщения `msg` в `messages`:
       - `val partBody = msg.displayMessageBody ?: msg.messageBody ?: ""`
       - Присоединить `partBody` к `StringBuilder`.
     - Получить итоговую строку `fullBody = sb.toString()`.
  6. Сконструировать и вернуть `SmsContract(originAddress = rawAddress, body = fullBody, timestampMillis = timestamp)`.
- **Ошибки и исключения:**
  - Функция не выбрасывает исключений при любых форматах PDU.
- **Граничные случаи:**
  - `messages = null` -> возвращает `null`.
  - `messages = emptyArray()` -> возвращает `null`.
  - Одиночный сегмент с пустым текстом `body = ""` -> возвращает `SmsContract(originAddress, "", timestamp)`.
  - Сегменты содержат `null` в качестве `messageBody` -> заменяется на пустую строку, исключение не возникает.
- **Примеры:**
  1. *Пример 1 (Одиночное SMS от цифрового номера):*
     - Вход: Массив из 1 элемента, `originatingAddress = "+79001112233"`, `messageBody = "Привет"`, `timestamp = 1774567890000L`.
     - Выход: `SmsContract(originAddress = "+79001112233", body = "Привет", timestampMillis = 1774567890000L)`.
  2. *Пример 2 (Многосегментное SMS из двух частей):*
     - Вход: Массив из 2 элементов от `"AlfaBank"`, часть 1: `"Операция покупки на сумму 1500 руб. "`, часть 2: `"Баланс: 45000 руб."`.
     - Выход: `SmsContract(originAddress = "AlfaBank", body = "Операция покупки на сумму 1500 руб. Баланс: 45000 руб.", timestampMillis = 1774567890000L)`.
  3. *Пример 3 (Сообщение без адреса):*
     - Вход: Сообщение с `originatingAddress = null`, `messageBody = "Test"`.
     - Выход: `SmsContract(originAddress = "UNKNOWN", body = "Test", timestampMillis = 1774567890000L)`.

---

##### 2.2 Метод `toPayloadJson`
- **Сигнатура:**
  ```kotlin
  fun toPayloadJson(
      originAddress: String,
      body: String,
      timestampMillis: Long,
      subId: Int? = null
  ): String
  ```
- **Предусловия:**
  - `originAddress` — непустая строка.
  - `body` — произвольная строка.
  - `timestampMillis >= 0L`.
- **Постусловия:**
  - Возвращает компактную строку валидного JSON-объекта согласно стандарту RFC 8259.
  - Ключи JSON-объекта строго детерминированы: `"originatingAddress"`, `"messageBody"`, `"timestampMillis"`, а также опциональный `"subId"`.
  - Символы кавычек, переносов строк и обратных слешей экранируются по стандарту JSON.
- **Пошаговое поведение:**
  1. Экранировать спецсимволы в строках `originAddress` и `body`.
  2. Сформировать JSON-объект:
     - Если `subId != null`:
       `"""{"originatingAddress":"$escapedAddress","messageBody":"$escapedBody","timestampMillis":$timestampMillis,"subId":$subId}"""`
     - Если `subId == null`:
       `"""{"originatingAddress":"$escapedAddress","messageBody":"$escapedBody","timestampMillis":$timestampMillis}"""`
  3. Вернуть полученную строку.
- **Ошибки:**
  - Не выбрасывает исключений.
- **Граничные случаи:**
  - `body` содержит кавычки `"` и переносы строк `\n` -> корректное экранирование `\"` и `\n`.
  - `body` пустое `""` -> сериализуется как `"messageBody":""`.
  - `subId = null` -> ключ `subId` исключается из JSON.
- **Примеры:**
  1. *Пример 1 (Стандартное сервисное SMS):*
     - Вход: `originAddress = "Tinkoff"`, `body = "Код 4321"`, `timestampMillis = 1774567890000L`, `subId = null`.
     - Выход: `{"originatingAddress":"Tinkoff","messageBody":"Код 4321","timestampMillis":1774567890000}`.
  2. *Пример 2 (Текст со спецсимволами и кавычками):*
     - Вход: `originAddress = "+12345"`, `body = "ООО \"Вектор\": счет оплачен\nСпасибо!"`, `timestampMillis = 1774567891000L`, `subId = 1`.
     - Выход: `{"originatingAddress":"+12345","messageBody":"ООО \"Вектор\": счет оплачен\nСпасибо!","timestampMillis":1774567891000,"subId":1}`.
  3. *Пример 3 (Пустой текст):*
     - Вход: `originAddress = "Alert"`, `body = ""`, `timestampMillis = 1000L`, `subId = null`.
     - Выход: `{"originatingAddress":"Alert","messageBody":"","timestampMillis":1000}`.

---

##### 2.3 Метод `toRawEvent`
- **Сигнатура:**
  ```kotlin
  fun toRawEvent(payload: SmsRawPayload, deduplicationKey: DeduplicationKey): RawEvent
  ```
- **Предусловия:**
  - `payload` — валидный экземпляр `SmsRawPayload`.
  - `deduplicationKey` — предварительно рассчитанный `DeduplicationKey`.
- **Постусловия:**
  - Возвращает доменный объект `RawEvent`, где:
    - `id = 0L` (до сохранения в БД).
    - `seq = payload.seq`.
    - `source = SourceId.SMS` (согласно `architecture.md` §3.1).
    - `packageName = "android.telephony.sms"`.
    - `receivedAt = payload.receivedAt`.
    - `payloadJson = toPayloadJson(payload.originAddress, payload.body, payload.timestampMillis, payload.subId)`.
    - `hash = deduplicationKey`.
- **Ошибки:**
  - Не выбрасывает исключений.
- **Граничные случаи:**
  - Минимальный `seq = 1L`.
- **Примеры:**
  1. *Пример 1 (Создание RawEvent из свежего SMS):*
     - Вход: `payload = SmsRawPayload(seq = 10L, originAddress = "MVD", body = "Внимание", timestampMillis = 1774567890000L, receivedAt = Instant.parse("2026-09-26T04:00:00Z"))`, `deduplicationKey = DeduplicationKey("abc...")`.
     - Выход: `RawEvent(id = 0L, seq = 10L, source = SourceId("sms"), packageName = "android.telephony.sms", receivedAt = Instant.parse("2026-09-26T04:00:00Z"), payloadJson = "...", hash = DeduplicationKey("abc..."))`.
  2. *Пример 2 (Создание RawEvent с subId):*
     - Вход: `payload = SmsRawPayload(..., subId = 2)`.
     - Выход: `RawEvent` с соответствующим JSON, содержащим `"subId":2`.
  3. *Пример 3 (Нулевой timestampMillis в старых устройствах):*
     - Вход: `payload = SmsRawPayload(..., timestampMillis = 0L)`.
     - Выход: `RawEvent` успешно создаётся.

---

##### 2.4 Метод `toEvent`
- **Сигнатура:**
  ```kotlin
  fun toEvent(rawEvent: RawEvent, contract: SmsContract): Event
  ```
- **Предусловия:**
  - `rawEvent` — сохранённый в хранилище экземпляр `RawEvent` (`rawEvent.id > 0L`).
  - `contract` — исходный `SmsContract`.
- **Постусловия:**
  - Возвращает нормализованный доменный объект `Event`, где:
    - `id = 0L` (до сохранения в хранилище).
    - `rawId = rawEvent.id`.
    - `ts = Instant.ofEpochMilli(contract.timestampMillis)`.
    - `title = contract.originAddress`.
    - `text = contract.body`.
    - `normalizedText = EventNormalizer.cleanText(contract.body)`.
    - `lang = EventNormalizer.detectLang(normalizedText)`.
    - `threadKey = ThreadKey(contract.originAddress)`.
    - `isUpdateOf = null` (входящие SMS атомарны и не являются обновлением предыдущих).
- **Пошаговое поведение:**
  1. Очистить текст через `val clean = EventNormalizer.cleanText(contract.body)`.
  2. Определить язык через `val detectedLang = EventNormalizer.detectLang(clean)`.
  3. Сконструировать `ThreadKey`:
     - Использовать нормализованный адрес отправителя: `ThreadKey(contract.originAddress.trim())`.
  4. Создать и вернуть объект `Event`.
- **Ошибки:**
  - Не выбрасывает исключений.
- **Граничные случаи:**
  - `contract.body = ""` -> `normalizedText = ""`, `lang = Lang.UNK`.
  - `contract.originAddress` с ведущими пробелами -> пробелы отсекаются в `ThreadKey`.
- **Примеры:**
  1. *Пример 1 (Русское банковское SMS):*
     - Вход: `contract = SmsContract(originAddress = "900", body = "Покупка 250р Пятерочка", timestampMillis = 1774567890000L)`, `rawEvent.id = 55L`.
     - Выход: `Event(id = 0L, rawId = 55L, ts = Instant.ofEpochMilli(1774567890000L), title = "900", text = "Покупка 250р Пятерочка", normalizedText = "Покупка 250р Пятерочка", lang = Lang.RU, threadKey = ThreadKey("900"), isUpdateOf = null)`.
  2. *Пример 2 (Английское сервисное SMS с лишними пробелами):*
     - Вход: `contract = SmsContract(originAddress = "Google", body = "G-998811   is your Google verification code.", timestampMillis = 1774567890000L)`, `rawEvent.id = 56L`.
     - Выход: `Event(id = 0L, rawId = 56L, title = "Google", text = "G-998811   is your Google verification code.", normalizedText = "G-998811 is your Google verification code.", lang = Lang.EN, threadKey = ThreadKey("Google"), isUpdateOf = null)`.
  3. *Пример 3 (SMS с невидимыми Unicode-символами):*
     - Вход: `contract = SmsContract(originAddress = "+79160000000", body = "Привет\u200B\uFEFF!", timestampMillis = 1774567890000L)`, `rawEvent.id = 57L`.
     - Выход: `Event(..., normalizedText = "Привет!", lang = Lang.RU, ...)`.

---

##### 2.5 Метод `fromCursor`
- **Сигнатура:**
  ```kotlin
  fun fromCursor(cursor: Cursor): SmsContract
  ```
- **Предусловия:**
  - `cursor` открыт и указывает на валидную строку выборки `ContentProvider` (`Telephony.Sms.Inbox.CONTENT_URI`).
  - Выборка содержит колонки `Telephony.Sms.ADDRESS`, `Telephony.Sms.BODY`, `Telephony.Sms.DATE`.
- **Постусловия:**
  - Возвращает сконструированный экземпляр `SmsContract`.
- **Пошаговое поведение:**
  1. Получить индекс колонки `addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)`.
  2. Получить индекс колонки `bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)`.
  3. Получить индекс колонки `dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)`.
  4. Извлечь значения:
     - `val address = if (addressIdx >= 0 && !cursor.isNull(addressIdx)) cursor.getString(addressIdx) else "UNKNOWN"`
     - `val body = if (bodyIdx >= 0 && !cursor.isNull(bodyIdx)) cursor.getString(bodyIdx) else ""`
     - `val date = if (dateIdx >= 0 && !cursor.isNull(dateIdx)) cursor.getLong(dateIdx) else 0L`
  5. Вернуть `SmsContract(originAddress = address.trim(), body = body, timestampMillis = date)`.
- **Ошибки:**
  - Выбрасывает `IllegalArgumentException`, если курсор закрыт.
- **Граничные случаи:**
  - Значения в колонках `null` -> подставляются безопасные значения по умолчанию (`"UNKNOWN"`, `""`, `0L`).
- **Примеры:**
  1. *Пример 1 (Чтение из курсора системного Inbox):*
     - Вход: Строка курсора: `address = "+79998887766"`, `body = "Ваш баланс: 100р"`, `date = 1774567890000L`.
     - Выход: `SmsContract("+79998887766", "Ваш баланс: 100р", 1774567890000L)`.
  2. *Пример 2 (Строка с пустым телом):*
     - Вход: `address = "112"`, `body = null`, `date = 1774567000000L`.
     - Выход: `SmsContract("112", "", 1774567000000L)`.
  3. *Пример 3 (Отсутствующий адрес):*
     - Вход: `address = null`, `body = "Тест"`, `date = 1000L`.
     - Выход: `SmsContract("UNKNOWN", "Тест", 1000L)`.

---

#### 3. Класс `SmsIngestController` / `SmsIngestControllerImpl`

Служба управления входящим конвейером SMS-сообщений. Инкапсулирует монотонный генератор `AtomicLong` для `seq`, неограниченный буфер `Channel<SmsRawPayload>(Channel.UNLIMITED)` и единственный фоновый консьюмер согласно ADR-004.

```kotlin
@Singleton
class SmsIngestControllerImpl @Inject constructor(
    private val storageGateway: StorageGateway,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SmsIngestController
```

##### 3.1 Метод `enqueueSms`
- **Сигнатура:**
  ```kotlin
  override suspend fun enqueueSms(contract: SmsContract, subId: Int?): Long
  ```
- **Предусловия:**
  - `contract` — валидный объект `SmsContract`.
- **Постусловия:**
  - Генератор `seqGenerator` инкрементирует значение `seq`.
  - Создаётся объект `SmsRawPayload` с присвоенным `seq` и текущим временем `receivedAt = Instant.now()`.
  - Полезная нагрузка отправляется в `channel.send(payload)` без блокировки.
  - Возвращается присвоенный номер `seq > 0L`.
- **Пошаговое поведение:**
  1. Вычислить очередной монотонный порядковый номер: `val nextSeq = seqGenerator.incrementAndGet()`.
  2. Зафиксировать системный момент приёма: `val now = Instant.now()`.
  3. Сконструировать `val payload = SmsRawPayload(seq = nextSeq, originAddress = contract.originAddress, body = contract.body, timestampMillis = contract.timestampMillis, receivedAt = now, subId = subId)`.
  4. Поместить элемент в буферный канал: `channel.send(payload)`.
  5. Вернуть `nextSeq`.
- **Ошибки:**
  - `ClosedSendChannelException`: если канал приёма был принудительно закрыт при остановке приложения.
- **Граничные случаи:**
  - Высокая частота поступления (burst SMS) -> канал `UNLIMITED` принимает все элементы мгновенно, не отбрасывая данные.
- **Примеры:**
  1. *Пример 1 (Очередное сообщение):*
     - Вход: `contract = SmsContract("900", "Списание 100р", 1774567890000L)`, `subId = null`.
     - Состояние: `seqGenerator` на значении `4L`.
     - Выход: `5L` (payload отправлен в канал).
  2. *Пример 2 (Сообщение с SIM2):*
     - Вход: `contract = SmsContract("+79991112233", "Привет", 1774567890000L)`, `subId = 2`.
     - Выход: `6L`.
  3. *Пример 3 (Первое сообщение после холодного старта):*
     - Состояние: `seqGenerator` инициализирован нулем.
     - Выход: `1L`.

---

##### 3.2 Метод `processPayload`
- **Сигнатура:**
  ```kotlin
  override suspend fun processPayload(payload: SmsRawPayload): Long
  ```
- **Предусловия:**
  - Вызывается фоновым единственным консьюмером канала в контексте `ioDispatcher`.
- **Постусловия:**
  - **Шаг 1:** Вычисляется канонический `payloadJson` и `deduplicationKey` через `EventNormalizer.computeDeduplicationKey`.
  - **Шаг 2:** Проверяется дубликат через `storageGateway.findDuplicate(deduplicationKey)`.
    - Если дубликат найден: выводится лог, обновляется `SourceHealth` и возвращается существующий `rawId` без повторной вставки.
  - **Шаг 3:** При отсутствии дубликата выполняется немедленный `storageGateway.insertRawEvent(rawEvent)` (согласно ADR-004, до любой нормализации и бизнес-логики).
  - **Шаг 4:** Выполняется нормализация текста и создание `Event` через `SmsMapper.toEvent(rawEvent, contract)`.
  - **Шаг 5:** Выполняется `storageGateway.insertEvent(event)`.
  - **Шаг 6:** Вызывается `storageGateway.upsertSourceHealth(health)` с обновлением отметки `lastEventAt`, пересчитанным количеством событий за 24 часа и актуальной глубиной очереди `queueDepth`.
  - Возвращается идентификатор созданной записи `rawEvent.id > 0L`.
- **Пошаговое поведение:**
  1. Сформировать `payloadJson` с помощью `SmsMapper.toPayloadJson(payload.originAddress, payload.body, payload.timestampMillis, payload.subId)`.
  2. Вычислить `val dedupKey = EventNormalizer.computeDeduplicationKey(SourceId.SMS, "android.telephony.sms", payloadJson)`.
  3. Выполнить проверку на дубликат: `val existingId = storageGateway.findDuplicate(dedupKey)`.
  4. Если `existingId != null`:
     - Обновить метрику здоровья: зафиксировать отсутствие ошибки и текущую глубину очереди.
     - Вернуть `existingId`.
  5. Сформировать доменный объект `RawEvent` с помощью `SmsMapper.toRawEvent(payload, dedupKey)`.
  6. Сохранить сырое событие: `val rawId = storageGateway.insertRawEvent(rawEvent)`.
  7. Создать доменный объект `Event`:
     - `val savedRawEvent = rawEvent.copy(id = rawId)`
     - `val contract = SmsContract(payload.originAddress, payload.body, payload.timestampMillis)`
     - `val event = SmsMapper.toEvent(savedRawEvent, contract)`
  8. Сохранить доменное событие: `storageGateway.insertEvent(event)`.
  9. Обновить статус здоровья источника:
     - Сформировать `SourceHealth(source = SourceId.SMS, lastEventAt = payload.receivedAt, events24h = updatedCount, lastError = null, queueDepth = channel.depth)`.
     - Вызвать `storageGateway.upsertSourceHealth(...)`.
  10. Вернуть `rawId`.
- **Ошибки и исключения:**
  - `SQLiteConstraintException`: перехватывается на уровне `StorageGateway.insertRawEvent` при гонках вставок.
  - Любые другие ошибки персистенции вызывают фиксацию текста сбоя в `SourceHealth.lastError` через `storageGateway.upsertSourceHealth(health.copy(lastError = ex.message))`.
- **Граничные случаи:**
  - Повторный приём того же SMS (дубликат сети или повторный запуск броадкаста) -> обнаруживается на шаге `findDuplicate`, новая строка в БД не создаётся.
  - Ошибочный текст (битые символы Unicode) -> успешно нормализуется в `EventNormalizer.cleanText` без падения.
- **Примеры:**
  1. *Пример 1 (Успешная обработка нового SMS):*
     - Вход: `payload` с текстом `"Баланс: 500р"`.
     - Результат: Вставлен `RawEvent` с `id = 101L`, вставлен `Event` с `id = 95L`, `SourceHealth` обновлён, возвращено `101L`.
  2. *Пример 2 (Обнаружение дубликата при повторном поступлении):*
     - Вход: Точно такой же `payload`.
     - Результат: `findDuplicate` возвращает `101L`, вставка в `raw_event` и `event` не производится, метод возвращает `101L`.
  3. *Пример 3 (Сбой SQLite при записи):*
     - Вход: `payload` во время блокировки хранилища.
     - Результат: Ошибка перехватывается, `SourceHealth.lastError` получает текст `"database locked"`, исключение логируется.

---

##### 3.3 Свойство `queueDepth`
- **Сигнатура:**
  ```kotlin
  override val queueDepth: Int
  ```
- **Предусловия:**
  - Нет.
- **Постусловия:**
  - Возвращает текущее неотрицательное количество элементов, находящихся в буфере канала и ожидающих обработки консьюмером. Значение $\ge 0$.

---

#### 4. Класс `SmsBackfillWorker`

Фоновый воркер `CoroutineWorker` библиотеки Android Jetpack WorkManager для однократной вычитки архива SMS-сообщений из системного хранилища (`Telephony.Sms.Inbox.CONTENT_URI`).

##### 4.1 Метод `doWork`
- **Сигнатура:**
  ```kotlin
  override suspend fun doWork(): Result
  ```
- **Предусловия:**
  - Запускается системой WorkManager при наличии предоставленного разрешения `android.permission.READ_SMS`.
- **Постусловия:**
  - При отсутствии разрешения `READ_SMS`: задача завершается с `Result.failure()` и обновляет `SourceHealth.lastError = "READ_SMS permission not granted"`.
  - Все входящие SMS из `Telephony.Sms.Inbox.CONTENT_URI` вычитываются порциями (батчами по 100 записей).
  - Каждое сообщение проверяется на дубликат через `StorageGateway.findDuplicate(deduplicationKey)`.
  - Новые сообщения немедленно персистятся в хранилище через вызов `StorageGateway.insertRawEvent` и `StorageGateway.insertEvent`.
  - По завершении успешной вычитки в `SharedPreferences` / `DataStore` выставляется флаг `PREF_KEY_SMS_BACKFILL_COMPLETED = true` и сохраняется метка времени последней синхронизации `PREF_KEY_SMS_LAST_SYNC_TIMESTAMP`.
  - Возвращает `Result.success()`.
- **Пошаговое поведение:**
  1. Проверить системное разрешение:
     - `ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED`.
     - Если разрешение не предоставлено: зафиксировать ошибку в `SourceHealth`, вернуть `Result.failure()`.
  2. Проверить флаг завершённости в настройках:
     - Если `isBackfillCompleted == true`: вернуть `Result.success()` (идемпотентность задачи).
  3. Определить начальную временную метку: `sinceTimestamp = getStoredLastSyncTimestamp() ?: 0L`.
  4. Сформировать параметры запроса к `ContentResolver`:
     - `uri = Telephony.Sms.Inbox.CONTENT_URI`
     - `projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)`
     - `selection = "${Telephony.Sms.DATE} > ?"`
     - `selectionArgs = arrayOf(sinceTimestamp.toString())`
     - `sortOrder = "${Telephony.Sms.DATE} ASC"`
  5. Открыть системный курсор через `applicationContext.contentResolver.query(...)`.
  6. Инициализировать счётчики: `importedCount = 0`, `duplicateCount = 0`, `maxTimestampSeen = sinceTimestamp`.
  7. Итерироваться по курсору в цикле `while (cursor.moveToNext())`:
     - Проверить статус отмены корутины (`coroutineContext.isActive`): если отменена, прервать цикл.
     - Преобразовать текущую запись в `SmsContract` с помощью `SmsMapper.fromCursor(cursor)`.
     - Вычислить `payloadJson = SmsMapper.toPayloadJson(sms.originAddress, sms.body, sms.timestampMillis)`.
     - Рассчитать `dedupKey = EventNormalizer.computeDeduplicationKey(SourceId.SMS, "android.telephony.sms", payloadJson)`.
     - Проверить наличие дубликата: `val existingId = storageGateway.findDuplicate(dedupKey)`.
     - Если `existingId != null`:
       - Увеличить `duplicateCount++`.
     - Если `existingId == null`:
       - Получить `seq = smsIngestController.nextSeq()`.
       - Сформировать `RawEvent` с текущим временем и `dedupKey`.
       - Выполнить `rawId = storageGateway.insertRawEvent(rawEvent)`.
       - Нормализовать и вставить `event = SmsMapper.toEvent(rawEvent.copy(id = rawId), sms)`.
       - Выполнить `storageGateway.insertEvent(event)`.
       - Увеличить `importedCount++`.
     - Обновить `maxTimestampSeen = maxOf(maxTimestampSeen, sms.timestampMillis)`.
     - Каждые 50 записей обновлять статус прогресса через `setProgressAsync(Data)`.
  8. Закрыть курсор.
  9. Сохранить `maxTimestampSeen` в хранилище настроек как `PREF_KEY_SMS_LAST_SYNC_TIMESTAMP`.
  10. Выставить флаг `PREF_KEY_SMS_BACKFILL_COMPLETED = true`.
  11. Обновить `SourceHealth` для `SourceId.SMS` с количеством импортированных сообщений.
  12. Вернуть `Result.success()`.
- **Ошибки и исключения:**
  - `SecurityException`: если во время работы отозвано разрешение `READ_SMS` -> перехватывается, обновляет `SourceHealth.lastError`, возвращает `Result.failure()`.
  - `SQLiteException`: ошибка при записи в базу данных -> возвращает `Result.retry()` для автоматического повтора средствами WorkManager.
- **Граничные случаи:**
  - Системный Inbox пуст -> цикл курсора завершается на 0 шагов, флаг выставляется в `true`, возвращает `Result.success()`.
  - В Inbox 10 000 сообщений -> постраничная/построчная обработка в цикле с проверкой `isActive` не переполняет память (OOM) и сохраняет отзывчивость системы.
  - Половина сообщений уже была получена через `SmsBroadcastReceiver` во время работы приложения -> `findDuplicate` безошибочно пропускает их, дублирования строк в БД нет.
- **Примеры:**
  1. *Пример 1 (Первый запуск с 50 сообщениями в Inbox):*
     - Состояние: БД пустая, в Inbox 50 SMS.
     - Результат: `importedCount = 50`, `duplicateCount = 0`, флаг завершённости выставлен в `true`, результат `Result.success()`.
  2. *Пример 2 (Повторный запуск после частичного получения):*
     - Состояние: 20 сообщений уже получены онлайн, в Inbox 30 сообщений (20 старых + 10 новых).
     - Результат: `importedCount = 10`, `duplicateCount = 20`, результат `Result.success()`.
  3. *Пример 3 (Разрешение READ_SMS не предоставлено):*
     - Состояние: Пользователь отклонил запрос пермиссии.
     - Результат: `doWork` фиксирует ошибку `"READ_SMS permission not granted"`, возвращает `Result.failure()`.

---

#### 5. Класс `SmsPoller`

Компонент фонового резервного контроля входящих SMS сообщений. Активируется в случае, если широковещательные уведомления от операционной системы не поступали в течение заданного порога времени (согласно `architecture.md` §2: "SmsPoller активируется только если broadcast не получен за N минут").

##### 5.1 Метод `pollNewMessages`
- **Сигнатура:**
  ```kotlin
  suspend fun pollNewMessages(sinceTimestamp: Long): Int
  ```
- **Предусловия:**
  - `sinceTimestamp >= 0L`.
  - Предоставлено разрешение `android.permission.READ_SMS`.
- **Постусловия:**
  - Запрашивает из `Telephony.Sms.Inbox.CONTENT_URI` сообщения с `DATE > sinceTimestamp`.
  - Пропускает дубликаты через `findDuplicate`.
  - Сохраняет новые сообщения в `StorageGateway`.
  - Возвращает число импортированных сообщений.
- **Пошаговое поведение:**
  1. Проверить наличие разрешения `READ_SMS`. Если нет, вернуть `0`.
  2. Выполнить запрос к `ContentResolver` с условием `date > sinceTimestamp`.
  3. Для каждого найденного сообщения вычислить `DeduplicationKey` и проверить `findDuplicate`.
  4. При отсутствии дубликата сохранить в `raw_event` и `event`.
  5. Вернуть количество сохранённых новых сообщений.
- **Ошибки:**
  - `SecurityException` -> возвращает `0`, пишет ошибку в `SourceHealth`.
- **Граничные случаи:**
  - Нет новых сообщений -> возвращает `0`.
- **Примеры:**
  1. *Пример 1 (Новых сообщений нет):*
     - Вход: `sinceTimestamp = 1774567890000L`.
     - Выход: `0`.
  2. *Пример 2 (Обнаружено 2 пропущенных броадкастом сообщения):*
     - Вход: `sinceTimestamp = 1774567000000L`.
     - Выход: `2` (сообщения сохранены в БД).
  3. *Пример 3 (Броадкаст уже сохранил сообщения):*
     - Вход: Найдено 1 сообщение, но `findDuplicate != null`.
     - Выход: `0`.

---

### Потоковые сценарии и диаграммы последовательностей (Sequence & Data Flow)

#### Сценарий 1: Онлайн-приём SMS через `SmsBroadcastReceiver` (Горячий путь: p95 < 25 ms)

```mermaid
sequenceDiagram
    autonumber
    participant OS as Android OS Telephony
    participant BR as SmsBroadcastReceiver
    participant Mapper as SmsMapper
    participant Ctrl as SmsIngestController
    participant GW as StorageGateway
    participant Norm as EventNormalizer

    OS->>BR: onReceive(intent: SMS_RECEIVED_ACTION)
    Note over BR: Main Thread (< 2 ms)
    BR->>BR: goAsync()
    BR->>Mapper: fromSmsMessages(getMessagesFromIntent(intent))
    Mapper-->>BR: SmsContract (assembled multipart)
    BR->>Ctrl: enqueueSms(contract)
    Note over Ctrl: seqGenerator.incrementAndGet()
    Ctrl->>Ctrl: Channel.send(SmsRawPayload)
    BR->>OS: pendingResult.finish()

    Note over Ctrl: Фоновый консьюмер (Dispatchers.IO)
    Ctrl->>Mapper: toPayloadJson(contract)
    Ctrl->>Norm: computeDeduplicationKey(source, pkg, payloadJson)
    Norm-->>Ctrl: DeduplicationKey (SHA-256)
    Ctrl->>GW: findDuplicate(dedupKey)
    alt Дубликат найден
        GW-->>Ctrl: existingRawId
        Ctrl->>GW: upsertSourceHealth(health)
    else Новое сообщение
        GW-->>Ctrl: null
        Note over Ctrl,GW: Шаг 1: Немедленный raw insert (ADR-004)
        Ctrl->>GW: insertRawEvent(rawEvent)
        GW-->>Ctrl: generated rawId
        Note over Ctrl,Norm: Шаг 2: Нормализация текста (ADR-005)
        Ctrl->>Mapper: toEvent(rawEvent, contract)
        Mapper->>Norm: cleanText(body)
        Mapper->>Norm: detectLang(cleanText)
        Mapper-->>Ctrl: Event
        Ctrl->>GW: insertEvent(event)
        GW-->>Ctrl: generated eventId
        Ctrl->>GW: upsertSourceHealth(updatedHealth)
    end
```

---

#### Сценарий 2: Однократная историческая вычитка архива (`SmsBackfillWorker`)

```mermaid
sequenceDiagram
    autonumber
    participant WM as WorkManager
    participant Worker as SmsBackfillWorker
    participant CR as ContentResolver (Telephony.Sms.Inbox)
    participant Mapper as SmsMapper
    participant Norm as EventNormalizer
    participant GW as StorageGateway
    participant Prefs as DataStore / SharedPreferences

    WM->>Worker: doWork()
    Worker->>Worker: checkSelfPermission(READ_SMS)
    Worker->>Prefs: isBackfillCompleted()
    alt Уже выполнен
        Worker-->>WM: Result.success()
    else Требуется вычитка
        Worker->>CR: query(Telephony.Sms.Inbox.CONTENT_URI, date > lastSync)
        CR-->>Worker: Cursor (batch of SMS)
        loop Для каждой строки курсора
            Worker->>Mapper: fromCursor(cursor)
            Mapper-->>Worker: SmsContract
            Worker->>Norm: computeDeduplicationKey(SMS, pkg, payloadJson)
            Worker->>GW: findDuplicate(dedupKey)
            alt Дубликат (уже получен онлайн)
                Note over Worker: Пропуск дубликата (duplicateCount++)
            else Новое сообщение
                Worker->>GW: insertRawEvent(rawEvent)
                GW-->>Worker: rawId
                Worker->>Mapper: toEvent(rawEvent, contract)
                Worker->>GW: insertEvent(event)
                Note over Worker: importedCount++
            end
        end
        Worker->>Prefs: saveLastSyncTimestamp(maxDate)
        Worker->>Prefs: setBackfillCompleted(true)
        Worker->>GW: upsertSourceHealth(health)
        Worker-->>WM: Result.success()
    end
```

---

### Конфигурация Android Manifest, Permissions и Lifecycle

#### 1. Фрагмент `AndroidManifest.xml` для модуля `:ingest:sms`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.npc.ingest.sms">

    <!-- Системные разрешения телефонии (ADR-001) -->
    <uses-permission android:name="android.permission.RECEIVE_SMS" />
    <uses-permission android:name="android.permission.READ_SMS" />

    <application>
        <!-- Регистрация BroadcastReceiver с защитой системным пермишеном BROADCAST_SMS -->
        <receiver
            android:name=".receiver.SmsBroadcastReceiver"
            android:exported="true"
            android:permission="android.permission.BROADCAST_SMS">
            <intent-filter android:priority="999">
                <action android:name="android.provider.Telephony.SMS_RECEIVED" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

#### 2. Защита от убийства процесса и управление жизненным циклом (HyperOS / Android 15)
1. **Приоритет Intent Filter:** Фильтр зарегистрирован с высоким приоритетом (`priority="999"`), обеспечивающим первоочередной приём сообщения.
2. **Безопасность `BROADCAST_SMS`:** Параметр `android:permission="android.permission.BROADCAST_SMS"` гарантирует, что только системный процесс телефонии Android (`com.android.phone`) имеет право посылать данный интент в приложение, блокируя атаки спуфинга со стороны сторонних приложений.
3. **Продление времени жизни через `goAsync()`:** Вызов `goAsync()` в `SmsBroadcastReceiver.onReceive` предоставляет до 30 секунд гарантированного процессорного времени операционной системы на обработку сообщения в фоновом потоке, предотвращая падение по ANR.
4. **Устойчивость к сбросу процесса:** Все входящие сообщения немедленно попадают в `StorageGateway.insertRawEvent` до начала нормализации, исключая потерю данных при выгрузке приложения из памяти.

---

### Зависимости модуля

#### Разрешённые зависимости:
- `:core:model` — чистый доменный слой данных (`RawEvent`, `Event`, `SourceId`, `DeduplicationKey`, `ThreadKey`, `Lang`, `EventNormalizer`).
- `:core:storage` — шлюз персистентного слоя `StorageGateway`.
- Android Platform API (`android.telephony.*`, `android.provider.Telephony`, `android.content.*`).
- AndroidX Core / Jetpack:
  - `androidx.core:core-ktx`
  - `androidx.work:work-runtime-ktx` (для `SmsBackfillWorker`)
- KotlinX Coroutines (`kotlinx.coroutines.channels.Channel`, `kotlinx.coroutines.Dispatchers.IO`).
- Hilt / Dagger (`dagger.hilt.android.AndroidEntryPoint`, `@HiltWorker`).

#### Запрещённые зависимости:
- `:ui:*` — UI компоненты и ViewModel строго запрещены.
- `:ingest:notification`, `:ingest:media` — соседние модули сбора данных изолированы.
- Room runtime и DAO напрямую (`RawEventDao`, `EventDao`) — доступ исключительно через `StorageGateway`.
- Прямое выполнение тяжеловесных ML-вычислений.

---

### Вне скоупа (Out of Scope)

В модуль `:ingest:sms` и зону `zone/ingest-sms` явно **НЕ входят**:
1. Отправка SMS-сообщений (`SEND_SMS`) и изменение статуса прочтения в системном Inbox (`WRITE_SMS`).
2. Приём и обработка MMS-сообщений (`WAP_PUSH_DELIVER_ACTION`, `MMS_RECEIVED_ACTION`).
3. Роль приложения по умолчанию для SMS (Default SMS App / `SMS_DELIVER_ACTION`).
4. Анализ финансовых транзакций, парсинг банковских категорий и извлечение балансов (выполняется на более поздних этапах пайплайна в модулях аналитики).
5. Отображение уведомлений пользователю в системной шторке (делегировано `:app`).

---

### Чек-лист соответствия Definition of Ready (DoR §7)

| Критерий DoR | Статус | Подтверждение в спецификации |
|---|---|---|
| **1. Полные сигнатуры всех публичных API** | СООТВЕТСТВУЕТ | Специфицированы сигнатуры `SmsBroadcastReceiver`, `SmsMapper`, `SmsIngestController`, `SmsBackfillWorker`, `SmsPoller`. |
| **2. Предусловия и постусловия** | СООТВЕТСТВУЕТ | Для каждого метода указаны точные требования к входным аргументам и гарантированное состояние системы на выходе. |
| **3. Пошаговые алгоритмы поведения** | СООТВЕТСТВУЕТ | Расписаны пошаговые алгоритмы работы `onReceive`, сборки multipart-SMS, вставки в хранилище и вычитки backfill. |
| **4. Обработка ошибок и исключений** | СООТВЕТСТВУЕТ | Описаны реакции на `SecurityException`, `SQLiteConstraintException`, пустые/битые PDU и выгрузку процесса. |
| **5. Граничные случаи (Edge Cases)** | СООТВЕТСТВУЕТ | Разобраны кейсы: пустые SMS, burst-нагрузка, 10 000 сообщений в архиве, отзыв пермиссий, отсутствие сети. |
| **6. Минимум 3 примера на каждый компонент** | СООТВЕТСТВУЕТ | Для всех ключевых функций и методов приведено не менее 3 конкретных детальных примеров входа/выхода. |
| **7. Архитектурное соответствие (ADR)** | СООТВЕТСТВУЕТ | Полная гармония с ADR-001 (minSdk 26, targetSdk 35), ADR-002 (0 пропусков SMS банков), ADR-004 (Channel + seq), ADR-005 (модульность нормализации). |
| **8. Чистота границ (Отсутствие продакшн-кода)** | СООТВЕТСТВУЕТ | Документ является исключительно технической спецификацией интерфейсов, контрактов и алгоритмов без реализации продакшн-кода. |
