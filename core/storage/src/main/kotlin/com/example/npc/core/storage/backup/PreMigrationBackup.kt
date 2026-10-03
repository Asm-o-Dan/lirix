package com.example.npc.core.storage.backup

import android.content.Context
import java.io.File
import java.io.RandomAccessFile

object PreMigrationBackup {
    private const val SQLITE_HEADER_PREFIX = "SQLite format 3\u0000"
    private const val MIN_SQLITE_FILE_SIZE = 512L

    /**
     * Создает снимок БД перед RoomDatabase.Builder.build().
     * Вызывается строго в фоновом потоке до открытия базы Room.
     * Безопасно работает на файловом уровне как с обычной SQLite,
     * так и с зашифрованной SQLCipher БД, включая связанные WAL и SHM файлы.
     */
    fun executeIfNeeded(context: Context, databaseName: String, targetVersion: Int = 3): Boolean {
        if (targetVersion <= 1) return false

        try {
            val dbFile = context.getDatabasePath(databaseName)?.takeIf { it.exists() } ?: return false

            // Если размер файла меньше минимального размера страницы SQLite, файл поврежден или пуст
            if (dbFile.length() < MIN_SQLITE_FILE_SIZE) return false

            val currentVersion = readUnencryptedVersionOrNull(dbFile)
            if (currentVersion != null && currentVersion >= targetVersion) {
                return false
            }

            val fromVersion = currentVersion ?: (targetVersion - 1)
            val backupDir = context.noBackupFilesDir ?: return false
            if (!backupDir.exists()) backupDir.mkdirs()

            val backupFile = File(backupDir, "$databaseName.v$fromVersion.bak")

            // Если бэкап для данной версии уже существует, повторное копирование не требуется
            if (backupFile.exists()) return true

            val walFile = File(dbFile.parentFile, "${dbFile.name}-wal")
            val shmFile = File(dbFile.parentFile, "${dbFile.name}-shm")

            var totalSize = dbFile.length()
            if (walFile.exists()) totalSize += walFile.length()
            if (shmFile.exists()) totalSize += shmFile.length()

            // Проверка свободного места: требуется минимум двукратный запас размера базы
            val requiredSpace = totalSize * 2
            if (backupDir.usableSpace in 1 until requiredSpace) {
                throw IllegalStateException("Insufficient disk space in noBackupFilesDir: required $requiredSpace bytes, available ${backupDir.usableSpace} bytes")
            }

            // Копирование основного файла БД
            dbFile.copyTo(backupFile, overwrite = true)

            // Копирование WAL-журнала при наличии
            if (walFile.exists() && walFile.length() > 0) {
                val backupWal = File(backupDir, "$databaseName.v$fromVersion.bak-wal")
                walFile.copyTo(backupWal, overwrite = true)
            }

            // Копирование SHM-файла при наличии
            if (shmFile.exists() && shmFile.length() > 0) {
                val backupShm = File(backupDir, "$databaseName.v$fromVersion.bak-shm")
                shmFile.copyTo(backupShm, overwrite = true)
            }

            return true
        } catch (e: IllegalStateException) {
            throw e
        } catch (_: Throwable) {
            return false
        }
    }

    private fun readUnencryptedVersionOrNull(file: File): Int? {
        if (file.length() < 68) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val headerBytes = ByteArray(16)
                raf.readFully(headerBytes)
                if (String(headerBytes, Charsets.US_ASCII) != SQLITE_HEADER_PREFIX) {
                    return null
                }
                raf.seek(60)
                raf.readInt()
            }
        } catch (_: Throwable) {
            null
        }
    }
}
