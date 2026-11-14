# Ручное избранное, сообщения мессенджеров, достоверность автопоиска — 2026-11-01

Спека: `.sdd/specs/media-core/favorites_messenger_lookup_v1.md` (SPEC-MEDIA-FAV-01, APPROVED v1).
План: этап 5 (уровень 2). Источник: `.sdd/reports/UI_UX_REVIEW_20261101.md`, «Медиатека» и P2.

## Статус

IMPLEMENTED / AUTOMATED PASS по этапу. В полном прогоне упал один тест — `TeachModeMultiSelectTest.test_custom_rule_with_multi_selector_execution`. Он вне этапа: проверяет `CustomRuleLyricsProvider.kt`, который сейчас правит сессия TeachMode (файл изменён в рабочей копии). Проверка на устройстве: NOT RUN (этап 6).

## Решения пользователя (2026-11-01)

| № | Вопрос | Решение |
|---|---|---|
| D2 | Выводить ли ручной выбор из существующих данных при миграции | Нет, начать с нуля: у всех треков отметка `NULL` |
| D4 | Готовые правила для голосовых/видеосообщений | 4 правила, включены |
| D4 | Повторное включение готового правила | Диалог с необязательной очисткой истории |
| D5 | Где блокировать автопоиск «Unknown Artist + цифры» | Android и Desktop |

## Что сделано

**Ручное избранное важнее автоматики (AGENTS 7.1)**
- Room 15: `music_tracks.favoriteUserSetAt INTEGER DEFAULT NULL` (`TrackEntity.favoriteUserSetAt`). `NULL` — пользователь не выбирал. Время — последний ручной выбор. `isFavorite` остаётся итоговым значением, запросы и UI не менялись.
- `MusicDao.setFavorite`, `toggleFavorite`, `invertFavorite` записывают отметку. Все экраны Android (`HistoryScreen`, `LibraryScreen`, `NowPlayingScreen`, `TrackActionBottomSheet`) и Desktop (`DesktopLibraryRepository.favorite`) уже вызывают эти методы, поэтому файлы экранов не менялись.
- Автоправило не трогает трек с отметкой: `addListeningCredit` (15 минут), `MonotonicSessionRecorder.record` (5 прослушиваний), `MusicFeatureEngine.recordPlayback`.
- Миграции 14→15: Android `AppDatabase.MIGRATION_14_15`, Desktop `MIGRATION_14_15`. Общий SQL в `storage/Schema15.kt`.
- Архив: поле не пишется, когда оно `null` (`@EncodeDefault(NEVER)`), поэтому контрольная сумма архивов v1/v2 сходится. Версия архива осталась 2. При слиянии побеждает более поздний ручной выбор; без ручного выбора — объединение, как раньше.

**Сообщения мессенджеров**
- На телефоне Telegram и AyuGram пишут голосовые и видеосообщения с исполнителем «Голосовое сообщение» / «Видеосообщение» и именем отправителя в названии. Таких записей 21, из них 12 в избранном. Музыка из AyuGram (13 треков) имеет обычных исполнителей, поэтому правило по пакету не подходит.
- 4 правила `ARTIST / EXACT`: `builtin:messenger-voice-ru|video-ru|voice-en|video-en`. Добавляются миграцией 14→15 и при создании новой базы (Android `onCreate`, Desktop `SEED_BUILTIN_RULES_CALLBACK`). Действуют на новые прослушивания. Миграция историю не удаляет. Удалённое пользователем правило не возвращается.
- `BlacklistViewModel.toggleRule`: включение правила `builtin:*` сразу фильтрует новые прослушивания. Если есть совпадающие записи, показывается диалог «Удалить совпадающие записи из истории: N?» с кнопками «Удалить» / «Оставить». После удаления есть «Отменить»: треки возвращаются, правило остаётся включённым. Для своих правил поведение прежнее.

**Автопоиск (AGENTS 7.4)**
- `AutomaticLookupEligibility` (commonMain): при неизвестном исполнителе (пустой или из `WrappedStatsEngine.UNKNOWN_ARTIST_NAMES`) и названии только из цифр, пробелов и `. _ - # ( )` автоматический поиск не запускается.
- Android: проверка в `AutomaticLookupCoordinator.runOperation` до сетевого запроса. Desktop: в `DesktopLyricsService.onTrackChanged` после проверки сохранённого текста. Ручной поиск и привязка не затронуты, сохранённые тексты не удаляются.

## Файлы

Код: `shared/.../storage/{MusicEntities,MusicDao,AppDatabase,LibraryArchive,Schema15}.kt`, `shared/src/{androidMain,desktopMain}/.../storage/DatabaseFactory.kt`, `shared/.../feature/lyrics/AutomaticLookupEligibility.kt`, `app/.../feature/{MonotonicSessionRecorder,MusicFeatureEngine}.kt`, `app/.../feature/lyrics/AutomaticLookupCoordinator.kt`, `app/.../ui/blacklist/{BlacklistViewModel,BlacklistSettingsScreen}.kt`, `app/src/main/res/values{,-ru}/strings.xml` (4 строки `blacklist_builtin_purge_*`), `desktop/.../lyrics/DesktopLyricsService.kt`.

Новые тесты: `app/src/test/.../FavoriteUserChoiceTest.kt`, `desktop/src/test/.../FavoriteUserChoiceDesktopTest.kt`, `shared/src/commonTest/.../AutomaticLookupEligibilityTest.kt`.

Изменённые тесты (контракт схемы изменился):
- `FavoritePersistenceTest` — ожидаемая запись теперь содержит отметку ручного выбора.
- `Schema14CompatibilityTest` — путь 12→14→15, версия 15. Ожидаемый identity hash v15 `17214f99238de8fab93f952143fea622` взят из сгенерированного Room-кода; хеш v14 оставлен в комментарии.
- `SongTranslationMigrationTest`, Desktop `SongTranslationTest`, `SourceAttributionPersistenceTest` — добавлена миграция 14→15 к цепочке.

## Проверка

| Проверка | Статус |
|---|---|
| `./gradlew --continue :shared:allTests :desktop:test` | PASS — shared 799/0, desktop 230 прошли, 20 пропущено (визуальные/внешние, не считаются пройденными), 0 ошибок |
| `./gradlew --continue :app:testDebugUnitTest` | FAIL 1 из 407 — `TeachModeMultiSelectTest` (вне этапа, см. статус). Все тесты этапа и изменённые тесты PASS: `FavoriteUserChoiceTest` 7/7, `FavoritePersistenceTest`, `Schema14CompatibilityTest`, `SongTranslationMigrationTest`, `AutomaticLookupCoordinatorTest` 11/11, `BlacklistIngressGuardTest`, `MonotonicListeningTimeTest` 11/11, `ShareCardGeneratorTest` 7/7 |
| Первый общий запуск `:app:testDebugUnitTest` | BLOCKED — не компилировались чужие незавершённые файлы (`TeachModeLogic.kt`, `KaraokeStudioDialog.kt`); при повторе уже компилировались |
| Миграция копии реальной базы с телефона (v14 → 15, Desktop Room + BundledSQLite, временный тест удалён) | PASS — 1142 трека, 199 избранных, 2364 сессии, 602 текста сохранены; отметок ручного выбора 0; существующее правило пользователя осталось; добавлены 4 правила `builtin:*` |
| Ручной UI-тест диалога и проверка на телефоне | NOT RUN |

Что проверяют новые тесты:
- реальная миграция 14→15 на Android (Robolectric) и Desktop (bundled SQLite): строки и `isFavorite` сохранены, отметка `NULL`, 4 правила добавлены, своё правило не тронуто, удалённое готовое правило не возвращается;
- свежая production-база Android получает 4 правила;
- снятое «сердце» не возвращается после 2 прослушиваний и 16 минут подтверждённого прослушивания (в сумме 7 прослушиваний); трек без ручного выбора по-прежнему попадает в избранное автоматически;
- `addListeningCredit` на Desktop: 15 минут продвигают трек без выбора и не продвигают трек с выбором;
- архив: в архиве без выбора нет поля, v1 расшифровывается с проверкой суммы, 6 случаев слияния;
- голосовые и видеосообщения Telegram/AyuGram не записываются, музыка из AyuGram записывается;
- включение готового правила: без согласия ничего не удаляется, после согласия удаляются только совпадения, «Отменить» возвращает трек и не трогает правило;
- автопоиск: «09 / Unknown Artist» не ищется (координатор Android и сервис Desktop), «69 / Anacondaz» ищется; 7 вариантов цифровых названий × 6 вариантов неизвестного исполнителя.

## Остаётся

- 62 трека на телефоне, с которых вы раньше сняли «сердце», один раз вернутся в избранное при следующем прослушивании (решение D2). После повторного снятия выбор сохранится.
- Уже накопленные 21 запись сообщений остаются в истории. Чтобы удалить: «Чёрный список» → выключить и снова включить готовое правило → «Удалить».
- «Отменить» после очистки возвращает треки, но не их сессии прослушивания. Так работала отмена и раньше (`BlacklistPurgeEngine.purgeMatchingTracksWithBackup` сохраняет только треки).
- Своё правило при повторном включении по-прежнему удаляет треки без подтверждения. Диалог сделан только для готовых правил, как было решено.
- YouTube-ролики в медиатеке не входят в этап.
- Проверка на устройстве не выполнялась (этап 6).

## SDD

- Спека: `.sdd/specs/media-core/favorites_messenger_lookup_v1.md` — APPROVED v1.
- `.sdd/board.md` и `.sdd/decisions.md` не менялись: их сейчас правят другие сессии. Запись ADR для координатора: «Room 15 хранит ручной выбор избранного (`favoriteUserSetAt`); автоматика не перезаписывает его; готовые правила `builtin:*` не удаляют историю без подтверждения».
