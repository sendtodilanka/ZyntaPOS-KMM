package com.zyntasolutions.zyntapos.seed

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Account
import com.zyntasolutions.zyntapos.domain.model.AccountBalance
import com.zyntasolutions.zyntapos.domain.model.AccountType
import com.zyntasolutions.zyntapos.domain.model.AccountingPeriod
import com.zyntasolutions.zyntapos.domain.model.CashMovement
import com.zyntasolutions.zyntapos.domain.model.CashRegister
import com.zyntasolutions.zyntapos.domain.model.Category
import com.zyntasolutions.zyntapos.domain.model.Coupon
import com.zyntasolutions.zyntapos.domain.model.CouponUsage
import com.zyntasolutions.zyntapos.domain.model.CustomRole
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.CustomerGroup
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.Employee
import com.zyntasolutions.zyntapos.domain.model.Expense
import com.zyntasolutions.zyntapos.domain.model.ExpenseCategory
import com.zyntasolutions.zyntapos.domain.model.FeatureConfig
import com.zyntasolutions.zyntapos.domain.model.LabelTemplate
import com.zyntasolutions.zyntapos.domain.model.NormalBalance
import com.zyntasolutions.zyntapos.domain.model.PeriodStatus
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.PrinterJobType
import com.zyntasolutions.zyntapos.domain.model.PrinterProfile
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.Promotion
import com.zyntasolutions.zyntapos.domain.model.RecurringExpense
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.StockTransfer
import com.zyntasolutions.zyntapos.domain.model.Supplier
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import com.zyntasolutions.zyntapos.domain.model.UnitOfMeasure
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.model.Warehouse
import com.zyntasolutions.zyntapos.domain.model.ZyntaFeature
import com.zyntasolutions.zyntapos.domain.repository.AccountRepository
import com.zyntasolutions.zyntapos.domain.repository.AccountingPeriodRepository
import com.zyntasolutions.zyntapos.domain.repository.CategoryRepository
import com.zyntasolutions.zyntapos.domain.repository.CouponRepository
import com.zyntasolutions.zyntapos.domain.repository.CustomerGroupRepository
import com.zyntasolutions.zyntapos.domain.repository.CustomerRepository
import com.zyntasolutions.zyntapos.domain.repository.EmployeeRepository
import com.zyntasolutions.zyntapos.domain.repository.ExpenseRepository
import com.zyntasolutions.zyntapos.domain.repository.FeatureRegistryRepository
import com.zyntasolutions.zyntapos.domain.repository.LabelTemplateRepository
import com.zyntasolutions.zyntapos.domain.repository.PrinterProfileRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.RegisterRepository
import com.zyntasolutions.zyntapos.domain.repository.RoleRepository
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import com.zyntasolutions.zyntapos.domain.repository.SupplierRepository
import com.zyntasolutions.zyntapos.domain.repository.TaxGroupRepository
import com.zyntasolutions.zyntapos.domain.repository.UnitGroupRepository
import com.zyntasolutions.zyntapos.domain.repository.UserRepository
import com.zyntasolutions.zyntapos.domain.repository.WarehouseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

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
        override fun searchGlobal(query: String) = flowOf(emptyList<Customer>())
        override fun getByStore(storeId: String) = flowOf(emptyList<Customer>())
        override fun getGlobalCustomers() = flowOf(emptyList<Customer>())
        override suspend fun makeGlobal(customerId: String) = Result.Success(Unit)
        override suspend fun updateLoyaltyPoints(customerId: String, points: Int) = Result.Success(Unit)
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
        override suspend fun getSystemAdmin(): Result<User?> = Result.Success(null)
        override suspend fun adminExists(): Result<Boolean> = Result.Success(false)
        override suspend fun transferSystemAdmin(fromUserId: String, toUserId: String): Result<Unit> = Result.Success(Unit)
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
        override fun getActiveCouponsForStore(nowEpochMillis: Long, storeId: String) = flowOf(emptyList<Coupon>())
        override fun getActivePromotionsForStore(nowEpochMillis: Long, storeId: String) = flowOf(emptyList<Promotion>())
        override suspend fun getPromotionById(id: String) = Result.Error(DatabaseException("nf")) as Result<Promotion>
        override suspend fun insertPromotion(promotion: Promotion) = Result.Success(Unit)
        override suspend fun updatePromotion(promotion: Promotion) = Result.Success(Unit)
        override suspend fun deletePromotion(id: String) = Result.Success(Unit)
    }

    // ── New entity fakes (Phase 2: warehouses, accounts, periods, etc.) ─────

    private val existingWarehouseIds = mutableSetOf<String>()
    private var warehouseInsertFails = false

    private val fakeWarehouseRepo = object : WarehouseRepository {
        override fun getByStore(storeId: String) = flowOf(emptyList<Warehouse>())
        override suspend fun getDefault(storeId: String) = Result.Success(null as Warehouse?)
        override suspend fun getById(id: String): Result<Warehouse> =
            if (id in existingWarehouseIds) {
                seedOrder += "Warehouse"
                Result.Success(Warehouse(id, storeId = "s", name = "Warehouse"))
            } else Result.Error(DatabaseException("Not found"))
        override suspend fun insert(warehouse: Warehouse): Result<Unit> {
            seedOrder += "Warehouse"
            return if (warehouseInsertFails) Result.Error(DatabaseException("fail"))
            else { existingWarehouseIds += warehouse.id; Result.Success(Unit) }
        }
        override suspend fun update(warehouse: Warehouse) = Result.Success(Unit)
        override fun getTransfersByWarehouse(warehouseId: String) = flowOf(emptyList<StockTransfer>())
        override suspend fun getTransferById(id: String) = Result.Error(DatabaseException("nf")) as Result<StockTransfer>
        override suspend fun getPendingTransfers() = Result.Success(emptyList<StockTransfer>())
        override suspend fun createTransfer(transfer: StockTransfer) = Result.Success(Unit)
        override suspend fun commitTransfer(transferId: String, confirmedBy: String) = Result.Success(Unit)
        override suspend fun cancelTransfer(transferId: String) = Result.Success(Unit)
        override suspend fun approveTransfer(transferId: String, approvedBy: String) = Result.Success(Unit)
        override suspend fun dispatchTransfer(transferId: String, dispatchedBy: String) = Result.Success(Unit)
        override suspend fun receiveTransfer(transferId: String, receivedBy: String) = Result.Success(Unit)
        override suspend fun getTransfersByStatus(status: StockTransfer.Status) = Result.Success(emptyList<StockTransfer>())
        override suspend fun getRackLocationForProduct(productId: String, warehouseId: String) =
            Result.Success(null as String? to null as String?)
    }

    private val existingAccountIds = mutableSetOf<String>()
    private var accountInsertFails = false

    private val fakeAccountRepo = object : AccountRepository {
        override fun getAll(storeId: String) = flowOf(emptyList<Account>())
        override fun getByType(storeId: String, accountType: AccountType) = flowOf(emptyList<Account>())
        override suspend fun getById(id: String): Result<Account?> =
            if (id in existingAccountIds) {
                seedOrder += "Account"
                Result.Success(Account(id, "1010", "Cash", AccountType.ASSET, "Current Assets",
                    normalBalance = NormalBalance.DEBIT, createdAt = 0, updatedAt = 0))
            } else Result.Success(null)
        override suspend fun getByCode(storeId: String, accountCode: String) = Result.Success(null as Account?)
        override suspend fun getBalance(accountId: String, periodId: String) = Result.Success(null as AccountBalance?)
        override fun getAllBalances(storeId: String, periodId: String) = flowOf(emptyList<AccountBalance>())
        override suspend fun create(account: Account): Result<Unit> {
            seedOrder += "Account"
            return if (accountInsertFails) Result.Error(DatabaseException("fail"))
            else { existingAccountIds += account.id; Result.Success(Unit) }
        }
        override suspend fun update(account: Account) = Result.Success(Unit)
        override suspend fun deactivate(id: String, updatedAt: Long) = Result.Success(Unit)
        override suspend fun isAccountCodeTaken(storeId: String, code: String, excludeId: String?) = Result.Success(false)
        override suspend fun seedDefaultAccounts(accounts: List<Account>) = Result.Success(Unit)
    }

    private val existingPeriodIds = mutableSetOf<String>()
    private var periodInsertFails = false

    private val fakeAccountingPeriodRepo = object : AccountingPeriodRepository {
        override fun getAll(storeId: String) = flowOf(emptyList<AccountingPeriod>())
        override suspend fun getById(id: String): Result<AccountingPeriod?> =
            if (id in existingPeriodIds) {
                seedOrder += "AccountingPeriod"
                Result.Success(AccountingPeriod(id, "Jan 2026", "2026-01-01", "2026-01-31",
                    PeriodStatus.OPEN, "2026-01-01", createdAt = 0, updatedAt = 0))
            } else Result.Success(null)
        override suspend fun getPeriodForDate(storeId: String, date: String) = Result.Success(null as AccountingPeriod?)
        override suspend fun getOpenPeriods(storeId: String) = Result.Success(emptyList<AccountingPeriod>())
        override suspend fun create(period: AccountingPeriod): Result<Unit> {
            seedOrder += "AccountingPeriod"
            return if (periodInsertFails) Result.Error(DatabaseException("fail"))
            else { existingPeriodIds += period.id; Result.Success(Unit) }
        }
        override suspend fun closePeriod(id: String, updatedAt: Long) = Result.Success(Unit)
        override suspend fun lockPeriod(id: String, lockedBy: String, lockedAt: Long) = Result.Success(Unit)
        override suspend fun reopenPeriod(id: String, updatedAt: Long) = Result.Success(Unit)
    }

    private val existingCustomerGroupIds = mutableSetOf<String>()
    private var customerGroupInsertFails = false

    private val fakeCustomerGroupRepo = object : CustomerGroupRepository {
        override fun getAll() = flowOf(emptyList<CustomerGroup>())
        override suspend fun getById(id: String): Result<CustomerGroup> =
            if (id in existingCustomerGroupIds) {
                seedOrder += "CustomerGroup"
                Result.Success(CustomerGroup(id, "Group"))
            } else Result.Error(DatabaseException("Not found"))
        override suspend fun insert(group: CustomerGroup): Result<Unit> {
            seedOrder += "CustomerGroup"
            return if (customerGroupInsertFails) Result.Error(DatabaseException("fail"))
            else { existingCustomerGroupIds += group.id; Result.Success(Unit) }
        }
        override suspend fun update(group: CustomerGroup) = Result.Success(Unit)
        override suspend fun delete(id: String) = Result.Success(Unit)
    }

    private val existingSettings = mutableMapOf<String, String>()
    private var settingsSetFails = false

    private val fakeSettingsRepo = object : SettingsRepository {
        override suspend fun get(key: String): String? = existingSettings[key]
        override suspend fun set(key: String, value: String): Result<Unit> {
            seedOrder += "Setting"
            return if (settingsSetFails) Result.Error(DatabaseException("fail"))
            else { existingSettings[key] = value; Result.Success(Unit) }
        }
        override suspend fun getAll() = existingSettings.toMap()
        override fun observe(key: String) = flowOf(existingSettings[key])
    }

    private val existingCustomRoleIds = mutableSetOf<String>()
    private var customRoleInsertFails = false

    private val fakeRoleRepo = object : RoleRepository {
        override fun getAllCustomRoles() = flowOf(emptyList<CustomRole>())
        override suspend fun getCustomRoleById(id: String): Result<CustomRole> =
            if (id in existingCustomRoleIds) {
                seedOrder += "CustomRole"
                Result.Success(CustomRole(id, "Role", permissions = emptySet(),
                    createdAt = Clock.System.now(), updatedAt = Clock.System.now()))
            } else Result.Error(DatabaseException("Not found"))
        override suspend fun createCustomRole(role: CustomRole): Result<Unit> {
            seedOrder += "CustomRole"
            return if (customRoleInsertFails) Result.Error(DatabaseException("fail"))
            else { existingCustomRoleIds += role.id; Result.Success(Unit) }
        }
        override suspend fun updateCustomRole(role: CustomRole) = Result.Success(Unit)
        override suspend fun deleteCustomRole(id: String) = Result.Success(Unit)
        override suspend fun getBuiltInRolePermissions(role: Role): Set<Permission>? = null
        override suspend fun setBuiltInRolePermissions(role: Role, permissions: Set<Permission>) = Result.Success(Unit)
        override suspend fun resetBuiltInRolePermissions(role: Role) = Result.Success(Unit)
    }

    private val existingLabelTemplateIds = mutableSetOf<String>()
    private var labelTemplateInsertFails = false

    private val fakeLabelTemplateRepo = object : LabelTemplateRepository {
        override fun getAll() = flowOf(emptyList<LabelTemplate>())
        override suspend fun getById(id: String): Result<LabelTemplate> =
            if (id in existingLabelTemplateIds) {
                seedOrder += "LabelTemplate"
                Result.Success(LabelTemplate(id, "Template", LabelTemplate.PaperType.CONTINUOUS_ROLL,
                    58.0, 30.0, 1, 0, 0.0, 3.0, 2.0, 2.0, 2.0, 2.0, createdAt = 0, updatedAt = 0))
            } else Result.Error(DatabaseException("Not found"))
        override suspend fun save(template: LabelTemplate): Result<Unit> {
            seedOrder += "LabelTemplate"
            return if (labelTemplateInsertFails) Result.Error(DatabaseException("fail"))
            else { existingLabelTemplateIds += template.id; Result.Success(Unit) }
        }
        override suspend fun delete(id: String) = Result.Success(Unit)
        override suspend fun count() = existingLabelTemplateIds.size
    }

    private val existingPrinterProfileIds = mutableSetOf<String>()
    private var printerProfileInsertFails = false

    private val fakePrinterProfileRepo = object : PrinterProfileRepository {
        override fun getAll() = flowOf(emptyList<PrinterProfile>())
        override suspend fun getById(id: String): Result<PrinterProfile> =
            if (id in existingPrinterProfileIds) {
                seedOrder += "PrinterProfile"
                Result.Success(PrinterProfile(id, "Profile", PrinterJobType.RECEIPT, "TCP", createdAt = 0, updatedAt = 0))
            } else Result.Error(DatabaseException("Not found"))
        override suspend fun getDefault(jobType: PrinterJobType) = Result.Success(null as PrinterProfile?)
        override suspend fun save(profile: PrinterProfile): Result<Unit> {
            seedOrder += "PrinterProfile"
            return if (printerProfileInsertFails) Result.Error(DatabaseException("fail"))
            else { existingPrinterProfileIds += profile.id; Result.Success(Unit) }
        }
        override suspend fun delete(id: String) = Result.Success(Unit)
    }

    private var featureInitDefaultsCalled = false
    private var featureInitFails = false

    private val fakeFeatureRegistryRepo = object : FeatureRegistryRepository {
        override fun observeAll() = flowOf(emptyList<FeatureConfig>())
        override fun observe(feature: ZyntaFeature) = flowOf(FeatureConfig(feature, true, null, null, 0))
        override suspend fun isEnabled(feature: ZyntaFeature) = true
        override suspend fun setEnabled(feature: ZyntaFeature, enabled: Boolean, activatedAt: Long, expiresAt: Long?) = Result.Success(Unit)
        override suspend fun initDefaults(now: Long): Result<Unit> {
            featureInitDefaultsCalled = true
            seedOrder += "FeatureConfig"
            return if (featureInitFails) Result.Error(DatabaseException("fail"))
            else Result.Success(Unit)
        }
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
        registerRepository = null,
        expenseRepository = fakeExpenseRepo,
        employeeRepository = fakeEmployeeRepo,
        couponRepository = fakeCouponRepo,
        warehouseRepository = fakeWarehouseRepo,
        accountRepository = fakeAccountRepo,
        accountingPeriodRepository = fakeAccountingPeriodRepo,
        customerGroupRepository = fakeCustomerGroupRepo,
        settingsRepository = fakeSettingsRepo,
        roleRepository = fakeRoleRepo,
        labelTemplateRepository = fakeLabelTemplateRepo,
        printerProfileRepository = fakePrinterProfileRepo,
        featureRegistryRepository = fakeFeatureRegistryRepo,
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
        // 11 entity inserts + 23 FeatureConfig defaults = 34 total
        assertEquals(34, summary.totalInserted)
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

        // 7 entity inserts + 23 FeatureConfig defaults = 30 total
        assertEquals(30, first.totalInserted)
        assertEquals(0, first.totalSkipped)

        // Entities skipped on second run; FeatureConfig still reports 23 (idempotent at DB level)
        assertEquals(23, second.totalInserted)
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

    // ════════════════════════════════════════════════════════════════════════
    // Warehouse seeding
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `seedWarehouses inserts all new warehouses`() = runTest {
        val dataSet = SeedDataSet(
            warehouses = listOf(
                SeedWarehouse("wh1", "s1", "Main Floor", isDefault = true),
                SeedWarehouse("wh2", "s1", "Back Storage"),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Warehouse" }
        assertEquals(2, result.inserted)
        assertEquals(0, result.skipped)
        assertEquals(0, result.failed)
    }

    @Test
    fun `seedWarehouses skips existing`() = runTest {
        existingWarehouseIds += "wh1"
        val dataSet = SeedDataSet(
            warehouses = listOf(
                SeedWarehouse("wh1", "s1", "Main"),
                SeedWarehouse("wh2", "s1", "Back"),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Warehouse" }
        assertEquals(1, result.inserted)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `seedWarehouses counts failures`() = runTest {
        warehouseInsertFails = true
        val dataSet = SeedDataSet(
            warehouses = listOf(SeedWarehouse("wh1", "s1", "Main")),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Warehouse" }
        assertEquals(0, result.inserted)
        assertEquals(1, result.failed)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Account (Chart of Accounts) seeding
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `seedAccounts inserts all new accounts`() = runTest {
        val dataSet = SeedDataSet(
            accounts = listOf(
                SeedAccount("a1", "1010", "Cash", "ASSET", "Current Assets", normalBalance = "DEBIT"),
                SeedAccount("a2", "4010", "Sales Revenue", "INCOME", "Revenue", normalBalance = "CREDIT"),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Account" }
        assertEquals(2, result.inserted)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `seedAccounts skips existing`() = runTest {
        existingAccountIds += "a1"
        val dataSet = SeedDataSet(
            accounts = listOf(
                SeedAccount("a1", "1010", "Cash", "ASSET", "Current Assets", normalBalance = "DEBIT"),
                SeedAccount("a2", "4010", "Revenue", "INCOME", "Revenue", normalBalance = "CREDIT"),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Account" }
        assertEquals(1, result.inserted)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `seedAccounts counts failures`() = runTest {
        accountInsertFails = true
        val dataSet = SeedDataSet(
            accounts = listOf(SeedAccount("a1", "1010", "Cash", "ASSET", "Current Assets", normalBalance = "DEBIT")),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Account" }
        assertEquals(1, result.failed)
    }

    // ════════════════════════════════════════════════════════════════════════
    // AccountingPeriod seeding
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `seedAccountingPeriods inserts all new periods`() = runTest {
        val dataSet = SeedDataSet(
            accountingPeriods = listOf(
                SeedAccountingPeriod("p1", "January 2026", "2026-01-01", "2026-01-31", "2026-01-01"),
                SeedAccountingPeriod("p2", "February 2026", "2026-02-01", "2026-02-28", "2026-01-01"),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "AccountingPeriod" }
        assertEquals(2, result.inserted)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `seedAccountingPeriods skips existing`() = runTest {
        existingPeriodIds += "p1"
        val dataSet = SeedDataSet(
            accountingPeriods = listOf(
                SeedAccountingPeriod("p1", "Jan 2026", "2026-01-01", "2026-01-31", "2026-01-01"),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "AccountingPeriod" }
        assertEquals(0, result.inserted)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `seedAccountingPeriods counts failures`() = runTest {
        periodInsertFails = true
        val dataSet = SeedDataSet(
            accountingPeriods = listOf(
                SeedAccountingPeriod("p1", "Jan 2026", "2026-01-01", "2026-01-31", "2026-01-01"),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "AccountingPeriod" }
        assertEquals(1, result.failed)
    }

    // ════════════════════════════════════════════════════════════════════════
    // CustomerGroup seeding
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `seedCustomerGroups inserts all new groups`() = runTest {
        val dataSet = SeedDataSet(
            customerGroups = listOf(
                SeedCustomerGroup("cg1", "VIP", discountType = "PERCENT", discountValue = 5.0),
                SeedCustomerGroup("cg2", "Wholesale", priceType = "WHOLESALE"),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "CustomerGroup" }
        assertEquals(2, result.inserted)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `seedCustomerGroups skips existing`() = runTest {
        existingCustomerGroupIds += "cg1"
        val dataSet = SeedDataSet(
            customerGroups = listOf(SeedCustomerGroup("cg1", "VIP")),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "CustomerGroup" }
        assertEquals(0, result.inserted)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `seedCustomerGroups counts failures`() = runTest {
        customerGroupInsertFails = true
        val dataSet = SeedDataSet(
            customerGroups = listOf(SeedCustomerGroup("cg1", "VIP")),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "CustomerGroup" }
        assertEquals(1, result.failed)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Settings seeding
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `seedSettings inserts all new settings`() = runTest {
        val dataSet = SeedDataSet(
            settings = listOf(
                SeedSetting("store.name", "Demo Store"),
                SeedSetting("store.currency", "LKR"),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Setting" }
        assertEquals(2, result.inserted)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `seedSettings skips existing keys`() = runTest {
        existingSettings["store.name"] = "Already Set"
        val dataSet = SeedDataSet(
            settings = listOf(
                SeedSetting("store.name", "Demo Store"),
                SeedSetting("store.currency", "LKR"),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Setting" }
        assertEquals(1, result.inserted)
        assertEquals(1, result.skipped)
        assertEquals("Already Set", existingSettings["store.name"], "Existing value must not be overwritten")
    }

    @Test
    fun `seedSettings counts failures`() = runTest {
        settingsSetFails = true
        val dataSet = SeedDataSet(
            settings = listOf(SeedSetting("key", "val")),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "Setting" }
        assertEquals(1, result.failed)
    }

    // ════════════════════════════════════════════════════════════════════════
    // CustomRole seeding
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `seedCustomRoles inserts all new roles`() = runTest {
        val dataSet = SeedDataSet(
            customRoles = listOf(
                SeedCustomRole("r1", "Kitchen Staff", permissions = listOf("PROCESS_SALE", "MANAGE_PRODUCTS")),
                SeedCustomRole("r2", "Supervisor", permissions = listOf("VIEW_REPORTS", "MANAGE_CUSTOMERS")),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "CustomRole" }
        assertEquals(2, result.inserted)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `seedCustomRoles skips existing`() = runTest {
        existingCustomRoleIds += "r1"
        val dataSet = SeedDataSet(
            customRoles = listOf(SeedCustomRole("r1", "Kitchen", permissions = listOf("PROCESS_SALE"))),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "CustomRole" }
        assertEquals(0, result.inserted)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `seedCustomRoles counts failures`() = runTest {
        customRoleInsertFails = true
        val dataSet = SeedDataSet(
            customRoles = listOf(SeedCustomRole("r1", "Kitchen", permissions = listOf("PROCESS_SALE"))),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "CustomRole" }
        assertEquals(1, result.failed)
    }

    // ════════════════════════════════════════════════════════════════════════
    // LabelTemplate seeding
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `seedLabelTemplates inserts all new templates`() = runTest {
        val dataSet = SeedDataSet(
            labelTemplates = listOf(
                SeedLabelTemplate("lt1", "58mm Single", paperWidthMm = 58.0, isDefault = true),
                SeedLabelTemplate("lt2", "80mm Two Column", paperWidthMm = 80.0, columns = 2),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "LabelTemplate" }
        assertEquals(2, result.inserted)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `seedLabelTemplates skips existing`() = runTest {
        existingLabelTemplateIds += "lt1"
        val dataSet = SeedDataSet(
            labelTemplates = listOf(SeedLabelTemplate("lt1", "58mm")),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "LabelTemplate" }
        assertEquals(0, result.inserted)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `seedLabelTemplates counts failures`() = runTest {
        labelTemplateInsertFails = true
        val dataSet = SeedDataSet(
            labelTemplates = listOf(SeedLabelTemplate("lt1", "58mm")),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "LabelTemplate" }
        assertEquals(1, result.failed)
    }

    // ════════════════════════════════════════════════════════════════════════
    // PrinterProfile seeding
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `seedPrinterProfiles inserts all new profiles`() = runTest {
        val dataSet = SeedDataSet(
            printerProfiles = listOf(
                SeedPrinterProfile("pp1", "Main Receipt", "RECEIPT", "TCP", tcpHost = "192.168.1.100"),
                SeedPrinterProfile("pp2", "Kitchen", "KITCHEN", "TCP", tcpHost = "192.168.1.101"),
            ),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "PrinterProfile" }
        assertEquals(2, result.inserted)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `seedPrinterProfiles skips existing`() = runTest {
        existingPrinterProfileIds += "pp1"
        val dataSet = SeedDataSet(
            printerProfiles = listOf(SeedPrinterProfile("pp1", "Receipt", "RECEIPT", "TCP")),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "PrinterProfile" }
        assertEquals(0, result.inserted)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `seedPrinterProfiles counts failures`() = runTest {
        printerProfileInsertFails = true
        val dataSet = SeedDataSet(
            printerProfiles = listOf(SeedPrinterProfile("pp1", "Receipt", "RECEIPT", "TCP")),
        )
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "PrinterProfile" }
        assertEquals(1, result.failed)
    }

    // ════════════════════════════════════════════════════════════════════════
    // FeatureConfig seeding (initDefaults)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `seedFeatureDefaults calls initDefaults`() = runTest {
        featureInitDefaultsCalled = false
        val dataSet = SeedDataSet() // empty, but featureRegistry is non-null
        runner().run(dataSet)
        assertTrue(featureInitDefaultsCalled, "initDefaults must be called when repo is available")
    }

    @Test
    fun `seedFeatureDefaults reports 23 inserted on success`() = runTest {
        val dataSet = SeedDataSet()
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "FeatureConfig" }
        assertEquals(23, result.inserted)
        assertEquals(0, result.failed)
    }

    @Test
    fun `seedFeatureDefaults reports failure correctly`() = runTest {
        featureInitFails = true
        val dataSet = SeedDataSet()
        val summary = runner().run(dataSet)
        val result = summary.results.single { it.entityType == "FeatureConfig" }
        assertEquals(0, result.inserted)
        assertEquals(23, result.failed)
    }

    // ════════════════════════════════════════════════════════════════════════
    // FK-safe ordering (extended for new entities)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `run seeds warehouses before products`() = runTest {
        seedOrder.clear()
        val dataSet = SeedDataSet(
            warehouses = listOf(SeedWarehouse("wh1", "s1", "Main")),
            products = listOf(SeedProduct("p1", "Item", categoryId = "c1", price = 1.0)),
        )
        runner().run(dataSet)
        val firstWh = seedOrder.indexOfFirst { it == "Warehouse" }
        val firstProd = seedOrder.indexOfFirst { it == "Product" }
        assertTrue(firstWh < firstProd, "Warehouses must be seeded before products")
    }

    @Test
    fun `run seeds customer groups before products`() = runTest {
        seedOrder.clear()
        val dataSet = SeedDataSet(
            customerGroups = listOf(SeedCustomerGroup("cg1", "VIP")),
            products = listOf(SeedProduct("p1", "Item", categoryId = "c1", price = 1.0)),
        )
        runner().run(dataSet)
        val firstCg = seedOrder.indexOfFirst { it == "CustomerGroup" }
        val firstProd = seedOrder.indexOfFirst { it == "Product" }
        assertTrue(firstCg < firstProd, "CustomerGroups must be seeded before products")
    }

    @Test
    fun `run seeds settings after coupons`() = runTest {
        seedOrder.clear()
        val dataSet = SeedDataSet(
            coupons = listOf(SeedCoupon("c1", "X", "X", "FIXED", 10.0, validFrom = 0, validTo = 1)),
            settings = listOf(SeedSetting("key", "val")),
        )
        runner().run(dataSet)
        val firstCoup = seedOrder.indexOfFirst { it == "Coupon" }
        val firstSetting = seedOrder.indexOfFirst { it == "Setting" }
        assertTrue(firstCoup < firstSetting, "Settings must be seeded after coupons")
    }

    @Test
    fun `run seeds feature config last`() = runTest {
        seedOrder.clear()
        val dataSet = SeedDataSet(
            settings = listOf(SeedSetting("key", "val")),
        )
        runner().run(dataSet)
        val lastSetting = seedOrder.indexOfLast { it == "Setting" }
        val firstFeature = seedOrder.indexOfFirst { it == "FeatureConfig" }
        assertTrue(lastSetting < firstFeature, "FeatureConfig must be seeded after settings")
    }

    // ════════════════════════════════════════════════════════════════════════
    // Null repo graceful degradation (extended)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `null new repositories degrade gracefully`() = runTest {
        val minimalRunner = SeedRunner(
            categoryRepository = fakeCategoryRepo,
            supplierRepository = fakeSupplierRepo,
            productRepository = fakeProductRepo,
            customerRepository = fakeCustomerRepo,
        )
        val dataSet = SeedDataSet(
            categories = listOf(SeedCategory("c1", "Cat")),
            warehouses = listOf(SeedWarehouse("wh1", "s1", "Main")),
            accounts = listOf(SeedAccount("a1", "1010", "Cash", "ASSET", "Current Assets", normalBalance = "DEBIT")),
            accountingPeriods = listOf(SeedAccountingPeriod("p1", "Jan", "2026-01-01", "2026-01-31", "2026-01-01")),
            customerGroups = listOf(SeedCustomerGroup("cg1", "VIP")),
            settings = listOf(SeedSetting("k", "v")),
            customRoles = listOf(SeedCustomRole("r1", "Staff", permissions = listOf("PROCESS_SALE"))),
            labelTemplates = listOf(SeedLabelTemplate("lt1", "58mm")),
            printerProfiles = listOf(SeedPrinterProfile("pp1", "Receipt", "RECEIPT", "TCP")),
        )
        val summary = minimalRunner.run(dataSet)
        assertEquals(1, summary.results.size, "Only categories should be seeded; all new repos are null")
        assertEquals("Category", summary.results[0].entityType)
        assertTrue(summary.isSuccess)
    }

    // ════════════════════════════════════════════════════════════════════════
    // DefaultSeedDataSet validation (extended for new entities)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `DefaultSeedDataSet has expected new entity counts`() {
        val dataSet = DefaultSeedDataSet.build()
        assertEquals(3, dataSet.warehouses.size, "Expected 3 warehouses")
        assertEquals(20, dataSet.accounts.size, "Expected 20 chart of accounts entries")
        assertEquals(6, dataSet.accountingPeriods.size, "Expected 6 accounting periods")
        assertEquals(4, dataSet.customerGroups.size, "Expected 4 customer groups")
        assertEquals(17, dataSet.settings.size, "Expected 17 settings")
        assertEquals(2, dataSet.customRoles.size, "Expected 2 custom roles")
        assertEquals(2, dataSet.labelTemplates.size, "Expected 2 label templates")
        assertEquals(2, dataSet.printerProfiles.size, "Expected 2 printer profiles")
    }

    @Test
    fun `DefaultSeedDataSet all warehouse IDs are unique`() {
        val ids = DefaultSeedDataSet.build().warehouses.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Warehouse IDs must be unique")
    }

    @Test
    fun `DefaultSeedDataSet all account codes are unique`() {
        val codes = DefaultSeedDataSet.build().accounts.map { it.accountCode }
        assertEquals(codes.size, codes.toSet().size, "Account codes must be unique")
    }

    @Test
    fun `DefaultSeedDataSet all account IDs are unique`() {
        val ids = DefaultSeedDataSet.build().accounts.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Account IDs must be unique")
    }

    @Test
    fun `DefaultSeedDataSet all accounting period IDs are unique`() {
        val ids = DefaultSeedDataSet.build().accountingPeriods.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "AccountingPeriod IDs must be unique")
    }

    @Test
    fun `DefaultSeedDataSet accounting periods have valid date ranges`() {
        DefaultSeedDataSet.build().accountingPeriods.forEach { ap ->
            assertTrue(ap.startDate < ap.endDate, "Period '${ap.periodName}' startDate must be before endDate")
        }
    }

    @Test
    fun `DefaultSeedDataSet all customer group IDs are unique`() {
        val ids = DefaultSeedDataSet.build().customerGroups.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "CustomerGroup IDs must be unique")
    }

    @Test
    fun `DefaultSeedDataSet customer groups have non-negative discount values`() {
        DefaultSeedDataSet.build().customerGroups.forEach { cg ->
            assertTrue(cg.discountValue >= 0.0, "CustomerGroup '${cg.name}' has negative discount")
        }
    }

    @Test
    fun `DefaultSeedDataSet all setting keys are unique`() {
        val keys = DefaultSeedDataSet.build().settings.map { it.key }
        assertEquals(keys.size, keys.toSet().size, "Setting keys must be unique")
    }

    @Test
    fun `DefaultSeedDataSet all custom role IDs are unique`() {
        val ids = DefaultSeedDataSet.build().customRoles.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "CustomRole IDs must be unique")
    }

    @Test
    fun `DefaultSeedDataSet custom roles have valid permission names`() {
        val validPermissions = Permission.entries.map { it.name }.toSet()
        DefaultSeedDataSet.build().customRoles.forEach { role ->
            role.permissions.forEach { perm ->
                assertTrue(perm in validPermissions, "CustomRole '${role.name}' has unknown permission '$perm'")
            }
        }
    }

    @Test
    fun `DefaultSeedDataSet all label template IDs are unique`() {
        val ids = DefaultSeedDataSet.build().labelTemplates.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "LabelTemplate IDs must be unique")
    }

    @Test
    fun `DefaultSeedDataSet all printer profile IDs are unique`() {
        val ids = DefaultSeedDataSet.build().printerProfiles.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "PrinterProfile IDs must be unique")
    }

    @Test
    fun `DefaultSeedDataSet contains all six account types`() {
        val types = DefaultSeedDataSet.build().accounts.map { it.accountType }.toSet()
        assertTrue("ASSET" in types, "Expected ASSET accounts")
        assertTrue("LIABILITY" in types, "Expected LIABILITY accounts")
        assertTrue("EQUITY" in types, "Expected EQUITY accounts")
        assertTrue("INCOME" in types, "Expected INCOME accounts")
        assertTrue("COGS" in types, "Expected COGS accounts")
        assertTrue("EXPENSE" in types, "Expected EXPENSE accounts")
    }

    @Test
    fun `DefaultSeedDataSet has at least one default warehouse`() {
        val defaults = DefaultSeedDataSet.build().warehouses.count { it.isDefault }
        assertTrue(defaults >= 1, "Expected at least one default warehouse")
    }
}
