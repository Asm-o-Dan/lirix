## Задача NOTIF-002: Модели данных и NotificationMapper

**Файлы:**
- `ingest/notification/src/main/kotlin/com/example/npc/ingest/notification/model/MessagingMessageData.kt`
- `ingest/notification/src/main/kotlin/com/example/npc/ingest/notification/model/MessagingStyleData.kt`
- `ingest/notification/src/main/kotlin/com/example/npc/ingest/notification/model/NotificationExtrasData.kt`
- `ingest/notification/src/main/kotlin/com/example/npc/ingest/notification/model/NotificationRawPayload.kt`
- `ingest/notification/src/main/kotlin/com/example/npc/ingest/notification/model/FilterRejectionReason.kt`
- `ingest/notification/src/main/kotlin/com/example/npc/ingest/notification/model/FilterDecision.kt`
- `ingest/notification/src/main/kotlin/com/example/npc/ingest/notification/mapper/NotificationMapper.kt`
(создать)

**Спека:** `.sdd/specs/ingest-notification/overview.md#типы-данных-и-структуры-data-structures--dto` и `#класс-notificationmapper` (v1)  
**Зависит от:** `NOTIF-001`  

**Поведение:**
1. Реализовать DTO-классы в точном соответствии со спецификацией:
   - `MessagingMessageData`: `(text: String, timestampMillis: Long, senderName: String?)`
   - `MessagingStyleData`: `(conversationTitle: String?, isGroupConversation: Boolean, messages: List<MessagingMessageData>, historicMessages: List<MessagingMessageData>)`
   - `NotificationExtrasData`: `(title, text, bigText, textLines, subText, infoText, progressMax, progressCurrent, isProgressIndeterminate, conversationTitle, messagingStyle)`
   - `NotificationRawPayload`: `(seq, packageName, id, tag, key, groupKey, postTimeEpochMs, flags, channelId, extras, receivedAt)`
   - `FilterRejectionReason`: `OWN_PACKAGE, ONGOING_EVENT, FOREGROUND_SERVICE, PROGRESS_BAR, GROUP_SUMMARY, EMPTY_CONTENT`
   - `FilterDecision`: `(isAccepted: Boolean, rejectionReason: FilterRejectionReason?)`
2. `NotificationMapper`:
   - `extractPayload(sbn, seq, receivedAt)`: извлекает метаданные из `StatusBarNotification` и вызывает `extractExtrasData`.
   - `extractExtrasData(notification)`: извлекает строковые данные (CharSequences приведены к String), обрабатывает `Notification.EXTRA_TEXT_LINES`, прогресс, и стиль сообщений.
   - `extractMessagingStyle(extras)`: парсит `EXTRA_MESSAGES` и `EXTRA_HISTORIC_MESSAGES`.
   - `toPayloadJson(payload)`: сериализует payload в стандартизированную компактную строку JSON с экранированием символов.

**Критерий приёмки:**
- `NotificationMapper` полностью извлекает все поля и корректно сериализует в JSON.
- Уведомления со спанами (`SpannableString`) безопасно конвертируются в плоский `String`.
