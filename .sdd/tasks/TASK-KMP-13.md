# Задача TASK-KMP-13: Сборка дистрибутива Windows MSI / EXE (packageMsi)

- **ID задачи:** `TASK-KMP-13`
- **Роль исполнителя:** Release Engineer / DevOps
- **Зона:** `release`
- **Файлы:**
  - `desktop/build.gradle.kts` (compose.desktop { application { nativeDistributions { ... } } })
  - `desktop/package/windows/lirix.ico`
  - `.github/workflows/desktop-release.yml`
- **Зависит от:** `TASK-KMP-11`, `TASK-KMP-12`
- **Основание:** [ADR-020](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/decisions.md#adr-020)
- **Приоритет:** HIGH

---

## 1. Цель задачи
Настроить упаковку десктопного приложения в автономный Windows установщик:
1. Конфигурация Compose Multiplatform `nativeDistributions`:
   - Форматы: `Msi`, `Exe`.
   - Включение нативного бинарника `lirix-media-bridge.exe` в bundle приложения (`appResourcesRootDir`).
   - Иконка `lirix.ico`, UUID инсталлятора, ассоциации файлов.
2. Проверка сборки установщика локально: `./gradlew :desktop:packageMsi` или `:desktop:packageUberJarForCurrentOS`.
3. Формирование релизного артефакта `Lirix-1.0.0-windows-x64.msi`.

---

## 2. Критерии приемки (DoD)
- [ ] Команда `./gradlew :desktop:packageMsi` (или `:desktop:packageDistributionForCurrentOS`) собирает рабочий установщик без ошибок.
- [ ] Установщик корректно инсталлирует Lirix в `Program Files` или `AppData\Local`, создавая ярлык на рабочем столе и в меню Пуск.
- [ ] Приложение запускается из установленной папки и автоматически запускает `lirix-media-bridge.exe`.
