package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.ProductVariant
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductVariantRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * ZyntaPOS — CreateProductUseCaseTest Unit Tests (commonTest)
 *
 * Validates product creation business rules in [CreateProductUseCase.invoke].
 *
 * Coverage:
 *  A. valid product with unique barcode persisted successfully
 *  B. blank product name returns REQUIRED validation error
 *  C. negative price returns MIN_VALUE error
 *  D. negative costPrice returns MIN_VALUE error
 *  E. duplicate barcode returns BARCODE_DUPLICATE error
 *  F. negative stockQty returns NEGATIVE_STOCK error from StockValidator
 *  G. product without barcode (null) skips barcode uniqueness check
 *  H. variants are persisted via variantRepository when non-empty
 *  I. repository insert error propagates
 */
class CreateProductUseCaseTest {

    private class FakeProductRepository(
        private val insertResult: Result<Unit> = Result.Success(Unit),
        private val barcodeExists: Boolean = false,
    ) : ProductRepository {
        val insertedProducts = mutableListOf<Product>()

        override fun getAll(): Flow<List<Product>> = flowOf(emptyList())
        override suspend fun getById(id: String): Result<Product> =
            Result.Error(DatabaseException("not found"))
        override fun search(query: String, categoryId: String?): Flow<List<Product>> = flowOf(emptyList())
        override suspend fun getByBarcode(barcode: String): Result<Product> =
            if (barcodeExists) Result.Success(Product(
                id = "existing", name = "Existing", categoryId = "cat-1", unitId = "unit-1",
                price = 1.0, barcode = barcode,
                createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(1_000_000L),
                updatedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(1_000_000L),
            ))
            else Result.Error(DatabaseException("not found"))
        override suspend fun insert(product: Product): Result<Unit> {
            insertedProducts.add(product)
            return insertResult
        }
        override suspend fun update(product: Product): Result<Unit> = Result.Success(Unit)
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
        override suspend fun getCount(): Int = 0
        override suspend fun getPage(
            pageRequest: com.zyntasolutions.zyntapos.core.pagination.PageRequest,
            categoryId: String?,
            searchQuery: String?,
        ): com.zyntasolutions.zyntapos.core.pagination.PaginatedResult<Product> =
            com.zyntasolutions.zyntapos.core.pagination.PaginatedResult(emptyList(), 0L, false)
    }

    private class FakeProductVariantRepository(
        private val replaceResult: Result<Unit> = Result.Success(Unit),
    ) : ProductVariantRepository {
        val replaceCallCount get() = _replaceCallCount
        private var _replaceCallCount = 0

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
            _replaceCallCount++
            return replaceResult
        }
    }

    private fun buildProduct(
        id: String = "prod-1",
        name: String = "Espresso",
        barcode: String? = null,
        price: Double = 3.50,
        costPrice: Double = 1.20,
        stockQty: Double = 100.0,
    ) = Product(
        id = id,
        name = name,
        barcode = barcode,
        categoryId = "cat-1",
        unitId = "unit-1",
        price = price,
        costPrice = costPrice,
        stockQty = stockQty,
        createdAt = Instant.fromEpochMilliseconds(1_000_000L),
        updatedAt = Instant.fromEpochMilliseconds(1_000_000L),
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - valid product with unique barcode is persisted successfully`() = runTest {
        val repo = FakeProductRepository(barcodeExists = false)
        val useCase = CreateProductUseCase(repo)

        val result = useCase(buildProduct(barcode = "1234567890"))

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.insertedProducts.size)
    }

    @Test
    fun `B - blank product name returns REQUIRED validation error`() = runTest {
        val repo = FakeProductRepository()
        val useCase = CreateProductUseCase(repo)

        val result = useCase(buildProduct(name = "   "))

        assertIs<Result.Error>(result)
        val error = result.exception as? ValidationException
        assertEquals("REQUIRED", error?.rule)
        assertEquals("name", error?.field)
        assertEquals(0, repo.insertedProducts.size)
    }

    @Test
    fun `C - negative price returns MIN_VALUE error`() = runTest {
        val repo = FakeProductRepository()
        val useCase = CreateProductUseCase(repo)

        val result = useCase(buildProduct(price = -1.0))

        assertIs<Result.Error>(result)
        val error = result.exception as? ValidationException
        assertEquals("MIN_VALUE", error?.rule)
        assertEquals("price", error?.field)
    }

    @Test
    fun `D - negative costPrice returns MIN_VALUE error`() = runTest {
        val repo = FakeProductRepository()
        val useCase = CreateProductUseCase(repo)

        val result = useCase(buildProduct(costPrice = -0.01))

        assertIs<Result.Error>(result)
        val error = result.exception as? ValidationException
        assertEquals("MIN_VALUE", error?.rule)
        assertEquals("costPrice", error?.field)
    }

    @Test
    fun `E - duplicate barcode returns BARCODE_DUPLICATE error`() = runTest {
        val repo = FakeProductRepository(barcodeExists = true)
        val useCase = CreateProductUseCase(repo)

        val result = useCase(buildProduct(barcode = "DUPE-BARCODE"))

        assertIs<Result.Error>(result)
        val error = result.exception as? ValidationException
        assertEquals("BARCODE_DUPLICATE", error?.rule)
        assertEquals("barcode", error?.field)
        assertEquals(0, repo.insertedProducts.size)
    }

    @Test
    fun `F - negative stockQty returns NEGATIVE_STOCK error from StockValidator`() = runTest {
        val repo = FakeProductRepository()
        val useCase = CreateProductUseCase(repo)

        val result = useCase(buildProduct(stockQty = -1.0))

        assertIs<Result.Error>(result)
        val error = result.exception as? ValidationException
        assertEquals("NEGATIVE_STOCK", error?.rule)
    }

    @Test
    fun `G - product without barcode skips barcode uniqueness check and succeeds`() = runTest {
        val repo = FakeProductRepository(barcodeExists = false)
        val useCase = CreateProductUseCase(repo)

        val result = useCase(buildProduct(barcode = null))

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.insertedProducts.size)
    }

    @Test
    fun `H - variants are persisted via variantRepository when non-empty`() = runTest {
        val productRepo = FakeProductRepository()
        val variantRepo = FakeProductVariantRepository()
        val useCase = CreateProductUseCase(productRepo, variantRepo)

        val variant = ProductVariant(
            id = "var-1",
            productId = "prod-1",
            name = "Large",
            price = 4.50,
        )

        val result = useCase(buildProduct(), variants = listOf(variant))

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, variantRepo.replaceCallCount)
    }

    @Test
    fun `I - repository insert error propagates as Result Error`() = runTest {
        val dbError = DatabaseException("Disk full")
        val repo = FakeProductRepository(insertResult = Result.Error(dbError))
        val useCase = CreateProductUseCase(repo)

        val result = useCase(buildProduct())

        assertIs<Result.Error>(result)
    }
}
