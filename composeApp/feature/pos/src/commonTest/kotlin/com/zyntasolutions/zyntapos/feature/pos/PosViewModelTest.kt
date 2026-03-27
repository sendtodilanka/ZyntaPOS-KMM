package com.zyntasolutions.zyntapos.feature.pos

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import com.zyntasolutions.zyntapos.core.pagination.PageRequest
import com.zyntasolutions.zyntapos.core.pagination.PaginatedResult
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.domain.formatter.ReceiptFormatter
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.Category
import com.zyntasolutions.zyntapos.domain.model.Coupon
import com.zyntasolutions.zyntapos.domain.model.CouponUsage
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.CustomerWallet
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.LoyaltyTier
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.OrderType
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.RewardPoints
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.model.WalletTransaction
import com.zyntasolutions.zyntapos.domain.model.Account
import com.zyntasolutions.zyntapos.domain.model.AccountBalance
import com.zyntasolutions.zyntapos.domain.model.AccountType
import com.zyntasolutions.zyntapos.domain.model.AccountingPeriod
import com.zyntasolutions.zyntapos.domain.model.JournalEntry
import com.zyntasolutions.zyntapos.domain.model.JournalReferenceType
import com.zyntasolutions.zyntapos.domain.printer.A4InvoicePrinterPort
import com.zyntasolutions.zyntapos.domain.printer.ReceiptPrinterPort
import com.zyntasolutions.zyntapos.domain.repository.AccountRepository
import com.zyntasolutions.zyntapos.domain.repository.AccountingPeriodRepository
import com.zyntasolutions.zyntapos.domain.repository.CategoryRepository
import com.zyntasolutions.zyntapos.domain.repository.JournalRepository
import com.zyntasolutions.zyntapos.domain.usecase.accounting.PostSaleJournalEntryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.PrintA4TaxInvoiceUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.ReprintLastReceiptUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateSalesReportUseCase
import com.zyntasolutions.zyntapos.domain.repository.CouponRepository
import com.zyntasolutions.zyntapos.domain.repository.CustomerWalletRepository
import com.zyntasolutions.zyntapos.domain.repository.LoyaltyRepository
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import com.zyntasolutions.zyntapos.domain.repository.StockRepository
import com.zyntasolutions.zyntapos.domain.usecase.auth.CheckPermissionUseCase
import com.zyntasolutions.zyntapos.domain.usecase.coupons.ApplyStorePromotionsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.coupons.CalculateCouponDiscountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.coupons.GetStorePromotionsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.coupons.ValidateCouponUseCase
import com.zyntasolutions.zyntapos.domain.port.EmailPort
import com.zyntasolutions.zyntapos.domain.usecase.pos.LookupOrderForReturnUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.SendReceiptByEmailUseCase
import com.zyntasolutions.zyntapos.domain.usecase.crm.CalculateLoyaltyDiscountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.crm.EarnRewardPointsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.crm.RedeemRewardPointsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.AddItemToCartUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.ApplyItemDiscountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.ApplyOrderDiscountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.CalculateOrderTotalsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.HoldOrderUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.OpenCashDrawerUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.PrintReceiptUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.ProcessPaymentUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.RemoveItemFromCartUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.RetrieveHeldOrderUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.UpdateCartItemQuantityUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.CashMovement
import com.zyntasolutions.zyntapos.domain.model.CashRegister
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.CustomerRepository
import com.zyntasolutions.zyntapos.domain.repository.RegisterRepository
import com.zyntasolutions.zyntapos.domain.repository.StoreRepository
import com.zyntasolutions.zyntapos.domain.model.Store
import com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger
import kotlinx.coroutines.flow.flowOf

// ─────────────────────────────────────────────────────────────────────────────
// PosViewModelTest — Sprint 17 + Sprint 22 extension
// Tests key PosViewModel MVI state transitions using hand-rolled fake
// repositories and real use case instances.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class PosViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val now: Instant = Clock.System.now()

    private val noOpAnalytics = object : AnalyticsTracker {
        override fun logEvent(name: String, params: Map<String, String>) {}
        override fun logScreenView(screenName: String, screenClass: String) {}
        override fun setUserId(userId: String?) {}
        override fun setUserProperty(name: String, value: String) {}
    }

    // ── No-op AuditRepository + SecurityAuditLogger ───────────────────────────

    private val noOpAuditRepository = object : AuditRepository {
        override suspend fun insert(entry: AuditEntry) = Unit
        override fun observeAll(): Flow<List<AuditEntry>> = MutableStateFlow(emptyList())
        override fun observeByUserId(userId: String): Flow<List<AuditEntry>> = MutableStateFlow(emptyList())
        override suspend fun getAllChronological(): List<AuditEntry> = emptyList()
        override suspend fun getLatestHash(): String? = null
        override suspend fun countEntries(): Long = 0L
        override suspend fun getRecentLoginFailureCount(userId: String, sinceEpochMillis: Long): Long = 0L
    }
    private val testAuditLogger = SecurityAuditLogger(noOpAuditRepository, "test-device")

    private val fakeAuthRepository = object : AuthRepository {
        private val _session = MutableStateFlow<User?>(
            User(
                id = "cashier-01", name = "Test Cashier", email = "cashier@zynta.com",
                role = Role.CASHIER, storeId = "store-01", isActive = true,
                pinHash = null, createdAt = Clock.System.now(), updatedAt = Clock.System.now(),
            )
        )
        override fun getSession(): Flow<User?> = _session
        override suspend fun login(email: String, password: String): Result<User> =
            Result.Success(_session.value!!)
        override suspend fun logout() { _session.value = null }
        override suspend fun refreshToken(): Result<Unit> = Result.Success(Unit)
        override suspend fun updatePin(userId: String, pin: String): Result<Unit> =
            Result.Success(Unit)
        override suspend fun validatePin(userId: String, pin: String): Result<Boolean> =
            Result.Success(true)
        override suspend fun quickSwitch(userId: String, pin: String): Result<User> =
            Result.Success(_session.value!!)
        override suspend fun validateManagerPin(pin: String): Result<Boolean> =
            Result.Success(true)
    }

    // ── Fake Repositories ──────────────────────────────────────────────────────

    private val productsMap = mutableMapOf<String, Product>()
    private val productsFlow = MutableStateFlow<List<Product>>(emptyList())

    private val fakeProductRepository = object : ProductRepository {
        override fun getAll(): Flow<List<Product>> = productsFlow

        override suspend fun getById(id: String): Result<Product> {
            return productsMap[id]?.let { Result.Success(it) }
                ?: Result.Error(DatabaseException("Product not found"))
        }

        override fun search(query: String, categoryId: String?): Flow<List<Product>> {
            return productsFlow.map { list ->
                list.filter { p ->
                    (categoryId == null || p.categoryId == categoryId) &&
                        (query.isBlank() || p.name.contains(query, ignoreCase = true))
                }
            }
        }

        override suspend fun getByBarcode(barcode: String): Result<Product> {
            return productsMap.values.firstOrNull { it.barcode == barcode }
                ?.let { Result.Success(it) }
                ?: Result.Error(DatabaseException("Not found"))
        }

        override suspend fun insert(product: Product): Result<Unit> = Result.Success(Unit)
        override suspend fun update(product: Product): Result<Unit> = Result.Success(Unit)
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
        override suspend fun getCount(): Int = productsMap.size
        override suspend fun getPage(pageRequest: PageRequest, categoryId: String?, searchQuery: String?): PaginatedResult<Product> =
            PaginatedResult(items = emptyList(), totalCount = 0L, hasMore = false)
    }

    private val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())

    private val fakeCategoryRepository = object : CategoryRepository {
        override fun getAll(): Flow<List<Category>> = categoriesFlow

        override suspend fun getById(id: String): Result<Category> {
            return categoriesFlow.value.firstOrNull { it.id == id }
                ?.let { Result.Success(it) }
                ?: Result.Error(DatabaseException("Category not found"))
        }

        override suspend fun insert(category: Category): Result<Unit> = Result.Success(Unit)
        override suspend fun update(category: Category): Result<Unit> = Result.Success(Unit)
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
        override fun getTree(): Flow<List<Category>> = categoriesFlow
    }

    private val createdOrders = mutableListOf<Order>()
    private val ordersFlow = MutableStateFlow<List<Order>>(emptyList())

    private val fakeOrderRepository = object : OrderRepository {
        override suspend fun create(order: Order): Result<Order> {
            val saved = order.copy(orderNumber = "ORD-${createdOrders.size + 1}")
            createdOrders.add(saved)
            ordersFlow.value = createdOrders.toList()
            return Result.Success(saved)
        }

        override suspend fun getById(id: String): Result<Order> {
            return ordersFlow.value.firstOrNull { it.id == id }
                ?.let { Result.Success(it) }
                ?: Result.Error(DatabaseException("Order not found"))
        }

        override fun getAll(filters: Map<String, String>): Flow<List<Order>> {
            return ordersFlow.map { orders ->
                orders.filter { order ->
                    filters.all { (key, value) ->
                        when (key) {
                            "status" -> order.status.name == value
                            else -> true
                        }
                    }
                }
            }
        }

        override suspend fun update(order: Order): Result<Unit> = Result.Success(Unit)
        override suspend fun void(id: String, reason: String): Result<Unit> = Result.Success(Unit)

        override fun getByDateRange(from: Instant, to: Instant): Flow<List<Order>> {
            return ordersFlow.map { orders ->
                orders.filter { it.createdAt in from..to }
            }
        }

        override suspend fun holdOrder(cart: List<CartItem>): Result<String> =
            Result.Success("held-order-id")

        override suspend fun retrieveHeld(holdId: String): Result<Order> =
            Result.Error(DatabaseException("Not implemented"))
        override suspend fun getPage(pageRequest: PageRequest, from: Instant?, to: Instant?, customerId: String?): PaginatedResult<Order> =
            PaginatedResult(items = emptyList(), totalCount = 0L, hasMore = false)
    }

    private val fakeStockRepository = object : StockRepository {
        override suspend fun adjustStock(adjustment: StockAdjustment): Result<Unit> =
            Result.Success(Unit)

        override fun getMovements(productId: String): Flow<List<StockAdjustment>> =
            MutableStateFlow(emptyList())

        override fun getAlerts(threshold: Double?): Flow<List<Product>> =
            MutableStateFlow(emptyList())
    }

    private val fakeSettingsRepository = object : SettingsRepository {
        private val store = mutableMapOf<String, String>()
        override suspend fun get(key: String): String? = store[key]
        override suspend fun set(key: String, value: String): Result<Unit> {
            store[key] = value
            return Result.Success(Unit)
        }
        override suspend fun getAll(): Map<String, String> = store.toMap()
        override fun observe(key: String): Flow<String?> = MutableStateFlow(store[key])
    }

    private val fakeReceiptPrinterPort = object : ReceiptPrinterPort {
        override suspend fun print(order: Order, cashierId: String): Result<Unit> =
            Result.Success(Unit)
        override suspend fun openCashDrawer(): Result<Unit> =
            Result.Success(Unit)
    }

    private val fakeA4PrinterPort = object : A4InvoicePrinterPort {
        override suspend fun printA4Invoice(order: Order): Result<Unit> = Result.Success(Unit)
        override suspend fun printA4ZReport(session: RegisterSession): Result<Unit> = Result.Success(Unit)
        override suspend fun printA4SalesReport(report: GenerateSalesReportUseCase.SalesReport): Result<Unit> = Result.Success(Unit)
    }

    // ── Fake accounting repositories ──────────────────────────────────────────

    private val fakeAccountRepository = object : AccountRepository {
        override fun getAll(storeId: String): Flow<List<Account>> = MutableStateFlow(emptyList())
        override fun getByType(storeId: String, accountType: AccountType): Flow<List<Account>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: String): Result<Account?> = Result.Success(null)
        override suspend fun getByCode(storeId: String, accountCode: String): Result<Account?> = Result.Success(null)
        override suspend fun getBalance(accountId: String, periodId: String): Result<AccountBalance?> = Result.Success(null)
        override fun getAllBalances(storeId: String, periodId: String): Flow<List<AccountBalance>> = MutableStateFlow(emptyList())
        override suspend fun create(account: Account): Result<Unit> = Result.Success(Unit)
        override suspend fun update(account: Account): Result<Unit> = Result.Success(Unit)
        override suspend fun deactivate(id: String, updatedAt: Long): Result<Unit> = Result.Success(Unit)
        override suspend fun isAccountCodeTaken(storeId: String, code: String, excludeId: String?): Result<Boolean> = Result.Success(false)
        override suspend fun seedDefaultAccounts(accounts: List<Account>): Result<Unit> = Result.Success(Unit)
    }

    private val fakeAccountingPeriodRepository = object : AccountingPeriodRepository {
        override fun getAll(storeId: String): Flow<List<AccountingPeriod>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: String): Result<AccountingPeriod?> = Result.Success(null)
        override suspend fun getPeriodForDate(storeId: String, date: String): Result<AccountingPeriod?> = Result.Success(null)
        override suspend fun getOpenPeriods(storeId: String): Result<List<AccountingPeriod>> = Result.Success(emptyList())
        override suspend fun create(period: AccountingPeriod): Result<Unit> = Result.Success(Unit)
        override suspend fun closePeriod(id: String, updatedAt: Long): Result<Unit> = Result.Success(Unit)
        override suspend fun lockPeriod(id: String, lockedBy: String, lockedAt: Long): Result<Unit> = Result.Success(Unit)
        override suspend fun reopenPeriod(id: String, updatedAt: Long): Result<Unit> = Result.Success(Unit)
    }

    private val fakeJournalRepository = object : JournalRepository {
        override fun getEntriesByDateRange(storeId: String, fromDate: String, toDate: String): Flow<List<JournalEntry>> = MutableStateFlow(emptyList())
        override fun getUnpostedEntries(storeId: String): Flow<List<JournalEntry>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: String): Result<JournalEntry?> = Result.Success(null)
        override suspend fun getByReference(referenceType: JournalReferenceType, referenceId: String): Result<List<JournalEntry>> = Result.Success(emptyList())
        override suspend fun getNextEntryNumber(storeId: String): Result<Int> = Result.Success(1)
        override suspend fun saveDraftEntry(entry: JournalEntry): Result<Unit> = Result.Success(Unit)
        override suspend fun postEntry(entryId: String, postedAt: Long): Result<Unit> = Result.Success(Unit)
        override suspend fun unpostEntry(entryId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun deleteEntry(entryId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun reverseEntry(originalEntryId: String, reversalDate: String, createdBy: String, now: Long): Result<JournalEntry> =
            Result.Error(DatabaseException("Not supported"))
    }

    // ── Sprint 22 fakes: wallet, loyalty, coupon ───────────────────────────────

    private val walletStore = mutableMapOf<String, CustomerWallet>()  // customerId -> wallet
    private val walletDebits = mutableListOf<Triple<String, Double, String>>() // walletId, amount, orderId

    private val fakeWalletRepository = object : CustomerWalletRepository {
        override suspend fun getOrCreate(customerId: String): Result<CustomerWallet> {
            val existing = walletStore[customerId]
            return if (existing != null) {
                Result.Success(existing)
            } else {
                val w = CustomerWallet(id = "wallet-$customerId", customerId = customerId, balance = 500.0)
                walletStore[customerId] = w
                Result.Success(w)
            }
        }

        override fun observeWallet(customerId: String): Flow<CustomerWallet?> =
            MutableStateFlow(walletStore[customerId])

        override fun getTransactions(walletId: String): Flow<List<WalletTransaction>> =
            MutableStateFlow(emptyList())

        override suspend fun credit(
            walletId: String, amount: Double,
            referenceType: String?, referenceId: String?, note: String?,
        ): Result<Unit> = Result.Success(Unit)

        override suspend fun debit(
            walletId: String, amount: Double,
            referenceType: String?, referenceId: String?, note: String?,
        ): Result<Unit> {
            walletDebits.add(Triple(walletId, amount, referenceId ?: ""))
            val wallet = walletStore.values.find { it.id == walletId } ?: return Result.Success(Unit)
            walletStore[wallet.customerId] = wallet.copy(balance = wallet.balance - amount)
            return Result.Success(Unit)
        }
    }

    private val loyaltyStore = mutableMapOf<String, Int>()  // customerId -> points
    private val earnedPointsLog = mutableListOf<Pair<String, Int>>()  // customerId, points

    private val fakeLoyaltyRepository = object : LoyaltyRepository {
        override fun getPointsHistory(customerId: String): Flow<List<RewardPoints>> =
            MutableStateFlow(emptyList())

        override suspend fun getBalance(customerId: String): Result<Int> =
            Result.Success(loyaltyStore.getOrDefault(customerId, 0))

        override suspend fun recordPoints(entry: RewardPoints): Result<Unit> {
            earnedPointsLog.add(entry.customerId to entry.points)
            loyaltyStore[entry.customerId] = entry.balanceAfter
            return Result.Success(Unit)
        }

        override fun getAllTiers(): Flow<List<LoyaltyTier>> = MutableStateFlow(emptyList())

        override suspend fun getTierForPoints(points: Int): Result<LoyaltyTier?> =
            Result.Success(null)

        override suspend fun saveTier(tier: LoyaltyTier): Result<Unit> = Result.Success(Unit)
        override suspend fun deleteTier(id: String): Result<Unit> = Result.Success(Unit)
        override suspend fun expirePointsForCustomer(customerId: String, nowEpochMillis: Long): Result<Int> =
            Result.Success(0)
    }

    private val couponStore = mutableMapOf<String, Coupon>()  // code -> Coupon

    private val fakeCouponRepository = object : CouponRepository {
        override fun getAll(): Flow<List<Coupon>> = MutableStateFlow(couponStore.values.toList())

        override fun getActiveCoupons(nowEpochMillis: Long): Flow<List<Coupon>> =
            MutableStateFlow(couponStore.values.filter { it.isActive }.toList())

        override suspend fun getByCode(code: String): Result<Coupon> {
            return couponStore[code]?.let { Result.Success(it) }
                ?: Result.Error(DatabaseException("Coupon not found: $code"))
        }

        override suspend fun getById(id: String): Result<Coupon> {
            return couponStore.values.find { it.id == id }?.let { Result.Success(it) }
                ?: Result.Error(DatabaseException("Not found"))
        }

        override suspend fun insert(coupon: Coupon): Result<Unit> {
            couponStore[coupon.code] = coupon
            return Result.Success(Unit)
        }

        override suspend fun update(coupon: Coupon): Result<Unit> {
            couponStore[coupon.code] = coupon
            return Result.Success(Unit)
        }

        override suspend fun toggleActive(id: String, isActive: Boolean): Result<Unit> = Result.Success(Unit)
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
        override suspend fun recordRedemption(usage: CouponUsage): Result<Unit> = Result.Success(Unit)

        override suspend fun getCustomerUsageCount(couponId: String, customerId: String): Result<Int> =
            Result.Success(0)

        override fun getUsageByCoupon(couponId: String): Flow<List<CouponUsage>> =
            MutableStateFlow(emptyList())

        override fun getAllPromotions() = MutableStateFlow(emptyList<com.zyntasolutions.zyntapos.domain.model.Promotion>())
        override fun getActivePromotions(nowEpochMillis: Long) = MutableStateFlow(emptyList<com.zyntasolutions.zyntapos.domain.model.Promotion>())
        override fun getActiveCouponsForStore(nowEpochMillis: Long, storeId: String) = getActiveCoupons(nowEpochMillis)
        override fun getActivePromotionsForStore(nowEpochMillis: Long, storeId: String) = MutableStateFlow(emptyList<com.zyntasolutions.zyntapos.domain.model.Promotion>())
        override suspend fun getPromotionById(id: String): Result<com.zyntasolutions.zyntapos.domain.model.Promotion> = Result.Error(DatabaseException("Not found"))
        override suspend fun insertPromotion(promotion: com.zyntasolutions.zyntapos.domain.model.Promotion) = Result.Success(Unit)
        override suspend fun updatePromotion(promotion: com.zyntasolutions.zyntapos.domain.model.Promotion) = Result.Success(Unit)
        override suspend fun deletePromotion(id: String) = Result.Success(Unit)
        override suspend fun upsertPromotionFromSync(payload: String) = Unit
    }

    // ── Fake CustomerRepository ───────────────────────────────────────────────

    private val fakeCustomerRepository = object : CustomerRepository {
        override fun getAll(): Flow<List<Customer>> = flowOf(emptyList())
        override suspend fun getById(id: String): Result<Customer> =
            Result.Error(DatabaseException("not used"))
        override fun search(query: String): Flow<List<Customer>> = flowOf(emptyList())
        override suspend fun insert(customer: Customer): Result<Unit> = Result.Success(Unit)
        override suspend fun update(customer: Customer): Result<Unit> = Result.Success(Unit)
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
        override fun searchGlobal(query: String): Flow<List<Customer>> = flowOf(emptyList())
        override fun getByStore(storeId: String): Flow<List<Customer>> = flowOf(emptyList())
        override fun getGlobalCustomers(): Flow<List<Customer>> = flowOf(emptyList())
        override suspend fun makeGlobal(customerId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun updateLoyaltyPoints(customerId: String, points: Int): Result<Unit> = Result.Success(Unit)
        override suspend fun getPage(pageRequest: PageRequest, searchQuery: String?): PaginatedResult<Customer> =
            PaginatedResult(items = emptyList(), totalCount = 0L, hasMore = false)
    }

    // ── Fake RegisterRepository ───────────────────────────────────────────────

    private val fakeRegisterRepository = object : RegisterRepository {
        override fun getRegisters(): Flow<List<CashRegister>> = flowOf(emptyList())
        override fun getActive(): Flow<RegisterSession?> = flowOf(null)
        override suspend fun openSession(
            registerId: String,
            openingBalance: Double,
            userId: String,
        ): Result<RegisterSession> = Result.Error(DatabaseException("not used"))
        override suspend fun closeSession(
            sessionId: String,
            actualBalance: Double,
            userId: String,
        ): Result<RegisterSession> = Result.Error(DatabaseException("not used"))
        override suspend fun addCashMovement(movement: CashMovement): Result<Unit> =
            Result.Success(Unit)
        override fun getMovements(sessionId: String): Flow<List<CashMovement>> =
            flowOf(emptyList())
        override suspend fun getSession(sessionId: String): Result<RegisterSession> =
            Result.Error(DatabaseException("not used"))
    }

    // ── Fake StoreRepository ───────────────────────────────────────────────────

    private val fakeStoreRepository = object : StoreRepository {
        override fun getAllStores(): Flow<List<Store>> = flowOf(emptyList())
        override suspend fun getById(storeId: String): Store? = null
        override suspend fun getStoreName(storeId: String): String? = "Test Store"
        override suspend fun upsertFromSync(store: Store) = Unit
    }

    // ── No-op EmailPort ───────────────────────────────────────────────────────

    private val noOpEmailPort = object : EmailPort {
        override suspend fun sendReceiptEmail(to: String, subject: String, pdfBytes: ByteArray) =
            Result.Success(Unit)
    }

    // ── Real use cases wired to fake repositories ──────────────────────────────

    private val calculateTotalsUseCase = CalculateOrderTotalsUseCase()

    private lateinit var viewModel: PosViewModel

    private val testCategory = Category(
        id = "cat-drinks",
        name = "Drinks",
        parentId = null,
        imageUrl = null,
        displayOrder = 0,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        productsMap.clear()
        productsFlow.value = emptyList()
        categoriesFlow.value = listOf(testCategory)
        createdOrders.clear()
        ordersFlow.value = emptyList()
        walletStore.clear()
        walletDebits.clear()
        loyaltyStore.clear()
        earnedPointsLog.clear()
        couponStore.clear()

        val sessionFlow = MutableStateFlow<User?>(null)
        val checkPermissionUseCase = CheckPermissionUseCase(sessionFlow)

        viewModel = PosViewModel(
            productRepository = fakeProductRepository,
            categoryRepository = fakeCategoryRepository,
            orderRepository = fakeOrderRepository,
            addItemUseCase = AddItemToCartUseCase(fakeProductRepository),
            removeItemUseCase = RemoveItemFromCartUseCase(),
            updateQtyUseCase = UpdateCartItemQuantityUseCase(fakeProductRepository),
            applyItemDiscountUseCase = ApplyItemDiscountUseCase(checkPermissionUseCase),
            applyOrderDiscountUseCase = ApplyOrderDiscountUseCase(fakeSettingsRepository, calculateTotalsUseCase),
            calculateTotalsUseCase = calculateTotalsUseCase,
            holdOrderUseCase = HoldOrderUseCase(fakeOrderRepository),
            retrieveHeldUseCase = RetrieveHeldOrderUseCase(fakeOrderRepository),
            processPaymentUseCase = ProcessPaymentUseCase(fakeOrderRepository, fakeStockRepository, calculateTotalsUseCase),
            printReceiptUseCase = PrintReceiptUseCase(fakeReceiptPrinterPort),
            openCashDrawerUseCase = OpenCashDrawerUseCase(fakeReceiptPrinterPort),
            receiptFormatter = ReceiptFormatter(CurrencyFormatter()),
            walletRepository = fakeWalletRepository,
            loyaltyRepository = fakeLoyaltyRepository,
            validateCouponUseCase = ValidateCouponUseCase(fakeCouponRepository),
            calculateCouponDiscountUseCase = CalculateCouponDiscountUseCase(),
            earnRewardPointsUseCase = EarnRewardPointsUseCase(fakeLoyaltyRepository),
            redeemRewardPointsUseCase = RedeemRewardPointsUseCase(fakeLoyaltyRepository),
            calculateLoyaltyDiscountUseCase = CalculateLoyaltyDiscountUseCase(),
            authRepository = fakeAuthRepository,
            storeRepository = fakeStoreRepository,
            customerRepository = fakeCustomerRepository,
            registerRepository = fakeRegisterRepository,
            postSaleJournalEntryUseCase = PostSaleJournalEntryUseCase(
                journalRepository = fakeJournalRepository,
                accountRepository = fakeAccountRepository,
                periodRepository = fakeAccountingPeriodRepository,
            ),
            reprintLastReceiptUseCase = ReprintLastReceiptUseCase(fakeOrderRepository, fakeReceiptPrinterPort),
            printA4TaxInvoiceUseCase = PrintA4TaxInvoiceUseCase(fakeOrderRepository, fakeA4PrinterPort, checkPermissionUseCase),
            lookupOrderForReturnUseCase = LookupOrderForReturnUseCase(fakeOrderRepository),
            getStorePromotionsUseCase = GetStorePromotionsUseCase(fakeCouponRepository),
            applyStorePromotionsUseCase = ApplyStorePromotionsUseCase(),
            settingsRepository = fakeSettingsRepository,
            currencyFormatter = CurrencyFormatter(),
            sendReceiptByEmailUseCase = SendReceiptByEmailUseCase(fakeOrderRepository, fakeSettingsRepository, noOpEmailPort),
            auditLogger = testAuditLogger,
            analytics = noOpAnalytics,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun addTestProduct(
        id: String = "p1",
        name: String = "Test Coffee",
        price: Double = 4.50,
        stockQty: Double = 100.0,
    ): Product {
        val product = Product(
            id = id,
            name = name,
            barcode = "1234567890123",
            sku = "SKU-COFFEE",
            categoryId = "cat-drinks",
            unitId = "unit-01",
            price = price,
            costPrice = 2.00,
            taxGroupId = null,
            stockQty = stockQty,
            minStockQty = 5.0,
            imageUrl = null,
            description = "Test product",
            isActive = true,
            createdAt = now,
            updatedAt = now,
        )
        productsMap[product.id] = product
        productsFlow.value = productsMap.values.toList()
        return product
    }

    private fun buildTestCustomer(id: String = "cust-01") = Customer(
        id = id,
        name = "Alice Smith",
        phone = "+94771234567",
        email = "alice@example.com",
    )

    private fun buildTestCoupon(
        code: String = "SAVE10",
        discountType: DiscountType = DiscountType.FIXED,
        discountValue: Double = 10.0,
        minimumPurchase: Double = 0.0,
    ): Coupon {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        return Coupon(
            id = "coupon-01",
            code = code,
            name = "Test Coupon",
            discountType = discountType,
            discountValue = discountValue,
            minimumPurchase = minimumPurchase,
            validFrom = nowMs - 86_400_000L,
            validTo = nowMs + 86_400_000L,
            isActive = true,
        )
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty cart and zero totals`() {
        val state = viewModel.state.value
        assertTrue(state.cartItems.isEmpty())
        assertEquals(0.0, state.orderTotals.total, 0.001)
        assertEquals(0.0, state.orderTotals.subtotal, 0.001)
        assertNull(state.selectedCustomer)
        assertNull(state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `initial state has no selected category`() {
        assertNull(viewModel.state.value.selectedCategoryId)
    }

    @Test
    fun `init loads store name and cashier name from session`() = runTest {
        advanceUntilIdle()
        val state = viewModel.state.value
        assertEquals("Test Store", state.storeName)
        assertEquals("store-01", state.activeStoreId)
        assertEquals("Test Cashier", state.cashierName)
    }

    @Test
    fun `initial state has no wallet balance and no coupon`() {
        val state = viewModel.state.value
        assertNull(state.walletBalance)
        assertNull(state.loyaltyPointsBalance)
        assertEquals(0.0, state.walletPaymentAmount, 0.001)
        assertNull(state.appliedCoupon)
        assertEquals(0.0, state.couponDiscount, 0.001)
    }

    // ── SearchQueryChanged ────────────────────────────────────────────────────

    @Test
    fun `SearchQueryChanged updates searchQuery state immediately`() = runTest {
        viewModel.dispatch(PosIntent.SearchQueryChanged("coffee"))
        advanceUntilIdle()
        assertEquals("coffee", viewModel.state.value.searchQuery)
    }

    @Test
    fun `SearchQueryChanged to empty string resets searchQuery`() = runTest {
        viewModel.dispatch(PosIntent.SearchQueryChanged("latte"))
        advanceUntilIdle()
        viewModel.dispatch(PosIntent.SearchQueryChanged(""))
        advanceUntilIdle()
        assertEquals("", viewModel.state.value.searchQuery)
    }

    // ── SearchFocusChanged ────────────────────────────────────────────────────

    @Test
    fun `SearchFocusChanged true sets isSearchFocused`() = runTest {
        viewModel.dispatch(PosIntent.SearchFocusChanged(true))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isSearchFocused)
    }

    @Test
    fun `SearchFocusChanged false clears isSearchFocused`() = runTest {
        viewModel.dispatch(PosIntent.SearchFocusChanged(true))
        viewModel.dispatch(PosIntent.SearchFocusChanged(false))
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isSearchFocused)
    }

    // ── SelectCategory ────────────────────────────────────────────────────────

    @Test
    fun `SelectCategory sets selectedCategoryId`() = runTest {
        viewModel.dispatch(PosIntent.SelectCategory("cat-drinks"))
        advanceUntilIdle()
        assertEquals("cat-drinks", viewModel.state.value.selectedCategoryId)
    }

    @Test
    fun `SelectCategory null clears selectedCategoryId`() = runTest {
        viewModel.dispatch(PosIntent.SelectCategory("cat-drinks"))
        viewModel.dispatch(PosIntent.SelectCategory(null))
        advanceUntilIdle()
        assertNull(viewModel.state.value.selectedCategoryId)
    }

    // ── ClearCart ─────────────────────────────────────────────────────────────

    @Test
    fun `ClearCart empties cart and resets totals`() = runTest {
        viewModel.dispatch(PosIntent.ClearCart)
        advanceUntilIdle()
        val state = viewModel.state.value
        assertTrue(state.cartItems.isEmpty())
        assertEquals(0.0, state.orderTotals.total, 0.001)
        assertNull(state.selectedCustomer)
        assertEquals(0.0, state.orderDiscount, 0.001)
    }

    @Test
    fun `ClearCart also resets wallet and coupon fields`() = runTest {
        // Pre-load coupon state
        val coupon = buildTestCoupon()
        couponStore[coupon.code] = coupon
        val product = addTestProduct()
        viewModel.dispatch(PosIntent.AddToCart(product))
        advanceUntilIdle()
        viewModel.dispatch(PosIntent.EnterCouponCode(coupon.code))
        viewModel.dispatch(PosIntent.ValidateCoupon)
        advanceUntilIdle()

        viewModel.dispatch(PosIntent.ClearCart)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.appliedCoupon)
        assertEquals(0.0, state.couponDiscount, 0.001)
        assertEquals("", state.couponCode)
        assertNull(state.walletBalance)
        assertEquals(0.0, state.walletPaymentAmount, 0.001)
    }

    // ── AddToCart — success ───────────────────────────────────────────────────

    @Test
    fun `AddToCart success adds item to cartItems and recalculates totals`() = runTest {
        val product = addTestProduct()

        viewModel.dispatch(PosIntent.AddToCart(product))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, state.cartItems.size)
        assertEquals("p1", state.cartItems[0].productId)
        assertEquals(4.50, state.orderTotals.subtotal, 0.001)
    }

    @Test
    fun `AddToCart failure emits ShowError effect`() = runTest {
        // Product with zero stock triggers OUT_OF_STOCK validation error
        val product = addTestProduct(stockQty = 0.0)

        viewModel.effects.test {
            viewModel.dispatch(PosIntent.AddToCart(product))
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is PosEffect.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── RemoveFromCart ────────────────────────────────────────────────────────

    @Test
    fun `RemoveFromCart removes item from cartItems`() = runTest {
        val product = addTestProduct()

        viewModel.dispatch(PosIntent.AddToCart(product))
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.cartItems.size)

        viewModel.dispatch(PosIntent.RemoveFromCart("p1"))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.cartItems.isEmpty())
    }

    // ── HoldOrder ─────────────────────────────────────────────────────────────

    @Test
    fun `HoldOrder clears active cart on success`() = runTest {
        val product = addTestProduct()

        viewModel.dispatch(PosIntent.AddToCart(product))
        advanceUntilIdle()

        viewModel.dispatch(PosIntent.HoldOrder)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.cartItems.isEmpty(), "Cart must be cleared after successful hold")
    }

    // ── ScanBarcode — not found ───────────────────────────────────────────────

    @Test
    fun `ScanBarcode with unknown barcode emits BarcodeNotFound effect`() = runTest {
        viewModel.effects.test {
            viewModel.dispatch(PosIntent.ScanBarcode("9999999999999"))
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is PosEffect.BarcodeNotFound)
            assertEquals("9999999999999", (effect as PosEffect.BarcodeNotFound).barcode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── ProcessPayment ────────────────────────────────────────────────────────

    @Test
    fun `ProcessPayment success emits receipt or cash drawer effect`() = runTest {
        val product = addTestProduct()

        viewModel.dispatch(PosIntent.AddToCart(product))
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(
                PosIntent.ProcessPayment(
                    method = PaymentMethod.CASH,
                    tendered = 10.0,
                ),
            )
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(
                effect is PosEffect.ShowReceiptScreen ||
                    effect is PosEffect.OpenCashDrawer ||
                    effect is PosEffect.PrintReceipt,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── SetScannerActive ──────────────────────────────────────────────────────

    @Test
    fun `SetScannerActive true sets scannerActive state`() = runTest {
        viewModel.dispatch(PosIntent.SetScannerActive(true))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.scannerActive)
    }

    @Test
    fun `SetScannerActive false clears scannerActive state`() = runTest {
        viewModel.dispatch(PosIntent.SetScannerActive(true))
        viewModel.dispatch(PosIntent.SetScannerActive(false))
        advanceUntilIdle()
        assertFalse(viewModel.state.value.scannerActive)
    }

    // ── Sprint 22: SelectCustomer loads wallet + loyalty ──────────────────────

    @Test
    fun `SelectCustomer loads wallet balance and loyalty points`() = runTest {
        val customer = buildTestCustomer()
        loyaltyStore[customer.id] = 150

        viewModel.dispatch(PosIntent.SelectCustomer(customer))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(customer, state.selectedCustomer)
        assertNotNull(state.walletBalance, "Wallet balance should be loaded after customer selection")
        assertEquals(500.0, state.walletBalance!!, 0.001)
        assertEquals(150, state.loyaltyPointsBalance)
    }

    @Test
    fun `SelectCustomer with zero loyalty points shows 0 balance`() = runTest {
        val customer = buildTestCustomer()
        // loyaltyStore is empty — defaults to 0

        viewModel.dispatch(PosIntent.SelectCustomer(customer))
        advanceUntilIdle()

        assertEquals(0, viewModel.state.value.loyaltyPointsBalance)
    }

    // ── Sprint 22: ClearCustomer resets wallet state ──────────────────────────

    @Test
    fun `ClearCustomer resets selectedCustomer and wallet fields`() = runTest {
        val customer = buildTestCustomer()
        viewModel.dispatch(PosIntent.SelectCustomer(customer))
        advanceUntilIdle()

        viewModel.dispatch(PosIntent.ClearCustomer)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.selectedCustomer)
        assertNull(state.walletBalance)
        assertNull(state.loyaltyPointsBalance)
        assertEquals(0.0, state.walletPaymentAmount, 0.001)
    }

    // ── Sprint 22: SetWalletPaymentAmount ─────────────────────────────────────

    @Test
    fun `SetWalletPaymentAmount updates walletPaymentAmount in state`() = runTest {
        viewModel.dispatch(PosIntent.SetWalletPaymentAmount(75.0))
        advanceUntilIdle()
        assertEquals(75.0, viewModel.state.value.walletPaymentAmount, 0.001)
    }

    @Test
    fun `SetWalletPaymentAmount to zero clears wallet contribution`() = runTest {
        viewModel.dispatch(PosIntent.SetWalletPaymentAmount(50.0))
        viewModel.dispatch(PosIntent.SetWalletPaymentAmount(0.0))
        advanceUntilIdle()
        assertEquals(0.0, viewModel.state.value.walletPaymentAmount, 0.001)
    }

    // ── Sprint 22: EnterCouponCode ────────────────────────────────────────────

    @Test
    fun `EnterCouponCode updates couponCode and clears previous error`() = runTest {
        // Simulate a prior error state
        viewModel.dispatch(PosIntent.EnterCouponCode("BAD"))
        viewModel.dispatch(PosIntent.ValidateCoupon)
        advanceUntilIdle()
        // couponError should be set now

        viewModel.dispatch(PosIntent.EnterCouponCode("SAVE10"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("SAVE10", state.couponCode)
        assertNull(state.couponError, "Entering a new code should clear the previous error")
    }

    // ── Sprint 22: ValidateCoupon — success ───────────────────────────────────

    @Test
    fun `ValidateCoupon with valid code sets appliedCoupon and couponDiscount`() = runTest {
        val product = addTestProduct(price = 100.0)
        viewModel.dispatch(PosIntent.AddToCart(product))
        advanceUntilIdle()

        val coupon = buildTestCoupon(code = "FLAT20", discountType = DiscountType.FIXED, discountValue = 20.0)
        couponStore[coupon.code] = coupon

        viewModel.dispatch(PosIntent.EnterCouponCode("FLAT20"))
        viewModel.dispatch(PosIntent.ValidateCoupon)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.appliedCoupon, "Coupon should be applied after successful validation")
        assertEquals("FLAT20", state.appliedCoupon!!.code)
        assertEquals(20.0, state.couponDiscount, 0.001)
        assertNull(state.couponError)
        assertFalse(state.couponValidating)
    }

    @Test
    fun `ValidateCoupon with percent coupon computes correct discount`() = runTest {
        val product = addTestProduct(price = 200.0)
        viewModel.dispatch(PosIntent.AddToCart(product))
        advanceUntilIdle()

        val coupon = buildTestCoupon(code = "PCT10", discountType = DiscountType.PERCENT, discountValue = 10.0)
        couponStore[coupon.code] = coupon

        viewModel.dispatch(PosIntent.EnterCouponCode("PCT10"))
        viewModel.dispatch(PosIntent.ValidateCoupon)
        advanceUntilIdle()

        // 10% of 200 = 20
        assertEquals(20.0, viewModel.state.value.couponDiscount, 0.001)
    }

    // ── Sprint 22: ValidateCoupon — failure ───────────────────────────────────

    @Test
    fun `ValidateCoupon with unknown code sets couponError`() = runTest {
        viewModel.dispatch(PosIntent.EnterCouponCode("NOSUCHCODE"))
        viewModel.dispatch(PosIntent.ValidateCoupon)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.appliedCoupon)
        assertEquals(0.0, state.couponDiscount, 0.001)
        assertNotNull(state.couponError, "Unknown coupon should produce a couponError")
        assertFalse(state.couponValidating)
    }

    @Test
    fun `ValidateCoupon below minimum purchase sets couponError`() = runTest {
        // Product price = 4.50, coupon minimum = 50 → should fail
        val product = addTestProduct(price = 4.50)
        viewModel.dispatch(PosIntent.AddToCart(product))
        advanceUntilIdle()

        val coupon = buildTestCoupon(code = "BIGBUY", minimumPurchase = 50.0)
        couponStore[coupon.code] = coupon

        viewModel.dispatch(PosIntent.EnterCouponCode("BIGBUY"))
        viewModel.dispatch(PosIntent.ValidateCoupon)
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.couponError)
        assertNull(viewModel.state.value.appliedCoupon)
    }

    @Test
    fun `ValidateCoupon with blank code is a no-op`() = runTest {
        viewModel.dispatch(PosIntent.EnterCouponCode("   "))
        viewModel.dispatch(PosIntent.ValidateCoupon)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.appliedCoupon)
        assertNull(state.couponError)
        assertFalse(state.couponValidating)
    }

    // ── Sprint 22: ClearCoupon ────────────────────────────────────────────────

    @Test
    fun `ClearCoupon resets all coupon fields`() = runTest {
        val product = addTestProduct(price = 100.0)
        viewModel.dispatch(PosIntent.AddToCart(product))
        advanceUntilIdle()

        val coupon = buildTestCoupon()
        couponStore[coupon.code] = coupon
        viewModel.dispatch(PosIntent.EnterCouponCode(coupon.code))
        viewModel.dispatch(PosIntent.ValidateCoupon)
        advanceUntilIdle()

        viewModel.dispatch(PosIntent.ClearCoupon)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.appliedCoupon)
        assertEquals(0.0, state.couponDiscount, 0.001)
        assertEquals("", state.couponCode)
        assertNull(state.couponError)
    }

    // ── Sprint 22: ProcessPayment earns loyalty points ────────────────────────

    @Test
    fun `ProcessPayment with customer earns loyalty points after success`() = runTest {
        val product = addTestProduct(price = 100.0)  // 100 / 10 = 10 points
        val customer = buildTestCustomer()

        viewModel.dispatch(PosIntent.AddToCart(product))
        advanceUntilIdle()
        viewModel.dispatch(PosIntent.SelectCustomer(customer))
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(PosIntent.ProcessPayment(method = PaymentMethod.CASH, tendered = 100.0))
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(earnedPointsLog.isNotEmpty(), "Loyalty points should have been earned after payment")
        val earned = earnedPointsLog.first { it.first == customer.id }
        assertTrue(earned.second > 0, "Earned points must be positive")
    }

    @Test
    fun `ProcessPayment without customer does not earn loyalty points`() = runTest {
        val product = addTestProduct(price = 100.0)
        viewModel.dispatch(PosIntent.AddToCart(product))
        advanceUntilIdle()
        // No customer selected

        viewModel.effects.test {
            viewModel.dispatch(PosIntent.ProcessPayment(method = PaymentMethod.CASH, tendered = 100.0))
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(earnedPointsLog.isEmpty(), "No loyalty points should be earned for walk-in sales")
    }

    // ── Sprint 22: ProcessPayment debits wallet ───────────────────────────────

    @Test
    fun `ProcessPayment with wallet amount debits customer wallet after success`() = runTest {
        val product = addTestProduct(price = 100.0)
        val customer = buildTestCustomer()
        walletStore[customer.id] = CustomerWallet(id = "wallet-cust-01", customerId = customer.id, balance = 200.0)

        viewModel.dispatch(PosIntent.AddToCart(product))
        advanceUntilIdle()
        viewModel.dispatch(PosIntent.SelectCustomer(customer))
        advanceUntilIdle()
        viewModel.dispatch(PosIntent.SetWalletPaymentAmount(50.0))
        advanceUntilIdle()

        viewModel.effects.test {
            // Tendered 50 cash + 50 wallet = 100 total
            viewModel.dispatch(PosIntent.ProcessPayment(method = PaymentMethod.CASH, tendered = 50.0))
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(walletDebits.isNotEmpty(), "Wallet should have been debited after payment")
        val debit = walletDebits.first()
        assertEquals(50.0, debit.second, 0.001)
    }

    @Test
    fun `ProcessPayment with no wallet amount does not debit wallet`() = runTest {
        val product = addTestProduct(price = 10.0)
        val customer = buildTestCustomer()
        walletStore[customer.id] = CustomerWallet(id = "wallet-cust-01", customerId = customer.id, balance = 200.0)

        viewModel.dispatch(PosIntent.AddToCart(product))
        advanceUntilIdle()
        viewModel.dispatch(PosIntent.SelectCustomer(customer))
        advanceUntilIdle()
        // walletPaymentAmount stays 0.0

        viewModel.effects.test {
            viewModel.dispatch(PosIntent.ProcessPayment(method = PaymentMethod.CASH, tendered = 10.0))
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(walletDebits.isEmpty(), "Wallet should NOT be debited when walletPaymentAmount is 0")
    }

    // ── Sprint 22: Coupon discount is included in ProcessPayment totals ───────

    @Test
    fun `ProcessPayment with applied coupon uses combined discount`() = runTest {
        val product = addTestProduct(price = 100.0)
        val coupon = buildTestCoupon(code = "OFF20", discountType = DiscountType.FIXED, discountValue = 20.0)
        couponStore[coupon.code] = coupon

        viewModel.dispatch(PosIntent.AddToCart(product))
        advanceUntilIdle()
        viewModel.dispatch(PosIntent.EnterCouponCode("OFF20"))
        viewModel.dispatch(PosIntent.ValidateCoupon)
        advanceUntilIdle()

        assertEquals(20.0, viewModel.state.value.couponDiscount, 0.001)

        // Effective total = 100 - 20 = 80 → tendering exactly 80 should succeed
        viewModel.effects.test {
            viewModel.dispatch(PosIntent.ProcessPayment(method = PaymentMethod.CASH, tendered = 80.0))
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(
                effect is PosEffect.ShowReceiptScreen ||
                    effect is PosEffect.OpenCashDrawer ||
                    effect is PosEffect.PrintReceipt,
                "Expected success effects but got: $effect",
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
