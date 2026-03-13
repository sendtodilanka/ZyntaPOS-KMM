package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.models.AdminAuditEntry
import com.zyntasolutions.zyntapos.api.models.AdminPagedResponse
import com.zyntasolutions.zyntapos.api.repository.AdminAuditRepository
import com.zyntasolutions.zyntapos.api.repository.AuditEntryInput
import com.zyntasolutions.zyntapos.api.repository.AuditFilter
import kotlinx.serialization.json.Json
import org.slf4j.MDC
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Audit-log business logic (S3-15).
 *
 * Responsibilities:
 * - Hash-chain computation (tamper-evident log integrity)
 * - Mapping domain parameters to [AuditEntryInput]
 * - Delegating all SQL to [AdminAuditRepository]
 *
 * No [org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction] calls here.
 */
open class AdminAuditService(
    private val auditRepo: AdminAuditRepository,
) {

    /** Write a new audit entry — call after every admin mutation. */
    open suspend fun log(
        adminId:        UUID?,
        adminName:      String?,
        eventType:      String,
        category:       String,
        entityType:     String? = null,
        entityId:       String? = null,
        previousValues: Map<String, String>? = null,
        newValues:      Map<String, String>? = null,
        ipAddress:      String? = null,
        userAgent:      String? = null,
        success:        Boolean = true,
        errorMessage:   String? = null,
    ) {
        // D6: Populate MDC so all log lines within this audit write carry adminId context.
        MDC.put("adminId", adminId?.toString())
        try {
            val now      = OffsetDateTime.now(ZoneOffset.UTC)
            val newId    = UUID.randomUUID()
            val lastHash = auditRepo.findLatestHash()
            val chainInput = "$lastHash|$eventType|${entityId ?: ""}|${now.toInstant().toEpochMilli()}"
            val hashChain  = sha256Hex(chainInput)

            val prevJson = previousValues?.let { m ->
                "{${m.entries.joinToString(",") { (k, v) -> "\"$k\":\"$v\"" }}}"
            }
            val newJson = newValues?.let { m ->
                "{${m.entries.joinToString(",") { (k, v) -> "\"$k\":\"$v\"" }}}"
            }

            auditRepo.insertEntry(
                AuditEntryInput(
                    id             = newId,
                    eventType      = eventType,
                    category       = category,
                    adminId        = adminId,
                    adminName      = adminName,
                    storeId        = null,
                    storeName      = null,
                    entityType     = entityType,
                    entityId       = entityId,
                    previousValues = prevJson,
                    newValues      = newJson,
                    ipAddress      = ipAddress,
                    userAgent      = userAgent,
                    success        = success,
                    errorMessage   = errorMessage,
                    hashChain      = hashChain,
                    createdAt      = now,
                )
            )
        } finally {
            // D6: Clear MDC to avoid context leakage across coroutine thread boundaries.
            MDC.remove("adminId")
        }
    }

    suspend fun listEntries(
        page:      Int,
        size:      Int,
        category:  String?,
        eventType: String?,
        adminId:   String?,
        from:      String?,
        to:        String?,
        search:    String?,
    ): AdminPagedResponse<AdminAuditEntry> {
        val result = auditRepo.listEntries(
            filter = AuditFilter(category, eventType, adminId, from, to, search),
            page   = page,
            size   = size,
        )
        return AdminPagedResponse(
            data       = result.data.map { it.toModel() },
            page       = result.page,
            size       = result.size,
            total      = result.total,
            totalPages = result.totalPages,
        )
    }

    suspend fun exportEntries(
        category:  String?,
        eventType: String?,
        from:      String?,
        to:        String?,
        limit:     Int = 10_000,
    ): List<AdminAuditEntry> {
        return auditRepo.exportEntries(
            filter = AuditFilter(category, eventType, null, from, to, null),
            limit  = limit,
        ).map { it.toModel() }
    }

    // ── Business logic helpers ────────────────────────────────────────────────

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun com.zyntasolutions.zyntapos.api.repository.AuditEntryRow.toModel() = AdminAuditEntry(
        id             = id,
        eventType      = eventType,
        category       = category,
        userId         = userId,
        userName       = userName,
        storeId        = storeId,
        storeName      = storeName,
        entityType     = entityType,
        entityId       = entityId,
        previousValues = previousValues?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() },
        newValues      = newValues?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() },
        ipAddress      = ipAddress,
        userAgent      = userAgent,
        success        = success,
        errorMessage   = errorMessage,
        hashChain      = hashChain,
        createdAt      = createdAt,
    )
}
