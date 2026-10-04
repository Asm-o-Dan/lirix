# Задача TASK-TEACH-02: Двухрежимная навигация, мульти-выбор блоков и составные селекторы в TeachMode

- **ID задачи:** `TASK-TEACH-02`
- **Роль исполнителя:** Кодер
- **Зона:** `media-ui` / `lyrics-engine`
- **Файлы:**
  1. `app/src/main/assets/teach_inspector.js` (поддержка флага активности инспектора, toggle мульти-выбора, объединение текста по порядку DOM, генерация составного селектора)
  2. `app/src/main/java/com/eventengine/app/ui/TeachModeScreen.kt` (тумблер режимов «🌐 Серфинг / 🎯 Инспектор», кнопка «Назад» WebView, отображение количества выбранных блоков и кнопка сброса)
  3. `app/src/main/java/com/eventengine/app/feature/lyrics/CustomRuleLyricsProvider.kt` (поддержка составных селекторов через запятую `sel1, sel2` в `extractContainerBySelector`)
  4. `app/src/test/java/com/eventengine/app/TeachModeMultiSelectTest.kt` (unit-тесты извлечения составных селекторов и структуры правила)
- **Спека:** [.sdd/architecture_lyr_community.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/architecture_lyr_community.md) (Раздел 4), [.sdd/intake/LYR-COMMUNITY.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/intake/LYR-COMMUNITY.md) (FR-3)
- **Приоритет:** CRITICAL (Дефект UX: невозможность навигации по ссылкам и невозможность выбора нескольких куплетов)

---

## 1. Механика дефекта и цели

Пользователь выявил две критические проблемы в текущей реализации TeachMode:
1. **Блокировка серфинга:** при открытии поиска Google или сайта клики по любым ссылкам блокируются скриптом `teach_inspector.js` (`e.preventDefault(); e.stopPropagation();`). Пользователь не может перейти из выдачи поиска на страницу песни или перемещаться по сайту.
2. **Монопольный одиночный выбор:** клик по новому блоку стирает предыдущее выделение (`lastElement = el`). Если песня на сайте разбита на несколько независимых тегов (например, `<p class="verse">` для каждого куплета или отдельные блоки припева), пользователь не может выделить всю песню целиком.

### Цели доработки:
1. Реализовать **двухрежимную модель**:
   - Режим 🌐 **«Серфинг»** (по умолчанию при загрузке): клики работают стандартно, работают ссылки, формы и переход по сайтам.
   - Режим 🎯 **«Инспектор»**: клики перехватываются, подсвечивают элементы и собирают селекторы.
2. Добавить навигационную кнопку **«Назад»** для WebView (`webView.canGoBack() / goBack()`).
3. Поддержать **мульти-выбор блоков (Multi-selection)**:
   - Добавление и снятие выделения при повторном клике на блок.
   - Сортировка блоков в порядке документа (`compareDocumentPosition`).
   - Объединение текстов выбранных блоков через двойной перенос `\n\n`.
   - Синтез составного CSS-селектора (через запятую `sel1, sel2` или общий класс).
4. Научить `CustomRuleLyricsProvider` извлекать составные селекторы через запятую.

---

## 2. Спецификация изменений

### 2.1 Изменения в `assets/teach_inspector.js`

1. **Глобальный флаг активности и функция переключения:**
   ```javascript
   window.__teachModeEnabled = false; // По умолчанию - режим серфинга
   window.__selectedElements = [];     // Массив выбранных DOM-элементов
   ```
2. **Экспортируемые функции управления для нативного вызова через `evaluateJavascript`:**
   ```javascript
   window.setTeachMode = function(enabled) {
       window.__teachModeEnabled = !!enabled;
       if (!window.__teachModeEnabled) {
           // При выключении инспектора не сбрасываем выделение, но прекращаем перехват тапов
       }
   };

   window.clearTeachSelection = function() {
       for (var i = 0; i < window.__selectedElements.length; i++) {
           var item = window.__selectedElements[i];
           item.el.style.outline = item.origOutline;
           item.el.style.boxShadow = item.origShadow;
       }
       window.__selectedElements = [];
       if (window.TeachBridge) {
           window.TeachBridge.onSelectionCleared();
       }
   };
   ```
3. **Логика перехвата тапа и мульти-выбора:**
   ```javascript
   document.addEventListener("click", function(e) {
       if (!window.__teachModeEnabled) {
           return; // Режим серфинга: разрешаем стандартное поведение ссылок
       }

       e.preventDefault();
       e.stopPropagation();

       var target = e.target;
       while (target && target !== document.body && /^(SPAN|B|I|EM|STRONG|FONT|A)$/i.test(target.tagName)) {
           target = target.parentElement;
       }
       if (!target || target === document.body || target === document.documentElement) return;

       // Проверяем, был ли элемент уже выбран (toggle)
       var existingIndex = -1;
       for (var i = 0; i < window.__selectedElements.length; i++) {
           if (window.__selectedElements[i].el === target) {
               existingIndex = i;
               break;
           }
       }

       if (existingIndex !== -1) {
           // Снятие выделения
           var removed = window.__selectedElements.splice(existingIndex, 1)[0];
           removed.el.style.outline = removed.origOutline;
           removed.el.style.boxShadow = removed.origShadow;
       } else {
           // Добавление в выборку
           var origOutline = target.style.outline || "";
           var origShadow = target.style.boxShadow || "";
           target.style.outline = "3px solid #A855F7";
           target.style.boxShadow = "0 0 16px rgba(168, 85, 247, 0.7)";
           window.__selectedElements.push({
               el: target,
               origOutline: origOutline,
               origShadow: origShadow
           });
       }

       notifySelectionChange();
   }, true);
   ```
4. **Сортировка по DOM и синтез объединенного селектора:**
   ```javascript
   function notifySelectionChange() {
       if (window.__selectedElements.length === 0) {
           if (window.TeachBridge) window.TeachBridge.onSelectionCleared();
           return;
       }

       // Сортировка элементов по порядку их следования в DOM
       window.__selectedElements.sort(function(a, b) {
           var pos = a.el.compareDocumentPosition(b.el);
           if (pos & Node.DOCUMENT_POSITION_FOLLOWING) return -1;
           if (pos & Node.DOCUMENT_POSITION_PRECEDING) return 1;
           return 0;
       });

       var texts = [];
       var selectors = [];
       var totalLines = 0;

       for (var i = 0; i < window.__selectedElements.length; i++) {
           var node = window.__selectedElements[i].el;
           var txt = (node.innerText || node.textContent || "").trim();
           if (txt.length > 0) {
               texts.push(txt);
               var lines = txt.split("\n").filter(function(l) { return l.trim().length > 0; }).length;
               totalLines += lines;
           }
           var sel = synthesizeSelector(node);
           if (selectors.indexOf(sel) === -1) {
               selectors.push(sel);
           }
       }

       // Объединяем селекторы через запятую (CSS group selector)
       var combinedSelector = selectors.join(", ");
       var combinedText = texts.join("\n\n");

       if (window.TeachBridge) {
           window.TeachBridge.onElementSelected(
               combinedSelector,
               combinedText,
               totalLines,
               window.location.hostname,
               window.__selectedElements.length
           );
       }
   }
   ```

---

### 2.2 Изменения в `TeachModeScreen.kt`

1. **Расширение `TeachJsBridge`:**
   ```kotlin
   class TeachJsBridge(
       private val onSelected: (selector: String, text: String, linesCount: Int, domain: String, blocksCount: Int) -> Unit,
       private val onCleared: () -> Unit
   ) {
       @JavascriptInterface
       fun onElementSelected(selector: String, text: String, linesCount: Int, domain: String, blocksCount: Int) {
           Handler(Looper.getMainLooper()).post {
               onSelected(selector, text, linesCount, domain, blocksCount)
           }
       }

       @JavascriptInterface
       fun onSelectionCleared() {
           Handler(Looper.getMainLooper()).post {
               onCleared()
           }
       }
   }
   ```

2. **Состояния экрана:**
   ```kotlin
   var isInspectorMode by rememberSaveable { mutableStateOf(false) } // false = Серфинг, true = Инспектор
   var canGoBack by remember { mutableStateOf(false) }
   var selectedBlocksCount by remember { mutableIntStateOf(0) }
   var webViewRef by remember { mutableStateOf<WebView?>(null) }
   ```

3. **Обновление TopBar:**
   - **Кнопка «Назад»:**
     ```kotlin
     IconButton(
         onClick = { webViewRef?.goBack() },
         enabled = canGoBack
     ) {
         Icon(
             imageVector = Icons.AutoMirrored.Filled.ArrowBack,
             contentDescription = "Назад",
             tint = if (canGoBack) AppColors.CyberCyan else AppColors.TextTertiary
         )
     }
     ```
   - **Тумблер режимов `[🌐 Серфинг / 🎯 Инспектор]`:**
     ```kotlin
     Row(
         modifier = Modifier
             .clip(RoundedCornerShape(8.dp))
             .background(AppColors.SurfaceLevel2)
             .padding(2.dp)
     ) {
         // Кнопка режима Серфинга
         Box(
             modifier = Modifier
                 .clip(RoundedCornerShape(6.dp))
                 .background(if (!isInspectorMode) AppColors.SurfaceLevel3 else Color.Transparent)
                 .clickable {
                     isInspectorMode = false
                     webViewRef?.evaluateJavascript("window.setTeachMode(false);", null)
                 }
                 .padding(horizontal = 8.dp, vertical = 4.dp)
         ) {
             Text(
                 "🌐 Серфинг",
                 style = MaterialTheme.typography.labelSmall.copy(
                     fontWeight = if (!isInspectorMode) FontWeight.Bold else FontWeight.Normal
                 ),
                 color = if (!isInspectorMode) AppColors.CyberCyan else AppColors.TextSecondary
             )
         }

         // Кнопка режима Инспектора
         Box(
             modifier = Modifier
                 .clip(RoundedCornerShape(6.dp))
                 .background(if (isInspectorMode) AppColors.HyperViolet else Color.Transparent)
                 .clickable {
                     isInspectorMode = true
                     webViewRef?.evaluateJavascript("window.setTeachMode(true);", null)
                 }
                 .padding(horizontal = 8.dp, vertical = 4.dp)
         ) {
             Text(
                 "🎯 Инспектор",
                 style = MaterialTheme.typography.labelSmall.copy(
                     fontWeight = if (isInspectorMode) FontWeight.Bold else FontWeight.Normal
                 ),
                 color = if (isInspectorMode) AppColors.AmoledBlack else AppColors.TextSecondary
             )
         }
     }
     ```

4. **Обновление нижней панели при наличии выделения (`selectedBlocksCount > 0`):**
   - Бейдж: `Text("${selectedBlocksCount} бл. • $selectedLinesCount строк", color = AppColors.TextPrimary)`.
   - Кнопка сброса: `TextButton(onClick = { webViewRef?.evaluateJavascript("window.clearTeachSelection();", null) }) { Text("Сбросить", color = AppColors.TextTertiary) }`.
   - Кнопка `[Привязать текст]` и `[Сохранить правило для сайта]`.

---

### 2.3 Поддержка составных селекторов в `CustomRuleLyricsProvider.kt`

В методе `extractContainerBySelector(html: String, selector: String): String?`:

```kotlin
fun extractContainerBySelector(html: String, selector: String): String? {
    val cleanSel = selector.trim()
    if (cleanSel.isBlank()) return null

    // Поддержка составных селекторов через запятую (например: "div.verse, div.chorus")
    if (cleanSel.contains(",")) {
        val subSelectors = cleanSel.split(',').map { it.trim() }.filter { it.isNotBlank() }
        val extractedBlocks = mutableListOf<String>()
        for (subSel in subSelectors) {
            val block = extractSingleContainer(html, subSel)
            if (!block.isNullOrBlank()) {
                extractedBlocks.add(block)
            }
        }
        return if (extractedBlocks.isNotEmpty()) {
            extractedBlocks.joinToString("\n\n")
        } else {
            null
        }
    }

    return extractSingleContainer(html, cleanSel)
}

private fun extractSingleContainer(html: String, subSel: String): String? {
    if (subSel.startsWith("#")) {
        val id = subSel.removePrefix("#")
        return FallbackLyricsScraper.extractBalancedTags(html, "id=\"$id\"").firstOrNull()
            ?: FallbackLyricsScraper.extractBalancedTags(html, "id='$id'").firstOrNull()
    }
    if (subSel.startsWith(".")) {
        val cls = subSel.removePrefix(".")
        return FallbackLyricsScraper.extractBalancedTags(html, "class=\"$cls\"").firstOrNull()
            ?: FallbackLyricsScraper.extractBalancedTags(html, "class='$cls'").firstOrNull()
            ?: FallbackLyricsScraper.extractBalancedTags(html, cls).firstOrNull()
    }
    if (subSel.contains("itemprop=")) {
        val marker = subSel.replace("\"", "").replace("'", "")
        return FallbackLyricsScraper.extractBalancedTags(html, marker).firstOrNull()
    }
    if (subSel.contains(".")) {
        val parts = subSel.split('.')
        val tag = parts[0].ifBlank { "div" }
        val cls = parts[1]
        return FallbackLyricsScraper.extractBalancedTags(html, cls, tag).firstOrNull()
    }
    if (subSel.contains("#")) {
        val parts = subSel.split('#')
        val tag = parts[0].ifBlank { "div" }
        val id = parts[1]
        return FallbackLyricsScraper.extractBalancedTags(html, id, tag).firstOrNull()
    }
    return FallbackLyricsScraper.extractBalancedTags(html, subSel).firstOrNull()
}
```

---

## 3. Критерии приемки (DoD)

1. **Unit-тесты в `TeachModeMultiSelectTest.kt`:**
   - `test_comma_separated_selector_extracts_all_blocks`: HTML с тремя блоками `<div class="verse1">...</div>` и `<div class="verse2">...</div>` при селекторе `div.verse1, div.verse2` извлекает оба куплета с разделением `\n\n`.
   - `test_single_selector_remains_backward_compatible`: стандартные селекторы `#lyrics`, `.song-text` продолжают работать без изменений.
2. **Интерактивные сценарии на устройстве (Poco M7):**
   - При открытии TeachMode активен режим **🌐 Серфинг**: пользователь нажимает на ссылки в Google, страницы открываются и происходит свободный переход.
   - Кнопка «Назад» в TopBar становится активной после перехода и возвращает на предыдущую страницу.
   - Пользователь нажимает на тумблер **🎯 Инспектор**: клики перестают переходить по ссылкам, а подсвечивают блоки фиолетовой рамкой (`#A855F7`).
   - Пользователь тапает по двум разным блокам (куплет 1 и куплет 2): оба блока остаются подсвеченными, в нижней панели отображается `«2 бл. • X строк»`, а селектор объединяется через запятую.
   - Повторный клик по выделенному блоку снимает с него подсветку и обновляет счетчик.
   - Нажатие «Сохранить правило для сайта» записывает правило с составным селектором в Room.
3. Проект успешно собирается и проходит тесты: `.\gradlew.bat test`.
