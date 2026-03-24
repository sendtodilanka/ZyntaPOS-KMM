package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.StockTransfer
import com.zyntasolutions.zyntapos.domain.model.Warehouse
import com.zyntasolutions.zyntapos.domain.repository.WarehouseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

// ─────────────────────────────────────────────────────────────────────────────
// Warehouse Fixtures
// ─────────────────────────────────────────────────────────────────────────────

fun buildWarehouse(
    id: String = "wh-01",
    storeId: String = "store-01",
    name: String = "Main Floor",
    isDefault: Boolean = false,
    isActive: Boolean = true,
) = Warehouse(id = id, storeId = storeId, name = name, isDefault = isDefault, isActive = isActive)

fun buildStockTransfer(
    id: String = "transfer-01",
    sourceWarehouseId: String = "wh-01",
    destWarehouseId: String = "wh-02",
    productId: String = "prod-01",
    quantity: Double = 10.0,
    status: StockTransfer.Status = StockTransfer.Status.PENDING,
    transferredBy: String? = null,
) = StockTransfer(
    id = id,
    sourceWarehouseId = sourceWarehouseId,
    destWarehouseId = destWarehouseId,
    productId = productId,
    quantity = quantity,
    status = status,
    transferredBy = transferredBy,
)

// ─────────────────────────────────────────────────────────────────────────────
// Fake WarehouseRepository
// ─────────────────────────────────────────────────────────────────────────────

/**
 * In-memory fake for [WarehouseRepository].
 */
class FakeWarehouseRepository : WarehouseRepository {
    val warehouses = mutableListOf<Warehouse>()
    val transfers = mutableListOf<StockTransfer>()
    var shouldFail = false
    var commitedTransferIds = mutableListOf<String>()

    private val _warehousesFlow = MutableStateFlow<List<Warehouse>>(emptyList())
    private val _transfersFlow = MutableStateFlow<List<StockTransfer>>(emptyList())

    override fun getByStore(storeId: String): Flow<List<Warehouse>> = _warehousesFlow

    override suspend fun getDefault(storeId: String): Result<Warehouse?> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        return Result.Success(warehouses.find { it.storeId == storeId && it.isDefault })
    }

    override suspend fun getById(id: String): Result<Warehouse> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        return warehouses.find { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("Warehouse not found: $id"))
    }

    override suspend fun insert(warehouse: Warehouse): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        warehouses.add(warehouse)
        _warehousesFlow.value = warehouses.toList()
        return Result.Success(Unit)
    }

    override suspend fun update(warehouse: Warehouse): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        warehouses.removeAll { it.id == warehouse.id }
        warehouses.add(warehouse)
        _warehousesFlow.value = warehouses.toList()
        return Result.Success(Unit)
    }

    override fun getTransfersByWarehouse(warehouseId: String): Flow<List<StockTransfer>> = _transfersFlow

    override suspend fun getTransferById(id: String): Result<StockTransfer> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        return transfers.find { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("Transfer not found: $id"))
    }

    override suspend fun getPendingTransfers(): Result<List<StockTransfer>> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        return Result.Success(transfers.filter { it.status == StockTransfer.Status.PENDING })
    }

    override suspend fun createTransfer(transfer: StockTransfer): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        transfers.add(transfer)
        _transfersFlow.value = transfers.toList()
        return Result.Success(Unit)
    }

    override suspend fun commitTransfer(transferId: String, confirmedBy: String): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        val idx = transfers.indexOfFirst { it.id == transferId }
        if (idx < 0) return Result.Error(DatabaseException("Transfer not found: $transferId"))
        val t = transfers[idx]
        if (t.status != StockTransfer.Status.PENDING) {
            return Result.Error(ValidationException("Transfer is already ${t.status}"))
        }
        transfers[idx] = t.copy(status = StockTransfer.Status.COMMITTED, transferredBy = confirmedBy)
        commitedTransferIds.add(transferId)
        _transfersFlow.value = transfers.toList()
        return Result.Success(Unit)
    }

    override suspend fun cancelTransfer(transferId: String): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        val idx = transfers.indexOfFirst { it.id == transferId }
        if (idx < 0) return Result.Error(DatabaseException("Transfer not found: $transferId"))
        val t = transfers[idx]
        if (!t.status.isCancellable) {
            return Result.Error(ValidationException("Transfer is already ${t.status}"))
        }
        transfers[idx] = t.copy(status = StockTransfer.Status.CANCELLED)
        _transfersFlow.value = transfers.toList()
        return Result.Success(Unit)
    }

    override suspend fun approveTransfer(transferId: String, approvedBy: String): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        val idx = transfers.indexOfFirst { it.id == transferId }
        if (idx < 0) return Result.Error(DatabaseException("Transfer not found: $transferId"))
        transfers[idx] = transfers[idx].copy(status = StockTransfer.Status.APPROVED, approvedBy = approvedBy)
        _transfersFlow.value = transfers.toList()
        return Result.Success(Unit)
    }

    override suspend fun dispatchTransfer(transferId: String, dispatchedBy: String): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        val idx = transfers.indexOfFirst { it.id == transferId }
        if (idx < 0) return Result.Error(DatabaseException("Transfer not found: $transferId"))
        transfers[idx] = transfers[idx].copy(status = StockTransfer.Status.IN_TRANSIT, dispatchedBy = dispatchedBy)
        _transfersFlow.value = transfers.toList()
        return Result.Success(Unit)
    }

    override suspend fun receiveTransfer(transferId: String, receivedBy: String): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        val idx = transfers.indexOfFirst { it.id == transferId }
        if (idx < 0) return Result.Error(DatabaseException("Transfer not found: $transferId"))
        transfers[idx] = transfers[idx].copy(status = StockTransfer.Status.RECEIVED, receivedBy = receivedBy)
        _transfersFlow.value = transfers.toList()
        return Result.Success(Unit)
    }

    override suspend fun getTransfersByStatus(status: StockTransfer.Status): Result<List<StockTransfer>> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        return Result.Success(transfers.filter { it.status == status })
    }

    // ── Pick List (P3-B1) ─────────────────────────────────────────────────

    /** Map of (productId, warehouseId) → (rackName, binLocation) for pick list tests. */
    val rackLocations = mutableMapOf<Pair<String, String>, Pair<String?, String?>>()

    override suspend fun getRackLocationForProduct(
        productId: String,
        warehouseId: String,
    ): Result<Pair<String?, String?>> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        return Result.Success(rackLocations[productId to warehouseId] ?: (null to null))
    }
}
