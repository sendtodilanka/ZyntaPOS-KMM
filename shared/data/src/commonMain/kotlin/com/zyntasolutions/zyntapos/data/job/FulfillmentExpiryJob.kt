package com.zyntasolutions.zyntapos.data.job

import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import com.zyntasolutions.zyntapos.domain.repository.FulfillmentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Coroutine-based periodic job that expires overdue Click & Collect fulfillment orders.
 *
 * Runs every 15 minutes and calls [FulfillmentRepository.expireOverdueOrders] to mark
 * orders past their pickup deadline as EXPIRED. The SQL query (fulfillment_orders.sq)
 * handles the actual update: orders with status NOT IN (PICKED_UP, EXPIRED, CANCELLED)
 * whose `pickup_deadline < now` are set to EXPIRED with sync_status = PENDING.
 *
 * On Android, this is supplemented by [FulfillmentExpiryWorker] (WorkManager) which
 * survives process death. On Desktop, this coroutine loop is the only mechanism.
 *
 * @param fulfillmentRepository Repository with [expireOverdueOrders] implementation.
 * @param storeId Current store ID to scope the expiry scan.
 * @param scope Long-lived coroutine scope (application / background IO scope).
 */
class FulfillmentExpiryJob(
    private val fulfillmentRepository: FulfillmentRepository,
    private val storeId: String,
    private val scope: CoroutineScope,
) {

    private val log = ZyntaLogger.forModule("FulfillmentExpiryJob")

    /**
     * Starts the background expiry loop.
     *
     * Runs immediately on first call, then repeats every 15 minutes.
     * Cancelled automatically when [scope] is cancelled.
     */
    fun start() {
        scope.launch {
            while (isActive) {
                runExpiry()
                delay(15.minutes)
            }
        }
    }

    /**
     * Executes one expiry pass. Exposed as `internal` for testing.
     */
    internal suspend fun runExpiry() {
        try {
            val now = Clock.System.now().toEpochMilliseconds()
            fulfillmentRepository.expireOverdueOrders(storeId, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e("FulfillmentExpiryJob failed: ${e.message}", throwable = e)
        }
    }
}
