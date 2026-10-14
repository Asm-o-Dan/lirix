# Исходный бриф: Портирование Lirix на Windows (Compose Multiplatform Desktop)

- **Дата:** 2026-10-15
- **Цель:** Создать десктопную версию Lirix для Windows 10/11 с максимальным переиспользованием существующей кодовой базы Android-версии.
- **Стратегия:** Внедрение архитектуры Kotlin Multiplatform (KMP) + Compose Multiplatform for Desktop.

---

## 1. Ключевые требования

### 1.1. Максимальное сохранение и шаринг существующего кода (до 75%)
1. **Дизайн-система и темы:**
   - Полное сохранение 4 дизайн-палитр (`Spotify`, `Yandex`, `Obsidian`, `Pastel`) через `LirixThemeTokens.colors`.
   - Полное сохранение винилового проигрывателя `AnalogTurntable` со сворачиванием, вращением и кинетическим тонармом при Play/Pause.
   - Единый компонент синхронизированного караоке-плеера LRC с плавной прокруткой и подсветкой активной строки.
   - Компонент аккордов с автопрокруткой и аппликатурами на грифе гитары.
2. **Бизнес-логика и движки ядра (Core Engines):**
   - Движок таймкодов караоке `LrcSyncEngine`.
   - Парсеры аккордов и текстов `AmDmChordParser`, `MyChordsParser`.
   - Движок черного списка `BlacklistMatcher` и правила фильтрации.
   - Аналитика `WrappedStatsEngine` и система ачивок `GamificationEngine`.

### 1.2. Специфика платформы Windows (Desktop Ingress & System Features)
1. **Перехват воспроизведения (Windows GSMTC Ingress):**
   - Интеграция с `Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager` через JNA / WinRT.
   - Перехват любых запущенных плееров: Spotify, Яндекс Музыка, VK Музыка, Telegram Desktop, AIMP, Foobar, Chrome, Edge, Firefox, Opera (YouTube, стримы).
   - Двунаправленный транспорт: Play/Pause, Seek, SkipNext, SkipPrevious.
   - Захват обложки трека в максимальном качестве.
2. **Windows Desktop UX:**
   - Click-Through прозрачный плавающий HUD караоке поверх игр и рабочего стола (`WS_EX_TRANSPARENT`).
   - Иконка в системном трее (System Tray) со статусом и быстрым меню.
   - Глобальные горячие клавиши (Global Hotkeys) для управления и быстрого добавления в черный список.
   - Хранение локальной базы данных SQLite через multiplatform SQLite / Room Multiplatform.

---

## 2. Этапы реализации (SDD Roadmap)
1. **Gate 1:** Архитектурный проект модульности KMP (`:shared`, `:app`, `:desktop`) и ADR-020.
2. **Gate 2:** Выделение `:shared` модуля с чистым Kotlin (движки, токены, общие Compose-компоненты).
3. **Gate 3:** Спецификация Windows Ingress (GSMTC / WinRT через JNA).
4. **Gate 4:** Десктопный модуль `:desktop` с Compose Desktop Window, системным треем и плавающим HUD.
5. **Gate 5:** Сборка Windows `.msi` / `.exe` через `packageMsi` / `packageExe`.
