# TASK-UI-TAPSYNC-INSETS-02: Защита нижних кнопок от перекрытия жестовой навигацией в TapSync Studio

## 1. Контекст и Проблематика
На физических устройствах Android с жестовой навигацией (MIUI, OneUI, Pixel) нижние кнопки управления («Отменить шаг», «Сбросить всё») в диалоге `LrcTapSyncStudioDialog` были наполовину утоплены вниз и перекрывались белой жестовой полосой Android. Причина: `WindowInsets.navigationBars` в Compose `Dialog` возвращает нулевой или недостаточный отступ без явной настройки окна диалога через `WindowCompat.setDecorFitsSystemWindows`.

## 2. Архитектурная зона и файлы реализации
- **Зона**: `media-ui`
- **Файл**:
  - `app/src/main/java/com/lirix/app/ui/components/LrcTapSyncStudioDialog.kt`

## 3. Детерминированные требования (DoR / Спецификация)
1. **Проброс WindowInsets в окно Dialog**:
   - Использовать `DisposableEffect(dialogView)` с получением `(dialogView.parent as? DialogWindowProvider)?.window`.
   - Вызывать `WindowCompat.setDecorFitsSystemWindows(window, false)` для полноэкранного расчета инсетов.
2. **Динамический безопасный отступ**:
   - `safeBottomPadding = maxOf(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(), 28.dp)`.
   - Гарантированный клиренс не менее 28dp на любых версиях ОС и OEM-оболочках.
3. **Оптимизация высоты контролов**:
   - Главная кнопка тапа: 48dp.
   - Нижние кнопки «Отменить шаг» / «Сбросить всё»: 42dp.
   - Отступ между блоками: 6dp.

## 4. DoD (Definition of Done)
- [x] Окно диалога настроено через `DialogWindowProvider`.
- [x] Рассчитан `safeBottomPadding`.
- [x] Нижние кнопки полностью видны над жестовой полосой на физическом устройстве.
- [x] Статус: **DONE**
