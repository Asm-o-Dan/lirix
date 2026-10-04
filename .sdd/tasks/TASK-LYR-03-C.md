# Задача TASK-LYR-03-C: Интерфейс отклонения текста, Undo Snackbar и экран исчерпания источников в NowPlayingScreen

- **ID задачи:** `TASK-LYR-03-C`
- **Роль исполнителя:** Кодер
- **Зона:** `media-ui`
- **Файлы:**
  1. `app/src/main/java/com/eventengine/app/ui/NowPlayingScreen.kt` (кнопка «Не тот текст», Snackbar с отменой, обработка состояния «Текст не найден»)
- **Спека:** [.sdd/architecture_lyr_community.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/architecture_lyr_community.md) (Раздел 3.3, 3.5), [.sdd/intake/LYR-COMMUNITY.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/intake/LYR-COMMUNITY.md)
- **Приоритет:** HIGH (Пользовательский интерфейс FR-1)

---

## 1. Назначение и контекст

Пользователь должен иметь возможность в один тап на экране `NowPlayingScreen` (на вкладках «Караоке», «Текст», «Аккорды») заявить, что найденный текст или аккорды не соответствуют треку. При этом:
1. Показывается кнопка «Не тот текст» рядом с бейджем провайдера или в блоке действий.
2. При нажатии текст немедленно заменяется на следующий источник в каскаде, а внизу экрана появляется `Snackbar` с действием `[Отменить]` (Undo в течение 5 секунд).
3. Если источники исчерпаны — показывается чистое состояние «Текст не найден во всех базах» с кнопками:
   - `[🌐 Найти в интернете]` (точка входа для FR-3 WebView Teach Mode);
   - `[🔄 Сбросить отклонённые]` (восстановление всех источников для трека).

---

## 2. Спецификация изменений в `ui/NowPlayingScreen.kt`

### 2.1 Новые состояния и SnackbarHost
1. В `NowPlayingScreen` объявить:
   ```kotlin
   val snackbarHostState = remember { SnackbarHostState() }
   var isRejectingLyrics by remember { mutableStateOf(false) }
   ```
2. Обернуть основной экран или добавить `SnackbarHost(hostState = snackbarHostState)` в корень `Box/Scaffold`.

### 2.2 Кнопка «Не тот текст»
Рядом с плашкой провайдера (строка 650–666) добавить интерактивную кнопку отклонения:
```kotlin
Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    // Бейдж провайдера
    Surface(
        color = AppColors.SurfaceLevel2,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, AppColors.BorderSubtle)
    ) {
        Text(
            text = lyricsCache?.provider?.uppercase() ?: "AUDIO",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            ),
            color = AppColors.TextTertiary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }

    // Кнопка отклонения "Не тот текст" (активна, когда текст найден и не является личной заметкой)
    if (lyricsCache != null && (!lyricsCache!!.plainLyrics.isNullOrBlank() || !lyricsCache!!.syncedLyricsLrc.isNullOrBlank())) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(AppColors.SurfaceLevel2)
                .border(1.dp, AppColors.BorderSubtle, RoundedCornerShape(6.dp))
                .clickable(enabled = !isRejectingLyrics) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val trackKey = currentTrack?.trackKey ?: return@clickable
                    val oldProvider = lyricsCache?.provider.orEmpty()
                    isRejectingLyrics = true

                    scope.launch(Dispatchers.IO) {
                        val engine = MusicFeatureEngine(context, db)
                        val nextResult = engine.rejectCurrentLyrics(trackKey, oldProvider)
                        
                        // Обновляем локальное состояние
                        lyricsCache = if (nextResult.hasLyrics) {
                            db.lyricsDao().getLyrics(trackKey)
                        } else null
                        isRejectingLyrics = false

                        // Показываем Snackbar с возможностью Undo
                        val snackbarResult = snackbarHostState.showSnackbar(
                            message = if (nextResult.hasLyrics) {
                                "Текст заменён на ${nextResult.source}"
                            } else {
                                "Источник отклонён. Других текстов нет"
                            },
                            actionLabel = "Отменить",
                            duration = SnackbarDuration.Short
                        )

                        if (snackbarResult == SnackbarResult.ActionPerformed) {
                            val restored = engine.undoLyricsRejection(trackKey, oldProvider)
                            lyricsCache = db.lyricsDao().getLyrics(trackKey)
                        }
                    }
                }
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "✕ Не тот текст",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = AppColors.HyperViolet
            )
        }
    }
}
```

### 2.3 Экран исчерпания источников («Текст не найден»)
Внутри `Crossfade` для режимов `KARAOKE` и `LYRICS`, когда `lyricsCache` пуст или не содержит строк:

```kotlin
@Composable
fun ExhaustedLyricsPlaceholder(
    onSearchWeb: () -> Unit,
    onResetRejections: () -> Unit,
    onRetryDefault: () -> Unit,
    hasRejections: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Текст не найден",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = AppColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (hasRejections) {
                "Все доступные источники были опрошены или отклонены"
            } else {
                "Текст отсутствует в стандартных базах"
            },
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Кнопка перехода к режиму поиска / обучения
        Button(
            onClick = onSearchWeb,
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.HyperViolet),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("🌐 Найти в интернете", color = AppColors.AmoledBlack, fontWeight = FontWeight.Bold)
        }

        if (hasRejections) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onResetRejections,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AppColors.CyberCyan)
            ) {
                Text("🔄 Сбросить отклонённые источники", color = AppColors.CyberCyan)
            }
        }
    }
}
```

---

## 3. Критерии приемки (DoD)

1. **Интерфейс NowPlaying:**
   - При отображении текста от любого источника (кроме заметок) видна кнопка `✕ Не тот текст`.
   - Нажатие на кнопку запускает замену текста на следующий источник с отображением лоадера.
   - Появляется `Snackbar` с текстом источника и кнопкой `[Отменить]`.
   - Нажатие на `[Отменить]` мгновенно откатывает отклонение и восстанавливает прежний текст.
2. **Экран «Текст не найден»:**
   - Если все источники отклонены, отображается плейсхолдер с кнопками `🌐 Найти в интернете` и `🔄 Сбросить отклонённые источники`.
   - Нажатие на `Сбросить отклонённые источники` очищает отклонения трека и восстанавливает первоначальный текст из основного провайдера.
3. Проект успешно собирается и проходит тесты (`.\gradlew.bat assembleDebug`).
