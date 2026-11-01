# TASK-I18N-002: Player, transport and tab navigation localization

**Спецификация:** `SPEC-I18N-01` §2  
**Зона:** media-ui / app  
**Файлы:**
- `app/src/main/java/com/lirix/app/ui/NowPlayingScreen.kt`
- `app/src/main/java/com/lirix/app/ui/components/EmptyPlayerStateView.kt`
- `app/src/main/java/com/lirix/app/ui/components/NavigationCapsule.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-ru/strings.xml`

## Что сделать
1. Заменить все захардкоженные строковые литералы в NowPlayingScreen на `stringResource(R.string.<id>)`:
   - Состояния плеера ("Ничего не играет", "Ищем текст песни...", "Текст не найден").
   - Режимы отображения (Текст, Караоке, Аккорды, Перевод).
   - Подписи транспорта и действий меню ⋮ ("Редактировать", "Синхронизировать", "Поделиться").
2. Добавить соответствующие ключи на английском (`values/`) и русском (`values-ru/`).

## DoD
- `:app:compileDebugKotlin` проходит без ошибок.
- На русском языке интерфейс выглядит идентично прежнему, на английском все элементы плеера переведены.
