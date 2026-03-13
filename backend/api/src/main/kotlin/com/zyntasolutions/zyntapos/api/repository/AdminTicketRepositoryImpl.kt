package com.zyntasolutions.zyntapos.api.repository

import com.zyntasolutions.zyntapos.api.db.AdminUsers
import com.zyntasolutions.zyntapos.api.db.SupportTickets
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID
import kotlin.math.ceil

class AdminTicketRepositoryImpl : AdminTicketRepository {

    override suspend fun create(input: TicketCreateInput): TicketRow = newSuspendedTransaction {
        SupportTickets.insert {
            it[id]             = input.id
            it[ticketNumber]   = input.ticketNumber
            it[storeId]        = input.storeId
            it[licenseId]      = input.licenseId
            it[createdBy]      = input.createdBy
            it[customerName]   = input.customerName
            it[customerEmail]  = input.customerEmail
            it[customerPhone]  = input.customerPhone
            it[assignedTo]     = null
            it[assignedAt]     = null
            it[title]          = input.title
            it[description]    = input.description
            it[category]       = input.category
            it[priority]       = input.priority
            it[status]         = "OPEN"
            it[resolvedBy]     = null
            it[resolvedAt]     = null
            it[resolutionNote] = null
            it[timeSpentMin]   = null
            it[slaDueAt]       = input.slaDueAt
            it[slaBreached]    = false
            it[createdAt]      = input.createdAt
            it[updatedAt]      = input.createdAt
        }
        // Re-read to return a complete row
        SupportTickets.selectAll()
            .where { SupportTickets.id eq input.id }
            .single()
            .toRow()
    }

    override suspend fun findById(id: UUID): TicketRow? = newSuspendedTransaction {
        SupportTickets.selectAll().where { SupportTickets.id eq id }.singleOrNull()?.toRow()
    }

    override suspend fun list(filter: TicketFilter, page: Int, size: Int): TicketPage =
        newSuspendedTransaction {
            var query = SupportTickets.selectAll()
            filter.status?.uppercase()?.let   { s -> query = query.andWhere { SupportTickets.status eq s } }
            filter.priority?.uppercase()?.let { p -> query = query.andWhere { SupportTickets.priority eq p } }
            filter.category?.uppercase()?.let { c -> query = query.andWhere { SupportTickets.category eq c } }
            filter.storeId?.let               { s -> query = query.andWhere { SupportTickets.storeId eq s } }
            filter.assignedTo?.let            { a ->
                runCatching { UUID.fromString(a) }.getOrNull()?.let { uuid ->
                    query = query.andWhere { SupportTickets.assignedTo eq uuid }
                }
            }
            filter.search?.takeIf { it.isNotBlank() }?.let { q ->
                query = query.andWhere { SupportTickets.title.lowerCase() like "%${q.lowercase()}%" }
            }

            val total = query.count().toInt()
            val rows = query
                .orderBy(SupportTickets.createdAt, SortOrder.DESC)
                .limit(size).offset((page * size).toLong())
                .map { it.toRow() }

            TicketPage(
                data       = rows,
                page       = page,
                size       = size,
                total      = total,
                totalPages = if (size > 0) ceil(total.toDouble() / size).toInt() else 0,
            )
        }

    override suspend fun update(id: UUID, patch: TicketPatch): Boolean = newSuspendedTransaction {
        SupportTickets.update({ SupportTickets.id eq id }) {
            patch.title?.let       { v -> it[SupportTickets.title] = v }
            patch.description?.let { v -> it[SupportTickets.description] = v }
            patch.priority?.let    { v -> it[SupportTickets.priority] = v.uppercase() }
            patch.slaDueAt?.let    { v -> it[SupportTickets.slaDueAt] = v }
            it[SupportTickets.updatedAt] = patch.updatedAt
        } > 0
    }

    override suspend fun updateStatus(
        id:             UUID,
        newStatus:      String,
        resolvedBy:     UUID?,
        resolvedAt:     Long?,
        resolutionNote: String?,
        timeSpentMin:   Int?,
        updatedAt:      Long,
    ): Boolean = newSuspendedTransaction {
        SupportTickets.update({ SupportTickets.id eq id }) {
            it[SupportTickets.status]         = newStatus
            it[SupportTickets.resolvedBy]     = resolvedBy
            it[SupportTickets.resolvedAt]     = resolvedAt
            it[SupportTickets.resolutionNote] = resolutionNote
            it[SupportTickets.timeSpentMin]   = timeSpentMin
            it[SupportTickets.updatedAt]      = updatedAt
        } > 0
    }

    override suspend fun assignTo(
        id:        UUID,
        assigneeId: UUID,
        newStatus:  String,
        now:        Long,
    ): Boolean = newSuspendedTransaction {
        SupportTickets.update({ SupportTickets.id eq id }) {
            it[SupportTickets.assignedTo]  = assigneeId
            it[SupportTickets.assignedAt]  = now
            it[SupportTickets.status]      = newStatus
            it[SupportTickets.updatedAt]   = now
        } > 0
    }

    override suspend fun checkSlaBreaches(now: Long): Int = newSuspendedTransaction {
        SupportTickets.update({
            (SupportTickets.slaDueAt lessEq now) and
            (SupportTickets.slaBreached eq false) and
            (SupportTickets.status neq "RESOLVED") and
            (SupportTickets.status neq "CLOSED")
        }) {
            it[SupportTickets.slaBreached] = true
        }
    }

    override suspend fun nextTicketNumber(year: Int): String = newSuspendedTransaction {
        val seqVal = exec("SELECT nextval('ticket_seq')") { rs ->
            rs.next(); rs.getLong(1)
        } ?: 1L
        "TKT-$year-${seqVal.toString().padStart(6, '0')}"
    }

    override suspend fun findUserNames(ids: List<UUID>): Map<UUID, String> =
        newSuspendedTransaction {
            if (ids.isEmpty()) emptyMap()
            else AdminUsers.selectAll()
                .where { AdminUsers.id inList ids }
                .associate { it[AdminUsers.id] to it[AdminUsers.name] }
        }

    // ── Mapping helper ────────────────────────────────────────────────────────

    private fun ResultRow.toRow() = TicketRow(
        id             = this[SupportTickets.id],
        ticketNumber   = this[SupportTickets.ticketNumber],
        storeId        = this[SupportTickets.storeId],
        licenseId      = this[SupportTickets.licenseId],
        createdBy      = this[SupportTickets.createdBy],
        customerName   = this[SupportTickets.customerName],
        customerEmail  = this[SupportTickets.customerEmail],
        customerPhone  = this[SupportTickets.customerPhone],
        assignedTo     = this[SupportTickets.assignedTo],
        assignedAt     = this[SupportTickets.assignedAt],
        title          = this[SupportTickets.title],
        description    = this[SupportTickets.description],
        category       = this[SupportTickets.category],
        priority       = this[SupportTickets.priority],
        status         = this[SupportTickets.status],
        resolvedBy     = this[SupportTickets.resolvedBy],
        resolvedAt     = this[SupportTickets.resolvedAt],
        resolutionNote = this[SupportTickets.resolutionNote],
        timeSpentMin   = this[SupportTickets.timeSpentMin],
        slaDueAt       = this[SupportTickets.slaDueAt],
        slaBreached    = this[SupportTickets.slaBreached],
        createdAt      = this[SupportTickets.createdAt],
        updatedAt      = this[SupportTickets.updatedAt],
    )
}
