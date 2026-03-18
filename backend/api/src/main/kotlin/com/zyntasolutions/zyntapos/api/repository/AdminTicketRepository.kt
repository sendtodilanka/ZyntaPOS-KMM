package com.zyntasolutions.zyntapos.api.repository

import java.util.UUID

/**
 * Data access interface for the `support_tickets` table (S3-15).
 *
 * SLA deadline logic and state-machine validation remain in
 * [com.zyntasolutions.zyntapos.api.service.AdminTicketService].
 */
interface AdminTicketRepository {

    suspend fun create(input: TicketCreateInput): TicketRow
    suspend fun findById(id: UUID): TicketRow?
    suspend fun list(filter: TicketFilter, page: Int, size: Int): TicketPage
    suspend fun update(id: UUID, patch: TicketPatch): Boolean
    suspend fun updateStatus(id: UUID, newStatus: String, resolvedBy: UUID?, resolvedAt: Long?,
                             resolutionNote: String?, timeSpentMin: Int?, updatedAt: Long): Boolean
    suspend fun assignTo(id: UUID, assigneeId: UUID, newStatus: String, now: Long): Boolean
    suspend fun checkSlaBreaches(now: Long): Int

    /** Finds tickets that were breached within the last [windowMs] milliseconds (for email alerts). */
    suspend fun findRecentlyBreached(now: Long, windowMs: Long): List<TicketRow>

    /** Runs the `SELECT nextval('ticket_seq')` sequence call. */
    suspend fun nextTicketNumber(year: Int): String

    /** Lookup admin_users names for a set of UUIDs in a single query. */
    suspend fun findUserNames(ids: List<UUID>): Map<UUID, String>

    /** Lookup admin_users emails for a set of UUIDs in a single query. */
    suspend fun findUserEmails(ids: List<UUID>): Map<UUID, String>

    /** Find ticket by customer access token (public, no auth required). */
    suspend fun findByCustomerToken(token: UUID): TicketRow?

    /** Aggregate ticket metrics for dashboard. */
    suspend fun getMetrics(): TicketMetricsData
}

data class TicketMetricsData(
    val totalOpen: Int,
    val totalAssigned: Int,
    val totalResolved: Int,
    val totalClosed: Int,
    val slaBreached: Int,
    val avgResolutionTimeMin: Int,
    val openByPriority: Map<String, Int>,
    val openByCategory: Map<String, Int>,
)

// ── Row / filter types ────────────────────────────────────────────────────────

data class TicketRow(
    val id:             UUID,
    val ticketNumber:   String,
    val storeId:        String?,
    val licenseId:      String?,
    val createdBy:      UUID,
    val customerName:   String,
    val customerEmail:  String?,
    val customerPhone:  String?,
    val assignedTo:     UUID?,
    val assignedAt:     Long?,
    val title:          String,
    val description:    String,
    val category:       String,
    val priority:       String,
    val status:         String,
    val resolvedBy:     UUID?,
    val resolvedAt:     Long?,
    val resolutionNote: String?,
    val timeSpentMin:   Int?,
    val slaDueAt:       Long?,
    val slaBreached:    Boolean,
    val customerAccessToken: UUID,
    val createdAt:      Long,
    val updatedAt:      Long,
)

data class TicketCreateInput(
    val id:           UUID,
    val ticketNumber: String,
    val storeId:      String?,
    val licenseId:    String?,
    val createdBy:    UUID,
    val customerName: String,
    val customerEmail: String?,
    val customerPhone: String?,
    val title:        String,
    val description:  String,
    val category:     String,
    val priority:     String,
    val slaDueAt:     Long,
    val createdAt:    Long,
)

data class TicketPatch(
    val title:       String?,
    val description: String?,
    val priority:    String?,
    val slaDueAt:    Long?,
    val updatedAt:   Long,
)

data class TicketFilter(
    val status:        String?,
    val priority:      String?,
    val category:      String?,
    val assignedTo:    String?,
    val storeId:       String?,
    val search:        String?,
    val searchBody:    Boolean = false,
    val createdAfter:  Long? = null,
    val createdBefore: Long? = null,
)

data class TicketPage(
    val data:       List<TicketRow>,
    val page:       Int,
    val size:       Int,
    val total:      Int,
    val totalPages: Int,
)
