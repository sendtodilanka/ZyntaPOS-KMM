package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.db.EmailDeliveryLogs
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Background job that retries FAILED email deliveries with exponential backoff.
 *
 * Retry schedule: attempt 1 at +2min, attempt 2 at +8min, attempt 3 at +32min.
 * After 3 failed retries the email stays FAILED permanently.
 *
 * Runs every 60 seconds, picking up emails whose [next_retry_at] has passed.
 */
class EmailRetryJob(private val config: AppConfig) {

    private val log = LoggerFactory.getLogger(EmailRetryJob::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val MAX_RETRIES = 3
        private val BACKOFF_SECONDS = longArrayOf(120, 480, 1920) // 2m, 8m, 32m
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    @Serializable
    private data class ResendEmailRequest(
        val from: String,
        val to: List<String>,
        val subject: String,
        val html: String,
    )

    fun start(intervalSeconds: Long = 60L) {
        scope.launch {
            log.info("EmailRetryJob started (interval: ${intervalSeconds}s, maxRetries: $MAX_RETRIES)")
            while (true) {
                try {
                    processRetries()
                } catch (e: Exception) {
                    log.warn("EmailRetryJob error: ${e.message}")
                }
                delay(intervalSeconds * 1_000L)
            }
        }
    }

    private suspend fun processRetries() {
        if (config.resendApiKey.isBlank()) return

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val retryable = newSuspendedTransaction {
            EmailDeliveryLogs.selectAll()
                .where {
                    (EmailDeliveryLogs.status eq "FAILED") and
                    (EmailDeliveryLogs.retryCount less MAX_RETRIES) and
                    (EmailDeliveryLogs.nextRetryAt lessEq now) and
                    EmailDeliveryLogs.htmlBody.isNotNull()
                }
                .limit(10) // batch size per cycle
                .map { row ->
                    RetryCandidate(
                        id = row[EmailDeliveryLogs.id],
                        to = row[EmailDeliveryLogs.toAddress],
                        from = row[EmailDeliveryLogs.fromAddress],
                        subject = row[EmailDeliveryLogs.subject],
                        htmlBody = row[EmailDeliveryLogs.htmlBody]!!,
                        retryCount = row[EmailDeliveryLogs.retryCount],
                    )
                }
        }

        if (retryable.isEmpty()) return
        log.info("EmailRetryJob: retrying ${retryable.size} failed email(s)")

        for (candidate in retryable) {
            retryEmail(candidate)
        }
    }

    private suspend fun retryEmail(candidate: RetryCandidate) {
        val result = runCatching {
            val response = client.post("https://api.resend.com/emails") {
                bearerAuth(config.resendApiKey)
                contentType(ContentType.Application.Json)
                setBody(
                    ResendEmailRequest(
                        from = candidate.from,
                        to = listOf(candidate.to),
                        subject = candidate.subject,
                        html = candidate.htmlBody,
                    )
                )
            }
            if (response.status != HttpStatusCode.OK && response.status.value !in 200..299) {
                val body = response.bodyAsText()
                throw RuntimeException("Resend API ${response.status}: $body")
            }
        }

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val newRetryCount = candidate.retryCount + 1

        newSuspendedTransaction {
            if (result.isSuccess) {
                EmailDeliveryLogs.update({ EmailDeliveryLogs.id eq candidate.id }) {
                    it[status] = "SENT"
                    it[sentAt] = now
                    it[errorMessage] = null
                    it[retryCount] = newRetryCount
                    it[nextRetryAt] = null
                }
                log.info("EmailRetryJob: retry succeeded for ${candidate.to} (attempt $newRetryCount)")
            } else {
                val nextRetry = if (newRetryCount < MAX_RETRIES) {
                    now.plusSeconds(BACKOFF_SECONDS[newRetryCount.coerceAtMost(BACKOFF_SECONDS.size - 1)])
                } else null

                EmailDeliveryLogs.update({ EmailDeliveryLogs.id eq candidate.id }) {
                    it[retryCount] = newRetryCount
                    it[errorMessage] = result.exceptionOrNull()?.message
                    it[nextRetryAt] = nextRetry
                }
                log.warn("EmailRetryJob: retry $newRetryCount/$MAX_RETRIES failed for ${candidate.to}: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    private data class RetryCandidate(
        val id: java.util.UUID,
        val to: String,
        val from: String,
        val subject: String,
        val htmlBody: String,
        val retryCount: Int,
    )

    fun close() {
        client.close()
    }
}
