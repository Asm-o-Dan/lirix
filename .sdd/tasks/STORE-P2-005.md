## Задача STORE-P2-005: Реализовать Room DAOs для конвейера

**Файлы:** 
- `core/storage/src/main/kotlin/com/example/npc/core/storage/dao/PipelineDao.kt`
- `core/storage/src/main/kotlin/com/example/npc/core/storage/dao/PipelineRevisionDao.kt`
- `core/storage/src/main/kotlin/com/example/npc/core/storage/dao/RuntimeAlertDao.kt`
(создать)
- `core/storage/src/main/kotlin/com/example/npc/core/storage/AppDatabase.kt` (модифицировать)

**Модуль:** `:core:storage`  
**Спека:** `.sdd/specs/pipeline-store/overview.md#6-dao-интерфейсы-и-контракты`  
**Контракт:** `.sdd/contracts/pipeline-store__dsl.md`  

---

### Сигнатуры (НЕ МЕНЯТЬ):

```kotlin
package com.example.npc.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.npc.core.storage.entity.PipelineDefinitionEntity
import com.example.npc.core.storage.model.PipelineWithRevision
import kotlinx.coroutines.flow.Flow

@Dao
interface PipelineDao {

    @Transaction
    @Query("""
        SELECT * FROM pipeline_definition
        WHERE enabled = 1 AND active_revision_id IS NOT NULL
        ORDER BY priority DESC, updated_at DESC
    """)
    fun observeActivePipelines(): Flow<List<PipelineWithRevision>>

    @Transaction
    @Query("SELECT * FROM pipeline_definition ORDER BY priority DESC, updated_at DESC")
    fun observeAllPipelines(): Flow<List<PipelineWithRevision>>

    @Transaction
    @Query("SELECT * FROM pipeline_definition WHERE id = :id LIMIT 1")
    fun getPipelineWithRevision(id: String): Flow<PipelineWithRevision?>

    @Query("SELECT * FROM pipeline_definition WHERE id = :id LIMIT 1")
    suspend fun getDefinitionById(id: String): PipelineDefinitionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPipeline(pipeline: PipelineDefinitionEntity)

    @Update
    suspend fun updatePipeline(pipeline: PipelineDefinitionEntity)

    @Query("UPDATE pipeline_definition SET enabled = :enabled, updated_at = :updatedAt WHERE id = :id")
    suspend fun toggleEnabled(id: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE pipeline_definition SET active_revision_id = :revisionId, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateActiveRevision(id: String, revisionId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM pipeline_definition WHERE id = :id")
    suspend fun deletePipeline(id: String)
}

@Dao
interface PipelineRevisionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRevision(revision: PipelineRevisionEntity): Long

    @Query("SELECT * FROM pipeline_revision WHERE id = :id LIMIT 1")
    suspend fun getRevisionById(id: Long): PipelineRevisionEntity?

    @Query("""
        SELECT * FROM pipeline_revision 
        WHERE pipeline_id = :pipelineId 
        ORDER BY revision_number DESC 
        LIMIT :limit
    """)
    fun observeRevisionsForPipeline(pipelineId: String, limit: Int = 10): Flow<List<PipelineRevisionEntity>>

    @Query("SELECT MAX(revision_number) FROM pipeline_revision WHERE pipeline_id = :pipelineId")
    suspend fun getLatestRevisionNumber(pipelineId: String): Long?

    @Query("SELECT COUNT(*) FROM pipeline_revision WHERE pipeline_id = :pipelineId")
    suspend fun getRevisionCount(pipelineId: String): Int

    @Query("""
        DELETE FROM pipeline_revision 
        WHERE pipeline_id = :pipelineId 
          AND id NOT IN (
              SELECT id FROM pipeline_revision 
              WHERE pipeline_id = :pipelineId 
              ORDER BY revision_number DESC 
              LIMIT :keepCount
          )
          AND id != (SELECT COALESCE(active_revision_id, -1) FROM pipeline_definition WHERE id = :pipelineId)
          AND id NOT IN (SELECT DISTINCT pipeline_revision_id FROM event WHERE pipeline_revision_id IS NOT NULL)
    """)
    suspend fun pruneOldRevisions(pipelineId: String, keepCount: Int = 10): Int
}

@Dao
interface RuntimeAlertDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: RuntimeAlertEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlerts(alerts: List<RuntimeAlertEntity>)

    @Query("""
        SELECT * FROM runtime_alert 
        WHERE is_dismissed = 0 
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    fun observeActiveAlerts(limit: Int = 50): Flow<List<RuntimeAlertEntity>>

    @Query("SELECT COUNT(*) FROM runtime_alert WHERE is_dismissed = 0")
    fun observeActiveAlertsCount(): Flow<Int>

    @Query("UPDATE runtime_alert SET is_dismissed = 1 WHERE id = :id")
    suspend fun dismissAlert(id: Long)

    @Query("UPDATE runtime_alert SET is_dismissed = 1 WHERE pipeline_id = :pipelineId AND is_dismissed = 0")
    suspend fun dismissAllForPipeline(pipelineId: String)

    @Query("DELETE FROM runtime_alert WHERE is_dismissed = 1 AND timestamp < :beforeTimestamp")
    suspend fun purgeDismissedAlerts(beforeTimestamp: Long): Int
}
```

```kotlin
// Модификация AppDatabase.kt
@Database(
    entities = [
        RawEventEntity::class,
        EventEntity::class,
        FinancialTransactionEntity::class,
        UserPrototypeEntity::class,
        SourceHealthEntity::class,
        PipelineDefinitionEntity::class,
        PipelineRevisionEntity::class,
        RuntimeAlertEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rawEventDao(): RawEventDao
    abstract fun eventDao(): EventDao
    abstract fun financialTransactionDao(): FinancialTransactionDao
    abstract fun userPrototypeDao(): UserPrototypeDao
    abstract fun sourceHealthDao(): SourceHealthDao
    abstract fun pipelineDao(): PipelineDao
    abstract fun pipelineRevisionDao(): PipelineRevisionDao
    abstract fun runtimeAlertDao(): RuntimeAlertDao
}
```

---

### Поведение:

1. **`PipelineDao`:**
   - `observeActivePipelines()`: возвращает реактивный `Flow` только включенных (`enabled = 1`) конвейеров с валидной активной ревизией (`active_revision_id IS NOT NULL`), отсортированных по убыванию приоритета.
   - Запросы используют аннотацию `@Transaction` для консистентного считывания составного объекта `PipelineWithRevision`.
2. **`PipelineRevisionDao`:**
   - `insertRevision()`: сохраняет новую иммутабельную версию конвейера и возвращает её сгенерированный ID.
   - `pruneOldRevisions()`: выполняет сборку мусора, безопасно удаляя ревизии сверх `keepCount`.
     * **Критический инвариант целостности:** ревизия гарантированно НЕ удаляется, если она является текущей активной (`id == active_revision_id`) ИЛИ если на неё ссылается хотя бы одна строка в таблице `event` (`pipeline_revision_id`).
3. **`RuntimeAlertDao`:**
   - Позволяет реактивно отслеживать поток неразрешенных инцидентов (`is_dismissed = 0`).
   - Метод `dismissAlert()` квитирует алерт без его физического удаления из базы.
   - Метод `purgeDismissedAlerts()` удаляет устаревшие квитированные алерты старше заданного `timestamp`.

---

### Ошибки:

- При нарушении ограничения уникальности `(pipeline_id, revision_number)` метод `insertRevision` выбрасывает `SQLiteConstraintException`.
- При попытке вставить `PipelineDefinitionEntity` с дублирующимся `id` через `insertPipeline` выбрасывается `SQLiteConstraintException` (стратегия `ABORT`).

---

### Граничные случаи:

- Вызов `pruneOldRevisions` при количестве ревизий $\le keepCount$ не удаляет ни одной записи (возвращает 0).
- При отсутствии активных конвейеров `observeActivePipelines()` эмитит пустой список `emptyList()`.
- Ревизии, на которые ссылаются исторические события в `event`, защищены от удаления даже при `keepCount = 0`.

---

### Запрещено:

- Выполнять запросы к DAO в главном потоке Android (`Dispatchers.Main`).
- Использовать строковую конкатенацию вместо именованных SQL-параметров (`:id`, `:pipelineId`).
- Удалять ревизии простым `DELETE FROM pipeline_revision WHERE id = :id` в обход правил защищенной очистки.

---

### Критерий приёмки:

- Все DAO интерфейсы и обновленный `AppDatabase` скомпилированы в `:core:storage`.
- KSP генерирует реализации `PipelineDao_Impl`, `PipelineRevisionDao_Impl`, `RuntimeAlertDao_Impl`.
- Unit-тесты на in-memory базе данных Room:
  1. Вставка и реактивное наблюдение `PipelineWithRevision`.
  2. Проверка работы `pruneOldRevisions` с подтверждением сохранения активной версии и версий, привязанных к `event`.
  3. Квитирование и очистка алертов.
