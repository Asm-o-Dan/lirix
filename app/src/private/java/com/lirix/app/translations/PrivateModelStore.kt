package com.lirix.app.translations

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

internal data class PrivateModelFile(val name: String, val url: String, val bytes: Long, val sha256: String)
internal data class PrivateModelPackage(val pair: String, val files: List<PrivateModelFile>, val eos: Int, val pad: Int, val vocabSize: Int) {
    val totalBytes: Long get() = files.sumOf { it.bytes }
}
internal class PrivateModelCatalog(val bytes: ByteArray) {
    val fingerprint = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    val packages: Map<String, PrivateModelPackage> = run {
        val array = JSONObject(bytes.toString(Charsets.UTF_8)).getJSONArray("packages")
        (0 until array.length()).associate { index ->
            val p = array.getJSONObject(index)
            val files = p.getJSONArray("files")
            val pair = p.getString("pair")
            require(pair in setOf("en-ru", "ru-en"))
            val records = (0 until files.length()).map { i ->
                val f = files.getJSONObject(i)
                PrivateModelFile(f.getString("name"), f.getString("url"), f.getLong("bytes"), f.getString("sha256")).also {
                    require(it.name.matches(Regex("[a-z_]+\\.(onnx|spm|json)")))
                    require(it.bytes in 1..150_000_000 && it.sha256.matches(Regex("[a-f0-9]{64}")))
                    require(URI(it.url).host == "huggingface.co")
                }
            }
            require(records.size == 6 && records.map { it.name }.toSet() == setOf("encoder_model.onnx", "decoder_model.onnx", "decoder_with_past_model.onnx", "source.spm", "target.spm", "vocab.json"))
            pair to PrivateModelPackage(pair, records, p.getInt("eos"), p.getInt("pad"), p.getInt("vocabSize"))
        }
    }
}

/** Only explicit model installation performs network requests; no input text is sent anywhere. */
internal class PrivateModelStore(
    private val root: File,
    private val catalog: PrivateModelCatalog,
    private val wifiAvailable: () -> Boolean,
    private val open: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection }
) {
    companion object { private val installGate = Mutex() }
    private val mutableProgress = MutableStateFlow<MachineDownloadProgress?>(null)
    val progress = mutableProgress.asStateFlow()
    fun packageFor(source: String, target: String) = catalog.packages["$source-$target"]
    fun directory(model: PrivateModelPackage) = File(root, "${model.pair}-${catalog.fingerprint.take(16)}")
    fun ready(model: PrivateModelPackage): Boolean {
        val folder = directory(model)
        return File(folder, ".ready").takeIf { it.isFile && it.length() == 64L }?.readText() == catalog.fingerprint &&
            model.files.all { File(folder, it.name).let { f -> f.isFile && f.length() == it.bytes } }
    }
    suspend fun verify(model: PrivateModelPackage): File {
        check(ready(model)) { "Model is not installed" }
        val folder = directory(model)
        for (record in model.files) {
            val digest = MessageDigest.getInstance("SHA-256")
            File(folder, record.name).inputStream().use { input ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            if (!digest.hex().equals(record.sha256, true)) {
                File(folder, ".ready").delete()
                error("Model checksum mismatch; download the package again")
            }
        }
        return folder
    }
    suspend fun download(model: PrivateModelPackage) = installGate.withLock {
        check(wifiAvailable()) { "Validated Wi-Fi required to download models" }
        root.mkdirs()
        val staging = File(root, ".download-${model.pair}")
        staging.mkdirs()
        model.files.forEach { File(staging, it.name).delete() }
        File(staging, ".ready").delete()
        check(root.usableSpace >= model.totalBytes + 16_000_000) { "Not enough space for this model" }
        var received = 0L
        mutableProgress.value = MachineDownloadProgress(0, model.totalBytes)
        try {
            for (record in model.files) {
                currentCoroutineContext().ensureActive()
                fetch(record, File(staging, record.name)) { n ->
                    received += n
                    mutableProgress.value = MachineDownloadProgress(received, model.totalBytes)
                }
            }
            currentCoroutineContext().ensureActive()
            File(staging, ".ready").writeText(catalog.fingerprint)
            val destination = directory(model)
            if (destination.exists()) {
                model.files.forEach { check(!File(destination, it.name).exists() || File(destination, it.name).delete()) }
                File(destination, ".ready").delete()
                check(destination.delete())
            }
            check(staging.renameTo(destination)) { "Could not install the verified model" }
        } finally {
            model.files.forEach { File(staging, it.name).delete() }
            File(staging, ".ready").delete(); staging.delete()
            mutableProgress.value = null
        }
    }
    private suspend fun fetch(record: PrivateModelFile, target: File, progress: (Int) -> Unit) {
        var next = URI(record.url)
        repeat(6) {
            require(next.scheme == "https" && next.rawUserInfo == null && next.port in setOf(-1, 443))
            val host = next.host.orEmpty().lowercase()
            require(host == "huggingface.co" || host.endsWith(".huggingface.co") || host.endsWith(".hf.co")) { "Untrusted model redirect" }
            check(wifiAvailable()) { "Wi-Fi disconnected" }
            val connection = open(next)
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000; connection.readTimeout = 30_000
                connection.setRequestProperty("User-Agent", "Lirix-private-model-download")
                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    next = next.resolve(connection.getHeaderField("Location") ?: error("Missing model redirect"))
                } else {
                    check(status == 200) { "Model download failed: HTTP $status" }
                    val expected = connection.contentLengthLong
                    check(expected < 0 || expected == record.bytes) { "Unexpected model size" }
                    val digest = MessageDigest.getInstance("SHA-256")
                    var count = 0L
                    connection.inputStream.use { input -> target.outputStream().use { output ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            check(wifiAvailable()) { "Wi-Fi disconnected" }
                            val n = input.read(buffer)
                            if (n < 0) break
                            count += n
                            check(count <= record.bytes) { "Model exceeds pinned size" }
                            output.write(buffer, 0, n); digest.update(buffer, 0, n); progress(n)
                        }
                    } }
                    check(count == record.bytes && digest.hex().equals(record.sha256, true)) { "Model checksum or size mismatch" }
                    return
                }
            } finally { connection.disconnect() }
        }
        error("Too many model redirects")
    }
    private fun MessageDigest.hex() = digest().joinToString("") { "%02x".format(it.toInt() and 255) }
}
