package com.zyntasolutions.zyntapos.data.job

import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import com.zyntasolutions.zyntapos.domain.model.IntegrityReport
import com.zyntasolutions.zyntapos.domain.usecase.admin.VerifyAuditIntegrityUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours

/**
 * Coroutine-based daily integrity verification job for the `audit_entries` hash chain.
 *
 * Walks the entire audit log in chronological order and recomputes SHA-256 hashes
 * to detect any tampering. The latest [IntegrityReport] is exposed via [latestReport]
 * so the Admin UI can display chain status without triggering a manual re-verify.
 *
 * ## Design
 * - [start] launches a long-lived coroutine that runs [VerifyAuditIntegrityUseCase]
 *   once per 24 hours.
 * - The first verification runs immediately on startup.
 * - All verification errors are swallowed via [runCatching]; a failing check never
 *   crashes the application and will simply retry on the next daily cycle.
 * - [runVerification] is `internal` so it can be called synchronously from tests.
 *
 * ## Registration
 * Bind in `dataModule` and call [start] during application initialisation:
 * ```kotlin
 * single { AuditIntegrityJob(verifyUseCase = get(), scope = get(named("IO"))) }
 * ```
 * Then in the application entry point:
 * ```kotlin
 * get<AuditIntegrityJob>().start()
 * ```
 *
 * @param verifyUseCase  Use case that walks the hash chain and returns [IntegrityReport].
 * @param scope          Long-lived [CoroutineScope] (application / background IO scope).
 */
class AuditIntegrityJob(
    private val verifyUseCase: VerifyAuditIntegrityUseCase,
    private val scope: CoroutineScope,
) {

    private val log = ZyntaLogger.forModule("AuditIntegrityJob")
    private val _latestReport = MutableStateFlow<IntegrityReport?>(null)

    /** The most recent integrity verification result, or null if never run. */
    val latestReport: StateFlow<IntegrityReport?> = _latestReport.asStateFlow()

    /**
     * Starts the background verification loop.
     *
     * The first run is performed immediately, then repeats every 24 hours.
     * The loop is cancelled automatically when [scope] is cancelled.
     */
    fun start() {
        scope.launch {
            while (isActive) {
                runVerification()
                delay(24.hours)
            }
        }
    }

    /**
     * Executes one integrity verification pass against the `audit_entries` hash chain.
     *
     * Exposed as `internal` so unit tests can invoke it directly without starting
     * the 24-hour delay loop.
     */
    internal suspend fun runVerification() {
        try {
            val report = verifyUseCase()
            _latestReport.value = report
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e("AuditIntegrityJob failed: ${e.message}", throwable = e)
        }
    }
}
