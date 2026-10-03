## Задача UI-SYNC-006: Доступ к системной диагностике и настройкам HyperOS из TopBar

**Модули:** `:ui:timeline`, `:app`  
**Целевые файлы:**  
- `ui/timeline/src/main/kotlin/com/example/npc/ui/timeline/ui/TimelineScreen.kt`
- `app/src/main/kotlin/com/example/npc/app/MainActivity.kt`  

### Требования:
1. В `TimelineScreen.kt`:
   - Добавить параметр `onOpenSettings: () -> Unit = {}` в функцию `TimelineScreen`.
   - В `CenterAlignedTopAppBar` в `actions` перед `Share` и `Delete` добавить иконку `IconButton` с `Icons.Default.Settings`:
     ```kotlin
     IconButton(onClick = onOpenSettings) {
         Icon(
             imageVector = Icons.Default.Settings,
             contentDescription = "Системные настройки и статус"
         )
     }
     ```
2. В `MainActivity.kt`:
   - Добавить состояние `var showSettingsScreen by remember { mutableStateOf(false) }`.
   - Передать `onOpenSettings = { showSettingsScreen = true }` в `TimelineScreen`.
   - Если `showSettingsScreen == true`, показывать `OnboardingScreen` с возможностью закрытия/возврата.

### Критерии приёмки (DoD):
- [x] Кнопка настроек доступна в панели приложения.
- [x] Пользователь может в любой момент проверить статус прав HyperOS.
