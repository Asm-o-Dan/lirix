# TASK-CON-WIN-12: Windows Firewall & UDP Port Rule Manager

## 1. Контекст и Описание задачи
В соответствии со спецификацией **`SPEC-CONNECT-01 (§6)`**, для устойчивой работы P2P Iroh QUIC (`ALPN lirix/connect/1`) и трансляции Audio Cast в локальной сети Windows Defender Firewall не должен блокировать входящие UDP пакеты приложения.
Необходимо реализовать системный утилитарный модуль для Desktop (`WindowsFirewallManager`), проверяющий наличие разрешающего правила для Lirix в Windows Firewall и предоставляющий понятную инструкцию или автоматический запуск PowerShell / netsh команды при первом запуске / необходимости.

---

## 2. Архитектурные требования
1. **Проверка состояния брандмауэра**:
   - Метод `checkFirewallRuleExists(): FirewallStatus` (через вызов `netsh advfirewall firewall show rule name="Lirix Connect"` или PowerShell `Get-NetFirewallRule`).
2. **Запрос / добавление правила**:
   - Формирование команды добавления входящего UDP правила:
     `netsh advfirewall firewall add rule name="Lirix Connect" dir=in action=allow protocol=UDP program="<PathToExeOrJvm>" profile=private,domain`
   - Диалог / подсказка пользователю при статусе `BLOCKED` или `NOT_CONFIGURED` с кнопкой «Разрешить в Брандмауэре Windows» (с UAC elevation при необходимости) или инструкцией для ручной настройки.
3. **Graceful Fallback**:
   - Если запуск происходит не на Windows или нет прав администратора, приложение не крашится, а логирует предупреждение и переключается на relay-режим.

---

## 3. Целевые файлы реализации
- `desktop/src/main/kotlin/com/lirix/app/desktop/system/WindowsFirewallManager.kt`
- `desktop/src/test/kotlin/com/lirix/app/desktop/WindowsFirewallManagerTest.kt`

---

## 4. Критерии приёмки (DoD)
1. Реализован `WindowsFirewallManager` с безопасным выполнением CLI-проверок без зависания UI потока (`withContext(Dispatchers.IO)`).
2. Написаны unit-тесты (с мокированием вывода командной строки).
3. `./gradlew.bat :desktop:compileKotlin` и `./gradlew.bat :desktop:test` проходят успешно.
