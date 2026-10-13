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

    window.clearTeachSelection = function() {
        for (var i = 0; i < window.__selectedParents.length; i++) {
            var p = window.__selectedParents[i];
            p.el.style.outline = p.origOutline;
            p.el.style.boxShadow = p.origShadow;
        }
        window.__selectedParents = [];

        for (var j = 0; j < window.__strippedChildren.length; j++) {
            var c = window.__strippedChildren[j];
            c.el.style.outline = c.origOutline;
            c.el.style.opacity = c.origOpacity;
            c.el.style.textDecoration = c.origTextDec;
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
        // Clone node to safely remove stripped children without mutating live DOM
        var clone = rootEl.cloneNode(true);

        // Remove scripts, styles, iframes, and ads
        var autoRemoves = clone.querySelectorAll("script, style, iframe, noscript, svg");
        for (var i = 0; i < autoRemoves.length; i++) {
            autoRemoves[i].remove();
        }

        // Remove all user-designated stripped elements
        for (var j = 0; j < strippedElements.length; j++) {
            var strippedNode = strippedElements[j].el;
            var selector = synthesizeSelector(strippedNode);
            try {
                var foundInClone = clone.querySelectorAll(selector);
                for (var k = 0; k < foundInClone.length; k++) {
                    foundInClone[k].remove();
                }
            } catch (e) {}
        }

        var text = (clone.innerText || clone.textContent || "").trim();
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
        lastParent.el.style.boxShadow = lastParent.origShadow;

        // Style the expanded parent
        var origOutline = parentEl.style.outline || "";
        var origShadow = parentEl.style.boxShadow || "";
        parentEl.style.outline = "3px solid #B388FF";
        parentEl.style.boxShadow = "0 0 16px rgba(179, 136, 255, 0.6)";

        lastParent.el = parentEl;
        lastParent.origOutline = origOutline;
        lastParent.origShadow = origShadow;

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
                restored.el.style.opacity = restored.origOpacity;
                restored.el.style.textDecoration = restored.origTextDec;
            } else {
                // Strip child
                var origOutline = target.style.outline || "";
                var origOpacity = target.style.opacity || "";
                var origTextDec = target.style.textDecoration || "";

                target.style.outline = "2px dashed #EF4444";
                target.style.opacity = "0.6";
                target.style.textDecoration = "line-through";

                window.__strippedChildren.push({
                    el: target,
                    origOutline: origOutline,
                    origOpacity: origOpacity,
                    origTextDec: origTextDec
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
                removedParent.el.style.boxShadow = removedParent.origShadow;

                // Clean up any stripped children that belonged to this parent
                for (var k = window.__strippedChildren.length - 1; k >= 0; k--) {
                    if (removedParent.el.contains(window.__strippedChildren[k].el)) {
                        var c = window.__strippedChildren.splice(k, 1)[0];
                        c.el.style.outline = c.origOutline;
                        c.el.style.opacity = c.origOpacity;
                        c.el.style.textDecoration = c.origTextDec;
                    }
                }
            } else {
                // Select new parent container
                var pOrigOutline = target.style.outline || "";
                var pOrigShadow = target.style.boxShadow || "";
                target.style.outline = "3px solid #B388FF";
                target.style.boxShadow = "0 0 16px rgba(179, 136, 255, 0.6)";

                window.__selectedParents.push({
                    el: target,
                    origOutline: pOrigOutline,
                    origShadow: pOrigShadow
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
