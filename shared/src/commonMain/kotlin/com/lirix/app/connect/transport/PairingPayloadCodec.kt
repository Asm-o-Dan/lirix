package com.lirix.app.connect.transport

import com.lirix.app.connect.model.PairingPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Исключение, выбрасываемое при попытке декодировать просроченный QR-payload сопряжения.
 */
class PairingExpiredException(
    val expiresAtEpochSec: Long,
    val currentEpochSec: Long,
    message: String = "Pairing payload expired at $expiresAtEpochSec (current: $currentEpochSec)"
) : IllegalArgumentException(message)

/**
 * Кодек строки сопряжения Lirix Connect (SPEC-CONNECT-01 §4.1, §4.2, TASK-CON-CORE-02).
 * Преобразует PairingPayload в формат URI 'lirix://pair?d=<base64url>' и обратно
 * с валидацией схемы, версии, размеров криптографических ключей и TTL.
 */
object PairingPayloadCodec {

    const val URI_PREFIX = "lirix://pair?d="
    const val EXPECTED_VERSION = 1
    const val ENDPOINT_ID_SIZE = 32
    const val PAIRING_SECRET_SIZE = 16

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @OptIn(ExperimentalEncodingApi::class)
    private val base64UrlEncoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    @OptIn(ExperimentalEncodingApi::class)
    private val base64UrlDecoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    /**
     * Кодирует PairingPayload в URI формат 'lirix://pair?d=<base64url>'.
     *
     * @throws IllegalArgumentException если поля payload не соответствуют спецификации.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun encode(payload: PairingPayload): String {
        validatePayloadFields(payload)

        val jsonString = json.encodeToString(payload)
        val jsonBytes = jsonString.encodeToByteArray()
        val base64Url = base64UrlEncoder.encode(jsonBytes).trimEnd('=')

        return "$URI_PREFIX$base64Url"
    }

    /**
     * Декодирует URI 'lirix://pair?d=<base64url>' обратно в PairingPayload.
     *
     * @param uri Строка URI со схемой lirix://pair?d=...
     * @param currentEpochSec Текущее время в секундах Epoch для валидации TTL (null для пропуска проверки TTL).
     * @throws IllegalArgumentException если схема неверна, payload пуст или поврежден,
     *                                  версия не равна 1 или размеры ключей некорректны.
     * @throws PairingExpiredException если payload.expiresAtEpochSec < currentEpochSec.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun decode(uri: String, currentEpochSec: Long? = null): PairingPayload {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Pairing URI cannot be empty")
        }

        if (!trimmed.startsWith(URI_PREFIX)) {
            throw IllegalArgumentException("Invalid pairing URI scheme: expected prefix '$URI_PREFIX' but got '$trimmed'")
        }

        val rawData = trimmed.substring(URI_PREFIX.length).substringBefore('&').trim()
        if (rawData.isEmpty()) {
            throw IllegalArgumentException("Missing or empty payload parameter 'd' in URI: $trimmed")
        }

        val jsonBytes = try {
            base64UrlDecoder.decode(rawData)
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed base64url payload in URI: ${e.message}", e)
        }

        val jsonString = jsonBytes.decodeToString()
        val payload = try {
            json.decodeFromString<PairingPayload>(jsonString)
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed JSON payload in URI: ${e.message}", e)
        }

        validatePayloadFields(payload)

        if (currentEpochSec != null && payload.expiresAtEpochSec < currentEpochSec) {
            throw PairingExpiredException(payload.expiresAtEpochSec, currentEpochSec)
        }

        return payload
    }

    /**
     * Проверяет обязательные инварианты структуры PairingPayload (версия, размеры Ed25519 и секрета).
     */
    fun validatePayloadFields(payload: PairingPayload) {
        if (payload.version != EXPECTED_VERSION) {
            throw IllegalArgumentException("Unsupported PairingPayload version: ${payload.version}, expected $EXPECTED_VERSION")
        }
        if (payload.endpointId.size != ENDPOINT_ID_SIZE) {
            throw IllegalArgumentException("Invalid endpointId size: expected $ENDPOINT_ID_SIZE bytes, got ${payload.endpointId.size}")
        }
        if (payload.pairingSecret.size != PAIRING_SECRET_SIZE) {
            throw IllegalArgumentException("Invalid pairingSecret size: expected $PAIRING_SECRET_SIZE bytes, got ${payload.pairingSecret.size}")
        }
    }
}
