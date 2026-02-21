package com.zyntasolutions.zyntapos.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * ZyntaPOS — Android WorkManager-based sync worker.
 *
 * Delegates to [SyncEngine.runOnce] as a [CoroutineWorker] so the OS can
 * schedule it with battery/network constraints and handle retry policies.
 *
 * ## Scheduling
 * Enqueued as a periodic job (15-minute minimum WorkManager interval) with
 * [NetworkType.CONNECTED] constraint. [schedule] should be called once at
 * application startup from the Android application class.
 *
 * ## Retry
 * WorkManager [BackoffPolicy.EXPONENTIAL] handles transient failures;
 * [SyncEngine] handles per-operation retry logic internally.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val log = Logger.withTag("SyncWorker")
    private val syncEngine: SyncEngine by inject()

    override suspend fun doWork(): Result {
        log.i { "SyncWorker.doWork() — starting sync cycle" }
        return try {
            syncEngine.runOnce()
            log.i { "SyncWorker.doWork() — sync cycle completed" }
            Result.success()
        } catch (e: Exception) {
            log.e(e) { "SyncWorker.doWork() — sync cycle failed" }
            if (runAttemptCount < MAX_RUN_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "ZyntaPOS_SyncWorker"
        private const val MAX_RUN_ATTEMPTS = 3

        /**
         * Enqueues a periodic sync work request.
         * Safe to call multiple times — [ExistingPeriodicWorkPolicy.KEEP] ensures
         * only one instance runs at a time.
         *
         * @param context Android application context.
         * @param requireWifi If `true`, sync only on UNMETERED (Wi-Fi) connections.
         */
        fun schedule(context: Context, requireWifi: Boolean = false) {
            val networkType = if (requireWifi) NetworkType.UNMETERED else NetworkType.CONNECTED
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                repeatInterval     = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                uniqueWorkName = WORK_NAME,
                existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
                request = request,
            )
        }

        /** Cancels any scheduled sync work. Call on logout / account removal. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
