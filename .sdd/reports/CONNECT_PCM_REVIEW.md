# CONNECT PCM independent review

Дата: 2026-10-16. RECHECK_READY review. Статус: **PASS — static implementation review; automated execution pending root results**.

Основание: SPEC-CON-PCM-01, CR-CON-PCM-01/02. Проверены product source и regression tests; изменения Android/Desktop сравнены с сохраненным connect_pcm_baseline. Reviewer не менял продуктовый код/тесты и не запускал Gradle (root выполняет общую проверку). Физический Android→Windows звук **NOT_RUN**, GATE8 не пройден.

## Проверка пяти областей

1. PASS static: Pcm16Packetizer.kt сохраняет каждый байт, payload<=960, stereo alignment, независимые copy, seq/ts modulo, flags=0. AndroidCastSourceManager sender использует один bounded worker, правильный timestamp capture, последовательный send/count.
2. PASS static: PCM_S16LE control 48000/2/1536, datagram bytes/header прежние, Desktop rejects OPUS/invalid format. Shared ConnectProtoTest включает PCM control; Android тест реконструирует 1920 bytes после codec decode.
3. PASS static: Desktop partial writes до полного блока, invalid progress <=0/>remaining/nonaligned закрывает line/state; exception diagnostic; createdLine cleanup при open/start error; closeAudioLine выполняет все stages перед deferred cancellation. Предыдущий finding закрыт.
4. PASS static: UDP state gate, no auto-open, packet.length reset, malformed PCM отвергается. ConnectPcmSocketTest проверяет настоящий TCP/UDP, 1920 bytes через partial writes, small→large, initial/lateUDP, restart, OPUS/rate reject, error progress, open/start/stop/flush cleanup.
5. PASS по исправленной Desktop regression scope: erroneous suspend reflection удален, socket harness use/finally, bounded awaits, synchronized bytes/volatile flags. Previous tests расширены без удаления прежних production assertions. Automated results не заявляются reviewer до отчета root.

## Повторная проверка Android failure paths

Оба remaining findings закрыты. AndroidCastSourceManager.kt:174–181 сохраняет captureCancellation, выполняет общий failure cleanup jobs/channel/active fields, затем повторно бросает cancellation. Ветка failed capture сохраняет stopMessageJob; следующий startCast дожидается off до отправки нового on. AndroidCastSourceTest.retryWaitsForCleanupAfterFailedCaptureStart блокирует отправку off, запускает retry и проверяет полный порядок true,false,true,false. Start-control failure также очищает active fields перед rethrow. Дополнительных блокирующих findings в согласованном scope не обнаружено.
## Ограничения

Production capture default больше не имитирует успех без MediaProjection/AudioRecord; synthetic seam явно включается тестами. LanConnectTransport.sendDatagram теперь пробрасывает local send errors. При отрицательном AudioRecord.read engine логирует и stopCapture, но manager still-active state после runtime capture failure остается наблюдаемостным риском для следующего CR; physical smoke должен включать capture/send/receive/write, ненулевой signal, 60 секунд и restart.

Без физического smoke нельзя считать звук подтвержденным. Security pairing/UDP auth, target switching и fabricated handoff остаются отдельными CR.

