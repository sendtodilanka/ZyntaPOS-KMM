package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.ReturnStockPolicy
import com.zyntasolutions.zyntapos.domain.model.Store
import com.zyntasolutions.zyntapos.domain.repository.StoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

/**
 * SQLDelight-backed implementation of [StoreRepository].
 *
 * Reads from the local `stores` table which is populated via sync.
 */
class StoreRepositoryImpl(
    private val database: ZyntaDatabase,
) : StoreRepository {

    private val q get() = database.storesQueries

    override fun getAllStores(): Flow<List<Store>> =
        q.getAllStores()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(storeId: String): Store? =
        withContext(Dispatchers.IO) {
            q.getStoreById(storeId).executeAsOneOrNull()?.toDomain()
        }

    override suspend fun getStoreName(storeId: String): String? =
        withContext(Dispatchers.IO) {
            q.getStoreById(storeId).executeAsOneOrNull()?.name
        }

    override suspend fun upsertFromSync(store: Store) =
        withContext(Dispatchers.IO) {
            val existing = q.getStoreById(store.id).executeAsOneOrNull()
            if (existing != null) {
                q.updateStore(
                    name = store.name,
                    address = store.address,
                    phone = store.phone,
                    email = store.email,
                    currency = store.currency,
                    timezone = store.timezone,
                    is_active = if (store.isActive) 1L else 0L,
                    max_discount_percent = store.maxDiscountPercent,
                    max_discount_amount = store.maxDiscountAmount,
                    return_stock_policy = store.returnStockPolicy.name,
                    updated_at = store.updatedAt.toEpochMilliseconds(),
                    sync_status = "SYNCED",
                    id = store.id,
                )
            } else {
                q.insertStore(
                    id = store.id,
                    name = store.name,
                    address = store.address,
                    phone = store.phone,
                    email = store.email,
                    currency = store.currency,
                    timezone = store.timezone,
                    is_active = if (store.isActive) 1L else 0L,
                    is_headquarters = if (store.isHeadquarters) 1L else 0L,
                    max_discount_percent = store.maxDiscountPercent,
                    max_discount_amount = store.maxDiscountAmount,
                    return_stock_policy = store.returnStockPolicy.name,
                    created_at = store.createdAt.toEpochMilliseconds(),
                    updated_at = store.updatedAt.toEpochMilliseconds(),
                    sync_status = "SYNCED",
                )
            }
        }

    private fun com.zyntasolutions.zyntapos.db.Stores.toDomain(): Store = Store(
        id = id,
        name = name,
        address = address,
        phone = phone,
        email = email,
        currency = currency,
        timezone = timezone,
        isActive = is_active == 1L,
        isHeadquarters = is_headquarters == 1L,
        createdAt = Instant.fromEpochMilliseconds(created_at),
        updatedAt = Instant.fromEpochMilliseconds(updated_at),
        maxDiscountPercent = max_discount_percent,
        maxDiscountAmount = max_discount_amount,
        returnStockPolicy = try {
            ReturnStockPolicy.valueOf(return_stock_policy)
        } catch (_: IllegalArgumentException) {
            ReturnStockPolicy.RETURN_TO_CURRENT_STORE
        },
    )
}
