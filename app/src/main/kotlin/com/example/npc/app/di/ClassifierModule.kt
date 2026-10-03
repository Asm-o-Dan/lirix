package com.example.npc.app.di

import com.example.npc.classify.rules.PackageGatedRouterImpl
import com.example.npc.classify.rules.RuleBasedCategoryClassifier
import com.example.npc.classify.rules.SemanticClassifierImpl
import com.example.npc.core.model.classify.PackageGatedRouter
import com.example.npc.core.model.classify.SemanticClassifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ClassifierModule {

    @Provides
    @Singleton
    fun providePackageGatedRouter(): PackageGatedRouter {
        return PackageGatedRouterImpl()
    }

    @Provides
    @Singleton
    fun provideRuleBasedCategoryClassifier(router: PackageGatedRouter): RuleBasedCategoryClassifier {
        return RuleBasedCategoryClassifier(router)
    }

    @Provides
    @Singleton
    fun provideSemanticClassifier(
        router: PackageGatedRouter,
        ruleClassifier: RuleBasedCategoryClassifier
    ): SemanticClassifier {
        return SemanticClassifierImpl(router, ruleClassifier)
    }
}
