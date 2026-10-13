# Задача TASK-TEACH-05: Стабилизация Touch Inspector, исключение потомков (Strip) и редизайн TeachMode

**Статус**: DONE  
**Зона**: `lyrics-engine` / `media-ui`  
**Спецификация**: `.sdd/specs/lyrics-engine/teach_inspector_v2_spec.md`  
**Архитектура**: `.sdd/architecture_teach_inspector_ux.md` / ADR-014  
**Исполнитель**: Кодер Media Ingress & UI  

## Чек-лист реализации

### 1. `app/src/main/assets/teach_inspector.js`
- Внедрить детекцию скролла в `touchstart` + `touchmove`: при $\Delta > 8\text{px}$ выставлять флаг `isScrolling = true` и полностью глушить событие инспекции в `touchend`.
- Устранить тройной вызов (`touchend` + `pointerup` + `click`) через дебаунс 300мс и подавление событий `click`.
- Реализовать двухслойное выделение:
  - 🟣 Родительский контейнер (`window.__selectedParents`) с фиолетовой рамкой.
  - 🔴 Исключенные потомки (`window.__strippedChildren`) с красным пунктиром и зачеркиванием при клике внутри родителя.
- Экспортировать функцию `window.expandTeachSelectionToParent()` для кнопки `[▲ Родитель]`.
- Передавать в `TeachBridge.onElementSelected` селектор родителя, JSON-массив `stripSelectors`, очищенный текст и количество вырезанных блоков.

### 2. `app/src/main/java/com/lirix/app/ui/TeachModeScreen.kt`
- **Шапка (Top Bar)**:
  - Разгрузить: Назад/Закрыть слева, название трека и домен по центру, капсула `[🌐 Сёрфинг | 🎯 Инспектор]` и кнопка меню **⋮** справа.
  - Создать `DropdownMenu`: Импорт правила, Экспорт правила, Сброс выделений, Открыть в браузере.
- **Нижняя панель (Inspector Dock)**:
  - Добавить бейдж `[🔴 N вырезано]` при наличии исключений.
  - Добавить кнопку `[▲ Родитель]`, вызывающую `window.expandTeachSelectionToParent()`.
  - Добавить сегментированный переключатель: **[📝 Текст песни]** / **[🎸 Аккорды]**.
  - Объединить действия в одну главную кнопку **«Сохранить правило и привязать»**:
    - Генерирует `search.urlTemplate` (например, `https://${domain}/search?q={query}`).
    - В зависимости от выбранного типа («Текст» или «Аккорды») записывает селектор в `content.selector` или `content.chordsSelector`, а вырезанные блоки — в `content.stripSelectors`.
    - Сохраняет текст/аккорды в Room (`music_tracks` и `lyrics_cache`) и возвращает пользователя в плеер.

---

## Верификация
1. `./gradlew testDebugUnitTest` — все тесты зеленые.
2. `./gradlew assembleRelease` — успешная сборка APK.
3. Отчет предоставить Координатору.
