# Межзонный контракт: Core Model (Доменное ядро)

**Версия:** FROZEN v1  
**Дата заморозки:** 2026-09-26  
**Стороны контракта:**
- Провайдер: `zone/core-model` (`:core:model`)
- Потребители: `zone/core-storage`, `zone/ingest-notification`, `zone/ingest-sms`, `zone/ingest-media`, `zone/ui-timeline`, `zone/app-lifecycle`

---

### 1. Доменные типы и value-классы

```kotlin
package com.example.npc.core.model

import java.time.Instant

@JvmInline
value class SourceId(val value: String) {
    init {
        require(value.matches(Regex("^[a-zA-Z0-9_-]{1,64}$"))) { "Invalid SourceId format" }
    }
    companion object {
        val NOTIFICATION = SourceId("notification")
        val SMS = SourceId("sms")
        val MEDIA = SourceId("media")
    }
}

@JvmInline
value class ThreadKey(val value: String) {
    init {
        require(value.isNotBlank() && value.length in 1..256) { "Invalid ThreadKey length" }
    }
}

@JvmInline
value class DeduplicationKey(val value: String) {
    init {
        require(value.matches(Regex("^[0-9a-f]{64}$"))) { "DeduplicationKey must be 64-char lowercase hex SHA-256" }
    }
}

enum class Lang {
    RU, EN, UNK
}

data class RawEvent(
    val id: Long = 0L,
    val seq: Long,
    val source: SourceId,
    val packageName: String,
    val receivedAt: Instant,
    val payloadJson: String,
    val hash: DeduplicationKey
)

data class Event(
    val id: Long = 0L,
    val rawId: Long,
    val ts: Instant,
    val title: String,
    val text: String,
    val normalizedText: String,
    val lang: Lang,
    val threadKey: ThreadKey? = null,
    val isUpdateOf: Long? = null
)

data class SourceHealth(
    val source: SourceId,
    val lastEventAt: Instant?,
    val events24h: Int,
    val lastError: String?,
    val queueDepth: Int
)
```

---

### 2. Контракт нормализации (`com.example.npc.core.model.normalize`)

```kotlin
package com.example.npc.core.model.normalize

import com.example.npc.core.model.*

object EventNormalizer {
    /**
     * Очистка невидимых символов Unicode, унификация переносов строк, схлопывание пробелов.
     */
    fun cleanText(rawText: String?): String

    /**
     * Быстрое детерминированное определение языка (RU, EN, UNK) на основе алфавитных долей.
     */
    fun detectLang(text: String): Lang

    /**
     * Детерминированный расчет SHA-256 хеша полезной нагрузки с исключением нестабильных полей postTime/when.
     */
    fun computeDeduplicationKey(source: SourceId, packageName: String, payloadJson: String): DeduplicationKey

    /**
     * Фабричный метод структурирования Event из RawEvent.
     */
    fun normalize(
        rawEvent: RawEvent,
        title: String,
        text: String,
        threadKey: ThreadKey? = null,
        isUpdateOf: Long? = null
    ): Event
}
```
