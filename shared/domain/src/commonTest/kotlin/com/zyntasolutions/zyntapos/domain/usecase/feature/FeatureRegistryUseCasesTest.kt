package com.zyntasolutions.zyntapos.domain.usecase.feature

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.ZyntaEdition
import com.zyntasolutions.zyntapos.domain.model.ZyntaFeature
import com.zyntasolutions.zyntapos.domain.fakes.FakeFeatureRegistryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [GetAllFeatureConfigsUseCase], [IsFeatureEnabledUseCase],
 * and [SetFeatureEnabledUseCase].
 *
 * Coverage:
 * 1. STANDARD feature cannot be disabled → [ValidationException] with STANDARD_ALWAYS_ENABLED rule
 * 2. PREMIUM feature can be disabled and re-enabled
 * 3. ENTERPRISE feature can be enabled and disabled
 * 4. [IsFeatureEnabledUseCase] returns false for disabled enterprise feature (default)
 * 5. [IsFeatureEnabledUseCase] returns true for enabled standard feature (default)
 * 6. [GetAllFeatureConfigsUseCase] returns exactly 23 features
 * 7. shouldFail=true causes [SetFeatureEnabledUseCase] to return [Result.Error]
 */
class FeatureRegistryUseCasesTest {

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun makeRepo() = FakeFeatureRegistryRepository()

    private fun standardFeature(): ZyntaFeature =
        ZyntaFeature.entries.first { it.edition == ZyntaEdition.STANDARD }

    private fun premiumFeature(): ZyntaFeature =
        ZyntaFeature.entries.first { it.edition == ZyntaEdition.PREMIUM }

    private fun enterpriseFeature(): ZyntaFeature =
        ZyntaFeature.entries.first { it.edition == ZyntaEdition.ENTERPRISE }

    // ─── SetFeatureEnabledUseCase: STANDARD guard ─────────────────────────────

    @Test
    fun `STANDARD feature cannot be disabled - returns ValidationException`() = runTest {
        val repo = makeRepo()
        val useCase = SetFeatureEnabledUseCase(repo)
        val feature = standardFeature()

        val result = useCase(feature = feature, enabled = false, now = 1_000L, expiresAt = null)

        assertIs<Result.Error>(result)
        val ex = result.exception
        assertIs<ValidationException>(ex)
        assertEquals("feature", ex.field)
        assertEquals("STANDARD_ALWAYS_ENABLED", ex.rule)
        // Repository must NOT have been mutated
        assertTrue(
            repo.storage[feature]?.isEnabled == true,
            "STANDARD feature must remain enabled after rejected disable",
        )
    }

    @Test
    fun `STANDARD feature enabling is a no-op success - already enabled`() = runTest {
        val repo = makeRepo()
        val useCase = SetFeatureEnabledUseCase(repo)
        val feature = standardFeature()

        val result = useCase(feature = feature, enabled = true, now = 2_000L, expiresAt = null)

        assertIs<Result.Success<Unit>>(result)
        assertTrue(repo.storage[feature]?.isEnabled == true)
    }

    // ─── SetFeatureEnabledUseCase: PREMIUM ────────────────────────────────────

    @Test
    fun `PREMIUM feature can be disabled`() = runTest {
        val repo = makeRepo()
        val useCase = SetFeatureEnabledUseCase(repo)
        val feature = premiumFeature()
        // Default: PREMIUM features start enabled
        assertTrue(repo.storage[feature]?.isEnabled == true)

        val result = useCase(feature = feature, enabled = false, now = 3_000L, expiresAt = null)

        assertIs<Result.Success<Unit>>(result)
        assertFalse(repo.storage[feature]?.isEnabled ?: true)
    }

    @Test
    fun `PREMIUM feature can be re-enabled after being disabled`() = runTest {
        val repo = makeRepo()
        val useCase = SetFeatureEnabledUseCase(repo)
        val feature = premiumFeature()

        // Disable first
        useCase(feature = feature, enabled = false, now = 4_000L, expiresAt = null)
        assertFalse(repo.storage[feature]?.isEnabled ?: true)

        // Re-enable
        val result = useCase(feature = feature, enabled = true, now = 5_000L, expiresAt = null)

        assertIs<Result.Success<Unit>>(result)
        assertTrue(repo.storage[feature]?.isEnabled == true)
    }

    // ─── SetFeatureEnabledUseCase: ENTERPRISE ─────────────────────────────────

    @Test
    fun `ENTERPRISE feature can be enabled`() = runTest {
        val repo = makeRepo()
        val useCase = SetFeatureEnabledUseCase(repo)
        val feature = enterpriseFeature()
        // Default: ENTERPRISE features start disabled
        assertFalse(repo.storage[feature]?.isEnabled ?: false)

        val result = useCase(feature = feature, enabled = true, now = 6_000L, expiresAt = null)

        assertIs<Result.Success<Unit>>(result)
        assertTrue(repo.storage[feature]?.isEnabled == true)
    }

    @Test
    fun `ENTERPRISE feature can be disabled after being enabled`() = runTest {
        val repo = makeRepo()
        val useCase = SetFeatureEnabledUseCase(repo)
        val feature = enterpriseFeature()

        // Enable first
        useCase(feature = feature, enabled = true, now = 7_000L, expiresAt = null)
        assertTrue(repo.storage[feature]?.isEnabled == true)

        // Disable
        val result = useCase(feature = feature, enabled = false, now = 8_000L, expiresAt = null)

        assertIs<Result.Success<Unit>>(result)
        assertFalse(repo.storage[feature]?.isEnabled ?: true)
    }

    // ─── IsFeatureEnabledUseCase ──────────────────────────────────────────────

    @Test
    fun `IsFeatureEnabledUseCase returns false for disabled enterprise feature by default`() = runTest {
        val repo = makeRepo()
        val useCase = IsFeatureEnabledUseCase(repo)
        val feature = enterpriseFeature()

        assertFalse(useCase(feature))
    }

    @Test
    fun `IsFeatureEnabledUseCase returns true for enabled standard feature by default`() = runTest {
        val repo = makeRepo()
        val useCase = IsFeatureEnabledUseCase(repo)
        val feature = standardFeature()

        assertTrue(useCase(feature))
    }

    @Test
    fun `IsFeatureEnabledUseCase returns true after enterprise feature is enabled`() = runTest {
        val repo = makeRepo()
        val setUseCase = SetFeatureEnabledUseCase(repo)
        val isEnabledUseCase = IsFeatureEnabledUseCase(repo)
        val feature = enterpriseFeature()

        setUseCase(feature = feature, enabled = true, now = 9_000L, expiresAt = null)

        assertTrue(isEnabledUseCase(feature))
    }

    // ─── GetAllFeatureConfigsUseCase ──────────────────────────────────────────

    @Test
    fun `GetAllFeatureConfigsUseCase returns all 24 features`() = runTest {
        val repo = makeRepo()
        val useCase = GetAllFeatureConfigsUseCase(repo)

        val configs = useCase().first()

        assertEquals(24, configs.size)
    }

    @Test
    fun `GetAllFeatureConfigsUseCase returns one config per ZyntaFeature`() = runTest {
        val repo = makeRepo()
        val useCase = GetAllFeatureConfigsUseCase(repo)

        val configs = useCase().first()
        val features = configs.map { it.feature }.toSet()

        assertEquals(ZyntaFeature.entries.toSet(), features)
    }

    @Test
    fun `GetAllFeatureConfigsUseCase default - standard and premium enabled, enterprise disabled`() = runTest {
        val repo = makeRepo()
        val useCase = GetAllFeatureConfigsUseCase(repo)

        val configs = useCase().first()

        configs.forEach { config ->
            when (config.feature.edition) {
                ZyntaEdition.STANDARD, ZyntaEdition.PREMIUM ->
                    assertTrue(config.isEnabled, "${config.feature} should be enabled by default")
                ZyntaEdition.ENTERPRISE ->
                    assertFalse(config.isEnabled, "${config.feature} should be disabled by default")
            }
        }
    }

    // ─── shouldFail propagation ───────────────────────────────────────────────

    @Test
    fun `SetFeatureEnabledUseCase propagates DB error when shouldFail is true`() = runTest {
        val repo = makeRepo().also { it.shouldFail = true }
        val useCase = SetFeatureEnabledUseCase(repo)
        val feature = premiumFeature()

        val result = useCase(feature = feature, enabled = false, now = 10_000L, expiresAt = null)

        assertIs<Result.Error>(result)
    }

    @Test
    fun `SetFeatureEnabledUseCase enterprise enable propagates DB error when shouldFail is true`() = runTest {
        val repo = makeRepo().also { it.shouldFail = true }
        val useCase = SetFeatureEnabledUseCase(repo)
        val feature = enterpriseFeature()

        val result = useCase(feature = feature, enabled = true, now = 11_000L, expiresAt = null)

        assertIs<Result.Error>(result)
    }
}
