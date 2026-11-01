# SPEC-I18N-01: Multi-Language Localization Engine (EN & RU)

**Версия:** 1.0  
**Статус:** APPROVED  
**Зона:** media-ui / app / shared  
**Целевые модули:** `:app`, `:shared`

---

## 1. Контекст и бизнес-требования
Приложение содержит множество захардкоженных русских строк в Composable-функциях (`NowPlayingScreen`, `LibraryScreen`, `WrappedScreen`, `DeviceConnectBottomSheet`).
Для международной аудитории требуется:
1. Вынос всех видимых пользователю строк в строковые ресурсы Android (`R.string.*`).
2. Базовый каталог: `app/src/main/res/values/strings.xml` (English — язык по умолчанию для всего мира).
3. Русская локализация: `app/src/main/res/values-ru/strings.xml` (полный паритет 100% ключей).
4. Поддержка Android 13+ Per-App Language Preferences через `@xml/locales_config`.
5. Кроссплатформенный слой строк для десктопа в `:shared` (`LirixStrings.kt`).

---

## 2. Архитектура и файлы

- `app/src/main/res/xml/locales_config.xml`:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <locale-config xmlns:android="http://schemas.android.com/apk/res/android">
      <locale android:name="en"/>
      <locale android:name="ru"/>
  </locale-config>
  ```
- `app/src/main/AndroidManifest.xml`: `android:localeConfig="@xml/locales_config"`
- `values/strings.xml` & `values-ru/strings.xml`:
  - `tab_now_playing`, `tab_history`, `tab_library`, `tab_wrapped`
  - `player_empty_title`, `player_empty_subtitle`, `player_searching_lyrics`
  - `karaoke_mode_lyrics`, `karaoke_mode_karaoke`, `karaoke_mode_chords`, `karaoke_mode_translation`
  - `share_dialog_title`, `share_static_png`, `share_animated_mp4`, `share_button_export`
  - `connect_title`, `connect_scan_qr`, `connect_audio_cast`

---

## 3. Декомпозиция задач (`.sdd/tasks/`)

| ID Задачи | Название | Файлы реализации |
|---|---|---|
| `TASK-I18N-001` | System locales config and core string resource dictionary | `res/xml/locales_config.xml`, `values/strings.xml`, `values-ru/strings.xml` |
| `TASK-I18N-002` | Player, transport and tab navigation localization | `app/.../NowPlayingScreen.kt`, `EmptyPlayerStateView.kt`, `NavigationCapsule.kt` |
| `TASK-I18N-003` | Library, history and blacklist screens localization | `app/.../LibraryScreen.kt`, `HistoryScreen.kt`, `BlacklistSettingsScreen.kt` |
| `TASK-I18N-004` | Wrapped, share posters and dialogs localization | `app/.../WrappedScreen.kt`, `ShareFormatDialog.kt`, `EditLyricsDialog.kt` |
| `TASK-I18N-005` | Desktop & Shared strings parity layer | `shared/.../ui/i18n/LirixStrings.kt` |
