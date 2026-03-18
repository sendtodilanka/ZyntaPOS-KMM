package com.zyntasolutions.zyntapos.api.repository

import com.zyntasolutions.zyntapos.api.test.AbstractIntegrationTest
import com.zyntasolutions.zyntapos.api.test.TestFixtures
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for [PosUserRepositoryImpl] against a real PostgreSQL database.
 *
 * Covers: store lookup, user queries, brute-force lockout, and POS session management.
 */
class PosUserRepositoryTest : AbstractIntegrationTest() {

    private val repo: PosUserRepository = PosUserRepositoryImpl()

    private fun runTest(block: suspend () -> Unit) {
        kotlinx.coroutines.test.runTest { block() }
    }

    @Nested
    inner class StoreLookup {

        @Test
        fun `findStoreByLicenseKey_existingActiveStore_returnsStoreId`() = runTest {
            val storeId = TestFixtures.insertStore(licenseKey = "LK-ACTIVE-001")

            val result = repo.findStoreByLicenseKey("LK-ACTIVE-001")

            assertEquals(storeId, result)
        }

        @Test
        fun `findStoreByLicenseKey_inactiveStore_returnsNull`() = runTest {
            TestFixtures.insertStore(licenseKey = "LK-INACTIVE", isActive = false)

            val result = repo.findStoreByLicenseKey("LK-INACTIVE")

            assertNull(result)
        }

        @Test
        fun `findStoreByLicenseKey_nonExistent_returnsNull`() = runTest {
            val result = repo.findStoreByLicenseKey("LK-DOES-NOT-EXIST")

            assertNull(result)
        }
    }

    @Nested
    inner class UserQueries {

        @Test
        fun `findActiveUsersByStore_returnsOnlyActiveUsersInStore`() = runTest {
            val storeId = TestFixtures.insertStore()
            TestFixtures.insertUser(storeId = storeId, username = "active1", isActive = true)
            TestFixtures.insertUser(storeId = storeId, username = "active2", isActive = true)
            TestFixtures.insertUser(storeId = storeId, username = "inactive", isActive = false)

            val result = repo.findActiveUsersByStore(storeId)

            assertEquals(2, result.size)
            assertTrue(result.all { it.isActive })
        }

        @Test
        fun `findActiveUsersByStore_doesNotReturnUsersFromOtherStores`() = runTest {
            val storeA = TestFixtures.insertStore(id = "store-a")
            val storeB = TestFixtures.insertStore(id = "store-b")
            TestFixtures.insertUser(storeId = storeA, username = "user_a")
            TestFixtures.insertUser(storeId = storeB, username = "user_b")

            val result = repo.findActiveUsersByStore(storeA)

            assertEquals(1, result.size)
            assertEquals("user_a", result.single().username)
        }

        @Test
        fun `findActiveUsersByStore_nullStoreId_returnsAllActiveUsers`() = runTest {
            val storeA = TestFixtures.insertStore(id = "store-a")
            val storeB = TestFixtures.insertStore(id = "store-b")
            TestFixtures.insertUser(storeId = storeA, username = "user_a")
            TestFixtures.insertUser(storeId = storeB, username = "user_b")

            val result = repo.findActiveUsersByStore(null)

            assertEquals(2, result.size)
        }

        @Test
        fun `findActiveUserById_existingActiveUser_returnsUser`() = runTest {
            val storeId = TestFixtures.insertStore()
            val userId = TestFixtures.insertUser(storeId = storeId, username = "cashier1")

            val result = repo.findActiveUserById(userId)

            assertNotNull(result)
            assertEquals(userId, result.id)
            assertEquals("cashier1", result.username)
        }

        @Test
        fun `findActiveUserById_inactiveUser_returnsNull`() = runTest {
            val storeId = TestFixtures.insertStore()
            val userId = TestFixtures.insertUser(storeId = storeId, isActive = false)

            val result = repo.findActiveUserById(userId)

            assertNull(result)
        }

        @Test
        fun `findActiveUserById_nonExistent_returnsNull`() = runTest {
            val result = repo.findActiveUserById("user-does-not-exist")

            assertNull(result)
        }

        @Test
        fun `findActiveUserById_mapsAllFields`() = runTest {
            val storeId = TestFixtures.insertStore()
            val userId = TestFixtures.insertUser(
                storeId = storeId,
                username = "john",
                email = "john@test.local",
                name = "John Doe",
                role = "MANAGER",
                failedAttempts = 2,
            )

            val result = repo.findActiveUserById(userId)

            assertNotNull(result)
            assertEquals(storeId, result.storeId)
            assertEquals("john", result.username)
            assertEquals("john@test.local", result.email)
            assertEquals("John Doe", result.name)
            assertEquals("MANAGER", result.role)
            assertEquals(true, result.isActive)
            assertEquals(2, result.failedAttempts)
            assertNull(result.lockedUntil)
        }
    }

    @Nested
    inner class UserMutations {

        @Test
        fun `updatePasswordHash_changesHash`() = runTest {
            val storeId = TestFixtures.insertStore()
            val userId = TestFixtures.insertUser(storeId = storeId, passwordHash = "old-hash")

            repo.updatePasswordHash(userId, "new-hash")

            val user = repo.findActiveUserById(userId)
            assertNotNull(user)
            assertEquals("new-hash", user.passwordHash)
        }

        @Test
        fun `updateFailedAttempts_setsCountAndLockout`() = runTest {
            val storeId = TestFixtures.insertStore()
            val userId = TestFixtures.insertUser(storeId = storeId)
            val lockUntil = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15)

            repo.updateFailedAttempts(userId, 5, lockUntil)

            val user = repo.findActiveUserById(userId)
            assertNotNull(user)
            assertEquals(5, user.failedAttempts)
            assertNotNull(user.lockedUntil)
        }

        @Test
        fun `resetFailedAttempts_clearsCountAndLockout`() = runTest {
            val storeId = TestFixtures.insertStore()
            val lockUntil = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15)
            val userId = TestFixtures.insertUser(
                storeId = storeId,
                failedAttempts = 5,
                lockedUntil = lockUntil,
            )

            repo.resetFailedAttempts(userId)

            val user = repo.findActiveUserById(userId)
            assertNotNull(user)
            assertEquals(0, user.failedAttempts)
            assertNull(user.lockedUntil)
        }
    }

    @Nested
    inner class PosSessionManagement {

        @Test
        fun `insertAndFindPosSession_roundTrip`() = runTest {
            val storeId = TestFixtures.insertStore()
            val userId = TestFixtures.insertUser(storeId = storeId)
            val tokenHash = "sha256:test-token-hash"
            val expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(24)

            repo.insertPosSession(
                userId = userId,
                storeId = storeId,
                tokenHash = tokenHash,
                deviceId = "device-1",
                userAgent = "ZyntaPOS/1.0",
                ip = "192.168.1.100",
                expiresAt = expiresAt,
            )

            val session = repo.findPosSessionByTokenHash(tokenHash, OffsetDateTime.now(ZoneOffset.UTC))

            assertNotNull(session)
            assertEquals(userId, session.userId)
            assertEquals(storeId, session.storeId)
            assertEquals(tokenHash, session.tokenHash)
            assertEquals("device-1", session.deviceId)
            assertEquals("ZyntaPOS/1.0", session.userAgent)
            assertEquals("192.168.1.100", session.ipAddress)
        }

        @Test
        fun `findPosSessionByTokenHash_expiredSession_returnsNull`() = runTest {
            val storeId = TestFixtures.insertStore()
            val userId = TestFixtures.insertUser(storeId = storeId)
            val expiresAt = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)

            repo.insertPosSession(
                userId = userId,
                storeId = storeId,
                tokenHash = "expired-token",
                deviceId = null,
                userAgent = null,
                ip = null,
                expiresAt = expiresAt,
            )

            val session = repo.findPosSessionByTokenHash("expired-token", OffsetDateTime.now(ZoneOffset.UTC))

            assertNull(session)
        }

        @Test
        fun `revokePosSession_marksSessionRevoked`() = runTest {
            val storeId = TestFixtures.insertStore()
            val userId = TestFixtures.insertUser(storeId = storeId)
            val expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(24)

            repo.insertPosSession(
                userId = userId,
                storeId = storeId,
                tokenHash = "to-revoke",
                deviceId = null,
                userAgent = null,
                ip = null,
                expiresAt = expiresAt,
            )

            val session = repo.findPosSessionByTokenHash("to-revoke", OffsetDateTime.now(ZoneOffset.UTC))
            assertNotNull(session)

            repo.revokePosSession(session.id, OffsetDateTime.now(ZoneOffset.UTC))

            val revokedSession = repo.findPosSessionByTokenHash("to-revoke", OffsetDateTime.now(ZoneOffset.UTC))
            assertNull(revokedSession)
        }

        @Test
        fun `revokeAllPosSessions_revokesAllForUser`() = runTest {
            val storeId = TestFixtures.insertStore()
            val userId = TestFixtures.insertUser(storeId = storeId)
            val expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(24)

            repo.insertPosSession(userId = userId, storeId = storeId, tokenHash = "token-1",
                deviceId = null, userAgent = null, ip = null, expiresAt = expiresAt)
            repo.insertPosSession(userId = userId, storeId = storeId, tokenHash = "token-2",
                deviceId = null, userAgent = null, ip = null, expiresAt = expiresAt)

            repo.revokeAllPosSessions(userId, OffsetDateTime.now(ZoneOffset.UTC))

            assertNull(repo.findPosSessionByTokenHash("token-1", OffsetDateTime.now(ZoneOffset.UTC)))
            assertNull(repo.findPosSessionByTokenHash("token-2", OffsetDateTime.now(ZoneOffset.UTC)))
        }

        @Test
        fun `findPosSessionByTokenHash_nonExistentToken_returnsNull`() = runTest {
            val result = repo.findPosSessionByTokenHash("does-not-exist", OffsetDateTime.now(ZoneOffset.UTC))

            assertNull(result)
        }
    }

    @Nested
    inner class StoreUserUniqueness {

        @Test
        fun `sameUsernameInDifferentStores_allowed`() = runTest {
            val storeA = TestFixtures.insertStore(id = "store-a")
            val storeB = TestFixtures.insertStore(id = "store-b")

            TestFixtures.insertUser(storeId = storeA, username = "shared_name")
            TestFixtures.insertUser(storeId = storeB, username = "shared_name")

            val usersA = repo.findActiveUsersByStore(storeA)
            val usersB = repo.findActiveUsersByStore(storeB)

            assertEquals(1, usersA.size)
            assertEquals(1, usersB.size)
        }
    }
}
