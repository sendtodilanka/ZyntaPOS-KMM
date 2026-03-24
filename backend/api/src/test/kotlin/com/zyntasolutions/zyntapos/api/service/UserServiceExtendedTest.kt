package com.zyntasolutions.zyntapos.api.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.models.UserInfo
import com.zyntasolutions.zyntapos.api.repository.PosSessionRow
import com.zyntasolutions.zyntapos.api.repository.PosUserRepository
import com.zyntasolutions.zyntapos.api.repository.PosUserRow
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * C4: Extended unit tests for UserService — login happy/sad paths, brute-force lockout,
 * token issuance, token refresh, and session management.
 *
 * Uses an in-memory fake PosUserRepository (no DB required).
 */
class UserServiceExtendedTest {

    companion object {
        private const val TEST_ISSUER = "https://api.test.local"
        private const val TEST_AUDIENCE = "test"
        private const val TEST_EMAIL = "cashier@store.test"
        private const val TEST_PASSWORD = "CashierPin123!"
        private const val TEST_STORE_ID = "store-001"
        private const val TEST_LICENSE_KEY = "LK-test-001"

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
                jwtAudience = TEST_AUDIENCE,
                jwtPublicKey = pub,
                jwtPrivateKey = priv,
                accessTokenTtlMs = 3_600_000L,     // 1 hour
                refreshTokenTtlMs = 86_400_000L,    // 1 day
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

        /** Create a SHA-256 hash in PinManager format (base64url-salt:hex-hash). */
        private fun createSha256Hash(password: String): String {
            val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
            val saltBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(salt)
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(salt)
            digest.update(password.toByteArray(Charsets.UTF_8))
            val hashHex = digest.digest().joinToString("") { "%02x".format(it) }
            return "$saltBase64:$hashHex"
        }
    }

    /**
     * In-memory fake PosUserRepository for unit testing.
     */
    private class FakePosUserRepo : PosUserRepository {
        val users = mutableListOf<PosUserRow>()
        val sessions = mutableMapOf<UUID, FakeSessionEntry>()
        private val storesByLicenseKey = mutableMapOf<String, String>()

        data class FakeSessionEntry(
            val id: UUID,
            val userId: String,
            val storeId: String,
            val tokenHash: String,
            val deviceId: String?,
            val userAgent: String?,
            val ipAddress: String?,
            val expiresAt: OffsetDateTime,
            var revokedAt: OffsetDateTime? = null,
        )

        fun addStore(licenseKey: String, storeId: String) {
            storesByLicenseKey[licenseKey] = storeId
        }

        fun addUser(
            email: String = TEST_EMAIL,
            password: String = TEST_PASSWORD,
            storeId: String = TEST_STORE_ID,
            role: String = "CASHIER",
            isActive: Boolean = true,
            useBcrypt: Boolean = false,
        ): PosUserRow {
            val hash = if (useBcrypt) {
                BCrypt.withDefaults().hashToString(4, password.toCharArray())
            } else {
                createSha256Hash(password)
            }
            // For SHA-256, we need a known salt — create a proper hash matching the password
            val finalHash = if (!useBcrypt) {
                // Use fixed salt for deterministic tests
                val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
                val saltBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(salt)
                val digest = MessageDigest.getInstance("SHA-256")
                digest.update(salt)
                digest.update(password.toByteArray(Charsets.UTF_8))
                val hashHex = digest.digest().joinToString("") { "%02x".format(it) }
                "$saltBase64:$hashHex"
            } else {
                hash
            }
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val user = PosUserRow(
                id = "user-${UUID.randomUUID().toString().take(8)}",
                storeId = storeId,
                username = email.substringBefore("@"),
                email = email,
                name = "Test User",
                passwordHash = finalHash,
                role = role,
                isActive = isActive,
                failedAttempts = 0,
                lockedUntil = null,
                createdAt = now,
                updatedAt = now,
            )
            users.add(user)
            return user
        }

        override suspend fun findStoreByLicenseKey(licenseKey: String): String? =
            storesByLicenseKey[licenseKey]

        override suspend fun findActiveUsersByStore(storeId: String?): List<PosUserRow> =
            users.filter { it.isActive && (storeId == null || it.storeId == storeId) }

        override suspend fun findActiveUserById(userId: String): PosUserRow? =
            users.find { it.id == userId && it.isActive }

        override suspend fun updatePasswordHash(userId: String, hash: String) {
            val index = users.indexOfFirst { it.id == userId }
            if (index >= 0) users[index] = users[index].copy(passwordHash = hash)
        }

        override suspend fun updateFailedAttempts(userId: String, count: Int, lockedUntil: OffsetDateTime?) {
            val index = users.indexOfFirst { it.id == userId }
            if (index >= 0) users[index] = users[index].copy(failedAttempts = count, lockedUntil = lockedUntil)
        }

        override suspend fun resetFailedAttempts(userId: String) {
            val index = users.indexOfFirst { it.id == userId }
            if (index >= 0) users[index] = users[index].copy(failedAttempts = 0, lockedUntil = null)
        }

        override suspend fun insertPosSession(
            userId: String, storeId: String, tokenHash: String,
            deviceId: String?, userAgent: String?, ip: String?, expiresAt: OffsetDateTime,
        ) {
            val id = UUID.randomUUID()
            sessions[id] = FakeSessionEntry(id, userId, storeId, tokenHash, deviceId, userAgent, ip, expiresAt)
        }

        override suspend fun findPosSessionByTokenHash(tokenHash: String, now: OffsetDateTime): PosSessionRow? {
            val entry = sessions.values.find {
                it.tokenHash == tokenHash && it.revokedAt == null && it.expiresAt.isAfter(now)
            } ?: return null
            return PosSessionRow(entry.id, entry.userId, entry.storeId, entry.tokenHash, entry.deviceId, entry.userAgent, entry.ipAddress)
        }

        override suspend fun revokePosSession(sessionId: UUID, revokedAt: OffsetDateTime) {
            sessions[sessionId] = sessions[sessionId]!!.copy(revokedAt = revokedAt)
        }

        override suspend fun revokeAllPosSessions(userId: String, revokedAt: OffsetDateTime) {
            sessions.entries.filter { it.value.userId == userId }.forEach { (id, entry) ->
                sessions[id] = entry.copy(revokedAt = revokedAt)
            }
        }
    }

    // ── Login happy path ────────────────────────────────────────────────────

    @Test
    fun `authenticate succeeds with correct email and SHA-256 password`() = runBlocking<Unit> {
        val repo = FakePosUserRepo()
        val user = repo.addUser()
        val service = UserService(posUserRepo = repo)

        val result = service.authenticate(TEST_EMAIL, TEST_PASSWORD, null)

        assertNotNull(result, "authenticate should return UserInfo for valid credentials")
        assertEquals(user.id, result.id)
        assertEquals(TEST_EMAIL, result.email)
        assertEquals("CASHIER", result.role)
        assertEquals(TEST_STORE_ID, result.storeId)
    }

    @Test
    fun `authenticate succeeds with bcrypt password`() = runBlocking<Unit> {
        val repo = FakePosUserRepo()
        repo.addUser(useBcrypt = true)
        val service = UserService(posUserRepo = repo)

        val result = service.authenticate(TEST_EMAIL, TEST_PASSWORD, null)

        assertNotNull(result)
        assertEquals(TEST_EMAIL, result.email)
    }

    // ── Login failure paths ─────────────────────────────────────────────────

    @Test
    fun `authenticate returns null for wrong password`() = runBlocking<Unit> {
        val repo = FakePosUserRepo()
        repo.addUser()
        val service = UserService(posUserRepo = repo)

        val result = service.authenticate(TEST_EMAIL, "wrong-password", null)

        assertNull(result, "authenticate should return null for wrong password")
    }

    @Test
    fun `authenticate returns null for non-existent email`() = runBlocking<Unit> {
        val repo = FakePosUserRepo()
        repo.addUser()
        val service = UserService(posUserRepo = repo)

        val result = service.authenticate("nobody@store.test", TEST_PASSWORD, null)

        assertNull(result)
    }

    @Test
    fun `authenticate returns null for inactive user`() = runBlocking<Unit> {
        val repo = FakePosUserRepo()
        repo.addUser(isActive = false)
        val service = UserService(posUserRepo = repo)

        // Inactive users are filtered out by findActiveUsersByStore
        val result = service.authenticate(TEST_EMAIL, TEST_PASSWORD, null)

        assertNull(result)
    }

    @Test
    fun `authenticate returns null for invalid license key`() = runBlocking<Unit> {
        val repo = FakePosUserRepo()
        repo.addUser()
        val service = UserService(posUserRepo = repo)

        val result = service.authenticate(TEST_EMAIL, TEST_PASSWORD, "invalid-license")

        assertNull(result, "should fail when license key is not found")
    }

    @Test
    fun `authenticate scopes to store when license key is provided`() = runBlocking<Unit> {
        val repo = FakePosUserRepo()
        repo.addStore(TEST_LICENSE_KEY, TEST_STORE_ID)
        repo.addUser(storeId = TEST_STORE_ID)
        repo.addUser(email = "other@other-store.test", storeId = "store-002")
        val service = UserService(posUserRepo = repo)

        val result = service.authenticate(TEST_EMAIL, TEST_PASSWORD, TEST_LICENSE_KEY)

        assertNotNull(result)
        assertEquals(TEST_STORE_ID, result.storeId)
    }

    // ── Brute-force lockout ─────────────────────────────────────────────────

    @Test
    fun `account locks after 5 failed attempts`() = runBlocking<Unit> {
        val repo = FakePosUserRepo()
        repo.addUser(useBcrypt = true)
        val service = UserService(posUserRepo = repo)

        // Fail 5 times
        repeat(5) {
            service.authenticate(TEST_EMAIL, "wrong", null)
        }

        // Verify the user is now locked
        val user = repo.users.first()
        assertTrue(user.failedAttempts >= 5, "Failed attempts should be >= 5, got ${user.failedAttempts}")
        assertNotNull(user.lockedUntil, "lockedUntil should be set after 5 failed attempts")
    }

    @Test
    fun `locked account rejects correct password`() = runBlocking<Unit> {
        val repo = FakePosUserRepo()
        repo.addUser(useBcrypt = true)
        val service = UserService(posUserRepo = repo)

        // Lock the account
        repeat(5) {
            service.authenticate(TEST_EMAIL, "wrong", null)
        }

        // Try with correct password — should still fail because locked
        val result = service.authenticate(TEST_EMAIL, TEST_PASSWORD, null)

        assertNull(result, "locked account should reject even correct password")
    }

    @Test
    fun `successful login resets failed attempt counter`() = runBlocking<Unit> {
        val repo = FakePosUserRepo()
        repo.addUser(useBcrypt = true)
        val service = UserService(posUserRepo = repo)

        // Fail 3 times (below lockout threshold)
        repeat(3) {
            service.authenticate(TEST_EMAIL, "wrong", null)
        }
        assertEquals(3, repo.users.first().failedAttempts)

        // Login successfully
        val result = service.authenticate(TEST_EMAIL, TEST_PASSWORD, null)
        assertNotNull(result)

        // Counter should be reset
        assertEquals(0, repo.users.first().failedAttempts)
    }

    @Test
    fun `four failed attempts do not trigger lockout`() = runBlocking<Unit> {
        val repo = FakePosUserRepo()
        repo.addUser(useBcrypt = true)
        val service = UserService(posUserRepo = repo)

        repeat(4) {
            service.authenticate(TEST_EMAIL, "wrong", null)
        }

        val user = repo.users.first()
        assertEquals(4, user.failedAttempts)
        assertNull(user.lockedUntil, "lockedUntil should be null after only 4 failed attempts")

        // 5th attempt with correct password should succeed
        val result = service.authenticate(TEST_EMAIL, TEST_PASSWORD, null)
        assertNotNull(result)
    }

    // ── Token issuance ──────────────────────────────────────────────────────

    @Test
    fun `issueTokens produces valid RS256 JWT with correct claims`() = runBlocking<Unit> {
        val repo = FakePosUserRepo()
        val service = UserService(posUserRepo = repo)
        val config = testConfig()

        val userInfo = UserInfo(
            id = "user-test",
            email = TEST_EMAIL,
            name = "Test User",
            role = "CASHIER",
            storeId = TEST_STORE_ID,
            isActive = true,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )

        val (accessToken, refreshToken, expiresIn) = service.issueTokens(
            userInfo, config, "device-1", "127.0.0.1", "TestAgent"
        )

        assertTrue(accessToken.isNotBlank())
        assertTrue(refreshToken.isNotBlank())
        assertEquals(3600L, expiresIn) // 1 hour in seconds

        // Verify the JWT
        val decoded = JWT.require(Algorithm.RSA256(testKeyPair.first as RSAPublicKey, null))
            .withIssuer(TEST_ISSUER)
            .withAudience(TEST_AUDIENCE)
            .build()
            .verify(accessToken)

        assertEquals("user-test", decoded.subject)
        assertEquals("CASHIER", decoded.getClaim("role").asString())
        assertEquals(TEST_STORE_ID, decoded.getClaim("storeId").asString())
        assertEquals("access", decoded.getClaim("type").asString())
        assertNotNull(decoded.id, "JWT should have a jti claim")
        assertNotNull(decoded.expiresAt)
    }

    @Test
    fun `issueTokens creates a POS session record`() = runBlocking<Unit> {
        val repo = FakePosUserRepo()
        val service = UserService(posUserRepo = repo)
        val config = testConfig()

        val userInfo = UserInfo(
            id = "user-test", email = TEST_EMAIL, name = "Test",
            role = "CASHIER", storeId = TEST_STORE_ID,
            isActive = true, createdAt = 0, updatedAt = 0,
        )

        service.issueTokens(userInfo, config, "device-1", "127.0.0.1", "Agent")

        assertEquals(1, repo.sessions.size, "Should create one POS session")
        val session = repo.sessions.values.first()
        assertEquals("user-test", session.userId)
        assertEquals(TEST_STORE_ID, session.storeId)
    }

    // ── Token refresh ───────────────────────────────────────────────────────

    @Test
    fun `refreshTokens succeeds with valid refresh token`() = runBlocking<Unit> {
        val repo = FakePosUserRepo()
        val user = repo.addUser(useBcrypt = true)
        val service = UserService(posUserRepo = repo)
        val config = testConfig()

        // First authenticate and get tokens
        val userInfo = service.authenticate(TEST_EMAIL, TEST_PASSWORD, null)!!
        val (_, refreshToken, _) = service.issueTokens(userInfo, config, "device-1", "127.0.0.1", "Agent")

        // Refresh
        val response = service.refreshTokens(refreshToken, config, "127.0.0.1", "Agent")

        assertNotNull(response, "refreshTokens should succeed with valid token")
        assertTrue(response.accessToken.isNotBlank())
        assertNotNull(response.refreshToken)
        assertTrue(response.refreshToken!!.isNotBlank())
    }

    @Test
    fun `refreshTokens returns null for invalid refresh token`() = runBlocking<Unit> {
        val repo = FakePosUserRepo()
        repo.addUser(useBcrypt = true)
        val service = UserService(posUserRepo = repo)
        val config = testConfig()

        val response = service.refreshTokens("invalid-token-value", config, null, null)

        assertNull(response, "refreshTokens should return null for invalid token")
    }

    @Test
    fun `refreshTokens revokes old session on rotation`() = runBlocking<Unit> {
        val repo = FakePosUserRepo()
        repo.addUser(useBcrypt = true)
        val service = UserService(posUserRepo = repo)
        val config = testConfig()

        val userInfo = service.authenticate(TEST_EMAIL, TEST_PASSWORD, null)!!
        val (_, refreshToken, _) = service.issueTokens(userInfo, config, "device-1", "127.0.0.1", "Agent")

        assertEquals(1, repo.sessions.size)

        // Refresh the token
        service.refreshTokens(refreshToken, config, "127.0.0.1", "Agent")

        // Old session should be revoked, new one created
        val revokedSessions = repo.sessions.values.filter { it.revokedAt != null }
        assertEquals(1, revokedSessions.size, "Old session should be revoked after refresh")
        assertEquals(2, repo.sessions.size, "Should have 2 sessions total (1 revoked + 1 new)")
    }

    @Test
    fun `refreshTokens rejects already-used refresh token`() = runBlocking<Unit> {
        val repo = FakePosUserRepo()
        repo.addUser(useBcrypt = true)
        val service = UserService(posUserRepo = repo)
        val config = testConfig()

        val userInfo = service.authenticate(TEST_EMAIL, TEST_PASSWORD, null)!!
        val (_, refreshToken, _) = service.issueTokens(userInfo, config, "device-1", "127.0.0.1", "Agent")

        // Use the refresh token once
        val firstRefresh = service.refreshTokens(refreshToken, config, "127.0.0.1", "Agent")
        assertNotNull(firstRefresh)

        // Try to reuse the same refresh token — should fail (single-use rotation)
        val secondRefresh = service.refreshTokens(refreshToken, config, "127.0.0.1", "Agent")
        assertNull(secondRefresh, "Reusing a consumed refresh token should fail")
    }

    // ── Session revocation ──────────────────────────────────────────────────

    @Test
    fun `revokeAllSessions marks all sessions as revoked`() = runBlocking<Unit> {
        val repo = FakePosUserRepo()
        repo.addUser(useBcrypt = true)
        val service = UserService(posUserRepo = repo)
        val config = testConfig()

        val userInfo = service.authenticate(TEST_EMAIL, TEST_PASSWORD, null)!!

        // Create multiple sessions
        service.issueTokens(userInfo, config, "device-1", "127.0.0.1", "Agent1")
        service.issueTokens(userInfo, config, "device-2", "127.0.0.1", "Agent2")
        assertEquals(2, repo.sessions.size)

        service.revokeAllSessions(userInfo.id)

        assertTrue(repo.sessions.values.all { it.revokedAt != null },
            "All sessions should be revoked")
    }

    @Test
    fun `revokeAllSessions makes refresh tokens unusable`() = runBlocking<Unit> {
        val repo = FakePosUserRepo()
        repo.addUser(useBcrypt = true)
        val service = UserService(posUserRepo = repo)
        val config = testConfig()

        val userInfo = service.authenticate(TEST_EMAIL, TEST_PASSWORD, null)!!
        val (_, refreshToken, _) = service.issueTokens(userInfo, config, "device-1", "127.0.0.1", "Agent")

        service.revokeAllSessions(userInfo.id)

        val response = service.refreshTokens(refreshToken, config, "127.0.0.1", "Agent")
        assertNull(response, "Refresh should fail after all sessions are revoked")
    }

    // ── JWT token properties ────────────────────────────────────────────────

    @Test
    fun `access token has correct TTL`() = runBlocking<Unit> {
        val repo = FakePosUserRepo()
        val service = UserService(posUserRepo = repo)
        val config = testConfig()

        val userInfo = UserInfo(
            id = "user-ttl", email = TEST_EMAIL, name = "Test",
            role = "ADMIN", storeId = TEST_STORE_ID,
            isActive = true, createdAt = 0, updatedAt = 0,
        )

        val (accessToken, _, _) = service.issueTokens(userInfo, config, null, null, null)

        val decoded = JWT.require(Algorithm.RSA256(testKeyPair.first as RSAPublicKey, null))
            .withIssuer(TEST_ISSUER)
            .build()
            .verify(accessToken)

        val ttlMs = decoded.expiresAt.time - System.currentTimeMillis()
        // Should be roughly 1 hour (3600000ms), with some tolerance for execution time
        assertTrue(ttlMs in 3_590_000L..3_600_500L,
            "Access token TTL should be ~1 hour but was ${ttlMs}ms")
    }

    @Test
    fun `each issueTokens call produces unique jti`() = runBlocking<Unit> {
        val repo = FakePosUserRepo()
        val service = UserService(posUserRepo = repo)
        val config = testConfig()

        val userInfo = UserInfo(
            id = "user-jti", email = TEST_EMAIL, name = "Test",
            role = "CASHIER", storeId = TEST_STORE_ID,
            isActive = true, createdAt = 0, updatedAt = 0,
        )

        val (token1, _, _) = service.issueTokens(userInfo, config, null, null, null)
        val (token2, _, _) = service.issueTokens(userInfo, config, null, null, null)

        val jti1 = JWT.decode(token1).id
        val jti2 = JWT.decode(token2).id
        assertTrue(jti1 != jti2, "Each token should have a unique jti")
    }

    @Test
    fun `access token includes role and storeId claims`() = runBlocking<Unit> {
        val repo = FakePosUserRepo()
        val service = UserService(posUserRepo = repo)
        val config = testConfig()

        val userInfo = UserInfo(
            id = "user-claims", email = TEST_EMAIL, name = "Manager",
            role = "MANAGER", storeId = "store-mgr-001",
            isActive = true, createdAt = 0, updatedAt = 0,
        )

        val (accessToken, _, _) = service.issueTokens(userInfo, config, null, null, null)
        val decoded = JWT.decode(accessToken)

        assertEquals("MANAGER", decoded.getClaim("role").asString())
        assertEquals("store-mgr-001", decoded.getClaim("storeId").asString())
        assertEquals("access", decoded.getClaim("type").asString())
    }
}
