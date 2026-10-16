# TASK-CON-SEC-01: Политика сопряжения, криптографический proof и TrustStore (Lirix Connect)

## 1. Контекст и Цель
Реализовать криптографический фундамент безопасного сопряжения устройств в модуле `:shared` по стандарту **SPEC-CONNECT-01 (Раздел 4.2)**:
- Одноразовый секрет сопряжения (`pairingSecret`, 16 байт) с TTL (120 с).
- Подтверждение знания секрета без передачи по сети: `proof = HMAC-SHA256(secret, nonce ‖ clientEndpointId ‖ serverEndpointId)`.
- Ограничение попыток (rate limiting: максимум 3 неверные попытки).
- Расчёт 6-значного проверочного кода хоста (`verifyCode`) для сверки человеком.
- Хранилище доверенных устройств (`TrustedDeviceStore`) с закреплением публичных ключей Ed25519 (`endpoint_id` pinning).
- Механизм отзыва доверия («Забыть устройство»).

---

## 2. Затрагиваемые файлы и целевые пакеты
Все компоненты реализуются в `shared/src/commonMain/kotlin/com/lirix/app/connect/`:
1. `shared/src/commonMain/kotlin/com/lirix/app/connect/model/Bytes.kt` — сериализуемая обёртка над `ByteArray` с консистентными `equals` и `hashCode`.
2. `shared/src/commonMain/kotlin/com/lirix/app/connect/model/PairingPayload.kt` — модель данных полезной нагрузки QR-кода.
3. `shared/src/commonMain/kotlin/com/lirix/app/connect/security/PairingSecurityManager.kt` — логика генерации секрета, расчёта/проверки HMAC-proof, тайм-аутов TTL и счётчика попыток.
4. `shared/src/commonMain/kotlin/com/lirix/app/connect/security/TrustedDeviceStore.kt` — реестр доверенных пинов (`TrustedDevice(endpointId, name, pairedAt, lastSeen)`).
5. `shared/src/commonTest/kotlin/com/lirix/app/connect/PairingSecurityTest.kt` — TDD Unit-тесты всех сценариев безопасности.

---

## 3. Требования к реализации (DoD)

### 3.1. `Bytes`
```kotlin
@Serializable
data class Bytes(val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Bytes) return false
        return data.contentEquals(other.data)
    }
    override fun hashCode(): Int = data.contentHashCode()
}
fun ByteArray.asBytes(): Bytes = Bytes(this)
```

### 3.2. `PairingPayload`
```kotlin
@Serializable
data class PairingPayload(
    val version: Int = 1,
    val endpointId: Bytes,          // 32 байта Ed25519
    val pairingSecret: Bytes,       // 16 байт
    val deviceName: String,
    val addrs: List<String>,        // "192.168.1.50:4433"
    val relayUrl: String?,
    val expiresAtEpochSec: Long     // currentTimeSec + 120
)
```

### 3.3. `PairingSecurityManager`
1. `generatePairingPayload(...)`:
   - Генерирует криптографически стойкие 16 байт секрета (`pairingSecret`).
   - Устанавливает `expiresAtEpochSec = now + ttlSec` (по умолчанию 120 с, максимум 300 с).
   - Сбрасывает счётчик неверных попыток (`failedAttempts = 0`).
2. `createProof(secret: Bytes, nonce: Bytes, clientEndpointId: Bytes, serverEndpointId: Bytes): Bytes`:
   - Вычисляет `HMAC-SHA256(key = secret, data = nonce.data + clientEndpointId.data + serverEndpointId.data)`.
3. `verifyPairRequest(request: PairRequest, expectedSecret: Bytes, serverEndpointId: Bytes, clientEndpointId: Bytes, currentEpochSec: Long): VerificationResult`:
   - Если `currentEpochSec > payload.expiresAtEpochSec`: возвращает `EXPIRED`.
   - Если `failedAttempts >= 3`: возвращает `TOO_MANY_ATTEMPTS`, аннулирует активный секрет.
   - Проверяет равенство HMAC-proof в постоянном времени (`constantTimeEquals`).
   - Если proof неверен: инкрементирует `failedAttempts` и возвращает `INVALID_PROOF`.
   - Если proof верен: аннулирует секрет (одноразовость) и возвращает `SUCCESS(verifyCode)`.
4. `generateVerifyCode(clientEndpointId: Bytes, serverEndpointId: Bytes): String`:
   - Первые 6 десятичных цифр от `SHA-256(clientEndpointId.data + serverEndpointId.data)`.

### 3.4. `TrustedDeviceStore`
```kotlin
data class TrustedDevice(
    val endpointId: Bytes,
    val deviceName: String,
    val deviceType: String,
    val pairedAtEpochSec: Long,
    val lastSeenEpochSec: Long
)
```
- Методы: `addTrusted(device)`, `isTrusted(endpointId: Bytes): Boolean`, `getTrusted(endpointId): TrustedDevice?`, `removeTrusted(endpointId: Bytes): Boolean`, `getAll(): List<TrustedDevice>`.

---

## 4. Контрольные Тесты (TDD)
1. Успешная генерация и совпадение HMAC-proof на клиенте и сервере.
2. Отклонение просроченного QR (TTL + 1 сек -> `EXPIRED`).
3. Блокировка после 3 неверных попыток (`TOO_MANY_ATTEMPTS`).
4. Одноразовость: повторный запрос с тем же секретом после успешного сопряжения отклоняется.
5. Закрепление `endpoint_id` в `TrustedDeviceStore` и успешный отзыв («Забыть устройство»).
