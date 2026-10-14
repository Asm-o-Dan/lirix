# Задача TASK-BLK-03: Интеграция Ingress Guard в MusicFeatureEngine

- **ID задачи:** `TASK-BLK-03`
- **Роль исполнителя:** Кодер (Core Engine)
- **Зона:** `media-core` & `media-ingress`
- **Файлы:**
  1. `app/src/main/java/com/lirix/app/ingestion/LivePlaybackSnapshot.kt` (добавить флаг `isBlacklisted: Boolean = false`)
  2. `app/src/main/java/com/lirix/app/feature/MusicFeatureEngine.kt` (добавить проверку правил черного списка перед записью в Room)
- **Спека:** [.sdd/specs/media-core/blacklist_storage_spec.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-core/blacklist_storage_spec.md)
- **Зависит от:** `TASK-BLK-01`, `TASK-BLK-02`
- **Приоритет:** HIGH

---

## 1. Цель задачи
Обеспечить мгновенное пресечение сохранения заблокированных треков в базу данных `music_tracks` и учет в сессиях `music_listening_sessions`. При поступлении события воспроизведения трека, подпадающего под активное правило черного списка, система передает в `LivePlaybackSnapshot` флаг `isBlacklisted = true`, но гарантированно не выполняет дисковую запись трека и сессии.

---

## 2. Обновление `LivePlaybackSnapshot.kt`
В `data class LivePlaybackSnapshot`:
Добавить поле по умолчанию:
```kotlin
val isBlacklisted: Boolean = false
```

---

## 3. Обновление `MusicFeatureEngine.kt`
1. Добавить реактивное наблюдение за активными правилами черного списка:
```kotlin
private val activeBlacklistRules = kotlinx.coroutines.flow.MutableStateFlow<List<com.lirix.app.storage.blacklist.BlacklistRuleEntity>>(emptyList())

fun startBlacklistRuleSync(scope: kotlinx.coroutines.CoroutineScope) {
    scope.launch {
        db.blacklistDao().getAllRulesFlow().collect { allRules ->
            activeBlacklistRules.value = allRules.filter { it.isEnabled }
        }
    }
}
```
2. В методе обработки события воспроизведения (`recordPlaybackSignal` / `onPlaybackEvent`):
```kotlin
val isBlocked = com.lirix.app.feature.blacklist.BlacklistMatcher.isBlacklisted(
    title = title,
    artist = artist,
    packageName = sourcePackage,
    activeRules = activeBlacklistRules.value
)

if (isBlocked) {
    timber.log.Timber.d("MusicFeatureEngine: Track '$title - $artist' ($sourcePackage) is blacklisted. Skipping persistence.")
    // Обновляем snapshot для UI с флагом isBlacklisted = true, но НЕ вызываем запись в DAO!
    _livePlaybackFlow.value = _livePlaybackFlow.value.copy(
        title = title,
        artist = artist,
        sourcePackage = sourcePackage,
        isPlaying = isPlaying,
        isBlacklisted = true
    )
    return
}
```

---

## 4. Критерии приемки (DoD)
- Заблокированный трек отображается в UI, но не появляется в `music_tracks` и `music_listening_sessions`.
- При выключении правила в БД трек снова начинает сохраняться штатно.
