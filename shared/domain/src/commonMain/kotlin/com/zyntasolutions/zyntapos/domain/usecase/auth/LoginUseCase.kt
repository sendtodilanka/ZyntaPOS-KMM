package com.zyntasolutions.zyntapos.domain.usecase.auth

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository

/**
 * Authenticates a user with email/password.
 *
 * ### Business Rules
 * 1. **Online path:** Delegates to [AuthRepository.login], which sends credentials
 *    to the remote API and caches the JWT + password hash for offline use.
 * 2. **Offline path:** If the network is unavailable, the repository automatically
 *    falls back to comparing [password] against the locally-cached BCrypt hash.
 *    Returns [com.zyntasolutions.zyntapos.core.result.AuthException] with reason
 *    `OFFLINE_NO_CACHE` if the device has never logged in online.
 * 3. Inactive accounts (`user.isActive == false`) are rejected with reason
 *    `ACCOUNT_DISABLED`.
 * 4. After 5 consecutive failures the account is temporarily locked for 15 minutes
 *    (enforced by the repository / remote API).
 *
 * @param authRepository Authentication gateway.
 */
class LoginUseCase(
    private val authRepository: AuthRepository,
) {
    /**
     * @param email    The user's email address.
     * @param password The plaintext password to validate.
     * @return [Result.Success] wrapping the authenticated [User],
     *         or [Result.Error] with [com.zyntasolutions.zyntapos.core.result.AuthException].
     */
    suspend operator fun invoke(email: String, password: String): Result<User> =
        authRepository.login(email, password)
}
