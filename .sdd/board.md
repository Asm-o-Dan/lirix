# SDD Task Board

| ID | Функция | Файл | Зона | Зависит от | Исполнитель | Статус |
|---|---|---|---|---|---|---|
| ARCH-001 | Архитектурный план пивота в Music & Lyrics Hub | .sdd/architecture.md | Arch | ADR-002 | Архитектор | DONE |
| TASK-SAFE-01 | Создание архивной ветки legacy-full-engine | git branch | Core | ARCH-001 | DevOps/Git | DONE |
| SPEC-ZONE-A | Спецификация Ingress (Filter, Debounce, Correlator) | .sdd/specs/media-ingress/overview.md | media-ingress | ARCH-001 | Спецификатор A | DONE |
| SPEC-ZONE-B | Спецификация Core (Room v6, Sessions, Tracks) | .sdd/specs/media-core/overview.md | media-core | ARCH-001 | Спецификатор B | DONE |
| SPEC-ZONE-C | Спецификация Lyrics (NetworkCondition, Providers) | .sdd/specs/lyrics-engine/overview.md | lyrics-engine | ARCH-001 | Спецификатор C | DONE |
| SPEC-ZONE-D | Спецификация Wrapped & Gamification Engine | .sdd/specs/wrapped-analytics/overview.md | wrapped-analytics | ARCH-001 | Спецификатор D | DONE |
| SPEC-ZONE-E | Спецификация UI (AMOLED 4 Tabs, Contracts) | .sdd/specs/media-ui/overview.md | media-ui | ARCH-001 | Спецификатор E | DONE |
| CONTRACTS-SYNC | Заморозка межзонных контрактов (FROZEN v1) | .sdd/contracts/*.md | Sync | SPEC-ZONE-A..E | Интегратор | DONE |
| TASK-PURGE-01 | Удаление 49 файлов legacy-балласта | multiple files | Clean | CONTRACTS-SYNC | Coder | DONE |
| TASK-DB-01 | Сущности LyricsCacheEntity и AchievementEntity | storage/MusicEntities.kt | media-core | TASK-PURGE-01 | Coder | DONE |
| TASK-DB-02 | Реализация MIGRATION_5_6 и чистый AppDatabase | storage/AppDatabase.kt | media-core | TASK-DB-01 | Coder | DONE |
| TASK-ING-01 | Строгий фильтр MediaIngressFilter | service/MediaIngressFilter.kt | media-ingress | TASK-PURGE-01 | Coder | DONE |
| TASK-ING-02 | Очистка и парсинг MusicTrackParser | music/MusicTrackParser.kt | media-ingress | TASK-ING-01 | Coder | DONE |
| TASK-LYR-01 | Определение типа сети NetworkConditionManager | feature/lyrics/NetworkConditionManager.kt | lyrics-engine | TASK-PURGE-01 | Coder | DONE |
| TASK-LYR-02 | Парсер аккордов AmDmChordParser | feature/lyrics/AmDmChordParser.kt | lyrics-engine | TASK-LYR-01 | Coder | DONE |
| TASK-WRP-01 | Движок WrappedStatsEngine | analytics/WrappedStatsEngine.kt | wrapped-analytics | TASK-PURGE-01 | Coder | DONE |
| TASK-UI-01 | Рефакторинг навигации на 4 AMOLED таба | ui/AppScaffold.kt | media-ui | TASK-PURGE-01 | Coder | DONE |
| TASK-TEST-01 | TDD Falling unit tests for Core, Ingress, Lyrics | test/... | QA | CONTRACTS-SYNC | QA | DONE |
| TASK-BUG-01 | Восстановление записи в Room (BUG-001) | Ingress -> Core -> Room | Bugfix | TASK-PURGE-01 | Coder | DONE |
| TASK-UI-02 | Синхронизированный плеер караоке LRC | ui/NowPlayingScreen.kt | media-ui | TASK-UI-01 | Coder | DONE |
| TASK-SYNC-01 | Реальная синхронизация MediaSession и калибровка | MediaSessionCollector / UI | media-sync | TASK-UI-02 | Coder | DONE |
| TASK-ART-01 | Захват и кэширование обложек альбомов | Ingress / Storage / Room v7 | media-art | TASK-SYNC-01 | Coder | DONE |
| TASK-ART-02 | Интеграция обложек в UI (Vinyl, History, Library) | ui/components / UI | media-ui | TASK-ART-01 | Coder | DONE |
| TASK-SYNC-02 | Двунаправленный транспортный контроль (Play/Pause, Seek ±10s, Click-to-Seek) | ingestion/MediaSessionCollector.kt, ui/NowPlayingScreen.kt | media-sync | TASK-ART-02 | Coder | DONE |
| TASK-BUG-02 | Фоновый prefetch текстов и dual-write синхронизация | feature/MusicFeatureEngine.kt, ingestion/MediaSessionCollector.kt, ui/NowPlayingScreen.kt | media-core | TASK-SYNC-02 | Coder | DONE |
| TASK-WRP-02 | 26 ачивок с тирами, Room v8 (MIGRATION_7_8) | storage/AppDatabase.kt, analytics/GamificationEngine.kt | wrapped-analytics | TASK-ART-02 | Coder | DONE |
| TASK-UI-03 | Аналитика таймфреймов, одержимость и WrappedScreen | analytics/WrappedStatsEngine.kt, ui/WrappedScreen.kt | media-ui | TASK-WRP-02 | Coder | DONE |
| TASK-SHR-01 | Генерация графических карточек Wrapped & NowPlaying и шеринг | feature/share/*, ui/WrappedScreen.kt, ui/NowPlayingScreen.kt | media-ui | TASK-UI-03 | Coder | DONE |
| TASK-BUG-03 | Точный динамический расчет времени прослушивания и учет активной сессии | feature/MusicFeatureEngine.kt, ingestion/MediaSessionCollector.kt, analytics/WrappedStatsEngine.kt | media-core | TASK-UI-03 | Coder | DONE |
| TASK-BUG-04 | Устранение аномального разгона времени Wrapped (строгая изоляция активной сессии) | analytics/WrappedStatsEngine.kt, feature/MusicFeatureEngine.kt | wrapped-analytics | TASK-BUG-03 | Coder | DONE |
| TASK-BUG-05 | Дедупликация и устранение x2/x3 инкремента playCount | feature/MusicFeatureEngine.kt, ingestion/MediaSessionCollector.kt | media-core | TASK-BUG-04 | Coder | DONE |
| TASK-UI-04 | Выбор и просмотр трека из Истории и Библиотеки в режиме IDLE | ui/AppScaffold.kt, ui/NowPlayingScreen.kt, ui/HistoryScreen.kt | media-ui | TASK-BUG-04 | Coder | DONE |
| TASK-BUG-06 | Отображение и функционал аккордов AmDm в NowPlaying (парсер, хранение, транспонирование) | feature/lyrics/AmDmChordParser.kt, feature/LyricsProvider.kt, ui/NowPlayingScreen.kt | lyrics-engine | TASK-BUG-04 | Coder | DONE |
| TASK-BUG-07 | Редактирование и сохранение персональных заметок к треку в NowPlaying | ui/NowPlayingScreen.kt, storage/MusicDao.kt | media-ui | TASK-BUG-04 | Coder | DONE |
| TASK-SHR-03 | Устранение пустоты и балансировка вертикальной композиции Stories 9:16 в ShareCardGenerator | feature/share/ShareCardGenerator.kt | media-ui | TASK-SHR-02 | Coder | DONE |
| ARCH-LYR-03 | Архитектура: отклонение источника, Share intent, WebView teach mode, общий реестр правил | .sdd/architecture_lyr_community.md | lyrics-engine | .sdd/intake/LYR-COMMUNITY.md | Архитектор | DONE |
| TASK-LYR-03-A | Модель данных отклонений источников и Room миграция MIGRATION_8_9 | storage/MusicEntities.kt, storage/LyricsDao.kt, storage/AppDatabase.kt | media-core | ARCH-LYR-03 | Coder | DONE |
| TASK-LYR-03-B | Каскадный опрос источников с фильтрацией отклонений в LyricsEngine | feature/LyricsProvider.kt, feature/MusicFeatureEngine.kt | lyrics-engine | TASK-LYR-03-A | Coder | DONE |
| TASK-LYR-03-C | Интерфейс отклонения текста, Undo Snackbar и экран исчерпания источников в NowPlayingScreen | ui/NowPlayingScreen.kt | media-ui | TASK-LYR-03-B | Coder | DONE |
| TASK-LYR-04-A | Модель пользовательских правил CustomLyricsRuleEntity и Room миграция MIGRATION_9_10 | storage/MusicEntities.kt, storage/LyricsDao.kt, storage/AppDatabase.kt | media-core | TASK-LYR-03-C | Coder | DONE |
| TASK-LYR-04-B | Приём текста и ссылок через Android Share Intent | AndroidManifest.xml, MainActivity.kt, ui/components/ShareLyricsAttachDialog.kt | media-ingress | TASK-LYR-04-A | Coder | DONE |
| TASK-LYR-04-C | Декларативный движок правил CustomRuleLyricsProvider и интеграция в каскад | feature/lyrics/CustomRuleLyricsProvider.kt, feature/LyricsProvider.kt | lyrics-engine | TASK-LYR-04-A | Coder | DONE |
| TASK-LYR-04-D | UI экран TeachModeScreen на Compose и WebView инспектор правил | assets/teach_inspector.js, ui/TeachModeScreen.kt, ui/AppScaffold.kt | media-ui | TASK-LYR-04-C | Coder | DONE |
| TASK-BUG-08 | Независимый поиск аккордов AmDm (включая гибрид LRCLIB + AmDm) и LyricsResult.chords | feature/LyricsProvider.kt, feature/MusicFeatureEngine.kt, ui/NowPlayingScreen.kt | lyrics-engine | TASK-LYR-04-D | Coder | DONE |
| TASK-TEACH-02 | Двухрежимная навигация, мульти-выбор блоков и составные селекторы в TeachMode | assets/teach_inspector.js, ui/TeachModeScreen.kt, feature/lyrics/CustomRuleLyricsProvider.kt | media-ui | TASK-BUG-08 | Coder | DONE |
| TASK-CHR-01 | Умная автопрокрутка текста аккордов с адаптивной скоростью и регулятором темпа | feature/lyrics/ChordAutoscrollCalculator.kt, ui/NowPlayingScreen.kt | media-ui | TASK-TEACH-02 | Coder | DONE |
| TASK-CHR-03 | Интерактивные аппликатуры аккордов на грифе и лента песен | feature/lyrics/GuitarChordDictionary.kt, feature/lyrics/GuitarFretboardDiagram.kt, feature/lyrics/GuitarChordDialog.kt | media-ui | TASK-CHR-02 | Coder | DONE |
| TASK-UI-05 | Сворачиваемый винил (Collapsible Turntable) и кинетический тонарм (Tonearm) при Play/Pause | ui/components/AnalogTurntable.kt, ui/NowPlayingScreen.kt | media-ui | TASK-CHR-03 | Coder | DONE |
| TASK-FLT-01 | Системный сервис FloatingLyricsService на SYSTEM_ALERT_WINDOW | service/FloatingLyricsService.kt | media-ui | TASK-UI-05 | Coder | DONE |
| TASK-FLT-02 | UI плавающего виджета FloatingLyricsOverlayView с жестом перетаскивания | ui/components/FloatingLyricsOverlayView.kt | media-ui | TASK-FLT-01 | Coder | DONE |
| TASK-FLT-03 | Интеграция переключателя оверлея в NowPlayingScreen и проверка разрешений | ui/NowPlayingScreen.kt | media-ui | TASK-FLT-02 | Coder | DONE |
| TASK-RUL-01 | Модель и кодек переносимых правил ScraperRuleBundleCodec с валидацией | feature/lyrics/ScraperRuleBundleCodec.kt | lyrics-engine | TASK-UI-05 | Coder | DONE |
| TASK-RUL-02 | UI диалоги экспорта и импорта правил в TeachModeScreen и LibraryScreen | ui/TeachModeScreen.kt, ui/LibraryScreen.kt | media-ui | TASK-RUL-01 | Coder | DONE |
| TASK-LRC-01 | Движок LrcSyncEngine и валидация формата таймкодов караоке | feature/lyrics/LrcSyncEngine.kt | lyrics-engine | TASK-RUL-02 | Coder | DONE |
| TASK-TG-01 | Детерминированное управление Play/Pause и скоринг MediaController | ingestion/MediaSessionCollector.kt | media-ingress | SPEC-TG-01 | Coder | DONE |
| TASK-REV-TG-01 | Ревью исправления паузы Telegram (Gate 7) | .sdd/reports/BUG_TG_01.review.md | review | TASK-TG-01 | Ревьюер | DONE |
| ARCH-UI-06 | Архитектурный план разгрузки NowPlayingScreen и Auto-Hide Floating Capsule | .sdd/architecture_ui_capsule.md | media-ui | ADR-007 | Архитектор | DONE |
| SPEC-UI-06 | Спецификация меню ⋮ и плавающего навбара | .sdd/specs/media-ui/overflow_and_capsule.md | media-ui | ARCH-UI-06 | Спецификатор E | DONE |
| TASK-UI-06-A | Компонент NowPlayingOverflowMenu и диалог калибровки | ui/components/NowPlayingOverflowMenu.kt | media-ui | SPEC-UI-06 | Coder | DONE |
| TASK-UI-06-B | Рефакторинг NowPlayingScreen: очистка шапки, перенос кнопок в меню | ui/NowPlayingScreen.kt | media-ui | TASK-UI-06-A | Coder | DONE |
| TASK-UI-06-C | Рефакторинг AppScaffold: авто-скрывающийся плавающий навбар-капсула | ui/AppScaffold.kt | media-ui | TASK-UI-06-B | Coder | DONE |
| TASK-REV-06 | Ревью, сборка Release APK и проверка на устройстве | .sdd/reports/UI-06.review.md | review | TASK-UI-06-C | Ревьюер / QA | DONE |
| ARCH-GST-01 | Архитектурный проект системы жестов (HorizontalPager, Vinyl Swipe, DoubleTap) | .sdd/architecture_gestures.md | Arch | ADR-008 | Архитектор | DONE |
| SPEC-GST-01 | Спецификация жестов навигации и управления | .sdd/specs/media-ui/gestures_spec.md | media-ui | ARCH-GST-01 | Спецификатор | DONE |
| TASK-GST-01-A | Реализация skipToNext и skipToPrevious в MediaSessionCollector | ingestion/MediaSessionCollector.kt | media-ingress | SPEC-GST-01 | Coder | DONE |
| TASK-GST-01-B | HorizontalPager и свайп вкладок режимов в NowPlayingScreen | ui/NowPlayingScreen.kt | media-ui | SPEC-GST-01 | Coder | DONE |
| TASK-GST-01-C | Свайпы по винилу (Track Next/Prev) и дабл-тап по карточке (Play/Pause) | ui/NowPlayingScreen.kt, ui/components/AnalogTurntable.kt | media-ui | TASK-GST-01-A..B | Coder | DONE |
| TASK-REV-GST-01 | Ревью, прогон тестов, сборка APK и верификация жестов | .sdd/reports/GST-01.review.md | review | TASK-GST-01-C | Ревьюер / QA | DONE |
| TASK-LIB-02 | Полнотекстовый поиск по текстам песен и заметкам со сниппетами | ui/LibraryScreen.kt | media-ui | TASK-LIB-01 | Coder | DONE |
| ARCH-BUG-09 | Архитектура: точный поиск seekTo-контроллера и модель жизненного цикла сессий без паузы | .sdd/architecture_seek_and_pause.md | Arch | ADR-009 | Архитектор | DONE |
| SPEC-BUG-09A | Спецификация резолвинга MediaController с ACTION_SEEK_TO в MediaSessionCollector | .sdd/specs/media-ingress/seek_controller_resolution.md | media-ingress | ARCH-BUG-09 | Спецификатор A | DONE |
| SPEC-BUG-09B | Спецификация предотвращения накрутки playCount при паузе в MusicFeatureEngine | .sdd/specs/media-core/pause_session_lifecycle.md | media-core | ARCH-BUG-09 | Спецификатор B | DONE |
| TASK-BUG-09A | Реализация выбора seekTo контроллера в MediaSessionCollector.kt | ingestion/MediaSessionCollector.kt | media-ingress | SPEC-BUG-09A | Coder A | DONE |
| TASK-BUG-09B | Реализация корректного статуса сессии при PAUSED в MusicFeatureEngine.kt | feature/MusicFeatureEngine.kt | media-core | SPEC-BUG-09B | Coder B | DONE |
| TASK-QA-09 | Тесты на сохранение playCount=1 при паузе >60с и seekTo controller selection | test/MusicDatabaseTest.kt | QA | TASK-BUG-09A..B | QA | DONE |
| TASK-REV-09 | Ревью кода, сборка Release APK и проверка на устройстве | .sdd/reports/BUG-09.review.md | review | TASK-QA-09 | Ревьюер | DONE |
| TASK-UI-07 | Видимость кнопок «Шаг назад» и «Сбросить» в LrcTapSyncStudioDialog (safe insets & padding) | ui/components/LrcTapSyncStudioDialog.kt | media-ui | TASK-REV-09 | Coder (UI) | DONE |
| SPEC-ING-03 | Спецификация арбитража сессий и подавления зомби-сессий | .sdd/specs/media-ingress/target_session_arbitration.md | media-ingress | ADR-010 | Спецификатор A | DONE |
| TASK-ING-03 | Реализация арбитража сессий и защиты активного плеера при старте | ingestion/MediaSessionCollector.kt, ingestion/NotificationListener.kt | media-ingress | SPEC-ING-03 | Coder A | DONE |



