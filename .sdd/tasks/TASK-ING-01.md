# Задача TASK-ING-01: реализовать `isMediaNotification`

**Файл:** `app/src/main/java/com/eventengine/app/service/MediaIngressFilter.kt` (создать)
**Спека:** [.sdd/specs/media-ingress/overview.md#isMediaNotification](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ingress/overview.md) (v1)

**Сигнатура (НЕ МЕНЯТЬ):**
```kotlin
fun isMediaNotification(sbn: StatusBarNotification): Boolean
```

**Поведение:**
1. Если `sbn == null` или `sbn.notification == null` $\to$ вернуть `false`.
2. Извлечь `packageName = sbn.packageName?.lowercase() ?: return false`.
3. Проверить вхождение `packageName` в Whitelist известных плееров (`com.spotify.music`, `ru.yandex.music`, `com.vkontakte.android`, `com.google.android.apps.youtube.music`, `com.vanced.android.apps.youtube.music`, `app.revanced.android.apps.youtube.music`, `com.aimp.player`, `com.apple.android.music`, `deezer.android.app`, `com.soundcloud.android`, `com.maxmpz.audioplayer`). Если совпало $\to$ вернуть `true`.
4. Проверить extras нотификации:
   - Если `sbn.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)` или `sbn.notification.extras.containsKey("android.mediaSession")` $\to$ вернуть `true`.
5. Проверить категорию:
   - Если `sbn.notification.category == Notification.CATEGORY_TRANSPORT` $\to$ вернуть `true`.
6. Проверить шаблон стиля:
   - Если `sbn.notification.extras.getString("android.template")?.contains("MediaStyle", ignoreCase = true) == true` $\to$ вернуть `true`.
7. Во всех остальных случаях вернуть `false`.

**Ошибки:**
- Не выбрасывать исключений. При любых NullPointerException или сбоях чтения extras перехватывать и возвращать `false`.

**Граничные случаи:**
- Уведомление банка (`com.tcsbank.mobile`) $\to$ `false`.
- СМС от 900 (`com.google.android.apps.messaging`) $\to$ `false`.
- Spotify (`com.spotify.music`) $\to$ `true`.
- Кастомный локальный плеер с `MediaStyle` $\to$ `true`.
- `extras == null` $\to$ `false`.

**Запрещено:**
- Читать тело SMS или банковские пуши.
- Обращаться к базе данных.
- Менять другие файлы.

**Критерий приёмки:**
- Проходят тесты `MediaIngressFilterTest::test_isMediaNotification_*`.
- Полная изоляция: ни один не-медиа пакет не проходит фильтр.
