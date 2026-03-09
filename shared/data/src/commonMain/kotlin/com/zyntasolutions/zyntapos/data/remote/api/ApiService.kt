package com.zyntasolutions.zyntapos.data.remote.api

import com.zyntasolutions.zyntapos.core.result.AuthException
import com.zyntasolutions.zyntapos.core.result.NetworkException
import com.zyntasolutions.zyntapos.core.result.SyncException
import com.zyntasolutions.zyntapos.data.remote.dto.AuthRefreshResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.AuthRequestDto
import com.zyntasolutions.zyntapos.data.remote.dto.AuthResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.ProductDto
import com.zyntasolutions.zyntapos.data.remote.dto.PublicKeyResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.SyncOperationDto
import com.zyntasolutions.zyntapos.data.remote.dto.SyncPullResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.SyncResponseDto

/**
 * ZyntaPOS — Remote API service contract.
 *
 * Defines all Phase 1 server endpoints. Every function returns a typed result;
 * HTTP-level errors are mapped to [ZyntaException] subclasses by implementations.
 *
 * ## Endpoint Summary
 * | Method | Path | Function |
 * |--------|------|----------|
 * | POST | /api/v1/auth/login | [login] |
 * | POST | /api/v1/auth/refresh | [refreshToken] |
 * | GET  | /api/v1/products | [getProducts] |
 * | POST | /api/v1/sync/push | [pushOperations] |
 * | GET  | /api/v1/sync/pull | [pullOperations] |
 * | GET  | /.well-known/public-key | [fetchPublicKey] |
 */
interface ApiService {

    /**
     * Authenticates the user with server credentials.
     *
     * @throws AuthException on 401 / invalid credentials.
     * @throws NetworkException on transport or timeout errors.
     */
    suspend fun login(request: AuthRequestDto): AuthResponseDto

    /**
     * Exchanges a refresh token for a new access token.
     *
     * @throws AuthException on 401 (refresh token expired).
     * @throws NetworkException on transport errors.
     */
    suspend fun refreshToken(refreshToken: String): AuthRefreshResponseDto

    /**
     * Retrieves the full product catalog from the server.
     * Typically called during initial seeding or after a full-resync.
     *
     * @throws NetworkException on transport or server errors.
     */
    suspend fun getProducts(): List<ProductDto>

    /**
     * Pushes a batch of locally pending operations to the server.
     *
     * @param operations Local outbox entries serialized as [SyncOperationDto].
     * @return [SyncResponseDto] with accepted / rejected / conflict IDs.
     * @throws SyncException on 4xx/5xx server-side batch failure.
     * @throws NetworkException on transport errors.
     */
    suspend fun pushOperations(operations: List<SyncOperationDto>): SyncResponseDto

    /**
     * Pulls server-side changes created after [lastSyncTimestamp].
     *
     * @param lastSyncTimestamp Unix epoch ms of the last successful sync.
     *                          Pass `0L` for a full resync.
     * @return [SyncPullResponseDto] with delta operations + new server timestamp.
     * @throws NetworkException on transport or server errors.
     */
    suspend fun pullOperations(lastSyncTimestamp: Long): SyncPullResponseDto

    /**
     * Fetches the current RS256 public key from `GET /.well-known/public-key`.
     *
     * Call after every successful online login or token refresh. Pass the returned
     * [PublicKeyResponseDto.publicKey] to
     * [com.zyntasolutions.zyntapos.security.auth.JwtManager.cachePublicKey] so that
     * [com.zyntasolutions.zyntapos.security.auth.JwtManager.verifyOfflineRole] uses
     * the freshest key. This enables server-side key rotation without an app update.
     *
     * @return [PublicKeyResponseDto] containing the standard Base64-encoded DER key.
     * @throws NetworkException on transport or server errors.
     */
    suspend fun fetchPublicKey(): PublicKeyResponseDto
}
