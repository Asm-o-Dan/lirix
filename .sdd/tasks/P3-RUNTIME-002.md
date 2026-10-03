## Задача P3-RUNTIME-002: Реализовать узел extract.template_bank с быстрым префильтром

**Модуль:** `:pipeline:runtime`, `:pipeline:nodes-api`  
**Целевые файлы:**  
- `pipeline/runtime/src/main/kotlin/com/example/npc/pipeline/runtime/node/builtin/TemplateBankNodeExecutor.kt`  
- `pipeline/runtime/src/main/kotlin/com/example/npc/pipeline/runtime/template/CompiledTemplateBank.kt`  
**Спецификация:** `.sdd/architecture_phase3.md#35-матчинг-в-рантайме`  

---

### Описание:
Реализовать исполнитель узла `extract.template_bank` для горячего пути конвейера:
1. Организация банка: группировка шаблонов по пакетам приложений (`sourceKey`) и сортировка по приоритету:
   `tier (OVERRIDE > FALLBACK) -> priority -> specificity desc`.
2. Быстрый строковый префильтр:
   - Проверка наличия обязательных литералов `requiredLiterals` через дешевый `String.contains()` до инициализации матчера RE2/J.
3. Сопоставление с текстом:
   - При совпадении паттерна извлечение именованных групп и сборка `FinancialTransaction`.
   - Запись в регистр `R_TX` и установка провенанса `R_TX_PROVENANCE = ExtractorKind.TEMPLATE`.
4. Поддержка режима `SHADOW`: шаблон сопоставляется, собирает статистику совпадений/расхождений с основным конвейером, но не изменяет `R_TX`.

### Критерии приёмки (DoD):
- Время сопоставления при наличии 200 шаблонов в банке $\le 1.0$ мс (p95).
- Корректная изоляция режима `SHADOW`.
- Тест-сьют с проверкой приоритетов `OVERRIDE` и `FALLBACK`.
