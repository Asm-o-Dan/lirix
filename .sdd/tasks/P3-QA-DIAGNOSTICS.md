## Задача P3-QA-DIAGNOSTICS: Тест-дизайн и реализация тестов для :feature:diagnostics

**Модуль:** `:feature:diagnostics`  
**Целевые файлы тестов:**  
- `feature/diagnostics/src/test/kotlin/com/example/npc/feature/diagnostics/vm/DiagnosticsViewModelTest.kt`  
- `feature/diagnostics/src/test/kotlin/com/example/npc/feature/diagnostics/reducer/DiagnosticsReducerTest.kt`  
- `feature/diagnostics/src/androidTest/kotlin/com/example/npc/feature/diagnostics/ui/DiagnosticsScreenUiTest.kt`  
**Спецификация:** `.sdd/architecture_phase3.md#63-orchestrator-diagnostics-screen`  
**Контракт:** `.sdd/contracts/ui-features__domain-contracts.md#23-orchestrator-diagnostics-screen`  

---

### Требования к тестам:

1. **DiagnosticsViewModelTest (Turbine):**
   - Периодический опрос `OrchestratorProbe.takeSnapshot()` с интервалом 500 мс (2 Гц).
   - Остановка сбора потока при переходе экрана в неактивное состояние.
   - Кнопка «Пауза»: заморозка обновления списка трейсов.
   - Вызов действия `resetCircuitBreaker(nodeId)`: отправка намерения сброса в рантайм.

2. **DiagnosticsReducerTest:**
   - Формирование состояния очередей, поколений конвейера и банка шаблонов.
   - Фильтрация трейсов по типу узла и результату выполнения.
   - Формирование алертов качества при `financeWithoutPayload > 0`.

3. **DiagnosticsScreenUiTest (Compose):**
   - Отображение карточки поколения, списка предохранителей и трейс-лога.
   - Проверка нажатия кнопки сброса открытого предохранителя.

### Критерий DoD:
- 100% PASS тестов экрана диагностики.
