# Оптимизация энергопотребления — 2026-10-21

Статус: IMPLEMENTED / regression tests PASS. Финальная компиляция после удаления лишнего clock у Connect: PASS (`:app:compileDebugKotlin`, BUILD SUCCESSFUL).

## Исправления и доказательства

| Требование | Реализация | Проверка |
|---|---|---|
| Остановить Cast при отключении ПК | `AndroidCastSourceManager` наблюдает состояние транспорта, прекращает capture/sender при disconnect и ошибке отправки; `AudioCastForegroundService` завершает foreground service после остановки. LAN EOF закрывает оба сокета; старый reader не закрывает новое подключение. | `AndroidCastSourceTest`: отключение после старта и до старта monitor, отсутствие дальнейших пакетов, остановка на первой ошибке отправки, отказ запуска disconnected. `LanDatagramFailureTest`: реальный локальный TCP EOF, UDP cleanup и reconnect. |
| Ограничить кадровые обновления плеера | Общий кадровый `playbackPositionMs` удалён из `NowPlayingScreen`. `PlaybackClockContent` изолирует state трекбара; karaoke просыпается на границе LRC. Clock не запускается на paused/null и останавливается ниже STARTED. Connect не имеет отдельного кадрового clock: позиция в его UI не отображается. | `PlaybackUiClockTest`: paused/null, extrapolation/speed/duration, local pause/seek anchor, lyric boundaries/duplicates. Компиляция Compose call sites. |
| Не публиковать невидимую позицию overlay | `FloatingLyricsService` больше не записывает `currentPositionMs`/`durationMs` в UI state на каждом тике. Публикуются отображаемые поля. | `FloatingLyricsEnergyTest`: другой raw anchor в той же строке сохраняет тот же экземпляр UI state. |
| Остановить вычисления overlay при screen off | System receiver обновляет `screenInteractive`; `combine/collectLatest` отменяет активный цикл. При screen on немедленно обрабатывается последний snapshot. Paused/null/static lyrics работают по событиям. Изменение калибровки вызывает событие SharedPreferences, в том числе на паузе. | Три `FloatingLyricsEnergyTest`: screen-off не читает lyrics DAO/не меняет UI, screen-on resync, paused без recurring clock, calibration refresh. Ключ кеша фиксируется после suspend Room query, чтобы отмена чтения не блокировала повторную загрузку. |
| Не перекодировать неизменную обложку | Ограниченный кеш weak bitmap references + generationId; sameAs для эквивалентного bitmap. Изменение пикселей, удаление сохранённого файла и новый artwork вызывают сохранение. Ошибка сохранения не кешируется. | `MediaSessionEnergyTest`, native graphics: неизменный/эквивалентный bitmap сохраняет mtime; мутация переписывает файл; удалённый файл восстанавливается. |
| Отменить heartbeat при исчезновении сессий | Пустой список контроллеров отменяет heartbeat; последний snapshot захватывается до очистки для финального progress update. Защитный выход из heartbeat при отсутствии подходящего playing snapshot. | `MediaSessionEnergyTest`: пустой список отменяет orphan job и очищает handle. Финальный source path проверен вручную. |
| Остановить ticker редактора синхронизации на паузе | Label имеет отдельный matching-source clock; pause/null не создаёт recurring job. Tap/rewind считывают точную позицию непосредственно при действии. Pulse читается в graphicsLayer. | Clock policy tests, существующий `LivePlaybackSyncTest`, компиляция диалога. |
| Отключить desktop meter без видимого потребителя | `Main` включает meter только для видимого неминимизированного Studio; native worker блокируется на ManualResetEventSlim без polling, когда disabled. Demand восстанавливается после рестарта bridge; поздние samples игнорируются. | Native `MeterDemandTests` и protocol tests. AOT bridge пересобран; bundled executable совпадает с publish output. |
| Снизить desktop DB activity | Blacklist кешируется реактивно; idle record timer остановлен. Прогресс считается в памяти, checkpoint 15 секунд. Pause, track change/replay и штатный exit выполняют flush. Track/session обновляются транзакционно, актуальные notes/favorite сохраняются. Завершение record защищено от collectLatest cancellation после входа в критическую секцию; ожидание первого blacklist snapshot остаётся отменяемым. | `DesktopLibraryRepositoryTest`: batching, pause/track boundaries, повторный flush без двойного кредита, notes, persisted blacklist. `ListeningLedgerTest`. |
| Остановить desktop HUD clock на паузе | HUD и desktop timing dialog используют существующий paused-aware `desktopPlaybackPositions`. | `DesktopPlaybackClockTest` и компиляция desktop. |

## Фактически выполненные проверки

- Объединённый Gradle-прогон: `BUILD SUCCESSFUL`, 46 Android tests (7 выбранных классов), 174 shared desktop tests, 11 desktop tests (3 выбранных класса); failures/errors/skips: 0.
- Native: `dotnet test native/LirixMediaBridge.Tests/LirixMediaBridge.Tests.csproj --no-restore --verbosity minimal` — 27 passed, 0 failed/skipped.
- XML результаты: `app/build/test-results/testDebugUnitTest/`, `shared/build/test-results/desktopTest/`, `desktop/build/test-results/test/`.
- Bundled `desktop/resources/lirix-media-bridge.exe` и Release AOT publish output: SHA256 `755BD27A1EABF450F1A3B68D3525473CC3C73A306B5760C907A4E272FA052AE3`.
- Проверка whitespace: `git diff --check` по файлам задачи прошла.
- Промежуточный прогон выявил необходимость native graphics для bitmap generationId и явного закрытия Service scope внутри test body. Тестовая фикстура исправлена; зависший worker был идентифицирован по JVM stack и завершён. Указанные выше PASS относятся к последующему завершившемуся прогону.

## Ограничения и отдельная критическая проблема

Физическая плавность UI, системные screen-off broadcasts на устройстве и экономия в мВт/процентах не измерялись. Скриншот батареи не устанавливает вклад отдельных дефектов. При аварийном завершении desktop возможна потеря ещё не сохранённого прогресса до 15 секунд; штатный exit выполняет flush.

BUG-LISTENING-TIME-01 остаётся OPEN / CRITICAL P1. Скриншот «00:03 / сегодня / 8 минут», позиция вместо прослушанного времени и границы суток зафиксированы в `../intake/CRITICAL_LISTENING_TIME_ACCOUNTING.md`. Эта работа не исправляет модель Android/shared listening-time accounting и не изменяет историю догадками.

Изменения других задач в рабочем дереве сохранены. Коммиты, публикация релиза и установка на телефон не выполнялись.
