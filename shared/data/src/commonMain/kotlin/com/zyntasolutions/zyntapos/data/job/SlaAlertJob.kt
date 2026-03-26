package com.zyntasolutions.zyntapos.data.job

import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.Notification
import com.zyntasolutions.zyntapos.domain.model.Notification.NotificationType
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.port.SyncStatusPort
import com.zyntasolutions.zyntapos.domain.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Monitors SLA-related metrics and generates alerts when thresholds are breached.
 *
 * ## Monitored Conditions
 * - **Sync queue growth**: Alerts when pending operations exceed [SYNC_QUEUE_THRESHOLD],
 *   indicating potential connectivity issues or sync failures.
 * - **Persistent sync failure**: Alerts when the last sync failed AND the queue is growing.
 *
 * Runs every 5 minutes via a coroutine loop. On Desktop, this is the only mechanism.
 * On Android, a WorkManager wrapper can be added if needed.
 *
 * @param syncStatusPort Source for sync health metrics.
 * @param notificationRepository Target for generated alert notifications.
 * @param scope Long-lived coroutine scope.
 */
class SlaAlertJob(
    private val syncStatusPort: SyncStatusPort,
    private val notificationRepository: NotificationRepository,
    private val scope: CoroutineScope,
) {

    private var lastAlertedPendingCount = 0

    /**
     * Starts the SLA monitoring loop. Runs every 5 minutes.
     */
    fun start() {
        scope.launch {
            while (isActive) {
                delay(5.minutes)
                runCheck()
            }
        }
    }

    /**
     * Executes one SLA check pass. Exposed as `internal` for testing.
     */
    internal suspend fun runCheck() {
        runCatching {
            val pendingCount = syncStatusPort.pendingCount.value
            val lastSyncFailed = syncStatusPort.lastSyncFailed.value
            val isConnected = syncStatusPort.isNetworkConnected.value

            // Sync queue backlog alert
            if (pendingCount >= SYNC_QUEUE_THRESHOLD && pendingCount > lastAlertedPendingCount) {
                createAlert(
                    title = "Sync Queue Backlog",
                    message = "$pendingCount operations pending in sync queue. " +
                        if (!isConnected) "Device is offline — operations will sync when connectivity is restored."
                        else if (lastSyncFailed) "Last sync cycle failed — check network connectivity."
                        else "Queue is unusually large — monitor for resolution.",
                )
                lastAlertedPendingCount = pendingCount
            }

            // Reset alert threshold when queue drains
            if (pendingCount < SYNC_QUEUE_THRESHOLD / 2) {
                lastAlertedPendingCount = 0
            }

            // Persistent sync failure alert
            if (lastSyncFailed && isConnected && pendingCount > 0) {
                createAlert(
                    title = "Sync Failure Detected",
                    message = "Sync is failing despite network connectivity. $pendingCount operation(s) queued. " +
                        "Check server availability and review sync conflict logs.",
                )
            }
        }
    }

    private suspend fun createAlert(title: String, message: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        notificationRepository.insert(
            Notification(
                id = IdGenerator.newId(),
                type = NotificationType.SYSTEM,
                title = title,
                message = message,
                channel = Notification.Channel.IN_APP,
                recipientType = Notification.RecipientType.ROLE,
                recipientId = Role.ADMIN.name,
                createdAt = now,
            )
        )
    }

    companion object {
        /** Alert when sync queue exceeds this many pending operations. */
        const val SYNC_QUEUE_THRESHOLD = 50
    }
}
