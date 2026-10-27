# SDD Dashboard & Task Board: Lirix (Music & Lyrics Hub)

> **Стандарт SDD (Spec Driven Design):**
> Ни одна задача не берётся в работу без утверждённой спецификации в `.sdd/specs/<zone>/`.
> Любая задача детерминирована (1 функция / 1 компонент = 1 карточка).

---

## 1. Сводный статус проекта (Executive Summary)

| Метрика | Значение | Описание |
|---|---|---|
| **Всего архитектурных спецификаций** | **24** | В папке `.sdd/specs/` по 6 зонам |
| **Утверждено / Заморожено (Approved / Frozen)** | **24 / 24 (100%)** | Все имеют готовый DoD и контракты |
| **Задач в активной разработке (`IN_PROGRESS`)** | **0** | Все задачи P0 завершены и приняты |
| **Задач подготовлено к взятию в работу (`READY`)** | **3** | Пакет P1: `TASK-REL-MOB-E2E`, `TASK-REL-DSK-PKG`, `TASK-CON-E2E-SMOKE` |
| **Реализовано и принято (`DONE`)** | **168+** | 100% задач Connect (16/16) и UI фиксов закрыты |

---

## 2. Реестр спецификаций архитектурных зон (`.sdd/specs/`)

Все спецификации разбиты по зонам ответственности с указанием версий, статусов готовности к кодингу и файлов:

| ID Спецификации | Зона | Документ / Ссылка | Описание и скоуп | Версия | Статус спеки | Статус внедрения |
|---|---|---|---|---|---|---|
| `SPEC-CONNECT-01` | `media-connect` | [.sdd/specs/media-connect/device_playback_connect_spec.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-connect/device_playback_connect_spec.md) | Lirix Connect: P2P сопряжение по QR, Handoff, Audio Cast | v1.0 | **APPROVED** | **IN_PROGRESS** |
| `SPEC-UI-MOBILE-2.0` | `media-ui` | [.sdd/specs/media-ui/mobile_screens_redesign_spec.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ui/mobile_screens_redesign_spec.md) | Мобильный редизайн 2.0 (Арифметика 800dp, Фокус, TalkBack) | v2.0 | **APPROVED** | **IN_PROGRESS** (Пакет FIX-01..05) |
| `SPEC-ZONE-A` | `media-ingress` | [.sdd/specs/media-ingress/overview.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ingress/overview.md) | Общая архитектура ингресса сессий и фильтрации | v1.0 | **FROZEN** | **DONE** |
| `SPEC-ZONE-B` | `media-core` | [.sdd/specs/media-core/overview.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-core/overview.md) | Ядро плеера, Room v6, сессии и треки | v1.0 | **FROZEN** | **DONE** |
| `SPEC-ZONE-C` | `lyrics-engine` | [.sdd/specs/lyrics-engine/overview.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/lyrics-engine/overview.md) | Каскад источников текста, LRCLIB и AmDm парсер | v1.0 | **FROZEN** | **DONE** |
| `SPEC-ZONE-D` | `wrapped-analytics` | [.sdd/specs/wrapped-analytics/overview.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/wrapped-analytics/overview.md) | Движок аналитики Wrapped, геймификация и ачивки | v1.0 | **FROZEN** | **DONE** |
| `SPEC-ZONE-E` | `media-ui` | [.sdd/specs/media-ui/overview.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ui/overview.md) | 4 AMOLED таба, NowPlayingScreen v1 | v1.0 | **FROZEN** | **DONE** |
| `SPEC-TG-01` | `media-ingress` | [.sdd/specs/media-ingress/telegram_pause_fix.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ingress/telegram_pause_fix.md) | Подавление дублирующих сессий Telegram | v1.0 | **FROZEN** | **DONE** |
| `SPEC-BUG-09A` | `media-ingress` | [.sdd/specs/media-ingress/seek_controller_resolution.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ingress/seek_controller_resolution.md) | Резолвинг MediaController с ACTION_SEEK_TO | v1.0 | **FROZEN** | **DONE** |
| `SPEC-BUG-09B` | `media-core` | [.sdd/specs/media-core/pause_session_lifecycle.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-core/pause_session_lifecycle.md) | Жизненный цикл сессий при PAUSED, защита playCount | v1.0 | **FROZEN** | **DONE** |
| `SPEC-ING-03` | `media-ingress` | [.sdd/specs/media-ingress/target_session_arbitration.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ingress/target_session_arbitration.md) | Арбитраж сессий и защита активного плеера | v1.0 | **FROZEN** | **DONE** |
| `SPEC-BUG-11` | `media-ingress` | [.sdd/specs/media-ingress/skip_and_lifecycle_cleanup.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ingress/skip_and_lifecycle_cleanup.md) | Skip-контроллеры и очистка завершенных сессий | v1.0 | **FROZEN** | **DONE** |
| `SPEC-MYCHORDS-01` | `lyrics-engine` | [.sdd/specs/lyrics-engine/mychords_parser_spec.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/lyrics-engine/mychords_parser_spec.md) | Резервный парсер аккордов MyChords.net | v1.0 | **FROZEN** | **DONE** |
| `SPEC-TEACH-03` | `lyrics-engine` | [.sdd/specs/lyrics-engine/teach_mode_inspector_fix.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/lyrics-engine/teach_mode_inspector_fix.md) | Touch Inspector и проброс трека в TeachMode | v1.0 | **FROZEN** | **DONE** |
| `SPEC-TEACH-05` | `lyrics-engine` | [.sdd/specs/lyrics-engine/teach_inspector_v2_spec.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/lyrics-engine/teach_inspector_v2_spec.md) | Touch Inspector v2 (scroll guard, debounce, strip) | v1.0 | **FROZEN** | **DONE** |
| `SPEC-CORE-SEC-01` | `media-core` | [.sdd/specs/media-core/core_security_and_stability_spec.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-core/core_security_and_stability_spec.md) | Устранение уязвимостей FileProvider, Network, Singleton | v1.0 | **FROZEN** | **DONE** |
| `SPEC-CORE-BL-01` | `media-core` | [.sdd/specs/media-core/blacklist_storage_spec.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-core/blacklist_storage_spec.md) | Хранилище Blacklist, Room v11, авто-purge | v1.0 | **FROZEN** | **DONE** |
| `SPEC-THEME-01` | `media-ui` | [.sdd/specs/media-ui/theme_palette_system_spec.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ui/theme_palette_system_spec.md) | Система 4 тем и динамическая палитра LirixThemeTokens | v1.0 | **FROZEN** | **DONE** |
| `SPEC-UI-06` | `media-ui` | [.sdd/specs/media-ui/overflow_and_capsule.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ui/overflow_and_capsule.md) | Меню ⋮ и плавающая капсула навигации | v1.0 | **FROZEN** | **DONE** |
| `SPEC-GST-01` | `media-ui` | [.sdd/specs/media-ui/gestures_spec.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ui/gestures_spec.md) | Жесты управления, виниловый свайп, HorizontalPager | v1.0 | **FROZEN** | **DONE** |
| `SPEC-TURNTABLE-01`| `media-ui` | [.sdd/specs/media-ui/analog_turntable.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ui/analog_turntable.md) | Аналоговый винил и тонарм | v1.0 | **FROZEN** | **DONE** |
| `SPEC-TAPSYNC-01` | `media-ui` | [.sdd/specs/media-ui/lrc_tap_sync_studio.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ui/lrc_tap_sync_studio.md) | Tap-to-Sync студия разметки караоке | v1.0 | **FROZEN** | **DONE** |
| `SPEC-UI-BL-01` | `media-ui` | [.sdd/specs/media-ui/blacklist_settings_spec.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ui/blacklist_settings_spec.md) | UI конструктора правил BlacklistSettingsScreen | v1.0 | **FROZEN** | **DONE** |
| `SPEC-DESIGN-RESEARCH-01` | `media-ui` | [.sdd/specs/media-ui/design_research_aimp_yandex.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ui/design_research_aimp_yandex.md) | Исследование и эталоны AIMP vs Яндекс Музыка | v1.0 | **FROZEN** | **DONE** |

---

## 3. Активные рабочие стримы (В работе и готовы к взятию)

### Стрим A: Lirix Connect (Спецификация `SPEC-CONNECT-01`)
*Архитектурный фундамент: P2P Iroh QUIC, Ed25519 Pinning, CBOR протокол, Handoff & Audio Cast.*

| ID Задачи | Название и функция | Файл реализации | Исполнитель | Статус | DoR / Критерии готовности |
|---|---|---|---|---|---|
| `TASK-CON-SEC-01` | Политика сопряжения: TTL 120с, HMAC-proof, TrustStore, «Забыть» | `shared/.../connect/security/*` | Core Engineer | **`DONE`** ✅ | Gate 7/8 PASS, unit-тесты зеленые |
| `TASK-CON-CORE-02` | Iroh-транспорт, ALPN `lirix/connect/1`, кодек PairingPayload, discovery | `shared/.../connect/transport/*` | Core Engineer | **`DONE`** ✅ | Gate 7/8 PASS, unit-тесты зеленые |
| `TASK-CON-PROTO-03` | CBOR-сериализация LirixConnectMsg, ConnectSessionManager, epoch/роли | `shared/.../connect/proto/*` | Core Engineer | **`DONE`** ✅ | Gate 7/8 PASS, unit-тесты зеленые |
| `TASK-CON-TIME-04` | Синхронизация часов (NTP Ping/Pong) и интерполяция позиции (±100мс) | `shared/.../connect/time/*` | Core Engineer | **`DONE`** ✅ | Gate 7/8 PASS, unit-тесты зеленые |
| `TASK-CON-REG-05` | Реестр устройств, присутствие (DeviceHeartbeat, last-seen), Android FG Service | `shared/..., app/...` | Core Engineer | **`DONE`** ✅ | Gate 7/8 PASS, unit-тесты 7/7 зеленые |
| `TASK-CON-HANDOFF-06` | Логика Handoff: TrackRef, очередь, караоке, кроссфейд 300мс, fallback на Cast | `shared/..., desktop/..., app/...` | Core Engineer | **`DONE`** ✅ | Gate 7/8 PASS, unit-тесты 6/6 зеленые |
| `TASK-CON-UI-07` | BottomSheet «Воспроизвести на устройстве» (Android): CameraX, громкость, a11y | `app/.../ui/connect/*` | Android UI Coder | **`DONE`** ✅ | Gate 7/8 PASS, unit-тесты 4/4 зеленые |
| `TASK-CON-UI-08` | Popover и QR-генератор (Desktop): TTL-таймер 120с, подтверждение, переименование | `desktop/.../ui/*` | Desktop UI Coder | **`DONE`** ✅ | Gate 5/5 PASS, компиляция :desktop:compileKotlin OK |
| `TASK-CON-CAST-09` | Cast-приёмник: адаптивный джиттер-буфер 40-80мс, PLC/FEC, отчёт CastStatus | `shared/.../connect/cast/*` | Audio Engineer | **`DONE`** ✅ | Gate 7/8 PASS, unit-тесты 9/9 зеленые |
| `TASK-CON-CAST-10` | Cast-источник Windows: WASAPI Loopback → Opus 192k → QUIC датаграммы | `desktop/.../cast/*` | Audio Engineer | **`DONE`** ✅ | Gate 3/3 PASS, unit-тесты :desktop:test зеленые |
| `TASK-CON-CAST-11` | Cast-источник Android: AudioPlaybackCapture, MediaProjection, FG service | `app/.../connect/cast/*` | Audio Engineer | **`DONE`** ✅ | Gate 7/8 PASS, unit-тесты зеленые |
| `TASK-CON-WIN-12` | Windows: запрос брандмауэра и настройка UDP-правила | `desktop/.../system/*` | Windows Engineer | **`DONE`** ✅ | Gate 3/3 PASS, unit-тесты :desktop:test зеленые |
| `TASK-CON-QA-13` | Автоматизированный тест-стенд CI: потери, джиттер, NAT/relay, смена сети, NFR | `shared/.../connect/qa/*` | QA Engineer | **`DONE`** ✅ | Gate 7/8 PASS, unit-тесты 5/5 NFR зеленые |
| `TASK-CON-FIX-14` | CameraX TextureView (`COMPATIBLE`) + ZXing Y-plane stride fix | `app/.../ui/connect/QrScannerView.kt` | Android UI Coder | **`DONE`** ✅ | Gate 7/8 PASS: видоискатель отображает видеопоток, QR распознан |
| `TASK-CON-FIX-15` | Запрос разрешения RECORD_AUDIO + привязка MediaProjection к Audio Cast | `app/.../DeviceConnectBottomSheet.kt, AudioCastForegroundService.kt` | Audio Engineer | **`DONE`** ✅ | Gate 7/8 PASS: runtime permission + сквозная передача projection |
| `TASK-CON-FIX-16` | Активация DesktopConnectServer на порту 4433 и запуск Desktop | `desktop/.../Main.kt, DesktopConnectServer.kt` | Desktop Engineer | **`DONE`** ✅ | Gate 7/8 PASS: порт 4433 LISTEN, JavaSound SourceDataLine готов |

---

### Стрим B: Устранение замечаний мобильного интерфейса (`SPEC-UI-MOBILE-2.0`)
*Устранение отклонений по ревью коллажа экранов 01–11: честность данных, заметки, аккорды, TapSync, полировка.*

| ID Задачи | Название и функция | Файл реализации | Исполнитель | Статус | DoR / Критерии готовности |
|---|---|---|---|---|---|
| `TASK-UI-MOB-FIX-01` | Данные и честность: фильтрация Unknown Artist, время суток, имена приложений в Blacklist | `app/analytics/*, app/ui/*` | Android UI Coder | **`DONE`** ✅ | Gate 7/8 PASS, unit-тесты зеленые |
| `TASK-UI-MOB-FIX-02` | Контекстная строка сдвига/шрифта и эргономика заметок с клавиатурой (`imePadding`) | `app/.../NowPlayingScreen.kt` | Android UI Coder | **`DONE`** ✅ | Gate 7/8 PASS, unit-тесты зеленые |
| `TASK-UI-MOB-FIX-03` | Слоговое позиционирование аккордов, акцентный слой, тональность Bm и кнопки 48dp | `shared/..., app/...` | Android UI Coder | **`DONE`** ✅ | Gate 7/8 PASS, unit-тесты зеленые |
| `TASK-UI-MOB-FIX-04` | Экран Tap-to-Sync: единый AccentFallback, отказ от неона/градиента, sentence case, insets | `app/.../LrcTapSyncStudioDialog.kt` | Android UI Coder | **`DONE`** ✅ | Gate 7/8 PASS, unit-тесты зеленые |
| `TASK-UI-MOB-FIX-05` | Полировка UI: очистка капса, векторные иконки без эмодзи, отступ дока, контраст | `app/..., assets/...` | Android UI Coder | **`DONE`** ✅ | Gate 7/8 PASS, ADR-037 зафиксирован |
| `TASK-BUG-FLT-04` | Сквозное восстановление FloatingLyricsOverlay (UI привязка, SYSTEM_ALERT_WINDOW диалог, Compose WindowManager fix) | `app/.../ui/*, app/.../service/*` | Android UI Coder | **`DONE`** ✅ | Gate 7/8 PASS, unit-тесты зеленые |
| `TASK-UI-TAPSYNC-REDESIGN` | Полный редизайн Tap-to-Sync Studio: Literata + Golos Text, Insets 16dp, No-Slop Deck | `app/.../LrcTapSyncStudioDialog.kt` | Android UI Coder | **`DONE`** ✅ | Gate 7 PASS: Literata + Golos tnum, insets 16dp, assembleDebug OK |
| `TASK-TEACH-LINEBREAKS-01` | Восстановление переносов строк и двустороннее отлипание аккордов в ядре | `shared/.../LrcSyncEngine.kt` | Core Engineer | **`DONE`** ✅ | Gate 7/8 PASS, 160 unit-тестов зеленые |
| `TASK-UI-TAPSYNC-INSETS-02` | Защита кнопок от перекрытия жестовой навигацией (safeBottomPadding >= 28dp) | `app/.../LrcTapSyncStudioDialog.kt` | Android UI Coder | **`DONE`** ✅ | Gate 7/8 PASS: кнопки полностью видны над баром |
| `TASK-UI-EDIT-LYRICS-01` | Компонент ручного редактирования текста песни (EditLyricsDialog) и интеграция | `app/.../ui/*, app/.../components/*` | Android UI Coder | **`DONE`** ✅ | Gate 7/8 PASS: тулбар помощников, синхронизация с Room |
| `TASK-UI-LYRICS-SEARCH-LIFECYCLE-01` | Индикация поиска текста «Ищем текст песни...» и сброс stale-кэша | `app/.../NowPlayingScreen.kt, EmptyPlayerStateView.kt` | Android UI Coder | **`DONE`** ✅ | Gate 7/8 PASS: stale cache исключен, поиск визуализирован |
| `TASK-UI-LIB-THEME-FIX-01` | Динамическая палитра тем в Медиатеке (устранение хардкода Graphite AccentFallback) | `app/.../LibraryScreen.kt, TrackActionBottomSheet.kt` | Android UI Coder | **`DONE`** ✅ | Gate 7/8 PASS: темы динамически связаны, скриншот подтвержден |



---

## 4. Реестр завершённых задач (Реализовано и принято, Gate 8 PASS)

| ID | Функция | Файл | Зона | Исполнитель | Статус |
|---|---|---|---|---|---|
| `TASK-REL-MOB-E2E` | Сборка и установка APK по ADB на физическое устройство (`creek_p_global`) | `app/.../outputs/apk/debug/app-debug.apk` | release | Release Coder | **`DONE`** ✅ |
| `TASK-REL-DSK-PKG` | Сборка автономных Windows дистрибутивов (Lirix-1.2.1.exe, Lirix-1.2.1.msi) | `desktop/.../binaries/main/exe/*` | release | Windows Engineer | **`DONE`** ✅ |
| `TASK-CON-UI-07` | BottomSheet «Воспроизвести на устройстве» (Android): CameraX/ZXing, громкость, a11y | `app/.../ui/connect/*` | media-connect | Android UI Coder | **`DONE`** |
| `TASK-UI-MOB-FIX-01..05` | Полный пакет фиксов мобильного редизайна (честность, сдвиг, аккорды, TapSync, полировка) | `app/..., shared/...` | media-ui | Android UI Coder | **`DONE`** |
| `TASK-BUG-DSK-CHORDS-01` | Устойчивый поиск аккордов в Desktop (изоляция таблицы AmDm, fallback по title) | `desktop/.../DesktopLyricsService.kt` | lyrics-engine | Desktop UI Coder | **`DONE`** |
| `TASK-UI-PROMPTER-FIX-01` | Устранение дефектов рендера Teleprompter Deck (шрифты, иконки, верстка, idle, DWM) | `desktop/...` | media-ui | Desktop UI Coder | **`DONE`** |
| `TASK-UI-MOB-01` | Внедрение палитр Graphite/Paper и шрифтов Literata/Golos на Android | `app/.../theme/*` | media-ui | Android UI Coder | **`DONE`** |
| `TASK-UI-MOB-02` | Автономный NowPlayingScreen (360x800, скрытый док, режим Фокус по Double-Tap 72%) | `app/.../NowPlayingScreen.kt` | media-ui | Android UI Coder | **`DONE`** |
| `TASK-UI-MOB-03` | Ландшафтный режим NowPlayingScreen (800x360, пульт 280dp + суфлёр 520dp, до 42sp) | `app/.../NowPlayingScreen.kt` | media-ui | Android UI Coder | **`DONE`** |
| `TASK-UI-MOB-04` | Редизайн LibraryScreen (поиск, чип-фильтры, карточки 64dp, Undo Snackbar) | `app/.../LibraryScreen.kt` | media-ui | Android UI Coder | **`DONE`** |
| `TASK-UI-MOB-05` | Редизайн WrappedScreen (книжное эссе Literata, честные факты, монохромный шеринг) | `app/.../WrappedScreen.kt` | media-ui | Android UI Coder | **`DONE`** |
| `TASK-UI-MOB-06` | Разгрузка TeachModeScreen, человеческие имена Blacklist, Tap-to-Sync лаг -200мс | `app/.../ui/*` | media-ui | Android UI Coder | **`DONE`** |
| `TASK-AUD-WASAPI-01` | Честный пик-метр через WASAPI Loopback (IAudioMeterInformation, dBFS -60..0) | `native/lirix-media-bridge/WasapiLoopbackMeter.cs` | win-ingress | Windows Engineer | **`DONE`** |
| `TASK-UI-PROMPTER-01` | Палитры Graphite & Chalk и Paper & Ink с динамическим Contrast Clamp акцента | `shared/.../LirixThemeTokens.kt` | media-ui | UI Coder | **`DONE`** |
| `TASK-UI-PROMPTER-02` | Типографика: Literata Medium (стихи) + Golos Text (интерфейс, табличные tnum) | `shared/.../LirixTypography.kt` | media-ui | UI Coder | **`DONE`** |
| `TASK-UI-PROMPTER-03` | Суфлёрская раскладка: колонка 640dp, левое выравнивание, 3dp маркер, 44dp слот seekTo | `desktop/.../DesktopTeleprompterView.kt` | media-ui | UI Coder | **`DONE`** |
| `TASK-UI-PROMPTER-04` | Панель транспорта с регулятором смещения задержки текста GSMTC (±0.1 с) | `desktop/.../DesktopTransportBar.kt` | media-ui | UI Coder | **`DONE`** |
| `TASK-UI-PROMPTER-05` | Сборка Teleprompter Deck (Studio 1040x680, Mini 380x110, HUD) и дистрибуция | `desktop/.../DesktopDeckScreen.kt` | media-ui | UI Coder | **`DONE`** |
| `TASK-UI-CHORDS-TOOLBAR-COMPACT-01` | Ультра-компактный тулбар аккордов и максимизация суфлёра (>85% экрана) | `shared/ui/player/ChordsTabContent.kt` | media-ui | UI Developer | **`IN_PROGRESS`** |
| `TASK-UI-EDIT-LYRICS-01` | Полноэкранный редактор текста песни EditLyricsDialog с помощниками | `app/ui/components/EditLyricsDialog.kt` | media-ui | UI Developer | **`DONE`** |
| `TASK-UI-TAPSYNC-INSETS-02` | Фикс безопасных отступов кнопок в Tap-to-Sync Studio над жестовой полосой | `app/ui/components/LrcTapSyncStudioDialog.kt` | media-ui | UI Developer | **`DONE`** |
| `TASK-TEACH-LINEBREAKS-01` | Интеллектуальное разбиение строк и отвязка аккордов в LrcSyncEngine | `shared/feature/lyrics/LrcSyncEngine.kt` | lyrics-engine | Core Engineer | **`DONE`** |
| `TASK-UI-TAPSYNC-REDESIGN` | Редизайн TapSync Studio в стиле Lirix 2.0 (Literata, Golos, эргономика тапа) | `app/ui/components/LrcTapSyncStudioDialog.kt` | media-ui | UI Developer | **`DONE`** |
| `TASK-UI-DSK-01..07` | Пакет задач Desktop Deck 2.0 (TopBar, VU-метр, AIMP Compact Widget, дистрибутив EXE) | `desktop/...` | media-ui | UI / Build Coder | **`DONE`** |
| `TASK-KMP-01..13` | Полная KMP-миграция :shared, :desktop, Room KMP 2.7.1, C# Native AOT мост GSMTC | `shared/..., desktop/..., native/...` | kmp-core | KMP Engineers | **`DONE`** |
| `TASK-BLK-01..09` | Полный цикл Черного Списка: Room v11 MIGRATION_10_11, каскадный Purge, UI конструктор | `app/storage/*, app/feature/*, app/ui/*`| media-core/ui | Storage/UI Coders | **`DONE`** |
| `TASK-THEME-01..05` | Система 4 тем (Spotify, Yandex, Obsidian, Pastel), LirixThemeTokens, Dialog, Vinyl | `shared/..., app/...` | media-ui | UI Coder | **`DONE`** |
| `TASK-CORE-01` | Устранение критических уязвимостей аудита (FileProvider, Network, Singleton) | `app/...` | media-core | Core Engineer | **`DONE`** |
| `TASK-GST-01-A..C` | Система жестов: свайпы винила (Next/Prev), дабл-тап (Play/Pause), HorizontalPager | `app/...` | media-ui | UI Coder | **`DONE`** |
| `TASK-BUG-09A..B` | Корректный seekTo контроллер и защита playCount при паузе | `app/...` | media-ingress | Ingress Coder | **`DONE`** |
| `TASK-ING-03..04` | Арбитраж сессий, подавление зомби-сессий, skip-контроллеры | `app/...` | media-ingress | Ingress Coder | **`DONE`** |
| `TASK-TEACH-01..05`| TeachMode Touch Inspector v2 (scroll guard, debounce, составные селекторы, strip) | `assets/teach_inspector.js, app/ui/*` | lyrics-engine | Coder | **`DONE`** |
| `TASK-MYCHORDS-01` | Парсер MyChords.net, интеграция в каскад и гибридный поиск аккордов | `shared/..., app/...` | lyrics-engine | Coder | **`DONE`** |
| `TASK-FLT-01..03` | Плавающий системный виджет FloatingLyricsOverlayView на SYSTEM_ALERT_WINDOW | `service/FloatingLyricsService.kt` | media-ui | Coder | **`DONE`** |
| `TASK-LRC-01` | Движок синхронизации караоке LrcSyncEngine с валидацией таймкодов | `shared/.../LrcSyncEngine.kt` | lyrics-engine | Coder | **`DONE`** |
| `TASK-TG-01` | Скоринг MediaController и детерминированная пауза Telegram | `app/.../MediaSessionCollector.kt` | media-ingress | Coder | **`DONE`** |
| `TASK-WRP-01..02` | WrappedStatsEngine, GamificationEngine, 26 ачивок, Room v8 MIGRATION_7_8 | `app/analytics/*, app/storage/*` | wrapped-analytics | Coder | **`DONE`** |
| `TASK-SHR-01..03` | Генерация карточек шеринга Stories 9:16 и квадратных постеров NowPlaying | `app/feature/share/*` | media-ui | Coder | **`DONE`** |
| `TASK-UI-CHORDS-SMART-WRAP-01` | Умный перенос строк и синхронное разбиение слоговых пар аккордов | `shared/.../ChordsTabContent.kt` | media-ui | UI Coder | **`IN_PROGRESS`** |
| `TASK-UI-LYRICS-SEARCH-LIFECYCLE-01` | Жизненный цикл поиска текстов, мгновенный сброс stale-состояния, индикатор поиска | `app/ui/NowPlayingScreen.kt, app/ui/components/EmptyPlayerStateView.kt` | media-ui | UI Coder | **`DONE`** |
| `TASK-BUG-CHORD-EXTRACT-01` | Фикс усечения знака диеза `#` и ложного артикля `A` | `shared/.../GuitarChordDictionary.kt` | lyrics-engine | Coder | **`DONE`** |
| `TASK-UI-CHORDS-TOOLBAR-COMPACT-01` | Ультра-компактный тулбар аккордов и максимизация суфлёра | `shared/.../ChordsTabContent.kt` | media-ui | UI Coder | **`DONE`** |
| `TASK-UI-EDIT-LYRICS-01` | Полноэкранный редактор текста песен и умные ассистенты (разбивка на строки, удаление аккордов) | `app/ui/components/EditLyricsDialog.kt, NowPlayingScreen.kt` | media-ui | UI Coder | **`DONE`** |
| `TASK-TEACH-LINEBREAKS-01` | Сохранение переносов строк `<br>`/`<p>` в TeachMode Touch Inspector и безопасный отступ снизу | `assets/teach_inspector.js, app/ui/*` | lyrics-engine | Coder | **`DONE`** |
| `TASK-UI-TAPSYNC-INSETS-02` | Исправление системных инсетов и поднятие кнопок TapSync над жестовой полоской | `app/ui/components/LrcTapSyncStudioDialog.kt` | media-ui | UI Coder | **`DONE`** |
| `TASK-UI-TAPSYNC-REDESIGN` | Редизайн TapSync Studio (устранение неона/градиентов, строгие дизайн-токены) | `app/ui/components/LrcTapSyncStudioDialog.kt` | media-ui | UI Coder | **`DONE`** |
| `ARCH-001` | Базовый архитектурный план пивота в Music & Lyrics Hub | `.sdd/architecture.md` | Arch | Архитектор | **`DONE`** |

## Connect PCM repair — 2026-10-16 (новый проверяемый пакет)

Пользователь подтвердил отсутствие звука. Аудит: `.sdd/reports/CONNECT_DIALECTIC_SYNTHESIS.md`. Спецификация: `.sdd/specs/media-connect/lan_pcm_audio_repair_v1.md` v1.0 APPROVED, ADR-047. Старые DONE не доказывают исправность runtime аудио.

| ID | Область | Зависит от | Исполнитель | Статус |
|---|---|---|---|---|
| CON-PCM-QA | Regression tests до реализации | — | Antigravity: Android Cast Audio Source Implementation | IN_PROGRESS |
| CON-PCM-000 | enum CastCodec.PCM_S16LE | QA | Antigravity | TODO |
| CON-PCM-001 | packetizePcm16Stereo | QA | Antigravity | TODO |
| CON-PCM-002 | Android startCast | 000,001 | Antigravity | TODO |
| CON-PCM-003 | Android stopCast | 002 | Antigravity | TODO |
| CON-PCM-004 | Desktop openAudioLine | 000,QA | Antigravity | TODO |
| CON-PCM-005 | Desktop handleMessage | 004 | Antigravity | TODO |
| CON-PCM-006 | Desktop writePcm | 005 | Antigravity | TODO |
| CON-PCM-007 | Desktop startUdpAudioListener | 006 | Antigravity | TODO |
| CON-PCM-REVIEW | Независимая проверка спецификации и diff | 003,007 | Independent reviewer | TODO |
| CON-PCM-SMOKE | Слышимый Android→Windows,stop/restart | REVIEW | Runtime QA + пользователь | TODO |

Отчет исполнителя: `.sdd/reports/CONNECT_PCM_EXECUTION.md`. Gate8 до слышимого звука не пройден. При ошибках отчет BLOCKED/FAIL; без устройства NOT_RUN. Codex продуктовый код не пишет.

## Дополнительные требования сессии
- WRAPPED-POSTER-AUDIT: анализ завершен, .sdd/reports/WRAPPED_POSTER_THEME_AUDIT.md; направление .sdd/specs/media-ui/wrapped_poster_theme_v2_proposal.md DRAFT, реализация не запущена.
- SESSION-COMMITS: пользователь требует clean git status в конце; PENDING.
- SESSION-RELEASE: GitHub Release в конце, .sdd/intake/SESSION_RELEASE_REQUIREMENTS.md; PENDING build/review/штатная авторизация.


## Device smoke — 2026-10-16 23:00
- Android 1.4.0/code17 installed preserving data; actual themed Wrapped export verified on phone.
- Fresh Desktop createDistributable PASS with --no-configuration-cache; running local app, TCP+UDP4433 bound.
- User reports green connection without sound. CR-CON-PCM-03 replaces simulated handoff click with real capture permission flow; physical smoke OPEN.
- Android full unit suite PASS after dispatching test Main via coroutine test scheduler. Desktop full suite has one scheduling-sensitive reverse-cast test; PCM receiver 9 tests PASS. Release HELD for device verification, no tag/push/publication.

- Device smoke PASS: user confirmed audible Android→Windows audio, sustained one minute and stop/start. CR-CON-PCM-03 closes the simulated selection route. Release gate now awaits final packaging/tests.


## Desktop feature parity — 2026-10-17
- IN_PROGRESS: user requests all Android features on Windows. Baseline9ef5bba/v1.4.0; branch codex/desktop-feature-parity.
- Approved scope: .sdd/specs/media-ui/desktop_feature_parity.md. Codex implements directly per existing instruction.
- First dependency: persistent desktop library/session recording; then feature UI and shared-engine integrations.

## HUD drag regression — 2026-10-17

- User report: dragging the desktop overlay sends it outside the screen.
- Status: IN_PROGRESS. Coordinator assigned architecture/specification to `hud_drag_architect`; product fix and independent review follow the specification.
- Architecture/specification gate passed: `.sdd/specs/media-ui/hud_drag_fix.md` v1; implementation card `.sdd/tasks/HUD-DRAG-001.md` assigned to `hud_drag_coder`.
- Implementation and independent static review PASS; `:desktop:compileKotlin :desktop:test` PASS (42 tests, zero failures/errors/skips). Native gate NOT RUN after two capture timeouts. Separate local validation image: `desktop/build/hud-drag-fixed-20261017/Lirix/Lirix.exe`; no running app was terminated or overwritten. See `.sdd/reports/HUD_DRAG_VALIDATION.md`.
- Acceptance: ordinary and repeated dragging track the pointer without runaway movement; header buttons remain clickable; geometry survives hide/reopen; existing reset remains available. Check Windows scaling and secondary monitor origins when the runtime permits.
- User cannot currently perform manual checks. Do not make manual acceptance a prerequisite for implementation or automated regression checks.

## Desktop karaoke missing mode — 2026-10-17

- User clarified that Karaoke tab is absent; only Text is visible. Intake: `.sdd/intake/DESKTOP_KARAOKE_MISSING_MODE.md`.
- Architect: `karaoke_architect`; QA: `karaoke_qa`; implementation: `karaoke_coder`. Root coordinates according to supplied AGENTS.md.
- Specification direction and frozen helper API approved: `.sdd/specs/media-ui/desktop_karaoke_fix.md`. Architect completes exact one-function cards; QA establishes regressions before implementation, then hands build ownership to coder.
- Required behavior: explicit Karaoke/Text modes, honest untimed state with studio action, sampled playback clock, offset-correct seek and shared offset observation. Separate captured-scope hotkey fix addresses observed missing Main dispatcher.
- Status: IN_PROGRESS. Native acceptance remains separate from unit tests. Preserve HUD fix and published release tag.
- Implementation and independent review PASS. Final desktop 51 tests PASS; shared 173 existing tests PASS/up-to-date. Standard fresh distributable created and project jar hashes verified. Native acceptance NOT RUN (capture timeout); current running copies retain old UI. Evidence: `.sdd/reports/DESKTOP_KARAOKE_VALIDATION.md`. No release publication.
- User smoke PASS, 2026-10-17: «работает караоке». Core desktop karaoke accepted by user; detailed native edge cases and broader feature parity remain separate checks.

## Desktop readiness — 2026-10-18

- Audits complete (source-based): architecture, QA and UI/UX reports; consolidated `.sdd/reports/DESKTOP_READINESS_PLAN.md`.
- DSK-READY-001–005 implemented, 11 real service/Room regression tests; full desktop 62 + shared 173 PASS (shared UP-TO-DATE), independent source review no blockers. Native offline acceptance pending.
- Next bounded UI corrections: honest library filters/empty-selection states and six-theme selector; dirty notes must survive navigation before selection changes ship. Broader draft-close protection and merge workflow remain open.
- Isolated DB/cache audit launch succeeded via safe alternative; screenshots still timed out. No visual PASS or release readiness declared.
- DESK-UX-101–105 APPROVED/IN_PROGRESS: honest library states/filters/selection, ephemeral note drafts retained across navigation, exact six-theme selector. QA 8 pure view-state tests written; initial RED missing-API compile only. Product executor desktop_library_ux_coder.
- Static visual audit alternative APPROVED: actual Compose ImageComposeScene CPU previews with real composables/fixtures, no new dependency/Main/native integration startup. Native focus/hotkeys/HUD/audio remain separate. Preview executor offline_lyrics_reviewer (role changed from completed offline review).

- DESK-UX-101–105 and DESK-VIS-201–205: automated checks PASS, desktop 81/shared 173; 61 detached preview frames produced. Native acceptance remains open.

## Live desktop playback regressions — 2026-10-18
- IN_PROGRESS: user reports YouTube seeks successfully from a karaoke line while Lirix timer remains behind; artwork also remains from the previous track.
- Architect playback_sync_architect owns diagnosis, strict specification and delegated regression/fix/review. Preserve existing UI batch and published release.
- SYNC-WIN-001–007: implementation and independent source review PASS; 24 native regression/protocol tests PASS. Fresh NativeAOT bridge published separately; desktop/shared checks and packaging IN_PROGRESS. Live browser acceptance pending.
- SYNC-WIN-001–008: implementation/review/package PASS, 24 native + 81 desktop + 173 shared tests without failures (JVM final run UP-TO-DATE). Fresh NativeAOT bridge included through corrected resource staging; published/resource/packaged SHA256 match. See DESKTOP_PLAYBACK_SYNC_BUILD.md. Physical browser seek/artwork acceptance pending; no release publication.

## Draft safety — 2026-10-19
- IN_PROGRESS: direct implementation authorized; session-scoped drafts, guarded dialog/app exit and retryable saves. Spec desktop_draft_safety.md. No subagents.

- Priority update: user reminded about song translations. After the bounded draft-safety patch, the next feature is Russian translation import/preview/offline display from Amalgama, before database-merge UI. Design exists; translation implementation is not yet shipped.

- Draft safety IMPLEMENTED/AUTOMATED PASS: 87 desktop tests and 173 shared tests without failures, fresh distributable and packaged hash checks PASS. Session retention only; native dialog/tray checks pending. Next priority: translations.

## Russian translations — 2026-10-19
- IN_PROGRESS: shared Amalgama explicit URL import, preview/confirm, additive Room v12 migration on both platforms, offline Original/Russian/Together UI. Direct implementation by root; original proposal narrowed by song_translations_ru_implementation.md. No automatic search or karaoke subtitle mapping in v1.

- Russian translations v1 IMPLEMENTED/AUTOMATED PASS: desktop 95 passed +1 unrelated opt-in skip, shared173, Android198; actual additive migrations on both platforms PASS. Eight shared-panel renders and live packaged-provider import of supplied URL (70 paired rows) verified. Fresh PC distributable/APK built. Physical acceptance, automatic discovery, bilingual karaoke and archive extension remain open. Evidence SONG_TRANSLATIONS_RU_V1.md.

## Automatic translations correction — 2026-10-19
- IN_PROGRESS: user clarified automatic parsing/discovery is the goal. Added catalog discovery + exact page validation, active-track coordinator, automatic save, protected manual selection and explicit ambiguous candidates. Manual import remains fallback. No subagents. Contract song_translations_automatic_v2.md.

- Automatic translations v2 IMPLEMENTED/AUTOMATED PASS:108 desktop +173 shared +198 Android tests passed;1 unrelated optional workspace generator skipped. No-click reader test, real Room protection and live metadata-only discovery Joseph/Hellraiser/Hurricane PASS. Fresh PC/APK and packaged hashes verified. Physical acceptance pending. See SONG_TRANSLATIONS_AUTOMATIC_V2.md.

## LyricsTranslate multilingual discovery — 2026-10-19
- Brief ready: .sdd/LYRICSTRANSLATE_MULTILINGUAL_BRIEF.md. Primary search and sample translation page inspected; no native parser or integration claimed. Architecture/product gate pending role authorization under latest user-supplied AGENTS.md. No agents launched.

- LyricsTranslate prototype: actual browser DOM verified; HTML parser and compatible metadata implemented. Direct HTTP403 prevents live discovery acceptance. Multilingual storage/settings/routing still pending; see reports/LYRICSTRANSLATE_PARSER_PROTOTYPE.md.

## Multilingual translations v3 — 2026-10-19
- IN_PROGRESS: identified HTTP now200 and real POST search verified. LyricsTranslate routing, language-aware DAO/coordinator, persistent target selection and shared reader implemented. Spec song_translations_multilingual_v3.md. Live/test/package validation in progress. No agents.

- Multilingual v3 IMPLEMENTED/AUTOMATED+LIVE PROVIDER PASS:120 desktop +173 shared +198 Android tests passed;2 opt-in checks skipped. Direct five-target search/load and browser-channel metadata fallback verified. Actual narrow/wide panel selection tests and fresh PC/APK packaging pass; jar hashes match. See reports/SONG_TRANSLATIONS_MULTILINGUAL_V3.md. Physical acceptance pending; no release publication.

## Desktop UX audit corrections — 2026-10-20
- User authorized all seven findings in order. First package e94990c: original fallback, persistent transport and compact full-width deck; focused actual Compose checks pass. Second package: honest statuses, snapshot-based poster preview/export, simple source text entry and parsed library LRC. Targeted six-theme/two-size matrix and poster controls pass; final broad tests and packaging in progress. Spec media-ui/desktop_ux_audit_corrections_v1.md. No agents.

- Seven UX findings IMPLEMENTED/AUTOMATED PASS. Final123 desktop +173 shared +198 Android passed (494 total),2 optional network probes skipped. Six-theme/two-size workspace matrix, persistent playback actions and real poster menu switching passed. Final source dialog binds to captured song across playback changes. PC/APK built and packaged jar hashes match. Report DESKTOP_UX_CORRECTIONS_20261020.md. Phone disconnected; native acceptance separate.

## Karaoke studio — 2026-10-20
- IN_PROGRESS / ARCHITECTURE. User approved retaining phone Tap-to-Sync plus a separate precise line-timing studio. Intake: .sdd/intake/KARAOKE_STUDIO_V1.md. Architect karaoke_studio_architect owns source audit, zone specifications and atomic cards. Implementation follows frozen contracts. Existing attachment inputs remain untracked. No release publication.

- Architecture delivered: .sdd/architecture/karaoke_studio_v1.md; model/UI contracts and KST-001..012 drafted. Verified risks: unclamped offset UI, current-track retargeting, premature close/nontransactional apply, ephemeral drafts and duplicate timestamp row keys. Direction approved; exact API/card gate refinement required before coding. Architect coordinates worker and test/build ownership.


### Karaoke priority correction — 2026-10-20
- User explicitly narrowed current delivery: verify/fix text offset, seeking and timing save; correct clipping and duplicated Connect actions FIRST. Studio implementation is DEFERRED until baseline corrections verified. Architect and worker notified immediately; no unfinished studio feature to ship in this package.

- Baseline corrections implementation underway: no offset migration, same returned clamped value for UI/storage, centralized inverse seek, negative pre-vocal lyric time retained, stable reader metrics/occurrence keys, portrait Connect dedup. Captured-target transaction and quick-dialog awaited save in integration. Separate read-only QA review started; architect owns builds. Earlier Studio RED is not acceptance evidence for this narrower package.

- Existing karaoke foundations AUTOMATED/REVIEW PASS: Android204 passed; unchanged shared173 cached, unchanged desktop123 passed/2 optional skips up-to-date (500 passed total). Fresh APK built. Report .sdd/reports/KARAOKE_FOUNDATIONS_20261020.md. No physical/visual acceptance: ADB empty. Studio remains DEFERRED; no publication.

## Repository hygiene — 2026-10-20
- DONE: cleanup PR https://github.com/Asm-o-Dan/lirix/pull/1 merged into main as ea52bc1 after GitHub CI success. Archive docs indexed under docs; APKs, UI/log dumps, unused tunnel binary and private machine helpers untracked with local copies retained. Same cleanup applied to feature branch; orphan local skills gitlink and one-shot scripts untracked here. Published v1.4.0 tag/assets/status unchanged; future prerelease flag derives from tag suffix.

## Android release 1.5.0 — 2026-10-20
- IN_PROGRESS. User explicitly authorizes publication for Android only; no PC build, desktop version edit or Windows release assets. Release engineer android_release_150 owns version18/1.5.0, signed build, certificate continuity, Android/shared tests and notes. Root verifies then commits/pushes fresh v1.5.0 tag; published v1.4.0 unchanged.

- Build/signing VERIFIED: 1.5.0/code18, com.lirix.app, non-debuggable, APK v2/v3 signature valid, permanent private signing material ignored locally. Android204+shared173 previous tests up-to-date without failures; release assembly succeeds. Old public1.4 debug certificate differs; user reported no key then instructed continue, transition/data warning in notes. PC unchanged. Publishing branch/tag and verified local APK through authenticated GitHub UI.

## Android system backup — 2026-10-20
- IN_PROGRESS. User requested system backup and instructed execution. Intake .sdd/intake/ANDROID_SYSTEM_BACKUP.md. Architect/implementer android_system_backup owns manifest/XML allowlist, SQLite consistency and Android verification. Include library/settings; exclude Connect trust/addresses and derived artwork/cache. No PC changes or destructive phone test. Existing1.4 migration and already-created1.5tag are separate constraints; publication remains pending.

- IMPLEMENTED / AUTOMATED+SOURCE REVIEW PASS. Android209 tests pass incl5policy tests; shared173 unchanged up-to-date. DebugAPKassembled with actualmanifestresources checked (allowBackuptrue, bothpolicyreferences, API28encryptedvariant, moderncloud+D2D). Separate reviewerbackup_policy_reviewPASS. No device/cloud roundtrip performed. Old1.4signaturemigration still unresolved; originalv1.5tag/APK not overwritten. Publicationversion clarification requested.

## Android release 1.5.1 — 2026-10-20
- User explicitly selected Android1.5.1 to include systembackup without movingexisting1.5.0tag. Version19, samepermanentprivatekey; Androidonly, Windowsjobs/publicationexcluded. Build/signature/metadata/assetchecks required beforeupload. NoPCsourcechanges.
- Final releasegatePASS:209Androidtests executed,173shared unchanged up-to-date; signedassembleRelease and lintVitalRelease succeed after removinginvalidredundantXMLexclusions. Effectivebackupallowlist unchanged; independentdelta reviewPASS. Sourcefreeze and commit precede finalartifactmetadata refresh/upload.
- PUBLISHED: https://github.com/Asm-o-Dan/lirix/releases/tag/v1.5.1 — stableLatest, AndroidAPK+SHA256SUMS only. Publicdownloadhash2042609e23ae7345db6f5b5e8ef2395c02ea847a977f06a92f8176a199e29b9a verified; code19, permanentcertificate, nondebuggable, compiledbackupresources and commitf21f94e verified. Old1.4metadata/assetsunchanged, Windowsjobskipped. Full209Android+173shared checks and releaselintPASS. Details ANDROID_RELEASE_151_PUBLICATION.md. Physicalbackuprestore remainsuntested; unrelateduntrackedWORKFLOW preserved.

## Android Wrapped motion prototype — 2026-10-20
- WRAPPED-MOTION: IMPLEMENTED / DEVICE ACCEPTANCE PENDING. User authorized direct Codex implementation with minimal process. Native real-data summary (minutes, favorite artist, top 3), themed 900 ms opacity reveal, replay button, explicit loading/empty states. Android APK assembled; 209 unit tests pass. System-scale 0, theme changes, period changes, long titles and scrolling require physical acceptance; ADB device absent. Full lint blocked by unchanged AndroidAudioCaptureService.kt:212 MissingPermission and LibraryScreen.kt:192 NewApi; no Wrapped errors. No desktop/storage/export changes.

## Karaoke studio — verification checkpoint 2026-10-21
- User-selected scope: line studio + real local-audio waveform; keep quick Tap-to-Sync. Word timing excluded; Japanese/Chinese romanization proposal recorded separately.
- Implementation prepared before current coordinator-only instruction. Current role: quality verification, independent review, documentation and isolated Git delivery; no new root product-code edits.
- Final debug APK assembled; Android 260 and shared 200 tests pass. Studio/draft/PCM/duration/backup coverage passes; independent review correction verified. Test artifact and evidence: intake/KARAOKE_STUDIO_IMPLEMENTATION.md.
- Final release lint PASS; independent review passed after duration correction. Phone absent from ADB; device acceptance pending and published release unchanged. See intake/KARAOKE_STUDIO_IMPLEMENTATION.md.

## Android player reading surface and context menu — 2026-10-21
- IMPLEMENTED / AUTOMATED PASS, physical acceptance pending: user supplied annotated 3830/3831 screenshots. Remove white explanatory copy, compact green attribution, move red editing/translation controls to contextual overflow/sheet, move Connect to symmetric lower transport.
- Audit confirms popup anchored to entire deck instead of MoreVert; approved bounded contract specs/android-ui/player_context_actions_v1.md. One executor owns UI implementation, preserving existing native themes and source/clock changes. Root owns quality gates and isolated Git delivery.
- Clean 679c37b + UI patch: Android231/shared189 tests PASS, Android APK and instrumentation APK compile PASS, desktop compatibility compile PASS, release lint PASS. Independent review PASS. ADB empty; actual UI tests and physical popup/font/RTL/layout acceptance pending. No release publication.

## Antigravity stream audit — 2026-10-22
- REVIEW FAIL / corrections required. User supplied mixed Latin/Han karaoke screenshot and asked to verify the JA/ZH/MP4/RU-EN session claims.
- Actual four production romanization engines reproduced unknown Han passthrough and per-line language switching. All 29 existing unmodified romanization tests passed in a standalone cached compiler/JUnit run; this is not a full Gradle/phone acceptance result.
- Confirmed core findings: partial reading displayed as complete; Japanese Han-only lines routed to Mandarin; contextual polyphones/particles/normalization gaps. Report: reports/ROMANIZATION_AUDIT_20261022.md.
- Additional read-only findings: Android locale application path disconnected, desktop locale/parity incomplete, encoder silently drops/unreliably finalizes frames, typewriter/long-line export limits. Report: reports/AG_STREAM_CLAIMS_REVIEW_20261022.md. Device codec/locales were not run; static findings are explicitly distinguished from runtime proof.
- No product/test/dependency, phone or release edits made by this audit. Prior green-test/feature-completion claims do not supersede these quality gaps.

## Audit repairs — 2026-10-22
- IN_PROGRESS: user authorized fixing all Romanization/locale/MP4 audit findings. Root coordinates under current AGENTS; owners implement approved v2 contracts and function cards.
- Roman owner: /root/romanization_audit, source-language context, real offline reading backends, qualified incomplete results, normalization and NowPlaying/reading UI. Approved pinned KuromojiIPADIC0.9.0, wanakana-core1.1.1 and houbb/pinyin0.4.0; actual compatibility/size/device gates still required.
- Locale owner: /root/ag_summary_consistency_review, Android lifecycle/resources/prefs, desktop observable/persisted language and UI catalogs, studio and Wrapped caller. Owns XML/catalog and narrowly scoped backup policy for reading preference file.
- MP4 owner: /root/mp4_repair, production backpressure/EOS/failure cleanup, text/grapheme layout, CounterRoll labels data and device codec test. CounterRollLabels(header,minutes,tracks,artists,topTracks,plays) API frozen; locale owner passes caller-localized immutable labels.
- Root owns Gradle/independent review/isolated delivery. MP4 old-code RED snapshot run active. Phone absent from ADB; no physical acceptance or release change claimed.

## Player device regressions — 2026-10-23
- User authorized hands-off ADB verification.
- REPRODUCED: Gold transpose selector changes but structural chord line remains unchanged; owner is preparing chord parsing specification and meaningful fixtures.
- REPRODUCED: heart remains outline while database favorite flag changes 0→1. Observed stored state + transactional toggle repair approved.
- CONFIRMED by source owner: repeated top safe inset; canonical AppScaffold owner handles consumption of parent padding.
- Database14 compatibility prerequisite frozen in build/repairs-baseline/db14-clean; installation gated on rebuilt Room identity.
- MP4 refreshed 26-file freeze under isolated tests. Locale/romanization and player integration pending.
- Acceptance evidence: .sdd/reports/PLAYER_DEVICE_BASELINE_20261023.md. Invalid earlier Library trial excluded.

## Android GitHub updates — 2026-10-24
- IMPLEMENTED / AUTOMATED PASS: GitHub update delivery; seven tests, debug APK and release lint pass. Daily foreground check, manual player menu, verified APK and system consent. Physical update acceptance pending: ADB empty. Contract: specs/android-ui/github_updates_v1.md; report: reports/ANDROID_UPDATES_20261024.md. No release or desktop updater changes.

## Player and precise studio polish — 2026-10-24
- IN_PROGRESS: preserve Material themes and Tap-to-Sync; adaptive player/search/studio layouts, mode transitions, live reduced-motion setting, precise shared ruler geometry and real waveform corpus. Scope: specs/android-ui/player_studio_polish_v1.md. Full Android329/shared249 unit suites pass; final debug/test APK and release lint pass. Native UI/gesture/codec tests compile but have not run: ADB empty, device acceptance requested. Exact-ms export/100+ minute playback fixed. Audit: reports/PLAYER_STUDIO_POLISH_20261024.md. Goal stays active.

## Последовательная очередь Android — 2026-10-25

- Общие настройки Android (Theme/Language/Updates/Blacklist): DELIVERED / пользовательская проверка живого плеера на телефоне ожидается. Source773434b отправлен; debug/test APK и release lint PASS; финальные12 UI-тестов PASS отдельно RU57.412s и EN58.556s; независимое ревью PASS. Реальный Back и восемь состояний системных отступов проверены. Приватная сборка: https://github.com/Asm-o-Dan/lirix-testing/releases/tag/android-preview-2026-10-25-settings (7 файлов проверены по размеру и серверному SHA256). Отчёт: reports/ANDROID_SETTINGS_SCREEN_20261025.md.
- Пользователь потребовал заканчивать предыдущую задачу перед началом следующей. После доставки настроек взят только первый пункт — восстановление поиска после появления интернета. Остальные четыре остаются отложенными.
- Очередь после настроек: 1) offline-lookup-recovery; 2) duplicate-player-editor-actions (/dialectic); 3) library-swipe-semantics; 4) remove-wrapped-replay-action; 5) profile-avatar-summary-dialectic (/dialectic, анализ до решения о реализации).
- Запись backlog в текущую codex/release-1.5.1 явно подтверждена пользователем. Пять файлов в .sdd/backlog/; исходное требование пользователя сохранять собственные изменения коммитами действует. Посторонние незакоммиченные изменения не включать в поставку.

## Восстановление поиска Android — 2026-10-25

- IN_PROGRESS, единственная задача реализации: offline-lookup-recovery. Intake и контракт revision3 утверждены после независимого DoR PASS; разрешена последовательная реализация monitor/gate, защищённого Room merge, трёх автоматических callers и компактного Retry. Спека: specs/android-ui/offline_lookup_recovery_v1.md; review: reports/ANDROID_OFFLINE_LOOKUP_SPEC_REVIEW_20261025.md.
- Сохранить чужие изменения source/clock/учёта прослушиваний в основном дереве; проверяемая поставка собирается из canonical checkout. Backlog-карточка удаляется вместе с принятым исправлением, не при начале работы.
- Общая цель интерфейса/студии остаётся открытой: точный таймлайн, реальная волна, сохранение/повторное открытие и устойчивость черновиков, плавные переходы и оставшиеся обрезания требуют собственных доказательств. Предыдущий ход дал прогресс (настройки, проверки и приватная доставка), а не завершение всей цели.
- Возобновление 2026-10-26: прежнего исполнителя нет в live tree; текущий кодер offline_recovery_finish завершает сохранённые integration/UI slices по той же revision3. Canonical ещё не объявлен READY и не собирается во время правок. QA: reports/ANDROID_OFFLINE_LOOKUP_QA_20261025.md. Эмулятор перезапущен без очистки, sys.boot_completed=1.

- Закрытие 2026-10-26: SOURCE COMPLETE / AUTOMATED + EMULATOR PASS. Android346/shared249, lint/build PASS; native19/19 PASS. Реальный Main с внешней синтетической MediaSession прошёл offline→online для той же песни, запись текста/LRC/аккордов подтверждена. Телефон и визуальная приёмка не заявлены. Пользователь отменил APK и попросил коммит + следующий пункт; новая сборка/публикация не выполняются. Следующая задача: duplicate-player-editor-actions, сначала dialectic-аудит.

## Аудит дублирующихся действий Android — 2026-10-26

- IN_PROGRESS, единственная активная задача: duplicate-player-editor-actions. Предыдущая задача поиска закрыта source-коммитом02b6dc9 и отправлена в GitHub; новая APK-поставка отменена пользователем.
- Ограниченный dialectic-аудит назначен duplicate_actions_thesis / duplicate_actions_antithesis: заметки, редакторы и контекстные меню; точные доказательства HEAD/worktree, поведение сохранения и Back. Изменения продукта до синтеза не выполняются.

- 2026-10-27: source cleanup prepared and independently reviewed; Android production and androidTest Kotlin compile PASS. Two existing UI tests updated and compiled, not executed. User requested no APK; device/visual/save-reopen validation remains open and no runtime PASS is claimed.

- 2026-10-27: user explicitly directed progression to library swipes despite outstanding on-device notes validation. Notes source committed/pushed fa9d36a; runtime/visual check remains pending, no APK requested.

## Свайпы медиатеки Android — 2026-10-27

- IN_PROGRESS: library-swipe-semantics. Audit actual right/left actions, persistence, feedback and undo; preserve user music files and data. No APK creation.

- User correction: remove row actions on swipes; use per-track ⋮ menu for favorite and actual library removal, swipes for navigation. Previous temporary-hide experiment superseded and not committed. Investigate deletion data contract and swipe routing before implementing.

- Menu/removal stage source implementation complete and independently reviewed, 6 targeted unit tests PASS, debug Kotlin compile PASS; no APK/device/UI/backup runtime claim. Next step same request: main-section navigation swipes; previous user explicitly requested no APK. Manual archive does not currently transfer exclusion preferences.
