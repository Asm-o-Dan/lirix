# Карточка Задачи: TASK-UI-07

- **ID**: `TASK-UI-07`
- **Зона**: `media-ui`
- **Целевой файл**: `app/src/main/java/com/lirix/app/ui/components/LrcTapSyncStudioDialog.kt`
- **Исполнитель**: Coder (UI / Compose)
- **Статус**: READY

---

## 1. Контекст и Проблема
На реальном устройстве (скриншот с POCO M7) кнопки «Шаг назад» и «Сбросить» внизу экрана студии караоке почти не видны — они обрезаны нижним краем дисплея и перекрываются системной полосой навигационных жестов.

### Причины:
1. В `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false))` стандартный модификатор `.navigationBarsPadding()` может отдавать 0dp из-за изолированного окна диалога.
2. Центральный блок телесуфлера (`weight(1f)`) забирает слишком много вертикального пространства из-за крупных вертикальных `Spacer(14.dp)` и паддингов.
3. Нижние кнопки («Шаг назад» и «Сбросить») расположены после кнопки тапа («ТАП • СЛЕДУЮЩАЯ СТРОКА») и упираются в нижнюю границу без безопасного отступа.

---

## 2. Требования к Реализации
В файле `app/src/main/java/com/lirix/app/ui/components/LrcTapSyncStudioDialog.kt`:

1. **Безопасные отступы диалога (System Insets)**:
   - В корневой `Column` диалога:
     ```kotlin
     modifier = Modifier
         .fillMaxSize()
         .statusBarsPadding()
         .navigationBarsPadding()
         .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp)
     ```
   - Задать `bottom = 28.dp` (вместо 16.dp), чтобы кнопки гарантированно находились выше системного индикатора жестов Android (Gesture Pill) даже если `navigationBarsPadding` в диалоге сброшен.

2. **Оптимизация вертикальных отступов в телесуфлере (Teleprompter)**:
   - Внутри `Surface` телесуфлера:
     - Паддинг `padding(horizontal = 16.dp, vertical = 8.dp)` (вместо 12.dp).
     - Отступы между предыдущей/активной/следующей строками: `Spacer(modifier = Modifier.height(8.dp))` (вместо 14.dp).
     - Активная строка: внутренний паддинг `padding(horizontal = 14.dp, vertical = 12.dp)` (вместо 18.dp).

3. **Компактная верстка нижней панели управления**:
   - Отступ между плеером и кнопкой тапа: `Spacer(modifier = Modifier.height(8.dp))` (вместо 12.dp).
   - Высота кнопки «ТАП • СЛЕДУЮЩАЯ СТРОКА»: `defaultMinSize(minHeight = 48.dp)` (вместо 54.dp/56.dp).
   - Отступ между кнопкой тапа и кнопками «Шаг назад» / «Сбросить»: `Spacer(modifier = Modifier.height(8.dp))`.
   - Высота кнопок «Шаг назад» / «Сбросить»: `defaultMinSize(minHeight = 38.dp)`.

---

## 3. Критерии Приемки (Definition of Done)
1. Код компилируется без ошибок.
2. Все кнопки внизу экрана («ТАП • СЛЕДУЮЩАЯ СТРОКА», «Шаг назад», «Сбросить») полностью и четко видны на экране с достаточным запасом до нижнего края (safe area).
3. `./gradlew testDebugUnitTest` и `./gradlew assembleRelease` проходят успешно.
