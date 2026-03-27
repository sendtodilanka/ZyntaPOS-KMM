package com.zyntasolutions.zyntapos.api.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.zyntasolutions.zyntapos.api.db.Stores
import com.zyntasolutions.zyntapos.api.db.SyncQueue
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory

/**
 * Background job that evaluates alert rules every N seconds.
 * Started once on application startup via [start].
 */
class AlertGenerationJob(
    private val alertsService: AdminAlertsService,
    private val ticketService: AdminTicketService? = null,
) {

    private val logger = LoggerFactory.getLogger(AlertGenerationJob::class.java)
    private val scope  = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(intervalSeconds: Long = 60L) {
        scope.launch {
            logger.info("AlertGenerationJob started (interval: ${intervalSeconds}s)")
            while (isActive) {
                try {
                    evaluate()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn("AlertGenerationJob evaluation error: ${e.message}")
                }
                delay(intervalSeconds * 1_000L)
            }
        }
    }

    private suspend fun evaluate() {
        // Check SLA breaches and send email notifications
        try {
            ticketService?.checkSlaBreaches()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("SLA breach check failed: ${e.message}")
        }

        // Gather current pending op counts per store
        val pendingByStore = newSuspendedTransaction {
            SyncQueue.selectAll()
                .where { SyncQueue.isProcessed eq false }
                .groupBy { it[SyncQueue.storeId] }
                .mapValues { (_, rows) -> rows.size }
        }

        val storeNames = newSuspendedTransaction {
            Stores.selectAll().associate { it[Stores.id] to it[Stores.name] }
        }

        if (pendingByStore.isNotEmpty()) {
            alertsService.evaluateRules(pendingByStore, storeNames)
        }
    }
}
