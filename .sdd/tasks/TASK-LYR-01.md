# Задача TASK-LYR-01: реализовать `getNetworkPolicy`

**Файл:** `app/src/main/java/com/eventengine/app/feature/lyrics/NetworkConditionManager.kt` (создать)
**Спека:** [.sdd/specs/lyrics-engine/overview.md#getNetworkPolicy](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/lyrics-engine/overview.md) (v1), контракт `contracts/core__lyrics.md`

**Сигнатура (НЕ МЕНЯТЬ):**
```kotlin
fun getNetworkPolicy(context: Context): LyricsNetworkPolicy
```

**Модель перечисления:**
```kotlin
enum class LyricsNetworkPolicy {
    PREFETCH_ALLOWED, // Wi-Fi / Ethernet - автозагрузка текста и аккордов
    ON_DEMAND_ONLY,   // Сотовая сеть (Cellular) - загрузка только по клику
    OFFLINE_ONLY      // Нет подключения - только локальный кэш
}
```

**Поведение:**
1. Получить `ConnectivityManager` из `context.getSystemService(Context.CONNECTIVITY_SERVICE)`.
2. Если `connectivityManager == null` $\to$ вернуть `LyricsNetworkPolicy.OFFLINE_ONLY`.
3. Получить активную сеть `val network = connectivityManager.activeNetwork ?: return LyricsNetworkPolicy.OFFLINE_ONLY`.
4. Получить `val caps = connectivityManager.getNetworkCapabilities(network) ?: return LyricsNetworkPolicy.OFFLINE_ONLY`.
5. Если `caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)` или `caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)`:
   - Вернуть `LyricsNetworkPolicy.PREFETCH_ALLOWED`.
6. Если `caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)`:
   - Вернуть `LyricsNetworkPolicy.ON_DEMAND_ONLY`.
7. Во всех остальных случаях вернуть `LyricsNetworkPolicy.OFFLINE_ONLY`.

**Ошибки:**
- Не бросать исключений при отсутствии разрешений или null-контексте, безопасно возвращать `OFFLINE_ONLY`.

**Критерий приёмки:**
- Тест `NetworkConditionManagerTest::test_network_policy_*` проходит (mock ConnectivityManager для Wi-Fi, Cellular, Offline).
