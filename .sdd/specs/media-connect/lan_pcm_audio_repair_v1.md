# SPEC-CON-PCM-01 — Android → Windows LAN PCM repair

Дата: 2026-10-16. Версия: 1.1. Статус: APPROVED для реализации первого ремонтного пакета. Автор: Codex, архитектура и спецификация по прямому поручению пользователя. Основание: CONNECT_DIALECTIC_VERIFICATION.md и пользовательский симптом «звук вообще не передается».

## Цель и границы

Устранить доказанный дефект 1920→1193 байта→SourceDataLine, сделать формат честным, ошибки наблюдаемыми и написать регрессионные тесты реального пути packetizer→codec→receiver. Это временный LAN PCM режим текущего TCP/UDP runtime. Он не заявляет соответствие исходному QUIC/Opus, безопасность сопряжения, работу через LTE, handoff или обратное Windows→Android воспроизведение. Для теста нужны обе обновленные стороны. Пакет не выпускается как безопасный публичный Connect.

## Контракт PCM-LAN v1

- Control: существующий SetAudioCast, добавить enum CastCodec.PCM_S16LE; OPUS сохранить. Android LAN источник объявляет PCM_S16LE, sampleRate=48000, channels=2, bitrateKbps=1536. Параметр startCast bitrateKbps сохранить для исходной совместимости, но PCM bitrate фиксирован, это явно задокументировать.
- Данные: signed PCM16 little endian, stereo interleaved. Stereo sample frame=4 байта. Input 10 ms=1920 байт, 480 sample frames.
- Сохранить существующий 7-байтный big endian wire-header и лимит1200. Поле opusPayload оставить как существующий контейнер wire payload; в PCM режиме это PCM, пояснить документацией, массово не переименовывать.
- Делить PCM на payload не более960 байт (240 sample frames=5ms), всегда кратные4. 1920→960+960. Никаких усечений/округления с потерей хвоста, искусственного silence/DTX вместо данных и попыток декодировать PCM как Opus.
- seq: 16bit modulo65536, +1 на датаграмму. timestamp48k: счетчик stereo sample frames, modulo2^32; +payload.size/4 на датаграмму. flags=0 для PCM, в том числе тишины.
- Примеры: 1920 байт,seq0,ts0→(seq0,ts0,960),(seq1,ts240,960); 960,seq65535,ts4294967200→(65535,4294967200,960); следующий input начинается с seq0 и ts144. Empty→[]. 4 байта→один payload4. Размер2 или1193→IllegalArgumentException("PCM payload must contain complete stereo frames").

## Функция пакетизации (shared)

Файл shared/src/commonMain/kotlin/com/lirix/app/connect/cast/Pcm16Packetizer.kt, top-level:

`fun packetizePcm16Stereo(pcmData: ByteArray, initialSeq: Int, initialTimestamp48k: Long): List<CastDatagram>`

Предусловия: initialSeq in0..65535, initialTimestamp48k in0..4294967295. Нарушение → IllegalArgumentException с сообщениями "Invalid PCM sequence" / "Invalid PCM timestamp". После проверки аргументов проверить кратность4; empty вернуть[]. Создавать независимые копии payload <=960, seq/ts рассчитывать по контракту. Конкатенация output payload byte-for-byte равна input; input не менять. Чистая функция, без I/O, новых зависимостей. O(n), память O(n).

## Android runtime

`AndroidCastSourceManager.startCast` сохраняет публичную сигнатуру. Использует targetProjection=argument?:attached, а не исходный nullable argument. Все исходящие audio datagrams формировать packetizePcm16Stereo. Отправку организовать одним sender worker через bounded Channel<AudioChunk>(capacity=64, BufferOverflow.DROP_OLDEST); callback только trySend, не запускать отдельную coroutine на каждую датаграмму. Дроп целого захваченного chunk при перегрузке допустим для live playback, усечение внутри chunk запрещено. Timestamp брать из AudioChunk.timestampSample (текущий48k формат); seq увеличивать за реально обработанные датаграммы. В пределах worker порядок datagrams сохраняется. Объявить private senderJob и outgoingChunks; stopCast отменяет worker и закрывает channel, повторный start создает новые.

Реальные ошибки encode/send/startCapture логировать Android Log.e("LirixCast",stage,error), без токенов/секретов/PCM содержимого. CancellationException всегда пробрасывать. sentDatagramCount увеличивается после успешного sendDatagram. До старта capture разрешить callback постановку в очередь (убрать гонку `_isCasting` выставляется лишь после старта): сначала создать канал/worker, callback пишет только в активный канал; при false capture закрыть/отменить оба. Состояние isCasting устанавливать согласно captureStarted, при неуспехе послать best-effort SetAudioCast(false,PCM_S16LE) и очистить transport/device.

В рамках этого пакета не менять feedRawAudio/test-only поведение AndroidAudioCaptureEngine; отдельно зафиксировать, что оно еще может скрывать ошибку AudioRecord. Реальный smoke обязан проверить ненулевой captured signal. Эта спецификация не разрешает выдавать unit feedRawAudio за реальный capture.

`stopCast()` сохраняет сигнатуру, делает cleanup worker/channel даже если isCasting=false (не бросать cleanup early return), останавливает capture, отменяет incomingMessagesJob, очищает поля и посылает enabled=false с PCM_S16LE. Поддерживающие поля/imports в этой карточке допустимы; чужая логика не меняется.

## Desktop runtime

Тестовый seam: последний необязательный аргумент конструктора DesktopConnectServer `audioLineFactory: (AudioFormat) -> SourceDataLine` с default существующим AudioSystem.getLine. Никакой новой зависимости. Это обеспечивает fake/proxy line в JVM-тестах без физической звуковой карты.

`internal fun openAudioLine(sampleRate: Int = SAMPLE_RATE, channels: Int = CHANNELS): Boolean`: закрыть прежний line, создать format PCM signed16 stereoLE через factory; open/start, assign activeAudioLine, applyVolume, вернутьtrue. sampleRate!=48000 или channels!=2 → diagnostic false без factory call. Исключение→stderr stage=output-open+класс/сообщение, release созданного line, activeAudioLine=null, _isAudioCastPlaying=false, returnfalse. Cancellation не поглощать.

`private suspend fun handleMessage(msg: LirixConnectMsg, output: DataOutputStream)` сохраняет сигнатуру. Менять только ветку SetAudioCast: enabled=false закрывает audio и сбрасывает state; enabled=true принимает только PCM_S16LE/48000/2, и isPlaying=true только если openAudioLine true; OPUS/неверный формат →закрыть line,false,stderr stage=format-rejected. Остальные сообщения не переписывать.

`private fun startUdpAudioListener(ds: DatagramSocket)` сохраняет сигнатуру. Перед каждым receive восстановить packet.length=buf.size, чтобы маленькая датаграмма не ограничила последующую. Не открывать audio из UDP. Decode только если Cast разрешен (_isAudioCastPlaying=true) и audio line есть. Передавать только непустой payload кратный4 в writePcm. Ошибки decode/неверный размер диагностировать; принимать следующие корректные пакеты. Пакет после SetAudioCast(false) не должен открыть line. Это state gate, не решение аутентификации UDP.

`internal fun writePcm(data: ByteArray)` сохраняет сигнатуру. Без activeAudioLine или вне активного Cast — return без открытия. Empty→return. Некратный4→stderr stage=pcm-invalid-size, без write. Обрабатывать partial writes циклом до полного блока; offset/len всегда кратны4. write возвращает <=0, некратное4 число или бросает→stderr stage=output-write, сбросить playing, закрытьline, выйти, не spin. Не подавлять ошибки. Согласовать locking существующим @Synchronized, не блокировать UDP навечно и не менять громкость/other UI.

## Тесты и acceptance

QA сначала пишет failing regression tests: packetizer lossless1920,short4,empty,invalid1193/2,seq rollover,timestamp rollover; source объявляет PCM и передает все1920 в двух datagrams; codec roundtrip сохраняет bytes. Desktop test seam: invalid1193 не доходит до write; valid960/960 доходит; partial write→полные1920 без потерь; output exception→playingfalse+diagnostic; OPUS reject; stop+lateUDP не открываетline; small packet→larger packet оба принимаются. Desktop network tests используют localhost ephemeral port и контролируемый teardown; никакого громкого реального аудио в automated tests.

Команды: :shared:desktopTest, :desktop:test, :app:testDebugUnitTest (адресный AndroidCastSourceTest), :app:assembleDebug. Сначала --offline, использовать существующие зависимости. Честно указать blocked/не запущенные проверки. Нельзя удалять/ослаблять старые тесты ради green; изменение expectations OPUS→PCM и количества пакетов согласно контракту допустимо.

Физический smoke отдельно: собрать обе стороны; установить debug APK поверх без очистки данных; запустить Windows receiver; включить на Android пользовательский media-source с разрешенным playback capture; разрешения RECORD_AUDIO/MediaProjection; проверить capture/send/receive/write diagnostics и слышимость, stop/restart, минимум60с без ошибок. До подтверждения слышимости GATE8 не пройден. DRM/bypass не реализовывать. Без доступного устройства → NOT_RUN с причиной, не PASS.

## За пределами

Security pairing/TLS/UDP session auth, target switching, demo handoff, Windows source, auto-reconnect, jitter/PLC/FEC и false-success AudioRecord production/test separation — следующие отдельные CR. Известные P1 не объявлять исправленными данным пакетом. Не трогать незакоммиченные файлы других фич, не reset/clean, не коммитить и не публиковать.

## CR-CON-PCM-01 (v1.1)
Internal visibility двух методов разрешена для direct regression tests. Cleanup при open/start failure и cancellation всегда закрывает созданный line; stop/flush error не препятствуетclose. См. .sdd/intake/CR-CON-PCM-01.md и карточку CON-PCM-008.md. Все другие требованияv1.0 сохраняются.

