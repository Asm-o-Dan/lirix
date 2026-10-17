package com.lirix.app.storage

/** Android legacy Room and Desktop SQLiteDriver use different transaction adapters. */
expect suspend fun <T> AppDatabase.libraryTransaction(block: suspend () -> T): T
