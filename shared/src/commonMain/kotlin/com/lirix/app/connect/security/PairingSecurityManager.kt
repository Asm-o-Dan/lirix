package com.lirix.app.connect.security

import com.lirix.app.connect.model.Bytes
import com.lirix.app.connect.model.PairRequest
import com.lirix.app.connect.model.PairingPayload
import com.lirix.app.connect.model.asBytes
import kotlin.random.Random

/**
 * Результат верификации входящего запроса на сопряжение.
 */
sealed interface VerificationResult {
    val isSuccess: Boolean get() = this is Success

    data class Success(val verifyCode: String) : VerificationResult
    data object Expired : VerificationResult
    data object TooManyAttempts : VerificationResult
    data object InvalidProof : VerificationResult
}

/**
 * Сравнение двух байтовых массивов в постоянном времени для защиты от timing-атак.
 */
fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var diff = 0
    for (i in a.indices) {
        diff = diff or (a[i].toInt() xor b[i].toInt())
    }
    return diff == 0
}

fun constantTimeEquals(a: Bytes, b: Bytes): Boolean = constantTimeEquals(a.data, b.data)

/**
 * Менеджер безопасности фазы сопряжения Lirix Connect (SPEC-CONNECT-01 Раздел 4.2).
 */
class PairingSecurityManager(
    private val random: Random = Random.Default
) {
    companion object {
        const val DEFAULT_TTL_SECONDS = 120L
        const val MAX_TTL_SECONDS = 300L
        const val SECRET_SIZE_BYTES = 16
        const val MAX_FAILED_ATTEMPTS = 3
    }

    var activePayload: PairingPayload? = null
        private set

    var failedAttempts: Int = 0
        private set

    /**
     * Генерирует 16-байтный одноразовый секрет и формирует PairingPayload с заданным TTL.
     */
    fun generatePairingPayload(
        endpointId: Bytes,
        deviceName: String,
        currentEpochSec: Long,
        addrs: List<String> = emptyList(),
        relayUrl: String? = null,
        ttlSec: Long = DEFAULT_TTL_SECONDS
    ): PairingPayload {
        val clampedTtl = ttlSec.coerceIn(1, MAX_TTL_SECONDS)
        val secret = random.nextBytes(SECRET_SIZE_BYTES).asBytes()
        val payload = PairingPayload(
            version = 1,
            endpointId = endpointId,
            pairingSecret = secret,
            deviceName = deviceName,
            addrs = addrs,
            relayUrl = relayUrl,
            expiresAtEpochSec = currentEpochSec + clampedTtl
        )
        activePayload = payload
        failedAttempts = 0
        return payload
    }

    /**
     * Аннулирует активный секрет (одноразовость).
     */
    fun invalidateSecret() {
        activePayload = null
    }

    fun resetFailedAttempts() {
        failedAttempts = 0
    }

    /**
     * Вычисляет HMAC-SHA256(key = secret, data = nonce ‖ clientEndpointId ‖ serverEndpointId).
     */
    fun createProof(
        secret: Bytes,
        nonce: Bytes,
        clientEndpointId: Bytes,
        serverEndpointId: Bytes
    ): Bytes {
        val data = nonce.data + clientEndpointId.data + serverEndpointId.data
        return HmacSha256.compute(secret.data, data).asBytes()
    }

    /**
     * Расчёт 6-значного проверочного кода хоста (verifyCode) для визуальной сверки человеком:
     * Первые 6 десятичных цифр от SHA-256(clientEndpointId.data + serverEndpointId.data).
     */
    fun generateVerifyCode(
        clientEndpointId: Bytes,
        serverEndpointId: Bytes
    ): String {
        val data = clientEndpointId.data + serverEndpointId.data
        val hash = Sha256.digest(data)
        val hexChars = "0123456789abcdef"
        val digits = StringBuilder(6)
        for (b in hash) {
            val octet = b.toInt() and 0xFF
            val h1 = hexChars[octet ushr 4]
            if (h1 in '0'..'9') {
                digits.append(h1)
                if (digits.length == 6) break
            }
            val h2 = hexChars[octet and 0x0F]
            if (h2 in '0'..'9') {
                digits.append(h2)
                if (digits.length == 6) break
            }
        }
        while (digits.length < 6) {
            digits.append('0')
        }
        return digits.toString()
    }

    /**
     * Верифицирует входящий PairRequest.
     */
    fun verifyPairRequest(
        request: PairRequest,
        expectedSecret: Bytes,
        serverEndpointId: Bytes,
        clientEndpointId: Bytes,
        currentEpochSec: Long
    ): VerificationResult {
        // 1. Лимит неверных попыток (rate limiting)
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            invalidateSecret()
            return VerificationResult.TooManyAttempts
        }

        // 2. Одноразовость: проверка наличия активного секрета
        val current = activePayload
        if (current == null || current.pairingSecret != expectedSecret) {
            return VerificationResult.Expired
        }

        // 3. Проверка срока жизни QR (TTL)
        if (currentEpochSec > current.expiresAtEpochSec) {
            invalidateSecret()
            return VerificationResult.Expired
        }

        // 4. Проверка HMAC-proof в постоянном времени
        val expectedProof = createProof(
            secret = expectedSecret,
            nonce = request.nonce,
            clientEndpointId = clientEndpointId,
            serverEndpointId = serverEndpointId
        )

        val isValid = constantTimeEquals(expectedProof.data, request.proof.data)

        if (!isValid) {
            failedAttempts++
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                invalidateSecret()
            }
            return VerificationResult.InvalidProof
        }

        // 5. Успех: аннулирование одноразового секрета и генерация verifyCode
        invalidateSecret()
        val verifyCode = generateVerifyCode(clientEndpointId, serverEndpointId)
        return VerificationResult.Success(verifyCode)
    }

    /**
     * Перегрузка для верификации по текущему активному секрету хоста.
     */
    fun verifyPairRequest(
        request: PairRequest,
        clientEndpointId: Bytes,
        currentEpochSec: Long
    ): VerificationResult {
        val current = activePayload ?: return if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            VerificationResult.TooManyAttempts
        } else {
            VerificationResult.Expired
        }
        return verifyPairRequest(
            request = request,
            expectedSecret = current.pairingSecret,
            serverEndpointId = current.endpointId,
            clientEndpointId = clientEndpointId,
            currentEpochSec = currentEpochSec
        )
    }
}

/**
 * Чистая реализация SHA-256 (FIPS 180-4) без платформозависимых библиотек.
 */
internal object Sha256 {
    private val K = intArrayOf(
        0x428a2f98.toInt(), 0x71374491.toInt(), 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
        0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc,
        0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
        0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
        0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(),
        0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt()
    )

    fun digest(data: ByteArray): ByteArray {
        var h0 = 0x6a09e667
        var h1 = 0xbb67ae85.toInt()
        var h2 = 0x3c6ef372
        var h3 = 0xa54ff53a.toInt()
        var h4 = 0x510e527f
        var h5 = 0x9b05688c.toInt()
        var h6 = 0x1f83d9ab
        var h7 = 0x5be0cd19

        val messageLen = data.size.toLong()
        val bitLen = messageLen * 8

        val padLen = ((56 - (data.size + 1) % 64) % 64 + 64) % 64
        val totalLen = data.size + 1 + padLen + 8
        val padded = ByteArray(totalLen)
        data.copyInto(padded)
        padded[data.size] = 0x80.toByte()

        for (i in 0 until 8) {
            padded[totalLen - 1 - i] = ((bitLen ushr (i * 8)) and 0xFF).toByte()
        }

        val w = IntArray(64)
        var offset = 0
        while (offset < totalLen) {
            for (i in 0 until 16) {
                val idx = offset + i * 4
                w[i] = ((padded[idx].toInt() and 0xFF) shl 24) or
                        ((padded[idx + 1].toInt() and 0xFF) shl 16) or
                        ((padded[idx + 2].toInt() and 0xFF) shl 8) or
                        (padded[idx + 3].toInt() and 0xFF)
            }
            for (i in 16 until 64) {
                val s0 = (w[i - 15] ushr 7 or (w[i - 15] shl 25)) xor
                        (w[i - 15] ushr 18 or (w[i - 15] shl 14)) xor
                        (w[i - 15] ushr 3)
                val s1 = (w[i - 2] ushr 17 or (w[i - 2] shl 15)) xor
                        (w[i - 2] ushr 19 or (w[i - 2] shl 13)) xor
                        (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }

            var a = h0
            var b = h1
            var c = h2
            var d = h3
            var e = h4
            var f = h5
            var g = h6
            var h = h7

            for (i in 0 until 64) {
                val s1 = (e ushr 6 or (e shl 26)) xor (e ushr 11 or (e shl 21)) xor (e ushr 25 or (e shl 7))
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = h + s1 + ch + K[i] + w[i]
                val s0 = (a ushr 2 or (a shl 30)) xor (a ushr 13 or (a shl 19)) xor (a ushr 22 or (a shl 10))
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + maj

                h = g
                g = f
                f = e
                e = d + temp1
                d = c
                c = b
                b = a
                a = temp1 + temp2
            }

            h0 += a
            h1 += b
            h2 += c
            h3 += d
            h4 += e
            h5 += f
            h6 += g
            h7 += h

            offset += 64
        }

        val out = ByteArray(32)
        writeInt(out, 0, h0)
        writeInt(out, 4, h1)
        writeInt(out, 8, h2)
        writeInt(out, 12, h3)
        writeInt(out, 16, h4)
        writeInt(out, 20, h5)
        writeInt(out, 24, h6)
        writeInt(out, 28, h7)
        return out
    }

    private fun writeInt(out: ByteArray, offset: Int, v: Int) {
        out[offset] = (v ushr 24).toByte()
        out[offset + 1] = (v ushr 16).toByte()
        out[offset + 2] = (v ushr 8).toByte()
        out[offset + 3] = v.toByte()
    }
}

/**
 * Чистая реализация HMAC-SHA256 (RFC 2104).
 */
internal object HmacSha256 {
    private const val BLOCK_SIZE = 64
    private const val IPAD: Byte = 0x36
    private const val OPAD: Byte = 0x5C

    fun compute(key: ByteArray, message: ByteArray): ByteArray {
        val preparedKey = if (key.size > BLOCK_SIZE) {
            Sha256.digest(key)
        } else {
            key
        }

        val kIpad = ByteArray(BLOCK_SIZE) { IPAD }
        val kOpad = ByteArray(BLOCK_SIZE) { OPAD }

        for (i in preparedKey.indices) {
            kIpad[i] = (kIpad[i].toInt() xor preparedKey[i].toInt()).toByte()
            kOpad[i] = (kOpad[i].toInt() xor preparedKey[i].toInt()).toByte()
        }

        val innerInput = ByteArray(BLOCK_SIZE + message.size)
        kIpad.copyInto(innerInput, 0)
        message.copyInto(innerInput, BLOCK_SIZE)
        val innerHash = Sha256.digest(innerInput)

        val outerInput = ByteArray(BLOCK_SIZE + innerHash.size)
        kOpad.copyInto(outerInput, 0)
        innerHash.copyInto(outerInput, BLOCK_SIZE)
        return Sha256.digest(outerInput)
    }
}
