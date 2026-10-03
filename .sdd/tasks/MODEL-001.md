## Задача MODEL-001: реализовать базовые value-классы и enum Lang

**Файл:** 
- `core/model/src/main/kotlin/com/example/npc/core/model/SourceId.kt`
- `core/model/src/main/kotlin/com/example/npc/core/model/ThreadKey.kt`
- `core/model/src/main/kotlin/com/example/npc/core/model/DeduplicationKey.kt`
- `core/model/src/main/kotlin/com/example/npc/core/model/EmbeddingRef.kt`
- `core/model/src/main/kotlin/com/example/npc/core/model/Lang.kt`
(создать)

**Спека:** `.sdd/specs/core-model/overview.md#типы-данных` (v1)

**Сигнатуры (НЕ МЕНЯТЬ):**

```kotlin
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

@JvmInline
value class ThreadKey(val value: String) {
    init {
        require(value.isNotBlank() && value.length in 1..256) { "ThreadKey must not be blank and length in 1..256, but was length: ${value.length}" }
    }
}

@JvmInline
value class DeduplicationKey(val value: String) {
    init {
        require(value.matches(HEX_REGEX)) { "DeduplicationKey must be 64-char lowercase hex SHA-256, but was: '$value'" }
    }
    companion object {
        private val HEX_REGEX = Regex("^[0-9a-f]{64}$")
    }
}

@JvmInline
value class EmbeddingRef(val vectorId: String) {
    init {
        require(vectorId.isNotBlank() && vectorId.length in 1..128) { "EmbeddingRef vectorId must not be blank and length in 1..128" }
    }
}

enum class Lang {
    RU,
    EN,
    UNK
}
```

**Ошибки:**
- При нарушении `init`-инвариантов выбрасывать `IllegalArgumentException`.

**Граничные случаи:**
- Пустые строки, пробелы, некорректный hex, некорректная длина строки.

**Запрещено:**
- Использовать Android SDK (`android.*`).
- Добавлять изменяемые (`var`) поля.

**Критерий приёмки:**
- Проходят юнит-тесты `core/model/src/test/kotlin/com/example/npc/core/model/ValueTypesTest.kt`.
