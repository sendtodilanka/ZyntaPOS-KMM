package com.zyntasolutions.zyntapos.api.db

import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

/**
 * Abstraction over Exposed's newSuspendedTransaction so that service-layer
 * code can be unit-tested with MockK without a live Database.connect().
 *
 * Production code uses [ExposedTransactionRunner].
 * Tests inject [NoOpTransactionRunner] (just runs the block directly).
 */
interface TransactionRunner {
    suspend fun <T> invoke(block: suspend () -> T): T
}

/** Production: wraps in a real Exposed transaction. */
class ExposedTransactionRunner : TransactionRunner {
    override suspend fun <T> invoke(block: suspend () -> T): T =
        newSuspendedTransaction { block() }
}

/** Test: runs block directly — no database needed. */
class NoOpTransactionRunner : TransactionRunner {
    override suspend fun <T> invoke(block: suspend () -> T): T = block()
}
