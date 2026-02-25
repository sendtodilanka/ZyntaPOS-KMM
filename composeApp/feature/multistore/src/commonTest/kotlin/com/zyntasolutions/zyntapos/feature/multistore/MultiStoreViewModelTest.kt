package com.zyntasolutions.zyntapos.feature.multistore

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.StockTransfer
import com.zyntasolutions.zyntapos.domain.model.Warehouse
import com.zyntasolutions.zyntapos.domain.model.WarehouseRack
import com.zyntasolutions.zyntapos.domain.repository.WarehouseRackRepository
import com.zyntasolutions.zyntapos.domain.repository.WarehouseRepository
import com.zyntasolutions.zyntapos.domain.usecase.multistore.CommitStockTransferUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.DeleteWarehouseRackUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.GetWarehouseRacksUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.SaveWarehouseRackUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// MultiStoreViewModelTest (WarehouseViewModel)
// Tests WarehouseViewModel MVI state transitions using hand-rolled fakes.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class MultiStoreViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val storeId = "store-001"
    private val userId = "user-001"

    // ── Fake backing state ────────────────────────────────────────────────────

    private val warehousesFlow = MutableStateFlow<List<Warehouse>>(emptyList())
    private val transfersFlow = MutableStateFlow<List<StockTransfer>>(emptyList())
    private val racksFlow = MutableStateFlow<List<WarehouseRack>>(emptyList())

    private var shouldFailInsertWarehouse = false
    private var shouldFailUpdateWarehouse = false
    private var shouldFailCreateTransfer = false
    private var shouldFailCommitTransfer = false
    private var shouldFailSaveRack = false
    private var shouldFailDeleteRack = false

    private val now = System.currentTimeMillis()

    private val testWarehouse = Warehouse(
        id = "wh-001",
        storeId = storeId,
        name = "Main Warehouse",
        isDefault = true,
    )

    private val testWarehouse2 = Warehouse(
        id = "wh-002",
        storeId = storeId,
        name = "Storage Room",
        isDefault = false,
    )

    private val testRack = WarehouseRack(
        id = "rack-001",
        warehouseId = "wh-001",
        name = "A1",
        description = "Aisle 1, Shelf 1",
        capacity = 100,
        createdAt = now,
        updatedAt = now,
    )

    private val testTransfer = StockTransfer(
        id = "transfer-001",
        sourceWarehouseId = "wh-001",
        destWarehouseId = "wh-002",
        productId = "prod-001",
        quantity = 10.0,
        status = StockTransfer.Status.PENDING,
    )

    // ── Fake WarehouseRepository ──────────────────────────────────────────────

    private val fakeWarehouseRepository = object : WarehouseRepository {
        override fun getByStore(storeId: String): Flow<List<Warehouse>> = warehousesFlow

        override suspend fun getDefault(storeId: String): Result<Warehouse?> =
            Result.Success(warehousesFlow.value.firstOrNull { it.isDefault })

        override suspend fun getById(id: String): Result<Warehouse> {
            val wh = warehousesFlow.value.firstOrNull { it.id == id }
                ?: return Result.Error(DatabaseException("Warehouse '$id' not found"))
            return Result.Success(wh)
        }

        override suspend fun insert(warehouse: Warehouse): Result<Unit> {
            if (shouldFailInsertWarehouse) return Result.Error(DatabaseException("Insert failed"))
            warehousesFlow.value = warehousesFlow.value + warehouse
            return Result.Success(Unit)
        }

        override suspend fun update(warehouse: Warehouse): Result<Unit> {
            if (shouldFailUpdateWarehouse) return Result.Error(DatabaseException("Update failed"))
            val idx = warehousesFlow.value.indexOfFirst { it.id == warehouse.id }
            if (idx == -1) return Result.Error(DatabaseException("Not found"))
            val updated = warehousesFlow.value.toMutableList().also { it[idx] = warehouse }
            warehousesFlow.value = updated
            return Result.Success(Unit)
        }

        override fun getTransfersByWarehouse(warehouseId: String): Flow<List<StockTransfer>> =
            transfersFlow.map { list ->
                list.filter { it.sourceWarehouseId == warehouseId || it.destWarehouseId == warehouseId }
            }

        override suspend fun getTransferById(id: String): Result<StockTransfer> {
            val t = transfersFlow.value.firstOrNull { it.id == id }
                ?: return Result.Error(DatabaseException("Transfer not found"))
            return Result.Success(t)
        }

        override suspend fun getPendingTransfers(): Result<List<StockTransfer>> =
            Result.Success(transfersFlow.value.filter { it.status == StockTransfer.Status.PENDING })

        override suspend fun createTransfer(transfer: StockTransfer): Result<Unit> {
            if (shouldFailCreateTransfer) return Result.Error(DatabaseException("Create transfer failed"))
            transfersFlow.value = transfersFlow.value + transfer
            return Result.Success(Unit)
        }

        override suspend fun commitTransfer(transferId: String, confirmedBy: String): Result<Unit> {
            if (shouldFailCommitTransfer) return Result.Error(DatabaseException("Commit failed"))
            val idx = transfersFlow.value.indexOfFirst { it.id == transferId }
            if (idx == -1) return Result.Error(DatabaseException("Transfer not found"))
            val updated = transfersFlow.value.toMutableList()
            updated[idx] = updated[idx].copy(status = StockTransfer.Status.COMMITTED)
            transfersFlow.value = updated
            return Result.Success(Unit)
        }

        override suspend fun cancelTransfer(transferId: String): Result<Unit> {
            val idx = transfersFlow.value.indexOfFirst { it.id == transferId }
            if (idx == -1) return Result.Error(DatabaseException("Transfer not found"))
            val updated = transfersFlow.value.toMutableList()
            updated[idx] = updated[idx].copy(status = StockTransfer.Status.CANCELLED)
            transfersFlow.value = updated
            return Result.Success(Unit)
        }
    }

    // ── Fake WarehouseRackRepository ──────────────────────────────────────────

    private val fakeWarehouseRackRepository = object : WarehouseRackRepository {
        override fun getByWarehouse(warehouseId: String): Flow<List<WarehouseRack>> =
            racksFlow.map { list -> list.filter { it.warehouseId == warehouseId } }

        override suspend fun getById(id: String): Result<WarehouseRack> {
            val r = racksFlow.value.firstOrNull { it.id == id }
                ?: return Result.Error(DatabaseException("Rack not found"))
            return Result.Success(r)
        }

        override suspend fun insert(rack: WarehouseRack): Result<Unit> {
            if (shouldFailSaveRack) return Result.Error(DatabaseException("Insert rack failed"))
            racksFlow.value = racksFlow.value + rack
            return Result.Success(Unit)
        }

        override suspend fun update(rack: WarehouseRack): Result<Unit> {
            if (shouldFailSaveRack) return Result.Error(DatabaseException("Update rack failed"))
            val idx = racksFlow.value.indexOfFirst { it.id == rack.id }
            if (idx == -1) return Result.Error(DatabaseException("Not found"))
            val updated = racksFlow.value.toMutableList().also { it[idx] = rack }
            racksFlow.value = updated
            return Result.Success(Unit)
        }

        override suspend fun delete(id: String, deletedAt: Long, updatedAt: Long): Result<Unit> {
            if (shouldFailDeleteRack) return Result.Error(DatabaseException("Delete rack failed"))
            racksFlow.value = racksFlow.value.filter { it.id != id }
            return Result.Success(Unit)
        }
    }

    // ── Use cases wired to fakes ──────────────────────────────────────────────

    private val commitTransferUseCase = CommitStockTransferUseCase(fakeWarehouseRepository)
    private val getWarehouseRacksUseCase = GetWarehouseRacksUseCase(fakeWarehouseRackRepository)
    private val saveWarehouseRackUseCase = SaveWarehouseRackUseCase(fakeWarehouseRackRepository)
    private val deleteWarehouseRackUseCase = DeleteWarehouseRackUseCase(fakeWarehouseRackRepository)

    private lateinit var viewModel: WarehouseViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        warehousesFlow.value = emptyList()
        transfersFlow.value = emptyList()
        racksFlow.value = emptyList()
        shouldFailInsertWarehouse = false
        shouldFailUpdateWarehouse = false
        shouldFailCreateTransfer = false
        shouldFailCommitTransfer = false
        shouldFailSaveRack = false
        shouldFailDeleteRack = false

        viewModel = WarehouseViewModel(
            warehouseRepository = fakeWarehouseRepository,
            commitTransferUseCase = commitTransferUseCase,
            getWarehouseRacksUseCase = getWarehouseRacksUseCase,
            saveWarehouseRackUseCase = saveWarehouseRackUseCase,
            deleteWarehouseRackUseCase = deleteWarehouseRackUseCase,
            currentStoreId = storeId,
            currentUserId = userId,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty warehouses and no error`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.state.value
        assertTrue(state.warehouses.isEmpty())
        assertNull(state.error)
        assertFalse(state.isLoading)
    }

    // ── Reactive warehouse list ────────────────────────────────────────────────

    @Test
    fun `warehouses from repository are reflected in state reactively`() = runTest {
        warehousesFlow.value = listOf(testWarehouse)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.warehouses.size)
        assertEquals("Main Warehouse", viewModel.state.value.warehouses.first().name)
    }

    // ── Warehouse CRUD ────────────────────────────────────────────────────────

    @Test
    fun `SelectWarehouse with null ID opens new-warehouse form and emits NavigateToDetail`() = runTest {
        viewModel.effects.test {
            viewModel.dispatch(WarehouseIntent.SelectWarehouse(null))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is WarehouseEffect.NavigateToDetail)
            assertNull((effect as WarehouseEffect.NavigateToDetail).warehouseId)
            cancelAndIgnoreRemainingEvents()
        }

        assertNull(viewModel.state.value.selectedWarehouse)
        assertFalse(viewModel.state.value.warehouseForm.isEditing)
    }

    @Test
    fun `SelectWarehouse with valid ID loads warehouse into form and emits NavigateToDetail`() = runTest {
        warehousesFlow.value = listOf(testWarehouse)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(WarehouseIntent.SelectWarehouse(testWarehouse.id))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is WarehouseEffect.NavigateToDetail)
            assertEquals(testWarehouse.id, (effect as WarehouseEffect.NavigateToDetail).warehouseId)
            cancelAndIgnoreRemainingEvents()
        }

        val state = viewModel.state.value
        assertNotNull(state.selectedWarehouse)
        assertEquals("Main Warehouse", state.warehouseForm.name)
        assertTrue(state.warehouseForm.isEditing)
    }

    @Test
    fun `SaveWarehouse with valid name creates new warehouse and emits ShowSuccess then NavigateToList`() = runTest {
        viewModel.dispatch(WarehouseIntent.UpdateWarehouseField("name", "New Warehouse"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(WarehouseIntent.SaveWarehouse)
            testDispatcher.scheduler.advanceUntilIdle()

            val effectSuccess = awaitItem()
            assertTrue(effectSuccess is WarehouseEffect.ShowSuccess)
            val effectNav = awaitItem()
            assertTrue(effectNav is WarehouseEffect.NavigateToList)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, warehousesFlow.value.size)
        assertEquals("New Warehouse", warehousesFlow.value.first().name)
    }

    @Test
    fun `SaveWarehouse with blank name sets validation error and does not persist`() = runTest {
        // name is blank by default
        viewModel.dispatch(WarehouseIntent.SaveWarehouse)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.warehouseForm.validationErrors["name"])
        assertTrue(warehousesFlow.value.isEmpty())
    }

    // ── Stock Transfers ────────────────────────────────────────────────────────

    @Test
    fun `SubmitTransfer with valid form creates transfer and emits TransferComplete`() = runTest {
        warehousesFlow.value = listOf(testWarehouse, testWarehouse2)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(WarehouseIntent.InitTransferForm("wh-001"))
        viewModel.dispatch(WarehouseIntent.UpdateTransferField("destWarehouseId", "wh-002"))
        viewModel.dispatch(WarehouseIntent.UpdateTransferField("productId", "prod-001"))
        viewModel.dispatch(WarehouseIntent.UpdateTransferField("quantity", "5.0"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(WarehouseIntent.SubmitTransfer)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect1 = awaitItem()
            assertTrue(effect1 is WarehouseEffect.ShowSuccess)
            val effect2 = awaitItem()
            assertTrue(effect2 is WarehouseEffect.TransferComplete)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, transfersFlow.value.size)
        assertEquals(StockTransfer.Status.PENDING, transfersFlow.value.first().status)
    }

    @Test
    fun `SubmitTransfer with missing required fields sets validation errors`() = runTest {
        // Leave form empty — no source, no dest, no product, no quantity
        viewModel.dispatch(WarehouseIntent.SubmitTransfer)
        testDispatcher.scheduler.advanceUntilIdle()

        val errors = viewModel.state.value.transferForm.validationErrors
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.containsKey("sourceWarehouseId") || errors.containsKey("destWarehouseId"))
        assertTrue(transfersFlow.value.isEmpty())
    }

    @Test
    fun `CommitTransfer on pending transfer emits ShowSuccess`() = runTest {
        transfersFlow.value = listOf(testTransfer)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(WarehouseIntent.CommitTransfer(testTransfer.id))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is WarehouseEffect.ShowSuccess)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(StockTransfer.Status.COMMITTED, transfersFlow.value.first().status)
    }

    @Test
    fun `CancelTransfer on pending transfer marks it CANCELLED and emits ShowSuccess`() = runTest {
        transfersFlow.value = listOf(testTransfer)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(WarehouseIntent.CancelTransfer(testTransfer.id))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is WarehouseEffect.ShowSuccess)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(StockTransfer.Status.CANCELLED, transfersFlow.value.first().status)
    }

    // ── Rack Management ───────────────────────────────────────────────────────

    @Test
    fun `LoadRacks observes racks for a warehouse reactively`() = runTest {
        racksFlow.value = listOf(testRack)
        viewModel.dispatch(WarehouseIntent.LoadRacks(testRack.warehouseId))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.racks.size)
        assertEquals("A1", viewModel.state.value.racks.first().name)
    }

    @Test
    fun `SaveRack with blank name sets validation error and does not persist`() = runTest {
        viewModel.dispatch(WarehouseIntent.UpdateRackField("name", ""))
        viewModel.dispatch(WarehouseIntent.UpdateRackField("warehouseId", "wh-001"))
        // Manually set warehouseId in rackForm
        viewModel.dispatch(WarehouseIntent.SelectRack(null, "wh-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(WarehouseIntent.SaveRack)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.rackForm.validationErrors["name"])
        assertTrue(racksFlow.value.isEmpty())
    }

    @Test
    fun `RequestDeleteRack sets showDeleteRackConfirm then ConfirmDeleteRack deletes it`() = runTest {
        racksFlow.value = listOf(testRack)
        viewModel.dispatch(WarehouseIntent.LoadRacks(testRack.warehouseId))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(WarehouseIntent.RequestDeleteRack(testRack))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.showDeleteRackConfirm)
        assertEquals(testRack.id, viewModel.state.value.showDeleteRackConfirm?.id)

        viewModel.effects.test {
            viewModel.dispatch(WarehouseIntent.ConfirmDeleteRack)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is WarehouseEffect.ShowSuccess)
            cancelAndIgnoreRemainingEvents()
        }

        assertNull(viewModel.state.value.showDeleteRackConfirm)
        assertTrue(racksFlow.value.isEmpty())
    }

    @Test
    fun `CancelDeleteRack clears showDeleteRackConfirm without deleting`() = runTest {
        racksFlow.value = listOf(testRack)
        viewModel.dispatch(WarehouseIntent.RequestDeleteRack(testRack))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.showDeleteRackConfirm)

        viewModel.dispatch(WarehouseIntent.CancelDeleteRack)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.showDeleteRackConfirm)
        assertEquals(1, racksFlow.value.size) // rack not deleted
    }

    // ── UI Feedback ───────────────────────────────────────────────────────────

    @Test
    fun `DismissMessage clears error and successMessage in state`() = runTest {
        warehousesFlow.value = listOf(testWarehouse)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(WarehouseIntent.UpdateWarehouseField("name", "Temp Warehouse"))
        viewModel.dispatch(WarehouseIntent.SaveWarehouse)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.successMessage)

        viewModel.dispatch(WarehouseIntent.DismissMessage)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.error)
        assertNull(viewModel.state.value.successMessage)
    }
}
