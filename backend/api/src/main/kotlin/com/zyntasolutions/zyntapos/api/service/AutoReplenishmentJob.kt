package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.repository.ReplenishmentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.inList
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Background job that scans replenishment rules and auto-creates purchase orders
 * for items at or below their reorder point where [autoApprove] = true.
 *
 * Runs every [intervalSeconds] (default 3600 = 1 hour).
 * Only creates a PO if no PENDING/ORDERED PO already exists for the same
 * product+supplier combination (idempotent guard).
 */
class AutoReplenishmentJob(
    private val replenishmentRepo: ReplenishmentRepository,
) {
    private val log = LoggerFactory.getLogger(AutoReplenishmentJob::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(intervalSeconds: Long = 3600L) {
        scope.launch {
            log.info("AutoReplenishmentJob started (interval: ${intervalSeconds}s)")
            while (true) {
                try {
                    processAutoReplenishment()
                } catch (e: Exception) {
                    log.error("AutoReplenishmentJob error: ${e.message}", e)
                }
                delay(intervalSeconds * 1_000L)
            }
        }
    }

    private suspend fun processAutoReplenishment() {
        val suggestions = replenishmentRepo.getSuggestions()
        val autoApprove = suggestions.filter { it.autoApprove }

        if (autoApprove.isEmpty()) {
            log.debug("AutoReplenishmentJob: no auto-approve replenishment suggestions this cycle")
            return
        }

        var created = 0
        var skipped = 0

        for (suggestion in autoApprove) {
            val alreadyExists = newSuspendedTransaction {
                // Guard: skip if a PENDING or ORDERED PO already exists for this product+supplier
                PurchaseOrders.selectAll()
                    .where {
                        (PurchaseOrders.supplierId eq suggestion.supplierId) and
                        (PurchaseOrders.status inList listOf("PENDING", "ORDERED"))
                    }
                    .any { row ->
                        // Check the PO covers this product — stored in notes as a convention
                        // until purchase_order_items table is available via sync
                        row[PurchaseOrders.notes]?.contains(suggestion.productId) == true
                    }
            }

            if (alreadyExists) {
                skipped++
                continue
            }

            try {
                val now = OffsetDateTime.now(ZoneOffset.UTC)
                val poId = UUID.randomUUID().toString()
                val orderNumber = "AUTO-${now.toLocalDate()}-${poId.take(8).uppercase()}"

                newSuspendedTransaction {
                    PurchaseOrders.insert {
                        it[id]           = poId
                        // storeId not tracked per-warehouse in replenishment rules — use warehouseId as storeId
                        it[storeId]      = suggestion.warehouseId
                        it[supplierId]   = suggestion.supplierId
                        it[orderNumber]  = orderNumber
                        it[status]       = "PENDING"
                        it[orderDate]    = now
                        it[expectedDate] = now.plusDays(7)
                        it[totalAmount]  = BigDecimal.ZERO   // priced when PO is confirmed
                        it[currency]     = "USD"
                        it[notes]        = "AUTO: product=${suggestion.productId} " +
                                           "qty=${suggestion.reorderQty} " +
                                           "currentStock=${suggestion.currentStock} " +
                                           "reorderPoint=${suggestion.reorderPoint}"
                        it[createdBy]    = "auto-replenishment"
                        it[syncVersion]  = now.toInstant().toEpochMilli()
                        it[createdAt]    = now
                        it[updatedAt]    = now
                    }
                }
                created++
                log.info(
                    "AutoReplenishmentJob: created PO $orderNumber for " +
                    "product=${suggestion.productId} supplier=${suggestion.supplierId} " +
                    "qty=${suggestion.reorderQty}"
                )
            } catch (e: Exception) {
                log.error(
                    "AutoReplenishmentJob: failed to create PO for " +
                    "product=${suggestion.productId}: ${e.message}"
                )
            }
        }

        log.info(
            "AutoReplenishmentJob: created $created POs, skipped $skipped " +
            "(${autoApprove.size} auto-approve suggestions)"
        )
    }
}
