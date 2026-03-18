package com.zyntasolutions.zyntapos.api.plugins

import com.zyntasolutions.zyntapos.api.auth.AdminAuthorizationException
import com.zyntasolutions.zyntapos.api.models.ErrorResponse
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for StatusPages exception mapping -- verifies correct HTTP status codes
 * and error response bodies for each exception type.
 */
class StatusPagesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `IllegalArgumentException maps to 400 Bad Request`() = testApplication {
        install(ContentNegotiation) { json() }
        application { configureStatusPages() }
        routing { get("/test") { throw IllegalArgumentException("bad param") } }

        val response = client.get("/test")
        assertEquals(HttpStatusCode.BadRequest, response.status)

        val body = json.decodeFromString<ErrorResponse>(response.bodyAsText())
        assertEquals("INVALID_REQUEST", body.code)
        assertEquals("bad param", body.message)
    }

    @Test
    fun `IllegalArgumentException with null message uses default`() = testApplication {
        install(ContentNegotiation) { json() }
        application { configureStatusPages() }
        routing { get("/test") { throw IllegalArgumentException() } }

        val response = client.get("/test")
        assertEquals(HttpStatusCode.BadRequest, response.status)

        val body = json.decodeFromString<ErrorResponse>(response.bodyAsText())
        assertEquals("Invalid request", body.message)
    }

    @Test
    fun `AdminAuthorizationException maps to 403 Forbidden`() = testApplication {
        install(ContentNegotiation) { json() }
        application { configureStatusPages() }
        routing { get("/test") { throw AdminAuthorizationException("no access") } }

        val response = client.get("/test")
        assertEquals(HttpStatusCode.Forbidden, response.status)

        val body = json.decodeFromString<ErrorResponse>(response.bodyAsText())
        assertEquals("FORBIDDEN", body.code)
        assertEquals("no access", body.message)
    }

    @Test
    fun `SecurityException maps to 403 Forbidden with generic message`() = testApplication {
        install(ContentNegotiation) { json() }
        application { configureStatusPages() }
        routing { get("/test") { throw SecurityException("token tampered") } }

        val response = client.get("/test")
        assertEquals(HttpStatusCode.Forbidden, response.status)

        val body = json.decodeFromString<ErrorResponse>(response.bodyAsText())
        assertEquals("FORBIDDEN", body.code)
        assertEquals("Access denied", body.message)
    }

    @Test
    fun `unhandled Throwable maps to 500 Internal Server Error`() = testApplication {
        install(ContentNegotiation) { json() }
        application { configureStatusPages() }
        routing { get("/test") { throw RuntimeException("unexpected crash") } }

        val response = client.get("/test")
        assertEquals(HttpStatusCode.InternalServerError, response.status)

        val body = json.decodeFromString<ErrorResponse>(response.bodyAsText())
        assertEquals("INTERNAL_ERROR", body.code)
        assertEquals("An internal error occurred", body.message)
    }

    @Test
    fun `normal routes return 200 when no exception`() = testApplication {
        install(ContentNegotiation) { json() }
        application { configureStatusPages() }
        routing { get("/test") { call.respondText("ok") } }

        val response = client.get("/test")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
