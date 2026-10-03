package com.example.npc.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.npc.core.storage.migration.MIGRATION_1_2
import com.example.npc.core.storage.migration.MIGRATION_2_3
import com.example.npc.core.storage.migration.MIGRATION_3_4
import com.example.npc.core.storage.migration.MIGRATION_4_5
import com.example.npc.core.storage.dao.DynamicTemplateDao
import com.example.npc.core.storage.dao.EventDao
import com.example.npc.core.storage.dao.ExtractionFeedbackDao
import com.example.npc.core.storage.dao.FinancialTransactionDao
import com.example.npc.core.storage.dao.PipelineDao
import com.example.npc.core.storage.dao.PipelineRevisionDao
import com.example.npc.core.storage.dao.RawEventDao
import com.example.npc.core.storage.dao.ReplayEventSourceDao
import com.example.npc.core.storage.dao.RuntimeAlertDao
import com.example.npc.core.storage.dao.SourceHealthDao
import com.example.npc.core.storage.dao.UserPrototypeDao
import com.example.npc.core.storage.entity.DynamicTemplateEntity
import com.example.npc.core.storage.entity.EventEntity
import com.example.npc.core.storage.entity.ExtractionFeedbackEntity
import com.example.npc.core.storage.entity.FinancialTransactionEntity
import com.example.npc.core.storage.entity.PipelineDefinitionEntity
import com.example.npc.core.storage.entity.PipelineRevisionEntity
import com.example.npc.core.storage.entity.RawEventEntity
import com.example.npc.core.storage.entity.RuntimeAlertEntity
import com.example.npc.core.storage.entity.SourceHealthEntity
import com.example.npc.core.storage.entity.TemplateBankMembershipEntity
import com.example.npc.core.storage.entity.TemplateBankVersionEntity
import com.example.npc.core.storage.entity.TemplateStatsEntity
import com.example.npc.core.storage.entity.UserPrototypeEntity

@Database(
    entities = [
        RawEventEntity::class,
        EventEntity::class,
        FinancialTransactionEntity::class,
        UserPrototypeEntity::class,
        SourceHealthEntity::class,
        PipelineDefinitionEntity::class,
        PipelineRevisionEntity::class,
        RuntimeAlertEntity::class,
        DynamicTemplateEntity::class,
        TemplateStatsEntity::class,
        TemplateBankVersionEntity::class,
        TemplateBankMembershipEntity::class,
        ExtractionFeedbackEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rawEventDao(): RawEventDao
    abstract fun eventDao(): EventDao
    abstract fun financialTransactionDao(): FinancialTransactionDao
    abstract fun userPrototypeDao(): UserPrototypeDao
    abstract fun sourceHealthDao(): SourceHealthDao
    abstract fun pipelineDao(): PipelineDao
    abstract fun pipelineRevisionDao(): PipelineRevisionDao
    abstract fun runtimeAlertDao(): RuntimeAlertDao
    abstract fun replayEventSourceDao(): ReplayEventSourceDao
    abstract fun dynamicTemplateDao(): DynamicTemplateDao
    abstract fun extractionFeedbackDao(): ExtractionFeedbackDao

    companion object {
        val MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5
        )
    }
}

