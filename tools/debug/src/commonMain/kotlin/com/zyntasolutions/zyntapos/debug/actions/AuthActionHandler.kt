package com.zyntasolutions.zyntapos.debug.actions

import com.zyntasolutions.zyntapos.core.result.AuthException
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.debug.model.UserSummary
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.UserRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock

/**
 * Abstracts authentication-related debug operations for the Auth tab.
 */
interface AuthActionHandler {
    /** Returns all users in the system as lightweight summaries (no credentials). */
    suspend fun getAllUsers(): Result<List<UserSummary>>

    /**
     * Creates a new [Role.ADMIN] user using the runtime-supplied credentials.
     *
     * The [plainPassword] value is forwarded directly to [UserRepository.create]
     * and is NOT stored anywhere by this handler.
     */
    suspend fun createAdminUser(email: String, name: String, plainPassword: String): Result<Unit>

    /** Deactivates the user identified by [userId] (soft-delete). */
    suspend fun deactivateUser(userId: String): Result<Unit>

    /** Clears the current session, forcing re-login. */
    suspend fun clearSession(): Result<Unit>

    /** Returns the current session user, or null if unauthenticated. */
    suspend fun getCurrentUser(): User?
}

/**
 * Default implementation backed by [AuthRepository] and [UserRepository].
 */
class AuthActionHandlerImpl(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : AuthActionHandler {

    override suspend fun getAllUsers(): Result<List<UserSummary>> {
        return try {
            val users = userRepository.getAll().firstOrNull() ?: emptyList()
            val summaries = users.map { u ->
                UserSummary(
                    id = u.id,
                    name = u.name,
                    email = u.email,
                    role = u.role.name,
                    isActive = u.isActive,
                )
            }
            Result.Success(summaries)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to load users: ${e.message}"))
        }
    }

    override suspend fun createAdminUser(
        email: String,
        name: String,
        plainPassword: String,
    ): Result<Unit> {
        val now = Clock.System.now()
        val user = User(
            id = generateId(),
            name = name,
            email = email,
            role = Role.ADMIN,
            storeId = "debug-store",
            isActive = true,
            pinHash = null,
            createdAt = now,
            updatedAt = now,
        )
        return userRepository.create(user, plainPassword)
    }

    override suspend fun deactivateUser(userId: String): Result<Unit> =
        userRepository.deactivate(userId)

    override suspend fun clearSession(): Result<Unit> {
        return try {
            authRepository.logout()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(AuthException("Logout failed: ${e.message}"))
        }
    }

    override suspend fun getCurrentUser(): User? =
        authRepository.getSession().firstOrNull()

    private fun generateId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return "debug-" + (1..8).map { chars.random() }.joinToString("")
    }
}
