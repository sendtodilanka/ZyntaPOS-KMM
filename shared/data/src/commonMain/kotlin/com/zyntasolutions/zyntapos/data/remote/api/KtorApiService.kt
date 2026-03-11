package com.zyntasolutions.zyntapos.data.remote.api

import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import com.zyntasolutions.zyntapos.core.config.AppConfig
import com.zyntasolutions.zyntapos.core.result.AuthException
import com.zyntasolutions.zyntapos.core.result.AuthFailureReason
import com.zyntasolutions.zyntapos.core.result.NetworkException
import com.zyntasolutions.zyntapos.core.result.SyncException
import com.zyntasolutions.zyntapos.core.result.ZyntaException
import com.zyntasolutions.zyntapos.data.remote.dto.AuthRefreshRequestDto
import com.zyntasolutions.zyntapos.data.remote.dto.AuthRefreshResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.AuthRequestDto
import com.zyntasolutions.zyntapos.data.remote.dto.AuthResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.ProductDto
import com.zyntasolutions.zyntapos.data.remote.dto.PublicKeyResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.SyncOperationDto
import com.zyntasolutions.zyntapos.data.remote.dto.SyncPullResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.SyncResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.LicenseActivateRequestDto
import com.zyntasolutions.zyntapos.data.remote.dto.LicenseActivateResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.LicenseHeartbeatRequestDto
import com.zyntasolutions.zyntapos.data.remote.dto.LicenseHeartbeatResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * ZyntaPOS — Ktor-backed implementation of [ApiService].
 *
 * All HTTP errors are mapped to the [ZyntaException] hierarchy so callers
 * (SyncEngine, AuthRepositoryImpl) never interact with raw HTTP status codes.
 *
 * Error mapping:
 * - 401 → [ZyntaException.AuthException]
 * - 4xx → [ZyntaException.NetworkException] (client error)
 * - 5xx → [ZyntaException.NetworkException] (server error)
 * - IOException / timeout → [ZyntaException.NetworkException]
 *
 * @param client Ktor [HttpClient] configured via [buildApiClient].
 * @param baseUrl Base URL of the ZyntaPOS API server (defaults to [AppConfig.BASE_URL]).
 */
class KtorApiService(
    private val client: HttpClient,
    private val baseUrl: String = AppConfig.BASE_URL,
) : ApiService {

    private val log = ZyntaLogger.forModule("KtorApiService")
    private val apiRoot = "$baseUrl/api/${AppConfig.API_VERSION}"

    // ── Auth ──────────────────────────────────────────────────────────────────

    override suspend fun login(request: AuthRequestDto): AuthResponseDto =
        safeRequest {
            client.post("$apiRoot/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    override suspend fun refreshToken(refreshToken: String): AuthRefreshResponseDto =
        safeRequest {
            client.post("$apiRoot/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(AuthRefreshRequestDto(refreshToken = refreshToken))
            }
        }

    // ── Products ──────────────────────────────────────────────────────────────

    override suspend fun getProducts(): List<ProductDto> =
        safeRequest {
            client.get("$apiRoot/products")
        }

    // ── Sync ──────────────────────────────────────────────────────────────────

    override suspend fun pushOperations(operations: List<SyncOperationDto>): SyncResponseDto =
        safeRequest {
            client.post("$apiRoot/sync/push") {
                contentType(ContentType.Application.Json)
                setBody(operations)
            }
        }

    override suspend fun pullOperations(lastSyncTimestamp: Long): SyncPullResponseDto =
        safeRequest {
            client.get("$apiRoot/sync/pull") {
                // "since" is the server_seq cursor returned by prior pull (TODO-007g)
                parameter("since", lastSyncTimestamp)
                parameter("limit", 50)
            }
        }

    // ── Well-known ────────────────────────────────────────────────────────────

    override suspend fun fetchPublicKey(): PublicKeyResponseDto =
        safeRequest {
            client.get("$baseUrl/.well-known/public-key")
        }

    // ─────────────────────────────────────────────────────────────────────────
    // ── Private helpers ───────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Executes an HTTP request, maps non-2xx responses and transport exceptions
     * to the appropriate [ZyntaException] subclass, and deserializes the body.
     */
    @Suppress("ThrowsCount") // multiple throw paths are required for correct HTTP error mapping
    private suspend inline fun <reified T> safeRequest(
        crossinline block: suspend () -> HttpResponse,
    ): T {
        return try {
            val response = block()
            if (response.status.isSuccess()) {
                response.body<T>()
            } else {
                log.e("HTTP ${response.status.value} on ${response.call.request.url}")
                throw mapHttpError(response)
            }
        } catch (e: ZyntaException) {
            throw e          // re-throw domain exceptions untouched
        } catch (e: Exception) {
            log.e("Network transport error", throwable = e)
            throw NetworkException(
                message = "Network error: ${e.message}",
                cause   = e,
            )
        }
    }

    private fun mapHttpError(response: HttpResponse): ZyntaException =
        when (response.status) {
            HttpStatusCode.Unauthorized ->
                AuthException(
                    message = "Unauthorized — token invalid or expired (401)",
                    reason  = AuthFailureReason.SESSION_EXPIRED,
                )
            HttpStatusCode.UnprocessableEntity ->
                SyncException(
                    message = "Sync batch rejected by server (422)",
                )
            else ->
                NetworkException(
                    message    = "HTTP ${response.status.value}: ${response.status.description}",
                    statusCode = response.status.value,
                )
        }
}
