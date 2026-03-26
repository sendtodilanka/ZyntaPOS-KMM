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
 * Android WorkManager-based worker for daily audit hash chain verification.
 *
 * Replaces the coroutine `while(isActive) { delay(24h) }` loop in
 * [AuditIntegrityJob.start] with OS-managed scheduling. WorkManager ensures:
 * - Battery-efficient scheduling (no wakelock needed)
 * - Survives process death and device reboot
 * - Exponential backoff on transient failures
 *
 * The [AuditIntegrityJob.runVerification] method is called directly, keeping the
 * verification logic in commonMain for cross-platform reuse (Desktop still uses
 * the coroutine-based loop).
 */
class AuditIntegrityWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val log = ZyntaLogger.forModule("AuditIntegrityWorker")
    private val integrityJob: AuditIntegrityJob by inject()

    override suspend fun doWork(): Result {
        log.i("AuditIntegrityWorker.doWork() — starting integrity check")
        return try {
            integrityJob.runVerification()
            log.i("AuditIntegrityWorker.doWork() — integrity check completed")
            Result.success()
        } catch (e: Exception) {
            log.e("AuditIntegrityWorker.doWork() — integrity check failed", throwable = e)
            if (runAttemptCount < MAX_RUN_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "ZyntaPOS_AuditIntegrity"
        private const val MAX_RUN_ATTEMPTS = 3

        /**
         * Enqueues a periodic audit integrity work request (once per 24 hours).
         *
         * Safe to call multiple times — [ExistingPeriodicWorkPolicy.KEEP] ensures
         * only one scheduled instance exists.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<AuditIntegrityWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInitialDelay(2, TimeUnit.HOURS) // stagger after LogRetention
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                uniqueWorkName = WORK_NAME,
                existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
                request = request,
            )
        }

        /** Cancels any scheduled audit integrity work. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
