## Задача STORE-P2-002: Расширить EventEntity полем pipeline_revision_id

**Файл:** `core/storage/src/main/kotlin/com/example/npc/core/storage/entity/EventEntity.kt` (модифицировать)  
**Модуль:** `:core:storage`  
**Спека:** `.sdd/specs/pipeline-store/overview.md#34-модификация-таблицы-event-штамп-ревизии-на-событиях`  
**Контракт:** `.sdd/contracts/pipeline-store__dsl.md#21-дополнение-таблицы-event`  

---

### Сигнатуры (НЕ МЕНЯТЬ):

```kotlin
package com.example.npc.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "event",
    foreignKeys = [
        ForeignKey(
            entity = RawEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["raw_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["is_update_of"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = PipelineRevisionEntity::class,
            parentColumns = ["id"],
            childColumns = ["pipeline_revision_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["raw_id"], unique = false),
        Index(value = ["ts"], unique = false),
        Index(value = ["thread_key"], unique = false),
        Index(value = ["is_update_of"], unique = false),
        Index(value = ["category"], unique = false),
        Index(value = ["content_fingerprint"], unique = false),
        Index(value = ["pipeline_revision_id"], unique = false)
    ]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "raw_id")
    val rawId: Long,

    @ColumnInfo(name = "ts")
    val ts: Long,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "text")
    val text: String,

    @ColumnInfo(name = "normalized_text")
    val normalizedText: String,

    @ColumnInfo(name = "lang")
    val lang: String,

    @ColumnInfo(name = "thread_key")
    val threadKey: String?,

    @ColumnInfo(name = "is_update_of")
    val isUpdateOf: Long?,

    // Поля Фазы 1
    @ColumnInfo(name = "category", defaultValue = "'UNCLASSIFIED'")
    val category: String = "UNCLASSIFIED",

    @ColumnInfo(name = "confidence", defaultValue = "0.0")
    val confidence: Double = 0.0,

    @ColumnInfo(name = "engine_used", defaultValue = "'NONE'")
    val engineUsed: String = "NONE",

    @ColumnInfo(name = "is_user_corrected", defaultValue = "0")
    val isUserCorrected: Boolean = false,

    @ColumnInfo(name = "content_fingerprint", defaultValue = "NULL")
    val contentFingerprint: String? = null,

    // Новое поле Фазы 2 (Схема v3): связь с ревизией конвейера
    @ColumnInfo(name = "pipeline_revision_id", defaultValue = "NULL")
    val pipelineRevisionId: Long? = null
)
```

---

### Поведение:

1. **Штамп ревизии (Provenance Tracking):**
   - Каждое новое событие, обработанное рантаймом Фазы 2 (`:pipeline:runtime`), получает ссылку `pipeline_revision_id` на конкретную скомпилированную ревизию конвейера, выполнившую его классификацию.
   - Это позволяет на экране Timeline и в диагностике точно восстановить, каким набором правил было обработано уведомление.
2. **Безопасность при удалении ревизий (`ON DELETE SET NULL`):**
   - При очистке старых неактивных версий сборщиком мусора (Garbage Collector) или ручном удалении ревизий, связанные строки в таблице `event` не удаляются! Значение `pipeline_revision_id` автоматически выставляется в `NULL`.
3. **Индексация:**
   - Добавление индекса `Index(value = ["pipeline_revision_id"])` обеспечивает быстрый поиск всех событий, обработанных заданной ревизией, а также эффективную работу проверки ссылочной целостности при сборке мусора.

---

### Ошибки:

- Попытка вставить событие с несуществующим `pipeline_revision_id` при включенных `PRAGMA foreign_keys = ON` приводит к `SQLiteConstraintException`.
- Нарушение аннотации `@ColumnInfo(defaultValue = "NULL")` приведет к несовпадению генерируемой Room схемы с DDL в `MIGRATION_2_3.kt`.

---

### Граничные случаи:

- Для всех существующих исторических событий из 7-дневной базы догфудинга поле `pipeline_revision_id` принимает значение `NULL` (аддитивное изменение без перезаписи строк).
- При `pipelineRevisionId == null` событие считается обработанным монолитным legacy-оркестратором Фазы 1.1 либо системным пресетом до включения трекинга ревизий.

---

### Запрещено:

- Использовать `onDelete = ForeignKey.CASCADE` (каскадное удаление уничтожит исторические события догфудинга!).
- Удалять или менять порядок существующих полей `EventEntity` (нарушит бинарную совместимость и существующие тесты мапперов).
- Делать поле `pipelineRevisionId` примитивным `Long` без поддержки `null` (сломает миграцию старых данных).

---

### Критерий приёмки:

- Файл `EventEntity.kt` успешно компилируется в составе `:core:storage`.
- KSP генерирует обновленную схему таблицы `event` для Room v3.
- Существующие unit-тесты `EventDaoTest`, `EventMapperTest` и мапперы Timeline 2.0 компилируются и проходят без регрессий.
