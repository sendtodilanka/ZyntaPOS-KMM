package com.zyntasolutions.zyntapos.sync.hub

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Forwards sync push/pull messages from WebSocket clients to the API service's
 * REST endpoints. This keeps the sync service stateless (no direct DB access)
 * while allowing POS devices to push operations over WebSocket.
 */
class SyncForwarder(private val apiBaseUrl: String) {
    private val logger = LoggerFactory.getLogger(SyncForwarder::class.java)

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    /**
     * Forwards a sync push message body to POST /v1/sync/push on the API service.
     * The [rawMessage] is the JSON envelope from the WebSocket; we extract the
     * inner payload and forward it.
     * Returns the API response as a JSON string to send back to the client.
     */
    suspend fun forwardPush(rawMessage: String, bearerToken: String?): String {
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val parsed = json.parseToJsonElement(rawMessage).let {
                it as? kotlinx.serialization.json.JsonObject
            }
            // Extract the inner push payload from the WS envelope
            val payload = parsed?.get("payload")?.toString() ?: rawMessage

            val response = client.post("$apiBaseUrl/v1/sync/push") {
                contentType(ContentType.Application.Json)
                if (bearerToken != null) bearerAuth(bearerToken)
                setBody(payload)
            }
            val body = response.bodyAsText()
            """{"type":"sync_push_response","status":${response.status.value},"data":$body}"""
        } catch (e: Exception) {
            logger.warn("SyncForwarder push failed: ${e.message}")
            """{"type":"sync_push_response","status":502,"error":"${e.message?.replace("\"", "'")}"}"""
        }
    }

    /**
     * Forwards a sync pull request to GET /v1/sync/pull on the API service.
     * Returns the API response as a JSON string to send back to the client.
     */
    suspend fun forwardPull(
        storeId: String,
        deviceId: String,
        since: Long,
        limit: Int,
        bearerToken: String?,
    ): String {
        return try {
            val response = client.get("$apiBaseUrl/v1/sync/pull") {
                if (bearerToken != null) bearerAuth(bearerToken)
                parameter("deviceId", deviceId)
                parameter("since", since)
                parameter("limit", limit)
            }
            val body = response.bodyAsText()
            """{"type":"sync_pull_response","status":${response.status.value},"data":$body}"""
        } catch (e: Exception) {
            logger.warn("SyncForwarder pull failed: ${e.message}")
            """{"type":"sync_pull_response","status":502,"error":"${e.message?.replace("\"", "'")}"}"""
        }
    }

    fun close() {
        client.close()
    }
}
