package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.repository.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Unit tests for AdminTicketService -- validation, SLA deadlines,
 * state-machine enforcement, CSV export, and bulk operations.
 */
class AdminTicketServiceTest {

    private val ticketRepo = mockk<AdminTicketRepository>()
    private val commentRepo = mockk<TicketCommentRepository>()
    private val service = AdminTicketService(ticketRepo, commentRepo)

    private val userId = UUID.randomUUID()

    private fun sampleTicketRow(
        id: UUID = UUID.randomUUID(),
        status: String = "OPEN",
        priority: String = "MEDIUM",
        slaBreached: Boolean = false,
    ) = TicketRow(
        id = id, ticketNumber = "TK-2026-0001", storeId = "s1", licenseId = null,
        createdBy = userId, customerName = "Jane", customerEmail = "jane@test.com",
        customerPhone = null, assignedTo = null, assignedAt = null,
        title = "Test ticket", description = "desc", category = "SOFTWARE",
        priority = priority, status = status, resolvedBy = null, resolvedAt = null,
        resolutionNote = null, timeSpentMin = null, slaDueAt = System.currentTimeMillis() + 86400000,
        slaBreached = slaBreached, customerAccessToken = UUID.randomUUID(),
        createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(),
    )

    // ── Validation ───────────────────────────────────────────────────────

    @Test
    fun `isValidCategory accepts all valid categories`() {
        listOf("HARDWARE", "SOFTWARE", "SYNC", "BILLING", "OTHER").forEach { cat ->
            assertTrue(service.isValidCategory(cat), "$cat should be valid")
        }
    }

    @Test
    fun `isValidCategory is case-insensitive`() {
        assertTrue(service.isValidCategory("hardware"))
        assertTrue(service.isValidCategory("Software"))
    }

    @Test
    fun `isValidCategory rejects invalid categories`() {
        assertFalse(service.isValidCategory("NETWORK"))
        assertFalse(service.isValidCategory(""))
    }

    @Test
    fun `isValidPriority accepts all valid priorities`() {
        listOf("LOW", "MEDIUM", "HIGH", "CRITICAL").forEach { p ->
            assertTrue(service.isValidPriority(p), "$p should be valid")
        }
    }

    @Test
    fun `isValidPriority is case-insensitive`() {
        assertTrue(service.isValidPriority("low"))
        assertTrue(service.isValidPriority("Critical"))
    }

    @Test
    fun `isValidPriority rejects invalid priorities`() {
        assertFalse(service.isValidPriority("URGENT"))
        assertFalse(service.isValidPriority(""))
    }

    // ── State machine: resolveTicket ─────────────────────────────────────

    @Test
    fun `resolveTicket throws if ticket already RESOLVED`() = runTest {
        val ticketId = UUID.randomUUID()
        val ticket = sampleTicketRow(id = ticketId, status = "RESOLVED")
        coEvery { ticketRepo.findById(ticketId) } returns ticket

        assertFailsWith<IllegalStateException> {
            service.resolveTicket(
                ticketId.toString(),
                ResolveTicketRequest("fixed it", 30),
                userId
            )
        }
    }

    @Test
    fun `resolveTicket throws if ticket already CLOSED`() = runTest {
        val ticketId = UUID.randomUUID()
        val ticket = sampleTicketRow(id = ticketId, status = "CLOSED")
        coEvery { ticketRepo.findById(ticketId) } returns ticket

        assertFailsWith<IllegalStateException> {
            service.resolveTicket(
                ticketId.toString(),
                ResolveTicketRequest("fixed", 10),
                userId
            )
        }
    }

    // ── State machine: closeTicket ───────────────────────────────────────

    @Test
    fun `closeTicket throws if ticket is not RESOLVED`() = runTest {
        val ticketId = UUID.randomUUID()
        val ticket = sampleTicketRow(id = ticketId, status = "ASSIGNED")
        coEvery { ticketRepo.findById(ticketId) } returns ticket

        assertFailsWith<IllegalStateException> {
            service.closeTicket(ticketId.toString())
        }
    }

    // ── assignTicket changes status from OPEN to ASSIGNED ────────────────

    @Test
    fun `assignTicket transitions OPEN to ASSIGNED`() = runTest {
        val ticketId = UUID.randomUUID()
        val assigneeId = UUID.randomUUID()
        val ticket = sampleTicketRow(id = ticketId, status = "OPEN")

        coEvery { ticketRepo.findById(ticketId) } returns ticket
        coEvery { ticketRepo.assignTo(ticketId, assigneeId, "ASSIGNED", any()) } returns true
        coEvery { ticketRepo.findUserNames(any()) } returns mapOf(userId to "Jane", assigneeId to "John")
        coEvery { commentRepo.listForTicket(ticketId) } returns emptyList()
        coEvery { commentRepo.findAuthorNames(any()) } returns emptyMap()

        val result = service.assignTicket(ticketId.toString(), assigneeId)

        assertNotNull(result)
        coVerify { ticketRepo.assignTo(ticketId, assigneeId, "ASSIGNED", any()) }
    }

    @Test
    fun `assignTicket keeps existing status if not OPEN`() = runTest {
        val ticketId = UUID.randomUUID()
        val assigneeId = UUID.randomUUID()
        val ticket = sampleTicketRow(id = ticketId, status = "RESOLVED")

        coEvery { ticketRepo.findById(ticketId) } returns ticket
        coEvery { ticketRepo.assignTo(ticketId, assigneeId, "RESOLVED", any()) } returns true
        coEvery { ticketRepo.findUserNames(any()) } returns mapOf(userId to "Jane", assigneeId to "John")
        coEvery { commentRepo.listForTicket(ticketId) } returns emptyList()
        coEvery { commentRepo.findAuthorNames(any()) } returns emptyMap()

        service.assignTicket(ticketId.toString(), assigneeId)

        coVerify { ticketRepo.assignTo(ticketId, assigneeId, "RESOLVED", any()) }
    }

    // ── Invalid UUID handling ────────────────────────────────────────────

    @Test
    fun `getTicket returns null for invalid UUID`() = runTest {
        assertNull(service.getTicket("not-a-uuid"))
    }

    @Test
    fun `updateTicket returns null for invalid UUID`() = runTest {
        assertNull(service.updateTicket("bad", UpdateTicketRequest(title = "new")))
    }

    @Test
    fun `assignTicket returns null for invalid UUID`() = runTest {
        assertNull(service.assignTicket("bad", UUID.randomUUID()))
    }

    @Test
    fun `resolveTicket returns null for invalid UUID`() = runTest {
        assertNull(service.resolveTicket("bad", ResolveTicketRequest("n", 0), userId))
    }

    @Test
    fun `closeTicket returns null for invalid UUID`() = runTest {
        assertNull(service.closeTicket("bad"))
    }

    @Test
    fun `addComment returns null for invalid UUID`() = runTest {
        assertNull(service.addComment("bad", AddCommentRequest("hi"), userId))
    }

    // ── CSV export ───────────────────────────────────────────────────────

    @Test
    fun `exportTicketsCsv produces header and data rows`() = runTest {
        val ticket = sampleTicketRow()
        coEvery { ticketRepo.list(any(), 0, 10_000) } returns TicketPage(
            data = listOf(ticket), page = 0, size = 10_000, total = 1, totalPages = 1,
        )

        val csv = service.exportTicketsCsv(null, null, null, null, null, null)
        val lines = csv.lines().filter { it.isNotBlank() }

        assertEquals(2, lines.size, "CSV should have header + 1 data row")
        assertTrue(lines[0].startsWith("ticket_number,"))
        assertTrue(lines[1].contains("TK-2026-0001"))
    }

    @Test
    fun `exportTicketsCsv escapes commas in title`() = runTest {
        val ticket = sampleTicketRow().copy(title = "Hello, World")
        coEvery { ticketRepo.list(any(), 0, 10_000) } returns TicketPage(
            data = listOf(ticket), page = 0, size = 10_000, total = 1, totalPages = 1,
        )

        val csv = service.exportTicketsCsv(null, null, null, null, null, null)
        assertTrue(csv.contains("\"Hello, World\""), "Title with comma should be quoted")
    }

    @Test
    fun `exportTicketsCsv escapes double quotes in title`() = runTest {
        val ticket = sampleTicketRow().copy(title = "Say \"hi\"")
        coEvery { ticketRepo.list(any(), 0, 10_000) } returns TicketPage(
            data = listOf(ticket), page = 0, size = 10_000, total = 1, totalPages = 1,
        )

        val csv = service.exportTicketsCsv(null, null, null, null, null, null)
        assertTrue(csv.contains("\"Say \"\"hi\"\"\""), "Double quotes should be escaped")
    }

    // ── Bulk operations ──────────────────────────────────────────────────

    @Test
    fun `bulkAssign counts successes and failures`() = runTest {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val assigneeId = UUID.randomUUID()
        val ticket1 = sampleTicketRow(id = id1, status = "OPEN")

        coEvery { ticketRepo.findById(id1) } returns ticket1
        coEvery { ticketRepo.assignTo(id1, assigneeId, "ASSIGNED", any()) } returns true
        coEvery { ticketRepo.findUserNames(any()) } returns mapOf(userId to "X", assigneeId to "Y")
        coEvery { commentRepo.listForTicket(id1) } returns emptyList()
        coEvery { commentRepo.findAuthorNames(any()) } returns emptyMap()

        // Second ticket fails (not found)
        coEvery { ticketRepo.findById(id2) } returns null

        val result = service.bulkAssign(listOf(id1.toString(), id2.toString()), assigneeId)
        assertEquals(1, result.updated)
        assertEquals(1, result.failed.size)
        assertEquals(id2.toString(), result.failed[0])
    }

    // ── getByCustomerToken ───────────────────────────────────────────────

    @Test
    fun `getByCustomerToken returns null for invalid UUID`() = runTest {
        assertNull(service.getByCustomerToken("not-uuid"))
    }

    @Test
    fun `getByCustomerToken returns public view`() = runTest {
        val token = UUID.randomUUID()
        val ticket = sampleTicketRow()
        coEvery { ticketRepo.findByCustomerToken(token) } returns ticket

        val view = service.getByCustomerToken(token.toString())
        assertNotNull(view)
        assertEquals("TK-2026-0001", view.ticketNumber)
        assertEquals("OPEN", view.status)
        assertEquals("MEDIUM", view.priority)
    }
}
