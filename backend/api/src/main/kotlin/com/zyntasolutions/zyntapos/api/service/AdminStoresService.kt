package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.models.*
import com.zyntasolutions.zyntapos.api.repository.AdminStoresRepository
import com.zyntasolutions.zyntapos.api.repository.StoreAdminRow
import com.zyntasolutions.zyntapos.api.repository.StoreConfigPatch

/**
 * Store admin business logic (S3-15).
 *
 * Responsibilities:
 * - Health-score calculation (thresholds: >50 pending ops → WARNING)
 * - Mapping repository rows to API response types
 *
 * No [org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction] calls here.
 */
class AdminStoresService(
    private val storesRepo: AdminStoresRepository,
) {

    suspend fun listStores(
        page:   Int,
        size:   Int,
        search: String?,
        status: String?,
    ): AdminPagedResponse<AdminStore> {
        // status filter maps to isActive flag (simplified)
        val isActive: Boolean? = when (status?.uppercase()) {
            "OFFLINE" -> false
            null      -> null
            else      -> true
        }
        val result = storesRepo.list(search, isActive, page, size)
        return AdminPagedResponse(
            data       = result.data.map { it.toAdminStore() },
            page       = result.page,
            size       = result.size,
            total      = result.total,
            totalPages = result.totalPages,
        )
    }

    suspend fun getStore(storeId: String): AdminStore? =
        storesRepo.findById(storeId)?.toAdminStore()

    suspend fun getStoreHealth(storeId: String): AdminStoreHealth? {
        val store = storesRepo.findById(storeId) ?: return null
        val pendingOps = storesRepo.countPendingOps(storeId)
        val status = healthStatus(store.isActive, pendingOps)
        return AdminStoreHealth(
            storeId        = storeId,
            status         = status,
            healthScore    = healthScore(status),
            dbSizeBytes    = 0L,
            syncQueueDepth = pendingOps,
            errorCount24h  = 0,
            uptimeHours    = 0.0,
            lastHeartbeatAt = null,
            responseTimeMs = 0L,
            appVersion     = "",
            osInfo         = "Android",
        )
    }

    suspend fun updateStoreConfig(storeId: String, req: AdminUpdateStoreConfigRequest): AdminStore? {
        val updated = storesRepo.update(storeId, StoreConfigPatch(req.timezone, req.currency))
        return updated?.toAdminStore()
    }

    suspend fun getAllStoreHealthSummaries(): List<StoreHealthSummary> {
        return storesRepo.listAllWithPendingOps().map { (store, pendingOps) ->
            StoreHealthSummary(
                storeId           = store.id,
                storeName         = store.name,
                status            = healthStatus(store.isActive, pendingOps).lowercase(),
                lastSync          = null,
                pendingOperations = pendingOps,
                appVersion        = "",
                androidVersion    = "",
                uptimePercent     = if (store.isActive) 99.0 else 0.0,
            )
        }
    }

    suspend fun getStoreHealthDetail(storeId: String): StoreHealthDetail? {
        val store = storesRepo.findById(storeId) ?: return null
        val pendingOps = storesRepo.countPendingOps(storeId)
        return StoreHealthDetail(
            storeId           = storeId,
            storeName         = store.name,
            status            = healthStatus(store.isActive, pendingOps).lowercase(),
            lastSync          = null,
            pendingOperations = pendingOps,
            appVersion        = "",
            androidVersion    = "",
            uptimePercent     = if (store.isActive) 99.0 else 0.0,
        )
    }

    // ── Business logic helpers ────────────────────────────────────────────────

    private fun healthStatus(isActive: Boolean, pendingOps: Int) = when {
        !isActive       -> "OFFLINE"
        pendingOps > 50 -> "WARNING"
        else            -> "HEALTHY"
    }

    private fun healthScore(status: String) = when (status) {
        "HEALTHY" -> 100
        "WARNING" -> 60
        else      -> 0
    }

    private fun StoreAdminRow.toAdminStore() = AdminStore(
        id              = id,
        name            = name,
        location        = timezone,
        licenseKey      = licenseKey,
        edition         = "STANDARD",
        status          = if (isActive) "HEALTHY" else "OFFLINE",
        activeUsers     = 0,
        lastSyncAt      = null,
        lastHeartbeatAt = null,
        appVersion      = "",
        createdAt       = createdAt,
    )
}
