# Спецификация: Устранение критических уязвимостей ядра (Core Security & Stability Fixes)

**Версия:** 1.0.0 (FROZEN)  
**Зона:** `media-core` & `media-ingress`  
**Основание:** [INDEPENDENT_CODEBASE_AUDIT.md](../../reports/INDEPENDENT_CODEBASE_AUDIT.md) (Дефекты P0 и P1)  
**Статус:** `APPROVED / READY FOR IMPLEMENTATION`

---

## 1. Область изменений (Scope)

Данная спецификация регламентирует устранение 4 критических дефектов безопасности и надежности ядра:
1. **P1 — Сетевое разрешение `ACCESS_NETWORK_STATE`**:
   - `AndroidManifest.xml`: добавление `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />` для гарантированной работы `NetworkConditionManager` и фонового префетчинга текстов.
2. **P1 — Защита от краша `FileUriExposedException` в `ShareManager`**:
   - `app/src/main/java/com/lirix/app/feature/share/ShareManager.kt`: полное устранение небезопасного вызова `Uri.fromFile()`. При сбое `FileProvider` возвращать `Result.failure` или `false` с логированием ошибки, не бросая `file://` Intent в Android 7.0+.
3. **P1 — Устранение Split-Brain синглтона `MediaSessionCollector`**:
   - `app/src/main/java/com/lirix/app/ingestion/MediaSessionCollector.kt`: превращение `MediaSessionCollector` в потокобезопасный синглтон `getInstance(context: Context)`. Использование `context.applicationContext`.
   - `app/src/main/java/com/lirix/app/LirixApp.kt`: получение экземпляра через `MediaSessionCollector.getInstance(this)`.
   - `app/src/main/java/com/lirix/app/ingestion/NotificationListener.kt`: использование синглтона; при `onDestroy()` сервиса НЕ занулять глобальные данные сессии всего приложения, а отвязывать только специфичные слушатели нотификаций.
   - `app/src/main/java/com/lirix/app/service/FloatingLyricsService.kt`: использование `MediaSessionCollector.getInstance(this)`.
4. **P0 — Безопасность сборки `build.gradle.kts`**:
   - `app/build.gradle.kts`: вынос захардкоженных дефолтных паролей подписи в переменные среды без открытого текста в git-репозитории.

---

## 2. Детальные контракты и сигнатуры

### 2.1. `ShareManager.kt`
- Метод `shareBitmap(...)`:
  ```kotlin
  val contentUri: Uri = try {
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
  } catch (e: Exception) {
      Log.e("ShareManager", "Failed to get FileProvider URI for sharing", e)
      return // или graceful exit с уведомлением, БЕЗ Uri.fromFile()
  }
  ```

### 2.2. `MediaSessionCollector.kt`
- Синглтон-фабрика:
  ```kotlin
  companion object {
      @Volatile
      private var instance: MediaSessionCollector? = null

      fun getInstance(context: Context): MediaSessionCollector {
          return instance ?: synchronized(this) {
              instance ?: MediaSessionCollector(context.applicationContext).also { instance = it }
          }
      }
  }
  ```
- Метод `release()` vs `stopListening()`:
  - `stopListening()` вызывается только при полном завершении работы приложения, либо инкрементирует/декрементирует счетчик активных подписчиков (ref count).

---

## 3. Критерии приемки (DoD)
- [ ] `ACCESS_NETWORK_STATE` присутствует в манифесте, тесты `NetworkConditionManager` не падают с SecurityException.
- [ ] `ShareManager` не содержит вызовов `Uri.fromFile(file)`.
- [ ] `MediaSessionCollector` создается строго один раз на всё приложение. Перезапуск `NotificationListener` не ломает воспроизведение в `MainActivity` и `FloatingLyricsService`.
- [ ] `./gradlew testDebugUnitTest` выполняется успешно.
