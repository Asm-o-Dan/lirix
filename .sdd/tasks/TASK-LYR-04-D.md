# Задача TASK-LYR-04-D: UI экран TeachModeScreen на Compose и WebView инспектор правил

- **ID задачи:** `TASK-LYR-04-D`
- **Роль исполнителя:** Кодер
- **Зона:** `media-ui`
- **Файлы:**
  1. `app/src/main/assets/teach_inspector.js` (инспекция тапов, неоновая подсветка `#A855F7`, синтез CSS-селектора)
  2. `app/src/main/java/com/eventengine/app/ui/TeachModeScreen.kt` (экран обучения сайту с WebView, превью текста и сохранением правила)
  3. `app/src/main/java/com/eventengine/app/ui/AppScaffold.kt` (вызов TeachModeScreen из NowPlaying «Найти в интернете» и из Share URL)
  4. `app/src/test/java/com/eventengine/app/TeachModeSelectorTest.kt` (тесты генерации селекторов и структуры JSON-манифеста)
- **Спека:** [.sdd/architecture_lyr_community.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/architecture_lyr_community.md) (Раздел 4), [.sdd/intake/LYR-COMMUNITY.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/intake/LYR-COMMUNITY.md) (FR-3)
- **Приоритет:** HIGH (Пользовательский интерфейс создания правил-парсеров)

---

## 1. Назначение и контекст

Если встроенные провайдеры не нашли текст или пользователь нашел песню на редком сайте, приложение должно предоставить интерактивный встроенный браузер (Teach Mode):
1. Пользователь открывает экран обучения (по кнопке «Найти в интернете» или через Android Share URL).
2. Браузер загружает страницу. При тапе на блок с текстом песни блок подсвечивается неоновой фиолетовой рамкой (`#A855F7`).
3. JavaScript-инспектор анализирует DOM-элемент, синтезирует устойчивый CSS-селектор (`#id`, `.class` или `itemprop`), считает количество строк текста и передает данные через `@JavascriptInterface` в нативный Compose.
4. В нижней плашке отображаются превью текста, селектор и две кнопки:
   - `[Привязать к треку]` — мгновенно сохраняет текст для текущего трека;
   - `[Сохранить правило для сайта]` — генерирует декларативный JSON-манифест правила и сохраняет его в таблицу `custom_lyrics_rules`.

---

## 2. Спецификация изменений

### 2.1 Скрипт-инспектор `assets/teach_inspector.js`

Создать файл в `app/src/main/assets/teach_inspector.js`:

```javascript
(function() {
    if (window.__teachInspectorInjected) return;
    window.__teachInspectorInjected = true;
    window.__teachModeEnabled = true;

    var lastElement = null;
    var originalOutline = "";
    var originalBoxShadow = "";

    function highlight(el) {
        if (lastElement && lastElement !== el) {
            lastElement.style.outline = originalOutline;
            lastElement.style.boxShadow = originalBoxShadow;
        }
        lastElement = el;
        originalOutline = el.style.outline || "";
        originalBoxShadow = el.style.boxShadow || "";
        el.style.outline = "3px solid #A855F7";
        el.style.boxShadow = "0 0 16px rgba(168, 85, 247, 0.6)";
    }

    function synthesizeSelector(el) {
        if (!el || el === document.body || el === document.documentElement) return "body";
        
        // 1. Проверка семантических атрибутов
        var itemprop = el.getAttribute("itemprop");
        if (itemprop) return '[itemprop="' + itemprop + '"]';

        // 2. Проверка ID (без динамических хэшей)
        if (el.id && !/\d{4,}/.test(el.id) && !/^ad/i.test(el.id)) {
            return "#" + el.id;
        }

        // 3. Проверка классов (исключая служебные/рекламные)
        if (el.className && typeof el.className === "string") {
            var classes = el.className.trim().split(/\s+/).filter(function(c) {
                return c.length > 2 && !/^(ad|col-|row|container|clearfix|_)/i.test(c);
            });
            if (classes.length > 0) {
                return el.tagName.toLowerCase() + "." + classes.slice(0, 2).join(".");
            }
        }

        // 4. Иерархический родительский селектор
        var parent = el.parentElement;
        if (parent && parent !== document.body) {
            var parentSel = synthesizeSelector(parent);
            if (parentSel !== "body") {
                return parentSel + " > " + el.tagName.toLowerCase();
            }
        }

        return el.tagName.toLowerCase();
    }

    document.addEventListener("click", function(e) {
        if (!window.__teachModeEnabled) return;
        
        // Игнорируем клики по ссылкам перехода, если цель — выбор текста
        e.preventDefault();
        e.stopPropagation();

        var target = e.target;
        // Если кликнули по вложенному span/b/i, поднимаемся к содержательному блоку div/p/pre/article
        while (target && target !== document.body && /^(SPAN|B|I|EM|STRONG|FONT|A)$/i.test(target.tagName)) {
            target = target.parentElement;
        }
        if (!target) target = e.target;

        highlight(target);

        var selector = synthesizeSelector(target);
        var rawText = (target.innerText || target.textContent || "").trim();
        var lines = rawText.split("\n").filter(function(line) { return line.trim().length > 0; }).length;

        if (window.TeachBridge && typeof window.TeachBridge.onElementSelected === "function") {
            window.TeachBridge.onElementSelected(selector, rawText, lines, window.location.hostname);
        }
    }, true);
})();
```

### 2.2 Экран `ui/TeachModeScreen.kt`

1. **Компонент и параметры:**
   ```kotlin
   @Composable
   fun TeachModeScreen(
       initialUrl: String?,
       targetTrack: TrackEntity?,
       onClose: () -> Unit,
       onRuleSaved: (CustomLyricsRuleEntity) -> Unit
   )
   ```
2. **Javascript Interface `TeachJsBridge`:**
   ```kotlin
   class TeachJsBridge(
       private val onSelected: (selector: String, text: String, linesCount: Int, domain: String) -> Unit
   ) {
       @JavascriptInterface
       fun onElementSelected(selector: String, text: String, linesCount: Int, domain: String) {
           Handler(Looper.getMainLooper()).post {
               onSelected(selector, text, linesCount, domain)
           }
       }
   }
   ```
3. **Безопасная конфигурация WebView:**
   ```kotlin
   AndroidView(
       factory = { ctx ->
           WebView(ctx).apply {
               settings.javaScriptEnabled = true
               settings.domStorageEnabled = true
               settings.allowFileAccess = false
               settings.allowContentAccess = false
               addJavascriptInterface(
                   TeachJsBridge { selector, text, lines, domain ->
                       selectedSelector = selector
                       selectedText = text
                       selectedLinesCount = lines
                       detectedDomain = domain
                   },
                   "TeachBridge"
               )
               webViewClient = object : WebViewClient() {
                   override fun onPageFinished(view: WebView?, url: String?) {
                       super.onPageFinished(view, url)
                       // Инъекция teach_inspector.js из assets
                       val script = context.assets.open("teach_inspector.js").bufferedReader().use { it.readText() }
                       view?.evaluateJavascript(script, null)
                   }
               }
               val startUrl = initialUrl?.takeIf { it.isNotBlank() }
                   ?: "https://www.google.com/search?q=" + URLEncoder.encode("${targetTrack?.artist} ${targetTrack?.title} текст песни", "UTF-8")
               loadUrl(startUrl)
           }
       },
       modifier = Modifier.fillMaxSize().weight(1f)
   )
   ```
4. **Нижняя панель действий при выборе элемента:**
   - Отображает бейдж селектора: `Surface(AppColors.SurfaceLevel2) { Text(selectedSelector, color = AppColors.CyberCyan) }`.
   - Индикатор строк: `Text("$selectedLinesCount строк текста", color = AppColors.TextSecondary)`.
   - Кнопка 1: `[Привязать текст к треку]` — вызывает `db.musicDao().updateLyrics(trackKey, selectedText, null)` и закрывает экран.
   - Кнопка 2: `[Сохранить правило для сайта]` — генерирует манифест:
     ```kotlin
     val ruleId = "rule_${detectedDomain.replace(".", "_")}_${System.currentTimeMillis() % 10000}"
     val ruleJson = JSONObject().apply {
         put("domain", detectedDomain)
         put("name", detectedDomain)
         put("content", JSONObject().apply {
             put("selector", selectedSelector)
             put("stripSelectors", org.json.JSONArray(listOf("script", "style", ".ads", "button")))
         })
     }.toString()

     val entity = CustomLyricsRuleEntity(
         id = ruleId,
         domain = detectedDomain,
         name = detectedDomain,
         ruleJson = ruleJson,
         isEnabled = true,
         priority = 100
     )
     db.lyricsDao().saveRule(entity)
     ```
   - Показ `Toast` или `Snackbar`: `«Правило для $detectedDomain сохранено и добавлено в цепочку поиска»`.

### 2.3 Интеграция в `AppScaffold.kt`

В `AppScaffold.kt` добавить состояние:
```kotlin
var activeTeachUrl by remember { mutableStateOf<String?>(null) }
var isTeachModeOpen by remember { mutableStateOf(false) }
```
- Открытие при клике на кнопку «🌐 Найти в интернете» на экране плеера:
  `activeTeachUrl = null; isTeachModeOpen = true`.
- Открытие при получении URL через Android Share (TASK-LYR-04-B):
  `activeTeachUrl = sharedUrl; isTeachModeOpen = true`.
- При `isTeachModeOpen == true` рендерится `TeachModeScreen` в качестве верхнего оверлея.

---

## 3. Критерии приемки (DoD)

1. **Unit-тесты в `TeachModeSelectorTest.kt`:**
   - `test_generated_rule_json_is_valid_and_parsable`: сгенерированный JSON правила корректно парсится через `CustomRuleLyricsProvider.parseConfig`.
   - `test_rule_entity_saved_with_correct_domain_and_priority`: сущность правила сохраняется в базу с `isEnabled = true` и `priority = 100`.
2. **Интерактивные сценарии на устройстве:**
   - При нажатии «Найти в интернете» в плеере открывается WebView с поисковой выдачей.
   - При тапе по блоку текста появляется фиолетовая подсветка и нижняя панель с селектором.
   - Нажатие «Сохранить правило для сайта» успешно записывает правило в Room и закрывает инспектор.
3. Проект компилируется и собирается: `.\gradlew.bat assembleDebug`.
