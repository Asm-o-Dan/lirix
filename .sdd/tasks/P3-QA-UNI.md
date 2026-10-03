## Задача P3-QA-UNI: Тест-дизайн и реализация тестового сьюта для :extract:universal

**Модуль:** `:extract:universal`  
**Целевые файлы тестов:**  
- `extract/universal/src/test/kotlin/com/example/npc/extract/universal/CandidateGeneratorsTest.kt`  
- `extract/universal/src/test/kotlin/com/example/npc/extract/universal/OpTypeResolverTest.kt`  
- `extract/universal/src/test/kotlin/com/example/npc/extract/universal/RoleAssignmentSolverTest.kt`  
- `extract/universal/src/test/kotlin/com/example/npc/extract/universal/SafetyGateTest.kt`  
- `extract/universal/src/test/kotlin/com/example/npc/extract/universal/UniversalExtractorGoldenTest.kt`  
**Спецификация:** `.sdd/architecture_phase3.md#3-архитектура-universal-slot-based-extractor`  
**Контракт:** `.sdd/contracts/universal-extractor__runtime.md`  

---

### Требования к тестам:

1. **CandidateGeneratorsTest:**
   - **Инвариант ADR-302:** Проверка отсечения чисел без валюты в радиусе $\pm 1$ токена.
   - Корректная генерация кандидатов баланса и маски карты.

2. **OpTypeResolverTest:**
   - Иерархия специфичности: $\text{DECLINED} \succ \text{REFUND} \succ \text{TRANSFER} \succ \text{CREDIT} \succ \text{DEBIT}$.
   - Разрешение фразы «Отказ в покупке» в `DECLINED`.
   - **Тест инцидента MAIB TEMU:** Разрешение «Restituire 245,90 MDL ... cu cardul» в `CREDIT` с флагом `isRefund = true`.

3. **RoleAssignmentSolverTest:**
   - Разделение суммы покупки и остатка на карте на 50 сложных паттернах уведомлений.
   - Ограничение: ровно один `TX_AMOUNT`, не более одного `BALANCE`.

4. **SafetyGateTest:**
   - 0 ложных транзакций на выборке из 50 SMS с OTP-кодами (VETO срабатывает безусловно).
   - 0 ложных транзакций на выборке промо-рассылок со скидками и ценами без остатка и маски карты.

5. **UniversalExtractorGoldenTest:**
   - Прогон по 150+ сообщениям golden-корпуса.
   - Recall $\ge 95\%$, Precision $\ge 99\%$ на вердиктах `ACCEPT`.
   - Микробенчмарк: время выполнения $p95 \le 3.0$ мс.

### Критерий DoD:
- 100% PASS всех тестов модуля `:extract:universal`.
