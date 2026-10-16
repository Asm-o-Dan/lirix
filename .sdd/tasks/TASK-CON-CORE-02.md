# TASK-CON-CORE-02: Iroh-транспорт, ALPN lirix/connect/1, кодек PairingPayload, discovery и реконнект

## 1. Контекст и Цель
Реализовать транспортный уровень Lirix Connect в модуле `:shared` по стандарту **SPEC-CONNECT-01 (Разделы 4.1, 4.2)**:
- Кодек строки сопряжения `PairingPayloadCodec`: преобразование `PairingPayload` в формат URI `lirix://pair?d=<base64url>` и обратно с валидацией версий и контрольной суммой.
- Транспортный интерфейс и абстракция `ConnectTransport`: управление соединением Iroh QUIC, ALPN `lirix/connect/1`, выделение bi-stream для управляющих сообщений и канала датаграмм для Audio Cast.
- Машина состояний соединения (`ConnectState`) и стратегия автоматического переподключения (reconnect с экспоненциальным backoff, целевое время восстановления после смены сети $\le 10$ с).
- Модель обнаружения пиров (discovery: прямой адрес в LAN vs iroh-relay).

---

## 2. Затрагиваемые файлы и целевые пакеты
Все компоненты реализуются в `shared/src/commonMain/kotlin/com/lirix/app/connect/`:
1. `shared/src/commonMain/kotlin/com/lirix/app/connect/transport/PairingPayloadCodec.kt` — кодирование/декодирование URI `lirix://pair?d=...`.
2. `shared/src/commonMain/kotlin/com/lirix/app/connect/transport/ConnectTransport.kt` — интерфейс сетевого транспорта QUIC bi-stream + datagrams.
3. `shared/src/commonMain/kotlin/com/lirix/app/connect/transport/ConnectState.kt` — состояния соединения (`DISCONNECTED`, `CONNECTING`, `CONNECTED`, `RECONNECTING`, `FAILED`).
4. `shared/src/commonMain/kotlin/com/lirix/app/connect/transport/ReconnectPolicy.kt` — тайминги, попытки и экспоненциальный backoff.
5. `shared/src/commonTest/kotlin/com/lirix/app/connect/PairingPayloadCodecTest.kt` — TDD-тесты кодека и URI.
6. `shared/src/commonTest/kotlin/com/lirix/app/connect/ReconnectPolicyTest.kt` — TDD-тесты логики переподключения.

---

## 3. Требования к реализации (DoD)

### 3.1. `PairingPayloadCodec`
- Префикс схемы: `lirix://pair?d=`.
- Кодирование: сериализация `PairingPayload` в CBOR / JSON, сжатие/упаковка и кодирование в `Base64Url` (без padding `=`).
- Декодирование: парсинг URI, декодирование `Base64Url`, десериализация `PairingPayload`.
- Валидация:
  - Проверка схемы URI (`lirix://pair?d=`).
  - Проверка версии `payload.version == 1`.
  - Проверка длины `endpointId` (ровно 32 байта) и `pairingSecret` (ровно 16 байт).
  - Проверка истечения TTL: если `payload.expiresAtEpochSec < currentEpochSec`, выброс `PairingExpiredException`.

### 3.2. `ConnectTransport` (Интерфейс)
```kotlin
interface ConnectTransport {
    val alpn: String get() = "lirix/connect/1"
    val state: StateFlow<ConnectState>
    
    suspend fun connect(target: PairingPayload): Result<Unit>
    suspend fun connectPinned(endpointId: Bytes, knownAddrs: List<String>, relayUrl: String?): Result<Unit>
    suspend fun disconnect(reason: String = "Normal")
    
    // Двунаправленный стрим управления (Control Bi-stream)
    suspend fun sendControlMessage(data: ByteArray)
    fun incomingControlMessages(): Flow<ByteArray>
    
    // Канал датаграмм для Audio Cast
    suspend fun sendDatagram(datagram: ByteArray)
    fun incomingDatagrams(): Flow<ByteArray>
}
```

### 3.3. `ReconnectPolicy`
- Стратегия: initial delay 500 мс, multiplier 1.5, max delay 5000 мс, max attempts 5 (общий интервал попыток $\le 10$ с).
- Событие смены сети (`NetworkChanged`): немедленный сброс таймера задержки и форсированная попытка прямого соединения / через relay.

---

## 4. Контрольные Тесты (TDD)
1. Кодирование `PairingPayload` в URI и точное восстановление всех полей (round-trip test).
2. Обработка некорректных URI (битый base64, пустая строка, чужая схема `http://` -> `IllegalArgumentException`).
3. Детектирование просроченного payload по `expiresAtEpochSec`.
4. Тест машины состояний `ReconnectPolicy`: корректный расчет задержек и ограничение 10 с.
5. Успешный прогон тестов через `./gradlew :shared:desktopTest`.
