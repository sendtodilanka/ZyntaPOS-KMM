package com.zyntasolutions.zyntapos.api.repository

import java.time.OffsetDateTime
import java.util.UUID

/**
 * Data access interface for the `admin_audit_log` table (S3-15).
 *
 * Separates tamper-evident storage from the hash-chain business logic
 * in [com.zyntasolutions.zyntapos.api.service.AdminAuditService].
 */
interface AdminAuditRepository {

    /**
     * Insert a new audit entry.
     * The [hashChain] value must be pre-computed by the caller before invoking this method.
     */
    suspend fun insertEntry(entry: AuditEntryInput)

    /** Return the hash_chain value of the most recently created entry, or empty string if none. */
    suspend fun findLatestHash(): String

    /** Paginated query — all filter fields are optional. */
    suspend fun listEntries(filter: AuditFilter, page: Int, size: Int): AuditPage

    /** Unbounded export for CSV/PDF — caller limits via [limit]. */
    suspend fun exportEntries(filter: AuditFilter, limit: Int): List<AuditEntryRow>
}

// ── Input / output row types ──────────────────────────────────────────────────

data class AuditEntryInput(
    val id:             UUID,
    val eventType:      String,
    val category:       String,
    val adminId:        UUID?,
    val adminName:      String?,
    val storeId:        String?,
    val storeName:      String?,
    val entityType:     String?,
    val entityId:       String?,
    val previousValues: String?,   // raw JSON string
    val newValues:      String?,   // raw JSON string
    val ipAddress:      String?,
    val userAgent:      String?,
    val success:        Boolean,
    val errorMessage:   String?,
    val hashChain:      String,
    val createdAt:      OffsetDateTime,
)

data class AuditEntryRow(
    val id:             String,
    val eventType:      String,
    val category:       String,
    val userId:         String?,
    val userName:       String?,
    val storeId:        String?,
    val storeName:      String?,
    val entityType:     String?,
    val entityId:       String?,
    val previousValues: String?,
    val newValues:      String?,
    val ipAddress:      String?,
    val userAgent:      String?,
    val success:        Boolean,
    val errorMessage:   String?,
    val hashChain:      String,
    val createdAt:      String,    // ISO-8601 instant string
)

data class AuditFilter(
    val category:  String?,
    val eventType: String?,
    val adminId:   String?,
    val from:      String?,
    val to:        String?,
    val search:    String?,
)

data class AuditPage(
    val data:       List<AuditEntryRow>,
    val page:       Int,
    val size:       Int,
    val total:      Int,
    val totalPages: Int,
)
