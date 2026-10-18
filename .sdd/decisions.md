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

## ADR-021: Редизайн интерфейса Lirix Desktop 2.0 на основе анализа AIMP и Яндекс Музыки

### Контекст
Пользователь запросил:
«проведи анализ интерфейса aimp, я.музыки для пк, и на основе этого делаем интерфейс для нашего пк приложения с нуля /frontend-design /browser»

### Архитектурные Решения
1. **Сравнительный аудит (UI Lead & SDD Coordinator)**:
   - Сформирован артефакт глубокого анализа: `.sdd/reports/UI_DESKTOP_AIMP_YANDEX_AUDIT.md`.
   - Выделены ключевые сильные стороны:
     - **От AIMP**: аналоговая студийная приборная панель, стерео пик-индикаторы (VU-метры $L/R$), отображение аудиофильских технических параметров, компактный режим виджета (Mini Player / Mini Deck).
     - **От Яндекс Музыки**: кинетическое крупное караоке с синхронной подсветкой активной строки, амбиентное свечение под цвет темы, чистая типографика.
     - **Фирменные возможности Lirix**: 4 глубокие темы, модуль гитарных аккордов (AmDm / MyChords) с транспозицией и интерактивными аппликатурами на грифе, прозрачный сквозной HUD-оверлей поверх игр (`WS_EX_TRANSPARENT`), перехват GSMTC.
2. **Компонентная декомпозиция (Compose Desktop)**:
   - `AnalogVuMeter.kt`: двухканальный стерео LED-индикатор с децибельной шкалой (-24..+3 dB, PEAK) и физикой демпфирования при воспроизведении/паузе.
   - `DesktopTopBar.kt`: единый управляющий хедер с живым бейджем источника аудио (`[firefox.exe]`, `[spotify.exe]`, `[yandexmusic.exe]`), статусом провайдера текстов и быстрыми переключателями режимов.
   - `DesktopCompactWidget.kt`: сверхкомпактный плавающий плеер (AIMP Mini Mode) с бегущей строкой активного караоке, обложкой и контролами.
   - `DesktopDeckScreen.kt 2.0`: полная студийная дека (Левая панель: винил + тонарм + VU-метр + тайминги + темы; Правая панель: вкладки Караоке, Аккорды, Инженерные параметры).

## ADR-022: Ревизия Lirix Desktop 2.0 — Суфлёрский Монитор (The Teleprompter Deck) и Честная Аудиометрия WASAPI

### Контекст
В ходе дизайн-ревизии исследователя (`v2.1.0-REVISED`) концепция псевдо-студийного перегруза 2000-х (бутафорский винил, немотивированное движение, декоративные шкалы) была признана избыточной и противоречащей принципам Anti-Slop. Пользователю-музыканту и вокалисту необходим чистый суфлёрский монитор с фокусом на тексте, безупречной типографикой и достоверными техническими данными.

### Архитектурные Решения
1. **Концепция «Суфлёрский монитор» (The Teleprompter Deck)**:
   - Единственный герой экрана — активная строка текста.
   - Отказ от вращающегося винила и надуманного декора в пользу спокойного книжного ритма.
   - Текстовая колонка лирики выровнена по левому краю (ширина до 640 dp), с естественным книжным рваным краем для стихов.
   - Активная строка фиксируется на 40% высоты вьюпорта.
   - Единственный акцентный маркер — 3 dp планка цвета `Accent` слева от активной строки.
   - Слева зарезервирован 44 dp невидимый слот под кнопку `[▶ 1:15]` при ховере, исключающий сдвиг верстки.
2. **Палитра и Адаптивный Акцент (Contrast Clamp)**:
   - Основная тёмная тема: `Graphite & Chalk` (`#1B1E24` / `#F2EFE9`, не слепящий AMOLED-черный).
   - Светлая тема: `Paper & Ink` (`#F5F2EB` / `#181B20`, матовая бумага без ореолов астигматизма).
   - Акцент извлекается из обложки трека и нормализуется по алгоритму `Contrast Clamp` с гарантией контраста $\ge 4.5:1$ (WCAG AA/AAA).
3. **Типографическая пара**:
   - Лирика: `Literata Medium` (экранный serif, книжная верстка стихов, Sentence case).
   - Интерфейс и таймкоды: `Golos Text` с табличными цифрами (`tnum`) против дергания верстки.
   - Аккорды: `Golos Text SemiBold` в цвете `Accent` с точным расчетом позиций через Compose `TextMeasurer`.
4. **Честная Аудиометрия (WASAPI Loopback)**:
   - Никаких фейковых данных или симулированных синусоид.
   - Замер реального системного микса через Windows WASAPI Loopback (`IAudioMeterInformation`) со шкалой от -60 до 0 dBFS.
   - При отсутствии аудиопотока или звукового устройства индикатор полностью скрывается.
   - Индикация клиппинга (`Clip` `#E5484D`) только при превышении -1.0 dBFS.
5. **Компенсация задержки GSMTC**:
   - Встроенный в панель транспорта регулятор смещения караоке: `Сдвиг текста −0,3 с [ − ] [ + ]` (шаг 0.1 с) для устранения буферизации аудиоподсистемы Windows.
6. **HUD и Mini Deck**:
   - `Mini Deck` ($380\times 110\,\text{dp}$): компактный плеер с одной строкой караоке.
   - `Game/IDE HUD` ($520\times 140\,\text{dp}$): `WS_EX_TRANSPARENT` + `WS_EX_LAYERED`, контурная обводка текста 1.5 dp (`#000000`) для читаемости поверх любых 3D-сцен и игр.

## ADR-023: Редизайн Мобильных Экранов Lirix (Android) — Точная Вертикальная Арифметика, Режим «Фокус» (Double-Tap) и Anti-Slop Очистка

### Контекст
По результатам UI/UX аудита исследователя спецификация мобильных экранов (`SPEC-UI-MOBILE-2.0`) была переработана под стандарт суфлёра (The Teleprompter Deck). Выявлены проблемы перегруженности мобильного экрана плеера системными панелями, неверной вертикальной арифметики и ложных срабатываний при касании экрана на пюпитре.

### Архитектурные Решения
1. **Автономный экран плеера (`NowPlayingScreen`)**:
   - Навигационный док (`[Плеер] [Медиатека] [Итоги]`) скрыт на экране плеера; возврат осуществляется системным жестом «Назад» или кнопкой `←` в шапке.
   - Переключатель режимов («Караоке», «Аккорды», «Текст», «Заметки») перенесён в нижнюю зону досягаемости большого пальца.
2. **Принцип верстки (weight(1f) против жесткого ограничения)**:
   - Фиксированные высоты задают каркас контролов (TopBar 48dp, метаданные 64dp, трекбар 48dp, сдвиг 48dp, табы 48dp, транспорт 56dp).
   - Колонка суфлёра занимает всё оставшееся пространство через `Modifier.weight(1f)`.
   - На экранах $< 700\,\text{dp}$ (например 360×640) срабатывает автосжатие: метаданные переносятся в шапку в одну строку, сохраняя высоту суфлёра не менее 300 dp.
3. **Режим «Фокус» через Double-Tap и Доступность**:
   - Активируется двойным тапом по тексту (`detectTapGestures(onDoubleTap = { ... })`), исключая ложные срабатывания при поправлении телефона на пюпитре.
   - Скрывает метаданные и вспомогательные панели, увеличивая полезную зону суфлёра до **$540\,\text{dp}$ (72% экрана)**.
   - Одноразовый хинт при первом запуске: *«Двойной тап по тексту — режим максимального фокуса»*.
   - TalkBack: семантическое действие `customAction("Скрыть метаданные")`.
   - Долгое нажатие на строку: перемотка `seekTo` (с хинтом и блокировкой, если `canSeek == false`).
4. **Ландшафтный пульт $800\times 360\,\text{dp}$ и вырез экрана**:
   - Левая колонка ($280\,\text{dp}$): все органы управления плеером.
   - Правая колонка ($520\,\text{dp}$): чистый двухколоночный суфлёр с кеглем до $42\,\text{sp}$ для чтения вокалистом с расстояния $1.5$ метра.
   - Корректная обработка `WindowInsets.displayCutout` (защита от выреза камеры).
   - Принудительное удержание подсветки `FLAG_KEEP_SCREEN_ON`.
5. **Честные данные, словарь и жесты**:
   - В «Итогах»: честная метрика *«просмотрено с текстом»* вместо «спето», книжная верстка эссе шрифтом `Literata` вместо разноцветных SaaS-карточек; адаптивная карточка шеринга под текущую тему.
   - Полный отказ от капса и эмодзи в интерфейсных контролах.
   - Защита жестов в Медиатеке: отступ старта свайпа $> 28\,\text{dp}$ от края экрана для исключения конфликта с системным жестом «Назад».
   - Свайп влево («Игнорировать») с обязательным Snackbar `[ Отменить ]`.
   - В Tap-to-Sync: настраиваемая компенсация моторного лага реакции (по умолчанию $200\,\text{мс}$), кнопки отмены шага `[ ↶ Отменить ]` и доводки `±0.1 с`.
   - Разгрузка TeachModeScreen (3 элемента), Blacklist («Правило игнорирования», читаемые имена приложений).
   - Статус: `IMPLEMENTED` (все задачи TASK-UI-MOB-01..06 реализованы и верифицированы тестами и сборкой).

## ADR-024: Архитектура Lirix Connect — P2P Сопряжение устройств по QR (Iroh QUIC), Сессионный Handoff и Live Audio Cast (по опыту Lyten02/AudioBridge)

### Контекст
Пользователь запросил:
«https://github.com/Lyten02/AudioBridge
запусти анализ проекта, там есть фича по соединению с телефоном и вообще с другим устройством через qr код, надо спроектировать функцию запуска воспроизведения аудиа на любом устройстве связанном, как в яндекс музыке есть кнопуа воспроизвести на устройстве»

В стриминговых сервисах (Spotify Connect, Яндекс Музыка) функция «Играть на устройстве» является ключевой точкой бесшовного пользовательского опыта. Однако централизованные сервисы требуют учётной записи на своих облачных серверах. Нам необходимо реализовать аналогичный опыт между ПК (Windows) и Смартфоном (Android) с сохранением суверенности данных и локальной автономности без серверов.

### Анализ Lyten02/AudioBridge
Проект AudioBridge демонстрирует зрелую и проверенную архитектуру связывания устройств:
1. **P2P QUIC транспорт (Iroh)**: обеспечивает прямое соединение устройств в локальной сети, устойчивый пробой NAT (hole punching) без настройки роутеров и fallback на зашифрованные DERP-релеи в случае изолированных сетей (например, телефон на 4G и домашний ПК).
2. **Безопасное сопряжение по QR-коду**: полезная нагрузка QR (`audiobridge://pair?d=<base64url>`) содержит 32 байта `EndpointId` (Ed25519), 16 байт общего секрета PSK, локальные IP-адреса и имя устройства. Сканирование занимает менее секунды и персистится в файлах конфигурации.
3. **Двунаправленный протокол `ControlMsg`**: по единому QUIC bi-stream синхронизируются статусы трека, состояние сессии и команды медиа-транспорта.

### Архитектурное Решение: Двухрежимный Lirix Connect

1. **Режим 1: Сессионный Handoff (Бесшовный перенос трека — стиль Spotify/Яндекс Музыки)**:
   - Основной режим для треков фонотеки и стриминга.
   - Исходное устройство отправляет команду `LirixConnectMsg::HandoffRequest`:
     - Метаданные трека (`title`, `artist`, `album`).
     - Точную позицию воспроизведения `positionMs`.
     - Кешированный LRC-текст караоке для исключения повторного поиска в сети.
     - Текущий статус воспроизведения `isPlaying`.
   - Целевое устройство локально стартует воспроизведение с той же миллисекунды.
   - Исходное устройство глушит локальный вывод и автоматически переходит в режим **«Пульт ДУ»**:
     - На экране синхронно бежит караоке-текст.
     - Отображается плашка: `● Воспроизводится на: ПК Daniil [Изменить]`.
     - Любые действия (смена трека, пауза, перемотка по тексту) транслируются на целевое устройство по P2P QUIC.

2. **Режим 2: Live Audio Cast (Прямой аудиострим Opus 48 кГц — ядро AudioBridge)**:
   - Резервный режим для неиндексируемых источников (игры, YouTube в браузере, видеоплееры, сторонний системный звук).
   - Захват через WASAPI Loopback (ПК) / AudioPlaybackCapture (Android).
   - Кодирование в Opus 192 кбит/с (10 мс фреймы, 48 кГц, стерео).
   - Передача через QUIC-датаграммы и воспроизведение через джиттер-буфер NetEQ-lite с ультранизкой задержкой (~30–40 мс).

3. **Безопасное сопряжение и pinning-модель**:
   - Полезная нагрузка QR (`lirix://pair?d=<base64url(CBOR)>`) содержит Ed25519 `endpoint_id`, одноразовый секрет (TTL 120 с) и топологию LAN.
   - Секрет не передаётся по сети: знание доказывается через HMAC-SHA256 proof.
   - Хост требует обязательного подтверждения пользователя («Добавить Pixel 8?» с 6-значным кодом).
   - После сопряжения устройства закрепляют `endpoint_id` друг друга (pinning). Последующие сессии работают без передачи секретов.
   - Кнопка «Забыть устройство» на любой стороне отзывает доверие и отправляет `Bye(UNPAIRED)`.

4. **Транспорт и кодирование**:
   - ALPN `lirix/connect/1` на базе Iroh QUIC bi-stream (control) и QUIC-датаграмм (cast).
   - Сериализация в CBOR с монотонным версионированием и устойчивостью к неизвестным полям.
   - Синхронизация времени через регулярный NTP-обмен `Ping/Pong` (погрешность $\le \pm 100$ мс).
   - Задержка Audio Cast: 60–120 мс по Wi-Fi с поддержкой адаптивного джиттер-буфера (40–80 мс), PLC и компенсации lip-sync.
   - Автоматический реконнект $\le 10$ с при смене сети (Wi-Fi ↔ 4G) через iroh-relay.

5. **Интерфейс (Material 3 & Compose Desktop)**:
   - Кнопка `DevicesOther` в `NowPlayingScreen` и `DesktopTopBar`.
   - Всплывающее меню / BottomSheet со списком связанных устройств, их онлайн-статусом, регулятором громкости, сканером CameraX и генератором QR (240 dp, ECC M).



## ADR-028: Implementation of Lirix Connect Transport Layer (TASK-CON-CORE-02)
- **Date:** 2026-10-15
- **Status:** APPROVED (Gates 6 & 7 Passed)
- **Context:** Implementation of the transport foundation for Lirix Connect (`SPEC-CONNECT-01 §4.1, §4.2`).
- **Decisions:**
  1. **PairingPayloadCodec:** Encodes/decodes URI scheme `lirix://pair?d=<base64url>` without padding `=`. Validates version (`version == 1`), 32-byte Ed25519 `endpointId`, 16-byte `pairingSecret`, and detects TTL expiration (`PairingExpiredException`).
  2. **ConnectTransport:** High-level network transport interface supporting ALPN `lirix/connect/1`, `StateFlow<ConnectState>`, bidirectional control stream, and raw QUIC datagram channel for Opus Audio Cast.
  3. **ConnectState:** Complete lifecycle state enum (`DISCONNECTED`, `CONNECTING`, `CONNECTED`, `RECONNECTING`, `FAILED`).
  4. **ReconnectPolicy:** Exponential backoff strategy (initial 500ms, multiplier 1.5, max 5000ms, max 5 attempts) bounded by a strict $\le 10$ s budget (accumulated 6593 ms). Immediate retry (0ms) and delay reset triggered on `onNetworkChanged()`.
  5. **Verification:** 100% test coverage with `PairingPayloadCodecTest` and `ReconnectPolicyTest` verified via `./gradlew :shared:desktopTest`.

## ADR-029: Data Honesty in Analytics & User-Centric Blacklist UI (TASK-UI-MOB-FIX-01)
- **Date:** 2026-10-15
- **Status:** APPROVED (Gates 6 & 7 Passed)
- **Context:** User review of Mobile 2.0 collage revealed data anomalies (Unknown Artist appearing in top artists and counting towards unique artists, time-of-day distribution ratio discrepancies) and technical jargon in Blacklist rules.
- **Decisions:**
  1. **Strict Unknown Artist Filtering (`WrappedStatsEngine`):** Added comprehensive pattern checking (`isUnknownArtist()`) matching "Unknown Artist", "unknown", "<unknown>", "Неизвестный исполнитель", and blank/whitespace values. These entries are completely excluded from `uniqueArtistsCount` and `topArtists`.
  2. **Honest Time-of-Day Metric:** Corrected calculation of listening distribution by time slots (morning, afternoon, evening, night) to accurately reflect session ratios. Verified in `WrappedStatsEngineTest`.
  3. **Human-Readable Blacklist Labels (`BlacklistSettingsScreen`):** Mapped raw package names (`com.yandex.music`, `com.spotify.music`, `org.telegram.messenger`, etc.) to clean Russian names via `PACKAGE_FRIENDLY_NAMES`. Replaced technical terms ("Подстрока") with intuitive rule titles ("Приложение: X", "Название содержит: Y").
  4. **Purge Undo Safety Net (`BlacklistViewModel`):** Implemented an Undo action via Snackbar upon creating a purge rule, restoring deleted tracks from memory backup if triggered accidentally.
  5. **Verification:** Unit tests verified via `./gradlew :shared:desktopTest`.

## ADR-029: Implementation of Lirix Connect Protocol Layer (TASK-CON-PROTO-03)
- **Date:** 2026-10-15
- **Status:** APPROVED (Gates 6 & 7 Passed)
- **Context:** Implementation of the full messaging protocol, length-prefixed framing, and session management (`SPEC-CONNECT-01 §4.1, §4.3, §4.4`).
- **Decisions:**
  1. **Envelope & DeviceRole:** Designed unified `Envelope` with monotonic `msgId`, `inReplyTo`, optional `sessionId`, generation `epoch`, sender monotonic timestamp `sentAtMonoMs`, and typed `LirixConnectMsg` body. Added `DeviceRole` (`PLAYER` vs `CONTROLLER`).
  2. **LirixConnectMsg Hierarchy:** Complete polymorphic hierarchy with `@Serializable` across 17 message types (`Hello`, `Welcome`, `Reject`, `Error`, `Bye`, `PairRequest`, `PairResult`, `Ping`, `Pong`, `DeviceHeartbeat`, `HandoffRequest`, `HandoffAck`, `HandoffReturn`, `RemoteMediaAction`, `RemoteStateUpdate`, `SetAudioCast`, `CastStatus`) and nested data models (`Capabilities`, `TrackRef`, `LyricsRef`, `QueueSnapshot`, `SessionSnapshot`).
  3. **ConnectMessageCodec:** Length-prefixed framing with 4-byte `u32 LE` header, guarding against buffer overflow with a strict 64 KB (`MAX_PAYLOAD_SIZE_BYTES = 65536`) limit.
  4. **ConnectSessionManager:** Session lifecycle engine maintaining `DeviceRole`, monotonic `msgId` generation, and epoch arbitration (rejecting stale commands with `ErrorCode.STALE_EPOCH`).
  5. **Verification:** 100% round-trip and validation coverage in `ConnectProtoTest` verified via `./gradlew :shared:desktopTest`.

## ADR-030: Implementation of Clock Synchronization and Playback Position Interpolation (TASK-CON-TIME-04)
- **Date:** 2026-10-15
- **Status:** APPROVED (Gates 6 & 7 Passed)
- **Context:** Implementation of network clock synchronization and real-time playback position interpolation (`SPEC-CONNECT-01 §4.5`).
- **Decisions:**
  1. **ClockSyncEngine:** Implemented NTP 4-timestamp algorithm ($t_0, t_1, t_2, t_3$) with RTT deduction $RTT = (t_3 - t_0) - (t_2 - t_1)$ and offset estimation $\text{offset} = ((t_1 - t_0) + (t_2 - t_3)) / 2$. Outlier and glitch filtering selects the sample with minimum RTT across a sliding window of 8 samples. Precision confirmed within $\pm 10$ ms across jittery and asymmetric network simulations.
  2. **PlaybackPositionInterpolator:** Computes exact local playback and karaoke teleprompter position:
     $$\text{targetPos} = \text{positionMs} + \text{playbackRate} \times (\text{nowMonoMs} - (\text{positionAtMonoMs} - \text{offset}))$$
     Guarantees strict pinning to `positionMs` during pause (`isPlaying == false`) and boundary clamping within $0 \dots \text{durationMs}$.
  3. **Verification:** 100% test coverage with `ClockSyncEngineTest` verified via `./gradlew :shared:desktopTest`.


## ADR-031: Contextual Controls & Note-Taking Ergonomics (TASK-UI-MOB-FIX-02)
- **Date:** 2026-10-15
- **Status:** APPROVED (Gates 7 & 8 Passed)
- **Context:** User review of Mobile 2.0 collage revealed that the delay/font size slider row appeared indiscriminately on all tabs (including Notes and Empty states) consuming vertical height, and the Notes input area shrank to 1-2 lines when the software keyboard was visible.
- **Decisions:**
  1. **Contextual Visibility (`NowPlayingScreen`):** The delay/font size adjustment row is shown strictly in `NowPlayingMode.KARAOKE` when synced lyrics exist (`isKaraokeWithLyrics = selectedMode == NowPlayingMode.KARAOKE && hasSyncedLyrics`). Hidden in `NOTES`, `CHORDS` (without LRC), and `EMPTY` states.
  2. **Maximized Notes Space with Keyboard Ergonomics:** When entering Notes mode and focusing or displaying the software keyboard (`WindowInsets.isImeVisible || isNotesFocused`), the track metadata, trackbar, tabs row, and transport playback controls are automatically hidden via `AnimatedVisibility`.
  3. **Full-Height Input & Done Action:** The note `OutlinedTextField` expands across all available viewport space above the IME using `Modifier.imePadding()`. A dedicated «Готово» button is provided in both the TopBar and notes header to dismiss the keyboard, drop focus, and save the note.
  4. **Verification:** `./gradlew.bat :app:compileDebugKotlin` and `./gradlew.bat testDebugUnitTest` passed cleanly.

## ADR-032: Desktop Lirix Connect UI & Pairing Popover (TASK-CON-UI-08)
- **Date:** 2026-10-15
- **Status:** APPROVED (Gates 7 & 8 Passed)
- **Context:** Implementation of the desktop UI layer for Lirix Connect (`SPEC-CONNECT-01 §5.2, §5.3`).
- **Decisions:**
  1. **DesktopTopBar Entry Point:** Added `Icons.Default.DevicesOther` button with tooltip and active connection badge (green LED `0xFF00E676`).
  2. **DesktopConnectDialog (Popover):**
     - Current PC section with volume slider and status.
     - Available devices section from `TrustedDeviceStore` with online/offline indicators and one-click transfer.
     - Pairing QR code generation via `ZXing` (ECC Level M, 240x240 dp) with payload `lirix://pair?d=<base64url>` and active 120s TTL countdown timer with refresh overlay.
     - Incoming pairing confirmation dialog with 6-digit verification code.
  3. **Global Hotkey:** Linked `Ctrl+Shift+D` in `DesktopDeckScreen` to toggle the Connect dialog.
  4. **Verification:** `./gradlew.bat :desktop:compileKotlin` and `./gradlew.bat :desktop:test` passed cleanly.

## ADR-033: Syllabic Chords Alignment, Key Signature Detection & 48dp Controls (TASK-UI-MOB-FIX-03)
- **Date:** 2026-10-15
- **Status:** APPROVED (Gates 7 & 8 Passed)
- **Context:** Mobile 2.0 collage review defect: chords rendered as a flat line without syllable matching; chord colors blended with lyrics; ♭ and ♯ buttons were below 48dp target; key signature was displayed as "Тон: 0" rather than the real musical key.
- **Decisions:**
  1. **Syllabic Alignment (`formatInlineChordsToSyllabicPair`):** Lines with inline bracketed chords (e.g. `[Am]Вчера я [C]видел [Dm]свет`) are dynamically split into aligned monospaced pairs where chords sit strictly above their corresponding syllables.
  2. **Visual Differentiation & Monospace Grid:** Chord names rendered with `colors.accentPrimary` and `FontWeight.Bold`, lyric lines rendered with `colors.textPrimary` and `FontWeight.Normal`. Both use `FontFamily.Monospace`, `softWrap = false`, and `horizontalScroll(rememberScrollState())` to prevent mid-line wrapping from breaking syllable columns.
  3. **Honest Musical Key Signature:** Automatically extracts the base root chord (e.g. `Bm` or `Am`) via `GuitarChordDictionary.extractChordsFromText(chordsText).firstOrNull()` and computes `currentKey` with `transposeSemitones`. Displays «Тональность: Bm» (and «Тональность: C#m (+2)» when transposed).
  4. **Touch Ergonomics ($\ge 48\times 48\,\text{dp}$):** `[ ♭ ]` and `[ ♯ ]` buttons expanded to 48dp with 18sp bold labels; middle key signature reset pill expanded to 48dp height.
  5. **Verification:** Added `ChordTransposerTest.test_transpose_bracketed_inline_chords`, verified with `:shared:allTests` and `:app:compileDebugKotlin`.

## ADR-034: Tap-to-Sync Studio Redesign & Ergonomics (TASK-UI-MOB-FIX-04)
- **Date:** 2026-10-15
- **Status:** APPROVED (Gates 7 & 8 Passed)
- **Context:** Mobile 2.0 collage review defect: `LrcTapSyncStudioDialog.kt` retained fluorescent gradients/neon styling (`CyberCyan` to `HyperViolet`); primary action button used all-caps with emoji («ТАП • СЛЕДУЮЩАЯ СТРОКА 👆»); bottom undo/reset buttons overlapped system gesture bar.
- **Decisions:**
  1. **Clean Design Tokens (Zero Neon/Gradients):** Replaced all gradient brushes and neon borders with clean `LirixThemeTokens.colors.surfaceCard`, `tokens.surfaceElevated`, and solid `tokens.accentPrimary`.
  2. **Sentence Case & Vector Icon:** Primary action button converted to sentence case: «Тап — следующая строка» (and «Сохранить караоке» upon completion) using vector `Icons.Default.TouchApp` and `Icons.Default.Check` in place of emojis.
  3. **Safe Insets & Touch Targets:** Container for «Отменить шаг» and «Сбросить всё» padded with `Modifier.navigationBarsPadding().padding(bottom = 16.dp)`, with `heightIn(min = 48.dp)` ensuring touch targets meet Material 3 accessibility guidelines.
  4. **Verification:** `./gradlew.bat :app:compileDebugKotlin` and `testDebugUnitTest` passed cleanly.


## ADR-035: Device Registry & Handoff State Machine (TASK-CON-REG-05 & TASK-CON-HANDOFF-06)
- **Date:** 2026-10-15
- **Status:** APPROVED (Gates 7 & 8 Passed)
- **Context:** Implementation of the reactive device registry with heartbeat presence monitoring and the domain Handoff finite state machine (`SPEC-CONNECT-01 §4.4, §4.6`).
- **Decisions:**
  1. **DeviceRegistry & Heartbeat (`DeviceRegistry.kt`):** Maintains active peer list with online/offline status, playback status, and last seen epoch. Processes `DeviceHeartbeat` every 10s and applies a strict 25s timeout (`HEARTBEAT_TIMEOUT_MS`) before transitioning devices to offline.
  2. **Handoff FSM (`HandoffStateMachine.kt`):** Full state machine (`IDLE` $\to$ `REQUESTED` $\to$ `TARGET_STARTING` $\to$ `TARGET_PLAYING` $\to$ `SOURCE_MUTED` / `SUGGEST_CAST` / `FAILED`).
  3. **Seamless 300ms Crossfade:** Local audio continues on the source device until the target confirms playback (`RemoteStateUpdate(isPlaying=true)`), followed by a 300ms crossfade before switching to remote controller mode.
  4. **Timeout & Failure Safe Fallback:** 5-second timeout on target response reverts cleanly to `IDLE` keeping local playback uninterrupted. Target rejection with `suggestCast=true` transitions to `SUGGEST_CAST`. Disconnection during remote control initiates a 10s reconnect period before offering local resumption.
  5. **Verification:** 13 new unit tests in `DeviceRegistryTest` (7) and `HandoffStateMachineTest` (6), verified with `./gradlew :shared:desktopTest` (143 passed, 0 failures).

## ADR-036: Windows Firewall & UDP Port Rule Manager (TASK-CON-WIN-12)
- **Date:** 2026-10-15
- **Status:** APPROVED (Gates 7 & 8 Passed)
- **Context:** Implementation of Windows Defender Firewall check and automated UDP rule provisioning for P2P QUIC Iroh transport and Live Audio Cast (`SPEC-CONNECT-01 §6`).
- **Decisions:**
  1. **Dual-Locale Netsh Parsing (`WindowsFirewallManager.kt`):** Executes `netsh advfirewall firewall show rule name="Lirix Connect"` on `Dispatchers.IO` and parses output across Russian and English Windows installations into typed `FirewallRuleStatus` (`ALLOWED`, `BLOCKED`, `NOT_CONFIGURED`, `NOT_SUPPORTED`).
  2. **PowerShell Elevation Helper:** Generates an elevated command with `-Verb RunAs` and explicit netsh rule definition for the current executable path, supporting jpackage, launcher binaries, and development JVM runners.
  3. **Verification:** 9 unit tests in `WindowsFirewallManagerTest` verified with `./gradlew :desktop:test`.

## ADR-037: UI Polish, Residual CAPS Cleansing & Anti-Slop (TASK-UI-MOB-FIX-05)
- **Date:** 2026-10-15
- **Status:** APPROVED (Gates 7 & 8 Passed)
- **Context:** Comprehensive review cleanup across 6 mobile UI files: remove residual ALL-CAPS, replace emojis with vector icons, fix bottom padding overlap in WrappedScreen, add Literata essay typography, fix negative padding in Library, and style teach inspector borders.
- **Decisions:**
  1. **Sentence Case & Typography (`BlacklistSettingsScreen.kt`):** Header and section titles converted to standard sentence case («Активные правила», «Всего правил: N», «Все (N)», «Треки», «Артисты», «Приложения»).
  2. **Vector Icons Without Emojis (`TeachModeScreen.kt`):** Replaced emoji characters (📝, 🎸) with vector Material icons `Icons.Default.Description` and `Icons.Default.MusicNote`. Compact floating dock added with expand/collapse toggle.
  3. **Inspector Border Styles (`teach_inspector.js`):** Selected containers styled with solid purple border `border: 2px solid #8B5CF6; outline: 2px solid #8B5CF6`; stripped elements highlighted with dashed red border `border: 2px dashed #EF4444; outline: 2px dashed #EF4444; background: rgba(239, 68, 68, 0.1)`.
  4. **Wrapped Padding & Literata Typography (`WrappedScreen.kt`):** Added `bottom = 96.dp` padding in `LazyColumn` contentPadding so content never sits beneath the floating navigation dock. Styled first sentence of essay with `FontStyle.Italic` (18sp Medium) and subsequent sentences with `FontStyle.Normal` (16sp Regular).
  5. **Empty State & Plain Lyrics Header (`NowPlayingScreen.kt`, `EmptyPlayerStateView.kt`):** Plain lyrics reading view styled with compact pill «Текст песни без таймингов» without distracting grey header bar. Empty player state displays friendly title «Не нашли текст для этого трека» and subtitle «Вы можете найти его в веб-поиске и привязать к песне в один клик».
  6. **Library Edge-to-Edge & Badges (`LibraryScreen.kt`):** Removed fragile `-16.dp` negative padding hack; chip bar `LazyRow` configured with clean `contentPadding = PaddingValues(horizontal = 16.dp)`. «Текст» badge styled with `tokens.surfaceElevated`, `tokens.borderSubtle`, and legible `tokens.textSecondary`.
  7. **Verification:** `./gradlew.bat :app:compileDebugKotlin` and tests passed with 0 errors.

## ADR-038: Android Connect BottomSheet & QR Code Pairing (TASK-CON-UI-07)
- **Date:** 2026-10-15
- **Status:** APPROVED (Gates 7 & 8 Passed)
- **Context:** Implementation of Android client device connect bottom sheet, QR-code camera scanner with fallback, volume slider, and reactive integration with `DeviceRegistry` and `HandoffStateMachine` (`SPEC-CONNECT-01 §5.1, §5.2, §5.3`).
- **Decisions:**
  1. **Architecture & Persistence (`AndroidConnectManager.kt`):** SharedPreferences-backed `AndroidPreferencesTrustedDeviceStore` for trusted device keys; coordinates reactive `DeviceRegistry` and `HandoffStateMachine`. Real-time system audio volume synchronization via `AudioManager.STREAM_MUSIC`.
  2. **QR Scanner & Permission Fallback (`QrScannerView.kt`):** `QrScannerDialog` checks `android.permission.CAMERA` with ActivityResultContracts; displays rounded viewfinder with animated scanning laser. Graceful fallback UI with «Открыть настройки» button and manual code entry field (`lirix://pair?d=...`). Validation via `PairingPayloadCodec.decode(uri, currentEpochSec)` with explicit `PairingExpiredException` handling.
  3. **ModalBottomSheet (`DeviceConnectBottomSheet.kt`):** Material 3 bottom sheet:
     - Header: «Воспроизвести на устройстве» + `Icons.Default.DevicesOther` + 48dp close button.
     - «ТЕКУЩЕЕ УСТРОЙСТВО»: «● Этот телефон (Android)» with green LED indicator + local stream volume slider (0%..100%).
     - «ДОСТУПНЫЕ УСТРОЙСТВА В СЕТИ»: reactive cards from `DeviceRegistry.devices`, status indicators (online/offline/playing), connection spinner up to 5s, remote volume slider, and «⋮» menu for rename and forget with confirmation dialogs.
     - «ПОДКЛЮЧИТЬ НОВОЕ УСТРОЙСТВО»: 52dp button launching `QrScannerDialog`.
     - Audio Cast Fallback (§5.3): Dialog offering «Включить Audio Cast» when direct Handoff fails or target rejects.
  4. **NowPlayingScreen Integration:** Vector icon `Icons.Default.DevicesOther` with active remote playback dot added to transport bar in both portrait and landscape orientations with $\ge 48\text{dp}$ touch target.
  5. **Verification:** Unit tests in `AndroidConnectManagerTest` (4/4 passed), compilation via `./gradlew.bat :app:compileDebugKotlin` and tests via `./gradlew.bat testDebugUnitTest` green.


## ADR-039: Live Audio Cast Receiver & Adaptive Jitter Buffer (TASK-CON-CAST-09)
- **Date:** 2026-10-15
- **Status:** APPROVED (Gates 7 & 8 Passed)
- **Context:** Implementation of cross-platform Live Audio Cast receiver, datagram framing codec, adaptive RFC 3550 jitter buffer, and periodic telemetry (`SPEC-CONNECT-01 §4.7`).
- **Decisions:**
  1. **CastDatagramCodec:** 7-byte binary header framing (`seq: u16`, `timestamp48k: u32`, `flags: u8`), MTU enforcement ($\le 1200$ bytes), and round-trip Big-Endian serialization.
  2. **AdaptiveJitterBuffer:** RFC 3550 transit jitter tracking with exponential smoothing ($d = |transit - prevTransit|$, $jitter += (d - jitter)/16$). Target latency dynamically adjusts within 40–80 ms. Reorders misplaced packets, drops late duplicates, and triggers `LossConcealment` markers for lost packets.
  3. **Telemetry & Sink:** `CastMetricsCollector` computes and dispatches periodic (1s) `CastStatus` reports (`lossPct`, `jitterMs`, `receiverLatencyMs`). Output rendered via `CastAudioSink` with volume attenuation and PLC invocation.
  4. **Verification:** 9 comprehensive unit tests in `CastReceiverEngineTest` passed via `./gradlew :shared:desktopTest` (153 total tests passed, 0 failures).

## ADR-040: Windows Audio Cast Source & WASAPI Loopback Capture (TASK-CON-CAST-10)
- **Date:** 2026-10-15
- **Status:** APPROVED (Gates 7 & 8 Passed)
- **Context:** Implementation of Windows desktop system audio loopback capture and packetization into QUIC datagrams (`SPEC-CONNECT-01 §4.7, §6`).
- **Decisions:**
  1. **WindowsWasapiAudioCapture:** 48 kHz 16-bit stereo PCM capture with `AudioRingBuffer` protecting against buffer overflows and clock skew. Evaluates RMS energy for voice activity detection (VAD) and emits `FLAG_DTX` on silence. Graceful fallback on non-Windows platforms.
  2. **DesktopCastSourceManager:** Slices audio into 10–20 ms frames, tracks monotonic sequence counters and 48 kHz timestamps, dispatches QUIC datagrams, and processes remote `CastStatus` telemetry for lip-sync compensation.
  3. **Verification:** Unit tests in `WindowsAudioCaptureTest` passed via `./gradlew :desktop:test`.

## ADR-041: Lirix Connect Full Stack Completion, NFR Stand & Android Audio Cast Stabilization (TASK-CON-QA-13, TASK-CON-CAST-11, TASK-BUG-FLT-04)
- **Date:** 2026-10-16
- **Status:** APPROVED (Gates 7 & 8 Passed)
- **Context:** Completion of all remaining P0 tasks in Lirix Connect and Android Mobile UI stacks: NFR automated test stand validation, Android system audio loopback capture engine with Opus streaming, Floating Lyrics Compose WindowManager overlay stabilization.
- **Decisions:**
  1. **NFR Integration Stand Validation (`ConnectIntegrationTestStand.kt`):** Verified all 5 non-functional requirement checks:
     - NFR-1: Packet loss tolerance up to 30%, 80ms jitter buffer recovery without pipeline crash.
     - NFR-2: Direct P2P failure triggers seamless relay fallback within 3000ms.
     - NFR-3: Wi-Fi to Cellular roaming handled with monotonic sequence preservation.
     - NFR-4: Pairing TTL 120s enforcement, constant-time HMAC-SHA256 proof, rate limiting with secret invalidation on 3 failed attempts (`VerificationResult.InvalidProof` on 3rd attempt, `VerificationResult.TooManyAttempts` on subsequent attempts).
     - NFR-5: Handoff crossfade latency within $\le 300\text{ms}$.
  2. **Android Audio Cast Engine (`AndroidAudioCaptureService.kt`, `AndroidCastSourceManager.kt`):** Slices 48kHz stereo PCM into 10ms (1920-byte) frames via synchronized `AudioRingBuffer`. RMS silence detection threshold ($\text{RMS} < 0.002$) applies `CastDatagram.FLAG_DTX`. Foreground service registered with `mediaProjection` type for Android 14+ compatibility. Unit tests deterministic under Coroutine TestScope.
  3. **Floating Lyrics WindowManager Overlay (`FloatingLyricsOverlayView.kt`, `FloatingLyricsService.kt`, `NowPlayingScreen.kt`):** Resolved runtime crash on detached window using custom `OverlayLifecycleOwner` (`SavedStateRegistryOwner` + `ViewModelStoreOwner`) and `DisposeOnDetachedFromWindowOrReleasedFromPool` composition strategy. Bound overlay toggle into `NowPlayingOverflowMenu` with `SYSTEM_ALERT_WINDOW` permission dialog.
  4. **Verification:**
     - `:shared:desktopTest`: 158/158 tests passed (100% green).
     - `:app:testDebugUnitTest`: all unit tests passing (100% green).



## ADR-042: Real LAN Transport and Desktop Audio Server Integration
- **Date:** 2026-10-16
- **Status:** APPROVED (Gates 7 & 8 Passed)
- **Context:** ConnectTransport previously relied on mock in-memory transports; real device-to-desktop communication required socket-level TCP control bi-stream and UDP audio datagram transmission.
- **Decisions:**
  1. **LanConnectTransport (`LanConnectTransport.kt`):** Implemented dual-channel transport: TCP control socket on port 4433 with 4-byte LE framing and CBOR serialization; UDP socket for 10ms PCM/Opus datagrams.
  2. **DesktopConnectServer (`DesktopConnectServer.kt`):** Singleton TCP/UDP server listening on port 4433, WASAPI/JavaSound PCM playback via `SourceDataLine` (48kHz 16-bit Stereo), dynamic volume control via `FloatControl.Type.MASTER_GAIN`.

## ADR-043: CameraX TextureView Rendering & Audio Cast Runtime Permission Resolution (TASK-CON-FIX-14..16)
- **Date:** 2026-10-16
- **Status:** APPROVED (Gates 7 & 8 Passed)
- **Context:** Physical device testing revealed: 1) Camera viewfinder displayed pitch black because `PreviewView` defaulted to `SurfaceView` which does not render through Compose window hierarchies on Android; 2) Audio Cast failed because `Manifest.permission.RECORD_AUDIO` was never requested before invoking `MediaProjection`, and `MediaProjection` token was decoupled from `startCast`; 3) Desktop app had not yet launched the new `DesktopConnectServer`.
- **Decisions:**
  1. **CameraX Compatible TextureView (`QrScannerView.kt`):** Configured `PreviewView.ImplementationMode.COMPATIBLE` to force `TextureView` rendering inside Compose layout tree, respecting clipping and alpha. Corrected ZXing Y-plane stride calculation with `rowStride` alignment.
  2. **Audio Cast Permission & Lifecycle (`DeviceConnectBottomSheet.kt`, `AudioCastForegroundService.kt`):** Added runtime request for `android.permission.RECORD_AUDIO` prior to launching `MediaProjectionManager.createScreenCaptureIntent()`. Connected `AudioCastForegroundService` to immediately propagate `mediaProjection` to `AndroidCastSourceManager` and trigger `startAudioCastToDevice`.
  3. **Desktop Process Restart:** Terminated legacy background `Lirix.exe` instances and activated `DesktopConnectServer` on TCP/UDP port 4433.

## ADR-044: Redesign of Tap-to-Sync Studio in Lirix 2.0 Design System (TASK-UI-TAPSYNC-REDESIGN)
- **Date:** 2026-10-16
- **Status:** APPROVED (Gates 1–4 Passed, Delegated to Implementation)
- **Context:** Physical device audit revealed outdated prototype artifacts on the Tap-to-Sync screen (`LrcTapSyncStudioDialog.kt`): neon cyan/violet gradients, raw caps labels, unicode emoji on primary action button, lack of `LiterataFontFamily` and `GolosTextFontFamily`, and button overlap by Android gesture navigation.
- **Decisions:**
  1. **Typography & Sentence Case:** Apply `LiterataFontFamily` with `.toSentenceCase()` for song lyrics (20sp active line with `FontWeight.Medium`, 14sp adjacent lines with `alpha = 0.65f`). Apply `GolosTextFontFamily` with OpenType `tnum` tabular figures for interface controls, song title, artist, progress badge, and timers.
  2. **Palette & Studio Deck Aesthetics:** Eliminate all gradients and raw hex colors in favor of `LirixThemeTokens.colors`. Central card uses `surfaceCard` with clean 1.5dp `accentPrimary` contour.
  3. **Ergonomic Primary Action Button:** 56dp height, rounded 16dp corners, solid `accentPrimary` fill, vector `Icons.Default.TouchApp`, text `Тап — следующая строка` in sentence case without emojis or caps.
  4. **Gesture Inset Safety:** Wrap bottom actions (`Отменить шаг`, `Сбросить всё`) in `Modifier.navigationBarsPadding().padding(bottom = 16.dp, top = 8.dp)` with minHeight 48dp, ensuring full visibility above Android system navigation bars and gesture pill.

## ADR-045: HTML Line Break Extraction & Studio Root Insets (TASK-UI-TAPSYNC-INSETS-02, TASK-TEACH-LINEBREAKS-01)
- **Date:** 2026-10-16
- **Status:** APPROVED (Gates 7 & 8 Passed)
- **Context:** User device audit revealed: 1) Bottom buttons in TapSync Studio were partially submerged below the screen because `navigationBarsPadding` was applied to the bottom Row rather than the root `Surface`, causing the `weight(1f)` Column to miscalculate window insets; 2) TeachMode extracted lyrics as a single run-on sentence with glued chords (`A#A ну`, `D7A то`) because detached `clone.innerText` in Chromium returns `textContent`, dropping all `<br>`, `<p>`, and `<div>` line breaks.
- **Decisions:**
  1. **Root Dialog Insets (`LrcTapSyncStudioDialog.kt`):** Applied `.statusBarsPadding().navigationBarsPadding()` directly to the root `Surface`, with `Column` padding `bottom = 8.dp`. Reduced bottom Row padding to `6.dp` and action button to `52.dp`. The buttons are guaranteed to sit completely above Android system gesture navigation.
  2. **Live DOM Text Extraction (`teach_inspector.js`):** `extractCleanText` reads `rootEl.innerText` directly from the live attached DOM with stripped elements temporarily hidden (`style.display = 'none'`), preserving 100% of the browser's CSS layout newlines (`<br>`, `<p>`, `<div>`, `<pre>`). Fallback clone explicitly replaces `<br>` with `\n` text nodes and adds newlines around block tags. Glued chords are automatically separated (`$1 $2`).
  3. **Resilient Sanitizer (`LrcSyncEngine.kt`, `NowPlayingScreen.kt`):** `LrcSyncEngine.sanitizePlainText` unsticks chords from letters and restores line breaks at section headers/punctuation for previously saved single-line tracks in `PlainLyricsReadingView` and `parsePlainToSyncLines`.

## ADR-046: Manual Lyrics Editor & Smart Assistants in Lirix 2.0 (TASK-UI-EDIT-LYRICS-01)
- **Date:** 2026-10-16
- **Status:** APPROVED (Gates 7 & 8 Passed)
- **Context:** Users required the ability to manually edit and correct song lyrics directly within the mobile application (fixing typos, stripping chords for karaoke, breaking running text into lines, pasting from clipboard).
- **Decisions:**
  1. **Fullscreen Modal Editor (`EditLyricsDialog.kt`):** Built with `DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)` and `DialogWindowProvider` to support true edge-to-edge rendering, status bar insets, navigation bar insets, and `imePadding()` to prevent the software keyboard from occluding controls.
  2. **Smart Toolbar Assistants:**
     - `Разбить на строки` (`Icons.Default.AutoAwesome`): applies `LrcSyncEngine.sanitizePlainText()` to automatically format run-on verses and unglue chords.
     - `Удалить аккорды` (`Icons.Default.MusicNote`): strips guitar chord lines and inline notation to produce clean singable karaoke text.
     - `Вставить` (`Icons.Default.ContentPaste`): one-tap paste from Android `ClipboardManager`.
  3. **Live Stats & Typography:** Displays real-time line and character counters. Song editor field uses `LiterataFontFamily` (16sp, line height 24sp), toolbar uses `GolosTextFontFamily`.
  4. **Tri-point Entry & Persistence:** Accessible from `NowPlayingScreen` (edit icon in text view), `NowPlayingOverflowMenu` (dropdown item "Редактировать текст песни"), and `LrcTapSyncStudioDialog` (top bar edit button). Asynchronously updates Room database (`lyricsDao().saveLyrics` and `musicDao().updateLyrics`) and updates in-memory UI state with confirmation `Snackbar`.

## ADR-047: Codex architecture/specification, Antigravity implementation; PCM LAN repair
- Date: 2026-10-16
- Status: APPROVED for scoped implementation, physical acceptance pending.
- User explicitly assigned Codex to author architecture/specifications and drive Antigravity chats, with Antigravity writing product code. This assignment supersedes earlier coordinator-only restrictions for architecture/specification authoring in this session. Product code remains delegated.
- Evidence: CONNECT_DIALECTIC_VERIFICATION.md; user reports no sound.
- Repair baseline: .sdd/specs/media-connect/lan_pcm_audio_repair_v1.md v1.0; packetize complete PCM16LE stereo into <=960-byte aligned payloads, explicit PCM codec, preserve data, truthful receiver errors/state. Temporary LAN repair, not QUIC/Opus parity or secure Connect release.
- QA before implementation; CON-PCM-000 is an explicit one-enum schema support card; other implementation cards are one function each. Independent review and physical audible Android-to-Windows smoke remain required.
- No claims that pairing, handoff, target switching or full AudioRecord error separation are fixed by this package.

## ADR-048: Codex resumes direct implementation
- Date:2026-10-16.
- User explicitly requested stopping Antigravity and writing all code directly. This supersedes coordinator-only and Antigravity-only coding restrictions for the remainder of this task.
- Antigravity task11516284 stopped through UI Cancel; User cancelled agent execution confirmed. Root Codex now implements and tests; independent reviewer stays read-only.
- Existing implemented changes preserved; failed suspend-reflection test replaced with real TCP/UDP production-path regression coverage, cleanup cancellation repaired.

## ADR-049: Ultra-Compact Chords Toolbar & Lyrics Area Maximization (TASK-UI-CHORDS-TOOLBAR-COMPACT-01)
- **Date:** 2026-10-16
- **Status:** APPROVED (Gates 1–4 Passed, Delegated to Implementation)
- **Context:** Mobile UI audit revealed that the chords tab toolbar was housed in a massive 250dp `Surface` card with multiple padded rows. This compressed the actual guitar lyrics reading view to 2-3 lines and caused horizontal overflow in portrait mode.
- **Decisions:**
  1. **Single-Row Ultra-Compact Header:** Replaced the multi-row `Surface` container with a slim, ergonomic single-row layout (height 40-42dp) with 6dp bottom spacing.
  2. **Minimalist Key Stepper:** Streamlined transpose control to `[ ♭ ]` `Key` `[ ♯ ]` (eliminating the redundant verbose "Тональность:" prefix) in a rounded 8dp badge.
  3. **Inline Chords Ribbon:** Embedded the song chord fingering chips directly into the center of the header as a horizontal `LazyRow`, preserving one-tap chord diagrams via `GuitarChordDialog`.
  4. **Micro Autoscroll Trigger:** Positioned speed toggle (`1.0x`) and play/pause (`▶` / `⏸`) buttons as micro-chips on the right edge.
  5. **Maximized Lyrics Surface:** Granted >85% of screen height to the guitar lyrics prompter with monospace typography and smooth vertical/horizontal scrolling.

## ADR-050: Fix Chord Sharp Symbol Truncation & False Article A Extraction (TASK-BUG-CHORD-EXTRACT-01)
- **Date:** 2026-10-16
- **Status:** APPROVED (Gates 1–4 Passed, Delegated to Implementation)
- **Context:** User reported that sharp chords in song lyrics (`C#`, `F#`) were displayed in the toolbar ribbon as natural chords (`C`, `F`), and natural `A` appeared erroneously.
- **Root Cause:** `GuitarChordDictionary.extractChordsFromText` used a regex with trailing word boundary `\b`. Since `#` is a non-word character (`\W`), `\b` matched before `#`, stripping sharps. Furthermore, whole-text scanning matched English indefinite article `a` / `A` as chord `A`.
- **Decisions:**
  1. **Token & Line-Aware Extraction:** Extract chords from bracketed inline notations `[...]`, `(...)` and chord-dense lines (`isChordLine`).
  2. **Sharps Preservation:** Match chord tokens via bounded whitespace/punctuation splitting without trailing `\b`, ensuring `C#`, `F#`, `G#`, `A#m` resolve intact against `CHORD_MAP`.
  3. **Vocal Line Guard:** Prevent lyric text from leaking solitary letters (`a`, `A`) into the guitar chord ribbon.

## ADR-051: Lyrics Search Lifecycle, Stale State Eviction & Search Progress (TASK-UI-LYRICS-SEARCH-LIFECYCLE-01)
- **Date:** 2026-10-16
- **Status:** APPROVED (Gates 1–4 Passed, Delegated to Implementation)
- **Context:** User reported that upon changing songs, the lyrics of the previous song remained on screen during background search, and if no lyrics were found online, the screen remained stuck displaying stale data from the previous song.
- **Root Cause:** `NowPlayingScreen.kt` held `lyricsCache` in a non-keyed `remember` block without clearing it on track transition. `RenderTeleprompterContent` did not receive `isLoadingLyrics`, rendering stale lyrics until network requests settled. Unhandled network exceptions in `LaunchedEffect` aborted coroutines before clearing `lyricsCache`.
- **Decisions:**
  1. **Keyed State Reset:** Key `lyricsCache` and `isLoadingLyrics` to `currentTrack?.trackKey`, immediately evicting stale cache on the very frame of track change.
  2. **Dedicated Searching State:** Added `PlayerEmptyStateType.SEARCHING_LYRICS` in `EmptyPlayerStateView.kt` with a themed `CircularProgressIndicator`, track metadata, and status copy («Ищем текст песни...»).
  3. **Strict Track Key Validation:** In `RenderTeleprompterContent`, reject cached entities whose `trackKey` does not match `currentTrack.trackKey`.
  4. **Resilient Network Exception Handling:** Wrap `AggregatedLyricsProvider` retrieval in `try/catch`, ensuring reliable transition to `PlayerEmptyStateType.LYRICS_NOT_FOUND` when online search fails.

## ADR-052: Smart Line Wrapping & Synchronized Syllabic Pair Wrapping for Chords (TASK-UI-CHORDS-SMART-WRAP-01)
- **Date:** 2026-10-16
- **Status:** APPROVED (Gates 1–4 Passed, Delegated to Implementation)
- **Context:** User reported that in the chords tab, long lines without line breaks were invisible because `softWrap = false` forced text to run off-screen horizontally.
- **Root Cause:** To prevent chords from desynchronizing from lyrics under blind text wrapping, `softWrap = false` was previously enforced. Without wrapping, lines longer than ~36 characters extended off-screen, rendering lyrics unreadable during performance. Text without `\n` linebreaks stretched into a single line.
- **Decisions:**
  1. **Run-on Preprocessing:** When chords text lacks line breaks (`lines().size <= 2 && length > 80`), apply `LrcSyncEngine.sanitizePlainText()` to format musical sections and sentences into lines.
  2. **Synchronized Syllabic Pair Wrapping:** For lines exceeding `maxCharsPerLine` (~36 chars portrait, ~54 landscape), split both chord line and lyrics line simultaneously at word boundaries. This keeps chords strictly above their syllables on wrapped sub-lines without horizontal truncation.
  3. **Wrap Toggle Control (`[ ↩ ]`):** Added a micro-action in the toolbar to toggle wrapping (`isWrapEnabled`, default `true`), allowing guitarists to choose between auto-wrapped viewport and classic single-line scroll.

## ADR-053: Dynamic Theme Palette Binding for Library Screen & Bottom Sheets (TASK-UI-LIB-THEME-FIX-01)
- **Date:** 2026-10-16
- **Status:** APPROVED (Gates 1–4 Passed, Delegated to Implementation)
- **Context:** User reported that selecting the Yandex Music theme applied yellow/orange accents to the bottom navigation bar and player, but the entire Library screen («Все», counters, «Синхронно» badges, search icon and border) remained stuck on graphite/blue (`#7FA6F0`).
- **Root Cause:** `LibraryScreen.kt` and `TrackActionBottomSheet.kt` hardcoded `val stableAccent = if (lirixColors.isDark) LirixThemeTokens.AccentFallbackDark else LirixThemeTokens.AccentFallbackLight`, bypassing the reactive `LirixThemeTokens.colors.accentPrimary` and `accentSecondary` of the selected theme.
- **Decisions:**
  1. **Dynamic Theme Accent Binding:** Replace hardcoded `AccentFallbackDark` with `lirixColors.accentPrimary` and `lirixColors.accentSecondary` across all interactive and highlight components in `LibraryScreen.kt` and `TrackActionBottomSheet.kt`.
  2. **High-Contrast Text Mapping:** For active filter chips, bind text color to `MaterialTheme.colorScheme.onPrimary`, ensuring maximum legibility (black text on Yandex yellow `#FFCC00` with 14:1 contrast ratio, high-contrast text on other themes).
  3. **Metadata & Badges Theme Harmonization:** Align «Синхронно» badges and play counts (`×N`) with `accentPrimary`, and favorite heart icons with `accentSecondary`.





## Desktop karaoke correction — 2026-10-17
- User clarified that the missing feature is a separate Karaoke tab on PC.
- Approved architect contract `.sdd/specs/media-ui/desktop_karaoke_fix.md` and function cards KARAOKE-001–012. Explicit Text/Karaoke modes; no invented timing for plain lyrics; contextual studio action, lifecycle playback sampling, correct offset inversion and observable offset.
- Scope includes removing the unnecessary Dispatchers.Main override in the captured Compose hotkey scope, addressing observed missing-dispatcher runtime errors without a new dependency.
- QA owns regression tests before implementation; coder owns product changes; independent review and native acceptance are separate gates. Current release stays immutable.
- CR-KARAOKE-PREFS-01: independent review found tests mutating the actual user preference node. Approved QA's proposal for an injected Preferences-backed offset store retaining the singleton API, key and default; tests use in-memory AbstractPreferences. QA specifies and owns fixture/tests, coder owns the backing implementation. Require a fresh passing run after isolation; the preceding successful run is not final acceptance.

## Direct implementation authorization — 2026-10-18
- User explicitly answered «Да, пиши код сам» to temporary role change after executor usage limits. Root may now read and write product/test code for the current desktop-readiness work; this supersedes the coordinator-only restriction for this scope. Existing executor assignments continue without overlapping file edits.
- Root owns adaptive teleprompter geometry and draft protection; executor owns the already approved desktop palette/library action-row batch. Build ownership remains exclusive.
