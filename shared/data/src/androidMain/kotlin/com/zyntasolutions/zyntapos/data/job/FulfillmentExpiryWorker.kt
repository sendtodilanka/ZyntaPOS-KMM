package com.zyntasolutions.zyntapos.data.job

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Android WorkManager-based worker for periodic fulfillment order expiry.
 *
 * Delegates to [FulfillmentExpiryJob.runExpiry] so the expiry logic stays in
 * commonMain for cross-platform reuse. WorkManager ensures the job survives
 * process death and device reboot.
 *
 * Runs every 15 minutes to check for overdue Click & Collect orders.
 */
class FulfillmentExpiryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val log = ZyntaLogger.forModule("FulfillmentExpiryWorker")
    private val expiryJob: FulfillmentExpiryJob by inject()

    override suspend fun doWork(): Result {
        log.i("FulfillmentExpiryWorker.doWork() — starting expiry pass")
        return try {
            expiryJob.runExpiry()
            log.i("FulfillmentExpiryWorker.doWork() — expiry pass completed")
            Result.success()
        } catch (e: Exception) {
            log.e("FulfillmentExpiryWorker.doWork() — expiry pass failed", throwable = e)
            if (runAttemptCount < MAX_RUN_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "ZyntaPOS_FulfillmentExpiry"
        private const val MAX_RUN_ATTEMPTS = 3

        /**
         * Enqueues a periodic fulfillment expiry work request (every 15 minutes).
         *
         * Safe to call multiple times — [ExistingPeriodicWorkPolicy.KEEP] ensures
         * only one scheduled instance exists.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FulfillmentExpiryWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                uniqueWorkName = WORK_NAME,
                existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
                request = request,
            )
        }

        /** Cancels any scheduled fulfillment expiry work. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
