# Android: восстановление поиска текста и аккордов — intake 2026-10-25

Статус: APPROVED после независимого DoR review revision3, уровень 2 (ОС/сеть, асинхронный lifecycle и запись Room). Разрешена последовательная реализация утверждённого контракта. Единственная текущая задача: backlog/offline-lookup-recovery.md; остальные пункты очереди не затронуты.

## Результат и границы

Открытый без сети трек получает текст/аккорды после появления пригодной сети без переключения песни. Для неуспеха доступен компактный явный повтор. Сетевая ошибка не изображается как подтверждённое отсутствие песни. Сохранённые тексты, заметки и черновики студии остаются целыми.

Входит: Android foreground lyrics/chords, scoped recovery coordinator, проверенный network monitor, дедупликация того же запроса с Android prefetch/overlay, безопасный merge отсутствующих полей. Не входит: переводы, Desktop UX, новые источники, новый экран, миграция БД, чистка кешей, новая аналитика, следующие backlog.

## Фактические причины и доказательства (main working tree)

- `app/src/main/java/com/lirix/app/ui/NowPlayingScreen.kt:490-538`: загрузка находится в LaunchedEffect только по trackKey; нет ключа сети или retry. Любой неуспех/Exception заканчивается lyricsCache=null, isLoadingLyrics=false. Подключение сети при прежнем trackKey не перезапускает эффект. Это подтверждённая причина foreground-застревания.
- `.../NowPlayingScreen.kt:343-345,505-511`: два Boolean и nullable cache не различают network failure/not-found; кеш с обычным текстом прерывает новую загрузку, даже если аккордов нет. `:1426-1444`: CHORDS читает кеш/inline-аккорды и показывает тот же lyrics-not-found fallback, отдельного повторного запроса нет.
- `app/src/main/java/com/lirix/app/feature/LyricsProvider.kt:130-175`: HttpTextClient сводит non-2xx и исключения к null; `:208-281` LrcLib после null/парсинга возвращает hasLyrics=false. `:32-42` LyricsResult не имеет статуса ошибки/полноты. Нельзя выводить причину null задним числом.
- `.../LyricsProvider.kt:946-1053`: текущий aggregator содержит дополнительные KuGou/Akkords/OVH и атрибуцию, cache/notes shortcuts и nullable source outcomes. Сохранить эти чужие изменения. Null от legacy provider не является доказательством каталожного отсутствия.
- `app/src/main/java/com/lirix/app/feature/lyrics/NetworkConditionManager.kt:23-50`: только синхронный snapshot транспорта, нет callback, INTERNET/VALIDATED не проверяются. Manifest уже содержит ACCESS_NETWORK_STATE (`app/src/main/AndroidManifest.xml:13`). Поиск registerDefaultNetworkCallback/registerNetworkCallback в Android production не нашёл существующего наблюдателя.
- `app/src/main/java/com/lirix/app/feature/MusicFeatureEngine.kt:226-279`: prefetch пропускает offline и возвращается без восстановления по connectivity; нет single-flight реестра. `app/src/main/java/com/lirix/app/ingestion/NotificationListener.kt:158` и `MediaSessionCollector.kt:90` запускают prefetch отдельно; `service/FloatingLyricsService.kt:271-299` самостоятельно запускает aggregator. Новый foreground retry без общей дедупликации удвоил бы запросы.
- `app/src/main/java/com/lirix/app/ui/components/EmptyPlayerStateView.kt:187-258`: есть ручной Teach search, но нет retry того же автоматического lookup.
- `shared/src/commonMain/kotlin/com/lirix/app/storage/LyricsDao.kt:40-45`: main transaction saveAutomaticIfNotUser защищает manual provider, но заменяет всю automatic cache entity; требуется сохранить уже заполненные поля/notes при chord-only recovery. `observeLyrics` существует (`:29`).
- `shared/src/jvmShared/kotlin/com/lirix/app/feature/lyrics/LyricsSourceHttp.kt:52-82,98-125`: transport timeout, cooldown403/429 и прочие failures тоже возвращают null; отмена корректно пробрасывается. `AkkordsProProvider.kt:93-124` имеет 60-секундный unavailable и 15-минутный miss. Offline null может закрыть источник на минуту; нельзя очищать все cooldown/miss кеши ради retry.
- `NowPlayingScreen.kt:532` и `MusicFeatureEngine.kt:277` перехватывают Exception вместе с CancellationException. Старый эффект может после отмены записать состояние; одной отмены недостаточно — нужен identity/generation guard плюс rethrow cancellation.

## Сопоставление исходников и сохранность

Сравнены чтением clean commit3b25fb3, canonical `.gradle/repairs-integration-ecce372` и main. Canonical MusicFeatureEngine/LyricsProvider/NetworkConditionManager/EmptyPlayerStateView совпадают с commit по LF; NowPlaying отличается служебными trailing whitespace (root подтвердил нормализованное совпадение). Main Engine/Provider/NowPlaying существенно отличаются: monotonic session recorder, PlaybackUiClock и source attribution/additional backends, guard записи manual lyrics. Это самостоятельная чужая работа, её запрещено заменить canonical файлом. Root сохранил baseline в `.gradle/offline-recovery-preservation/main/` и `source-baseline.json`; не перезаписывать.

Перед кодом ревью должно согласовать перенос bounded retry hunks в main и эквивалентный canonical вариант без включения/удаления unrelated source/clock/session изменений. Общую nullable shared-source архитектуру не рефакторить ради одного Android retry: unknown исходы остаются unknown.

## Гейты

1. Независимое ревью спеки `specs/android-ui/offline_lookup_recovery_v1.md`, API и сохранности данных.
2. Реализация по зависимостям только этой задачи; тесты классификации/конкуренции/Room до подключения UI.
3. Unit + native + реальная Android network toggle проверка, RU/EN, отсутствие дублей/старых результатов.
4. Независимый diff review, сборка, приватная тестовая доставка с ограничениями. Не заявлять телефонную проверку по эмулятору.

Неизвестность: нет runtime-сетевого trace пользовательского эпизода, поэтому конкретный упавший источник/HTTP-код не установлен. Доказана невозобновляемость effect и потеря информации об исходе, а не причина каждой пустой выдачи.

## Уточнение мобильной политики и текущая OS база

Историческая `.sdd/specs/lyrics-engine/overview.md` не доказывает runtime-гейт: NetworkConditionManager возвращает ON_DEMAND_ONLY на cellular, но prefetch фактически запрещает только OFFLINE_ONLY, foreground вообще не проверяет policy. Новая спека сохраняет наблюдаемое разрешение загрузки открытого трека через validated cellular и не включает исправление общей политики prefetch в эту задачу.

Root зафиксировал эмулятор API35 с validated Wi-Fi и cellular fallback в `.gradle/karaoke-private-verification/offline-recovery-connectivity-baseline.txt`. Для реального offline QA недостаточно выключить только Wi-Fi. Сеть этим анализом не менялась.

## Revision2 после независимого review

См. `.sdd/reports/ANDROID_OFFLINE_LOOKUP_SPEC_REVIEW_20261025.md`. Спека переработана до минимального bounded scope и снова READY_FOR_REVIEW (код не начат).

- R1: явный target-success исключает USER_NOTE/isUserNote/NONE; automatic missing-target путь обходит notes shortcut, сохраняя исходные notes и legacy API.
- R2: source rejection, manual/fresh-field authority проверяются в одной Room transaction с cache и music_tracks writes; provenance меняется только для заполненных полей.
- Общий gate ключуется trackKey+rejections, не target; цели сериализуются через explicit join/recheck/follow-up rule. Финитный app-owned job с90s deadline/existing HTTP timeouts и finally cleanup; без ref-count cancellation.
- Только Loading/Available/WaitingForNetwork/Incomplete: неизвестный null не означает not-found. Отложены full provider taxonomy, transport refactor и cooldown scheduler. Существующие source cooldowns не сбрасываются.
- Сохранены validated cellular для открытого трека, pending recovery во время Loading, missing-chord acquisition, Room observation и race/data invariants.
- В спеки приведён bounded allowlist файлов/functions и независимый тест-план. Исполнитель один, slices последовательные; отдельные атомарные карточки пока не нужны.

## Revision3: узкие DoR уточнения

READY_FOR_REVIEW revision3. Независимый re-review одобрил остальной уменьшенный scope.

1. Awaited covering attempt расходует coverage всех waiters при null/miss/error/timeout. Parent handle содержит shared follow-up reference; максимум один opposite follow-up, без рекурсии и без persistent miss cache. Тест трёх all-miss waiters:1 lyric chain + максимум1 chord follow-up.
2. AutomaticLookupCandidate несёт text/sourceId/url/author отдельно для plain/synced/chords. AmDm/MyChords chords авторизуются именно своим sourceId, не aggregate LRCLIB. Тест concurrent AmDm rejection сохраняет разрешённые слова и отбрасывает только запрещённые аккорды/их provenance.
3. Исправление факта: sourceAttributionJson уже есть в canonical и HEAD3b25fb3 MusicEntities.kt:92. Сохранять существующий JSON в обоих checkout; новая схема не нужна. Различие касается helper-кода, не наличия колонки.
4.90s — logical coroutine deadline; HTTP unwind может занять existing socket timeout. ensureActive перед следующим источником и commit; hard physical90s не обещается.

В спеки заданы точные сигнатуры DTO, automatic acquisition/chord helper, transactional merge и parent/follow-up handles. Код/Gradle/ADB/Git не затронуты. Нужен финальный узкий re-review перед DoR.
