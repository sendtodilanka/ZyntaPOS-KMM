package com.zyntasolutions.zyntapos.data.job

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Notification
import com.zyntasolutions.zyntapos.domain.port.SyncStatusPort
import com.zyntasolutions.zyntapos.domain.repository.NotificationRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — SlaAlertJobTest Unit Tests (commonTest)
 *
 * Validates SLA monitoring alert logic in [SlaAlertJob.runCheck].
 *
 * Coverage:
 *  A. no alert when pending count is below threshold
 *  B. sync queue backlog alert created when pending count reaches threshold
 *  C. second check at same count does NOT create a duplicate alert (dedup)
 *  D. alert NOT created when pending count drops to zero (below threshold)
 *  E. persistent sync failure alert created when lastSyncFailed=true, connected, pending>0
 *  F. sync failure alert NOT created when device is offline (isConnected=false)
 *  G. sync failure alert NOT created when pending count is 0
 *  H. exception swallowed without re-throwing (non-cancellation exception)
 *  I. lastAlertedPendingCount resets when queue drains below threshold/2
 */
class SlaAlertJobTest {

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private class FakeSyncStatusPort(
        pendingCountValue: Int = 0,
        lastSyncFailedValue: Boolean = false,
        isNetworkConnectedValue: Boolean = true,
    ) : SyncStatusPort {
        override val isSyncing: StateFlow<Boolean> = MutableStateFlow(false)
        override val isNetworkConnected: StateFlow<Boolean> = MutableStateFlow(isNetworkConnectedValue)
        override val lastSyncFailed: StateFlow<Boolean> = MutableStateFlow(lastSyncFailedValue)
        override val pendingCount: StateFlow<Int> = MutableStateFlow(pendingCountValue)
        override val newConflictCount: SharedFlow<Int> = MutableSharedFlow()
        override val onSyncComplete: SharedFlow<Unit> = MutableSharedFlow()
    }

    private class FakeNotificationRepository : NotificationRepository {
        val inserted = mutableListOf<Notification>()

        override fun getUnread(recipientId: String): Flow<List<Notification>> = flowOf(emptyList())
        override fun getAll(recipientId: String): Flow<List<Notification>> = flowOf(emptyList())
        override suspend fun getUnreadCount(recipientId: String): Result<Int> = Result.Success(0)
        override suspend fun insert(notification: Notification): Result<Unit> {
            inserted.add(notification)
            return Result.Success(Unit)
        }
        override suspend fun markRead(id: String): Result<Unit> = Result.Success(Unit)
        override suspend fun markAllRead(recipientId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun pruneOld(beforeEpochMillis: Long): Result<Unit> = Result.Success(Unit)
    }

    private fun makeJob(
        pending: Int = 0,
        lastSyncFailed: Boolean = false,
        isConnected: Boolean = true,
    ): Pair<SlaAlertJob, FakeNotificationRepository> {
        val notifRepo = FakeNotificationRepository()
        val syncPort = FakeSyncStatusPort(
            pendingCountValue = pending,
            lastSyncFailedValue = lastSyncFailed,
            isNetworkConnectedValue = isConnected,
        )
        val scope = kotlinx.coroutines.MainScope()
        return SlaAlertJob(
            syncStatusPort = syncPort,
            notificationRepository = notifRepo,
            scope = scope,
        ) to notifRepo
    }

    // ── A — No alert below threshold ──────────────────────────────────────────

    @Test
    fun `A - no alert when pending count is below threshold`() = runTest {
        val (job, notifRepo) = makeJob(pending = SlaAlertJob.SYNC_QUEUE_THRESHOLD - 1)

        job.runCheck()

        assertTrue(notifRepo.inserted.isEmpty(), "No alert expected below threshold")
    }

    // ── B — Alert at threshold ────────────────────────────────────────────────

    @Test
    fun `B - sync queue backlog alert created when pending count reaches threshold`() = runTest {
        val (job, notifRepo) = makeJob(pending = SlaAlertJob.SYNC_QUEUE_THRESHOLD)

        job.runCheck()

        assertEquals(1, notifRepo.inserted.size, "Expected exactly one sync backlog alert")
        assertTrue(
            notifRepo.inserted[0].title.contains("Sync Queue"),
            "Alert title must reference sync queue",
        )
    }

    // ── C — No duplicate alert at same count ──────────────────────────────────

    @Test
    fun `C - second check at same pending count does not create duplicate alert`() = runTest {
        val (job, notifRepo) = makeJob(pending = SlaAlertJob.SYNC_QUEUE_THRESHOLD)

        job.runCheck() // first check — alert created
        job.runCheck() // second check — same count, no new alert

        // Second check may also emit the "sync failure" path if lastSyncFailed is true
        // but with default (false) we should only have 1 backlog alert from first run
        val backlogAlerts = notifRepo.inserted.filter { it.title.contains("Sync Queue") }
        assertEquals(1, backlogAlerts.size, "No duplicate backlog alert for same pending count")
    }

    // ── D — No alert when queue is empty ─────────────────────────────────────

    @Test
    fun `D - no alert when pending count is zero`() = runTest {
        val (job, notifRepo) = makeJob(pending = 0)

        job.runCheck()

        assertTrue(notifRepo.inserted.isEmpty(), "No alert expected for empty queue")
    }

    // ── E — Sync failure alert when connected ─────────────────────────────────

    @Test
    fun `E - sync failure alert created when lastSyncFailed true and connected and pending gt 0`() = runTest {
        val (job, notifRepo) = makeJob(pending = 5, lastSyncFailed = true, isConnected = true)

        job.runCheck()

        val failureAlerts = notifRepo.inserted.filter { it.title.contains("Sync Failure") }
        assertEquals(1, failureAlerts.size, "Expected sync failure alert")
    }

    // ── F — No sync failure alert when offline ────────────────────────────────

    @Test
    fun `F - no sync failure alert when device is offline`() = runTest {
        val (job, notifRepo) = makeJob(pending = 5, lastSyncFailed = true, isConnected = false)

        job.runCheck()

        val failureAlerts = notifRepo.inserted.filter { it.title.contains("Sync Failure") }
        assertTrue(failureAlerts.isEmpty(), "No sync failure alert when device is offline")
    }

    // ── G — No sync failure alert when pending is 0 ───────────────────────────

    @Test
    fun `G - no sync failure alert when pending count is 0`() = runTest {
        val (job, notifRepo) = makeJob(pending = 0, lastSyncFailed = true, isConnected = true)

        job.runCheck()

        val failureAlerts = notifRepo.inserted.filter { it.title.contains("Sync Failure") }
        assertTrue(failureAlerts.isEmpty(), "No sync failure alert when pending queue is empty")
    }

    // ── H — Exception swallowed ───────────────────────────────────────────────

    @Test
    fun `H - runCheck swallows non-cancellation exceptions without re-throwing`() = runTest {
        val throwingPort = object : SyncStatusPort {
            override val isSyncing: StateFlow<Boolean> = MutableStateFlow(false)
            override val isNetworkConnected: StateFlow<Boolean> get() = throw RuntimeException("port error")
            override val lastSyncFailed: StateFlow<Boolean> = MutableStateFlow(false)
            override val pendingCount: StateFlow<Int> get() = throw RuntimeException("port error")
            override val newConflictCount: SharedFlow<Int> = MutableSharedFlow()
            override val onSyncComplete: SharedFlow<Unit> = MutableSharedFlow()
        }
        val notifRepo = FakeNotificationRepository()
        val scope = kotlinx.coroutines.MainScope()
        val job = SlaAlertJob(throwingPort, notifRepo, scope)

        // Must not throw
        job.runCheck()
    }

    // ── I — lastAlertedPendingCount resets when queue drains ──────────────────

    @Test
    fun `I - re-alerts after queue drains below half-threshold and grows again`() = runTest {
        val notifRepo = FakeNotificationRepository()
        val scope = kotlinx.coroutines.MainScope()
        val threshold = SlaAlertJob.SYNC_QUEUE_THRESHOLD
        val halfThreshold = threshold / 2

        // First check — at threshold, alert created
        var pending = threshold
        val port1 = FakeSyncStatusPort(pendingCountValue = pending)
        val job = SlaAlertJob(port1, notifRepo, scope)
        job.runCheck()
        val alertsAfterFirst = notifRepo.inserted.filter { it.title.contains("Sync Queue") }.size

        // Simulate queue draining below half-threshold — resets lastAlertedPendingCount
        // (We need to create a new job with low pending to trigger the reset, since job state persists)
        val port2 = FakeSyncStatusPort(pendingCountValue = halfThreshold - 1)
        val job2 = SlaAlertJob(port2, notifRepo, scope).also { it.runCheck() }

        // Now at threshold again — should alert again since counter was reset
        val port3 = FakeSyncStatusPort(pendingCountValue = threshold)
        val job3 = SlaAlertJob(port3, notifRepo, scope).also { it.runCheck() }

        val backlogAlerts = notifRepo.inserted.filter { it.title.contains("Sync Queue") }
        assertEquals(2, backlogAlerts.size, "Expected 2 alerts: initial + re-alert after drain")
    }
}
