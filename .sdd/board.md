# Board — Notification Pipeline Constructor

> Координатор обновляет этот файл после каждого изменения статуса.

## Текущая фаза: 3 — DYNAMIC TEMPLATE INDUCTION & UNIVERSAL EXTRACTOR (GATE 3 PASSED: Contracts Frozen v4)

### Замороженные контракты (FROZEN v1 — Фаза 0)
- `.sdd/contracts/core-model__all.md`
- `.sdd/contracts/core-storage__ingest.md`
- `.sdd/contracts/core-storage__ui.md`

### Замороженные контракты Фазы 1 (FROZEN v2 — GATE 3 пройден ✅)
- `.sdd/contracts/core-model__classify.md`
- `.sdd/contracts/core-model__extract.md`
- `.sdd/contracts/core-storage__extract.md`
- `.sdd/contracts/core-storage__ui.md`

### Замороженные контракты Фазы 2 (FROZEN v3 — GATE 3 пройден ✅)
- `.sdd/contracts/pipeline-dsl__compiler.md`
- `.sdd/contracts/pipeline-compiler__spi.md`
- `.sdd/contracts/pipeline-runtime__compiler.md`
- `.sdd/contracts/pipeline-store__dsl.md`
- `.sdd/contracts/pipeline-replay__runtime.md`

### Замороженные контракты Фазы 3 (FROZEN v4 — GATE 3 пройден ✅)
- `.sdd/contracts/core-text__universal-inducer.md`
- `.sdd/contracts/universal-extractor__runtime.md`
- `.sdd/contracts/induction-engine__compiler-store.md`
- `.sdd/contracts/template-bank__runtime-store.md`
- `.sdd/contracts/ui-features__domain-contracts.md`

### Статус спецификаций (GATE 2 пройден ✅)
- `zone/core-text` — APPROVED / FROZEN (Фаза 3)
- `zone/universal-extractor` — APPROVED / FROZEN (Фаза 3)
- `zone/induction-engine` — APPROVED / FROZEN (Фаза 3)
- `zone/template-bank` — APPROVED / FROZEN (Фаза 3)
- `zone/ui-editor` — APPROVED / FROZEN (Фаза 3)
- `zone/ui-analytics` — APPROVED / FROZEN (Фаза 3)
- `zone/ui-diagnostics` — APPROVED / FROZEN (Фаза 3)
- `zone/pipeline-dsl` — APPROVED / FROZEN (Фаза 2)
- `zone/pipeline-compiler` — APPROVED / FROZEN (Фаза 2)
- `zone/pipeline-spi` — APPROVED / FROZEN (Фаза 2)
- `zone/pipeline-runtime` — APPROVED / FROZEN (Фаза 2)
- `zone/pipeline-store` — APPROVED / FROZEN (Фаза 2)
- `zone/pipeline-replay` — APPROVED / FROZEN (Фаза 2)
- `zone/core-model` — APPROVED / FROZEN (Фаза 0/1)
- `zone/core-storage` — APPROVED / FROZEN (Фаза 0/1)
- `zone/ingest-notification` — APPROVED / FROZEN (Фаза 0/1)
- `zone/ingest-sms` — APPROVED / FROZEN (Фаза 0)
- `zone/ingest-media` — APPROVED / FROZEN (Фаза 0)
- `zone/app-lifecycle` — APPROVED / FROZEN (Фаза 0/1)
- `zone/ui-timeline` — APPROVED / FROZEN (Фаза 0/1)

---

## Очередь задач реализации (DAG)

| ID | Задача / Функция | Файл | Зона | Зависит от | Исполнитель | Статус |
|---|---|---|---|---|---|---|
| INFRA-001 | Скелет Gradle KTS + version catalog + build-logic | settings.gradle.kts | infra | GATE 4 | coder-infra | DONE |
| MODEL-001 | Value-классы (SourceId, ThreadKey, DeduplicationKey, EmbeddingRef, Lang) | core/model/... | model | INFRA-001 | coder-model | DONE |
| MODEL-002 | Доменные сущности (RawEvent, Event, SourceHealth) | core/model/... | model | MODEL-001 | coder-model | DONE |
| MODEL-003 | EventNormalizer.cleanText | core/model/.../EventNormalizer.kt | model | INFRA-001 | coder-model | DONE |
| MODEL-004 | EventNormalizer.detectLang | core/model/.../EventNormalizer.kt | model | MODEL-001 | coder-model | DONE |
| MODEL-005 | EventNormalizer.computeDeduplicationKey | core/model/.../EventNormalizer.kt | model | MODEL-001 | coder-model | DONE |
| MODEL-006 | EventNormalizer.normalize | core/model/.../EventNormalizer.kt | model | MODEL-002..004 | coder-model | DONE |
| QA-MODEL | Написание набора тестов по спеке (47 тестов: 100% PASS) | core/model/src/test/... | qa | INFRA-001 | qa-engineer | DONE |

---

### Модуль :core:storage (Room + SQLCipher 4.6.x + AndroidKeyStore) — ЗАВЕРШЁН (100% GREEN)

| ID | Задача / Функция | Файл | Зона | Зависит от | Исполнитель | Статус |
|---|---|---|---|---|---|---|
| STORAGE-001 | Конфигурация build.gradle.kts (:core:storage: Room KSP, SQLCipher, schemas) | core/storage/build.gradle.kts | storage | MODEL-ALL | coder-storage | DONE |
| STORAGE-002 | Room сущности (RawEventEntity, EventEntity, SourceHealthEntity) | core/storage/.../entity/ | storage | STORAGE-001 | coder-storage | DONE |
| STORAGE-003 | DAO интерфейсы (RawEventDao, EventDao, SourceHealthDao) | core/storage/.../dao/ | storage | STORAGE-002 | coder-storage | DONE |
| STORAGE-004 | Мапперы (RawEventMapper, EventMapper, SourceHealthMapper) | core/storage/.../mapper/ | storage | STORAGE-002 | coder-storage | DONE |
| STORAGE-005 | SqlCipherSupportFactoryProvider (Keystore AES-GCM, passphrase fill(0)) | core/storage/.../SqlCipher... | storage | STORAGE-001 | coder-storage | DONE |
| STORAGE-006 | AppDatabase + StorageGatewayImpl (insert, upsert, observe, export, delete) | core/storage/.../Storage... | storage | STORAGE-003..005 | coder-storage | DONE |
| QA-STORAGE | Юнит / Room / SQLCipher тесты по спецификации core-storage (24 теста: 100% PASS) | core/storage/src/test/... | qa | STORAGE-001 | qa-engineer | DONE |

---

### Модуль :ingest:notification (PipelineNotificationListenerService, Channel, IngestWatchdog) — ЗАВЕРШЁН (17 тестов: 100% PASS)

| ID | Задача / Функция | Файл | Зона | Зависит от | Исполнитель | Статус |
|---|---|---|---|---|---|---|
| NOTIF-001 | Конфигурация build.gradle.kts (:ingest:notification: Hilt, WorkManager, Storage) | ingest/notification/build.gradle.kts | notif | STORAGE-ALL | coder-notif | DONE |
| NOTIF-002 | NotificationMapper (извлечение всех extras, MessagingStyle, canonical JSON) | ingest/notification/.../NotificationMapper.kt | notif | NOTIF-001 | coder-notif | DONE |
| NOTIF-003 | PipelineNotificationListenerService (Channel(UNLIMITED), single consumer, immediate insertRawEvent) | ingest/notification/.../PipelineNotificationListenerService.kt | notif | NOTIF-001,002 | coder-notif | DONE |
| NOTIF-004 | IngestWatchdog (15-min WorkManager check, requestRebind, component toggle fallback) | ingest/notification/.../IngestWatchdog.kt | notif | NOTIF-001 | coder-notif | DONE |
| QA-NOTIF | Тесты маппера, фильтра мусора, канала и watchdog (17 тестов: 100% PASS) | ingest/notification/src/test/... | qa | NOTIF-001 | qa-engineer | DONE |

---

### Модуль :ingest:sms (SmsBroadcastReceiver, SmsIngestController, SmsPoller, SmsBackfillWorker)

| ID | Задача / Функция | Файл | Зона | Зависит от | Исполнитель | Статус |
|---|---|---|---|---|---|---|
| SMS-001 | Каркас модуля :ingest:sms и AndroidManifest | ingest/sms/build.gradle.kts | sms | STORAGE-ALL | coder-sms | DONE |
| SMS-002 | Модели данных и SmsMapper | ingest/sms/.../SmsMapper.kt | sms | SMS-001 | coder-sms | DONE |
| SMS-003 | SmsIngestController, SmsBroadcastReceiver и SmsPoller | ingest/sms/.../SmsIngestController.kt | sms | SMS-002 | coder-sms | DONE |
| SMS-004 | SmsBackfillWorker и фоновые задачи WorkManager | ingest/sms/.../SmsBackfillWorker.kt | sms | SMS-003 | coder-sms | DONE |

| QA-SMS | Написание тестового набора для модуля :ingest:sms (14 тестов: 100% PASS) | ingest/sms/src/test/... | qa | SMS-001 | qa-engineer | DONE |


---

### Модуль :ingest:media (MediaSessionManager, MediaControllerCompat, FSM Boundary Detector, Recovery)

| ID | Задача / Функция | Файл | Зона | Зависит от | Исполнитель | Статус |
|---|---|---|---|---|---|---|
| MEDIA-001 | Каркас модуля :ingest:media и AndroidManifest | ingest/media/build.gradle.kts | media | SMS-ALL | coder-media | DONE |
| MEDIA-002 | Модели данных и DTO для :ingest:media | ingest/media/.../model/ | media | MEDIA-001 | coder-media | DONE |
| MEDIA-003 | MediaPayloadMapper (сериализация и нормализация) | ingest/media/.../mapper/ | media | MEDIA-002 | coder-media | DONE |
| MEDIA-004 | MediaSessionBoundaryDetector (FSM детекции границ сессий) | ingest/media/.../detector/ | media | MEDIA-002,003 | coder-media | DONE |
| MEDIA-005 | MediaControllerHolder и MediaSessionObserver | ingest/media/.../observer/ | media | MEDIA-004 | coder-media | DONE |
| MEDIA-006 | MediaSessionRecoveryManager и DI модуль | ingest/media/.../recovery/ | media | MEDIA-005 | coder-media | DONE |
| QA-MEDIA | Написание набора тестов для модуля :ingest:media (57 тестов: 100% PASS) | ingest/media/src/test/... | qa | MEDIA-001 | qa-engineer | DONE |

---

### Модуль :ui:timeline (Jetpack Compose, TimelineScreen, ViewModel, EventDetails, Filter & Search, Export)

| ID | Задача / Функция | Файл | Зона | Зависит от | Исполнитель | Статус |
|---|---|---|---|---|---|---|
| UI-001 | Каркас модуля :ui:timeline и AndroidManifest | ui/timeline/build.gradle.kts | ui-timeline | MEDIA-ALL | coder-ui | DONE |
| UI-002 | Модели представления (EventUiModel, State) и EventUiMapper | ui/timeline/.../model/ | ui-timeline | UI-001 | coder-ui | DONE |
| UI-003 | TimelineViewModel (реактивный стейт, Flow, поиск, экспорт) | ui/timeline/.../ui/TimelineViewModel.kt | ui-timeline | UI-002 | coder-ui | DONE |
| UI-004 | Compose экраны и компоненты (TimelineScreen, Details, Dialogs) | ui/timeline/.../ui/ | ui-timeline | UI-003 | coder-ui | DONE |
| QA-UI | Написание тестов для модуля :ui:timeline (12 тестов: 100% PASS) | ui/timeline/src/test/... | qa | UI-001 | qa-engineer | DONE |


---

### Модуль :app (zone/app-lifecycle: Application, Hilt DI, Watchdog, AbsenceAlert, HyperOS Onboarding, MainActivity)

| ID | Задача / Функция | Файл | Зона | Зависит от | Исполнитель | Статус |
|---|---|---|---|---|---|---|
| APP-001 | Каркас модуля :app, сборка и AndroidManifest | app/build.gradle.kts | app-lifecycle | UI-ALL | coder-app | DONE |
| APP-002 | App класс, NotificationChannels и Hilt AppModule | app/src/main/.../App.kt | app-lifecycle | APP-001 | coder-app | DONE |
| APP-003 | BootCompletedReceiver и AbsenceAlertWorker (DoD 9) | app/src/main/.../worker/ | app-lifecycle | APP-002 | coder-app | DONE |
| APP-004 | OnboardingFlow (HyperOS / Poco M7) и MainActivity | app/src/main/.../onboarding/ | app-lifecycle | APP-003 | coder-app | DONE |
| QA-APP | Написание тестового набора для модуля :app (31 тест: 100% PASS) | app/src/test/... | qa | APP-001 | qa-engineer | DONE |

---

### Развёртывание и Dogfooding (DevOps & Deployer)

| ID | Задача / Функция | Файл | Зона | Зависит от | Исполнитель | Статус |
|---|---|---|---|---|---|---|
| DEPLOY-001 | Подключение Poco M7 по USB (2440cbe2) и установка app-debug.apk | app/build/outputs/apk/debug/app-debug.apk | ops | APP-ALL | devops-deployer | DONE |
| HOTFIX-001 | Фикс DI StorageGatewayProvider в PipelineNotificationListenerService + деплой | PipelineNotificationListenerService.kt | notif/ops | DEPLOY-001 | coder-deployer | DONE |
| HOTFIX-002 | Активация MediaSessionObserver + мгновенная фиксация смены треков + деплой | MediaSessionObserver.kt | media/ops | HOTFIX-001 | coder-deployer | DONE |
| DEPLOY-002 | Верификация прохождения Onboarding (разрешения, автозапуск HyperOS) | logcat / UI | ops | HOTFIX-001 | devops-deployer | DONE |
| DEPLOY-003 | Мониторинг фоновых сервисов и фиксация логов Dogfooding (7 дней) | .sdd/analysis/ | ops | DEPLOY-002 | devops-deployer | IN_PROGRESS |

---

## Фаза 1: «Хардкод-MVP» (Семантические правила, экстракторы, Timeline 2.0)
Текущий этап: **✅ ФАЗА 1 И СКВОЗНОЙ КОНВЕЙЕР (zone/app-pipeline) ЗАВЕРШЕНЫ — GATE 8 PASSED (2026-09-28, Poco M7 2440cbe2)**
Архитектурный документ: [`.sdd/architecture_phase1.md`](file:///c:/Users/DaniilTuT/Documents/antigravity/vibrant-hawking/.sdd/architecture_phase1.md) (GATE 1 PASSED ✅)
Спецификации всех 5 зон утверждены (GATE 2 PASSED ✅)
Замороженные контракты Фазы 1 (GATE 3 PASSED ✅):
- [`.sdd/contracts/core-model__classify.md`](file:///c:/Users/DaniilTuT/Documents/antigravity/vibrant-hawking/.sdd/contracts/core-model__classify.md)
- [`.sdd/contracts/core-model__extract.md`](file:///c:/Users/DaniilTuT/Documents/antigravity/vibrant-hawking/.sdd/contracts/core-model__extract.md)
- [`.sdd/contracts/core-storage__extract.md`](file:///c:/Users/DaniilTuT/Documents/antigravity/vibrant-hawking/.sdd/contracts/core-storage__extract.md)
- [`.sdd/contracts/core-storage__ui.md`](file:///c:/Users/DaniilTuT/Documents/antigravity/vibrant-hawking/.sdd/contracts/core-storage__ui.md)
Декомпозиция DAG (GATE 4 PASSED ✅): 49 атомарных карточек в `.sdd/tasks/`

| ID | Задача / Функция | Файл | Зона | Зависит от | Исполнитель | Статус |
|---|---|---|---|---|---|---|
| ARCH-P1 | Разработка architecture_phase1.md, карта зон, границы, риски, Opus review | .sdd/architecture_phase1.md | arch | Dogfooding | architect | DONE |
| SPEC-P1-MODEL | Спецификация доменных моделей и контрактов Фазы 1 | .sdd/specs/core-model/ | spec-model | ARCH-P1 | spec-model | DONE |
| SPEC-P1-STORAGE | Спецификация Room MIGRATION_1_2, DAO и StorageGateway v2 | .sdd/specs/core-storage/ | spec-storage | ARCH-P1 | spec-storage | DONE |
| SPEC-P1-EXTRACT | Спецификация региональных финансовых экстракторов (APB, Сбербанк, MAIB) | .sdd/specs/extract-finance/ | spec-extract | ARCH-P1 | spec-extract | DONE |
| SPEC-P1-CLASSIFY | Спецификация PackageGatedRouter и Prototype-First резолвера | .sdd/specs/classify-rules/ | spec-classify | ARCH-P1 | spec-classify | DONE |
| SPEC-P1-UI | Спецификация Timeline 2.0, бейджей, виджетов и диалогов разметки | .sdd/specs/ui-timeline/ | spec-ui | ARCH-P1 | spec-ui | DONE |
| P1-CONTRACTS | Синхронизация и заморозка контрактов Фазы 1 (FROZEN v2) | .sdd/contracts/ | contracts | ALL-SPECS | integrator | DONE |
| P1-CODE-DAG | Карточки реализации функций (49 карточек: MODEL, STORAGE, CLASSIFY, EXTRACT, UI) | .sdd/tasks/ | dev | P1-CONTRACTS | integrator | DONE |
| P1-QA-DESIGN | Тест-дизайн по спекам до написания кода (TDD/Golden Suite) | tests | qa | P1-CONTRACTS, P1-CODE-DAG | qa-engineer | DONE |
| P1-CODE-EXEC | Исполнение задач DAG кодерами по TDD тестам (5 модулей: 283 теста 100% GREEN) | code | dev | P1-QA-DESIGN | coders | DONE |
| APP-P1-001 | Регистрация MIGRATION_1_2 и PreMigrationBackup в AppModule | app/src/main/.../di/AppModule.kt | app-lifecycle | P1-CODE-EXEC | coder-app | DONE |
| SPEC-P1-APP | Спецификация EventProcessingOrchestrator и живой связки конвейера | .sdd/specs/app-pipeline/overview.md | app-pipeline | P1-DEPLOY-001 | spec-pipeline | DONE |
| PIPE-QA-DESIGN | Тест-дизайн EventProcessingOrchestrator (DoD 1..8, QA-ORCH-001..008) | app/src/test/... | qa | SPEC-P1-APP | qa-engineer | DONE |
| PIPE-001 | Добавление зависимостей :classify:rules и :extract:finance в :app | app/build.gradle.kts | app-pipeline | SPEC-P1-APP | coder-app | DONE |
| PIPE-002 | DTO модели конвейера (OrchestratorState, Status, Target, Metrics) | app/src/main/.../pipeline/model/ | app-pipeline | PIPE-001 | coder-app | DONE |
| PIPE-003 | Расширение StorageGateway и EventDao (tryClaim, complete, pendingIds) | core/storage/... | storage | SPEC-P1-APP | coder-storage | DONE |
| PIPE-004 | Hilt DI модули (ClassifierModule, ExtractorModule, OrchestratorModule) | app/src/main/.../di/ | app-pipeline | PIPE-001,002 | coder-app | DONE |
| PIPE-005 | Реализация EventProcessingOrchestratorImpl (Channel, loop, claim, sweep) | app/src/main/.../pipeline/ | app-pipeline | PIPE-002..004 | coder-app | DONE |
| PIPE-006 | Связка в PipelineNotificationListenerService и App.onCreate | ingest/notification/ & app/ | app-pipeline | PIPE-005 | coder-app | DONE |
| DEPLOY-P1-APP | Сборка и деплой на Poco M7 с верификацией сквозного конвейера | app/build/outputs/apk/... | ops | PIPE-006, PIPE-QA-DESIGN | devops-deployer | DONE |



---

### Секция QA-дизайна тестов (GATE 5: Тест-дизайн по спецификациям — PASSED ✅)

| ID | Тестовый сьют | Целевой модуль/пакет | Зависит от | Исполнитель | Статус |
|---|---|---|---|---|---|
| QA-P1-MODEL | Тесты моделей: CurrencyCode, Money, TransactionType, Status, Prototype, Confidence | core/model/src/test/ | P1-CODE-DAG | qa-engineer | DONE |
| QA-P1-STORAGE | Тесты Room MIGRATION_1_2, PreMigrationBackup, DAO v2, StorageGatewayImpl v2 | core/storage/src/test/ | P1-CODE-DAG | qa-engineer | DONE |
| QA-P1-CLASSIFY | Тесты PackageGatedRouter, Fingerprinter, PrototypeStage, HeuristicClassifier | classify/rules/src/test/ | P1-CODE-DAG | qa-engineer | DONE |
| QA-P1-EXTRACT | GoldenBankTransactionsTest (23 реальные транзакции), ReDoS stress-тесты, CircuitBreaker | extract/finance/src/test/ | P1-CODE-DAG | qa-engineer | DONE |
| QA-P1-UI | Тесты EventUiMapper v2, TimelineViewModel v2, Compose UI сценарии | ui/timeline/src/test/ | P1-CODE-DAG | qa-engineer | DONE |

---

### Детальный DAG задач Фазы 1

#### 1. Модуль `:core:model` (Чистый Kotlin JVM) — ЗАВЕРШЁН (100% GREEN, 120 тестов)
| ID | Задача / Файл | Зависит от | Статус |
|---|---|---|---|
| MODEL-P1-001 | `Category.kt` (Enum доменных категорий + fromStringOrUnclassified) | P1-QA-DESIGN | DONE |
| MODEL-P1-002 | `CurrencyCode.kt` (Value-класс RUP, MDL, RUB, EUR, USD, minorDigits, symbol) | P1-QA-DESIGN | DONE |
| MODEL-P1-003 | `Money.kt` (Неотрицательная сумма, safe math Long, formatDisplay) | MODEL-P1-002 | DONE |
| MODEL-P1-004 | `TransactionType.kt` (DEBIT, CREDIT, TRANSFER + псевдонимы EXPENSE, INCOME) | P1-QA-DESIGN | DONE |
| MODEL-P1-005 | `TransactionStatus.kt` (COMPLETED, DECLINED + псевдоним SUCCESS) | P1-QA-DESIGN | DONE |
| MODEL-P1-006 | `FinancialTransaction.kt` (Доменная модель финансовой проводки) | MODEL-P1-003..005 | DONE |
| MODEL-P1-007 | `Confidence.kt` (Value-класс валидированной уверенности 0.0..1.0) | P1-QA-DESIGN | DONE |
| MODEL-P1-008 | `Engine.kt` (Enum движка: NONE, PROTOTYPE, RULES, USER) | P1-QA-DESIGN | DONE |
| MODEL-P1-009 | `UserPrototype.kt` (Доменная модель шаблона обратной связи, supportCount) | MODEL-P1-001 | DONE |
| MODEL-P1-010 | `ClassificationResult.kt` (Результат семантической классификации) | MODEL-P1-001,007,008 | DONE |
| MODEL-P1-011 | `SemanticClassifier.kt`, `PackageGatedRouter.kt` (Контрактные интерфейсы) | MODEL-P1-009,010 | DONE |
| MODEL-P1-012 | `FinanceExtractor.kt`, `ParsedFinanceResult.kt`, `CurrencyResolver.kt` | MODEL-P1-002..006 | DONE |

#### 2. Модуль `:core:storage` (Room + SQLCipher 4.6.x + AndroidKeyStore) — ЗАВЕРШЁН (100% GREEN, Room v2)
| ID | Задача / Файл | Зависит от | Статус |
|---|---|---|---|
| STORAGE-P1-001 | `PreMigrationBackup.kt` (Холодный снапшот БД с wal_checkpoint в noBackupFilesDir) | P1-QA-DESIGN | DONE |
| STORAGE-P1-002 | `MIGRATION_1_2.kt` (Room Migration 1->2 с PRAGMA foreign_key_check) | STORAGE-P1-001 | DONE |
| STORAGE-P1-003 | `EventEntity.kt` (Обновление схемы v2 с defaultValue для category, confidence...) | MODEL-P1-ALL | DONE |
| STORAGE-P1-004 | `FinancialTransactionEntity.kt` (Таблица financial_transaction, ON DELETE SET NULL) | MODEL-P1-ALL | DONE |
| STORAGE-P1-005 | `UserPrototypeEntity.kt` (Таблица user_prototype, уникальный индекс) | MODEL-P1-ALL | DONE |
| STORAGE-P1-006 | `FinancialTransactionDao.kt` (DAO выборки транзакций, Flow, период) | STORAGE-P1-004 | DONE |
| STORAGE-P1-007 | `UserPrototypeDao.kt` (DAO поиска по package+fingerprint, инкремент счетчика) | STORAGE-P1-005 | DONE |
| STORAGE-P1-008 | `EventDao.kt` (Обновление EventDao: recordUserCorrection, observeByCategory) | STORAGE-P1-003 | DONE |
| STORAGE-P1-009 | `FinancialTransactionMapper.kt` (Двусторонний маппинг DTO <-> Entity) | STORAGE-P1-004,006 | DONE |
| STORAGE-P1-010 | `UserPrototypeMapper.kt` (Двусторонний маппинг DTO <-> Entity) | STORAGE-P1-005,007 | DONE |
| STORAGE-P1-011 | `EventMapper.kt` (Обновление маппера событий с полями классификации) | STORAGE-P1-003,008 | DONE |
| STORAGE-P1-012 | `AppDatabase.kt` (Room database v2, регистрация 5 Entities и 5 DAOs) | STORAGE-P1-003..008 | DONE |
| STORAGE-P1-013 | `StorageGateway.kt` (Расширение контракта StorageGateway методами Фазы 1) | MODEL-P1-ALL | DONE |
| STORAGE-P1-014 | `StorageGatewayImpl.kt` (Реализация транзакционных методов withTransaction) | STORAGE-P1-009..013 | DONE |

#### 3. Модуль `:classify:rules` (Чистый Kotlin JVM) — ЗАВЕРШЁН (100% GREEN, 28 тестов)
| ID | Задача / Файл | Зависит от | Статус |
|---|---|---|---|
| CLASSIFY-P1-001 | `PackageGatedRouter.kt` (Белый список банков, черный список мессенджеров) | MODEL-P1-ALL | DONE |
| CLASSIFY-P1-002 | `Fingerprinter.kt` (O(n) нормализация текста в шаблон + SHA-256 хэш) | MODEL-P1-ALL | DONE |
| CLASSIFY-P1-003 | `PrototypeStage.kt` (Prototype-First резолвер: supportCount >= 2 -> 1.0) | MODEL-P1-ALL | DONE |
| CLASSIFY-P1-004 | `RuleBasedCategoryClassifier.kt` (Эвристический классификатор не-прототипов) | CLASSIFY-P1-001 | DONE |
| CLASSIFY-P1-005 | `SemanticClassifierImpl.kt` (Координатор фасад двухфазной классификации) | CLASSIFY-P1-001..004 | DONE |

#### 4. Модуль `:extract:finance` (Чистый Kotlin JVM + RE2/J) — ЗАВЕРШЁН (100% GREEN, Golden Suite)
| ID | Задача / Файл | Зависит от | Статус |
|---|---|---|---|
| EXTRACT-P1-001 | `RegionalTextSanitizer.kt` (Нормализация NBSP, диакритик, усечение 1024) | MODEL-P1-ALL | DONE |
| EXTRACT-P1-002 | `AmountParser.kt` (Рукописный O(n) парсер сумм в minor units Long) | MODEL-P1-ALL | DONE |
| EXTRACT-P1-003 | `BankCurrencyResolver.kt` (Контекстный резолвер RUP / MDL / EUR / USD / RUB) | MODEL-P1-ALL | DONE |
| EXTRACT-P1-004 | `CircuitBreaker.kt` (Предохранитель budget 50ms, threshold 3, cooldown 10m) | MODEL-P1-ALL | DONE |
| EXTRACT-P1-005 | `ApbNotificationExtractor.kt` (Экстрактор Агропромбанк ПМР на RE2/J) | EXTRACT-P1-001..003 | DONE |
| EXTRACT-P1-006 | `PrisbankNotificationExtractor.kt` (Экстрактор Приднестровский Сбербанк на RE2/J) | EXTRACT-P1-001..003 | DONE |
| EXTRACT-P1-007 | `MaibNotificationExtractor.kt` (Экстрактор MAIB Молдова с детекцией DECLINED) | EXTRACT-P1-001..003 | DONE |
| EXTRACT-P1-008 | `BankSmsExtractor.kt` (Fallback-экстрактор банковских SMS: 900, APB, MAIB) | EXTRACT-P1-001..003 | DONE |
| EXTRACT-P1-009 | `IsolatedExtractorRunner.kt` (Песочница запуска экстракторов с CircuitBreaker) | EXTRACT-P1-004..008 | DONE |

#### 5. Модуль `:ui:timeline` (Jetpack Compose Material 3) — ЗАВЕРШЁН (100% GREEN, 22 теста)
| ID | Задача / Файл | Зависит от | Статус |
|---|---|---|---|
| UI-P1-001 | `FinancialTransactionUiModel.kt` (UI-модели транзакций и статусов) | MODEL-P1-ALL | DONE |
| UI-P1-002 | `EventUiModel.kt` (Обогащение EventUiModel категорией, движком и транзакцией) | UI-P1-001 | DONE |
| UI-P1-003 | `EventUiMapper.kt` (Маппинг событий и транзакций с форматированием знаков и сумм) | UI-P1-002 | DONE |
| UI-P1-004 | `TimelineViewModel.kt` (Объединение потоков observeEvents и observeTransactions) | STORAGE-P1-ALL, UI-P1-003 | DONE |
| UI-P1-005 | `CategoryBadge.kt` (Compose бейдж категории с иконкой движка и кликом) | UI-P1-002 | DONE |
| UI-P1-006 | `TransactionCard.kt` (Compose карточка финансовой транзакции с индикацией отказа) | UI-P1-001 | DONE |
| UI-P1-007 | `CategoryCorrectionDialog.kt` (Диалог ручной коррекции категории в 1 клик) | MODEL-P1-001 | DONE |
| UI-P1-008 | `EventDetailsDialog.kt` (Детальный диалог события с транзакцией и raw JSON) | UI-P1-005..007 | DONE |
| UI-P1-009 | `TimelineScreen.kt` (Интеграция чипов фильтрации, бейджей, карточек и диалогов) | UI-P1-004..008 | DONE |

---

## Фаза 2: «Конструктор конвейеров: Декларативный DSL, компилятор, SPI узлов, Hot Swap и Replay»
Текущий этап: **GATE 8 PASSED (Развёрнуто на Poco M7 2440cbe2, миграция Room v3 успешна, база догфудинга сохранена, сервис активен)** ✅  
Архитектурный мастер-документ: [`.sdd/architecture_phase2.md`](file:///c:/Users/DaniilTuT/Documents/antigravity/vibrant-hawking/.sdd/architecture_phase2.md)

### Очередь спецификаций и контрактов Фазы 2 (GATE 2 & GATE 3 PASSED ✅)

| ID | Задача / Спецификация | Файл | Зона | Зависит от | Исполнитель | Статус |
|---|---|---|---|---|---|---|
| ARCH-P2 | Разработка architecture_phase2.md с Claude Opus 5.5 | .sdd/architecture_phase2.md | arch | Phase 1.1 | architect | DONE |
| SPEC-P2-DSL | Спецификация схемы DSL v1 (PipelineDefinition, Triggers, Conditions, Transforms, Actions) | .sdd/specs/pipeline-dsl/overview.md | pipeline-dsl | ARCH-P2 | spec-dsl | DONE |
| SPEC-P2-COMPILER | Спецификация компилятора и валидатора (PipelineDefinition -> CompiledPipeline, Diagnostics) | .sdd/specs/pipeline-compiler/overview.md | pipeline-compiler | SPEC-P2-DSL | spec-compiler | DONE |
| SPEC-P2-SPI | Спецификация SPI каталога узлов (NodeSpec, NodeExecutor, встроенные узлы Фазы 1.1) | .sdd/specs/pipeline-spi/overview.md | pipeline-spi | SPEC-P2-DSL | spec-spi | DONE |
| SPEC-P2-RUNTIME | Спецификация рантайма и горячей подмены (ActivePipelineProvider, AtomicReference) | .sdd/specs/pipeline-runtime/overview.md | pipeline-runtime | SPEC-P2-COMPILER, SPEC-P2-SPI | spec-runtime | DONE |
| SPEC-P2-STORE | Спецификация хранения и Room миграции v2 -> v3 (таблицы определений, ревизий, бэкап) | .sdd/specs/pipeline-store/overview.md | pipeline-store | SPEC-P2-DSL | spec-store | DONE |
| SPEC-P2-REPLAY | Спецификация симуляции и Replay Engine (прогон по истории 7 дней, diff, sandbox) | .sdd/specs/pipeline-replay/overview.md | pipeline-replay | SPEC-P2-RUNTIME, SPEC-P2-STORE | spec-replay | DONE |
| P2-CONTRACTS | Синхронизация и заморозка 5 межзонных контрактов (FROZEN v3) | .sdd/contracts/ | contracts | ALL-SPECS | integrator | DONE |

---

### Декомпозиция задач Фазы 2 (GATE 4: DECOMPOSITION — PASSED ✅)
Декомпозиция спецификаций и контрактов Фазы 2 выполнена Спецификаторами соответствующих зон строго по шаблону §6 (1 функция/структура на задачу в 1 файле, без циклов в DAG). Всего сформировано 45 задач.

| Зона | Модуль | Префикс задач | Кол-во карточек | Статус декомпозиции |
|---|---|---|---|---|
| `infra` | Корневой Gradle | `INFRA-P2-xxx` | 1 | DONE |
| `zone/pipeline-dsl` | `:pipeline:dsl` | `DSL-P2-xxx` | 8 | DONE |
| `zone/pipeline-spi` | `:pipeline:nodes-api` | `SPI-P2-xxx` | 12 | DONE |
| `zone/pipeline-compiler` | `:pipeline:compiler` | `COMPILER-P2-xxx` | 8 | DONE |
| `zone/pipeline-runtime` | `:pipeline:runtime` | `RUNTIME-P2-xxx` | 5 | DONE |
| `zone/pipeline-store` | `:core:storage` | `STORE-P2-xxx` | 6 | DONE |
| `zone/pipeline-replay` | `:feature:replay` | `REPLAY-P2-xxx` | 5 | DONE |

---

### Очередь задач реализации Фазы 2 (DAG)

| ID | Задача / Файл | Зона | Зависит от | Исполнитель | Статус |
|---|---|---|---|---|---|
| **INFRA-P2-001** | Каркасы 5 модулей в settings.gradle.kts и build.gradle.kts | infra | GATE 4 | coder-infra | DONE |
| **DSL-P2-001** | `TriggerDefinition.kt` (Notification, Sms, Media) | dsl | INFRA-P2-001 | coder-dsl | DONE |
| **DSL-P2-002** | `ConditionDefinition.kt` (10 типов предикатов) | dsl | INFRA-P2-001 | coder-dsl | DONE |
| **DSL-P2-003** | `TransformDefinition.kt` (5 узлов трансформации) | dsl | INFRA-P2-001 | coder-dsl | DONE |
| **DSL-P2-004** | `ActionDefinition.kt` (5 действий, SetCategory, Tx...) | dsl | INFRA-P2-001 | coder-dsl | DONE |
| **DSL-P2-005** | `StageDefinition.kt` (Линейный шаг с Gate Condition) | dsl | DSL-P2-002..004 | coder-dsl | DONE |
| **DSL-P2-006** | `PipelineDefinition.kt` (Корневой AST schemaVersion=1) | dsl | DSL-P2-001,005 | coder-dsl | DONE |
| **DSL-P2-007** | `PipelineJsonCodec.kt` (JSON кодек kotlinx.serialization) | dsl | DSL-P2-006 | coder-dsl | DONE |
| **DSL-P2-008** | `LegacyPipelinePreset.kt` (Встроенный пресет legacy-1.1) | dsl | DSL-P2-006,007 | coder-dsl | DONE |
| **QA-P2-DSL** | Тестовый сьют схемы DSL v1 (47 тестов: 100% PASS) | dsl | DSL-P2-001..008 | qa-engineer | DONE |
| **SPI-P2-001** | `Bank.kt` (Регистровые банки: LONG, DOUBLE, REF, TEXT) | spi | INFRA-P2-001 | coder-spi | DONE |
| **SPI-P2-002** | `TextRegister.kt` (Безаллокационный CharSequence регистр) | spi | INFRA-P2-001 | coder-spi | DONE |
| **SPI-P2-003** | `FrameLayout.kt` (Топология слотов и маски полей) | spi | SPI-P2-001 | coder-spi | DONE |
| **SPI-P2-004** | `StepResult.kt` (@JvmInline value class: NEXT, JUMP, HALT, FAIL) | spi | INFRA-P2-001 | coder-spi | DONE |
| **SPI-P2-005** | `EffectBuffer.kt` (SoA-буфер эффектов: mark, rollback, reset) | spi | SPI-P2-001 | coder-spi | DONE |
| **SPI-P2-006** | `Frame.kt` (Контекст потока с массивами и resetRefs) | spi | SPI-P2-002..005 | coder-spi | DONE |
| **SPI-P2-007** | `NodeExecutor.kt` (Интерфейс горячего пути zero-alloc) | spi | SPI-P2-004..006 | coder-spi | DONE |
| **SPI-P2-008** | `NodeSpec.kt` (NodeTraits, ParamSchema, PortSchema, BindContext) | spi | SPI-P2-007 | coder-spi | DONE |
| **SPI-P2-009** | `NodeRegistry.kt` (Потокобезопасный каталог на ConcurrentHashMap) | spi | SPI-P2-008 | coder-spi | DONE |
| **SPI-P2-010** | Встроенные исполнители условий (PackageMatch, Regex...) | spi | SPI-P2-007,008 | coder-spi | DONE |
| **SPI-P2-011** | Встроенные исполнители трансформаций (Sanitize, Amount...) | spi | SPI-P2-007,008 | coder-spi | DONE |
| **SPI-P2-012** | Встроенные исполнители действий (SetCategory, Tx, Drop...) | spi | SPI-P2-007,008 | coder-spi | DONE |
| **QA-P2-SPI** | Тестовый сьют регистрового рантайма и SPI узлов (46 тестов: 100% PASS) | spi | SPI-P2-001..012 | qa-engineer | DONE |
| **COMPILER-P2-001** | `CompilationDiagnostic.kt` (DiagnosticSeverity, Code, Span) | compiler | INFRA-P2-001 | coder-compiler | DONE |
| **COMPILER-P2-002** | `CompilationResult.kt` (Success, Failure с инвариантом ERROR) | compiler | COMPILER-P2-001 | coder-compiler | DONE |
| **COMPILER-P2-003** | `Pass1StructuralValidator.kt` (AST лимиты, arity, depth) | compiler | DSL-P2-ALL, COMPILER-P2-002 | coder-compiler | DONE |
| **COMPILER-P2-004** | `Pass2RegexValidator.kt` (RE2/J, ReDoS guard, literal lowering) | compiler | DSL-P2-ALL, COMPILER-P2-002 | coder-compiler | DONE |
| **COMPILER-P2-005** | `Pass3DataflowAnalyzer.kt` (Definite assignment, SymbolTable) | compiler | DSL-P2-ALL, COMPILER-P2-002 | coder-compiler | DONE |
| **COMPILER-P2-006** | `Pass4ControlFlowAnalyzer.kt` (DAG-ацикличность, dead stages) | compiler | DSL-P2-ALL, COMPILER-P2-002 | coder-compiler | DONE |
| **COMPILER-P2-007** | `CompiledPipeline.kt`, `CompiledStage.kt`, `Signal.kt` | compiler | SPI-P2-ALL, COMPILER-P2-002 | coder-compiler | DONE |
| **COMPILER-P2-008** | `PipelineCompilerImpl.kt` (Фасад 4 проходов и Lowering) | compiler | COMPILER-P2-003..007 | coder-compiler | DONE |
| **QA-P2-COMPILER** | Тестовый сьют 4-фазного компилятора и ReDoS защиты | compiler | COMPILER-P2-001..008 | qa-engineer | DONE |
| **RUNTIME-P2-001** | `ActivePipelineProvider.kt` (AtomicReference, atomic swap) | runtime | COMPILER-P2-007 | coder-runtime | DONE |
| **RUNTIME-P2-002** | `ExecutionContextPool.kt` (Безаллокационный пул фреймов) | runtime | SPI-P2-006 | coder-runtime | DONE |
| **RUNTIME-P2-003** | `TraceRing.kt` (Кольцевой буфер 128 записей) | runtime | COMPILER-P2-007 | coder-runtime | DONE |
| **RUNTIME-P2-004** | `NodeCircuitBreaker.kt` (Предохранитель: 3 сбоя / 5 мин) | runtime | INFRA-P2-001 | coder-runtime | DONE |
| **RUNTIME-P2-005** | `PipelineRuntimeEngine.kt` (Горячий цикл, Pinned Gen, 3-tier) | runtime | RUNTIME-P2-001..004 | coder-runtime | DONE |
| **QA-P2-RUNTIME** | Тестовый сьют горячего рантайма, swap и circuit breaker | runtime | RUNTIME-P2-001..005 | qa-engineer | DONE |
| **STORE-P2-001** | Room v3 сущности (Definition, Revision, Alert) | storage | DSL-P2-ALL | coder-store | DONE |
| **STORE-P2-002** | `EventEntity.kt` (Схема v3: pipeline_revision_id) | storage | STORE-P2-001 | coder-store | DONE |
| **STORE-P2-003** | `PreMigrationBackup.kt` 3.0 (Снапшот триады файлов перед v3) | storage | STORE-P2-001 | coder-store | DONE |
| **STORE-P2-004** | `MIGRATION_2_3.kt` (SQL миграция, автосид legacy-1.1) | storage | STORE-P2-001..003, DSL-P2-008 | coder-store | DONE |
| **STORE-P2-005** | `PipelineDao.kt` (DefinitionDao, RevisionDao, AlertDao) | storage | STORE-P2-001,002 | coder-store | DONE |
| **STORE-P2-006** | `PipelineRepositoryImpl.kt` (Save, Activate, GC, Alerts) | storage | STORE-P2-004,005 | coder-store | DONE |
| **QA-P2-STORE** | Тестовый сьют миграции Room v3 и репозитория (69 тестов: 100% PASS) | storage | STORE-P2-001..006 | qa-engineer | DONE |
| **REPLAY-P2-001** | `ReplayCriteria.kt`, `TimeRange.kt`, `ReplayStatus.kt` | replay | INFRA-P2-001 | coder-replay | DONE |
| **REPLAY-P2-002** | `ReplayDiffReport.kt`, `DiffMetrics.kt`, `Discrepancy` | replay | REPLAY-P2-001 | coder-replay | DONE |
| **REPLAY-P2-003** | `ReplayEventSourceDao.kt` (Keyset pagination чтение) | storage | STORE-P2-002 | coder-store | DONE |
| **REPLAY-P2-004** | `VirtualEffectEvaluator.kt` (Sandbox без SQLite мутаций) | replay | SPI-P2-005, REPLAY-P2-002 | coder-replay | DONE |
| **REPLAY-P2-005** | `ReplayEngineImpl.kt` (Потоковый симулятор Flow, quantiles) | replay | REPLAY-P2-001..004, RUNTIME-P2-005 | coder-replay | DONE |
| **QA-P2-REPLAY** | Тестовый сьют симуляции Replay Engine и Diff-отчётов (14 тестов: 100% PASS) | replay | REPLAY-P2-001..005 | qa-engineer | DONE |

---

### Очередь задач синхронизации UI с бэкендом (UI-SYNC)

| ID | Задача / Файл | Зона | Зависит от | Исполнитель | Статус |
|---|---|---|---|---|---|
| **UI-SYNC-001** | `Event.kt`, `EventMapper.kt` (Синхронизация модели данных) | model/storage | — | coder-data | DONE |
| **UI-SYNC-002** | `StorageGateway.kt`, `StorageGatewayImpl.kt` (getRawEvent) | storage | UI-SYNC-001 | coder-data | DONE |
| **UI-SYNC-003** | `EventUiMapper.kt`, `EventUiModel.kt` (Устранение regex-эвристик) | ui | UI-SYNC-001 | coder-ui | DONE |
| **UI-SYNC-004** | `TimelineViewModel.kt` (Асинхронная подгрузка RawEvent) | ui | UI-SYNC-002,003 | coder-ui | DONE |
| **UI-SYNC-005** | `TimelineItemRow.kt`, `EventDetailsDialog.kt` (Ревизии) | ui | UI-SYNC-003 | coder-ui | DONE |
| **UI-SYNC-006** | `TimelineScreen.kt`, `MainActivity.kt` (Настройки/HyperOS) | ui/app | UI-SYNC-005 | coder-ui | DONE |

---

## Очередь задач реализации Фазы 3 (Phase 3 DAG — GATE 3 PASSED, ALL READY)

### 1. Зона core:text (нормализатор, детерминированный Lexer O(N), лексиконы ru/ro/en)
| ID | Задача / Файл | Зона | Зависит от | Исполнитель | Статус |
|---|---|---|---|---|---|
| **P3-TXT-001** | `TextNormalizer.kt`, `OffsetMap.kt` (NFKC, диакритика, гомоглифы) | text | — | coder-text | DONE |
| **P3-TXT-002** | `Lexer.kt`, `TokenStream.kt` (FSM конечный автомат O(N), NUMBER clusters) | text | P3-TXT-001 | coder-text | DONE |
| **P3-TXT-003** | `LexiconRepository.kt`, `LexiconLoader.kt` (Словарь стем RU/RO/EN) | text | P3-TXT-001 | coder-text | DONE |
| **P3-TXT-004** | Golden-корпус: 150+ пушей, включая инцидент возврата TEMU (MAIB) | text | P3-TXT-002,003 | qa-engineer | IN_PROGRESS |

### 2. Зона extract:universal (кандидаты, Currency-Bound суммы, RoleAssignmentSolver, фоллбэк)
| ID | Задача / Файл | Зона | Зависит от | Исполнитель | Статус |
|---|---|---|---|---|---|
| **P3-UNI-001** | Генераторы кандидатов: `AmountCandidateGenerator` (Currency-bound ADR-302) | extract | P3-TXT-002 | coder-extract | DONE |
| **P3-UNI-002** | `FeatureExtractor.kt`, `ScoringConfig.kt` (Сигмоидный скоринг признаков) | extract | P3-UNI-001 | coder-extract | DONE |
| **P3-UNI-003** | `OpTypeResolver.kt` (Иерархия специфичности: DECLINED > REFUND > TRANSFER...) | extract | P3-TXT-003, P3-UNI-001 | coder-extract | DONE |
| **P3-UNI-004** | `RoleAssignmentSolver.kt` (Оптимальное разделение TX_AMOUNT vs BALANCE) | extract | P3-UNI-002 | coder-extract | DONE |
| **P3-UNI-005** | `SafetyGate.kt` (OTP & Promo Veto, Source Prior, пороги ACCEPT/SUGGEST) | extract | P3-UNI-003,004 | coder-extract | DONE |
| **P3-UNI-006** | `SourceProfileRegistry.kt` (Разрешение неоднозначностей RUB vs RUP) | extract | P3-UNI-005 | coder-extract | DONE |
| **P3-UNI-007** | `ExtractUniversalNode.kt` (Регистрация в SPI и рантайме конвейера, breaker) | runtime | P3-UNI-005,006 | coder-runtime | DONE |
| **P3-UNI-008** | Провенанс и обратная связь: `ExtractionFeedbackEntity`, `financeWithoutPayload` | storage | P3-UNI-007 | worker (db1b8a5f) | DONE |

### 3. Зона induction (SafeFragments, TemplateBuilder RE2/J, валидация компилятором)
| ID | Задача / Файл | Зона | Зависит от | Исполнитель | Статус |
|---|---|---|---|---|---|
| **P3-IND-001** | `TokenSegmenter.kt` (Разбиение токенов на LITERAL, SLOT, VARIABLE) | induction | P3-TXT-002 | coder-inducer | DONE |
| **P3-IND-002** | `SafeFragments.kt`, `TemplateBuilder.kt` (Сборка ограниченных RE2/J регулярок) | induction | P3-IND-001 | coder-inducer | DONE |
| **P3-IND-003** | `RightBoundedRule.kt`, `AnchorSelector.kt` (Правила правой границы и хвоста) | induction | P3-IND-002 | coder-inducer | DONE |
| **P3-IND-004** | `TemplateLint.kt`, `compileTemplate` (Проверка сложности в PipelineCompiler) | compiler | P3-IND-002 | coder-compiler | DONE |
| **P3-IND-005** | `RoundTripValidator.kt` (100% совпадение разметки на исходном образце) | induction | P3-IND-003,004 | coder-inducer | DONE |
| **P3-IND-006** | `ReplayValidator.kt` (Positive, Negative, Conflict симуляция на ReplayEngine) | replay | P3-IND-005 | coder-replay | DONE |
| **P3-IND-007** | `TemplateCanonicalHasher.kt`, `TemplateDeduplicator.kt` (Дедупликация) | induction | P3-IND-005 | coder-inducer | DONE |

### 4. Зона storage & runtime (миграция Room v3 -> v4, TemplateBankDao, TransactionProvenance)
| ID | Задача / Файл | Зона | Зависит от | Исполнитель | Статус |
|---|---|---|---|---|---|
| **P3-STORE-001** | Room v4 миграция (`MIGRATION_3_4.kt`), `DynamicTemplateEntity`, индексы | storage | P3-IND-004 | coder-store | DONE |
| **P3-STORE-002** | `StorageGateway.observeAggregatedTotals` (Мультивалютная SQL агрегация) | storage | P3-STORE-001 | coder-store | DONE |
| **P3-RUNTIME-001** | `RuntimeGeneration.kt`, `ActiveGenerationProvider` (Атомарная CAS подмена) | runtime | P3-STORE-001 | coder-runtime | DONE |
| **P3-RUNTIME-002** | `TemplateBankNodeExecutor.kt` (Узел `extract.template_bank`, префильтр O(1)) | runtime | P3-RUNTIME-001 | coder-runtime | DONE |
| **P3-RUNTIME-003** | `TemplateBankManagerImpl.kt` (FSM жизненного цикла, Mutex транзакции) | storage | P3-STORE-001, P3-RUNTIME-001 | coder-store | DONE |
| **P3-RUNTIME-004** | `TemplateHealthMonitor.kt` (Автокарантин при сбоях, промоушен SHADOW) | runtime | P3-RUNTIME-002,003 | coder-runtime | DONE |
| **P3-RUNTIME-005** | `OrchestratorProbeImpl.kt` (Lock-Free Seqlock чтение TraceRing 128) | runtime | P3-RUNTIME-001 | coder-runtime | DONE |
| **P3-RUNTIME-006** | `TemplateBackfillEngine.kt` (Идемпотентный бэкфилл истории с защитой USER_EDITED) | replay | P3-RUNTIME-003 | worker (6e9d8e30) | DONE |

### 5. Зона feature:editor (One-Tap интерактивный чип-редактор шаблонов в EventDetailsDialog)
| ID | Задача / Файл | Зона | Зависит от | Исполнитель | Статус |
|---|---|---|---|---|---|
| **P3-UI-001** | Каркас MVI Store, BaseViewModel, Kotlin Immutable Collections | domain | — | coder-ui | DONE |
| **P3-UI-002** | `TokenGridView.kt`, `RoleSelectionMenu.kt` (Интерактивная токенная сетка) | ui | P3-UI-001, P3-TXT-002 | coder-ui | DONE |
| **P3-UI-003** | `EditorViewModel.kt` (Живая валидация с debounce 150ms, дифф конфликтов) | ui | P3-UI-002, P3-IND-006 | coder-ui | DONE |
| **P3-UI-004** | `TemplateEditorSheet.kt`, интеграция в `EventDetailsDialog` (One-Tap Save, Undo) | ui | P3-UI-003, P3-RUNTIME-003 | coder-ui | DONE |

### 6. Зона feature:analytics (мультивалютный дашборд MDL/RUP/USD/EUR/RUB, Canvas-графики 60fps)
| ID | Задача / Файл | Зона | Зависит от | Исполнитель | Статус |
|---|---|---|---|---|---|
| **P3-UI-005** | `FinancialAnalyticsScreen.kt` (Мультивалютные карточки MDL, RUP, USD, EUR, RUB) | ui | P3-UI-001, P3-STORE-002 | coder-ui | DONE |
| **P3-UI-006** | `AnalyticsTrendChart.kt` (Canvas 60 FPS графики, Paging 3 список транзакций) | ui | P3-UI-005 | coder-ui | DONE |

### 7. Зона feature:diagnostics (экран очередей, TraceRing seqlock, CircuitBreaker статусы)
| ID | Задача / Файл | Зона | Зависит от | Исполнитель | Статус |
|---|---|---|---|---|---|
| **P3-UI-007** | `OrchestratorDiagnosticsScreen.kt` (Мониторинг очередей, TraceRing, сброс Breaker) | ui | P3-UI-001, P3-RUNTIME-005 | worker (adca8603) | DONE |
| **P3-UI-008** | Baseline Profiles и Macrobenchmark на Poco M7 (Jank rate < 1%, Frame < 16ms) | benchmark | P3-UI-004,006,007 | qa-engineer | READY |

### 8. Зона QA (тестовые сьюты по каждой зоне)
| ID | Задача / Файл | Зона | Зависит от | Исполнитель | Статус |
|---|---|---|---|---|---|
| **P3-QA-TXT** | Тестовый сьют :core:text (нормализатор, FSM Lexer, лексиконы, OffsetMap) | qa | P3-TXT-ALL | qa-engineer | READY |
| **P3-QA-UNI** | Тестовый сьют :extract:universal (кандидаты, OpType, Solver, SafetyGate) | qa | P3-UNI-ALL | qa-engineer | READY |
| **P3-QA-IND** | Тестовый сьют :induction (Segmenter, SafeFragments, Lint, RoundTrip) | qa | P3-IND-ALL | qa-engineer | READY |
| **P3-QA-STORE** | Тестовый сьют :core:storage & :pipeline:runtime (MIGRATION_3_4, swap, Probe) | qa | P3-STORE-ALL, P3-RUNTIME-ALL | qa-engineer | READY |
| **P3-QA-EDITOR** | Тестовый сьют :feature:editor (MVI Store, токенная сетка, валидация, Undo) | qa | P3-UI-001..004 | qa-engineer | READY |
| **P3-QA-ANALYTICS**| Тестовый сьют :feature:analytics (карточки валют, периоды, Canvas, Paging) | qa | P3-UI-005,006 | qa-engineer | READY |
| **P3-QA-DIAGNOSTICS**| Тестовый сьют :feature:diagnostics (Probe snapshot 2Hz, сброс Breaker) | qa | P3-UI-007,008 | qa-engineer | READY |

### 9. Внеочередные задачи оптимизации и исправления (Goal Mandate: Deduplication & Slot Pipeline)
| ID | Задача / Файл | Зона | Зависит от | Исполнитель | Статус |
|---|---|---|---|---|---|
| **FIX-DUP-001** | Дедупликация транзакций (isUpdateOf + 5-мин окно кросс-нотификаций P2P/процессинга) | storage/app | — | worker (db1b8a5f) | DONE |
| **OPT-PIPE-001** | Конвейер слотовых регулярок (anchor + slot rules < 256 символов) | induction/runtime | — | worker (2e9f9e6f) | DONE |
| **UI-PAGE-001** | Динамическая подгрузка и бесконечный скролл в Timeline (Пагинация) | ui-timeline | — | worker (adca8603) | DONE |
| **UI-STUB-001** | Реальное сохранение шаблонов в БД и устранение заглушек | ui-timeline | — | worker (adca8603) | DONE |

### 10. Задачи по устранению архитектурных заглушек и разрывов (Codebase Audit Findings)
| ID | Задача / Файл | Зона | Уровень | Исполнитель | Статус |
|---|---|---|---|---|---|
| **AUDIT-001** | Восстановление IngestWatchdog в AppModule (замена dummy-заглушки) | app/di/receiver | CRITICAL | worker (8cd3489e) | DONE |
| **AUDIT-002** | Полноценная интеграция SMS Ingestion (DI в SmsReceiver, HiltWorker, submit в Orchestrator) | ingest/sms | CRITICAL | worker (8cd3489e) | DONE |
| **AUDIT-003** | Передача Media Session событий в EventProcessingOrchestrator | ingest/media | MAJOR | worker (8cd3489e) | DONE |
| **AUDIT-004** | Подключение копирования сырого JSON в EventDetailsDialog | ui/timeline | MAJOR | worker (adca8603) | DONE |
| **AUDIT-005** | Подключение экрана диагностики OrchestratorDiagnosticsScreen в навигацию MainActivity | app/ui | MAJOR | worker (adca8603) | DONE |
| **AUDIT-006** | Удаление неиспользуемого разрешения READ_CALENDAR из онбординга и манифеста | app/onboarding | MINOR | worker (8cd3489e) | DONE |
| **AUDIT-007** | Очистка неиспользуемого параметра onTransactionClick в FinancialAnalyticsScreen | ui/analytics | MINOR | worker (adca8603) | DONE |
| **AUDIT-008** | Интеграция TemplateBankManager в AppModule DI и MainActivity TimelineViewModel | app/di/ui | CRITICAL | worker (adca8603) | DONE |
| **AUDIT-009** | Запуск Backfill переобработки при сохранении шаблона для отображения в аналитике | ui/replay | CRITICAL | worker (adca8603) | DONE |
| **AUDIT-010** | Устранение тройного учета денег в FinancialAnalytics / Room DAO deduplication | core/storage/analytics | CRITICAL | worker (db1b8a5f) | DONE |
| **AUDIT-011** | Передача TemplateBankManager и DynamicTemplateDao в OrchestratorDiagnosticsScreen в MainActivity | app/ui/diagnostics | CRITICAL | worker (adca8603) | DONE |
| **AUDIT-012** | Смягчение индукции шаблонов (автоматические wildcard слоты для CARD_MASK и BALANCE, гибкие пробелы) | induction/editor | CRITICAL | worker (2e9f9e6f) | DONE |
| **AUDIT-013** | Gap-Aware Token Emission в TemplateBuilder (учёт расстояния между токенами, фикс TEMU.COM) | induction | CRITICAL | worker (2e9f9e6f) | DONE |
| **AUDIT-014** | Поддержка BALANCE_CURRENCY в RoleSelectionMenu и мультивалютное разделение слотов (Cross-Currency) | ui/induction | CRITICAL | worker (2e9f9e6f) | DONE |
| **AUDIT-015** | Domain Token Rule в FsmLexer.kt (неделимые домены TEMU.COM / мерчанты без http/www) | core/text | MAJOR | worker (2e9f9e6f) | DONE |
| **HOTFIX-ROOM-001** | Создание MIGRATION_4_5 и повышение версии Room до 5 (фикс краша integrity hash) | storage | CRITICAL | worker (3b5a8f4a) | DONE |

---

Статусы: `TODO → READY → IN_PROGRESS → IN_REVIEW → DONE`, а также `BLOCKED`, `STALE`








