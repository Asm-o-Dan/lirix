# TASK-I18N-005: Desktop & Shared Strings Parity Layer

**Спецификация:** `SPEC-I18N-01` §2  
**Зона:** media-ui / shared  
**Файлы:**
- `shared/src/commonMain/kotlin/com/lirix/app/ui/i18n/LirixStrings.kt`
- `desktop/src/main/kotlin/com/lirix/app/desktop/ui/DesktopDeckScreen.kt`

## Что сделать
1. Создать в `:shared` кроссплатформенный класс `LirixStrings` с поддержкой языков (EN/RU).
2. Подключить `LirixStrings` к Desktop-интерфейсу (`DesktopDeckScreen.kt`, телесуфлер, настройки тем).

## DoD
- `:shared:compileKotlinDesktop` и `:desktop:compileKotlin` проходят успешно.
