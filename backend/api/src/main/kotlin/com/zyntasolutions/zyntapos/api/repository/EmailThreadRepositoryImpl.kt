package com.zyntasolutions.zyntapos.api.repository

import com.zyntasolutions.zyntapos.api.db.EmailThreads
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

class EmailThreadRepositoryImpl : EmailThreadRepository {

    override suspend fun findByTicketId(ticketId: UUID): List<EmailThreadRow> =
        newSuspendedTransaction {
            EmailThreads.selectAll()
                .where { EmailThreads.ticketId eq ticketId }
                .orderBy(EmailThreads.receivedAt, SortOrder.ASC)
                .map { it.toRow() }
        }

    override suspend fun findParentIdByMessageId(messageId: String): UUID? =
        newSuspendedTransaction {
            EmailThreads.selectAll()
                .where { EmailThreads.messageId eq messageId }
                .firstOrNull()
                ?.get(EmailThreads.id)
        }

    private fun ResultRow.toRow() = EmailThreadRow(
        id             = this[EmailThreads.id],
        ticketId       = this[EmailThreads.ticketId],
        messageId      = this[EmailThreads.messageId],
        inReplyTo      = this[EmailThreads.inReplyTo],
        parentThreadId = this[EmailThreads.parentThreadId],
        fromAddress    = this[EmailThreads.fromAddress],
        fromName       = this[EmailThreads.fromName],
        toAddress      = this[EmailThreads.toAddress],
        subject        = this[EmailThreads.subject],
        bodyText       = this[EmailThreads.bodyText],
        receivedAt     = this[EmailThreads.receivedAt].toString(),
        createdAt      = this[EmailThreads.createdAt].toString(),
    )
}
