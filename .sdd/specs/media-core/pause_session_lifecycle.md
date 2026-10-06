# Спецификация Зоны Media Core: Жизненный Цикл Сессии при Паузе и Защита PlayCount

## 1. Назначение и Контекст
В Android-системе воспроизведение аудио регулярно переходит в состояние `PAUSED` (например, при нажатии пользователем кнопки паузы, входящем звонке или кратковременном прерывании фокуса).
Ранее сессия в Room помечалась `isCompleted = true` при любом получении сигнала `PAUSED`. В результате:
1. При возобновлении воспроизведения того же трека через промежуток времени (> 60 сек кулдауна) приложение ошибочно считало, что начался повторный прогон трека с нуля (`isTrackReplayAfterCompletion == true`).
2. Это приводило к инкременту `playCount += 1`, искажению статистики в истории и годовом отчете Wrapped.

---

## 2. Модель Состояний Сессии Прослушивания

### Правила `isCompleted`:
Сессия помечается как `isCompleted = true` **ТОЛЬКО** в двух случаях:
1. Поступил явный сигнал остановки воспроизведения: `playbackState == "STOPPED"`.
2. Произошла смена трека (`isNewTrack == true`), в результате чего предыдущая незакрытая сессия финализируется.

При получении сигнала `playbackState == "PAUSED"`:
- Сессия обновляется (`endTimeMs = timestamp`, `durationMs` увеличивается на дельту воспроизведения).
- `isCompleted` устанавливается в `false`!
- Сессия остается активной и ждет либо возобновления (`PLAYING`), либо остановки (`STOPPED`), либо смены трека.

---

## 3. Требования к модификации `MusicFeatureEngine.kt`

### 3.1 Метод `recordPlaybackSignal`
В строке обновления существующей сессии:
```kotlin
// Было:
isCompleted = (playbackState == "PAUSED" || playbackState == "STOPPED")

// Должно стать:
isCompleted = (playbackState == "STOPPED")
```

### 3.2 Метод `processMusicEvent`
В строке обновления существующей сессии:
```kotlin
// Было:
isCompleted = (playbackState == "PAUSED" || playbackState == "STOPPED")

// Должно стать:
isCompleted = (playbackState == "STOPPED")
```

### 3.3 Правило Инкремента `playCount`
Проверка `isTrackReplayAfterCompletion`:
```kotlin
val isTrackReplayAfterCompletion = (lastSession != null && lastSession.trackKey == trackKey && lastSession.isCompleted && isCooldownPassed)
```
Поскольку при паузе `lastSession.isCompleted` остается `false`, при возобновлении (`PLAYING` после `PAUSED` на том же треке):
- `isTrackReplayAfterCompletion` будет равен `false`.
- `isTrackSwitch` равен `false`.
- `isFirstTrackOccurrence` равен `false`.
- `shouldIncrement` равен `false`.
- Значение `playCount` сохраняется неизменным!

---

## 4. Контракт Тестирования (DoD)
1. **Тест на сохранение открытого статуса сессии при паузе**:
   После отправки события с `playbackState = "PAUSED"` для сессии в БД должно выполняться:
   `assertFalse(session.isCompleted)`.
2. **Тест на паузу и возобновление через интервал > 60 секунд**:
   - `t0`: сигнал `PLAYING` для трека A -> `playCount == 1`.
   - `t0 + 10s`: сигнал `PAUSED` для трека A -> `playCount == 1`, `session.isCompleted == false`.
   - `t0 + 80s` (> 60s cooldown): сигнал `PLAYING` для трека A -> `playCount == 1` (счетчик НЕ увеличивается!).
   - Проверка в БД: `track.playCount == 1`.
