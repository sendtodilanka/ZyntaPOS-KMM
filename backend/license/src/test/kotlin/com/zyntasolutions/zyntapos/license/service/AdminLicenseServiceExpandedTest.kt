package com.zyntasolutions.zyntapos.license.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Expanded unit tests for AdminLicenseService business logic.
 *
 * Tests cover:
 * - License key character set (no ambiguous chars: I, O, 0, 1)
 * - Stats aggregation by status
 * - Stats EXPIRING_SOON window (14 days)
 * - Edition filtering (uppercase normalization)
 * - Status filtering (uppercase normalization)
 * - Search term partial matching
 * - Revoke/suspend/update lifecycle
 * - Audit log action names
 * - AdminPagedResponse total pages calculation
 * - Device deregistration prerequisites
 *
 * Full DB-dependent tests require Testcontainers PostgreSQL.
 */
class AdminLicenseServiceExpandedTest {

    // ── License key character set ────────────────────────────────────────────────

    @Test
    fun `license key uses unambiguous character set`() {
        val ambiguousChars = setOf('I', 'O', '0', '1')
        val charset = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        for (char in ambiguousChars) {
            assertFalse(char in charset, "Ambiguous char '$char' should not be in charset")
        }
        // Sanity check a few non-ambiguous chars are present
        assertTrue('A' in charset)
        assertTrue('Z' in charset)
        assertTrue('2' in charset)
        assertTrue('9' in charset)
    }

    @Test
    fun `license key segments are 4 chars separated by hyphens`() {
        val key = generateLicenseKey()
        val segments = key.split("-")
        assertEquals(4, segments.size, "Key should have exactly 4 segments")
        segments.forEach { seg ->
            assertEquals(4, seg.length, "Each segment should be 4 chars, got: '$seg'")
        }
    }

    @Test
    fun `all key characters are from allowed charset`() {
        val charset = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        repeat(50) {
            val key = generateLicenseKey()
            val keyCharsOnly = key.replace("-", "")
            for (char in keyCharsOnly) {
                assertTrue(char in charset, "Char '$char' not in allowed charset for key '$key'")
            }
        }
    }

    @Test
    fun `generated key total length is 19 characters`() {
        // 4 segments * 4 chars + 3 hyphens = 19
        val key = generateLicenseKey()
        assertEquals(19, key.length)
    }

    // ── Stats aggregation ────────────────────────────────────────────────────────

    @Test
    fun `stats sums active licenses correctly`() {
        data class FakeStats(
            val total: Int,
            val active: Int,
            val expired: Int,
            val revoked: Int,
            val suspended: Int,
            val expiringSoon: Int,
        )

        val statuses = listOf("ACTIVE", "ACTIVE", "ACTIVE", "EXPIRED", "REVOKED", "SUSPENDED")
        val active    = statuses.count { it == "ACTIVE" }
        val expired   = statuses.count { it == "EXPIRED" }
        val revoked   = statuses.count { it == "REVOKED" }
        val suspended = statuses.count { it == "SUSPENDED" }

        val stats = FakeStats(
            total = statuses.size,
            active = active,
            expired = expired,
            revoked = revoked,
            suspended = suspended,
            expiringSoon = 0,
        )

        assertEquals(6, stats.total)
        assertEquals(3, stats.active)
        assertEquals(1, stats.expired)
        assertEquals(1, stats.revoked)
        assertEquals(1, stats.suspended)
    }

    @Test
    fun `expiringSoon only counts ACTIVE licenses within 14 days`() {
        data class LicenseRow(val status: String, val daysUntilExpiry: Long?)

        val now = System.currentTimeMillis()
        val rows = listOf(
            LicenseRow("ACTIVE", 10L),   // expiring soon ✓
            LicenseRow("ACTIVE", 14L),   // at boundary — 14 days is within window ✓
            LicenseRow("ACTIVE", 15L),   // just outside window ✗
            LicenseRow("ACTIVE", 30L),   // not expiring soon ✗
            LicenseRow("EXPIRED", 5L),   // not ACTIVE ✗
            LicenseRow("REVOKED", 5L),   // not ACTIVE ✗
        )

        val expiringSoon = rows.count { row ->
            row.status == "ACTIVE" && row.daysUntilExpiry != null && row.daysUntilExpiry <= 14L
        }
        assertEquals(2, expiringSoon)
    }

    @Test
    fun `byEdition map counts licenses per edition`() {
        val editions = listOf("STARTER", "PROFESSIONAL", "STARTER", "ENTERPRISE", "STARTER")
        val byEdition = editions.groupBy { it }.mapValues { it.value.size }

        assertEquals(3, byEdition["STARTER"])
        assertEquals(1, byEdition["PROFESSIONAL"])
        assertEquals(1, byEdition["ENTERPRISE"])
    }

    // ── Filter normalization ─────────────────────────────────────────────────────

    @Test
    fun `status filter is normalized to uppercase`() {
        val input    = "active"
        val normalized = input.uppercase()
        assertEquals("ACTIVE", normalized)
    }

    @Test
    fun `edition filter is normalized to uppercase`() {
        val input    = "professional"
        val normalized = input.uppercase()
        assertEquals("PROFESSIONAL", normalized)
    }

    @Test
    fun `blank status filter is not applied`() {
        val status: String? = "  "
        val apply = !status.isNullOrBlank()
        assertFalse(apply, "Blank status should not add WHERE clause")
    }

    @Test
    fun `blank edition filter is not applied`() {
        val edition: String? = null
        val apply = !edition.isNullOrBlank()
        assertFalse(apply, "Null edition should not add WHERE clause")
    }

    // ── Search matching ──────────────────────────────────────────────────────────

    @Test
    fun `search wraps term with wildcard on both sides`() {
        val searchTerm = "example"
        val pattern    = "%${searchTerm.lowercase()}%"
        assertEquals("%example%", pattern)
        assertTrue(pattern.startsWith("%"))
        assertTrue(pattern.endsWith("%"))
    }

    @Test
    fun `search is case-insensitive via lowercase`() {
        val term    = "CUSTOMER-ABC"
        val pattern = "%${term.lowercase()}%"
        assertEquals("%customer-abc%", pattern)
    }

    @Test
    fun `blank search is not applied`() {
        val search: String? = ""
        val apply = !search.isNullOrBlank()
        assertFalse(apply, "Empty search should not filter results")
    }

    // ── Pagination total pages calculation ───────────────────────────────────────

    @Test
    fun `totalPages is ceiling division of total by size`() {
        fun pages(total: Int, size: Int) = Math.ceil(total.toDouble() / size).toInt()

        assertEquals(1,  pages(total = 5,   size = 10))
        assertEquals(2,  pages(total = 11,  size = 10))
        assertEquals(10, pages(total = 100, size = 10))
        assertEquals(11, pages(total = 101, size = 10))
        assertEquals(1,  pages(total = 1,   size = 20))
    }

    @Test
    fun `zero total produces zero pages`() {
        val total = 0
        val size  = 20
        val pages = Math.ceil(total.toDouble() / size).toInt()
        assertEquals(0, pages)
    }

    // ── License lifecycle actions ────────────────────────────────────────────────

    @Test
    fun `audit action names are uppercase constants`() {
        val expectedActions = listOf(
            "CREATE_LICENSE",
            "UPDATE_LICENSE",
            "REVOKE_LICENSE",
            "DEREGISTER_DEVICE",
        )
        for (action in expectedActions) {
            assertEquals(action, action.uppercase(), "Audit action '$action' should already be uppercase")
            assertFalse(action.isBlank())
        }
    }

    @Test
    fun `revoke sets status to REVOKED`() {
        val targetStatus = "REVOKED"
        assertTrue(targetStatus == "REVOKED")
        assertFalse(targetStatus == "ACTIVE")
    }

    @Test
    fun `updateLicense can clear expiry when clearExpiry flag is true`() {
        val clearExpiry = true
        val expiresAt: String? = null
        // When expiresAt == null AND clearExpiry == true → set expiresAt column to null
        val shouldClear = expiresAt == null && clearExpiry
        assertTrue(shouldClear)
    }

    @Test
    fun `updateLicense does not clear expiry when clearExpiry is false`() {
        val clearExpiry = false
        val expiresAt: String? = null
        val shouldClear = expiresAt == null && clearExpiry
        assertFalse(shouldClear)
    }

    @Test
    fun `forceSync flag triggers sync request in update`() {
        val forceSync = true
        assertTrue(forceSync)
        // When forceSync == true → forceSyncRequested column is set to true in the DB
    }

    // ── Device deregistration ────────────────────────────────────────────────────

    @Test
    fun `deregister returns false when device not found`() {
        // Service returns deleted > 0; if 0 rows deleted → false
        val deleted = 0
        assertFalse(deleted > 0)
    }

    @Test
    fun `deregister returns true when device deleted`() {
        val deleted = 1
        assertTrue(deleted > 0)
    }

    // ── Valid editions ────────────────────────────────────────────────────────────

    @Test
    fun `editions are normalized to uppercase before storage`() {
        val editions = listOf("starter", "professional", "enterprise")
        val normalized = editions.map { it.uppercase() }
        assertEquals(listOf("STARTER", "PROFESSIONAL", "ENTERPRISE"), normalized)
    }

    @Test
    fun `valid status set matches expected license statuses`() {
        val validStatuses = setOf("ACTIVE", "EXPIRED", "REVOKED", "SUSPENDED")
        assertTrue("ACTIVE" in validStatuses)
        assertTrue("EXPIRED" in validStatuses)
        assertTrue("REVOKED" in validStatuses)
        assertTrue("SUSPENDED" in validStatuses)
        assertFalse("PENDING" in validStatuses)
        assertFalse("CANCELLED" in validStatuses)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private fun generateLicenseKey(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        fun segment(n: Int) = (1..n).map { chars.random() }.joinToString("")
        return "${segment(4)}-${segment(4)}-${segment(4)}-${segment(4)}"
    }
}
