# Архитектурный проект: Движок Черного Списка и Каскадная Очистка Медиатеки (Blacklist Filters & Auto-Purge)

**TASK_ID:** ARCH-BLK-01  
**Версия:** 1.0 (FROZEN)  
**Дата:** 2026-10-14  
**Статус:** APPROVED (Gate 1 Passed)  
**Контекст решения:** ADR-016 (.sdd/decisions.md), .sdd/intake/BLACKLIST_FILTER_ENGINE.md  

---

## 1. Архитектурный контекст и цели

### 1.1 Проблематика
В текущей реализации Lirix автоматически инжестирует любые аудиопотоки через `MediaSessionCollector` и `NotificationListener`, если у них есть распознаваемые метаданные (`title` и `artist`). Это приводит к замусориванию базы данных `music_tracks`, хроники прослушиваний `music_listening_sessions` и искажению годовой аналитики Wrapped следующими категориями контента:
1. **Служебные и голосовые аудиосообщения мессенджеров** (например, пакет `org.telegram.messenger`, треки вида «Голосовое сообщение», «Voice note»).
2. **Нерелевантные провайдеры** (навигаторы, подкаст-клиенты, аудиокниги, сторонние утилиты).
3. **Нежелательные треки и артисты** (рекламные вставки, интро, джинглы, конкретные авторы, которых пользователь не хочет видеть в своей медиатеке).

### 1.2 Цели архитектуры
1. **Детерминированная рантайм-фильтрация (Ingress Guard)**: Мгновенное пресечение сохранения нежелательных аудиозаписей в базу данных до выполнения любых дисковых операций и сетевых запросов текстов песен.
2. **Немедленная каскадная очистка (Purge Engine)**: При создании нового запрета или активации существующего система сканирует накопленную медиатеку и атомарно удаляет все треки и связанные с ними сессии прослушивания, возвращая пользователю отчет.
3. **Интерактивный UI-конструктор**: Специализированный экран настроек `BlacklistSettingsScreen` с пресетами популярных приложений, выбором режимов сопоставления и превью количества удаляемых треков в реальном времени.

### 1.3 Нефункциональные требования (NFR)
- **Zero-Latency Ingress**: Проверка трека по активному набору правил должна занимать $< 2\text{ms}$ в фоновом потоке, не вызывая джиттера в UI и задержек в смене треков.
- **In-Memory Rule Caching**: Активные правила кэшируются в памяти в скомпилированном виде (прекомпилированные `Regex`), избегая повторных выборок из SQLite при каждом `playbackState` событии.
- **Data Isolation**: Сущности черного списка вынесены в отдельную таблицу `blacklist_rules`, не нарушая целостность существующих таблиц `music_tracks` и `music_listening_sessions`.
- **Atomic Cascade Integrity**: Удаление данных из `music_tracks`, `music_listening_sessions` и `lyrics_cache` выполняется строго в рамках единой ACID-транзакции Room (`@Transaction`).
- **Fail-Safe Regex**: Некорректные или зависающие регулярные выражения изолируются (`try-catch` с таймаутом/валидацией при создании) и не приводят к крашу приложения.
- **Theme Consistency**: 100% поддержка 4 динамических палитр Lirix через `LirixThemeTokens.colors`.

---

## 2. Модель данных (Storage Layer)

### 2.1 Перечисления (Enums)

```kotlin
package com.lirix.app.storage.blacklist

/**
 * Тип поля медиа-метаданных для применения правила фильтрации.
 */
enum class BlacklistRuleType {
    TITLE,         // Сопоставление по названию композиции
    ARTIST,        // Сопоставление по имени исполнителя
    PACKAGE_NAME   // Сопоставление по Android application package name
}

/**
 * Режим сопоставления строкового паттерна.
 */
enum class BlacklistMatchMode {
    CONTAINS_IGNORE_CASE, // Вхождение подстроки без учета регистра (по умолчанию)
    EXACT,                // Полное точное совпадение
    REGEX                 // Регулярное выражение (Case-Insensitive)
}
```

### 2.2 Сущность Room: `BlacklistRuleEntity`

Таблица: `blacklist_rules`

| Колонка | SQLite Тип | Nullable | Описание |
|---|---|---|---|
| `id` | `TEXT` | NOT NULL (PK) | Уникальный UUID правила (`UUID.randomUUID().toString()`) |
| `ruleType` | `TEXT` | NOT NULL | Тип критерия: `TITLE`, `ARTIST`, `PACKAGE_NAME` |
| `pattern` | `TEXT` | NOT NULL | Текстовый шаблон или имя пакета |
| `matchMode` | `TEXT` | NOT NULL | Режим: `CONTAINS_IGNORE_CASE`, `EXACT`, `REGEX` |
| `isEnabled` | `INTEGER` | NOT NULL | Флаг активности правила (1 — активно, 0 — отключено) |
| `createdAt` | `INTEGER` | NOT NULL | Timestamp создания в миллисекундах |

```kotlin
package com.lirix.app.storage.blacklist

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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

### 2.3 DAO интерфейс: `BlacklistDao`

```kotlin
package com.lirix.app.storage.blacklist

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BlacklistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: BlacklistRuleEntity)

    @Update
    suspend fun updateRule(rule: BlacklistRuleEntity)

    @Delete
    suspend fun deleteRule(rule: BlacklistRuleEntity)

    @Query("DELETE FROM blacklist_rules WHERE id = :id")
    suspend fun deleteRuleById(id: String)

    @Query("SELECT * FROM blacklist_rules ORDER BY createdAt DESC")
    fun observeAllRules(): Flow<List<BlacklistRuleEntity>>

    @Query("SELECT * FROM blacklist_rules ORDER BY createdAt DESC")
    suspend fun getAllRules(): List<BlacklistRuleEntity>

    @Query("SELECT * FROM blacklist_rules WHERE isEnabled = 1 ORDER BY createdAt DESC")
    fun observeActiveRules(): Flow<List<BlacklistRuleEntity>>

    @Query("SELECT * FROM blacklist_rules WHERE isEnabled = 1")
    suspend fun getActiveRules(): List<BlacklistRuleEntity>

    @Query("SELECT COUNT(*) FROM blacklist_rules")
    suspend fun getRuleCount(): Int

    @Query("UPDATE blacklist_rules SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setRuleEnabled(id: String, isEnabled: Boolean)
}
```

### 2.4 Расширение TypeConverters
В `com.lirix.app.storage.Converters`:
- Конвертеры для `BlacklistRuleType` (`fromBlacklistRuleType`, `toBlacklistRuleType`).
- Конвертеры для `BlacklistMatchMode` (`fromBlacklistMatchMode`, `toBlacklistMatchMode`).

### 2.5 Миграция Room: `v10 -> v11` (`MIGRATION_10_11`)

В `AppDatabase.kt`:
1. Увеличение версии: `version = 11`.
2. Добавление `BlacklistRuleEntity::class` в аннотацию `@Database(entities = [...])`.
3. Регистрация абстрактного метода: `abstract fun blacklistDao(): BlacklistDao`.
4. Реализация детерминированной миграции:

```kotlin
@JvmField
val MIGRATION_10_11: Migration = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `blacklist_rules` (
                `id` TEXT NOT NULL,
                `ruleType` TEXT NOT NULL,
                `pattern` TEXT NOT NULL,
                `matchMode` TEXT NOT NULL,
                `isEnabled` INTEGER NOT NULL DEFAULT 1,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_blacklist_rules_isEnabled` ON `blacklist_rules` (`isEnabled`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_blacklist_rules_ruleType` ON `blacklist_rules` (`ruleType`)")
    }
}
```
5. Включение `MIGRATION_10_11` в `addMigrations(...)` построителя `Room.databaseBuilder`.

---

## 3. Алгоритм немедленной каскадной очистки (Purge Engine)

### 3.1 Архитектура `BlacklistEngine`
`BlacklistEngine` является центральным сервисным координатором зоны `media-core`. Он инкапсулирует:
1. Матчинг метаданных трека против правил (`matchesRule(rule, title, artist, sourcePackage)`).
2. Превью количества удаляемых треков без выполнения изменений (`previewPurgeCount(rule)`).
3. Атомарную процедуру очистки (`purgeMatchingTracks(rule)`).

```
[Пользователь] 
      │ (ввод паттерна в диалоге)
      ▼
[BlacklistEngine.previewPurgeCount(rule)] ────▶ [SELECT trackKey, title, artist, sourcePackage FROM music_tracks]
      │                                                │
      ▼                                                ▼
UI показывает: "Будет удалено: 14 треков"         Матчинг в памяти
      │
      ▼ (нажатие "Сохранить и очистить")
[BlacklistDao.insertRule(rule)]
      │
      ▼
[BlacklistEngine.purgeMatchingTracks(rule)]
      │
      ├── Room @Transaction
      │     ├── 1. Поиск целевых trackKeys
      │     ├── 2. DELETE FROM music_listening_sessions WHERE trackKey IN (:keys)
      │     ├── 3. DELETE FROM lyrics_cache WHERE trackKey IN (:keys)
      │     └── 4. DELETE FROM music_tracks WHERE trackKey IN (:keys)
      │
      ▼
[BlacklistPurgeReport(deletedTracksCount = 14, deletedSessionsCount = 38)]
```

### 3.2 Алгоритм сопоставления (Matching Logic)

```kotlin
fun matchesRule(
    rule: BlacklistRuleEntity,
    title: String,
    artist: String,
    sourcePackage: String
): Boolean {
    if (!rule.isEnabled) return false
    val targetValue = when (rule.ruleType) {
        BlacklistRuleType.TITLE -> title.trim()
        BlacklistRuleType.ARTIST -> artist.trim()
        BlacklistRuleType.PACKAGE_NAME -> sourcePackage.trim()
    }
    val pattern = rule.pattern.trim()
    if (pattern.isEmpty()) return false

    return when (rule.matchMode) {
        BlacklistMatchMode.CONTAINS_IGNORE_CASE -> {
            targetValue.contains(pattern, ignoreCase = true)
        }
        BlacklistMatchMode.EXACT -> {
            targetValue.equals(pattern, ignoreCase = true)
        }
        BlacklistMatchMode.REGEX -> {
            try {
                Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(targetValue)
            } catch (e: Exception) {
                false // Защита от синтаксических ошибок в регулярках
            }
        }
    }
}
```

### 3.3 Транзакционное каскадное удаление (Room Transaction)

В `MusicDao` добавляются пакетные методы удаления по списку ключей:

```kotlin
@Query("DELETE FROM music_tracks WHERE trackKey IN (:trackKeys)")
suspend fun deleteTracksByKeys(trackKeys: List<String>): Int

@Query("DELETE FROM music_listening_sessions WHERE trackKey IN (:trackKeys)")
suspend fun deleteSessionsByTrackKeys(trackKeys: List<String>): Int

@Query("DELETE FROM lyrics_cache WHERE trackKey IN (:trackKeys)")
suspend fun deleteLyricsCacheByTrackKeys(trackKeys: List<String>): Int

@Query("SELECT trackKey, title, artist, album, sourcePackage FROM music_tracks")
suspend fun getAllTrackSnapshots(): List<TrackSnapshot>
```

Метод выполнения очистки в `BlacklistEngine`:

```kotlin
data class BlacklistPurgeReport(
    val ruleId: String,
    val deletedTracksCount: Int,
    val deletedSessionsCount: Int
)

suspend fun purgeMatchingTracks(rule: BlacklistRuleEntity): BlacklistPurgeReport = withContext(Dispatchers.IO) {
    val allTracks = musicDao.getAllTrackSnapshots()
    val matchingKeys = allTracks.filter { snapshot ->
        matchesRule(rule, snapshot.title, snapshot.artist, snapshot.sourcePackage)
    }.map { it.trackKey }

    if (matchingKeys.isEmpty()) {
        return@withContext BlacklistPurgeReport(rule.id, 0, 0)
    }

    // Выполняем каскадное удаление пакетами по 500 ключей (защита от лимита переменных SQLite)
    var totalDeletedSessions = 0
    var totalDeletedTracks = 0

    appDatabase.withTransaction {
        matchingKeys.chunked(500).forEach { chunk ->
            totalDeletedSessions += musicDao.deleteSessionsByTrackKeys(chunk)
            musicDao.deleteLyricsCacheByTrackKeys(chunk)
            totalDeletedTracks += musicDao.deleteTracksByKeys(chunk)
        }
    }

    Timber.i("Blacklist Purge executed for rule %s: %d tracks, %d sessions deleted", 
        rule.id, totalDeletedTracks, totalDeletedSessions)

    BlacklistPurgeReport(
        ruleId = rule.id,
        deletedTracksCount = totalDeletedTracks,
        deletedSessionsCount = totalDeletedSessions
    )
}
```

---

## 4. Точки перехвата и фильтрация в рантайме (Ingress & Core Guard)

### 4.1 In-Memory кэш активных правил: `BlacklistFilterManager`
Чтобы не выполнять дисковые запросы к SQLite при каждом получении сигнала воспроизведения, создается singleton/manager `BlacklistFilterManager`:
- Наблюдает за `blacklistDao.observeActiveRules()`.
- Держит в памяти актуальный список `CopyOnWriteArrayList<CompiledBlacklistRule>`.
- Предоставляет метод мгновенной проверки `fun isTrackBlocked(title: String, artist: String, packageName: String): Boolean`.

```kotlin
class BlacklistFilterManager(private val blacklistDao: BlacklistDao, scope: CoroutineScope) {
    private val compiledRules = java.util.concurrent.CopyOnWriteArrayList<CompiledBlacklistRule>()

    init {
        scope.launch(Dispatchers.Default) {
            blacklistDao.observeActiveRules().collect { rules ->
                val compiled = rules.map { rule ->
                    CompiledBlacklistRule(
                        original = rule,
                        regex = if (rule.matchMode == BlacklistMatchMode.REGEX) {
                            runCatching { Regex(rule.pattern, RegexOption.IGNORE_CASE) }.getOrNull()
                        } else null
                    )
                }
                compiledRules.clear()
                compiledRules.addAll(compiled)
            }
        }
    }

    fun isTrackBlocked(title: String, artist: String, packageName: String): Boolean {
        if (compiledRules.isEmpty()) return false
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        val cleanPkg = packageName.trim()

        return compiledRules.any { rule ->
            rule.matches(cleanTitle, cleanArtist, cleanPkg)
        }
    }
}
```

### 4.2 Точка перехвата 1: `MusicFeatureEngine.recordPlaybackSignal`
В `MusicFeatureEngine` внедряется `BlacklistFilterManager` (или прямая проверка через DAO):

```kotlin
suspend fun recordPlaybackSignal(
    title: String,
    artist: String,
    album: String = "",
    sourcePackage: String,
    playbackState: String = "PLAYING",
    durationDeltaMs: Long = 0L,
    timestamp: Long = System.currentTimeMillis(),
    albumArtUri: String? = null
): TrackEntity? {
    if (title.isBlank()) return null

    // ── GUARD: Проверка по черному списку ──
    if (blacklistFilterManager.isTrackBlocked(title, artist, sourcePackage)) {
        Timber.tag(TAG).d("Playback signal BLOCKED by blacklist: [%s] %s - %s", sourcePackage, artist, title)
        return null // Возврат null прерывает сохранение в Room и предзагрузку текстов
    }

    // Стандартная обработка сессий и сохранение в music_tracks...
}
```

### 4.3 Точка перехвата 2: `MediaSessionCollector.persistMediaEvent`
В `MediaSessionCollector.kt`:
- Если `musicEngine.recordPlaybackSignal(...)` возвращает `null` из-за блокировки:
  - `prefetchLyricsAsync` **НЕ** вызывается.
  - Уведомление в шторке (`MusicLyricsNotificationManager.showLyricsPrompt`) **НЕ** показывается для заблокированных треков.

### 4.4 Поведение UI плеера (`NowPlayingScreen`) для заблокированных треков
Трек, играющий в стороннем плеере, по-прежнему транслируется через `_livePlaybackFlow` для синхронизации обложки и текущей позиции:
- В заголовке трека или над ним отображается бейдж:  
  `[ 🚫 ЗАБЛОКИРОВАНО ФИЛЬТРОМ ]` (стилизованный в `AppColors.ExpenseRed` с прозрачностью 0.15f на фоне и красной рамкой).
- Кнопка «В избранное» (Сердечко) деактивируется (disabled) с визуальным индикатором блокировки.
- В футере карточки отображается информационная подсказка:  
  *«Этот трек исключен из истории, аналитики и не сохраняется в медиатеку согласно вашим правилам»*.
- Карточки караоке и текста работают в режиме реального времени, если текст был запрошен вручную, но не сохраняются в постоянный кэш медиатеки.

---

## 5. Архитектура UI экрана `BlacklistSettingsScreen`

### 5.1 Точки входа и навигация

```
[NowPlayingScreen]                     [LibraryScreen]
       │                                      │
       ▼ (тап по ⋮)                           ▼ (кнопка [🚫] в тулбаре)
[NowPlayingOverflowMenu]                      │
       │                                      │
       ▼ ("Черный список и фильтры")          │
       └──────────────────┬───────────────────┘
                          │
                          ▼
            [BlacklistSettingsScreen]
```

1. **Из NowPlayingScreen (Overflow Menu ⋮)**:
   - Новый пункт меню: `Черный список и фильтры` (иконка `Icons.Default.Block`).
2. **Из LibraryScreen (Тулбар медиатеки)**:
   - Новая иконка-кнопка в шапке рядом с `Palette` (темы) и `FileDownload` (импорт правил): `Icons.Default.FilterList` или `Icons.Default.Block` с тултипом "Черный список".
3. **Обратная навигация**:
   - Кнопка `[←]` в TopAppBar возвращает на предыдущий экран с сохранением стейта.

### 5.2 Архитектура экрана `BlacklistSettingsScreen`

Компонент: `com.lirix.app.ui.BlacklistSettingsScreen.kt`

#### Элементы экрана:
1. **TopAppBar**:
   - Заголовок: «Черный список».
   - Счетчик: «Всего правил: N» (в стилизованном `Surface` бейдже).
2. **Empty State**:
   - Если правил нет — анимированная карточка со щитом `Icons.Default.Shield`, пояснением и кнопкой «Добавить первое правило».
3. **Список правил (`LazyColumn`)**:
   - Карточка каждого правила содержит:
     - Тип критерия (`TITLE` -> 🎵 Название, `ARTIST` -> 👤 Исполнитель, `PACKAGE_NAME` -> 📦 Приложение).
     - Текст шаблона (моноширинный шрифт, жирное начертание).
     - Бейдж режима (`Подстрока`, `Точное`, `RegEx`).
     - Дата создания.
     - Переключатель активности (`Switch`): при включении заново опрашивается `PurgeEngine` с диалогом подтверждения.
     - Кнопка удаления (`IconButton` с `Icons.Default.Delete`).
4. **Floating Action Button (FAB) / Нижняя панель**:
   - Кнопка «+ Добавить запрет» (акцентный градиент `accentPrimary` -> `accentSecondary`).

### 5.3 Конструктор правил (`BlacklistRuleDialog` / BottomSheet)

Интерактивный диалог создания правила:
1. **Селектор типа правила (Segmented Buttons)**:
   - `[ 🎵 Название ]` | `[ 👤 Артист ]` | `[ 📦 Приложение ]`.
2. **Блок быстрых пресетов (Quick App Chips)**:
   - Отображается, если выбран тип `PACKAGE_NAME`:
     - 🟢 **Spotify** (`com.spotify.music`)
     - 🔵 **VK Музыка** (`com.vkontakte.android`)
     - 🔷 **Telegram** (`org.telegram.messenger`)
     - 🟡 **Яндекс Музыка** (`ru.yandex.music`)
     - 🔴 **YouTube** (`com.google.android.youtube`)
     - 🔴 **YouTube Music** (`com.google.android.apps.youtube.music`)
   - Тап по чипу мгновенно подставляет имя пакета в поле ввода.
3. **Поле ввода шаблона (`OutlinedTextField`)**:
   - Подсказка адаптируется под выбранный тип:
     - Для `TITLE`: *«Например: Реклама, Voice note, Intro»*
     - Для `ARTIST`: *«Например: Нежелательный исполнитель»*
     - Для `PACKAGE_NAME`: *«Например: org.telegram.messenger»*
4. **Селектор режима совпадения**:
   - Радио-кнопки или выпадающий список:
     - `Содержит подстроку (без регистра)`
     - `Точное совпадение`
     - `Регулярное выражение (RegEx)`
5. **Динамический счетчик превью очистки (Live Purge Preview)**:
   - В процессе набора текста запускается дебаунс-запрос (300мс) к `BlacklistEngine.previewPurgeCount(draftRule)`.
   - Показывается информационный баннер:
     - Если совпадений 0: `✅ В медиатеке нет совпадающих треков`.
     - Если совпадений > 0: `⚠️ Внимание: 12 треков и их сессии будут удалены из медиатеки!`.
6. **Кнопки управления**:
   - `[ Отмена ]`
   - `[ Сохранить и очистить ]` (активна только если поле шаблона не пустое).

### 5.4 Поддержка 4 тем оформления (`LirixThemeTokens.colors`)

Все компоненты экрана строго используют дизайн-токены:
- Фон экрана: `LirixThemeTokens.colors.appBackground`
- Карточки правил: `LirixThemeTokens.colors.surfaceCard` с границей `LirixThemeTokens.colors.borderSubtle`
- Акцентные бейджи типов: `LirixThemeTokens.colors.accentPrimary` и `accentSecondary`
- Текст: `LirixThemeTokens.colors.textPrimary`, `textSecondary`, `textMuted`
- Опасные действия (удаление, баннер очистки): `AppColors.ExpenseRed`
- Скругления: `LirixThemeTokens.colors.cardCornerRadius` и `pillCornerRadius`

---

## 6. Инструкции и границы для Спецификаторов

### 6.1 Зона `SPEC-BLK-01`: Storage, Data Engine & Ingress Guard

**Ответственность:**
- Создание файлов в `com.lirix.app.storage.blacklist.*`:
  - `BlacklistRuleEntity.kt`
  - `BlacklistRuleType.kt`
  - `BlacklistMatchMode.kt`
  - `BlacklistDao.kt`
- Обновление `AppDatabase.kt`:
  - Инкремент версии схемы до 11 (`version = 11`).
  - Реализация `MIGRATION_10_11`.
  - Подключение `BlacklistRuleEntity` и `blacklistDao()`.
- Обновление `MusicDao.kt`:
  - Добавление `deleteTracksByKeys`, `deleteSessionsByTrackKeys`, `deleteLyricsCacheByTrackKeys`, `getAllTrackSnapshots`.
- Реализация `BlacklistEngine.kt`:
  - Метод `matchesRule(...)`.
  - Метод `previewPurgeCount(rule)`.
  - Метод `purgeMatchingTracks(rule)`.
- Реализация `BlacklistFilterManager.kt` и интеграция в `MusicFeatureEngine.kt` (`recordPlaybackSignal`).
- Unit-тесты:
  - `BlacklistMatchingTest` (проверка всех комбинаций `RuleType` x `MatchMode` x `Regex`).
  - `BlacklistMigrationTest` (проверка `MIGRATION_10_11` на тестовой БД).
  - `BlacklistPurgeCascadeTest` (проверка атомарного удаления треков и сессий).

### 6.2 Зона `SPEC-BLK-02`: UI Settings Screen & Navigation Wiring

**Ответственность:**
- Создание `BlacklistSettingsScreen.kt`:
  - Главный экран со списком правил, свитчами активности и удалением.
  - Адаптивный TopAppBar со счетчиком правил.
- Создание `BlacklistRuleDialog.kt` (или BottomSheet):
  - Конструктор правила с селектором типа, пресетами пакетов, выбором matchMode.
  - Реактивное превью удаляемых треков через `previewPurgeCount`.
- Доработка `NowPlayingOverflowMenu.kt`:
  - Добавление пункта меню «Черный список и фильтры» (`onOpenBlacklistSettings`).
- Доработка `LibraryScreen.kt`:
  - Добавление кнопки перехода в настройки фильтрации в шапку экрана.
- Доработка `NowPlayingScreen.kt`:
  - Визуальный бейдж `[🚫 ЗАБЛОКИРОВАНО ФИЛЬТРОМ]` и блокировка добавления в избранное для заблокированного трека.
- Доработка `AppScaffold.kt`:
  - Добавление стейта отображения экрана настроек черного списка (`isBlacklistSettingsOpen`) и оверлейная навигация с анимацией перехода.
- UI/UX верификация:
  - Проверка читаемости во всех 4 темах (Spotify, Yandex, Obsidian AMOLED, Pastel).

---

## 7. Чек-лист гейтов качества (Quality Gates)

- [x] **Gate 1 (DoR Architecture)**: Настоящий документ полностью описывает контекст, структуру БД, миграцию, рантайм-интеграцию, UI и границы спецификаций.
- [ ] **Gate 2 (Specifications)**: Создание `SPEC-BLK-01` и `SPEC-BLK-02`.
- [ ] **Gate 3 (Contract Sync)**: Согласование интерфейсов `BlacklistEngine` и `BlacklistFilterManager`.
- [ ] **Gate 4 (Task Decomposition)**: Декомпозиция задач на атомарные функции (1 функция = 1 карточка).
- [ ] **Gate 5 (TDD Red Tests)**: Написание модульных тестов на правила и каскадное удаление.
- [ ] **Gate 6 (Implementation)**: Поэтапная реализация кода.
- [ ] **Gate 7 (Review & Audit)**: Проверка соответствия спецификациям и дизайн-токенам.
- [ ] **Gate 8 (Acceptance Build)**: Сборка и верификация APK.
