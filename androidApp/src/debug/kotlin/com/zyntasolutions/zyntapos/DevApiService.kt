package com.zyntasolutions.zyntapos

import com.zyntasolutions.zyntapos.data.remote.api.ApiService
import com.zyntasolutions.zyntapos.data.remote.dto.AuthRefreshResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.AuthRequestDto
import com.zyntasolutions.zyntapos.data.remote.dto.AuthResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.ProductDto
import com.zyntasolutions.zyntapos.data.remote.dto.PublicKeyResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.SyncOperationDto
import com.zyntasolutions.zyntapos.data.remote.dto.SyncPullResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.SyncResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.UserDto
import kotlin.time.Clock

/**
 * No-op [ApiService] for local dev/testing builds — **debug source set only**.
 *
 * Replaces [KtorApiService] so the app runs fully offline without any real
 * backend. All network calls are stubbed with empty or accepted responses:
 *
 * - [login]             — Unreachable in practice; [AuthRepositoryImpl] is 100% local.
 *                         Returns a harmless fake token to satisfy the interface.
 * - [refreshToken]      — Returns a renewed fake token (no server call).
 * - [getProducts]       — Returns empty list; local SQLite is the source of truth.
 * - [pushOperations]    — Marks all pending outbox entries as "accepted" so the
 *                         sync queue stays clean without a real server.
 * - [pullOperations]    — Returns empty; no server data to pull.
 *
 * **Security note:** This class does NOT bypass local authentication.
 * Login still validates the user's email + BCrypt password hash against the
 * local SQLite database. Only remote sync is stubbed out.
 *
 * This file lives in `src/debug/` — the Android build system physically
 * excludes it from every non-debug build type. It cannot reach production.
 */
internal class DevApiService : ApiService {

    /**
     * Auth is local-only ([AuthRepositoryImpl] never calls [ApiService.login]).
     * Stub returned for interface completeness only.
     */
    override suspend fun login(request: AuthRequestDto): AuthResponseDto =
        AuthResponseDto(
            accessToken  = "dev-access-token",
            refreshToken = "dev-refresh-token",
            expiresIn    = Long.MAX_VALUE,
            user         = UserDto(
                id        = "dev-user",
                name      = "Dev User",
                email     = request.email,
                role      = "ADMIN",
                storeId   = "store-default",
                isActive  = true,
                createdAt = Clock.System.now().toEpochMilliseconds(),
                updatedAt = Clock.System.now().toEpochMilliseconds(),
            ),
        )

    override suspend fun refreshToken(refreshToken: String): AuthRefreshResponseDto =
        AuthRefreshResponseDto(
            accessToken = "dev-access-token",
            expiresIn   = Long.MAX_VALUE,
        )

    override suspend fun getProducts(): List<ProductDto> = emptyList()

    /**
     * Accepts all pending operations so the local sync queue is marked clean.
     * No data leaves the device.
     */
    override suspend fun pushOperations(operations: List<SyncOperationDto>): SyncResponseDto =
        SyncResponseDto(
            accepted        = operations.map { it.id },
            rejected        = emptyList(),
            conflicts       = emptyList(),
            deltaOperations = emptyList(),
            serverTimestamp = Clock.System.now().toEpochMilliseconds(),
        )

    override suspend fun pullOperations(lastSyncTimestamp: Long): SyncPullResponseDto =
        SyncPullResponseDto(
            operations      = emptyList(),
            serverTimestamp = Clock.System.now().toEpochMilliseconds(),
        )

    /** Returns a no-op placeholder key — dev builds use the default bundled key. */
    override suspend fun fetchPublicKey(): PublicKeyResponseDto =
        PublicKeyResponseDto(publicKey = "")
}
