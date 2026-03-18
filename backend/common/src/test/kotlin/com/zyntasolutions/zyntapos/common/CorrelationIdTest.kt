package com.zyntasolutions.zyntapos.common

import io.ktor.client.request.*
import io.ktor.server.application.install
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the CorrelationId plugin -- verifies X-Request-ID propagation,
 * generation, and length validation.
 */
class CorrelationIdTest {

    @Test
    fun `generates X-Request-ID when none provided`() = testApplication {
        application { install(CorrelationId) }
        routing { get("/test") { call.respondText("ok") } }

        val response = client.get("/test")
        val requestId = response.headers["X-Request-ID"]
        assertNotNull(requestId, "X-Request-ID should be present in response")
        assertTrue(requestId.isNotBlank())
        assertTrue(requestId.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    @Test
    fun `preserves caller-provided X-Request-ID`() = testApplication {
        application { install(CorrelationId) }
        routing { get("/test") { call.respondText("ok") } }

        val response = client.get("/test") {
            header("X-Request-ID", "my-trace-id-123")
        }
        assertEquals("my-trace-id-123", response.headers["X-Request-ID"])
    }

    @Test
    fun `ignores blank X-Request-ID and generates new one`() = testApplication {
        application { install(CorrelationId) }
        routing { get("/test") { call.respondText("ok") } }

        val response = client.get("/test") {
            header("X-Request-ID", "   ")
        }
        val requestId = response.headers["X-Request-ID"]
        assertNotNull(requestId)
        assertTrue(requestId.trim().isNotEmpty())
        assertTrue(requestId != "   ")
    }

    @Test
    fun `rejects overly long X-Request-ID (over 128 chars)`() = testApplication {
        application { install(CorrelationId) }
        routing { get("/test") { call.respondText("ok") } }

        val longId = "a".repeat(200)
        val response = client.get("/test") {
            header("X-Request-ID", longId)
        }
        val requestId = response.headers["X-Request-ID"]
        assertNotNull(requestId)
        assertTrue(requestId != longId, "Should not preserve ID longer than 128 chars")
    }

    @Test
    fun `each request gets a unique ID when none provided`() = testApplication {
        application { install(CorrelationId) }
        routing { get("/test") { call.respondText("ok") } }

        val id1 = client.get("/test").headers["X-Request-ID"]
        val id2 = client.get("/test").headers["X-Request-ID"]

        assertNotNull(id1)
        assertNotNull(id2)
        assertTrue(id1 != id2, "Each request should get a unique correlation ID")
    }
}
