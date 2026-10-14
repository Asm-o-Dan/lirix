# Задача TASK-BLK-06: Запросы динамических пресетов и подсказок из фонотеки в MusicDao

- **ID задачи:** `TASK-BLK-06`
- **Роль исполнителя:** Кодер (Storage & Core)
- **Зона:** `media-core` & `storage`
- **Файлы:**
  1. `app/src/main/java/com/lirix/app/storage/MusicDao.kt` (добавить запросы)
  2. `app/src/test/java/com/lirix/app/BlacklistEngineTest.kt` (добавить тесты выборки подсказок)
- **Спека / Решение:** [ADR-018 в .sdd/decisions.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/decisions.md)
- **Приоритет:** HIGH (Блокирует `TASK-BLK-07` и `TASK-BLK-08`)

---

## 1. Цель задачи
Реализовать в `MusicDao` методы быстрой выборки уникальных значений из реальной истории прослушиваний и фонотеки Room для наполнения чипов-подсказок в конструкторе черного списка:
1. Уникальные пакеты приложений-провайдеров (`sourcePackage`) из истории сессий.
2. Топовые артисты из фонотеки (`music_tracks`), отсортированные по популярности (`playCount DESC`).
3. Недавние названия треков из фонотеки (`music_tracks`), отсортированные по времени прослушивания (`lastPlayedAt DESC`).

---

## 2. Контракт методов в `MusicDao.kt`

Добавить в интерфейс `MusicDao`:

```kotlin
    @Query("SELECT DISTINCT sourcePackage FROM music_listening_sessions WHERE sourcePackage IS NOT NULL AND sourcePackage != '' ORDER BY id DESC LIMIT :limit")
    suspend fun getDistinctSourcePackages(limit: Int = 20): List<String>

    @Query("SELECT DISTINCT artist FROM music_tracks WHERE artist IS NOT NULL AND artist != '' ORDER BY playCount DESC, lastPlayedAt DESC LIMIT :limit")
    suspend fun getTopArtists(limit: Int = 20): List<String>

    @Query("SELECT DISTINCT title FROM music_tracks WHERE title IS NOT NULL AND title != '' ORDER BY lastPlayedAt DESC, playCount DESC LIMIT :limit")
    suspend fun getRecentTrackTitles(limit: Int = 20): List<String>
```

---

## 3. Критерии приемки (DoD)
- [ ] Методы скомпилированы и зарегистрированы в Room DAO без ошибок KSP.
- [ ] `limit` настраивается (по умолчанию 20).
- [ ] Пустые строки (`''`) и `NULL` фильтруются на уровне SQL-запроса.
- [ ] Написан unit-тест в `BlacklistEngineTest.kt`, проверяющий корректность возврата уникальных пакетов, исполнителей и названий.
