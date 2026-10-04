## Модуль: MediaUiScaffold   Зона: media-ui   Версия спеки: v1   Статус: APPROVED

### Назначение
Модуль реализует 4-вкладочный пользовательский интерфейс приложения на базе Jetpack Compose в дизайн-системе Obsidian Pulse AMOLED (фон `#000000`, акценты Hyper Violet `#7C4DFF` / `#B388FF`, поддержка 120 Гц). Модуль НЕ выполняет прямых обращений к дисковым файлам или сети, получая данные исключительно через StateFlow от `media-core`, `lyrics-engine` и `wrapped-analytics`.

---

### Типы данных
- `AppTab` — перечисление: `NOW_PLAYING` (Сейчас играет), `HISTORY` (История), `LIBRARY` (Библиотека), `WRAPPED` (Итоги & Ачивки).
- `NowPlayingUiState` — поля: `activeTrack: TrackItemUiModel?`, `isPlaying: Boolean`, `playbackPositionMs: Long`, `durationMs: Long`, `lyricsState: LyricsDisplayState`, `displayMode: LyricsViewMode`, `isFavorite: Boolean`, `isWifiConnected: Boolean`.
- `HistoryUiState` — поля: `groupedSessions: Map<String, List<HistorySessionItemUiModel>>`, `searchQuery: String`, `isLoading: Boolean`.
- `LibraryUiState` — поля: `tracks: List<LibraryTrackUiModel>`, `activeFilter: LibraryFilter`, `searchQuery: String`, `selectedArtist: String?`.
- `WrappedUiState` — поля: `activeTimeRange: WrappedTimeRange`, `stats: WrappedStats?`, `achievements: List<AchievementUiModel>`, `unlockedCount: Int`, `isExportingCard: Boolean`, `exportedCardUri: String?`.

---

### Публичный API

#### 1. Компонент `AppScaffold(viewModel: MediaHubViewModel)`
- **Сигнатура:** `@Composable fun AppScaffold(viewModel: MediaHubViewModel): Unit`
- **Предусловия:** `viewModel` инициализирован и подключен к репозиториям.
- **Постусловия:** Отрисовывает корневой контейнер с нижней панелью навигации (4 таба) и анимированным переключением экранов через `Crossfade`.
- **Поведение:**
  1. Читает `currentTab` из локального запоминаемого состояния `rememberSaveable`.
  2. Применяет цвет подложки `AppColors.AmoledBlack` (`#000000`).
  3. Отрисовывает нижний `NavigationBar` с 4 пунктами:
     - 🎵 «Сейчас играет» (`Icons.Default.PlayCircle` / `Icons.Outlined.PlayCircleOutline`)
     - 📜 «История» (`Icons.Default.History` / `Icons.Outlined.History`)
     - 📚 «Библиотека» (`Icons.Default.LibraryMusic` / `Icons.Outlined.LibraryMusic`)
     - 🏆 «Итоги & Ачивки» (`Icons.Default.EmojiEvents` / `Icons.Outlined.EmojiEvents`)
  4. При клике на вкладку воспроизводит короткий тактильный отклик `HapticFeedbackType.TextHandleMove`.
  5. Переключает активное содержимое через `Crossfade(targetState = currentTab, animationSpec = tween(200))`.
- **Ошибки:** исключений не бросает.
- **Побочные эффекты:** тактильная вибрация при клике.
- **Граничные случаи:** Высокая частота кликов $\to$ анимация смены экрана плавно прерывается без артефактов.
- **Примеры:**
  1. Запуск приложения $\to$ активна вкладка «Сейчас играет».
  2. Клик на «История» $\to$ переключение на `HistoryScreen` с вибрацией.
  3. Клик на «Итоги & Ачивки» $\to$ переключение на `WrappedScreen`.

#### 2. Компонент `KaraokeLyricsScroller(syncedLines: List<KaraokeLineUiModel>, currentPositionMs: Long, onLineClick: (Long) -> Unit)`
- **Сигнатура:** `@Composable fun KaraokeLyricsScroller(syncedLines: List<KaraokeLineUiModel>, currentPositionMs: Long, onLineClick: (Long) -> Unit): Unit`
- **Предусловия:** `syncedLines` отсортированы по `timestampMs`.
- **Постусловия:** Отрисовывает вертикальный список строк караоке с автоматической центровкой активной строки на экране.
- **Поведение:**
  1. Вычисляет индекс активной строки: наибольший индекс `i`, для которого `syncedLines[i].timestampMs <= currentPositionMs`.
  2. При изменении активного индекса запускает `LaunchedEffect(activeIndex)`:
     `lazyListState.animateScrollToItem(index = (activeIndex - 2).coerceAtLeast(0), scrollOffset = 0)`.
  3. Активная строка выделяется крупным жирным шрифтом и цветом `AppColors.HyperViolet` (`#B388FF`), неактивные отображаются полупрозрачным `AppColors.TextSecondary`.
  4. При клике на строку вызывает `onLineClick(line.timestampMs)`.
- **Ошибки:** исключений не бросает.
- **Побочные эффекты:** плавная анимация прокрутки LazyColumn.
- **Граничные случаи:** `syncedLines.isEmpty()` $\to$ отображается информационное сообщение о недоступности караоке.
- **Примеры:**
  1. Позиция $15$ с, строка начинается в $14$ с $\to$ строка подсвечивается и центрируется.
  2. Перемотка назад $\to$ скроллер мгновенно возвращается к предыдущей строке.
  3. Пустой список $\to$ показ заглушки без сбоев.

#### 3. Компонент `AchievementBadgeItem(achievement: AchievementUiModel, onClick: () -> Unit)`
- **Сигнатура:** `@Composable fun AchievementBadgeItem(achievement: AchievementUiModel, onClick: () -> Unit): Unit`
- **Предусловия:** `achievement != null`.
- **Постусловия:** Отрисовывает квадратную карточку достижения со статусом разблокировки.
- **Поведение:**
  1. Если `achievement.isUnlocked == true`:
     - Иконка окрашивается в золотой цвет `#FFD700` с неоновым свечением.
     - Фон карточки — темно-фиолетовый `#1A1026`.
     - Прогресс-бар скрывается, отображается дата разблокировки.
  2. Если `achievement.isUnlocked == false`:
     - Иконка монохромная серая `#555555`.
     - Отображается линейный индикатор прогресса `LinearProgressIndicator(progress = achievement.progressPercentage)`.
     - Если `achievement.isSecret == true`, название скрыто знаками `???`.
- **Ошибки:** исключений не бросает.
- **Побочные эффекты:** нет.
- **Граничные случаи:** `maxProgress == 0` $\to$ защита от деления на ноль (`progressPercentage = 0f`).
- **Примеры:**
  1. Разблокированная ачивка «Первый бит» $\to$ золотая иконка, статус «Получено».
  2. В процессе «Сотник» (45/100) $\to$ прогресс-бар 45%, серая иконка.
  3. Секретная ачивка $\to$ скрытое описание «Секретное достижение».

---

### Зависимости
- Межзонный контракт: [.sdd/contracts/core__ui.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/contracts/core__ui.md)
- Внешние библиотеки: `androidx.compose.material3:material3:1.3.1`, `androidx.compose.animation:animation`.

---

### Вне скоупа
- Прямое выполнение SQL-запросов (только через ViewModel).
- Прямое управление сетевым клиентом HttpTextClient.
- Работа с низкоуровневыми системными BroadcastReceiver.
