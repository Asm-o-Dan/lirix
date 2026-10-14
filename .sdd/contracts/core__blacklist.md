# Межзонный контракт: core__blacklist

- **Зоны:** `media-core` & `storage` <──> `media-ingress` <──> `media-ui`
- **Версия:** FROZEN v1
- **Дата фиксации:** 2026-10-14
- **Статус:** FROZEN
- **Нормативная база:** [architecture_blacklist_filters.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/architecture_blacklist_filters.md), ADR-016, SPEC-BLK-01, SPEC-BLK-02

---

## 1. Назначение
Контракт синхронизирует типы данных, методы фильтрации рантайма и результаты каскадной очистки между слоем хранения (`Room v11`), бизнес-логикой ядра (`MusicFeatureEngine`) и пользовательским интерфейсом (`BlacklistSettingsScreen`).

---

## 2. Модели Данных

```kotlin
package com.lirix.app.storage.blacklist

enum class BlacklistRuleType {
    TITLE,
    ARTIST,
    PACKAGE_NAME
}

enum class BlacklistMatchMode {
    CONTAINS_IGNORE_CASE,
    EXACT,
    REGEX
}

data class BlacklistRuleEntity(
    val id: String,
    val ruleType: BlacklistRuleType,
    val pattern: String,
    val matchMode: BlacklistMatchMode = BlacklistMatchMode.CONTAINS_IGNORE_CASE,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
```

---

## 3. Операционные Контракты

### 3.1 Контракт Фильтрации Входящего Потока (Ingress Guard)
```kotlin
package com.lirix.app.feature.blacklist

interface BlacklistGuard {
    /**
     * Возвращает true, если трек подпадает под любое активное правило.
     * Должно выполняться за < 2ms без дискового ввода-вывода.
     */
    fun isBlacklisted(
        title: String,
        artist: String,
        packageName: String
    ): Boolean
}
```

### 3.2 Контракт Каскадной Очистки (Purge Engine)
```kotlin
package com.lirix.app.feature.blacklist

data class PurgeReport(
    val deletedTracksCount: Int,
    val deletedSessionsCount: Int
)

interface BlacklistPurgeEngine {
    /**
     * Атомарно удаляет все треки и связанные с ними сессии прослушивания,
     * подходящие под указанное правило.
     */
    suspend fun purgeMatchingTracks(rule: BlacklistRuleEntity): PurgeReport

    /**
     * Рассчитывает количество треков, которые будут удалены правилом,
     * без фактического удаления (для превью в UI).
     */
    suspend fun previewPurgeCount(rule: BlacklistRuleEntity): Int
}
```
