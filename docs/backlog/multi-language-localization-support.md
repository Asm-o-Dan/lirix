---
worth: yes
where: app/src/main/res/values/strings.xml:1
added: 2026-10-16
---
# Multi-Language Localization Support (i18n)

Support multiple languages (at least English and Russian) instead of hardcoded Russian UI strings.

### Limitation & Technical Debt
- Most UI strings across Jetpack Compose screens (`KaraokeScreen.kt`, `LibraryScreen.kt`, `TrackActionBottomSheet.kt`, `DeviceConnectBottomSheet.kt`, `StudioScreen.kt`) are hardcoded string literals in Russian.
- `app/src/main/res/values/strings.xml` contains only 20 legacy strings with a mix of English and Russian.
- There is no `values-ru/` directory or locale configuration in `AndroidManifest.xml` / Compose theme.
- Non-Russian speaking users cannot comfortably use the application.

### Why Deferred
- Refactoring all UI screens to `stringResource(R.string.*)` touches dozens of Compose components across the entire app (high blast radius).
- The current release (v1.4.0) is under release freeze; performing a massive string extraction now risks UI regression right before publishing.

### Resolution Plan
1. **Audit & Extract**: Extract all hardcoded user-facing strings from `app/src/main/java/com/lirix/app/ui/` into resource IDs.
2. **Resource Catalogs**:
   - `app/src/main/res/values/strings.xml` (Default: English).
   - `app/src/main/res/values-ru/strings.xml` (Russian translation).
3. **Compose Integration**: Replace all raw string literals with `stringResource(R.string.<id>)`.
4. **App Locale Picker**: Support Android 13+ Per-App Language Preferences (`android:localeConfig="@xml/locales_config"`) and in-app language switch option.
5. **Validation**: Run Android lint to ensure 100% translation parity without missing keys.
