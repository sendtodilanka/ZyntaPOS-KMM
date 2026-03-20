package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.test.AbstractIntegrationTest
import com.zyntasolutions.zyntapos.api.test.TestFixtures
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for [AdminTransferService] — IST workflow state machine.
 *
 * Tests cover:
 * - Transfer creation (PENDING status)
 * - PENDING → APPROVED transition via approve()
 * - APPROVED → IN_TRANSIT transition via dispatch()
 * - IN_TRANSIT → RECEIVED transition via receive()
 * - PENDING|APPROVED → CANCELLED transition via cancel()
 * - Invalid state transitions return null
 * - Listing with store/status filters and pagination
 * - Full end-to-end workflow
 *
 * Uses a real PostgreSQL container via AbstractIntegrationTest.
 * The stock_transfers table is truncated before each test.
 */
class AdminTransferServiceTest : AbstractIntegrationTest() {

    private val service = AdminTransferService()

    @BeforeEach
    fun cleanTransfersTable() {
        transaction(database) {
            exec("TRUNCATE TABLE stock_transfers CASCADE")
        }
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private fun makeRequest(
        srcWh: String = "wh-source",
        dstWh: String = "wh-dest",
        productId: String = "prod-001",
        quantity: Double = 10.0,
        notes: String? = null,
        srcStore: String? = null,
        dstStore: String? = null,
    ) = CreateTransferRequest(
        sourceWarehouseId = srcWh,
        destWarehouseId   = dstWh,
        sourceStoreId     = srcStore,
        destStoreId       = dstStore,
        productId         = productId,
        quantity          = quantity,
        notes             = notes,
    )

    // ── Create ─────────────────────────────────────────────────────────────────

    @Nested
    inner class Create {

        @Test
        fun `create_insertsPendingTransfer`() = runTest {
            val result = service.create(makeRequest(), "admin@test.local")

            assertNotNull(result.id)
            assertEquals("PENDING", result.status)
            assertEquals("wh-source", result.sourceWarehouseId)
            assertEquals("wh-dest", result.destWarehouseId)
            assertEquals("prod-001", result.productId)
            assertEquals(10.0, result.quantity)
            assertEquals("admin@test.local", result.createdBy)
            assertNull(result.approvedBy)
            assertNull(result.dispatchedBy)
            assertNull(result.receivedBy)
        }

        @Test
        fun `create_withOptionalFields_persisted`() = runTest {
            // Insert stores required by the FK constraint on source_store_id / dest_store_id
            TestFixtures.insertStore(id = "store-A")
            TestFixtures.insertStore(id = "store-B")

            val result = service.create(
                makeRequest(
                    notes    = "urgent shipment",
                    srcStore = "store-A",
                    dstStore = "store-B",
                ),
                createdBy = "ops@test.local",
            )

            assertEquals("urgent shipment", result.notes)
            assertEquals("store-A", result.sourceStoreId)
            assertEquals("store-B", result.destStoreId)
        }

        @Test
        fun `create_setsTimestamps`() = runTest {
            val before = System.currentTimeMillis() - 1000
            val result = service.create(makeRequest(), "admin@test.local")
            val after = System.currentTimeMillis() + 1000

            assertTrue(result.createdAt >= before)
            assertTrue(result.createdAt <= after)
            assertTrue(result.updatedAt >= before)
            assertTrue(result.updatedAt <= after)
        }

        @Test
        fun `create_multipleTransfers_allPersisted`() = runTest {
            service.create(makeRequest(productId = "p1"), "admin@test.local")
            service.create(makeRequest(productId = "p2"), "admin@test.local")
            service.create(makeRequest(productId = "p3"), "admin@test.local")

            val list = service.listTransfers()
            assertEquals(3, list.total)
        }
    }

    // ── GetById ────────────────────────────────────────────────────────────────

    @Nested
    inner class GetById {

        @Test
        fun `getById_existingTransfer_returnsDto`() = runTest {
            val created = service.create(makeRequest(), "admin@test.local")
            val result  = service.getById(created.id)

            assertNotNull(result)
            assertEquals(created.id, result.id)
            assertEquals("PENDING", result.status)
        }

        @Test
        fun `getById_nonexistent_returnsNull`() = runTest {
            val result = service.getById("transfer-does-not-exist")
            assertNull(result)
        }
    }

    // ── Approve ────────────────────────────────────────────────────────────────

    @Nested
    inner class Approve {

        @Test
        fun `approve_pendingTransfer_setsApprovedStatus`() = runTest {
            val created  = service.create(makeRequest(), "creator@test.local")
            val approved = service.approve(created.id, "approver@test.local")

            assertNotNull(approved)
            assertEquals("APPROVED", approved.status)
            assertEquals("approver@test.local", approved.approvedBy)
            assertNotNull(approved.approvedAt)
        }

        @Test
        fun `approve_notPending_returnsNull`() = runTest {
            val created  = service.create(makeRequest(), "creator@test.local")
            service.approve(created.id, "approver@test.local")                    // APPROVED
            val secondApprove = service.approve(created.id, "approver@test.local") // APPROVED → can't approve again
            assertNull(secondApprove)
        }

        @Test
        fun `approve_nonexistent_returnsNull`() = runTest {
            val result = service.approve("no-such-transfer", "approver@test.local")
            assertNull(result)
        }

        @Test
        fun `approve_cancelledTransfer_returnsNull`() = runTest {
            val created = service.create(makeRequest(), "creator@test.local")
            service.cancel(created.id, "admin@test.local")

            val result = service.approve(created.id, "approver@test.local")
            assertNull(result)
        }
    }

    // ── Dispatch ───────────────────────────────────────────────────────────────

    @Nested
    inner class Dispatch {

        @Test
        fun `dispatch_approvedTransfer_setsInTransitStatus`() = runTest {
            val created    = service.create(makeRequest(), "creator@test.local")
            service.approve(created.id, "approver@test.local")
            val dispatched = service.dispatch(created.id, "dispatcher@test.local")

            assertNotNull(dispatched)
            assertEquals("IN_TRANSIT", dispatched.status)
            assertEquals("dispatcher@test.local", dispatched.dispatchedBy)
            assertNotNull(dispatched.dispatchedAt)
        }

        @Test
        fun `dispatch_pendingTransfer_returnsNull`() = runTest {
            val created = service.create(makeRequest(), "creator@test.local")
            val result  = service.dispatch(created.id, "dispatcher@test.local")
            assertNull(result)
        }

        @Test
        fun `dispatch_nonexistent_returnsNull`() = runTest {
            val result = service.dispatch("no-such-transfer", "dispatcher@test.local")
            assertNull(result)
        }

        @Test
        fun `dispatch_alreadyInTransit_returnsNull`() = runTest {
            val created = service.create(makeRequest(), "creator@test.local")
            service.approve(created.id, "approver@test.local")
            service.dispatch(created.id, "dispatcher@test.local")
            val secondDispatch = service.dispatch(created.id, "dispatcher@test.local")
            assertNull(secondDispatch)
        }
    }

    // ── Receive ────────────────────────────────────────────────────────────────

    @Nested
    inner class Receive {

        @Test
        fun `receive_inTransitTransfer_setsReceivedStatus`() = runTest {
            val created  = service.create(makeRequest(), "creator@test.local")
            service.approve(created.id, "approver@test.local")
            service.dispatch(created.id, "dispatcher@test.local")
            val received = service.receive(created.id, "receiver@test.local")

            assertNotNull(received)
            assertEquals("RECEIVED", received.status)
            assertEquals("receiver@test.local", received.receivedBy)
            assertNotNull(received.receivedAt)
        }

        @Test
        fun `receive_approvedTransfer_returnsNull`() = runTest {
            val created = service.create(makeRequest(), "creator@test.local")
            service.approve(created.id, "approver@test.local")
            val result  = service.receive(created.id, "receiver@test.local")
            assertNull(result)
        }

        @Test
        fun `receive_pendingTransfer_returnsNull`() = runTest {
            val created = service.create(makeRequest(), "creator@test.local")
            val result  = service.receive(created.id, "receiver@test.local")
            assertNull(result)
        }

        @Test
        fun `receive_nonexistent_returnsNull`() = runTest {
            val result = service.receive("no-such-transfer", "receiver@test.local")
            assertNull(result)
        }
    }

    // ── Cancel ─────────────────────────────────────────────────────────────────

    @Nested
    inner class Cancel {

        @Test
        fun `cancel_pendingTransfer_setsCancelled`() = runTest {
            val created   = service.create(makeRequest(), "creator@test.local")
            val cancelled = service.cancel(created.id, "admin@test.local")

            assertNotNull(cancelled)
            assertEquals("CANCELLED", cancelled.status)
        }

        @Test
        fun `cancel_approvedTransfer_setsCancelled`() = runTest {
            val created = service.create(makeRequest(), "creator@test.local")
            service.approve(created.id, "approver@test.local")
            val cancelled = service.cancel(created.id, "admin@test.local")

            assertNotNull(cancelled)
            assertEquals("CANCELLED", cancelled.status)
        }

        @Test
        fun `cancel_inTransitTransfer_returnsNull`() = runTest {
            val created = service.create(makeRequest(), "creator@test.local")
            service.approve(created.id, "approver@test.local")
            service.dispatch(created.id, "dispatcher@test.local")
            val result = service.cancel(created.id, "admin@test.local")
            assertNull(result)
        }

        @Test
        fun `cancel_receivedTransfer_returnsNull`() = runTest {
            val created = service.create(makeRequest(), "creator@test.local")
            service.approve(created.id, "approver@test.local")
            service.dispatch(created.id, "dispatcher@test.local")
            service.receive(created.id, "receiver@test.local")
            val result = service.cancel(created.id, "admin@test.local")
            assertNull(result)
        }

        @Test
        fun `cancel_nonexistent_returnsNull`() = runTest {
            val result = service.cancel("no-such-transfer", "admin@test.local")
            assertNull(result)
        }

        @Test
        fun `cancel_alreadyCancelled_returnsNull`() = runTest {
            val created = service.create(makeRequest(), "creator@test.local")
            service.cancel(created.id, "admin@test.local")
            val secondCancel = service.cancel(created.id, "admin@test.local")
            assertNull(secondCancel)
        }
    }

    // ── ListTransfers ──────────────────────────────────────────────────────────

    @Nested
    inner class ListTransfers {

        @Test
        fun `listTransfers_noFilters_returnsAll`() = runTest {
            TestFixtures.insertStore(id = "s1")
            TestFixtures.insertStore(id = "s2")
            service.create(makeRequest(srcStore = "s1"), "admin@test.local")
            service.create(makeRequest(srcStore = "s2"), "admin@test.local")

            val result = service.listTransfers()
            assertEquals(2, result.total)
            assertEquals(2, result.transfers.size)
        }

        @Test
        fun `listTransfers_emptyTable_returnsEmpty`() = runTest {
            val result = service.listTransfers()
            assertEquals(0, result.total)
            assertTrue(result.transfers.isEmpty())
        }

        @Test
        fun `listTransfers_filterBySourceStoreId_returnsMatching`() = runTest {
            // Insert stores required by FK constraint on source_store_id
            TestFixtures.insertStore(id = "store-A")
            TestFixtures.insertStore(id = "store-B")

            service.create(makeRequest(srcStore = "store-A"), "admin@test.local")
            service.create(makeRequest(srcStore = "store-B"), "admin@test.local")

            val result = service.listTransfers(storeId = "store-A")
            assertEquals(1, result.total)
            assertEquals("store-A", result.transfers.single().sourceStoreId)
        }

        @Test
        fun `listTransfers_filterByDestStoreId_returnsMatching`() = runTest {
            // Insert stores required by FK constraint on dest_store_id
            TestFixtures.insertStore(id = "store-C")
            TestFixtures.insertStore(id = "store-D")

            service.create(makeRequest(dstStore = "store-C"), "admin@test.local")
            service.create(makeRequest(dstStore = "store-D"), "admin@test.local")

            val result = service.listTransfers(storeId = "store-C")
            assertEquals(1, result.total)
            assertEquals("store-C", result.transfers.single().destStoreId)
        }

        @Test
        fun `listTransfers_filterByStatus_returnsMatching`() = runTest {
            val t1 = service.create(makeRequest(), "admin@test.local")
            val t2 = service.create(makeRequest(), "admin@test.local")
            service.approve(t2.id, "approver@test.local")

            val pending  = service.listTransfers(status = "PENDING")
            val approved = service.listTransfers(status = "APPROVED")

            assertEquals(1, pending.total)
            assertEquals("PENDING", pending.transfers.single().status)
            assertEquals(1, approved.total)
            assertEquals("APPROVED", approved.transfers.single().status)
        }

        @Test
        fun `listTransfers_invalidStatus_ignored_returnsAll`() = runTest {
            service.create(makeRequest(), "admin@test.local")
            service.create(makeRequest(), "admin@test.local")

            val result = service.listTransfers(status = "INVALID_STATUS")
            assertEquals(2, result.total)
        }

        @Test
        fun `listTransfers_pagination_respectsPageSize`() = runTest {
            repeat(5) { service.create(makeRequest(productId = "p$it"), "admin@test.local") }

            val page0 = service.listTransfers(page = 0, size = 2)
            val page1 = service.listTransfers(page = 1, size = 2)
            val page2 = service.listTransfers(page = 2, size = 2)

            assertEquals(5, page0.total)
            assertEquals(2, page0.transfers.size)
            assertEquals(2, page1.transfers.size)
            assertEquals(1, page2.transfers.size)
        }

        @Test
        fun `listTransfers_orderedByCreatedAtDesc`() = runTest {
            val t1 = service.create(makeRequest(productId = "first"), "admin@test.local")
            Thread.sleep(10) // Ensure different timestamps
            val t2 = service.create(makeRequest(productId = "second"), "admin@test.local")

            val result = service.listTransfers()
            assertEquals(2, result.transfers.size)
            // Most recent first
            assertEquals(t2.id, result.transfers[0].id)
            assertEquals(t1.id, result.transfers[1].id)
        }
    }

    // ── Full workflow ──────────────────────────────────────────────────────────

    @Nested
    inner class FullWorkflow {

        @Test
        fun `fullWorkflow_pendingToReceived_allTransitionsSucceed`() = runTest {
            // Insert stores required by FK constraint on source_store_id / dest_store_id
            TestFixtures.insertStore(id = "store-main")
            TestFixtures.insertStore(id = "store-branch")

            // 1. Create
            val created = service.create(
                makeRequest(
                    srcWh    = "warehouse-main",
                    dstWh    = "warehouse-branch",
                    productId = "WIDGET-001",
                    quantity  = 50.0,
                    notes     = "Monthly restock",
                    srcStore  = "store-main",
                    dstStore  = "store-branch",
                ),
                createdBy = "ops@test.local",
            )
            assertEquals("PENDING", created.status)
            assertNull(created.approvedBy)

            // 2. Approve
            val approved = service.approve(created.id, "manager@test.local")
            assertNotNull(approved)
            assertEquals("APPROVED", approved!!.status)
            assertEquals("manager@test.local", approved.approvedBy)

            // 3. Dispatch
            val dispatched = service.dispatch(created.id, "warehouse-team@test.local")
            assertNotNull(dispatched)
            assertEquals("IN_TRANSIT", dispatched!!.status)
            assertEquals("warehouse-team@test.local", dispatched.dispatchedBy)

            // 4. Receive
            val received = service.receive(created.id, "branch-mgr@test.local")
            assertNotNull(received)
            assertEquals("RECEIVED", received!!.status)
            assertEquals("branch-mgr@test.local", received.receivedBy)
            assertNotNull(received.receivedAt)

            // Verify final state via getById
            val finalState = service.getById(created.id)
            assertNotNull(finalState)
            assertEquals("RECEIVED", finalState!!.status)
            assertEquals("ops@test.local", finalState.createdBy)
            assertEquals("manager@test.local", finalState.approvedBy)
            assertEquals("warehouse-team@test.local", finalState.dispatchedBy)
            assertEquals("branch-mgr@test.local", finalState.receivedBy)
        }

        @Test
        fun `fullWorkflow_pendingToCancelled_noFurtherTransitions`() = runTest {
            val created = service.create(makeRequest(), "admin@test.local")
            assertEquals("PENDING", created.status)

            val cancelled = service.cancel(created.id, "admin@test.local")
            assertNotNull(cancelled)
            assertEquals("CANCELLED", cancelled!!.status)

            // No further transitions allowed
            assertNull(service.approve(created.id, "admin@test.local"))
            assertNull(service.dispatch(created.id, "admin@test.local"))
            assertNull(service.receive(created.id, "admin@test.local"))
            assertNull(service.cancel(created.id, "admin@test.local"))
        }
    }
}
