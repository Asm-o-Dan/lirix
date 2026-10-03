## Задача P3-QA-TXT: Тест-дизайн и реализация тестового сьюта для :core:text

**Модуль:** `:core:text`  
**Целевые файлы тестов:**  
- `core/text/src/test/kotlin/com/example/npc/core/text/TextNormalizerTest.kt`  
- `core/text/src/test/kotlin/com/example/npc/core/text/OffsetMapPropertyTest.kt`  
- `core/text/src/test/kotlin/com/example/npc/core/text/LexerFsmTest.kt`  
- `core/text/src/test/kotlin/com/example/npc/core/text/LexiconLoaderTest.kt`  
**Спецификация:** `.sdd/architecture_phase3.md#32-лексер-и-токенизация`  
**Контракт:** `.sdd/contracts/core-text__universal-inducer.md`  

---

### Требования к тестам:

1. **TextNormalizerTest:**
   - Проверка приведения NFKC, удаления невидимых управляющих символов и нулевых пробелов (`\u200B`, `\u202F`).
   - Унификация переводов строк `\r\n` -> `\n`.
   - Проверка `keyForm`: удаление диакритических знаков румынского языка (`ă/â/î/ș/ț`) и буквы `ё`.
   - Замена латино-кириллических гомоглифов (кириллическая `М` в латинском `MDL`).

2. **OffsetMapPropertyTest:**
   - Биективность проекции смещений: для любого спана `[start, end)` в `normalized` исходный спан `toOriginalSpan()` указывает на эквивалентную семантическую подстроку в `original`.
   - Граничные случаи: строки с эмодзи (суррогатные пары), пустые строки, последовательности множественных пробелов.

3. **LexerFsmTest:**
   - Детерминированный конечный автомат $O(N)$ без исключений: Fuzz-тест на 10 000 случайных строк.
   - Разбор числовых кластеров с множественными интерпретациями (`12 345,67`, `1.234,56`, `245.9`).
   - Выделение валют (`MDL`, `RUP`, `USD`, `EUR`, `RUB`, `lei`, `лей`, `руб`, `р.`, `$`, `€`).
   - Маркировка неоднозначной формы `руб` как `CurrencyAmbiguous`.
   - Выделение масок карт (`*1234`, `**1234`, `**** 1234`).

4. **LexiconLoaderTest:**
   - Загрузка стем-словарей `lexicon_v1.json` (RU, RO, EN).
   - Скорость инициализации словаря $\le 5$ мс.
   - 100% совпадение категоризации ключевых слов (`DECLINED`, `REFUND`, `CREDIT`, `TRANSFER`, `DEBIT`, `BALANCE`, `OTP`, `PROMO`).

### Критерий DoD:
- 100% PASS всех тестов модуля `:core:text`.
- Нулевые аллокации regex в горячем цикле лексера.
