package com.lirix.app.translations

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.optimaize.langdetect.LanguageDetectorBuilder
import com.optimaize.langdetect.ngram.NgramExtractors
import com.optimaize.langdetect.profiles.LanguageProfileReader
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.File
import java.nio.LongBuffer

/** Private build only: pinned runtime, local language identification, no metrics/network API. */
internal class OnnxMarianBackend(context: Context) : MachineTranslationBackend {
    private val application = context.applicationContext
    private val catalog = PrivateModelCatalog(application.assets.open("offline_translation_models.json").use { it.readBytes() })
    val cacheVersion = "${MachineTranslationEngineInfo.Private.version}/${catalog.fingerprint}"
    private val store = PrivateModelStore(File(application.noBackupFilesDir, "translation-models"), catalog, {
        val manager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    })
    override val downloadProgress = store.progress
    private val detector by lazy {
        LanguageDetectorBuilder.create(NgramExtractors.standard())
            .withProfiles(LanguageProfileReader().readAllBuiltIn()).minimalConfidence(.90).build()
    }
    override fun supports(source: String, target: String) = store.packageFor(source, target) != null
    override suspend fun identify(text: String): String? {
        currentCoroutineContext().ensureActive()
        val detected = detector.detect(text.take(8_000))
        return if (detected.isPresent) detected.get().language else null
    }
    override suspend fun modelsAvailable(source: String, target: String) = store.packageFor(source, target)?.let(store::ready) == true
    override suspend fun download(source: String, target: String) = store.download(store.packageFor(source, target) ?: error("Unsupported translation pair"))
    override suspend fun translate(lines: List<String>, source: String, target: String): List<String> {
        val model = store.packageFor(source, target) ?: error("Unsupported translation pair")
        val directory = store.verify(model)
        val environment = OrtEnvironment.getEnvironment().also { it.setTelemetry(false) }
        val vocabularyJson = JSONObject(File(directory, "vocab.json").readText())
        val vocabulary = mutableMapOf<String, Int>()
        val reverse = arrayOfNulls<String>(model.vocabSize)
        vocabularyJson.keys().forEach { piece ->
            val id = vocabularyJson.getInt(piece)
            require(id in reverse.indices && reverse[id] == null)
            vocabulary[piece] = id; reverse[id] = piece
        }
        require(reverse.all { it != null } && vocabulary["<unk>"] != null)
        val memo = mutableMapOf<String, String>()
        var generated = 0
        SentencePieceProcessor(File(directory, "source.spm").readBytes()).use { sourceProcessor ->
            SentencePieceProcessor(File(directory, "target.spm").readBytes()).use { targetProcessor ->
                OrtSession.SessionOptions().use { options ->
                    options.setIntraOpNumThreads(2); options.setInterOpNumThreads(1)
                    options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                    environment.createSession(File(directory, "encoder_model.onnx").absolutePath, options).use { encoder ->
                        environment.createSession(File(directory, "decoder_model.onnx").absolutePath, options).use { decoder ->
                            environment.createSession(File(directory, "decoder_with_past_model.onnx").absolutePath, options).use { past ->
                                return lines.map { line ->
                                    currentCoroutineContext().ensureActive()
                                    if (line.isBlank()) "" else memo[line] ?: run {
                                        val pieces = sourceProcessor.encode(line)
                                        require(pieces.size in 1..511) { "A source line exceeds model capacity" }
                                        val ids = pieces.map { vocabulary[it] ?: vocabulary.getValue("<unk>") }.map { it.toLong() } + model.eos.toLong()
                                        val output = generate(environment, encoder, decoder, past, ids.toLongArray(), model.eos, model.pad, model.vocabSize)
                                        generated += output.size
                                        require(generated <= 16_384) { "Translation exceeds token budget" }
                                        targetProcessor.decode(output.map { reverse[it] ?: error("Invalid generated token") }.toTypedArray())
                                            .also { require(it.isNotBlank()); memo[line] = it }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    private suspend fun generate(
        environment: OrtEnvironment, encoder: OrtSession, decoder: OrtSession, past: OrtSession,
        tokens: LongArray, eos: Int, pad: Int, vocabularySize: Int
    ): List<Int> {
        OnnxTensor.createTensor(environment, LongBuffer.wrap(tokens), longArrayOf(1, tokens.size.toLong())).use { input ->
            OnnxTensor.createTensor(environment, LongBuffer.wrap(LongArray(tokens.size) { 1L }), longArrayOf(1, tokens.size.toLong())).use { mask ->
                encoder.run(mapOf("input_ids" to input, "attention_mask" to mask)).use { encoded ->
                    OnnxTensor.createTensor(environment, LongBuffer.wrap(longArrayOf(pad.toLong())), longArrayOf(1, 1)).use { start ->
                        val hidden = tensor(encoded, "last_hidden_state")
                        decoder.run(mapOf("encoder_hidden_states" to hidden, "encoder_attention_mask" to mask, "input_ids" to start)).use { first ->
                            var latest = first
                            try {
                                val output = mutableListOf<Int>()
                                repeat(256) {
                                    currentCoroutineContext().ensureActive()
                                    val logits = tensor(latest, "logits").floatBuffer
                                    require(logits.remaining() >= vocabularySize)
                                    val offset = logits.limit() - vocabularySize
                                    var best = -1; var score = Float.NEGATIVE_INFINITY
                                    for (id in 0 until vocabularySize) {
                                        val value = logits.get(offset + id)
                                        require(!value.isNaN()) { "Invalid model logits" }
                                        if (id != pad && value > score) { best = id; score = value }
                                    }
                                    require(best >= 0)
                                    if (best == eos) { require(output.isNotEmpty()); return output }
                                    output += best
                                    OnnxTensor.createTensor(environment, LongBuffer.wrap(longArrayOf(best.toLong())), longArrayOf(1, 1)).use { next ->
                                        val inputs = linkedMapOf<String, OnnxTensor>()
                                        for (name in past.inputNames) inputs[name] = when (name) {
                                            "input_ids" -> next
                                            "encoder_attention_mask" -> mask
                                            "encoder_hidden_states" -> hidden
                                            else -> {
                                                require(name.startsWith("past_key_values.")) { "Unexpected model protocol" }
                                                val present = name.replaceFirst("past_key_values.", "present.")
                                                (latest.get(present).orElse(null) ?: first.get(present).orElse(null)) as? OnnxTensor
                                                    ?: error("Missing decoder cache")
                                            }
                                        }
                                        val updated = past.run(inputs)
                                        if (latest !== first) latest.close()
                                        latest = updated
                                    }
                                }
                                error("Translation did not finish within the token limit")
                            } finally { if (latest !== first) latest.close() }
                        }
                    }
                }
            }
        }
    }
    private fun tensor(result: OrtSession.Result, name: String): OnnxTensor = result.get(name).orElse(null) as? OnnxTensor ?: error("Missing model output")
}
