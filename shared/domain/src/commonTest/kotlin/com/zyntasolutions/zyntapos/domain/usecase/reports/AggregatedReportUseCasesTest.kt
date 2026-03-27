package com.zyntasolutions.zyntapos.domain.usecase.reports

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.domain.model.Expense
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeCustomerRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeExpenseRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildCustomer
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildExpense
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for aggregation-based report use cases:
 * [GenerateExpenseReportUseCase], [GenerateCustomerReportUseCase].
 *
 * These use cases perform non-trivial grouping/aggregation in the domain layer,
 * unlike the thin-wrapper use cases in [StandardReportUseCasesTest].
 */
class AggregatedReportUseCasesTest {

    // ─────────────────────────────────────────────────────────────────────────
    // GenerateExpenseReportUseCase
    // ─────────────────────────────────────────────────────────────────────────

    // Use a very wide range so all expense dates pass the filter
    private val from = 0L
    private val to   = Long.MAX_VALUE

    private fun addExpenses(repo: FakeExpenseRepository, vararg expenses: Expense) {
        repo.expenses.addAll(expenses)
    }

    @Test
    fun `expense_emptyRepository_allZero`() = runTest {
        GenerateExpenseReportUseCase(FakeExpenseRepository())(from, to).test {
            val report = awaitItem()
            assertEquals(0.0, report.totalApproved)
            assertEquals(0.0, report.totalPending)
            assertEquals(0.0, report.totalRejected)
            assertEquals(0, report.approvedCount)
            assertEquals(0, report.pendingCount)
            assertEquals(0, report.rejectedCount)
            assertTrue(report.byCategory.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `expense_onlyApproved_reportsCorrectTotals`() = runTest {
        val repo = FakeExpenseRepository()
        addExpenses(repo,
            buildExpense(id = "e1", amount = 200.0, status = Expense.Status.APPROVED),
            buildExpense(id = "e2", amount = 300.0, status = Expense.Status.APPROVED),
        )
        GenerateExpenseReportUseCase(repo)(from, to).test {
            val report = awaitItem()
            assertEquals(500.0, report.totalApproved)
            assertEquals(0.0, report.totalPending)
            assertEquals(2, report.approvedCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `expense_mixedStatuses_segregatesCorrectly`() = runTest {
        val repo = FakeExpenseRepository()
        addExpenses(repo,
            buildExpense(id = "e1", amount = 100.0, status = Expense.Status.APPROVED),
            buildExpense(id = "e2", amount = 50.0,  status = Expense.Status.PENDING),
            buildExpense(id = "e3", amount = 75.0,  status = Expense.Status.REJECTED),
        )
        GenerateExpenseReportUseCase(repo)(from, to).test {
            val report = awaitItem()
            assertEquals(100.0, report.totalApproved)
            assertEquals(50.0,  report.totalPending)
            assertEquals(75.0,  report.totalRejected)
            assertEquals(1, report.approvedCount)
            assertEquals(1, report.pendingCount)
            assertEquals(1, report.rejectedCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `expense_byCategoryIncludesOnlyApproved`() = runTest {
        val repo = FakeExpenseRepository()
        addExpenses(repo,
            buildExpense(id = "e1", amount = 100.0, categoryId = "cat-A", status = Expense.Status.APPROVED),
            buildExpense(id = "e2", amount = 200.0, categoryId = "cat-A", status = Expense.Status.APPROVED),
            buildExpense(id = "e3", amount = 999.0, categoryId = "cat-A", status = Expense.Status.PENDING),
            buildExpense(id = "e4", amount = 50.0,  categoryId = "cat-B", status = Expense.Status.APPROVED),
        )
        GenerateExpenseReportUseCase(repo)(from, to).test {
            val report = awaitItem()
            assertEquals(300.0, report.byCategory["cat-A"])
            assertEquals(50.0,  report.byCategory["cat-B"])
            assertEquals(2, report.byCategory.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `expense_uncategorisedExpensesGroupedUnderNullKey`() = runTest {
        val repo = FakeExpenseRepository()
        addExpenses(repo,
            buildExpense(id = "e1", amount = 80.0, categoryId = null, status = Expense.Status.APPROVED),
        )
        GenerateExpenseReportUseCase(repo)(from, to).test {
            val report = awaitItem()
            assertEquals(80.0, report.byCategory[null])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `expense_fromAndToTimestampsPreservedInReport`() = runTest {
        val customFrom = 1_000L
        val customTo   = 2_000L
        GenerateExpenseReportUseCase(FakeExpenseRepository())(customFrom, customTo).test {
            val report = awaitItem()
            assertEquals(customFrom, report.from)
            assertEquals(customTo,   report.to)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GenerateCustomerReportUseCase
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `customer_emptyRepository_allZero`() = runTest {
        val repo = FakeCustomerRepository()

        GenerateCustomerReportUseCase(repo)().test {
            val report = awaitItem()
            assertEquals(0, report.totalCustomers)
            assertEquals(0, report.registeredCustomers)
            assertEquals(0, report.walkInCustomers)
            assertEquals(0L, report.totalLoyaltyPoints)
            assertTrue(report.topByLoyaltyPoints.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `customer_countsRegisteredAndWalkIn`() = runTest {
        val repo = FakeCustomerRepository()
        repo.insert(buildCustomer(id = "c1"))                          // isWalkIn = false (default)
        repo.insert(buildCustomer(id = "c2").copy(isWalkIn = true))
        repo.insert(buildCustomer(id = "c3"))

        GenerateCustomerReportUseCase(repo)().test {
            val report = awaitItem()
            assertEquals(3, report.totalCustomers)
            assertEquals(2, report.registeredCustomers)
            assertEquals(1, report.walkInCustomers)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `customer_countsCreditEnabled`() = runTest {
        val repo = FakeCustomerRepository()
        repo.insert(buildCustomer(id = "c1", creditEnabled = true))
        repo.insert(buildCustomer(id = "c2", creditEnabled = false))
        repo.insert(buildCustomer(id = "c3", creditEnabled = true))

        GenerateCustomerReportUseCase(repo)().test {
            val report = awaitItem()
            assertEquals(2, report.creditEnabledCustomers)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `customer_sumsTotalLoyaltyPoints`() = runTest {
        val repo = FakeCustomerRepository()
        repo.insert(buildCustomer(id = "c1", loyaltyPoints = 100))
        repo.insert(buildCustomer(id = "c2", loyaltyPoints = 250))
        repo.insert(buildCustomer(id = "c3", loyaltyPoints = 50))

        GenerateCustomerReportUseCase(repo)().test {
            val report = awaitItem()
            assertEquals(400L, report.totalLoyaltyPoints)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `customer_topByLoyaltyPoints_sorted_descending_capped_at_10`() = runTest {
        val repo = FakeCustomerRepository()
        (1..15).forEach { i ->
            repo.insert(buildCustomer(id = "c$i", loyaltyPoints = i * 10))
        }

        GenerateCustomerReportUseCase(repo)().test {
            val report = awaitItem()
            assertEquals(10, report.topByLoyaltyPoints.size)
            // Top entry should have the highest points (150)
            assertEquals(150, report.topByLoyaltyPoints.first().loyaltyPoints)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `customer_byGroupCount_groupsCorrectly`() = runTest {
        val repo = FakeCustomerRepository()
        repo.insert(buildCustomer(id = "c1", groupId = "vip"))
        repo.insert(buildCustomer(id = "c2", groupId = "vip"))
        repo.insert(buildCustomer(id = "c3", groupId = "regular"))
        repo.insert(buildCustomer(id = "c4", groupId = null))

        GenerateCustomerReportUseCase(repo)().test {
            val report = awaitItem()
            assertEquals(2, report.byGroup["vip"])
            assertEquals(1, report.byGroup["regular"])
            assertEquals(1, report.byGroup[null])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `customer_storeIdFilter_returnsOnlyMatchingStore`() = runTest {
        val repo = FakeCustomerRepository()
        repo.insert(buildCustomer(id = "c1").copy(storeId = "store-01"))
        repo.insert(buildCustomer(id = "c2").copy(storeId = "store-02"))
        repo.insert(buildCustomer(id = "c3").copy(storeId = "store-01"))

        GenerateCustomerReportUseCase(repo)(storeId = "store-01").test {
            val report = awaitItem()
            assertEquals(2, report.totalCustomers)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `customer_nullStoreId_returnsAllCustomers`() = runTest {
        val repo = FakeCustomerRepository()
        repo.insert(buildCustomer(id = "c1").copy(storeId = "store-01"))
        repo.insert(buildCustomer(id = "c2").copy(storeId = "store-02"))

        GenerateCustomerReportUseCase(repo)(storeId = null).test {
            val report = awaitItem()
            assertEquals(2, report.totalCustomers)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
