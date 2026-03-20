package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.WarehouseStock
import com.zyntasolutions.zyntapos.domain.repository.WarehouseStockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

// ─────────────────────────────────────────────────────────────────────────────
// WarehouseStock Fixtures
// ─────────────────────────────────────────────────────────────────────────────

fun buildWarehouseStock(
    id: String = "ws-01",
    warehouseId: String = "wh-01",
    productId: String = "prod-01",
    quantity: Double = 100.0,
    minQuantity: Double = 10.0,
    productName: String? = "Test Product",
) = WarehouseStock(
    id = id,
    warehouseId = warehouseId,
    productId = productId,
    quantity = quantity,
    minQuantity = minQuantity,
    productName = productName,
)

// ─────────────────────────────────────────────────────────────────────────────
// Fake WarehouseStockRepository
// ─────────────────────────────────────────────────────────────────────────────

/**
 * In-memory fake for [WarehouseStockRepository].
 */
class FakeWarehouseStockRepository : WarehouseStockRepository {

    private val _entries = MutableStateFlow<List<WarehouseStock>>(emptyList())

    /** Direct access for test setup. */
    val entries: List<WarehouseStock> get() = _entries.value

    var shouldFail = false

    fun seed(vararg items: WarehouseStock) {
        _entries.value = items.toList()
    }

    override fun getByWarehouse(warehouseId: String): Flow<List<WarehouseStock>> =
        _entries.map { list -> list.filter { it.warehouseId == warehouseId } }

    override fun getByProduct(productId: String): Flow<List<WarehouseStock>> =
        _entries.map { list -> list.filter { it.productId == productId } }

    override suspend fun getEntry(warehouseId: String, productId: String): Result<WarehouseStock?> {
        if (shouldFail) return Result.Error(ValidationException("Forced failure"))
        return Result.Success(
            _entries.value.find { it.warehouseId == warehouseId && it.productId == productId }
        )
    }

    override suspend fun getTotalStock(productId: String): Result<Double> {
        if (shouldFail) return Result.Error(ValidationException("Forced failure"))
        val total = _entries.value.filter { it.productId == productId }.sumOf { it.quantity }
        return Result.Success(total)
    }

    override fun getLowStockByWarehouse(warehouseId: String): Flow<List<WarehouseStock>> =
        _entries.map { list ->
            list.filter { it.warehouseId == warehouseId && it.isLowStock }
        }

    override fun getAllLowStock(): Flow<List<WarehouseStock>> =
        _entries.map { list -> list.filter { it.isLowStock } }

    override suspend fun upsert(stock: WarehouseStock): Result<Unit> {
        if (shouldFail) return Result.Error(ValidationException("Forced failure"))
        val current = _entries.value.toMutableList()
        val idx = current.indexOfFirst {
            it.warehouseId == stock.warehouseId && it.productId == stock.productId
        }
        if (idx >= 0) current[idx] = stock else current.add(stock)
        _entries.value = current
        return Result.Success(Unit)
    }

    override suspend fun adjustStock(
        warehouseId: String,
        productId: String,
        delta: Double,
    ): Result<Unit> {
        if (shouldFail) return Result.Error(ValidationException("Forced failure"))
        val current = _entries.value.toMutableList()
        val idx = current.indexOfFirst { it.warehouseId == warehouseId && it.productId == productId }
        return if (idx >= 0) {
            current[idx] = current[idx].copy(quantity = current[idx].quantity + delta)
            _entries.value = current
            Result.Success(Unit)
        } else {
            Result.Error(ValidationException("Stock entry not found for warehouse $warehouseId product $productId"))
        }
    }

    override suspend fun transferStock(
        sourceWarehouseId: String,
        destWarehouseId: String,
        productId: String,
        quantity: Double,
    ): Result<Unit> {
        if (shouldFail) return Result.Error(ValidationException("Forced failure"))
        val sourceResult = adjustStock(sourceWarehouseId, productId, -quantity)
        if (sourceResult is Result.Error) return sourceResult
        return adjustStock(destWarehouseId, productId, quantity)
    }

    override suspend fun deleteEntry(warehouseId: String, productId: String): Result<Unit> {
        if (shouldFail) return Result.Error(ValidationException("Forced failure"))
        _entries.value = _entries.value.filterNot {
            it.warehouseId == warehouseId && it.productId == productId
        }
        return Result.Success(Unit)
    }
}
