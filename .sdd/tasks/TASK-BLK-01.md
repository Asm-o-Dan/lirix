# Задача TASK-BLK-01: Модель хранения BlacklistRuleEntity, DAO и Room Миграция v10 -> v11

- **ID задачи:** `TASK-BLK-01`
- **Роль исполнителя:** Кодер (Storage & Core)
- **Зона:** `media-core` & `storage`
- **Файлы:**
  1. `app/src/main/java/com/lirix/app/storage/blacklist/BlacklistEntities.kt` (создать)
  2. `app/src/main/java/com/lirix/app/storage/blacklist/BlacklistDao.kt` (создать)
  3. `app/src/main/java/com/lirix/app/storage/MusicDao.kt` (добавить методы пакетного удаления `deleteTracksByKeys`, `deleteSessionsByTrackKeys`)
  4. `app/src/main/java/com/lirix/app/storage/AppDatabase.kt` (обновить: `version = 11`, `MIGRATION_10_11`, добавить `blacklistDao()`)
- **Спека:** [.sdd/specs/media-core/blacklist_storage_spec.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-core/blacklist_storage_spec.md)
- **Приоритет:** HIGH (Блокирует `TASK-BLK-02` и UI)

---

## 1. Цель задачи
Реализовать слой хранения правил блокировки (Черного списка) в базе данных Room: сущность `BlacklistRuleEntity`, перечисления `BlacklistRuleType` и `BlacklistMatchMode`, DAO-интерфейс `BlacklistDao`, методы пакетного удаления в `MusicDao`, а также безопасную миграцию `MIGRATION_10_11` с версии 10 на версию 11.

---

## 2. Модель данных (создать в `storage/blacklist/BlacklistEntities.kt`)

```kotlin
package com.lirix.app.storage.blacklist

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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

@Entity(
    tableName = "blacklist_rules",
    indices = [
        Index(value = ["isEnabled"]),
        Index(value = ["ruleType"])
    ]
)
data class BlacklistRuleEntity(
    @PrimaryKey
    val id: String,
    val ruleType: BlacklistRuleType,
    val pattern: String,
    val matchMode: BlacklistMatchMode = BlacklistMatchMode.CONTAINS_IGNORE_CASE,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
```

---

## 3. DAO интерфейс (создать в `storage/blacklist/BlacklistDao.kt`)

```kotlin
package com.lirix.app.storage.blacklist

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BlacklistDao {
    @Query("SELECT * FROM blacklist_rules ORDER BY createdAt DESC")
    fun getAllRulesFlow(): Flow<List<BlacklistRuleEntity>>

    @Query("SELECT * FROM blacklist_rules ORDER BY createdAt DESC")
    suspend fun getAllRules(): List<BlacklistRuleEntity>

    @Query("SELECT * FROM blacklist_rules WHERE isEnabled = 1 ORDER BY createdAt DESC")
    suspend fun getActiveRules(): List<BlacklistRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: BlacklistRuleEntity)

    @Update
    suspend fun updateRule(rule: BlacklistRuleEntity)

    @Query("DELETE FROM blacklist_rules WHERE id = :ruleId")
    suspend fun deleteRule(ruleId: String)

    @Query("SELECT * FROM blacklist_rules WHERE id = :ruleId LIMIT 1")
    suspend fun getRuleById(ruleId: String): BlacklistRuleEntity?

    @Query("SELECT COUNT(*) FROM blacklist_rules")
    suspend fun countRules(): Int
}
```

---

## 4. Обновление `MusicDao.kt`
Добавить методы для пакетной очистки треков и сессий:
```kotlin
@Query("DELETE FROM music_tracks WHERE trackKey IN (:trackKeys)")
suspend fun deleteTracksByKeys(trackKeys: List<String>): Int

@Query("DELETE FROM music_listening_sessions WHERE trackKey IN (:trackKeys)")
suspend fun deleteSessionsByTrackKeys(trackKeys: List<String>): Int
```

---

## 5. Обновление `AppDatabase.kt`
1. Поднять `version = 11`.
2. Добавить `BlacklistRuleEntity::class` в список сущностей `@Database`.
3. Добавить абстрактный метод `abstract fun blacklistDao(): BlacklistDao`.
4. Реализовать `MIGRATION_10_11`:
```kotlin
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `blacklist_rules` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `ruleType` TEXT NOT NULL,
                `pattern` TEXT NOT NULL,
                `matchMode` TEXT NOT NULL,
                `isEnabled` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_blacklist_rules_isEnabled` ON `blacklist_rules` (`isEnabled`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_blacklist_rules_ruleType` ON `blacklist_rules` (`ruleType`)"
        )
    }
}
```
5. Зарегистрировать `MIGRATION_10_11` в `addMigrations` фабрики `AppDatabase`.

---

## 6. Критерии приемки (DoD)
- Схема базы данных компилируется без предупреждений Room.
- Все методы `BlacklistDao` и `MusicDao` валидны.
