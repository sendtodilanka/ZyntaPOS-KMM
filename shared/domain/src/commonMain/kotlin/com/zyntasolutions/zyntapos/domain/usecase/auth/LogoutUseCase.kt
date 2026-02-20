package com.zyntasolutions.zyntapos.domain.usecase.auth

import com.zyntasolutions.zyntapos.domain.repository.AuthRepository

/**
 * Destroys the current authenticated session.
 *
 * ### Business Rules
 * 1. Clears the JWT access and refresh tokens from secure storage.
 * 2. Resets all in-memory session state (cached user, role, permissions).
 * 3. The cached password hash is **not** cleared — offline login must remain
 *    possible after a logout/re-login cycle.
 * 4. This use case is fire-and-forget; any storage failure is silently logged
 *    (the session is considered ended regardless of persistence outcome).
 *
 * @param authRepository Session lifecycle gateway.
 */
class LogoutUseCase(
    private val authRepository: AuthRepository,
) {
    /**
     * Executes the logout, clearing the session and sensitive in-memory state.
     */
    suspend operator fun invoke() {
        authRepository.logout()
    }
}
