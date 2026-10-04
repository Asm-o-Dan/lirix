# Задача TASK-PURGE-01: Удаление 49 файлов устаревшего мультидоменного балласта

**Файл:** Реестр 49 файлов из [architecture.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/architecture.md#L370-L450)
**Спека:** [architecture.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/architecture.md) (Раздел 6), ADR-002, ADR-003

## Контекст и цель
Полная очистка проекта от банковских, финансовых, учебных, ONNX ML и устаревших UI экранов в рамках перехода на Music & Lyrics Hub.
Все эти файлы уже сохранены в архивной ветке `legacy-full-engine`.

## Список на удаление:
1. **Финансы:**
   - `app/src/main/java/com/eventengine/app/feature/finance/FinanceFeatureEngine.kt`
   - `app/src/main/java/com/eventengine/app/feature/finance/FinancialDirection.kt`
   - `app/src/main/java/com/eventengine/app/storage/FinancialTransactionDao.kt`
   - `app/src/main/java/com/eventengine/app/storage/FinancialTransactionEntity.kt`
   - `app/src/main/java/com/eventengine/app/ui/FinanceScreen.kt`
2. **Учёба (Moodle):**
   - `app/src/main/java/com/eventengine/app/feature/study/StudyFeatureEngine.kt`
   - `app/src/main/java/com/eventengine/app/storage/StudyItemDao.kt`
   - `app/src/main/java/com/eventengine/app/storage/StudyItemEntity.kt`
   - `app/src/main/java/com/eventengine/app/storage/StudyItemEnums.kt`
   - `app/src/main/java/com/eventengine/app/ui/StudyScreen.kt`
3. **ML, ONNX, Классификаторы, Правила:**
   - `app/src/main/java/com/eventengine/app/feature/rules/DeclarativeRulesEngine.kt`
   - `app/src/main/java/com/eventengine/app/storage/RuleEntity.kt`
   - `app/src/main/java/com/eventengine/app/storage/RuleDao.kt`
   - `app/src/main/java/com/eventengine/app/ui/RulesScreen.kt`
   - `app/src/main/java/com/eventengine/app/classifier/RuleClassifier.kt` (банковская часть / не-медиа)
   - `app/src/main/java/com/eventengine/app/classifier/MockJevAdapter.kt`
   - `app/src/main/java/com/eventengine/app/classifier/JevClassifier.kt`
   - `app/src/main/java/com/eventengine/app/classifier/CompositeConfidenceEngine.kt`
   - `app/src/main/java/com/eventengine/app/classifier/SimilarityEngine.kt`
   - `app/src/main/java/com/eventengine/app/classifier/SeedPrototypeBank.kt`
   - `app/src/main/java/com/eventengine/app/ml/OnnxRuntimeMobile.kt`
4. **Устаревшие тесты, не относящиеся к музыке:**
   - Финансовые тесты, тесты правил, тесты классификаторов покупок/переводов.

## Критерий приёмки:
- Указанные файлы удалены из файловой структуры.
- Проект не имеет синтаксических ошибок, связанных с импортами удалённых файлов (вызовы зачищены либо изолированы).
