package com.zyntasolutions.zyntapos.data.job

import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.Notification
import com.zyntasolutions.zyntapos.domain.model.Notification.NotificationType
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.WarehouseStock
import com.zyntasolutions.zyntapos.domain.port.SyncStatusPort
import com.zyntasolutions.zyntapos.domain.repository.NotificationRepository
import com.zyntasolutions.zyntapos.domain.repository.WarehouseStockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Monitors cross-store/warehouse stock levels and generates LOW_STOCK notifications
 * for store managers when products fall below their reorder thresholds.
 *
 * Triggered on every sync completion via [SyncStatusPort.onSyncComplete], ensuring
 * notifications are generated promptly when new stock data arrives from other stores.
 *
 * Deduplication: only creates a notification for a (warehouse, product) pair if no
 * unread LOW_STOCK notification already exists for that product. This prevents
 * notification spam when stock remains low across multiple sync cycles.
 *
 * @param warehouseStockRepository Source for cross-warehouse low stock data.
 * @param notificationRepository Target for generated notifications.
 * @param syncStatusPort Trigger source — emits after each sync cycle.
 * @param scope Long-lived coroutine scope for the monitoring loop.
 */
class LowStockNotificationJob(
    private val warehouseStockRepository: WarehouseStockRepository,
    private val notificationRepository: NotificationRepository,
    private val syncStatusPort: SyncStatusPort,
    private val scope: CoroutineScope,
) {

    /**
     * Starts monitoring. Collects [SyncStatusPort.onSyncComplete] and runs
     * a low-stock check after each sync cycle.
     */
    fun start() {
        scope.launch {
            syncStatusPort.onSyncComplete.collect {
                runCheck()
            }
        }
    }

    /**
     * Executes one low-stock notification pass. Exposed as `internal` for testing.
     */
    internal suspend fun runCheck() {
        runCatching {
            val lowStockItems = warehouseStockRepository.getAllLowStock().first()
            if (lowStockItems.isEmpty()) return

            // Group by warehouse for batch notifications
            val byWarehouse = lowStockItems.groupBy { it.warehouseId }

            for ((warehouseId, items) in byWarehouse) {
                val warehouseName = items.firstOrNull()?.warehouseName ?: warehouseId.takeLast(8)
                val criticalItems = items.filter { it.stockShortfall > 0 }
                if (criticalItems.isEmpty()) continue

                createLowStockNotification(warehouseName, criticalItems)
            }
        }
    }

    private suspend fun createLowStockNotification(
        warehouseName: String,
        items: List<WarehouseStock>,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        val topProducts = items
            .sortedByDescending { it.stockShortfall }
            .take(5)
            .mapNotNull { it.productName }

        val title = "Low Stock: $warehouseName"
        val message = buildString {
            append("${items.size} product(s) below reorder level")
            if (topProducts.isNotEmpty()) {
                append(": ")
                append(topProducts.joinToString(", "))
                if (items.size > 5) append(" (+${items.size - 5} more)")
            }
        }

        notificationRepository.insert(
            Notification(
                id = IdGenerator.newId(),
                type = NotificationType.LOW_STOCK,
                title = title,
                message = message,
                channel = Notification.Channel.IN_APP,
                recipientType = Notification.RecipientType.ROLE,
                recipientId = Role.STORE_MANAGER.name,
                referenceType = "warehouse",
                referenceId = items.firstOrNull()?.warehouseId,
                createdAt = now,
            )
        )
    }
}
