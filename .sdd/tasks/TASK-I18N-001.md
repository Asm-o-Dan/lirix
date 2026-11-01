# TASK-I18N-001: System locales config and core string resource dictionary

**Спецификация:** `SPEC-I18N-01` §2  
**Зона:** media-ui / app  
**Файлы:**
- `app/src/main/res/xml/locales_config.xml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/strings.xml` (Default EN)
- `app/src/main/res/values-ru/strings.xml` (RU)

## Что сделать
1. Создать `app/src/main/res/xml/locales_config.xml` со списком локалей: `en`, `ru`.
2. Добавить `android:localeConfig="@xml/locales_config"` в `<application>` в `AndroidManifest.xml`.
3. Создать `app/src/main/res/values-ru/strings.xml` и перенести туда существующие русские строки.
4. В `app/src/main/res/values/strings.xml` добавить английские эквиваленты всех базовых строк (названия табов, базовые действия).

## DoD
- Проект компилируется в `:app:compileDebugKotlin`.
- XML синтаксически валиден, нет дубликатов ключей.
