(function() {
    if (window.__teachInspectorInjected) return;
    window.__teachInspectorInjected = true;
    window.__teachModeEnabled = false; // Default: Surfing mode
    window.__selectedElements = [];    // Array of selected elements

    window.setTeachMode = function(enabled) {
        window.__teachModeEnabled = !!enabled;
    };

    window.clearTeachSelection = function() {
        for (var i = 0; i < window.__selectedElements.length; i++) {
            var item = window.__selectedElements[i];
            item.el.style.outline = item.origOutline;
            item.el.style.boxShadow = item.origShadow;
        }
        window.__selectedElements = [];
        if (window.TeachBridge && typeof window.TeachBridge.onSelectionCleared === "function") {
            window.TeachBridge.onSelectionCleared();
        }
    };

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

    function notifySelectionChange() {
        if (window.__selectedElements.length === 0) {
            if (window.TeachBridge && typeof window.TeachBridge.onSelectionCleared === "function") {
                window.TeachBridge.onSelectionCleared();
            }
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

        var combinedSelector = selectors.join(", ");
        var combinedText = texts.join("\n\n");

        if (window.TeachBridge && typeof window.TeachBridge.onElementSelected === "function") {
            window.TeachBridge.onElementSelected(
                combinedSelector,
                combinedText,
                totalLines,
                window.location.hostname,
                window.__selectedElements.length
            );
        }
    }

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
})();
