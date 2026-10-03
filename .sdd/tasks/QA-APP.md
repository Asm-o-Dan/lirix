## Задача QA-APP: Написание тестового набора для модуля :app

**Файлы:**
- `app/src/test/kotlin/com/example/npc/app/onboarding/OnboardingManagerTest.kt`
- `app/src/test/kotlin/com/example/npc/app/worker/AbsenceAlertWorkerTest.kt`
- `app/src/test/kotlin/com/example/npc/app/receiver/BootCompletedReceiverTest.kt`
(создать)

**Спека:**
- `.sdd/specs/app-lifecycle/overview.md#тестовые-сценарии-и-верификация-gate-6-и-gate-7` (v1)

**Зависит от:** `APP-001`

**Поведение:**
1. `OnboardingManagerTest`:
   - Проверка расчета `isMandatorySetupComplete`:
     - Все флаги true -> true.
     - Отсутствие доступа к уведомлениям -> false.
     - На HyperOS без подтверждения автозапуска -> false.
2. `AbsenceAlertWorkerTest`:
   - Расчет дельты времени с момента `lastEventAt`:
     - Если дельта > 6 часов -> генерация алертов в `NotificationManagerCompat`.
     - Если дельта <= 6 часов -> отмена алертов (cancel).
3. `BootCompletedReceiverTest`:
   - Проверка вызова шедулеров при получении `ACTION_BOOT_COMPLETED`.

**Критерий приёмки:**
- Все тесты модуля `:app` проходят: `.\gradlew.bat :app:testDebugUnitTest` завершается BUILD SUCCESSFUL.
