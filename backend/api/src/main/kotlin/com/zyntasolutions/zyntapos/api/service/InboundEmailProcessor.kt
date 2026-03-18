package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.db.EmailThreads
import com.zyntasolutions.zyntapos.api.repository.AdminTicketRepository
import com.zyntasolutions.zyntapos.api.repository.TicketRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Processes inbound emails delivered by the CF Email Worker (TODO-008a).
 *
 * ## Flow
 * 1. [InboundEmailRoutes] receives `POST /internal/email/inbound` with HMAC-SHA256 signature.
 * 2. [verifySignature] validates the HMAC to ensure the request is from the CF Worker.
 * 3. [process] deduplicates by `messageId`, creates a `support_ticket` if none exists
 *    for this thread (or links to an existing one via `inReplyTo`), inserts an
 *    `email_threads` row, and calls [ChatwootService] to notify HELPDESK agents.
 *
 * ## HMAC verification
 * `Authorization: HMAC-SHA256 <base64-signature>`
 * Signature = HMAC-SHA256(raw JSON body, INBOUND_EMAIL_HMAC_SECRET)
 */
class InboundEmailProcessor(
    private val config: AppConfig,
    private val ticketRepo: AdminTicketRepository,
    private val emailService: EmailService,
    private val chatwootService: ChatwootService,
    private val ticketService: AdminTicketService,
) {
    private val log = LoggerFactory.getLogger(InboundEmailProcessor::class.java)

    // ── HMAC signature verification ────────────────────────────────────────────

    /**
     * Returns `true` if the HMAC-SHA256 signature in [authHeader] matches
     * the HMAC of [rawBody] with the configured secret.
     *
     * Always returns `true` if INBOUND_EMAIL_HMAC_SECRET is blank (dev mode).
     */
    fun verifySignature(authHeader: String?, rawBody: String): Boolean {
        if (config.inboundEmailHmacSecret.isBlank()) {
            log.warn("INBOUND_EMAIL_HMAC_SECRET not configured — skipping HMAC verification (dev mode)")
            return true
        }
        val expectedPrefix = "HMAC-SHA256 "
        if (authHeader == null || !authHeader.startsWith(expectedPrefix)) return false
        val receivedSig = authHeader.removePrefix(expectedPrefix)
        val computed = hmacSha256(rawBody, config.inboundEmailHmacSecret)
        // Constant-time comparison to prevent timing attacks
        return MessageDigest.isEqual(
            computed.toByteArray(Charsets.UTF_8),
            receivedSig.toByteArray(Charsets.UTF_8),
        )
    }

    // ── Main processing ────────────────────────────────────────────────────────

    /**
     * Persists the inbound email and triggers ticket + Chatwoot conversation creation.
     * Idempotent — duplicate messageIds are silently dropped.
     */
    suspend fun process(payload: InboundEmailPayload) = withContext(Dispatchers.IO) {
        // 1. Dedup by messageId
        if (payload.messageId != null && emailThreadExists(payload.messageId)) {
            log.info("Duplicate inbound email ignored — messageId=${payload.messageId}")
            return@withContext
        }

        // 2. Find or create ticket
        val ticket = resolveTicket(payload)

        // 3. Resolve parent thread for reply chain tracking (V21)
        val parentThreadId: UUID? = payload.inReplyTo?.let { replyTo ->
            newSuspendedTransaction {
                EmailThreads.selectAll()
                    .where { EmailThreads.messageId eq replyTo }
                    .firstOrNull()
                    ?.get(EmailThreads.id)
            }
        }

        // 4. Persist email thread row
        val threadId = UUID.randomUUID()
        newSuspendedTransaction {
            EmailThreads.insert {
                it[id]          = threadId
                it[ticketId]    = ticket.id
                it[messageId]   = payload.messageId
                it[inReplyTo]   = payload.inReplyTo
                it[emailReferences]  = payload.references
                it[fromAddress] = payload.fromAddress
                it[fromName]    = payload.fromName
                it[toAddress]   = payload.toAddress
                it[subject]     = payload.subject
                it[bodyText]    = payload.bodyText?.take(65_535)
                it[bodyHtml]    = payload.bodyHtml?.take(65_535)
                it[EmailThreads.parentThreadId] = parentThreadId
                it[receivedAt]  = OffsetDateTime.parse(payload.receivedAt)
                it[createdAt]   = OffsetDateTime.now(ZoneOffset.UTC)
            }
        }
        log.info("Inbound email stored — threadId=$threadId ticketId=${ticket.id} from=${payload.fromAddress}")

        // 5. Create Chatwoot conversation (best-effort)
        runCatching {
            val conversationId = chatwootService.createConversation(
                fromEmail = payload.fromAddress,
                fromName  = payload.fromName,
                subject   = payload.subject,
                bodyText  = payload.bodyText,
                bodyHtml  = payload.bodyHtml,
            )
            if (conversationId != null) {
                newSuspendedTransaction {
                    EmailThreads.update({ EmailThreads.id eq threadId }) {
                        it[chatwootConversationId] = conversationId
                    }
                }
            }
        }.onFailure { e -> log.warn("Chatwoot conversation creation failed: ${e.message}") }

        // 6. Auto-reply to sender (best-effort)
        runCatching {
            emailService.sendTicketCreated(
                toEmail              = payload.fromAddress,
                ticketNumber         = ticket.ticketNumber,
                title                = ticket.title,
                customerAccessToken  = ticket.customerAccessToken.toString(),
            )
        }.onFailure { e -> log.warn("Auto-reply email failed: ${e.message}") }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private suspend fun emailThreadExists(messageId: String): Boolean =
        newSuspendedTransaction {
            EmailThreads.selectAll().where { EmailThreads.messageId eq messageId }.count() > 0
        }

    private suspend fun resolveTicket(payload: InboundEmailPayload): TicketRow {
        // Try to link to existing ticket via In-Reply-To thread
        val existingTicketId: UUID? = newSuspendedTransaction {
            payload.inReplyTo?.let { replyTo ->
                EmailThreads
                    .selectAll().where { EmailThreads.messageId eq replyTo }
                    .limit(1)
                    .firstOrNull()
                    ?.get(EmailThreads.ticketId)
            }
        }

        if (existingTicketId != null) {
            val existing = ticketRepo.findById(existingTicketId)
            if (existing != null) {
                log.info("Linked inbound email to existing ticket #${existing.ticketNumber}")
                return existing
            }
        }

        // No existing thread — create via AdminTicketService (single source of truth for SLA)
        val systemCreatorId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val priority = inferPriorityFromEmail(payload.subject, payload.bodyText)

        val request = CreateTicketRequest(
            customerName  = payload.fromName ?: payload.fromAddress,
            customerEmail = payload.fromAddress,
            title         = payload.subject.take(200),
            description   = buildTicketDescription(payload),
            category      = categoriseByInbox(payload.toAddress),
            priority      = priority,
        )
        val response = ticketService.createTicket(request, systemCreatorId)
        val ticketId = UUID.fromString(response.id)
        val ticket = ticketRepo.findById(ticketId)
            ?: error("Ticket created but not found: ${response.id}")
        log.info("Created ticket #${ticket.ticketNumber} (priority=$priority) from inbound email — from=${payload.fromAddress}")
        return ticket
    }

    /**
     * Keyword-based priority inference for inbound emails.
     * Returns CRITICAL/HIGH/MEDIUM/LOW based on subject + body content.
     */
    private fun inferPriorityFromEmail(subject: String, body: String?): String {
        val text = "$subject ${body ?: ""}".lowercase()
        return when {
            text.containsAny("urgent", "critical", "down", "broken", "emergency", "outage") -> "CRITICAL"
            text.containsAny("billing", "payment", "invoice", "security", "breach", "locked") -> "HIGH"
            text.containsAny("feature", "suggestion", "how to", "question", "training") -> "LOW"
            else -> "MEDIUM"
        }
    }

    private fun String.containsAny(vararg keywords: String) = keywords.any { this.contains(it) }

    private fun buildTicketDescription(payload: InboundEmailPayload): String {
        val body = payload.bodyText?.take(2000) ?: payload.bodyHtml?.take(2000) ?: "(no body)"
        return "Inbound email from ${payload.fromAddress}\nTo: ${payload.toAddress}\n\n$body"
    }

    private fun categoriseByInbox(toAddress: String): String = when {
        toAddress.startsWith("billing") -> "BILLING"
        toAddress.startsWith("bugs")    -> "BUG_REPORT"
        toAddress.startsWith("alerts")  -> "ALERT"
        else                            -> "GENERAL"
    }

    private fun hmacSha256(data: String, secret: String): String {
        val key = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256").apply { init(key) }
        return Base64.getEncoder().encodeToString(mac.doFinal(data.toByteArray(Charsets.UTF_8)))
    }
}

// ── Payload model (mirrors CF Worker JSON) ────────────────────────────────────

@Serializable
data class InboundEmailPayload(
    val messageId:   String?,
    val inReplyTo:   String?,
    val references:  String?,
    val fromAddress: String,
    val fromName:    String?,
    val toAddress:   String,
    val subject:     String,
    val bodyText:    String?,
    val bodyHtml:    String?,
    val receivedAt:  String,
)
