# Задача TASK-BLK-08: Динамические чипы подсказок из фонотеки в BottomSheet конструкторе

- **ID задачи:** `TASK-BLK-08`
- **Роль исполнителя:** Кодер (UI Compose)
- **Зона:** `media-ui`
- **Файлы:**
  1. `app/src/main/java/com/lirix/app/ui/blacklist/BlacklistRuleConstructorSheet.kt`
  2. `app/src/main/java/com/lirix/app/ui/blacklist/BlacklistSettingsScreen.kt`
- **Зависит от:** `TASK-BLK-07`
- **Спека / Решение:** [ADR-018 в .sdd/decisions.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/decisions.md)
- **Приоритет:** HIGH

---

## 1. Цель задачи
Адаптировать `BlacklistRuleConstructorSheet.kt` для показа горизонтальной ленты быстрых чипов на основе `dbSuggestions` из состояния `BlacklistUiState` для **всех трех критериев**:
1. `TITLE`: заголовок «Недавние треки из фонотеки:»
2. `ARTIST`: заголовок «Популярные исполнители из фонотеки:»
3. `PACKAGE_NAME`: заголовок «Приложения из истории фонотеки:»

---

## 2. Требования к UI и поведению
1. **Проброс данных:**
   В компонент `BlacklistRuleConstructorSheet` добавить параметр:
   `suggestions: List<String> = emptyList()`
   (пробрасывать из `uiState.dbSuggestions` в `BlacklistSettingsScreen.kt`).

2. **Отображение чипов:**
   - Если `suggestions.isNotEmpty()`:
     - Заголовок в стиле `MaterialTheme.typography.labelSmall`, цвет `tokens.textSecondary`.
     - Горизонтальный ряд `Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp))`.
     - Каждый чип — `Surface`:
       - Скругление: `RoundedCornerShape(tokens.pillCornerRadius.coerceAtLeast(8.dp))`
       - Подсветка: если `draftPattern.trim() == item`, то фон `tokens.accentGlow`, граница `tokens.accentPrimary`, иначе фон `tokens.surfacePrimary`, граница `tokens.borderSubtle`.
       - Клик: вызывает `onPatternChange(item)`.
       - Для пакетов: если пакет совпадает с известным (`com.spotify.music` -> «Spotify», `org.telegram.messenger` -> «Telegram», `ru.yandex.music` -> «Яндекс Музыка», `com.vkontakte.android` -> «VK Музыка», `com.google.android.apps.youtube.music` -> «YouTube Music»), отображать понятное имя с бейджем пакета в скобках или чистое имя, а при клике подставлять сам `packageName`.
   - Если `suggestions.isEmpty()`:
     - Для `TITLE` и `ARTIST`: аккуратная подсказка «В фонотеке пока нет записей» серым приглушенным цветом `tokens.textMuted`.

3. **Touch Target & Дизайн:**
   - Высота чипа не менее 36dp с вертикальными отступами, обеспечивающими `minimumInteractiveComponentSize` (>=48dp touch target).
   - Поддержка всех 4 тем через `LirixThemeTokens.colors`.

---

## 3. Критерии приемки (DoD)
- [ ] При выборе «По артисту» под полем ввода отображаются чипы исполнителей из базы.
- [ ] При выборе «По названию» отображаются чипы треков из базы.
- [ ] При выборе «Приложение» отображаются пакеты из базы.
- [ ] Клик по любому чипу заполняет поле ввода и сразу пересчитывает счетчик удалений.
- [ ] Проект успешно собирается `./gradlew assembleRelease` без ошибок.
