## Задача P3-QA-IND: Тест-дизайн и реализация тестового сьюта для :induction

**Модуль:** `:induction`, `:pipeline:compiler`  
**Целевые файлы тестов:**  
- `induction/src/test/kotlin/com/example/npc/induction/TokenSegmenterTest.kt`  
- `induction/src/test/kotlin/com/example/npc/induction/TemplateBuilderTest.kt`  
- `induction/src/test/kotlin/com/example/npc/induction/RightBoundedRuleTest.kt`  
- `pipeline/compiler/src/test/kotlin/com/example/npc/pipeline/compiler/template/TemplateLintTest.kt`  
- `induction/src/test/kotlin/com/example/npc/induction/RoundTripValidatorTest.kt`  
- `induction/src/test/kotlin/com/example/npc/induction/ReplayValidatorTest.kt`  
**Спецификация:** `.sdd/architecture_phase3.md#4-архитектура-dynamic-template-induction`  
**Контракт:** `.sdd/contracts/induction-engine__compiler-store.md`  

---

### Требования к тестам:

1. **TokenSegmenterTest:**
   - Разбиение токенов на `LITERAL`, `SLOT`, `VARIABLE`, `WHITESPACE`.
   - Автоматическое вынесение дат и времени в переменные `VARIABLE`.

2. **TemplateBuilderTest:**
   - Замена слотов на именованные группы `SafeFragments` (`amount`, `curr`, `card`, `bal`, `merchant`).
   - Экранирование литералов `\Q...\E` и нормализация пробелов `\s+`.
   - Синтез паттерна для инцидента MAIB TEMU: полученный regex успешно компилируется в RE2/J.

3. **RightBoundedRuleTest:**
   - Ленивые группы мерчанта проверяются на наличие правого ограничителя.
   - Ошибка `RIGHT_UNBOUNDED_SLOT` при попытке создать шаблон с неограниченным мерчантом в конце строки.

4. **TemplateLintTest:**
   - Проверка лимитов компилятора: длина $\le 1024$, именованные группы $\le 12$, вложенность $\le 3$.
   - Специфичность: не менее 2 литералов суммарной длиной от 8 символов.
   - Запрет неэкранированных бесконечных квантификаторов `.*` и `.+`.

5. **RoundTripValidatorTest:**
   - 100% совпадение разметки слотов при сопоставлении скомпилированного шаблона с исходным сообщением.

6. **ReplayValidatorTest:**
   - Блокировка шаблона при ложном срабатывании хотя бы на одном сообщении из негативной выборки (спам, OTP).
   - Формирование отчета о конфликтах с существующими экстракторами.

### Критерий DoD:
- 100% PASS всех тестов модуля `:induction`.
- 0 падений компилятора при фаззинге случайной разметки.
