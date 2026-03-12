package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.db.AdminUsers
import com.zyntasolutions.zyntapos.api.models.AdminPagedResponse
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.UUID
import kotlin.math.ceil

// ── Exposed table objects ──────────────────────────────────────────────────

object SupportTickets : Table("support_tickets") {
    val id             = uuid("id")
    val ticketNumber   = text("ticket_number")
    val storeId        = text("store_id").nullable()
    val licenseId      = text("license_id").nullable()
    val createdBy      = uuid("created_by")
    val customerName   = text("customer_name")
    val customerEmail  = text("customer_email").nullable()
    val customerPhone  = text("customer_phone").nullable()
    val assignedTo     = uuid("assigned_to").nullable()
    val assignedAt     = long("assigned_at").nullable()
    val title          = text("title")
    val description    = text("description")
    val category       = text("category")
    val priority       = text("priority")
    val status         = text("status")
    val resolvedBy     = uuid("resolved_by").nullable()
    val resolvedAt     = long("resolved_at").nullable()
    val resolutionNote = text("resolution_note").nullable()
    val timeSpentMin   = integer("time_spent_min").nullable()
    val slaDueAt       = long("sla_due_at").nullable()
    val slaBreached    = bool("sla_breached")
    val createdAt      = long("created_at")
    val updatedAt      = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object TicketComments : Table("ticket_comments") {
    val id         = uuid("id")
    val ticketId   = uuid("ticket_id")
    val authorId   = uuid("author_id")
    val body       = text("body")
    val isInternal = bool("is_internal")
    val createdAt  = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

// ── Serializable response models ──────────────────────────────────────────

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

// ── Service ──────────────────────────────────────────────────────────────────

class AdminTicketService {

    /** SLA deadlines by priority (milliseconds). */
    private fun slaDeadlineMs(priority: String): Long {
        val now = Instant.now().toEpochMilli()
        return now + when (priority.uppercase()) {
            "CRITICAL" -> 4L  * 60 * 60 * 1000   //  4 hours
            "HIGH"     -> 24L * 60 * 60 * 1000   // 24 hours
            "MEDIUM"   -> 48L * 60 * 60 * 1000   // 48 hours
            else       -> 72L * 60 * 60 * 1000   // 72 hours (LOW)
        }
    }

    /** Generate ticket number like TKT-2026-000042. */
    private suspend fun nextTicketNumber(): String = newSuspendedTransaction {
        val seqVal = exec("SELECT nextval('ticket_seq')") { rs ->
            rs.next()
            rs.getLong(1)
        } ?: 1L
        val year = java.time.Year.now().value
        "TKT-$year-${seqVal.toString().padStart(6, '0')}"
    }

    private val validCategories = setOf("HARDWARE", "SOFTWARE", "SYNC", "BILLING", "OTHER")
    private val validPriorities  = setOf("LOW", "MEDIUM", "HIGH", "CRITICAL")
    private val validStatuses    = setOf("OPEN", "ASSIGNED", "IN_PROGRESS", "PENDING_CUSTOMER", "RESOLVED", "CLOSED")

    fun isValidCategory(v: String) = v.uppercase() in validCategories
    fun isValidPriority(v: String) = v.uppercase() in validPriorities

    /** Create a new support ticket. */
    suspend fun createTicket(req: CreateTicketRequest, createdBy: UUID): TicketResponse {
        val ticketNumber = nextTicketNumber()
        val now = Instant.now().toEpochMilli()
        val slaAt = slaDeadlineMs(req.priority)
        val newId = UUID.randomUUID()

        return newSuspendedTransaction {
            SupportTickets.insert {
                it[id]             = newId
                it[this.ticketNumber] = ticketNumber
                it[storeId]        = req.storeId
                it[licenseId]      = req.licenseId
                it[this.createdBy] = createdBy
                it[customerName]   = req.customerName
                it[customerEmail]  = req.customerEmail
                it[customerPhone]  = req.customerPhone
                it[assignedTo]     = null
                it[assignedAt]     = null
                it[title]          = req.title
                it[description]    = req.description
                it[category]       = req.category.uppercase()
                it[priority]       = req.priority.uppercase()
                it[status]         = "OPEN"
                it[resolvedBy]     = null
                it[resolvedAt]     = null
                it[resolutionNote] = null
                it[timeSpentMin]   = null
                it[slaDueAt]       = slaAt
                it[slaBreached]    = false
                it[createdAt]      = now
                it[updatedAt]      = now
            }

            val creatorName = AdminUsers.selectAll().where { AdminUsers.id eq createdBy }
                .singleOrNull()?.get(AdminUsers.name) ?: "Unknown"

            TicketResponse(
                id = newId.toString(), ticketNumber = ticketNumber,
                storeId = req.storeId, licenseId = req.licenseId,
                createdBy = createdBy.toString(), createdByName = creatorName,
                customerName = req.customerName, customerEmail = req.customerEmail,
                customerPhone = req.customerPhone,
                assignedTo = null, assignedToName = null, assignedAt = null,
                title = req.title, description = req.description,
                category = req.category.uppercase(), priority = req.priority.uppercase(),
                status = "OPEN",
                resolvedBy = null, resolvedAt = null, resolutionNote = null, timeSpentMin = null,
                slaDueAt = slaAt, slaBreached = false,
                createdAt = now, updatedAt = now,
            )
        }
    }

    /** List tickets with optional filters, paginated. */
    suspend fun listTickets(
        status: String?,
        priority: String?,
        category: String?,
        assignedTo: String?,
        storeId: String?,
        search: String?,
        page: Int,
        size: Int,
    ): AdminPagedResponse<TicketResponse> = newSuspendedTransaction {
        var query = SupportTickets.selectAll()

        status?.uppercase()?.let   { s -> query = query.andWhere { SupportTickets.status eq s } }
        priority?.uppercase()?.let { p -> query = query.andWhere { SupportTickets.priority eq p } }
        category?.uppercase()?.let { c -> query = query.andWhere { SupportTickets.category eq c } }
        storeId?.let               { s -> query = query.andWhere { SupportTickets.storeId eq s } }
        assignedTo?.let            { a ->
            val uuid = runCatching { UUID.fromString(a) }.getOrNull()
            if (uuid != null) query = query.andWhere { SupportTickets.assignedTo eq uuid }
        }
        search?.takeIf { it.isNotBlank() }?.let { q ->
            val like = "%${q.lowercase()}%"
            query = query.andWhere {
                SupportTickets.title.lowerCase() like like
            }
        }

        val total = query.count().toInt()
        val rows = query
            .orderBy(SupportTickets.createdAt, SortOrder.DESC)
            .limit(size).offset((page * size).toLong())
            .toList()

        val userIds = (rows.mapNotNull { it[SupportTickets.createdBy] } +
                       rows.mapNotNull { it[SupportTickets.assignedTo] } +
                       rows.mapNotNull { it[SupportTickets.resolvedBy] }).distinct()
        val userNames = if (userIds.isEmpty()) emptyMap() else
            AdminUsers.selectAll().where { AdminUsers.id inList userIds }
                .associate { it[AdminUsers.id] to it[AdminUsers.name] }

        val data = rows.map { row -> row.toTicketResponse(userNames) }
        AdminPagedResponse(
            data = data,
            page = page,
            size = size,
            total = total,
            totalPages = if (size > 0) ceil(total.toDouble() / size).toInt() else 0
        )
    }

    /** Get a single ticket with its comments. */
    suspend fun getTicket(id: String): TicketResponse? = newSuspendedTransaction {
        val ticketId = runCatching { UUID.fromString(id) }.getOrNull() ?: return@newSuspendedTransaction null
        val row = SupportTickets.selectAll().where { SupportTickets.id eq ticketId }.singleOrNull()
            ?: return@newSuspendedTransaction null

        val userIds = listOfNotNull(
            row[SupportTickets.createdBy],
            row[SupportTickets.assignedTo],
            row[SupportTickets.resolvedBy],
        ).distinct()
        val userNames = AdminUsers.selectAll().where { AdminUsers.id inList userIds }
            .associate { it[AdminUsers.id] to it[AdminUsers.name] }

        val comments = TicketComments
            .selectAll().where { TicketComments.ticketId eq ticketId }
            .orderBy(TicketComments.createdAt, SortOrder.ASC)
            .map { cr ->
                val authorId = cr[TicketComments.authorId]
                TicketCommentResponse(
                    id = cr[TicketComments.id].toString(),
                    ticketId = ticketId.toString(),
                    authorId = authorId.toString(),
                    authorName = userNames[authorId] ?: "Unknown",
                    body = cr[TicketComments.body],
                    isInternal = cr[TicketComments.isInternal],
                    createdAt = cr[TicketComments.createdAt],
                )
            }

        row.toTicketResponse(userNames, comments)
    }

    /** Update title/description/priority of a ticket. */
    suspend fun updateTicket(id: String, req: UpdateTicketRequest): TicketResponse? {
        val ticketId = runCatching { UUID.fromString(id) }.getOrNull() ?: return null
        val now = Instant.now().toEpochMilli()
        return newSuspendedTransaction {
            val updated = SupportTickets.update({ SupportTickets.id eq ticketId }) {
                req.title?.let       { v -> it[SupportTickets.title] = v }
                req.description?.let { v -> it[SupportTickets.description] = v }
                req.priority?.let    { v ->
                    it[SupportTickets.priority] = v.uppercase()
                    it[SupportTickets.slaDueAt] = slaDeadlineMs(v)
                }
                it[SupportTickets.updatedAt] = now
            }
            if (updated == 0) null else getTicket(id)
        }
    }

    /**
     * Assign a ticket to an operator.
     * Sets assigned_to + assigned_at + status→ASSIGNED (unless already further along).
     */
    suspend fun assignTicket(id: String, assigneeId: UUID): TicketResponse? {
        val ticketId = runCatching { UUID.fromString(id) }.getOrNull() ?: return null
        val now = Instant.now().toEpochMilli()
        return newSuspendedTransaction {
            val row = SupportTickets.selectAll().where { SupportTickets.id eq ticketId }.singleOrNull()
                ?: return@newSuspendedTransaction null
            val currentStatus = row[SupportTickets.status]
            val newStatus = if (currentStatus == "OPEN") "ASSIGNED" else currentStatus
            SupportTickets.update({ SupportTickets.id eq ticketId }) {
                it[SupportTickets.assignedTo] = assigneeId
                it[SupportTickets.assignedAt] = now
                it[SupportTickets.status]     = newStatus
                it[SupportTickets.updatedAt]  = now
            }
            getTicket(id)
        }
    }

    /**
     * Resolve a ticket (ADMIN/OPERATOR only).
     * Requires a resolutionNote and time_spent_min.
     * Returns null if ticket not found; throws IllegalStateException if status is wrong.
     */
    suspend fun resolveTicket(id: String, req: ResolveTicketRequest, resolvedBy: UUID): TicketResponse? {
        val ticketId = runCatching { UUID.fromString(id) }.getOrNull() ?: return null
        val now = Instant.now().toEpochMilli()
        return newSuspendedTransaction {
            val row = SupportTickets.selectAll().where { SupportTickets.id eq ticketId }.singleOrNull()
                ?: return@newSuspendedTransaction null
            val currentStatus = row[SupportTickets.status]
            if (currentStatus == "RESOLVED" || currentStatus == "CLOSED") {
                throw IllegalStateException("Ticket is already $currentStatus")
            }
            SupportTickets.update({ SupportTickets.id eq ticketId }) {
                it[SupportTickets.status]         = "RESOLVED"
                it[SupportTickets.resolvedBy]     = resolvedBy
                it[SupportTickets.resolvedAt]     = now
                it[SupportTickets.resolutionNote] = req.resolutionNote
                it[SupportTickets.timeSpentMin]   = req.timeSpentMin
                it[SupportTickets.updatedAt]      = now
            }
            getTicket(id)
        }
    }

    /**
     * Close a ticket (only allowed after RESOLVED).
     */
    suspend fun closeTicket(id: String): TicketResponse? {
        val ticketId = runCatching { UUID.fromString(id) }.getOrNull() ?: return null
        val now = Instant.now().toEpochMilli()
        return newSuspendedTransaction {
            val row = SupportTickets.selectAll().where { SupportTickets.id eq ticketId }.singleOrNull()
                ?: return@newSuspendedTransaction null
            if (row[SupportTickets.status] != "RESOLVED") {
                throw IllegalStateException("Ticket must be RESOLVED before it can be CLOSED")
            }
            SupportTickets.update({ SupportTickets.id eq ticketId }) {
                it[SupportTickets.status]    = "CLOSED"
                it[SupportTickets.updatedAt] = now
            }
            getTicket(id)
        }
    }

    /** Add a comment to a ticket. */
    suspend fun addComment(ticketId: String, req: AddCommentRequest, authorId: UUID): TicketCommentResponse? {
        val uuid = runCatching { UUID.fromString(ticketId) }.getOrNull() ?: return null
        val now = Instant.now().toEpochMilli()
        val newId = UUID.randomUUID()
        return newSuspendedTransaction {
            val ticketExists = SupportTickets.selectAll().where { SupportTickets.id eq uuid }.any()
            if (!ticketExists) return@newSuspendedTransaction null

            TicketComments.insert {
                it[id]              = newId
                it[TicketComments.ticketId]   = uuid
                it[TicketComments.authorId]   = authorId
                it[body]            = req.body
                it[isInternal]      = req.isInternal
                it[createdAt]       = now
            }

            val authorName = AdminUsers.selectAll().where { AdminUsers.id eq authorId }
                .singleOrNull()?.get(AdminUsers.name) ?: "Unknown"

            // Bump ticket updated_at
            SupportTickets.update({ SupportTickets.id eq uuid }) {
                it[SupportTickets.updatedAt] = now
            }

            TicketCommentResponse(
                id = newId.toString(), ticketId = ticketId,
                authorId = authorId.toString(), authorName = authorName,
                body = req.body, isInternal = req.isInternal, createdAt = now,
            )
        }
    }

    /** List comments for a ticket. */
    suspend fun listComments(ticketId: String): List<TicketCommentResponse>? {
        val uuid = runCatching { UUID.fromString(ticketId) }.getOrNull() ?: return null
        return newSuspendedTransaction {
            val ticketExists = SupportTickets.selectAll().where { SupportTickets.id eq uuid }.any()
            if (!ticketExists) return@newSuspendedTransaction null

            val authorIds = TicketComments.selectAll().where { TicketComments.ticketId eq uuid }
                .map { it[TicketComments.authorId] }.distinct()
            val userNames = if (authorIds.isEmpty()) emptyMap() else
                AdminUsers.selectAll().where { AdminUsers.id inList authorIds }
                    .associate { it[AdminUsers.id] to it[AdminUsers.name] }

            TicketComments.selectAll()
                .where { TicketComments.ticketId eq uuid }
                .orderBy(TicketComments.createdAt, SortOrder.ASC)
                .map { cr ->
                    val authorId = cr[TicketComments.authorId]
                    TicketCommentResponse(
                        id = cr[TicketComments.id].toString(),
                        ticketId = ticketId,
                        authorId = authorId.toString(),
                        authorName = userNames[authorId] ?: "Unknown",
                        body = cr[TicketComments.body],
                        isInternal = cr[TicketComments.isInternal],
                        createdAt = cr[TicketComments.createdAt],
                    )
                }
        }
    }

    /** Update sla_breached flag for all overdue tickets. Called by a scheduled job. */
    suspend fun checkSlaBreaches() = newSuspendedTransaction {
        val now = Instant.now().toEpochMilli()
        SupportTickets.update({
            (SupportTickets.slaDueAt lessEq now) and
            (SupportTickets.slaBreached eq false) and
            (SupportTickets.status neq "RESOLVED") and
            (SupportTickets.status neq "CLOSED")
        }) {
            it[SupportTickets.slaBreached] = true
        }
    }
}

// ── Mapping helper ────────────────────────────────────────────────────────

private fun ResultRow.toTicketResponse(
    userNames: Map<UUID, String>,
    comments: List<TicketCommentResponse> = emptyList(),
) = TicketResponse(
    id              = this[SupportTickets.id].toString(),
    ticketNumber    = this[SupportTickets.ticketNumber],
    storeId         = this[SupportTickets.storeId],
    licenseId       = this[SupportTickets.licenseId],
    createdBy       = this[SupportTickets.createdBy].toString(),
    createdByName   = userNames[this[SupportTickets.createdBy]] ?: "Unknown",
    customerName    = this[SupportTickets.customerName],
    customerEmail   = this[SupportTickets.customerEmail],
    customerPhone   = this[SupportTickets.customerPhone],
    assignedTo      = this[SupportTickets.assignedTo]?.toString(),
    assignedToName  = this[SupportTickets.assignedTo]?.let { userNames[it] },
    assignedAt      = this[SupportTickets.assignedAt],
    title           = this[SupportTickets.title],
    description     = this[SupportTickets.description],
    category        = this[SupportTickets.category],
    priority        = this[SupportTickets.priority],
    status          = this[SupportTickets.status],
    resolvedBy      = this[SupportTickets.resolvedBy]?.toString(),
    resolvedAt      = this[SupportTickets.resolvedAt],
    resolutionNote  = this[SupportTickets.resolutionNote],
    timeSpentMin    = this[SupportTickets.timeSpentMin],
    slaDueAt        = this[SupportTickets.slaDueAt],
    slaBreached     = this[SupportTickets.slaBreached],
    createdAt       = this[SupportTickets.createdAt],
    updatedAt       = this[SupportTickets.updatedAt],
    comments        = comments,
)
