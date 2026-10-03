# Спецификация: zone/template-bank

Архитектурная зона: `zone/template-bank` (`:data:storage`, `:pipeline:runtime`)  
Контракты: [architecture_phase3.md](file:///.sdd/architecture_phase3.md), [core-model/overview.md](file:///.sdd/specs/core-model/overview.md)  
Статус зоны: **FROZEN**

---

## Модуль: DynamicTemplateEntity  Зона: zone/template-bank  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Определяет Room-сущность динамического шаблона в SQLite базе данных (`dynamic_template`), а также вспомогательные таблицы статистики и версионирования банка, хранящие скомпилированные регулярные выражения RE2/J, биндинги слотов, константы и аудит жизненного цикла. НЕ выполняет компиляцию шаблонов в рантайм-объекты и не исполняет матчинг по сообщениям.

### Типы данных
```kotlin
package com.example.npc.data.storage.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dynamic_template",
    indices = [
        Index(value = ["sourceKey", "state"]),
        Index(value = ["canonicalHash"], unique = true)
    ]
)
data class DynamicTemplateEntity(
    @PrimaryKey
    val id: String,                         // UUIDv4
    val sourceKey: String,                 // e.g. "md.maib.maibank" or "com.apb.mobile"
    val tier: String,                      // 'OVERRIDE' | 'FALLBACK'
    val origin: String,                    // 'USER' | 'AUTO' | 'AUTO_REFINED'
    val state: String,                     // 'DRAFT' | 'ACTIVE' | 'SHADOW' | 'QUARANTINED' | 'DISABLED' | 'SUPERSEDED'
    val priority: Int,                     // 0..1000
    val pattern: String,                   // (?i)\Qrestituire\E...
    val bindingsJson: String,              // JSON-сериализованный маппинг групп на слоты
    val constantsJson: String,             // JSON-сериализованные семантические константы (opType, isRefund)
    val amountFormatJson: String,          // Разделители дробной/целой части
    val specVersion: Int,                  // Версия схемы (текущая: 1)
    val compilerVersion: Int,              // Версия компилятора (текущая: 1)
    val canonicalHash: String,             // SHA-256(sourceKey + ":" + pattern)
    val specificity: Float,                // Оценка специфичности (0.0 .. 1.0)
    val parentTemplateId: String?,         // Ссылка на предка при уточнении шаблона
    val sampleEventId: String?,            // Идентификатор образцового события
    val createdAt: Long,                   // Epoch millis
    val updatedAt: Long,                   // Epoch millis
    val stateReason: String?               // Причина перевода в текущий статус (например, карантин)
)

@Entity(
    tableName = "template_stats",
    foreignKeys = [
        ForeignKey(
            entity = DynamicTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TemplateStatsEntity(
    @PrimaryKey
    val templateId: String,
    val hits: Long = 0L,
    val parseFailures: Long = 0L,
    val userCorrections: Long = 0L,
    val shadowAgreements: Long = 0L,
    val shadowDisagreements: Long = 0L,
    val lastHitAt: Long? = null
)

@Entity(tableName = "template_bank_version")
data class TemplateBankVersionEntity(
    @PrimaryKey
    val version: Long,                     // Монотонный счетчик (1, 2, 3...)
    val parentVersion: Long?,
    val membershipHash: String,            // SHA-256 отсортированных активных templateId
    val createdAt: Long,
    val cause: String                      // Причина обновления (ADD, QUARANTINE, PROMOTE)
)

@Entity(
    tableName = "template_bank_membership",
    primaryKeys = ["version", "templateId"]
)
data class TemplateBankMembershipEntity(
    val version: Long,
    val templateId: String
)
```
- **Инварианты:**
  - `canonicalHash` уникален в таблице `dynamic_template` (предотвращает создание дубликатов паттернов).
  - Поле `tier` принимает строго значения `"OVERRIDE"` или `"FALLBACK"`.
  - Поле `origin` принимает строго `"USER"`, `"AUTO"`, `"AUTO_REFINED"`.
  - Каскадное удаление: при удалении записи шаблона его статистика в `template_stats` удаляется автоматически.

### Публичный API
Модуль предоставляет Room DAO интерфейсы:
```kotlin
package com.example.npc.data.storage.dao

import androidx.room.*
import com.example.npc.data.storage.entity.DynamicTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DynamicTemplateDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(template: DynamicTemplateEntity)

    @Update
    suspend fun update(template: DynamicTemplateEntity)

    @Query("SELECT * FROM dynamic_template WHERE id = :id")
    suspend fun getById(id: String): DynamicTemplateEntity?

    @Query("SELECT * FROM dynamic_template WHERE sourceKey = :sourceKey AND state = 'ACTIVE' ORDER BY priority DESC")
    suspend fun getActiveBySource(sourceKey: String): List<DynamicTemplateEntity>

    @Query("SELECT * FROM dynamic_template WHERE sourceKey = :sourceKey AND state = 'ACTIVE' ORDER BY priority DESC")
    fun observeActiveBySource(sourceKey: String): Flow<List<DynamicTemplateEntity>>

    @Query("UPDATE dynamic_template SET state = :newState, stateReason = :reason, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateState(id: String, newState: String, reason: String?, timestamp: Long)
}
```
- **Предусловия:** Валидные поля сущности, открытая база данных SQLCipher.
- **Постусловия:** Запись сохраняется в зашифрованной БД.
- **Ошибки:** `SQLiteConstraintException` при коллизии `canonicalHash`.
- **Побочные эффекты:** Дисковый ввод-вывод Room.
- **Граничные случаи:** Дубликат шаблона -> `SQLiteConstraintException`; несуществующий ID -> возврат `null`.
- **Примеры:**
  1. *Сохранение нового шаблона MAIB:* `insert(entity)` -> успешная вставка записи.
  2. *Повторная вставка того же паттерна:* `insert(entity)` -> `SQLiteConstraintException`.
  3. *Выборка активных шаблонов:* `getActiveBySource("md.maib.maibank")` -> список активных шаблонов, отсортированных по приоритету.

### Внутренние функции
- `fun calculateCanonicalHash(sourceKey: String, pattern: String): String`: вычисление SHA-256 хеша.

### Зависимости
- Room Runtime, AndroidX SQLite, SQLCipher.

### Вне скоупа
- Экспорт шаблонов в облако.

---

## Модуль: Room v4 MIGRATION_3_4  Зона: zone/template-bank  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Реализует безопасную, строго аддитивную SQL-миграцию базы данных Room со схемы версии 3 на версию 4 (`MIGRATION_3_4`), создающую таблицы динамического банка шаблонов и добавляющую колонки провенанса в таблицу `financial_transactions` со 100% сохранением целостности существующих данных. НЕ производит очистку данных, деструктивных изменений колонок и сброса пользовательских настроек.

### Типы данных
```kotlin
package com.example.npc.data.storage.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Выполняются DDL-операции Фазы 3
    }
}
```

### Публичный API

#### `MIGRATION_3_4.migrate`
```kotlin
override fun migrate(db: SupportSQLiteDatabase)
```
- **Предусловия:** Версия БД равна 3. База данных успешно расшифрована мастер-ключом SQLCipher.
- **Постусловия:** Версия БД повышается до 4. Созданы 4 новые таблицы, добавлены индексы, таблица `financial_transactions` дополнена 7 новыми колонками со значениями по умолчанию.
- **Пошаговое поведение:**
  1. Выполнить `db.execSQL` для создания таблицы `dynamic_template`:
     ```sql
     CREATE TABLE IF NOT EXISTS `dynamic_template` (
         `id` TEXT NOT NULL,
         `sourceKey` TEXT NOT NULL,
         `tier` TEXT NOT NULL,
         `origin` TEXT NOT NULL,
         `state` TEXT NOT NULL,
         `priority` INTEGER NOT NULL,
         `pattern` TEXT NOT NULL,
         `bindingsJson` TEXT NOT NULL,
         `constantsJson` TEXT NOT NULL,
         `amountFormatJson` TEXT NOT NULL,
         `specVersion` INTEGER NOT NULL,
         `compilerVersion` INTEGER NOT NULL,
         `canonicalHash` TEXT NOT NULL,
         `specificity` REAL NOT NULL,
         `parentTemplateId` TEXT,
         `sampleEventId` TEXT,
         `createdAt` INTEGER NOT NULL,
         `updatedAt` INTEGER NOT NULL,
         `stateReason` TEXT,
         PRIMARY KEY(`id`)
     );
     ```
  2. Создать индексы:
     ```sql
     CREATE INDEX IF NOT EXISTS `idx_dyn_tmpl_src_state` ON `dynamic_template` (`sourceKey`, `state`);
     CREATE UNIQUE INDEX IF NOT EXISTS `idx_dyn_tmpl_hash` ON `dynamic_template` (`canonicalHash`);
     ```
  3. Создать таблицу `template_stats`:
     ```sql
     CREATE TABLE IF NOT EXISTS `template_stats` (
         `templateId` TEXT NOT NULL,
         `hits` INTEGER NOT NULL DEFAULT 0,
         `parseFailures` INTEGER NOT NULL DEFAULT 0,
         `userCorrections` INTEGER NOT NULL DEFAULT 0,
         `shadowAgreements` INTEGER NOT NULL DEFAULT 0,
         `shadowDisagreements` INTEGER NOT NULL DEFAULT 0,
         `lastHitAt` INTEGER,
         PRIMARY KEY(`templateId`),
         FOREIGN KEY(`templateId`) REFERENCES `dynamic_template`(`id`) ON DELETE CASCADE
     );
     ```
  4. Создать таблицу `template_bank_version`:
     ```sql
     CREATE TABLE IF NOT EXISTS `template_bank_version` (
         `version` INTEGER NOT NULL,
         `parentVersion` INTEGER,
         `membershipHash` TEXT NOT NULL,
         `createdAt` INTEGER NOT NULL,
         `cause` TEXT NOT NULL,
         PRIMARY KEY(`version`)
     );
     ```
  5. Создать таблицу `template_bank_membership`:
     ```sql
     CREATE TABLE IF NOT EXISTS `template_bank_membership` (
         `version` INTEGER NOT NULL,
         `templateId` TEXT NOT NULL,
         PRIMARY KEY(`version`, `templateId`)
     );
     ```
  6. Расширить `financial_transactions` колонками провенанса:
     ```sql
     ALTER TABLE `financial_transactions` ADD COLUMN `extractorKind` TEXT NOT NULL DEFAULT 'STATIC';
     ALTER TABLE `financial_transactions` ADD COLUMN `templateId` TEXT DEFAULT NULL;
     ALTER TABLE `financial_transactions` ADD COLUMN `pipelineRevision` INTEGER NOT NULL DEFAULT 1;
     ALTER TABLE `financial_transactions` ADD COLUMN `bankVersion` INTEGER NOT NULL DEFAULT 0;
     ALTER TABLE `financial_transactions` ADD COLUMN `confidence` REAL NOT NULL DEFAULT 1.0;
     ALTER TABLE `financial_transactions` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'CONFIRMED_AUTO';
     ALTER TABLE `financial_transactions` ADD COLUMN `isRefund` INTEGER NOT NULL DEFAULT 0;
     ```
  7. Инициализировать нулевую версию банка:
     ```sql
     INSERT INTO `template_bank_version` (`version`, `parentVersion`, `membershipHash`, `createdAt`, `cause`)
     VALUES (0, NULL, 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', 0, 'INITIAL_EMPTY');
     ```
- **Ошибки:** `SQLException` при синтаксических ошибках или недостатке места на диске.
- **Побочные эффекты:** Модификация системного каталога SQLite.
- **Граничные случаи:** Таблицы уже частично созданы (используется `IF NOT EXISTS`); база с миллионом существующих транзакций (`ALTER TABLE ADD COLUMN DEFAULT` выполняется мгновенно за счет metadata-only изменения SQLite).
- **Примеры:**
  1. *Вход:* База v3 с 500 записями транзакций Фазы 1.  
     *Выход:* База v4, все старые транзакции получили `extractorKind = 'STATIC'`, `isRefund = 0`.
  2. *Вход:* Пустая база v3.  
     *Выход:* База v4 с нулевой записью в `template_bank_version`.
  3. *Вход:* Повторный запуск миграции на v4.  
     *Выход:* Room подавляет повторный вызов (проверка схемы Room OpenHelper).

### Внутренние функции
- `private fun executeDdlScripts(db: SupportSQLiteDatabase)`

### Зависимости
- `androidx.room.migration.Migration`, `androidx.sqlite.db.SupportSQLiteDatabase`.

### Вне скоупа
- Миграция других пользовательских таблиц (календарь, медиа).

---

## Модуль: TemplateBankManager  Зона: zone/template-bank  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Осуществляет транзакционное управление жизненным циклом динамических шаблонов в БД Room v4 (регистрация, перевод в `ACTIVE`, `SHADOW`, `QUARANTINED`, `DISABLED`), гарантируя монотонный инкремент `bankVersion` и потокобезопасное обновление членства шаблонов. НЕ выполняет сопоставление регулярных выражений на горячем пути рантайма.

### Типы данных
```kotlin
package com.example.npc.data.storage.bank

import com.example.npc.data.storage.entity.DynamicTemplateEntity
import kotlinx.coroutines.flow.Flow

enum class TemplateState {
    DRAFT,
    VALIDATED,
    ACTIVE,
    SHADOW,
    QUARANTINED,
    DISABLED,
    SUPERSEDED
}

data class DynamicTemplateDraft(
    val sourceKey: String,
    val tier: String,
    val origin: String,
    val pattern: String,
    val bindingsJson: String,
    val constantsJson: String,
    val amountFormatJson: String,
    val specificity: Float,
    val sampleEventId: String?
)

data class BankTransitionResult(
    val templateId: String,
    val previousState: TemplateState,
    val newState: TemplateState,
    val newBankVersion: Long
)
```
- **Инварианты:**
  - При любом изменении состава активных шаблонов `bankVersion` строго увеличивается на +1.
  - Шаблоны с происхождением `USER` при сохранении переводятся сразу в `ACTIVE` (ADR-307).
  - Авто-индуцированные шаблоны (`AUTO`) переводятся в `SHADOW` до набора 5 успешных проверок.

### Публичный API

#### `TemplateBankManager.registerDraft`
```kotlin
suspend fun registerDraft(draft: DynamicTemplateDraft): String
```
- **Предусловия:** `draft.pattern` валиден и прошел `TemplateLint`.
- **Постусловия:** Создает запись в таблице `dynamic_template` со статусом `DRAFT` или `VALIDATED`. Возвращает сгенерированный `templateId`.

#### `TemplateBankManager.activateTemplate`
```kotlin
suspend fun activateTemplate(templateId: String): BankTransitionResult
```
- **Предусловия:** Шаблон существует в статусе `VALIDATED` или `SHADOW`.
- **Постусловия:** В единой SQLite-транзакции:
  1. Статус шаблона переводится в `ACTIVE`.
  2. Вычисляется `newVersion = currentBankVersion + 1`.
  3. Собирается список всех активных `templateId`, вычисляется SHA-256 хеш состава (`membershipHash`).
  4. Записывается новая строка в `template_bank_version` и связанные строки в `template_bank_membership`.
  5. Возвращается `BankTransitionResult`.

#### `TemplateBankManager.quarantineTemplate`
```kotlin
suspend fun quarantineTemplate(templateId: String, reason: String): BankTransitionResult
```
- **Предусловия:** Шаблон существует в БД.
- **Постусловия:** Статус шаблона переводится в `QUARANTINED`, `stateReason = reason`, генерируется новая версия банка `bankVersion + 1`, шаблон исключается из активного множества.

#### Примеры:
1. *Вход:* Пользователь подтвердил шаблон MAIB TEMU в UI -> `activateTemplate(id)`.  
   *Выход:* `BankTransitionResult(id, DRAFT, ACTIVE, newBankVersion = 1)`.
2. *Вход:* Сбой исполнения шаблона в рантайме -> `quarantineTemplate(id, "CircuitBreaker tripped")`.  
   *Выход:* `BankTransitionResult(id, ACTIVE, QUARANTINED, newBankVersion = 2)`.
3. *Вход:* Попытка активации несуществующего шаблона -> выбрасывается `NoSuchElementException`.

### Внутренние функции
- `private suspend fun commitNewBankVersion(db: RoomDatabase, cause: String): Long`
- `private fun calculateMembershipHash(sortedActiveIds: List<String>): String`

### Зависимости
- `DynamicTemplateDao`, Room Database, SQLite-транзакции.

### Вне скоупа
- Отрисовка диалогов в UI.

---

## Модуль: RuntimeGeneration (AtomicReference CAS подмена)  Зона: zone/template-bank  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Обеспечивает атомарное хранение и lock-free горячую подмену связки скомпилированного конвейера (`CompiledPipeline`) и скомпилированного банка активных шаблонов (`CompiledTemplateBank`) через `AtomicReference` в рантайме конвейера за время $\le 0.1$ мс (ADR-304/ADR-365). НЕ производит компиляцию регулярных выражений на горячем пути (компиляция выполняется асинхронно до вызова CAS).

### Типы данных
```kotlin
package com.example.npc.pipeline.runtime

import com.example.npc.pipeline.compiler.CompiledPipeline
import java.util.concurrent.atomic.AtomicReference

data class CompiledTemplate(
    val id: String,
    val sourceKey: String,
    val priority: Int,
    val pattern: com.google.re2j.Pattern,
    val requiredLiterals: List<String>,
    val constants: Map<String, String>,
    val bindingsJson: String
)

data class CompiledTemplateBank(
    val bankVersion: Long,
    val overrideTemplatesBySource: Map<String, List<CompiledTemplate>>,
    val fallbackTemplatesBySource: Map<String, List<CompiledTemplate>>
)

data class RuntimeGeneration(
    val pipeline: CompiledPipeline,
    val bank: CompiledTemplateBank,
    val generationId: Long
)
```
- **Инварианты:**
  - `generationId` монотонно возрастает при каждой успешной подмене.
  - Согласованность: конвейер и банк шаблонов подменяются строго одновременно в одном неделимом CAS-цикле.
  - Иммутабельность: экземпляры `RuntimeGeneration` и `CompiledTemplateBank` полностью неизменяемы после инстанцирования.

### Публичный API

#### `RuntimeGenerationHolder.get`
```kotlin
fun get(): RuntimeGeneration
```
- **Предусловия:** Холдер инициализирован стартовым поколением при старте приложения.
- **Постусловия:** Возвращает актуальный `RuntimeGeneration` за $O(1)$ без блокировок.

#### `RuntimeGenerationHolder.updateBank`
```kotlin
fun updateBank(newBank: CompiledTemplateBank): RuntimeGeneration
```
- **Предусловия:** `newBank.bankVersion >= current.bank.bankVersion`.
- **Постусловия:** Атомарно обновляет ссылку через CAS-цикл (`compareAndSet`), сохраняя текущий `CompiledPipeline`, инкрементируя `generationId`. Возвращает новое поколение.
- **Пошаговое поведение:**
  1. В цикле `while (true)`:
     - Считать текущее поколение $cur = ref.get()$.
     - Сконструировать $next = RuntimeGeneration(cur.pipeline, newBank, cur.generationId + 1)$.
     - Если $ref.compareAndSet(cur, next)$ завершился успешно: вернуть $next$.
- **Ошибки:** Отсутствуют.
- **Побочные эффекты:** Модификация ссылки `AtomicReference`.
- **Граничные случаи:** Конкурентная подмена из двух потоков корректно разрешается через CAS.
- **Примеры:**
  1. *Вход:* Исходное поколение genId=1, bankVersion=0. Вызов `updateBank(bankVersion=1)`.  
     *Выход:* Новое поколение genId=2, bankVersion=1.
  2. *Вход:* Чтение на горячем пути уведомления: `holder.get()`.  
     *Выход:* Мгновенная ссылка без synchronized/блокировок за < 10 нс.
  3. *Вход:* Обновление конвейера с сохранением текущего банка: `updatePipeline(newPipeline)`.  
     *Выход:* Новое поколение genId=3 с новым конвейером и прежним банком.

### Внутренние функции
- `private val generationRef = AtomicReference<RuntimeGeneration>(initialGeneration)`

### Зависимости
- `CompiledPipeline`, `com.google.re2j.Pattern`, `java.util.concurrent.atomic.AtomicReference`.

### Вне скоупа
- Чтение файлов с диска.

---

## Модуль: TemplateHealthMonitor  Зона: zone/template-bank  Версия: v1  Статус: FROZEN

### Назначение: что делает и чего НЕ делает
Осуществляет непрерывный аудит качества и безопасности исполнения шаблонов в рантайме: накапливает статистику срабатываний, сбоев и правок пользователя, и выполняет автоматические переходы состояний (промоушен из `SHADOW` в `ACTIVE` после 5 бессбойных совпадений; немедленный карантин `QUARANTINED` при срабатывании `NodeCircuitBreaker`). НЕ блокирует поток обработки уведомлений (работает асинхронно через фоновый канал метрик).

### Типы данных
```kotlin
package com.example.npc.data.storage.bank

data class ExecutionFeedback(
    val templateId: String,
    val isSuccess: Boolean,
    val executionDurationNanos: Long,
    val isUserCorrection: Boolean,
    val isShadowAgreement: Boolean
)

data class TemplateHealthStatus(
    val templateId: String,
    val hits: Long,
    val failureRate: Float,
    val isPromotionReady: Boolean,
    val isQuarantineRecommended: Boolean
)
```
- **Инварианты:**
  - При превышении порога времени исполнения ($\ge 5$ мс) или необработанном исключении шаблон считается сбойным (`isSuccess = false`).
  - Промоушен из `SHADOW` в `ACTIVE` разрешен только при `shadowAgreements >= 5` и `failureRate == 0.0f`.
  - Карантин `QUARANTINED` инициируется при 2 сбоях подряд либо при `failureRate > 0.05f` при общем числе хитов $\ge 20$.

### Публичный API

#### `TemplateHealthMonitor.recordFeedback`
```kotlin
suspend fun recordFeedback(feedback: ExecutionFeedback)
```
- **Предусловия:** `feedback.templateId` валиден.
- **Постусловия:** Обновляет счетчики в `template_stats` в БД. При выполнении условий промоушена или карантина вызывает соответствующий метод `TemplateBankManager`.
- **Пошаговое поведение:**
  1. Найти запись статистики в БД.
  2. Инкрементировать счетчик `hits`, при успехе обновить `lastHitAt`.
  3. Если `isSuccess == false`: инкрементировать `parseFailures`.
  4. Если `isUserCorrection == true`: инкрементировать `userCorrections`.
  5. Если `isShadowAgreement == true`: инкрементировать `shadowAgreements`.
  6. Проверить триггеры безопасности:
     - Если `parseFailures >= 2` подряд -> вызвать `bankManager.quarantineTemplate(templateId, "Consecutive parse failures")`.
     - Если шаблон в статусе `SHADOW` и `shadowAgreements >= 5` без сбоев -> вызвать `bankManager.activateTemplate(templateId)`.
- **Ошибки:** Отсутствуют.
- **Побочные эффекты:** Запись в БД и возможное порождение новой версии банка.
- **Граничные случаи:** Высокая частота событий (батчинг обновлений через буфер); удаленный шаблон.
- **Примеры:**
  1. *Вход:* 5-е успешное совпадение теневого шаблона -> автоматический промоушен в `ACTIVE`.
  2. *Вход:* Ошибка разбора регулярочного захвата -> инкремент `parseFailures`.
  3. *Вход:* Срабатывание тайм-аута -> немедленный перевод в `QUARANTINED`.

### Внутренние функции
- `private fun evaluateHealthRules(stats: TemplateStatsEntity, state: TemplateState): HealthDecision`

### Зависимости
- `DynamicTemplateDao`, `TemplateBankManager`.

### Вне скоупа
- Отправка телеметрии на внешний сервер.
