# CR-CON-PCM-02 — production capture, send errors, restart ordering

2026-10-16. APPROVED, Codex теперь основной исполнитель по прямому указанию пользователя.

Расширение исходного ремонтного пакета после непосредственной проверки:
- Production AndroidAudioCaptureEngine.startCapture возвращаетfalse при отсутствии MediaProjection, неподдерживаемомAPI или ошибке AudioRecord. Synthetic feed разрешен только явным constructor seam allowSyntheticInput=true в тестах, по умолчаниюfalse. Ошибкиstart/read/stop/release диагностируются, release не зависит от успехаstop.
- Android sender callback захватывает собственный channel, чтобы старый callback не писал в новую сессию. startCast ждет pending stop control, stopCastSuspend дожидается его; previous sender завершается перед отправкой stop. Регресс: start/stop/start→контрольный порядокtrue,false,true.
- LanConnectTransport.sendDatagram при отсутствии подключения бросает IllegalStateException("Transport not connected"), socket send exception не скрывает. UDP delivery этим не подтверждается, но sent counter больше не считает очевидный локальный отказ успехом.
- Desktop cleanup выполняетstop/flush/close даже при CancellationException, затем повторно бросает cancellation. Write progress<=0,не кратное4,больше remaining→диагностика+stop, безspin.
- Тесты реального TCP/UDP control path заменяют ошибочный reflection вызов suspend handleMessage безContinuation. Fake output не заменяет проверку фактической слышимости.

Разрешенные дополнительные файлы: AndroidAudioCaptureService.kt (constructor/startCapture/stopCapture), LanConnectTransport.kt (sendDatagram), LanDatagramFailureTest.kt, ConnectPcmSocketTest.kt. Остальные изменения в уже разрешенных файлах. Архитектура handoff/security/target switching по-прежнему отдельно.
