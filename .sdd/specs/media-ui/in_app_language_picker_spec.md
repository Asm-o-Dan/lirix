# SPEC-I18N-02: In-App Language Selection & Runtime Switching

## 1. Контекст и Проблема
Ранее в `SPEC-I18N-01` была внедрена системная локализация через `locales_config.xml` (Per-App Language Preferences Android 13+). Однако:
1. Пользователю неудобно переходить в настройки операционной системы (Настройки -> Приложения -> Lirix -> Язык).
2. На многих прошивках (MIUI/HyperOS, ColorOS, планшеты, старые версии Android < 13) системный селектор языка приложений скрыт или отсутствует.
3. В приложении отсутствует встроенный диалог выбора языка (In-App Language Picker).

## 2. Архитектурное Решение

### 2.1 Перечисление Языков (`AppLanguage.kt`)
Создается перечисление поддерживаемых языков в общем или UI слое:
```kotlin
enum class AppLanguage(val code: String, val titleRes: Int, val nativeName: String) {
    SYSTEM("", R.string.language_system_default, "Системный / System Default"),
    RUSSIAN("ru", R.string.language_ru, "Русский"),
    ENGLISH("en", R.string.language_en, "English");

    companion object {
        fun fromCode(code: String?): AppLanguage = when (code?.lowercase()) {
            "ru" -> RUSSIAN
            "en" -> ENGLISH
            else -> SYSTEM
        }
    }
}
```

### 2.2 Управление Локалью на Android (`LanguageManager.kt`)
Использование AndroidX `AppCompatDelegate`:
```kotlin
object LanguageManager {
    fun setLanguage(context: Context, language: AppLanguage) {
        val appLocale = if (language == AppLanguage.SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.code)
        }
        AppCompatDelegate.setApplicationLocales(appLocale)
        // Сохранение в SharedPreferences для быстрого чтения
        context.getSharedPreferences("lirix_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("pref_app_language", language.code)
            .apply()
    }

    fun getCurrentLanguage(context: Context): AppLanguage {
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        if (currentLocales.isEmpty) {
            val saved = context.getSharedPreferences("lirix_prefs", Context.MODE_PRIVATE)
                .getString("pref_app_language", null)
            return AppLanguage.fromCode(saved)
        }
        return AppLanguage.fromCode(currentLocales[0]?.language)
    }
}
```

### 2.3 UI Компонент (`LanguageSelectionDialog.kt`)
Диалог выбора языка в стиле Material 3 / AMOLED:
- Заголовок с иконкой `Language` (Глобус) — `R.string.settings_language_title` ("Язык интерфейса" / "App Language").
- 3 радиокнопки:
  1. "Как в системе" / "System default"
  2. "Русский"
  3. "English"
- При выборе опции вызывается `LanguageManager.setLanguage(context, selected)` и диалог закрывается, а интерфейс мгновенно перерисовывается на выбранном языке.

### 2.4 Точки Входа
1. **NowPlayingOverflowMenu.kt:**
   - Новый пункт в меню ⋮: `DropdownMenuItem` с иконкой `Icons.Outlined.Language` и текстом `R.string.menu_language` ("Язык интерфейса" / "App Language").
2. **Desktop Deck (DesktopDeckScreen.kt):**
   - Переключение `LirixStrings.currentLocale.value = if (locale == LirixLocale.RU) LirixLocale.EN else LirixLocale.RU`.

## 3. Критерии Приемки (DoD)
1. В меню ⋮ на `NowPlayingScreen` присутствует пункт "Язык интерфейса".
2. При нажатии открывается диалог с выбором: Системный, Русский, English.
3. При выборе языка интерфейс мгновенно переключается без перезапуска приложения.
4. Выбранный язык сохраняется между перезапусками приложения.
5. Unit-тесты на `LanguageManager` и парсинг кодов языков успешно проходят.
