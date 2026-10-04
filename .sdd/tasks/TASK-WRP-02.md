# Задача TASK-WRP-02: 26 достижений, Room v8 (MIGRATION_7_8) и GamificationEngine

**Файлы:**
- `app/src/main/java/com/eventengine/app/storage/MusicEntities.kt` (обновить `AchievementEntity`)
- `app/src/main/java/com/eventengine/app/storage/MusicDao.kt` (добавить выборку сессий)
- `app/src/main/java/com/eventengine/app/storage/AppDatabase.kt` (v8 + `MIGRATION_7_8`)
- `app/src/main/java/com/eventengine/app/analytics/GamificationEngine.kt` (алгоритм 26 ачивок)
- `app/src/test/java/com/eventengine/app/AppDatabaseMigrationTest.kt` (тест MIGRATION_7_8)
- `app/src/test/java/com/eventengine/app/GamificationEngineTest.kt` (тесты 26 ачивок)

**Спецификация:** [.sdd/specs/wrapped-analytics/overview.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/wrapped-analytics/overview.md) (v2), контракт `core__wrapped.md`.

---

## 1. Контекст и архитектурная цель
В рамках 3-го этапа пивота Music & Lyrics Hub внедряется полноценная геймификация из **26 уникальных достижений**, разбитых на 4 статусных тира:
- 🥉 **Бронза (Bronze)** — 7 ачивок: первые шаги, исследование базовых фич.
- 🥈 **Серебро (Silver)** — 8 ачивок: активный слушатель, регулярность, караоке.
- 🥇 **Золото (Gold)** — 7 ачивок: глубокая вовлеченность, марафоны, ночные бдения, стрики.
- 💎 **Платина / Секретные (Platinum/Secret)** — 4 ачивки: экстремальные сценарии, полночные пасхалки и мастерство.

---

## 2. Изменения в модели данных и Room v8

### 2.1. Обновление `AchievementEntity`
В `app/src/main/java/com/eventengine/app/storage/MusicEntities.kt`:
```kotlin
@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val iconRes: String,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long? = null,
    val currentProgress: Int = 0,
    val maxProgress: Int = 1,
    val tier: String = "BRONZE",       // "BRONZE", "SILVER", "GOLD", "PLATINUM"
    val category: String = "LISTENING" // "LISTENING", "EXPLORATION", "LYRICS", "SPECIAL"
)
```

### 2.2. Расширение `MusicDao.kt`
Для расчета сессионных метрик и стриков добавить в `MusicDao`:
```kotlin
@Query("SELECT * FROM music_listening_sessions ORDER BY startTimeMs ASC")
fun observeAllSessions(): Flow<List<ListeningSessionEntity>>

@Query("SELECT * FROM music_listening_sessions ORDER BY startTimeMs ASC")
suspend fun getAllSessions(): List<ListeningSessionEntity>
```

### 2.3. Миграция `MIGRATION_7_8` в `AppDatabase.kt`
1. Повысить версию базы данных: `@Database(version = 8, ...)`
2. Реализовать объект `MIGRATION_7_8`:
```kotlin
@JvmField
val MIGRATION_7_8: Migration = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Добавление колонок tier и category с безопасными значениями по умолчанию
        db.execSQL("ALTER TABLE `achievements` ADD COLUMN `tier` TEXT NOT NULL DEFAULT 'BRONZE'")
        db.execSQL("ALTER TABLE `achievements` ADD COLUMN `category` TEXT NOT NULL DEFAULT 'LISTENING'")
        
        // 2. Сидирование полного реестра 26 достижений (INSERT OR IGNORE сохраняет существующие разблокировки)
        seed26Achievements(db)
    }
}
```
3. Добавить `MIGRATION_7_8` в `.addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)` в `buildDatabase`.
4. В `Callback.onCreate(db)` вызывать `seed26Achievements(db)` вместо старого сида из 7 элементов.

---

## 3. Полный реестр 26 достижений

```kotlin
enum class AchievementTier(val titleRu: String, val badge: String, val colorHex: Long) {
    BRONZE("Бронза", "🥉", 0xFFCD7F32),
    SILVER("Серебро", "🥈", 0xFFC0C0C0),
    GOLD("Золото", "🥇", 0xFFFFD700),
    PLATINUM("Платина", "💎", 0xFF00E5FF)
}
```

### 🥉 Тир 1: Бронза (7 достижений)
1. `FIRST_TRACK`: "Первая нота" | "Прослушан 1 трек" | icon: `ic_music_note` | max: 1 | Cat: `LISTENING`
2. `DISCOVERY_5`: "Первые шаги" | "Послушано 5 разных артистов" | icon: `ic_explore` | max: 5 | Cat: `EXPLORATION`
3. `LYRICS_NOVICE`: "Подпевала" | "Открыт текст песни" | icon: `ic_lyrics` | max: 1 | Cat: `LYRICS`
4. `CHORD_STRUMMER`: "Первый аккорд" | "Открыты аккорды к треку" | icon: `ic_music_note` | max: 1 | Cat: `LYRICS`
5. `SHORT_SESSION`: "Разминка" | "15 минут музыки за сессию" | icon: `ic_timer` | max: 1 | Cat: `LISTENING`
6. `NOTE_TAKER`: "Заметки на полях" | "Добавлена первая личная заметка к треку" | icon: `ic_edit` | max: 1 | Cat: `SPECIAL`
7. `STREAK_3`: "На волне" | "Слушайте музыку 3 дня подряд" | icon: `ic_repeat` | max: 3 | Cat: `LISTENING`

### 🥈 Тир 2: Серебро (8 достижений)
8. `CENTURY_CLUB`: "Клуб сотни" | "100 уникальных треков в библиотеке" | icon: `ic_disc` | max: 100 | Cat: `EXPLORATION`
9. `ARTIST_DEVOTEE`: "Преданный фанат" | "Один исполнитель прослушан 15 раз" | icon: `ic_star` | max: 15 | Cat: `LISTENING`
10. `KARAOKE_REGULAR`: "Караоке-бар" | "10 треков с караоке LRC" | icon: `ic_lyrics` | max: 10 | Cat: `LYRICS`
11. `STREAK_7`: "Музыкальная неделя" | "Слушайте музыку 7 дней подряд" | icon: `ic_repeat` | max: 7 | Cat: `LISTENING`
12. `MARATHON_LISTENER`: "Марафонец" | "Сессия прослушивания более 2 часов" | icon: `ic_timer` | max: 1 | Cat: `LISTENING`
13. `MORNING_ENERGY`: "Бодрое утро" | "5 сессий с 06:00 до 10:00 утра" | icon: `ic_sun` | max: 5 | Cat: `SPECIAL`
14. `EVENING_CHILL`: "Вечерний релакс" | "10 сессий вечером (18:00 - 23:00)" | icon: `ic_moon` | max: 10 | Cat: `SPECIAL`
15. `GENRE_EXPLORER`: "Меломан" | "Более 15 разных исполнителей" | icon: `ic_explore` | max: 15 | Cat: `EXPLORATION`

### 🥇 Тир 3: Золото (7 достижений)
16. `LIBRARY_300`: "Золотая фонотека" | "300 уникальных треков в библиотеке" | icon: `ic_disc` | max: 300 | Cat: `EXPLORATION`
17. `REPEAT_FANATIC`: "На повторе" | "Один трек прослушан более 30 раз" | icon: `ic_repeat` | max: 30 | Cat: `LISTENING`
18. `KARAOKE_MASTER`: "Звезда караоке" | "25 треков с LRC-караоке" | icon: `ic_lyrics` | max: 25 | Cat: `LYRICS`
19. `NIGHT_OWL`: "Ночная сова" | "10 сессий глубокой ночью (01:00 - 05:00)" | icon: `ic_moon` | max: 10 | Cat: `SPECIAL`
20. `STREAK_30`: "Железная привычка" | "30 дней непрерывного прослушивания" | icon: `ic_repeat` | max: 30 | Cat: `LISTENING`
21. `MARATHON_5H`: "Аудио-марафон 5ч" | "Более 5 часов музыки за один день" | icon: `ic_timer` | max: 1 | Cat: `LISTENING`
22. `ALBUM_COLLECTOR`: "Хранитель винила" | "20 треков с обложками альбомов" | icon: `ic_disc` | max: 20 | Cat: `EXPLORATION`

### 💎 Тир 4: Платина / Секретные (4 достижения)
23. `SECRET_MIDNIGHT`: "Полуночная тайна" | "Включен трек в первые 5 минут полуночи (00:00 - 00:05)" | icon: `ic_star` | max: 1 | Cat: `SPECIAL`
24. `SECRET_REPEAT_DAY`: "Одержимость дня" | "Один трек прослушан 15 раз за одни сутки" | icon: `ic_repeat` | max: 1 | Cat: `LISTENING`
25. `DISCOGRAPHY_BINGE`: "Дискографический запой" | "8 разных треков одного артиста за сутки" | icon: `ic_explore` | max: 8 | Cat: `EXPLORATION`
26. `PLATINUM_PERFECTION`: "Абсолютный слух" | "Разблокировано 20 любых достижений" | icon: `ic_star` | max: 20 | Cat: `SPECIAL`

---

## 4. Алгоритмы проверки в `GamificationEngine.kt`

Функциональная сигнатура сохраняет обратную совместимость:
```kotlin
fun checkAchievements(
    currentAchievements: List<AchievementEntity>,
    tracks: List<MusicTrackEntity>,
    sessions: List<MusicListeningSessionEntity>
): List<AchievementEntity>
```

### Логика расчета метрик:
1. **Стрики активности по дням:**
   - Преобразовывать `session.startTimeMs` в локальную дату `yyyy-MM-dd` через `Calendar`.
   - Получить отсортированный список уникальных дней активности.
   - Найти максимальную длину непрерывной цепочки дней (где разница между последовательными днями ровно 1 день).
2. **Часовые слоты сессий:**
   - Утро: `hour in 6..9`
   - Вечер: `hour in 18..22`
   - Ночь: `hour in 1..4`
   - Секретная полночь: `hour == 0 && minute in 0..5`
3. **Дневные марафоны:**
   - Сгруппировать сессии по дате `yyyy-MM-dd`.
   - Суммировать `durationMs` за каждый день. Проверять `maxDailyDuration >= 5 * 3600 * 1000L`.
4. **Обложки и караоке:**
   - Обложки: `tracks.count { !it.albumArtUri.isNullOrBlank() }`
   - Караоке: `tracks.count { !it.syncedLyrics.isNullOrBlank() }`
   - Тексты: `tracks.count { !it.plainLyrics.isNullOrBlank() || !it.syncedLyrics.isNullOrBlank() }`
5. **Платиновый перфекционизм:**
   - `val unlockedCount = currentAchievements.count { it.id != "PLATINUM_PERFECTION" && it.isUnlocked }`
   - Разблокируется, если `unlockedCount >= 20`.

---

## 5. Критерии приемки (DoD)
1. Команда `./gradlew test` завершается успешно (зеленый статус).
2. Тест `AppDatabaseMigrationTest.kt::test_migration_7_to_8` валидирует:
   - Добавление колонок `tier` и `category`.
   - Сохранение ранее записанных треков и сессий.
   - Наличие ровно 26 записей в таблице `achievements` после миграции.
3. Тесты в `GamificationEngineTest.kt` покрывают разблокировку каждого из 4 тиров (стрик 3/7/30, ночные слоты, обложки, полночь).
4. Отсутствие регрессий в существующих 80 тестах.
