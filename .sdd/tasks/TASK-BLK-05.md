# Задача TASK-BLK-05: Интеграция точек входа в Черный Список (Навигация)

- **ID задачи:** `TASK-BLK-05`
- **Роль исполнителя:** Кодер (UI/Navigation)
- **Зона:** `media-ui`
- **Файлы:**
  1. `app/src/main/java/com/lirix/app/ui/components/NowPlayingOverflowMenu.kt` (добавить пункт «🛡 Черный список фонотеки»)
  2. `app/src/main/java/com/lirix/app/ui/NowPlayingScreen.kt` (проброс коллбэка `onOpenBlacklistSettings`)
  3. `app/src/main/java/com/lirix/app/ui/LibraryScreen.kt` (добавить иконку быстрого перехода в шапку)
  4. `app/src/main/java/com/lirix/app/ui/AppScaffold.kt` (обработка навигации на экран `BlacklistSettingsScreen`)
- **Спека:** [.sdd/specs/media-ui/blacklist_settings_spec.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ui/blacklist_settings_spec.md)
- **Зависит от:** `TASK-BLK-04`
- **Приоритет:** MEDIUM

---

## 1. Цель задачи
Обеспечить бесшовный доступ пользователя к экрану `BlacklistSettingsScreen` из двух логических точек:
1. Меню плеера NowPlaying (меню ⋮): пункт «Фильтры фонотеки».
2. Шапка экрана Фонотеки (Библиотеки): иконка щита `Shield` рядом с поисковой строкой.

---

## 2. Изменения в `NowPlayingOverflowMenu.kt`
Добавить пункт выпадающего меню:
```kotlin
DropdownMenuItem(
    text = {
        Text(
            text = "🛡 Черный список фонотеки",
            color = LirixThemeTokens.colors.textPrimary
        )
    },
    onClick = {
        onDismiss()
        onOpenBlacklistSettings()
    }
)
```

---

## 3. Изменения в `LibraryScreen.kt`
В шапке фонотеки (`Row` с заголовком «Медиатека» или поиском):
Добавить `IconButton` с размером `.size(48.dp)`:
```kotlin
IconButton(
    onClick = onOpenBlacklistSettings,
    modifier = Modifier.size(48.dp)
) {
    Icon(
        imageVector = Icons.Default.Shield,
        contentDescription = "Фильтры медиатеки",
        tint = LirixThemeTokens.colors.textSecondary
    )
}
```

---

## 4. Изменения в `AppScaffold.kt`
Добавить флаг / экран навигации:
```kotlin
var isBlacklistSettingsOpen by remember { mutableStateOf(false) }

if (isBlacklistSettingsOpen) {
    BlacklistSettingsScreen(
        onBack = { isBlacklistSettingsOpen = false },
        db = appDatabase
    )
}
```

---

## 5. Критерии приемки (DoD)
- Нажатие пункта в меню ⋮ и иконки щита в Библиотеке открывает экран черного списка.
- Кнопка «Назад» на экране черного списка корректно возвращает пользователя на предыдущий экран.
