package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.RackProduct
import com.zyntasolutions.zyntapos.domain.repository.RackProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

// ─────────────────────────────────────────────────────────────────────────────
// RackProduct Fixtures
// ─────────────────────────────────────────────────────────────────────────────

fun buildRackProduct(
    id: String = "rp-01",
    rackId: String = "rack-01",
    productId: String = "prod-01",
    quantity: Double = 10.0,
    binLocation: String? = "A1",
) = RackProduct(
    id = id,
    rackId = rackId,
    productId = productId,
    quantity = quantity,
    binLocation = binLocation,
)

// ─────────────────────────────────────────────────────────────────────────────
// Fake RackProductRepository
// ─────────────────────────────────────────────────────────────────────────────

/**
 * In-memory fake for [RackProductRepository].
 *
 * Supports [shouldFail] to simulate repository-level errors.
 * Flow re-emits on every [upsert] or [delete] call.
 */
class FakeRackProductRepository : RackProductRepository {

    val rackProducts = mutableListOf<RackProduct>()
    private val _flow = MutableStateFlow<List<RackProduct>>(emptyList())

    var shouldFail = false
    var upsertCalled = false
    var lastUpsertedProduct: RackProduct? = null
    var lastDeletedRackId: String? = null
    var lastDeletedProductId: String? = null

    override fun getByRack(rackId: String): Flow<List<RackProduct>> =
        _flow.map { list -> list.filter { it.rackId == rackId } }

    override suspend fun upsert(rackProduct: RackProduct): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        upsertCalled = true
        lastUpsertedProduct = rackProduct
        val idx = rackProducts.indexOfFirst {
            it.rackId == rackProduct.rackId && it.productId == rackProduct.productId
        }
        if (idx >= 0) rackProducts[idx] = rackProduct else rackProducts.add(rackProduct)
        _flow.value = rackProducts.toList()
        return Result.Success(Unit)
    }

    override suspend fun delete(rackId: String, productId: String): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        lastDeletedRackId = rackId
        lastDeletedProductId = productId
        val removed = rackProducts.removeAll { it.rackId == rackId && it.productId == productId }
        _flow.value = rackProducts.toList()
        if (!removed) return Result.Error(DatabaseException("RackProduct not found: $rackId/$productId"))
        return Result.Success(Unit)
    }
}
