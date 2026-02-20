package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Contract for all authentication and session lifecycle operations.
 *
 * Implementations are responsible for:
 * - Validating credentials (online via API, offline via cached hash)
 * - Persisting the authenticated session (JWT tokens) in [SecurePreferences]
 * - Observing session validity so the UI can react to expiry or logout
 *
 * No framework or platform code may be imported into this interface.
 */
interface AuthRepository {

    /**
     * Authenticates a user with [email] and [password].
     *
     * Online path: sends credentials to the remote API, caches the received JWT
     * and password hash locally for future offline authentication.
     *
     * Offline path: compares [password] against the locally-stored BCrypt hash.
     * Returns [Result.Error] with [AuthException] if the device has never been
     * authenticated online (no cached hash available).
     *
     * @return [Result.Success] wrapping the authenticated [User], or
     *         [Result.Error] with [com.zyntasolutions.zyntapos.core.result.ZentaException.AuthException].
     */
    suspend fun login(email: String, password: String): Result<User>

    /**
     * Destroys the current session.
     *
     * Clears the JWT from [SecurePreferences] and resets any in-memory session state.
     * Does NOT clear the cached password hash — offline login must remain possible
     * after logout.
     */
    suspend fun logout()

    /**
     * Emits the currently authenticated [User], or `null` when no session exists.
     *
     * This [Flow] remains active and re-emits whenever the session changes
     * (login, logout, token refresh). UI layers collect this to gate navigation.
     */
    fun getSession(): Flow<User?>

    /**
     * Requests a new access token using the cached refresh token.
     *
     * Should be called proactively before the access token expires (e.g., from
     * a background coroutine 60 s before expiry) or reactively on a 401 response.
     *
     * @return [Result.Success] with [Unit] on success, or
     *         [Result.Error] with [com.zyntasolutions.zyntapos.core.result.ZentaException.AuthException]
     *         if the refresh token is also expired (forces full logout).
     */
    suspend fun refreshToken(): Result<Unit>

    /**
     * Updates the quick-switch PIN for the user identified by [userId].
     *
     * The raw [pin] value is hashed by the implementation before storage.
     * The caller should already have verified the user's password or current PIN
     * before invoking this method.
     *
     * @param userId  UUID of the user whose PIN is being updated.
     * @param pin     Raw 4–6-digit PIN string (validation is done by [PinManager]).
     * @return [Result.Success] with [Unit], or [Result.Error] on validation/storage failure.
     */
    suspend fun updatePin(userId: String, pin: String): Result<Unit>
}
