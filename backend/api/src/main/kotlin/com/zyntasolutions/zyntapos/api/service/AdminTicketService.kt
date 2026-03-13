package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.models.AdminPagedResponse
import com.zyntasolutions.zyntapos.api.repository.AdminTicketRepository
import com.zyntasolutions.zyntapos.api.repository.CommentCreateInput
import com.zyntasolutions.zyntapos.api.repository.TicketCommentRepository
import com.zyntasolutions.zyntapos.api.repository.TicketCreateInput
import com.zyntasolutions.zyntapos.api.repository.TicketFilter
import com.zyntasolutions.zyntapos.api.repository.TicketPatch
import com.zyntasolutions.zyntapos.api.repository.TicketRow
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

// ── Serializable request / response models ────────────────────────────────────
// These are wire types (HTTP request / response); kept in service package
// because they are tightly coupled to the HTTP API contract.

@Serializable
data class TicketResponse(
    val id: String,
    val ticketNumber: String,
    val storeId: String?,
    val licenseId: String?,
    val createdBy: String,
    val createdByName: String,
    val customerName: String,
    val customerEmail: String?,
    val customerPhone: String?,
    val assignedTo: String?,
    val assignedToName: String?,
    val assignedAt: Long?,
    val title: String,
    val description: String,
    val category: String,
    val priority: String,
    val status: String,
    val resolvedBy: String?,
    val resolvedAt: Long?,
    val resolutionNote: String?,
    val timeSpentMin: Int?,
    val slaDueAt: Long?,
    val slaBreached: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val comments: List<TicketCommentResponse> = emptyList(),
)

@Serializable
data class TicketCommentResponse(
    val id: String,
    val ticketId: String,
    val authorId: String,
    val authorName: String,
    val body: String,
    val isInternal: Boolean,
    val createdAt: Long,
)

@Serializable
data class CreateTicketRequest(
    val storeId: String? = null,
    val licenseId: String? = null,
    val customerName: String,
    val customerEmail: String? = null,
    val customerPhone: String? = null,
    val title: String,
    val description: String,
    val category: String,
    val priority: String,
)

@Serializable
data class UpdateTicketRequest(
    val title: String? = null,
    val description: String? = null,
    val priority: String? = null,
)

@Serializable
data class AssignTicketRequest(val assigneeId: String)

@Serializable
data class ResolveTicketRequest(
    val resolutionNote: String,
    val timeSpentMin: Int,
)

@Serializable
data class AddCommentRequest(
    val body: String,
    val isInternal: Boolean = false,
)

// ── Service ───────────────────────────────────────────────────────────────────

/**
 * Ticket business logic (S3-15).
 *
 * Responsibilities:
 * - SLA deadline calculation
 * - State-machine enforcement (OPEN → ASSIGNED → RESOLVED → CLOSED)
 * - Ticket number generation (delegated to repository for sequence call)
 * - Mapping repository rows to HTTP response types
 *
 * No [org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction] calls here.
 */
class AdminTicketService(
    private val ticketRepo: AdminTicketRepository,
    private val commentRepo: TicketCommentRepository,
) {

    private val validCategories = setOf("HARDWARE", "SOFTWARE", "SYNC", "BILLING", "OTHER")
    private val validPriorities  = setOf("LOW", "MEDIUM", "HIGH", "CRITICAL")

    fun isValidCategory(v: String) = v.uppercase() in validCategories
    fun isValidPriority(v: String) = v.uppercase() in validPriorities

    /** SLA deadlines by priority (milliseconds from now). */
    private fun slaDeadlineMs(priority: String): Long {
        val now = Instant.now().toEpochMilli()
        return now + when (priority.uppercase()) {
            "CRITICAL" -> 4L  * 60 * 60 * 1000   //  4 hours
            "HIGH"     -> 24L * 60 * 60 * 1000   // 24 hours
            "MEDIUM"   -> 48L * 60 * 60 * 1000   // 48 hours
            else       -> 72L * 60 * 60 * 1000   // 72 hours (LOW)
        }
    }

    suspend fun createTicket(req: CreateTicketRequest, createdBy: UUID): TicketResponse {
        val now  = Instant.now().toEpochMilli()
        val year = java.time.Year.now().value
        val ticketNumber = ticketRepo.nextTicketNumber(year)
        val newId = UUID.randomUUID()

        val row = ticketRepo.create(
            TicketCreateInput(
                id           = newId,
                ticketNumber = ticketNumber,
                storeId      = req.storeId,
                licenseId    = req.licenseId,
                createdBy    = createdBy,
                customerName = req.customerName,
                customerEmail = req.customerEmail,
                customerPhone = req.customerPhone,
                title        = req.title,
                description  = req.description,
                category     = req.category.uppercase(),
                priority     = req.priority.uppercase(),
                slaDueAt     = slaDeadlineMs(req.priority),
                createdAt    = now,
            )
        )
        val userNames = ticketRepo.findUserNames(listOf(createdBy))
        return row.toResponse(userNames)
    }

    suspend fun listTickets(
        status:     String?,
        priority:   String?,
        category:   String?,
        assignedTo: String?,
        storeId:    String?,
        search:     String?,
        page:       Int,
        size:       Int,
    ): AdminPagedResponse<TicketResponse> {
        val page_ = ticketRepo.list(
            filter = TicketFilter(status, priority, category, assignedTo, storeId, search),
            page   = page,
            size   = size,
        )
        val allUserIds = page_.data.flatMap {
            listOfNotNull(it.createdBy, it.assignedTo, it.resolvedBy)
        }.distinct()
        val userNames = ticketRepo.findUserNames(allUserIds)
        return AdminPagedResponse(
            data       = page_.data.map { it.toResponse(userNames) },
            page       = page_.page,
            size       = page_.size,
            total      = page_.total,
            totalPages = page_.totalPages,
        )
    }

    suspend fun getTicket(id: String): TicketResponse? {
        val ticketId = runCatching { UUID.fromString(id) }.getOrNull() ?: return null
        val row = ticketRepo.findById(ticketId) ?: return null

        val userIds = listOfNotNull(row.createdBy, row.assignedTo, row.resolvedBy)
        val userNames = ticketRepo.findUserNames(userIds)

        val comments = commentRepo.listForTicket(ticketId)
        val authorIds = comments.map { it.authorId }.distinct()
        val authorNames = commentRepo.findAuthorNames(authorIds)

        val commentResponses = comments.map { c ->
            TicketCommentResponse(
                id         = c.id.toString(),
                ticketId   = id,
                authorId   = c.authorId.toString(),
                authorName = authorNames[c.authorId] ?: "Unknown",
                body       = c.body,
                isInternal = c.isInternal,
                createdAt  = c.createdAt,
            )
        }
        return row.toResponse(userNames, commentResponses)
    }

    suspend fun updateTicket(id: String, req: UpdateTicketRequest): TicketResponse? {
        val ticketId = runCatching { UUID.fromString(id) }.getOrNull() ?: return null
        val now = Instant.now().toEpochMilli()
        val slaDueAt = req.priority?.let { slaDeadlineMs(it) }
        val updated = ticketRepo.update(ticketId, TicketPatch(req.title, req.description, req.priority, slaDueAt, now))
        return if (updated) getTicket(id) else null
    }

    suspend fun assignTicket(id: String, assigneeId: UUID): TicketResponse? {
        val ticketId = runCatching { UUID.fromString(id) }.getOrNull() ?: return null
        val now = Instant.now().toEpochMilli()
        val existing = ticketRepo.findById(ticketId) ?: return null
        val newStatus = if (existing.status == "OPEN") "ASSIGNED" else existing.status
        ticketRepo.assignTo(ticketId, assigneeId, newStatus, now)
        return getTicket(id)
    }

    suspend fun resolveTicket(id: String, req: ResolveTicketRequest, resolvedBy: UUID): TicketResponse? {
        val ticketId = runCatching { UUID.fromString(id) }.getOrNull() ?: return null
        val existing = ticketRepo.findById(ticketId) ?: return null
        if (existing.status == "RESOLVED" || existing.status == "CLOSED") {
            throw IllegalStateException("Ticket is already ${existing.status}")
        }
        val now = Instant.now().toEpochMilli()
        ticketRepo.updateStatus(
            id             = ticketId,
            newStatus      = "RESOLVED",
            resolvedBy     = resolvedBy,
            resolvedAt     = now,
            resolutionNote = req.resolutionNote,
            timeSpentMin   = req.timeSpentMin,
            updatedAt      = now,
        )
        return getTicket(id)
    }

    suspend fun closeTicket(id: String): TicketResponse? {
        val ticketId = runCatching { UUID.fromString(id) }.getOrNull() ?: return null
        val existing = ticketRepo.findById(ticketId) ?: return null
        if (existing.status != "RESOLVED") {
            throw IllegalStateException("Ticket must be RESOLVED before it can be CLOSED")
        }
        val now = Instant.now().toEpochMilli()
        ticketRepo.updateStatus(
            id             = ticketId,
            newStatus      = "CLOSED",
            resolvedBy     = null,
            resolvedAt     = null,
            resolutionNote = null,
            timeSpentMin   = null,
            updatedAt      = now,
        )
        return getTicket(id)
    }

    suspend fun addComment(ticketId: String, req: AddCommentRequest, authorId: UUID): TicketCommentResponse? {
        val uuid = runCatching { UUID.fromString(ticketId) }.getOrNull() ?: return null
        val exists = ticketRepo.findById(uuid) != null
        if (!exists) return null

        val now = Instant.now().toEpochMilli()
        val newId = UUID.randomUUID()
        val row = commentRepo.add(
            CommentCreateInput(
                id         = newId,
                ticketId   = uuid,
                authorId   = authorId,
                body       = req.body,
                isInternal = req.isInternal,
                createdAt  = now,
            )
        )
        // Bump ticket updated_at
        ticketRepo.update(uuid, TicketPatch(null, null, null, null, now))

        val authorName = commentRepo.findAuthorNames(listOf(authorId))[authorId] ?: "Unknown"
        return TicketCommentResponse(
            id         = row.id.toString(),
            ticketId   = ticketId,
            authorId   = authorId.toString(),
            authorName = authorName,
            body       = row.body,
            isInternal = row.isInternal,
            createdAt  = row.createdAt,
        )
    }

    suspend fun listComments(ticketId: String): List<TicketCommentResponse>? {
        val uuid = runCatching { UUID.fromString(ticketId) }.getOrNull() ?: return null
        if (ticketRepo.findById(uuid) == null) return null

        val comments = commentRepo.listForTicket(uuid)
        val authorIds = comments.map { it.authorId }.distinct()
        val authorNames = commentRepo.findAuthorNames(authorIds)

        return comments.map { c ->
            TicketCommentResponse(
                id         = c.id.toString(),
                ticketId   = ticketId,
                authorId   = c.authorId.toString(),
                authorName = authorNames[c.authorId] ?: "Unknown",
                body       = c.body,
                isInternal = c.isInternal,
                createdAt  = c.createdAt,
            )
        }
    }

    suspend fun checkSlaBreaches() {
        ticketRepo.checkSlaBreaches(Instant.now().toEpochMilli())
    }

    // ── Mapping helper ────────────────────────────────────────────────────────

    private fun TicketRow.toResponse(
        userNames: Map<UUID, String>,
        comments:  List<TicketCommentResponse> = emptyList(),
    ) = TicketResponse(
        id              = id.toString(),
        ticketNumber    = ticketNumber,
        storeId         = storeId,
        licenseId       = licenseId,
        createdBy       = createdBy.toString(),
        createdByName   = userNames[createdBy] ?: "Unknown",
        customerName    = customerName,
        customerEmail   = customerEmail,
        customerPhone   = customerPhone,
        assignedTo      = assignedTo?.toString(),
        assignedToName  = assignedTo?.let { userNames[it] },
        assignedAt      = assignedAt,
        title           = title,
        description     = description,
        category        = category,
        priority        = priority,
        status          = status,
        resolvedBy      = resolvedBy?.toString(),
        resolvedAt      = resolvedAt,
        resolutionNote  = resolutionNote,
        timeSpentMin    = timeSpentMin,
        slaDueAt        = slaDueAt,
        slaBreached     = slaBreached,
        createdAt       = createdAt,
        updatedAt       = updatedAt,
        comments        = comments,
    )
}
