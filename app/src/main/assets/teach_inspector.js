(function() {
    if (window.__teachInspectorInjected) return;
    window.__teachInspectorInjected = true;
    window.__teachModeEnabled = false; // Default: Surfing mode

    // State for inclusion (parents) and exclusion (stripped children)
    window.__selectedParents = [];     // Array of { el, origOutline, origShadow }
    window.__strippedChildren = [];    // Array of { el, origOutline, origOpacity, origTextDec }

    // Scroll guard & debounce state
    var startTouchX = 0;
    var startTouchY = 0;
    var isScrollingGesture = false;
    var lastInspectTimestamp = 0;
    var SCROLL_THRESHOLD_PX = 8;
    var DEBOUNCE_DELAY_MS = 300;

    window.setTeachMode = function(enabled) {
        window.__teachModeEnabled = !!enabled;
    };

    window.setTouchSlop = function(slopPx) {
        if (typeof slopPx === "number" && slopPx > 0) {
            SCROLL_THRESHOLD_PX = slopPx;
        }
    };

    window.clearTeachSelection = function() {
        for (var i = 0; i < window.__selectedParents.length; i++) {
            var p = window.__selectedParents[i];
            p.el.style.outline = p.origOutline;
            p.el.style.border = p.origBorder || "";
            p.el.style.boxShadow = p.origShadow;
            p.el.style.backgroundColor = p.origBg || "";
        }
        window.__selectedParents = [];

        for (var j = 0; j < window.__strippedChildren.length; j++) {
            var c = window.__strippedChildren[j];
            c.el.style.outline = c.origOutline;
            c.el.style.border = c.origBorder || "";
            c.el.style.opacity = c.origOpacity;
            c.el.style.textDecoration = c.origTextDec;
            c.el.style.backgroundImage = c.origBgImg || "";
            c.el.style.backgroundColor = c.origBg || "";
        }
        window.__strippedChildren = [];

        if (window.TeachBridge && typeof window.TeachBridge.onSelectionCleared === "function") {
            window.TeachBridge.onSelectionCleared();
        }
    };

    function synthesizeSelector(el) {
        if (!el || el === document.body || el === document.documentElement) return "body";

        // 1. Semantic attributes
        var itemprop = el.getAttribute("itemprop");
        if (itemprop) return '[itemprop="' + itemprop + '"]';

        // 2. ID check (ignore dynamic auto-generated hashes/ads)
        if (el.id && !/\d{4,}/.test(el.id) && !/^ad/i.test(el.id)) {
            return "#" + el.id;
        }

        // 3. Class names (ignore utility/layout classes)
        if (el.className && typeof el.className === "string") {
            var classes = el.className.trim().split(/\s+/).filter(function(c) {
                return c.length > 2 && !/^(ad|col-|row|container|clearfix|_)/i.test(c);
            });
            if (classes.length > 0) {
                return el.tagName.toLowerCase() + "." + classes.slice(0, 2).join(".");
            }
        }

        // 4. Hierarchical parent selector
        var parent = el.parentElement;
        if (parent && parent !== document.body) {
            var parentSel = synthesizeSelector(parent);
            if (parentSel !== "body") {
                return parentSel + " > " + el.tagName.toLowerCase();
            }
        }

        return el.tagName.toLowerCase();
    }

    function isInsideSelectedParent(target) {
        for (var i = 0; i < window.__selectedParents.length; i++) {
            var p = window.__selectedParents[i].el;
            if (p === target || p.contains(target)) {
                return p;
            }
        }
        return null;
    }

    function extractCleanText(rootEl, strippedElements) {
        if (!rootEl) return "";

        // Strategy 1: Read innerText directly from the live attached DOM element.
        // In Chromium/Blink, live innerText evaluates CSS layout and preserves exact newlines
        // from <br>, <p>, <div>, <pre>, and whitespace around rendered blocks.
        var liveText = "";
        try {
            var hiddenNodes = [];
            for (var s = 0; s < (strippedElements || []).length; s++) {
                var sNode = strippedElements[s].el;
                if (sNode && sNode.style) {
                    hiddenNodes.push({ el: sNode, prevDisplay: sNode.style.display });
                    sNode.style.display = "none";
                }
            }
            if (rootEl.innerText && rootEl.innerText.trim().length > 0) {
                liveText = rootEl.innerText.trim();
            }
            // Restore visibility of stripped elements
            for (var h = 0; h < hiddenNodes.length; h++) {
                hiddenNodes[h].el.style.display = hiddenNodes[h].prevDisplay;
            }
        } catch (e) {}

        if (liveText.length > 0 && liveText.indexOf("\n") !== -1) {
            // Live DOM provided proper newlines! Normalize excessive blank lines and unstick glued chords.
            return liveText
                .replace(/\r\n/g, "\n")
                .replace(/\n\s*\n\s*\n+/g, "\n\n")
                .replace(/\b([A-H][#b]?(?:m|maj|min|dim|aug|sus|add|\d)*)([А-Яа-яA-Za-z])/g, "$1 $2");
        }

        // Strategy 2: Fallback clone processing with explicit <br> -> \n, block breaks, and chord spacing
        var clone = rootEl.cloneNode(true);

        // Remove scripts, styles, iframes, noscript, and svg
        var autoRemoves = clone.querySelectorAll("script, style, iframe, noscript, svg");
        for (var i = 0; i < autoRemoves.length; i++) {
            autoRemoves[i].remove();
        }

        // Remove user-designated stripped elements
        for (var j = 0; j < (strippedElements || []).length; j++) {
            var strippedNode = strippedElements[j].el;
            var selector = synthesizeSelector(strippedNode);
            try {
                var foundInClone = clone.querySelectorAll(selector);
                for (var k = 0; k < foundInClone.length; k++) {
                    foundInClone[k].remove();
                }
            } catch (e) {}
        }

        // Replace <br> and <br/> with newline text nodes
        var brs = clone.querySelectorAll("br");
        for (var b = 0; b < brs.length; b++) {
            brs[b].replaceWith(document.createTextNode("\n"));
        }

        // Add newlines around block-level elements so they don't concatenate into a single line
        var blocks = clone.querySelectorAll("p, div, li, tr, pre, blockquote, h1, h2, h3, h4, h5, h6");
        for (var bl = 0; bl < blocks.length; bl++) {
            blocks[bl].prepend(document.createTextNode("\n"));
            blocks[bl].append(document.createTextNode("\n"));
        }

        // Ensure spaces around inline chord tags (span, b, strong, a, etc.) so chords don't glue to words
        var inlines = clone.querySelectorAll("span, b, strong, a, i, em");
        for (var inl = 0; inl < inlines.length; inl++) {
            var el = inlines[inl];
            if (el.nextSibling && el.nextSibling.nodeType === Node.TEXT_NODE) {
                if (!/^\s/.test(el.nextSibling.textContent) && !/\s$/.test(el.textContent)) {
                    el.after(document.createTextNode(" "));
                }
            }
        }

        var text = (clone.innerText || clone.textContent || "").trim();
        text = text
            .replace(/\r\n/g, "\n")
            .replace(/\n\s*\n\s*\n+/g, "\n\n")
            .replace(/\b([A-H][#b]?(?:m|maj|min|dim|aug|sus|add|\d)*)([А-Яа-яA-Za-z])/g, "$1 $2");
        return text;
    }

    function notifySelectionChange() {
        if (window.__selectedParents.length === 0) {
            if (window.TeachBridge && typeof window.TeachBridge.onSelectionCleared === "function") {
                window.TeachBridge.onSelectionCleared();
            }
            return;
        }

        // Order parents by document position
        window.__selectedParents.sort(function(a, b) {
            var pos = a.el.compareDocumentPosition(b.el);
            if (pos & Node.DOCUMENT_POSITION_FOLLOWING) return -1;
            if (pos & Node.DOCUMENT_POSITION_PRECEDING) return 1;
            return 0;
        });

        var texts = [];
        var parentSelectors = [];
        var totalLines = 0;

        for (var i = 0; i < window.__selectedParents.length; i++) {
            var pNode = window.__selectedParents[i].el;
            var cleanText = extractCleanText(pNode, window.__strippedChildren);
            if (cleanText.length > 0) {
                texts.push(cleanText);
                var lines = cleanText.split("\n").filter(function(l) { return l.trim().length > 0; }).length;
                totalLines += lines;
            }
            var sel = synthesizeSelector(pNode);
            if (parentSelectors.indexOf(sel) === -1) {
                parentSelectors.push(sel);
            }
        }

        var stripSelectors = [];
        for (var j = 0; j < window.__strippedChildren.length; j++) {
            var sSel = synthesizeSelector(window.__strippedChildren[j].el);
            if (stripSelectors.indexOf(sSel) === -1) {
                stripSelectors.push(sSel);
            }
        }

        var combinedParentSelector = parentSelectors.join(", ");
        var combinedText = texts.join("\n\n");
        var stripJson = JSON.stringify(stripSelectors);

        if (window.TeachBridge && typeof window.TeachBridge.onElementSelected === "function") {
            window.TeachBridge.onElementSelected(
                combinedParentSelector,
                stripJson,
                combinedText,
                totalLines,
                window.location.hostname,
                window.__selectedParents.length,
                window.__strippedChildren.length
            );
        }
    }

    // Expand current selection to parent container ([▲ Родитель])
    window.expandTeachSelectionToParent = function() {
        if (window.__selectedParents.length === 0) return;
        var lastParent = window.__selectedParents[window.__selectedParents.length - 1];
        var parentEl = lastParent.el.parentElement;
        if (!parentEl || parentEl === document.body || parentEl === document.documentElement) return;

        // Restore current element styling
        lastParent.el.style.outline = lastParent.origOutline;
        lastParent.el.style.border = lastParent.origBorder || "";
        lastParent.el.style.boxShadow = lastParent.origShadow;
        lastParent.el.style.backgroundColor = lastParent.origBg || "";

        // Style the expanded parent (solid purple 2px #8B5CF6)
        var origOutline = parentEl.style.outline || "";
        var origBorder = parentEl.style.border || "";
        var origShadow = parentEl.style.boxShadow || "";
        var origBg = parentEl.style.backgroundColor || "";
        parentEl.style.outline = "2px solid #8B5CF6";
        parentEl.style.border = "2px solid #8B5CF6";
        parentEl.style.backgroundColor = "rgba(139, 92, 246, 0.1)";
        parentEl.style.boxShadow = "0 0 12px rgba(139, 92, 246, 0.35)";

        lastParent.el = parentEl;
        lastParent.origOutline = origOutline;
        lastParent.origBorder = origBorder;
        lastParent.origShadow = origShadow;
        lastParent.origBg = origBg;

        notifySelectionChange();
    };

    function handleElementInteraction(target) {
        if (!target || target === document.body || target === document.documentElement) return;

        var containingParent = isInsideSelectedParent(target);

        if (containingParent && containingParent !== target) {
            // Click inside an already selected parent -> toggle as stripped child
            var strippedIdx = -1;
            for (var i = 0; i < window.__strippedChildren.length; i++) {
                if (window.__strippedChildren[i].el === target) {
                    strippedIdx = i;
                    break;
                }
            }

            if (strippedIdx !== -1) {
                // Restore child
                var restored = window.__strippedChildren.splice(strippedIdx, 1)[0];
                restored.el.style.outline = restored.origOutline;
                restored.el.style.border = restored.origBorder || "";
                restored.el.style.opacity = restored.origOpacity;
                restored.el.style.textDecoration = restored.origTextDec;
                restored.el.style.backgroundImage = restored.origBgImg || "";
                restored.el.style.backgroundColor = restored.origBg || "";
            } else {
                // Strip child (Dashed border + diagonal hatched fill + line-through for color-blindness accessibility)
                var origOutline = target.style.outline || "";
                var origBorder = target.style.border || "";
                var origOpacity = target.style.opacity || "";
                var origTextDec = target.style.textDecoration || "";
                var origBgImg = target.style.backgroundImage || "";
                var origBg = target.style.backgroundColor || "";

                target.style.outline = "2px dashed #EF4444";
                target.style.border = "2px dashed #EF4444";
                target.style.backgroundColor = "rgba(239, 68, 68, 0.1)";
                target.style.backgroundImage = "repeating-linear-gradient(45deg, rgba(239, 68, 68, 0.15), rgba(239, 68, 68, 0.15) 6px, transparent 6px, transparent 12px)";
                target.style.opacity = "0.7";
                target.style.textDecoration = "line-through";

                window.__strippedChildren.push({
                    el: target,
                    origOutline: origOutline,
                    origBorder: origBorder,
                    origOpacity: origOpacity,
                    origTextDec: origTextDec,
                    origBgImg: origBgImg,
                    origBg: origBg
                });
            }
        } else {
            // Click outside or direct on parent -> toggle parent container
            // Avoid targeting body or html
            while (target && target.parentElement && target.parentElement !== document.body && /^(SPAN|B|I|EM|STRONG|FONT|A)$/i.test(target.tagName) && target.parentElement.children.length === 1) {
                target = target.parentElement;
            }

            var parentIdx = -1;
            for (var j = 0; j < window.__selectedParents.length; j++) {
                if (window.__selectedParents[j].el === target) {
                    parentIdx = j;
                    break;
                }
            }

            if (parentIdx !== -1) {
                // Deselect parent
                var removedParent = window.__selectedParents.splice(parentIdx, 1)[0];
                removedParent.el.style.outline = removedParent.origOutline;
                removedParent.el.style.border = removedParent.origBorder || "";
                removedParent.el.style.boxShadow = removedParent.origShadow;
                removedParent.el.style.backgroundColor = removedParent.origBg || "";

                // Clean up any stripped children that belonged to this parent
                for (var k = window.__strippedChildren.length - 1; k >= 0; k--) {
                    if (removedParent.el.contains(window.__strippedChildren[k].el)) {
                        var c = window.__strippedChildren.splice(k, 1)[0];
                        c.el.style.outline = c.origOutline;
                        c.el.style.border = c.origBorder || "";
                        c.el.style.opacity = c.origOpacity;
                        c.el.style.textDecoration = c.origTextDec;
                        c.el.style.backgroundImage = c.origBgImg || "";
                        c.el.style.backgroundColor = c.origBg || "";
                    }
                }
            } else {
                // Select new parent container (solid purple 2px #8B5CF6)
                var pOrigOutline = target.style.outline || "";
                var pOrigBorder = target.style.border || "";
                var pOrigShadow = target.style.boxShadow || "";
                var pOrigBg = target.style.backgroundColor || "";
                target.style.outline = "2px solid #8B5CF6";
                target.style.border = "2px solid #8B5CF6";
                target.style.backgroundColor = "rgba(139, 92, 246, 0.1)";
                target.style.boxShadow = "0 0 12px rgba(139, 92, 246, 0.35)";

                window.__selectedParents.push({
                    el: target,
                    origOutline: pOrigOutline,
                    origBorder: pOrigBorder,
                    origShadow: pOrigShadow,
                    origBg: pOrigBg
                });
            }
        }

        notifySelectionChange();
    }

    // Touch event listeners for robust gesture arbitration (Scroll Guard)
    window.addEventListener("touchstart", function(e) {
        if (!window.__teachModeEnabled) return;
        var touch = e.touches[0];
        if (touch) {
            startTouchX = touch.clientX;
            startTouchY = touch.clientY;
            isScrollingGesture = false;
        }
    }, { capture: true, passive: true });

    window.addEventListener("touchmove", function(e) {
        if (!window.__teachModeEnabled) return;
        var touch = e.touches[0];
        if (touch) {
            var dx = touch.clientX - startTouchX;
            var dy = touch.clientY - startTouchY;
            if (Math.hypot(dx, dy) > SCROLL_THRESHOLD_PX) {
                isScrollingGesture = true;
            }
        }
    }, { capture: true, passive: true });

    window.addEventListener("touchend", function(e) {
        if (!window.__teachModeEnabled) return;
        if (isScrollingGesture) {
            // User scrolled, do not trigger inspector!
            return;
        }

        var now = Date.now();
        if (now - lastInspectTimestamp < DEBOUNCE_DELAY_MS) {
            return;
        }
        lastInspectTimestamp = now;

        var touch = e.changedTouches ? e.changedTouches[0] : null;
        if (!touch) return;

        var target = document.elementFromPoint(touch.clientX, touch.clientY);
        if (target) {
            e.preventDefault();
            e.stopPropagation();
            handleElementInteraction(target);
        }
    }, { capture: true, passive: false });

    // Block native click events in inspector mode to prevent link navigation and duplicate calls
    document.addEventListener("click", function(e) {
        if (!window.__teachModeEnabled) {
            return; // Surfing mode: allow normal link clicks
        }
        e.preventDefault();
        e.stopPropagation();

        var now = Date.now();
        if (now - lastInspectTimestamp < DEBOUNCE_DELAY_MS) {
            return;
        }
        lastInspectTimestamp = now;

        handleElementInteraction(e.target);
    }, true);

})();
