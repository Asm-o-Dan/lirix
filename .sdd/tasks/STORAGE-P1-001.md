## Задача STORAGE-P1-001: Реализовать утилиту PreMigrationBackup

**Модуль:** `:core:storage`  
**Целевой файл:** `core/storage/src/main/kotlin/com/example/npc/core/storage/migration/PreMigrationBackup.kt`  
**Спецификация:** `.sdd/specs/core-storage/overview.md#32-механизм-холодного-резервного-копирования-premigrationbackup`  
**Архитектура:** `.sdd/architecture_phase1.md#52`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.storage.migration

import android.content.Context

object PreMigrationBackup {
    fun executeIfNeeded(context: Context, databaseName: String, targetVersion: Int)
}
```

### Инварианты и алгоритм:
1. Выполняется ДО `RoomDatabase.Builder.build()`.
2. Проверяет наличие файла БД (`context.getDatabasePath(databaseName)`). Если файла нет — мгновенный return.
3. Открывает файл через `SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE)`.
4. Принудительно выполняет чекпоинт WAL-журнала: `PRAGMA wal_checkpoint(TRUNCATE)`.
5. Считывает `db.version`. Если `currentVersion in 1 until targetVersion`:
   - Проверяет свободное место: `context.noBackupFilesDir.usableSpace > dbFile.length() * 2`.
   - Создает копию файла БД: `File(context.noBackupFilesDir, "$databaseName.v$currentVersion.bak")`.
6. Перехватывает любые исключения и не ломает старт приложения, если бэкап не удался.

### Критерии приемки (DoD):
- [ ] При первом запуске поверх БД v1 создается консистентный бэкап `.bak` в `noBackupFilesDir`.
- [ ] WAL сброшен в основной файл перед копированием.
- [ ] Тесты покрывают сценарии: существующая БД v1, отсутствие файла, отсутствие свободного места.
