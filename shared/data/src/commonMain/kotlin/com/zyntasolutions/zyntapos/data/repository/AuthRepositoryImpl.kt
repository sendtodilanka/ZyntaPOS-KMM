package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.core.result.AuthException
import com.zyntasolutions.zyntapos.core.result.AuthFailureReason
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.mapper.UserMapper
import com.zyntasolutions.zyntapos.domain.port.SecureStorageKeys
import com.zyntasolutions.zyntapos.domain.port.SecureStoragePort
import com.zyntasolutions.zyntapos.domain.port.PasswordHashPort
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.time.Clock

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
 * via [PasswordHashPort.verify]. The hash is written during first-time setup / import.
 * If no user record exists locally, returns [AuthFailureReason.OFFLINE_NO_CACHE].
 *
 * ## Session storage
 * JWT tokens are persisted in [SecureStoragePort] (platform-encrypted storage).
 * The in-memory [_session] StateFlow is the authoritative source for active session.
 *
 * MERGED-F3 (2026-02-22): [securePrefs] parameter type changed from `SecurePreferences`
 * (`:shared:security`) to [SecureStoragePort] (`:shared:domain`) so `:shared:data`
 * holds no compile-time dependency on `:shared:security`.
 *
 * @param db               Encrypted [ZyntaDatabase] singleton.
 * @param securePrefs      [SecureStoragePort] — platform-encrypted key-value store.
 * @param passwordHasher   Domain port for BCrypt hash + verify operations.
 */
class AuthRepositoryImpl(
    private val db: ZyntaDatabase,
    private val securePrefs: SecureStoragePort,
    private val passwordHasher: PasswordHashPort,
) : AuthRepository {

    private val _session = MutableStateFlow<User?>(null)

    init {
        // Restore session from secure storage on init
        val cachedUserId = securePrefs.get(SecureStorageKeys.KEY_USER_ID)
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
            val valid = passwordHasher.verify(password, userRow.password_hash)
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
            securePrefs.put(SecureStorageKeys.KEY_USER_ID, user.id)
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
        securePrefs.remove(SecureStorageKeys.KEY_ACCESS_TOKEN)
        securePrefs.remove(SecureStorageKeys.KEY_REFRESH_TOKEN)
        securePrefs.remove(SecureStorageKeys.KEY_TOKEN_EXPIRY)
        securePrefs.remove(SecureStorageKeys.KEY_USER_ID)
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
            val pinHash = passwordHasher.hash(pin)
            val now     = Clock.System.now().toEpochMilliseconds()
            db.usersQueries.updateUserPin(pin_hash = pinHash, updated_at = now, id = userId)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update PIN failed", cause = t)) },
        )
    }
}
