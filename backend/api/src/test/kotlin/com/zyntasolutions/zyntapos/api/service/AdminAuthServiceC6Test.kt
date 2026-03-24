package com.zyntasolutions.zyntapos.api.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zyntasolutions.zyntapos.api.auth.AdminRole
import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.repository.AdminAuditRepository
import com.zyntasolutions.zyntapos.api.repository.AdminUserRepository
import com.zyntasolutions.zyntapos.api.repository.AuditEntryInput
import com.zyntasolutions.zyntapos.api.repository.AuditEntryRow
import com.zyntasolutions.zyntapos.api.repository.AuditFilter
import com.zyntasolutions.zyntapos.api.repository.AuditPage
import com.zyntasolutions.zyntapos.api.repository.ResetTokenRow
import com.zyntasolutions.zyntapos.api.repository.SessionRow
import kotlinx.coroutines.runBlocking
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive admin auth tests — Phase C, task C6.
 *
 * Covers scenarios NOT already tested in AdminAuthServiceTest / AdminAuthServiceExtendedTest:
 *  - Lockout cooldown expiry (account unlocks after period)
 *  - Password reset token expiry + reuse prevention
 *  - Session listing for admin user
 *  - Single session revocation
 *  - Password change (current password validation + new password strength)
 *  - Token refresh flow (rotation, revocation of old session)
 *  - Logout (session revocation)
 *  - Access token TTL and claim completeness
 *  - Email case-insensitive lookup
 *  - MFA pending token cannot be used as refresh token
 *  - Login with null passwordHash user
 */
class AdminAuthServiceC6Test {

    companion object {
        private const val TEST_ISSUER = "https://panel.test.local"
        private const val TEST_PASSWORD = "SecureP@ssw0rd!"

        private fun generateTestRsaKeyPair(): Pair<java.security.PublicKey, java.security.PrivateKey> {
            val kpg = java.security.KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048)
            val kp = kpg.generateKeyPair()
            return kp.public to kp.private
        }

        private val testKeyPair = generateTestRsaKeyPair()

        private fun testConfig(): AppConfig {
            val (pub, priv) = testKeyPair
            return AppConfig(
                jwtIssuer = TEST_ISSUER,
                jwtAudience = "test",
                jwtPublicKey = pub,
                jwtPrivateKey = priv,
                accessTokenTtlMs = 3_600_000L,
                refreshTokenTtlMs = 86_400_000L,
                adminJwtPublicKey = pub,
                adminJwtPrivateKey = priv,
                adminJwtIssuer = TEST_ISSUER,
                adminAccessTokenTtlMs = 900_000L,
                adminRefreshTokenTtlDays = 7L,
                adminPanelUrl = "https://panel.test.local",
                redisUrl = "redis://localhost:6379",
                resendApiKey = "",
                emailFromAddress = "test@test.local",
                emailFromName = "Test",
                playIntegrityPackageName = "",
                playIntegrityApiKey = "",
                inboundEmailHmacSecret = "",
                chatwootApiUrl = "",
                chatwootApiToken = "",
                chatwootAccountId = "",
                chatwootInboxId = "",
            )
        }

        private val noOpAuditRepo = object : AdminAuditRepository {
            override suspend fun insertEntry(entry: AuditEntryInput) { /* no-op */ }
            override suspend fun findLatestHash() = ""
            override suspend fun listEntries(filter: AuditFilter, page: Int, size: Int) =
                AuditPage(emptyList(), page, size, 0, 0)
            override suspend fun exportEntries(filter: AuditFilter, limit: Int) =
                emptyList<AuditEntryRow>()
        }

        private val noOpAudit = object : AdminAuditService(noOpAuditRepo) {
            override suspend fun log(
                adminId: UUID?,
                adminName: String?,
                eventType: String,
                category: String,
                entityType: String?,
                entityId: String?,
                previousValues: Map<String, String>?,
                newValues: Map<String, String>?,
                ipAddress: String?,
                userAgent: String?,
                success: Boolean,
                errorMessage: String?
            ) { /* no-op */ }
        }

        private val noOpEmailService = EmailService(testConfig())
    }

    /**
     * In-memory fake AdminUserRepository — reused across C6 tests.
     */
    private class FakeAdminUserRepo : AdminUserRepository {
        val users = mutableMapOf<UUID, AdminUserRow>()
        val sessions = mutableMapOf<UUID, SessionRow>()
        val sessionTokenHashes = mutableMapOf<UUID, String>() // sessionId → tokenHash
        val resetTokens = mutableMapOf<String, ResetTokenRow>()
        var failedAttemptsByUser = mutableMapOf<UUID, Int>()
        var lockedUntilByUser = mutableMapOf<UUID, Long?>()
        private val passwordChangedAtByUser = mutableMapOf<UUID, Long?>()

        override suspend fun findByEmail(email: String): AdminUserRow? =
            users.values.find { it.email == email.lowercase().trim() }

        override suspend fun findById(id: UUID): AdminUserRow? =
            users[id]?.takeIf { it.isActive }

        override suspend fun count(): Long = users.size.toLong()

        override suspend fun getLockedUntil(id: UUID): Long? = lockedUntilByUser[id]

        override suspend fun getFailedAttempts(id: UUID): Int = failedAttemptsByUser[id] ?: 0

        override suspend fun getLastLoginIp(id: UUID): String? = null

        override suspend fun getPasswordChangedAt(id: UUID): Long? =
            passwordChangedAtByUser[id] ?: Instant.now().toEpochMilli()

        override suspend fun createUser(email: String, name: String, role: AdminRole, passwordHash: String): AdminUserRow {
            val now = Instant.now().toEpochMilli()
            val user = AdminUserRow(
                id = UUID.randomUUID(),
                email = email.lowercase().trim(),
                name = name,
                role = role,
                passwordHash = passwordHash,
                mfaEnabled = false,
                isActive = true,
                lastLoginAt = null,
                createdAt = now,
            )
            users[user.id] = user
            failedAttemptsByUser[user.id] = 0
            lockedUntilByUser[user.id] = null
            passwordChangedAtByUser[user.id] = now
            return user
        }

        override suspend fun updateLoginSuccess(id: UUID, timestampMs: Long, ip: String?) {
            failedAttemptsByUser[id] = 0
            lockedUntilByUser[id] = null
            users[id] = users[id]!!.copy(lastLoginAt = timestampMs)
        }

        override suspend fun updateFailedAttempts(id: UUID, count: Int, lockedUntilMs: Long?) {
            failedAttemptsByUser[id] = count
            lockedUntilByUser[id] = lockedUntilMs
        }

        override suspend fun updatePassword(id: UUID, passwordHash: String) {
            users[id] = users[id]!!.copy(passwordHash = passwordHash)
            passwordChangedAtByUser[id] = Instant.now().toEpochMilli()
        }

        override suspend fun updateUser(id: UUID, name: String?, role: AdminRole?, isActive: Boolean?): AdminUserRow? {
            val user = users[id] ?: return null
            val updated = user.copy(
                name = name ?: user.name,
                role = role ?: user.role,
                isActive = isActive ?: user.isActive,
            )
            users[id] = updated
            return updated
        }

        override suspend fun insertSession(userId: UUID, tokenHash: String, ip: String?, userAgent: String?, createdAt: Long, expiresAt: Long) {
            val session = SessionRow(
                id = UUID.randomUUID(),
                userId = userId,
                userAgent = userAgent,
                ipAddress = ip,
                createdAt = createdAt,
                expiresAt = expiresAt,
                revokedAt = null,
            )
            sessions[session.id] = session
            sessionTokenHashes[session.id] = tokenHash
        }

        override suspend fun findSessionByTokenHash(tokenHash: String, nowMs: Long): SessionRow? =
            sessions.entries
                .find { (id, _) -> sessionTokenHashes[id] == tokenHash }
                ?.value
                ?.takeIf { it.revokedAt == null && it.expiresAt > nowMs }

        override suspend fun revokeSession(sessionId: UUID, revokedAtMs: Long) {
            sessions[sessionId] = sessions[sessionId]!!.copy(revokedAt = revokedAtMs)
        }

        override suspend fun revokeAllSessions(userId: UUID, revokedAtMs: Long) {
            sessions.entries.filter { it.value.userId == userId }.forEach { (id, session) ->
                sessions[id] = session.copy(revokedAt = revokedAtMs)
            }
        }

        override suspend fun revokeSessionByTokenHash(tokenHash: String, revokedAtMs: Long) {
            val entry = sessions.entries.find { (id, _) -> sessionTokenHashes[id] == tokenHash }
            if (entry != null) {
                sessions[entry.key] = entry.value.copy(revokedAt = revokedAtMs)
            }
        }

        override suspend fun listActiveSessions(userId: UUID, nowMs: Long): List<AdminSessionRow> =
            sessions.values
                .filter { it.userId == userId && it.revokedAt == null && it.expiresAt > nowMs }
                .map { AdminSessionRow(it.id, it.userId, it.userAgent, it.ipAddress, it.createdAt, it.expiresAt, it.revokedAt) }

        override suspend fun findByIdWithPassword(id: UUID): AdminUserRow? = users[id]

        override suspend fun deleteUnusedResetTokens(userId: UUID) {
            val keysToRemove = resetTokens.entries.filter { it.value.adminUserId == userId && it.value.usedAt == null }.map { it.key }
            keysToRemove.forEach { resetTokens.remove(it) }
        }

        override suspend fun insertResetToken(id: UUID, userId: UUID, tokenHash: String, expiresAt: Long, createdAt: Long) {
            resetTokens[tokenHash] = ResetTokenRow(
                id = id,
                adminUserId = userId,
                tokenHash = tokenHash,
                expiresAt = expiresAt,
                usedAt = null,
                createdAt = createdAt,
            )
        }

        override suspend fun findResetToken(tokenHash: String): ResetTokenRow? = resetTokens[tokenHash]

        override suspend fun markResetTokenUsed(tokenHash: String, usedAt: Long) {
            resetTokens[tokenHash] = resetTokens[tokenHash]!!.copy(usedAt = usedAt)
        }

        /** Sentinel value to indicate "compute hash from password parameter". */
        private val HASH_FROM_PASSWORD = "__compute__"

        fun addTestUser(
            email: String = "admin@test.local",
            password: String = TEST_PASSWORD,
            role: AdminRole = AdminRole.ADMIN,
            mfaEnabled: Boolean = false,
            isActive: Boolean = true,
            passwordHash: String? = HASH_FROM_PASSWORD,
        ): AdminUserRow {
            val effectiveHash = if (passwordHash == HASH_FROM_PASSWORD) {
                BCrypt.withDefaults().hashToString(4, password.toCharArray())
            } else {
                passwordHash
            }
            val user = AdminUserRow(
                id = UUID.randomUUID(),
                email = email.lowercase(),
                name = "Test Admin",
                role = role,
                passwordHash = effectiveHash,
                mfaEnabled = mfaEnabled,
                isActive = isActive,
                lastLoginAt = null,
                createdAt = Instant.now().toEpochMilli(),
            )
            users[user.id] = user
            failedAttemptsByUser[user.id] = 0
            lockedUntilByUser[user.id] = null
            return user
        }
    }

    // ── Lockout cooldown expiry ──────────────────────────────────────────────

    @Test
    fun `account unlocks after lockout cooldown period expires`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val user = repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        // Lock account by failing 5 times
        repeat(5) {
            service.login("admin@test.local", "wrong", null, null)
        }

        // Confirm locked
        val locked = service.login("admin@test.local", TEST_PASSWORD, null, null)
        assertIs<AdminAuthResult.AccountLocked>(locked)

        // Simulate cooldown expiry: set lockedUntil to the past
        repo.lockedUntilByUser[user.id] = Instant.now().toEpochMilli() - 1_000L

        // Should succeed now
        val result = service.login("admin@test.local", TEST_PASSWORD, null, null)
        assertIs<AdminAuthResult.Success>(result)
    } }

    @Test
    fun `account stays locked during cooldown period`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val user = repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        // Lock account
        repeat(5) {
            service.login("admin@test.local", "wrong", null, null)
        }

        // lockedUntil is in the future (set by incrementFailedAttempts)
        val lockedUntil = repo.lockedUntilByUser[user.id]
        assertNotNull(lockedUntil)
        assertTrue(lockedUntil > Instant.now().toEpochMilli())

        // Even correct password should fail
        val result = service.login("admin@test.local", TEST_PASSWORD, null, null)
        assertIs<AdminAuthResult.AccountLocked>(result)
    } }

    // ── Password reset token expiry ─────────────────────────────────────────

    @Test
    fun `resetPassword fails with expired token`() { runBlocking {
        val repo = FakeAdminUserRepo()
        repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        val rawToken = service.generatePasswordResetToken("admin@test.local")!!

        // Manually expire the token by setting expiresAt to the past
        val tokenEntries = repo.resetTokens.entries.toList()
        tokenEntries.forEach { (hash, row) ->
            repo.resetTokens[hash] = row.copy(expiresAt = Instant.now().toEpochMilli() - 1_000L)
        }

        val result = service.resetPassword(rawToken, "NewP@ssw0rd!")
        assertFalse(result, "Expired reset token should be rejected")
    } }

    @Test
    fun `resetPassword fails when token has already been used`() { runBlocking {
        val repo = FakeAdminUserRepo()
        repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        val rawToken = service.generatePasswordResetToken("admin@test.local")!!

        // Use the token once
        val firstResult = service.resetPassword(rawToken, "NewP@ssw0rd1!")
        assertTrue(firstResult, "First use of reset token should succeed")

        // Attempt reuse
        val secondResult = service.resetPassword(rawToken, "NewP@ssw0rd2!")
        assertFalse(secondResult, "Reused reset token should be rejected")
    } }

    @Test
    fun `after password reset, old password no longer works`() { runBlocking {
        val repo = FakeAdminUserRepo()
        repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        val rawToken = service.generatePasswordResetToken("admin@test.local")!!
        val newPassword = "BrandNewP@ss99"
        service.resetPassword(rawToken, newPassword)

        // Old password should fail
        val oldResult = service.login("admin@test.local", TEST_PASSWORD, null, null)
        assertIs<AdminAuthResult.InvalidCredentials>(oldResult)

        // New password should work
        val newResult = service.login("admin@test.local", newPassword, null, null)
        assertIs<AdminAuthResult.Success>(newResult)
    } }

    // ── Session listing ─────────────────────────────────────────────────────

    @Test
    fun `listActiveSessions returns active sessions for user`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val user = repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        // Login twice to create sessions
        service.login("admin@test.local", TEST_PASSWORD, "10.0.0.1", "Chrome")
        service.login("admin@test.local", TEST_PASSWORD, "10.0.0.2", "Firefox")

        val sessions = service.listActiveSessions(user.id)
        assertEquals(2, sessions.size)
    } }

    @Test
    fun `listActiveSessions excludes revoked sessions`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val user = repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        service.login("admin@test.local", TEST_PASSWORD, "10.0.0.1", "Chrome")
        service.login("admin@test.local", TEST_PASSWORD, "10.0.0.2", "Firefox")

        service.revokeAllSessions(user.id)

        val sessions = service.listActiveSessions(user.id)
        assertEquals(0, sessions.size)
    } }

    @Test
    fun `listActiveSessions returns empty for user with no sessions`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val user = repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        val sessions = service.listActiveSessions(user.id)
        assertTrue(sessions.isEmpty())
    } }

    // ── Password change ─────────────────────────────────────────────────────

    @Test
    fun `changePassword succeeds with correct current password`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val user = repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        val newPassword = "NewSecure#Pass1"
        val changed = service.changePassword(user.id, TEST_PASSWORD, newPassword)
        assertTrue(changed)

        // New password should work for login
        val result = service.login("admin@test.local", newPassword, null, null)
        assertIs<AdminAuthResult.Success>(result)
    } }

    @Test
    fun `changePassword fails with wrong current password`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val user = repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        val changed = service.changePassword(user.id, "wrong-current", "NewSecure#Pass1")
        assertFalse(changed)

        // Original password should still work
        val result = service.login("admin@test.local", TEST_PASSWORD, null, null)
        assertIs<AdminAuthResult.Success>(result)
    } }

    @Test
    fun `changePassword revokes all existing sessions`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val user = repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        // Create sessions
        service.login("admin@test.local", TEST_PASSWORD, "10.0.0.1", "Chrome")
        service.login("admin@test.local", TEST_PASSWORD, "10.0.0.2", "Firefox")
        assertTrue(repo.sessions.values.any { it.revokedAt == null })

        service.changePassword(user.id, TEST_PASSWORD, "NewSecure#Pass1")

        // All sessions should be revoked
        assertTrue(repo.sessions.values.all { it.revokedAt != null })
    } }

    @Test
    fun `changePassword fails for non-existent user`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        val changed = service.changePassword(UUID.randomUUID(), TEST_PASSWORD, "NewSecure#Pass1")
        assertFalse(changed)
    } }

    @Test
    fun `changePassword fails when user has null passwordHash`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val user = repo.addTestUser(passwordHash = null)
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        val changed = service.changePassword(user.id, TEST_PASSWORD, "NewSecure#Pass1")
        assertFalse(changed)
    } }

    // ── Token refresh flow ──────────────────────────────────────────────────

    @Test
    fun `refresh returns new token pair for valid refresh token`() { runBlocking {
        val repo = FakeAdminUserRepo()
        repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        val loginResult = service.login("admin@test.local", TEST_PASSWORD, "127.0.0.1", "TestAgent")
        assertIs<AdminAuthResult.Success>(loginResult)

        val tokens = service.refresh(loginResult.refreshToken, "127.0.0.1", "TestAgent")
        assertNotNull(tokens)
        assertTrue(tokens.first.isNotBlank(), "New access token should be non-blank")
        assertTrue(tokens.second.isNotBlank(), "New refresh token should be non-blank")
    } }

    @Test
    fun `refresh returns null for invalid refresh token`() { runBlocking {
        val repo = FakeAdminUserRepo()
        repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        val result = service.refresh("nonexistent-refresh-token", null, null)
        assertNull(result)
    } }

    @Test
    fun `refresh revokes old session (single-use rotation)`() { runBlocking {
        val repo = FakeAdminUserRepo()
        repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        val loginResult = service.login("admin@test.local", TEST_PASSWORD, "127.0.0.1", "TestAgent")
        assertIs<AdminAuthResult.Success>(loginResult)

        // Should have 1 active session after login
        val sessionsBefore = repo.sessions.values.filter { it.revokedAt == null }
        assertEquals(1, sessionsBefore.size)

        // Refresh creates new session and revokes old
        service.refresh(loginResult.refreshToken, "127.0.0.1", "TestAgent")

        // Old session should be revoked, new session should be active
        val revokedSessions = repo.sessions.values.filter { it.revokedAt != null }
        assertTrue(revokedSessions.isNotEmpty(), "Old session should be revoked after refresh")

        val activeSessions = repo.sessions.values.filter { it.revokedAt == null }
        assertTrue(activeSessions.isNotEmpty(), "New session should be created after refresh")
    } }

    @Test
    fun `refresh token cannot be reused after rotation`() { runBlocking {
        val repo = FakeAdminUserRepo()
        repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        val loginResult = service.login("admin@test.local", TEST_PASSWORD, "127.0.0.1", "TestAgent")
        assertIs<AdminAuthResult.Success>(loginResult)

        // First refresh should succeed
        val firstRefresh = service.refresh(loginResult.refreshToken, null, null)
        assertNotNull(firstRefresh)

        // Second refresh with same token should fail (session revoked)
        val secondRefresh = service.refresh(loginResult.refreshToken, null, null)
        assertNull(secondRefresh, "Reused refresh token should be rejected")
    } }

    // ── Logout ──────────────────────────────────────────────────────────────

    @Test
    fun `logout revokes session by refresh token`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val user = repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        val loginResult = service.login("admin@test.local", TEST_PASSWORD, "127.0.0.1", "TestAgent")
        assertIs<AdminAuthResult.Success>(loginResult)

        assertTrue(repo.sessions.values.any { it.revokedAt == null })

        service.logout(loginResult.refreshToken, user.id, user.name, "127.0.0.1")

        // Session should be revoked after logout
        val activeSessions = repo.sessions.values.filter { it.revokedAt == null && it.userId == user.id }
        assertEquals(0, activeSessions.size, "No active sessions should remain after logout")
    } }

    // ── JWT access token structure ──────────────────────────────────────────

    @Test
    fun `access token is RS256 signed with correct issuer and audience`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val user = repo.addTestUser(role = AdminRole.FINANCE)
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        val result = service.login("admin@test.local", TEST_PASSWORD, null, null) as AdminAuthResult.Success

        val decoded = JWT.require(Algorithm.RSA256(testKeyPair.first as RSAPublicKey, null))
            .withIssuer(TEST_ISSUER)
            .withClaim("type", "admin_access")
            .build()
            .verify(result.accessToken)

        // Verify RS256 algorithm
        assertEquals("RS256", decoded.algorithm)

        // Verify all expected claims are present
        assertEquals(user.id.toString(), decoded.subject)
        assertEquals("admin@test.local", decoded.getClaim("email").asString())
        assertEquals("Test Admin", decoded.getClaim("name").asString())
        assertEquals("FINANCE", decoded.getClaim("role").asString())
        assertEquals(false, decoded.getClaim("mfa").asBoolean())
        assertEquals("admin_access", decoded.getClaim("type").asString())
        assertNotNull(decoded.issuedAt)
        assertNotNull(decoded.expiresAt)

        // Verify TTL is approximately 15 minutes (900_000ms)
        val ttlMs = decoded.expiresAt.time - decoded.issuedAt.time
        assertTrue(ttlMs in 895_000L..905_000L,
            "Access token TTL should be ~900000ms but was ${ttlMs}ms")
    } }

    @Test
    fun `access token contains mfa claim set to true when MFA is enabled`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val user = repo.addTestUser(mfaEnabled = true)
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        // MFA-enabled user gets MfaRequired first
        val mfaResult = service.login("admin@test.local", TEST_PASSWORD, null, null)
        assertIs<AdminAuthResult.MfaRequired>(mfaResult)

        // Complete MFA to get access token
        val completed = service.completeMfaLogin(mfaResult.pendingToken, "127.0.0.1", "TestAgent")
        assertNotNull(completed)

        val decoded = JWT.require(Algorithm.RSA256(testKeyPair.first as RSAPublicKey, null))
            .withIssuer(TEST_ISSUER)
            .withClaim("type", "admin_access")
            .build()
            .verify(completed.second.first)

        assertEquals(true, decoded.getClaim("mfa").asBoolean())
    } }

    // ── Email case insensitivity ────────────────────────────────────────────

    @Test
    fun `login is case-insensitive for email`() { runBlocking {
        val repo = FakeAdminUserRepo()
        repo.addTestUser(email = "Admin@Test.LOCAL")
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        val result = service.login("ADMIN@TEST.LOCAL", TEST_PASSWORD, null, null)
        assertIs<AdminAuthResult.Success>(result)
    } }

    // ── Login with null passwordHash ────────────────────────────────────────

    @Test
    fun `login fails for user with null passwordHash (OAuth-only user)`() { runBlocking {
        val repo = FakeAdminUserRepo()
        repo.addTestUser(passwordHash = null)
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        val result = service.login("admin@test.local", TEST_PASSWORD, null, null)
        assertIs<AdminAuthResult.InvalidCredentials>(result)
    } }

    // ── issueTokensForUser (Google OAuth flow) ──────────────────────────────

    @Test
    fun `issueTokensForUser produces valid access and refresh tokens`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val user = repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        val (accessToken, refreshToken) = service.issueTokensForUser(user, "10.0.0.1", "OAuthAgent")

        // Verify access token is valid
        val userId = service.verifyAccessToken(accessToken)
        assertEquals(user.id, userId)

        // Verify refresh token creates a session
        assertTrue(repo.sessions.isNotEmpty())

        // Verify refresh token is non-blank
        assertTrue(refreshToken.isNotBlank())
    } }

    // ── MFA pending token cannot be used as access token ────────────────────

    @Test
    fun `MFA pending token is rejected by verifyAccessToken`() { runBlocking {
        val repo = FakeAdminUserRepo()
        repo.addTestUser(mfaEnabled = true)
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        val result = service.login("admin@test.local", TEST_PASSWORD, null, null)
        assertIs<AdminAuthResult.MfaRequired>(result)

        // Pending token should not work as an access token
        val userId = service.verifyAccessToken(result.pendingToken)
        assertNull(userId, "MFA pending token must not be accepted as access token")
    } }

    // ── Multiple roles produce correct JWT claim ────────────────────────────

    @Test
    fun `login produces correct role claim for each admin role`() { runBlocking {
        for (role in listOf(AdminRole.ADMIN, AdminRole.OPERATOR, AdminRole.FINANCE, AdminRole.AUDITOR, AdminRole.HELPDESK)) {
            val repo = FakeAdminUserRepo()
            repo.addTestUser(email = "user-${role.name}@test.local", role = role)
            val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

            val result = service.login("user-${role.name}@test.local", TEST_PASSWORD, null, null)
            assertIs<AdminAuthResult.Success>(result)

            val decoded = JWT.require(Algorithm.RSA256(testKeyPair.first as RSAPublicKey, null))
                .withIssuer(TEST_ISSUER)
                .withClaim("type", "admin_access")
                .build()
                .verify(result.accessToken)

            assertEquals(role.name, decoded.getClaim("role").asString(),
                "JWT role claim should be ${role.name}")
        }
    } }

    // ── Failed attempts increment correctly ─────────────────────────────────

    @Test
    fun `failed attempt counter increments on each wrong password`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val user = repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        repeat(3) { i ->
            service.login("admin@test.local", "wrong-$i", null, null)
            assertEquals(i + 1, repo.failedAttemptsByUser[user.id])
        }
    } }

    @Test
    fun `lockout sets lockedUntil to approximately 15 minutes in the future`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val user = repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        val before = Instant.now().toEpochMilli()
        repeat(5) {
            service.login("admin@test.local", "wrong", null, null)
        }

        val lockedUntil = repo.lockedUntilByUser[user.id]
        assertNotNull(lockedUntil)

        // Should be ~15 minutes from now
        val lockDurationMs = lockedUntil - before
        assertTrue(lockDurationMs in 14 * 60 * 1000L..16 * 60 * 1000L,
            "Lockout duration should be ~15 min but was ${lockDurationMs}ms")
    } }

    // ── Token uniqueness ────────────────────────────────────────────────────

    @Test
    fun `each login produces unique access and refresh tokens`() { runBlocking {
        val repo = FakeAdminUserRepo()
        repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        val result1 = service.login("admin@test.local", TEST_PASSWORD, null, null) as AdminAuthResult.Success
        val result2 = service.login("admin@test.local", TEST_PASSWORD, null, null) as AdminAuthResult.Success

        assertNotEquals(result1.accessToken, result2.accessToken, "Access tokens should be unique per login")
        assertNotEquals(result1.refreshToken, result2.refreshToken, "Refresh tokens should be unique per login")
    } }

    // ── Bootstrap edge cases ────────────────────────────────────────────────

    @Test
    fun `bootstrap user can login immediately`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        service.bootstrap("first@admin.com", "First Admin", TEST_PASSWORD)

        val result = service.login("first@admin.com", TEST_PASSWORD, null, null)
        assertIs<AdminAuthResult.Success>(result)
        assertEquals("First Admin", result.user.name)
    } }

    // ── Refresh with inactive user ──────────────────────────────────────────

    @Test
    fun `refresh fails when user has been deactivated`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val user = repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo, noOpEmailService)

        val loginResult = service.login("admin@test.local", TEST_PASSWORD, "127.0.0.1", "TestAgent")
        assertIs<AdminAuthResult.Success>(loginResult)

        // Deactivate user
        repo.updateUser(user.id, isActive = false, name = null, role = null)

        // Refresh should fail because findById only returns active users
        val tokens = service.refresh(loginResult.refreshToken, null, null)
        assertNull(tokens, "Refresh should fail for deactivated user")
    } }
}
