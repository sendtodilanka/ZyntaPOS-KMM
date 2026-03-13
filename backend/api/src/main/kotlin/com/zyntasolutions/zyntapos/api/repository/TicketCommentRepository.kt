package com.zyntasolutions.zyntapos.api.repository

import java.util.UUID

/**
 * Data access interface for the `ticket_comments` table (S3-15).
 */
interface TicketCommentRepository {

    suspend fun add(input: CommentCreateInput): CommentRow
    suspend fun listForTicket(ticketId: UUID): List<CommentRow>
    suspend fun existsTicket(ticketId: UUID): Boolean
    suspend fun findAuthorNames(ids: List<UUID>): Map<UUID, String>
}

// ── Row types ─────────────────────────────────────────────────────────────────

data class CommentCreateInput(
    val id:         UUID,
    val ticketId:   UUID,
    val authorId:   UUID,
    val body:       String,
    val isInternal: Boolean,
    val createdAt:  Long,
)

data class CommentRow(
    val id:         UUID,
    val ticketId:   UUID,
    val authorId:   UUID,
    val body:       String,
    val isInternal: Boolean,
    val createdAt:  Long,
)
