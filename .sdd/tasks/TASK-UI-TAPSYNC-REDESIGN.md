# TASK-UI-TAPSYNC-REDESIGN: Редизайн экрана интерактивной синхронизации Tap-to-Sync Studio под дизайн-систему Lirix 2.0

## 1. Контекст и Проблематика
На физическом устройстве экран Tap-to-Sync Studio (`LrcTapSyncStudioDialog.kt`) сохранял элементы старого прототипа:
1. **Неоновые градиенты и обводки**: градиенты от Cyan к Violet, светящиеся рамки.
2. **Типографика**: использовались системные шрифты Compose вместо утвержденной типографической пары Lirix 2.0 (`Literata` для строк песен, `Golos Text` с `tnum` для интерфейса и таймкодов).
3. **Регистр и эмодзи**: капс («ТАП • СЛЕДУЮЩАЯ СТРОКА», «ТАПАЙТЕ В НАЧАЛО ЭТОЙ СТРОКИ») и эмодзи 👆 вместо векторных иконок и Sentence case.
4. **Обрезка системным баром**: нижние кнопки управления («Отменить шаг», «Сбросить всё») перекрывались жестовой полосой Android из-за неполного учета `navigationBarsPadding`.

---

## 2. Архитектурная зона и файл реализации
- **Зона**: `media-ui`
- **Файл**: `app/src/main/java/com/lirix/app/ui/components/LrcTapSyncStudioDialog.kt`
- **Зависимости**:
  - `com.lirix.app.ui.theme.LirixThemeTokens`
  - `com.lirix.app.ui.theme.LiterataFontFamily`
  - `com.lirix.app.ui.theme.GolosTextFontFamily`
  - `com.lirix.app.ui.theme.FontFeature`
  - `com.lirix.app.ui.theme.toSentenceCase`

---

## 3. Детерминированные требования (DoR / Спецификация)

### 3.1. Типографика (Lirix 2.0)
1. **Строки текста песни (Lyrics Teleprompter)**:
   - Все строки песни (`lines`) выводятся через `LiterataFontFamily` с применением `.toSentenceCase()`.
   - **Активная фокусная строка (`currentItem`)**:
     - `fontFamily = LiterataFontFamily`, `fontWeight = FontWeight.Medium` (не ExtraBold).
     - `fontSize = 20.sp`, `lineHeight = 28.sp`.
     - `color = tokens.textPrimary`, `textAlign = TextAlign.Center`.
   - **Контекстные строки (`prevItem`, `nextItem`)**:
     - `fontFamily = LiterataFontFamily`, `fontWeight = FontWeight.Normal`.
     - `fontSize = 14.sp`, `lineHeight = 20.sp`.
     - `color = tokens.textSecondary.copy(alpha = 0.65f)`.
   - **Служебные метки («— Начало песни —», «— Конец текста —»)**:
     - `fontFamily = GolosTextFontFamily`, `fontSize = 12.sp`, `color = tokens.textMuted.copy(alpha = 0.5f)`.

2. **Интерфейс, кнопки и таймкоды (Interface & Controls)**:
   - Заголовок трека: `fontFamily = GolosTextFontFamily`, `fontWeight = FontWeight.SemiBold`, `fontSize = 15.sp`.
   - Артист: `fontFamily = GolosTextFontFamily`, `fontWeight = FontWeight.Normal`, `fontSize = 12.sp`.
   - Счетчик прогресса («Строка X из Y»): `fontFamily = GolosTextFontFamily`, `fontWeight = FontWeight.Medium`, `fontSize = 12.sp`.
   - Подсказка под активной строкой: «Нажмите в начале этой строки» (`GolosTextFontFamily`, `fontSize = 12.sp`, `fontWeight = FontWeight.Normal`, `color = tokens.accentPrimary`).
   - Таймкоды плеера (`currentPlaybackPositionMs` / `durationMs`):
     - `fontFamily = GolosTextFontFamily`, `fontFeatureSettings = FontFeature.tabularFigures()`, `fontWeight = FontWeight.SemiBold`, `fontSize = 14.sp`.
   - Все кнопки действий: `fontFamily = GolosTextFontFamily`, `fontWeight = FontWeight.SemiBold`.

---

### 3.2. Компоновка и стилизация (No-Slop Studio Deck)
1. **Фокусная область телесуфлера (Центральный холст)**:
   - Фоновый контейнер `Surface` с `tokens.surfacePrimary`, скругление `20.dp`, граница `1.dp` `tokens.borderSubtle`.
   - Карточка активной строки:
     - Скругление `16.dp`, фон `tokens.surfaceCard`.
     - Акцентная граница `1.5.dp` `tokens.accentPrimary` (без размытых теней и неоновых градиентов).
     - Пульсация масштаба `pulseScale` в диапазоне `1.0f .. 1.025f` (плавный тактильный ритм).

2. **Главная кнопка тапа (Action Button)**:
   - Высота: ровно `56.dp`, скругление `16.dp`.
   - Заливка: сплошной `tokens.accentPrimary`.
   - Текст: `Тап — следующая строка` (Sentence case, никакого КАПСА).
   - Иконка перед текстом: векторная `Icons.Default.TouchApp` (размер 22dp), цвет `tokens.appBackground` (или чистый контрастный цвет).
   - При завершении всех строк: текст `Сохранить караоке` + иконка `Icons.Default.Check`.

3. **Панель плеера и задержки реакции**:
   - Панель плеера: `tokens.surfaceElevated`, граница `1.dp` `tokens.borderSubtle`.
   - Кнопка Play/Pause: круглая `42.dp`, заливка `tokens.accentPrimary`, иконка `tokens.appBackground`.
   - Блок задержки реакции: текст «Задержка реакции: 200 мс», чипы «−50 мс» и «+50 мс» с сенсорной зоной $\ge 44\,\text{dp}$.

4. **Нижний док действий и безопасные отступы (Navigation Insets)**:
   - Контейнер нижних кнопок («Отменить шаг», «Сбросить всё») оформлен через:
     ```kotlin
     modifier = Modifier
         .fillMaxWidth()
         .navigationBarsPadding()
         .padding(bottom = 16.dp, top = 8.dp)
     ```
   - Кнопки `OutlinedButton` с минимальной высотой `48.dp`, скругление `12.dp`, текст и иконки `tokens.textPrimary` / `tokens.textSecondary`.
   - Полное исключение обрезки экранными жестами или панелью навигации Android.

5. **Диалог предпросмотра LRC (`LrcPreviewDialog`)**:
   - Все строки в списке предпросмотра форматируются с таймкодами `[MM:SS.xx]` (в `GolosTextFontFamily` с `tabularFigures`), текст строки в `LiterataFontFamily`.
   - Кнопки тонкой доводки `[ −0,1 с ]` и `[ +0,1 с ]` в палитре `tokens.surfaceElevated`.
   - Кнопка «Сохранить» в `tokens.accentPrimary`.

---

## 4. Критерии приёмки (DoD)
1. В `LrcTapSyncStudioDialog.kt` нет ни одного вызова `AppColors.*`, градиентов `Brush.*Gradient`, капса и эмодзи.
2. Все строки лирики отображаются гарнитурой `LiterataFontFamily` с `toSentenceCase()`.
3. Все контроллеры, таймкоды и подписи отображаются гарнитурой `GolosTextFontFamily` (для цифр включен `tnum`).
4. Нижние кнопки «Отменить шаг» и «Сбросить всё» имеют надежный отступ `navigationBarsPadding().padding(bottom = 16.dp)` и четко видны на реальном устройстве.
5. Проект собирается без ошибок: `./gradlew.bat :app:assembleDebug`.
6. Свежий APK устанавливается на физический телефон по ADB: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
7. Снят подтверждающий скриншот через `adb exec-out screencap -p` с экрана Tap-to-Sync Studio.
