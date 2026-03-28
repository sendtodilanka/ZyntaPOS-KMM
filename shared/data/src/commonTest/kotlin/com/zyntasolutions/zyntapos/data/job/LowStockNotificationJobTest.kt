package com.zyntasolutions.zyntapos.data.job

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Notification
import com.zyntasolutions.zyntapos.domain.model.WarehouseStock
import com.zyntasolutions.zyntapos.domain.repository.NotificationRepository
import com.zyntasolutions.zyntapos.domain.repository.WarehouseStockRepository
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
 * ZyntaPOS — LowStockNotificationJobTest Unit Tests (commonTest)
 *
 * Validates the warehouse low-stock alert logic in [LowStockNotificationJob.runCheck].
 *
 * Coverage:
 *  A. no notification when getAllLowStock returns empty list
 *  B. notification created with warehouse name in title
 *  C. notification message lists product names for critical items
 *  D. items with stockShortfall <= 0 are excluded (no notification for non-critical)
 *  E. multiple warehouses produce separate notifications for each
 *  F. exception swallowed without re-throwing (non-cancellation exception)
 *  G. notification recipientId is STORE_MANAGER role name
 */
class LowStockNotificationJobTest {

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private class FakeWarehouseStockRepository(
        private val lowStockItems: List<WarehouseStock> = emptyList(),
        private val shouldThrow: Boolean = false,
    ) : WarehouseStockRepository {
        override fun getAllLowStock(): Flow<List<WarehouseStock>> {
            if (shouldThrow) throw RuntimeException("DB error")
            return flowOf(lowStockItems)
        }

        override fun getByWarehouse(warehouseId: String): Flow<List<WarehouseStock>> = flowOf(emptyList())
        override fun getByProduct(productId: String): Flow<List<WarehouseStock>> = flowOf(emptyList())
        override suspend fun getEntry(warehouseId: String, productId: String): Result<WarehouseStock?> =
            Result.Success(null)
        override suspend fun getTotalStock(productId: String): Result<Double> = Result.Success(0.0)
        override fun getLowStockByWarehouse(warehouseId: String): Flow<List<WarehouseStock>> = flowOf(emptyList())
        override suspend fun upsert(stock: WarehouseStock): Result<Unit> = Result.Success(Unit)
        override suspend fun adjustStock(
            warehouseId: String,
            productId: String,
            delta: Double,
        ): Result<Unit> = Result.Success(Unit)
        override suspend fun transferStock(
            sourceWarehouseId: String,
            destWarehouseId: String,
            productId: String,
            quantity: Double,
        ): Result<Unit> = Result.Success(Unit)
        override suspend fun deleteEntry(warehouseId: String, productId: String): Result<Unit> =
            Result.Success(Unit)
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

    private class FakeSyncStatusPort : com.zyntasolutions.zyntapos.domain.port.SyncStatusPort {
        override val isSyncing: StateFlow<Boolean> = MutableStateFlow(false)
        override val isNetworkConnected: StateFlow<Boolean> = MutableStateFlow(true)
        override val lastSyncFailed: StateFlow<Boolean> = MutableStateFlow(false)
        override val pendingCount: StateFlow<Int> = MutableStateFlow(0)
        override val newConflictCount: SharedFlow<Int> = MutableSharedFlow()
        override val onSyncComplete: SharedFlow<Unit> = MutableSharedFlow()
    }

    private fun buildWarehouseStock(
        warehouseId: String = "wh-1",
        productId: String = "prod-1",
        productName: String? = "Espresso",
        quantity: Double = 2.0,
        minQuantity: Double = 10.0,
        warehouseName: String? = "Main Warehouse",
    ) = WarehouseStock(
        id = "$warehouseId-$productId",
        warehouseId = warehouseId,
        productId = productId,
        quantity = quantity,
        minQuantity = minQuantity,
        productName = productName,
        warehouseName = warehouseName,
    )

    private fun makeJob(
        warehouseStockRepo: FakeWarehouseStockRepository,
        notifRepo: FakeNotificationRepository = FakeNotificationRepository(),
    ): Pair<LowStockNotificationJob, FakeNotificationRepository> {
        val syncPort = FakeSyncStatusPort()
        val scope = kotlinx.coroutines.MainScope()
        return LowStockNotificationJob(
            warehouseStockRepository = warehouseStockRepo,
            notificationRepository = notifRepo,
            syncStatusPort = syncPort,
            scope = scope,
        ) to notifRepo
    }

    // ── A — No notification when empty ────────────────────────────────────────

    @Test
    fun `A - no notification when getAllLowStock returns empty list`() = runTest {
        val (job, notifRepo) = makeJob(FakeWarehouseStockRepository(emptyList()))

        job.runCheck()

        assertTrue(notifRepo.inserted.isEmpty(), "No notification expected when stock list is empty")
    }

    // ── B — Notification title contains warehouse name ─────────────────────

    @Test
    fun `B - notification title contains warehouse name`() = runTest {
        val stock = buildWarehouseStock(warehouseName = "North Warehouse", quantity = 1.0, minQuantity = 10.0)
        val (job, notifRepo) = makeJob(FakeWarehouseStockRepository(listOf(stock)))

        job.runCheck()

        assertEquals(1, notifRepo.inserted.size)
        assertTrue(
            notifRepo.inserted[0].title.contains("North Warehouse"),
            "Title must contain warehouse name: ${notifRepo.inserted[0].title}",
        )
    }

    // ── C — Message lists product names ───────────────────────────────────────

    @Test
    fun `C - notification message lists product name for critical items`() = runTest {
        val stock = buildWarehouseStock(productName = "Latte Blend", quantity = 2.0, minQuantity = 15.0)
        val (job, notifRepo) = makeJob(FakeWarehouseStockRepository(listOf(stock)))

        job.runCheck()

        assertEquals(1, notifRepo.inserted.size)
        assertTrue(
            notifRepo.inserted[0].message.contains("Latte Blend"),
            "Message must list the product name: ${notifRepo.inserted[0].message}",
        )
    }

    // ── D — Non-critical items excluded ───────────────────────────────────────

    @Test
    fun `D - no notification when all items have stockShortfall of zero or less`() = runTest {
        // stockShortfall = minQuantity - quantity; non-critical = shortfall <= 0
        val stockAtReorder = buildWarehouseStock(quantity = 10.0, minQuantity = 10.0) // shortfall = 0
        val stockAboveReorder = buildWarehouseStock(quantity = 15.0, minQuantity = 10.0) // shortfall < 0
        val (job, notifRepo) = makeJob(FakeWarehouseStockRepository(listOf(stockAtReorder, stockAboveReorder)))

        job.runCheck()

        assertTrue(notifRepo.inserted.isEmpty(), "No notification when no items are critically low")
    }

    // ── E — Exception swallowed ───────────────────────────────────────────────

    @Test
    fun `E - runCheck swallows non-cancellation exceptions without re-throwing`() = runTest {
        val (job, _) = makeJob(FakeWarehouseStockRepository(shouldThrow = true))

        // Must not throw
        job.runCheck()
    }

    // ── F — Multiple warehouses produce separate notifications ─────────────────

    @Test
    fun `F - each warehouse with critical items gets its own notification`() = runTest {
        val stockWh1 = buildWarehouseStock(warehouseId = "wh-1", warehouseName = "Warehouse A", quantity = 1.0, minQuantity = 10.0)
        val stockWh2 = buildWarehouseStock(warehouseId = "wh-2", warehouseName = "Warehouse B", quantity = 2.0, minQuantity = 20.0)
        val (job, notifRepo) = makeJob(FakeWarehouseStockRepository(listOf(stockWh1, stockWh2)))

        job.runCheck()

        assertEquals(2, notifRepo.inserted.size, "Expected one notification per warehouse")
        val titles = notifRepo.inserted.map { it.title }
        assertTrue(titles.any { it.contains("Warehouse A") }, "Expected notification for Warehouse A")
        assertTrue(titles.any { it.contains("Warehouse B") }, "Expected notification for Warehouse B")
    }

    // ── G — Recipient is STORE_MANAGER ────────────────────────────────────────

    @Test
    fun `G - notification recipientId is STORE_MANAGER role`() = runTest {
        val stock = buildWarehouseStock(quantity = 1.0, minQuantity = 10.0)
        val (job, notifRepo) = makeJob(FakeWarehouseStockRepository(listOf(stock)))

        job.runCheck()

        assertEquals(1, notifRepo.inserted.size)
        assertEquals("STORE_MANAGER", notifRepo.inserted[0].recipientId)
    }
}
