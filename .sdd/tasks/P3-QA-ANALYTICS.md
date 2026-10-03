## Задача P3-QA-ANALYTICS: Тест-дизайн и реализация тестов для :feature:analytics

**Модуль:** `:feature:analytics`  
**Целевые файлы тестов:**  
- `feature/analytics/src/test/kotlin/com/example/npc/feature/analytics/vm/FinancialAnalyticsViewModelTest.kt`  
- `feature/analytics/src/test/kotlin/com/example/npc/feature/analytics/reducer/AnalyticsReducerTest.kt`  
- `feature/analytics/src/androidTest/kotlin/com/example/npc/feature/analytics/ui/AnalyticsScreenUiTest.kt`  
**Спецификация:** `.sdd/architecture_phase3.md#62-financial-analytics-ui`  
**Контракт:** `.sdd/contracts/ui-features__domain-contracts.md#22-financial-analytics-ui`  

---

### Требования к тестам:

1. **AnalyticsReducerTest:**
   - Переключение временных диапазонов (`TODAY`, `THIS_WEEK`, `THIS_MONTH`, `CUSTOM`).
   - Переключение фильтра направления (`ALL`, `EXPENSE`, `INCOME`).
   - Переключение режима учета возвратов (`REDUCE_EXPENSE` vs `TREAT_AS_INCOME`).

2. **FinancialAnalyticsViewModelTest (Turbine):**
   - Подписка на реактивный Flow из `StorageGateway`.
   - Корректное формирование списка карточек валют без сквозного суммирования разных валют (**ADR-305**).
   - Обработка бейджа транзакций `SUGGESTED`.

3. **AnalyticsScreenUiTest (Compose):**
   - Отрисовка графиков динамики трат на `Canvas`.
   - Прокрутка постраничного списка транзакций Paging 3.
   - Клик по транзакции открывает `EventDetailsDialog`.

### Критерий DoD:
- 100% PASS юнит- и UI-тестов аналитики.
