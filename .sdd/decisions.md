# SDD Decisions Log

## ADR-001: Adoption of Spec Driven Design (SDD) Workflow
- **Date:** 2026-09-26
- **Status:** APPROVED
- **Context:** Transition project governance to strict Spec Driven Design model.
- **Decision:** All features must proceed through intake, architecture, specification, contract sync, test design, single-function implementation, and review gates.
- **Consequences:** Coordinator does not read/write product code directly; tasks are partitioned to 1 function per task.

## ADR-002: Pivot and Descope to Pure Music & Lyrics Hub with Gamified Wrapped
- **Date:** 2026-10-01
- **Status:** APPROVED (Gate 0 Passed via /grill-me)
- **Context:** The user explicitly requested to completely narrow the application scope to media/music playback tracking and lyrics fetching. Eliminate all financial, banking, study, and non-media features. Everything not related to media must be ignored.
- **Decisions (agreed in /grill-me):**
  1. **Ingestion & DB Clean Slate:** Drop all non-media notifications at `NotificationListener` ingress. Purge finance/study tables from Room; retain only music & lyrics schema (`music_tracks`, `music_listening_sessions`, `lyrics_cache`, `achievements`).
  2. **Legacy Code Archive:** Create git branch `legacy-full-engine` to freeze the prior multi-domain implementation, then delete dead finance/study code from master/main.
  3. **Media Sources:** Ingest any audio/video with identifiable (Artist - Track) metadata (Spotify, Yandex Music, VK, YouTube Music, ReVanced, AIMP, audiobooks).
  4. **Network-Aware Lyrics Engine:** Automatic background lyrics pre-fetching and caching when on Wi-Fi; on-demand (lazy) when on cellular mobile data. Supports synced LRC karaoke, plain lyrics, and AmDm guitar chords.
  5. **4-Tab AMOLED UI:**
     - Tab 1: 🎵 «Сейчас играет» (Player, cover art, synced LRC karaoke, AmDm chords).
     - Tab 2: 📜 «История» (Chronological stream of listened tracks with search and play counters).
     - Tab 3: 📚 «Библиотека» (Offline cached lyrics, favorites, artist catalogue).
     - Tab 4: 🏆 «Итоги & Ачивки» (Music Wrapped stats: total time, top artists, listening personality, gamification badges, local privacy-first computation + Share Card image export).
- **Consequences:** Codebase size reduced by ~40%, zero financial/banking complexity, focused 100% on music tracking, lyrics, and gamified listening stats.

## ADR-003: Approval of Architectural Baseline ARCH-001 (Gate 1 Passed)
- **Date:** 2026-10-01
- **Status:** APPROVED (Gate 1 Passed)
- **Context:** Completion of `ARCH-001` by Architect. Review of architecture specification `.sdd/architecture.md`.
- **Verdict:**
  1. Five distinct zones identified and bounded without overlap (media-ingress, media-core, lyrics-engine, wrapped-analytics, media-ui).
  2. Safe purge registry of 49 files verified against domain requirements.
  3. Git safety net verified: branch `legacy-full-engine` created.
  4. Room v6 migration path (`MIGRATION_5_6`) verified.
  5. Inter-zone integration points mapped.
- **Next Phase:** Transition to Phase 2 (Specification per zone) and Phase 3 (Contract Sync).

## ADR-004: Approval of Specifications & Frozen Contracts (Gate 2, 3 & 4 Passed)
- **Date:** 2026-10-01
- **Status:** APPROVED (Gates 2, 3, 4 Passed)
- **Context:** Completion of zone specifications (`.sdd/specs/media-ingress/`, `media-core/`, `lyrics-engine/`, `wrapped-analytics/`, `media-ui/`), freezing of 4 cross-zone contracts in `.sdd/contracts/` (`FROZEN v1`), and task decomposition into `.sdd/tasks/`.
- **Verdict:**
  1. Gate 2 (DoR Specifications): All 5 zone specifications conform strictly to SDD §5 template.
  2. Gate 3 (Contract Sync): Inter-zone contracts are synchronous, strongly typed, and frozen.
  3. Gate 4 (Decomposition DoR): Task cards partitioned to 1 deterministic function per file. No cyclic dependencies in DAG.
- **Next Phase:** Transition to Phase 5 (QA Test Design: red tests) and Phase 6 (Coder Implementation: starting with `TASK-PURGE-01`).

## ADR-005: Comprehensive Verification (Gates 6 & 7 Passed) & Release Build
- **Date:** 2026-10-01
- **Status:** APPROVED (Gates 6 & 7 Passed, Gate 8 Ready)
- **Context:** Completion of all tasks across 5 zones (Ingress, Core, Lyrics, Wrapped, UI), execution of full unit test suite (64/64 tests green), and successful compilation of debug APK.
- **Verdict:**
  1. Gate 6 (Implementation DoD): All 10 development tasks verified DONE. 64 out of 64 unit tests passed (100% success rate, 0 failures).
  2. Gate 7 (Review): Code matches frozen contracts and specifications without regressions or extraneous changes.
  3. Release Artifact: `app/build\outputs\apk\debug\app-debug.apk` built successfully with Java 21 Temurin.
- **Next Phase:** Phase 8 Acceptance & Device Deployment.

## ADR-006: Production Release v1.1.1 Signing, Permission Onboarding Flow, and Play Protect Hardening
- **Date:** 2026-10-04
- **Status:** APPROVED (Gates 6, 7 & 8 Passed)
- **Context:** User reported that sideloaded release APK did not prompt for notification permissions on initial launch, preventing now-playing media detection, and was blocked/warned by Google Play Protect and Xiaomi Security due to debug keystore signing and cleartext traffic flags.
- **Root Cause:**
  1. `release.yml` mistakenly built `assembleDebug` and renamed `app-debug.apk` with `1.0.0-debug` version and default Android debug key (`CN=Android Debug`).
  2. `NotificationListener.isPermissionGranted` was never checked on app launch to prompt the user, leaving the app in IDLE state without guidance.
  3. Android 13+ (API 33+) marks sideloaded notification listeners as "Restricted Settings" by default, preventing toggle without unlocking in App Info.
- **Decisions:**
  1. **Release Keystore & Versioning:** Generated `app/lirix-release.jks` (RSA 2048, 10,000 days validity, v1/v2/v3 signatures). Bumped `versionCode = 2`, `versionName = "1.1.1"`. Configured `signingConfigs.release` in `app/build.gradle.kts`.
  2. **Security Hardening:** Removed `android:usesCleartextTraffic="true"` from `AndroidManifest.xml` (all network calls use HTTPS).
  3. **Permission Onboarding UX:** Built `NotificationPermissionDialog` and interactive IDLE card in `NowPlayingScreen` with direct intents to notification settings and step-by-step guidance for Android 13+ "Restricted Settings" unlock. Added lifecycle resume re-check.
  4. **CI/CD Workflow Update:** Updated `.github/workflows/release.yml` to compile `assembleRelease` and package signed `app-release.apk`.
- **Verdict:** All unit tests green. Production release build succeeds and passes `apksigner` verification with v2/v3 schemes.

## ADR-008: Gesture-Driven Navigation and Playback Control (Gate 0 Passed)
- **Date:** 2026-10-06
- **Status:** APPROVED (Gate 0 Passed via /grill-me)
- **Context:** User requested natural swipe and touch gesture support to reduce reliance on small click targets.
- **Decisions:**
  1. **Mode Tabs Swipe:** Migrate `NowPlayingScreen` content container to `HorizontalPager(state = pagerState)`. Mode tabs in segmented control mirror pager state bidirectionally with haptic tick.
  2. **Track Skipping Gesture:** Horizontal swipe gestures across the vinyl turntable dispatch `skipToNext()` (swipe left) and `skipToPrevious()` (swipe right).
  3. **Double-Tap Playback:** Double-tap on the lyrics/chords content card toggles `Play/Pause`.
  4. **IPC Ingress Safety:** Implement `skipToNext()` and `skipToPrevious()` in `MediaSessionCollector` using `resolveTargetController()` with single-controller targeting and `dispatchMediaButtonEvent` fallback.
- **Next Phase:** Transition to Phase 1 (Architecture) & Phase 2 (Specification).

## ADR-009: Telegram SeekTo Controller Resolution & PlayCount Pause Inflation Prevention
- **Date:** 2026-10-06
- **Status:** APPROVED (Gate 0 Passed)
- **Context:**
  1. Seeking in Telegram/AyuGram external player fails because multiple MediaControllers exist (`MediaSessionHelper` without `ACTION_SEEK_TO` vs `telegramAudioPlayer` with `ACTION_SEEK_TO`). Collector picked the first one blindly.
  2. Pausing track marked session `isCompleted = true`, causing subsequent resumption to trigger `isTrackReplayAfterCompletion = true` and falsely incrementing `playCount`.
- **Decisions:**
  1. `MediaSessionCollector.seekTo`: Query controllers for package, filter by `(actions and PlaybackState.ACTION_SEEK_TO) != 0L`, and dispatch `seekTo` to capable controller(s).
  2. `MusicFeatureEngine`: Exclude `PAUSED` from `isCompleted = true` (only `STOPPED` marks completion). Track resumption must not increment `playCount`.
- **Next Phase:** Transition to Phase 1 (Architecture & Delegation to Subagents).

## ADR-010: Target MediaSession Arbitration and Ghost/Zombie Session Suppression
- **Date:** 2026-10-06
- **Status:** APPROVED (Gate 0 Passed)
- **Context:**
  When opening Lirix, an inactive/closed session (e.g. YouTube in PAUSED/STOPPED state without notification in status bar) overwrote the active playing Telegram session in `_livePlaybackFlow`.
  Root causes:
  1. `MediaSessionCollector.updateControllers()` looped over all controllers and called `handleMetadataChange()` for each, causing the last processed controller to blindly overwrite `_livePlaybackFlow.value`, regardless of playback state.
  2. Non-playing controllers were allowed to overwrite active playing sessions (`isPlaying == true`) of other apps.
  3. When media notifications are dismissed in the system notification shade, dead sessions lingered as zombie snapshots.
- **Decisions:**
  1. **Strict Arbitration in `updateControllers`**: Register callbacks for all controllers, but select the initial snapshot using `resolveTargetController(controllers)` — playing sessions (`STATE_PLAYING`) have absolute priority (+10000 score).
  2. **Active Playback Protection**: A non-playing controller event (`isPlaying == false`) must never overwrite `_livePlaybackFlow` if the current snapshot is playing (`current.isPlaying == true`) from another package.
  3. **Notification Removal Sync**: When a notification is dismissed for a package, if that package was shown in `_livePlaybackFlow` and is not playing, clear it or fall back to an active playing session.
- **Next Phase:** Transition to Phase 2 (Specification SPEC-ING-03) and Phase 4 (Task Card TASK-ING-03).

## ADR-011: Comprehensive Ingress Robustness: Skip Controller Selection, Session Cleanup & Lifecycle Leaks Fix
- **Date:** 2026-10-06
- **Status:** APPROVED (Gate 0 Passed via /goal)
- **Context:**
  Systematic audit of similar edge cases revealed:
  1. `skipToNext()` and `skipToPrevious()` used generic `resolveTargetController()`, which fails in multi-session apps (Telegram/AyuGram) where one controller lacks `ACTION_SKIP_TO_NEXT`/`PREVIOUS`.
  2. `updateControllers()` failed to clean up removed controllers when apps close (`controllers.isNullOrEmpty()` early return), leaving dead references and leaking callbacks in `activeControllers`.
  3. `ShareLyricsAttachDialog` modal list could overflow screen vertically on compact devices when keyboard is opened.
- **Decisions:**
  1. **Specialized Skip Controller Selection**: Implement `resolveSkipController(controllers, isNext: Boolean)` filtering by `PlaybackState.ACTION_SKIP_TO_NEXT` and `ACTION_SKIP_TO_PREVIOUS`.
  2. **Deterministic Lifecycle Cleanup**: Unregister callbacks and prune `activeMediaControllers`/`activeControllers` when sessions disappear. Transition to IDLE snapshot when all sessions terminate.
  3. **Dialog Safety**: Constrain `ShareLyricsAttachDialog` recent tracks list to prevent vertical overflow.
- **Next Phase:** Transition to Specification & Task Decomposition.

## ADR-012: Ремонт WebView Инспектора и Сквозная Привязка Правил Обучения (Teach Mode)

### Контекст
Пользователь столкнулся с двумя критическими проблемами в режиме обучения (`TeachMode`):
1. **Инспектор элементов не реагирует на тапы на мобильном устройстве:**
   В `app/src/main/assets/teach_inspector.js` слушатель зарегистрирован только на событие `click`. На мобильных устройствах WebView событие `click` эмулируется с задержкой 300мс, либо блокируется сайтами со сложной иерархией через `stopPropagation`/`pointer-events`. Необходим перехват `pointerup` / `touchend` на стадии capture с вычислением элемента через `document.elementFromPoint`.
2. **«Сохранить правило» не подгружает текст песни в плеере:**
   - В `NowPlayingScreen.kt` меню «Режим обучения» передает `onOpenTeachMode?.invoke()`, а `AppScaffold.kt` передает в `TeachModeScreen` параметр `targetTrack = viewingTrack`, который в момент воспроизведения равен `null`. Из-за этого скрыта кнопка «Привязать к треку», а кнопка «Сохранить правило» сохраняет селектор в базу, но не привязывает извлеченный текст к треку и кэшу.
   - Кроме того, в `NowPlayingScreen.kt` экземпляры `AggregatedLyricsProvider()` вызываются с конструктором по умолчанию без передачи `lyricsDao`, поэтому кастомные правила из БД вообще не опрашиваются при загрузке песни.

### Решение
1. **Ingress & Inspector (`teach_inspector.js`)**:
   - Добавить слушатели `touchend` и `pointerup` на фазе capture (`true`) для надежного захвата целевого элемента (`elementFromPoint`) при касании пальцем на мобильном экране.
2. **UI Navigation Flow (`NowPlayingScreen.kt` + `AppScaffold.kt`)**:
   - Обновить сигнатуру `onOpenTeachMode: ((TrackEntity?) -> Unit)?` и передавать `currentTrack`.
   - В `AppScaffold` сохранять переданный `activeTeachTrack` и пробрасывать его в `TeachModeScreen(targetTrack = activeTeachTrack ?: viewingTrack)`.
3. **Core & Cache Integration (`TeachModeScreen.kt`)**:
   - При нажатии «Сохранить правило»: если есть `selectedText` и `targetTrack`, выполнять атомарную запись в `music_tracks` (`musicDao().updateLyrics`) и `lyrics_cache` (`lyricsDao().saveLyrics`), вызывать `onLyricsAttached(selectedText)` и закрывать экран, чтобы текст мгновенно появился в плеере.
4. **Provider Wiring (`NowPlayingScreen.kt` & `MusicFeatureEngine.kt`)**:
   - Передавать `db.lyricsDao()` во все экземпляры `AggregatedLyricsProvider(lyricsDao = db.lyricsDao())`.

## ADR-013: Внедрение резервного парсера текстов и аккордов MyChords.net

### Контекст
Пользователь запросил:
«https://mychords.net/ru/dai_darogu/275290-day-darogu-supersession.html
Надо сделать парсер текстов и аккордов с этого сайта если с амдм не получится»

AmDm.ru является ценным источником, однако многие современные и независимые треки отсутствуют в его базе или имеют неполную верстку. MyChords.net содержит огромную базу русскоязычных и зарубежных треков с аккордами и удобной версткой.

### Архитектурное Решение
1. **Зона `lyrics-engine` (`MyChordsParser.kt`)**:
   - Создать независимый утилитный парсер `MyChordsParser.kt` по аналогии с `AmDmChordParser.kt`.
   - Логика извлечения:
     - Находит блок `<div[^>]*class=["'][^"']*w-words__text[^"']*["'][^>]*>([\s\S]*?)</div>`.
     - Преобразует строчные контейнеры `<div class="pline">` и `<span class="subline">` в сплошной текст с сохранением переносов строк и точных пробелов для выравнивания аккордов над текстом.
     - Очищает оставшиеся HTML-теги (`<[^>]+>`) и экранирует HTML-сущности (`&nbsp;`, `&quot;`, `&amp;` и др.).
     - Возвращает как чистый текст песни (`plainLyrics`), так и текст с аккордами (`chords`).
2. **Scraper & Cascade Integration (`LyricsProvider.kt`)**:
   - Добавить идентификатор источника: `LyricsSourceIds.MYCHORDS = "builtin:mychords"`.
   - Реализовать метод `scrapeMyChordsDetails(artist: String, title: String): MyChordsScrapeResult?` в `FallbackLyricsScraper`:
     - Поиск через официальный endpoint автодополнения: `https://mychords.net/ru/ajax/autocomplete?q={artist+title}` (заголовок `X-Requested-With: XMLHttpRequest`).
     - Если автодополнение находит URL песни, загружает страницу песни и парсит через `MyChordsParser`.
     - Если автодополнение не сработало, производит резервный поиск через поисковую форму `https://mychords.net/ru/search?q={query}`.
   - Включить `MyChords` в каскад:
     - В `getLyrics`: если AmDm не нашел текст или отклонен (`LyricsSourceIds.AMDM`), опрашивается `MyChords`.
     - В гибридном режиме поиска аккордов (`fetchAmDmChords` -> расширить до `fetchChordTabs`): если трек найден в LRCLIB или другом источнике, но у него нет аккордов, сначала проверяется AmDm, а если аккорды не найдены — проверяется MyChords.net!
3. **Безопасность и автономность**:
   - Полная изоляция от сетевых сбоев (`try-catch`, логгирование через Timber).
   - Поддержка в TDD unit-тестах.

## ADR-014: Интерактивный Двухслойный Инспектор (Parent + Strip), Разгрузка Шапки и Типизация Аккордов

### Контекст
По результатам UI/UX аудита выявлены критические проблемы эргономики и надежности `TeachModeScreen` и `teach_inspector.js`:
1. **Ложные нажатия и срыв выделения при скролле**: Отсутствие фильтрации скролла вызывало срабатывание инспектора при обычном листании страницы. Двойное срабатывание (`touchend` + `click`) вызывало мгновенный сброс выделения.
2. **Слепой подъем к родителю**: Принудительный пропуск тегов `SPAN` приводил к захвату гигантских контейнеров вместо конкретных текстовых блоков.
3. **Отсутствие визуального исключения потомков**: Невозможность исключить рекламные блоки и кнопки из выбранного родительского контейнера.
4. **Визуальная перегрузка шапки и нижней панели**: Кнопки импорта, экспорта, сброса и привязки загромождали интерфейс на мобильных экранах.

### Решение
1. **Стабилизация тач-ввода (`teach_inspector.js`)**:
   - Детекция скролла: при смещении пальца $> 8\text{px}$ в `touchmove` жест помечается как прокрутка, событие инспекции в `touchend` игнорируется.
   - Защита от дребезга (300мс) против дублирующих `pointerup`/`click`.
   - Точный захват узлов без слепого пропуска `SPAN`.
2. **Двухслойный выбор (Родитель + Исключение потомков)**:
   - Клик по странице: выделение контейнера (🟣 Фиолетовый контур).
   - Клик внутри уже выделенного контейнера: пометка элемента как вырезанного (🔴 Красный контур с зачеркиванием).
   - Вырезанные элементы автоматически заносятся в `stripSelectors` и удаляются из превью текста.
   - Кнопка `[▲ Родитель]` для удобного подъема на уровень выше.
3. **Разгрузка интерфейса через меню ⋮ (Overflow Menu)**:
   - В шапке остаются только: Назад/Закрыть, контекст трека/домена, переключатель [Серфинг / Инспектор] и кнопка ⋮.
   - В меню ⋮ переносятся: Импорт правила, Экспорт правила, Полный сброс, Открыть в браузере.
4. **Типизация данных и единая кнопка действия**:
   - Сегментированный переключатель: **[📝 Текст] / [🎸 Аккорды]**.
   - Единая акцентная кнопка: **«Сохранить и привязать к треку»**, сохраняющая правило в БД с автогенерацией `search.urlTemplate` и сразу привязывающая результат к активной песне.

## ADR-015: Полное и бесшовное покрытие дизайн-токенами (Vinyl, Wrapped, Chords, Tap-to-Sync, Overlay)

### Контекст
В ходе тестирования 4 цветовых тем (Spotify Mood, Yandex Music, Obsidian Pulse AMOLED, Pastel Serenity) на физическом устройстве выявлены остаточные очаги немигрированного хардкода `AppColors.*`:
1. Виниловый проигрыватель и звукосниматель (`AnalogTurntable.kt`): статичный фиолетово-бирюзовый градиент обода, фиолетовый картридж, бирюзовая игла.
2. Экран Музыкального Wrapped (`WrappedScreen.kt`): 81 жесткое вхождение старых цветов (карточка архетипа, фильтры периода, компас активности, сетка метрик, кнопка «ПОСТЕР»).
3. Кнопка создания караоке «⏱ Создать караоке (Tap-to-Sync)» в `NowPlayingScreen.kt`: захардкоженные цвета `HyperViolet` и `CyberCyan`.
4. Вкладка Аккордов (`NowPlayingMode.CHORDS`): бирюзовый цвет основного текста аккордов, неконтрастный в светлой теме, захардкоженные кнопки автоскролла и транспонирования.
5. Плавающий системный оверлей (`FloatingLyricsOverlayView.kt`): отсутствие реактивной обертки `LirixTheme`, 33 захардкоженных вызова старых цветов.

### Решение
1. `AnalogTurntable.kt`: динамические градиенты обода винила и шпинделя на базе `LirixThemeTokens.colors.accentPrimary`, `accentSecondary`, `accentGlow`; стилизация картриджа и иглы звукоснимателя; тема для кнопки скрытия/показа винила.
2. `WrappedScreen.kt`: полная замена всех 81 вхождений `AppColors.*` на `LirixThemeTokens.colors` (адаптивный фон баннера архетипа, карточки метрик, цветные шкалы компаса активности).
3. `NowPlayingScreen.kt` (Chords & Tap-to-Sync): текст аккордов `lirixColors.textPrimary` (читается на любом фоне), тулбар и кнопки управления адаптированы под акценты темы; кнопка Tap-to-Sync оформлена в `lirixColors.surfaceCard` + `lirixColors.accentPrimary`.
4. `FloatingLyricsOverlayView.kt`: внедрение реактивного наблюдения за `ThemePreferencesRepository.selectedTheme`, оборачивание контента в `LirixTheme(themeType = currentTheme)`, полная миграция 33 вызовов на `LirixThemeTokens.colors`.

## ADR-016: Система Черного Списка и Конструктор Запретов Медиатеки (Blacklist Rules & Auto-Purge)

### Контекст
Пользователь запросил новую ключевую возможность:
«возможность блокировать сохранение записей в медиатеке по названию или по автору или по провайдеру(приложению) для этого нужен отдельный экран настроек с конструктором запретов при с создании запрета идёт проверка всей медиатеки и удаляется все что попадает под данное правило»

### Архитектурные Решения
1. **Зона `storage` & `media-core`**:
   - Новая сущность Room: `BlacklistRuleEntity` (id, ruleType: TITLE/ARTIST/PACKAGE_NAME, pattern, matchMode, isEnabled, createdAt).
   - Миграция схемы базы данных Room: v10 -> v11 (`MIGRATION_10_11`).
   - `BlacklistDao`: управление правилами (CRUD, Flow).
   - Атомарная процедура каскадной очистки медиатеки `purgeMatchingTracks(rule)`: удаление подходящих записей из `music_tracks` и `music_listening_sessions`.
2. **Зона `media-ingress` & `media-core`**:
   - Фильтр реального времени: `BlacklistFilterManager` проверяет входящие события воспроизведения. Если трек подпадает под активное правило — блокируется его сохранение в Room и учет во Wrapped-статистике.
3. **Зона `media-ui`**:
   - Новый экран `BlacklistSettingsScreen` с конструктором правил (выбор типа критерия, подсказки системных пакетов плееров, превью количества удаляемых треков перед подтверждением).
   - Интеграция перехода на экран настроек из Overflow Menu (⋮) и из экрана Медиатеки.
   - 100% соответствие дизайн-системе `LirixThemeTokens.colors` (4 темы).
4. **Контроль качества**:
   - TDD unit-тесты миграции Room, фильтрации и каскадного удаления.

## ADR-017: UI/UX Дизайн-Аудит и Визуальный Эталон BlacklistSettingsScreen (/frontend-design)

### Контекст
В рамках подготовки к верстке экрана «Черный список / Конструктор запретов» проведен бескомпромиссный аудит интерфейса Lirix по методологии `/frontend-design` (отказ от нейрослопа, аналоговая глубина, тактильный отклик, эргономика 20:9, 100% консистентность 4 тем).

### Решения (UI Design Audit Lead)
1. **Отчет аудита**: Сформирован документ `.sdd/reports/UI_DESIGN_AUDIT_FRONTEND.md`.
2. **Токенизация**:
   - Выявлен Token Gap: отсутствие семантических токенов опасности (`dangerPrimary`, `dangerContainer`) в `LirixColors`, приводящий к хардкоду старых цветов. Требуется расширение палитры.
   - Выявлен критический дефект в `TeachModeScreen.kt`: захардкоженные цвета `AppColors.*` вместо `LirixThemeTokens.colors`. Запланирован рефакторинг.
3. **Эргономика сенсорных зон**:
   - Все интерактивные элементы управления (кнопки перемотки 10s, иконки шапки, чипы) должны быть расширены до $\ge 48\times 48\,\text{dp}$ через `minimumInteractiveComponentSize()`.
4. **Архитектура `BlacklistSettingsScreen`**:
   - Список активных правил с микро-бейджами `[НАЗВАНИЕ]`, `[АРТИСТ]`, `[ПРОВАЙДЕР]`, счетчиками удалений и поддержкой свайп-удаления.
   - Конструктор правила в виде BottomSheet в зоне доступности большого пальца с быстрым выбором популярных плееров (Spotify, VK, Яндекс Музыка, Telegram, YouTube) и реактивным счетчиком предварительной оценки удалений (`Удалится: N треков`).

## ADR-018: Динамические пресеты и подсказки из фонотеки в конструкторе черного списка

### Контекст
Пользователь уточнил требование к конструктору запретов:
«пресеты должны браться из текущей базы»

Ранее пресеты были захардкожены статическим списком пакетов (Spotify, VK Музыка, Яндекс Музыка, Telegram), а для категорий «По артисту» и «По названию» быстрые чипы отсутствовали вовсе. При реальном использовании фонотека пользователя уже содержит уникальный набор пакетов (провайдеров), топовых исполнителей и недавних треков, которые пользователь хочет блокировать в 1 клик.

### Архитектурные Решения
1. **Зона `media-core` & `storage` (`MusicDao.kt`)**:
   - Реализовать реактивные / suspend выборки уникальных значений из текущих данных базы:
     - `getDistinctSourcePackages()`: `SELECT DISTINCT sourcePackage FROM music_listening_sessions WHERE sourcePackage IS NOT NULL AND sourcePackage != ''`
     - `getTopArtists(limit = 15)`: `SELECT DISTINCT artist FROM music_tracks WHERE artist IS NOT NULL AND artist != '' ORDER BY playCount DESC LIMIT :limit`
     - `getRecentTrackTitles(limit = 15)`: `SELECT DISTINCT title FROM music_tracks WHERE title IS NOT NULL AND title != '' ORDER BY lastPlayedAt DESC, playCount DESC LIMIT :limit`
2. **Зона `media-ui` (`BlacklistViewModel.kt`)**:
   - Добавить поле в `BlacklistUiState`:
     `val dbSuggestions: List<String> = emptyList()`
   - При открытии конструктора (`openConstructor`) и при смене типа критерия (`setDraftType(type)`):
     асинхронно опрашивать `MusicDao` и заполнять `dbSuggestions` актуальными значениями из фонотеки.
   - Для `PACKAGE_NAME`: если в базе пусто (еще не записаны сессии), объединять с дефолтными системными плеерами в качестве умного фоллбэка.
3. **Зона `media-ui` (`BlacklistRuleConstructorSheet.kt`)**:
   - Заменить статическую строку `APP_PRESETS` на динамическую горизонтальную ленту чипов `dbSuggestions` для **всех трех категорий**:
     - `TITLE`: «Треки из вашей фонотеки:»
     - `ARTIST`: «Исполнители из вашей фонотеки:»
     - `PACKAGE_NAME`: «Приложения из истории фонотеки:»
   - При нажатии на чип: мгновенная подстановка в поле `draftPattern` и реактивный перерасчет `draftPurgePreviewCount` (дебаунс 250мс).
   - Стилизация в дизайн-системе `LirixThemeTokens.colors` с выделением активного чипа.

## ADR-019: Реактивная фильтрация и превью конкретных удаляемых треков в конструкторе

### Контекст
Пользователь запросил:
«пусть то что ты вводишь в значения фильтрует треки из вашей фонотеки чтобы сразу показывало что удалит»

При вводе текста в поле значения или выборе подстроки пользователь должен не просто видеть абстрактный счетчик `N`, а сразу в реальном времени видеть список конкретных треков из своей фонотеки, которые совпали с введенным правилом и будут удалены при нажатии «Создать правило защиты».

### Архитектурные Решения
1. **Зона `media-core` (`BlacklistPurgeEngine.kt`)**:
   - Добавить метод:
     `suspend fun findMatchingTracks(rule: BlacklistRuleEntity, limit: Int = 20): List<TrackEntity>`
     который возвращает полные сущности треков (`TrackEntity`), удовлетворяющие фильтру `BlacklistMatcher.matches()`.
2. **Зона `media-ui` (`BlacklistViewModel.kt`)**:
   - Расширить `BlacklistUiState`:
     `val previewMatchingTracks: List<TrackEntity> = emptyList()`
   - В `recalculatePreview()` (дебаунс 250мс):
     - Если `draftPattern.isBlank()`: `previewMatchingTracks = emptyList()`, `draftPurgePreviewCount = 0`.
     - Если `draftPattern.isNotBlank()`:
       `val matches = purgeEngine.findMatchingTracks(tempRule, limit = 20)`
       `val totalCount = purgeEngine.previewPurgeCount(tempRule)`
       `_uiState.update { it.copy(draftPurgePreviewCount = totalCount, previewMatchingTracks = matches, isCalculatingPreview = false) }`
3. **Зона `media-ui` (`BlacklistRuleConstructorSheet.kt`)**:
   - Если `draftPattern.isBlank()`:
     Отображать чипы-подсказки из базы `suggestions` («Треки / Исполнители / Приложения из вашей фонотеки»).
   - Если `draftPattern.isNotBlank()`:
     - Заголовок секции: «Будет удалено из фонотеки (${previewCount}):»
     - Если `previewMatchingTracks.isNotEmpty()`:
       Горизонтальная лента чипов с предупреждающим бейджем `❌ ${track.title} • ${track.artist}` (акцентный/danger стиль).
     - Если `previewMatchingTracks.isEmpty()` и `!isCalculatingPreview`:
       Информационный бейдж: «✓ Ни один трек в фонотеке не подпадает под это правило».



## ADR-020: Мультиплатформенный пивот на Compose Multiplatform (Windows Desktop + Android)

### Контекст
Пользователь запросил:
«максимум общего кода: делаем ветку/модуль Compose Multiplatform Desktop — сохраняем 4 темы, анимацию тонарма, аккорды и переносим готовый движок.»

Текущая кодовая база Lirix содержит отточенные и проверенные компоненты:
- Алгоритмы парсинга и синхронизации караоке (`LrcSyncEngine`).
- Парсеры аккордов и табов (`AmDmChordParser`, `MyChordsParser`, `GuitarChordDictionary`).
- Аналитику Wrapped и 26 ачивок (`WrappedStatsEngine`, `GamificationEngine`).
- Фильтр черного списка и каскадную очистку (`BlacklistMatcher`, `BlacklistPurgeEngine`).
- Аналоговый виниловый стол с кинетическим тонармом (`AnalogTurntable`).
- Дизайн-систему с 4 глубокими темами (`LirixThemeTokens`, `AppColors`).

Чтобы перенести приложение на Windows с сохранением 100% логики и визуального стиля без дублирования кода на C# или веб-стек, принимается решение о переходе на модульную архитектуру Kotlin Multiplatform (KMP) + Compose Multiplatform Desktop.

### Архитектурные Решения
1. **Модульная структура проекта**:
   - `:shared` (KMP модуль):
     - `commonMain`: ядро движков, бизнес-логика, модели, Room/SQLite сущности, дизайн-токены `LirixThemeTokens`, общие Compose-компоненты (винил, караоке, аккорды, карточки).
   - `:app` (Android Application):
     - Android-специфичные сервисы (`NotificationListener`, `FloatingLyricsService`, Android `MediaSessionCollector`).
   - `:desktop` (Windows Desktop Application):
     - Точка входа Compose Desktop (`main()`, `application`, `Window`).
     - Windows Ingress: перехват мультимедиа через WinRT GSMTC (`GlobalSystemMediaTransportControlsSessionManager`) посредством JNA.
     - Системный трей Windows (`Tray`).
     - Глобальные хоткеи (Global Hotkeys) для фонового управления.
     - Click-Through прозрачный оверлей караоке поверх игр и окон (`WS_EX_TRANSPARENT`).
2. **План выполнения по гейтам SDD**:
   - Gate 1: Архитектурный проект и схема зависимостей Gradle (`.sdd/architecture_kmp_desktop.md`).
   - Gate 2: Выделение общего модуля `:shared` без ломки Android-приложения.
   - Gate 3: Реализация модуля `:desktop` с перехватом Windows GSMTC.
   - Gate 4: Интеграция 4 тем, винила и караоке в Windows Desktop окно.
   - Gate 5: Дистрибуция: сборка Windows инсталлятора (`.msi` / `.exe`).
