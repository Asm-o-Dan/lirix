package com.example.npc.core.model

@JvmInline
value class EmbeddingRef(val vectorId: String) {
    init {
        require(vectorId.isNotBlank() && vectorId.length in 1..128) { "EmbeddingRef vectorId must not be blank and length in 1..128" }
    }
}
