# Задача UI-PAGE-001: Динамическая подгрузка и бесконечный скролл в Timeline (Пагинация)

## 1. Контекст и проблема
В `TimelineViewModel.kt` выборка событий жестко ограничена `limit = 100`:
```kotlin
storageGateway.observeEvents(limit = 100)
storageGateway.observeTransactions(limit = 100)
```
Поскольку на реальном устройстве (Poco M7) поступает большой поток уведомлений (медиа-сессии, чаты, системные пуши), 100 последних событий накапливаются за 2–3 часа, скрывая всю предыдущую историю.

## 2. Требования к реализации
1. **Динамический размер окна (Page Size / Limit):**
   - Начальный лимит: 100 событий.
   - Шаг инкремента (Page Step): +100 событий (или подгрузка при достижении конца списка).
   - В `TimelineViewModel`:
     - Ввести реактивный поток текущего лимита `_currentLimit = MutableStateFlow(100)`.
     - Связать `_currentLimit` с `flatMapLatest` (или реактивным обновлением `observeEvents(limit)` и `observeTransactions(limit)`).
     - Метод `fun loadMore()`: увеличивает `_currentLimit.value += 100` (если еще есть не загруженные элементы, либо без ограничений).
2. **UI интеграция в `TimelineScreen.kt`:**
   - Отслеживать скролл через `LazyListState`:
     ```kotlin
     val shouldLoadMore by remember {
         derivedStateOf {
             val totalItems = listState.layoutInfo.totalItemsCount
             val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
             lastVisibleItemIndex >= totalItems - 5 && totalItems > 0
         }
     }
     LaunchedEffect(shouldLoadMore) {
         if (shouldLoadMore) {
             viewModel.loadMore()
         }
     }
     ```
   - Добавить индикатор загрузки `CircularProgressIndicator` в конце списка, если идет подгрузка.
3. **Верификация:**
   - Юнит-тесты в `TimelineViewModelTest.kt`:
     - Проверка начального лимита 100.
     - Вызов `loadMore()` запрашивает расширенный список событий (200, 300...).
   - Сборка и прогон `./gradlew :ui:timeline:testDebugUnitTest --no-daemon`.
