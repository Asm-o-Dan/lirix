# Task: TASK-LIB-02 (Full-Text Lyrics & Notes Deep Search in Library)

## 1. Context & Motivation
Пользователи часто помнят конкретную фразу из песни или ключевое слово из своих гитарных заметок, но забывают точное название трека или артиста.
Текущий поиск фильтрует только по `title` и `artist`, игнорируя текст песни (`plainLyrics`, `syncedLyrics`) и персональные заметки (`userNotes`).

## 2. Specification
1. **Search Matching Expansion**:
   В `LibraryScreen.kt` в списках `filteredLibraryTracks` и `filteredHistoryTracks`:
   - Совпадение засчитывается при нахождении `searchQuery` в:
     - `title`
     - `artist`
     - `album`
     - `plainLyrics`
     - `syncedLyrics`
     - `userNotes`
2. **Contextual Search Snippets**:
   - Функция `extractSearchSnippet(text, query)` извлекает 50 символов контекста вокруг совпадения.
   - В карточке трека отображается стильный сниппет:
     - `🎵 "...найденный фрагмент..."` (CyberCyan) при совпадении в тексте песни.
     - `📝 "...найденный фрагмент..."` (ElectricMint) при совпадении в заметках.
3. **Ergonomics & Performance**:
   - Быстрый поиск в памяти (in-memory Kotlin collection filtering).
   - Тактильный отклик при очистке поиска.
