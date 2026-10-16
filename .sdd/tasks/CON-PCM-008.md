# CON-PCM-008 — DesktopConnectServer.closeAudioLine cleanup

Спека SPEC-CON-PCM-01 v1.1, CR-CON-PCM-01 APPROVED. Зависимости004..007. Один файл desktop/src/main/kotlin/com/lirix/app/desktop/cast/DesktopConnectServer.kt, одна функция private fun closeAudioLine().

Отсоединить activeAudioLine от поля, затем best-effort stop,flush,close каждый независимо. Ошибка stop/flush не препятствует close. Для каждой реальной ошибки diagnostic stage=output-close с классом/сообщением, без аудиоданных/секретов. Повторный close безопасен, null→return. Сохранить существующую синхронизацию. Не добавлять новых методов и не переписывать прочую логику.

Прочие failure recovery changes в openAudioLine разрешены только как доработка карточки004: cleanup созданной line гарантирован при exception и CancellationException; cancellation повторно бросить после освобождения. Для CancellationException из cleanup не терять исходное исключение.

DoD: fake SourceDataLine.stop/flush throwing не препятствуетclose, statefalse; output-open/start failure закрывает созданныйline. Regression тесты в уже разрешенном ConnectPcmReceiverTest.kt. Сообщить результат в CONNECT_PCM_EXECUTION.md.
