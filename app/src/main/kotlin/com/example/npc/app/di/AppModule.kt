package com.example.npc.app.di

import android.content.Context
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.room.Room
import androidx.work.WorkManager
import com.example.npc.core.storage.AppDatabase
import com.example.npc.core.storage.SqlCipherSupportFactoryProvider
import com.example.npc.core.storage.StorageGateway
import com.example.npc.core.storage.StorageGatewayImpl
import com.example.npc.core.storage.backup.PreMigrationBackup
import com.example.npc.core.storage.migration.MIGRATION_1_2
import com.example.npc.core.storage.migration.MIGRATION_2_3
import com.example.npc.core.storage.migration.MIGRATION_3_4
import com.example.npc.core.storage.migration.MIGRATION_4_5
import com.example.npc.core.storage.repository.PipelineRepository
import com.example.npc.core.storage.repository.PipelineRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import com.example.npc.core.storage.bank.TemplateBankManager
import com.example.npc.core.storage.bank.TemplateBankManagerImpl
import com.example.npc.core.storage.dao.DynamicTemplateDao
import com.example.npc.domain.usecase.TemplateBackfillEngine
import com.example.npc.feature.replay.backfill.TemplateBackfillEngine as TemplateBackfillEngineImpl

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        PreMigrationBackup.executeIfNeeded(context, "npc_database.db", targetVersion = 5)

        val keyFile = File(context.filesDir, "sqlcipher_key.bin")
        val passphrase = SqlCipherSupportFactoryProvider.getOrCreatePassphrase(keyStorageFile = keyFile)
        val factory = SqlCipherSupportFactoryProvider.createOpenHelperFactory(passphrase)
        SqlCipherSupportFactoryProvider.wipePassphrase(passphrase)

        return Room.databaseBuilder(context, AppDatabase::class.java, "npc_database.db")
            .openHelperFactory(factory)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
    }

    @Provides
    @Singleton
    fun providePipelineRepository(db: AppDatabase): PipelineRepository {
        return PipelineRepositoryImpl(
            database = db,
            pipelineDao = db.pipelineDao(),
            revisionDao = db.pipelineRevisionDao(),
            alertDao = db.runtimeAlertDao()
        )
    }

    @Provides
    @Singleton
    fun provideFinancialTransactionDeduplicator(): com.example.npc.core.storage.dedup.FinancialTransactionDeduplicator {
        return com.example.npc.core.storage.dedup.FinancialTransactionDeduplicator()
    }

    @Provides
    @Singleton
    fun provideStorageGateway(
        db: AppDatabase,
        deduplicator: com.example.npc.core.storage.dedup.FinancialTransactionDeduplicator
    ): StorageGateway {
        return StorageGatewayImpl(
            database = db,
            rawEventDao = db.rawEventDao(),
            eventDao = db.eventDao(),
            sourceHealthDao = db.sourceHealthDao(),
            deduplicator = deduplicator
        )
    }

    @Provides
    @Singleton
    fun provideTemplateBankManager(db: AppDatabase): TemplateBankManager {
        return TemplateBankManagerImpl(
            database = db,
            templateDao = db.dynamicTemplateDao()
        )
    }

    @Provides
    @Singleton
    fun provideDynamicTemplateDao(db: AppDatabase): DynamicTemplateDao {
        return db.dynamicTemplateDao()
    }

    @Provides
    @Singleton
    fun provideExtractionFeedbackDao(db: AppDatabase): com.example.npc.core.storage.dao.ExtractionFeedbackDao {
        return db.extractionFeedbackDao()
    }

    @Provides
    @Singleton
    fun provideTemplateBackfillEngine(
        db: AppDatabase,
        storageGateway: StorageGateway
    ): TemplateBackfillEngine {
        return TemplateBackfillEngineImpl(
            eventSourceDao = db.replayEventSourceDao(),
            storageGateway = storageGateway,
            transactionDao = db.financialTransactionDao(),
            eventDao = db.eventDao()
        )
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideNotificationManagerCompat(@ApplicationContext context: Context): NotificationManagerCompat {
        return NotificationManagerCompat.from(context)
    }

    @Provides
    @Singleton
    fun providePowerManager(@ApplicationContext context: Context): PowerManager {
        return context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    @Provides
    @Singleton
    fun provideAbsenceAlertScheduler(impl: com.example.npc.app.worker.AbsenceAlertSchedulerImpl): com.example.npc.app.worker.AbsenceAlertScheduler = impl

    @Provides
    @Singleton
    fun provideIngestWatchdog(
        @ApplicationContext context: Context,
        workManager: WorkManager
    ): com.example.npc.ingest.notification.watchdog.IngestWatchdog {
        return com.example.npc.ingest.notification.watchdog.IngestWatchdog(context, workManager)
    }
}
