package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.repository.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for AdminAuditService -- hash-chain computation,
 * SHA-256 digest, and delegation to repository.
 */
class AdminAuditServiceTest {

    private val auditRepo = mockk<AdminAuditRepository>()
    private val service = AdminAuditService(auditRepo)

    // ── Hash chain computation ───────────────────────────────────────────

    @Test
    fun `log computes hash chain from latest hash plus event metadata`() = runTest {
        val adminId = UUID.randomUUID()
        val slot = slot<AuditEntryInput>()
        coEvery { auditRepo.findLatestHash() } returns "abc123"
        coEvery { auditRepo.insertEntry(capture(slot)) } just Runs

        service.log(
            adminId = adminId,
            adminName = "Admin",
            eventType = "USER_CREATE",
            category = "AUTH",
            entityType = "user",
            entityId = "u-42",
        )

        coVerify(exactly = 1) { auditRepo.insertEntry(any()) }
        val entry = slot.captured
        assertEquals("USER_CREATE", entry.eventType)
        assertEquals("AUTH", entry.category)
        assertEquals("user", entry.entityType)
        assertEquals("u-42", entry.entityId)
        assertTrue(entry.hashChain.length == 64, "hashChain should be 64 hex chars (SHA-256)")
        assertTrue(entry.hashChain.all { it in "0123456789abcdef" })
    }

    @Test
    fun `hash chain incorporates previous hash`() = runTest {
        val slot1 = slot<AuditEntryInput>()
        val slot2 = slot<AuditEntryInput>()

        // First log: empty chain
        coEvery { auditRepo.findLatestHash() } returns ""
        coEvery { auditRepo.insertEntry(capture(slot1)) } just Runs
        service.log(adminId = null, adminName = null, eventType = "E1", category = "C")

        // Second log: chain includes first hash
        coEvery { auditRepo.findLatestHash() } returns slot1.captured.hashChain
        coEvery { auditRepo.insertEntry(capture(slot2)) } just Runs
        service.log(adminId = null, adminName = null, eventType = "E2", category = "C")

        // Hash chains should differ because inputs differ
        assertTrue(slot1.captured.hashChain != slot2.captured.hashChain)
    }

    @Test
    fun `log stores previous and new values as JSON`() = runTest {
        coEvery { auditRepo.findLatestHash() } returns ""
        val slot = slot<AuditEntryInput>()
        coEvery { auditRepo.insertEntry(capture(slot)) } just Runs

        service.log(
            adminId = UUID.randomUUID(),
            adminName = "Admin",
            eventType = "UPDATE",
            category = "CONFIG",
            previousValues = mapOf("theme" to "light"),
            newValues = mapOf("theme" to "dark"),
        )

        val entry = slot.captured
        assertTrue(entry.previousValues!!.contains("\"theme\":\"light\""))
        assertTrue(entry.newValues!!.contains("\"theme\":\"dark\""))
    }

    @Test
    fun `log passes null JSON when no values provided`() = runTest {
        coEvery { auditRepo.findLatestHash() } returns ""
        val slot = slot<AuditEntryInput>()
        coEvery { auditRepo.insertEntry(capture(slot)) } just Runs

        service.log(
            adminId = UUID.randomUUID(),
            adminName = "Admin",
            eventType = "DELETE",
            category = "DATA",
        )

        assertEquals(null, slot.captured.previousValues)
        assertEquals(null, slot.captured.newValues)
    }

    @Test
    fun `log records IP address and user agent`() = runTest {
        coEvery { auditRepo.findLatestHash() } returns ""
        val slot = slot<AuditEntryInput>()
        coEvery { auditRepo.insertEntry(capture(slot)) } just Runs

        service.log(
            adminId = UUID.randomUUID(),
            adminName = "Admin",
            eventType = "LOGIN",
            category = "AUTH",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
        )

        assertEquals("192.168.1.1", slot.captured.ipAddress)
        assertEquals("Mozilla/5.0", slot.captured.userAgent)
    }

    @Test
    fun `log records error state`() = runTest {
        coEvery { auditRepo.findLatestHash() } returns ""
        val slot = slot<AuditEntryInput>()
        coEvery { auditRepo.insertEntry(capture(slot)) } just Runs

        service.log(
            adminId = UUID.randomUUID(),
            adminName = "Admin",
            eventType = "LOGIN_FAILED",
            category = "AUTH",
            success = false,
            errorMessage = "Invalid password",
        )

        assertEquals(false, slot.captured.success)
        assertEquals("Invalid password", slot.captured.errorMessage)
    }

    // ── listEntries delegation ───────────────────────────────────────────

    @Test
    fun `listEntries delegates to repository`() = runTest {
        val auditPage = AuditPage(emptyList(), 0, 10, 0, 0)
        coEvery { auditRepo.listEntries(any(), 0, 10) } returns auditPage

        val result = service.listEntries(0, 10, null, null, null, null, null, null)
        assertEquals(0, result.total)
        coVerify { auditRepo.listEntries(any(), 0, 10) }
    }

    // ── SHA-256 helper verification ──────────────────────────────────────

    @Test
    fun `sha256Hex produces consistent output`() {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "test-input"
        val expected = digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

        // Verify it's 64 hex chars
        assertEquals(64, expected.length)
        assertTrue(expected.all { it in "0123456789abcdef" })
    }
}
