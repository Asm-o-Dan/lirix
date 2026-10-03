package com.example.npc.pipeline.dsl

import com.example.npc.core.model.classify.Category
import com.example.npc.pipeline.dsl.codec.CategorySerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
enum class MatchMode {
    @SerialName("EXACT") EXACT,
    @SerialName("PREFIX") PREFIX,
    @SerialName("GLOB") GLOB,
    @SerialName("CONTAINS") CONTAINS
}

@Serializable
enum class TextFieldTarget {
    @SerialName("TITLE_OR_TEXT") TITLE_OR_TEXT,
    @SerialName("TITLE") TITLE,
    @SerialName("TEXT") TEXT,
    @SerialName("SENDER") SENDER,
    @SerialName("PACKAGE_NAME") PACKAGE_NAME
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface ConditionDefinition {
    val type: String

    @Serializable
    @SerialName("PackageMatch")
    data class PackageMatch(
        @SerialName("packages") val packages: List<String>,
        @SerialName("matchMode") val matchMode: MatchMode = MatchMode.EXACT,
        @SerialName("negate") val negate: Boolean = false
    ) : ConditionDefinition {
        override val type: String get() = "PackageMatch"
    }

    @Serializable
    @SerialName("SenderMatch")
    data class SenderMatch(
        @SerialName("senders") val senders: List<String>,
        @SerialName("caseSensitive") val caseSensitive: Boolean = false,
        @SerialName("negate") val negate: Boolean = false
    ) : ConditionDefinition {
        override val type: String get() = "SenderMatch"
    }

    @Serializable
    @SerialName("TextRegexMatch")
    data class TextRegexMatch(
        @SerialName("pattern") val pattern: String,
        @SerialName("targetField") val targetField: TextFieldTarget = TextFieldTarget.TITLE_OR_TEXT,
        @SerialName("caseSensitive") val caseSensitive: Boolean = false,
        @SerialName("maxMatchLength") val maxMatchLength: Int = 1024
    ) : ConditionDefinition {
        override val type: String get() = "TextRegexMatch"
        init {
            require(pattern.isNotBlank()) { "Regex pattern cannot be blank" }
            require(pattern.length <= MAX_REGEX_LENGTH) {
                "Regex length ${pattern.length} exceeds max allowed $MAX_REGEX_LENGTH chars"
            }
        }
        companion object {
            const val MAX_REGEX_LENGTH = 256
        }
    }

    @Serializable
    @SerialName("CategoryMatch")
    data class CategoryMatch(
        @Serializable(with = CategorySerializer::class)
        @SerialName("category") val category: Category,
        @SerialName("minConfidence") val minConfidence: Double = 0.0
    ) : ConditionDefinition {
        override val type: String get() = "CategoryMatch"
    }

    @Serializable
    @SerialName("PrototypeSupportCount")
    data class PrototypeSupportCount(
        @SerialName("minSupportCount") val minSupportCount: Int = 2
    ) : ConditionDefinition {
        override val type: String get() = "PrototypeSupportCount"
    }

    @Serializable
    @SerialName("LogicalAnd")
    data class LogicalAnd(
        @SerialName("conditions") val conditions: List<ConditionDefinition>
    ) : ConditionDefinition {
        override val type: String get() = "LogicalAnd"
        init {
            require(conditions.size in 2..MAX_LOGICAL_ARITY) {
                "LogicalAnd requires between 2 and $MAX_LOGICAL_ARITY conditions, but was ${conditions.size}"
            }
        }
    }

    @Serializable
    @SerialName("LogicalOr")
    data class LogicalOr(
        @SerialName("conditions") val conditions: List<ConditionDefinition>
    ) : ConditionDefinition {
        override val type: String get() = "LogicalOr"
        init {
            require(conditions.size in 2..MAX_LOGICAL_ARITY) {
                "LogicalOr requires between 2 and $MAX_LOGICAL_ARITY conditions, but was ${conditions.size}"
            }
        }
    }

    @Serializable
    @SerialName("LogicalNot")
    data class LogicalNot(
        @SerialName("condition") val condition: ConditionDefinition
    ) : ConditionDefinition {
        override val type: String get() = "LogicalNot"
    }

    @Serializable
    @SerialName("AlwaysTrue")
    data object AlwaysTrue : ConditionDefinition {
        override val type: String get() = "AlwaysTrue"
    }

    @Serializable
    @SerialName("AlwaysFalse")
    data object AlwaysFalse : ConditionDefinition {
        override val type: String get() = "AlwaysFalse"
    }

    companion object {
        const val MAX_LOGICAL_ARITY = 16
        const val MAX_NESTING_DEPTH = 5
    }
}
