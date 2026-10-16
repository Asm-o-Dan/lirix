# TASK-UI-LIB-THEME-FIX-01: Динамическая тема Медиатеки и устранение захардкоженного акцента Графита

## 1. Контекст и проблема
Пользователь выбрал тему «Яндекс Музыка» (фирменный желтый акцент `#FFCC00`, оранжевый `#FF5500`, тёмный фон `#0E0F12`), однако на экране «Медиатека» (`LibraryScreen.kt`) все ключевые акценты оставались сине-графитовыми (`#7FA6F0` / `AccentFallbackDark`):
1. Активные чипсы фильтра («Все», «Любимые», «С текстом», «Аккорды») окрашивались в синий цвет графитовой темы.
2. Иконка лупы и рамка фокуса поисковой строки `OutlinedTextField` оставались синими.
3. Бейджи «Синхронно» на карточках треков отображались в синей рамке с синим текстом.
4. Счетчики прослушиваний `×18` отображались синим цветом.
5. Иконка «Избранное» (сердечко) и диалог действий трека `TrackActionBottomSheet.kt` использовали синий `AccentFallbackDark`.

## 2. Корень проблемы
В файлах `LibraryScreen.kt` (строка 144) и `TrackActionBottomSheet.kt` (строка 57) находилась жесткая привязка:
```kotlin
val stableAccent = if (lirixColors.isDark) LirixThemeTokens.AccentFallbackDark else LirixThemeTokens.AccentFallbackLight
```
Вместо динамического акцента текущей выбранной темы `lirixColors.accentPrimary` экран принудительно использовал константу `AccentFallbackDark` (`#7FA6F0`), игнорируя смену темы в `ThemePreferencesRepository` / `ColorPalettes`.

## 3. Архитектурное решение (ADR-053)
1. **Переход на `lirixColors.accentPrimary` и `lirixColors.accentSecondary`:**
   - В `LibraryScreen.kt` заменить константу `stableAccent` на динамический акцент темы `val accent = lirixColors.accentPrimary`.
   - Для активного чипса фильтра использовать фон `accent` и адаптивный цвет текста:
     `color = if (isSelected) MaterialTheme.colorScheme.onPrimary else lirixColors.textPrimary`.
     (Для Яндекс Музыки — чёткий чёрный текст на жёлтом фоне с контрастом 14:1, для тёмных тем — фоновый цвет, для светлых — белый).
   - Поисковая строка: `focusedBorderColor = accent`, иконка поиска: `tint = accent`.
   - Карточки треков:
     - Бейдж «Синхронно»: `borderColor = accent`.
     - Счетчик прослушиваний `×N`: `color = accent`.
     - Иконка «Избранное»: `tint = tokens.accentSecondary` (в тон с `NowPlayingScreen` и оранжевым акцентом Яндекс Музыки).
2. **Исправление `TrackActionBottomSheet.kt`:**
   - Заменить `stableAccent` на `tokens.accentPrimary`.
   - Иконка «В избранное / Убрать из избранного»: `iconTint = if (track.isFavorite) tokens.accentSecondary else tokens.accentPrimary`.

## 4. Критерии приемки (DoD)
- [x] При выборе темы «Яндекс Музыка» активный фильтр «Все» в Медиатеке ярко-желтый `#FFCC00` с черным текстом.
- [x] Рамка фокуса поиска и лупа подсвечиваются цветом активной темы.
- [x] Бейджи «Синхронно» и счетчики `×18` соответствуют акценту активной темы.
- [x] Иконка «В избранном» окрашивается в акцентный цвет темы.
- [x] При переключении на Spotify, Obsidian или Пастель — вся Медиатека мгновенно меняет цветовую гамму.
- [x] Сборка `:app:assembleDebug` компилируется без ошибок и проверена на реальном устройстве с захватом скриншота.
