package com.lirix.app.storage

import androidx.room.withTransaction

actual suspend fun <T> AppDatabase.libraryTransaction(block: suspend () -> T): T = withTransaction { block() }
