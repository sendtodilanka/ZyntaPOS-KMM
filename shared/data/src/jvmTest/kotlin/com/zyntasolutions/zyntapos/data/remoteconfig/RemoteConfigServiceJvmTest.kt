package com.zyntasolutions.zyntapos.data.remoteconfig

import com.zyntasolutions.zyntapos.core.config.RemoteConfigKeys
import com.zyntasolutions.zyntapos.core.config.RemoteEdition
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Unit tests for the JVM stub implementation of [RemoteConfigService].
 *
 * The JVM actual is a no-op stub — all getters return their defaults and
 * [fetchAndActivate] always returns false (no Firebase RC SDK on Desktop JVM).
 *
 * These tests verify:
 * 1. [fetchAndActivate] returns false without throwing
 * 2. All typed getters return the supplied default value
 * 3. [getEdition] returns [RemoteEdition.STARTER] as the safe default
 * 4. The service compiles and is injectable (implements [com.zyntasolutions.zyntapos.core.config.RemoteConfigProvider])
 */
class RemoteConfigServiceJvmTest {

    private val service = RemoteConfigService()

    @Test
    fun fetchAndActivate_returns_false_on_jvm_stub() = runTest {
        val result = service.fetchAndActivate()
        assertFalse(result, "JVM stub should always return false — no Firebase RC SDK")
    }

    @Test
    fun getString_returns_defaultValue_when_no_remote_config() {
        val result = service.getString(RemoteConfigKeys.EDITION_KEY, "FALLBACK")
        assertEquals("FALLBACK", result)
    }

    @Test
    fun getString_returns_empty_when_no_default_supplied() {
        val result = service.getString("any_key")
        assertEquals("", result)
    }

    @Test
    fun getBoolean_returns_defaultValue() {
        assertFalse(service.getBoolean(RemoteConfigKeys.MULTI_STORE_ENABLED))
        assertFalse(service.getBoolean(RemoteConfigKeys.ACCOUNTING_ENABLED))
        assertFalse(service.getBoolean(RemoteConfigKeys.STAFF_MODULE_ENABLED))
        assertFalse(service.getBoolean(RemoteConfigKeys.DIAGNOSTIC_ENABLED))
        assertFalse(service.getBoolean(RemoteConfigKeys.MAINTENANCE_MODE))
    }

    @Test
    fun getBoolean_returns_true_when_true_is_supplied_as_default() {
        val result = service.getBoolean("any_key", defaultValue = true)
        assertEquals(true, result)
    }

    @Test
    fun getLong_returns_defaultValue() {
        val result = service.getLong(RemoteConfigKeys.MIN_APP_VERSION, defaultValue = 99L)
        assertEquals(99L, result)
    }

    @Test
    fun getLong_returns_zero_when_no_default_supplied() {
        val result = service.getLong("any_key")
        assertEquals(0L, result)
    }

    @Test
    fun getEdition_returns_STARTER_as_safe_default_on_jvm() {
        val edition = service.getEdition()
        assertEquals(RemoteEdition.STARTER, edition,
            "JVM stub must default to STARTER — license server is source of truth on Desktop")
    }

    @Test
    fun service_implements_RemoteConfigProvider_interface() {
        // Compile-time check that the expect/actual wires correctly.
        // If RemoteConfigService doesn't implement RemoteConfigProvider, this won't compile.
        val provider: com.zyntasolutions.zyntapos.core.config.RemoteConfigProvider = service
        assertEquals(RemoteEdition.STARTER, provider.getEdition())
    }
}
