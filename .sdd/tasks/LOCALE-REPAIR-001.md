# LOCALE-REPAIR-001 — resolveAppLocale

Function: pure resolveAppLocale(selection, systemLocales) -> supported locale.
Inputs: SYSTEM/RU/EN; ordered system languages. Output: ru/en, deterministic EN fallback.
DoD: test explicit dominance, system order, unsupported/emptyfallback. No platform/UI mutations.
