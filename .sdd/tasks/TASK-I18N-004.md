# TASK-I18N-004: Wrapped, share posters and dialogs localization

**Спецификация:** `SPEC-I18N-01` §2  
**Зона:** media-ui / app  
**Файлы:**
- `app/src/main/java/com/lirix/app/ui/WrappedScreen.kt`
- `app/src/main/java/com/lirix/app/ui/components/LrcTapSyncStudioDialog.kt`
- `app/src/main/java/com/lirix/app/ui/components/EditLyricsDialog.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-ru/strings.xml`

## Что сделать
1. Локализовать экран Wrapped:
   - Периоды ("За всё время", "Этот месяц", "Эта неделя", "Сегодня").
   - Метрики ("Минут прослушано", "Любимый артист", "Топ треков").
   - Ачивки и бейджи геймификации.
2. Локализовать диалоги Tap-to-Sync Studio и редактор текста песен.

## DoD
- Успешная компиляция `:app`.
- Экран Wrapped корректно рендерит локализованный текст для всех периодов.
