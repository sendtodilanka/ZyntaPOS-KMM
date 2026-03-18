package com.zyntasolutions.zyntapos.api.repository

import java.util.UUID

/**
 * Data access interface for the `email_threads` table.
 * Surfaces inbound email threads linked to support tickets.
 */
data class EmailThreadRow(
    val id: UUID,
    val ticketId: UUID?,
    val messageId: String?,
    val inReplyTo: String?,
    val parentThreadId: UUID?,
    val fromAddress: String,
    val fromName: String?,
    val toAddress: String,
    val subject: String,
    val bodyText: String?,
    val receivedAt: String,
    val createdAt: String,
)

interface EmailThreadRepository {
    suspend fun findByTicketId(ticketId: UUID): List<EmailThreadRow>
    suspend fun findParentIdByMessageId(messageId: String): UUID?
}
