# Intake: Дефект управления воспроизведением Telegram / AyuGram (Play/Pause не срабатывает)

## 1. Описание Проблемы
Пользователь сообщает:
«не со всеми медиа сессиями получается взаимодействовать тот же телеграмм не реагирует, я жму на паузу а воспроизведение не останавливается»

## 2. Симптомы
- При нажатии на кнопку паузы в Lirix воспроизведение в Telegram / AyuGram продолжается и не останавливается.
- При этом треки из Яндекс Музыки могут работать корректно.

## 3. Гипотезы для проверки (Архитектору / Debug-Detective)
1. Telegram / AyuGram регистрирует несколько параллельных сессий (`MediaSessionHelper/42`, `telegramAudioPlayer/41`, `MediaSessionHelper/16`).
2. Команда `transportControls.pause()` отправляется не тому контроллеру (например, сессии-пустышке `MediaSessionHelper`, а не `telegramAudioPlayer`).
3. Telegram может требовать отправку через `MediaSessionManager.dispatchMediaKeyEvent` или через его зарегистрированный `mediaButtonReceiver` (`org.telegram.messenger.MusicPlayerReceiver`).
4. При нажатии в UI `NowPlayingScreen.kt` флаг `isCurrentlyPlaying` или локальное состояние `localIsPlaying` рассинхронизировано, из-за чего вместо `pause()` вызывается `play()`.
5. `MediaSessionCollector.activeMediaControllers` перезаписывается неактивной сессией при коллбэках `onMetadataChanged` / `onPlaybackStateChanged`.

## 4. Критерии готовности (DoD)
- Найдена точная первопричина (root-cause).
- Спроектировано решение, гарантирующее остановку воспроизведения во всех сессиях Telegram/AyuGram.
- Написаны unit/интеграционные тесты или проверочный скрипт.
- Проверено на реальном устройстве через ADB.
