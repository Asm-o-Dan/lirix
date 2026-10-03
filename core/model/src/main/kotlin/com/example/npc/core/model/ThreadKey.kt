package com.example.npc.core.model

@JvmInline
value class ThreadKey(val value: String) {
    init {
        require(value.isNotBlank() && value.length in 1..256) { "ThreadKey must not be blank and length in 1..256, but was length: ${value.length}" }
    }
}
