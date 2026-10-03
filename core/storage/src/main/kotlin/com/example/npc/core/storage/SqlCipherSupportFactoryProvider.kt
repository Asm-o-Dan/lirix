package com.example.npc.core.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.io.FileOutputStream
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
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12

    fun getOrCreatePassphrase(
        keyAlias: String = KEY_ALIAS,
        keyStorageFile: File
    ): ByteArray {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        return if (keyStore.containsAlias(keyAlias) && keyStorageFile.exists()) {
            val fileBytes = keyStorageFile.readBytes()
            if (fileBytes.size < IV_LENGTH_BYTES) {
                throw IllegalStateException("Key storage file is corrupted or too small")
            }
            val iv = fileBytes.copyOfRange(0, IV_LENGTH_BYTES)
            val ciphertext = fileBytes.copyOfRange(IV_LENGTH_BYTES, fileBytes.size)

            val secretKey = keyStore.getKey(keyAlias, null) as SecretKey
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            }
            val rawDbKey = cipher.doFinal(ciphertext)
            if (rawDbKey.size != KEY_SIZE_BYTES) {
                throw IllegalStateException("Decrypted database key has invalid size: ${rawDbKey.size}")
            }
            rawDbKey
        } else {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            val secretKey = keyGenerator.generateKey()

            val rawDbKey = ByteArray(KEY_SIZE_BYTES).apply {
                SecureRandom().nextBytes(this)
            }

            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, secretKey)
            }
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(rawDbKey)

            keyStorageFile.parentFile?.mkdirs()
            val tempFile = File.createTempFile("db_key", ".tmp", keyStorageFile.parentFile)
            FileOutputStream(tempFile).use { out ->
                out.write(iv)
                out.write(ciphertext)
                out.flush()
            }
            if (!tempFile.renameTo(keyStorageFile)) {
                tempFile.copyTo(keyStorageFile, overwrite = true)
                tempFile.delete()
            }

            rawDbKey
        }
    }

    fun createOpenHelperFactory(passphrase: ByteArray): SupportOpenHelperFactory {
        if (passphrase.size != KEY_SIZE_BYTES) {
            throw IllegalArgumentException("Passphrase must be exactly $KEY_SIZE_BYTES bytes")
        }
        System.loadLibrary("sqlcipher")
        return SupportOpenHelperFactory(passphrase)
    }

    fun wipePassphrase(passphrase: ByteArray) {
        Arrays.fill(passphrase, 0.toByte())
    }
}
