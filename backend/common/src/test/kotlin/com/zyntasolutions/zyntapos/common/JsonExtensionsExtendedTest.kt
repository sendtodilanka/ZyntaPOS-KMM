package com.zyntasolutions.zyntapos.common

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * C9: Extended tests for JsonExtensions — covers lng(), additional edge cases
 * for str/dbl/int/bool, and boundary conditions.
 */
class JsonExtensionsExtendedTest {

    private fun parseObj(json: String): JsonObject =
        Json.parseToJsonElement(json).let { it as JsonObject }

    // ── lng() ──────────────────────────────────────────────────────────────

    @Test
    fun `lng returns long value for existing key`() {
        val obj = parseObj("""{"timestamp": 1710000000000}""")
        assertEquals(1710000000000L, obj.lng("timestamp"))
    }

    @Test
    fun `lng returns null for missing key`() {
        val obj = parseObj("""{"other": 1}""")
        assertNull(obj.lng("timestamp"))
    }

    @Test
    fun `lng returns null for non-numeric value`() {
        val obj = parseObj("""{"timestamp": "not-a-number"}""")
        assertNull(obj.lng("timestamp"))
    }

    @Test
    fun `lng returns value for small integer`() {
        val obj = parseObj("""{"seq": 42}""")
        assertEquals(42L, obj.lng("seq"))
    }

    @Test
    fun `lng handles Long MAX_VALUE`() {
        val obj = parseObj("""{"big": 9223372036854775807}""")
        assertEquals(Long.MAX_VALUE, obj.lng("big"))
    }

    @Test
    fun `lng returns null for double value`() {
        val obj = parseObj("""{"val": 3.14}""")
        // longOrNull returns null for non-integer values
        assertNull(obj.lng("val"))
    }

    @Test
    fun `lng returns zero as long`() {
        val obj = parseObj("""{"val": 0}""")
        assertEquals(0L, obj.lng("val"))
    }

    @Test
    fun `lng handles negative long`() {
        val obj = parseObj("""{"val": -100}""")
        assertEquals(-100L, obj.lng("val"))
    }

    // ── str() edge cases ──────────────────────────────────────────────────

    @Test
    fun `str returns empty string for empty value`() {
        val obj = parseObj("""{"name": ""}""")
        assertEquals("", obj.str("name"))
    }

    @Test
    fun `str returns boolean value as string`() {
        val obj = parseObj("""{"flag": true}""")
        assertEquals("true", obj.str("flag"))
    }

    @Test
    fun `str handles special characters`() {
        val obj = buildJsonObject {
            put("name", "hello\nworld")
        }
        assertEquals("hello\nworld", obj.str("name"))
    }

    @Test
    fun `str handles unicode`() {
        val obj = buildJsonObject {
            put("name", "\u00E9\u00E0\u00FC")
        }
        assertEquals("\u00E9\u00E0\u00FC", obj.str("name"))
    }

    // ── dbl() edge cases ──────────────────────────────────────────────────

    @Test
    fun `dbl handles negative value`() {
        val obj = parseObj("""{"val": -99.5}""")
        assertEquals(-99.5, obj.dbl("val"))
    }

    @Test
    fun `dbl handles zero`() {
        val obj = parseObj("""{"val": 0}""")
        assertEquals(0.0, obj.dbl("val"))
    }

    @Test
    fun `dbl handles very large value`() {
        val obj = parseObj("""{"val": 1.7976931348623157E308}""")
        assertEquals(Double.MAX_VALUE, obj.dbl("val"))
    }

    @Test
    fun `dbl returns 0 for JSON null value`() {
        val obj = JsonObject(mapOf("val" to JsonNull))
        assertEquals(0.0, obj.dbl("val"))
    }

    // ── int() edge cases ──────────────────────────────────────────────────

    @Test
    fun `int handles negative value`() {
        val obj = parseObj("""{"count": -10}""")
        assertEquals(-10, obj.int("count"))
    }

    @Test
    fun `int handles zero`() {
        val obj = parseObj("""{"count": 0}""")
        assertEquals(0, obj.int("count"))
    }

    @Test
    fun `int handles large int value`() {
        val obj = parseObj("""{"count": 2147483647}""")
        assertEquals(Int.MAX_VALUE, obj.int("count"))
    }

    @Test
    fun `int returns 0 for boolean value`() {
        val obj = parseObj("""{"count": true}""")
        assertEquals(0, obj.int("count"))
    }

    // ── bool() edge cases ─────────────────────────────────────────────────

    @Test
    fun `bool returns default true for JSON null value`() {
        val obj = JsonObject(mapOf("flag" to JsonNull))
        assertTrue(obj.bool("flag"))
    }

    @Test
    fun `bool returns custom default for JSON null value`() {
        val obj = JsonObject(mapOf("flag" to JsonNull))
        assertFalse(obj.bool("flag", default = false))
    }

    @Test
    fun `bool returns default for non-boolean string`() {
        val obj = parseObj("""{"flag": "yes"}""")
        // "yes" is not a valid boolean — booleanOrNull returns null
        assertTrue(obj.bool("flag", default = true))
    }

    @Test
    fun `bool returns default for numeric value`() {
        val obj = parseObj("""{"flag": 1}""")
        // 1 is not a boolean — booleanOrNull returns null
        assertTrue(obj.bool("flag", default = true))
    }

    // ── Empty object ──────────────────────────────────────────────────────

    @Test
    fun `all accessors return defaults for empty object`() {
        val obj = parseObj("""{}""")
        assertNull(obj.str("x"))
        assertEquals(0.0, obj.dbl("x"))
        assertEquals(0, obj.int("x"))
        assertNull(obj.lng("x"))
        assertTrue(obj.bool("x"))
    }

    // ── Nested objects are not extracted by str ────────────────────────────

    @Test
    fun `str returns JSON representation for nested object`() {
        val obj = parseObj("""{"nested": {"key": "value"}}""")
        // Accessing a nested object via str() should not return null — it returns the JSON string
        // but will throw since it's not a primitive
        val result = runCatching { obj.str("nested") }
        assertTrue(result.isFailure, "str() should fail for nested objects (not a primitive)")
    }
}
