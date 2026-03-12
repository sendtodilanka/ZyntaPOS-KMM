package com.zyntasolutions.zyntapos.common

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * S3-9: Tests for JsonExtensions (str, dbl, int, bool helper functions).
 */
class JsonExtensionsTest {

    private fun parseObj(json: String): JsonObject =
        Json.parseToJsonElement(json).let { it as JsonObject }

    // ── str() ───────────────────────────────────────────────────────────

    @Test
    fun `str returns string value for existing key`() {
        val obj = parseObj("""{"name": "Alice"}""")
        assertEquals("Alice", obj.str("name"))
    }

    @Test
    fun `str returns null for missing key`() {
        val obj = parseObj("""{"name": "Alice"}""")
        assertNull(obj.str("missing"))
    }

    @Test
    fun `str returns null for JSON null value`() {
        val obj = JsonObject(mapOf("name" to JsonNull))
        assertNull(obj.str("name"))
    }

    @Test
    fun `str returns numeric value as string`() {
        val obj = parseObj("""{"count": 42}""")
        assertEquals("42", obj.str("count"))
    }

    // ── dbl() ───────────────────────────────────────────────────────────

    @Test
    fun `dbl returns double value for existing key`() {
        val obj = parseObj("""{"price": 19.99}""")
        assertEquals(19.99, obj.dbl("price"))
    }

    @Test
    fun `dbl returns 0 for missing key`() {
        val obj = parseObj("""{"price": 19.99}""")
        assertEquals(0.0, obj.dbl("missing"))
    }

    @Test
    fun `dbl returns 0 for non-numeric value`() {
        val obj = parseObj("""{"price": "not-a-number"}""")
        assertEquals(0.0, obj.dbl("price"))
    }

    @Test
    fun `dbl handles integer value as double`() {
        val obj = parseObj("""{"price": 10}""")
        assertEquals(10.0, obj.dbl("price"))
    }

    // ── int() ───────────────────────────────────────────────────────────

    @Test
    fun `int returns integer value for existing key`() {
        val obj = parseObj("""{"count": 5}""")
        assertEquals(5, obj.int("count"))
    }

    @Test
    fun `int returns 0 for missing key`() {
        val obj = parseObj("""{"count": 5}""")
        assertEquals(0, obj.int("missing"))
    }

    @Test
    fun `int returns 0 for non-integer value`() {
        val obj = parseObj("""{"count": "abc"}""")
        assertEquals(0, obj.int("count"))
    }

    @Test
    fun `int truncates double to null and returns 0`() {
        val obj = parseObj("""{"count": 3.7}""")
        // intOrNull returns null for non-integer doubles
        assertEquals(0, obj.int("count"))
    }

    // ── bool() ──────────────────────────────────────────────────────────

    @Test
    fun `bool returns boolean value for existing key`() {
        val obj = parseObj("""{"active": true}""")
        assertTrue(obj.bool("active"))
    }

    @Test
    fun `bool returns false value`() {
        val obj = parseObj("""{"active": false}""")
        assertEquals(false, obj.bool("active"))
    }

    @Test
    fun `bool returns default for missing key`() {
        val obj = parseObj("""{"other": 1}""")
        assertTrue(obj.bool("active", default = true))
        assertEquals(false, obj.bool("active", default = false))
    }

    @Test
    fun `bool default is true when not specified`() {
        val obj = parseObj("""{}""")
        assertTrue(obj.bool("missing"))
    }
}
