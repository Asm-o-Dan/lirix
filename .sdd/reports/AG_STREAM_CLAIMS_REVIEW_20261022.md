# Проверка заявленных стримов Antigravity — 2026-10-22

Read-only review production-кода и тестов MP4/i18n. Романизация проверяется отдельно. Gradle, устройство, установки и экспорт на реальном кодеке в этой проверке не запускались. «Все тесты зелёные» не подтверждено свежим запуском.

## Найденные несоответствия

### P2 — встроенный Android-селектор сохраняет галочку, но не применяет язык к Activity

- `app/src/main/java/com/lirix/app/ui/i18n/LanguageManager.kt:37` вызывает только `AppCompatDelegate.setApplicationLocales`; следующая операция — запись `pref_app_language`.
- `app/src/main/java/com/lirix/app/MainActivity.kt:30` наследует `ComponentActivity`; собственный AppCompatDelegate не создаётся. В `LirixApp`, `MainActivity`, `AppScaffold` и остальных production-источниках нет альтернативного LocaleManager setter, locale Context/configuration wrapper или Compose-провайдера языка. `pref_app_language` читается только `getCurrentLanguage`, для выбора радиокнопки.
- Проверена именно используемая зависимость `androidx.appcompat:appcompat:1.6.1` (`app/build.gradle.kts:102`): javap локального AAR показывает, что на API33+ `getLocaleManagerForApplication()` ищет Context исключительно в `sActivityDelegates`; при отсутствии delegates setter ничего не делает. На <=32 setter применяет настройки только active delegates. Это проверка байткода зависимости, не предположение по имени superclass.
- Сценарий: системный RU → ⋮ → English. Диалог закрывается и при повторном открытии показывает English, но ресурсы вкладок/меню остаются RU. После холодного запуска собственная сохранённая настройка также не передаётся в resources. Это вывод из полного статического пути; на телефоне не воспроизведён.
- `LanguageManagerTest.kt:9,16,23,30,38` — только пять enum/fromCode/property-тестов. Тестов реального RU→EN, SYSTEM, process restart нет. Нарушены DoD3/4 `in_app_language_picker_spec.md`.

### P2 — «полный паритет Android и ПК» отсутствует: desktop остаётся RU без выбора языка

- `shared/src/commonMain/kotlin/com/lirix/app/ui/i18n/LirixStrings.kt:111` задаёт RU; `:113` — обычный var, без observable state, system locale или persistence.
- Production-поиск `LirixStrings` обнаруживает использование только в `DesktopDeckScreen.kt`; присваиваний `currentLanguage` вне самого setter нет. Desktop main не содержит выбора/инициализации локали.
- `desktop/src/main/kotlin/com/lirix/app/desktop/ui/DesktopDeckScreen.kt:282,343,456` по-прежнему содержит русские сообщения про тайминги, поиск аккордов и источник аудио. Каталог shared имеет 28 свойств, а не паритет Android-каталога.
- Сценарий: запуск ПК с английской ОС. Интерфейс остаётся русским; штатного действия для EN нет. Даже будущая запись в plain var сама по себе не инвалидирует Compose.

### P2 — MP4 молча пропускает кадры при backpressure и всё равно возвращает успех

- `app/src/main/java/com/lirix/app/feature/share/animation/Mp4FrameEncoder.kt:195` делает один `dequeueInputBuffer(10000µs)`. Если результат отрицателен, `encodeViaByteBuffer` просто заканчивается; кадр не повторяется и ошибка не поднимается. `:175` всё равно увеличивает frameIndex.
- Конец также не гарантирован: `:256` один раз ждёт входной EOS-буфер; `:216` через десять TRY_AGAIN прекращает drain, даже без EOS. `:263` подавляет ошибку finish, а muxer.stop подавляется отдельно. `AnimatedPosterPipeline.kt:84` возвращает success без проверки muxed frames/EOS/валидности файла.
- Сценарий: кодек занят во время offline1080p-экспорта и не отдаёт inputbuffer за10мс. Кадр теряется без уведомления, timestamps содержат пропуск; при проблеме финализации может быть предложен повреждённый/неполный MP4 как успешный. Факт веток подтверждён кодом; частота на конкретном телефоне не измерена.

### P2 — длинные строки MP4 обрезаются, а последний текст typewriter может не допечататься

- `VinylSpinAnimator.kt:265,275,285` рисует полный title/artist/lyric через один `Canvas.drawText` фиксированным размером; нет измерения, переноса, ellipsize или fit. Длинный заголовок или строка на1080px выходит за края и необратимо обрезается экспортом.
- Отдельный детерминированный конец: `AnimatedPosterPipeline.kt:70` передаёт последний t=95/96 при стандартных96кадрах. `VinylSpinAnimator.kt:134-140` заканчивает печать lyric только приt>=1, а до этого округляет `localT*length`. Для строки длиной100UTF16единиц последний кадр содержит97, а не100. Последние3символа никогда не появляются в видео. Предпросмотр также циклический; готовая строка не удерживается.
- Это не предположение о кодеке: арифметика рендеринга. Тестов длинной строки в полном timeline-export нет.

### P2 — локализация Android неполная даже после исправления runtime switch

- `app/src/main/java/com/lirix/app/ui/components/KaraokeStudioDialog.kt:259,265,283,305` — русские «Закрыть», «Точная студия», счётчик строк, «Выбрать аудио для волны» без stringResource; ошибки и черновики тоже русские. EN-пользователь попадёт в русскую студию.
- `CounterRollAnimator.kt:181,216-218,278,302` — «Итоги», «минут», «треков», «артистов», «Топ треков», «воспр.» жёстко в Canvas. English Wrapped MP4 будет смешивать локализованный periodLabel с русскими подписями.
- Наличие 267 `<string>` в каждом Android XML само по себе не доказывает вынос всех видимых строк; требование1 `i18n_localization_v1.md` не выполнено полностью.

### P3 — вкладка About на ПК ошибочно названа «Медиатека»

- `DesktopDeckScreen.kt:224` сопоставляет `DesktopLyricsMode.ABOUT` с `LirixStrings.current.libraryTitle` (RU «Медиатека» / EN «Music Library»), хотя открывается информация об аудиосессии.
- Сценарий: открыть раздел трека на ПК; название обещает библиотеку вместо содержимого вкладки. В shared-каталоге нужен отдельный ключ About, а не reuse libraryTitle.

## Что реально присутствует

- Android XML-каталоги EN/RU существуют; Android13 locales_config включён в manifest. Встроенный диалог System/RU/EN доступен из ⋮ плеера.
- MP4 экспорт доступен из ShareFormatDialog и вызывается из NowPlaying/ Wrapped. AnimationSpec.STORIES —1080×1920, fps24, duration4000ms. Винил, счётчики и typewriter реализованы. Default encoder — ByteBuffer/NV12 (`preferSurface=false`), поэтому проблема отсутствующего explicit timestamp в surface-ветке не заявляется как дефект штатного пути.
- `Mp4FrameEncoderTest` и `AnimatedPosterPipelineTest` находятся в `src/test`, Robolectric SDK33; valid-file проверки ограничены exists/length>0 при32/64px. `ShareAnimatedPosterTest` использует произвольные байты, проверяя MIME/Intent/copy, а не видео. В `src/androidTest` инструментального MP4/locales теста не найдено. Эти тесты не доказывают1080p24fps/duration/framecount/цвета/декодируемость настоящего MP4.

## Ограничения проверки

Ни Android-переключение, ни MP4 экспорт, ни desktop запуск на устройстве в этом review не выполнялись. Build/test зелёность и опубликованный APK не проверены. Не утверждается, что все дефекты уже воспроизведены runtime. Изменений product/tests, зависимостей, индекса и коммитов не было.
