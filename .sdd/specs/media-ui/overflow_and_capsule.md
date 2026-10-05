# Спецификация: Разгрузка UI плеера (Троеточие ⋮) и Плавающая Капсула Навигации

- **Зона:** `media-ui`
- **Версия:** v1 (FROZEN)
- **Статус:** APPROVED

---

## 1. Компонент `NowPlayingOverflowMenu`

### 1.1 Сигнатура
```kotlin
@Composable
fun NowPlayingOverflowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    isOverlayActive: Boolean,
    onToggleOverlay: () -> Unit,
    onShareClick: () -> Unit,
    onRejectSourceClick: () -> Unit,
    onCalibrateClick: () -> Unit,
    onTeachModeClick: () -> Unit,
    isVinylCollapsed: Boolean,
    onToggleVinyl: () -> Unit,
    modifier: Modifier = Modifier
)
```

### 1.2 Диалог калибровки тайминга `TimingCalibrationDialog`
```kotlin
@Composable
fun TimingCalibrationDialog(
    currentOffsetMs: Long,
    onOffsetChange: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onDismissRequest: () -> Unit
)
```

---

## 2. Поведение NowPlayingScreen

1. В шапке трека (`Row` с метаданными):
   - Название и исполнитель занимают весь доступный `weight(1f)`.
   - Справа остаются строго две иконки:
     - `IconButton` Favorite (`♡` / `❤️`)
     - `IconButton` Overflow Menu (`⋮` / `Icons.Default.MoreVert`)
2. Постоянные плашки калибровки и смены источника удаляются из основного Column экрана.
3. При нажатии на `⋮` открывается стилизованное меню Obsidian Pulse со списком действий:
   - "Плавающий оверлей (PIP)"
   - "Поделиться постером (Share)"
   - "Сменить источник текста"
   - "Калибровка задержки караоке"
   - "Режим обучения (Teach Mode)"
   - "Скрыть / показать винил"

---

## 3. Поведение AppScaffold

1. Нижняя панель заменяется с тяжелого 80dp `NavigationBar` на тонкую плавающую капсулу:
   - Высота: 48dp
   - Скругление: 24dp
   - Градиентный бордер 1.5dp: HyperViolet -> CyberCyan
   - Отступ снизу: 16dp
2. Автоскрытие:
   - При скролле контента капсула плавно уезжает вниз `slideOutVertically { it + 100 }`.
   - При паузе или тапе возвращается `slideInVertically { it + 100 }`.
