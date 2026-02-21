package com.zyntasolutions.zyntapos.core.result

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [ZyntaException] hierarchy.
 * Target: ≥ 90% coverage of result/ZyntaException.kt
 */
class ZyntaExceptionTest {

    // ── NetworkException ──────────────────────────────────────────────────────

    @Test
    fun `NetworkException stores message and statusCode`() {
        val ex = NetworkException("Not found", statusCode = 404)
        assertEquals("Not found", ex.message)
        assertEquals(404, ex.statusCode)
    }

    @Test
    fun `NetworkException isClientError true for 4xx`() {
        val ex = NetworkException("Forbidden", statusCode = 403)
        assertTrue(ex.isClientError)
        assertFalse(ex.isServerError)
    }

    @Test
    fun `NetworkException isServerError true for 5xx`() {
        val ex = NetworkException("Internal Server Error", statusCode = 500)
        assertTrue(ex.isServerError)
        assertFalse(ex.isClientError)
    }

    @Test
    fun `NetworkException null statusCode means neither client nor server error`() {
        val ex = NetworkException("Timeout")
        assertFalse(ex.isClientError)
        assertFalse(ex.isServerError)
    }

    // ── DatabaseException ─────────────────────────────────────────────────────

    @Test
    fun `DatabaseException stores operation label`() {
        val ex = DatabaseException("Constraint violation", operation = "INSERT products")
        assertEquals("INSERT products", ex.operation)
        assertTrue(ex is ZyntaException)
    }

    @Test
    fun `DatabaseException with no operation defaults to empty string`() {
        val ex = DatabaseException("Error")
        assertEquals("", ex.operation)
    }

    // ── AuthException ──────────────────────────────────────────────────────────

    @Test
    fun `AuthException defaults to INVALID_CREDENTIALS`() {
        val ex = AuthException("Bad password")
        assertEquals(AuthFailureReason.INVALID_CREDENTIALS, ex.reason)
    }

    @Test
    fun `AuthException stores custom reason`() {
        val ex = AuthException("Token expired", reason = AuthFailureReason.SESSION_EXPIRED)
        assertEquals(AuthFailureReason.SESSION_EXPIRED, ex.reason)
    }

    // ── ValidationException ───────────────────────────────────────────────────

    @Test
    fun `ValidationException stores field and rule`() {
        val ex = ValidationException("Barcode exists", field = "barcode", rule = "BARCODE_DUPLICATE")
        assertEquals("barcode", ex.field)
        assertEquals("BARCODE_DUPLICATE", ex.rule)
    }

    // ── HalException ──────────────────────────────────────────────────────────

    @Test
    fun `HalException stores device label`() {
        val ex = HalException("Printer offline", device = "thermal_printer")
        assertEquals("thermal_printer", ex.device)
    }

    // ── SyncException ─────────────────────────────────────────────────────────

    @Test
    fun `SyncException isMaxRetriesReached false below threshold`() {
        val ex = SyncException("Sync failed", retryCount = 4)
        assertFalse(ex.isMaxRetriesReached)
    }

    @Test
    fun `SyncException isMaxRetriesReached true at MAX_RETRIES`() {
        val ex = SyncException("Sync failed", retryCount = SyncException.MAX_RETRIES)
        assertTrue(ex.isMaxRetriesReached)
    }

    @Test
    fun `SyncException stores operationId`() {
        val ex = SyncException("Sync failed", operationId = "op-123")
        assertEquals("op-123", ex.operationId)
    }

    // ── Sealed class hierarchy check ──────────────────────────────────────────

    @Test
    fun `All exception types extend ZyntaException`() {
        val exceptions: List<ZyntaException> = listOf(
            NetworkException("n"),
            DatabaseException("d"),
            AuthException("a"),
            ValidationException("v"),
            HalException("h"),
            SyncException("s"),
        )
        assertTrue(exceptions.all { it is ZyntaException })
    }
}
