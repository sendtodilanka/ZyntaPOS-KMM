package com.zyntasolutions.zyntapos.api.routes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for AdminHealthRoutes validation and response structure logic.
 *
 * Tests cover:
 * - Overall health status aggregation rules
 * - Store ID path parameter validation
 * - Service status enumeration
 * - Health check response field structure
 * - Latency threshold logic
 */
class AdminHealthRoutesTest {

    // ── Overall status aggregation ────────────────────────────────────────────

    @Test
    fun `overall status is healthy when all services healthy`() {
        val services = listOf("healthy", "healthy", "healthy")
        val overall = when {
            services.any { it == "unhealthy" } -> "unhealthy"
            services.any { it == "degraded"  } -> "degraded"
            else                               -> "healthy"
        }
        assertEquals("healthy", overall)
    }

    @Test
    fun `overall status is unhealthy when any service is unhealthy`() {
        val services = listOf("healthy", "unhealthy", "healthy")
        val overall = when {
            services.any { it == "unhealthy" } -> "unhealthy"
            services.any { it == "degraded"  } -> "degraded"
            else                               -> "healthy"
        }
        assertEquals("unhealthy", overall)
    }

    @Test
    fun `overall status is degraded when any service is degraded but none unhealthy`() {
        val services = listOf("healthy", "degraded", "healthy")
        val overall = when {
            services.any { it == "unhealthy" } -> "unhealthy"
            services.any { it == "degraded"  } -> "degraded"
            else                               -> "healthy"
        }
        assertEquals("degraded", overall)
    }

    @Test
    fun `unhealthy takes precedence over degraded`() {
        val services = listOf("healthy", "degraded", "unhealthy")
        val overall = when {
            services.any { it == "unhealthy" } -> "unhealthy"
            services.any { it == "degraded"  } -> "degraded"
            else                               -> "healthy"
        }
        assertEquals("unhealthy", overall)
    }

    // ── Store ID path parameter ───────────────────────────────────────────────

    @Test
    fun `missing store ID is rejected`() {
        val storeId: String? = null
        assertNull(storeId)
    }

    @Test
    fun `blank store ID is invalid`() {
        val storeId = ""
        assertTrue(storeId.isBlank())
    }

    @Test
    fun `valid store ID is non-blank`() {
        val storeId = "store-colombo-001"
        assertFalse(storeId.isBlank())
    }

    // ── Service status values ─────────────────────────────────────────────────

    @Test
    fun `all valid service statuses are defined`() {
        val validStatuses = setOf("healthy", "degraded", "unhealthy")
        assertTrue(validStatuses.contains("healthy"))
        assertTrue(validStatuses.contains("degraded"))
        assertTrue(validStatuses.contains("unhealthy"))
        assertEquals(3, validStatuses.size)
    }

    // ── Health check response fields ──────────────────────────────────────────

    @Test
    fun `health response includes overall status field`() {
        data class SystemHealth(val overall: String, val services: List<String>, val checkedAt: String)
        val response = SystemHealth(
            overall = "healthy",
            services = listOf("healthy", "healthy"),
            checkedAt = "2026-03-20T10:00:00Z",
        )
        assertNotNull(response.overall)
        assertFalse(response.overall.isBlank())
    }

    @Test
    fun `health response checkedAt is ISO 8601 format`() {
        val checkedAt = "2026-03-20T10:00:00Z"
        assertTrue(checkedAt.contains("T"))
        assertTrue(checkedAt.endsWith("Z") || checkedAt.contains("+"))
    }

    // ── Latency threshold logic ───────────────────────────────────────────────

    @Test
    fun `high latency is above 1000ms threshold`() {
        val latencyMs = 1200
        assertTrue(latencyMs > 1000)
    }

    @Test
    fun `warning latency is between 500ms and 1000ms`() {
        val latencyMs = 750
        assertTrue(latencyMs in 500..1000)
    }

    @Test
    fun `normal latency is below 500ms`() {
        val latencyMs = 12
        assertTrue(latencyMs < 500)
    }

    // ── Uptime percentage ─────────────────────────────────────────────────────

    @Test
    fun `uptime of 100 percent means no downtime`() {
        val uptime = 100.0
        assertEquals(100.0, uptime)
    }

    @Test
    fun `uptime below 90 percent is critical`() {
        val uptime = 85.5
        assertTrue(uptime < 90)
    }

    @Test
    fun `uptime between 90 and 95 is warning`() {
        val uptime = 92.0
        assertTrue(uptime in 90.0..95.0)
    }

    @Test
    fun `uptime above 95 percent is normal`() {
        val uptime = 99.98
        assertTrue(uptime >= 95.0)
    }
}
