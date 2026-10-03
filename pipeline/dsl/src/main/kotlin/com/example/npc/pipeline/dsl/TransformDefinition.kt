package com.example.npc.pipeline.dsl

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface TransformDefinition {
    val type: String

    @Serializable
    @SerialName("FingerprintCompute")
    data class FingerprintCompute(
        @SerialName("algorithm") val algorithm: String = "SHA-256-TEMPLATED",
        @SerialName("targetVar") val targetVar: String = "contentFingerprint"
    ) : TransformDefinition {
        override val type: String get() = "FingerprintCompute"
    }

    @Serializable
    @SerialName("RegionalTextSanitize")
    data class RegionalTextSanitize(
        @SerialName("maxChars") val maxChars: Int = 1024,
        @SerialName("normalizeNbsp") val normalizeNbsp: Boolean = true,
        @SerialName("stripDiacritics") val stripDiacritics: Boolean = false,
        @SerialName("targetVar") val targetVar: String = "sanitizedText"
    ) : TransformDefinition {
        override val type: String get() = "RegionalTextSanitize"
    }

    @Serializable
    @SerialName("AmountParse")
    data class AmountParse(
        @SerialName("currencyHint") val currencyHint: String? = null,
        @SerialName("sourceVar") val sourceVar: String = "sanitizedText",
        @SerialName("targetVar") val targetVar: String = "parsedAmount"
    ) : TransformDefinition {
        override val type: String get() = "AmountParse"
    }

    @Serializable
    @SerialName("CurrencyResolve")
    data class CurrencyResolve(
        @SerialName("defaultCurrency") val defaultCurrency: String = "RUP",
        @SerialName("sourceVar") val sourceVar: String = "sanitizedText",
        @SerialName("targetVar") val targetVar: String = "resolvedCurrency"
    ) : TransformDefinition {
        override val type: String get() = "CurrencyResolve"
    }

    @Serializable
    @SerialName("FinanceExtract")
    data class FinanceExtract(
        @SerialName("extractorId") val extractorId: String = "auto",
        @SerialName("allowedPackages") val allowedPackages: List<String> = emptyList(),
        @SerialName("timeoutMs") val timeoutMs: Long = 50L,
        @SerialName("useCircuitBreaker") val useCircuitBreaker: Boolean = true,
        @SerialName("targetVar") val targetVar: String = "financialTransaction"
    ) : TransformDefinition {
        override val type: String get() = "FinanceExtract"
        init {
            require(timeoutMs in 5L..500L) { "timeoutMs must be between 5 and 500 ms, but was $timeoutMs" }
        }
    }
}
