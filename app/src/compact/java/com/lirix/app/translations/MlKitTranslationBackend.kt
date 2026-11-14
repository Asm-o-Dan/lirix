package com.lirix.app.translations

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Only this adapter knows ML Kit. SDK tasks can outlive coroutine cancellation. */
internal class MlKitTranslationBackend : MachineTranslationBackend {
    override suspend fun identify(text: String): String? {
        val client = LanguageIdentification.getClient(LanguageIdentificationOptions.Builder().setConfidenceThreshold(.5f).build())
        val task = client.identifyLanguage(text.take(8_000))
        return try {
            val code = task.awaitResult()
            if (code == "und") null else TranslateLanguage.fromLanguageTag(code)
        } finally {
            if (task.isComplete) client.close() else task.addOnCompleteListener { client.close() }
        }
    }
    override suspend fun modelsAvailable(source: String, target: String): Boolean {
        val installed = RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java).awaitResult().map { it.language }.toSet()
        return (setOf(source, target) - "en").all { it in installed }
    }
    override suspend fun download(source: String, target: String) {
        val manager = RemoteModelManager.getInstance()
        for (language in setOf(source, target) - "en") {
            currentCoroutineContext().ensureActive()
            manager.download(TranslateRemoteModel.Builder(language).build(), DownloadConditions.Builder().requireWifi().build()).awaitResult()
        }
    }
    override suspend fun translate(lines: List<String>, source: String, target: String): List<String> {
        val client = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build())
        var pending: Task<String>? = null
        val repeated = mutableMapOf<String, String>()
        return try {
            lines.map { line ->
                currentCoroutineContext().ensureActive()
                if (line.isBlank()) "" else repeated[line] ?: run {
                    val task = client.translate(line)
                    pending = task
                    task.awaitResult().also { require(it.isNotBlank()); repeated[line] = it }
                }
            }
        } finally {
            val task = pending
            if (task == null || task.isComplete) client.close() else task.addOnCompleteListener { client.close() }
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
