package com.example.npc.app.di

import com.example.npc.core.model.extract.FinanceExtractor
import com.example.npc.extract.finance.CircuitBreaker
import com.example.npc.extract.finance.IsolatedExtractorRunner
import com.example.npc.extract.finance.apb.ApbNotificationExtractor
import com.example.npc.extract.finance.maib.MaibNotificationExtractor
import com.example.npc.extract.finance.prisbank.PrisbankNotificationExtractor
import com.example.npc.extract.finance.sms.BankSmsExtractor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ExtractorModule {

    @Provides
    @Singleton
    fun provideFinanceExtractors(): List<FinanceExtractor> {
        return listOf(
            ApbNotificationExtractor(),
            PrisbankNotificationExtractor(),
            MaibNotificationExtractor(),
            BankSmsExtractor()
        )
    }

    @Provides
    @Singleton
    fun provideCircuitBreaker(): CircuitBreaker {
        return CircuitBreaker(
            failureThreshold = 3,
            cooldownMs = 600_000L, // 10 минут
            timeBudgetMs = 50L
        )
    }

    @Provides
    @Singleton
    @JvmSuppressWildcards
    fun provideIsolatedExtractorRunner(
        extractors: List<FinanceExtractor>,
        circuitBreaker: CircuitBreaker
    ): IsolatedExtractorRunner {
        return IsolatedExtractorRunner(extractors, circuitBreaker)
    }
}
