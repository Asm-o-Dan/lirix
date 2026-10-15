package com.lirix.app.ingestion

actual fun getSystemElapsedRealtimeMs(): Long = System.nanoTime() / 1_000_000L
