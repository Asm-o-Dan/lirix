package com.lirix.app.desktop.system

import com.lirix.app.ingestion.LivePlaybackSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Lightweight native Discord RPC implementation over Windows named pipes (\\.\pipe\discord-ipc-0).
 * Displays current track, artist, and playback timeline in Discord user status.
 * Spec: TASK-KMP-12 (.sdd/tasks/TASK-KMP-12.md)
 */
class DiscordRpcManager(
    private val clientId: String = "1306912345678901234", // Default Lirix Discord Application ID
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private var pipe: RandomAccessFile? = null
    private var isConnected = false
    private var updateJob: Job? = null

    companion object {
        private const val OP_HANDSHAKE = 0
        private const val OP_FRAME = 1
        private const val OP_CLOSE = 2
    }

    fun start() {
        connect()
    }

    fun stop() {
        updateJob?.cancel()
        disconnect()
    }

    @Synchronized
    private fun connect(): Boolean {
        if (isConnected && pipe != null) return true

        for (i in 0..9) {
            val pipePath = "\\\\.\\pipe\\discord-ipc-$i"
            try {
                val raf = RandomAccessFile(pipePath, "rw")
                pipe = raf

                // Send Handshake
                val handshakeJson = """{"v":1,"client_id":"$clientId"}"""
                sendPacket(OP_HANDSHAKE, handshakeJson)

                // Read Handshake response (non-blocking / simple read)
                val header = ByteArray(8)
                raf.readFully(header)
                val len = ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                if (len > 0) {
                    val respBytes = ByteArray(len)
                    raf.readFully(respBytes)
                }

                isConnected = true
                return true
            } catch (_: Exception) {
                // Try next pipe index
            }
        }
        pipe = null
        isConnected = false
        return false
    }

    @Synchronized
    private fun disconnect() {
        try {
            pipe?.let { p ->
                sendPacket(OP_CLOSE, "{}")
                p.close()
            }
        } catch (_: Exception) {}
        pipe = null
        isConnected = false
    }

    @Synchronized
    private fun sendPacket(op: Int, json: String) {
        val p = pipe ?: return
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(8 + bytes.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(op)
        buffer.putInt(bytes.size)
        buffer.put(bytes)
        p.write(buffer.array())
    }

    /**
     * Updates Discord Presence with current track information.
     */
    fun updatePresence(playback: LivePlaybackSnapshot?) {
        updateJob?.cancel()
        updateJob = scope.launch {
            if (playback == null || !playback.isPlaying) {
                clearPresence()
                return@launch
            }

            if (!isConnected && !connect()) {
                return@launch
            }

            try {
                val nowSec = System.currentTimeMillis() / 1000L
                val currentPositionSec = playback.currentPositionMs() / 1000L
                val startSec = nowSec - currentPositionSec
                val endSec = if (playback.durationMs > 0L) startSec + (playback.durationMs / 1000L) else null

                val activityPayload = buildJsonObject {
                    put("cmd", "SET_ACTIVITY")
                    putJsonObject("args") {
                        put("pid", ProcessHandle.current().pid().toInt())
                        putJsonObject("activity") {
                            put("details", playback.title.ifBlank { "Музыка" })
                            put("state", playback.artist.ifBlank { "Lirix Player" })
                            putJsonObject("timestamps") {
                                put("start", startSec)
                                if (endSec != null && endSec > startSec) {
                                    put("end", endSec)
                                }
                            }
                            putJsonObject("assets") {
                                put("large_image", "lirix_logo")
                                put("large_text", "Lirix Music Hub")
                                put("small_image", if (playback.isPlaying) "play" else "pause")
                                put("small_text", if (playback.isPlaying) "Playing" else "Paused")
                            }
                        }
                    }
                    put("nonce", UUID.randomUUID().toString())
                }.toString()

                synchronized(this@DiscordRpcManager) {
                    sendPacket(OP_FRAME, activityPayload)
                }
            } catch (e: Exception) {
                disconnect()
            }
        }
    }

    fun clearPresence() {
        if (!isConnected) return
        scope.launch {
            try {
                val clearPayload = buildJsonObject {
                    put("cmd", "SET_ACTIVITY")
                    putJsonObject("args") {
                        put("pid", ProcessHandle.current().pid().toInt())
                    }
                    put("nonce", UUID.randomUUID().toString())
                }.toString()

                synchronized(this@DiscordRpcManager) {
                    sendPacket(OP_FRAME, clearPayload)
                }
            } catch (_: Exception) {
                disconnect()
            }
        }
    }
}
