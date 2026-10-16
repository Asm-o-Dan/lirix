# TASK-UI-LYRICS-SEARCH-LIFECYCLE-01: Жизненный цикл поиска текстов, сброс stale-состояния и индикация поиска

## 1. Контекст и проблема
При переключении треков в `NowPlayingScreen`:
1. `lyricsCache` сохранял кэш текста предыдущего трека во время фонового сетевого поиска (`AggregatedLyricsProvider`).
2. В интерфейсе продолжал отображаться текст и аккорды старой песни (ghost/stale lyrics), создавая рассинхронизацию.
3. Состояние загрузки `isLoadingLyrics` не отображалось в телесуфлёре/караоке, пользователь не видел, что идет поиск.
4. Если поиск ничего не находил или падал по таймауту/сетевой ошибке, исключение прерывало корутину без сброса `lyricsCache`, и старый текст оставался на экране навсегда.

## 2. Архитектурное решение (ADR-051)
1. **Расширение `PlayerEmptyStateType`:**
   - Добавить состояние `SEARCHING_LYRICS` в `EmptyPlayerStateView.kt`.
   - Оформить карточку с `CircularProgressIndicator` фирменного акцентного цвета, заголовком «Ищем текст песни...», названием трека/артиста и пояснением «Проверяем базу Lirix и онлайн-источники...».
2. **Мгновенная инвалидация кэша в `NowPlayingScreen.kt`:**
   - Привязать `lyricsCache` и `isLoadingLyrics` к ключу текущего трека:
     `remember(currentTrack?.trackKey) { mutableStateOf<LyricsCacheEntity?>(null) }`
     `remember(currentTrack?.trackKey) { mutableStateOf(currentTrack != null) }`
   - При смене трека кэш мгновенно очищается на том же кадре Compose, исключая показ текста прошлой песни.
3. **Безопасная обработка в `LaunchedEffect(currentTrack?.trackKey)`:**
   - Обернуть поиск в `try { ... } catch (e: Exception) { ... }`, гарантируя перевод в `isLoadingLyrics = false` и `lyricsCache = null` при любой сетевой ошибке или таймауте.
4. **Валидация принадлежности кэша в `RenderTeleprompterContent`:**
   - Передать `isLoadingLyrics: Boolean` в `RenderTeleprompterContent`.
   - Если `isLoadingLyrics == true` для режимов `KARAOKE`, `LYRICS`, `CHORDS` — отображать `EmptyPlayerStateView(PlayerEmptyStateType.SEARCHING_LYRICS)`.
   - Использовать кэш исключительно при `lyricsCache?.trackKey == currentTrack.trackKey`.
   - Если поиск завершен и текст не найден — отображать `EmptyPlayerStateView(PlayerEmptyStateType.LYRICS_NOT_FOUND)` с возможностью ручного поиска.

## 3. Критерии приемки (DoD)
- [x] При переключении трека старый текст мгновенно исчезает.
- [x] Во время поиска отображается экран «Ищем текст песни...» с индикатором и метаданными трека.
- [x] Если текст найден в базе или сети — плавно отображается текст новой песни.
- [x] Если текст не найден — отображается экран «Не нашли текст для этого трека» с кнопкой «Искать вручную».
- [x] Ни при каких условиях текст предыдущего трека не сохраняется на экране новой песни.
- [x] Сборка `:app:assembleDebug` успешна, проверена на физическом устройстве.
