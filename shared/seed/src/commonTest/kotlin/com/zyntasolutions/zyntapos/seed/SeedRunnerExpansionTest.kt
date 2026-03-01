package com.zyntasolutions.zyntapos.seed

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CashMovement
import com.zyntasolutions.zyntapos.domain.model.CashRegister
import com.zyntasolutions.zyntapos.domain.model.Category
import com.zyntasolutions.zyntapos.domain.model.Coupon
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.Employee
import com.zyntasolutions.zyntapos.domain.model.Expense
import com.zyntasolutions.zyntapos.domain.model.ExpenseCategory
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.RecurringExpense
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
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
import com.zyntasolutions.zyntapos.domain.model.Promotion
import com.zyntasolutions.zyntapos.domain.model.CouponUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for expanded [SeedRunner] covering all new entity types added for
 * E2E testing: UnitOfMeasure, TaxGroup, User, CashRegister, ExpenseCategory,
 * Employee, Expense, and Coupon.
 *
 * Uses hand-rolled fakes (Mockative incompatible with Kotlin 2.3+/KSP1).
 *
 * Pattern follows the existing [SeedRunnerTest]:
 * - Idempotency: existing records are skipped
 * - Insertion success: new records are inserted
 * - Failure path: insert errors are counted
 * - FK-safe ordering validation
 * - DefaultSeedDataSet shape validation for new entity counts
 */
class SeedRunnerExpansionTest {

    // ── Existing fakes (required by SeedRunner constructor) ─────────────────

    private val existingCategoryIds = mutableSetOf<String>()
    private val existingSupplierIds = mutableSetOf<String>()
    private val existingProductIds = mutableSetOf<String>()
    private val existingCustomerIds = mutableSetOf<String>()

    private val fakeCategoryRepo = object : CategoryRepository {
        override fun getAll(): Flow<List<Category>> = flowOf(emptyList())
        override fun getTree(): Flow<List<Category>> = flowOf(emptyList())
        override suspend fun getById(id: String): Result<Category> =
            if (id in existingCategoryIds) Result.Success(Category(id, "cat"))
            else Result.Error(DatabaseException("Not found"))
        override suspend fun insert(c: Category): Result<Unit> {
            existingCategoryIds += c.id; return Result.Success(Unit)
        }
        override suspend fun update(c: Category) = Result.Success(Unit)
        override suspend fun delete(id: String) = Result.Success(Unit)
    }

    private val fakeSupplierRepo = object : SupplierRepository {
        override fun getAll(): Flow<List<Supplier>> = flowOf(emptyList())
        override suspend fun getById(id: String): Result<Supplier> =
            if (id in existingSupplierIds) Result.Success(Supplier(id, "sup"))
            else Result.Error(DatabaseException("Not found"))
        override suspend fun insert(s: Supplier): Result<Unit> {
            existingSupplierIds += s.id; return Result.Success(Unit)
        }
        override suspend fun update(s: Supplier) = Result.Success(Unit)
        override suspend fun delete(id: String) = Result.Success(Unit)
    }

    private val fakeProductRepo = object : ProductRepository {
        override fun getAll(): Flow<List<Product>> = flowOf(emptyList())
        override fun search(q: String, cid: String?) = flowOf(emptyList<Product>())
        override suspend fun getByBarcode(b: String) = Result.Error(DatabaseException("nf")) as Result<Product>
        override suspend fun getCount() = existingProductIds.size
        override suspend fun getById(id: String): Result<Product> =
            if (id in existingProductIds) Result.Success(
                Product(id, "p", categoryId = "c", unitId = "u", price = 1.0, createdAt = Clock.System.now(), updatedAt = Clock.System.now())
            ) else Result.Error(DatabaseException("Not found"))
        override suspend fun insert(p: Product): Result<Unit> {
            seedOrder += "Product"
            existingProductIds += p.id; return Result.Success(Unit)
        }
        override suspend fun update(p: Product) = Result.Success(Unit)
        override suspend fun delete(id: String) = Result.Success(Unit)
    }

    private val fakeCustomerRepo = object : CustomerRepository {
        override fun getAll(): Flow<List<Customer>> = flowOf(emptyList())
        override fun search(q: String) = flowOf(emptyList<Customer>())
        override suspend fun getById(id: String): Result<Customer> =
            if (id in existingCustomerIds) Result.Success(Customer(id, "c", phone = "0"))
            else Result.Error(DatabaseException("Not found"))
        override suspend fun insert(c: Customer): Result<Unit> {
            existingCustomerIds += c.id; return Result.Success(Unit)
        }
        override suspend fun update(c: Customer) = Result.Success(Unit)
        override suspend fun delete(id: String) = Result.Success(Unit)
    }

    // ── New entity fakes ────────────────────────────────────────────────────

    private val existingUnitIds = mutableSetOf<String>()
    private var unitInsertFails = false
    private val seedOrder = mutableListOf<String>()

    private val fakeUnitGroupRepo = object : UnitGroupRepository {
        override fun getAll(): Flow<List<UnitOfMeasure>> = flowOf(emptyList())
        override suspend fun getById(id: String): Result<UnitOfMeasure> =
            if (id in existingUnitIds) {
                seedOrder += "UnitOfMeasure"
                Result.Success(UnitOfMeasure(id, "Unit", "u"))
            } else Result.Error(DatabaseException("Not found"))
        override suspend fun insert(unit: UnitOfMeasure): Result<Unit> {
            seedOrder += "UnitOfMeasure"
            return if (unitInsertFails) Result.Error(DatabaseException("fail"))
            else { existingUnitIds += unit.id; Result.Success(Unit) }
        }
        override suspend fun update(unit: UnitOfMeasure) = Result.Success(Unit)
        override suspend fun delete(id: String) = Result.Success(Unit)
    }

    private val existingTaxGroupIds = mutableSetOf<String>()
    private var taxGroupInsertFails = false

    private val fakeTaxGroupRepo = object : TaxGroupRepository {
        override fun getAll(): Flow<List<TaxGroup>> = flowOf(emptyList())
        override suspend fun getById(id: String): Result<TaxGroup> =
            if (id in existingTaxGroupIds) {
                seedOrder += "TaxGroup"
                Result.Success(TaxGroup(id, "Tax", 0.0))
            } else Result.Error(DatabaseException("Not found"))
        override suspend fun insert(tg: TaxGroup): Result<Unit> {
            seedOrder += "TaxGroup"
            return if (taxGroupInsertFails) Result.Error(DatabaseException("fail"))
            else { existingTaxGroupIds += tg.id; Result.Success(Unit) }
        }
        override suspend fun update(tg: TaxGroup) = Result.Success(Unit)
        override suspend fun delete(id: String) = Result.Success(Unit)
    }

    private val existingUserIds = mutableSetOf<String>()
    private var userCreateFails = false
    private var lastCreatedUser: User? = null

    private val fakeUserRepo = object : UserRepository {
        override fun getAll(storeId: String?): Flow<List<User>> = flowOf(emptyList())
        override suspend fun getById(id: String): Result<User> =
            if (id in existingUserIds) {
                seedOrder += "User"
                Result.Success(User(id, "u", "e", com.zyntasolutions.zyntapos.domain.model.Role.CASHIER, "s",
                    createdAt = Clock.System.now(), updatedAt = Clock.System.now()))
            } else Result.Error(DatabaseException("Not found"))
        override suspend fun create(user: User, plainPassword: String): Result<Unit> {
            seedOrder += "User"
            lastCreatedUser = user
            return if (userCreateFails) Result.Error(DatabaseException("fail"))
            else { existingUserIds += user.id; Result.Success(Unit) }
        }
        override suspend fun update(user: User) = Result.Success(Unit)
        override suspend fun updatePassword(userId: String, newPlainPassword: String) = Result.Success(Unit)
        override suspend fun deactivate(userId: String) = Result.Success(Unit)
    }

    private val existingExpenseCategoryIds = mutableSetOf<String>()
    private var expenseCategoryInsertFails = false
    private val existingExpenseIds = mutableSetOf<String>()
    private var expenseInsertFails = false

    private val fakeExpenseRepo = object : ExpenseRepository {
        override fun getAll(): Flow<List<Expense>> = flowOf(emptyList())
        override fun getByStatus(status: Expense.Status) = flowOf(emptyList<Expense>())
        override fun getByDateRange(from: Long, to: Long) = flowOf(emptyList<Expense>())
        override suspend fun getById(id: String): Result<Expense> =
            if (id in existingExpenseIds) {
                seedOrder += "Expense"
                Result.Success(Expense(id, amount = 1.0, description = "x", expenseDate = 0L))
            } else Result.Error(DatabaseException("Not found"))
        override suspend fun getTotalByPeriod(from: Long, to: Long) = Result.Success(0.0)
        override suspend fun insert(expense: Expense): Result<Unit> {
            seedOrder += "Expense"
            return if (expenseInsertFails) Result.Error(DatabaseException("fail"))
            else { existingExpenseIds += expense.id; Result.Success(Unit) }
        }
        override suspend fun update(expense: Expense) = Result.Success(Unit)
        override suspend fun approve(id: String, approvedBy: String) = Result.Success(Unit)
        override suspend fun reject(id: String, rejectedBy: String, reason: String?) = Result.Success(Unit)
        override suspend fun delete(id: String) = Result.Success(Unit)

        // Expense Categories
        override fun getAllCategories(): Flow<List<ExpenseCategory>> = flowOf(emptyList())
        override suspend fun getCategoryById(id: String): Result<ExpenseCategory> =
            if (id in existingExpenseCategoryIds) {
                seedOrder += "ExpenseCategory"
                Result.Success(ExpenseCategory(id, "cat"))
            } else Result.Error(DatabaseException("Not found"))
        override suspend fun saveCategory(category: ExpenseCategory): Result<Unit> {
            seedOrder += "ExpenseCategory"
            return if (expenseCategoryInsertFails) Result.Error(DatabaseException("fail"))
            else { existingExpenseCategoryIds += category.id; Result.Success(Unit) }
        }
        override suspend fun deleteCategory(id: String) = Result.Success(Unit)

        // Recurring
        override fun getAllRecurring(): Flow<List<RecurringExpense>> = flowOf(emptyList())
        override suspend fun getActiveRecurring() = Result.Success(emptyList<RecurringExpense>())
        override suspend fun saveRecurring(r: RecurringExpense) = Result.Success(Unit)
        override suspend fun updateLastRun(id: String, lastRunMillis: Long) = Result.Success(Unit)
        override suspend fun deleteRecurring(id: String) = Result.Success(Unit)
    }

    private val existingEmployeeIds = mutableSetOf<String>()
    private var employeeInsertFails = false

    private val fakeEmployeeRepo = object : EmployeeRepository {
        override fun getActive(storeId: String) = flowOf(emptyList<Employee>())
        override fun getAll(storeId: String) = flowOf(emptyList<Employee>())
        override suspend fun getById(id: String): Result<Employee> =
            if (id in existingEmployeeIds) {
                seedOrder += "Employee"
                Result.Success(Employee(id, storeId = "s", firstName = "F", lastName = "L",
                    hireDate = "2024-01-01", position = "P", createdAt = 0, updatedAt = 0))
            } else Result.Error(DatabaseException("Not found"))
        override suspend fun getByUserId(userId: String) = Result.Success(null as Employee?)
        override suspend fun search(storeId: String, query: String) = Result.Success(emptyList<Employee>())
        override suspend fun insert(employee: Employee): Result<Unit> {
            seedOrder += "Employee"
            return if (employeeInsertFails) Result.Error(DatabaseException("fail"))
            else { existingEmployeeIds += employee.id; Result.Success(Unit) }
        }
        override suspend fun update(e: Employee) = Result.Success(Unit)
        override suspend fun setActive(id: String, isActive: Boolean) = Result.Success(Unit)
        override suspend fun delete(id: String) = Result.Success(Unit)
    }

    private val existingCouponIds = mutableSetOf<String>()
    private var couponInsertFails = false

    private val fakeCouponRepo = object : CouponRepository {
        override fun getAll(): Flow<List<Coupon>> = flowOf(emptyList())
        override fun getActiveCoupons(nowEpochMillis: Long) = flowOf(emptyList<Coupon>())
        override suspend fun getByCode(code: String) = Result.Error(DatabaseException("nf")) as Result<Coupon>
        override suspend fun getById(id: String): Result<Coupon> =
            if (id in existingCouponIds) {
                seedOrder += "Coupon"
                Result.Success(Coupon(id, "C", "C", com.zyntasolutions.zyntapos.domain.model.DiscountType.FIXED,
                    10.0, validFrom = 0, validTo = 1))
            } else Result.Error(DatabaseException("Not found"))
        override suspend fun insert(coupon: Coupon): Result<Unit> {
            seedOrder += "Coupon"
            return if (couponInsertFails) Result.Error(DatabaseException("fail"))
            else { existingCouponIds += coupon.id; Result.Success(Unit) }
        }
        override suspend fun update(c: Coupon) = Result.Success(Unit)
        override suspend fun toggleActive(id: String, isActive: Boolean) = Result.Success(Unit)
        override suspend fun delete(id: String) = Result.Success(Unit)
        override suspend fun recordRedemption(usage: CouponUsage) = Result.Success(Unit)
        override suspend fun getCustomerUsageCount(couponId: String, customerId: String) = Result.Success(0)
        override fun getUsageByCoupon(couponId: String) = flowOf(emptyList<CouponUsage>())
        override fun getAllPromotions() = flowOf(emptyList<Promotion>())
        override fun getActivePromotions(nowEpochMillis: Long) = flowOf(emptyList<Promotion>())
        override suspend fun getPromotionById(id: String) = Result.Error(DatabaseException("nf")) as Result<Promotion>
        override suspend fun insertPromotion(promotion: Promotion) = Result.Success(Unit)
        override suspend fun updatePromotion(promotion: Promotion) = Result.Success(Unit)
        override suspend fun deletePromotion(id: String) = Result.Success(Unit)
    }

    // ── SeedRunner factory ──────────────────────────────────────────────────

    private fun runner() = SeedRunner(
        categoryRepository = fakeCategoryRepo,
        supplierRepository = fakeSupplierRepo,
        productRepository = fakeProductRepo,
        customerRepository = fakeCustomerRepo,
        unitGroupRepository = fakeUnitGroupRepo,
        taxGroupRepository = fakeTaxGroupRepo,
        userRepository = fakeUserRepo,
        registerRepository = null, // CashRegister seeding is a passthrough for now
        expenseRepository = fakeExpenseRepo,
        employeeRepository = fakeEmployeeRepo,
        couponRepository = fakeCouponRepo,
    )

    // ════════════════════════════════════════════════════════════════════════
    // UnitOfMeasure seeding
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `seedUnits inserts all new units`() = runTest {
        val dataSet = SeedDataSet(
            units = listOf(
                SeedUnitOfMeasure("u1", "Pieces", "pcs", isBaseUnit = true),
                SeedUnitOfMeasure("u2", "Kilogram", "kg", isBaseUnit = true),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "UnitOfMeasure" }
        assertEquals(2, result.inserted)
        assertEquals(0, result.skipped)
        assertEquals(0, result.failed)
    }

    @Test
    fun `seedUnits skips existing units`() = runTest {
        existingUnitIds += "u1"
        val dataSet = SeedDataSet(
            units = listOf(
                SeedUnitOfMeasure("u1", "Pieces", "pcs"),
                SeedUnitOfMeasure("u2", "Kg", "kg"),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "UnitOfMeasure" }
        assertEquals(1, result.inserted)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `seedUnits counts failures`() = runTest {
        unitInsertFails = true
        val dataSet = SeedDataSet(
            units = listOf(SeedUnitOfMeasure("u1", "Pieces", "pcs")),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "UnitOfMeasure" }
        assertEquals(0, result.inserted)
        assertEquals(1, result.failed)
    }

    // ════════════════════════════════════════════════════════════════════════
    // TaxGroup seeding
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `seedTaxGroups inserts all new tax groups`() = runTest {
        val dataSet = SeedDataSet(
            taxGroups = listOf(
                SeedTaxGroup("t1", "VAT 15%", 15.0),
                SeedTaxGroup("t2", "Zero Rate", 0.0),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "TaxGroup" }
        assertEquals(2, result.inserted)
        assertEquals(0, result.skipped)
        assertEquals(0, result.failed)
    }

    @Test
    fun `seedTaxGroups skips existing groups`() = runTest {
        existingTaxGroupIds += "t1"
        val dataSet = SeedDataSet(
            taxGroups = listOf(
                SeedTaxGroup("t1", "VAT 15%", 15.0),
                SeedTaxGroup("t2", "Zero Rate", 0.0),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "TaxGroup" }
        assertEquals(1, result.inserted)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `seedTaxGroups counts failures`() = runTest {
        taxGroupInsertFails = true
        val dataSet = SeedDataSet(
            taxGroups = listOf(SeedTaxGroup("t1", "VAT 15%", 15.0)),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "TaxGroup" }
        assertEquals(0, result.inserted)
        assertEquals(1, result.failed)
        assertFalse(summary.isSuccess)
    }

    // ════════════════════════════════════════════════════════════════════════
    // User seeding
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `seedUsers inserts all new users`() = runTest {
        val dataSet = SeedDataSet(
            users = listOf(
                SeedUser("u1", "Admin", "admin@test.com", "ADMIN", "s1"),
                SeedUser("u2", "Cashier", "cashier@test.com", "CASHIER", "s1"),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "User" }
        assertEquals(2, result.inserted)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `seedUsers skips existing users`() = runTest {
        existingUserIds += "u1"
        val dataSet = SeedDataSet(
            users = listOf(SeedUser("u1", "Admin", "admin@test.com", "ADMIN", "s1")),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "User" }
        assertEquals(0, result.inserted)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `seedUsers counts failures`() = runTest {
        userCreateFails = true
        val dataSet = SeedDataSet(
            users = listOf(SeedUser("u1", "Admin", "admin@test.com", "ADMIN", "s1")),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "User" }
        assertEquals(1, result.failed)
    }

    @Test
    fun `seedUsers maps role string to enum correctly`() = runTest {
        val dataSet = SeedDataSet(
            users = listOf(SeedUser("u1", "Manager", "mgr@test.com", "STORE_MANAGER", "s1")),
        )
        runner().run(dataSet)
        assertEquals(com.zyntasolutions.zyntapos.domain.model.Role.STORE_MANAGER, lastCreatedUser?.role)
    }

    // ════════════════════════════════════════════════════════════════════════
    // ExpenseCategory seeding
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `seedExpenseCategories inserts all new categories`() = runTest {
        val dataSet = SeedDataSet(
            expenseCategories = listOf(
                SeedExpenseCategory("ec1", "Rent", "Monthly rent"),
                SeedExpenseCategory("ec2", "Utilities"),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "ExpenseCategory" }
        assertEquals(2, result.inserted)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `seedExpenseCategories skips existing`() = runTest {
        existingExpenseCategoryIds += "ec1"
        val dataSet = SeedDataSet(
            expenseCategories = listOf(SeedExpenseCategory("ec1", "Rent")),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "ExpenseCategory" }
        assertEquals(0, result.inserted)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `seedExpenseCategories counts failures`() = runTest {
        expenseCategoryInsertFails = true
        val dataSet = SeedDataSet(
            expenseCategories = listOf(SeedExpenseCategory("ec1", "Rent")),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "ExpenseCategory" }
        assertEquals(1, result.failed)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Employee seeding
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `seedEmployees inserts all new employees`() = runTest {
        val dataSet = SeedDataSet(
            employees = listOf(
                SeedEmployee("e1", "s1", "John", "Doe", hireDate = "2024-01-01", position = "Cashier"),
                SeedEmployee("e2", "s1", "Jane", "Smith", hireDate = "2024-06-01", position = "Manager"),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Employee" }
        assertEquals(2, result.inserted)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `seedEmployees skips existing`() = runTest {
        existingEmployeeIds += "e1"
        val dataSet = SeedDataSet(
            employees = listOf(SeedEmployee("e1", "s1", "John", "Doe", hireDate = "2024-01-01", position = "Cashier")),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Employee" }
        assertEquals(0, result.inserted)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `seedEmployees counts failures`() = runTest {
        employeeInsertFails = true
        val dataSet = SeedDataSet(
            employees = listOf(SeedEmployee("e1", "s1", "John", "Doe", hireDate = "2024-01-01", position = "Cashier")),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Employee" }
        assertEquals(1, result.failed)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Expense seeding
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `seedExpenses inserts all new expenses`() = runTest {
        val dataSet = SeedDataSet(
            expenses = listOf(
                SeedExpense("x1", amount = 1000.0, description = "Rent", expenseDate = 1700000000000L),
                SeedExpense("x2", amount = 500.0, description = "Power", expenseDate = 1700000000000L),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Expense" }
        assertEquals(2, result.inserted)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `seedExpenses skips existing`() = runTest {
        existingExpenseIds += "x1"
        val dataSet = SeedDataSet(
            expenses = listOf(SeedExpense("x1", amount = 1000.0, description = "Rent", expenseDate = 0L)),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Expense" }
        assertEquals(0, result.inserted)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `seedExpenses counts failures`() = runTest {
        expenseInsertFails = true
        val dataSet = SeedDataSet(
            expenses = listOf(SeedExpense("x1", amount = 1000.0, description = "Rent", expenseDate = 0L)),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Expense" }
        assertEquals(1, result.failed)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Coupon seeding
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `seedCoupons inserts all new coupons`() = runTest {
        val dataSet = SeedDataSet(
            coupons = listOf(
                SeedCoupon("c1", "SAVE10", "Save 10%", "PERCENT", 10.0, validFrom = 0, validTo = 1),
                SeedCoupon("c2", "FLAT50", "Flat 50", "FIXED", 50.0, validFrom = 0, validTo = 1),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Coupon" }
        assertEquals(2, result.inserted)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `seedCoupons skips existing`() = runTest {
        existingCouponIds += "c1"
        val dataSet = SeedDataSet(
            coupons = listOf(SeedCoupon("c1", "SAVE10", "10%", "PERCENT", 10.0, validFrom = 0, validTo = 1)),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Coupon" }
        assertEquals(0, result.inserted)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `seedCoupons counts failures`() = runTest {
        couponInsertFails = true
        val dataSet = SeedDataSet(
            coupons = listOf(SeedCoupon("c1", "SAVE10", "10%", "PERCENT", 10.0, validFrom = 0, validTo = 1)),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Coupon" }
        assertEquals(1, result.failed)
    }

    // ════════════════════════════════════════════════════════════════════════
    // FK-safe ordering (extended)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `run seeds units before products`() = runTest {
        seedOrder.clear()
        val dataSet = SeedDataSet(
            units = listOf(SeedUnitOfMeasure("u1", "Pieces", "pcs")),
            products = listOf(SeedProduct("p1", "Espresso", categoryId = "c1", price = 2.50)),
        )
        runner().run(dataSet)
        val firstUnit = seedOrder.indexOfFirst { it == "UnitOfMeasure" }
        val firstProduct = seedOrder.indexOfFirst { it == "Product" }
        assertTrue(firstUnit < firstProduct, "Units must be seeded before products")
    }

    @Test
    fun `run seeds tax groups before products`() = runTest {
        seedOrder.clear()
        val dataSet = SeedDataSet(
            taxGroups = listOf(SeedTaxGroup("t1", "VAT", 15.0)),
            products = listOf(SeedProduct("p1", "Espresso", categoryId = "c1", price = 2.50)),
        )
        runner().run(dataSet)
        val firstTax = seedOrder.indexOfFirst { it == "TaxGroup" }
        val firstProduct = seedOrder.indexOfFirst { it == "Product" }
        assertTrue(firstTax < firstProduct, "TaxGroups must be seeded before products")
    }

    @Test
    fun `run seeds users before employees`() = runTest {
        seedOrder.clear()
        val dataSet = SeedDataSet(
            users = listOf(SeedUser("u1", "Admin", "a@t.com", "ADMIN", "s1")),
            employees = listOf(SeedEmployee("e1", "s1", "F", "L", hireDate = "2024-01-01", position = "P")),
        )
        runner().run(dataSet)
        val firstUser = seedOrder.indexOfFirst { it == "User" }
        val firstEmployee = seedOrder.indexOfFirst { it == "Employee" }
        assertTrue(firstUser < firstEmployee, "Users must be seeded before employees")
    }

    @Test
    fun `run seeds expense categories before expenses`() = runTest {
        seedOrder.clear()
        val dataSet = SeedDataSet(
            expenseCategories = listOf(SeedExpenseCategory("ec1", "Rent")),
            expenses = listOf(SeedExpense("x1", categoryId = "ec1", amount = 1000.0, description = "Rent", expenseDate = 0L)),
        )
        runner().run(dataSet)
        val firstCat = seedOrder.indexOfFirst { it == "ExpenseCategory" }
        val firstExp = seedOrder.indexOfFirst { it == "Expense" }
        assertTrue(firstCat < firstExp, "ExpenseCategories must be seeded before Expenses")
    }

    // ════════════════════════════════════════════════════════════════════════
    // Full expanded run
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `run with all new entity types returns isSuccess true`() = runTest {
        val dataSet = SeedDataSet(
            categories = listOf(SeedCategory("c1", "Coffee")),
            suppliers = listOf(SeedSupplier("s1", "Supplier A")),
            products = listOf(SeedProduct("p1", "Espresso", categoryId = "c1", price = 2.50)),
            customers = listOf(SeedCustomer("cu1", "Alice", phone = "+1-555-0001")),
            units = listOf(SeedUnitOfMeasure("u1", "Pieces", "pcs")),
            taxGroups = listOf(SeedTaxGroup("t1", "VAT 15%", 15.0)),
            users = listOf(SeedUser("usr1", "Admin", "admin@test.com", "ADMIN", "s1")),
            expenseCategories = listOf(SeedExpenseCategory("ec1", "Rent")),
            employees = listOf(SeedEmployee("e1", "s1", "John", "Doe", hireDate = "2024-01-01", position = "Cashier")),
            expenses = listOf(SeedExpense("x1", amount = 1000.0, description = "Rent", expenseDate = 0L)),
            coupons = listOf(SeedCoupon("cp1", "SAVE10", "10% off", "PERCENT", 10.0, validFrom = 0, validTo = 1)),
        )
        val summary = runner().run(dataSet)
        assertTrue(summary.isSuccess)
        assertEquals(11, summary.totalInserted)
        assertEquals(0, summary.totalFailed)
    }

    @Test
    fun `run is idempotent for new entity types`() = runTest {
        val dataSet = SeedDataSet(
            units = listOf(SeedUnitOfMeasure("u1", "Pieces", "pcs")),
            taxGroups = listOf(SeedTaxGroup("t1", "VAT", 15.0)),
            users = listOf(SeedUser("usr1", "Admin", "a@t.com", "ADMIN", "s1")),
            expenseCategories = listOf(SeedExpenseCategory("ec1", "Rent")),
            employees = listOf(SeedEmployee("e1", "s1", "F", "L", hireDate = "2024-01-01", position = "P")),
            expenses = listOf(SeedExpense("x1", amount = 100.0, description = "D", expenseDate = 0L)),
            coupons = listOf(SeedCoupon("c1", "C1", "C", "FIXED", 10.0, validFrom = 0, validTo = 1)),
        )
        val r = runner()
        val first = r.run(dataSet)
        val second = r.run(dataSet)

        assertEquals(7, first.totalInserted)
        assertEquals(0, first.totalSkipped)

        assertEquals(0, second.totalInserted)
        assertEquals(7, second.totalSkipped)
    }

    @Test
    fun `null optional repositories degrade gracefully`() = runTest {
        val minimalRunner = SeedRunner(
            categoryRepository = fakeCategoryRepo,
            supplierRepository = fakeSupplierRepo,
            productRepository = fakeProductRepo,
            customerRepository = fakeCustomerRepo,
            // all new repos are null
        )
        val dataSet = SeedDataSet(
            categories = listOf(SeedCategory("c1", "Coffee")),
            taxGroups = listOf(SeedTaxGroup("t1", "VAT", 15.0)), // should be skipped
            users = listOf(SeedUser("u1", "A", "a@t.com", "ADMIN", "s1")), // should be skipped
        )
        val summary = minimalRunner.run(dataSet)
        // Only category result, tax groups and users skipped because repos are null
        assertEquals(1, summary.results.size)
        assertEquals("Category", summary.results[0].entityType)
        assertTrue(summary.isSuccess)
    }

    // ════════════════════════════════════════════════════════════════════════
    // DefaultSeedDataSet validation (expanded)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `DefaultSeedDataSet build returns expected new entity counts`() {
        val dataSet = DefaultSeedDataSet.build()
        assertEquals(10, dataSet.units.size, "Expected 10 units of measure")
        assertEquals(5, dataSet.taxGroups.size, "Expected 5 tax groups")
        assertEquals(6, dataSet.users.size, "Expected 6 users")
        assertEquals(3, dataSet.cashRegisters.size, "Expected 3 cash registers")
        assertEquals(8, dataSet.expenseCategories.size, "Expected 8 expense categories")
        assertEquals(8, dataSet.employees.size, "Expected 8 employees")
        assertEquals(10, dataSet.expenses.size, "Expected 10 expenses")
        assertEquals(5, dataSet.coupons.size, "Expected 5 coupons")
    }

    @Test
    fun `DefaultSeedDataSet all unit IDs are unique`() {
        val ids = DefaultSeedDataSet.build().units.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Unit IDs must be unique")
    }

    @Test
    fun `DefaultSeedDataSet all tax group IDs are unique`() {
        val ids = DefaultSeedDataSet.build().taxGroups.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "TaxGroup IDs must be unique")
    }

    @Test
    fun `DefaultSeedDataSet all user IDs are unique`() {
        val ids = DefaultSeedDataSet.build().users.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "User IDs must be unique")
    }

    @Test
    fun `DefaultSeedDataSet all user emails are unique`() {
        val emails = DefaultSeedDataSet.build().users.map { it.email }
        assertEquals(emails.size, emails.toSet().size, "User emails must be unique")
    }

    @Test
    fun `DefaultSeedDataSet all employee IDs are unique`() {
        val ids = DefaultSeedDataSet.build().employees.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Employee IDs must be unique")
    }

    @Test
    fun `DefaultSeedDataSet all expense IDs are unique`() {
        val ids = DefaultSeedDataSet.build().expenses.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Expense IDs must be unique")
    }

    @Test
    fun `DefaultSeedDataSet all coupon IDs are unique`() {
        val ids = DefaultSeedDataSet.build().coupons.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Coupon IDs must be unique")
    }

    @Test
    fun `DefaultSeedDataSet all coupon codes are unique`() {
        val codes = DefaultSeedDataSet.build().coupons.map { it.code }
        assertEquals(codes.size, codes.toSet().size, "Coupon codes must be unique")
    }

    @Test
    fun `DefaultSeedDataSet all tax rates are in valid range`() {
        DefaultSeedDataSet.build().taxGroups.forEach { tg ->
            assertTrue(
                tg.rate in 0.0..100.0,
                "Tax group '${tg.name}' has rate ${tg.rate} outside 0-100 range",
            )
        }
    }

    @Test
    fun `DefaultSeedDataSet all expenses have positive amounts`() {
        DefaultSeedDataSet.build().expenses.forEach { exp ->
            assertTrue(
                exp.amount > 0.0,
                "Expense '${exp.description}' has non-positive amount ${exp.amount}",
            )
        }
    }

    @Test
    fun `DefaultSeedDataSet all employees have non-blank hire date`() {
        DefaultSeedDataSet.build().employees.forEach { emp ->
            assertTrue(
                emp.hireDate.isNotBlank(),
                "Employee '${emp.firstName} ${emp.lastName}' has blank hireDate",
            )
        }
    }

    @Test
    fun `DefaultSeedDataSet all coupons have validFrom before validTo`() {
        DefaultSeedDataSet.build().coupons.forEach { c ->
            assertTrue(
                c.validFrom < c.validTo,
                "Coupon '${c.code}' has validFrom (${c.validFrom}) >= validTo (${c.validTo})",
            )
        }
    }

    @Test
    fun `DefaultSeedDataSet expenses reference valid expense category IDs`() {
        val dataSet = DefaultSeedDataSet.build()
        val categoryIds = dataSet.expenseCategories.map { it.id }.toSet()
        dataSet.expenses.filter { it.categoryId != null }.forEach { exp ->
            assertTrue(
                exp.categoryId in categoryIds,
                "Expense '${exp.description}' references unknown categoryId '${exp.categoryId}'",
            )
        }
    }

    @Test
    fun `DefaultSeedDataSet contains at least one user per role`() {
        val dataSet = DefaultSeedDataSet.build()
        val roles = dataSet.users.map { it.role }.toSet()
        assertTrue("ADMIN" in roles, "Expected at least one ADMIN user")
        assertTrue("CASHIER" in roles, "Expected at least one CASHIER user")
        assertTrue("STORE_MANAGER" in roles, "Expected at least one STORE_MANAGER user")
    }

    @Test
    fun `DefaultSeedDataSet contains at least one pending expense for approval workflow testing`() {
        val pending = DefaultSeedDataSet.build().expenses.count { it.status == "PENDING" }
        assertTrue(pending >= 1, "Expected at least one PENDING expense for approval testing")
    }

    @Test
    fun `DefaultSeedDataSet contains at least one rejected expense for status testing`() {
        val rejected = DefaultSeedDataSet.build().expenses.count { it.status == "REJECTED" }
        assertTrue(rejected >= 1, "Expected at least one REJECTED expense for status testing")
    }
}
