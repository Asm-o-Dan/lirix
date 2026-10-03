## Задача MODEL-P1-011: Реализовать интерфейсы SemanticClassifier и PackageGatedRouter

**Модуль:** `:core:model`  
**Целевой файл:** 
- `core/model/src/main/kotlin/com/example/npc/core/model/classify/SemanticClassifier.kt`
- `core/model/src/main/kotlin/com/example/npc/core/model/classify/PackageGatedRouter.kt`  
**Спецификация:** `.sdd/specs/core-model/overview.md#41-engine-classificationresult`  
**Контракт:** `.sdd/contracts/core-model__classify.md`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.model.classify

import com.example.npc.core.model.Event
import com.example.npc.core.model.SourceId

interface SemanticClassifier {
    fun classify(event: Event, prototype: UserPrototype?): ClassificationResult
}

interface PackageGatedRouter {
    fun canClassifyAsFinance(packageName: String, senderOrTitle: String?, sourceId: SourceId): Boolean
    fun isMessengerBlacklisted(packageName: String): Boolean

    companion object {
        val BANK_PACKAGES: Set<String>
        val BANK_SMS_SENDERS: Set<String>
        val MESSENGER_PACKAGES: Set<String>
    }
}
```

### Инварианты и алгоритм:
1. `SemanticClassifier`: чистая функция `classify`, принимающая иммутабельный `Event` и опциональный `UserPrototype`. Не зависит от I/O и корутин.
2. `PackageGatedRouter`: объявляет константные множества доверенных банковских пакетов, доверенных SMS-отправителей и заблокированных мессенджеров.

### Критерии приемки (DoD):
- [ ] Интерфейсы скомпилированы в `:core:model` без зависимостей от Android SDK и Room.
- [ ] Константы списков `BANK_PACKAGES`, `BANK_SMS_SENDERS`, `MESSENGER_PACKAGES` доступны для внешних потребителей.
