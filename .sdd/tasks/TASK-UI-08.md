# Карточка Задачи: TASK-UI-08

- **ID**: `TASK-UI-08`
- **Зона**: `media-ui`
- **Зависит от**: `SPEC-BUG-11` (`.sdd/specs/media-ingress/skip_and_lifecycle_cleanup.md`)
- **Целевой файл**: `app/src/main/java/com/lirix/app/ui/components/ShareLyricsAttachDialog.kt`
- **Исполнитель**: Coder (UI)
- **Статус**: READY

---

## 1. Контекст и Проблема
В диалоге привязки текста `ShareLyricsAttachDialog.kt` список недавних треков `LazyColumn` не имеет явного ограничения по высоте, что на экранах с клавиатурой или небольшим разрешением может выталкивать кнопку «Отмена» за нижний край экрана.

---

## 2. Детальные Инструкции по Реализации
В файле `ShareLyricsAttachDialog.kt`:
Заменить вызов `LazyColumn` (строка ~172):
```kotlin
LazyColumn(
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 180.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp)
) {
```

---

## 3. Критерии Приемки (Definition of Done)
1. Код компилируется без ошибок.
2. Список недавних треков прокручивается внутри отведённых 180dp и не выходит за границы экрана.
