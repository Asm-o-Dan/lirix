package com.example.npc.pipeline.dsl

import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.Engine
import com.example.npc.core.model.finance.Direction
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.pipeline.dsl.codec.CategorySerializer
import com.example.npc.pipeline.dsl.codec.DirectionSerializer
import com.example.npc.pipeline.dsl.codec.EngineSerializer
import com.example.npc.pipeline.dsl.codec.TransactionStatusSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface ActionDefinition {
    val type: String

    @Serializable
    @SerialName("SetCategory")
    data class SetCategory(
        @Serializable(with = CategorySerializer::class)
        @SerialName("category") val category: Category,
        @SerialName("confidence") val confidence: Double = 1.0,
        @Serializable(with = EngineSerializer::class)
        @SerialName("engine") val engine: Engine = Engine.RULES
    ) : ActionDefinition {
        override val type: String get() = "SetCategory"
        init {
            require(confidence in 0.0..1.0) { "Confidence must be within 0.0..1.0, but was $confidence" }
        }
    }

    @Serializable
    @SerialName("CreateTransaction")
    data class CreateTransaction(
        @Serializable(with = DirectionSerializer::class)
        @SerialName("direction") val direction: Direction? = null,
        @SerialName("amountVar") val amountVar: String = "parsedAmount",
        @SerialName("currencyVar") val currencyVar: String = "resolvedCurrency",
        @Serializable(with = TransactionStatusSerializer::class)
        @SerialName("status") val status: TransactionStatus = TransactionStatus.COMPLETED
    ) : ActionDefinition {
        override val type: String get() = "CreateTransaction"
    }

    @Serializable
    @SerialName("SaveToStorage")
    data class SaveToStorage(
        @SerialName("completeProcessing") val completeProcessing: Boolean = true
    ) : ActionDefinition {
        override val type: String get() = "SaveToStorage"
    }

    @Serializable
    @SerialName("DropEvent")
    data class DropEvent(
        @SerialName("reason") val reason: String
    ) : ActionDefinition {
        override val type: String get() = "DropEvent"
    }

    @Serializable
    @SerialName("StopProcessing")
    data class StopProcessing(
        @SerialName("reason") val reason: String
    ) : ActionDefinition {
        override val type: String get() = "StopProcessing"
    }
}
