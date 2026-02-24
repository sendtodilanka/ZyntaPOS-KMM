package com.zyntasolutions.zyntapos.seed

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Category
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.Supplier
import com.zyntasolutions.zyntapos.domain.repository.CategoryRepository
import com.zyntasolutions.zyntapos.domain.repository.CustomerRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.SupplierRepository
import kotlinx.datetime.Clock

/**
 * Debug-only seed data runner.
 *
 * Populates the local database with sample data for UI/UX testing.
 * Reads a [SeedDataSet] (from JSON or programmatic construction) and delegates
 * all persistence to existing domain repository interfaces — no new domain logic.
 *
 * ### Production footprint
 * This class is referenced only from debug-specific DI modules
 * (e.g. `SeedModule`). It is excluded from release builds by
 * convention — only include `:shared:seed` in `debugImplementation`
 * configurations. It never modifies production data on its own.
 *
 * ### Idempotency
 * Each `run*` method checks if data already exists before inserting,
 * making the runner safe to call multiple times (on each debug launch).
 *
 * @param categoryRepository  Persists [Category] records.
 * @param supplierRepository  Persists [Supplier] records.
 * @param productRepository   Persists [Product] records.
 * @param customerRepository  Persists [Customer] records.
 */
class SeedRunner(
    private val categoryRepository: CategoryRepository,
    private val supplierRepository: SupplierRepository,
    private val productRepository: ProductRepository,
    private val customerRepository: CustomerRepository,
) {
    /**
     * Outcome for a single entity seed operation.
     *
     * @property entityType Human-readable entity name (e.g. "Category").
     * @property inserted   Number of records successfully inserted.
     * @property skipped    Number of records skipped (already existed).
     * @property failed     Number of records that failed to insert.
     */
    data class SeedResult(
        val entityType: String,
        val inserted: Int,
        val skipped: Int,
        val failed: Int,
    )

    /**
     * Summary of a full [run] invocation.
     *
     * @property results Individual results per entity type.
     * @property errors  Any non-critical errors encountered during seeding.
     */
    data class SeedSummary(
        val results: List<SeedResult>,
        val errors: List<String>,
    ) {
        /** True when every entity was inserted or skipped without failure. */
        val isSuccess: Boolean get() = results.all { it.failed == 0 }
        val totalInserted: Int get() = results.sumOf { it.inserted }
        val totalSkipped: Int get() = results.sumOf { it.skipped }
        val totalFailed: Int get() = results.sumOf { it.failed }
    }

    /**
     * Seeds all entities from the supplied [dataSet].
     *
     * The seeding order respects FK dependencies:
     * Categories → Suppliers → Products → Customers
     *
     * @param dataSet  The seed dataset to insert.
     * @return A [SeedSummary] with per-entity results.
     */
    suspend fun run(dataSet: SeedDataSet): SeedSummary {
        val results = mutableListOf<SeedResult>()
        val errors = mutableListOf<String>()

        // Order matters: categories and suppliers before products.
        if (dataSet.categories.isNotEmpty()) {
            results.add(seedCategories(dataSet.categories))
        }
        if (dataSet.suppliers.isNotEmpty()) {
            results.add(seedSuppliers(dataSet.suppliers))
        }
        if (dataSet.products.isNotEmpty()) {
            results.add(seedProducts(dataSet.products))
        }
        if (dataSet.customers.isNotEmpty()) {
            results.add(seedCustomers(dataSet.customers))
        }

        return SeedSummary(results = results, errors = errors)
    }

    // ── Per-entity seeders ─────────────────────────────────────────────────────

    private suspend fun seedCategories(seedItems: List<SeedCategory>): SeedResult {
        var inserted = 0; var skipped = 0; var failed = 0
        for (seed in seedItems) {
            val existing = categoryRepository.getById(seed.id)
            if (existing is Result.Success) { skipped++; continue }

            val category = Category(
                id = seed.id,
                name = seed.name,
                parentId = seed.parentId,
                displayOrder = seed.displayOrder,
            )
            when (categoryRepository.insert(category)) {
                is Result.Success -> inserted++
                is Result.Error -> failed++
                is Result.Loading -> Unit
            }
        }
        return SeedResult("Category", inserted, skipped, failed)
    }

    private suspend fun seedSuppliers(seedItems: List<SeedSupplier>): SeedResult {
        var inserted = 0; var skipped = 0; var failed = 0
        for (seed in seedItems) {
            val existing = supplierRepository.getById(seed.id)
            if (existing is Result.Success) { skipped++; continue }

            val supplier = Supplier(
                id = seed.id,
                name = seed.name,
                contactPerson = seed.contactPerson,
                email = seed.email,
                phone = seed.phone,
                address = seed.address,
                notes = seed.notes,
            )
            when (supplierRepository.insert(supplier)) {
                is Result.Success -> inserted++
                is Result.Error -> failed++
                is Result.Loading -> Unit
            }
        }
        return SeedResult("Supplier", inserted, skipped, failed)
    }

    private suspend fun seedProducts(seedItems: List<SeedProduct>): SeedResult {
        var inserted = 0; var skipped = 0; var failed = 0
        for (seed in seedItems) {
            val existing = productRepository.getById(seed.id)
            if (existing is Result.Success) { skipped++; continue }

            val product = Product(
                id = seed.id,
                name = seed.name,
                sku = seed.sku,
                barcode = seed.barcode,
                categoryId = seed.categoryId,
                unitId = seed.unitId,
                price = seed.price,
                costPrice = seed.costPrice,
                taxGroupId = seed.taxGroupId,
                stockQty = seed.stockQty,
                minStockQty = seed.minStockQty,
                description = seed.description,
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now(),
            )
            when (productRepository.insert(product)) {
                is Result.Success -> inserted++
                is Result.Error -> failed++
                is Result.Loading -> Unit
            }
        }
        return SeedResult("Product", inserted, skipped, failed)
    }

    private suspend fun seedCustomers(seedItems: List<SeedCustomer>): SeedResult {
        var inserted = 0; var skipped = 0; var failed = 0
        for (seed in seedItems) {
            val existing = customerRepository.getById(seed.id)
            if (existing is Result.Success) { skipped++; continue }

            val customer = Customer(
                id = seed.id,
                name = seed.name,
                phone = seed.phone,
                email = seed.email,
                loyaltyPoints = seed.loyaltyPoints,
            )
            when (customerRepository.insert(customer)) {
                is Result.Success -> inserted++
                is Result.Error -> failed++
                is Result.Loading -> Unit
            }
        }
        return SeedResult("Customer", inserted, skipped, failed)
    }
}
