package com.lirix.app.storage

import androidx.room.useWriterConnection
import androidx.room.immediateTransaction

actual suspend fun <T> AppDatabase.libraryTransaction(block: suspend () -> T): T =
    useWriterConnection { connection -> connection.immediateTransaction { block() } }
