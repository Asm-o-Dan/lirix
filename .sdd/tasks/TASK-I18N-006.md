# TASK-I18N-006: In-App Language Selection Dialog & Runtime Locale Switcher

- **Spec:** [.sdd/specs/media-ui/in_app_language_picker_spec.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ui/in_app_language_picker_spec.md)
- **Zone:** `media-ui`
- **Owner:** Android UI Coder / i18n Engineer
- **Status:** DONE

## 1. Description
Добавить в приложение возможность выбора языка интерфейса непосредственно из UI (меню ⋮ плеера и настройки), без необходимости заходить в системные настройки Android.

## 2. Requirements & Files
1. `app/src/main/java/com/lirix/app/ui/i18n/LanguageManager.kt`:
   - Реализовать enum `AppLanguage` (`SYSTEM`, `RUSSIAN`, `ENGLISH`).
   - Реализовать методы `setLanguage(context: Context, language: AppLanguage)` и `getCurrentLanguage(context: Context): AppLanguage` с использованием `AppCompatDelegate.setApplicationLocales` и сохранением в SharedPreferences.
2. `app/src/main/java/com/lirix/app/ui/components/LanguageSelectionDialog.kt`:
   - Красивый AMOLED/Material 3 диалог с выбором языка (радиокнопки).
3. `app/src/main/java/com/lirix/app/ui/components/NowPlayingOverflowMenu.kt`:
   - Добавить пункт `onOpenLanguageDialog` с иконкой `Language` / `Globe`.
4. `app/src/main/java/com/lirix/app/ui/NowPlayingScreen.kt`:
   - Подключить состояние `showLanguageDialog` и отображение `LanguageSelectionDialog`.
5. Строковые ресурсы:
   - Добавить ключи `language_title`, `language_system_default`, `language_ru`, `language_en`, `menu_language` в `values/strings.xml` и `values-ru/strings.xml`.
6. Unit-тесты:
   - `app/src/test/java/com/lirix/app/ui/i18n/LanguageManagerTest.kt`.

## 3. DoD (Definition of Done)
- Меню ⋮ содержит пункт "Язык интерфейса" / "App Language".
- Диалог позволяет выбрать и мгновенно переключить язык на лету.
- Выбор сохраняется при перезапуске приложения.
- `:app:compileDebugKotlin` и тесты проходят успешно.
