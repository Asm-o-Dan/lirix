# Задача PIPE-004: Реализовать Hilt DI модули конвейера

**Файлы:**
- `app/src/main/kotlin/com/example/npc/app/di/ClassifierModule.kt` (создать)
- `app/src/main/kotlin/com/example/npc/app/di/ExtractorModule.kt` (создать)
- `app/src/main/kotlin/com/example/npc/app/di/OrchestratorModule.kt` (создать)
- `app/src/main/kotlin/com/example/npc/app/pipeline/OrchestratorProvider.kt` (создать)
**Спека:** `.sdd/specs/app-pipeline/overview.md#секция-6-и-7`

## Содержимое:
1. `ClassifierModule`: `@Module @InstallIn(SingletonComponent::class)`:
   - `providePackageGatedRouter(): PackageGatedRouter`
   - `provideRuleBasedCategoryClassifier(router): RuleBasedCategoryClassifier`
   - `provideSemanticClassifier(router, ruleClassifier): SemanticClassifier`
2. `ExtractorModule`:
   - `provideFinanceExtractors(): List<FinanceExtractor>`
   - `provideCircuitBreaker(): CircuitBreaker` (budget 50ms, threshold 3, cooldown 10m)
   - `provideIsolatedExtractorRunner(extractors, circuitBreaker): IsolatedExtractorRunner`
3. `OrchestratorModule`:
   - `provideEventProcessingOrchestrator(...) : EventProcessingOrchestrator`
4. `OrchestratorProvider`:
   - `interface OrchestratorProvider { fun provideOrchestrator(): EventProcessingOrchestrator }`

## Критерий приёмки:
- Успешная компиляция: `./gradlew.bat :app:compileDebugKotlin`.
