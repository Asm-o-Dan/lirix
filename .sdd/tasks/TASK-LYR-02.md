# Задача TASK-LYR-02: реализовать `parseAmDmHtml`

**Файл:** `app/src/main/java/com/eventengine/app/feature/lyrics/AmDmChordParser.kt` (создать)
**Спека:** [.sdd/specs/lyrics-engine/overview.md#parseAmDmHtml](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/lyrics-engine/overview.md) (v1)

**Сигнатура (НЕ МЕНЯТЬ):**
```kotlin
fun parseAmDmHtml(html: String): String?
```

**Поведение:**
1. Если `html.isBlank()` $\to$ вернуть `null`.
2. Найти блок с аккордами и текстом песни:
   - В разметке AmDm.ru блок обычно расположен внутри `<pre itemprop="chordsBlock">` или `<div class="b-podbor__text">` или между `<pre>` и `</pre>`.
3. Извлечь содержимое блока:
   - Удалить HTML-теги ссылок (`<a ...>`, `</a>`), сохраняя сами аккорды (например, `[Am]`, `[C]`, `[Em]`).
   - Заменить HTML-сущности (`&nbsp;` $\to$ пробел, `&quot;` $\to$ `"`, `&amp;` $\to$ `&`).
   - Сохранить оригинальные переводы строк и пробельное форматирование позиционирования аккордов над слогами.
4. Если блок не найден или пуст $\to$ вернуть `null`.
5. Вернуть очищенный текст аккордов.

**Ошибки:**
- Не бросать исключений при некорректном HTML, возвращать `null`.

**Критерий приёмки:**
- Проходят тесты `AmDmChordParserTest::test_parse_valid_html`, `test_parse_empty_html`, `test_preserve_chord_alignment`.
