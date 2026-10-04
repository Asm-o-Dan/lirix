# Задача TASK-BUG-07: Редактирование и сохранение персональных заметок к треку в NowPlaying

- **ID задачи:** `TASK-BUG-07`
- **Роль исполнителя:** Кодер
- **Зона:** `media-ui` / `media-core`
- **Файлы:**
  1. `app/src/main/java/com/eventengine/app/ui/NowPlayingScreen.kt` (интерактивный редактор заметок, переключение режимов, автосохранение)
  2. `app/src/main/java/com/eventengine/app/storage/MusicDao.kt` (использование `updateUserNotes`)
  3. `app/src/test/java/com/eventengine/app/MusicDatabaseTest.kt` (тест обновления заметок в Room)
- **Спека:** [.sdd/specs/media-ui/overview.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ui/overview.md) (v1), [.sdd/specs/media-core/overview.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-core/overview.md) (v1)
- **Приоритет:** HIGH (Дефект: вкладка «Заметки» в NowPlaying доступна только для чтения)

---

## 1. Контекст и механика дефекта

В `NowPlayingScreen.kt` (строки 878–905) режим `NowPlayingMode.NOTES` отображает лишь статичный компонент:
```kotlin
Text(
    text = if (notes.isNotBlank()) notes else "Заметок пока нет...",
    ...
)
```
Пользователь не имеет возможности ввести текст, изменить существующую запись или сохранить персональные мысли к песне (например, каподастр, гитарный строй, любимые строки). При этом метод `db.musicDao().updateUserNotes(trackKey, notes)` уже реализован в DAO, но не связан с UI.

---

## 2. Спецификация интерактивного редактора заметок

### 2.1 Локальное состояние в `NowPlayingScreen.kt`
```kotlin
var isEditingNotes by remember { mutableStateOf(false) }
var notesInputText by remember(currentTrack?.trackKey, currentTrack?.userNotes) {
    mutableStateOf(currentTrack?.userNotes.orEmpty())
}
```

### 2.2 Разметка вкладки `NowPlayingMode.NOTES`
Вместо статичного `Text`:
1. **Верхняя панель действий:**
   - Слева: плашка с иконкой `📝 Персональные заметки`.
   - Справа:
     - В режиме чтения: кнопка `[✏️ Редактировать]` (`Icons.Default.Edit`).
     - В режиме редактирования: кнопки `[✕ Отмена]` (`Icons.Default.Close`) и `[✓ Сохранить]` (`Icons.Default.Check`) с акцентным цветом `AppColors.ElectricMint`.
2. **Режим редактирования (`isEditingNotes == true`):**
   - Поле ввода `OutlinedTextField`:
     - Фон: `AppColors.SurfaceLevel1`.
     - Рамка: `Border(1.dp, AppColors.ElectricMint)`.
     - Цвет текста: `AppColors.TextPrimary`.
     - Placeholder: `"Запишите гитарный строй, каподастр, любимые строчки или мысли об этой песне..."`.
     - Свойства: `minLines = 6`, `maxLines = 15`.
   - При нажатии `[✓ Сохранить]`:
     ```kotlin
     haptic.performHapticFeedback(HapticFeedbackType.LongPress)
     val trackKey = currentTrack.trackKey
     val newNotes = notesInputText.trim()
     scope.launch(Dispatchers.IO) {
         db.musicDao().updateUserNotes(trackKey, newNotes)
     }
     isEditingNotes = false
     ```
3. **Режим просмотра (`isEditingNotes == false`):**
   - Если заметка есть:
     - Карточка с текстом заметки (`SurfaceLevel2`, `BorderSubtle`), кликабельная для быстрого перехода к редактированию.
   - Если заметка пуста:
     - Пустое состояние со стильной кнопкой `[+ Добавить заметку]`, нажатие на которую сразу переводит в режим редактирования.

---

## 3. Критерии приемки (DoD)

1. **Редактирование и сохранение:**
   - Пользователь может нажать кнопку редактирования, ввести текст заметки и нажать «Сохранить».
   - Введенный текст немедленно сохраняется в базе данных `music_tracks` через `db.musicDao().updateUserNotes`.
2. **Персистентность:**
   - При перезапуске приложения или переключении треков сохраненная заметка отображается на вкладке «Заметки».
3. **Unit-тест в `MusicDatabaseTest.kt`:**
   - `test_user_notes_update_and_persistence`: запись заметки обновляет поле `userNotes` в базе и корректно считывается при выборке.
4. Прогон `./gradlew test` завершается со статусом SUCCESS.
