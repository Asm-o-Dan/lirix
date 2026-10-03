# 📊 Dogfooding Phase 0 Verification & Analytics Report

**File Evaluated:** `synthetic_dataset.json`  
**Validation Timestamp:** `2026-09-26T09:47:06.115323+00:00`  
**Overall Gate Status:** **🟢 PASS**

---

## 🎯 Summary Gate Evaluation (DoD Criteria)

| DoD Criterion | Target Metric | Observed Value | Status |
| :--- | :--- | :--- | :---: |
| **DoD 8: Export Schema & Integrity** | 0 RFC 8259 syntax/type errors | 0 errors | 🟢 PASS |
| **DoD 6: Garbage & Duplicates** | 0 Group Summaries leaked, updates merged | 0 leaked, 0 unlinked | 🟢 PASS |
| **DoD 2: Banking Transactions** | Complete capture (SMS + Push), zero drop | 2 txs (1 SMS, 1 Push) | 🟢 PASS |
| **DoD 3: Notification Distribution** | Representative sampling & clean audit | 8 packages, sample size 20 | 🟢 PASS |
| **DoD 4: Music Playback Continuity** | Session duration & seamless transitions | 0h 13m 37s (3 tracks), 0 anomalies | 🟢 PASS |

---

## 🏦 DoD 2: Banking & Financial Transactions Breakdown

- **Total Financial Events:** 2
- **SMS Banking Count:** 1
- **Push Banking Count:** 1
- **Continuity Status:** No abnormal gaps (> 72h) detected between consecutive financial operations.

---

## 📱 DoD 3: Notification Packages & Shade Audit Sample

### Top Packages Distribution
| Package Identifier | Event Count |
| :--- | :---: |
| `org.telegram.messenger` | 5 |
| `com.whatsapp` | 5 |
| `com.google.android.calendar` | 5 |
| `com.yandex.eda` | 5 |
| `com.wildberries` | 5 |
| `com.spotify.music` | 3 |
| `com.android.sms` | 1 |
| `com.idamob.tinkoff.android` | 1 |

### 20 Random Events for Manual Notification Shade Cross-Check
*(Deterministic sample with seed 42 to verify 0 dropped notifications)*

| Event ID | Time (UTC) | Package / Thread | Title | Text Snippet |
| :---: | :--- | :--- | :--- | :--- |
| 22 | `2026-09-20 10:19:20 UTC` | `notif:com.wildberries:tag_19:219` | Wildberries | Заказ готов к выдаче в ПВЗ #19 |
| 5 | `2026-09-20 10:02:20 UTC` | `notif:com.google.android.calendar:tag_2:202` | Google Календарь | Встреча через 10 минут: Синхронизация #2 |
| 2 | `2026-09-20 10:00:10 UTC` | `notif:com.idamob.tinkoff.android:fin_tx:101` | Т-Банк | Перевод +1500 ₽ от Иван И. |
| 10 | `2026-09-20 10:07:20 UTC` | `notif:com.google.android.calendar:tag_7:207` | Google Календарь | Встреча через 10 минут: Синхронизация #7 |
| 9 | `2026-09-20 10:06:20 UTC` | `notif:com.whatsapp:tag_6:206` | WhatsApp | Мама: Купи хлеб #6 |
| 23 | `2026-09-20 10:20:20 UTC` | `notif:org.telegram.messenger:tag_20:220` | Telegram | Анна: Привет, когда созвон? #20 |
| 6 | `2026-09-20 10:03:20 UTC` | `notif:com.yandex.eda:tag_3:203` | Яндекс Еда | Курьер уже в пути к вам #3 |
| 26 | `2026-09-20 10:23:20 UTC` | `notif:com.yandex.eda:tag_23:223` | Яндекс Еда | Курьер уже в пути к вам #23 |
| 19 | `2026-09-20 10:16:20 UTC` | `notif:com.whatsapp:tag_16:216` | WhatsApp | Мама: Купи хлеб #16 |
| 4 | `2026-09-20 10:01:20 UTC` | `notif:com.whatsapp:tag_1:201` | WhatsApp | Мама: Купи хлеб #1 |
| 15 | `2026-09-20 10:12:20 UTC` | `notif:com.google.android.calendar:tag_12:212` | Google Календарь | Встреча через 10 минут: Синхронизация #12 |
| 25 | `2026-09-20 10:22:20 UTC` | `notif:com.google.android.calendar:tag_22:222` | Google Календарь | Встреча через 10 минут: Синхронизация #22 |
| 16 | `2026-09-20 10:13:20 UTC` | `notif:com.yandex.eda:tag_13:213` | Яндекс Еда | Курьер уже в пути к вам #13 |
| 3 | `2026-09-20 10:00:20 UTC` | `notif:org.telegram.messenger:tag_0:200` | Telegram | Анна: Привет, когда созвон? #0 |
| 20 | `2026-09-20 10:17:20 UTC` | `notif:com.google.android.calendar:tag_17:217` | Google Календарь | Встреча через 10 минут: Синхронизация #17 |
| 13 | `2026-09-20 10:10:20 UTC` | `notif:org.telegram.messenger:tag_10:210` | Telegram | Анна: Привет, когда созвон? #10 |
| 24 | `2026-09-20 10:21:20 UTC` | `notif:com.whatsapp:tag_21:221` | WhatsApp | Мама: Купи хлеб #21 |
| 17 | `2026-09-20 10:14:20 UTC` | `notif:com.wildberries:tag_14:214` | Wildberries | Заказ готов к выдаче в ПВЗ #14 |
| 12 | `2026-09-20 10:09:20 UTC` | `notif:com.wildberries:tag_9:209` | Wildberries | Заказ готов к выдаче в ПВЗ #9 |
| 7 | `2026-09-20 10:04:20 UTC` | `notif:com.wildberries:tag_4:204` | Wildberries | Заказ готов к выдаче в ПВЗ #4 |

---

## 🎵 DoD 4: Music Session & Continuity Audit

- **Total Media Sessions:** 3
- **Useful Playback Time:** **0h 13m 37s** (817000 ms)
- **Micro-Sessions Filtered (< 5s skips):** 0
- **Track Continuity:** Continuous playback sequence verified with 0 overlapping collisions.

---

## 🧹 DoD 6: Garbage & Cleanliness Audit

- **Leaked Group Summaries (`FLAG_GROUP_SUMMARY`):** 0
- **Unlinked Duplicate Updates:** 0

---

## 🔍 Detailed Issues Log

🎉 **No validation errors or critical warnings found.**