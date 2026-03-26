package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.core.pagination.PageRequest
import com.zyntasolutions.zyntapos.core.pagination.PaginatedResult
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.CustomerRepository
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import com.zyntasolutions.zyntapos.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

// ─────────────────────────────────────────────────────────────────────────────
// FakeCustomerRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakeCustomerRepository : CustomerRepository {
    val customers = mutableListOf<Customer>()
    private val _flow = MutableStateFlow<List<Customer>>(emptyList())
    override fun getAll(): Flow<List<Customer>> = _flow
    override suspend fun getById(id: String): Result<Customer> =
        customers.firstOrNull { it.id == id }?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("Not found"))
    override fun search(query: String): Flow<List<Customer>> =
        _flow.map { list -> list.filter { it.name.contains(query, true) || it.phone.contains(query) } }
    override suspend fun insert(customer: Customer): Result<Unit> {
        customers.add(customer)
        _flow.value = customers.toList()
        return Result.Success(Unit)
    }
    override suspend fun update(customer: Customer): Result<Unit> {
        val i = customers.indexOfFirst { it.id == customer.id }
        if (i == -1) return Result.Error(DatabaseException("Not found"))
        customers[i] = customer
        _flow.value = customers.toList()
        return Result.Success(Unit)
    }
    override suspend fun delete(id: String): Result<Unit> {
        customers.removeAll { it.id == id }
        _flow.value = customers.toList()
        return Result.Success(Unit)
    }
    override fun searchGlobal(query: String): Flow<List<Customer>> = search(query)
    override fun getByStore(storeId: String): Flow<List<Customer>> =
        _flow.map { list -> list.filter { it.storeId == storeId } }
    override fun getGlobalCustomers(): Flow<List<Customer>> =
        _flow.map { list -> list.filter { it.storeId == null } }
    override suspend fun makeGlobal(customerId: String): Result<Unit> {
        val i = customers.indexOfFirst { it.id == customerId }
        if (i == -1) return Result.Error(DatabaseException("Not found"))
        customers[i] = customers[i].copy(storeId = null)
        _flow.value = customers.toList()
        return Result.Success(Unit)
    }
    override suspend fun updateLoyaltyPoints(customerId: String, points: Int): Result<Unit> {
        val i = customers.indexOfFirst { it.id == customerId }
        if (i == -1) return Result.Error(DatabaseException("Not found"))
        customers[i] = customers[i].copy(loyaltyPoints = points)
        _flow.value = customers.toList()
        return Result.Success(Unit)
    }
    override suspend fun getPage(pageRequest: PageRequest, searchQuery: String?): PaginatedResult<Customer> =
        PaginatedResult(items = emptyList(), totalCount = 0L, hasMore = false)
}

// ─────────────────────────────────────────────────────────────────────────────
// FakeSettingsRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakeSettingsRepository : SettingsRepository {
    private val store = mutableMapOf<String, String>()

    fun put(key: String, value: String) { store[key] = value }

    override suspend fun get(key: String): String? = store[key]

    override suspend fun set(key: String, value: String): Result<Unit> {
        store[key] = value
        return Result.Success(Unit)
    }

    override suspend fun getAll(): Map<String, String> = store.toMap()

    override fun observe(key: String): Flow<String?> =
        MutableStateFlow(store[key])
}

// ─────────────────────────────────────────────────────────────────────────────
// FakeSyncRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakeSyncRepository : SyncRepository {
    val operations = mutableListOf<SyncOperation>()
    override suspend fun getPendingOperations(): List<SyncOperation> = operations.toList()
    override suspend fun markSynced(ids: List<String>): Result<Unit> {
        operations.removeAll { it.id in ids }
        return Result.Success(Unit)
    }
    override suspend fun pushToServer(ops: List<SyncOperation>): Result<Unit> = Result.Success(Unit)
    override suspend fun pullFromServer(lastSyncTs: Long): Result<List<SyncOperation>> = Result.Success(emptyList())
}
