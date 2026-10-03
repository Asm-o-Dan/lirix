## Задача STORE-P2-006: Реализовать PipelineRepositoryImpl

**Файлы:** 
- `core/storage/src/main/kotlin/com/example/npc/core/storage/repository/PipelineRepository.kt` (создать / обновить)
- `core/storage/src/main/kotlin/com/example/npc/core/storage/repository/PipelineRepositoryImpl.kt` (создать)

**Модуль:** `:core:storage`  
**Спека:** `.sdd/specs/pipeline-store/overview.md#7-контракт-репозитория-pipelinerepository`  
**Контракт:** `.sdd/contracts/pipeline-store__dsl.md#4-контракт-репозитория-конвейеров-pipelinerepository`  

---

### Сигнатуры (НЕ МЕНЯТЬ):

```kotlin
package com.example.npc.core.storage.repository

import com.example.npc.pipeline.dsl.PipelineDefinition
import kotlinx.coroutines.flow.Flow

interface PipelineRepository {

    /**
     * Сохраняет новую ревизию конвейера в БД.
     * Автоматически сериализует AST через [PipelineJsonCodec] и вычисляет SHA-256.
     *
     * @param definition Определение конвейера.
     * @param commitMessage Описание изменений.
     * @return Первичный ключ созданной записи ревизии.
     */
    suspend fun saveRevision(
        definition: PipelineDefinition,
        commitMessage: String? = null
    ): Long

    /**
     * Активирует указанную ревизию конвейера как рабочую.
     */
    suspend fun activateRevision(pipelineId: String, revisionNumber: Long)

    /**
     * Загружает текущую активную ревизию конвейера.
     */
    suspend fun getActiveDefinition(pipelineId: String): PipelineDefinition?

    /**
     * Наблюдает за изменением активной версии конвейера.
     */
    fun observeActiveDefinition(pipelineId: String): Flow<PipelineDefinition?>

    /**
     * Наблюдает за всеми активными конвейерами (для рантайма горячей подмены).
     */
    fun observeActivePipelines(): Flow<List<PipelineDefinition>>

    /**
     * Получает конкретную ревизию конвейера по номеру.
     */
    suspend fun getRevision(pipelineId: String, revisionNumber: Long): PipelineDefinition?

    /**
     * Откатывает конвейер на историческую ревизию.
     */
    suspend fun rollbackToRevision(pipelineId: String, revisionId: Long): Result<PipelineDefinition>

    /**
     * Включение / выключение конвейера.
     */
    suspend fun toggleEnabled(pipelineId: String, enabled: Boolean)

    /**
     * Очищает старые неактивные ревизии, оставляя не более [keepCount] последних версий.
     */
    suspend fun gcOldRevisions(pipelineId: String, keepCount: Int = 10)

    /**
     * Сохраняет рантайм-алерт о сбое или инциденте.
     */
    suspend fun recordAlert(
        pipelineId: String,
        nodeId: String?,
        level: String,
        code: String,
        message: String,
        payloadJson: String? = null
    )
}
```

```kotlin
package com.example.npc.core.storage.repository

import androidx.room.withTransaction
import com.example.npc.core.storage.AppDatabase
import com.example.npc.core.storage.entity.PipelineDefinitionEntity
import com.example.npc.core.storage.entity.PipelineRevisionEntity
import com.example.npc.core.storage.entity.RuntimeAlertEntity
import com.example.npc.pipeline.dsl.PipelineDefinition
import com.example.npc.pipeline.dsl.codec.PipelineJsonCodec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PipelineRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : PipelineRepository {
    // Реализация методов
}
```

---

### Поведение:

1. **`saveRevision(definition, commitMessage)`:**
   - Выполняется под защитой `mutationMutex.withLock` на `dispatcher`.
   - Сериализует `definition` в канонический JSON через `PipelineJsonCodec.encodeToString(definition)`.
   - Вычисляет SHA-256 хеш строки (64 символа hex).
   - Внутри `database.withTransaction`:
     * Получает максимальный существующий номер ревизии: `val currentMax = revisionDao.getLatestRevisionNumber(definition.id) ?: 0L`.
     * Вычисляет следующий номер: `val nextRevNumber = currentMax + 1L`.
     * Создает или обновляет запись `PipelineDefinitionEntity` (сохраняя метаданные, whitelist, имя).
     * Вставляет `PipelineRevisionEntity` с `revisionNumber = nextRevNumber`, JSON и хешем.
     * Возвращает сгенерированный первичный ключ ревизии `id`.

2. **`activateRevision(pipelineId, revisionNumber)`:**
   - Внутри транзакции находит ревизию по составному ключу `(pipelineId, revisionNumber)`.
   - Если ревизия не найдена, выбрасывает `IllegalArgumentException("Revision $revisionNumber for pipeline $pipelineId not found")`.
   - Обновляет `active_revision_id` в таблице `pipeline_definition`.
   - Запускает автоматическую сборку мусора `gcOldRevisions(pipelineId, keepCount = 10)`.

3. **`rollbackToRevision(pipelineId, revisionId)`:**
   - Находит ревизию по её первичному ключу `revisionId`.
   - Проверяет принадлежность конвейеру: `check(revision.pipelineId == pipelineId)`.
   - Обновляет `active_revision_id` в `pipeline_definition`.
   - Десериализует `PipelineJsonCodec.decodeFromString(revision.definitionJson)` и возвращает результат в `Result.success`.

4. **`observeActiveDefinition(pipelineId)` и `observeActivePipelines()`:**
   - Слушает `pipelineDao.observeActivePipelines()`.
   - Преобразует каждую сущность с активной ревизией в доменный объект `PipelineDefinition` через `PipelineJsonCodec.decodeFromString`.
   - Невалидные или поврежденные записи фильтруются с логированием в `RuntimeAlert`.

5. **`gcOldRevisions(pipelineId, keepCount)`:**
   - Вызывает `revisionDao.pruneOldRevisions(pipelineId, keepCount)`.
   - Удаляет только неактивные ревизии старше `keepCount`, на которые нет ссылок в `event.pipeline_revision_id`.

6. **`recordAlert(...)`:**
   - Создает `RuntimeAlertEntity` с текущим монотонным временем `System.currentTimeMillis()` и сохраняет через `runtimeAlertDao.insertAlert`.

---

### Ошибки:

- При передаче неизвестного `pipelineId` в `activateRevision` выбрасывается `IllegalArgumentException`.
- При повреждении JSON в базе данных операция десериализации выбрасывает `IllegalStateException` с подробным описанием ошибки декодирования.
- Ошибки записи в Room транзакции приводят к откату изменений.

---

### Граничные случаи:

- Параллельный вызов `saveRevision` из разных корутин сериализуется через `Mutex`, предотвращая гонку генерации номеров ревизий (`nextRevNumber`).
- Сохранение первого конвейера в пустую базу (`currentMax == null`) устанавливает `revisionNumber = 1`.
- Вызов `gcOldRevisions`, когда количество версий $\le 10$, не удаляет ничего.
- Попытка активировать чужую ревизию пресекается проверкой принадлежности.

---

### Запрещено:

- Выполнять сериализацию или парсинг JSON на главном потоке Android (`Dispatchers.Main`).
- Мутировать `active_revision_id` вне транзакции Room.
- Удалять ревизию, являющуюся текущей активной или привязанной к существующим событиям.
- Использовать нестандартные форматы JSON вместо детерминированного `PipelineJsonCodec`.

---

### Критерий приёмки:

- Интерфейс `PipelineRepository` и класс `PipelineRepositoryImpl` скомпилированы в `:core:storage`.
- Написаны комплексные Unit- и интеграционные тесты `PipelineRepositoryImplTest`:
  1. `saveRevision` инкрементирует ревизии (1 $\to$ 2 $\to$ 3).
  2. `activateRevision` корректно переключает рабочую версию.
  3. `rollbackToRevision` восстанавливает исходный `PipelineDefinition`.
  4. `gcOldRevisions` очищает старые ревизии сверх лимита, сохраняя активную.
  5. `recordAlert` персистит инцидент в таблицу `runtime_alert`.
