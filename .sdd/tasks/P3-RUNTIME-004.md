## Задача P3-RUNTIME-004: Реализовать автоматический карантин и промоушен SHADOW-шаблонов

**Модуль:** `:pipeline:runtime`, `:core:storage`  
**Целевые файлы:**  
- `pipeline/runtime/src/main/kotlin/com/example/npc/pipeline/runtime/monitor/TemplateHealthMonitor.kt`  
- `pipeline/runtime/src/main/kotlin/com/example/npc/pipeline/runtime/monitor/ShadowPromotionMonitor.kt`  
**Спецификация:** `.sdd/architecture_phase3.md#33-машина-состояний-шаблона`  

---

### Описание:
Реализовать фоновый монитор надежности и обучения банка шаблонов:
1. **Автокарантин:**
   - Если активный шаблон сматчил текст, но последующий парсинг суммы/валюты завершился ошибкой (`parseFailures >= 3` подряд) — шаблон немедленно переводится в статус `QUARANTINED`.
   - Если срабатывание `NodeCircuitBreaker` атрибутировано конкретному шаблону — автокарантин с причиной `BREAKER_TRIPPED`.
   - После карантина банк перекомпилируется без сбойного шаблона, восстанавливая работоспособность конвейера.
2. **Промоушен SHADOW-шаблонов:**
   - Отслеживание статистики `shadowAgreements` и `shadowDisagreements`.
   - При достижении критерия `shadowAgreements >= 5 && shadowDisagreements == 0` система генерирует событие готовности к авто-промоушену в статус `ACTIVE` (или выводит подсказку пользователю в UI).

### Критерии приёмки (DoD):
- Сбойный шаблон изолируется без падения всего конвейера.
- Unit-тесты на авто-карантин и продвижение из SHADOW.
