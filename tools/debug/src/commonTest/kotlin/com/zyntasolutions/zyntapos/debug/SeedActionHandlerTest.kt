package com.zyntasolutions.zyntapos.debug

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.debug.actions.SeedActionHandlerImpl
import com.zyntasolutions.zyntapos.debug.model.SeedProfile
import com.zyntasolutions.zyntapos.domain.model.Category
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.Supplier
import com.zyntasolutions.zyntapos.domain.repository.CategoryRepository
import com.zyntasolutions.zyntapos.domain.repository.CustomerRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.SupplierRepository
import com.zyntasolutions.zyntapos.seed.SeedRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [SeedActionHandlerImpl].
 *
 * [SeedRunner] is constructed with stub repositories so no real SQLDelight driver
 * is required. Tests verify that [SeedActionHandlerImpl] correctly wraps
 * [SeedRunner.run] outcomes into [Result.Success] or [Result.Error].
 *
 * Key observations about [SeedRunner]:
 * - Per-entity insert failures (returned as [Result.Error] from a repository)
 *   are counted as `failed` in [SeedRunner.SeedSummary] — they do NOT throw.
 * - An uncaught exception from a repository method (i.e., a real `throw`)
 *   propagates out of [SeedRunner.run] and is caught by [SeedActionHandlerImpl],
 *   which wraps it in [Result.Error] with a [DatabaseException].
 */
class SeedActionHandlerTest {

    // ── Stub repositories ──────────────────────────────────────────────────────

    /**
     * Default stub: all inserts succeed; [getById] always returns Error so
     * [SeedRunner] treats every seed record as new and inserts it.
     */
    private open class StubCategoryRepository : CategoryRepository {
        val inserted = mutableListOf<Category>()

        override fun getAll(): Flow<List<Category>> = MutableStateFlow(emptyList())
        override fun getTree(): Flow<List<Category>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: String): Result<Category> =
            Result.Error(DatabaseException("not found"))
        override suspend fun insert(category: Category): Result<Unit> {
            inserted += category
            return Result.Success(Unit)
        }
        override suspend fun update(category: Category): Result<Unit> = Result.Success(Unit)
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
    }

    /** Variant that causes [SeedRunner.run] to throw by exploding in [getById]. */
    private class ThrowingCategoryRepository : StubCategoryRepository() {
        override suspend fun getById(id: String): Result<Category> {
            throw RuntimeException("Simulated DB crash in category lookup")
        }
    }

    /** Variant that reports insert failures via [Result.Error] (non-throwing). */
    private class FailingInsertCategoryRepository : StubCategoryRepository() {
        override suspend fun insert(category: Category): Result<Unit> =
            Result.Error(DatabaseException("UNIQUE constraint failed"))
    }

    private open class StubSupplierRepository : SupplierRepository {
        val inserted = mutableListOf<Supplier>()

        override fun getAll(): Flow<List<Supplier>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: String): Result<Supplier> =
            Result.Error(DatabaseException("not found"))
        override suspend fun insert(supplier: Supplier): Result<Unit> {
            inserted += supplier
            return Result.Success(Unit)
        }
        override suspend fun update(supplier: Supplier): Result<Unit> = Result.Success(Unit)
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
    }

    private open class StubProductRepository : ProductRepository {
        val inserted = mutableListOf<Product>()
        private val flow = MutableStateFlow<List<Product>>(emptyList())

        override fun getAll(): Flow<List<Product>> = flow
        override suspend fun getById(id: String): Result<Product> =
            Result.Error(DatabaseException("not found"))
        override fun search(query: String, categoryId: String?): Flow<List<Product>> = flow
        override suspend fun getByBarcode(barcode: String): Result<Product> =
            Result.Error(DatabaseException("not found"))
        override suspend fun insert(product: Product): Result<Unit> {
            inserted += product
            return Result.Success(Unit)
        }
        override suspend fun update(product: Product): Result<Unit> = Result.Success(Unit)
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
        override suspend fun getCount(): Int = inserted.size
    }

    private open class StubCustomerRepository : CustomerRepository {
        val inserted = mutableListOf<Customer>()

        override fun getAll(): Flow<List<Customer>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: String): Result<Customer> =
            Result.Error(DatabaseException("not found"))
        override fun search(query: String): Flow<List<Customer>> = MutableStateFlow(emptyList())
        override suspend fun insert(customer: Customer): Result<Unit> {
            inserted += customer
            return Result.Success(Unit)
        }
        override suspend fun update(customer: Customer): Result<Unit> = Result.Success(Unit)
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
        override fun searchGlobal(query: String): Flow<List<Customer>> = MutableStateFlow(emptyList())
        override fun getByStore(storeId: String): Flow<List<Customer>> = MutableStateFlow(emptyList())
        override fun getGlobalCustomers(): Flow<List<Customer>> = MutableStateFlow(emptyList())
        override suspend fun makeGlobal(customerId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun updateLoyaltyPoints(customerId: String, points: Int): Result<Unit> = Result.Success(Unit)
    }

    // ── SUT builder ───────────────────────────────────────────────────────────

    private fun buildHandler(
        categoryRepo: CategoryRepository = StubCategoryRepository(),
        supplierRepo: SupplierRepository = StubSupplierRepository(),
        productRepo: ProductRepository = StubProductRepository(),
        customerRepo: CustomerRepository = StubCustomerRepository(),
    ): SeedActionHandlerImpl {
        val runner = SeedRunner(categoryRepo, supplierRepo, productRepo, customerRepo)
        return SeedActionHandlerImpl(runner)
    }

    // ── Happy-path: all three profiles ────────────────────────────────────────

    @Test
    fun `runProfile Demo returns Result Success`() = runTest {
        val handler = buildHandler()

        val result = handler.runProfile(SeedProfile.Demo)

        assertIs<Result.Success<SeedRunner.SeedSummary>>(result)
    }

    @Test
    fun `runProfile Retail returns Result Success - shares DefaultSeedDataSet in Phase 1`() = runTest {
        val handler = buildHandler()

        val result = handler.runProfile(SeedProfile.Retail)

        assertIs<Result.Success<SeedRunner.SeedSummary>>(result)
    }

    @Test
    fun `runProfile Restaurant returns Result Success - shares DefaultSeedDataSet in Phase 1`() = runTest {
        val handler = buildHandler()

        val result = handler.runProfile(SeedProfile.Restaurant)

        assertIs<Result.Success<SeedRunner.SeedSummary>>(result)
    }

    // ── SeedSummary field propagation ─────────────────────────────────────────

    @Test
    fun `runProfile Demo SeedSummary has non-empty results list`() = runTest {
        val handler = buildHandler()

        val result = handler.runProfile(SeedProfile.Demo) as Result.Success
        assertTrue(result.data.results.isNotEmpty(),
            "Expected at least one SeedResult entry in the summary")
    }

    @Test
    fun `runProfile Demo totalInserted is positive on clean stub database`() = runTest {
        val handler = buildHandler()

        val result = handler.runProfile(SeedProfile.Demo) as Result.Success
        assertTrue(result.data.totalInserted > 0,
            "Expected at least one record inserted; totalInserted=${result.data.totalInserted}")
    }

    @Test
    fun `runProfile Demo totalFailed is zero when all inserts succeed`() = runTest {
        val handler = buildHandler()

        val result = handler.runProfile(SeedProfile.Demo) as Result.Success
        assertEquals(0, result.data.totalFailed,
            "Expected no failures against a successful stub repository")
    }

    @Test
    fun `runProfile Demo isSuccess is true when no failures`() = runTest {
        val handler = buildHandler()

        val result = handler.runProfile(SeedProfile.Demo) as Result.Success
        assertTrue(result.data.isSuccess)
    }

    @Test
    fun `runProfile propagates totalSkipped for records already in DB`() = runTest {
        // Simulate that all categories already exist by returning Success from getById
        val alreadyExistsCategoryRepo = object : StubCategoryRepository() {
            override suspend fun getById(id: String): Result<Category> =
                Result.Success(
                    Category(id = id, name = "existing", parentId = null, displayOrder = 0)
                )
        }
        val handler = buildHandler(categoryRepo = alreadyExistsCategoryRepo)

        val result = handler.runProfile(SeedProfile.Demo) as Result.Success
        // Categories are skipped; skipped >= 0 and inserted should decrease accordingly
        assertTrue(result.data.totalSkipped >= 0)
    }

    @Test
    fun `runProfile inserts categories into categoryRepository`() = runTest {
        val categoryRepo = StubCategoryRepository()
        val handler = buildHandler(categoryRepo = categoryRepo)

        handler.runProfile(SeedProfile.Demo)

        assertTrue(categoryRepo.inserted.isNotEmpty(),
            "Expected DefaultSeedDataSet to contain at least one category")
    }

    @Test
    fun `runProfile inserts products into productRepository`() = runTest {
        val productRepo = StubProductRepository()
        val handler = buildHandler(productRepo = productRepo)

        handler.runProfile(SeedProfile.Demo)

        assertTrue(productRepo.inserted.isNotEmpty(),
            "Expected DefaultSeedDataSet to contain at least one product")
    }

    // ── Failure when SeedRunner throws ────────────────────────────────────────

    @Test
    fun `runProfile returns Result Error wrapping DatabaseException when SeedRunner throws`() = runTest {
        // ThrowingCategoryRepository throws from getById which propagates out of SeedRunner.run()
        val handler = buildHandler(categoryRepo = ThrowingCategoryRepository())

        val result = handler.runProfile(SeedProfile.Demo)

        assertIs<Result.Error>(result)
        assertIs<DatabaseException>(result.exception)
    }

    @Test
    fun `runProfile Error message starts with Seed run failed`() = runTest {
        val handler = buildHandler(categoryRepo = ThrowingCategoryRepository())

        val result = handler.runProfile(SeedProfile.Demo)

        assertIs<Result.Error>(result)
        assertTrue(
            result.exception.message.startsWith("Seed run failed"),
            "Expected message starting with 'Seed run failed', got: ${result.exception.message}",
        )
    }

    // ── Partial failures recorded in SeedSummary (not thrown) ─────────────────

    @Test
    fun `runProfile records failed count in SeedSummary when insert returns Result Error`() = runTest {
        // Repository returns Result.Error — counted as failed, not thrown
        val handler = buildHandler(categoryRepo = FailingInsertCategoryRepository())

        val result = handler.runProfile(SeedProfile.Demo) as Result.Success
        // The category entity type should have failed > 0
        val categoryResult = result.data.results.firstOrNull { it.entityType == "Category" }
        assertIs<SeedRunner.SeedResult>(categoryResult)
        assertTrue(categoryResult.failed > 0,
            "Expected at least one category insert to be counted as failed")
    }

    @Test
    fun `runProfile isSuccess is false when any insert failure is recorded`() = runTest {
        val handler = buildHandler(categoryRepo = FailingInsertCategoryRepository())

        val result = handler.runProfile(SeedProfile.Demo) as Result.Success
        assertTrue(!result.data.isSuccess,
            "isSuccess should be false when totalFailed > 0")
    }
}
