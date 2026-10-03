## Задача CLASSIFY-P1-001: Реализовать PackageGatedRouter

**Модуль:** `:classify:rules`  
**Целевой файл:** `classify/rules/src/main/kotlin/com/example/npc/classify/rules/PackageGatedRouter.kt`  
**Спецификация:** `.sdd/specs/classify-rules/overview.md#3-строгий-пакетный-роутинг-packagegatedrouter`  
**Контракт:** `.sdd/contracts/core-model__classify.md`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.classify.rules

import com.example.npc.core.model.SourceId
import com.example.npc.core.model.classify.PackageGatedRouter

class PackageGatedRouterImpl : PackageGatedRouter {
    override fun canClassifyAsFinance(packageName: String, senderOrTitle: String?, sourceId: SourceId): Boolean
    override fun isMessengerBlacklisted(packageName: String): Boolean
}
```

### Инварианты и алгоритм:
1. `isMessengerBlacklisted`: проверяет вхождение пакета в черный список мессенджеров (`com.radolyn.ayugram`, `org.telegram.messenger`, `com.whatsapp`, `com.viber.voip`, `com.vkontakte.android` и др.). Если в списке $\to$ `true`.
2. `canClassifyAsFinance`:
   - Если `isMessengerBlacklisted(packageName)` $\to$ немедленно `false` (жесткая аппаратная блокировка).
   - Если `sourceId == SourceId.MEDIA` $\to$ `false`.
   - Если `packageName in BANK_PACKAGES` (`com.apb.mobile`, `com.prisbank.app`, `md.maib.maibank`) $\to$ `true`.
   - Если `packageName in SMS_PACKAGES` или `sourceId == SourceId.SMS`:
     - Проверяет `senderOrTitle?.uppercase() in BANK_SMS_SENDERS` (`"APB"`, `"AGROPROMBANK"`, `"PRISBANK"`, `"SBERBANK"`, `"MAIB"`, `"900"`).
     - Если совпал $\to$ `true`, иначе `false`.
   - Для всех прочих пакетов $\to$ `false`.

### Критерии приемки (DoD):
- [ ] Пуш турагентства InTour из AyuGram (`com.radolyn.ayugram`) возвращает `false` (устранение бага Фазы 0).
- [ ] Пуш Яндекс.Погоды возвращает `false`.
- [ ] Пуши Агропромбанка, Сбербанка и MAIB возвращают `true`.
- [ ] SMS от "900" или "APB" возвращает `true`, а от личного номера — `false`.
- [ ] 100% покрытие unit-тестами.
