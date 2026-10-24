# Locale repair v2 — контракт на согласование

Статус: proposed; product-правки начинаются после согласования root. Scope: устранение подтверждённых i18n-дефектов аудита 2026-10-22. Не меняет текст песен/переводов, язык романизации, пользовательские заметки, БД и версии релиза.

## Android runtime

Сохраняем ComponentActivity. AppCompat1.6.1 API не используется как источник resources: в текущем приложении нет activity delegates. Без новых зависимостей.

1. `LanguageManager` хранит AppLanguage SYSTEM/RUSSIAN/ENGLISH в существующем `lirix_prefs/pref_app_language`, предоставляет observable StateFlow, на старте читает существующее сохранение. Невалидное значение => SYSTEM. Изменение атомарно обновляет state и preference. Не меняет Locale.setDefault глобально.
2. `resolveAppLocale(language, systemLocales)` — чистая функция: explicit ru/en сохраняются при изменениях ОС; SYSTEM выбирает первый ru/en из системного порядка, иначе EN. Системные локали берутся из system resource configuration, а не из уже локализованного app context.
3. `LocalizedAppContent` на уровне MainActivity.setContent читает selection и LocalConfiguration (для реагирования на изменения ОС). Копирует Configuration, setLocales(LocaleList выбранного effective locale), сохраняет fontScale/density/прочие поля. Создаёт Android `ContextThemeWrapper`, производный от activity, с applyOverrideConfiguration **до первого доступа к Resources**. Предоставляет LocalContext + LocalConfiguration и LTR layout direction (оба поддерживаемых языка LTR) всем UI и диалогам. Не применяет key(locale) к целому дереву и не сбрасывает экран/черновики/воспроизведение. Новые ресурсы должны немедленно доходить до stringResource/context.getString.
4. `LanguageSelectionDialog` наблюдает менеджер, а не remember единоразового getter; закрывается после выбора. Проверяем RU→EN→SYSTEM и сохранение выбранного режима отдельно от effective locale.
5. Root integration обязательно заменяет `NowPlayingScreen.kt` direct Activity cast для keepScreenOn на `context.findHostActivity()` с обходом ContextWrapper; public helper в `app/src/main/java/com/lirix/app/ui/i18n/LocalizedAppContent.kt`, package `com.lirix.app.ui.i18n`, signature `fun Context.findHostActivity(): Activity?`. Перед provider включением audit всех Activity cast, window access, launcher/ActivityResult и context owner usages, не только найденного426. ActivityResultRegistry/ViewModel owners не подменяются. Activity остаётся живой при Compose-only смене.
6. `locales_config` существующий не удаляется. API33+: framework `LocaleManager.applicationLocales` — authority после однократной миграции старого `pref_app_language`; новый `pref_framework_locale_migrated` отмечает миграцию. При первом запуске: непустой framework имеет приоритет; иначе explicit старый pref переносится в framework один раз, SYSTEM не пишет explicit; затем marker сохраняется. При последующих startup/resume/configuration changes framework ru/en/empty читается в observable selection и prefs, а не перезаписывается старым pref. Empty всегда SYSTEM. In-app выбор обновляет framework только при реально отличающихся tags, затем синхронизирует state/prefs. Unsupported external app locale normalizes SYSTEM; системный OS language выбирается из `LocaleManager.systemLocales`, не app override. API26..32: authority prefs, системные локали из Resources.getSystem().configuration.locales; app language никогда не глобальный Locale.setDefault. Проверки startup/resume — в lifecycle host, не фоновой pollingloop. Android13+ framework может пересоздавать Activity; это допустимый platform lifecycle, но не требуемый для Compose UI на26..32. Все изменения UI должны переживать стандартную рекреацию. Не обещаем отсутствие platform Activity recreation; процесс вручную не перезапускается.

## Shared / desktop

- `LirixStrings` использует observable Compose state для effective language, отдельный persisted preference SYSTEM/RU/EN и независимое resolve(language, systemLocale). Существующий AppLanguage.EN/RU сохраняется для совместимости, SYSTEM добавляется при необходимости. По умолчанию SYSTEM, неизвестное => SYSTEM. `StringsContract`/EN/RU keysets полностью равны; строки имеют именованные/форматные параметры, без конкатенации русских хвостов.
- Desktop использует существующую java.util.prefs.Preferences (новый изолированный ключ языка), сохраняет selection, при старте вычисляет effective locale. Root composition подписана на state; меню языка доступно из sidebar/settings независимо от наличия трека. Три пункта: System / Русский / English. Отдельный `aboutTrack` вместо libraryTitle у ABOUT.
- Вынос русского UI охватывает shipped desktop workspace, deck, dialogs, transport, teleprompter, HUD/compact, library, settings, poster и shared dialogs. Технические имена источников/форматы/бренды не переводим. Русские тексты данных/песен не считаются UI. Существующее поведение экранов и функций сохраняется.
- Android добирает literal UI в студии строк и сводке/постерах; проводится поиск оставшихся видимых literals в shipped screen paths. XML EN/RU keysets совпадают; placeholder types/count совпадают. Существующие AG267ключей сохраняются, дубли ключей недопустимы.

## Граница MP4 owner / romanization owner

- Catalog XML и shared `LirixStrings` — единственный writer locale owner. Остальные owners передают список необходимых ключей.
- MP4 owner вводит immutable `CounterRollLabels(header, minutes, tracks, artists, topTracks, plays)` параметр конструктора с EN default; не читает Context внутри background animator. Locale owner владеет `WrappedScreen.kt` и предоставляет локализованный snapshot в `WrappedScreen` при открытии share, один и тот же для preview/save/share. Это согласованный freeze API.
- `CounterRollAnimator` geometry, typewriter/encoder — только MP4 owner. Locale owner не редактирует этот файл параллельно.
- NowPlaying/menu/RomanizedKaraokeLine — romanization owner. Locale owner даёт helper/API и точную integration вставку для keepScreenOn и ключи. MainActivity/root locale bridge — locale owner.
- DesktopDeckScreen есть пересечение romanization: locale owner только извлечение видимых labels; romanization owner сперва сообщает точный набор строк и не переписывает файл целиком.

## Runtime/test cases (должны быть RED до реализации)

Android Robolectric SDK26,32,33,35:
- coldStart reads ru/en/System before UI, old pref reused; invalid fallsback.
- chooseEnglish changes both stringResource/tab+context.getString to English; chooseRussian reverses, selection persists, DB/lyrics not rewritten.
- SYSTEM follows RU→EN system config, explicit RU remains RU on ENsystem; unsupported system => EN. Android13 external locale preference clear/ru/en sync checked separately.
- remembered current tab/input/selected lyric survive Compose locale switch; root context unwrap yields same Activity and window works.
- preference reset/new instance coldstart proves persistence rather than singleton carryover.
- snapshot localized Configuration preserves fontScale, orientation and density.
- Language picker semantics selection after reopened dialog matches persisted mode, not just effective locale.

Desktop unit/Compose:
- persisted language state recreates from fresh repository, invalid=>SYSTEM; system EN/RU fallback; state invalidates labels.
- reachable picker with no media; ABOUT title correct; keyset/format parity for shared strings.
- view state/drafts remain after switch; current rows/selected view stable.

Static contracts:
- EN/RU XML names and placeholders equal, no duplicates; shared catalog equality.
- Studio and MP4 labels are localized; EN animation snapshot has no hardcoded Russian captions.

Root runs combined Gradle once owners complete. Owner does not run Gradle, install, stage, commit. Tests are added before implementation; actual RED run is root-controlled (not claimed until root executes).

## Baseline and clean-verification inputs

`build/repairs-baseline/i18n/` contains copies before repair of locale files/resources, activity/scaffold, studio, desktop UI/main, CounterRoll and relevant tests; git-status.txt records dirty/untracked inputs. AG locale files/resources and animation package are initially untracked; app/build.gradle.kts already dirty with AppCompat1.6.1; AndroidManifest already dirty with locales_config. These are pre-existing AG inputs, not new dependencies introduced by repair. Root must include these required baseline AG files when creating a clean verification worktree; not commit unrelated dirty files by folder.

Official reference checked: AppCompat source and cached1.6.1 bytecode show delegate requirements. Android framework locale APIs on33+ must be gated by API. No network-dependent locale runtime is required.
