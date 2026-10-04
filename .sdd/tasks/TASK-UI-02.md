# Задача TASK-UI-02: Синхронизированный плеер караоке LRC в NowPlayingScreen

- **ID задачи:** `TASK-UI-02`
- **Роль исполнителя:** Кодер
- **Зона:** `media-ui`
- **Файл:** `app/src/main/java/com/eventengine/app/ui/NowPlayingScreen.kt` (изменить)
- **Место в файле:** заменить `SyncedKaraokeView` и `parseLrcLines` на новый компонент `HighFidelityKaraokePlayer` и обновить `NowPlayingScreen`
- **Спека:** [.sdd/specs/media-ui/overview.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ui/overview.md) (v1), [.sdd/contracts/core__ui.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/contracts/core__ui.md) (FROZEN v1)

---

## 1. Контекст и цель задачи

Пользовательское требование: «норм караоке надо». 
Текущая реализация караоке в `NowPlayingScreen.kt` статична: отсутствует тайминг воспроизведения, нет центровки скролла, неактивные строки не затемняются, а при отсутствии LRC отображается голый текст об ошибке.

**Цель:** реализовать высококачественный, плавный 120 Гц караоке-плеер с неоновой подсветкой активной строки, затемнением неактивных строк, центрированным автоскроллингом, интерактивной перемоткой по тапу и элегантным fallback-режимом для plain text.

---

## 2. Сигнатуры компонентов (НЕ МЕНЯТЬ)

### 2.1 Модель строки караоке
```kotlin
data class KaraokeLine(
    val timestampMs: Long,
    val text: String
)
```

### 2.2 Функция парсинга LRC
```kotlin
fun parseLrcLinesStrict(rawLrc: String): List<KaraokeLine>
```

### 2.3 Компонент караоке-плеера
```kotlin
@Composable
fun HighFidelityKaraokePlayer(
    lrcText: String?,
    plainText: String?,
    playbackPositionMs: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
)
```

---

## 3. Детальное поведение

### 3.1 Алгоритм парсинга `parseLrcLinesStrict(rawLrc: String)`
1. Если `rawLrc.isBlank()` $\to$ вернуть `emptyList()`.
2. Разбить строку по `\n`.
3. Для каждой строки применить регулярное выражение:
   `Regex("""^\[(\d{1,2}):(\d{2})(?:[.:](\d{2,3}))?\](.*)$""")`.
4. Извлечь:
   - Минуты $M = group(1).toLong()$.
   - Секунды $S = group(2).toLong()$.
   - Дроби $MS = group(3)$:
     - если длина равна 2 $\to ms = val \times 10$.
     - если длина равна 3 $\to ms = val$.
     - если группа отсутствует $\to ms = 0$.
   - Общее время: $timestampMs = M \times 60000L + S \times 1000L + ms$.
   - Текст строки: $text = group(4).trim()$.
5. Пропустить пустые строки или метаданные тегов вида `[ar:]`, `[ti:]`, `[length:]`.
6. Отсортировать результирующий список по `timestampMs`.
7. Вернуть отсортированный список `List<KaraokeLine>`.

### 3.2 Логика позиционирования и автоскролла в `HighFidelityKaraokePlayer`
1. **Fallback (нет LRC):**
   - Если `lrcText.isNullOrBlank()`, а `plainText` заполнен:
     - Отрисовать режим чтения с бейджем вверху: `Surface(color = AppColors.SurfaceLevel2, shape = RoundedCornerShape(8.dp))` с надписью «Обычный текст (LRC таймкоды отсутствуют)».
     - Предоставить вертикальный скролл всего текста `rememberScrollState()`.
2. **Определение активной строки при наличии LRC:**
   - Вычислить индекс активной строки `activeIndex`:
     наибольший индекс $i$, для которого `lines[i].timestampMs <= playbackPositionMs`.
     Если позиция меньше времени первой строки $\to activeIndex = 0$.
3. **Плавная центровка на экране:**
   - Создать `lazyListState = rememberLazyListState()`.
   - При изменении `activeIndex` и условии `isPlaying == true`:
     запустить `LaunchedEffect(activeIndex)`:
     ```kotlin
     val targetScrollIndex = (activeIndex - 2).coerceAtLeast(0)
     lazyListState.animateScrollToItem(
         index = targetScrollIndex,
         scrollOffset = 0
     )
     ```
4. **Визуальный дизайн элементов списка (`LazyColumn`):**
   - Добавить отступы сверху и снизу `contentPadding = PaddingValues(vertical = 120.dp)`, чтобы первая и последняя строки могли центрироваться в окне.
   - Для активной строки ($index == activeIndex$):
     - Шрифт: `MaterialTheme.typography.headlineSmall` (22 sp), `FontWeight.ExtraBold`.
     - Цвет текста: `AppColors.HyperViolet` (`#B388FF`) или акцентный неоновый `#00E5FF`.
     - Неоновый фон/подсветка: лёгкая подсветка плашки `AppColors.HyperVioletGlow` (альфа 0.15) со скруглением `RoundedCornerShape(12.dp)`.
     - Масштабирование: `Modifier.scale(1.04f)` через `animateFloatAsState`.
     - Прозрачность: `alpha = 1.0f`.
   - Для неактивных строк ($index \ne activeIndex$):
     - Шрифт: `MaterialTheme.typography.titleMedium` (16 sp), `FontWeight.Medium`.
     - Цвет текста: `AppColors.TextSecondary`.
     - Прозрачность: плавное затемнение (dimming) `alpha = 0.35f` с анимацией `animateFloatAsState(if (isActive) 1f else 0.35f, tween(300))`.
5. **Интерактивный тап по строке:**
   - При клике на любую строку караоке вызвать `onSeekTo(line.timestampMs)`.
   - Обеспечить тактильный отклик `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.TextHandleMove)`.

### 3.3 Интеграция позиции воспроизведения в `NowPlayingScreen`
1. В `NowPlayingScreen` добавить внутренний таймер воспроизведения для обновления `playbackPositionMs`:
   - Если плеер играет (`isPlaying == true`), каждую 100 мс корутина `LaunchedEffect(isPlaying)` увеличивает локальное смещение `currentPositionMs += 100L`.
   - При клике по строке караоке `onSeekTo = { targetMs -> currentPositionMs = targetMs }`.

---

## 4. Ошибки и граничные случаи

- **Пустой вход:** `rawLrc = ""` $\to$ возвращает `emptyList()`, переключается в fallback plain text.
- **Смешанные таймкоды (сотые и тысячные доли):** `[01:05.5]` и `[01:05.500]` $\to$ парсятся к одинаковым миллисекундам ($65500$ мс).
- **Быстрая перемотка:** резкое изменение позиции с $10$ с на $180$ с $\to$ `animateScrollToItem` корректно перескакивает без рассинхронизации.

---

## 5. Можно использовать

- Дизайн-токены `AppColors.HyperViolet`, `AppColors.HyperVioletGlow`, `AppColors.AmoledBlack`, `AppColors.CyberCyan` из `com.eventengine.app.ui.theme.AppColors`.
- Стандартные анимации Compose: `animateFloatAsState`, `animateScrollToItem`, `Crossfade`.
- `HapticFeedbackType.TextHandleMove` из `androidx.compose.ui.hapticfeedback`.

---

## 6. Запрещено

- Добавлять сторонние библиотеки парсинга LRC или медиаплееров.
- Менять схему базы данных Room или DAOs.
- Ломать существующие вкладки («Текст», «Аккорды», «Заметки»).
- Блокировать UI-поток тяжелыми вычислениями.

---

## 7. Критерии приёмки

1. [ ] Функция `parseLrcLinesStrict` покрыта unit-тестами (корректные таймкоды, сотые доли, пробелы, некорректные строки).
2. [ ] Вкладка «Караоке» в `NowPlayingScreen` подсвечивает текущую строку ярким цветом (`#B388FF` / `#00E5FF`) с увеличенным размером шрифта.
3. [ ] Неактивные строки плавно затемняются до прозрачности 0.35.
4. [ ] При воспроизведении список плавно центрирует текущую строку.
5. [ ] При тапе на любую строку происходит переход на указанный таймкод с тактильным откликом.
6. [ ] При отсутствии LRC трек корректно открывается в fallback-режиме чтения с плашкой «Обычный текст».
7. [ ] Сборка `./gradlew assembleDebug` и все тесты `./gradlew testDebugUnitTest` проходят успешно.
