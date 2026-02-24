package com.zyntasolutions.zyntapos.domain.usecase.expenses

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Expense
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeExpenseRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildExpense
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Unit tests for [ApproveExpenseUseCase].
 *
 * Covers:
 * - Approve PENDING expense → status = APPROVED
 * - Reject PENDING expense with reason → status = REJECTED
 * - Approve already APPROVED expense → ValidationException
 * - Approve already REJECTED expense → ValidationException
 * - Reject already APPROVED expense → ValidationException
 * - Expense not found → Result.Error propagated
 * - DB error on approve → propagated
 */
class ApproveExpenseUseCaseTest {

    private fun makeUseCase(repo: FakeExpenseRepository = FakeExpenseRepository()) =
        ApproveExpenseUseCase(repo) to repo

    @Test
    fun `approve PENDING expense - status becomes APPROVED`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.expenses.add(buildExpense(id = "exp-01", status = Expense.Status.PENDING))

        val result = useCase.approve("exp-01", "manager-01")

        assertIs<Result.Success<Unit>>(result)
        assertEquals(Expense.Status.APPROVED, repo.expenses.first().status)
        assertEquals("manager-01", repo.expenses.first().approvedBy)
    }

    @Test
    fun `reject PENDING expense with reason - status becomes REJECTED`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.expenses.add(buildExpense(id = "exp-02", status = Expense.Status.PENDING))

        val result = useCase.reject("exp-02", "manager-01", "Duplicate receipt")

        assertIs<Result.Success<Unit>>(result)
        assertEquals(Expense.Status.REJECTED, repo.expenses.first().status)
        assertEquals("Duplicate receipt", repo.expenses.first().rejectReason)
    }

    @Test
    fun `approve already APPROVED expense - returns ValidationException`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.expenses.add(buildExpense(id = "exp-03", status = Expense.Status.APPROVED))

        val result = useCase.approve("exp-03", "manager-01")

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertContains(result.exception.message ?: "", "APPROVED", ignoreCase = true)
    }

    @Test
    fun `approve already REJECTED expense - returns ValidationException`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.expenses.add(buildExpense(id = "exp-04", status = Expense.Status.REJECTED))

        val result = useCase.approve("exp-04", "manager-01")

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertContains(result.exception.message ?: "", "REJECTED", ignoreCase = true)
    }

    @Test
    fun `reject already APPROVED expense - returns ValidationException`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.expenses.add(buildExpense(id = "exp-05", status = Expense.Status.APPROVED))

        val result = useCase.reject("exp-05", "manager-01", reason = null)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
    }

    @Test
    fun `expense not found - returns Result Error`() = runTest {
        val (useCase, _) = makeUseCase()

        val result = useCase.approve("non-existent", "manager-01")

        assertIs<Result.Error>(result)
    }

    @Test
    fun `DB error on approve - propagated as Result Error`() = runTest {
        val repo = FakeExpenseRepository().also {
            it.expenses.add(buildExpense(id = "exp-db", status = Expense.Status.PENDING))
            it.shouldFail = true
        }
        val useCase = ApproveExpenseUseCase(repo)

        val result = useCase.approve("exp-db", "manager-01")

        assertIs<Result.Error>(result)
    }
}
