# TASK-UI-EDIT-LYRICS-01: Компонент ручного редактирования текста песни и умные помощники

## 1. Контекст и Проблематика
Пользователи нуждаются в возможности самостоятельно редактировать текст песни прямо в мобильном приложении: исправлять опечатки, убирать аккорды для караоке, разбивать текст на строки и вставлять скопированный текст из буфера обмена.

## 2. Архитектурная зона и файлы реализации
- **Зона**: `media-ui`
- **Файлы**:
  - `app/src/main/java/com/lirix/app/ui/components/EditLyricsDialog.kt` (новый компонент)
  - `app/src/main/java/com/lirix/app/ui/components/NowPlayingOverflowMenu.kt`
  - `app/src/main/java/com/lirix/app/ui/components/LrcTapSyncStudioDialog.kt`
  - `app/src/main/java/com/lirix/app/ui/NowPlayingScreen.kt`

## 3. Детерминированные требования (DoR / Спецификация)
1. **Дизайн-система Lirix 2.0**:
   - `GolosTextFontFamily` для интерфейса, `LiterataFontFamily` для поля ввода текста.
   - Скругления `pillCornerRadius`, акцентные цвета токенов `LirixThemeTokens`.
   - Полная поддержка `WindowInsets` (`statusBarsPadding`, `navigationBars`, `imePadding` для виртуальной клавиатуры).
2. **Панель умных инструментов (Smart Toolbar)**:
   - **«Разбить на строки»** (`Icons.Default.AutoAwesome`): применяет `LrcSyncEngine.sanitizePlainText(text)`.
   - **«Убрать аккорды»** (`Icons.Default.MusicNote`): удаляет аккорды из текста.
   - **«Вставить»** (`Icons.Default.ContentPaste`): вставляет текст из буфера обмена Android.
3. **Информационная строка**:
   - Живой счетчик строк и символов.
4. **Точки входа в UI**:
   - Кнопка редактирования `Icons.Default.Edit` в `PlainLyricsReadingView` (экран текста).
   - Пункт «Редактировать текст песни» в меню `NowPlayingOverflowMenu`.
   - Кнопка карандаша в верхнем тулбаре `LrcTapSyncStudioDialog`.
5. **Сохранение**:
   - Асинхронное обновление Room (`lyricsDao().saveLyrics` и `musicDao().updateLyrics`).
   - Моментальное обновление состояния `lyricsCache` в UI с показом `Snackbar`.

## 4. DoD (Definition of Done)
- [x] Компонент `EditLyricsDialog` создан и стилизован.
- [x] Интеграция в `NowPlayingScreen`, `NowPlayingOverflowMenu` и `LrcTapSyncStudioDialog`.
- [x] Сборка `:app:assembleDebug` успешна.
- [x] Установка и верификация на физическом устройстве по ADB.
- [x] Статус: **DONE**
