package com.zyntasolutions.zyntapos.security.license

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.FeatureConfig
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.model.ZyntaEdition
import com.zyntasolutions.zyntapos.domain.model.ZyntaFeature
import com.zyntasolutions.zyntapos.domain.repository.FeatureRegistryRepository
import com.zyntasolutions.zyntapos.security.rbac.RbacEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// LicenseManager Tests
// ─────────────────────────────────────────────────────────────────────────────

class LicenseManagerTest {

    private val rbacEngine = RbacEngine()

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildUser(role: Role) = User(
        id = "test-user-id",
        storeId = "store-1",
        name = "Test User",
        email = "test@example.com",
        role = role,
        isActive = true,
        pinHash = null,
        createdAt = Instant.fromEpochSeconds(0),
        updatedAt = Instant.fromEpochSeconds(0),
    )

    private fun licenseManager(repo: FeatureRegistryRepository) =
        LicenseManager(featureRepo = repo, rbacEngine = rbacEngine)

    // ── Test 1: canAccess returns false when feature is disabled ──────────────

    @Test
    fun `canAccess returns false when feature is disabled`() = runTest {
        val repo = InMemoryFeatureRegistryRepository()
        repo.setEnabled(ZyntaFeature.STAFF_HR, enabled = false, activatedAt = 0L, expiresAt = null)

        val result = licenseManager(repo).canAccess(ZyntaFeature.STAFF_HR, buildUser(Role.ADMIN)).first()

        assertFalse(result)
    }

    // ── Test 2: canAccess returns false when user lacks required permission ───

    @Test
    fun `canAccess returns false when feature enabled but user lacks permission`() = runTest {
        val repo = InMemoryFeatureRegistryRepository()
        repo.setEnabled(ZyntaFeature.STAFF_HR, enabled = true, activatedAt = 0L, expiresAt = null)
        // CASHIER does not hold MANAGE_STAFF
        val cashier = buildUser(Role.CASHIER)

        val result = licenseManager(repo).canAccess(ZyntaFeature.STAFF_HR, cashier).first()

        assertFalse(result)
    }

    // ── Test 3: canAccess returns true when feature enabled and user has permission

    @Test
    fun `canAccess returns true when feature enabled and user has permission`() = runTest {
        val repo = InMemoryFeatureRegistryRepository()
        repo.setEnabled(ZyntaFeature.STAFF_HR, enabled = true, activatedAt = 0L, expiresAt = null)

        // Role.ADMIN has all permissions, including MANAGE_STAFF required by STAFF_HR
        val result = licenseManager(repo).canAccess(ZyntaFeature.STAFF_HR, buildUser(Role.ADMIN)).first()

        assertTrue(result)
    }

    // ── Test 4: canAccess returns true for STANDARD feature with no required permissions

    @Test
    fun `canAccess returns true for STANDARD feature with no required permissions`() = runTest {
        val repo = InMemoryFeatureRegistryRepository()
        // AUTH is STANDARD with emptySet() requiredPermissions — any authenticated user can access
        repo.setEnabled(ZyntaFeature.AUTH, enabled = true, activatedAt = 0L, expiresAt = null)
        val cashier = buildUser(Role.CASHIER)

        val result = licenseManager(repo).canAccess(ZyntaFeature.AUTH, cashier).first()

        assertTrue(result)
    }

    // ── Test 5: isAlwaysAccessible returns true only for STANDARD edition features

    @Test
    fun `isAlwaysAccessible returns true only for STANDARD edition features`() {
        val manager = licenseManager(InMemoryFeatureRegistryRepository())

        ZyntaFeature.entries.forEach { feature ->
            val expected = feature.edition == ZyntaEdition.STANDARD
            val actual = manager.isAlwaysAccessible(feature)
            if (expected) {
                assertTrue(actual, "${feature.name} (STANDARD) should be always accessible")
            } else {
                assertFalse(actual, "${feature.name} (${feature.edition}) should NOT be always accessible")
            }
        }
    }

    // ── Local fake repository ─────────────────────────────────────────────────

    private class InMemoryFeatureRegistryRepository : FeatureRegistryRepository {

        private val state = MutableStateFlow<Map<ZyntaFeature, FeatureConfig>>(emptyMap())

        override fun observeAll(): Flow<List<FeatureConfig>> =
            state.map { it.values.toList() }

        override fun observe(feature: ZyntaFeature): Flow<FeatureConfig> =
            state.map { map ->
                map[feature] ?: FeatureConfig(
                    feature = feature,
                    isEnabled = false,
                    activatedAt = null,
                    expiresAt = null,
                    updatedAt = 0L,
                )
            }

        override suspend fun isEnabled(feature: ZyntaFeature): Boolean =
            state.value[feature]?.isEnabled ?: false

        override suspend fun setEnabled(
            feature: ZyntaFeature,
            enabled: Boolean,
            activatedAt: Long,
            expiresAt: Long?,
        ): Result<Unit> {
            state.value = state.value + (feature to FeatureConfig(
                feature = feature,
                isEnabled = enabled,
                activatedAt = activatedAt,
                expiresAt = expiresAt,
                updatedAt = 0L,
            ))
            return Result.Success(Unit)
        }

        override suspend fun initDefaults(now: Long): Result<Unit> {
            ZyntaFeature.entries.forEach { feature ->
                if (!state.value.containsKey(feature)) {
                    setEnabled(feature, enabled = false, activatedAt = now, expiresAt = null)
                }
            }
            return Result.Success(Unit)
        }
    }
}
