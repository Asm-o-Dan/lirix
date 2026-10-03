package com.example.npc.core.model

@JvmInline
value class DeduplicationKey(val value: String) {
    init {
        require(value.matches(HEX_REGEX)) { "DeduplicationKey must be 64-char lowercase hex SHA-256, but was: '$value'" }
    }
    companion object {
        private val HEX_REGEX = Regex("^[0-9a-f]{64}$")
    }
}
