package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.WarehouseRack
import com.zyntasolutions.zyntapos.domain.repository.WarehouseRackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

// ─────────────────────────────────────────────────────────────────────────────
// WarehouseRack Fixtures
// ─────────────────────────────────────────────────────────────────────────────

fun buildWarehouseRack(
    id: String = "rack-01",
    warehouseId: String = "wh-01",
    name: String = "Rack A1",
    description: String? = null,
    capacity: Int? = null,
) = WarehouseRack(
    id = id,
    warehouseId = warehouseId,
    name = name,
    description = description,
    capacity = capacity,
    createdAt = Clock.System.now().toEpochMilliseconds(),
    updatedAt = Clock.System.now().toEpochMilliseconds(),
)

// ─────────────────────────────────────────────────────────────────────────────
// Fake WarehouseRackRepository
// ─────────────────────────────────────────────────────────────────────────────

/**
 * In-memory fake for [WarehouseRackRepository].
 */
class FakeWarehouseRackRepository : WarehouseRackRepository {
    val racks = mutableListOf<WarehouseRack>()
    var shouldFail = false
    var lastDeletedId: String? = null
    var lastDeletedAt: Long? = null
    var insertCalled = false
    var updateCalled = false

    private val _racksFlow = MutableStateFlow<List<WarehouseRack>>(emptyList())

    override fun getByWarehouse(warehouseId: String): Flow<List<WarehouseRack>> =
        _racksFlow.map { list -> list.filter { it.warehouseId == warehouseId } }

    override suspend fun getById(id: String): Result<WarehouseRack> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        return racks.find { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("WarehouseRack not found: $id"))
    }

    override suspend fun insert(rack: WarehouseRack): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        insertCalled = true
        racks.add(rack)
        _racksFlow.value = racks.toList()
        return Result.Success(Unit)
    }

    override suspend fun update(rack: WarehouseRack): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        updateCalled = true
        val idx = racks.indexOfFirst { it.id == rack.id }
        if (idx < 0) {
            racks.add(rack)
        } else {
            racks[idx] = rack
        }
        _racksFlow.value = racks.toList()
        return Result.Success(Unit)
    }

    override suspend fun delete(id: String, deletedAt: Long, updatedAt: Long): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        val idx = racks.indexOfFirst { it.id == id }
        if (idx < 0) return Result.Error(DatabaseException("WarehouseRack not found: $id"))
        racks.removeAt(idx)
        _racksFlow.value = racks.toList()
        lastDeletedId = id
        lastDeletedAt = deletedAt
        return Result.Success(Unit)
    }
}
