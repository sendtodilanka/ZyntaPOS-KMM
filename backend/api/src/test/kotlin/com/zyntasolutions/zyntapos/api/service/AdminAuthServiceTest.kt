package com.zyntasolutions.zyntapos.api.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zyntasolutions.zyntapos.api.auth.AdminRole
import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.repository.AdminAuditRepository
import com.zyntasolutions.zyntapos.api.repository.AdminUserRepository
import com.zyntasolutions.zyntapos.api.repository.AuditEntryInput
import com.zyntasolutions.zyntapos.api.repository.AuditFilter
import com.zyntasolutions.zyntapos.api.repository.AuditPage
import com.zyntasolutions.zyntapos.api.repository.AuditEntryRow
import com.zyntasolutions.zyntapos.api.repository.ResetTokenRow
import com.zyntasolutions.zyntapos.api.repository.SessionRow
import java.security.interfaces.RSAPublicKey
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for AdminAuthService security properties.
 *
 * Coverage:
 *  - G2: Password length limit constant (128 chars)
 *  - JWT signing and verification (access + MFA pending tokens)
 *  - Token type segregation (MFA pending ≠ access)
 *  - Tamper detection
 */
class AdminAuthServiceTest {

    companion object {
        private const val TEST_ISSUER = "https://panel.test.local"

        private fun generateTestRsaKeyPair(): Pair<java.security.PublicKey, java.security.PrivateKey> {
            val kpg = java.security.KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048)
            val kp = kpg.generateKeyPair()
            return kp.public to kp.private
        }

        // Shared test keypair so tests can verify tokens outside the service
        private val testKeyPair = generateTestRsaKeyPair()

        private fun testConfig(): AppConfig {
            val (pub, priv) = testKeyPair
            return AppConfig(
                jwtIssuer              = TEST_ISSUER,
                jwtAudience            = "test",
                jwtPublicKey           = pub,
                jwtPrivateKey          = priv,
                accessTokenTtlMs       = 3_600_000L,
                refreshTokenTtlMs      = 86_400_000L,
                adminJwtPublicKey      = pub,
                adminJwtPrivateKey     = priv,
                adminJwtIssuer         = TEST_ISSUER,
                adminAccessTokenTtlMs  = 900_000L,
                adminRefreshTokenTtlDays = 7L,
                adminPanelUrl          = "https://panel.test.local",
                redisUrl               = "redis://localhost:6379",
                resendApiKey           = "",
                emailFromAddress       = "test@test.local",
                emailFromName          = "Test",
                playIntegrityPackageName = "",
                playIntegrityApiKey    = "",
                inboundEmailHmacSecret = "",
                chatwootApiUrl         = "",
                chatwootApiToken       = "",
                chatwootAccountId      = "",
                chatwootInboxId        = "",
            )
        }

        /**
         * No-op repository for tests that only exercise pure JWT methods.
         * Methods that access DB will throw — tests that call them need Testcontainers.
         */
        private val noOpRepo = object : AdminUserRepository {
            override suspend fun findByEmail(email: String) = throw NotImplementedError()
            override suspend fun findById(id: UUID) = throw NotImplementedError()
            override suspend fun count() = throw NotImplementedError()
            override suspend fun getLockedUntil(id: UUID) = throw NotImplementedError()
            override suspend fun getFailedAttempts(id: UUID) = throw NotImplementedError()
            override suspend fun getLastLoginIp(id: UUID): String? = throw NotImplementedError()
            override suspend fun getPasswordChangedAt(id: UUID): Long? = throw NotImplementedError()
            override suspend fun createUser(email: String, name: String, role: AdminRole, passwordHash: String) = throw NotImplementedError()
            override suspend fun updateLoginSuccess(id: UUID, timestampMs: Long, ip: String?) = throw NotImplementedError()
            override suspend fun updateFailedAttempts(id: UUID, count: Int, lockedUntilMs: Long?) = throw NotImplementedError()
            override suspend fun updatePassword(id: UUID, passwordHash: String) = throw NotImplementedError()
            override suspend fun updateUser(id: UUID, name: String?, role: AdminRole?, isActive: Boolean?) = throw NotImplementedError()
            override suspend fun insertSession(userId: UUID, tokenHash: String, ip: String?, userAgent: String?, createdAt: Long, expiresAt: Long) = throw NotImplementedError()
            override suspend fun findSessionByTokenHash(tokenHash: String, nowMs: Long): SessionRow? = throw NotImplementedError()
            override suspend fun revokeSession(sessionId: UUID, revokedAtMs: Long) = throw NotImplementedError()
            override suspend fun revokeAllSessions(userId: UUID, revokedAtMs: Long) = throw NotImplementedError()
            override suspend fun revokeSessionByTokenHash(tokenHash: String, revokedAtMs: Long) = throw NotImplementedError()
            override suspend fun listActiveSessions(userId: UUID, nowMs: Long): List<AdminSessionRow> = throw NotImplementedError()
            override suspend fun findByIdWithPassword(id: UUID): AdminUserRow? = throw NotImplementedError()
            override suspend fun deleteUnusedResetTokens(userId: UUID) = throw NotImplementedError()
            override suspend fun insertResetToken(id: UUID, userId: UUID, tokenHash: String, expiresAt: Long, createdAt: Long) = throw NotImplementedError()
            override suspend fun findResetToken(tokenHash: String): ResetTokenRow? = throw NotImplementedError()
            override suspend fun markResetTokenUsed(tokenHash: String, usedAt: Long) = throw NotImplementedError()
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

        private fun testUser(mfaEnabled: Boolean = false) = AdminUserRow(
            id           = UUID.randomUUID(),
            email        = "admin@test.local",
            name         = "Test Admin",
            role         = AdminRole.ADMIN,
            passwordHash = null,
            mfaEnabled   = mfaEnabled,
            isActive     = true,
            lastLoginAt  = null,
            createdAt    = System.currentTimeMillis()
        )
    }

    // ── G2: Password length limit ─────────────────────────────────────────────

    @Test
    fun `MAX_PASSWORD_LENGTH is 128`() {
        assertEquals(128, AdminAuthService.MAX_PASSWORD_LENGTH,
            "bcrypt silently truncates at 72 bytes; we enforce a hard 128-char ceiling")
    }

    @Test
    fun `password exactly at limit is within bound`() {
        assertTrue("a".repeat(128).length <= AdminAuthService.MAX_PASSWORD_LENGTH)
    }

    @Test
    fun `password over limit exceeds MAX_PASSWORD_LENGTH`() {
        assertTrue("a".repeat(129).length > AdminAuthService.MAX_PASSWORD_LENGTH)
    }

    // ── MFA pending token ─────────────────────────────────────────────────────

    @Test
    fun `issueMfaPendingToken produces valid RS256 JWT with type=admin_mfa_pending`() {
        val service = AdminAuthService(testConfig(), noOpAudit, noOpRepo, noOpEmailService)
        val user = testUser(mfaEnabled = true)

        val token = service.issueMfaPendingToken(user)

        val decoded = JWT.require(Algorithm.RSA256(testKeyPair.first as RSAPublicKey, null))
            .withIssuer(TEST_ISSUER)
            .withClaim("type", "admin_mfa_pending")
            .build()
            .verify(token)

        assertEquals(user.id.toString(), decoded.subject)
        assertEquals("admin_mfa_pending", decoded.getClaim("type").asString())
        assertNotNull(decoded.expiresAt)
    }

    @Test
    fun `issueMfaPendingToken expires in roughly 2 minutes`() {
        val service = AdminAuthService(testConfig(), noOpAudit, noOpRepo, noOpEmailService)
        val token = service.issueMfaPendingToken(testUser())

        val decoded = JWT.require(Algorithm.RSA256(testKeyPair.first as RSAPublicKey, null))
            .withIssuer(TEST_ISSUER)
            .withClaim("type", "admin_mfa_pending")
            .build()
            .verify(token)

        val ttlMs = decoded.expiresAt.time - decoded.issuedAt.time
        assertTrue(ttlMs in 115_000L..125_000L,
            "MFA pending TTL should be ~120s but was ${ttlMs}ms")
    }

    @Test
    fun `verifyMfaPendingToken returns userId for valid token`() {
        val service = AdminAuthService(testConfig(), noOpAudit, noOpRepo, noOpEmailService)
        val user = testUser()
        val token = service.issueMfaPendingToken(user)

        assertEquals(user.id, service.verifyMfaPendingToken(token))
    }

    @Test
    fun `verifyMfaPendingToken returns null for access-type token (type segregation)`() {
        val service = AdminAuthService(testConfig(), noOpAudit, noOpRepo, noOpEmailService)
        val (pub, priv) = testKeyPair

        val accessTyped = JWT.create()
            .withIssuer(TEST_ISSUER)
            .withSubject(UUID.randomUUID().toString())
            .withClaim("type", "admin_access")
            .withIssuedAt(java.util.Date())
            .withExpiresAt(java.util.Date(System.currentTimeMillis() + 60_000))
            .sign(Algorithm.RSA256(pub as RSAPublicKey, priv as java.security.interfaces.RSAPrivateKey))

        assertNull(service.verifyMfaPendingToken(accessTyped),
            "An access token must not be accepted as an MFA pending token")
    }

    @Test
    fun `verifyMfaPendingToken returns null for expired token`() {
        val service = AdminAuthService(testConfig(), noOpAudit, noOpRepo, noOpEmailService)
        val (pub, priv) = testKeyPair

        val expired = JWT.create()
            .withIssuer(TEST_ISSUER)
            .withSubject(UUID.randomUUID().toString())
            .withClaim("type", "admin_mfa_pending")
            .withIssuedAt(java.util.Date(System.currentTimeMillis() - 300_000))
            .withExpiresAt(java.util.Date(System.currentTimeMillis() - 1_000))
            .sign(Algorithm.RSA256(pub as RSAPublicKey, priv as java.security.interfaces.RSAPrivateKey))

        assertNull(service.verifyMfaPendingToken(expired))
    }

    // ── Access token ─────────────────────────────────────────────────────────

    @Test
    fun `verifyAccessToken returns null for MFA pending token (type segregation)`() {
        val service = AdminAuthService(testConfig(), noOpAudit, noOpRepo, noOpEmailService)
        val pendingToken = service.issueMfaPendingToken(testUser())

        assertNull(service.verifyAccessToken(pendingToken),
            "An MFA pending token must not be accepted as an access token")
    }

    @Test
    fun `verifyAccessToken returns null for tampered JWT`() {
        val service = AdminAuthService(testConfig(), noOpAudit, noOpRepo, noOpEmailService)
        val tampered = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhdHRhY2tlciIsInR5cGUiOiJhZG1pbl9hY2Nlc3MifQ.fakesig"

        assertNull(service.verifyAccessToken(tampered))
    }

    @Test
    fun `verifyAccessToken returns null for blank string`() {
        val service = AdminAuthService(testConfig(), noOpAudit, noOpRepo, noOpEmailService)
        assertNull(service.verifyAccessToken(""))
    }

    @Test
    fun `verifyAccessToken returns null for token signed with wrong key`() {
        val service = AdminAuthService(testConfig(), noOpAudit, noOpRepo, noOpEmailService)
        // Generate a different RSA keypair
        val wrongKeyPair = generateTestRsaKeyPair()

        val wrongKey = JWT.create()
            .withIssuer(TEST_ISSUER)
            .withSubject(UUID.randomUUID().toString())
            .withClaim("type", "admin_access")
            .withIssuedAt(java.util.Date())
            .withExpiresAt(java.util.Date(System.currentTimeMillis() + 60_000))
            .sign(Algorithm.RSA256(wrongKeyPair.first as RSAPublicKey, wrongKeyPair.second as java.security.interfaces.RSAPrivateKey))

        assertNull(service.verifyAccessToken(wrongKey))
    }

    @Test
    fun `verifyAccessToken rejects old HS256 token after RS256 migration`() {
        val service = AdminAuthService(testConfig(), noOpAudit, noOpRepo, noOpEmailService)

        val hs256Token = JWT.create()
            .withIssuer(TEST_ISSUER)
            .withSubject(UUID.randomUUID().toString())
            .withClaim("type", "admin_access")
            .withIssuedAt(java.util.Date())
            .withExpiresAt(java.util.Date(System.currentTimeMillis() + 60_000))
            .sign(Algorithm.HMAC256("any-old-symmetric-secret"))

        assertNull(service.verifyAccessToken(hs256Token),
            "HS256 tokens must be rejected after RS256 migration")
    }
}
