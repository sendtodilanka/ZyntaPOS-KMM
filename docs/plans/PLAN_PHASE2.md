# ZyntaPOS — Phase 2: Growth Execution Plan

> **Document ID:** ZYNTA-PLAN-PHASE2-v1.0
> **Status:** ✅ IMPLEMENTED (2026-02-24, commit `5672a9a`) — See `docs/ai_workflows/execution_log.md` Phase 2 section for full implementation details and known gaps
> **Scope:** Months 7–12 | Multi-Store, CRM, Promotions, Financial Tools
> **Architecture:** KMP (Android + Desktop JVM)
> **Author:** Senior KMP Architect & Lead Engineer
> **Reference Plans:** ZYNTA-MASTER-PLAN-v1.0 | ZYNTA-ER-DIAGRAM-v1.0 | ZYNTA-UI-UX-PLAN-v1.0 | ZYNTA-PLAN-PHASE1-v1.0

---

## Context

Phase 1 delivered a fully functional single-store POS MVP with offline sync, encrypted DB, RBAC, hardware abstraction, and all core transaction flows. Phase 2 transforms ZyntaPOS from a single-store tool into an enterprise CRM + multi-store platform. The 4 feature modules scaffolded in Phase 1 (`:composeApp:feature:customers`, `:composeApp:feature:coupons`, `:composeApp:feature:expenses`, `:composeApp:feature:multistore`) are ready for full implementation. Reports module is extended with financial and customer analytics. POS is extended with wallet payments, loyalty earn/redeem, coupon application, and customer group pricing.

---

## 1. Phase 2 Scope & Boundaries

### 1.1 In-Scope Modules

| Module ID | Module | Priority | Sprint Target |
|-----------|--------|----------|---------------|
| M13 | `:composeApp:feature:customers` | P0 | 7–9 |
| M14 | `:composeApp:feature:coupons` | P0 | 10–11 |
| M15 | `:composeApp:feature:multistore` | P0 | 12–14 |
| M16 | `:composeApp:feature:expenses` | P0 | 15–17 |
| M12-ext | `:composeApp:feature:reports` (financial + customer) | P1 | 18–19 |
| M03-ext | `:shared:data` (new schema + CRDT upgrades) | P0 | 1–3 |
| M02-ext | `:shared:domain` (new models + use cases) | P0 | 2–4 |
| Navigation | New routes + RBAC nav items | P0 | 5–6 |
| POS-ext | Wallet payment, loyalty, coupon integration | P1 | 22 |
| i18n | Multi-language framework | P2 | 23 |

### 1.2 Out-of-Scope for Phase 2

- Staff Management / Payroll / Attendance (Phase 3)
- Sri Lanka E-Invoicing IRD API (Phase 3)
- Media Manager / product image pipeline (Phase 3)
- System Admin Console (Phase 3)
- Customer Portal (future)
- Rack/warehouse location management (Phase 3)
- Module marketplace

### 1.3 Phase 2 Deliverables

- ✅ Customer CRUD + 360° profile (orders, wallet, loyalty, installments)
- ✅ Customer groups with group-based pricing at POS
- ✅ Customer wallet: fund/deduct/transfer + wallet as POS payment method
- ✅ Loyalty points earn (auto on purchase), redeem (at POS), tier management
- ✅ Credit sales with configurable per-customer limits
- ✅ Installment plans with payment schedule tracking
- ✅ Coupon engine: Flat/Percentage/BOGO, usage limits, validity, scope
- ✅ Promotion engine: BUY_X_GET_Y, Bundle, Flash Sale
- ✅ Coupon application at POS checkout
- ✅ Multi-store dashboard (Admin-only) with cross-store KPIs
- ✅ Store CRUD + warehouse management
- ✅ Inter-store stock transfer wizard
- ✅ Expense log with recurring expenses + receipt capture
- ✅ Expense approval workflow (Accountant role)
- ✅ Financial reports: P&L, Cash Flow, Tax Summary
- ✅ Customer reports: purchase history, loyalty analytics
- ✅ Advanced CRDT: PN-Counter for wallet balance / stock / coupon usage
- ✅ In-app notification system (low-stock, payment due, expiry)
- ✅ Multi-language framework (EN default + SI/TA stubs)
- ✅ Android APK + Desktop JAR (updated)

---

## 2. Module Dependency Graph (Phase 2 Additions)

```
Phase 1 modules (unchanged foundation)
         ↓
:shared:domain (extended — new models + use cases)
         ↓
:shared:data (extended — new .sq files + migrations + CRDT)
         ↓
:composeApp:navigation (extended — new routes + nav items)
         ↓
┌─────────────┬─────────────┬─────────────┬─────────────┐
│ :customers  │  :coupons   │ :multistore │  :expenses  │
│  (M13)      │   (M14)     │   (M15)     │   (M16)     │
└─────────────┴─────────────┴─────────────┴─────────────┘
         ↓ (POS integration sprint 22)
:composeApp:feature:pos (extended)
:composeApp:feature:reports (extended — financial + customer)
```

**Dependency rule:** All Phase 2 feature modules → `:composeApp:core` + `:shared:domain` only. Data access injected via Koin at runtime via `:shared:data`. Never import `:shared:data` directly from feature modules.

---

## 3. Sprint Breakdown (24 Sprints × 1 Week)

| Sprint | Weeks | Module(s) | Key Goal |
|--------|-------|-----------|----------|
| 1 | W01 | `:shared:data` | CRM SQLDelight schema: customers, wallets, loyalty |
| 2 | W02 | `:shared:data` | Coupons + Promotions + Expenses SQLDelight schema |
| 3 | W03 | `:shared:data` | Multi-store schema: warehouses, stock_transfers, notifications + migrations |
| 4 | W04 | `:shared:domain` | New domain models: CRM + Coupon + Expense group |
| 5 | W05 | `:shared:domain` | New domain models: Multi-Store + new use case interfaces |
| 6 | W06 | `:shared:data` | CRM repository implementations |
| 7 | W07 | `:shared:data` | Coupons + Expenses + Multi-Store repository implementations |
| 8 | W08 | `:composeApp:navigation` | New routes + nav items + MainNavScreens extensions |
| 9 | W09 | `:composeApp:feature:customers` (Part 1) | CustomerListScreen + CustomerDetailScreen + MVI |
| 10 | W10 | `:composeApp:feature:customers` (Part 2) | Wallet, Loyalty, Installment sub-screens |
| 11 | W11 | `:composeApp:feature:customers` (Part 3) | Customer groups, GDPR export, POS quick-add |
| 12 | W12 | `:composeApp:feature:coupons` (Part 1) | CouponListScreen + CouponDetailScreen + MVI |
| 13 | W13 | `:composeApp:feature:coupons` (Part 2) | Promotion rule engine + BOGO/Bundle/Flash sale |
| 14 | W14 | `:composeApp:feature:multistore` (Part 1) | MultistoreListScreen + StoreDetailScreen |
| 15 | W15 | `:composeApp:feature:multistore` (Part 2) | StockTransfer wizard + Warehouse management |
| 16 | W16 | `:composeApp:feature:multistore` (Part 3) | Central KPI dashboard + comparison charts |
| 17 | W17 | `:composeApp:feature:expenses` (Part 1) | ExpenseListScreen + ExpenseFormScreen |
| 18 | W18 | `:composeApp:feature:expenses` (Part 2) | Recurring expenses + approval workflow |
| 19 | W19 | `:composeApp:feature:reports` extension | Financial reports: P&L, Cash Flow, Tax Summary |
| 20 | W20 | `:composeApp:feature:reports` extension | Customer reports + loyalty analytics |
| 21 | W21 | Advanced CRDT + Notifications | PN-Counter sync, conflict log UI, notification center |
| 22 | W22 | `:composeApp:feature:pos` extension | Wallet payment, loyalty earn/redeem, coupon at POS |
| 23 | W23 | i18n + Settings extensions | Multi-language framework, currency locale, language picker |
| 24 | W24 | Integration QA & Release | E2E tests, performance validation, APK/JAR packaging |

---

## 4. Step-by-Step Execution Plan

---

### Sprint 1: CRM SQLDelight Schema

**Goal:** Add all CRM tables to the encrypted local database.

**New files to create:**

`shared/data/src/commonMain/sqldelight/com/zyntasolutions/zyntapos/db/customer_groups.sq`
```sql
CREATE TABLE IF NOT EXISTS customer_groups (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    discount_type TEXT,        -- 'FLAT', 'PERCENTAGE', NULL
    discount_value REAL DEFAULT 0.0,
    price_type TEXT DEFAULT 'RETAIL',  -- 'RETAIL', 'WHOLESALE', 'CUSTOM'
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT,
    sync_id TEXT NOT NULL,
    sync_version INTEGER NOT NULL DEFAULT 1,
    sync_status TEXT NOT NULL DEFAULT 'PENDING'
);
```

`shared/data/src/commonMain/sqldelight/.../customer_addresses.sq`
`shared/data/src/commonMain/sqldelight/.../customer_wallets.sq`
`shared/data/src/commonMain/sqldelight/.../wallet_transactions.sq`
`shared/data/src/commonMain/sqldelight/.../installment_plans.sq`
`shared/data/src/commonMain/sqldelight/.../installment_payments.sq`
`shared/data/src/commonMain/sqldelight/.../reward_points.sq`
`shared/data/src/commonMain/sqldelight/.../loyalty_tiers.sq`

**Extend existing `customers.sq`** (already present as stub): Add missing columns:
- `group_id TEXT REFERENCES customer_groups(id)`
- `credit_limit REAL DEFAULT 0.0`
- `credit_enabled INTEGER DEFAULT 0`
- `gender TEXT`
- `birthday TEXT`
- `is_walk_in INTEGER DEFAULT 0`
- `store_id TEXT`
- All sync metadata columns

**Tasks:**
- 1.1 Write all 8 CRM `.sq` files with complete DDL, indexes, and queries
- 1.2 Add `customers_fts` FTS5 virtual table (like `products_fts`) with triggers on `INSERT/UPDATE/DELETE`
- 1.3 Create `shared/data/src/commonMain/.../data/local/migration/DatabaseMigrations.kt` with migration 1→2
- 1.4 Run `./gradlew generateSqlDelightInterface` to verify schema compiles

---

### Sprint 2: Coupons + Expenses SQLDelight Schema

**New files:**
- `coupons.sq` — coupons, coupon_usage, promotions, customer_coupons tables
- `expense_categories.sq` — self-referencing hierarchy
- `expenses.sq` — expenses + recurring_expenses tables

**Tasks:**
- 2.1 Write coupons.sq: coupons table with `idx_coupons_code` index, coupon_usage, promotions, customer_coupons
- 2.2 Write expense_categories.sq with parent_id self-ref
- 2.3 Write expenses.sq with recurring_expenses table
- 2.4 Update migration to version 3
- 2.5 Verify: `./gradlew :shared:data:generateCommonMainZyntaPosDatabaseInterface`

---

### Sprint 3: Multi-Store Schema + Migrations

**New files:**
- `warehouses.sq` — warehouses table
- `stock_transfers.sq` — inter-store transfer records
- `notifications.sq` — in-app notification queue
- `sync_state.sq` — per-entity sync cursor
- `conflict_log.sq` — CRDT conflict audit trail

**Tasks:**
- 3.1 Write warehouses.sq + stock_transfers.sq
- 3.2 Write notifications.sq with `is_read` flag + indexes
- 3.3 Write sync_state.sq + conflict_log.sq
- 3.4 Finalize migration chain: version 1→2→3→4 (numbered `.sqm` files)
- 3.5 Add migration test: `./gradlew :shared:data:verifySqlDelightMigration`
- 3.6 Update `DatabaseMigrations.kt` to apply all migrations in sequence

---

### Sprint 4: New Domain Models — CRM + Coupons + Expenses

**Location:** `shared/domain/src/commonMain/kotlin/.../domain/model/`

**New domain model files (ADR-002 compliant — no *Entity suffix):**

```
CustomerGroup.kt        — id, name, description, discountType, discountValue, priceType
CustomerAddress.kt      — id, customerId, label, addressLine1, city, postalCode, isDefault
CustomerWallet.kt       — id, customerId, balance
WalletTransaction.kt    — id, walletId, type(CREDIT/DEBIT/REFUND), amount, balanceAfter, referenceType, referenceId
InstallmentPlan.kt      — id, orderId, customerId, totalAmount, paidAmount, remaining, numInstallments, frequency, startDate, status
InstallmentPayment.kt   — id, planId, dueDate, amount, paidAmount, paidAt, status, paymentId
RewardPoints.kt         — id, customerId, points, balanceAfter, type(EARNED/REDEEMED/EXPIRED), referenceType, referenceId, expiresAt
LoyaltyTier.kt          — id, name, minPoints, discountPercent, pointsMultiplier, benefits: List<String>
Coupon.kt               — id, code, name, discountType, discountValue, minimumPurchase, maximumDiscount, usageLimit, usageCount, perCustomerLimit, scope, scopeIds, validFrom, validTo, isActive
CouponUsage.kt          — id, couponId, orderId, customerId, discountAmount, usedAt
Promotion.kt            — id, name, type(BUY_X_GET_Y/BUNDLE/FLASH_SALE), config: PromotionConfig, validFrom, validTo, priority, isActive
ExpenseCategory.kt      — id, name, description, parentId
Expense.kt              — id, storeId, categoryId, amount, description, expenseDate, receiptUrl, isRecurring, status(PENDING/APPROVED/REJECTED)
RecurringExpense.kt     — id, storeId, categoryId, amount, description, frequency(DAILY/WEEKLY/MONTHLY), startDate, endDate, isActive
```

**Extend existing Customer.kt:** Add groupId, creditLimit, creditEnabled, gender, birthday, isWalkIn, storeId

**New enum types:**
```
WalletTransactionType.kt    — CREDIT, DEBIT, REFUND
RewardPointType.kt          — EARNED, REDEEMED, EXPIRED, ADJUSTED
InstallmentStatus.kt        — ACTIVE, COMPLETED, DEFAULTED
InstallmentFrequency.kt     — WEEKLY, BIWEEKLY, MONTHLY
CouponScope.kt              — CART, PRODUCT, CATEGORY, CUSTOMER
PromotionType.kt            — BUY_X_GET_Y, BUNDLE, FLASH_SALE, SCHEDULED
ExpenseStatus.kt            — PENDING, APPROVED, REJECTED
```

**Tasks:**
- 4.1 Create all 14 domain model files
- 4.2 Add new enum types
- 4.3 Extend Customer.kt with new fields
- 4.4 Update `shared/seed/.../SeedRunner.kt` to seed customer groups and sample loyalty tiers
- 4.5 Verify: `./gradlew :shared:domain:test`

---

### Sprint 5: New Domain Models — Multi-Store + Use Case Interfaces

**New domain model files:**

```
Warehouse.kt            — id, storeId, name, managerId, isActive, isDefault
StockTransfer.kt        — id, sourceWarehouseId, destWarehouseId, productId, quantity, status, notes, transferredAt
Notification.kt         — id, type, title, message, channel, recipientType, recipientId, isRead, createdAt
Store.kt (extend)       — Add warehouses: List<Warehouse>, managerName fields
FinancialSummary.kt     — period, totalRevenue, totalExpenses, grossProfit, netProfit, taxCollected
CashFlowEntry.kt        — date, inflow, outflow, netFlow, runningBalance
```

**New Repository interfaces in `shared/domain/src/commonMain/.../domain/repository/`:**

```kotlin
interface CustomerGroupRepository
interface CustomerWalletRepository
interface LoyaltyRepository  // reward_points + loyalty_tiers
interface InstallmentRepository
interface CouponRepository
interface PromotionRepository
interface ExpenseRepository
interface ExpenseCategoryRepository
interface WarehouseRepository
interface StockTransferRepository
interface NotificationRepository
interface FinancialReportRepository
```

**New Use Case interfaces per functional group:**

CRM (15 use cases):
- `GetCustomersUseCase`, `SaveCustomerUseCase`, `DeleteCustomerUseCase`
- `GetCustomerGroupsUseCase`, `SaveCustomerGroupUseCase`
- `AddWalletFundsUseCase`, `DeductWalletFundsUseCase`, `GetWalletHistoryUseCase`
- `EarnRewardPointsUseCase`, `RedeemRewardPointsUseCase`, `GetLoyaltyTiersUseCase`, `SaveLoyaltyTierUseCase`
- `CreateInstallmentPlanUseCase`, `ProcessInstallmentPaymentUseCase`, `GetInstallmentPlansUseCase`
- `ExportCustomerDataUseCase` (GDPR)

Coupons (7 use cases):
- `GetCouponsUseCase`, `SaveCouponUseCase`, `DeleteCouponUseCase`
- `ValidateCouponUseCase` — returns `Result<CouponValidationResult, CouponError>`
- `ApplyCouponToOrderUseCase`, `GetCouponUsageUseCase`
- `GetPromotionsUseCase`, `SavePromotionUseCase`

Multi-Store (8 use cases):
- `GetStoresUseCase`, `SaveStoreUseCase`, `SwitchActiveStoreUseCase`
- `GetMultiStoreKpiUseCase`
- `GetWarehousesUseCase`, `SaveWarehouseUseCase`
- `InitiateStockTransferUseCase`, `GetStockTransferHistoryUseCase`

Expenses (8 use cases):
- `GetExpensesUseCase`, `SaveExpenseUseCase`, `DeleteExpenseUseCase`
- `ApproveExpenseUseCase`, `RejectExpenseUseCase`
- `GetExpenseCategoriesUseCase`, `SaveExpenseCategoryUseCase`
- `GetRecurringExpensesUseCase`, `SaveRecurringExpenseUseCase`

Financial Reports (5 use cases):
- `GenerateProfitLossReportUseCase`
- `GenerateCashFlowReportUseCase`
- `GenerateTaxSummaryReportUseCase`
- `GenerateCustomerReportUseCase`
- `GetCustomerPurchaseHistoryUseCase`

**Tasks:**
- 5.1 Create all new repository interfaces
- 5.2 Write all use case interfaces (SAM-compatible `fun interface` pattern, no defaults per ADR C.2)
- 5.3 Create all multi-store + financial domain models
- 5.4 Write `ValidateCouponUseCase` with typed error: `sealed class CouponError { Expired, UsageLimitReached, MinimumPurchaseNotMet, NotApplicable, NotFound }`
- 5.5 Verify: `./gradlew :shared:domain:test`

---

### Sprint 6: CRM Repository Implementations

**Location:** `shared/data/src/commonMain/kotlin/.../data/repository/`

**New files:**
- `CustomerRepositoryImpl.kt` — extend existing stub with full CRUD + FTS5 search + group linking
- `CustomerGroupRepositoryImpl.kt`
- `CustomerWalletRepositoryImpl.kt` — atomic balance update with `wallet_transactions` insert
- `LoyaltyRepositoryImpl.kt` — reward_points ledger + tier computation
- `InstallmentRepositoryImpl.kt`

**New mapper files** in `shared/data/.../data/local/mapper/`:
- `CustomerMapper.kt` (extend existing), `CustomerGroupMapper.kt`, `WalletMapper.kt`, `LoyaltyMapper.kt`, `InstallmentMapper.kt`

**Critical patterns:**
- Wallet balance update MUST be atomic: use SQLDelight transaction `{ db.wallet.updateBalance(); db.walletTransactions.insert() }`
- Loyalty tier assignment: computed in `LoyaltyRepositoryImpl.getCurrentTier(points)` using sorted `loyalty_tiers` query
- Customer FTS5: `SELECT * FROM customers_fts WHERE customers_fts MATCH ?` — use same pattern as products_fts

**Tasks:**
- 6.1 Implement CustomerRepositoryImpl with FTS5 search
- 6.2 Implement CustomerWalletRepositoryImpl with atomic transactions
- 6.3 Implement LoyaltyRepositoryImpl with tier auto-assignment
- 6.4 Implement all CRM use cases in `shared/domain/.../domain/usecase/crm/`
- 6.5 Unit tests: `./gradlew :shared:domain:test` — 95% coverage for wallet operations

---

### Sprint 7: Coupons + Expenses + Multi-Store Repository Implementations

**New files:**
- `CouponRepositoryImpl.kt` — code lookup by index, usage count increment (atomic)
- `PromotionRepositoryImpl.kt`
- `ExpenseRepositoryImpl.kt` — status workflow (PENDING→APPROVED/REJECTED)
- `ExpenseCategoryRepositoryImpl.kt`
- `RecurringExpenseRepositoryImpl.kt`
- `WarehouseRepositoryImpl.kt`
- `StockTransferRepositoryImpl.kt`

**Implement all use cases:**
- `ValidateCouponUseCaseImpl.kt` — validation pipeline: active check → date range → usage limits → min purchase → scope check
- `ApplyCouponToOrderUseCaseImpl.kt` — calculates discount, creates `coupon_usage` record
- All expense CRUD use cases
- All stock transfer use cases

**Tasks:**
- 7.1 Implement all 7 repository implementations
- 7.2 Implement `ValidateCouponUseCaseImpl` with all 5 error cases
- 7.3 Implement `PromotionRuleEngine.kt` in `:shared:domain` — evaluates BOGO/Bundle/Flash rules from JSON config
- 7.4 Update `DataModule.kt` Koin bindings with all new repository singletons
- 7.5 Tests: coupon validation, stock transfer use cases — 95% coverage

---

### Sprint 8: Navigation Extensions

**Files to modify:**

`composeApp/navigation/src/commonMain/kotlin/.../navigation/ZyntaRoute.kt` — Add:
```kotlin
// Customers
data object CustomerList : ZyntaRoute()
data class CustomerDetail(val customerId: String? = null) : ZyntaRoute()
data class CustomerWallet(val customerId: String) : ZyntaRoute()

// Coupons
data object CouponList : ZyntaRoute()
data class CouponDetail(val couponId: String? = null) : ZyntaRoute()

// Expenses
data object ExpenseList : ZyntaRoute()
data class ExpenseDetail(val expenseId: String? = null) : ZyntaRoute()

// Multi-Store (Admin only)
data object MultiStoreDashboard : ZyntaRoute()
data class StoreDetail(val storeId: String) : ZyntaRoute()
data class WarehouseDetail(val warehouseId: String) : ZyntaRoute()
data class StockTransferWizard(val sourceWarehouseId: String) : ZyntaRoute()
```

`composeApp/navigation/src/commonMain/.../NavigationItems.kt` — Add nav items:
```kotlin
NavItem(CustomerList, "Customers", Icons.Default.People, requiredPermission = Permission.MANAGE_CUSTOMERS)
NavItem(CouponList,   "Coupons",   Icons.Default.LocalOffer, requiredPermission = Permission.MANAGE_COUPONS)
NavItem(ExpenseList,  "Expenses",  Icons.Default.Receipt, requiredPermission = Permission.VIEW_EXPENSES)
NavItem(MultiStoreDashboard, "Stores", Icons.Default.Store, requiredPermission = Permission.MANAGE_STORES)
```

`composeApp/navigation/src/commonMain/.../MainNavGraph.kt` — Add sub-graphs for all Phase 2 modules.

`composeApp/navigation/src/commonMain/.../MainNavScreens.kt` — Add composable factories for all new screens.

**New Permissions to add in `shared/domain/.../domain/model/Permission.kt`:**
- `MANAGE_CUSTOMERS`, `VIEW_CUSTOMERS`
- `MANAGE_COUPONS`
- `VIEW_EXPENSES`, `MANAGE_EXPENSES`, `APPROVE_EXPENSES`
- `MANAGE_STORES`, `VIEW_ALL_STORES`

**Update RBAC role-permission mappings** in `shared/security/.../security/RbacEngine.kt`.

**Tasks:**
- 8.1 Add all Phase 2 routes to ZyntaRoute.kt
- 8.2 Add Permission enum entries + update RbacEngine role maps
- 8.3 Add nav items to NavigationItems.kt
- 8.4 Wire Phase 2 sub-graphs in MainNavGraph.kt
- 8.5 Add screen factories to MainNavScreens.kt
- 8.6 Update Koin `navigationModule` if needed

---

### Sprints 9–11: `:composeApp:feature:customers`

**Location:** `composeApp/feature/customers/src/commonMain/kotlin/.../feature/customers/`

**MVI Contract:**

```kotlin
// State
data class CustomersState(
    val customers: List<Customer> = emptyList(),
    val searchQuery: String = "",
    val selectedCustomer: Customer? = null,
    val customerGroups: List<CustomerGroup> = emptyList(),
    val wallet: CustomerWallet? = null,
    val walletHistory: List<WalletTransaction> = emptyList(),
    val loyaltyPoints: Int = 0,
    val currentTier: LoyaltyTier? = null,
    val loyaltyTiers: List<LoyaltyTier> = emptyList(),
    val rewardHistory: List<RewardPoints> = emptyList(),
    val installmentPlans: List<InstallmentPlan> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

// Intent (sealed interface)
sealed interface CustomersIntent {
    // List
    data class SearchQueryChanged(val query: String) : CustomersIntent
    data object LoadCustomers : CustomersIntent
    // CRUD
    data class SelectCustomer(val customerId: String) : CustomersIntent
    data class SaveCustomer(val customer: Customer) : CustomersIntent
    data class DeleteCustomer(val customerId: String) : CustomersIntent
    data object NewCustomer : CustomersIntent
    data object BackToList : CustomersIntent
    // Wallet
    data class AddWalletFunds(val amount: Double, val reason: String) : CustomersIntent
    data class DeductWalletFunds(val amount: Double, val reason: String) : CustomersIntent
    // Loyalty
    data class RedeemPoints(val points: Int, val orderId: String) : CustomersIntent
    data class SaveLoyaltyTier(val tier: LoyaltyTier) : CustomersIntent
    // Groups
    data class SaveCustomerGroup(val group: CustomerGroup) : CustomersIntent
    // GDPR
    data class ExportCustomerData(val customerId: String) : CustomersIntent
    data object DismissError : CustomersIntent
}

// Effect (sealed interface)
sealed interface CustomersEffect {
    data class ShowError(val message: String) : CustomersEffect
    data class ShowSuccess(val message: String) : CustomersEffect
    data class ExportReady(val filePath: String) : CustomersEffect
    data object NavigateBack : CustomersEffect
}
```

**Screen files to create:**
- `CustomerListScreen.kt` — searchable table with columns: Name, Phone, Group, Balance, Points, Last Purchase
- `CustomerDetailScreen.kt` — 360° profile with tabs: Overview, Orders, Wallet, Loyalty, Notes
- `CustomerFormScreen.kt` — Create/edit form (name, phone, email, group, credit limit)
- `CustomerWalletScreen.kt` — Balance display + Add/Deduct/History
- `CustomerLoyaltyScreen.kt` — Points ledger + tier display + manual redemption
- `LoyaltyTierManagementScreen.kt` — Tier CRUD (Admin only)
- `CustomerGroupListScreen.kt` + `CustomerGroupDetailScreen.kt`
- `InstallmentPlansScreen.kt` — Active plans with payment schedule

**DI module** `composeApp/feature/customers/src/commonMain/.../customers/di/CustomersModule.kt`:
```kotlin
val customersModule = module {
    single<CustomerRepository> { get<CustomerRepositoryImpl>() }
    single<CustomerWalletRepository> { get<CustomerWalletRepositoryImpl>() }
    single<LoyaltyRepository> { get<LoyaltyRepositoryImpl>() }
    single { GetCustomersUseCase(get()) }
    single { SaveCustomerUseCase(get()) }
    single { AddWalletFundsUseCase(get()) }
    single { DeductWalletFundsUseCase(get()) }
    single { EarnRewardPointsUseCase(get()) }
    single { RedeemRewardPointsUseCase(get()) }
    single { GetLoyaltyTiersUseCase(get()) }
    single { ExportCustomerDataUseCase(get()) }
    viewModel { CustomersViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
}
```

**Tests:** `composeApp/feature/customers/src/commonTest/` — CustomersViewModelTest using Turbine

---

### Sprints 12–13: `:composeApp:feature:coupons`

**MVI Contract:**

```kotlin
data class CouponsState(
    val coupons: List<Coupon> = emptyList(),
    val promotions: List<Promotion> = emptyList(),
    val selectedCoupon: Coupon? = null,
    val statusFilter: CouponStatusFilter = CouponStatusFilter.ALL,
    val formState: CouponFormState = CouponFormState(),
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface CouponsIntent {
    data object LoadCoupons : CouponsIntent
    data class FilterByStatus(val filter: CouponStatusFilter) : CouponsIntent
    data class SelectCoupon(val couponId: String) : CouponsIntent
    data class SaveCoupon(val coupon: Coupon) : CouponsIntent
    data class DeleteCoupon(val couponId: String) : CouponsIntent
    data class ToggleActive(val couponId: String, val active: Boolean) : CouponsIntent
    data class SavePromotion(val promotion: Promotion) : CouponsIntent
    data object NewCoupon : CouponsIntent
    data object BackToList : CouponsIntent
    data object DismissError : CouponsIntent
}

sealed interface CouponsEffect {
    data class ShowError(val message: String) : CouponsEffect
    data class ShowSuccess(val message: String) : CouponsEffect
}
```

**Screen files:**
- `CouponListScreen.kt` — Table with status filter tabs: All / Active / Scheduled / Expired
- `CouponDetailScreen.kt` — Form + live preview of coupon summary card
- `PromotionListScreen.kt` — Promotion CRUD (BOGO, Bundle, Flash Sale)
- `CouponUsageReportScreen.kt` — Usage analytics per coupon

**DI module** `composeApp/feature/coupons/src/commonMain/.../coupons/di/CouponsModule.kt`

**Critical — ValidateCouponUseCase integration:** Shared between `:feature:coupons` (management) and `:feature:pos` (application). Keep use case in `:shared:domain` and inject in both modules.

---

### Sprints 14–16: `:composeApp:feature:multistore`

**MVI Contract:**

```kotlin
data class MultistoreState(
    val stores: List<Store> = emptyList(),
    val selectedStore: Store? = null,
    val warehouses: List<Warehouse> = emptyList(),
    val pendingTransfers: List<StockTransfer> = emptyList(),
    val multiStoreKpi: MultiStoreKpi? = null,
    val transferWizardState: TransferWizardState = TransferWizardState(),
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface MultistoreIntent {
    data object LoadStores : MultistoreIntent
    data class SelectStore(val storeId: String) : MultistoreIntent
    data class SaveStore(val store: Store) : MultistoreIntent
    data class SwitchActiveStore(val storeId: String) : MultistoreIntent
    // Warehouses
    data class LoadWarehouses(val storeId: String) : MultistoreIntent
    data class SaveWarehouse(val warehouse: Warehouse) : MultistoreIntent
    // Stock Transfer
    data class StartTransfer(val sourceWarehouseId: String) : MultistoreIntent
    data class SelectTransferProduct(val productId: String, val qty: Int) : MultistoreIntent
    data class SetTransferDestination(val destWarehouseId: String) : MultistoreIntent
    data object ConfirmTransfer : MultistoreIntent
    data object CancelTransfer : MultistoreIntent
    data object DismissError : MultistoreIntent
}
```

**Screen files:**
- `MultistoreListScreen.kt` — Store cards grid with KPIs (Revenue, Orders, Stock Health, Status badge)
- `StoreDetailScreen.kt` — Tabbed: Overview KPIs + chart, Inventory snapshot, Transfers
- `StoreFormScreen.kt` — Create/edit store (Admin only)
- `WarehouseListScreen.kt` + `WarehouseDetailScreen.kt`
- `StockTransferWizardScreen.kt` — 3-step: Source warehouse → Product + qty → Destination → Confirm
- `MultiStoreKpiDashboardScreen.kt` — Comparison chart with multi-line overlay

**RBAC:** All multistore screens check `Permission.MANAGE_STORES` — Admin role only.

---

### Sprints 17–18: `:composeApp:feature:expenses`

**MVI Contract:**

```kotlin
data class ExpensesState(
    val expenses: List<Expense> = emptyList(),
    val categories: List<ExpenseCategory> = emptyList(),
    val recurringExpenses: List<RecurringExpense> = emptyList(),
    val selectedExpense: Expense? = null,
    val statusFilter: ExpenseStatus? = null,
    val dateRange: DateRange = DateRange.thisMonth(),
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface ExpensesIntent {
    data object LoadExpenses : ExpensesIntent
    data class FilterByStatus(val status: ExpenseStatus?) : ExpensesIntent
    data class ChangeDateRange(val range: DateRange) : ExpensesIntent
    data class SelectExpense(val expenseId: String) : ExpensesIntent
    data class SaveExpense(val expense: Expense) : ExpensesIntent
    data class DeleteExpense(val expenseId: String) : ExpensesIntent
    data class ApproveExpense(val expenseId: String) : ExpensesIntent
    data class RejectExpense(val expenseId: String, val reason: String) : ExpensesIntent
    data class SaveRecurringExpense(val recurring: RecurringExpense) : ExpensesIntent
    data class SaveCategory(val category: ExpenseCategory) : ExpensesIntent
    data object NewExpense : ExpensesIntent
    data object DismissError : ExpensesIntent
}
```

**Screen files:**
- `ExpenseListScreen.kt` — Table: Date, Category, Description, Amount, Status chip
- `ExpenseFormScreen.kt` — Form with category picker, amount, receipt photo upload, recurrence toggle
- `ExpenseCategoryScreen.kt` — Hierarchical tree (Chart of Accounts)
- `RecurringExpenseListScreen.kt`
- `FinancialStatementsScreen.kt` — P&L, Cash Flow, Balance Sheet tabs (populated in Sprint 19)

---

### Sprints 19–20: `:composeApp:feature:reports` Extension

**New report types to add to existing `ReportsViewModel`:**

Financial Reports (Sprint 19):
- `GenerateProfitLossReportUseCase` — aggregates orders revenue - expenses per period
- `GenerateCashFlowReportUseCase` — daily inflow (payments) + outflow (cash movements + expenses)
- `GenerateTaxSummaryReportUseCase` — tax collected grouped by tax_group

Customer Reports (Sprint 20):
- `GenerateCustomerReportUseCase` — customer list with lifetime value, order count, last purchase
- `GetCustomerPurchaseHistoryUseCase` — per-customer order timeline

**New screen files:**
- `ProfitLossReportScreen.kt`
- `CashFlowReportScreen.kt`
- `TaxSummaryReportScreen.kt`
- `CustomerAnalyticsScreen.kt`
- `CustomerPurchaseHistoryScreen.kt`

**Reuse existing:** `ZyntaDatePicker`, `ZyntaTable`, `ZyntaSalesChart`, `ZyntaKpiCard` from `:composeApp:designsystem`

---

### Sprint 21: Advanced CRDT + Notification System

**CRDT Upgrades** in `shared/data/src/commonMain/.../data/sync/`:

| Entity | CRDT Type | Implementation |
|--------|-----------|----------------|
| `customer_wallets.balance` | PN-Counter | Track +/- transactions separately; merge by summing |
| `reward_points` (balance) | Ledger (append-only) | Never update, only insert; balance = SUM(points) |
| `coupons.usage_count` | PN-Counter | Atomic increment with version check |
| `stock_entries.quantity` | PN-Counter | Track adjustments as deltas; merge = sum of deltas |
| `expenses` | LWW Register | Last-write-wins per field with timestamp |
| `settings` | LWW Register | Server wins on conflict |

**`ConflictResolver.kt`** (extend existing in `:shared:data`): Add case handling for all new entity types.

**`SyncEngine.kt`** upgrade: Use `sync_state` table per entity_type for cursor-based incremental sync.

**In-App Notification System:**

New class `NotificationManager.kt` in `:shared:data`:
- Background check for: low stock (stock < threshold), expiring products (within 7 days), installment payments due (within 3 days)
- Inserts `Notification` records to `notifications` table
- Emits `Flow<List<Notification>>` for UI consumption

New `NotificationCenterScreen.kt` in `:composeApp:feature:dashboard` (or via bottom sheet from app bar bell icon).

---

### Sprint 22: POS Extension — Wallet / Loyalty / Coupons

**Files to modify:**

`composeApp/feature/pos/src/commonMain/.../pos/mvi/PosIntent.kt` — Add:
```kotlin
data class ApplyCouponCode(val code: String) : PosIntent
data object RemoveCoupon : PosIntent
data class PayWithWallet(val customerId: String, val amount: Double) : PosIntent
data object EarnLoyaltyPoints : PosIntent  // auto-triggered post-payment
```

`composeApp/feature/pos/src/commonMain/.../pos/mvi/PosState.kt` — Add:
```kotlin
val appliedCoupon: Coupon? = null,
val couponDiscount: Double = 0.0,
val couponValidationError: String? = null,
val walletBalance: Double? = null,  // shown when customer selected + has wallet
val loyaltyPointsEarnable: Int = 0,  // preview before payment
```

**New POS UI components:**
- `CouponInputDialog.kt` — Code entry field + "Apply" button → shows validation result inline
- `WalletPaymentPanel.kt` — Shows balance, amount to charge from wallet, remaining due
- `LoyaltyPointsEarnPreview.kt` — Small chip showing "Earn X points" above PAY button

**POS Module update** `composeApp/feature/pos/.../pos/di/PosModule.kt` — Add:
```kotlin
single { ValidateCouponUseCase(get()) }
single { ApplyCouponToOrderUseCase(get()) }
single { GetCustomerWalletUseCase(get()) }
single { EarnRewardPointsUseCase(get()) }
```

**Customer group pricing:** In `PosViewModel`, when `SelectCustomer` intent fires, re-price all cart items if customer's group has a discount. Uses `CalculateOrderTotalsUseCase` with `customerGroupDiscount` parameter.

---

### Sprint 23: Multi-Language Framework

**New infrastructure in `:shared:core`:**

`shared/core/src/commonMain/kotlin/.../core/i18n/LocalizationManager.kt`:
```kotlin
object LocalizationManager {
    val supportedLanguages = listOf("en", "si", "ta")
    fun getString(key: String, lang: String = currentLanguage): String
    var currentLanguage: String // persisted to settings table
}
```

**String resource files (JSON):**
- `shared/core/src/commonMain/resources/strings_en.json` — English (full)
- `shared/core/src/commonMain/resources/strings_si.json` — Sinhala (stub → Phase 3 full)
- `shared/core/src/commonMain/resources/strings_ta.json` — Tamil (stub → Phase 3 full)

**Currency locale formatting:** Update `CurrencyFormatter` in `:shared:core` to accept `locale: String` param. Pull locale from active store's `currency`/`timezone` settings.

**Language selector:** Add to `composeApp/feature/settings/.../settings/screen/AppearanceSettingsScreen.kt` — Language dropdown (EN / SI / TA).

**Settings persistence:** Store language preference in `settings` table with `key = "app_language"`.

---

### Sprint 24: Integration QA & Release

**Tasks:**
- 24.1 End-to-end test: Customer wallet payment flow (POS → wallet deducted → transaction logged)
- 24.2 End-to-end test: Coupon applied at POS → usage_count incremented → coupon_usage record created
- 24.3 End-to-end test: Inter-store stock transfer (source warehouse stock decremented, dest incremented)
- 24.4 End-to-end test: Expense created → Accountant approves → appears in P&L report
- 24.5 Performance validation: customer FTS5 search < 200ms on 10K records
- 24.6 CRDT merge test: Concurrent wallet updates from 2 devices → no balance inconsistency
- 24.7 `./gradlew clean test lint detekt --parallel --continue`
- 24.8 Update `version.properties`: VERSION_NAME=1.1.0, BUILD=2
- 24.9 Build Android APK + Desktop distributable
- 24.10 Update `docs/ai_workflows/execution_log.md` with Phase 2 completion entries

---

## 5. Database Schema Summary — New SQLDelight Files

| File | Tables | Phase |
|------|--------|-------|
| `customer_groups.sq` | customer_groups | 2 |
| `customers.sq` (extend) | customers + customers_fts | 2 |
| `customer_addresses.sq` | customer_addresses | 2 |
| `customer_wallets.sq` | customer_wallets, wallet_transactions | 2 |
| `installment_plans.sq` | installment_plans, installment_payments | 2 |
| `reward_points.sq` | reward_points | 2 |
| `loyalty_tiers.sq` | loyalty_tiers | 2 |
| `coupons.sq` | coupons, coupon_usage, promotions, customer_coupons | 2 |
| `expense_categories.sq` | expense_categories | 2 |
| `expenses.sq` | expenses, recurring_expenses | 2 |
| `warehouses.sq` | warehouses | 2 |
| `stock_transfers.sq` | stock_transfers | 2 |
| `notifications.sq` | notifications | 2 |
| `sync_state.sq` | sync_state | 2 |
| `conflict_log.sq` | conflict_log | 2 |

**Migration strategy:**
- Phase 1 DB = version 1
- Each sprint group adds a numbered migration: `1.sqm`, `2.sqm`, `3.sqm`, `4.sqm`
- `DatabaseMigrations.kt` applies migrations sequentially on app start
- Test: `./gradlew verifySqlDelightMigration` after each sprint

---

## 6. Koin Module Loading Order Update

Phase 1 loading order (unchanged tiers 1–7), Phase 2 adds:

**Tier 7 additions** (feature modules):
```kotlin
// In ZyntaApplication.kt — add to existing feature modules list:
customersModule,
couponsModule,
multistoreModule,
expensesModule,
```

**New data bindings in `DataModule.kt`** (Tier 5):
```kotlin
// CRM
single<CustomerGroupRepository> { CustomerGroupRepositoryImpl(get()) }
single<CustomerWalletRepository> { CustomerWalletRepositoryImpl(get()) }
single<LoyaltyRepository> { LoyaltyRepositoryImpl(get()) }
single<InstallmentRepository> { InstallmentRepositoryImpl(get()) }
// Coupons
single<CouponRepository> { CouponRepositoryImpl(get()) }
single<PromotionRepository> { PromotionRepositoryImpl(get()) }
// Expenses
single<ExpenseRepository> { ExpenseRepositoryImpl(get()) }
single<ExpenseCategoryRepository> { ExpenseCategoryRepositoryImpl(get()) }
// Multi-Store
single<WarehouseRepository> { WarehouseRepositoryImpl(get()) }
single<StockTransferRepository> { StockTransferRepositoryImpl(get()) }
single<NotificationRepository> { NotificationRepositoryImpl(get()) }
```

---

## 7. Critical File Paths

| What | Path |
|------|------|
| New SQLDelight schemas | `shared/data/src/commonMain/sqldelight/com/zyntasolutions/zyntapos/db/` |
| DB migrations | `shared/data/src/commonMain/sqldelight/com/zyntasolutions/zyntapos/db/migrations/` |
| New domain models | `shared/domain/src/commonMain/kotlin/.../domain/model/` |
| New repository interfaces | `shared/domain/src/commonMain/kotlin/.../domain/repository/` |
| New use cases | `shared/domain/src/commonMain/kotlin/.../domain/usecase/` |
| New repository impls | `shared/data/src/commonMain/kotlin/.../data/repository/` |
| CRDT/Sync upgrades | `shared/data/src/commonMain/kotlin/.../data/sync/` |
| Navigation additions | `composeApp/navigation/src/commonMain/kotlin/.../navigation/` |
| Customers feature | `composeApp/feature/customers/src/commonMain/kotlin/.../feature/customers/` |
| Coupons feature | `composeApp/feature/coupons/src/commonMain/kotlin/.../feature/coupons/` |
| Multistore feature | `composeApp/feature/multistore/src/commonMain/kotlin/.../feature/multistore/` |
| Expenses feature | `composeApp/feature/expenses/src/commonMain/kotlin/.../feature/expenses/` |
| POS extensions | `composeApp/feature/pos/src/commonMain/kotlin/.../feature/pos/` |
| Reports extensions | `composeApp/feature/reports/src/commonMain/kotlin/.../feature/reports/` |
| i18n resources | `shared/core/src/commonMain/resources/` |
| LocalizationManager | `shared/core/src/commonMain/kotlin/.../core/i18n/LocalizationManager.kt` |
| ZyntaRoute additions | `composeApp/navigation/src/commonMain/kotlin/.../navigation/ZyntaRoute.kt` |
| MainNavGraph additions | `composeApp/navigation/src/commonMain/kotlin/.../navigation/MainNavGraph.kt` |
| MainNavScreens additions | `composeApp/navigation/src/commonMain/kotlin/.../navigation/MainNavScreens.kt` |
| NavigationItems additions | `composeApp/navigation/src/commonMain/kotlin/.../navigation/NavigationItems.kt` |
| DataModule Koin bindings | `shared/data/src/commonMain/kotlin/.../data/di/DataModule.kt` |

---

## 8. Testing Strategy

| Layer | Coverage Target | Tools |
|-------|----------------|-------|
| Wallet use cases | 95% | Kotlin Test + Mockative 3 |
| Coupon validation engine | 95% | Kotlin Test + Mockative 3 |
| Installment calculations | 90% | Kotlin Test |
| Promotion rule engine | 90% | Kotlin Test |
| Multi-store stock transfer | 90% | Kotlin Test |
| Financial report aggregation | 95% | Kotlin Test |
| Customer FTS5 search | 85% | jvmTest + in-memory SQLDelight |
| ViewModels (all Phase 2) | 80% | Turbine + Koin-test |

**Test file locations:**
- Domain use case tests: `shared/domain/src/commonTest/kotlin/.../usecase/`
- Repository integration tests: `shared/data/src/jvmTest/kotlin/.../repository/`
- ViewModel tests: `composeApp/feature/{feature}/src/commonTest/`

---

## 9. Performance Targets

| Metric | Target |
|--------|--------|
| Customer FTS5 search | < 200ms on 10K customers |
| Coupon validation | < 50ms (local DB lookup) |
| Wallet balance update | < 100ms (atomic transaction) |
| P&L report generation | < 2s for 6-month period |
| Multi-store KPI dashboard | < 3s cold load (parallel queries) |
| Stock transfer confirmation | < 1s (2 stock updates + 1 transfer record) |
| Notification check (background) | < 500ms per cycle |

---

## 10. Risk Register

| # | Risk | Probability | Impact | Mitigation |
|---|------|------------|--------|------------|
| R1 | Wallet PN-Counter conflicts on offline concurrent updates | Medium | High | Atomic SQLDelight transactions; CRDT merge on sync; balance never goes below 0 check |
| R2 | Coupon usage_count race condition (2 terminals same time) | Medium | Medium | Optimistic locking: check usage_count before insert; server-authoritative on conflict |
| R3 | Multi-store stock transfer leaves inconsistent state | Low | High | Two-phase: PENDING transfer record first, then COMMITTED after both warehouses update |
| R4 | Expense approval workflow — offline approvals conflict | Low | Medium | LWW with approver timestamp; server approval overrides local |
| R5 | customer_fts FTS5 triggers slow on large bulk import | Medium | Medium | Disable triggers during bulk import; rebuild FTS index after |
| R6 | i18n string keys missing in SI/TA files → crash | Medium | Low | Fallback to EN string if key missing in LocalizationManager.getString() |
| R7 | Navigation back-stack grows on multistore drilldown | Low | Medium | Use `saveState=true` + `restoreState=true` on sub-graphs |

---

## 11. Definition of Done (Phase 2)

**Each sprint is DONE when:**
- [ ] All new `.sq` files compile (`./gradlew generateSqlDelightInterface`)
- [ ] All migrations pass (`./gradlew verifySqlDelightMigration`)
- [ ] Domain model files created (plain names, no *Entity — ADR-002)
- [ ] Use case interfaces + implementations written
- [ ] Koin bindings registered in DataModule / feature DI module
- [ ] Unit tests written and passing (coverage targets met)
- [ ] `./gradlew detekt` passes (no new violations)
- [ ] All ViewModels extend `BaseViewModel<S,I,E>` (ADR-001)
- [ ] Feature module DI declared in `di/<Feature>Module.kt`

**Phase 2 is DONE when:**
- [ ] All 4 new feature modules fully implemented (Customers, Coupons, Multistore, Expenses)
- [ ] Reports extended with 5 new report types
- [ ] POS extended with coupon, wallet, loyalty integrations
- [ ] `./gradlew clean test lint detekt --parallel --continue` passes
- [ ] APK and Desktop JAR built successfully
- [ ] Performance targets validated
- [ ] E2E flows tested manually: wallet payment, coupon application, stock transfer, expense approval
- [ ] `version.properties` bumped to 1.1.0 / BUILD=2
- [ ] `docs/ai_workflows/execution_log.md` updated

---

## 12. Commit Strategy

Follow Conventional Commits per CLAUDE.md. Phase 2 scope tags:

```
feat(customers): implement customer wallet with PN-Counter CRDT
feat(coupons): add ValidateCouponUseCase with 5 typed error cases
feat(multistore): add inter-store stock transfer wizard
feat(expenses): implement recurring expense scheduler
feat(pos): integrate coupon application and wallet payment at checkout
feat(reports): add P&L and cash flow financial reports
feat(navigation): add Phase 2 routes and RBAC nav items
feat(data): add CRM SQLDelight schema with customers_fts FTS5
feat(i18n): add LocalizationManager with EN/SI/TA support
build(gradle): bump version to 1.1.0 for Phase 2 release
```

---

## 13. Branch Strategy

All Phase 2 development on: `claude/phase-one-planning-vvA0k`

Commit step-by-step per sprint. If file too large to commit in one shot, commit in logical chunks:
1. Schema files (`.sq` + migrations)
2. Domain models
3. Repository interfaces + use cases
4. Repository implementations
5. Feature UI (screen by screen)
6. DI modules + navigation wiring
7. Tests
