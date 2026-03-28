package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.ProductVariant
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeProductRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildProduct
import com.zyntasolutions.zyntapos.domain.repository.ProductVariantRepository
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.domain.model.Product
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * ZyntaPOS — UpdateProductUseCaseTest Unit Tests (commonTest)
 *
 * Validates product update business rules in [UpdateProductUseCase.invoke].
 *
 * Coverage:
 *  A. product not found returns NOT_FOUND error
 *  B. blank name returns REQUIRED error
 *  C. negative price returns MIN_VALUE error
 *  D. negative costPrice returns MIN_VALUE error
 *  E. changed barcode already in use returns BARCODE_DUPLICATE error
 *  F. same barcode as existing product skips uniqueness check and succeeds
 *  G. new barcode not in use succeeds
 *  H. null variants leaves variants unchanged (no replaceAll call)
 *  I. non-null variants replaces all via variantRepository
 */
class UpdateProductUseCaseTest {

    private class FakeProductVariantRepository(
        private val replaceResult: Result<Unit> = Result.Success(Unit),
    ) : ProductVariantRepository {
        var replaceCallCount = 0

        override fun getByProductId(productId: String): Flow<List<ProductVariant>> = flowOf(emptyList())
        override suspend fun getById(id: String): Result<ProductVariant> =
            Result.Error(DatabaseException("not found"))
        override suspend fun getByBarcode(barcode: String): Result<ProductVariant> =
            Result.Error(DatabaseException("not found"))
        override suspend fun insert(variant: ProductVariant): Result<Unit> = Result.Success(Unit)
        override suspend fun update(variant: ProductVariant): Result<Unit> = Result.Success(Unit)
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
        override suspend fun deleteByProductId(productId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun replaceAll(productId: String, variants: List<ProductVariant>): Result<Unit> {
            replaceCallCount++
            return replaceResult
        }
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - product not found returns NOT_FOUND error`() = runTest {
        val repo = FakeProductRepository() // empty — product does not exist
        val useCase = UpdateProductUseCase(repo)

        val result = useCase(buildProduct(id = "ghost-id"))

        assertIs<Result.Error>(result)
        val error = result.exception as? ValidationException
        assertEquals("NOT_FOUND", error?.rule)
        assertEquals("id", error?.field)
    }

    @Test
    fun `B - blank name returns REQUIRED error before any DB lookup`() = runTest {
        val repo = FakeProductRepository()
        val useCase = UpdateProductUseCase(repo)

        val result = useCase(buildProduct(name = "   "))

        assertIs<Result.Error>(result)
        val error = result.exception as? ValidationException
        assertEquals("REQUIRED", error?.rule)
        assertEquals("name", error?.field)
    }

    @Test
    fun `C - negative price returns MIN_VALUE error`() = runTest {
        val repo = FakeProductRepository()
        val useCase = UpdateProductUseCase(repo)

        val result = useCase(buildProduct(price = -0.01))

        assertIs<Result.Error>(result)
        val error = result.exception as? ValidationException
        assertEquals("MIN_VALUE", error?.rule)
        assertEquals("price", error?.field)
    }

    @Test
    fun `D - negative costPrice returns MIN_VALUE error`() = runTest {
        val repo = FakeProductRepository()
        val useCase = UpdateProductUseCase(repo)

        val result = useCase(buildProduct(costPrice = -1.0))

        assertIs<Result.Error>(result)
        val error = result.exception as? ValidationException
        assertEquals("MIN_VALUE", error?.rule)
        assertEquals("costPrice", error?.field)
    }

    @Test
    fun `E - changed barcode already in use returns BARCODE_DUPLICATE error`() = runTest {
        val repo = FakeProductRepository()
        // Product to update has barcode "OLD-BARCODE"
        repo.addProduct(buildProduct(id = "prod-1", barcode = "OLD-BARCODE"))
        // Another product already has "NEW-BARCODE"
        repo.addProduct(buildProduct(id = "prod-2", barcode = "NEW-BARCODE"))
        val useCase = UpdateProductUseCase(repo)

        val result = useCase(buildProduct(id = "prod-1", barcode = "NEW-BARCODE"))

        assertIs<Result.Error>(result)
        val error = result.exception as? ValidationException
        assertEquals("BARCODE_DUPLICATE", error?.rule)
        assertEquals("barcode", error?.field)
    }

    @Test
    fun `F - unchanged barcode skips uniqueness check and succeeds`() = runTest {
        val repo = FakeProductRepository()
        repo.addProduct(buildProduct(id = "prod-1", barcode = "SAME-BARCODE"))
        val useCase = UpdateProductUseCase(repo)

        // Update with same barcode — should not trigger duplicate check
        val result = useCase(buildProduct(id = "prod-1", barcode = "SAME-BARCODE", name = "Renamed"))

        assertIs<Result.Success<*>>(result)
    }

    @Test
    fun `G - new barcode not used by any other product succeeds`() = runTest {
        val repo = FakeProductRepository()
        repo.addProduct(buildProduct(id = "prod-1", barcode = "OLD-BARCODE"))
        val useCase = UpdateProductUseCase(repo)

        val result = useCase(buildProduct(id = "prod-1", barcode = "BRAND-NEW-BARCODE"))

        assertIs<Result.Success<*>>(result)
    }

    @Test
    fun `H - null variants skips variant replacement`() = runTest {
        val repo = FakeProductRepository()
        repo.addProduct(buildProduct(id = "prod-1"))
        val variantRepo = FakeProductVariantRepository()
        val useCase = UpdateProductUseCase(repo, variantRepo)

        val result = useCase(buildProduct(id = "prod-1"), variants = null)

        assertIs<Result.Success<*>>(result)
        assertEquals(0, variantRepo.replaceCallCount)
    }

    @Test
    fun `I - non-null variants triggers replaceAll on variantRepository`() = runTest {
        val repo = FakeProductRepository()
        repo.addProduct(buildProduct(id = "prod-1"))
        val variantRepo = FakeProductVariantRepository()
        val useCase = UpdateProductUseCase(repo, variantRepo)

        val variants = listOf(
            ProductVariant(id = "var-1", productId = "prod-1", name = "Large", price = 12.0),
        )
        val result = useCase(buildProduct(id = "prod-1"), variants = variants)

        assertIs<Result.Success<*>>(result)
        assertEquals(1, variantRepo.replaceCallCount)
    }
}
