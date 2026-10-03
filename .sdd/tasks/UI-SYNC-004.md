## Задача UI-SYNC-004: Асинхронная подгрузка RawEvent и корректная передача метаданных в TimelineViewModel

**Модуль:** `:ui:timeline`  
**Целевой файл:** `ui/timeline/src/main/kotlin/com/example/npc/ui/timeline/ui/TimelineViewModel.kt`  

### Входные контракты / сигнатуры:

В `onEventClicked`:
```kotlin
    fun onEventClicked(event: EventUiModel) {
        _selectedEventForDetails.value = event
        viewModelScope.launch {
            try {
                val raw = storageGateway.getRawEventByEventId(event.id)
                if (raw != null && _selectedEventForDetails.value?.id == event.id) {
                    _selectedEventForDetails.value = _selectedEventForDetails.value?.copy(
                        packageName = raw.packageName,
                        payloadJson = raw.payloadJson
                    )
                }
            } catch (_: Exception) {}
        }
    }
```

В `onCategoryCorrected`:
```kotlin
    fun onCategoryCorrected(eventId: Long, newCategory: Category) {
        val targetEvent = _eventForCategoryCorrection.value ?: _selectedEventForDetails.value
        _eventForCategoryCorrection.value = null

        viewModelScope.launch {
            try {
                val pkg = targetEvent?.packageName?.takeIf { it.isNotBlank() } ?: "unknown.package"
                val fp = targetEvent?.contentFingerprint.orEmpty()
                storageGateway.recordUserCorrection(
                    eventId = eventId,
                    packageName = pkg,
                    contentFingerprint = fp,
                    newCategory = newCategory,
                    correctedAt = Instant.now()
                )
                _effects.emit(TimelineUiEffect.ShowSnackbar("Категория обновлена на ${newCategory.name}"))
            } catch (e: Exception) {
                _effects.emit(TimelineUiEffect.ShowSnackbar("Ошибка сохранения: ${e.message}"))
            }
        }
    }
```

### Инварианты:
1. Подгрузка сырых данных происходит асинхронно при выборе события без задержки рендеринга ленты.
2. При сохранении коррекции передаются реальный отпечаток и имя пакета.

### Критерии приёмки (DoD):
- [x] Все тесты `TimelineViewModelTest` проходят (100% PASS).
