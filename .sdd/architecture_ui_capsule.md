# Архитектурный проект: Разгрузка UI плеера (Троеточие ⋮) и Скрывающаяся плавающая капсула навигации

- **ID задачи:** `ARCH-UI-06`
- **Зона:** `media-ui`
- **Статус:** COMPLETED (Gate 1 Passed)
- **Целевые файлы:**
  - `app/src/main/java/com/lirix/app/ui/components/NowPlayingOverflowMenu.kt` (новый компонент)
  - `app/src/main/java/com/lirix/app/ui/NowPlayingScreen.kt` (рефакторинг шапки и действий)
  - `app/src/main/java/com/lirix/app/ui/AppScaffold.kt` (плавающая капсула)

---

## 1. Архитектура NowPlayingOverflowMenu

### 1.1 Состав меню (Obsidian Pulse Bottom Sheet / Dropdown)
Меню группирует второстепенные и сервисные функции, разгружая основной экран:
1. **Плавающий оверлей поверх всех окон (PIP)** — `FloatingLyricsService.toggle(context)` с индикацией активности (зеленый бейдж/иконка).
2. **Поделиться треком / Stories картой (Share)** — открытие диалога выбора формата (9:16 Stories или 1:1 Square).
3. **Сменить источник текста («Не тот текст»)** — отклонение текущего провайдера и переход к следующему в каскаде (`onRejectLyricsSource`).
4. **Калибровка задержки тайминга** — открытие тонкого модального слайдера/диалога (±50 мс с шагом ±10 мс) вместо постоянной плашки на экране.
5. **Режим обучения (Teach Mode)** — переход на экран инспектора селекторов `TeachModeScreen`.
6. **Переключение винила** — скрыть/показать аналоговый диск (`isVinylCollapsed = !isVinylCollapsed`).

### 1.2 Очистка шапки плеера (`NowPlayingScreen.kt`)
- Было:
  `[Мини-диск + Название + Артист] + [Кнопка разрешения] + [Кнопка PIP] + [Кнопка Share] + [Кнопка Избранное]`
- Стало:
  `[Мини-диск + Название + Артист (weight 1f)] + [Кнопка Избранное ♡] + [Кнопка Троеточие ⋮]`
- Удаляется из постоянного UI:
  - Постоянная строка калибровки тайминга под транспортом.
  - Постоянная плашка отклонения источника («Не тот текст») под караоке (переносится в меню ⋮ и вызывается оттуда).

---

## 2. Архитектура Auto-Hide Floating Capsule Navigation (`AppScaffold.kt`)

### 2.1 Геометрия и стиль
- Вместо полноразмерного `NavigationBar` (80dp) внедряется ультра-тонкая плавающая капсула:
  - Высота: `48.dp`
  - Форма: `RoundedCornerShape(24.dp)` (Pill)
  - Расположение: плавает над нижним краем экрана с отступом `padding(horizontal = 24.dp, bottom = 16.dp)`.
  - Фон: `AppColors.SurfaceLevel1.copy(alpha = 0.92f)` с размытием / сатином.
  - Обводка: градиент `HyperViolet` $\to$ `CyberCyan` (1.dp).

### 2.2 Логика скрытия (Kinetic Auto-Hide)
- Отслеживание скролла:
  - Через `NestedScrollConnection` в `AppScaffold` или `rememberScrollState() / rememberLazyListState()`.
  - Скролл вниз (палец вверх $\to$ чтение караоке дальше) $\implies$ капсула анимируется вниз:
    `AnimatedVisibility(visible = isCapsuleVisible, enter = slideInVertically { it + 100 } + fadeIn(), exit = slideOutVertically { it + 100 } + fadeOut())`
  - Пауза воспроизведения или одиночный тап по экрану $\implies$ капсула мгновенно и мягко возвращается.

---

## 3. План декомпозиции на задачи
1. `TASK-UI-06-A`: Создание файла `app/src/main/java/com/lirix/app/ui/components/NowPlayingOverflowMenu.kt` (компонент меню и диалог калибровки).
2. `TASK-UI-06-B`: Рефакторинг `app/src/main/java/com/lirix/app/ui/NowPlayingScreen.kt` (очистка шапки, перенос экшенов в ⋮, удаление визуального мусора).
3. `TASK-UI-06-C`: Рефакторинг `app/src/main/java/com/lirix/app/ui/AppScaffold.kt` (плавающая капсула 48dp со скрытием при скролле).
