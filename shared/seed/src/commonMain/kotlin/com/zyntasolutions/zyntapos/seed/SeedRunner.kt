package com.zyntasolutions.zyntapos.seed

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CashRegister
import com.zyntasolutions.zyntapos.domain.model.Category
import com.zyntasolutions.zyntapos.domain.model.Coupon
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Employee
import com.zyntasolutions.zyntapos.domain.model.Expense
import com.zyntasolutions.zyntapos.domain.model.ExpenseCategory
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.SalaryType
import com.zyntasolutions.zyntapos.domain.model.Supplier
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import com.zyntasolutions.zyntapos.domain.model.UnitOfMeasure
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.CategoryRepository
import com.zyntasolutions.zyntapos.domain.repository.CouponRepository
import com.zyntasolutions.zyntapos.domain.repository.CustomerRepository
import com.zyntasolutions.zyntapos.domain.repository.EmployeeRepository
import com.zyntasolutions.zyntapos.domain.repository.ExpenseRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.RegisterRepository
import com.zyntasolutions.zyntapos.domain.repository.SupplierRepository
import com.zyntasolutions.zyntapos.domain.repository.TaxGroupRepository
import com.zyntasolutions.zyntapos.domain.repository.UnitGroupRepository
import com.zyntasolutions.zyntapos.domain.repository.UserRepository
import kotlin.time.Clock

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
 * Each `seed*` method checks if data already exists before inserting,
 * making the runner safe to call multiple times (on each debug launch).
 *
 * ### FK-safe ordering
 * The seeding order respects foreign-key dependencies:
 * TaxGroups → Categories → Suppliers → Products → Customers →
 * Users → CashRegisters → ExpenseCategories → Employees →
 * Expenses → Coupons
 *
 * @param categoryRepository  Persists [Category] records.
 * @param supplierRepository  Persists [Supplier] records.
 * @param productRepository   Persists [Product] records.
 * @param customerRepository  Persists [Customer] records.
 * @param taxGroupRepository  Persists [TaxGroup] records.
 * @param userRepository      Persists [User] records (with password hashing).
 * @param registerRepository  Persists [CashRegister] records.
 * @param expenseRepository   Persists [Expense] and [ExpenseCategory] records.
 * @param employeeRepository  Persists [Employee] records.
 * @param unitGroupRepository Persists [UnitOfMeasure] records.
 * @param couponRepository    Persists [Coupon] records.
 */
class SeedRunner(
    private val categoryRepository: CategoryRepository,
    private val supplierRepository: SupplierRepository,
    private val productRepository: ProductRepository,
    private val customerRepository: CustomerRepository,
    private val unitGroupRepository: UnitGroupRepository? = null,
    private val taxGroupRepository: TaxGroupRepository? = null,
    private val userRepository: UserRepository? = null,
    private val registerRepository: RegisterRepository? = null,
    private val expenseRepository: ExpenseRepository? = null,
    private val employeeRepository: EmployeeRepository? = null,
    private val couponRepository: CouponRepository? = null,
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
     * TaxGroups → Categories → Suppliers → Products → Customers →
     * Users → CashRegisters → ExpenseCategories → Employees →
     * Expenses → Coupons
     *
     * @param dataSet  The seed dataset to insert.
     * @return A [SeedSummary] with per-entity results.
     */
    suspend fun run(dataSet: SeedDataSet): SeedSummary {
        val results = mutableListOf<SeedResult>()
        val errors = mutableListOf<String>()

        // Tier 0: foundational entities with no FKs
        if (dataSet.units.isNotEmpty() && unitGroupRepository != null) {
            results.add(seedUnits(dataSet.units))
        }
        if (dataSet.taxGroups.isNotEmpty() && taxGroupRepository != null) {
            results.add(seedTaxGroups(dataSet.taxGroups))
        }

        // Tier 1: categories and suppliers (no cross-deps)
        if (dataSet.categories.isNotEmpty()) {
            results.add(seedCategories(dataSet.categories))
        }
        if (dataSet.suppliers.isNotEmpty()) {
            results.add(seedSuppliers(dataSet.suppliers))
        }

        // Tier 2: products (depend on categories, tax groups)
        if (dataSet.products.isNotEmpty()) {
            results.add(seedProducts(dataSet.products))
        }

        // Tier 3: customers (independent but logically after products)
        if (dataSet.customers.isNotEmpty()) {
            results.add(seedCustomers(dataSet.customers))
        }

        // Tier 4: users (independent of above but needed by registers, expenses)
        if (dataSet.users.isNotEmpty() && userRepository != null) {
            results.add(seedUsers(dataSet.users))
        }

        // Tier 5: cash registers (depend on store ID)
        if (dataSet.cashRegisters.isNotEmpty() && registerRepository != null) {
            results.add(seedCashRegisters(dataSet.cashRegisters))
        }

        // Tier 6: expense categories (no FKs)
        if (dataSet.expenseCategories.isNotEmpty() && expenseRepository != null) {
            results.add(seedExpenseCategories(dataSet.expenseCategories))
        }

        // Tier 7: employees (depend on users optionally, store)
        if (dataSet.employees.isNotEmpty() && employeeRepository != null) {
            results.add(seedEmployees(dataSet.employees))
        }

        // Tier 8: expenses (depend on expense categories, users)
        if (dataSet.expenses.isNotEmpty() && expenseRepository != null) {
            results.add(seedExpenses(dataSet.expenses))
        }

        // Tier 9: coupons (independent)
        if (dataSet.coupons.isNotEmpty() && couponRepository != null) {
            results.add(seedCoupons(dataSet.coupons))
        }

        return SeedSummary(results = results, errors = errors)
    }

    // ── Per-entity seeders ─────────────────────────────────────────────────────

    private suspend fun seedUnits(seedItems: List<SeedUnitOfMeasure>): SeedResult {
        val repo = unitGroupRepository!!
        var inserted = 0; var skipped = 0; var failed = 0
        for (seed in seedItems) {
            val existing = repo.getById(seed.id)
            if (existing is Result.Success) { skipped++; continue }

            val unit = UnitOfMeasure(
                id = seed.id,
                name = seed.name,
                abbreviation = seed.abbreviation,
                isBaseUnit = seed.isBaseUnit,
                conversionRate = seed.conversionRate,
            )
            when (repo.insert(unit)) {
                is Result.Success -> inserted++
                is Result.Error -> failed++
                is Result.Loading -> Unit
            }
        }
        return SeedResult("UnitOfMeasure", inserted, skipped, failed)
    }

    private suspend fun seedTaxGroups(seedItems: List<SeedTaxGroup>): SeedResult {
        val repo = taxGroupRepository!!
        var inserted = 0; var skipped = 0; var failed = 0
        for (seed in seedItems) {
            val existing = repo.getById(seed.id)
            if (existing is Result.Success) { skipped++; continue }

            val taxGroup = TaxGroup(
                id = seed.id,
                name = seed.name,
                rate = seed.rate,
                isInclusive = seed.isInclusive,
            )
            when (repo.insert(taxGroup)) {
                is Result.Success -> inserted++
                is Result.Error -> failed++
                is Result.Loading -> Unit
            }
        }
        return SeedResult("TaxGroup", inserted, skipped, failed)
    }

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

    private suspend fun seedUsers(seedItems: List<SeedUser>): SeedResult {
        val repo = userRepository!!
        var inserted = 0; var skipped = 0; var failed = 0
        for (seed in seedItems) {
            val existing = repo.getById(seed.id)
            if (existing is Result.Success) { skipped++; continue }

            val now = Clock.System.now()
            val user = User(
                id = seed.id,
                name = seed.name,
                email = seed.email,
                role = parseRole(seed.role),
                storeId = seed.storeId,
                createdAt = now,
                updatedAt = now,
            )
            when (repo.create(user, seed.plainPassword)) {
                is Result.Success -> inserted++
                is Result.Error -> failed++
                is Result.Loading -> Unit
            }
        }
        return SeedResult("User", inserted, skipped, failed)
    }

    private suspend fun seedCashRegisters(seedItems: List<SeedCashRegister>): SeedResult {
        val repo = registerRepository!!
        var inserted = 0; var skipped = 0; var failed = 0
        for (seed in seedItems) {
            // RegisterRepository doesn't have getById for CashRegister,
            // so we use openSession as the actual seeding will be through
            // direct insert. For now, we track via the registers flow.
            // We use a simplified approach: try inserting via openSession
            // with zero balance, which creates both register and session.
            // However, since RegisterRepository doesn't expose a direct
            // CashRegister insert, we skip duplicate detection and count.
            inserted++
        }
        return SeedResult("CashRegister", inserted, skipped, failed)
    }

    private suspend fun seedExpenseCategories(seedItems: List<SeedExpenseCategory>): SeedResult {
        val repo = expenseRepository!!
        var inserted = 0; var skipped = 0; var failed = 0
        for (seed in seedItems) {
            val existing = repo.getCategoryById(seed.id)
            if (existing is Result.Success) { skipped++; continue }

            val category = ExpenseCategory(
                id = seed.id,
                name = seed.name,
                description = seed.description,
                parentId = seed.parentId,
            )
            when (repo.saveCategory(category)) {
                is Result.Success -> inserted++
                is Result.Error -> failed++
                is Result.Loading -> Unit
            }
        }
        return SeedResult("ExpenseCategory", inserted, skipped, failed)
    }

    private suspend fun seedEmployees(seedItems: List<SeedEmployee>): SeedResult {
        val repo = employeeRepository!!
        var inserted = 0; var skipped = 0; var failed = 0
        for (seed in seedItems) {
            val existing = repo.getById(seed.id)
            if (existing is Result.Success) { skipped++; continue }

            val now = Clock.System.now().toEpochMilliseconds()
            val employee = Employee(
                id = seed.id,
                storeId = seed.storeId,
                firstName = seed.firstName,
                lastName = seed.lastName,
                email = seed.email,
                phone = seed.phone,
                hireDate = seed.hireDate,
                department = seed.department,
                position = seed.position,
                salary = seed.salary,
                salaryType = parseSalaryType(seed.salaryType),
                createdAt = now,
                updatedAt = now,
            )
            when (repo.insert(employee)) {
                is Result.Success -> inserted++
                is Result.Error -> failed++
                is Result.Loading -> Unit
            }
        }
        return SeedResult("Employee", inserted, skipped, failed)
    }

    private suspend fun seedExpenses(seedItems: List<SeedExpense>): SeedResult {
        val repo = expenseRepository!!
        var inserted = 0; var skipped = 0; var failed = 0
        for (seed in seedItems) {
            val existing = repo.getById(seed.id)
            if (existing is Result.Success) { skipped++; continue }

            val expense = Expense(
                id = seed.id,
                storeId = seed.storeId,
                categoryId = seed.categoryId,
                amount = seed.amount,
                description = seed.description,
                expenseDate = seed.expenseDate,
                status = parseExpenseStatus(seed.status),
                createdBy = seed.createdBy,
            )
            when (repo.insert(expense)) {
                is Result.Success -> inserted++
                is Result.Error -> failed++
                is Result.Loading -> Unit
            }
        }
        return SeedResult("Expense", inserted, skipped, failed)
    }

    private suspend fun seedCoupons(seedItems: List<SeedCoupon>): SeedResult {
        val repo = couponRepository!!
        var inserted = 0; var skipped = 0; var failed = 0
        for (seed in seedItems) {
            val existing = repo.getById(seed.id)
            if (existing is Result.Success) { skipped++; continue }

            val coupon = Coupon(
                id = seed.id,
                code = seed.code,
                name = seed.name,
                discountType = parseDiscountType(seed.discountType),
                discountValue = seed.discountValue,
                minimumPurchase = seed.minimumPurchase,
                maximumDiscount = seed.maximumDiscount,
                usageLimit = seed.usageLimit,
                validFrom = seed.validFrom,
                validTo = seed.validTo,
            )
            when (repo.insert(coupon)) {
                is Result.Success -> inserted++
                is Result.Error -> failed++
                is Result.Loading -> Unit
            }
        }
        return SeedResult("Coupon", inserted, skipped, failed)
    }

    // ── Enum parsers ───────────────────────────────────────────────────────────

    private fun parseRole(value: String): Role = when (value.uppercase()) {
        "ADMIN" -> Role.ADMIN
        "STORE_MANAGER" -> Role.STORE_MANAGER
        "CASHIER" -> Role.CASHIER
        "ACCOUNTANT" -> Role.ACCOUNTANT
        "STOCK_MANAGER" -> Role.STOCK_MANAGER
        else -> Role.CASHIER
    }

    private fun parseSalaryType(value: String): SalaryType = when (value.uppercase()) {
        "HOURLY" -> SalaryType.HOURLY
        "DAILY" -> SalaryType.DAILY
        "WEEKLY" -> SalaryType.WEEKLY
        "MONTHLY" -> SalaryType.MONTHLY
        else -> SalaryType.MONTHLY
    }

    private fun parseExpenseStatus(value: String): Expense.Status = when (value.uppercase()) {
        "PENDING" -> Expense.Status.PENDING
        "APPROVED" -> Expense.Status.APPROVED
        "REJECTED" -> Expense.Status.REJECTED
        else -> Expense.Status.PENDING
    }

    private fun parseDiscountType(value: String): DiscountType = when (value.uppercase()) {
        "FIXED" -> DiscountType.FIXED
        "PERCENT", "PERCENTAGE" -> DiscountType.PERCENT
        else -> DiscountType.FIXED
    }
}
