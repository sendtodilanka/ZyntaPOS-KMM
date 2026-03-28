package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Category
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeCategoryRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — SaveCategoryUseCase Unit Tests (commonTest)
 *
 * Coverage:
 *  A.  Blank name returns REQUIRED error
 *  B.  Whitespace-only name returns REQUIRED error
 *  C.  Category is its own parent returns SELF_REFERENCE error
 *  D.  Null parentId is valid (root category)
 *  E.  Different parentId is valid
 *  F.  Negative displayOrder returns MIN_VALUE error
 *  G.  Zero displayOrder is valid
 *  H.  Insert path used when isUpdate=false
 *  I.  Update path used when isUpdate=true
 */
class SaveCategoryUseCaseTest {

    private fun makeUseCase(): Pair<SaveCategoryUseCase, FakeCategoryRepository> {
        val repo = FakeCategoryRepository()
        return SaveCategoryUseCase(repo) to repo
    }

    private fun category(
        id: String = "cat-1",
        name: String = "Beverages",
        parentId: String? = null,
        displayOrder: Int = 0,
    ) = Category(id = id, name = name, parentId = parentId, displayOrder = displayOrder)

    @Test
    fun `A - blank name returns REQUIRED error`() = runTest {
        val (useCase, _) = makeUseCase()
        val result = useCase(category(name = ""), isUpdate = false)
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("REQUIRED", ex.rule)
        assertEquals("name", ex.field)
    }

    @Test
    fun `B - whitespace-only name returns REQUIRED error`() = runTest {
        val (useCase, _) = makeUseCase()
        val result = useCase(category(name = "   "), isUpdate = false)
        assertIs<Result.Error>(result)
        assertEquals("REQUIRED", (result.exception as ValidationException).rule)
    }

    @Test
    fun `C - category with itself as parent returns SELF_REFERENCE error`() = runTest {
        val (useCase, _) = makeUseCase()
        val result = useCase(category(id = "cat-1", parentId = "cat-1"), isUpdate = false)
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("SELF_REFERENCE", ex.rule)
        assertEquals("parentId", ex.field)
    }

    @Test
    fun `D - null parentId root category is valid`() = runTest {
        val (useCase, repo) = makeUseCase()
        val result = useCase(category(parentId = null), isUpdate = false)
        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.categories.size)
    }

    @Test
    fun `E - different parentId is valid`() = runTest {
        val (useCase, repo) = makeUseCase()
        val result = useCase(category(id = "cat-2", parentId = "cat-1"), isUpdate = false)
        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.categories.size)
    }

    @Test
    fun `F - negative displayOrder returns MIN_VALUE error`() = runTest {
        val (useCase, _) = makeUseCase()
        val result = useCase(category(displayOrder = -1), isUpdate = false)
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("MIN_VALUE", ex.rule)
        assertEquals("displayOrder", ex.field)
    }

    @Test
    fun `G - zero displayOrder is valid`() = runTest {
        val (useCase, repo) = makeUseCase()
        val result = useCase(category(displayOrder = 0), isUpdate = false)
        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.categories.size)
    }

    @Test
    fun `H - isUpdate false calls insert`() = runTest {
        val (useCase, repo) = makeUseCase()
        useCase(category(id = "cat-1"), isUpdate = false)
        assertEquals(1, repo.categories.size)
        assertEquals("cat-1", repo.categories.first().id)
    }

    @Test
    fun `I - isUpdate true calls update`() = runTest {
        val (useCase, repo) = makeUseCase()
        // Pre-insert so update has something to work with
        repo.categories.add(category(id = "cat-1", name = "Old Name"))
        val updated = category(id = "cat-1", name = "New Name")
        val result = useCase(updated, isUpdate = true)
        assertIs<Result.Success<Unit>>(result)
        assertEquals("New Name", repo.categories.first { it.id == "cat-1" }.name)
    }
}
