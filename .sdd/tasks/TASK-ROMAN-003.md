# TASK-ROMAN-003: Chinese Hanzi to Pinyin Engine

**Спецификация:** `SPEC-ROMAN-01` §2, §3  
**Зона:** lyrics-engine  
**Файл:** `shared/src/commonMain/kotlin/com/lirix/app/feature/lyrics/romanization/ChinesePinyinEngine.kt`  
**Зависимости:** `TASK-ROMAN-001`

## Что сделать
1. Реализовать транслитерацию китайских иероглифов (Ханьцзы) в Mandarin Pinyin:
   - Встроенная таблица частых иероглифов Юникода (CJK Unified Ideographs).
   - Поддержка тональных знаков (ā, á, ǎ, à, ē, é, ě, è, ī, í, ǐ, ì, ō, ó, ǒ, ò, ū, ú, ǔ, ù, ǖ, ǘ, ǚ, ǜ) и режима без тонов.
   - Сегментация на слова с пробелами между слогами.
   - Сохранение пунктуации и латинских символов.

## DoD
- Unit-тесты:
  - "你好" $\rightarrow$ "nǐ hǎo" (или "ni hao" без тонов)
  - "我爱你" $\rightarrow$ "wǒ ài nǐ"
- Нулевые внешние зависимости, быстрая работа в памяти commonMain.
