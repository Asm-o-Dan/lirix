# Задача TASK-UI-06-A: Компонент NowPlayingOverflowMenu и диалог калибровки

- **ID задачи:** `TASK-UI-06-A`
- **Роль исполнителя:** Кодер
- **Зона:** `media-ui`
- **Файл:** `app/src/main/java/com/lirix/app/ui/components/NowPlayingOverflowMenu.kt` (создать)
- **Спека:** `.sdd/specs/media-ui/overflow_and_capsule.md`

## 1. Содержимое файла
Создать выпадающее меню на Jetpack Compose с палитрой Obsidian Pulse:
- Фон меню: `AppColors.SurfaceLevel1` с закруглением `RoundedCornerShape(16.dp)` и бордером `AppColors.BorderSubtle`.
- Элементы `DropdownMenuItem`:
  1. Плавающий оверлей поверх окон (иконка `PictureInPictureAlt`, бейдж активности)
  2. Поделиться треком (иконка `Share`)
  3. Сменить источник текста (иконка `Close` / `SyncProblem`)
  4. Калибровка тайминга (иконка `Tune` / `Timer`)
  5. Режим обучения (иконка `School` / `Code`)
  6. Скрыть / показать винил (иконка `Album` / `MusicNote`)
- Диалог `TimingCalibrationDialog` для настройки оффсета ±50мс с кнопками -10мс, +10мс, Сброс и Закрыть.
