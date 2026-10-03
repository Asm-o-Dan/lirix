package com.example.npc.core.model

@JvmInline
value class SourceId(val value: String) {
    init {
        require(value.matches(VALID_REGEX)) { "SourceId must match ^[a-zA-Z0-9_-]{1,64}$, but was: '$value'" }
    }
    companion object {
        private val VALID_REGEX = Regex("^[a-zA-Z0-9_-]{1,64}$")
        val NOTIFICATION = SourceId("notification")
        val SMS = SourceId("sms")
        val MEDIA = SourceId("media")
    }
}
