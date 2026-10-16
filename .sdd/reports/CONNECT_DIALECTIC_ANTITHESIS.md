# Connect: Antithesis — 2026-10-16

Тезис проверен статически по реальному runtime-коду: «Connect обеспечивает рабочее и безопасное Android ↔ Windows соединение». Продуктовый код не изменялся, тесты не запускались. Ниже подтвержденные дефекты; сетевые атаки и акустические измерения не выполнялись.

Все пути ниже абсолютные; общий корень: `C:/Users/DaniilTuT/Documents/antigravity/quick-nobel/`.

## P1 — Обход аутентификации управляющего канала и pairing

- `C:/Users/DaniilTuT/Documents/antigravity/quick-nobel/desktop/src/main/kotlin/com/lirix/app/desktop/cast/DesktopConnectServer.kt:167` принимает любой декодированный PairRequest, не проверяет nonce/proof/TTL, создает постоянный фиктивный endpoint и возвращает PairResult(ok=true). SetAudioCast на строке 193 также не требует предварительного pairing. Входной TCP listener — строка 84, без TLS.
- `C:/Users/DaniilTuT/Documents/antigravity/quick-nobel/app/src/main/java/com/lirix/app/connect/AndroidConnectManager.kt:200` отправляет нулевые nonce/proof; строка 154 доверяет QR до установления сети и ответа сервера.
- `C:/Users/DaniilTuT/Documents/antigravity/quick-nobel/shared/src/commonMain/kotlin/com/lirix/app/connect/transport/LanConnectTransport.kt:59` connectPinned игнорирует endpointId и relayUrl, подключается к адресу обычным Socket (95).

Сценарий: неизвестный узел LAN подключается к 4433 и отправляет корректно сериализованный SetAudioCast либо произвольный PairRequest. Сервер принимает команду/сопряжение без секрета и подтверждения хоста. Существование отдельного PairingSecurityManager эту runtime-ветку не защищает.

## P1 — Любая UDP-датаграмма может включить воспроизведение

`C:/Users/DaniilTuT/Documents/antigravity/quick-nobel/desktop/src/main/kotlin/com/lirix/app/desktop/cast/DesktopConnectServer.kt:221`: receive не проверяет отправителя, сессию и состояние Cast. На строках 225–232 payload напрямую подается в PCM, отсутствующая audio line автоматически открывается. Сценарий: отправить на UDP 4433 пакет с 7-байтным заголовком и PCM без TCP-соединения. Также поздние пакеты после SetAudioCast(false) повторно открывают звук. Нет защиты от инъекции и подслушивания аудио.

## P1 — Handoff в UI симулируется вместо передачи трека

`C:/Users/DaniilTuT/Documents/antigravity/quick-nobel/app/src/main/java/com/lirix/app/ui/connect/DeviceConnectBottomSheet.kt:529`: вызов FSM сопровождается явной симуляцией Ack и RemoteStateUpdate после delay на строках 533–551. `C:/Users/DaniilTuT/Documents/antigravity/quick-nobel/shared/src/commonMain/kotlin/com/lirix/app/connect/handoff/HandoffStateMachine.kt:69` только меняет локальное состояние, не отправляет запрос. DesktopConnectServer:210 игнорирует остальные сообщения, включая HandoffRequest. Сценарий: выбрать устройство при отключенном/недоступном ПК — через 1.8 с UI получает вымышленный успешный ответ. Запуск трека, очередь, караоке и физический кроссфейд этим не реализованы.

## P1 — Audio Cast теряет 37.9% PCM и нарушает границы сэмплов

`C:/Users/DaniilTuT/Documents/antigravity/quick-nobel/app/src/main/java/com/lirix/app/connect/cast/AndroidAudioCaptureService.kt:45`: кадр 10 мс содержит 1920 байт (48000 × 2 × 2 × 0.01). `C:/Users/DaniilTuT/Documents/antigravity/quick-nobel/app/src/main/java/com/lirix/app/connect/cast/AndroidCastSourceManager.kt:139` обрезает каждый кадр до 1193 байт, отправляет его как opusPayload, хотя строка 89 объявляет OPUS. 1193 не кратно ни 2 байтам сэмпла, ни 4 байтам stereo frame. Приемник DesktopConnectServer:227–232 не декодирует Opus и пишет эти байты напрямую как PCM. Аналогичное обрезание: `C:/Users/DaniilTuT/Documents/antigravity/quick-nobel/desktop/src/main/kotlin/com/lirix/app/desktop/cast/DesktopCastSourceManager.kt:195`. Сценарий: непрерывный 48k stereo сигнал теряет 727 байт из каждых 1920, накапливая разрыв по длительности и искажения; корректный Opus-передатчик тоже даст шум на этом приемнике. Jitter buffer/PLC/FEC из shared runtime-приемником не используются.

## P1 — Выбор второго устройства сохраняет соединение с первым

`C:/Users/DaniilTuT/Documents/antigravity/quick-nobel/app/src/main/java/com/lirix/app/connect/AndroidConnectManager.kt:177` возвращает любой текущий CONNECTED transport, не сравнивая endpoint или адрес. startAudioCastToDevice:227 также берет его без сравнения цели. Сценарий: подключить A, выбрать B — новый deviceName может отображаться в UI, а аудио продолжит отправляться A. Требуется привязка активного транспорта к идентичности выбранной цели и явное переключение.

## P2 — Захват сообщает успех даже без AudioRecord

`C:/Users/DaniilTuT/Documents/antigravity/quick-nobel/app/src/main/java/com/lirix/app/connect/cast/AndroidAudioCaptureService.kt:185`: реальный AudioRecord создается только при API >=29 и nonnull projection. Ошибки и неинициализированный record проходят к безусловному return true (237). AndroidCastSourceManager:127 передает исходный projection вместо уже вычисленного targetProjection:78. Сценарий: attachMediaProjection(token), затем startCast без параметра либо AudioRecord initialization failure — isCasting=true, реальных кадров нет. Нормальный сервисный путь ADR-043 сейчас передает projection явно, поэтому проблема fallback-токена относится к отдельной публичной ветке, а не утверждению, что каждый запуск сломан.

## P2 — Reconnect/presence не интегрированы в приложения

LanConnectTransport:137 при ошибке чтения только ставит DISCONNECTED. AndroidConnectManager не подписывается на transport.state и не вызывает ReconnectPolicy/onConnectionLost; дальнейший reconnect возможен лишь при ручном запуске нового cast. Реальных ссылок на DeviceHeartbeatMonitor/ReconnectPolicy в app/desktop main нет; реестр при сканировании сразу помечает устройство online. Сценарий: оборвать Wi-Fi во время Cast — восстановление за <=10 с и достоверное presence не обеспечиваются runtime-цепочкой.

## Почему зеленые тесты не доказывают E2E

`C:/Users/DaniilTuT/Documents/antigravity/quick-nobel/shared/src/commonTest/kotlin/com/lirix/app/connect/qa/ConnectIntegrationTestStand.kt:60` тестирует FSM/ConnectSessionManager, передавая объекты через NetworkAnomalySimulator (97), а RemoteStateUpdate создается вручную (116). Ни LAN socket, ни DesktopConnectServer, ни реальный плеер/Opus/AudioRecord этим сценарием не проверяются. Это полезные компонентные тесты, но не подтверждение звука/безопасности на двух устройствах.

## Спецификация и ADR

SPEC-CONNECT-01 требует QUIC/TLS/Ed25519 pinning, HMAC и TTL, host approval, Opus и jitter buffer. `C:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/decisions.md:670` ADR-042 одобряет TCP/UDP и JavaSound, но не задает замену безопасности и использует неопределенное «PCM/Opus». ADR-043:678 действительно связывает runtime permissions/MediaProjection с сервисом и запускает сервер. Это устраняет отдельные интеграционные блокеры, но не закрывает перечисленные разрывы.

Вердикт Antithesis: есть работающий socket-каркас для одностороннего Android→Desktop Cast, однако утверждение о безопасном полном Connect и бесшовном handoff опровергается кодом. Приоритет: закрыть неавторизованный ingress; убрать успешные демо-ответы; определить единый формат аудио и передавать целые кадры; подключить runtime-протокол/плеер; затем провести двухустройственный E2E.

