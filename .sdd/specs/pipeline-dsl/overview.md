# Спецификация: zone/pipeline-dsl

## Модуль: `:pipeline:dsl` · Зона: `zone/pipeline-dsl` · Версия схемы: `v1` · Фаза 2: «Конструктор конвейеров» · Статус: `PROPOSED`

---

### 1. Назначение, архитектурный контекст и границы модуля

Модуль `:pipeline:dsl` является чистым Kotlin JVM модулем (`kotlin-jvm`), свободным от любых зависимостей Android OS (`android.*`, `androidx.*`), Room, SQLite или UI-фреймворков. Он определяет формальную декларативную объектную модель (Abstract Syntax Tree / AST) и контракт схемы конвейера обработки событий (Notification Pipeline Constructor) версии `v1`.

#### 1.1. Роль в архитектуре Фазы 2 (принцип «Компилируй один раз, исполняй многократно»)
В архитектуре Фазы 2 конвейер обработки событий разделен на три независимых представления:
1. **Декларативное представление (`PipelineDefinition`, модуль `:pipeline:dsl`):**
   - Человекочитаемая, сериализуемая в JSON, версионируемая и редактируемая структура данных.
   - Хранится в репозитории/БД Room, передается в UI редактора, экспортируется/импортируется пользователем.
2. **Скомпилированное представление (`CompiledPipeline`, модуль `:pipeline:compiler`):**
   - Иммутабельный, валидированный исполняемый граф, в котором все регулярные выражения скомпилированы через безопасный движок (RE2/J), ссылки на узлы разрешены, а этапы скомпонованы в плоский массив прямых функциональных вызовов без рефлексии и runtime lookup.
3. **Исполняющий рантайм (`PipelineRuntime` / `ActivePipelineProvider`, модуль `:pipeline:runtime`):**
   - Горячий путь исполнения событий (`submit(eventId)`), захватывающий атомарную ссылку `AtomicReference<CompiledPipeline>`.

```
  ┌────────────────────────────────────────────────────────┐
  │         Декларативный уровень (:pipeline:dsl)          │
  │                  PipelineDefinition                    │
  │     (JSON AST, schemaVersion=1, Stages, Conditions)    │
  └───────────────────────────┬────────────────────────────┘
                              │
                    validate / compile (CPU background)
                              │
                              ▼
  ┌────────────────────────────────────────────────────────┐
  │         Скомпилированный уровень (:pipeline:compiler)  │
  │                   CompiledPipeline                     │
  │    (Иммутабельный, прекомпилированные RE2/J паттерны)   │
  └───────────────────────────┬────────────────────────────┘
                              │
                     AtomicReference.set() (Hot swap < 100ms)
                              │
                              ▼
  ┌────────────────────────────────────────────────────────┐
  │         Горячий рантайм (:pipeline:runtime)            │
  │             onNotificationPosted -> execute            │
  │      (Zero reflection, Zero JSON parsing, < 15ms)      │
  └────────────────────────────────────────────────────────┘
```

#### 1.2. Архитектурные инварианты изоляции
1. **Чистая JVM-модель (Zero Android):** Никаких импортов `android.content.Context`, `android.os.Bundle` или `android.service.notification.StatusBarNotification`. Модуль компилируется и тестируется на стандартной JVM за миллисекунды.
2. **Иммутабельность:** Все структуры данных являются Kotlin `data class` или `sealed interface` с неизменяемыми полями (`val`). Любая модификация конвейера порождает новую ревизию объекта.
3. **Детерминизм и каноничность:** Одинаковое семантическое состояние пайплайна всегда преобразуется в байт-в-байт идентичный канонический JSON с фиксированным порядком полей и лексикографической сортировкой ключей.
4. **Безопасность по умолчанию (Safe by Design):** Схема DSL жестко ограничивает глубину вложенности логических операторов, максимальную длину паттернов и предотвращает создание циклических зависимостей и ReDoS-уязвимостей еще на уровне декларативной спецификации.

---

### 2. Схема `PipelineDefinition` (Корень декларативной модели)

`PipelineDefinition` является корневым объектом схемы DSL v1. Он агрегирует метаданные конвейера, условия активации, белый список пакетов, триггеры источников и упорядоченный список этапов обработки.

#### 2.1. Определение сущности в Kotlin
```kotlin
package com.example.npc.pipeline.dsl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PipelineDefinition(
    /**
     * Уникальный строковый идентификатор конвейера.
     * Формат: lower-case slug (^[a-z0-9_-]{3,64}$).
     * Пример: "legacy-1.1-preset", "bank-alerts-v1", "work-slack-filter".
     */
    @SerialName("id")
    val id: String,

    /**
     * Человекочитаемое имя конвейера для отображения в UI и логах.
     * Длина: 1..128 символов.
     */
    @SerialName("name")
    val name: String,

    /**
     * Необязательное развернутое описание назначения конвейера.
     * Длина: до 1024 символов.
     */
    @SerialName("description")
    val description: String? = null,

    /**
     * Версия схемы декларативного описания.
     * Для текущей спецификации строго равна 1.
     */
    @SerialName("schemaVersion")
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,

    /**
     * Монотонно возрастающий номер ревизии конвейера (1, 2, 3...).
     * Инкрементируется при каждом сохранении изменений пользователем.
     */
    @SerialName("revision")
    val revision: Long = 1L,

    /**
     * Флаг активности конвейера.
     * Неактивные конвейеры исключаются из рантайм-маршрутизации событий.
     */
    @SerialName("enabled")
    val enabled: Boolean = true,

    /**
     * Приоритет конвейера при множественной маршрутизации.
     * Диапазон: 0..1000 (где 1000 — наивысший приоритет, 0 — fallback).
     */
    @SerialName("priority")
    val priority: Int = 100,

    /**
     * Аппаратный белый список имен Android-пакетов (Fast Package Pre-filter).
     * Если список не пуст, конвейер вызывается ТОЛЬКО для событий из указанных пакетов.
     * Пустой список означает обработку событий от всех пакетов операционной системы.
     */
    @SerialName("packageWhitelist")
    val packageWhitelist: List<String> = emptyList(),

    /**
     * Список триггеров источников событий, активирующих данный конвейер.
     * Должен содержать как минимум один триггер.
     */
    @SerialName("triggers")
    val triggers: List<TriggerDefinition>,

    /**
     * Линейная упорядоченная последовательность этапов обработки.
     * Выполняются строго последовательно от первого до последнего.
     */
    @SerialName("stages")
    val stages: List<StageDefinition>,

    /**
     * Произвольные пользовательские строковые метаданные и метки.
     * Например: {"author": "system", "builtIn": "true", "origin": "preset-legacy-1.1"}.
     */
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
```

---

### 3. Схема этапа `StageDefinition` (Линейный шаг конвейера)

В соответствии с утвержденной архитектурой Фазы 2, конвейер строится на базе **линейной модели шагов (Linear Pipeline Model)**. Ветвление реализуется исключительно через условные предикаты (`ConditionDefinition`) и терминаторы шагов, без сложного недетерминированного графа (DAG).

#### 3.1. Жизненный цикл исполнения этапа:
1. **Проверка фильтра (Gate Condition):** Если у этапа задано условие `condition`, оно вычисляется. Если результат `false`, этап полностью пропускается, управление передается следующему этапу. Если `condition == null`, этап исполняется безусловно.
2. **Трансформации (`transforms`):** Последовательно выполняются зарегистрированные шаги нормализации, санитайзинга и извлечения признаков. Результаты сохраняются в контекст события (`PipelineExecutionContext`).
3. **Действия (`actions`):** Выполняются побочные эффекты (классификация, создание проводки, сохранение в Room, сброс).
4. **Терминация (`terminateOnMatch`):** Если флаг `terminateOnMatch == true` и этап успешно сработал (условие истинно), конвейер немедленно завершает свою работу, минуя все последующие этапы.

#### 3.2. Определение сущности в Kotlin
```kotlin
package com.example.npc.pipeline.dsl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StageDefinition(
    /**
     * Уникальный идентификатор этапа в рамках конвейера.
     * Формат: lower-case slug (^[a-z0-9_-]{3,64}$).
     */
    @SerialName("id")
    val id: String,

    /**
     * Человекочитаемое название этапа (для отображения в UI и трассировке).
     */
    @SerialName("name")
    val name: String,

    /**
     * Флаг включения/выключения этапа. Выключенный этап игнорируется рантаймом.
     */
    @SerialName("enabled")
    val enabled: Boolean = true,

    /**
     * Входное условие-предикат (Gate).
     * Если null — этап выполняется безусловно.
     */
    @SerialName("condition")
    val condition: ConditionDefinition? = null,

    /**
     * Список последовательных трансформаций данных.
     */
    @SerialName("transforms")
    val transforms: List<TransformDefinition> = emptyList(),

    /**
     * Список действий и побочных эффектов этапа.
     */
    @SerialName("actions")
    val actions: List<ActionDefinition> = emptyList(),

    /**
     * Терминирующий флаг (Early Exit).
     * При значении true дальнейшая обработка конвейера прекращается, если этап сработал.
     */
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

### 4. Типы узлов и полиморфные структуры конвейера

Все декларативные узлы DSL используют строгую сериализацию `kotlinx.serialization` с полиморфным дискриминатором поля `"type"`.

```
                        ┌───────────────────────────────┐
                        │      Polymorphic Nodes        │
                        └───────────────┬───────────────┘
                                        │
        ┌───────────────────┬───────────┴───────────┬───────────────────┐
        ▼                   ▼                       ▼                   ▼
┌──────────────┐    ┌───────────────┐       ┌───────────────┐   ┌───────────────┐
│TriggerDef    │    │ConditionDef   │       │TransformDef   │   │ActionDef      │
│(NOTIFICATION,│    │(PackageMatch, │       │(Fingerprint,  │   │(SetCategory,  │
│ SMS, MEDIA)  │    │ Regex, And...)│       │ Sanitize...)  │   │ Transaction.. │
└──────────────┘    └───────────────┘       └───────────────┘   └───────────────┘
```

#### 4.1. Триггеры источников (`TriggerDefinition`)

Определяют, какие события операционной системы активируют обработку.

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

    /**
     * Источник: системные уведомления Android (NotificationListenerService).
     */
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

    /**
     * Источник: SMS-сообщения (SmsBroadcastReceiver или системные SMS-клиенты).
     */
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

    /**
     * Источник: мультимедийные сессии (MediaSessionManager).
     */
    @Serializable
    @SerialName("MEDIA")
    data class Media(
        @SerialName("captureArtwork")
        val captureArtwork: Boolean = false
    ) : TriggerDefinition {
        override val type: String get() = "MEDIA"
    }
}
```

---

#### 4.2. Логические предикаты (`ConditionDefinition`)

Декларативные предикаты для фильтрации событий. Модель поддерживает логическую композицию (`And`, `Or`, `Not`) с аппаратным лимитом глубины вложенности $\le 5$.

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

    /**
     * Сопоставление имени пакета с заданным набором.
     */
    @Serializable
    @SerialName("PackageMatch")
    data class PackageMatch(
        @SerialName("packages")
        val packages: List<String>,
        @SerialName("matchMode")
        val matchMode: MatchMode = MatchMode.EXACT,
        @SerialName("negate")
        val negate: Boolean = false
    ) : ConditionDefinition {
        override val type: String get() = "PackageMatch"
    }

    /**
     * Сопоставление отправителя (Sender / Title) со списком доверенных имен.
     */
    @Serializable
    @SerialName("SenderMatch")
    data class SenderMatch(
        @SerialName("senders")
        val senders: List<String>,
        @SerialName("caseSensitive")
        val caseSensitive: Boolean = false,
        @SerialName("negate")
        val negate: Boolean = false
    ) : ConditionDefinition {
        override val type: String get() = "SenderMatch"
    }

    /**
     * Проверка текста по безопасному регулярному выражению (компилируется через RE2/J).
     */
    @Serializable
    @SerialName("TextRegexMatch")
    data class TextRegexMatch(
        @SerialName("pattern")
        val pattern: String,
        @SerialName("targetField")
        val targetField: TextFieldTarget = TextFieldTarget.TITLE_OR_TEXT,
        @SerialName("caseSensitive")
        val caseSensitive: Boolean = false,
        @SerialName("maxMatchLength")
        val maxMatchLength: Int = 1024
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

    /**
     * Проверка текущей категории события в контексте обработки.
     */
    @Serializable
    @SerialName("CategoryMatch")
    data class CategoryMatch(
        @SerialName("category")
        val category: Category,
        @SerialName("minConfidence")
        val minConfidence: Double = 0.0
    ) : ConditionDefinition {
        override val type: String get() = "CategoryMatch"
    }

    /**
     * Проверка наличия пользовательского подтвержденного шаблона (Feedback Loop).
     */
    @Serializable
    @SerialName("PrototypeSupportCount")
    data class PrototypeSupportCount(
        @SerialName("minSupportCount")
        val minSupportCount: Int = 2
    ) : ConditionDefinition {
        override val type: String get() = "PrototypeSupportCount"
    }

    /**
     * Логическое "И" (Конъюнкция).
     */
    @Serializable
    @SerialName("LogicalAnd")
    data class LogicalAnd(
        @SerialName("conditions")
        val conditions: List<ConditionDefinition>
    ) : ConditionDefinition {
        override val type: String get() = "LogicalAnd"
        init {
            require(conditions.size in 2..MAX_LOGICAL_ARITY) {
                "LogicalAnd requires between 2 and $MAX_LOGICAL_ARITY conditions"
            }
        }
    }

    /**
     * Логическое "ИЛИ" (Дизъюнкция).
     */
    @Serializable
    @SerialName("LogicalOr")
    data class LogicalOr(
        @SerialName("conditions")
        val conditions: List<ConditionDefinition>
    ) : ConditionDefinition {
        override val type: String get() = "LogicalOr"
        init {
            require(conditions.size in 2..MAX_LOGICAL_ARITY) {
                "LogicalOr requires between 2 and $MAX_LOGICAL_ARITY conditions"
            }
        }
    }

    /**
     * Логическое "НЕ" (Отрицание).
     */
    @Serializable
    @SerialName("LogicalNot")
    data class LogicalNot(
        @SerialName("condition")
        val condition: ConditionDefinition
    ) : ConditionDefinition {
        override val type: String get() = "LogicalNot"
    }

    /**
     * Безусловные константы.
     */
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

---

#### 4.3. Узлы трансформации данных (`TransformDefinition`)

Трансформации выполняют детерминированную нормализацию, санитайзинг строк, вычисление отпечатков и изолированное извлечение признаков без глобальных побочных эффектов.

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

    /**
     * Вычисление хеша отпечатка шаблона уведомления (O(n)).
     * Заменяет цифры, даты, суммы на плейсхолдеры и строит SHA-256.
     */
    @Serializable
    @SerialName("FingerprintCompute")
    data class FingerprintCompute(
        @SerialName("algorithm")
        val algorithm: String = "SHA-256-TEMPLATED",
        @SerialName("targetVar")
        val targetVar: String = "contentFingerprint"
    ) : TransformDefinition {
        override val type: String get() = "FingerprintCompute"
    }

    /**
     * Региональная нормализация текста: удаление NBSP, диакритик, усечение длины.
     */
    @Serializable
    @SerialName("RegionalTextSanitize")
    data class RegionalTextSanitize(
        @SerialName("maxChars")
        val maxChars: Int = 1024,
        @SerialName("normalizeNbsp")
        val normalizeNbsp: Boolean = true,
        @SerialName("stripDiacritics")
        val stripDiacritics: Boolean = false,
        @SerialName("targetVar")
        val targetVar: String = "sanitizedText"
    ) : TransformDefinition {
        override val type: String get() = "RegionalTextSanitize"
    }

    /**
     * Парсинг денежной суммы в минимальных неделимых единицах (Minor Units Long).
     */
    @Serializable
    @SerialName("AmountParse")
    data class AmountParse(
        @SerialName("currencyHint")
        val currencyHint: String? = null,
        @SerialName("sourceVar")
        val sourceVar: String = "sanitizedText",
        @SerialName("targetVar")
        val targetVar: String = "parsedAmount"
    ) : TransformDefinition {
        override val type: String get() = "AmountParse"
    }

    /**
     * Разрешение валюты (RUP, MDL, RUB, EUR, USD) на базе контекста.
     */
    @Serializable
    @SerialName("CurrencyResolve")
    data class CurrencyResolve(
        @SerialName("defaultCurrency")
        val defaultCurrency: String = "RUP",
        @SerialName("sourceVar")
        val sourceVar: String = "sanitizedText",
        @SerialName("targetVar")
        val targetVar: String = "resolvedCurrency"
    ) : TransformDefinition {
        override val type: String get() = "CurrencyResolve"
    }

    /**
     * Изолированный вызов финансового экстрактора (APB, Prisbank, MAIB, SMS).
     */
    @Serializable
    @SerialName("FinanceExtract")
    data class FinanceExtract(
        @SerialName("extractorId")
        val extractorId: String = "auto",
        @SerialName("allowedPackages")
        val allowedPackages: List<String> = emptyList(),
        @SerialName("timeoutMs")
        val timeoutMs: Long = 50L,
        @SerialName("useCircuitBreaker")
        val useCircuitBreaker: Boolean = true,
        @SerialName("targetVar")
        val targetVar: String = "financialTransaction"
    ) : TransformDefinition {
        override val type: String get() = "FinanceExtract"
        init {
            require(timeoutMs in 5L..500L) { "timeoutMs must be between 5 and 500 ms" }
        }
    }
}
```

---

#### 4.4. Терминальные действия и побочные эффекты (`ActionDefinition`)

Определяют фиксацию состояний, запись в базу данных или подавление события.

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

    /**
     * Присвоение категории события, уверенности и идентификатора движка.
     */
    @Serializable
    @SerialName("SetCategory")
    data class SetCategory(
        @SerialName("category")
        val category: Category,
        @SerialName("confidence")
        val confidence: Double = 1.0,
        @SerialName("engine")
        val engine: Engine = Engine.RULES
    ) : ActionDefinition {
        override val type: String get() = "SetCategory"
        init {
            require(confidence in 0.0..1.0) { "Confidence must be within 0.0..1.0" }
        }
    }

    /**
     * Формирование финансовой транзакции из контекстных переменных.
     */
    @Serializable
    @SerialName("CreateTransaction")
    data class CreateTransaction(
        @SerialName("direction")
        val direction: Direction? = null,
        @SerialName("amountVar")
        val amountVar: String = "parsedAmount",
        @SerialName("currencyVar")
        val currencyVar: String = "resolvedCurrency",
        @SerialName("status")
        val status: TransactionStatus = TransactionStatus.COMPLETED
    ) : ActionDefinition {
        override val type: String get() = "CreateTransaction"
    }

    /**
     * Транзакционная запись результатов обработки события в Room.
     */
    @Serializable
    @SerialName("SaveToStorage")
    data class SaveToStorage(
        @SerialName("completeProcessing")
        val completeProcessing: Boolean = true
    ) : ActionDefinition {
        override val type: String get() = "SaveToStorage"
    }

    /**
     * Дроп/подавление события (не сохранять или пометить как DROPPED).
     */
    @Serializable
    @SerialName("DropEvent")
    data class DropEvent(
        @SerialName("reason")
        val reason: String
    ) : ActionDefinition {
        override val type: String get() = "DropEvent"
    }

    /**
     * Прекращение исполнения текущего пайплайна с указанием причины.
     */
    @Serializable
    @SerialName("StopProcessing")
    data class StopProcessing(
        @SerialName("reason")
        val reason: String
    ) : ActionDefinition {
        override val type: String get() = "StopProcessing"
    }
}
```

---

### 5. Сериализация, канонизация и эволюция схемы (Schema Evolution)

Модуль `:pipeline:dsl` предоставляет детерминированный кодек для сериализации конвейера в JSON и обратно, а также правила вычисления канонического хэша ревизии.

#### 5.1. Конфигурация сериализатора `PipelineJsonCodec`
Сериализация опирается на официальную библиотеку `kotlinx.serialization.json.Json` со строгими настройками:
- `encodeDefaults = true` — все поля со значениями по умолчанию явно сериализуются, что исключает расхождения при смене дефолтов в будущих версиях.
- `ignoreUnknownKeys = true` — обеспечивает прямую совместимость (Forward Compatibility): если приложение v1.1 встречает файл, экспортированный из v1.2 с новым дополнительным полем, парсер не падает.
- `isLenient = false` — запрещает нестандартные JSON-расширения (комментарии, неэкранированные кавычки).
- `classDiscriminator = "type"` — полиморфные типы десериализуются строго по полю `type`.

```kotlin
package com.example.npc.pipeline.dsl

import kotlinx.serialization.json.Json

object PipelineJsonCodec {
    val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = false
        classDiscriminator = "type"
    }

    val canonicalJson = Json {
        prettyPrint = false
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = false
        classDiscriminator = "type"
    }

    fun encodeToString(definition: PipelineDefinition): String {
        return prettyJson.encodeToString(PipelineDefinition.serializer(), definition)
    }

    fun decodeFromString(jsonString: String): PipelineDefinition {
        return canonicalJson.decodeFromString(PipelineDefinition.serializer(), jsonString)
    }
}
```

#### 5.2. Правила детерминированной канонизации (Canonical JSON Hash)
Для обеспечения целостности данных, версионирования в SQLite и детекции изменений без ложных diff'ов определяется алгоритм канонизации:
1. Ключи JSON-объектов на всех уровнях вложенности сортируются лексикографически по возрастанию байтовых значений Unicode.
2. Пробельные символы между токенами (`:`, `,`, `{`, `}`) удаляются (compact format).
3. Строковые литералы кодируются строго в UTF-8 без BOM.
4. Контрольная сумма ревизии вычисляется как $H = \text{SHA-256}(\text{CanonicalJson})$.

#### 5.3. Инварианты валидности структуры (Structural Invariants)
Перед передачей объекта в компилятор проверяются структурные инварианты схемы:
1. **Идентификационная чистота:** Все `id` в пайплайне (`PipelineDefinition.id`, `StageDefinition.id`) удовлетворяют маске `^[a-z0-9_-]{3,64}$` и являются уникальными в пределах конвейера. Дубликаты ID этапов строго запрещены.
2. **Лимит сложности графа:**
   - Количество этапов $\le 50$.
   - Глубина вложенности логических операторов (`And`, `Or`, `Not`) $\le 5$.
   - Количество операндов в одном `LogicalAnd` / `LogicalOr` $\le 16$.
3. **Безопасность регулярных выражений (ReDoS Guard):**
   - Длина паттерна `TextRegexMatch.pattern` $\le 256$ символов.
   - Запрет заведомо опасных вложенных квантификаторов вида `(a+)+`, `(a|b+)+`, `(a*)*`. В компиляторе `:pipeline:compiler` паттерны компилируются исключительно через `com.google.re2j.Pattern` с гарантией линейного времени $O(n)$.

---

### 6. Эталонный пресет «Legacy 1.1» (The Golden Baseline Preset)

Для обеспечения **100% паритета** с поведением монолитного конвейера Фазы 1.1 создается эталонный системный пресет `preset-legacy-1.1`. Ниже приведено полное декларативное описание на языке DSL v1.

```json
{
  "id": "legacy-1.1-preset",
  "name": "Legacy 1.1 Baseline Pipeline",
  "description": "Эталонная конфигурация Фазы 1.1 для дифференциального теста паритета 100% совпадения",
  "schemaVersion": 1,
  "revision": 1,
  "enabled": true,
  "priority": 1000,
  "packageWhitelist": [],
  "metadata": {
    "systemPreset": "true",
    "parityBaseline": "1.1"
  },
  "triggers": [
    {
      "type": "NOTIFICATION",
      "ignoreSelf": true,
      "subTypes": []
    },
    {
      "type": "SMS",
      "allowDirectReceiver": true,
      "allowMessagingApps": true
    },
    {
      "type": "MEDIA",
      "captureArtwork": false
    }
  ],
  "stages": [
    {
      "id": "stage-fingerprint",
      "name": "Вычисление отпечатка контента",
      "enabled": true,
      "condition": null,
      "transforms": [
        {
          "type": "FingerprintCompute",
          "algorithm": "SHA-256-TEMPLATED",
          "targetVar": "contentFingerprint"
        }
      ],
      "actions": [],
      "terminateOnMatch": false
    },
    {
      "id": "stage-prototype-feedback",
      "name": "Пользовательский прототип (Feedback Loop)",
      "enabled": true,
      "condition": {
        "type": "PrototypeSupportCount",
        "minSupportCount": 2
      },
      "transforms": [],
      "actions": [
        {
          "type": "SetCategory",
          "category": "UNCLASSIFIED",
          "confidence": 1.0,
          "engine": "PROTOTYPE"
        }
      ],
      "terminateOnMatch": false
    },
    {
      "id": "stage-bank-finance",
      "name": "Финансовая обработка доверенных банков",
      "enabled": true,
      "condition": {
        "type": "LogicalAnd",
        "conditions": [
          {
            "type": "LogicalOr",
            "conditions": [
              {
                "type": "PackageMatch",
                "packages": [
                  "com.apb.mobile",
                  "com.prisbank.app",
                  "md.maib.maibank"
                ],
                "matchMode": "EXACT"
              },
              {
                "type": "LogicalAnd",
                "conditions": [
                  {
                    "type": "PackageMatch",
                    "packages": [
                      "com.google.android.apps.messaging",
                      "com.android.mms",
                      ""
                    ],
                    "matchMode": "EXACT"
                  },
                  {
                    "type": "SenderMatch",
                    "senders": [
                      "APB",
                      "AGROPROMBANK",
                      "PRISBANK",
                      "SBERBANK",
                      "MAIB",
                      "900"
                    ],
                    "caseSensitive": false
                  }
                ]
              }
            ]
          },
          {
            "type": "LogicalNot",
            "condition": {
              "type": "PackageMatch",
              "packages": [
                "org.telegram.messenger",
                "org.telegram.plus",
                "org.thunderdog.challegram",
                "com.radolyn.ayugram",
                "nekox.messenger",
                "com.whatsapp",
                "com.whatsapp.w4b",
                "com.viber.voip",
                "com.facebook.orca",
                "com.facebook.mlite",
                "com.discord",
                "com.vkontakte.android",
                "com.vk.im"
              ],
              "matchMode": "EXACT"
            }
          }
        ]
      },
      "transforms": [
        {
          "type": "RegionalTextSanitize",
          "maxChars": 1024,
          "normalizeNbsp": true,
          "stripDiacritics": false,
          "targetVar": "sanitizedText"
        },
        {
          "type": "FinanceExtract",
          "extractorId": "auto",
          "allowedPackages": [
            "com.apb.mobile",
            "com.prisbank.app",
            "md.maib.maibank",
            "com.google.android.apps.messaging",
            "com.android.mms"
          ],
          "timeoutMs": 50,
          "useCircuitBreaker": true,
          "targetVar": "financialTransaction"
        }
      ],
      "actions": [
        {
          "type": "SetCategory",
          "category": "FINANCE",
          "confidence": 0.98,
          "engine": "RULES"
        },
        {
          "type": "SaveToStorage",
          "completeProcessing": true
        }
      ],
      "terminateOnMatch": true
    },
    {
      "id": "stage-music",
      "name": "Мультимедиа и аудиоплееры",
      "enabled": true,
      "condition": {
        "type": "PackageMatch",
        "packages": [
          "ru.yandex.music",
          "app.revanced.android.youtube",
          "com.google.android.apps.youtube.music",
          "com.spotify.music",
          "com.shaiban.audioplayer.mplayer",
          "com.vkontakte.music",
          "org.videolan.vlc",
          "com.apple.android.music",
          "deezer.android.app",
          "com.google.android.youtube"
        ],
        "matchMode": "EXACT"
      },
      "transforms": [],
      "actions": [
        {
          "type": "SetCategory",
          "category": "MUSIC",
          "confidence": 0.95,
          "engine": "RULES"
        },
        {
          "type": "SaveToStorage",
          "completeProcessing": true
        }
      ],
      "terminateOnMatch": true
    },
    {
      "id": "stage-communication",
      "name": "Мессенджеры, звонки и личные SMS",
      "enabled": true,
      "condition": {
        "type": "LogicalOr",
        "conditions": [
          {
            "type": "PackageMatch",
            "packages": [
              "org.telegram.messenger",
              "org.telegram.plus",
              "org.thunderdog.challegram",
              "com.radolyn.ayugram",
              "nekox.messenger",
              "com.whatsapp",
              "com.whatsapp.w4b",
              "com.viber.voip",
              "com.facebook.orca",
              "com.facebook.mlite",
              "com.discord",
              "com.vkontakte.android",
              "com.vk.im",
              "com.google.android.dialer",
              "com.android.phone",
              "com.android.server.telecom",
              "com.google.android.apps.messaging",
              "com.android.mms"
            ],
            "matchMode": "EXACT"
          }
        ]
      },
      "transforms": [],
      "actions": [
        {
          "type": "SetCategory",
          "category": "COMMUNICATION",
          "confidence": 0.90,
          "engine": "RULES"
        },
        {
          "type": "SaveToStorage",
          "completeProcessing": true
        }
      ],
      "terminateOnMatch": true
    },
    {
      "id": "stage-services",
      "name": "Сервисы: погода, доставка, такси",
      "enabled": true,
      "condition": {
        "type": "LogicalOr",
        "conditions": [
          {
            "type": "PackageMatch",
            "packages": [
              "ru.yandex.weatherplugin",
              "com.google.android.apps.weather",
              "org.mozilla.firefox",
              "com.android.chrome",
              "com.google.android.gm",
              "com.google.android.apps.maps",
              "ru.yandex.taxi",
              "com.ubercab",
              "com.deliveryclub",
              "ru.yandex.eda",
              "com.eventengine.app.debug",
              "android"
            ],
            "matchMode": "EXACT"
          },
          {
            "type": "TextRegexMatch",
            "pattern": "(?i)(погода|°c|°f|ветер|дождь|ясно|облачно|ощущается как|снег|градус|давление|влажность|водитель|автомобиль|курьер|заказ в пути|доставка)",
            "targetField": "TITLE_OR_TEXT",
            "caseSensitive": false,
            "maxMatchLength": 1024
          }
        ]
      },
      "transforms": [],
      "actions": [
        {
          "type": "SetCategory",
          "category": "SERVICES",
          "confidence": 0.90,
          "engine": "RULES"
        },
        {
          "type": "SaveToStorage",
          "completeProcessing": true
        }
      ],
      "terminateOnMatch": true
    },
    {
      "id": "stage-fallback-other",
      "name": "Fallback: прочие уведомления",
      "enabled": true,
      "condition": null,
      "transforms": [],
      "actions": [
        {
          "type": "SetCategory",
          "category": "OTHER",
          "confidence": 0.50,
          "engine": "RULES"
        },
        {
          "type": "SaveToStorage",
          "completeProcessing": true
        }
      ],
      "terminateOnMatch": true
    }
  ]
}
```

---

### 7. Тест-план и критерии приёмки (DoD и Quality Gates)

Для обеспечения надежности и стабильности модуля `:pipeline:dsl` в CI/CD пайплайне утверждается следующий обязательный набор тестов:

#### 7.1. Property-Based тесты обратимости сериализации (Round-Trip Invariant)
- **Инвариант:** Для любого валидного сгенерированного объекта `x: PipelineDefinition` строго выполняется:
  $$\text{deserialize}(\text{serialize}(x)) \equiv x$$
- **Реализация:** Kotest Property Testing (`Arb.bind<PipelineDefinition>()`) на 10 000 случайных генераций структур данных. Проверяются граничные значения: нулевые списки, длинные строки, предельные приоритеты (0 и 1000), все типы условий, трансформаций и действий.

#### 7.2. Тесты стабильности канонического JSON
- **Инвариант:** Изменение порядка добавления полей в `metadata` или перестановка ключей при сериализации не меняет вычисленный SHA-256 канонического представления.
- **Проверка:** Сериализация одного и того же объекта на разных JVM-окружениях и архитектурах процессора (x86_64 в эмуляторе и ARM64 на Poco M7) дает идентичный бинарный хэш.

#### 7.3. Негативные тесты и валидация схемы
1. **Невалидные идентификаторы:** ID со спецсимволами (`id = "my pipeline!"`, `id = "UPPER_CASE"`, `id = "a"`) должны выбрасывать `IllegalArgumentException` при создании.
2. **Дубликаты идентификаторов этапов:** Конвейер с двумя этапами, имеющими одинаковый `id`, должен отбраковываться на уровне валидации.
3. **Неизвестные поля (Forward Compatibility):** JSON, содержащий новые поля из будущих версий (например, `"futureEnhancement": 42`), успешно парсится в текущую модель без исключений благодаря `ignoreUnknownKeys = true`.
4. **Синтаксический мусор и Fuzzing:** Передача в десериализатор пустой строки, бинарного мусора, поврежденного JSON, массивов вместо объектов приводит к детерминированному `SerializationException` без утечек памяти и без падений JVM.

#### 7.4. Защита от катастрофического бэктрекинга (ReDoS)
1. **Длина паттерна:** Попытка создания `TextRegexMatch` с длиной `pattern > 256` символов немедленно прерывается исключением.
2. **Опасные паттерны:** Тестовый прогон проверяет поведение на известных уязвимых конструкциях `(a+)+$`, `(a|a)+$`, `^(a+)+y`. В компиляторе `:pipeline:compiler` данные паттерны исполняются на базе RE2/J (линейное время сопоставления $O(n)$ гарантировано).

#### 7.5. Бюджеты производительности на Poco M7 (Serial: `2440cbe2`)
| Операция | Бюджет времени (Poco M7) | Бюджет аллокаций памяти |
|---|---|---|
| Полная десериализация `PipelineDefinition` (50 узлов) | $\le 10$ мс (JVM cold $\le 20$ мс) | $\le 150$ КБ в хипе |
| Сериализация в Pretty JSON | $\le 5$ мс | $\le 80$ КБ в хипе |
| Вычисление канонического SHA-256 хэша | $\le 2$ мс | $\le 20$ КБ в хипе |

---

### 8. Межмодульные интерфейсы и передача контракта

Модуль `:pipeline:dsl` экспортирует следующие ключевые артефакты для смежных зон Фазы 2:
1. **Для зоны `zone/pipeline-compiler` (`:pipeline:compiler`):**
   - Корневую структуру AST `PipelineDefinition`, перечисления `MatchMode`, `TextFieldTarget`.
   - Иерархии `TriggerDefinition`, `ConditionDefinition`, `TransformDefinition`, `ActionDefinition`.
2. **Для зоны `zone/pipeline-store` (`:data:pipeline-store`):**
   - Кодек `PipelineJsonCodec` для сохранения определения конвейера в колонку `definition_json` таблицы Room `pipeline_definition`.
   - Алгоритм канонизации для генерации контрольной суммы ревизии `revision_hash`.
3. **Для зоны `zone/parity-test` (`:testing:parity`):**
   - Системный фабричный метод `Legacy11Preset.create(): PipelineDefinition`, гарантирующий идентичность логики хардкод-конвейера Фазы 1.1 и декларативного DSL.
