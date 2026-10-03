# Межзонный контракт: Pipeline DSL ↔ Pipeline Compiler

**Версия:** FROZEN v3  
**Дата заморозки:** 2026-09-28  
**Статус:** FROZEN (GATE 3 PASSED)  
**Стороны контракта:**
- Провайдер декларативной схемы AST: `zone/pipeline-dsl` (`:pipeline:dsl`)
- Потребитель AST и валидатор: `zone/pipeline-compiler` (`:pipeline:compiler`)
- Вторичные потребители: `zone/pipeline-store` (`:core:storage`), `zone/pipeline-runtime` (`:pipeline:runtime`), `zone/pipeline-replay` (`:feature:replay`)

---

### 1. Архитектурный контекст и границы

Контракт фиксирует синтаксическую структуру, типы данных, ограничения схемы и форматы полиморфной сериализации декларативного конвейера версии 1 (`schemaVersion = 1`). Модуль `:pipeline:dsl` является чистым Kotlin JVM модулем (Zero Android SDK dependencies, Zero Room dependencies) и поставляет иммутабельное AST-дерево в компилятор `:pipeline:compiler`.

```
┌────────────────────────────────────────────────────────┐
│               zone/pipeline-dsl (:pipeline:dsl)        │
│  - PipelineDefinition, StageDefinition                 │
│  - TriggerDefinition (NOTIFICATION, SMS, MEDIA)       │
│  - ConditionDefinition (PackageMatch, Regex, And, Not) │
│  - TransformDefinition (Sanitize, Amount, Currency)    │
│  - ActionDefinition (SetCategory, Transaction, Drop)   │
│  - PipelineJsonCodec (kotlinx.serialization)           │
└───────────────────────────┬────────────────────────────┘
                            │ AST / JSON (schemaVersion = 1)
                            ▼
┌────────────────────────────────────────────────────────┐
│         zone/pipeline-compiler (:pipeline:compiler)    │
│  - PipelineCompiler.compile(definition)                │
│  - Multi-pass Static Verification (P1xxx .. P4xxx)     │
│  - CompiledPipeline & FrameLayout lowering             │
└────────────────────────────────────────────────────────┘
```

---

### 2. Модели схемы DSL (`PipelineDefinition`, `StageDefinition`)

```kotlin
package com.example.npc.pipeline.dsl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PipelineDefinition(
    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String,

    @SerialName("description")
    val description: String? = null,

    @SerialName("schemaVersion")
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,

    @SerialName("revision")
    val revision: Long = 1L,

    @SerialName("enabled")
    val enabled: Boolean = true,

    @SerialName("priority")
    val priority: Int = 100,

    @SerialName("packageWhitelist")
    val packageWhitelist: List<String> = emptyList(),

    @SerialName("triggers")
    val triggers: List<TriggerDefinition>,

    @SerialName("stages")
    val stages: List<StageDefinition>,

    @SerialName("metadata")
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(id.matches(ID_REGEX)) {
            "Pipeline ID '$id' is invalid. Must match pattern ^[a-z0-9_-]{3,64}$"
        }
        require(name.isNotBlank() && name.length <= MAX_NAME_LENGTH) {
            "Pipeline name must be between 1 and $MAX_NAME_LENGTH characters, but was: '${name.take(30)}...'"
        }
        require(schemaVersion == CURRENT_SCHEMA_VERSION) {
            "Unsupported schemaVersion: $schemaVersion. Expected: $CURRENT_SCHEMA_VERSION"
        }
        require(revision >= 1L) {
            "Revision must be positive (>= 1), but was: $revision"
        }
        require(priority in 0..MAX_PRIORITY) {
            "Priority must be between 0 and $MAX_PRIORITY, but was: $priority"
        }
        require(triggers.isNotEmpty()) {
            "Pipeline must declare at least one trigger"
        }
        require(stages.isNotEmpty()) {
            "Pipeline must contain at least one stage"
        }
        require(stages.size <= MAX_STAGES_COUNT) {
            "Pipeline cannot exceed $MAX_STAGES_COUNT stages, but has: ${stages.size}"
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_NAME_LENGTH = 128
        const val MAX_PRIORITY = 1000
        const val MAX_STAGES_COUNT = 50
        val ID_REGEX = Regex("^[a-z0-9_-]{3,64}$")
    }
}

@Serializable
data class StageDefinition(
    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String,

    @SerialName("enabled")
    val enabled: Boolean = true,

    @SerialName("condition")
    val condition: ConditionDefinition? = null,

    @SerialName("transforms")
    val transforms: List<TransformDefinition> = emptyList(),

    @SerialName("actions")
    val actions: List<ActionDefinition> = emptyList(),

    @SerialName("terminateOnMatch")
    val terminateOnMatch: Boolean = false
) {
    init {
        require(id.matches(PipelineDefinition.ID_REGEX)) {
            "Stage ID '$id' is invalid. Must match pattern ^[a-z0-9_-]{3,64}$"
        }
        require(name.isNotBlank() && name.length <= PipelineDefinition.MAX_NAME_LENGTH) {
            "Stage name must be between 1 and ${PipelineDefinition.MAX_NAME_LENGTH} characters"
        }
        require(transforms.isNotEmpty() || actions.isNotEmpty()) {
            "Stage '$id' must declare at least one transform or action"
        }
    }
}
```

---

### 3. Полиморфные узлы DSL (Дискриминатор `"type"`)

#### 3.1. Триггеры (`TriggerDefinition`)
```kotlin
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
        @SerialName("ignoreSelf") val ignoreSelf: Boolean = true,
        @SerialName("subTypes") val subTypes: List<String> = emptyList()
    ) : TriggerDefinition {
        override val type: String get() = "NOTIFICATION"
    }

    @Serializable
    @SerialName("SMS")
    data class Sms(
        @SerialName("allowDirectReceiver") val allowDirectReceiver: Boolean = true,
        @SerialName("allowMessagingApps") val allowMessagingApps: Boolean = true
    ) : TriggerDefinition {
        override val type: String get() = "SMS"
    }

    @Serializable
    @SerialName("MEDIA")
    data class Media(
        @SerialName("captureArtwork") val captureArtwork: Boolean = false
    ) : TriggerDefinition {
        override val type: String get() = "MEDIA"
    }
}
```

#### 3.2. Логические предикаты (`ConditionDefinition`)
```kotlin
package com.example.npc.pipeline.dsl

import com.example.npc.core.model.classify.Category
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
                "LogicalAnd requires between 2 and $MAX_LOGICAL_ARITY conditions"
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
                "LogicalOr requires between 2 and $MAX_LOGICAL_ARITY conditions"
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
    object AlwaysTrue : ConditionDefinition {
        override val type: String get() = "AlwaysTrue"
    }

    @Serializable
    @SerialName("AlwaysFalse")
    object AlwaysFalse : ConditionDefinition {
        override val type: String get() = "AlwaysFalse"
    }

    companion object {
        const val MAX_LOGICAL_ARITY = 16
        const val MAX_NESTING_DEPTH = 5
    }
}
```

#### 3.3. Трансформации (`TransformDefinition`)
```kotlin
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
            require(timeoutMs in 5L..500L) { "timeoutMs must be between 5 and 500 ms" }
        }
    }
}
```

#### 3.4. Терминальные действия (`ActionDefinition`)
```kotlin
package com.example.npc.pipeline.dsl

import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.Engine
import com.example.npc.core.model.finance.Direction
import com.example.npc.core.model.finance.TransactionStatus
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
        @SerialName("category") val category: Category,
        @SerialName("confidence") val confidence: Double = 1.0,
        @SerialName("engine") val engine: Engine = Engine.RULES
    ) : ActionDefinition {
        override val type: String get() = "SetCategory"
        init {
            require(confidence in 0.0..1.0) { "Confidence must be within 0.0..1.0" }
        }
    }

    @Serializable
    @SerialName("CreateTransaction")
    data class CreateTransaction(
        @SerialName("direction") val direction: Direction? = null,
        @SerialName("amountVar") val amountVar: String = "parsedAmount",
        @SerialName("currencyVar") val currencyVar: String = "resolvedCurrency",
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
```

---

### 4. JSON Кодек (`PipelineJsonCodec`)

Контракт сериализации гарантирует строгий, детерминированный, кросс-платформенный JSON:

```kotlin
package com.example.npc.pipeline.dsl.codec

import com.example.npc.pipeline.dsl.PipelineDefinition
import kotlinx.serialization.json.Json

object PipelineJsonCodec {
    val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        prettyPrint = false
        encodeDefaults = true
        coerceInputValues = false
        classDiscriminator = "type"
    }

    val prettyJson: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        prettyPrint = true
        encodeDefaults = true
        coerceInputValues = false
        classDiscriminator = "type"
    }

    fun encodeToString(definition: PipelineDefinition): String = json.encodeToString(PipelineDefinition.serializer(), definition)
    fun encodeToPrettyString(definition: PipelineDefinition): String = prettyJson.encodeToString(PipelineDefinition.serializer(), definition)
    fun decodeFromString(jsonString: String): PipelineDefinition = json.decodeFromString(PipelineDefinition.serializer(), jsonString)
}
```

---

### 5. Точка входа компилятора (`PipelineCompiler`)

```kotlin
package com.example.npc.pipeline.compiler

import com.example.npc.pipeline.dsl.PipelineDefinition

interface PipelineCompiler {
    /**
     * Выполняет 4-проходную статическую верификацию и компиляцию AST в CompiledPipeline.
     * Предусловия: definition.schemaVersion == 1, definition.stages.isNotEmpty().
     * Постусловия: При Success возвращается иммутабельный CompiledPipeline с 0-alloc раскладкой FrameLayout.
     *              При Failure возвращается список диагностик, содержащий как минимум одну с уровнем ERROR.
     */
    fun compile(definition: PipelineDefinition): CompilationResult
}
```
