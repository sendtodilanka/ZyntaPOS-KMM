package com.zyntasolutions.zyntapos.data.remote

/**
 * ZentaPOS — KtorApiService Integration Tests
 *
 * Step 3.4.7 | :shared:data | commonTest
 *
 * Uses Ktor [MockEngine] to exercise all HTTP error-mapping paths in
 * [KtorApiService] without hitting a real server.
 *
 * Coverage:
 *  A. HTTP 200 → correct DTO deserialization for each endpoint
 *  B. HTTP 401 → [ZentaException.AuthException] thrown
 *  C. HTTP 4xx  → [ZentaException.NetworkException] thrown
 *  D. HTTP 5xx  → [ZentaException.NetworkException] thrown
 *  E. HTTP 422  → [ZentaException.SyncException] thrown (pushOperations)
 *  F. Partial acceptance in pushOperations handled correctly
 *  G. Empty pull delta handled correctly
 */

import com.zyntasolutions.zyntapos.core.result.AuthException
import com.zyntasolutions.zyntapos.core.result.NetworkException
import com.zyntasolutions.zyntapos.core.result.SyncException
import com.zyntasolutions.zyntapos.data.remote.api.KtorApiService
import com.zyntasolutions.zyntapos.data.remote.dto.AuthRequestDto
import com.zyntasolutions.zyntapos.data.remote.dto.AuthResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.AuthRefreshResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.SyncOperationDto
import com.zyntasolutions.zyntapos.data.remote.dto.SyncResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.SyncPullResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.ProductDto
import com.zyntasolutions.zyntapos.data.remote.dto.UserDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private val testJson = Json {
    ignoreUnknownKeys = true
    isLenient         = true
    encodeDefaults    = true
}

/** Builds a [KtorApiService] that always responds with [statusCode] and [body]. */
private fun mockService(
    statusCode: HttpStatusCode = HttpStatusCode.OK,
    body: String               = "",
    baseUrl: String            = "http://localhost",
): KtorApiService {
    val engine = MockEngine { _ ->
        respond(
            content = body,
            status  = statusCode,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }
    val client = HttpClient(engine) {
        install(ContentNegotiation) { json(testJson) }
    }
    return KtorApiService(client = client, baseUrl = baseUrl)
}

// ── Test data helpers ─────────────────────────────────────────────────────────

private fun testUser() = UserDto(
    id        = "user-1",
    name      = "Test Cashier",
    email     = "cashier@test.com",
    role      = "CASHIER",
    storeId   = "store-1",
    isActive  = true,
    createdAt = 1_700_000_000L,
    updatedAt = 1_700_000_000L,
)

private fun testAuthResponse() = AuthResponseDto(
    accessToken  = "access_xyz",
    refreshToken = "refresh_abc",
    expiresIn    = 3600L,
    user         = testUser(),
)

private fun testSyncOp(id: String = "op-1", entityType: String = "ORDER") = SyncOperationDto(
    id         = id,
    entityType = entityType,
    entityId   = "entity-$id",
    operation  = "CREATE",
    payload    = """{"id":"entity-$id"}""",
    createdAt  = 1_700_000_000L,
)

// ══════════════════════════════════════════════════════════════════════════════
// Test class
// ══════════════════════════════════════════════════════════════════════════════

class ApiServiceTest {

    // ── A. Login 200 → AuthResponseDto ──────────────────────────────────────

    @Test
    fun login_200_returns_correct_dto() = runTest {
        val service = mockService(
            body = testJson.encodeToString(testAuthResponse()),
        )
        val result = service.login(AuthRequestDto(email = "cashier@test.com", password = "secret"))

        assertEquals("access_xyz",            result.accessToken)
        assertEquals("refresh_abc",           result.refreshToken)
        assertEquals("user-1",                result.user.id)
        assertEquals("CASHIER",               result.user.role)
    }

    // ── B. Login 401 → AuthException ───────────────────────────────────────

    @Test
    fun login_401_throws_auth_exception() = runTest {
        val service = mockService(HttpStatusCode.Unauthorized, """{"error":"invalid credentials"}""")
        assertFailsWith<AuthException> {
            service.login(AuthRequestDto(email = "bad@test.com", password = "wrong"))
        }
    }

    // ── C. Login 500 → NetworkException with status code in message ─────────

    @Test
    fun login_500_throws_network_exception_with_status() = runTest {
        val service = mockService(HttpStatusCode.InternalServerError, """{"error":"server error"}""")
        val ex = assertFailsWith<NetworkException> {
            service.login(AuthRequestDto(email = "a@b.com", password = "pass"))
        }
        assertTrue(ex.message!!.contains("500"), "Message should contain status code: ${ex.message}")
    }

    // ── D. Login 404 → NetworkException ────────────────────────────────────

    @Test
    fun login_404_throws_network_exception() = runTest {
        val service = mockService(HttpStatusCode.NotFound)
        assertFailsWith<NetworkException> {
            service.login(AuthRequestDto(email = "a@b.com", password = "pass"))
        }
    }

    // ── E. refreshToken 200 → AuthRefreshResponseDto ───────────────────────

    @Test
    fun refreshToken_200_returns_new_access_token() = runTest {
        val dto = AuthRefreshResponseDto(accessToken = "new_access_token", expiresIn = 3600L)
        val service = mockService(body = testJson.encodeToString(dto))

        val result = service.refreshToken("valid_refresh_token")
        assertEquals("new_access_token", result.accessToken)
        assertEquals(3600L, result.expiresIn)
    }

    // ── F. refreshToken 401 → AuthException ───────────────────────────────

    @Test
    fun refreshToken_401_throws_auth_exception() = runTest {
        val service = mockService(HttpStatusCode.Unauthorized)
        assertFailsWith<AuthException> {
            service.refreshToken("expired_refresh_token")
        }
    }

    // ── G. getProducts 200 → List<ProductDto> with correct fields ───────────

    @Test
    fun getProducts_200_deserializes_correctly() = runTest {
        val products = listOf(
            ProductDto(
                id        = "p-1",
                name      = "Espresso",
                barcode   = "8901234567890",
                sku       = "ESP-001",
                price     = 4.50,
                stockQty  = 100.0,
                createdAt = 1_700_000_000L,
                updatedAt = 1_700_000_000L,
            )
        )
        val service = mockService(body = testJson.encodeToString(products))

        val result = service.getProducts()
        assertEquals(1,         result.size)
        assertEquals("p-1",     result[0].id)
        assertEquals("Espresso", result[0].name)
        assertEquals(4.50,      result[0].price)
    }

    // ── H. getProducts 200 → empty list ────────────────────────────────────

    @Test
    fun getProducts_200_empty_list() = runTest {
        val service = mockService(body = "[]")
        val result = service.getProducts()
        assertTrue(result.isEmpty())
    }

    // ── I. pushOperations 200 → accepted IDs in SyncResponseDto ──────────

    @Test
    fun pushOperations_200_returns_accepted_ids() = runTest {
        val response = SyncResponseDto(
            accepted        = listOf("op-1"),
            rejected        = emptyList(),
            conflicts       = emptyList(),
            deltaOperations = emptyList(),
            serverTimestamp = 1_700_000_000L,
        )
        val service = mockService(body = testJson.encodeToString(response))

        val result = service.pushOperations(listOf(testSyncOp("op-1")))
        assertTrue(result.accepted.contains("op-1"))
        assertTrue(result.rejected.isEmpty())
    }

    // ── J. pushOperations 422 → SyncException ────────────────────────────

    @Test
    fun pushOperations_422_throws_sync_exception() = runTest {
        val service = mockService(HttpStatusCode.UnprocessableEntity, """{"error":"batch rejected"}""")
        assertFailsWith<SyncException> {
            service.pushOperations(listOf(testSyncOp("op-bad")))
        }
    }

    // ── K. pushOperations partial: some accepted, some rejected ──────────

    @Test
    fun pushOperations_partial_acceptance_returns_both_lists() = runTest {
        val response = SyncResponseDto(
            accepted        = listOf("op-1"),
            rejected        = listOf("op-2"),
            conflicts       = emptyList(),
            deltaOperations = emptyList(),
            serverTimestamp = 1_700_000_000L,
        )
        val service = mockService(body = testJson.encodeToString(response))

        val result = service.pushOperations(listOf(testSyncOp("op-1"), testSyncOp("op-2")))
        assertEquals(listOf("op-1"), result.accepted)
        assertEquals(listOf("op-2"), result.rejected)
    }

    // ── L. pushOperations with deltaOperations bundled in push ack ───────

    @Test
    fun pushOperations_response_includes_delta_operations() = runTest {
        val deltaOp = testSyncOp("srv-op-1", "PRODUCT")
        val response = SyncResponseDto(
            accepted        = listOf("op-1"),
            rejected        = emptyList(),
            conflicts       = emptyList(),
            deltaOperations = listOf(deltaOp),
            serverTimestamp = 1_700_000_100L,
        )
        val service = mockService(body = testJson.encodeToString(response))

        val result = service.pushOperations(listOf(testSyncOp("op-1")))
        assertEquals(1, result.deltaOperations.size)
        assertEquals("srv-op-1", result.deltaOperations[0].id)
    }

    // ── M. pullOperations 200 → delta operations ──────────────────────────

    @Test
    fun pullOperations_200_returns_delta() = runTest {
        val expected = SyncPullResponseDto(
            operations = listOf(
                SyncOperationDto(
                    id         = "srv-op-1",
                    entityType = "PRODUCT",
                    entityId   = "p-99",
                    operation  = "UPDATE",
                    payload    = """{"id":"p-99","stock_qty":50}""",
                    createdAt  = 1_700_000_100L,
                )
            ),
            serverTimestamp = 1_700_000_100L,
        )
        val service = mockService(body = testJson.encodeToString(expected))

        val result = service.pullOperations(lastSyncTimestamp = 1_700_000_000L)
        assertEquals(1,           result.operations.size)
        assertEquals("srv-op-1",  result.operations[0].id)
        assertEquals("PRODUCT",   result.operations[0].entityType)
    }

    // ── N. pullOperations 200 → empty delta ──────────────────────────────

    @Test
    fun pullOperations_200_empty_delta_returns_empty_list() = runTest {
        val response = SyncPullResponseDto(
            operations      = emptyList(),
            serverTimestamp = 1_700_000_200L,
        )
        val service = mockService(body = testJson.encodeToString(response))

        val result = service.pullOperations(lastSyncTimestamp = 1_700_000_000L)
        assertTrue(result.operations.isEmpty())
        assertEquals(1_700_000_200L, result.serverTimestamp)
    }

    // ── O. pullOperations 500 → NetworkException ──────────────────────────

    @Test
    fun pullOperations_500_throws_network_exception() = runTest {
        val service = mockService(HttpStatusCode.InternalServerError)
        assertFailsWith<NetworkException> {
            service.pullOperations(lastSyncTimestamp = 0L)
        }
    }
}
