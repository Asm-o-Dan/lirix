# Стратегический План Развития и Рефакторинга Lirix (V2 Evolution Plan)

**Статус**: APPROVED  
**Координатор**: SDD-Координатор проекта  
**Основа**: Независимый аудит кодовой базы ([`.sdd/reports/INDEPENDENT_CODEBASE_AUDIT.md`](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/reports/INDEPENDENT_CODEBASE_AUDIT.md)) и требования пользователя.

---

## 1. Концепция: Вдумчивое улучшение с заделом на будущее
Мы отказываемся от поверхностных точечных заплаток в пользу фундаментального архитектурного оздоровления платформы по 4 стратегическим направлениям (Эпикам):

```
                        [ Lirix Core Architecture ]
                                    |
      +-----------------------------+-----------------------------+
      |                             |                             |
[ Эпик 1: Мульти-Темы ]    [ Эпик 2: Декомпозиция ]    [ Эпик 3: Стабильность ]
  Spotify / Yandex /          NowPlayingScreen           MediaSession DI,
  Obsidian / Pastel           (ViewModel + Tabs)         Room, Network, I/O
```

---

## 2. Эпик 1: Система Визуальных Тем (Themes & Visual Styles)
*Исполнитель: UI/UX Дизайнер (выделенный чат `8484cbf7-2dcd-4de6-8b78-f70da9921ee4`)*

### Темы в разработке:
1. 🟢 **Spotify Mood**:
   - Матовый графит `#121212` / `#181818`
   - Неоновый скандинавский зеленый `#1DB954`
   - Минималистичные радиусы, круглые кнопки управления
2. 🟡 **Yandex Music**:
   - Глубокий темно-синий/графитовый `#0E0F12`
   - Фирменный сочный желто-янтарный `#FFCC00` / `#FF5500`
   - Кинетические акценты волны, мягкие скругления 12–16dp
3. 🟣 **Original Lirix (Obsidian Pulse)**:
   - 100% AMOLED Black `#000000`
   - HyperViolet `#B388FF` и CyberCyan `#00E5FF`
   - Киберпанк/виниловая хардварная эстетика
4. 🌸 **Pastel Serenity**:
   - Мягкий светлый пастельный фон `#F8F9FA` / `#F3EDF7`
   - Пудрово-розовый `#E8B4B8` / шалфей `#A2B9A1` / лаванда
   - Журнальная типографика, уютное чтение текстов без напряжения глаз

### Архитектурная интеграция:
- Рефакторинг `AppColors.kt` из статического синглтона в динамический `CompositionLocalProvider(LocalLirixColors provides themeColors)`.
- Сохранение выбранной темы в `SharedPreferences` / DataStore.
- Переключатель тем в настройках и меню ⋮ с визуальными кружками-палитрами.

---

## 3. Эпик 2: Декомпозиция God-Composable `NowPlayingScreen.kt` (2246 строк)
*Архитектурный задел на будущее: переход к чистому MVI / MVVM*

1. **Создание `NowPlayingViewModel`**:
   - Вынос бизнес-логики (опрос сессий, таймеры, загрузка текстов, аккордов, заметок) из UI-слоя.
   - Замена прямого чтения Room DAO в Composable на реактивные `StateFlow`.
2. **Модуляризация вкладок**:
   - `ui/player/components/TurntableHeader.kt` (винил, тонарм, свайпы)
   - `ui/player/tabs/KaraokeTabContent.kt` (синхронизированные слова, бинарный поиск строки, `derivedStateOf`)
   - `ui/player/tabs/ChordsTabContent.kt` (аккорды, транспонирование, диаграммы)
   - `ui/player/tabs/NotesTabContent.kt` (заметки к треку)
   - `ui/player/components/PlayerControlsDock.kt` (изолированный бегунок времени без рекомпозиции всего экрана!)

---

## 4. Эпик 3: Системная Стабильность и Ядро (Core Hardening)
*Устранение критических уязвимостей и сбоев, вскрытых аудитом*

1. **Сетевой уровень и префетчинг**:
   - Добавление `android.permission.ACCESS_NETWORK_STATE` в `AndroidManifest.xml` для восстановления работы авто-префетчера текстов на реальных смартфонах.
2. **Безопасность шеринга**:
   - Полная замена небезопасного `Uri.fromFile()` на устойчивый `FileProvider` в `ShareManager.kt` с защитой от крашей на Android 7+.
3. **Архитектура `MediaSessionCollector`**:
   - Устранение Split-Brain: привязка жизненного цикла к ApplicationContext через единственный экземпляр.
   - Ликвидация блокирующего `runBlocking(Dispatchers.IO)` при сохранении обложек на UI-потоке — перевод на асинхронный корутинный пайплайн.
4. **Очистка репозитория от мертвого легаси**:
   - Удаление заброшенных файлов старого проекта событий (`EventDao.kt`, `EventEntity.kt`, `domain/Event.kt`, `HistoryScreen.kt`, `LyricsViewerDialog.kt`).

---

## 5. Дорожная карта задач (Roadmap)
- [x] **AUDIT-01**: Запуск независимого аудита и составление отчета (Завершено).
- [ ] **THEME-01**: Разработка дизайн-палитр (Spotify, Yandex, Obsidian, Pastel) дизайнером.
- [ ] **THEME-02**: Архитектура `DynamicThemeProvider` и селектор тем в UI.
- [ ] **CORE-01**: Исправление сетевых разрешений, FileProvider и синглтона MediaSessionCollector.
- [ ] **REFACTOR-01**: Декомпозиция `NowPlayingScreen` и изоляция рекомпозиций бегунка времени.
