# Отчёт о реализации: TASK-RUL-01 и TASK-RUL-02

- **Задачи:** `TASK-RUL-01` (Кодек переносимых правил ScraperRuleBundleCodec) & `TASK-RUL-02` (UI диалоги экспорта/импорта в TeachModeScreen и LibraryScreen)
- **Зона:** `lyrics-engine` / `media-ui`
- **Роль:** Инженер-разработчик модуля парсинга Lirix
- **Статус:** DONE
- **Тесты:** PASS (11/11 `ScraperRuleBundleCodecTest`, весь сьют `testDebugUnitTest` PASS)

---

## 1. Реализованные компоненты

### 1. `feature/lyrics/ScraperRuleBundleCodec.kt` (TASK-RUL-01)
1. **Модель `ScraperRuleBundle`:**
   - Версионированная схема `schemaVersion = 1`.
   - Полная спецификация: `name`, `domain`, `contentSelector`, `stripSelectors`, `chordsSelector`, `lineBreakStrategy`, `searchUrlTemplate`, `searchResultSelector`, `headers`, `author`, `version`, `exportedAt`, `appVersion`, `checksum`.
2. **Сериализация:**
   - `serialize(rule: CustomLyricsRuleEntity, pretty: Boolean = true): String`
   - `serializeBundle(bundle: ScraperRuleBundle, pretty: Boolean = true): String`
   - Поддержка форматированного (отступы 2 пробела) и компактного JSON.
3. **Целостность и анти-тамперинг:**
   - Канонический хэш SHA-256 по полям `schemaVersion`, `domain`, `contentSelector`, `chordsSelector`, `searchUrlTemplate`, `author`, `version`.
   - Проверка чексуммы при десериализации; отсечение любых модифицированных пакетов (`SecurityException`).
4. **Многоуровневая валидация и безопасность:**
   - **Защита от XSS и инъекций:** блокировка тегов `<script>`, `<style>`, `<img>`, схем `javascript:`, выражений `eval(`, `expression(`, обработчиков `onload=`, `onerror=`.
   - **Защита от SSRF/Localhost:** валидация FQDN, запрет `localhost`, `127.0.0.1`, `0.0.0.0`, приватных IPv4 адресов и схем `file:`, `data:`, `content:`.
   - **Протокол HTTPS:** поиск по шаблону `searchUrlTemplate` разрешён строго по протоколу `https://` и только на целевой домен или его поддомены.
   - **Защита заголовков:** запрет передачи чувствительных заголовков `Cookie`, `Authorization`, `Proxy-*` и CRLF инъекций.

### 2. TDD Unit-тесты `ScraperRuleBundleCodecTest.kt`
Покрыты 11 сценариев:
1. `test_serialize_and_deserialize_valid_bundle_roundtrip` (полный цикл сериализации и десериализации)
2. `test_pretty_vs_compact_serialization` (проверка компактного и красивого JSON)
3. `test_tampering_detection_checksum_mismatch` (обнаружение подделки селектора при валидации)
4. `test_missing_checksum_fails_validation` (обязательность чексуммы)
5. `test_reject_missing_required_fields` (проверка обязательности `domain` и `contentSelector`)
6. `test_reject_malformed_json` (обработка синтаксических ошибок JSON)
7. `test_reject_unsupported_schema_version` (проверка неподдерживаемых схем v2+)
8. `test_injection_protection_in_selectors` (блокировка инъекций в селекторах)
9. `test_domain_validation_security` (блокировка localhost, loopback IP)
10. `test_search_url_template_security` (защита протокола HTTPS и хоста поиска)
11. `test_forbidden_headers_security` (блокировка Cookie и инъекций заголовков)

### 3. UI компоненты `ScraperRuleDialogs.kt` и интеграция (TASK-RUL-02)
1. **`ExportRuleDialog`:**
   - Моноширинный предпросмотр JSON с подсветкой метаданных и SHA-256 чексуммы.
   - Кнопка **«Копировать»** (запись в системный буфер `LocalClipboardManager` + тактильный отклик и Toast).
   - Кнопка **«Поделиться»** (запуск системного диалога Android `Intent.ACTION_SEND` с `text/plain`).
2. **`ImportRuleDialog`:**
   - Поле ввода JSON с кнопкой быстрого действия **«Вставить из буфера»**.
   - Динамический предпросмотр: проверка статуса подписи, вывод названия, домена, селектора и автора.
   - Проверка конфликтов с базой данных (предупреждение, если правило для этого домена уже существует в БД).
   - Импорт и сохранение в Room через `LyricsDao.saveRule(...)`.
3. **Точки вызова:**
   - **`TeachModeScreen`:**
     - Верхняя панель: кнопка «Импорт правила» (`Icons.Default.FileDownload`).
     - Нижняя панель выделения: кнопка «Поделиться правилом» (`Icons.Default.Share`) рядом с сохранением в БД.
   - **`LibraryScreen`:**
     - Заголовок экрана: кнопка «Импорт правила парсера» для загрузки готовых пакетов в фонотеку.
