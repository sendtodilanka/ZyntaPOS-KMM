package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.InstallmentPayment
import com.zyntasolutions.zyntapos.domain.model.InstallmentPlan
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — InstallmentRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [InstallmentRepositoryImpl] against a real in-memory SQLite database.
 * Requires customers seeded to satisfy customer_id FK constraint.
 *
 * Coverage:
 *  A. createPlan → getPlanById round-trip preserves all fields
 *  B. getPlansByCustomer emits plans for customer via Turbine
 *  C. getActivePlans returns only ACTIVE plans
 *  D. recordPayment updates paid_amount, remaining_amount and auto-completes when fully paid
 *  E. getDuePayments returns payments due before given timestamp
 *  F. getPaymentsByPlan emits payment schedule via Turbine
 *  G. updatePlanStatus changes status to DEFAULTED
 */
class InstallmentRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: InstallmentRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = InstallmentRepositoryImpl(db, SyncEnqueuer(db))

        val now = Clock.System.now().toEpochMilliseconds()

        // Seed customers required by customer_id FK constraint
        db.customersQueries.insertCustomer(
            id = "cust-01", store_id = "store-01",
            name = "Alice Smith",
            email = null, phone = null, address = null,
            group_id = null, loyalty_points = 0L,
            notes = null, is_active = 1L,
            credit_limit = 0.0, credit_enabled = 0L,
            gender = null, birthday = null, is_walk_in = 0L,
            created_at = now, updated_at = now,
            sync_status = "PENDING",
        )
        db.customersQueries.insertCustomer(
            id = "cust-02", store_id = "store-01",
            name = "Bob Jones",
            email = null, phone = null, address = null,
            group_id = null, loyalty_points = 0L,
            notes = null, is_active = 1L,
            credit_limit = 0.0, credit_enabled = 0L,
            gender = null, birthday = null, is_walk_in = 0L,
            created_at = now, updated_at = now,
            sync_status = "PENDING",
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makePlan(
        id: String = "plan-01",
        orderId: String = "order-01",
        customerId: String = "cust-01",
        totalAmount: Double = 30000.0,
        numInstallments: Int = 3,
        frequency: InstallmentPlan.Frequency = InstallmentPlan.Frequency.MONTHLY,
        status: InstallmentPlan.Status = InstallmentPlan.Status.ACTIVE,
    ) = InstallmentPlan(
        id = id,
        orderId = orderId,
        customerId = customerId,
        totalAmount = totalAmount,
        paidAmount = 0.0,
        remainingAmount = totalAmount,
        numInstallments = numInstallments,
        frequency = frequency,
        startDate = now,
        endDate = null,
        status = status,
        notes = null,
    )

    private fun makePayments(planId: String, totalAmount: Double, numInstallments: Int): List<InstallmentPayment> {
        val installmentAmount = totalAmount / numInstallments
        return (1..numInstallments).map { i ->
            InstallmentPayment(
                id = "$planId-pmt-$i",
                planId = planId,
                dueDate = now + i * 30L * 24 * 3_600_000L,
                amount = installmentAmount,
                paidAmount = 0.0,
                status = InstallmentPayment.Status.PENDING,
            )
        }
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - createPlan then getPlanById round-trip preserves all fields`() = runTest {
        val plan = makePlan(id = "plan-01", totalAmount = 30000.0, numInstallments = 3)
        val payments = makePayments("plan-01", 30000.0, 3)

        val createResult = repo.createPlan(plan, payments)
        assertIs<Result.Success<Unit>>(createResult)

        val fetchResult = repo.getPlanById("plan-01")
        assertIs<Result.Success<InstallmentPlan>>(fetchResult)
        val fetched = fetchResult.data
        assertEquals("plan-01", fetched.id)
        assertEquals("cust-01", fetched.customerId)
        assertEquals(30000.0, fetched.totalAmount)
        assertEquals(3, fetched.numInstallments)
        assertEquals(InstallmentPlan.Status.ACTIVE, fetched.status)
    }

    @Test
    fun `B - getPlansByCustomer emits plans for customer via Turbine`() = runTest {
        repo.createPlan(makePlan(id = "plan-01", customerId = "cust-01"), emptyList())
        repo.createPlan(makePlan(id = "plan-02", customerId = "cust-01", orderId = "order-02"), emptyList())
        repo.createPlan(makePlan(id = "plan-03", customerId = "cust-02", orderId = "order-03"), emptyList())

        repo.getPlansByCustomer("cust-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.all { it.customerId == "cust-01" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getActivePlans returns only ACTIVE plans`() = runTest {
        repo.createPlan(makePlan(id = "plan-active", status = InstallmentPlan.Status.ACTIVE), emptyList())
        repo.createPlan(makePlan(id = "plan-completed", customerId = "cust-02",
            orderId = "order-02", status = InstallmentPlan.Status.COMPLETED), emptyList())

        val result = repo.getActivePlans()
        assertIs<Result.Success<List<InstallmentPlan>>>(result)
        assertEquals(1, result.data.size)
        assertEquals("plan-active", result.data.first().id)
    }

    @Test
    fun `D - recordPayment updates paid_amount and auto-completes when fully paid`() = runTest {
        val plan = makePlan(id = "plan-01", totalAmount = 10000.0, numInstallments = 1)
        val payments = makePayments("plan-01", 10000.0, 1)
        repo.createPlan(plan, payments)

        val payment = payments.first().copy(
            paidAmount = 10000.0,
            paidAt = now,
            status = InstallmentPayment.Status.PAID,
        )
        val recordResult = repo.recordPayment("plan-01", payment)
        assertIs<Result.Success<Unit>>(recordResult)

        // Plan should now be COMPLETED (remaining = 0)
        val fetched = (repo.getPlanById("plan-01") as Result.Success).data
        assertEquals(InstallmentPlan.Status.COMPLETED, fetched.status)
        assertEquals(10000.0, fetched.paidAmount)
        assertEquals(0.0, fetched.remainingAmount)
    }

    @Test
    fun `E - getDuePayments returns payments due before given timestamp`() = runTest {
        val pastDue = now - 3_600_000L  // 1 hour ago
        val futureDue = now + 3_600_000L // 1 hour from now

        val plan = makePlan(id = "plan-01")
        val overduePayment = InstallmentPayment(
            id = "plan-01-pmt-overdue", planId = "plan-01",
            dueDate = pastDue, amount = 10000.0, status = InstallmentPayment.Status.PENDING,
        )
        val pendingPayment = InstallmentPayment(
            id = "plan-01-pmt-future", planId = "plan-01",
            dueDate = futureDue, amount = 10000.0, status = InstallmentPayment.Status.PENDING,
        )
        repo.createPlan(plan, listOf(overduePayment, pendingPayment))

        val result = repo.getDuePayments(now)
        assertIs<Result.Success<List<InstallmentPayment>>>(result)
        assertEquals(1, result.data.size)
        assertEquals("plan-01-pmt-overdue", result.data.first().id)
    }

    @Test
    fun `F - getPaymentsByPlan emits payment schedule via Turbine`() = runTest {
        val plan = makePlan(id = "plan-01", totalAmount = 30000.0, numInstallments = 3)
        val payments = makePayments("plan-01", 30000.0, 3)
        repo.createPlan(plan, payments)

        repo.getPaymentsByPlan("plan-01").test {
            val list = awaitItem()
            assertEquals(3, list.size)
            assertTrue(list.all { it.planId == "plan-01" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `G - updatePlanStatus changes status to DEFAULTED`() = runTest {
        repo.createPlan(makePlan(id = "plan-01", status = InstallmentPlan.Status.ACTIVE), emptyList())

        val updateResult = repo.updatePlanStatus("plan-01", InstallmentPlan.Status.DEFAULTED)
        assertIs<Result.Success<Unit>>(updateResult)

        val fetched = (repo.getPlanById("plan-01") as Result.Success).data
        assertEquals(InstallmentPlan.Status.DEFAULTED, fetched.status)
    }
}
