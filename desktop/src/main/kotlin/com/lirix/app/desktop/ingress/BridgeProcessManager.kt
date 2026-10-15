package com.lirix.app.desktop.ingress

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class BridgeProcessManager(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val customBinaryPath: String? = null
) {
    private val _messages = MutableSharedFlow<BridgeInboundMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<BridgeInboundMessage> = _messages.asSharedFlow()

    @Volatile
    private var process: Process? = null

    @Volatile
    private var writer: BufferedWriter? = null

    @Volatile
    private var isRunning = false

    private var supervisorJob: Job? = null
    private val writeLock = Any()

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            stop()
        })
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        supervisorJob = scope.launch {
            runWatchdogLoop()
        }
    }

    fun stop() {
        isRunning = false
        supervisorJob?.cancel()
        killProcess()
    }

    private suspend fun runWatchdogLoop() {
        var backoffMs = 500L
        while (isRunning && scope.isActive) {
            val binaryFile = findBridgeExecutable()
            if (binaryFile == null || !binaryFile.exists()) {
                System.err.println("[BridgeProcessManager] Bridge binary not found! Retrying in ${backoffMs}ms...")
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(10_000L)
                continue
            }

            try {
                val pb = ProcessBuilder(binaryFile.absolutePath)
                pb.redirectErrorStream(false)
                val proc = withContext(Dispatchers.IO) { pb.start() }
                process = proc
                writer = BufferedWriter(OutputStreamWriter(proc.outputStream, StandardCharsets.UTF_8))
                backoffMs = 500L

                val stderrJob = scope.launch(Dispatchers.IO) {
                    try {
                        BufferedReader(InputStreamReader(proc.errorStream, StandardCharsets.UTF_8)).use { reader ->
                            while (isActive) {
                                val errLine = reader.readLine() ?: break
                                if (errLine.isNotBlank()) {
                                    System.err.println("[Bridge stderr] $errLine")
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                withContext(Dispatchers.IO) {
                    BufferedReader(InputStreamReader(proc.inputStream, StandardCharsets.UTF_8)).use { reader ->
                        while (isRunning && isActive) {
                            val line = reader.readLine() ?: break
                            if (line.isBlank()) continue
                            try {
                                val msg = bridgeJson.decodeFromString<BridgeInboundMessage>(line)
                                _messages.tryEmit(msg)
                            } catch (e: Exception) {
                                System.err.println("[BridgeProcessManager] Failed to parse bridge output: $line ($e)")
                            }
                        }
                    }
                }

                stderrJob.cancel()
            } catch (e: Exception) {
                System.err.println("[BridgeProcessManager] Process crashed or failed to start: ${e.message}")
            } finally {
                killProcess()
            }

            if (isRunning) {
                _messages.tryEmit(BridgeInboundMessage(type = "closed"))
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(10_000L)
            }
        }
    }

    fun sendCommand(cmd: String, positionMs: Long? = null) {
        val message = BridgeCommandMessage(cmd = cmd, command = cmd, positionMs = positionMs)
        val json = bridgeJson.encodeToString(BridgeCommandMessage.serializer(), message)
        synchronized(writeLock) {
            try {
                writer?.let { w ->
                    w.write(json)
                    w.newLine()
                    w.flush()
                }
            } catch (e: Exception) {
                System.err.println("[BridgeProcessManager] Failed to send command: $json ($e)")
            }
        }
    }

    private fun killProcess() {
        synchronized(writeLock) {
            try {
                writer?.close()
            } catch (_: Exception) {}
            writer = null
        }
        try {
            process?.let { p ->
                if (p.isAlive) {
                    p.destroy()
                    if (!p.waitFor(500, TimeUnit.MILLISECONDS)) {
                        p.destroyForcibly()
                    }
                }
            }
        } catch (_: Exception) {}
        process = null
    }

    fun findBridgeExecutable(): File? {
        val codeSourceCandidates = runCatching {
            val jarFile = File(BridgeProcessManager::class.java.protectionDomain.codeSource.location.toURI())
            val dir = if (jarFile.isFile) jarFile.parentFile else jarFile
            listOf(
                File(dir, "lirix-media-bridge.exe"),
                File(dir, "resources/lirix-media-bridge.exe"),
                File(dir.parentFile, "lirix-media-bridge.exe"),
                File(dir.parentFile, "app/lirix-media-bridge.exe"),
                File(dir.parentFile, "resources/lirix-media-bridge.exe")
            )
        }.getOrNull() ?: emptyList()

        val candidates = sequenceOf(
            customBinaryPath?.let { File(it) },
            System.getProperty("lirix.bridge.path")?.let { File(it) },
            System.getenv("LIRIX_BRIDGE_PATH")?.let { File(it) },
            // Bundled application resources (Compose Desktop runtime)
            File(System.getProperty("compose.application.resources.dir", ""), "lirix-media-bridge.exe"),
            File("desktop/resources/lirix-media-bridge.exe"),
            File("../desktop/resources/lirix-media-bridge.exe"),
            // AppData and LocalAppData paths
            System.getenv("LOCALAPPDATA")?.let { File(it, "Lirix/lirix-media-bridge.exe") },
            System.getenv("APPDATA")?.let { File(it, "Lirix/lirix-media-bridge.exe") },
            // Installed Program Files locations
            File("C:\\Program Files\\Lirix\\lirix-media-bridge.exe"),
            File("C:\\Program Files\\Lirix\\app\\lirix-media-bridge.exe"),
            System.getenv("ProgramFiles")?.let { File(it, "Lirix/lirix-media-bridge.exe") },
            System.getenv("ProgramFiles(x86)")?.let { File(it, "Lirix/lirix-media-bridge.exe") },
            // Working directory
            File(System.getProperty("user.dir", ""), "lirix-media-bridge.exe"),
            File(System.getProperty("user.dir", ""), "app/lirix-media-bridge.exe"),
            File(System.getProperty("user.dir", ""), "resources/lirix-media-bridge.exe"),
            // Git workspace development builds
            File("native/lirix-media-bridge/bin/Release/net8.0-windows10.0.19041.0/win-x64/publish/lirix-media-bridge.exe"),
            File("../native/lirix-media-bridge/bin/Release/net8.0-windows10.0.19041.0/win-x64/publish/lirix-media-bridge.exe"),
            File("C:\\Users\\DaniilTuT\\Documents\\antigravity\\quick-nobel\\native\\lirix-media-bridge\\bin\\Release\\net8.0-windows10.0.19041.0\\win-x64\\publish\\lirix-media-bridge.exe")
        ) + codeSourceCandidates.asSequence()

        val found = candidates.filterNotNull().firstOrNull { it.exists() && it.isFile }
        if (found != null) {
            println("[BridgeProcessManager] Discovered bridge binary at: ${found.absolutePath}")
        }
        return found
    }
}

