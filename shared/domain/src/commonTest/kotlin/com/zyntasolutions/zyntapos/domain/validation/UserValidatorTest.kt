package com.zyntasolutions.zyntapos.domain.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * ZyntaPOS — UserValidator Unit Tests (commonTest)
 *
 * All methods under test are pure functions (no I/O) — ideal for commonTest.
 *
 * Coverage:
 *  A.  validateName — blank returns error
 *  B.  validateName — whitespace-only returns error
 *  C.  validateName — non-blank returns null (valid)
 *  D.  validateEmail — blank returns error
 *  E.  validateEmail — missing @ returns error
 *  F.  validateEmail — missing dot returns error
 *  G.  validateEmail — too short (< 5 chars) returns error
 *  H.  validateEmail — valid address returns null
 *  I.  validatePassword — blank returns error
 *  J.  validatePassword — fewer than 6 chars returns error
 *  K.  validatePassword — exactly 6 chars returns null
 *  L.  validatePassword — more than 6 chars returns null
 *  M.  validateConfirmPassword — blank confirmation returns error
 *  N.  validateConfirmPassword — mismatch returns error
 *  O.  validateConfirmPassword — matching returns null
 *  P.  validatePhone — blank returns error
 *  Q.  validatePhone — non-blank returns null
 */
class UserValidatorTest {

    // ── validateName ─────────────────────────────────────────────────────────

    @Test
    fun `A - validateName blank string returns error`() {
        assertNotNull(UserValidator.validateName(""))
    }

    @Test
    fun `B - validateName whitespace-only returns error`() {
        assertNotNull(UserValidator.validateName("   "))
    }

    @Test
    fun `C - validateName non-blank returns null`() {
        assertNull(UserValidator.validateName("Alice"))
    }

    // ── validateEmail ─────────────────────────────────────────────────────────

    @Test
    fun `D - validateEmail blank returns error`() {
        assertNotNull(UserValidator.validateEmail(""))
    }

    @Test
    fun `E - validateEmail missing at-sign returns error`() {
        assertNotNull(UserValidator.validateEmail("nodomain.com"))
    }

    @Test
    fun `F - validateEmail missing dot returns error`() {
        assertNotNull(UserValidator.validateEmail("user@nodot"))
    }

    @Test
    fun `G - validateEmail too short returns error`() {
        // length < 5: "a@b." has length 4
        assertNotNull(UserValidator.validateEmail("a@b."))
    }

    @Test
    fun `H - validateEmail valid address returns null`() {
        assertNull(UserValidator.validateEmail("user@example.com"))
    }

    @Test
    fun `H2 - validateEmail minimal valid address returns null`() {
        // "a@b.c" length=5, has @ and .
        assertNull(UserValidator.validateEmail("a@b.c"))
    }

    // ── validatePassword ──────────────────────────────────────────────────────

    @Test
    fun `I - validatePassword blank returns error`() {
        assertNotNull(UserValidator.validatePassword(""))
    }

    @Test
    fun `J - validatePassword fewer than 6 chars returns error`() {
        assertNotNull(UserValidator.validatePassword("abc12"))
        assertEquals("Password must be at least 6 characters", UserValidator.validatePassword("abc12"))
    }

    @Test
    fun `K - validatePassword exactly 6 chars returns null`() {
        assertNull(UserValidator.validatePassword("abcd12"))
    }

    @Test
    fun `L - validatePassword more than 6 chars returns null`() {
        assertNull(UserValidator.validatePassword("superSecurePass!99"))
    }

    // ── validateConfirmPassword ───────────────────────────────────────────────

    @Test
    fun `M - validateConfirmPassword blank confirmation returns error`() {
        assertNotNull(UserValidator.validateConfirmPassword("", "password123"))
    }

    @Test
    fun `N - validateConfirmPassword mismatch returns error`() {
        val result = UserValidator.validateConfirmPassword("password456", "password123")
        assertNotNull(result)
        assertEquals("Passwords do not match", result)
    }

    @Test
    fun `O - validateConfirmPassword matching passwords returns null`() {
        assertNull(UserValidator.validateConfirmPassword("password123", "password123"))
    }

    // ── validatePhone ─────────────────────────────────────────────────────────

    @Test
    fun `P - validatePhone blank returns error`() {
        assertNotNull(UserValidator.validatePhone(""))
    }

    @Test
    fun `Q - validatePhone non-blank returns null`() {
        assertNull(UserValidator.validatePhone("+94771234567"))
    }
}
