package com.lirix.app.ingestion

actual fun getSystemElapsedRealtimeMs(): Long = android.os.SystemClock.elapsedRealtime()
