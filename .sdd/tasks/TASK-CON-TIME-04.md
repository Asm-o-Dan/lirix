# TASK-CON-TIME-04: Синхронизация часов (NTP Ping/Pong) и интерполяция позиции (±100мс)

## 1. Контекст и Цель
Реализовать алгоритм сетевой синхронизации часов и интерполяции позиции трека в модуле `:shared` по стандарту **SPEC-CONNECT-01 (§4.5)**:
1. Алгоритм расчёта сетевого смещения (NTP 4-timestamp):
   - Отправка серий по 5–8 `Ping(t0)` / `Pong(t0, t1, t2)`.
   - Приём ответа в момент `t3`: $RTT = (t_3 - t_0) - (t_2 - t_1)$.
   - Смещение часов по измерению с минимальным $RTT$:
     $$\text{offset} = \frac{(t_1 - t_0) + (t_2 - t_3)}{2}$$
2. Интерполятор позиции трека `PlaybackPositionInterpolator`:
   - Расчёт локальной позиции на контроллере и целевом плеере:
     $$\text{targetPos} = \text{positionMs} + \text{playbackRate} \times (\text{nowMonoMs} - (\text{positionAtMonoMs} + \text{offset}))$$
   - Защита от отрицательных значений и выход за пределы `durationMs`.
   - Обработка паузы (`isPlaying = false`): позиция фиксируется на `positionMs`.
   - Плавное устранение дрейфа при приходе периодических `RemoteStateUpdate` (не реже раза в 5 с).
3. Допустимая погрешность $\le \pm 100$ мс (целевая $\le \pm 50$ мс по LAN).

---

## 2. Затрагиваемые файлы и целевые пакеты
Модуль `shared/src/commonMain/kotlin/com/lirix/app/connect/`:
1. `time/ClockSyncEngine.kt` — сбор образцов Ping/Pong, фильтрация по min RTT, расчет offset.
2. `time/PlaybackPositionInterpolator.kt` — расчет монотонной позиции для суфлёра/караоке и синхронного старта.
3. Тесты: `shared/src/commonTest/kotlin/com/lirix/app/connect/ClockSyncEngineTest.kt`.

---

## 3. Критерии приёмки (DoD)
1. **Точность NTP**: unit-тест с искусственными сетевыми задержками (RTT 20–100 мс) и асимметрией подтверждает точность вычисления offset в пределах $\pm 10$ мс.
2. **Интерполяция при воспроизведении**: при `playbackRate = 1.0f` позиция монотонно нарастает со временем.
3. **Интерполяция при паузе**: при `isPlaying = false` интерполированная позиция остаётся строго равной `positionMs`.
4. **Ограничения длительности**: позиция зажата в диапазоне `0 .. durationMs`.
5. **Прогон тестов**: все тесты в `ClockSyncEngineTest.kt` проходят через `./gradlew :shared:desktopTest`.
