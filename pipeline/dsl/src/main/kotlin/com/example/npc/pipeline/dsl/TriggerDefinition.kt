package com.example.npc.pipeline.dsl

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface TriggerDefinition {
    val type: String

    @Serializable
    @SerialName("NOTIFICATION")
    data class Notification(
        @SerialName("ignoreSelf")
        val ignoreSelf: Boolean = true,
        @SerialName("subTypes")
        val subTypes: List<String> = emptyList()
    ) : TriggerDefinition {
        override val type: String get() = "NOTIFICATION"
    }

    @Serializable
    @SerialName("SMS")
    data class Sms(
        @SerialName("allowDirectReceiver")
        val allowDirectReceiver: Boolean = true,
        @SerialName("allowMessagingApps")
        val allowMessagingApps: Boolean = true
    ) : TriggerDefinition {
        override val type: String get() = "SMS"
    }

    @Serializable
    @SerialName("MEDIA")
    data class Media(
        @SerialName("captureArtwork")
        val captureArtwork: Boolean = false
    ) : TriggerDefinition {
        override val type: String get() = "MEDIA"
    }
}
