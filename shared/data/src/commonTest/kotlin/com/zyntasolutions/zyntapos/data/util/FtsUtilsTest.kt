package com.zyntasolutions.zyntapos.data.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ZyntaPOS — FtsUtilsTest Unit Tests (commonTest)
 *
 * Validates the FTS5 query builder in [toFtsQuery].
 *
 * Coverage:
 *  A. single word appends wildcard
 *  B. two words each get wildcard
 *  C. leading and trailing whitespace is trimmed
 *  D. multiple internal spaces collapsed to single space
 *  E. double-quotes are stripped
 *  F. empty string produces empty result
 *  G. whitespace-only string produces empty result
 *  H. mixed case is preserved (FTS5 handles case-folding)
 */
class FtsUtilsTest {

    @Test
    fun `A - single word appends wildcard`() {
        assertEquals("coffee*", "coffee".toFtsQuery())
    }

    @Test
    fun `B - two words each get their own wildcard`() {
        assertEquals("coff* cak*", "coff cak".toFtsQuery())
    }

    @Test
    fun `C - leading and trailing whitespace is trimmed`() {
        assertEquals("espresso*", "  espresso  ".toFtsQuery())
    }

    @Test
    fun `D - multiple internal spaces are treated as single separator`() {
        assertEquals("hot* coffee*", "hot   coffee".toFtsQuery())
    }

    @Test
    fun `E - double quotes are stripped before tokenising`() {
        assertEquals("mocha*", "\"mocha\"".toFtsQuery())
    }

    @Test
    fun `F - empty string produces empty result`() {
        assertEquals("", "".toFtsQuery())
    }

    @Test
    fun `G - whitespace-only string produces empty result`() {
        assertEquals("", "   ".toFtsQuery())
    }

    @Test
    fun `H - mixed case is preserved`() {
        assertEquals("Latte*", "Latte".toFtsQuery())
    }

    @Test
    fun `I - three-word query all get wildcards`() {
        assertEquals("iced* flat* white*", "iced flat white".toFtsQuery())
    }
}
