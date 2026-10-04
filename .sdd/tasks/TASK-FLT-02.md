# TASK-FLT-02: UI плавающего виджета FloatingLyricsOverlayView с жестом перетаскивания

**Файл:** `app/src/main/java/com/eventengine/app/ui/components/FloatingLyricsOverlayView.kt`
**Зона:** `media-ui`
**Спецификация:** `.sdd/intake/TRACK_C_FLOATING_OVERLAY.md`

## 1. Цель
Создать плавающее наложение с эргономичным дизайном:
- В свёрнутом виде: полупрозрачная Dynamic Island «таблетка» (pill) с текущей строкой караоке или аккордом.
- В развёрнутом виде: карточка мини-плеера (320dp) с 3 строками караоке, кнопками `Play/Pause`, `±10s`, кнопкой возврата в Lirix и закрытия.

## 2. Физика и жесты (Kinetic Drag & Magnetic Snap)
- Распознавание тапа vs свайпа через `touchSlop`.
- Плавное перемещение `WindowManager.LayoutParams.x/y` без рывков.
- При отпускании пальца — кинетическое примагничивание (`ValueAnimator`, `DecelerateInterpolator`) к ближайшему краю экрана (левому или правому с безопасным отступом).
