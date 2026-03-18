package com.zyntasolutions.zyntapos.api.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for pure helper functions used in AdminConfigService and AdminMetricsService.
 *
 * Tests cover:
 * - PostgreSQL array literal parsing/formatting (toPgArray/fromPgArray)
 * - JSON payload extraction (extractTotal/extractField)
 *
 * These are private methods in the services, so we re-implement them here
 * to verify the logic without needing a database.
 */
class AdminConfigServiceHelpersTest {

    // ── fromPgArray ──────────────────────────────────────────────────────

    @Test
    fun `fromPgArray parses simple array`() {
        val result = fromPgArray("{a,b,c}")
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun `fromPgArray parses quoted values`() {
        val result = fromPgArray("""{"hello","world"}""")
        assertEquals(listOf("hello", "world"), result)
    }

    @Test
    fun `fromPgArray returns empty for empty array`() {
        assertEquals(emptyList(), fromPgArray("{}"))
    }

    @Test
    fun `fromPgArray returns empty for blank string`() {
        assertEquals(emptyList(), fromPgArray(""))
        assertEquals(emptyList(), fromPgArray("  "))
    }

    @Test
    fun `fromPgArray handles single element`() {
        assertEquals(listOf("only"), fromPgArray("{only}"))
    }

    // ── toPgArray ────────────────────────────────────────────────────────

    @Test
    fun `toPgArray formats list as pg array literal`() {
        val result = toPgArray(listOf("a", "b"))
        assertTrue(result.startsWith("{"))
        assertTrue(result.endsWith("}"))
        assertTrue(result.contains("\"a\""))
        assertTrue(result.contains("\"b\""))
    }

    @Test
    fun `toPgArray handles empty list`() {
        assertEquals("{}", toPgArray(emptyList()))
    }

    @Test
    fun `toPgArray escapes quotes in values`() {
        val result = toPgArray(listOf("say \"hi\""))
        assertTrue(result.contains("\\\""), "Quotes should be escaped")
    }

    // ── extractTotal (JSON parsing from sync payload) ────────────────────

    @Test
    fun `extractTotal returns total from JSON payload`() {
        val payload = """{"total":123.45,"items":[]}"""
        assertEquals(123.45, extractTotal(payload), 0.001)
    }

    @Test
    fun `extractTotal returns 0 for missing total field`() {
        assertEquals(0.0, extractTotal("""{"items":[]}"""), 0.001)
    }

    @Test
    fun `extractTotal returns 0 for invalid JSON`() {
        assertEquals(0.0, extractTotal("not json"), 0.001)
    }

    @Test
    fun `extractTotal returns 0 for empty string`() {
        assertEquals(0.0, extractTotal(""), 0.001)
    }

    // ── extractField (generic JSON field extraction) ─────────────────────

    @Test
    fun `extractField returns string value`() {
        val payload = """{"productId":"p-42","name":"Widget"}"""
        assertEquals("p-42", extractField(payload, "productId"))
        assertEquals("Widget", extractField(payload, "name"))
    }

    @Test
    fun `extractField returns null for missing field`() {
        assertNull(extractField("""{"a":"1"}""", "b"))
    }

    @Test
    fun `extractField returns null for invalid JSON`() {
        assertNull(extractField("not json", "field"))
    }

    // ── Helper mirrors (same logic as private methods in the services) ───

    private fun fromPgArray(value: String): List<String> {
        if (value.isBlank() || value == "{}") return emptyList()
        return value.removeSurrounding("{", "}")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }
    }

    private fun toPgArray(list: List<String>): String =
        list.joinToString(",", "{", "}") { "\"${it.replace("\"", "\\\"")}\"" }

    private fun extractTotal(payload: String): Double = try {
        val json = Json.parseToJsonElement(payload) as? JsonObject
        json?.get("total")?.jsonPrimitive?.doubleOrNull ?: 0.0
    } catch (_: Exception) { 0.0 }

    private fun extractField(payload: String, field: String): String? = try {
        val json = Json.parseToJsonElement(payload) as? JsonObject
        json?.get(field)?.jsonPrimitive?.content
    } catch (_: Exception) { null }
}
