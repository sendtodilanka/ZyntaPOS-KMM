package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.plugins.withCsrfProtection
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for the CSRF double-submit cookie plugin.
 *
 * Verifies:
 *  - XSRF-TOKEN cookie is set on GET responses
 *  - POST without XSRF header → 403
 *  - POST with matching header → passes through
 *  - Excluded paths bypass CSRF check
 */
class CsrfPluginTest {

    private fun buildTestApp() = testApplication {
        routing {
            withCsrfProtection {
                get("/test") {
                    call.respondText("ok")
                }
                post("/test") {
                    call.respondText("created", status = HttpStatusCode.Created)
                }
                post("/admin/auth/login") {
                    // Should bypass CSRF (excluded path)
                    call.respondText("login-ok")
                }
                post("/admin/auth/refresh") {
                    call.respondText("refresh-ok")
                }
            }
        }
    }

    @Test
    fun `GET response sets XSRF-TOKEN cookie`() = buildTestApp().run {
        val response = client.get("/test")
        assertEquals(HttpStatusCode.OK, response.status)

        val setCookie = response.headers.getAll(HttpHeaders.SetCookie)
        assertNotNull(setCookie, "Expected Set-Cookie header")
        assertTrue(
            setCookie.any { it.startsWith("XSRF-TOKEN=") },
            "Expected XSRF-TOKEN cookie in Set-Cookie: $setCookie"
        )
    }

    @Test
    fun `POST without XSRF header returns 403`() = buildTestApp().run {
        val response = client.post("/test") {
            // No X-XSRF-Token header, no cookie
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("CSRF_INVALID"), "Expected CSRF_INVALID error code in body: $body")
    }

    @Test
    fun `POST to excluded login path bypasses CSRF check`() = buildTestApp().run {
        val response = client.post("/admin/auth/login") {
            // No XSRF header — but login is excluded
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST to excluded refresh path bypasses CSRF check`() = buildTestApp().run {
        val response = client.post("/admin/auth/refresh") {
            // No XSRF header — but refresh is excluded
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST with correct XSRF header passes through`() = buildTestApp().run {
        // First GET to obtain the token from the cookie
        val getResponse = client.get("/test")
        val setCookieHeader = getResponse.headers.getAll(HttpHeaders.SetCookie)
            ?.firstOrNull { it.startsWith("XSRF-TOKEN=") }
            ?: error("No XSRF-TOKEN cookie set")

        val token = setCookieHeader
            .substringAfter("XSRF-TOKEN=")
            .substringBefore(";")
            .trim()

        // Now POST with the cookie + matching header
        val postResponse = client.post("/test") {
            cookie("XSRF-TOKEN", token)
            header("X-XSRF-Token", token)
        }
        assertEquals(HttpStatusCode.Created, postResponse.status)
    }

    @Test
    fun `POST with wrong XSRF header value returns 403`() = buildTestApp().run {
        val getResponse = client.get("/test")
        val setCookieHeader = getResponse.headers.getAll(HttpHeaders.SetCookie)
            ?.firstOrNull { it.startsWith("XSRF-TOKEN=") }
            ?: error("No XSRF-TOKEN cookie set")

        val realToken = setCookieHeader
            .substringAfter("XSRF-TOKEN=")
            .substringBefore(";")
            .trim()

        // Send wrong value in header
        val postResponse = client.post("/test") {
            cookie("XSRF-TOKEN", realToken)
            header("X-XSRF-Token", "wrong-value")
        }
        assertEquals(HttpStatusCode.Forbidden, postResponse.status)
    }
}
