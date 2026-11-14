# TeachMode 2.0 — этап 4 (SPEC-TEACH-06 §5), 2026-11-01

Статус: **IN PROGRESS** (работа прервана лимитом использования; ничего не закоммичено).

## Сделано
- Спека: раздел 5 (расхождения D1–D11, решения, критерии 5.4), решения утверждены пользователем.
- Tier A: одна функция `TeachModeBindingHelper.bindManualContentToTrack` (`ui/TeachModeLogic.kt`) в `libraryTransaction`; трек фиксируется при открытии; диалог подтверждения при замене заметно более коротким текстом; «Текст» не трогает аккорды и очищает старый LRC (как до WIP); ошибка БД — экран открыт, выделение сохранено.
- JS: тап выделяет блок (проверено в браузере), «Сузить», ссылка выдачи для селектора; исправлена регулярка, ломавшая слова «But/Hello/And» → «B ut…».
- i18n: `values/strings_teach.xml`, `values-ru/strings_teach.xml` (86 ключей, plurals); ≥48 dp; цвета из токенов; `e.message` убран.
- Движок: `RuleOutcome` (типизированные исходы), `RuleNetwork.kt` (политика URL, лимиты, 403/429 без повторов), `RuleCandidateRanker.kt` (порог, Ambiguous/LowConfidence), `RuleWebViewRenderer.kt` (только при проверке правила), шаблон поиска из навигации, активация только после проверки.
- Фикстуры: синтетический текст, 4 сайта (amdm, modern, international, amalgama), `expected.txt`/`expected_url.txt`.
- `androidx.appcompat` удалён из `app/build.gradle.kts`.

## Проверки
| Проверка | Статус |
|---|---|
| Целевые тесты (TeachRule*, TeachMode*, CustomRule*, ScraperRuleBundleCodec, AmDmChordCascade), 50 тестов, до правок по ревью | PASS |
| `:app:testDebugUnitTest :app:compileDebugAndroidTestKotlin` (до правок по ревью) | FAIL: 13 в LanguageRuntimeTest/PosterTextSnapshotTest — `R.string.tab_now_playing` отдаёт чужую строку; вероятно устаревшие константы R после нового файла ресурсов, не проверено чистой сборкой |
| Прогон после правок по ревью | NOT RUN |
| Приёмка на телефоне | NOT RUN (нужно согласие пользователя) |

## Независимое ревью (выполнено, 4 MAJOR, блокеров нет)
Исправлено в коде, но **не перепроверено тестами**: 1) старый LRC при ручной привязке; 2) чужой исполнитель с тем же названием проходил порог (добавлены требование совпадения исполнителя и точности названия); 3) контекст соседних результатов (контекст только из контейнера с одной ссылкой, идентичность песни с учётом строки); 4) фолбэк «главная страница сайта» для правил без шаблона удалён (в т. ч. v1). Также: минимальная правдоподобность текста, «голые слова» в селекторе контента.

## Осталось
- `TeachModeMultiSelectTest.test_custom_rule_with_multi_selector_execution` упадёт: правило v1 без шаблона теперь `InvalidRule` (по спеке 5.2.6) — дать тесту шаблон и страницу поиска.
- Добавить негативные тесты: «только чужой исполнитель», «только Rain Dance».
- MINOR из ревью: 5 (зафиксировать параметры диалога правила), 6 (не включать правило, сработавшее только через WebView), 7 (отмена не прерывает HTTP), 8 (домен-публичный суффикс), 10 (таймаут `readHtml`), 12 (мост: игнорировать вне режима выбора, лимит длины), 16–19.
- Импорт bundle создаёт активные правила без проверки (`ScraperRuleBundleCodec`, вне файлов TeachMode) — отдельная задача; Desktop не проверяет `status`.
- Чистый полный прогон, обновление board.md/decisions.md основной сессией.

---

## Продолжение 2026-11-10 (основная сессия, К2 из `intake/ROADMAP_20261109.md`)

Статус: **IMPLEMENTED / AUTOMATED PASS**, приёмка на устройстве NOT RUN, ничего не закоммичено.

### Сделано
- `TeachModeMultiSelectTest`: правило получило шаблон поиска и страницу выдачи (сделано сессией TeachMode до остановки), тест проходит.
- Негативные тесты ранжирования (`TeachRuleEngineUnitTest`): то же название у другого исполнителя → LowConfidence; «Rain Dance» / «After the Rain» при поиске «Rain» → LowConfidence, точное название рядом выбирается; исполнитель соседнего результата в общем контейнере не заимствуется.
- Четыре MAJOR-правки ревью покрыты тестами: очистка старого LRC (`TeachModeSelectorTest`), порог исполнителя и контекст соседей (новые тесты), отсутствие фолбэка без шаблона (`TeachRuleGoldenFlowTest.invalid_rules_fail_before_any_request`).
- №6: найден пробел — результаты поиска, полученные только через WebView, не отмечались. Теперь `Success.usedWebView = true` и в этом случае; правило, сработавшее только через WebView, нельзя включить (`TeachRuleFactory.canActivate`), в диалоге показывается пояснение, черновик сохранить можно.
- №7: отмена прерывает HTTP — уже было реализовано (`invokeOnCompletion { disconnect() }` в `RuleNetwork.kt`), изменений нет.
- №10: чтение HTML после истечения срока ограничено `READ_HTML_TIMEOUT_MS` (2 с) в `RuleWebViewRenderer`.
- №12: мост JS принимает выделение только в режиме выбора и в пределах размеров (`TeachBridgeGuard`); очистка и «Сузить» вне режима выбора игнорируются.
- Backlog: `teach-rule-bundle-import-validation.md`, `teach-mode-review-minor.md` (№5, №8, №16–19).

### Проверки 2026-11-10 (рабочая копия со всеми потоками)
Команда: `./gradlew --continue :shared:allTests :app:testPrivateDebugUnitTest :app:testCompactDebugUnitTest :desktop:test :app:compilePrivateDebugAndroidTestKotlin :app:assemblePrivateDebug`

| Проверка | Статус |
|---|---|
| `:app:testPrivateDebugUnitTest` | PASS, 471/471 |
| `:app:testCompactDebugUnitTest` | PASS, 466/466 |
| `:shared` Android | PASS, 266/266 |
| `:shared` Desktop | PASS, 270/270 |
| `:desktop:test` | FAIL, 2 из 241 (20 пропущено): `UnalignedTranslationPanelTest` ждёт подпись «…в Google», интерфейс показывает «…в Google Translate». Зона машинного перевода (в работе), TeachMode не касается |
| `:app:compilePrivateDebugAndroidTestKotlin`, `:app:assemblePrivateDebug` | PASS (задачи выполнены до падения `:desktop:test`, сборка шла с `--continue`) |
| 13 падений `LanguageRuntimeTest`/`PosterTextSnapshotTest` из прошлого прогона | не воспроизводятся |
| Срабатывание `READ_HTML_TIMEOUT_MS` и поведение моста в настоящем WebView | NOT RUN (нужен эмулятор) |
| Привязка и проверка правила на телефоне | NOT RUN |
