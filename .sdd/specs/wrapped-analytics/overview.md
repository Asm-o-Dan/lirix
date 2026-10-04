## Модуль: WrappedAnalytics   Зона: wrapped-analytics   Версия спеки: v1   Статус: APPROVED

### Назначение
Модуль выполняет локальный 100% приватный расчёт годовой и месячной аналитики прослушиваний (Music Wrapped), управляет правилами разблокировки 7 геймификационных достижений и рендерит графическую Story-карточку в растровый `Bitmap` для экспорта. Модуль НЕ отправляет телеметрию и НЕ взаимодействует с облаком.

---

### Типы данных
- `WrappedStats` — поля: `timeRange: WrappedTimeRange`, `totalListeningTimeMs: Long`, `totalTracksPlayed: Int`, `uniqueArtistsCount: Int`, `topArtists: List<ArtistStat>`, `topTracks: List<TrackStat>`, `peakHourOfDay: Int`, `archetype: ListeningArchetype`, `calculatedAtMs: Long`.
- `WrappedTimeRange` — перечисление: `LAST_30_DAYS`, `CURRENT_YEAR`, `ALL_TIME`.
- `ListeningArchetype` — перечисление: `NIGHT_OWL`, `LOYAL_REPEATER`, `GENRE_NOMAD`, `ACOUSTIC_BARD`, `DAY_WORKER`.
- `AchievementEvaluationResult` — поля: `unlockedAchievements: List<AchievementEntity>`, `updatedAchievements: List<AchievementEntity>`.

---

### Публичный API

#### 1. Функция `calculateWrapped(timeRange: WrappedTimeRange) -> WrappedStats`
- **Сигнатура:** `suspend fun calculateWrapped(timeRange: WrappedTimeRange): WrappedStats`
- **Предусловия:** База данных `media-core` инициализирована.
- **Постусловия:** Возвращает агрегированный объект `WrappedStats` на основе реальных сессий прослушивания за запрошенный интервал.
- **Поведение:**
  1. Вычисляет граничный таймштамп `minTimestamp`:
     - `LAST_30_DAYS` $\to now - 30 \times 86400 \times 1000L$.
     - `CURRENT_YEAR` $\to$ начало 1 января текущего года в локальной таймзоне.
     - `ALL_TIME` $\to 0L$.
  2. Запрашивает из `ListeningSessionDao` все сессии с `startTimeMs >= minTimestamp`.
  3. Агрегирует суммарное время: `totalListeningTimeMs = SUM(durationMs)`.
  4. Группирует треки по `artist` и суммирует длительность, вычисляет топ-5 исполнителей и их доли в процентах.
  5. Группирует треки по `trackKey`, находит топ-5 треков по частоте воспроизведения.
  6. Находит пиковый час суток (`peakHourOfDay = 0..23`) через группировку `startTimeMs` по часам.
  7. Определяет музыкальный архетип `ListeningArchetype`:
     - Если суммарная длительность с 01:00 до 05:00 $> 0.4 \times totalListeningTimeMs \to$ `NIGHT_OWL`.
     - Иначе если топ-1 артист занимает $> 45\%$ от общего времени $\to$ `LOYAL_REPEATER`.
     - Иначе если число прослушиваний треков с аккордами $\ge 5 \to$ `ACOUSTIC_BARD`.
     - Иначе если `uniqueArtistsCount > 30` при среднем числе повторов $< 2.0 \to$ `GENRE_NOMAD`.
     - Иначе $\to$ `DAY_WORKER`.
  8. Возвращает собранный объект `WrappedStats`.
- **Ошибки:** при пустой истории возвращает объект со всеми нулевыми счетчиками и дефолтным архетипом `DAY_WORKER`.
- **Побочные эффекты:** нет (чистое чтение).
- **Граничные случаи:** 0 прослушанных треков $\to$ `totalListeningTimeMs = 0`, пустые списки топов, без падения при делении на ноль.
- **Примеры:**
  1. Вход: `CURRENT_YEAR`, 50 часов музыки, ночью $\to$ Выход: `archetype=NIGHT_OWL, totalListeningTimeMs=180000000`.
  2. Вход: `LAST_30_DAYS`, 80% времени только группа «Кино» $\to$ Выход: `archetype=LOYAL_REPEATER, topArtists[0].artistName="Кино"`.
  3. Вход: новая установка, 0 треков $\to$ Выход: `totalListeningTimeMs=0, topArtists=emptyList(), archetype=DAY_WORKER`.

#### 2. Функция `evaluateAchievements() -> AchievementEvaluationResult`
- **Сигнатура:** `suspend fun evaluateAchievements(): AchievementEvaluationResult`
- **Предусловия:** Таблица `achievements` заполнена дефолтными 7 записями.
- **Постусловия:** Значения `currentProgress` и `isUnlocked` актуализированы в SQLite. Возвращает список ачивок, разблокированных именно в текущем вызове.
- **Поведение:**
  1. `ach_first_track`: проверяет `totalSessions >= 1`. Если да $\to$ `unlock(progress=1)`.
  2. `ach_centurion`: проверяет `uniqueTracksCount >= 100`. Устанавливает `currentProgress = min(100, uniqueTracksCount)`. При 100 $\to$ `unlock`.
  3. `ach_marathoner`: проверяет максимальную сумму `durationMs` в пределах календарных суток. Если $\ge 18\_000\_000$ мс (5 ч) $\to$ `unlock`.
  4. `ach_karaoke_star`: подсчитывает количество уникальных треков с открытым LRC караоке. Прогресс `0..10`. При 10 $\to$ `unlock`.
  5. `ach_campfire_singer`: подсчитывает просмотры треков с аккордами. Прогресс `0..5`. При 5 $\to$ `unlock`.
  6. `ach_night_owl`: подсчитывает сессии между 01:00 и 05:00. Прогресс `0..10`. При 10 $\to$ `unlock`.
  7. `ach_loyal_repeater`: проверяет `maxPlayCount >= 25`. Прогресс `0..25`. При 25 $\to$ `unlock`.
  8. Обновляет измененные сущности в `AchievementDao`.
- **Ошибки:** исключений не бросает.
- **Побочные эффекты:** обновление записей в таблице `achievements`.
- **Граничные случаи:** повторный вызов при неизменных данных $\to$ `unlockedAchievements` пуст, прогресс не сбрасывается.
- **Примеры:**
  1. Вход: первое прослушивание $\to$ Выход: `unlockedAchievements` содержит `ach_first_track`.
  2. Вход: 99 треков $\to$ Выход: `ach_centurion.currentProgress = 99, isUnlocked = false`.
  3. Вход: 100-й трек $\to$ Выход: `unlockedAchievements` содержит `ach_centurion` (`isUnlocked = true`).

#### 3. Функция `renderShareCard(stats: WrappedStats, context: Context) -> Uri`
- **Сигнатура:** `suspend fun renderShareCard(stats: WrappedStats, context: Context): Uri`
- **Предусловия:** `context != null`.
- **Постусловия:** Создает в кэше приложения файл `wrapped_share_<timestamp>.png` (разрешение 1080x1920, соотношение 9:16 для Stories) и возвращает безопасный content Uri через `FileProvider`.
- **Поведение:**
  1. Создает внеэкранный `Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)`.
  2. Инициализирует `Canvas(bitmap)` с темным градиентом `#000000` $\to$ `#1A0933` (Obsidian Pulse AMOLED).
  3. Отрисовывает заголовок «MUSIC WRAPPED», активный временной диапазон, бейдж архетипа.
  4. Отрисовывает крупно общее время прослушивания и топ-3 артистов с процентами.
  5. Сохраняет `Bitmap` в файл кэша через `FileOutputStream` с компрессией PNG 100%.
  6. Получает `Uri` через `FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)`.
  7. Возвращает сформированный `Uri`.
- **Ошибки:** При нехватке памяти (OOM) перехватывает ошибку и выбрасывает `RenderException("Не удалось сгенерировать карточку из-за нехватки памяти")`.
- **Побочные эффекты:** Создание файла на диске в кэш-директории.
- **Граничные случаи:** `stats` с нулевыми данными $\to$ генерирует карточку-заглушку с текстом «Начните слушать музыку прямо сейчас».
- **Примеры:**
  1. Вход: валидный `WrappedStats` $\to$ Выход: `content://com.eventengine.app.fileprovider/cache/wrapped_share_1727820000.png`.
  2. Вход: повторный экспорт $\to$ Выход: новый свежий файл в кэше.
  3. Ошибка записи на диск $\to$ выбрасывает `RenderException`.

---

### Зависимости
- Межзонный контракт: [.sdd/contracts/core__wrapped.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/contracts/core__wrapped.md)
- Внешние библиотеки: `android.graphics.Canvas`, `android.graphics.Bitmap`, `androidx.core.content.FileProvider`.

---

### Вне скоупа
- Публикация карточки напрямую в API соцсетей (используется только стандартный Android Intent).
- Воспроизведение звуковых превью.
- Сбор онлайн-аналитики или краш-репортов.
