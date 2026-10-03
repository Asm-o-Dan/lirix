## Задача STORAGE-005: реализовать SqlCipherSupportFactoryProvider

**Файл:** `core/storage/src/main/kotlin/com/example/npc/core/storage/SqlCipherSupportFactoryProvider.kt` (создать)  
**Спека:** `.sdd/specs/core-storage/overview.md#безопасность-и-шифрование-sqlciphersupportfactoryprovider` (v1)  
**Зависит от:** `STORAGE-001`  

**Сигнатуры и поведение:**

```kotlin
package com.example.npc.core.storage

import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SqlCipherSupportFactoryProvider {
    const val KEY_ALIAS: String = "npc_sqlcipher_master_key"
    const val KEY_SIZE_BYTES: Int = 32
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12

    fun getOrCreatePassphrase(
        keyAlias: String = KEY_ALIAS,
        keyStorageFile: File
    ): ByteArray

    fun createOpenHelperFactory(passphrase: ByteArray): SupportOpenHelperFactory

    fun wipePassphrase(passphrase: ByteArray)
}
```

**Поведение методов:**
1. `getOrCreatePassphrase(keyAlias, keyStorageFile)`:
   - Если мастер-ключ отсутствует в `AndroidKeyStore` (или файл не существует):
     - Генерирует AES-256 ключ в `AndroidKeyStore` (цель ENCRYPT/DECRYPT, режим GCM, без padding).
     - Генерирует 32 случайных байта через `SecureRandom()`.
     - Шифрует AES/GCM/NoPadding, сохраняет `[iv (12 bytes) + ciphertext + tag]` в `keyStorageFile`.
     - Возвращает исходный 32-байтный массив.
   - Если ключ и файл существуют:
     - Считывает файл, извлекает IV и зашифрованный блоб.
     - Расшифровывает мастер-ключом из `AndroidKeyStore`.
     - Проверяет `rawDbKey.size == 32`, иначе бросает `IllegalStateException`.
     - Возвращает 32-байтный массив.
2. `createOpenHelperFactory(passphrase)`:
   - Проверяет `passphrase.size == 32`. Если нет — `throw IllegalArgumentException("Passphrase must be exactly 32 bytes")`.
   - Вызывает `System.loadLibrary("sqlcipher")`.
   - Возвращает `SupportOpenHelperFactory(passphrase)`.
3. `wipePassphrase(passphrase)`:
   - Вызывает `Arrays.fill(passphrase, 0.toByte())`.

**Запрещено:**
- Использовать `pickFirst` для OpenSSL без проверки ABI.
- Оставлять не занулённый массив секретной фразы после передачи в фабрику.
- Использовать небезопасные режимы шифрования (ECB, CBC без HMAC).

**Критерий приёмки:**
- Вызов `createOpenHelperFactory` с ключом не 32 байта выбрасывает `IllegalArgumentException`.
- Метод `wipePassphrase` зануляет все элементы переданного массива.
