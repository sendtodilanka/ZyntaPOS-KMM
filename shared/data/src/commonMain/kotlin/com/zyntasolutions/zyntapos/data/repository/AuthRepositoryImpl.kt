package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.core.result.AuthException
import com.zyntasolutions.zyntapos.core.result.AuthFailureReason
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.mapper.UserMapper
import com.zyntasolutions.zyntapos.data.local.security.SecurePreferences
import com.zyntasolutions.zyntapos.security.auth.PasswordHasher
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Concrete implementation of [AuthRepository].
 *
 * ## Authentication flow
 *
 * **Online** (Sprint 6 scope: offline-only MVP):
 * When a network API is available (Sprint 7+), the impl will first attempt API login,
 * cache the JWT and BCrypt hash, then fall through to the offline path on failure.
 *
 * **Offline (Phase 1 MVP)**:
 * Compares the supplied [password] against the BCrypt hash stored in the `users` table
 * via [PasswordHasher.verify]. The hash is written during first-time setup / import.
 * If no user record exists locally, returns [AuthFailureReason.OFFLINE_NO_CACHE].
 *
 * ## Session storage
 * JWT tokens are persisted in [SecurePreferences] (platform-encrypted storage).
 * The in-memory [_session] StateFlow is the authoritative source for active session.
 *
 * @param db               Encrypted [ZyntaDatabase] singleton.
 * @param securePrefs      Platform-encrypted key-value store.
 */
class AuthRepositoryImpl(
    private val db: ZyntaDatabase,
    private val securePrefs: SecurePreferences,
) : AuthRepository {

    private val _session = MutableStateFlow<User?>(null)

    init {
        // Restore session from secure storage on init
        val cachedUserId = securePrefs.get(SecurePreferences.Keys.CURRENT_USER_ID)
        if (cachedUserId != null) {
            val userRow = db.usersQueries.getUserById(cachedUserId).executeAsOneOrNull()
            if (userRow != null) {
                _session.value = UserMapper.toDomain(userRow)
            } else {
                // User no longer exists; clear stale session
                securePrefs.clear()
            }
        }
    }

    override suspend fun login(email: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        runCatching {
            val userRow = db.usersQueries.getUserByEmail(email).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    AuthException(
                        message = "No local account found for $email",
                        reason  = AuthFailureReason.OFFLINE_NO_CACHE,
                    )
                )
            if (userRow.is_active != 1L) {
                return@withContext Result.Error(
                    AuthException(
                        message = "Account is disabled",
                        reason  = AuthFailureReason.ACCOUNT_DISABLED,
                    )
                )
            }
            val valid = PasswordHasher.verifyPassword(password, userRow.password_hash)
            if (!valid) {
                return@withContext Result.Error(
                    AuthException(
                        message = "Invalid credentials",
                        reason  = AuthFailureReason.INVALID_CREDENTIALS,
                    )
                )
            }
            val user = UserMapper.toDomain(userRow)
            // Cache session
            securePrefs.put(SecurePreferences.Keys.CURRENT_USER_ID, user.id)
            _session.value = user
            user
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t ->
                if (t is AuthException) Result.Error(t)
                else Result.Error(DatabaseException(t.message ?: "Login DB error", cause = t))
            },
        )
    }

    override suspend fun logout() = withContext(Dispatchers.IO) {
        securePrefs.remove(SecurePreferences.Keys.ACCESS_TOKEN)
        securePrefs.remove(SecurePreferences.Keys.REFRESH_TOKEN)
        securePrefs.remove(SecurePreferences.Keys.TOKEN_EXPIRY)
        securePrefs.remove(SecurePreferences.Keys.CURRENT_USER_ID)
        _session.value = null
    }

    override fun getSession(): Flow<User?> = _session.asStateFlow()

    /**
     * Refresh token is a no-op in Phase 1 (offline-only MVP).
     * Sprint 6 Step 3.4 (Ktor client) will provide the real implementation.
     */
    override suspend fun refreshToken(): Result<Unit> = Result.Success(Unit)

    override suspend fun updatePin(userId: String, pin: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val pinHash = PasswordHasher.hashPassword(pin)
            val now     = Clock.System.now().toEpochMilliseconds()
            db.usersQueries.updateUserPin(pin_hash = pinHash, updated_at = now, id = userId)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update PIN failed", cause = t)) },
        )
    }
}
