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
 * Android WorkManager-based worker for daily log retention enforcement.
 *
 * Replaces the coroutine `while(isActive) { delay(24h) }` loop in
 * [LogRetentionJob.start] with OS-managed scheduling. WorkManager ensures:
 * - Battery-efficient scheduling (no wakelock needed)
 * - Survives process death and device reboot
 * - Exponential backoff on transient failures
 *
 * The [LogRetentionJob.runRetention] method is called directly, keeping the
 * retention logic in commonMain for cross-platform reuse (Desktop still uses
 * the coroutine-based loop).
 */
class LogRetentionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val log = ZyntaLogger.forModule("LogRetentionWorker")
    private val retentionJob: LogRetentionJob by inject()

    override suspend fun doWork(): Result {
        log.i("LogRetentionWorker.doWork() — starting retention pass")
        return try {
            retentionJob.runRetention()
            log.i("LogRetentionWorker.doWork() — retention pass completed")
            Result.success()
        } catch (e: Exception) {
            log.e("LogRetentionWorker.doWork() — retention pass failed", throwable = e)
            if (runAttemptCount < MAX_RUN_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "ZyntaPOS_LogRetention"
        private const val MAX_RUN_ATTEMPTS = 3

        /**
         * Enqueues a periodic log retention work request (once per 24 hours).
         *
         * Safe to call multiple times — [ExistingPeriodicWorkPolicy.KEEP] ensures
         * only one scheduled instance exists.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<LogRetentionWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInitialDelay(1, TimeUnit.HOURS) // defer first run to reduce startup load
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                uniqueWorkName = WORK_NAME,
                existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
                request = request,
            )
        }

        /** Cancels any scheduled log retention work. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
