# TASK-I18N-003: Library, history and blacklist localization

**Спецификация:** `SPEC-I18N-01` §2  
**Зона:** media-ui / app  
**Файлы:**
- `app/src/main/java/com/lirix/app/ui/LibraryScreen.kt`
- `app/src/main/java/com/lirix/app/ui/BlacklistSettingsScreen.kt`
- `app/src/main/java/com/lirix/app/ui/components/TrackActionBottomSheet.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-ru/strings.xml`

## Что сделать
1. Заменить все захардкоженные строки в Медиатеке, Истории и Чёрном списке:
   - Фильтры ("Все", "Избранное", "С аккордами", "С караоке").
   - Диалог действий с треком ("Удалить из кэша", "В черный список", "Экспорт LRC").
   - Настройки фильтрации черного списка (правила, добавление, очистка).

## DoD
- Успешная компиляция `:app`.
- 100% паритет строковых ключей между `values/` и `values-ru/`.
