# Задача TASK-BLK-04: Экран настроек BlacklistSettingsScreen и BottomSheet конструктор правил

- **ID задачи:** `TASK-BLK-04`
- **Роль исполнителя:** Кодер (UI/Compose)
- **Зона:** `media-ui`
- **Файлы:**
  1. `app/src/main/java/com/lirix/app/ui/blacklist/BlacklistViewModel.kt` (создать)
  2. `app/src/main/java/com/lirix/app/ui/blacklist/BlacklistSettingsScreen.kt` (создать)
  3. `app/src/main/java/com/lirix/app/ui/blacklist/BlacklistRuleConstructorSheet.kt` (создать)
- **Спека:** [.sdd/specs/media-ui/blacklist_settings_spec.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ui/blacklist_settings_spec.md), [.sdd/reports/UI_DESIGN_AUDIT_FRONTEND.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/reports/UI_DESIGN_AUDIT_FRONTEND.md)
- **Зависит от:** `TASK-BLK-01`, `TASK-BLK-02`
- **Приоритет:** HIGH

---

## 1. Цель задачи
Реализовать полноценный экран настроек Черного Списка (`BlacklistSettingsScreen.kt`) с интерактивным `ModalBottomSheet` конструктором правил, реактивным превью количества удаляемых треков при вводе (дебаунс 250мс), поддержкой 4 тем оформления (`LirixThemeTokens.colors`) и сенсорными областями $\ge 48\times 48\,\text{dp}$.

---

## 2. Архитектура ViewModel (`BlacklistViewModel.kt`)

```kotlin
package com.lirix.app.ui.blacklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lirix.app.feature.blacklist.BlacklistPurgeEngine
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.blacklist.BlacklistMatchMode
import com.lirix.app.storage.blacklist.BlacklistRuleEntity
import com.lirix.app.storage.blacklist.BlacklistRuleType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class BlacklistViewModel(private val db: AppDatabase) : ViewModel() {
    private val purgeEngine = BlacklistPurgeEngine(db)
    private val _uiState = MutableStateFlow(BlacklistUiState())
    val uiState: StateFlow<BlacklistUiState> = _uiState.asStateFlow()

    private var previewJob: Job? = null

    init {
        loadRules()
    }

    private fun loadRules() {
        viewModelScope.launch {
            db.blacklistDao().getAllRulesFlow().collect { rules ->
                _uiState.update { it.copy(rules = rules) }
            }
        }
    }

    fun openConstructor() {
        _uiState.update {
            it.copy(
                isConstructorOpen = true,
                draftType = BlacklistRuleType.TITLE,
                draftPattern = "",
                draftMatchMode = BlacklistMatchMode.CONTAINS_IGNORE_CASE,
                draftPurgePreviewCount = 0
            )
        }
    }

    fun closeConstructor() {
        _uiState.update { it.copy(isConstructorOpen = false) }
    }

    fun setDraftType(type: BlacklistRuleType) {
        _uiState.update { it.copy(draftType = type) }
        recalculatePreview()
    }

    fun setDraftPattern(pattern: String) {
        _uiState.update { it.copy(draftPattern = pattern) }
        recalculatePreview()
    }

    fun setDraftMatchMode(mode: BlacklistMatchMode) {
        _uiState.update { it.copy(draftMatchMode = mode) }
        recalculatePreview()
    }

    private fun recalculatePreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(250) // Дебаунс 250 мс
            val state = _uiState.value
            if (state.draftPattern.isBlank()) {
                _uiState.update { it.copy(draftPurgePreviewCount = 0) }
                return@launch
            }
            val tempRule = BlacklistRuleEntity(
                id = "preview",
                ruleType = state.draftType,
                pattern = state.draftPattern,
                matchMode = state.draftMatchMode,
                isEnabled = true
            )
            val count = purgeEngine.previewPurgeCount(tempRule)
            _uiState.update { it.copy(draftPurgePreviewCount = count) }
        }
    }

    fun toggleRule(rule: BlacklistRuleEntity) {
        viewModelScope.launch {
            val updated = rule.copy(isEnabled = !rule.isEnabled)
            db.blacklistDao().updateRule(updated)
            if (updated.isEnabled) {
                // Если правило включили повторно, производим очистку
                purgeEngine.purgeMatchingTracks(updated)
            }
        }
    }

    fun deleteRule(ruleId: String) {
        viewModelScope.launch {
            db.blacklistDao().deleteRule(ruleId)
            _uiState.update { it.copy(snackbarMessage = "Правило удалено") }
        }
    }

    fun saveDraftAndPurge() {
        val state = _uiState.value
        val pattern = state.draftPattern.trim()
        if (pattern.isEmpty()) return

        viewModelScope.launch {
            val newRule = BlacklistRuleEntity(
                id = UUID.randomUUID().toString(),
                ruleType = state.draftType,
                pattern = pattern,
                matchMode = state.draftMatchMode,
                isEnabled = true
            )
            db.blacklistDao().insertRule(newRule)
            val report = purgeEngine.purgeMatchingTracks(newRule)
            _uiState.update {
                it.copy(
                    isConstructorOpen = false,
                    snackbarMessage = "Правило создано. Удалено ${report.deletedTracksCount} треков."
                )
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
```

---

## 3. Требования к верстке
1. Строгое использование `LirixThemeTokens.colors`:
   - Никаких захардкоженных `AppColors.*`!
   - Кнопка создания: `accentPrimary`.
   - Текст заголовков и пресетов: `textPrimary`.
   - Карточки: `surfaceCard` с обводкой `outlineVariant`.
2. Готовые пресеты при выборе `PACKAGE_NAME`:
   - `Spotify` (`com.spotify.music`)
   - `VK Музыка` (`com.vkontakte.android`)
   - `Яндекс Музыка` (`ru.yandex.music`)
   - `Telegram` (`org.telegram.messenger`)
   - `YouTube Music` (`com.google.android.apps.youtube.music`)
3. Touch targets $\ge 48\times 48\,\text{dp}$.

---

## 4. Критерии приемки (DoD)
- Экран и BottomSheet компилируются без ошибок.
- Дебаунс 250мс корректно обновляет превью-счетчик.
- Все 4 темы отображаются контрастно и эстетично.
