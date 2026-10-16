# CR-CON-PCM-01 — внутренний seam и cleanup audio line

Дата2026-10-16. Статус APPROVED автором архитектуры Codex. Основание: независимый review текущей реализации.

1. Разрешить internal visibility только для DesktopConnectServer.openAudioLine и writePcm. Их exact arguments/return/semantics остаются из SPEC. Причина: прямые JVM regression tests реальных методов, без reflection/physical audio. Это module-internal seam, не новый публичный API. Карточки004/006 обновлены ссылкой на данный CR; уже написанные direct tests могут сохраниться.
2. Во всех новых failure paths CancellationException пробрасывается после cleanup принадлежащих операции ресурсов. Любое созданное SourceDataLine закрывается даже если stop/flush бросают. Упавший start/open не оставляет ресурса или playing=true.
3. Новая карточка CON-PCM-008 для closeAudioLine: отдельная функция, отдельный file scope. Исправление в том же файле не дает права на прочие методы вне их карточек.
4. QA дополнительный тест: factory-created line.open/start бросает, stop/flush тоже бросают — close все равно вызван, statefalse. Cleanup cancellation также освобождает line до повторного throw. Это регресс error handling, не попытка лечить baseline чужих функций.

Версия SPEC повышена до1.1. До review/final smoke карточки остаются IN_REVIEW, независимо от внутренних DONE исполнителя.
