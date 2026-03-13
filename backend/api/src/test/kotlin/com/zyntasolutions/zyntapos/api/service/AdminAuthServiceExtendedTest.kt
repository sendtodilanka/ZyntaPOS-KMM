package com.zyntasolutions.zyntapos.api.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zyntasolutions.zyntapos.api.auth.AdminRole
import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.repository.AdminUserRepository
import com.zyntasolutions.zyntapos.api.repository.ResetTokenRow
import com.zyntasolutions.zyntapos.api.repository.SessionRow
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Extended test suite for AdminAuthService (S3-6).
 *
 * Tests login happy path, bcrypt verification, failed attempt lockout,
 * MFA TOTP validation, password reset token generation/expiry, and session revocation.
 *
 * Uses in-memory fake repository (no DB required).
 */
class AdminAuthServiceExtendedTest {

    companion object {
        private const val TEST_SECRET = "test-secret-must-be-long-enough-for-hmac256-at-least-32-chars!"
        private const val TEST_ISSUER = "https://panel.test.local"

        private fun generateTestRsaKeyPair(): Pair<java.security.PublicKey, java.security.PrivateKey> {
            val kpg = java.security.KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048)
            val kp = kpg.generateKeyPair()
            return kp.public to kp.private
        }

        private fun testConfig(): AppConfig {
            val (pub, priv) = generateTestRsaKeyPair()
            return AppConfig(
                jwtIssuer = TEST_ISSUER,
                jwtAudience = "test",
                jwtPublicKey = pub,
                jwtPrivateKey = priv,
                accessTokenTtlMs = 3_600_000L,
                refreshTokenTtlMs = 86_400_000L,
                adminJwtSecret = TEST_SECRET,
                adminJwtIssuer = TEST_ISSUER,
                adminAccessTokenTtlMs = 900_000L,
                adminRefreshTokenTtlDays = 7L,
                googleClientId = "",
                googleClientSecret = "",
                googleRedirectUri = "",
                googleAllowedDomain = "",
                adminPanelUrl = "https://panel.test.local",
                redisUrl = "redis://localhost:6379",
                resendApiKey = "",
                emailFromAddress = "test@test.local",
                emailFromName = "Test",
            )
        }

        private val noOpAudit = AdminAuditService()

        private const val TEST_PASSWORD = "SecureP@ssw0rd!"
        private val TEST_BCRYPT_HASH = BCrypt.withDefaults().hashToString(4, TEST_PASSWORD.toCharArray())
    }

    /**
     * In-memory fake implementation of AdminUserRepository for unit testing.
     */
    private class FakeAdminUserRepo : AdminUserRepository {
        val users = mutableMapOf<UUID, AdminUserRow>()
        val sessions = mutableMapOf<UUID, SessionRow>()
        val resetTokens = mutableMapOf<String, ResetTokenRow>()
        var failedAttemptsByUser = mutableMapOf<UUID, Int>()
        var lockedUntilByUser = mutableMapOf<UUID, Long?>()

        override suspend fun findByEmail(email: String): AdminUserRow? =
            users.values.find { it.email == email.lowercase().trim() }

        override suspend fun findById(id: UUID): AdminUserRow? =
            users[id]?.takeIf { it.isActive }

        override suspend fun count(): Long = users.size.toLong()

        override suspend fun getLockedUntil(id: UUID): Long? = lockedUntilByUser[id]

        override suspend fun getFailedAttempts(id: UUID): Int = failedAttemptsByUser[id] ?: 0

        override suspend fun createUser(email: String, name: String, role: AdminRole, passwordHash: String): AdminUserRow {
            val now = java.time.Instant.now().toEpochMilli()
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
        }

        override suspend fun findSessionByTokenHash(tokenHash: String, nowMs: Long): SessionRow? =
            sessions.values.find { it.revokedAt == null && it.expiresAt > nowMs }

        override suspend fun revokeSession(sessionId: UUID, revokedAtMs: Long) {
            sessions[sessionId] = sessions[sessionId]!!.copy(revokedAt = revokedAtMs)
        }

        override suspend fun revokeAllSessions(userId: UUID, revokedAtMs: Long) {
            sessions.entries.filter { it.value.userId == userId }.forEach { (id, session) ->
                sessions[id] = session.copy(revokedAt = revokedAtMs)
            }
        }

        override suspend fun deleteUnusedResetTokens(userId: UUID) {
            resetTokens.entries.removeAll { it.value.adminUserId == userId && it.value.usedAt == null }
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

        fun addTestUser(
            email: String = "admin@test.local",
            password: String = TEST_PASSWORD,
            role: AdminRole = AdminRole.ADMIN,
            mfaEnabled: Boolean = false,
            isActive: Boolean = true,
        ): AdminUserRow {
            val user = AdminUserRow(
                id = UUID.randomUUID(),
                email = email.lowercase(),
                name = "Test Admin",
                role = role,
                passwordHash = BCrypt.withDefaults().hashToString(4, password.toCharArray()),
                mfaEnabled = mfaEnabled,
                isActive = isActive,
                lastLoginAt = null,
                createdAt = java.time.Instant.now().toEpochMilli(),
            )
            users[user.id] = user
            failedAttemptsByUser[user.id] = 0
            lockedUntilByUser[user.id] = null
            return user
        }
    }

    // ── Login happy path ────────────────────────────────────────────────────

    @Test
    fun `login succeeds with correct email and password`() { runBlocking {
        val repo = FakeAdminUserRepo()
        repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo)

        val result = service.login("admin@test.local", TEST_PASSWORD, "127.0.0.1", "TestAgent")

        assertIs<AdminAuthResult.Success>(result)
        assertEquals("admin@test.local", result.user.email)
        assertTrue(result.accessToken.isNotBlank())
        assertTrue(result.refreshToken.isNotBlank())
    } }

    @Test
    fun `login returns InvalidCredentials for wrong password`() { runBlocking {
        val repo = FakeAdminUserRepo()
        repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo)

        val result = service.login("admin@test.local", "wrong-password", null, null)

        assertIs<AdminAuthResult.InvalidCredentials>(result)
    } }

    @Test
    fun `login returns InvalidCredentials for non-existent email`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val service = AdminAuthService(testConfig(), noOpAudit, repo)

        val result = service.login("nobody@test.local", TEST_PASSWORD, null, null)

        assertIs<AdminAuthResult.InvalidCredentials>(result)
    } }

    @Test
    fun `login returns AccountInactive for disabled user`() { runBlocking {
        val repo = FakeAdminUserRepo()
        repo.addTestUser(isActive = false)
        val service = AdminAuthService(testConfig(), noOpAudit, repo)

        val result = service.login("admin@test.local", TEST_PASSWORD, null, null)

        assertIs<AdminAuthResult.AccountInactive>(result)
    } }

    // ── Brute-force lockout ─────────────────────────────────────────────────

    @Test
    fun `account locks after 5 failed attempts`() { runBlocking {
        val repo = FakeAdminUserRepo()
        repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo)

        // Fail 5 times
        repeat(5) {
            service.login("admin@test.local", "wrong", null, null)
        }

        // 6th attempt should be locked
        val result = service.login("admin@test.local", TEST_PASSWORD, null, null)
        assertIs<AdminAuthResult.AccountLocked>(result)
    } }

    @Test
    fun `successful login resets failed attempt counter`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val user = repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo)

        // Fail 3 times (below lockout threshold)
        repeat(3) {
            service.login("admin@test.local", "wrong", null, null)
        }

        // Login successfully
        val result = service.login("admin@test.local", TEST_PASSWORD, null, null)
        assertIs<AdminAuthResult.Success>(result)

        // Counter should be reset
        assertEquals(0, repo.failedAttemptsByUser[user.id])
    } }

    // ── MFA ─────────────────────────────────────────────────────────────────

    @Test
    fun `login returns MfaRequired when MFA is enabled`() { runBlocking {
        val repo = FakeAdminUserRepo()
        repo.addTestUser(mfaEnabled = true)
        val service = AdminAuthService(testConfig(), noOpAudit, repo)

        val result = service.login("admin@test.local", TEST_PASSWORD, null, null)

        assertIs<AdminAuthResult.MfaRequired>(result)
        assertTrue(result.pendingToken.isNotBlank())
    } }

    @Test
    fun `completeMfaLogin issues full tokens from valid pending token`() { runBlocking {
        val repo = FakeAdminUserRepo()
        repo.addTestUser(mfaEnabled = true)
        val service = AdminAuthService(testConfig(), noOpAudit, repo)

        val loginResult = service.login("admin@test.local", TEST_PASSWORD, null, null)
        assertIs<AdminAuthResult.MfaRequired>(loginResult)

        val mfaResult = service.completeMfaLogin(loginResult.pendingToken, "127.0.0.1", "TestAgent")
        assertNotNull(mfaResult)
        assertTrue(mfaResult.second.first.isNotBlank())  // access token
        assertTrue(mfaResult.second.second.isNotBlank()) // refresh token
    } }

    @Test
    fun `completeMfaLogin returns null for invalid pending token`() { runBlocking {
        val repo = FakeAdminUserRepo()
        repo.addTestUser(mfaEnabled = true)
        val service = AdminAuthService(testConfig(), noOpAudit, repo)

        val result = service.completeMfaLogin("invalid-token", null, null)
        assertNull(result)
    } }

    // ── Password reset ──────────────────────────────────────────────────────

    @Test
    fun `generatePasswordResetToken returns token for existing user`() { runBlocking {
        val repo = FakeAdminUserRepo()
        repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo)

        val token = service.generatePasswordResetToken("admin@test.local")
        assertNotNull(token)
        assertTrue(token.length >= 32) // 32 bytes hex = 64 chars
    } }

    @Test
    fun `generatePasswordResetToken returns null for non-existent email`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val service = AdminAuthService(testConfig(), noOpAudit, repo)

        val token = service.generatePasswordResetToken("nobody@test.local")
        assertNull(token)
    } }

    @Test
    fun `resetPassword succeeds with valid token`() { runBlocking {
        val repo = FakeAdminUserRepo()
        repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo)

        val token = service.generatePasswordResetToken("admin@test.local")!!
        val result = service.resetPassword(token, "NewP@ssw0rd!")
        assertTrue(result)
    } }

    @Test
    fun `resetPassword fails with invalid token`() { runBlocking {
        val repo = FakeAdminUserRepo()
        repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo)

        val result = service.resetPassword("invalid-token", "NewP@ssw0rd!")
        assertTrue(!result)
    } }

    @Test
    fun `resetPassword revokes all active sessions`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val user = repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo)

        // Login to create a session
        service.login("admin@test.local", TEST_PASSWORD, "127.0.0.1", "TestAgent")
        assertTrue(repo.sessions.isNotEmpty())

        // Reset password
        val token = service.generatePasswordResetToken("admin@test.local")!!
        service.resetPassword(token, "NewP@ssw0rd!")

        // All sessions should be revoked
        assertTrue(repo.sessions.values.all { it.revokedAt != null })
    } }

    // ── Session management ──────────────────────────────────────────────────

    @Test
    fun `revokeAllSessions marks all sessions as revoked`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val user = repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo)

        // Login twice to create sessions
        service.login("admin@test.local", TEST_PASSWORD, "127.0.0.1", "Agent1")
        service.login("admin@test.local", TEST_PASSWORD, "127.0.0.1", "Agent2")

        assertTrue(repo.sessions.size >= 2)

        service.revokeAllSessions(user.id)

        assertTrue(repo.sessions.values.all { it.revokedAt != null })
    } }

    @Test
    fun `bootstrap creates first admin user`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val service = AdminAuthService(testConfig(), noOpAudit, repo)

        assertTrue(service.needsBootstrap())

        val user = service.bootstrap("first@admin.com", "First Admin", TEST_PASSWORD)
        assertNotNull(user)
        assertEquals("first@admin.com", user.email)
        assertEquals(AdminRole.ADMIN, user.role)

        assertTrue(!service.needsBootstrap())
    } }

    @Test
    fun `bootstrap returns null when admin already exists`() { runBlocking {
        val repo = FakeAdminUserRepo()
        repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo)

        val result = service.bootstrap("another@admin.com", "Another", TEST_PASSWORD)
        assertNull(result)
    } }

    // ── Password length validation ──────────────────────────────────────────

    @Test
    fun `login rejects password longer than MAX_PASSWORD_LENGTH`() { runBlocking {
        val repo = FakeAdminUserRepo()
        repo.addTestUser()
        val service = AdminAuthService(testConfig(), noOpAudit, repo)

        val longPassword = "a".repeat(AdminAuthService.MAX_PASSWORD_LENGTH + 1)
        val result = service.login("admin@test.local", longPassword, null, null)

        assertIs<AdminAuthResult.InvalidCredentials>(result)
    } }

    // ── Access token JWT claims ─────────────────────────────────────────────

    @Test
    fun `login success produces JWT with correct claims`() { runBlocking {
        val repo = FakeAdminUserRepo()
        val user = repo.addTestUser(role = AdminRole.OPERATOR)
        val service = AdminAuthService(testConfig(), noOpAudit, repo)

        val result = service.login("admin@test.local", TEST_PASSWORD, null, null) as AdminAuthResult.Success

        val decoded = JWT.require(Algorithm.HMAC256(TEST_SECRET))
            .withIssuer(TEST_ISSUER)
            .withClaim("type", "admin_access")
            .build()
            .verify(result.accessToken)

        assertEquals(user.id.toString(), decoded.subject)
        assertEquals("admin@test.local", decoded.getClaim("email").asString())
        assertEquals("OPERATOR", decoded.getClaim("role").asString())
        assertNotNull(decoded.expiresAt)
    } }
}
