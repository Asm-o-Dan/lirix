# Спецификация Зоны Media UI: Экран Конструктора Запретов Медиатеки (SPEC-BLK-02)

**TASK_ID:** SPEC-BLK-02  
**Зона ответственности:** `media-ui`  
**Зависимости:** ARCH-BLK-01, TASK-DESIGN-AUDIT-02 (.sdd/reports/UI_DESIGN_AUDIT_FRONTEND.md), SPEC-BLK-01  
**Статус:** FROZEN v1  

---

## 1. Назначение и Контекст
Спецификация регламентирует реализацию экрана `BlacklistSettingsScreen.kt`, интерактивного диалога-конструктора правил запрета с реактивным счетчиком удаляемых треков, навигацию из шапки плеера и библиотеки, а также интеграцию в дизайн-систему 4 тем `LirixThemeTokens.colors`.

---

## 2. Архитектура Состояния (UiState)

```kotlin
package com.lirix.app.ui.blacklist

import com.lirix.app.storage.blacklist.BlacklistMatchMode
import com.lirix.app.storage.blacklist.BlacklistRuleEntity
import com.lirix.app.storage.blacklist.BlacklistRuleType

data class BlacklistUiState(
    val rules: List<BlacklistRuleEntity> = emptyList(),
    val activeFilterTab: BlacklistTab = BlacklistTab.ALL,
    val isLoading: Boolean = false,
    val isConstructorOpen: Boolean = false,
    // Поля черновика конструктора
    val draftType: BlacklistRuleType = BlacklistRuleType.TITLE,
    val draftPattern: String = "",
    val draftMatchMode: BlacklistMatchMode = BlacklistMatchMode.CONTAINS_IGNORE_CASE,
    val draftPurgePreviewCount: Int = 0,
    val isCalculatingPreview: Boolean = false,
    // Уведомления
    val snackbarMessage: String? = null
)

enum class BlacklistTab {
    ALL,
    TITLES,
    ARTISTS,
    PACKAGES
}
```

---

## 3. Компоненты Экрана `BlacklistSettingsScreen`

### 3.1 Шапка экрана (TopAppBar)
- Кнопка «Назад» (`NavigationBack`, touch target $48\times 48\,\text{dp}$).
- Заголовок: `ФИЛЬТРЫ МЕДИАТЕКИ // ЧЕРНЫЙ СПИСОК` (шрифт полужирный, цвет `textPrimary`).
- Подзаголовок: `Контроль чистоты фонотеки и авто-очистка` (шрифт 12sp, цвет `textSecondary`).

### 3.2 Информационный дашборд-баннер
- Контейнер со скруглением `12.dp`, фон `surfaceCard`, тонкая рамка `outlineVariant`.
- Метрики:
  - `ПРАВИЛА АКТИВНЫ: N` (акцентный цвет `accentPrimary`).
  - Текст описания: `Входящий поток сканируется на лету при воспроизведении. Блокированные треки не сохраняются в фонотеку.`

### 3.3 Вкладки фильтрации списка правил
Сегментированный ряд чипов (`All`, `Titles`, `Artists`, `Packages`) с высотой строки $48\,\text{dp}$:
- `[ ВСЕ (N) ]`
- `[ ТРЕКИ (N) ]`
- `[ АРТИСТЫ (N) ]`
- `[ ПРИЛОЖЕНИЯ (N) ]`

### 3.4 Карточка правила (`BlacklistRuleCard`)
- **Бейдж типа**:
  - `TITLE`: моноширинный тег `[ НАЗВАНИЕ ]` (фон `accentPrimary.copy(alpha = 0.15f)`, текст `accentPrimary`).
  - `ARTIST`: тег `[ АРТИСТ ]` (фон `accentSecondary.copy(alpha = 0.15f)`, текст `accentSecondary`).
  - `PACKAGE_NAME`: тег `[ ПРОВАЙДЕР ]` (фон `accentGlow.copy(alpha = 0.15f)`, текст `accentGlow`).
- **Текст паттерна**: `Text(rule.pattern, fontWeight = FontWeight.Bold, color = textPrimary)`.
- **Подпись**: режим сопоставления (`Подстрока`, `Точное совпадение`, `Regex`) и статус (`Активно` / `На паузе`).
- **Элементы управления**:
  - Тумблер `Switch(checked = rule.isEnabled, onCheckedChange = { ... })`.
  - Кнопка удаления `IconButton` с иконкой корзины (touch target $48\times 48\,\text{dp}$) с виброоткликом `HapticFeedbackType.LongPress`.

### 3.5 Пустое состояние (Empty State)
Если правил нет:
- Иконка `ShieldCheck` размером $64\,\text{dp}$.
- Текст: `Медиатека не фильтруется`.
- Подпись: `Создайте первое правило, чтобы заблокировать рекламу, голосовые сообщения из мессенджеров или нежелательных артистов.`

### 3.6 Нижняя кнопка «+ Создать правило блокировки»
- Закреплена внизу экрана (Floating Action Button или полноширинная кнопка в нижней панели).
- Высота кнопки $\ge 52\,\text{dp}$, фон `accentPrimary`, текст `surfaceBase` (высококонтрастный полужирный).

---

## 4. Конструктор Правила (`BlacklistRuleConstructorSheet`)

Выполняется в виде `ModalBottomSheet` (поддерживает жест свайпа вниз для закрытия):

1. **Заголовок конструктора**:
   `НОВОЕ ПРАВИЛО ЗАПРЕТА` + кнопка закрытия `[X]`.
2. **Селектор критерия**:
   Ряд из 3 кнопок-селекторов:
   - `[ 🎵 По названию ]`
   - `[ 👤 По артисту ]`
   - `[ 📦 Приложение ]`
3. **Поле ввода паттерна**:
   - `OutlinedTextField` с кнопкой очистки ввода.
   - Плейсхолдер зависит от выбранного типа:
     - Для `TITLE`: `Например: "Реклама", "Voice note", "Голосовое"`
     - Для `ARTIST`: `Например: "Нежелательный исполнитель"`
     - Для `PACKAGE_NAME`: `Например: "org.telegram.messenger"`
4. **Быстрые пресеты для приложений** (показываются только при выборе `PACKAGE_NAME`):
   Горизонтальная лента чипов в 1 клик:
   - `[ Spotify ]` -> вставляет `com.spotify.music`
   - `[ VK Музыка ]` -> вставляет `com.vkontakte.android`
   - `[ Яндекс Музыка ]` -> вставляет `ru.yandex.music`
   - `[ Telegram ]` -> вставляет `org.telegram.messenger`
   - `[ YouTube Music ]` -> вставляет `com.google.android.apps.youtube.music`
5. **Селектор режима сопоставления**:
   - `(•) Содержит подстроку`
   - `( ) Точное совпадение`
   - `( ) Регулярное выражение (Regex)`
6. **Реактивный расчет очистки (Immediate Purge Preview)**:
   - По мере набора текста срабатывает дебаунс $250\,\text{ms}$, запрашивающий базу данных на количество совпадений.
   - Плашка предварительного расчета:
     - Если совпадений $> 0$:
       Красная/терракотовая предупреждающая рамка:  
       `⚠️ В текущей медиатеке найдено: N треков. Они будут БЕЗВОЗВРАТНО УДАЛЕНЫ при подтверждении!`
     - Если совпадений $0$:
       Информационная серая плашка:  
       `В текущей медиатеке совпадений нет. Правило будет защищать от будущих записей.`
7. **Кнопка подтверждения**:
   - Текст: `Исключить и удалить N треков` (или `Создать правило защиты`).
   - При нажатии:
     1. Создается и сохраняется `BlacklistRuleEntity`.
     2. Вызывается `purgeMatchingTracks(rule)`.
     3. Закрывается BottomSheet.
     4. Показывается Snackbar: `Правило сохранено. Удалено X треков и Y сессий`.

---

## 5. Точки Входа и Навигация

1. **Из `NowPlayingScreen.kt` (Меню ⋮ Overflow Menu)**:
   В `NowPlayingOverflowMenu.kt` добавляется пункт:
   - Иконка `Block` / `Shield`.
   - Текст: `Черный список / Запреты`.
   - Действие: открывает `BlacklistSettingsScreen`.
2. **Из `LibraryScreen.kt` (Шапка Фонотеки)**:
   В шапку фонотеки рядом с поиском добавляется иконка щита `Shield` с переходом в `BlacklistSettingsScreen`.

---

## 6. Дизайн-система и Цветовые Токены (4 Темы)

Все компоненты экрана строго используют `LirixThemeTokens.colors`:
- Фон экрана: `tokens.surfaceBase`
- Карточки: `tokens.surfaceCard`
- Рамки и разделители: `tokens.outlineVariant`
- Основной текст: `tokens.textPrimary`
- Второстепенный текст: `tokens.textSecondary`
- Акцентные кнопки: `tokens.accentPrimary`
- Предупреждения об удалении: в темных темах `Color(0xFFE57373)` на подложке `Color(0x22E57373)`, в светлой теме `Pastel Serenity` мягкий терракотовый тон `Color(0xFFC85A5A)` на подложке `Color(0xFFFDE8E8)`.
- Сенсорные области: строго $\ge 48\times 48\,\text{dp}$.
