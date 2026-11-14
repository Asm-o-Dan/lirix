package com.lirix.app.translations

internal interface MachineTranslationBackend {
    val downloadProgress: kotlinx.coroutines.flow.StateFlow<MachineDownloadProgress?>? get() = null
    fun supports(source: String, target: String): Boolean = true
    suspend fun identify(text: String): String?
    suspend fun modelsAvailable(source: String, target: String): Boolean
    suspend fun download(source: String, target: String)
    suspend fun translate(lines: List<String>, source: String, target: String): List<String>
}

