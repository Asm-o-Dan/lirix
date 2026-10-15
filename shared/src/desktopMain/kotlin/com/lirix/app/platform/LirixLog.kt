package com.lirix.app.platform

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val LOG_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

private fun formatLog(level: String, tag: String, message: String): String {
    val now = LocalDateTime.now().format(LOG_TIME_FORMATTER)
    return "[$now] [$level] [$tag] $message"
}

actual fun logDebug(tag: String, message: String) {
    println(formatLog("DEBUG", tag, message))
}

actual fun logInfo(tag: String, message: String) {
    println(formatLog("INFO", tag, message))
}

actual fun logWarn(tag: String, message: String) {
    System.err.println(formatLog("WARN", tag, message))
}

actual fun logError(tag: String, message: String, throwable: Throwable?) {
    System.err.println(formatLog("ERROR", tag, message))
    throwable?.printStackTrace(System.err)
}
