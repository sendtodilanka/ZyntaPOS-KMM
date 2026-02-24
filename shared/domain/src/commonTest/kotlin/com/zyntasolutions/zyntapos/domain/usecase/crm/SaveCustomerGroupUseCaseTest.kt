package com.zyntasolutions.zyntapos.domain.usecase.crm

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.CustomerGroup
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeCustomerGroupRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildCustomerGroup
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [SaveCustomerGroupUseCase].
 *
 * Covers:
 * - Happy path insert: group persisted correctly
 * - Happy path update: existing group updated
 * - Blank name: [ValidationException] without DB write
 * - Negative discount value: [ValidationException]
 * - DB error: propagated as [Result.Error]
 */
class SaveCustomerGroupUseCaseTest {

    private fun makeUseCase(repo: FakeCustomerGroupRepository = FakeCustomerGroupRepository()) =
        SaveCustomerGroupUseCase(repo) to repo

    @Test
    fun `save new group with valid data - persisted and returns Success`() = runTest {
        val (useCase, repo) = makeUseCase()
        val group = buildCustomerGroup(id = "grp-new", name = "Premium")

        val result = useCase(group, isNew = true)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.store.size)
        assertEquals("Premium", repo.store.first().name)
    }

    @Test
    fun `update existing group - replaces old record`() = runTest {
        val (useCase, repo) = makeUseCase()
        val original = buildCustomerGroup(id = "grp-01", name = "Basic")
        repo.store.add(original)

        val updated = original.copy(name = "Basic Plus", discountValue = 5.0)
        val result = useCase(updated, isNew = false)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.store.size)
        assertEquals("Basic Plus", repo.store.first().name)
        assertEquals(5.0, repo.store.first().discountValue)
    }

    @Test
    fun `save group with blank name - returns ValidationException without DB write`() = runTest {
        val (useCase, repo) = makeUseCase()
        val group = buildCustomerGroup(name = "   ")

        val result = useCase(group, isNew = true)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertTrue(repo.store.isEmpty(), "No write should occur for invalid group")
    }

    @Test
    fun `save group with negative discount - domain model rejects at construction`() {
        // CustomerGroup.init enforces discountValue >= 0.0 as a domain invariant.
        // The use case guard is a secondary safety net for corrupt data paths.
        assertFailsWith<IllegalArgumentException> {
            buildCustomerGroup(discountValue = -5.0)
        }
    }

    @Test
    fun `discount value above 100 - returns ValidationException`() = runTest {
        val (useCase, _) = makeUseCase()
        val group = buildCustomerGroup(discountValue = 101.0)

        val result = useCase(group, isNew = true)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
    }

    @Test
    fun `valid WHOLESALE price type - accepted without error`() = runTest {
        val (useCase, repo) = makeUseCase()
        val group = buildCustomerGroup(name = "Wholesale Partners", priceType = CustomerGroup.PriceType.WHOLESALE)

        val result = useCase(group, isNew = true)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(CustomerGroup.PriceType.WHOLESALE, repo.store.first().priceType)
    }

    @Test
    fun `DB error - propagated as Result Error`() = runTest {
        val repo = FakeCustomerGroupRepository().also { it.shouldFail = true }
        val useCase = SaveCustomerGroupUseCase(repo)
        val group = buildCustomerGroup(name = "Good Group")

        val result = useCase(group, isNew = true)

        assertIs<Result.Error>(result)
    }
}
