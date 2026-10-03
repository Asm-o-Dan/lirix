package com.example.npc.core.storage

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SqlCipherSupportFactoryProviderTest {

    @Test
    fun `createOpenHelperFactory throws IllegalArgumentException when passphrase is not 32 bytes`() {
        val shortKey = ByteArray(16) { 1 }
        val emptyKey = ByteArray(0)
        val longKey = ByteArray(64) { 1 }

        assertThrows<IllegalArgumentException> {
            SqlCipherSupportFactoryProvider.createOpenHelperFactory(shortKey)
        }

        assertThrows<IllegalArgumentException> {
            SqlCipherSupportFactoryProvider.createOpenHelperFactory(emptyKey)
        }

        assertThrows<IllegalArgumentException> {
            SqlCipherSupportFactoryProvider.createOpenHelperFactory(longKey)
        }
    }

    @Test
    fun `wipePassphrase fills entire byte array with zeroes`() {
        val secret = ByteArray(32) { (it + 1).toByte() }
        secret[0] shouldBe 1.toByte()
        secret[31] shouldBe 32.toByte()

        SqlCipherSupportFactoryProvider.wipePassphrase(secret)

        for (byte in secret) {
            byte shouldBe 0.toByte()
        }
    }

    @Test
    fun `wipePassphrase handles empty array gracefully`() {
        val empty = ByteArray(0)
        SqlCipherSupportFactoryProvider.wipePassphrase(empty)
        empty.size shouldBe 0
    }
}
