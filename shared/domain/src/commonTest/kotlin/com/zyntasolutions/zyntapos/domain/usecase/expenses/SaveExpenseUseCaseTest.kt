package com.zyntasolutions.zyntapos.domain.usecase.expenses

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeExpenseRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildExpense
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [SaveExpenseUseCase].
 *
 * Covers:
 * - Happy path insert: expense persisted correctly
 * - Happy path update: existing expense updated
 * - Blank description: [ValidationException] without DB write
 * - Zero/negative amount: [ValidationException] (model rejects via require at construction)
 * - DB error: propagated as [Result.Error]
 */
class SaveExpenseUseCaseTest {

    private fun makeUseCase(repo: FakeExpenseRepository = FakeExpenseRepository()) =
        SaveExpenseUseCase(repo) to repo

    @Test
    fun `insert new expense with valid data - persisted and returns Success`() = runTest {
        val (useCase, repo) = makeUseCase()
        val expense = buildExpense(id = "exp-new", description = "New Laptop", amount = 1500.0)

        val result = useCase(expense, isNew = true)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.expenses.size)
        assertEquals("New Laptop", repo.expenses.first().description)
    }

    @Test
    fun `update existing expense - replaces old record`() = runTest {
        val (useCase, repo) = makeUseCase()
        val original = buildExpense(id = "exp-01", description = "Coffee", amount = 50.0)
        repo.expenses.add(original)

        val updated = original.copy(description = "Coffee & Snacks", amount = 75.0)
        val result = useCase(updated, isNew = false)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.expenses.size)
        assertEquals("Coffee & Snacks", repo.expenses.first().description)
        assertEquals(75.0, repo.expenses.first().amount)
    }

    @Test
    fun `blank description - returns ValidationException without DB write`() = runTest {
        val (useCase, repo) = makeUseCase()
        val expense = buildExpense(description = "   ")

        val result = useCase(expense, isNew = true)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertTrue(repo.expenses.isEmpty(), "No write should occur for blank description")
    }

    @Test
    fun `zero amount - domain model rejects at construction`() {
        // Expense.init enforces amount > 0.0. The use case also validates this as a secondary guard.
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            buildExpense(amount = 0.0)
        }
    }

    @Test
    fun `DB error - propagated as Result Error`() = runTest {
        val repo = FakeExpenseRepository().also { it.shouldFail = true }
        val useCase = SaveExpenseUseCase(repo)
        val expense = buildExpense(description = "Valid", amount = 100.0)

        val result = useCase(expense, isNew = true)

        assertIs<Result.Error>(result)
    }
}
