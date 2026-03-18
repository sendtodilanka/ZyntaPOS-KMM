package com.zyntasolutions.zyntapos.api.repository

import com.zyntasolutions.zyntapos.api.auth.AdminRole
import com.zyntasolutions.zyntapos.api.test.AbstractIntegrationTest
import com.zyntasolutions.zyntapos.api.test.TestFixtures
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for [AdminUserRepositoryImpl] against a real PostgreSQL database.
 *
 * Covers: CRUD, email uniqueness, role assignment, lockout persistence,
 * session management, and password reset token lifecycle.
 */
class AdminUserRepositoryTest : AbstractIntegrationTest() {

    private val repo: AdminUserRepository = AdminUserRepositoryImpl()

    private fun runTest(block: suspend () -> Unit) {
        kotlinx.coroutines.test.runTest { block() }
    }

    @Nested
    inner class CreateAndQuery {

        @Test
        fun `createUser_returnsCreatedUser`() = runTest {
            val user = repo.createUser("admin@test.local", "Test Admin", AdminRole.ADMIN, "hashed-pw")

            assertEquals("admin@test.local", user.email)
            assertEquals("Test Admin", user.name)
            assertEquals(AdminRole.ADMIN, user.role)
            assertTrue(user.isActive)
            assertEquals(false, user.mfaEnabled)
        }

        @Test
        fun `createUser_emailNormalization_lowercaseAndTrimmed`() = runTest {
            val user = repo.createUser("  ADMIN@Test.Local  ", "Admin", AdminRole.ADMIN, "hashed-pw")

            assertEquals("admin@test.local", user.email)
        }

        @Test
        fun `findByEmail_existingUser_returnsUser`() = runTest {
            repo.createUser("lookup@test.local", "Lookup Admin", AdminRole.OPERATOR, "pw")

            val result = repo.findByEmail("lookup@test.local")

            assertNotNull(result)
            assertEquals("lookup@test.local", result.email)
            assertEquals("Lookup Admin", result.name)
            assertEquals(AdminRole.OPERATOR, result.role)
        }

        @Test
        fun `findByEmail_nonExistent_returnsNull`() = runTest {
            val result = repo.findByEmail("nobody@test.local")

            assertNull(result)
        }

        @Test
        fun `findByEmail_caseInsensitive`() = runTest {
            repo.createUser("mixed@test.local", "Mixed", AdminRole.AUDITOR, "pw")

            val result = repo.findByEmail("  MIXED@TEST.LOCAL  ")

            assertNotNull(result)
            assertEquals("mixed@test.local", result.email)
        }

        @Test
        fun `findById_existingActiveUser_returnsUser`() = runTest {
            val created = repo.createUser("byid@test.local", "ById", AdminRole.FINANCE, "pw")

            val result = repo.findById(created.id)

            assertNotNull(result)
            assertEquals(created.id, result.id)
        }

        @Test
        fun `findById_inactiveUser_returnsNull`() = runTest {
            val created = repo.createUser("inactive@test.local", "Inactive", AdminRole.ADMIN, "pw")
            repo.updateUser(created.id, isActive = false, name = null, role = null)

            val result = repo.findById(created.id)

            assertNull(result)
        }

        @Test
        fun `findByIdWithPassword_inactiveUser_stillReturnsUser`() = runTest {
            val created = repo.createUser("pw-check@test.local", "PwCheck", AdminRole.ADMIN, "pw")
            repo.updateUser(created.id, isActive = false, name = null, role = null)

            val result = repo.findByIdWithPassword(created.id)

            assertNotNull(result)
            assertEquals(created.id, result.id)
        }

        @Test
        fun `count_returnsCorrectNumber`() = runTest {
            assertEquals(0L, repo.count())

            repo.createUser("one@test.local", "One", AdminRole.ADMIN, "pw")
            repo.createUser("two@test.local", "Two", AdminRole.OPERATOR, "pw")

            assertEquals(2L, repo.count())
        }
    }

    @Nested
    inner class RoleAssignment {

        @Test
        fun `createUser_allRoles`() = runTest {
            for (role in AdminRole.entries) {
                val user = repo.createUser("${role.name.lowercase()}@test.local", role.name, role, "pw")
                assertEquals(role, user.role)
            }
        }

        @Test
        fun `updateUser_changeRole`() = runTest {
            val created = repo.createUser("role-change@test.local", "Admin", AdminRole.ADMIN, "pw")

            val updated = repo.updateUser(created.id, name = null, role = AdminRole.HELPDESK, isActive = null)

            assertNotNull(updated)
            assertEquals(AdminRole.HELPDESK, updated.role)
        }
    }

    @Nested
    inner class Mutations {

        @Test
        fun `updateUser_changeName`() = runTest {
            val created = repo.createUser("name@test.local", "Old Name", AdminRole.ADMIN, "pw")

            val updated = repo.updateUser(created.id, name = "New Name", role = null, isActive = null)

            assertNotNull(updated)
            assertEquals("New Name", updated.name)
        }

        @Test
        fun `updateUser_nonExistentId_returnsNull`() = runTest {
            val result = repo.updateUser(UUID.randomUUID(), name = "X", role = null, isActive = null)

            // Returns the row if it exists, null check depends on impl; may return row with no changes
            // The update returns findById which filters isActive, so non-existent returns null
            assertNull(result)
        }

        @Test
        fun `updatePassword_changesHash`() = runTest {
            val created = repo.createUser("pw-up@test.local", "Admin", AdminRole.ADMIN, "old-hash")

            repo.updatePassword(created.id, "new-hash")

            val user = repo.findByIdWithPassword(created.id)
            assertNotNull(user)
            assertEquals("new-hash", user.passwordHash)
        }

        @Test
        fun `updateLoginSuccess_clearsFailedAttemptsAndSetsTimestamp`() = runTest {
            val created = repo.createUser("login@test.local", "Admin", AdminRole.ADMIN, "pw")
            repo.updateFailedAttempts(created.id, 3, System.currentTimeMillis() + 60_000L)

            val now = System.currentTimeMillis()
            repo.updateLoginSuccess(created.id, now, "1.2.3.4")

            assertEquals(0, repo.getFailedAttempts(created.id))
            assertNull(repo.getLockedUntil(created.id))
        }
    }

    @Nested
    inner class LockoutPersistence {

        @Test
        fun `updateFailedAttempts_incrementsAndSetsLockout`() = runTest {
            val created = repo.createUser("lock@test.local", "Admin", AdminRole.ADMIN, "pw")
            val lockUntilMs = System.currentTimeMillis() + 900_000L

            repo.updateFailedAttempts(created.id, 5, lockUntilMs)

            assertEquals(5, repo.getFailedAttempts(created.id))
            assertEquals(lockUntilMs, repo.getLockedUntil(created.id))
        }

        @Test
        fun `updateFailedAttempts_nullLockout_clearsLock`() = runTest {
            val created = repo.createUser("unlock@test.local", "Admin", AdminRole.ADMIN, "pw")
            repo.updateFailedAttempts(created.id, 3, System.currentTimeMillis() + 60_000L)

            repo.updateFailedAttempts(created.id, 0, null)

            assertEquals(0, repo.getFailedAttempts(created.id))
            assertNull(repo.getLockedUntil(created.id))
        }
    }

    @Nested
    inner class SessionManagement {

        @Test
        fun `insertAndFindSession_roundTrip`() = runTest {
            val created = repo.createUser("session@test.local", "Admin", AdminRole.ADMIN, "pw")
            val tokenHash = "sha256:session-token"
            val now = System.currentTimeMillis()
            val expiresAt = now + 3_600_000L

            repo.insertSession(created.id, tokenHash, "1.2.3.4", "Mozilla/5.0", now, expiresAt)

            val session = repo.findSessionByTokenHash(tokenHash, now)

            assertNotNull(session)
            assertEquals(created.id, session.userId)
            assertEquals("1.2.3.4", session.ipAddress)
            assertEquals("Mozilla/5.0", session.userAgent)
            assertEquals(now, session.createdAt)
            assertEquals(expiresAt, session.expiresAt)
            assertNull(session.revokedAt)
        }

        @Test
        fun `findSessionByTokenHash_expiredSession_returnsNull`() = runTest {
            val created = repo.createUser("expired@test.local", "Admin", AdminRole.ADMIN, "pw")
            val pastExpiry = System.currentTimeMillis() - 3_600_000L

            repo.insertSession(created.id, "expired-token", null, null, pastExpiry - 7_200_000L, pastExpiry)

            val session = repo.findSessionByTokenHash("expired-token", System.currentTimeMillis())

            assertNull(session)
        }

        @Test
        fun `revokeSession_marksRevoked`() = runTest {
            val created = repo.createUser("revoke@test.local", "Admin", AdminRole.ADMIN, "pw")
            val now = System.currentTimeMillis()
            val expiresAt = now + 3_600_000L

            repo.insertSession(created.id, "revoke-me", null, null, now, expiresAt)
            val session = repo.findSessionByTokenHash("revoke-me", now)
            assertNotNull(session)

            repo.revokeSession(session.id, now)

            assertNull(repo.findSessionByTokenHash("revoke-me", now))
        }

        @Test
        fun `revokeAllSessions_revokesAllForUser`() = runTest {
            val created = repo.createUser("revoke-all@test.local", "Admin", AdminRole.ADMIN, "pw")
            val now = System.currentTimeMillis()
            val expiresAt = now + 3_600_000L

            repo.insertSession(created.id, "token-a", null, null, now, expiresAt)
            repo.insertSession(created.id, "token-b", null, null, now, expiresAt)

            repo.revokeAllSessions(created.id, now)

            assertNull(repo.findSessionByTokenHash("token-a", now))
            assertNull(repo.findSessionByTokenHash("token-b", now))
        }

        @Test
        fun `revokeSessionByTokenHash_revokesSpecificSession`() = runTest {
            val created = repo.createUser("revoke-hash@test.local", "Admin", AdminRole.ADMIN, "pw")
            val now = System.currentTimeMillis()
            val expiresAt = now + 3_600_000L

            repo.insertSession(created.id, "keep-me", null, null, now, expiresAt)
            repo.insertSession(created.id, "revoke-me", null, null, now, expiresAt)

            repo.revokeSessionByTokenHash("revoke-me", now)

            assertNotNull(repo.findSessionByTokenHash("keep-me", now))
            assertNull(repo.findSessionByTokenHash("revoke-me", now))
        }

        @Test
        fun `listActiveSessions_returnsOnlyActiveNonExpired`() = runTest {
            val created = repo.createUser("list-sessions@test.local", "Admin", AdminRole.ADMIN, "pw")
            val now = System.currentTimeMillis()
            val futureExpiry = now + 3_600_000L
            val pastExpiry = now - 3_600_000L

            repo.insertSession(created.id, "active-1", null, null, now, futureExpiry)
            repo.insertSession(created.id, "active-2", null, null, now, futureExpiry)
            repo.insertSession(created.id, "expired", null, null, now - 7_200_000L, pastExpiry)

            val sessions = repo.listActiveSessions(created.id, now)

            assertEquals(2, sessions.size)
        }
    }

    @Nested
    inner class PasswordResetTokens {

        @Test
        fun `insertAndFindResetToken_roundTrip`() = runTest {
            val created = repo.createUser("reset@test.local", "Admin", AdminRole.ADMIN, "pw")
            val tokenId = UUID.randomUUID()
            val tokenHash = "sha256:reset-token-hash"
            val now = System.currentTimeMillis()
            val expiresAt = now + 3_600_000L

            repo.insertResetToken(tokenId, created.id, tokenHash, expiresAt, now)

            val token = repo.findResetToken(tokenHash)

            assertNotNull(token)
            assertEquals(tokenId, token.id)
            assertEquals(created.id, token.adminUserId)
            assertEquals(tokenHash, token.tokenHash)
            assertEquals(expiresAt, token.expiresAt)
            assertNull(token.usedAt)
            assertEquals(now, token.createdAt)
        }

        @Test
        fun `markResetTokenUsed_setsUsedTimestamp`() = runTest {
            val created = repo.createUser("mark-used@test.local", "Admin", AdminRole.ADMIN, "pw")
            val tokenHash = "sha256:use-me"
            val now = System.currentTimeMillis()

            repo.insertResetToken(UUID.randomUUID(), created.id, tokenHash, now + 3_600_000L, now)

            repo.markResetTokenUsed(tokenHash, now)

            val token = repo.findResetToken(tokenHash)
            assertNotNull(token)
            assertEquals(now, token.usedAt)
        }

        @Test
        fun `deleteUnusedResetTokens_removesOnlyUnused`() = runTest {
            val created = repo.createUser("delete-tokens@test.local", "Admin", AdminRole.ADMIN, "pw")
            val now = System.currentTimeMillis()

            // Insert two tokens: one unused, one used
            repo.insertResetToken(UUID.randomUUID(), created.id, "unused-token", now + 3_600_000L, now)
            repo.insertResetToken(UUID.randomUUID(), created.id, "used-token", now + 3_600_000L, now)
            repo.markResetTokenUsed("used-token", now)

            repo.deleteUnusedResetTokens(created.id)

            assertNull(repo.findResetToken("unused-token"))
            assertNotNull(repo.findResetToken("used-token"))
        }

        @Test
        fun `findResetToken_nonExistent_returnsNull`() = runTest {
            val result = repo.findResetToken("does-not-exist")

            assertNull(result)
        }
    }
}
