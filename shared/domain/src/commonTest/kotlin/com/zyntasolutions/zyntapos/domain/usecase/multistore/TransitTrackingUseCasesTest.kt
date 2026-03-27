package com.zyntasolutions.zyntapos.domain.usecase.multistore

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Store
import com.zyntasolutions.zyntapos.domain.model.StockTransfer
import com.zyntasolutions.zyntapos.domain.model.TransitEvent
import com.zyntasolutions.zyntapos.domain.repository.StoreRepository
import com.zyntasolutions.zyntapos.domain.repository.TransitTrackingRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeWarehouseRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildStockTransfer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// Inline Fakes
// ─────────────────────────────────────────────────────────────────────────────

private fun buildStore(id: String = "s1", name: String = "Store A") = Store(
    id = id,
    name = name,
    createdAt = Instant.fromEpochSeconds(0),
    updatedAt = Instant.fromEpochSeconds(0),
)

private class FakeStoreRepo(private val stores: List<Store> = emptyList()) : StoreRepository {
    override fun getAllStores(): Flow<List<Store>> = MutableStateFlow(stores)
    override suspend fun getById(storeId: String): Store? = stores.firstOrNull { it.id == storeId }
    override suspend fun getStoreName(storeId: String): String? = stores.firstOrNull { it.id == storeId }?.name
    override suspend fun upsertFromSync(store: Store) {}
}

private class FakeTransitRepo : TransitTrackingRepository {
    val events = mutableListOf<TransitEvent>()
    var inTransitCount = 0

    override fun getEventsForTransfer(transferId: String): Flow<List<TransitEvent>> =
        flowOf(events.filter { it.transferId == transferId })

    override suspend fun addEvent(event: TransitEvent): Result<Unit> {
        events.add(event)
        return Result.Success(Unit)
    }

    override suspend fun getInTransitCount(): Result<Int> = Result.Success(inTransitCount)
}

// ─────────────────────────────────────────────────────────────────────────────
// GetAllStoresUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class GetAllStoresUseCaseTest {

    @Test
    fun `invoke_returnsAllStoresFlow`() = runTest {
        val stores = listOf(
            buildStore(id = "s1", name = "Store A"),
            buildStore(id = "s2", name = "Store B"),
        )
        GetAllStoresUseCase(FakeStoreRepo(stores)).invoke().test {
            val list = awaitItem()
            assertEquals(2, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `invoke_returnsEmptyFlow_whenNoStores`() = runTest {
        GetAllStoresUseCase(FakeStoreRepo()).invoke().test {
            assertEquals(0, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GetInTransitCountUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class GetInTransitCountUseCaseTest {

    @Test
    fun `invoke_returnsCountFromRepository`() = runTest {
        val repo = FakeTransitRepo().also { it.inTransitCount = 7 }
        val result = GetInTransitCountUseCase(repo).invoke()
        assertIs<Result.Success<*>>(result)
        assertEquals(7, (result as Result.Success).data)
    }

    @Test
    fun `invoke_returnsZeroWhenNoInTransit`() = runTest {
        val result = GetInTransitCountUseCase(FakeTransitRepo()).invoke()
        assertIs<Result.Success<*>>(result)
        assertEquals(0, (result as Result.Success).data)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GetTransitHistoryUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class GetTransitHistoryUseCaseTest {

    private fun buildEvent(id: String, transferId: String) = TransitEvent(
        id = id,
        transferId = transferId,
        eventType = TransitEvent.EventType.CHECKPOINT,
        recordedBy = "user-01",
        recordedAt = 1_000_000L,
    )

    @Test
    fun `invoke_returnsEventsForTransfer`() = runTest {
        val repo = FakeTransitRepo()
        repo.events.add(buildEvent("e1", "t1"))
        repo.events.add(buildEvent("e2", "t1"))
        repo.events.add(buildEvent("e3", "t2"))

        GetTransitHistoryUseCase(repo).invoke("t1").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.all { it.transferId == "t1" })
            cancelAndIgnoreRemainingEvents()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AddTransitEventUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class AddTransitEventUseCaseTest {

    private fun makeUseCase(
        transitRepo: FakeTransitRepo = FakeTransitRepo(),
        warehouseRepo: FakeWarehouseRepository = FakeWarehouseRepository(),
    ) = AddTransitEventUseCase(transitRepo, warehouseRepo)

    @Test
    fun `blankTransferId_returnsValidationError`() = runTest {
        val result = makeUseCase().invoke("", TransitEvent.EventType.CHECKPOINT, "user-01")
        assertIs<Result.Error>(result)
        assertIs<ValidationException>((result as Result.Error).exception)
    }

    @Test
    fun `blankRecordedBy_returnsValidationError`() = runTest {
        val result = makeUseCase().invoke("t1", TransitEvent.EventType.CHECKPOINT, "")
        assertIs<Result.Error>(result)
        assertIs<ValidationException>((result as Result.Error).exception)
    }

    @Test
    fun `autoGeneratedEventType_returnsValidationError`() = runTest {
        val warehouseRepo = FakeWarehouseRepository()
        warehouseRepo.transfers.add(buildStockTransfer(id = "t1", status = StockTransfer.Status.IN_TRANSIT))

        val result = makeUseCase(warehouseRepo = warehouseRepo)
            .invoke("t1", TransitEvent.EventType.DISPATCHED, "user-01")
        assertIs<Result.Error>(result)
        assertIs<ValidationException>((result as Result.Error).exception)
    }

    @Test
    fun `inTransitTransfer_withValidEventType_addsEvent`() = runTest {
        val transitRepo = FakeTransitRepo()
        val warehouseRepo = FakeWarehouseRepository()
        warehouseRepo.transfers.add(buildStockTransfer(id = "t1", status = StockTransfer.Status.IN_TRANSIT))

        val result = makeUseCase(transitRepo, warehouseRepo)
            .invoke("t1", TransitEvent.EventType.CHECKPOINT, "user-01", "Colombo hub")
        assertIs<Result.Success<*>>(result)
        assertEquals(1, transitRepo.events.size)
        assertEquals(TransitEvent.EventType.CHECKPOINT, transitRepo.events.first().eventType)
    }

    @Test
    fun `nonInTransitTransfer_returnsValidationError`() = runTest {
        val warehouseRepo = FakeWarehouseRepository()
        warehouseRepo.transfers.add(buildStockTransfer(id = "t1", status = StockTransfer.Status.APPROVED))

        val result = makeUseCase(warehouseRepo = warehouseRepo)
            .invoke("t1", TransitEvent.EventType.CHECKPOINT, "user-01")
        assertIs<Result.Error>(result)
        assertIs<ValidationException>((result as Result.Error).exception)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ApproveStockTransferUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class ApproveStockTransferUseCaseTest {

    @Test
    fun `blankTransferId_returnsValidationError`() = runTest {
        val result = ApproveStockTransferUseCase(FakeWarehouseRepository()).invoke("", "manager-01")
        assertIs<Result.Error>(result)
        assertIs<ValidationException>((result as Result.Error).exception)
    }

    @Test
    fun `blankApproverId_returnsValidationError`() = runTest {
        val result = ApproveStockTransferUseCase(FakeWarehouseRepository()).invoke("t1", "")
        assertIs<Result.Error>(result)
        assertIs<ValidationException>((result as Result.Error).exception)
    }

    @Test
    fun `pendingTransfer_approves`() = runTest {
        val repo = FakeWarehouseRepository()
        repo.transfers.add(buildStockTransfer(id = "t1", status = StockTransfer.Status.PENDING))

        val result = ApproveStockTransferUseCase(repo).invoke("t1", "manager-01")
        assertIs<Result.Success<*>>(result)
        assertEquals(StockTransfer.Status.APPROVED, repo.transfers.first { it.id == "t1" }.status)
    }

    @Test
    fun `alreadyApprovedTransfer_returnsStatusError`() = runTest {
        val repo = FakeWarehouseRepository()
        repo.transfers.add(buildStockTransfer(id = "t1", status = StockTransfer.Status.APPROVED))

        val result = ApproveStockTransferUseCase(repo).invoke("t1", "manager-01")
        assertIs<Result.Error>(result)
        assertIs<ValidationException>((result as Result.Error).exception)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DispatchStockTransferUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class DispatchStockTransferUseCaseTest {

    @Test
    fun `blankTransferId_returnsValidationError`() = runTest {
        val result = DispatchStockTransferUseCase(FakeWarehouseRepository()).invoke("", "user-01")
        assertIs<Result.Error>(result)
        assertIs<ValidationException>((result as Result.Error).exception)
    }

    @Test
    fun `approvedTransfer_dispatches`() = runTest {
        val repo = FakeWarehouseRepository()
        repo.transfers.add(buildStockTransfer(id = "t1", status = StockTransfer.Status.APPROVED))

        val result = DispatchStockTransferUseCase(repo).invoke("t1", "driver-01")
        assertIs<Result.Success<*>>(result)
        assertEquals(StockTransfer.Status.IN_TRANSIT, repo.transfers.first { it.id == "t1" }.status)
    }

    @Test
    fun `nonApprovedTransfer_returnsStatusError`() = runTest {
        val repo = FakeWarehouseRepository()
        repo.transfers.add(buildStockTransfer(id = "t1", status = StockTransfer.Status.PENDING))

        val result = DispatchStockTransferUseCase(repo).invoke("t1", "driver-01")
        assertIs<Result.Error>(result)
        assertIs<ValidationException>((result as Result.Error).exception)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ReceiveStockTransferUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class ReceiveStockTransferUseCaseTest {

    @Test
    fun `blankTransferId_returnsValidationError`() = runTest {
        val result = ReceiveStockTransferUseCase(FakeWarehouseRepository()).invoke("", "user-01")
        assertIs<Result.Error>(result)
        assertIs<ValidationException>((result as Result.Error).exception)
    }

    @Test
    fun `inTransitTransfer_receives`() = runTest {
        val repo = FakeWarehouseRepository()
        repo.transfers.add(buildStockTransfer(id = "t1", status = StockTransfer.Status.IN_TRANSIT))

        val result = ReceiveStockTransferUseCase(repo).invoke("t1", "receiver-01")
        assertIs<Result.Success<*>>(result)
        assertEquals(StockTransfer.Status.RECEIVED, repo.transfers.first { it.id == "t1" }.status)
    }

    @Test
    fun `nonInTransitTransfer_returnsStatusError`() = runTest {
        val repo = FakeWarehouseRepository()
        repo.transfers.add(buildStockTransfer(id = "t1", status = StockTransfer.Status.APPROVED))

        val result = ReceiveStockTransferUseCase(repo).invoke("t1", "receiver-01")
        assertIs<Result.Error>(result)
        assertIs<ValidationException>((result as Result.Error).exception)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LogWorkflowTransitEventUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class LogWorkflowTransitEventUseCaseTest {

    @Test
    fun `logsAutoGeneratedEvent_withoutStatusCheck`() = runTest {
        val repo = FakeTransitRepo()
        val result = LogWorkflowTransitEventUseCase(repo).invoke(
            transferId = "t1",
            eventType = TransitEvent.EventType.DISPATCHED,
            recordedBy = "system",
        )
        assertIs<Result.Success<*>>(result)
        assertEquals(1, repo.events.size)
        assertEquals(TransitEvent.EventType.DISPATCHED, repo.events.first().eventType)
    }

    @Test
    fun `logsReceivedEvent`() = runTest {
        val repo = FakeTransitRepo()
        val result = LogWorkflowTransitEventUseCase(repo).invoke(
            transferId = "t1",
            eventType = TransitEvent.EventType.RECEIVED,
            recordedBy = "receiver-01",
            note = "All items present",
        )
        assertIs<Result.Success<*>>(result)
        assertEquals("All items present", repo.events.first().note)
    }
}
