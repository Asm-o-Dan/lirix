## Задача STORE-P2-003: Обновить PreMigrationBackup до версии 3.0

**Файл:** `core/storage/src/main/kotlin/com/example/npc/core/storage/backup/PreMigrationBackup.kt` (модифицировать)  
**Модуль:** `:core:storage`  
**Спека:** `.sdd/specs/pipeline-store/overview.md#2-холодный-файловый-снапшот-базы-данных-premigrationbackup-30`  
**Контракт:** `.sdd/contracts/pipeline-store__dsl.md#3-миграция-v2--v3-и-premigrationbackup-30`  

---

### Сигнатуры (НЕ МЕНЯТЬ):

```kotlin
package com.example.npc.core.storage.backup

import android.content.Context
import java.io.File

object PreMigrationBackup {
    private const val SQLITE_HEADER_PREFIX = "SQLite format 3\u0000"
    private const val MIN_SQLITE_FILE_SIZE = 512L

    /**
     * Создает холодный пофайловый снимок триады базы данных (.db, -wal, -shm)
     * строго до открытия базы через RoomDatabase.Builder.build().
     *
     * @param context Контекст приложения.
     * @param databaseName Имя файла базы данных (например, "npc_database.db").
     * @param targetVersion Целевая версия схемы Room (по умолчанию 3).
     * @return true, если бэкап успешно создан или уже существует; false, если бэкап не требовался.
     * @throws IllegalStateException при нехватке свободного дискового пространства.
     */
    fun executeIfNeeded(context: Context, databaseName: String, targetVersion: Int = 3): Boolean
}
```

---

### Поведение:

1. **Проверка необходимости бэкапа:**
   - Если `targetVersion <= 1`, немедленно возвращает `false` (для начальной базы бэкап не требуется).
   - Получает файл базы данных: `val dbFile = context.getDatabasePath(databaseName)?.takeIf { it.exists() } ?: return false`.
   - Если `dbFile.length() < MIN_SQLITE_FILE_SIZE` (512 байт), файл пуст или поврежден $\to$ возвращает `false`.
2. **Определение текущей версии схемы:**
   - Для незашифрованных баз SQLite пытается прочитать `user_version` по смещению 60..63 заголовка файла через `RandomAccessFile`.
   - Для зашифрованных баз SQLCipher заголовок зашифрован (магические байты `SQLite format 3` отсутствуют). В этом случае применяется детерминированный fallback: `fromVersion = targetVersion - 1` (для targetVersion=3 `fromVersion = 2`).
   - Если определенная версия `currentVersion >= targetVersion`, возвращает `false` (миграция не требуется, база уже актуальна).
3. **Проверка целевой директории и существующего бэкапа:**
   - Директория назначения: `val backupDir = context.noBackupFilesDir ?: throw IllegalStateException("noBackupFilesDir unavailable")`. Если директория не существует, создается `backupDir.mkdirs()`.
   - Основной файл бэкапа: `File(backupDir, "$databaseName.v$fromVersion.bak")`.
   - Если файл бэкапа данной версии уже существует на диске, повторное копирование пропускается $\to$ возвращает `true`.
4. **Проверка дискового пространства ($2\times$ запас):**
   - Находит сопутствующие файлы в каталоге БД: `dbFile.parentFile` $\to$ `"${dbFile.name}-wal"` и `"${dbFile.name}-shm"`.
   - Рассчитывает суммарный размер: `totalSize = dbFile.length() + walFile.length() + shmFile.length()`.
   - Проверяет доступное место: `val requiredSpace = totalSize * 2`.
   - Если `backupDir.usableSpace < requiredSpace`, выбрасывает `IllegalStateException("Insufficient disk space in noBackupFilesDir: required $requiredSpace bytes, available ${backupDir.usableSpace} bytes")`.
5. **Атомарное бинарное копирование триады файлов:**
   - Копирует `dbFile.copyTo(backupFile, overwrite = true)`.
   - Если `walFile.exists() && walFile.length() > 0`, копирует `walFile.copyTo(File(backupDir, "$databaseName.v$fromVersion.bak-wal"), overwrite = true)`.
   - Если `shmFile.exists() && shmFile.length() > 0`, копирует `shmFile.copyTo(File(backupDir, "$databaseName.v$fromVersion.bak-shm"), overwrite = true)`.
   - Возвращает `true`.

---

### Ошибки:

- При `backupDir.usableSpace < requiredSpace` выбрасывать `IllegalStateException` (предотвращает начало миграции при риске переполнения накопителя Poco M7).
- Непредвиденные ошибки ввода-вывода (IO) логируются, и при невозможности создать резервную копию выбрасывается контролируемое исключение, чтобы не допустить повреждения базы догфудинга без возможности отката.

---

### Граничные случаи:

- Файлы WAL и SHM отсутствуют (БД была закрыта в чистом состоянии с контрольной точкой) — копируется только `.db`.
- База данных размером > 50 МБ на Poco M7 — копирование потоками по 64 КБ занимает $\le 200$ мс.
- Зашифрованная SQLCipher база данных: страницы копируются побайтово без дешифрования (ключ шифрования не требуется).

---

### Запрещено:

- Открывать базу данных через `SQLiteDatabase.openDatabase()` или `Room.databaseBuilder().build()` внутри `PreMigrationBackup` (это запустит механизм миграции Room до создания снимка!).
- Сохранять файлы бэкапа в каталоги, доступные для облачного резервирования (`context.filesDir`, `context.cacheDir`), или во внешнее хранилище (нарушение приватности финансового реестра).
- Игнорировать проверку свободного места на устройстве.

---

### Критерий приёмки:

- Класс `PreMigrationBackup` скомпилирован в модуле `:core:storage`.
- Unit/Robolectric тесты проверяют:
  1. Корректное копирование файлов `.db`, `-wal`, `-shm` в `noBackupFilesDir`.
  2. Выброс `IllegalStateException` при эмуляции недостаточного объема диска.
  3. Пропуск бэкапа, если версия совпадает или бэкап уже создан.
