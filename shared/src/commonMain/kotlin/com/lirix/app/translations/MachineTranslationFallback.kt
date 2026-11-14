package com.lirix.app.translations

import kotlinx.coroutines.flow.StateFlow

data class MachineDownloadProgress(val received: Long, val total: Long)

data class MachineTranslationRequest(
    val trackKey: String, val title: String, val artist: String,
    val original: String, val targetLanguage: String
)

sealed interface MachineTranslationResult {
    data class Translated(val document: SongTranslation) : MachineTranslationResult
    data class ModelRequired(val sourceLanguage: String) : MachineTranslationResult
    data class UnsupportedPair(val source: String, val target: String) : MachineTranslationResult
    data object SameLanguage : MachineTranslationResult
    data object UnknownLanguage : MachineTranslationResult
}

data class MachineTranslationEngineInfo(
    val name: String, val version: String, val downloadHint: String, val notice: String,
    val helpUrl: String, val diagnostics: Boolean
) {
    companion object {
        val Compact = MachineTranslationEngineInfo("Google Translate", "mlkit-translate-17.0.3/lines-v1",
            "Нужны языковые модели: около 30 МБ на язык. Загрузка только по Wi-Fi.",
            "Компактная сборка содержит ML Kit. SDK может отправлять Google диагностику при запуске приложения, даже если машинный перевод выключен. Тексты и результаты обрабатываются на устройстве.",
            "https://developers.google.com/ml-kit/terms#privacy", true)
        val Private = MachineTranslationEngineInfo("OPUS-MT", "opus-mt-32a5add3/spm-0.2.0/ort-1.23.2/v1",
            "Пакет EN→RU или RU→EN: около 234 МБ на направление. Загрузка только по Wi-Fi.",
            "Приватная сборка: локальный движок без SDK-телеметрии. После загрузки пакета текст переводится без сети. Пока доступны EN→RU и RU→EN.",
            "https://opus.nlpl.eu/Opus-MT/", false)
    }
}

/** Optional platform capability; results never enter the published/manual translation DAO. */
interface MachineTranslationFallback {
    val engineInfo: MachineTranslationEngineInfo get() = MachineTranslationEngineInfo.Compact
    val downloadProgress: StateFlow<MachineDownloadProgress?>? get() = null
    val enabled: StateFlow<Boolean>
    fun setEnabled(value: Boolean)
    suspend fun translate(request: MachineTranslationRequest, downloadModels: Boolean = false): MachineTranslationResult
}

internal fun machineFallbackAllowed(
    state: AutomaticTranslationState?, enabled: Boolean, hasHumanDocument: Boolean,
    hasPreview: Boolean, original: String
): Boolean = enabled && state == AutomaticTranslationState.Missing && !hasHumanDocument && !hasPreview && original.isNotBlank()
