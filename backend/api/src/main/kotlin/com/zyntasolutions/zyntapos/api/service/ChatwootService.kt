package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Chatwoot REST API client — creates conversations from inbound emails (TODO-008a).
 *
 * When an inbound email arrives at support@, billing@, or bugs@zyntapos.com,
 * the CF Worker POSTs the parsed payload to `/internal/email/inbound`.
 * [InboundEmailProcessor] calls this service to create or update a Chatwoot
 * conversation so HELPDESK agents can see and respond to the email.
 *
 * ## Configuration
 * - `CHATWOOT_API_URL`     — internal URL (e.g. `http://chatwoot-web:3000`)
 * - `CHATWOOT_API_TOKEN`   — agent/administrator access token from Chatwoot profile settings
 * - `CHATWOOT_ACCOUNT_ID`  — account ID shown in Chatwoot dashboard URL
 * - `CHATWOOT_INBOX_ID`    — inbox ID for the "Support" email inbox
 *
 * All values are blank by default — if any are blank, calls are skipped silently.
 */
class ChatwootService(private val config: AppConfig) {

    private val log = LoggerFactory.getLogger(ChatwootService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
    }

    // ── Chatwoot API Models ────────────────────────────────────────────────────

    @Serializable
    private data class CreateContactRequest(
        val name: String,
        val email: String,
        @SerialName("inbox_id") val inboxId: Int,
    )

    @Serializable
    private data class ContactResponse(val id: Int, val email: String? = null)

    @Serializable
    private data class CreateConversationRequest(
        @SerialName("inbox_id") val inboxId: Int,
        @SerialName("contact_id") val contactId: Int,
        @SerialName("additional_attributes") val additionalAttributes: Map<String, String> = emptyMap(),
    )

    @Serializable
    private data class ConversationResponse(val id: Int)

    @Serializable
    private data class CreateMessageRequest(
        val content: String,
        @SerialName("message_type") val messageType: String = "incoming",
        val private: Boolean = false,
    )

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Creates (or finds) a Chatwoot contact for [fromEmail] and opens a new
     * conversation in the support inbox with the email content as the first message.
     *
     * Returns the Chatwoot conversation ID, or `null` if Chatwoot is not configured
     * or the API call fails.
     */
    suspend fun createConversation(
        fromEmail: String,
        fromName: String?,
        subject: String,
        bodyText: String?,
        bodyHtml: String?,
    ): Int? {
        if (!isConfigured()) {
            log.debug("Chatwoot not configured — skipping conversation creation")
            return null
        }

        return runCatching {
            val inboxId = config.chatwootInboxId.toIntOrNull() ?: run {
                log.warn("CHATWOOT_INBOX_ID is not a valid integer: '${config.chatwootInboxId}'")
                return@runCatching null
            }

            // Step 1: Create or find contact
            val contactId = upsertContact(fromEmail, fromName ?: fromEmail, inboxId) ?: return@runCatching null

            // Step 2: Create conversation
            val conversationId = createConversation(contactId, inboxId, subject) ?: return@runCatching null

            // Step 3: Post email body as first message
            val messageContent = buildMessageContent(subject, bodyText, bodyHtml)
            postMessage(conversationId, messageContent)

            conversationId
        }.getOrElse { e ->
            log.error("Chatwoot API error: ${e.message}", e)
            null
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun isConfigured(): Boolean =
        config.chatwootApiUrl.isNotBlank() &&
            config.chatwootApiToken.isNotBlank() &&
            config.chatwootAccountId.isNotBlank() &&
            config.chatwootInboxId.isNotBlank()

    private suspend fun upsertContact(email: String, name: String, inboxId: Int): Int? {
        val url = "${config.chatwootApiUrl}/api/v1/accounts/${config.chatwootAccountId}/contacts"
        val response = client.post(url) {
            header("api_access_token", config.chatwootApiToken)
            contentType(ContentType.Application.Json)
            setBody(CreateContactRequest(name = name, email = email, inboxId = inboxId))
        }
        if (response.status != HttpStatusCode.OK && response.status.value !in 200..299) {
            log.warn("Chatwoot create-contact returned ${response.status}: ${response.bodyAsText()}")
            return null
        }
        return json.decodeFromString<ContactResponse>(response.bodyAsText()).id
    }

    private suspend fun createConversation(contactId: Int, inboxId: Int, subject: String): Int? {
        val url = "${config.chatwootApiUrl}/api/v1/accounts/${config.chatwootAccountId}/conversations"
        val response = client.post(url) {
            header("api_access_token", config.chatwootApiToken)
            contentType(ContentType.Application.Json)
            setBody(
                CreateConversationRequest(
                    inboxId = inboxId,
                    contactId = contactId,
                    additionalAttributes = mapOf("email_subject" to subject),
                )
            )
        }
        if (response.status != HttpStatusCode.OK && response.status.value !in 200..299) {
            log.warn("Chatwoot create-conversation returned ${response.status}: ${response.bodyAsText()}")
            return null
        }
        return json.decodeFromString<ConversationResponse>(response.bodyAsText()).id
    }

    private suspend fun postMessage(conversationId: Int, content: String) {
        val url = "${config.chatwootApiUrl}/api/v1/accounts/${config.chatwootAccountId}/conversations/$conversationId/messages"
        val response = client.post(url) {
            header("api_access_token", config.chatwootApiToken)
            contentType(ContentType.Application.Json)
            setBody(CreateMessageRequest(content = content))
        }
        if (response.status != HttpStatusCode.OK && response.status.value !in 200..299) {
            log.warn("Chatwoot post-message returned ${response.status}: ${response.bodyAsText()}")
        }
    }

    private fun buildMessageContent(subject: String, bodyText: String?, bodyHtml: String?): String {
        val body = bodyText?.take(4000) ?: bodyHtml?.take(4000) ?: "(no body)"
        return "**Subject:** $subject\n\n$body"
    }

    fun close() = client.close()
}
