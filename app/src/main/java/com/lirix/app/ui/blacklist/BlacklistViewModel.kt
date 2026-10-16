package com.lirix.app.ui.blacklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lirix.app.feature.blacklist.BlacklistPurgeEngine
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.TrackEntity
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

enum class BlacklistTab {
    ALL,
    TITLES,
    ARTISTS,
    PACKAGES
}

data class BlacklistUiState(
    val rules: List<BlacklistRuleEntity> = emptyList(),
    val activeFilterTab: BlacklistTab = BlacklistTab.ALL,
    val isLoading: Boolean = false,
    val isConstructorOpen: Boolean = false,
    // Draft constructor fields
    val draftType: BlacklistRuleType = BlacklistRuleType.TITLE,
    val draftPattern: String = "",
    val draftMatchMode: BlacklistMatchMode = BlacklistMatchMode.CONTAINS_IGNORE_CASE,
    val draftPurgePreviewCount: Int = 0,
    val previewMatchingTracks: List<TrackEntity> = emptyList(),
    val isCalculatingPreview: Boolean = false,
    val dbSuggestions: List<String> = emptyList(),
    // Notifications
    val snackbarMessage: String? = null,
    val showUndoForPurge: Boolean = false
) {
    val filteredRules: List<BlacklistRuleEntity>
        get() = when (activeFilterTab) {
            BlacklistTab.ALL -> rules
            BlacklistTab.TITLES -> rules.filter { it.ruleType == BlacklistRuleType.TITLE }
            BlacklistTab.ARTISTS -> rules.filter { it.ruleType == BlacklistRuleType.ARTIST }
            BlacklistTab.PACKAGES -> rules.filter { it.ruleType == BlacklistRuleType.PACKAGE_NAME }
        }

    val activeRulesCount: Int
        get() = rules.count { it.isEnabled }
}

class BlacklistViewModel(private val db: AppDatabase) : ViewModel() {
    private val purgeEngine = BlacklistPurgeEngine(db)
    private val _uiState = MutableStateFlow(BlacklistUiState(isLoading = true))
    val uiState: StateFlow<BlacklistUiState> = _uiState.asStateFlow()

    private var previewJob: Job? = null

    init {
        loadRules()
    }

    private fun loadRules() {
        viewModelScope.launch {
            db.blacklistDao().getAllRulesFlow().collect { rules ->
                _uiState.update { it.copy(rules = rules, isLoading = false) }
            }
        }
    }

    fun setActiveFilterTab(tab: BlacklistTab) {
        _uiState.update { it.copy(activeFilterTab = tab) }
    }

    fun openConstructor() {
        _uiState.update {
            it.copy(
                isConstructorOpen = true,
                draftType = BlacklistRuleType.TITLE,
                draftPattern = "",
                draftMatchMode = BlacklistMatchMode.CONTAINS_IGNORE_CASE,
                draftPurgePreviewCount = 0,
                previewMatchingTracks = emptyList(),
                isCalculatingPreview = false
            )
        }
        loadSuggestionsForCurrentType()
    }

    fun closeConstructor() {
        previewJob?.cancel()
        _uiState.update { it.copy(isConstructorOpen = false, isCalculatingPreview = false) }
    }

    fun setDraftType(type: BlacklistRuleType) {
        _uiState.update { it.copy(draftType = type) }
        loadSuggestionsForCurrentType()
        recalculatePreview()
    }

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
        val currentPattern = _uiState.value.draftPattern.trim()
        if (currentPattern.isEmpty()) {
            _uiState.update {
                it.copy(
                    draftPurgePreviewCount = 0,
                    previewMatchingTracks = emptyList(),
                    isCalculatingPreview = false
                )
            }
            return
        }

        _uiState.update { it.copy(isCalculatingPreview = true) }
        previewJob = viewModelScope.launch {
            delay(250) // Reactive debounce 250ms
            val state = _uiState.value
            if (state.draftPattern.isBlank()) {
                _uiState.update {
                    it.copy(
                        draftPurgePreviewCount = 0,
                        previewMatchingTracks = emptyList(),
                        isCalculatingPreview = false
                    )
                }
                return@launch
            }
            val tempRule = BlacklistRuleEntity(
                id = "preview",
                ruleType = state.draftType,
                pattern = state.draftPattern.trim(),
                matchMode = state.draftMatchMode,
                isEnabled = true
            )
            val matches = purgeEngine.findMatchingTracks(tempRule, limit = 20)
            val count = purgeEngine.previewPurgeCount(tempRule)
            _uiState.update {
                it.copy(
                    draftPurgePreviewCount = count,
                    previewMatchingTracks = matches,
                    isCalculatingPreview = false
                )
            }
        }
    }

    fun toggleRule(rule: BlacklistRuleEntity) {
        viewModelScope.launch {
            val updated = rule.copy(isEnabled = !rule.isEnabled)
            db.blacklistDao().updateRule(updated)
            if (updated.isEnabled) {
                // If rule is re-enabled, purge matching tracks immediately
                val report = purgeEngine.purgeMatchingTracks(updated)
                if (report.deletedTracksCount > 0) {
                    _uiState.update {
                        it.copy(snackbarMessage = "Правило включено. Удалено ${report.deletedTracksCount} треков.")
                    }
                }
            }
        }
    }

    fun deleteRule(ruleId: String) {
        viewModelScope.launch {
            db.blacklistDao().deleteRule(ruleId)
            _uiState.update { it.copy(snackbarMessage = "Правило удалено") }
        }
    }

    private var lastPurgedTracks: List<TrackEntity>? = null
    private var lastCreatedRuleId: String? = null

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
            val (report, purgedBackup) = purgeEngine.purgeMatchingTracksWithBackup(newRule)
            lastPurgedTracks = purgedBackup
            lastCreatedRuleId = newRule.id
            _uiState.update {
                it.copy(
                    isConstructorOpen = false,
                    snackbarMessage = if (report.deletedTracksCount > 0) {
                        "Удалено ${report.deletedTracksCount} треков."
                    } else {
                        "Правило игнорирования создано."
                    },
                    showUndoForPurge = report.deletedTracksCount > 0
                )
            }
        }
    }

    fun undoLastPurge() {
        viewModelScope.launch {
            val tracks = lastPurgedTracks
            val ruleId = lastCreatedRuleId
            if (!tracks.isNullOrEmpty()) {
                purgeEngine.restoreTracks(tracks)
            }
            if (ruleId != null) {
                db.blacklistDao().deleteRule(ruleId)
            }
            lastPurgedTracks = null
            lastCreatedRuleId = null
            _uiState.update {
                it.copy(
                    snackbarMessage = "Удаление отменено, треки восстановлены.",
                    showUndoForPurge = false
                )
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null, showUndoForPurge = false) }
    }

    companion object {
        fun provideFactory(db: AppDatabase): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BlacklistViewModel(db) as T
            }
        }
    }
}
