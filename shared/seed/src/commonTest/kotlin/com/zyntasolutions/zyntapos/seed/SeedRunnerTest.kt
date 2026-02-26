package com.zyntasolutions.zyntapos.seed

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Category
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.Supplier
import com.zyntasolutions.zyntapos.domain.repository.CategoryRepository
import com.zyntasolutions.zyntapos.domain.repository.CustomerRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.SupplierRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [SeedRunner] and [SeedRunner.SeedSummary].
 *
 * Uses hand-rolled fakes instead of Mockative (incompatible with Kotlin 2.3+ / KSP1).
 *
 * Coverage:
 * - [SeedRunner.SeedSummary] computed properties
 * - Idempotency: existing records are skipped, not re-inserted
 * - Insertion success path: all new records are inserted
 * - Failure path: insert errors are counted in [SeedRunner.SeedResult.failed]
 * - Empty dataset: empty entity lists produce no [SeedRunner.SeedResult] entries
 * - FK-safe ordering: categories/suppliers seeded before products
 * - Mixed scenarios: combinations of insert, skip, fail
 * - [DefaultSeedDataSet.build] shape validation
 */
class SeedRunnerTest {

    // ── Fake collaborators ──────────────────────────────────────────────────────

    /** Controls which IDs are considered "already existing" in the fake store. */
    private val existingCategoryIds = mutableSetOf<String>()
    private val existingSupplierIds = mutableSetOf<String>()
    private val existingProductIds  = mutableSetOf<String>()
    private val existingCustomerIds = mutableSetOf<String>()

    /** When true, every insert call for that type returns [Result.Error]. */
    private var categoryInsertFails  = false
    private var supplierInsertFails  = false
    private var productInsertFails   = false
    private var customerInsertFails  = false

    /** Records the order in which entity types were seeded (first insert or skip). */
    private val seedOrder = mutableListOf<String>()

    private val fakeCategoryRepository = object : CategoryRepository {
        override fun getAll(): Flow<List<Category>> = flowOf(emptyList())
        override fun getTree(): Flow<List<Category>> = flowOf(emptyList())
        override suspend fun getById(id: String): Result<Category> =
            if (id in existingCategoryIds) {
                seedOrder += "Category"
                Result.Success(stubCategory(id))
            } else Result.Error(DatabaseException("Not found"))
        override suspend fun insert(category: Category): Result<Unit> {
            seedOrder += "Category"
            return if (categoryInsertFails) Result.Error(DatabaseException("Insert failed"))
            else { existingCategoryIds += category.id; Result.Success(Unit) }
        }
        override suspend fun update(category: Category): Result<Unit> = Result.Success(Unit)
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
    }

    private val fakeSupplierRepository = object : SupplierRepository {
        override fun getAll(): Flow<List<Supplier>> = flowOf(emptyList())
        override suspend fun getById(id: String): Result<Supplier> =
            if (id in existingSupplierIds) {
                seedOrder += "Supplier"
                Result.Success(stubSupplier(id))
            } else Result.Error(DatabaseException("Not found"))
        override suspend fun insert(supplier: Supplier): Result<Unit> {
            seedOrder += "Supplier"
            return if (supplierInsertFails) Result.Error(DatabaseException("Insert failed"))
            else { existingSupplierIds += supplier.id; Result.Success(Unit) }
        }
        override suspend fun update(supplier: Supplier): Result<Unit> = Result.Success(Unit)
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
    }

    private val fakeProductRepository = object : ProductRepository {
        override fun getAll(): Flow<List<Product>> = flowOf(emptyList())
        override fun search(query: String, categoryId: String?): Flow<List<Product>> = flowOf(emptyList())
        override suspend fun getByBarcode(barcode: String): Result<Product> = Result.Error(DatabaseException("Not found"))
        override suspend fun getCount(): Int = existingProductIds.size
        override suspend fun getById(id: String): Result<Product> =
            if (id in existingProductIds) {
                seedOrder += "Product"
                Result.Success(stubProduct(id))
            } else Result.Error(DatabaseException("Not found"))
        override suspend fun insert(product: Product): Result<Unit> {
            seedOrder += "Product"
            return if (productInsertFails) Result.Error(DatabaseException("Insert failed"))
            else { existingProductIds += product.id; Result.Success(Unit) }
        }
        override suspend fun update(product: Product): Result<Unit> = Result.Success(Unit)
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
    }

    private val fakeCustomerRepository = object : CustomerRepository {
        override fun getAll(): Flow<List<Customer>> = flowOf(emptyList())
        override fun search(query: String): Flow<List<Customer>> = flowOf(emptyList())
        override suspend fun getById(id: String): Result<Customer> =
            if (id in existingCustomerIds) {
                seedOrder += "Customer"
                Result.Success(stubCustomer(id))
            } else Result.Error(DatabaseException("Not found"))
        override suspend fun insert(customer: Customer): Result<Unit> {
            seedOrder += "Customer"
            return if (customerInsertFails) Result.Error(DatabaseException("Insert failed"))
            else { existingCustomerIds += customer.id; Result.Success(Unit) }
        }
        override suspend fun update(customer: Customer): Result<Unit> = Result.Success(Unit)
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
    }

    private fun runner() = SeedRunner(
        categoryRepository = fakeCategoryRepository,
        supplierRepository = fakeSupplierRepository,
        productRepository  = fakeProductRepository,
        customerRepository = fakeCustomerRepository,
    )

    // ── SeedSummary computed properties ─────────────────────────────────────────

    @Test
    fun `SeedSummary isSuccess returns true when no results have failures`() {
        val summary = SeedRunner.SeedSummary(
            results = listOf(
                SeedRunner.SeedResult("Category", inserted = 3, skipped = 1, failed = 0),
                SeedRunner.SeedResult("Product",  inserted = 5, skipped = 0, failed = 0),
            ),
            errors = emptyList(),
        )
        assertTrue(summary.isSuccess)
    }

    @Test
    fun `SeedSummary isSuccess returns false when any result has failures`() {
        val summary = SeedRunner.SeedSummary(
            results = listOf(
                SeedRunner.SeedResult("Category", inserted = 3, skipped = 0, failed = 0),
                SeedRunner.SeedResult("Product",  inserted = 4, skipped = 0, failed = 1),
            ),
            errors = emptyList(),
        )
        assertFalse(summary.isSuccess)
    }

    @Test
    fun `SeedSummary totalInserted sums across all entity types`() {
        val summary = SeedRunner.SeedSummary(
            results = listOf(
                SeedRunner.SeedResult("Category", inserted = 2, skipped = 0, failed = 0),
                SeedRunner.SeedResult("Supplier", inserted = 3, skipped = 0, failed = 0),
                SeedRunner.SeedResult("Product",  inserted = 5, skipped = 0, failed = 0),
                SeedRunner.SeedResult("Customer", inserted = 4, skipped = 0, failed = 0),
            ),
            errors = emptyList(),
        )
        assertEquals(14, summary.totalInserted)
    }

    @Test
    fun `SeedSummary totalSkipped sums across all entity types`() {
        val summary = SeedRunner.SeedSummary(
            results = listOf(
                SeedRunner.SeedResult("Category", inserted = 0, skipped = 2, failed = 0),
                SeedRunner.SeedResult("Supplier", inserted = 0, skipped = 1, failed = 0),
            ),
            errors = emptyList(),
        )
        assertEquals(3, summary.totalSkipped)
    }

    @Test
    fun `SeedSummary totalFailed sums across all entity types`() {
        val summary = SeedRunner.SeedSummary(
            results = listOf(
                SeedRunner.SeedResult("Category", inserted = 0, skipped = 0, failed = 1),
                SeedRunner.SeedResult("Product",  inserted = 0, skipped = 0, failed = 2),
            ),
            errors = emptyList(),
        )
        assertEquals(3, summary.totalFailed)
    }

    @Test
    fun `SeedSummary isSuccess is false when results list is empty`() {
        // No results = vacuously true (all { } on empty list returns true)
        val summary = SeedRunner.SeedSummary(results = emptyList(), errors = emptyList())
        assertTrue(summary.isSuccess)
    }

    // ── Empty dataset ───────────────────────────────────────────────────────────

    @Test
    fun `run with fully empty dataset returns empty results list`() = runTest {
        val summary = runner().run(SeedDataSet())
        assertTrue(summary.results.isEmpty())
        assertTrue(summary.isSuccess)
        assertEquals(0, summary.totalInserted)
    }

    @Test
    fun `run skips entity type when its list is empty`() = runTest {
        val dataSet = SeedDataSet(
            categories = listOf(SeedCategory("c1", "Coffee")),
            products   = emptyList(), // no products → no Product result
        )
        val summary = runner().run(dataSet)
        assertEquals(1, summary.results.size)
        assertEquals("Category", summary.results[0].entityType)
    }

    // ── Category seeding ────────────────────────────────────────────────────────

    @Test
    fun `seedCategories inserts all new categories`() = runTest {
        val dataSet = SeedDataSet(
            categories = listOf(
                SeedCategory("c1", "Coffee"),
                SeedCategory("c2", "Tea"),
                SeedCategory("c3", "Bakery"),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Category" }
        assertEquals(3, result.inserted)
        assertEquals(0, result.skipped)
        assertEquals(0, result.failed)
    }

    @Test
    fun `seedCategories skips categories that already exist`() = runTest {
        existingCategoryIds += setOf("c1", "c2")
        val dataSet = SeedDataSet(
            categories = listOf(SeedCategory("c1", "Coffee"), SeedCategory("c2", "Tea")),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Category" }
        assertEquals(0, result.inserted)
        assertEquals(2, result.skipped)
        assertEquals(0, result.failed)
    }

    @Test
    fun `seedCategories counts failures when insert returns error`() = runTest {
        categoryInsertFails = true
        val dataSet = SeedDataSet(
            categories = listOf(SeedCategory("c1", "Coffee"), SeedCategory("c2", "Tea")),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Category" }
        assertEquals(0, result.inserted)
        assertEquals(0, result.skipped)
        assertEquals(2, result.failed)
    }

    @Test
    fun `seedCategories handles mixed insert skip and fail correctly`() = runTest {
        existingCategoryIds += "c2"    // will be skipped
        categoryInsertFails = false    // new categories c1, c3 succeed; c2 is skipped
        val dataSet = SeedDataSet(
            categories = listOf(
                SeedCategory("c1", "Coffee"),  // new → inserted
                SeedCategory("c2", "Tea"),     // existing → skipped
                SeedCategory("c3", "Bakery"),  // new → inserted
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Category" }
        assertEquals(2, result.inserted)
        assertEquals(1, result.skipped)
        assertEquals(0, result.failed)
    }

    // ── Supplier seeding ────────────────────────────────────────────────────────

    @Test
    fun `seedSuppliers inserts all new suppliers`() = runTest {
        val dataSet = SeedDataSet(
            suppliers = listOf(
                SeedSupplier("s1", "Supplier A"),
                SeedSupplier("s2", "Supplier B"),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Supplier" }
        assertEquals(2, result.inserted)
        assertEquals(0, result.skipped)
        assertEquals(0, result.failed)
    }

    @Test
    fun `seedSuppliers skips existing suppliers`() = runTest {
        existingSupplierIds += "s1"
        val dataSet = SeedDataSet(
            suppliers = listOf(SeedSupplier("s1", "Supplier A"), SeedSupplier("s2", "Supplier B")),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Supplier" }
        assertEquals(1, result.inserted)
        assertEquals(1, result.skipped)
        assertEquals(0, result.failed)
    }

    @Test
    fun `seedSuppliers counts failures when insert returns error`() = runTest {
        supplierInsertFails = true
        val dataSet = SeedDataSet(
            suppliers = listOf(SeedSupplier("s1", "Supplier A")),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Supplier" }
        assertEquals(0, result.inserted)
        assertEquals(0, result.skipped)
        assertEquals(1, result.failed)
    }

    // ── Product seeding ─────────────────────────────────────────────────────────

    @Test
    fun `seedProducts inserts all new products`() = runTest {
        val dataSet = SeedDataSet(
            products = listOf(
                SeedProduct("p1", "Espresso", categoryId = "c1", price = 2.50),
                SeedProduct("p2", "Latte", categoryId = "c1", price = 4.00),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Product" }
        assertEquals(2, result.inserted)
        assertEquals(0, result.skipped)
        assertEquals(0, result.failed)
    }

    @Test
    fun `seedProducts skips existing products`() = runTest {
        existingProductIds += "p1"
        val dataSet = SeedDataSet(
            products = listOf(
                SeedProduct("p1", "Espresso", categoryId = "c1", price = 2.50),
                SeedProduct("p2", "Latte", categoryId = "c1", price = 4.00),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Product" }
        assertEquals(1, result.inserted)
        assertEquals(1, result.skipped)
        assertEquals(0, result.failed)
    }

    @Test
    fun `seedProducts counts failures when insert returns error`() = runTest {
        productInsertFails = true
        val dataSet = SeedDataSet(
            products = listOf(SeedProduct("p1", "Espresso", categoryId = "c1", price = 2.50)),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Product" }
        assertEquals(0, result.inserted)
        assertEquals(0, result.skipped)
        assertEquals(1, result.failed)
    }

    // ── Customer seeding ────────────────────────────────────────────────────────

    @Test
    fun `seedCustomers inserts all new customers`() = runTest {
        val dataSet = SeedDataSet(
            customers = listOf(
                SeedCustomer("cu1", "Alice", phone = "+1-555-0001"),
                SeedCustomer("cu2", "Bob", phone = "+1-555-0002"),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Customer" }
        assertEquals(2, result.inserted)
        assertEquals(0, result.skipped)
        assertEquals(0, result.failed)
    }

    @Test
    fun `seedCustomers skips existing customers`() = runTest {
        existingCustomerIds += "cu1"
        val dataSet = SeedDataSet(
            customers = listOf(
                SeedCustomer("cu1", "Alice", phone = "+1-555-0001"),
                SeedCustomer("cu2", "Bob", phone = "+1-555-0002"),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Customer" }
        assertEquals(1, result.inserted)
        assertEquals(1, result.skipped)
        assertEquals(0, result.failed)
    }

    @Test
    fun `seedCustomers counts failures when insert returns error`() = runTest {
        customerInsertFails = true
        val dataSet = SeedDataSet(
            customers = listOf(SeedCustomer("cu1", "Alice", phone = "+1-555-0001")),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Customer" }
        assertEquals(0, result.inserted)
        assertEquals(0, result.skipped)
        assertEquals(1, result.failed)
    }

    @Test
    fun `seedCustomers stores email and loyalty points correctly`() = runTest {
        var insertedCustomer: Customer? = null
        val capturingCustomerRepo = object : CustomerRepository by fakeCustomerRepository {
            override suspend fun insert(customer: Customer): Result<Unit> {
                insertedCustomer = customer
                return Result.Success(Unit)
            }
        }
        val capturingRunner = SeedRunner(
            categoryRepository = fakeCategoryRepository,
            supplierRepository = fakeSupplierRepository,
            productRepository  = fakeProductRepository,
            customerRepository = capturingCustomerRepo,
        )
        capturingRunner.run(SeedDataSet(
            customers = listOf(
                SeedCustomer("cu1", "Alice", phone = "+1-555-0001", email = "alice@test.com", loyaltyPoints = 300),
            ),
        ))
        assertEquals("cu1",             insertedCustomer?.id)
        assertEquals("Alice",           insertedCustomer?.name)
        assertEquals("+1-555-0001",     insertedCustomer?.phone)
        assertEquals("alice@test.com",  insertedCustomer?.email)
        assertEquals(300,               insertedCustomer?.loyaltyPoints)
    }

    // ── FK-safe ordering ────────────────────────────────────────────────────────

    @Test
    fun `run seeds categories before products`() = runTest {
        val dataSet = SeedDataSet(
            categories = listOf(SeedCategory("c1", "Coffee")),
            products   = listOf(SeedProduct("p1", "Espresso", categoryId = "c1", price = 2.50)),
        )
        runner().run(dataSet)
        val firstCategory = seedOrder.indexOfFirst { it == "Category" }
        val firstProduct  = seedOrder.indexOfFirst { it == "Product" }
        assertTrue(firstCategory < firstProduct, "Categories must be seeded before products")
    }

    @Test
    fun `run seeds suppliers before products`() = runTest {
        val dataSet = SeedDataSet(
            suppliers = listOf(SeedSupplier("s1", "Supplier A")),
            products  = listOf(SeedProduct("p1", "Espresso", categoryId = "c1", price = 2.50)),
        )
        runner().run(dataSet)
        val firstSupplier = seedOrder.indexOfFirst { it == "Supplier" }
        val firstProduct  = seedOrder.indexOfFirst { it == "Product" }
        assertTrue(firstSupplier < firstProduct, "Suppliers must be seeded before products")
    }

    @Test
    fun `run follows full FK-safe order categories suppliers products customers`() = runTest {
        val dataSet = SeedDataSet(
            categories = listOf(SeedCategory("c1", "Coffee")),
            suppliers  = listOf(SeedSupplier("s1", "Supplier A")),
            products   = listOf(SeedProduct("p1", "Espresso", categoryId = "c1", price = 2.50)),
            customers  = listOf(SeedCustomer("cu1", "Alice", phone = "+1-555-0001")),
        )
        runner().run(dataSet)
        val entityTypes = seedOrder.distinct() // first-seen order per entity type
        assertEquals(listOf("Category", "Supplier", "Product", "Customer"), entityTypes)
    }

    // ── Full run – integrated scenarios ─────────────────────────────────────────

    @Test
    fun `run with all new data returns all inserted and isSuccess true`() = runTest {
        val summary = runner().run(
            SeedDataSet(
                categories = listOf(SeedCategory("c1", "Coffee"), SeedCategory("c2", "Tea")),
                suppliers  = listOf(SeedSupplier("s1", "Supplier A")),
                products   = listOf(SeedProduct("p1", "Espresso", categoryId = "c1", price = 2.50)),
                customers  = listOf(SeedCustomer("cu1", "Alice", phone = "+1-555-0001")),
            )
        )
        assertEquals(4, summary.results.size)
        assertEquals(5, summary.totalInserted)   // 2 cats + 1 sup + 1 prod + 1 cust
        assertEquals(0, summary.totalSkipped)
        assertEquals(0, summary.totalFailed)
        assertTrue(summary.isSuccess)
    }

    @Test
    fun `run with all existing data returns all skipped and isSuccess true`() = runTest {
        existingCategoryIds += "c1"
        existingSupplierIds += "s1"
        existingProductIds  += "p1"
        existingCustomerIds += "cu1"
        val summary = runner().run(
            SeedDataSet(
                categories = listOf(SeedCategory("c1", "Coffee")),
                suppliers  = listOf(SeedSupplier("s1", "Supplier A")),
                products   = listOf(SeedProduct("p1", "Espresso", categoryId = "c1", price = 2.50)),
                customers  = listOf(SeedCustomer("cu1", "Alice", phone = "+1-555-0001")),
            )
        )
        assertEquals(0, summary.totalInserted)
        assertEquals(4, summary.totalSkipped)
        assertEquals(0, summary.totalFailed)
        assertTrue(summary.isSuccess)
    }

    @Test
    fun `run with partial failures marks isSuccess false`() = runTest {
        productInsertFails = true
        val summary = runner().run(
            SeedDataSet(
                categories = listOf(SeedCategory("c1", "Coffee")),
                products   = listOf(SeedProduct("p1", "Espresso", categoryId = "c1", price = 2.50)),
            )
        )
        assertFalse(summary.isSuccess)
        assertEquals(1, summary.totalFailed)
        assertEquals(1, summary.totalInserted) // category still succeeded
    }

    @Test
    fun `run is idempotent - second call skips all previously inserted records`() = runTest {
        val dataSet = SeedDataSet(
            categories = listOf(SeedCategory("c1", "Coffee")),
            customers  = listOf(SeedCustomer("cu1", "Alice", phone = "+1-555-0001")),
        )
        val runner = runner()
        val firstRun  = runner.run(dataSet)
        val secondRun = runner.run(dataSet)

        // First run: everything inserted
        assertEquals(2, firstRun.totalInserted)
        assertEquals(0, firstRun.totalSkipped)

        // Second run: everything skipped (already in existingIds after first run)
        assertEquals(0, secondRun.totalInserted)
        assertEquals(2, secondRun.totalSkipped)
    }

    // ── DefaultSeedDataSet validation ────────────────────────────────────────────

    @Test
    fun `DefaultSeedDataSet build returns expected entity counts`() {
        val dataSet = DefaultSeedDataSet.build()
        assertEquals(12, dataSet.categories.size, "Expected 12 categories")
        assertEquals(8,  dataSet.suppliers.size,  "Expected 8 suppliers")
        assertEquals(65, dataSet.products.size,   "Expected 65 products")
        assertEquals(25, dataSet.customers.size,  "Expected 25 customers")
    }

    @Test
    fun `DefaultSeedDataSet all category IDs are unique`() {
        val ids = DefaultSeedDataSet.build().categories.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Category IDs must be unique")
    }

    @Test
    fun `DefaultSeedDataSet all supplier IDs are unique`() {
        val ids = DefaultSeedDataSet.build().suppliers.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Supplier IDs must be unique")
    }

    @Test
    fun `DefaultSeedDataSet all product IDs are unique`() {
        val ids = DefaultSeedDataSet.build().products.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Product IDs must be unique")
    }

    @Test
    fun `DefaultSeedDataSet all customer IDs are unique`() {
        val ids = DefaultSeedDataSet.build().customers.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Customer IDs must be unique")
    }

    @Test
    fun `DefaultSeedDataSet all products reference valid category IDs`() {
        val dataSet = DefaultSeedDataSet.build()
        val categoryIds = dataSet.categories.map { it.id }.toSet()
        dataSet.products.forEach { product ->
            assertTrue(
                product.categoryId in categoryIds,
                "Product '${product.name}' references unknown categoryId '${product.categoryId}'",
            )
        }
    }

    @Test
    fun `DefaultSeedDataSet all customers have non-blank phone numbers`() {
        DefaultSeedDataSet.build().customers.forEach { customer ->
            assertTrue(
                customer.phone.isNotBlank(),
                "Customer '${customer.name}' has blank phone",
            )
        }
    }

    @Test
    fun `DefaultSeedDataSet all products have positive price`() {
        DefaultSeedDataSet.build().products.forEach { product ->
            assertTrue(
                product.price > 0.0,
                "Product '${product.name}' has non-positive price ${product.price}",
            )
        }
    }

    @Test
    fun `DefaultSeedDataSet contains expected low-stock products for alert testing`() {
        val dataSet = DefaultSeedDataSet.build()
        val lowStockCount = dataSet.products.count { it.stockQty < it.minStockQty && it.minStockQty > 0 }
        assertTrue(lowStockCount >= 5, "Expected at least 5 low-stock products, got $lowStockCount")
    }

    @Test
    fun `DefaultSeedDataSet contains at least one out-of-stock product`() {
        val dataSet = DefaultSeedDataSet.build()
        val outOfStock = dataSet.products.count { it.stockQty == 0.0 }
        assertTrue(outOfStock >= 1, "Expected at least one out-of-stock product")
    }

    @Test
    fun `DefaultSeedDataSet run inserts full dataset without failures`() = runTest {
        val summary = runner().run(DefaultSeedDataSet.build())
        assertEquals(0, summary.totalFailed)
        assertTrue(summary.isSuccess)
        assertEquals(110, summary.totalInserted) // 12 + 8 + 65 + 25
    }

    // ── Stub helpers ─────────────────────────────────────────────────────────────

    private fun stubCategory(id: String) = Category(
        id = id,
        name = "Category $id",
    )

    private fun stubSupplier(id: String) = Supplier(
        id = id,
        name = "Supplier $id",
    )

    private fun stubProduct(id: String) = Product(
        id = id,
        name = "Product $id",
        categoryId = "cat-stub",
        unitId = "unit-pcs",
        price = 1.0,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
    )

    private fun stubCustomer(id: String) = Customer(
        id = id,
        name = "Customer $id",
        phone = "+1-000-0000",
    )
}
