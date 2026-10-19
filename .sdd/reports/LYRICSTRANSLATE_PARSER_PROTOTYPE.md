# LyricsTranslate parser prototype
Date: 2026-10-19

## Scope and evidence
User clarified coordinator-only rules belong to Antigravity and explicitly authorized Codex to continue directly. No agents used.
Actual browser DOM inspected on the public search and English Danza Kuduro translation pages. Observed selectors: [data-panel=original/translation] .translate__title, #original-lyrics, #translation-body, .top__artists a.top__tag, Translation details block author, notice-warning__text. Search form uses POST and named formtree fields; do not guess GET discovery routes.

Direct urllib HTTPS request to /en/songs received HTTP403 even outside the local sandbox. Ordinary in-app browser opened the page. No source protection bypass attempted. Automated native HTTP discovery is therefore not integrated or claimed working.

## Implemented
Shared JVM HTML parser for five verified English language labels (ru/en/es/pt/zh), strict source URL checks, size limit, mandatory page identity and target-language verification, translator and warning extraction. Original and translation remain independent whole texts. No inferred line mapping/timestamps.
Backward-compatible serialized fields: sourceName (Amalgama default), aligned (true default), warnings (empty default). Reader attributes the actual source and shows warnings. Existing Russian DAO/coordinator/preferences are unchanged: this is parser infrastructure, not a multilingual release or a language-selector UI.
Five synthetic tests cover language identity, unsupported/malformed pages, unsafe URLs, independent texts/attribution and old Russian JSON. No actual song HTML or lyrics committed.

## Remaining
Native HTTP access/discovery, language-aware DAO/coordinator/cache and preferences, variant discovery/selection, provider routing, verified live parse and five-language end-to-end checks. Existing APK and installed PC executable are not replaced by test builds. No release created.

## Validation
Gradle :desktop:test :shared:desktopTest :app:testDebugUnitTest --offline completed BUILD SUCCESSFUL. Desktop114 discovered,110 passed,4 opt-in checks skipped (workspace preview and three translation preview/live probes); all five new parser tests passed. Shared173 passed. Android results recorded after XML verification. No independent review or native integration acceptance claimed. git diff --check passed.
Android XML verification:198 passed,0 failures/errors/skips. Total481 passed,4 opt-in skips.
