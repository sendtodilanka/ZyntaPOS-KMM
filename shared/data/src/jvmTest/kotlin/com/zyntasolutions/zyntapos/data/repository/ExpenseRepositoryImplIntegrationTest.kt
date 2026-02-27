package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Expense
import com.zyntasolutions.zyntapos.domain.model.ExpenseCategory
import com.zyntasolutions.zyntapos.domain.model.RecurringExpense
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — ExpenseRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates ExpenseRepositoryImpl against a real in-memory SQLite database.
 * No mocks — all assertions exercise actual SQLDelight-generated queries.
 *
 * Coverage:
 *  A. insert expense → getById round-trip preserves all fields
 *  B. getAll returns all inserted expenses as a Flow
 *  C. getByStatus returns only expenses matching the given status
 *  D. approve sets status = APPROVED and records approvedBy
 *  E. reject sets status = REJECTED and records rejectReason
 *  F. update changes amount and description
 *  G. delete removes the expense; getById returns Result.Error
 *  H. getById for unknown id returns Result.Error
 *  I. getByDateRange returns only expenses within the range
 *  J. getTotalByPeriod sums only APPROVED expenses in the range
 *  K. saveCategory (insert) → getCategoryById round-trip
 *  L. saveCategory (update) changes existing category
 *  M. getAllCategories returns all categories as a Flow
 *  N. deleteCategory removes category; getCategoryById returns Result.Error
 *  O. saveRecurring (insert) → getAllRecurring includes new entry
 *  P. getActiveRecurring returns only is_active = 1 recurring expenses
 *  Q. deleteRecurring removes the recurring entry
 */
class ExpenseRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: ExpenseRepositoryImpl

    private val baseDate = 1_700_000_000_000L  // arbitrary epoch ms

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        val syncEnqueuer = SyncEnqueuer(db)
        repo = ExpenseRepositoryImpl(db, syncEnqueuer)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeExpense(
        id: String          = "exp-1",
        storeId: String?    = "store-1",
        categoryId: String? = null,
        amount: Double      = 100.0,
        description: String = "Office supplies",
        expenseDate: Long   = baseDate,
        isRecurring: Boolean = false,
        status: Expense.Status = Expense.Status.PENDING,
        createdBy: String?  = "user-1",
    ) = Expense(
        id          = id,
        storeId     = storeId,
        categoryId  = categoryId,
        amount      = amount,
        description = description,
        expenseDate = expenseDate,
        isRecurring = isRecurring,
        status      = status,
        createdBy   = createdBy,
    )

    private fun makeCategory(
        id: String          = "cat-1",
        name: String        = "Utilities",
        description: String? = null,
        parentId: String?   = null,
    ) = ExpenseCategory(
        id          = id,
        name        = name,
        description = description,
        parentId    = parentId,
    )

    private fun makeRecurring(
        id: String          = "rec-1",
        storeId: String?    = "store-1",
        categoryId: String? = null,
        amount: Double      = 500.0,
        description: String = "Monthly rent",
        frequency: RecurringExpense.Frequency = RecurringExpense.Frequency.MONTHLY,
        startDate: Long     = baseDate,
        isActive: Boolean   = true,
    ) = RecurringExpense(
        id          = id,
        storeId     = storeId,
        categoryId  = categoryId,
        amount      = amount,
        description = description,
        frequency   = frequency,
        startDate   = startDate,
        isActive    = isActive,
    )

    // ── A. insert → getById round-trip ────────────────────────────────────────

    @Test
    fun insert_then_getById_preserves_all_fields() = runTest {
        val expense = makeExpense(
            id          = "exp-rt",
            storeId     = "store-rt",
            amount      = 250.0,
            description = "Stationery purchase",
            expenseDate = baseDate + 1000L,
            isRecurring = false,
            status      = Expense.Status.PENDING,
            createdBy   = "user-admin",
        )
        val insertResult = repo.insert(expense)
        assertIs<Result.Success<Unit>>(insertResult)

        val result = repo.getById("exp-rt")
        assertIs<Result.Success<Expense>>(result)
        val retrieved = result.data
        assertEquals("exp-rt",              retrieved.id)
        assertEquals("store-rt",            retrieved.storeId)
        assertEquals(250.0,                 retrieved.amount)
        assertEquals("Stationery purchase", retrieved.description)
        assertEquals(baseDate + 1000L,      retrieved.expenseDate)
        assertEquals(false,                 retrieved.isRecurring)
        assertEquals(Expense.Status.PENDING, retrieved.status)
        assertEquals("user-admin",          retrieved.createdBy)
    }

    // ── B. getAll returns all expenses as a Flow ──────────────────────────────

    @Test
    fun getAll_returns_all_inserted_expenses() = runTest {
        repo.insert(makeExpense(id = "exp-b1", amount = 50.0))
        repo.insert(makeExpense(id = "exp-b2", amount = 75.0))

        repo.getAll().test {
            val items = awaitItem()
            assertEquals(2, items.size)
            assertTrue(items.any { it.id == "exp-b1" })
            assertTrue(items.any { it.id == "exp-b2" })
            cancel()
        }
    }

    // ── C. getByStatus returns only matching status ───────────────────────────

    @Test
    fun getByStatus_returns_only_expenses_with_matching_status() = runTest {
        repo.insert(makeExpense(id = "exp-pending",  status = Expense.Status.PENDING))
        repo.insert(makeExpense(id = "exp-approved", status = Expense.Status.APPROVED))
        repo.insert(makeExpense(id = "exp-rejected", status = Expense.Status.REJECTED))

        repo.getByStatus(Expense.Status.PENDING).test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("exp-pending", items[0].id)
            cancel()
        }

        repo.getByStatus(Expense.Status.APPROVED).test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("exp-approved", items[0].id)
            cancel()
        }
    }

    // ── D. approve sets status = APPROVED ─────────────────────────────────────

    @Test
    fun approve_sets_status_to_approved_and_records_approver() = runTest {
        repo.insert(makeExpense(id = "exp-appr", status = Expense.Status.PENDING))

        val approveResult = repo.approve("exp-appr", "manager-1")
        assertIs<Result.Success<Unit>>(approveResult)

        val result = repo.getById("exp-appr")
        assertIs<Result.Success<Expense>>(result)
        assertEquals(Expense.Status.APPROVED, result.data.status)
        assertEquals("manager-1",             result.data.approvedBy)
        assertNotNull(result.data.approvedAt)
    }

    // ── E. reject sets status = REJECTED with reason ──────────────────────────

    @Test
    fun reject_sets_status_to_rejected_with_reason() = runTest {
        repo.insert(makeExpense(id = "exp-rej", status = Expense.Status.PENDING))

        val rejectResult = repo.reject("exp-rej", "manager-2", "Duplicate entry")
        assertIs<Result.Success<Unit>>(rejectResult)

        val result = repo.getById("exp-rej")
        assertIs<Result.Success<Expense>>(result)
        assertEquals(Expense.Status.REJECTED, result.data.status)
        assertEquals("Duplicate entry",       result.data.rejectReason)
        assertEquals("manager-2",             result.data.approvedBy)
    }

    // ── F. update changes amount and description ──────────────────────────────

    @Test
    fun update_changes_amount_and_description() = runTest {
        val original = makeExpense(id = "exp-upd", amount = 100.0, description = "Old desc")
        repo.insert(original)

        val updated = original.copy(amount = 150.0, description = "New desc")
        val updateResult = repo.update(updated)
        assertIs<Result.Success<Unit>>(updateResult)

        val result = repo.getById("exp-upd")
        assertIs<Result.Success<Expense>>(result)
        assertEquals(150.0,     result.data.amount)
        assertEquals("New desc", result.data.description)
    }

    // ── G. delete removes expense; getById returns Result.Error ───────────────

    @Test
    fun delete_removes_expense_and_getById_returns_error() = runTest {
        repo.insert(makeExpense(id = "exp-del"))

        val deleteResult = repo.delete("exp-del")
        assertIs<Result.Success<Unit>>(deleteResult)

        val result = repo.getById("exp-del")
        assertIs<Result.Error>(result)
    }

    // ── H. getById for unknown id returns Result.Error ────────────────────────

    @Test
    fun getById_for_unknown_id_returns_error() = runTest {
        val result = repo.getById("does-not-exist")
        assertIs<Result.Error>(result)
    }

    // ── I. getByDateRange returns expenses within range ───────────────────────

    @Test
    fun getByDateRange_returns_only_expenses_within_range() = runTest {
        val t0 = baseDate
        val t1 = baseDate + 1_000L
        val t2 = baseDate + 2_000L
        val t3 = baseDate + 3_000L

        repo.insert(makeExpense(id = "exp-in1", expenseDate = t1))
        repo.insert(makeExpense(id = "exp-in2", expenseDate = t2))
        repo.insert(makeExpense(id = "exp-out", expenseDate = t3 + 1_000L))

        repo.getByDateRange(t0, t3).test {
            val items = awaitItem()
            assertEquals(2, items.size)
            assertTrue(items.any { it.id == "exp-in1" })
            assertTrue(items.any { it.id == "exp-in2" })
            cancel()
        }
    }

    // ── J. getTotalByPeriod sums only APPROVED expenses ───────────────────────

    @Test
    fun getTotalByPeriod_sums_only_approved_expenses_in_range() = runTest {
        val t0 = baseDate
        val t1 = baseDate + 500L
        val t2 = baseDate + 1_000L

        // Insert APPROVED expenses in range
        repo.insert(makeExpense(id = "exp-appr1", amount = 100.0, expenseDate = t1, status = Expense.Status.PENDING))
        repo.insert(makeExpense(id = "exp-appr2", amount = 200.0, expenseDate = t2, status = Expense.Status.PENDING))
        repo.insert(makeExpense(id = "exp-pend",  amount = 50.0,  expenseDate = t1, status = Expense.Status.PENDING))

        // Approve two of them
        repo.approve("exp-appr1", "mgr-1")
        repo.approve("exp-appr2", "mgr-1")
        // exp-pend remains PENDING — should not be counted

        val result = repo.getTotalByPeriod(t0, t2 + 1_000L)
        assertIs<Result.Success<Double>>(result)
        assertEquals(300.0, result.data, absoluteTolerance = 0.001)
    }

    // ── K. saveCategory (insert) → getCategoryById round-trip ────────────────

    @Test
    fun saveCategory_insert_then_getCategoryById_preserves_fields() = runTest {
        val category = makeCategory(
            id          = "cat-rt",
            name        = "Rent & Utilities",
            description = "Recurring fixed costs",
        )
        val saveResult = repo.saveCategory(category)
        assertIs<Result.Success<Unit>>(saveResult)

        val result = repo.getCategoryById("cat-rt")
        assertIs<Result.Success<ExpenseCategory>>(result)
        assertEquals("cat-rt",               result.data.id)
        assertEquals("Rent & Utilities",     result.data.name)
        assertEquals("Recurring fixed costs", result.data.description)
    }

    // ── L. saveCategory (update) changes existing category ───────────────────

    @Test
    fun saveCategory_update_changes_name_and_description() = runTest {
        val category = makeCategory(id = "cat-upd", name = "Old Name", description = "Old desc")
        repo.saveCategory(category)

        val updated = category.copy(name = "New Name", description = "New desc")
        val updateResult = repo.saveCategory(updated)
        assertIs<Result.Success<Unit>>(updateResult)

        val result = repo.getCategoryById("cat-upd")
        assertIs<Result.Success<ExpenseCategory>>(result)
        assertEquals("New Name", result.data.name)
        assertEquals("New desc", result.data.description)
    }

    // ── M. getAllCategories returns all categories as a Flow ──────────────────

    @Test
    fun getAllCategories_returns_all_inserted_categories() = runTest {
        repo.saveCategory(makeCategory(id = "cat-1", name = "Food"))
        repo.saveCategory(makeCategory(id = "cat-2", name = "Transport"))

        repo.getAllCategories().test {
            val items = awaitItem()
            assertEquals(2, items.size)
            assertTrue(items.any { it.id == "cat-1" })
            assertTrue(items.any { it.id == "cat-2" })
            cancel()
        }
    }

    // ── N. deleteCategory removes category; getCategoryById returns Error ─────

    @Test
    fun deleteCategory_removes_category_and_getCategoryById_returns_error() = runTest {
        repo.saveCategory(makeCategory(id = "cat-del"))

        val deleteResult = repo.deleteCategory("cat-del")
        assertIs<Result.Success<Unit>>(deleteResult)

        val result = repo.getCategoryById("cat-del")
        assertIs<Result.Error>(result)
    }

    // ── O. saveRecurring (insert) → getAllRecurring includes entry ────────────

    @Test
    fun saveRecurring_insert_then_getAllRecurring_includes_new_entry() = runTest {
        val recurring = makeRecurring(
            id          = "rec-rt",
            amount      = 1000.0,
            description = "Office rent",
            frequency   = RecurringExpense.Frequency.MONTHLY,
        )
        val saveResult = repo.saveRecurring(recurring)
        assertIs<Result.Success<Unit>>(saveResult)

        repo.getAllRecurring().test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("rec-rt", items[0].id)
            assertEquals(1000.0,   items[0].amount)
            cancel()
        }
    }

    // ── P. getActiveRecurring returns only is_active = 1 ─────────────────────

    @Test
    fun getActiveRecurring_returns_only_active_recurring_expenses() = runTest {
        repo.saveRecurring(makeRecurring(id = "rec-active",   isActive = true))
        repo.saveRecurring(makeRecurring(id = "rec-inactive", isActive = false, description = "Cancelled lease"))

        val result = repo.getActiveRecurring()
        assertIs<Result.Success<List<RecurringExpense>>>(result)
        assertEquals(1, result.data.size)
        assertEquals("rec-active", result.data[0].id)
    }

    // ── Q. deleteRecurring removes the recurring entry ────────────────────────

    @Test
    fun deleteRecurring_removes_recurring_expense_from_getAllRecurring() = runTest {
        repo.saveRecurring(makeRecurring(id = "rec-del"))
        repo.saveRecurring(makeRecurring(id = "rec-keep", description = "Keep this one"))

        val deleteResult = repo.deleteRecurring("rec-del")
        assertIs<Result.Success<Unit>>(deleteResult)

        repo.getAllRecurring().test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("rec-keep", items[0].id)
            cancel()
        }
    }
}
