# Ревизионный отчёт: Security & Code Review («Второй ключ») — Фаза 0

**Статус:** ✅ **APPROVED**  
**Дата:** 2026-09-26  
**Роль:** Security & Code Reviewer («Второй ключ»)  
**Проект:** Notification Pipeline Constructor  

---

### 1. Криптография и Keystore
- **Android Keystore (AES-256-GCM):** В `SqlCipherSupportFactoryProvider.kt` мастер-ключ генерируется и хранится в `AndroidKeyStore` с `AES/GCM/NoPadding`, 256-бит ключ, 128-бит GCM тег и 12-байтный случайный IV.
- **Passphrase & Zeroization:** Базовый ключ шифрования БД (32 байта) генерируется через `SecureRandom`, шифруется мастер-ключом и сохраняется в локальный файл. В `AppModule.kt` после создания `SupportOpenHelperFactory` немедленно вызывается `SqlCipherSupportFactoryProvider.wipePassphrase(passphrase)`, где массив зануляется через `Arrays.fill(..., 0)`.
- **Хардкод ключей:** Хардкод ключей или статических паролей отсутствует.

---

### 2. Приватность и утечки PII (Zero PII in logs)
- Проведён сквозной статический анализ всех вызовов `Log.*`, `println`, `printStackTrace`, `System.out/err` по всем модулям (`core/`, `ingest/`, `ui/`, `app/`).
- **Результат:** Никаких утечек PII не обнаружено.
  - SMS: поля `body`, `originatingAddress` и PDU в логи не передаются.
  - Уведомления: `title`, `text`, `bigText`, `messages` в логи не выводятся; логируются исключительно `seq`, `packageName`, тип источника, количество восстановленных сессий и безопасные ошибки компонентов/ОС.
  - Токены, пароли и пользовательские данные в логах отсутствуют.

---

### 3. Android Lifecycle & Memory Leaks
- **PipelineNotificationListenerService:** В методе `onDestroy()` корректно вызывается закрытие канала `channel.close()` и отмена скоупа `serviceScope.cancel()`. В `onListenerDisconnected()` статус дисконнекта фиксируется без зависания корутин.
- **SmsBroadcastReceiver:** Вызов `val pendingResult = goAsync()` защищён блоком `try ... catch ... finally`, где `pendingResult.finish()` гарантированно вызывается в секции `finally`.
- **MainActivity & Compose:** ViewModel принимает только `StorageGateway` (Singleton), Activity Context внутри ViewModel не удерживается, утечек контекста или циклических ссылок не зафиксировано.

---

### 4. Проверка сборки (Build Status)
- Запуск `.\gradlew.bat :app:assembleDebug` прошёл успешно:
  - **Result:** `BUILD SUCCESSFUL` (145 actionable tasks, exit code 0).
  - Модули компилируются без ошибок, целевой Debug APK собран.

---

### Findings & Замечания:
- **Critical / High:** 0
- **Medium:** 0
- **Low / Suggestion:** 
  - Рекомендация на будущее: в `MainActivity.kt` для создания `TimelineViewModel` перейти на `@HiltViewModel` + `hiltViewModel()`, чтобы управление жизненным циклом ViewModel полностью делегировалось `ViewModelStoreOwner`.

---

### Sign-off:
- **Security Sign-off:** ✅ **PASSED**
- **Privacy (Zero-PII) Sign-off:** ✅ **PASSED**
- **Architecture & Lifecycle Sign-off:** ✅ **PASSED**
