## Задача MODEL-P1-008: Реализовать перечисление Engine

**Модуль:** `:core:model`  
**Целевой файл:** `core/model/src/main/kotlin/com/example/npc/core/model/classify/Engine.kt`  
**Спецификация:** `.sdd/specs/core-model/overview.md#41-engine-classificationresult`  
**Контракт:** `.sdd/contracts/core-model__classify.md`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.model.classify

enum class Engine {
    NONE,       // Категория не назначена
    PROTOTYPE,  // Назначено по базе подтвержденных пользовательских прототипов (supportCount >= 2)
    RULES,      // Назначено статическим детерминированным правилом (эвристика)
    USER;       // Назначено прямой ручной правкой пользователя в UI (Feedback Loop)

    companion object {
        fun fromStringOrDefault(raw: String?, default: Engine = NONE): Engine
    }
}
```

### Инварианты и алгоритм:
1. `fromStringOrDefault`:
   - Сопоставление без учета регистра: "PROTOTYPE" $\to$ `PROTOTYPE`, "RULES" $\to$ `RULES`, "USER" $\to$ `USER`, "NONE" $\to$ `NONE`.
   - При null, пустой строке или неизвестном значении возвращает переданный `default` (по умолчанию `NONE`).

### Критерии приемки (DoD):
- [ ] 100% покрытие тестами парсинга всех значений и некорректных строк.
