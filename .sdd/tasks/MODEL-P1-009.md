## Задача MODEL-P1-009: Реализовать доменную модель UserPrototype

**Модуль:** `:core:model`  
**Целевой файл:** `core/model/src/main/kotlin/com/example/npc/core/model/classify/UserPrototype.kt`  
**Спецификация:** `.sdd/specs/core-model/overview.md#37-userprototype`  
**Контракт:** `.sdd/contracts/core-model__classify.md`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.model.classify

import java.time.Instant

data class UserPrototype(
    val id: Long = 0L,
    val packageName: String,
    val fingerprint: String,
    val category: Category,
    val supportCount: Int,
    val createdAt: Instant,
    val lastSeenAt: Instant = createdAt
) {
    init {
        require(id >= 0L) { "UserPrototype id must be >= 0 (got $id)" }
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(fingerprint.matches(Regex("^[0-9a-f]{64}$"))) {
            "fingerprint must be 64-char lowercase hex SHA-256 (got '$fingerprint')"
        }
        require(supportCount >= 1) { "supportCount must be >= 1 (got $supportCount)" }
        require(!createdAt.isAfter(lastSeenAt)) { "createdAt ($createdAt) cannot be after lastSeenAt ($lastSeenAt)" }
    }

    val isConfident: Boolean get() = supportCount >= CONFIDENCE_THRESHOLD

    companion object {
        const val CONFIDENCE_THRESHOLD = 2
    }
}
```

### Инварианты и алгоритм:
1. `fingerprint` обязан быть строго 64-символьной строкой в нижнем регистре шестнадцатеричного формата SHA-256.
2. `supportCount >= 1` (начальное значение при создании прототипа).
3. `isConfident`: возвращает `true` при `supportCount >= 2` (порог активации Prototype-First).
4. `createdAt` не может быть позже `lastSeenAt`.

### Критерии приемки (DoD):
- [ ] Ошибочный хэш или отрицательный id выбрасывает `IllegalArgumentException`.
- [ ] Свойство `isConfident` возвращает `false` при count = 1 и `true` при count >= 2.
