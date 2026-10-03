package com.example.npc.pipeline.dsl.preset

import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.Engine
import com.example.npc.pipeline.dsl.*

object LegacyPipelinePreset {
    const val PRESET_ID = "legacy-1.1-preset"

    val canonicalDefinition: PipelineDefinition get() = create()

    fun create(): PipelineDefinition {
        return PipelineDefinition(
            id = PRESET_ID,
            name = "Legacy 1.1 Baseline Pipeline",
            description = "Эталонная конфигурация Фазы 1.1 для дифференциального теста паритета 100% совпадения",
            schemaVersion = 1,
            revision = 1L,
            enabled = true,
            priority = 1000,
            packageWhitelist = emptyList(),
            triggers = listOf(
                TriggerDefinition.Notification(ignoreSelf = true),
                TriggerDefinition.Sms(allowDirectReceiver = true, allowMessagingApps = true),
                TriggerDefinition.Media(captureArtwork = false)
            ),
            stages = listOf(
                // Stage 1: stage-fingerprint
                StageDefinition(
                    id = "stage-fingerprint",
                    name = "Content Fingerprint Compute",
                    transforms = listOf(
                        TransformDefinition.FingerprintCompute(
                            algorithm = "SHA-256-TEMPLATED",
                            targetVar = "contentFingerprint"
                        )
                    )
                ),
                // Stage 2: stage-prototype-feedback
                StageDefinition(
                    id = "stage-prototype-feedback",
                    name = "Prototype Support Feedback Loop",
                    condition = ConditionDefinition.PrototypeSupportCount(minSupportCount = 2),
                    actions = listOf(
                        ActionDefinition.SetCategory(
                            category = Category.UNCLASSIFIED,
                            confidence = 1.0,
                            engine = Engine.PROTOTYPE
                        )
                    )
                ),
                // Stage 3: stage-bank-finance
                StageDefinition(
                    id = "stage-bank-finance",
                    name = "Trusted Banks & Finance Extraction",
                    condition = ConditionDefinition.LogicalAnd(
                        conditions = listOf(
                            ConditionDefinition.PackageMatch(
                                packages = listOf("com.apb.mobile", "com.prisbank.app", "md.maib.maibank"),
                                matchMode = MatchMode.EXACT
                            ),
                            ConditionDefinition.PackageMatch(
                                packages = listOf("com.radolyn.ayugram", "org.telegram.messenger"),
                                matchMode = MatchMode.EXACT,
                                negate = true
                            )
                        )
                    ),
                    transforms = listOf(
                        TransformDefinition.RegionalTextSanitize(
                            maxChars = 1024,
                            normalizeNbsp = true,
                            stripDiacritics = false,
                            targetVar = "sanitizedText"
                        ),
                        TransformDefinition.FinanceExtract(
                            extractorId = "auto",
                            timeoutMs = 50L,
                            useCircuitBreaker = true,
                            targetVar = "financialTransaction"
                        )
                    ),
                    actions = listOf(
                        ActionDefinition.SetCategory(
                            category = Category.FINANCE,
                            confidence = 0.98,
                            engine = Engine.RULES
                        ),
                        ActionDefinition.SaveToStorage(completeProcessing = true)
                    ),
                    terminateOnMatch = true
                ),
                // Stage 4: stage-music
                StageDefinition(
                    id = "stage-music",
                    name = "Media & Music Services",
                    condition = ConditionDefinition.PackageMatch(
                        packages = listOf(
                            "com.google.android.apps.youtube.music",
                            "com.spotify.music",
                            "com.vkontakte.android"
                        ),
                        matchMode = MatchMode.EXACT
                    ),
                    actions = listOf(
                        ActionDefinition.SetCategory(
                            category = Category.MUSIC,
                            confidence = 0.95,
                            engine = Engine.RULES
                        ),
                        ActionDefinition.SaveToStorage(completeProcessing = true)
                    ),
                    terminateOnMatch = true
                ),
                // Stage 5: stage-communication
                StageDefinition(
                    id = "stage-communication",
                    name = "Messengers & Communication",
                    condition = ConditionDefinition.LogicalOr(
                        conditions = listOf(
                            ConditionDefinition.PackageMatch(
                                packages = listOf(
                                    "org.telegram.messenger",
                                    "com.whatsapp",
                                    "com.viber.voip"
                                ),
                                matchMode = MatchMode.EXACT
                            ),
                            ConditionDefinition.SenderMatch(
                                senders = listOf("Telegram", "WhatsApp"),
                                caseSensitive = false
                            )
                        )
                    ),
                    actions = listOf(
                        ActionDefinition.SetCategory(
                            category = Category.COMMUNICATION,
                            confidence = 0.90,
                            engine = Engine.RULES
                        ),
                        ActionDefinition.SaveToStorage(completeProcessing = true)
                    ),
                    terminateOnMatch = true
                ),
                // Stage 6: stage-services
                StageDefinition(
                    id = "stage-services",
                    name = "Services Taxi & Weather",
                    condition = ConditionDefinition.LogicalOr(
                        conditions = listOf(
                            ConditionDefinition.PackageMatch(
                                packages = listOf(
                                    "ru.yandex.taxi",
                                    "com.ubercab",
                                    "ru.yandex.weather"
                                ),
                                matchMode = MatchMode.EXACT
                            ),
                            ConditionDefinition.TextRegexMatch(
                                pattern = "(?i)(заказ|доставка|такси|погода)"
                            )
                        )
                    ),
                    actions = listOf(
                        ActionDefinition.SetCategory(
                            category = Category.SERVICES,
                            confidence = 0.90,
                            engine = Engine.RULES
                        ),
                        ActionDefinition.SaveToStorage(completeProcessing = true)
                    ),
                    terminateOnMatch = true
                ),
                // Stage 7: stage-fallback-other
                StageDefinition(
                    id = "stage-fallback-other",
                    name = "Fallback Other",
                    condition = null,
                    actions = listOf(
                        ActionDefinition.SetCategory(
                            category = Category.OTHER,
                            confidence = 0.50,
                            engine = Engine.RULES
                        ),
                        ActionDefinition.SaveToStorage(completeProcessing = true)
                    ),
                    terminateOnMatch = true
                )
            ),
            metadata = mapOf(
                "systemPreset" to "true",
                "parityBaseline" to "1.1"
            )
        )
    }
}
