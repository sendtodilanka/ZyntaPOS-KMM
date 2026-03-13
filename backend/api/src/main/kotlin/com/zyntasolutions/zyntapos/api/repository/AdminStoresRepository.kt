package com.zyntasolutions.zyntapos.api.repository

/**
 * Data access interface for the `stores` and `sync_queue` tables
 * as accessed by admin store management features (S3-15).
 *
 * Health-score business logic remains in
 * [com.zyntasolutions.zyntapos.api.service.AdminStoresService].
 */
interface AdminStoresRepository {

    suspend fun list(search: String?, isActive: Boolean?, page: Int, size: Int): StoreAdminPage

    suspend fun findById(storeId: String): StoreAdminRow?

    suspend fun update(storeId: String, patch: StoreConfigPatch): StoreAdminRow?

    /** Count of unprocessed sync_queue entries for [storeId]. */
    suspend fun countPendingOps(storeId: String): Int

    /** All stores with their pending-op counts, for bulk health checks. */
    suspend fun listAllWithPendingOps(): List<Pair<StoreAdminRow, Int>>

    /** Store name map for a given set of IDs (used by metrics / alerts). */
    suspend fun findStoreNames(ids: List<String>): Map<String, String>
}

// ── Row / filter types ────────────────────────────────────────────────────────

data class StoreAdminRow(
    val id:        String,
    val name:      String,
    val licenseKey: String,
    val timezone:  String,
    val currency:  String,
    val isActive:  Boolean,
    val createdAt: String,    // ISO-8601 instant string
    val updatedAt: String,
)

data class StoreConfigPatch(
    val timezone: String?,
    val currency: String?,
)

data class StoreAdminPage(
    val data:       List<StoreAdminRow>,
    val page:       Int,
    val size:       Int,
    val total:      Int,
    val totalPages: Int,
)
