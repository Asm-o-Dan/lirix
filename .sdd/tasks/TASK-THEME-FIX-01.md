# TASK-THEME-FIX-01: Полная динамическая тема ExhaustedLyricsPlaceholder, вкладок плеера и реактивность винила

## 1. Контекст и проблема
Пользователь зафиксировал два критических бага в оформлении UI:
1. «кстати кнопки найти в интернете и повторить поиск не меняют оформление»
2. «цвет винила не сразу обновляется»

### Детальный анализ причин:
1. В `app/src/main/java/com/lirix/app/ui/NowPlayingScreen.kt` компонент `ExhaustedLyricsPlaceholder` (строки 2191–2266) содержит жестко захардкоженные цвета из устаревшего `AppColors`:
   - Кнопка «🌐 Найти в интернете»: `containerColor = AppColors.HyperViolet`, текст `AppColors.AmoledBlack`.
   - Кнопка «🔄 Сбросить отклонённые источники»: `border = BorderStroke(1.dp, AppColors.CyberCyan)`, текст `AppColors.CyberCyan`.
   - Кнопка «Повторить поиск»: `border = BorderStroke(1.dp, AppColors.HyperViolet.copy(alpha = 0.5f))`, иконка `AppColors.HyperViolet`, текст `AppColors.TextPrimary`.
   - Иконка и заголовки: `AppColors.TextTertiary`, `AppColors.TextPrimary`, `AppColors.TextSecondary`.
   - Индикатор загрузки текстов (строка 1248): `CircularProgressIndicator(color = AppColors.HyperViolet)`.
   - Вкладка «Заметки» (строки 1670–1850): бейджи, кнопки «Сохранить», «Редактировать», «Добавить заметку» и поля ввода захардкожены на `AppColors.ElectricMint`, `AppColors.SurfaceLevel*`, `AppColors.BorderSubtle`.

2. Задержка обновления цвета винила:
   - В `AnalogTurntable.kt` фон диска `Box(modifier = Modifier.background(Brush.radialGradient(...)).border(1.5.dp, Brush.sweepGradient(...)))` и рисунок иглы на Canvas читают `val tokens = LirixThemeTokens.colors`.
   - При смене темы в диалоге `ThemeSelectionDialog` вызывается `themeRepo.setTheme(newTheme)`, что меняет `_selectedTheme.value`.
   - Однако при вызове `AnalogTurntable(...)` в `NowPlayingScreen.kt` параметры темы не передаются, а Compose мемоизирует модификаторы `Box` или пропускает рекомпозицию `Box` внутри вложенного контейнера, пока не произойдет смена трека или рекомпозиция плеера.
   - Также в мини-диске свёрнутого состояния (`isVinylCollapsed`) кисти не кэшируются через `remember(lirixColors)`.

---

## 2. Архитектурные требования к изменениям

### Зона 1: `app/src/main/java/com/lirix/app/ui/components/AnalogTurntable.kt`
1. Добавить явный параметр токенов с дефолтным значением:
   ```kotlin
   @Composable
   fun AnalogTurntable(
       isPlaying: Boolean,
       albumArtBitmap: ImageBitmap?,
       isCollapsed: Boolean,
       onToggleCollapse: () -> Unit,
       onTogglePlayPause: () -> Unit,
       modifier: Modifier = Modifier,
       tokens: LirixColors = LirixThemeTokens.colors
   )
   ```
2. Градиенты диска вычислять с явным `remember(tokens)`:
   ```kotlin
   val vinylBackgroundBrush = remember(tokens) {
       Brush.radialGradient(
           listOf(
               tokens.surfaceElevated,
               tokens.surfaceCard,
               tokens.appBackground
           )
       )
   }
   val vinylBorderBrush = remember(tokens) {
       Brush.sweepGradient(
           listOf(
               tokens.accentPrimary,
               tokens.accentSecondary,
               tokens.accentGlow,
               tokens.accentPrimary
           )
       )
   }
   ```
3. Использовать `vinylBackgroundBrush` и `vinylBorderBrush` в `Modifier.background(vinylBackgroundBrush).border(1.5.dp, vinylBorderBrush, CircleShape)`.
4. В `Canvas` отрисовки тонарма использовать `tokens.accentPrimary` и `tokens.accentSecondary`.

### Зона 2: `app/src/main/java/com/lirix/app/ui/NowPlayingScreen.kt`
1. В вызове `AnalogTurntable` явно передавать `tokens = lirixColors`:
   ```kotlin
   AnalogTurntable(
       isPlaying = isPlaying,
       albumArtBitmap = vinylCenterArt,
       isCollapsed = isVinylCollapsed,
       onToggleCollapse = { ... },
       onTogglePlayPause = { ... },
       tokens = lirixColors
   )
   ```
2. В компактном мини-диске (строка 777) обернуть `Brush.sweepGradient` в `remember(lirixColors)`:
   ```kotlin
   val miniVinylBorderBrush = remember(lirixColors) {
       Brush.sweepGradient(
           listOf(
               lirixColors.accentPrimary,
               lirixColors.accentSecondary,
               lirixColors.accentPrimary
           )
       )
   }
   ```
3. Индикатор загрузки текстов (строка 1248):
   - `CircularProgressIndicator(color = lirixColors.accentPrimary)`
   - Текст `color = lirixColors.textSecondary`
4. Рефакторинг `ExhaustedLyricsPlaceholder`:
   - Принимать `tokens: LirixColors = LirixThemeTokens.colors` (или читать `val tokens = LirixThemeTokens.colors`).
   - Иконка `MusicNote`: `tint = tokens.textMuted`.
   - Заголовок: `color = tokens.textPrimary`.
   - Описание: `color = tokens.textSecondary`.
   - Кнопка «🌐 Найти в интернете»:
     - `containerColor = tokens.accentPrimary`
     - Текст: `color = if (tokens.isDark && tokens != ColorPalettes.SpotifyMood) Color.White else Color.Black` (или контрастный цвет: `if (tokens.isDark && !tokens.isDark) ...`, для Yandex/Spotify: `if (tokens.isDark) Color.Black else Color.White` согласно стандарту темы). Проще и надежнее: `color = if (tokens.appBackground == Color.Black || tokens.isDark) Color.Black else Color.White` (или `if (tokens.isDark) Color.Black else Color.White` - в Spotify зеленый на черном, в Yandex желтый на черном, в Obsidian сиреневый на черном, в Pastel фиолетовый на белом).
   - Кнопка «🔄 Сбросить отклонённые источники»:
     - `border = BorderStroke(1.dp, tokens.accentSecondary)`
     - Текст: `color = tokens.accentSecondary`
   - Кнопка «Повторить поиск»:
     - `border = BorderStroke(1.dp, tokens.accentPrimary.copy(alpha = 0.6f))`
     - Иконка: `tint = tokens.accentPrimary`
     - Текст: `color = tokens.textPrimary`
5. Вкладка «Заметки» (NOTES tab, строки 1670–1850):
   - Заменить все `AppColors.ElectricMint` на `lirixColors.accentPrimary` или `lirixColors.accentSecondary`.
   - Заменить `AppColors.SurfaceLevel*` на `lirixColors.surfacePrimary` / `lirixColors.surfaceCard`.
   - Заменить `AppColors.BorderSubtle` на `lirixColors.borderSubtle`.
   - Заменить `AppColors.Text*` на `lirixColors.text*`.

---

## 3. Критерии приёмки (DoD)
1. Команда `./gradlew testDebugUnitTest` завершается с успехом (214+ тестов зеленые).
2. Команда `./gradlew assembleRelease` собирает валидный APK без ошибок компиляции Compose.
3. При переключении темы в диалоге («Spotify Mood», «Yandex Music», «Obsidian Pulse», «Pastel Serenity») винил мгновенно перерисовывается в цвета выбранной темы без задержек.
4. Экран отсутствия текстов (`ExhaustedLyricsPlaceholder`) полностью соответствует активной теме (в Spotify — зеленый акцент, в Yandex — яркий желтый, в Obsidian — кибер-сиреневый, в Pastel — пастельный лавандовый).
