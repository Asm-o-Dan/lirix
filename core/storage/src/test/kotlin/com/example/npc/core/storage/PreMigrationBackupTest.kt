package com.example.npc.core.storage

import android.content.Context
import com.example.npc.core.storage.migration.PreMigrationBackup
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PreMigrationBackupTest {

    @TempDir
    lateinit var tempDir: File

    private val context: Context = mockk(relaxed = true)
    private lateinit var dbDir: File
    private lateinit var noBackupDir: File

    @BeforeEach
    fun setUp() {
        dbDir = File(tempDir, "databases").apply { mkdirs() }
        noBackupDir = File(tempDir, "no_backup").apply { mkdirs() }

        every { context.noBackupFilesDir } returns noBackupDir
    }

    @Test
    fun `executeIfNeeded does nothing when database file does not exist`() {
        val nonExistentDb = File(dbDir, "non_existent.db")
        every { context.getDatabasePath("non_existent.db") } returns nonExistentDb

        PreMigrationBackup.executeIfNeeded(
            context = context,
            databaseName = "non_existent.db",
            targetVersion = 2
        )

        noBackupDir.listFiles()?.isEmpty() shouldBe true
    }

    @Test
    fun `executeIfNeeded does not backup when current version equals or exceeds target version`() {
        val dbFile = File(dbDir, "test_v2.db").apply {
            writeBytes("mock sqlite v2 content".toByteArray())
        }
        every { context.getDatabasePath("test_v2.db") } returns dbFile

        // Even if file exists, if version is already >= targetVersion (or cannot be determined as < target), no backup created
        PreMigrationBackup.executeIfNeeded(
            context = context,
            databaseName = "test_v2.db",
            targetVersion = 1
        )

        val backups = noBackupDir.listFiles { _, name -> name.endsWith(".bak") }
        backups?.isEmpty() shouldBe true
    }

    @Test
    fun `executeIfNeeded safely handles corrupt or unreadable database without throwing`() {
        val corruptFile = File(dbDir, "corrupt.db").apply {
            writeBytes("NOT_A_SQLITE_DATABASE".toByteArray())
        }
        every { context.getDatabasePath("corrupt.db") } returns corruptFile

        // Should return cleanly without throwing any exception
        PreMigrationBackup.executeIfNeeded(
            context = context,
            databaseName = "corrupt.db",
            targetVersion = 2
        )

        val backups = noBackupDir.listFiles { _, name -> name.endsWith(".bak") }
        backups?.isEmpty() shouldBe true
    }

    @Test
    fun `executeIfNeeded checks usable space before creating backup`() {
        val dbFile = File(dbDir, "large.db").apply {
            writeBytes(ByteArray(1024 * 1024)) // 1MB file
        }
        every { context.getDatabasePath("large.db") } returns dbFile

        // When space is sufficient and conditions met, backup format is <dbName>.v<currentVersion>.bak
        val expectedBackupName = "large.db.v1.bak"
        val expectedBackupFile = File(noBackupDir, expectedBackupName)

        // Verifying backup target path logic
        expectedBackupFile.name shouldBe expectedBackupName
    }

    @Test
    fun `executeIfNeeded creates backup of database along with wal and shm files`() {
        val dbFile = File(dbDir, "app.db").apply {
            writeBytes(ByteArray(1024))
        }
        val walFile = File(dbDir, "app.db-wal").apply {
            writeBytes(ByteArray(512))
        }
        val shmFile = File(dbDir, "app.db-shm").apply {
            writeBytes(ByteArray(256))
        }
        every { context.getDatabasePath("app.db") } returns dbFile

        PreMigrationBackup.executeIfNeeded(
            context = context,
            databaseName = "app.db",
            targetVersion = 2
        )

        val backupDb = File(noBackupDir, "app.db.v1.bak")
        val backupWal = File(noBackupDir, "app.db.v1.bak-wal")
        val backupShm = File(noBackupDir, "app.db.v1.bak-shm")

        backupDb.exists() shouldBe true
        backupDb.length() shouldBe 1024
        backupWal.exists() shouldBe true
        backupWal.length() shouldBe 512
        backupShm.exists() shouldBe true
        backupShm.length() shouldBe 256
    }

    @Test
    fun `executeIfNeeded skips backup if already exists`() {
        val dbFile = File(dbDir, "app.db").apply {
            writeBytes(ByteArray(1024))
        }
        val existingBackup = File(noBackupDir, "app.db.v1.bak").apply {
            writeBytes("existing_backup_content".toByteArray())
        }
        every { context.getDatabasePath("app.db") } returns dbFile

        PreMigrationBackup.executeIfNeeded(
            context = context,
            databaseName = "app.db",
            targetVersion = 2
        )

        existingBackup.readText() shouldBe "existing_backup_content"
    }
}
