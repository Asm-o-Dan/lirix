# Задача TASK-BLK-07: Интеграция выборки подсказок в BlacklistViewModel

- **ID задачи:** `TASK-BLK-07`
- **Роль исполнителя:** Кодер (Core & ViewModel)
- **Зона:** `media-core` & `media-ui`
- **Файлы:**
  1. `app/src/main/java/com/lirix/app/ui/blacklist/BlacklistViewModel.kt`
- **Зависит от:** `TASK-BLK-06`
- **Спека / Решение:** [ADR-018 в .sdd/decisions.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/decisions.md)
- **Приоритет:** HIGH (Блокирует `TASK-BLK-08`)

---

## 1. Цель задачи
Обеспечить реактивную загрузку подсказок из базы данных `AppDatabase.musicDao()` в `BlacklistViewModel` для выбранного типа правила (`draftType`):
- `BlacklistRuleType.TITLE` -> вызов `db.musicDao().getRecentTrackTitles(limit = 20)`
- `BlacklistRuleType.ARTIST` -> вызов `db.musicDao().getTopArtists(limit = 20)`
- `BlacklistRuleType.PACKAGE_NAME` -> вызов `db.musicDao().getDistinctSourcePackages(limit = 20)`
  - Для пакетов: если в базе найдено меньше 4 пакетов, дополнить дефолтными системными плеерами (`com.spotify.music`, `com.vkontakte.android`, `ru.yandex.music`, `org.telegram.messenger`, `com.google.android.apps.youtube.music`), исключая дубликаты.

---

## 2. Контракт данных в `BlacklistUiState`

Добавить в `BlacklistUiState`:
```kotlin
data class BlacklistUiState(
    // ...
    val dbSuggestions: List<String> = emptyList(),
    // ...
)
```

В `BlacklistViewModel`:
1. При `openConstructor()`: вызывать функцию `loadSuggestionsForCurrentType()`.
2. При `setDraftType(type: BlacklistRuleType)`: обновлять `draftType` и вызывать `loadSuggestionsForCurrentType()`.
3. Функция `loadSuggestionsForCurrentType()`:
```kotlin
private fun loadSuggestionsForCurrentType() {
    viewModelScope.launch {
        val type = _uiState.value.draftType
        val suggestions = when (type) {
            BlacklistRuleType.TITLE -> db.musicDao().getRecentTrackTitles(20)
            BlacklistRuleType.ARTIST -> db.musicDao().getTopArtists(20)
            BlacklistRuleType.PACKAGE_NAME -> {
                val dbPackages = db.musicDao().getDistinctSourcePackages(20)
                val fallbackPackages = listOf(
                    "com.spotify.music",
                    "com.vkontakte.android",
                    "ru.yandex.music",
                    "org.telegram.messenger",
                    "com.google.android.apps.youtube.music"
                )
                (dbPackages + fallbackPackages).distinct()
            }
        }
        _uiState.update { it.copy(dbSuggestions = suggestions) }
    }
}
```

---

## 3. Критерии приемки (DoD)
- [ ] При смене вкладки типа критерия в конструкторе список `dbSuggestions` мгновенно обновляется.
- [ ] Для пустой базы fallback для пакетов корректно отображает список известных плееров.
- [ ] Все вызовы выполняются в корутине `viewModelScope` без блокировки главного потока.
