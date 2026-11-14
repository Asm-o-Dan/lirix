# К1. Защита данных телефона — 2026-11-12

План: `intake/ROADMAP_20261109.md`, раздел К1. Уровень 1. Ничего не закоммичено.

## Сделано
- `app/build.gradle.kts`: тип сборки `uitest` (пакет `com.lirix.app.uitest`), `testBuildType = "uitest"`, манифест тестового хоста Compose общий с `debug`, `uitestImplementation` для `ui-tooling` и `ui-test-manifest`.
- `app/build.gradle.kts`: проверка перед `connected*AndroidTest` — физическое устройство без `-Plirix.allowPhysicalDevice=true` останавливает задачу.
- `scripts/lirix_phone_snapshot.py`: снимок `databases/` и `shared_prefs/` через `run-as` в папку вне репозитория, `quick_check`, количества строк.
- `WORKFLOW.md` §5, `decisions.md`, команды проверки в `intake/ROADMAP_20261109.md` и `intake/CODEX_STUDIO_AND_ACCEPTANCE_20261101.md`.

Отклонение от плана: вместо суффикса `.debug` у debug-сборки и отдельной `preview` — отдельный тестовый тип `uitest`. Debug-APK по-прежнему обновляет `com.lirix.app` на телефоне, отдельная preview-сборка не нужна.

## Проверки
| Проверка | Статус |
|---|---|
| `:app:compilePrivateUitestAndroidTestKotlin :app:assemblePrivateUitest :app:assemblePrivateUitestAndroidTest :app:assemblePrivateDebug` | PASS |
| Пакеты APK (`aapt2 dump badging`): debug `com.lirix.app`; uitest `com.lirix.app.uitest`; тестовый APK `com.lirix.app.uitest.test` → target `com.lirix.app.uitest` | PASS |
| Список задач: только `connectedPrivateUitestAndroidTest`, `connectedCompactUitestAndroidTest` | PASS |
| `connectedPrivateUitestAndroidTest` без устройств: проверка пропускает, AGP сообщает «No connected devices» | PASS |
| Блокировка на физическом телефоне | NOT RUN — телефон не подключён |
| Прогон UI-тестов на эмуляторе с установленным рядом `com.lirix.app` | NOT RUN — перенесено в К4.1 |
| Снимок скриптом: защита от папки внутри репозитория, отказ без устройства | PASS |
| Снимок скриптом с телефона | NOT RUN — телефон не подключён |
| `:app:testPrivateDebugUnitTest :app:testCompactDebugUnitTest :app:assembleCompactDebug :app:compileCompactUitestAndroidTestKotlin` | PASS, 471/471 и 466/466 |

## К1.1. Сверка данных телефона (для владельца)
Эталон — копии базы с телефона от 01.11 (вне Git, `.gradle/karaoke-private-verification/`), обе `quick_check: ok`:

| Данные | 10:58, версия 14 | 11:19, версия 15 |
|---|---|---|
| Треки | 1142 | 1142 |
| В избранном | 199 | 199 |
| Сессии прослушивания | 2364 | 2364 |
| Тексты (кеш) | 602 | 602 |
| Свои правила сайтов | 6 | 6 |
| Правила чёрного списка | 1 | 5 (+4 готовых правила миграции 15) |
| Переводы песен | 39 | 39 |
| Достижения | 26 | 26 |

Что проверить на телефоне: в медиатеке около 1142 треков (новые прослушивания после 01.11 увеличат число), в фильтре «Избранное» около 199 (по решению D2 ранее снятые «сердца» могли вернуться после прослушивания), открываются свои тексты и заметки к нескольким знакомым песням, в Teach Mode видны 6 своих правил, «Итоги» за октябрь не пустые. Точнее — подключить телефон и запустить `python scripts/lirix_phone_snapshot.py`: он выведет те же количества.
