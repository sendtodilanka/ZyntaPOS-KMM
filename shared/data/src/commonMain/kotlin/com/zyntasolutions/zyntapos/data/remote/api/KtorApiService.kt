package com.zyntasolutions.zyntapos.data.remote.api

import co.touchlab.kermit.Logger
import com.zyntasolutions.zyntapos.core.config.AppConfig
import com.zyntasolutions.zyntapos.core.result.AuthException
import com.zyntasolutions.zyntapos.core.result.AuthFailureReason
import com.zyntasolutions.zyntapos.core.result.NetworkException
import com.zyntasolutions.zyntapos.core.result.SyncException
import com.zyntasolutions.zyntapos.core.result.ZentaException
import com.zyntasolutions.zyntapos.data.remote.dto.AuthRefreshRequestDto
import com.zyntasolutions.zyntapos.data.remote.dto.AuthRefreshResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.AuthRequestDto
import com.zyntasolutions.zyntapos.data.remote.dto.AuthResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.ProductDto
import com.zyntasolutions.zyntapos.data.remote.dto.SyncOperationDto
import com.zyntasolutions.zyntapos.data.remote.dto.SyncPullResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.SyncResponseDto
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
 * ZentaPOS — Ktor-backed implementation of [ApiService].
 *
 * All HTTP errors are mapped to the [ZentaException] hierarchy so callers
 * (SyncEngine, AuthRepositoryImpl) never interact with raw HTTP status codes.
 *
 * Error mapping:
 * - 401 → [ZentaException.AuthException]
 * - 4xx → [ZentaException.NetworkException] (client error)
 * - 5xx → [ZentaException.NetworkException] (server error)
 * - IOException / timeout → [ZentaException.NetworkException]
 *
 * @param client Ktor [HttpClient] configured via [buildApiClient].
 * @param baseUrl Base URL of the ZentaPOS API server (defaults to [AppConfig.BASE_URL]).
 */
class KtorApiService(
    private val client: HttpClient,
    private val baseUrl: String = AppConfig.BASE_URL,
) : ApiService {

    private val log = Logger.withTag("KtorApiService")
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
                parameter("last_sync_ts", lastSyncTimestamp)
            }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // ── Private helpers ───────────────────────────────────────────────────────
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Executes an HTTP request, maps non-2xx responses and transport exceptions
     * to the appropriate [ZentaException] subclass, and deserializes the body.
     */
    private suspend inline fun <reified T> safeRequest(
        crossinline block: suspend () -> HttpResponse,
    ): T {
        return try {
            val response = block()
            if (response.status.isSuccess()) {
                response.body<T>()
            } else {
                log.e { "HTTP ${response.status.value} on ${response.call.request.url}" }
                when (response.status) {
                    HttpStatusCode.Unauthorized ->
                        throw AuthException(
                            message = "Unauthorized — token invalid or expired (401)",
                            reason  = AuthFailureReason.SESSION_EXPIRED,
                        )
                    HttpStatusCode.UnprocessableEntity ->
                        throw SyncException(
                            message = "Sync batch rejected by server (422)",
                        )
                    else ->
                        throw NetworkException(
                            message    = "HTTP ${response.status.value}: ${response.status.description}",
                            statusCode = response.status.value,
                        )
                }
            }
        } catch (e: ZentaException) {
            throw e          // re-throw domain exceptions untouched
        } catch (e: Exception) {
            log.e(e) { "Network transport error" }
            throw NetworkException(
                message = "Network error: ${e.message}",
                cause   = e,
            )
        }
    }
}
