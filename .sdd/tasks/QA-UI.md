## Задача QA-UI: Написание набора тестов для модуля :ui:timeline

**Файлы:**
- `ui/timeline/src/test/kotlin/com/example/npc/ui/timeline/mapper/EventUiMapperTest.kt`
- `ui/timeline/src/test/kotlin/com/example/npc/ui/timeline/ui/TimelineViewModelTest.kt`
(создать)

**Спека:**
- `.sdd/specs/ui-timeline/overview.md#тестовые-сценарии-и-верификация` (v1)

**Зависит от:** `UI-001` (TDD: тесты пишутся параллельно с реализацией)

**Поведение:**
1. `EventUiMapperTest`:
   - Маппинг `Event` -> `EventUiModel` (форматирование времени, извлечение источника из rawEvent, флаг isUpdate).
   - Маппинг `SourceHealth` -> `SourceHealthUiModel` (статусы HEALTHY, WARNING, CRITICAL при различных интервалах времени и ошибках).
2. `TimelineViewModelTest`:
   - Начальное состояние: подписка на Flow из `storageGateway`.
   - Фильтрация по `SourceFilter` (проверка фильтрации только выбранного источника).
   - Полнотекстовый поиск по `searchQuery` (регистронезависимый поиск в title и text).
   - Действие `onExportJsonClicked` вызывает `exportAllToJson` и эмитит эффект `ShareJsonExport`.
   - Действие `onConfirmDeleteAll` вызывает `storageGateway.deleteAll()` и закрывает диалог.

**Критерий приёмки:**
- 100% юнит-тестов проходят успешно (`.\gradlew.bat :ui:timeline:testDebugUnitTest`).
