# ZyntaPOS — AI Execution Log
> **Doc ID:** ZENTA-EXEC-LOG-v1.1
> **Architecture:** KMP — Desktop (JVM) + Android
> **Strategy:** Clean Architecture · MVI · Koin · SQLDelight · Compose Multiplatform
> **Log Created:** 2026-02-20 | **Last Updated:** 2026-02-20
> **Reference Plan:** `docs/plans/PLAN_PHASE1.md`
> **Status:** 🟡 PHASE 1 PENDING EXECUTION
>
> ---
> **📌 CANONICAL NAMESPACE (FIX-14.01):**
> Root Package: `com.zyntasolutions.zyntapos`
> All Sprint 2–24 file paths use `com/zyntasolutions/zyntapos/` (updated by NS-6 from original `com/zentapos/`)
>
> **📌 SESSION NOTE (FIX-14.02):**
> `composeHotReload = "1.0.0"` is present in `libs.versions.toml` as an undocumented
> addition (not in the original plan). It is retained for desktop hot-reload DX support.

---

## 📌 EXECUTION STATUS LEGEND
- `[ ]` Not Started
- `[~]` In Progress
- `[x]` Completed
- `[!]` Blocked/Issue

---

## 📊 Project Audit Summary (2026-02-20)

### Current State (Baseline)
- **Project scaffold:** KMP skeleton with single `:composeApp` module (JVM + Android)
- **Kotlin:** 2.3.0 | **Compose Multiplatform:** 1.10.0 | **Material3:** 1.10.0-alpha05
- **Missing:** All shared modules (`:shared:core`, `:shared:domain`, `:shared:data`, `:shared:hal`, `:shared:security`)
- **Missing:** All feature modules (auth, pos, inventory, register, reports, settings)
- **Missing:** Koin DI, SQLDelight, Ktor, kotlinx-datetime, Kermit, Coil, SQLCipher
- **Missing:** MVI base infrastructure, Design System, Navigation graph
- **Git:** Initialized and committed

### Module Gap Analysis
| Required Module | Status |
|----------------|--------|
| `:shared:core` | ❌ Not created |
| `:shared:domain` | ❌ Not created |
| `:shared:data` | ❌ Not created |
| `:shared:hal` | ❌ Not created |
| `:shared:security` | ❌ Not created |
| `:composeApp:designsystem` | ❌ Not created |
| `:composeApp:navigation` | ❌ Not created |
| `:composeApp:feature:auth` | ❌ Not created |
| `:composeApp:feature:pos` | ❌ Not created |
| `:composeApp:feature:inventory` | ❌ Not created |
| `:composeApp:feature:register` | ❌ Not created |
| `:composeApp:feature:reports` | ❌ Not created |
| `:composeApp:feature:settings` | ❌ Not created |


---

## ═══════════════════════════════════════════
## PHASE 0 — PROJECT FOUNDATION & TOOLCHAIN
## ═══════════════════════════════════════════
> **Goal:** Harden build system, add all dependencies, create directory scaffold, configure CI skeleton
> **Status:** 🟢 COMPLETE

### P0.1 — Build System & Dependency Catalog
- [x] P0.1.1 — Upgrade `libs.versions.toml`: add Koin 4.0+, SQLDelight 2.0+, Ktor 3.0+, SQLCipher, Kermit, Coil 3.0+, kotlinx-datetime 0.6+, kotlinx-serialization 1.7+, Mockative | 2026-02-20
- [x] P0.1.2 — Update root `build.gradle.kts`: add SQLDelight Gradle plugin, kotlinx-serialization plugin | 2026-02-20
- [x] P0.1.3 — Update `gradle.properties`: enable Gradle Build Cache, parallel builds, configure memory (Xmx4g) | 2026-02-20
- [x] P0.1.4 — Update `settings.gradle.kts`: register all new modules (`:shared:core`, `:shared:domain`, `:shared:data`, `:shared:hal`, `:shared:security`, all `:composeApp:*` feature modules) | 2026-02-20

### P0.2 — Directory Scaffold Creation
- [x] P0.2.1 — Create `:shared:core` Gradle module with `commonMain/androidMain/jvmMain` source sets | 2026-02-20
- [x] P0.2.2 — Create `:shared:domain` Gradle module with `commonMain` source set | 2026-02-20
- [x] P0.2.3 — Create `:shared:data` Gradle module with `commonMain/androidMain/jvmMain` source sets + SQLDelight config | 2026-02-20
- [x] P0.2.4 — Create `:shared:hal` Gradle module with `commonMain/androidMain/jvmMain` source sets (expect/actual) | 2026-02-20
- [x] P0.2.5 — Create `:shared:security` Gradle module with `commonMain/androidMain/jvmMain` source sets | 2026-02-20
- [x] P0.2.6 — Create `:composeApp:designsystem` Gradle module | 2026-02-20
- [x] P0.2.7 — Create `:composeApp:navigation` Gradle module | 2026-02-20
- [x] P0.2.8 — Create `docs/` full hierarchy: `docs/architecture/`, `docs/api/`, `docs/compliance/`, `docs/ai_workflows/` | 2026-02-20

### P0.3 — Baseline Config & Security
- [x] P0.3.1 — Configure `local.properties` with API key placeholders; add Secrets Gradle Plugin wiring | 2026-02-20
- [x] P0.3.2 — Update `.gitignore`: exclude `local.properties`, `*.jks`, `*.keystore`, build outputs | 2026-02-20
- [x] P0.3.3 — Create `README.md` with architecture overview, setup guide, and module map | 2026-02-20
- [x] P0.3.4 — Verify full project sync and clean build succeeds (both Android + JVM targets) | 2026-02-20 — BUILD SUCCESSFUL in 12s (63 tasks: assembleDebug ✅ jvmJar ✅)

### P0.4 — Hotfixes Applied
- [x] FIX.1 — Add `androidKmpLibrary` plugin alias to `libs.versions.toml` | 2026-02-20
- [x] FIX.2 — Create `:androidApp` module (isolate `com.android.application`) | 2026-02-20
- [x] FIX.3 — Update `settings.gradle.kts`: include `:androidApp` | 2026-02-20
- [x] FIX.4 — Refactor `:composeApp/build.gradle.kts`: drop app plugin, fix compose accessors | 2026-02-20
- [x] FIX.5 — Shrink `:composeApp/androidMain/AndroidManifest.xml` to library manifest | 2026-02-20
- [x] FIX.6 — Fix `androidLibrary` → `androidKmpLibrary` + compose accessors in 6 library modules | 2026-02-20

### P0.5 — Feature Module Scaffold (FIX-01)
- [x] FIX-01.01 — Create `composeApp/feature/auth/` + build.gradle.kts + AuthModule.kt stub | 2026-02-20
- [x] FIX-01.02 — Create `composeApp/feature/pos/` + build.gradle.kts + PosModule.kt stub | 2026-02-20
- [x] FIX-01.03 — Create `composeApp/feature/inventory/` + build.gradle.kts + InventoryModule.kt stub | 2026-02-20
- [x] FIX-01.04 — Create `composeApp/feature/register/` + build.gradle.kts + RegisterModule.kt stub | 2026-02-20
- [x] FIX-01.05 — Create `composeApp/feature/reports/` + build.gradle.kts + ReportsModule.kt stub | 2026-02-20
- [x] FIX-01.06 — Create `composeApp/feature/settings/` + build.gradle.kts + SettingsModule.kt stub | 2026-02-20
- [x] FIX-01.07 — Create `composeApp/feature/customers/` + build.gradle.kts + CustomersModule.kt stub | 2026-02-20
- [x] FIX-01.08 — Create `composeApp/feature/coupons/` + build.gradle.kts + CouponsModule.kt stub | 2026-02-20
- [x] FIX-01.09 — Create `composeApp/feature/expenses/` + build.gradle.kts + ExpensesModule.kt stub | 2026-02-20
- [x] FIX-01.10 — Create `composeApp/feature/staff/` + build.gradle.kts + StaffModule.kt stub | 2026-02-20
- [x] FIX-01.11 — Create `composeApp/feature/multistore/` + build.gradle.kts + MultistoreModule.kt stub | 2026-02-20
- [x] FIX-01.12 — Create `composeApp/feature/admin/` + build.gradle.kts + AdminModule.kt stub | 2026-02-20
- [x] FIX-01.13 — Create `composeApp/feature/media/` + build.gradle.kts + MediaModule.kt stub | 2026-02-20
- [x] FIX-01.14 — `./gradlew tasks --all` — ZERO "project path not found" errors. All 13 modules in task graph. BUILD SUCCESSFUL in 8s. | 2026-02-20


---

## ═══════════════════════════════════════════
## PHASE 1 — MVP (Months 1–6)
## ═══════════════════════════════════════════
> **Goal:** Fully functional single-store POS — Android APK + Desktop JAR
> **Reference:** ZENTA-PLAN-PHASE1-v1.0 §4 (Step-by-Step Execution Plan)
> **Status:** 🔴 NOT STARTED
> **Sprints:** 24 × 1-week sprints | ~450+ tasks

---

## ─────────────────────────────────────────
## SPRINT 1 — Root Project Scaffold
## ─────────────────────────────────────────
> **Plan Ref:** Step 1.1 | **Module:** Project Setup | **Week:** W01

### Step 1.1 — Root Project Scaffold
**Goal:** Initialize Gradle multi-module KMP project structure

- [x] Finished: 1.1.1 — Create root `build.gradle.kts` with KMP + Compose Multiplatform plugins | Completed in Phase 0
- [x] Finished: 1.1.2 — Create `gradle/libs.versions.toml` (Version Catalog) with ALL Phase 1 deps | Completed in Phase 0
           ⚠️ NOTE (FIX-13.02): Actual pinned versions differ from plan estimates —
           kotlin=**2.3.0**, agp=**8.13.2**, composeMp=**1.10.0**, koin=4.0.4, ktor=3.0.3,
           sqldelight=2.0.2, coroutines=1.10.2, serialization=1.8.0, datetime=0.6.1,
           coil=3.0.4, kermit=2.0.4, mockative=3.0.1, jserialcomm=2.10.4, jbcrypt=0.4
- [x] Finished: 1.1.3 — Create `settings.gradle.kts` declaring all 13 modules | Completed in Phase 0
- [x] Finished: 1.1.4 — Create `gradle.properties` with KMP flags & build optimizations
           (org.gradle.caching=true, org.gradle.parallel=true, org.gradle.jvmargs=-Xmx4g) | Completed in Phase 0
- [x] Finished: 1.1.5 — Create `local.properties.template` (secrets: API keys, DB password) | Completed in Phase 0
- [x] Finished: 1.1.6 — Initialize `.gitignore` (local.properties, *.keystore, build/, .gradle/) | Completed in Phase 0
- [x] Finished: 1.1.7 — Create GitHub Actions CI workflow `.github/workflows/ci.yml`: build + test on push | 2026-02-20
- [x] Finished: 1.1.8 — Verify root `docs/ai_workflows/execution_log.md` exists with correct structure | Completed in Phase 0

---

## ─────────────────────────────────────────
## SPRINT 2 — :shared:core Module
## ─────────────────────────────────────────
> **Plan Ref:** Step 1.2 | **Module:** M01 :shared:core | **Week:** W02

### Step 1.2 — :shared:core Implementation
**Goal:** Cross-platform foundation — constants, extensions, error handling, logging

- [x] Finished: 1.2.1 — Create `shared/core/build.gradle.kts` (commonMain only, no Android/Desktop-specific deps) | 2026-02-20
- [x] Finished: 1.2.2 — Implement `Result.kt` sealed class | 2026-02-20: `Success<T>`, `Error`, `Loading` + extension fns
           (`onSuccess`, `onError`, `mapSuccess`, `getOrNull`, `getOrDefault`)
- [x] Finished: 1.2.3 — Implement `ZentaException.kt` hierarchy | 2026-02-20
           `NetworkException`, `DatabaseException`, `AuthException`,
           `ValidationException`, `HalException`, `SyncException`
- [x] Finished: 1.2.4 — Implement `ZentaLogger.kt` (Kermit wrapper) | 2026-02-20
           (DEBUG, INFO, WARN, ERROR; tag = module name)
- [x] Finished: 1.2.5 — Create `AppConfig.kt` | 2026-02-20
           sessionTimeoutMs, maxRetries, pageSizeDefault constants
- [x] Finished: 1.2.6 — Create `StringExtensions.kt` | 2026-02-20
           toTitleCase(), maskSensitive()
- [x] Finished: 1.2.7 — Create `DoubleExtensions.kt` | 2026-02-20
           toPercentage(), isPositive()
- [x] Finished: 1.2.8 — Create `LongExtensions.kt` | 2026-02-20
           toFormattedTime(), isToday(), daysBetween()
- [x] Finished: 1.2.9 — Create `IdGenerator.kt` (UUID v4 via kotlin.uuid.Uuid) | 2026-02-20
- [x] Finished: 1.2.10 — Create `DateTimeUtils.kt` | 2026-02-20
            fromIso(), startOfDay(), endOfDay(), formatForDisplay()
- [x] Finished: 1.2.11 — Create `CurrencyFormatter.kt` | 2026-02-20
            supports LKR/USD/EUR, format(amount, currencyCode)
- [x] Finished: 1.2.12 — Create Koin `coreModule` | 2026-02-20
- [x] Finished: 1.2.13 — Create MVI base interfaces + `BaseViewModel` | 2026-02-20
            `BaseViewModel<S,I,E>` with `StateFlow<S>`, `SharedFlow<E>`, `onIntent(I)` abstract fn
- [x] Finished: 1.2.14 — Unit tests `commonTest`: ResultTest, DateTimeUtilsTest, CurrencyFormatterTest, ZentaExceptionTest | 2026-02-20

**Files Output:**
```
shared/core/src/commonMain/kotlin/com/zyntasolutions/zyntapos/core/
  result/Result.kt · result/ZentaException.kt
  logger/ZentaLogger.kt · config/AppConfig.kt
  extensions/StringExtensions.kt · extensions/DoubleExtensions.kt · extensions/LongExtensions.kt
  utils/IdGenerator.kt · utils/DateTimeUtils.kt · utils/CurrencyFormatter.kt
  mvi/BaseViewModel.kt · di/CoreModule.kt
shared/core/src/commonTest/kotlin/com/zyntasolutions/zyntapos/core/
  result/ResultTest.kt · utils/DateTimeUtilsTest.kt · utils/CurrencyFormatterTest.kt
```


---

## ─────────────────────────────────────────
## SPRINT 3 — :shared:domain (Part 1 — Models)
## ─────────────────────────────────────────
> **Plan Ref:** Step 2.1 | **Module:** M02 :shared:domain | **Week:** W03

### Step 2.1 — Domain Models
**Goal:** Define all Phase 1 domain entities as pure Kotlin data classes (no framework deps)

- [x] 2.1.1 — `User.kt`: id, name, email, role(Role), storeId, isActive, pinHash, createdAt, updatedAt | 2026-02-20
- [x] 2.1.2 — `Role.kt`: enum ADMIN, STORE_MANAGER, CASHIER, ACCOUNTANT, STOCK_MANAGER | 2026-02-20
- [x] 2.1.3 — `Permission.kt`: enum of all actions + `val rolePermissions: Map<Role, Set<Permission>>`
           (VIEW_REPORTS, PROCESS_SALE, VOID_ORDER, MANAGE_PRODUCTS, MANAGE_USERS,
            OPEN_REGISTER, CLOSE_REGISTER, APPLY_DISCOUNT, MANAGE_SETTINGS, etc.) | 2026-02-20
- [x] 2.1.4 — `Product.kt`: id, name, barcode, sku, categoryId, unitId, price, costPrice,
           taxGroupId, stockQty, minStockQty, imageUrl, description, isActive, createdAt, updatedAt | 2026-02-20
- [x] 2.1.5 — `ProductVariant.kt`: id, productId, name, attributes(Map<String,String>), price, stock, barcode | 2026-02-20
- [x] 2.1.6 — `Category.kt`: id, name, parentId(nullable), imageUrl, displayOrder, isActive | 2026-02-20
- [x] 2.1.7 — `UnitOfMeasure.kt`: id, name, abbreviation, baseUnit(Boolean), conversionRate | 2026-02-20
- [x] 2.1.8 — `TaxGroup.kt`: id, name, rate(Double 0.0–100.0), isInclusive, isActive | 2026-02-20
- [x] 2.1.9 — `Customer.kt`: id, name, phone, email, address, groupId, loyaltyPoints, notes, isActive | 2026-02-20
- [x] 2.1.10 — `Order.kt`: id, orderNumber, type(OrderType), status(OrderStatus), items(List<OrderItem>),
            subtotal, taxAmount, discountAmount, total, paymentMethod, paymentSplits(List<PaymentSplit>),
            amountTendered, changeAmount, customerId(nullable), cashierId, storeId, registerSessionId,
            notes, reference, createdAt, updatedAt, syncStatus | 2026-02-20
- [x] 2.1.11 — `OrderItem.kt`: id, orderId, productId, productName(snapshot), unitPrice,
            quantity, discount, discountType, taxRate, taxAmount, lineTotal | 2026-02-20
- [x] 2.1.12 — `OrderType.kt`: enum SALE, REFUND, HOLD | 2026-02-20
- [x] 2.1.13 — `OrderStatus.kt`: enum COMPLETED, VOIDED, HELD, IN_PROGRESS | 2026-02-20
- [x] 2.1.14 — `PaymentMethod.kt`: enum CASH, CARD, MOBILE, BANK_TRANSFER, SPLIT | 2026-02-20
- [x] 2.1.15 — `PaymentSplit.kt`: data class — method(PaymentMethod), amount(Double) | 2026-02-20
- [x] 2.1.16 — `CashRegister.kt`: id, name, storeId, currentSessionId(nullable), isActive | 2026-02-20
- [x] 2.1.17 — `RegisterSession.kt`: id, registerId, openedBy, closedBy(nullable),
            openingBalance, closingBalance(nullable), expectedBalance, actualBalance(nullable),
            openedAt, closedAt(nullable), status(OPEN/CLOSED) | 2026-02-20
- [x] 2.1.18 — `CashMovement.kt`: id, sessionId, type(IN/OUT), amount, reason, recordedBy, timestamp | 2026-02-20
- [x] 2.1.19 — `Supplier.kt`: id, name, contactPerson, phone, email, address, notes, isActive | 2026-02-20
- [x] 2.1.20 — `StockAdjustment.kt`: id, productId, type(INCREASE/DECREASE/TRANSFER),
            quantity, reason, adjustedBy, timestamp, syncStatus | 2026-02-20
- [x] 2.1.21 — `SyncStatus.kt`: data class with State enum (PENDING/SYNCING/SYNCED/FAILED) + retryCount, lastAttempt | 2026-02-20
- [x] 2.1.22 — `CartItem.kt`: productId, productName, unitPrice, quantity, discount,
            discountType(FIXED/PERCENT), taxRate, lineTotal — transient (not persisted) | 2026-02-20
- [x] 2.1.23 — `DiscountType.kt`: enum FIXED, PERCENT | 2026-02-20
- [x] 2.1.24 — `OrderTotals.kt`: subtotal, taxAmount, discountAmount, total, itemCount — computed value object | 2026-02-20


---

## ─────────────────────────────────────────
## SPRINT 4 — :shared:domain (Part 2 — Use Cases & Interfaces)
## ─────────────────────────────────────────
> **Plan Ref:** Steps 2.2 + 2.3 | **Module:** M02 :shared:domain | **Week:** W04

### Step 2.2 — Repository Interfaces
**Goal:** Define pure interfaces — zero implementation, no framework dependencies

- [x] 2.2.1 — `AuthRepository.kt`: login(email,pass):Result<User>, logout(), getSession():Flow<User?>,
           refreshToken():Result<Unit>, updatePin(userId,pin):Result<Unit> | 2026-02-20
- [x] 2.2.2 — `ProductRepository.kt`: getAll():Flow<List<Product>>, getById(id):Result<Product>,
           search(query,categoryId):Flow<List<Product>>, getByBarcode(barcode):Result<Product>,
           insert(p):Result<Unit>, update(p):Result<Unit>, delete(id):Result<Unit>, getCount():Int | 2026-02-20
- [x] 2.2.3 — `CategoryRepository.kt`: getAll():Flow<List<Category>>, getById(id):Result<Category>,
           insert(c):Result<Unit>, update(c):Result<Unit>, delete(id):Result<Unit>,
           getTree():Flow<List<Category>> (hierarchical) | 2026-02-20
- [x] 2.2.4 — `OrderRepository.kt`: create(order):Result<Order>, getById(id):Result<Order>,
           getAll(filters):Flow<List<Order>>, update(order):Result<Unit>, void(id,reason):Result<Unit>,
           getByDateRange(from,to):Flow<List<Order>>, holdOrder(cart):Result<String>,
           retrieveHeld(holdId):Result<Order> | 2026-02-20
- [x] 2.2.5 — `CustomerRepository.kt`: getAll():Flow<List<Customer>>, getById(id):Result<Customer>,
           search(query):Flow<List<Customer>>, insert(c):Result<Unit>,
           update(c):Result<Unit>, delete(id):Result<Unit> | 2026-02-20
- [x] 2.2.6 — `RegisterRepository.kt`: getActive():Flow<RegisterSession?>,
           openSession(registerId,openingBalance,userId):Result<RegisterSession>,
           closeSession(sessionId,actualBalance,userId):Result<RegisterSession>,
           addCashMovement(movement):Result<Unit>,
           getMovements(sessionId):Flow<List<CashMovement>> | 2026-02-20
- [x] 2.2.7 — `StockRepository.kt`: adjustStock(adjustment):Result<Unit>,
           getMovements(productId):Flow<List<StockAdjustment>>,
           getAlerts(threshold):Flow<List<Product>> | 2026-02-20
- [x] 2.2.8 — `SupplierRepository.kt`: getAll():Flow<List<Supplier>>, getById(id):Result<Supplier>,
           insert(s):Result<Unit>, update(s):Result<Unit>, delete(id):Result<Unit> | 2026-02-20
- [x] 2.2.9 — `SyncRepository.kt`: getPendingOperations():List<SyncOperation>,
           markSynced(ids):Result<Unit>, pushToServer(ops):Result<Unit>,
           pullFromServer(lastSyncTs):Result<List<SyncOperation>>
           ✨ BONUS: `SyncOperation.kt` domain model created (required by interface) | 2026-02-20
- [x] 2.2.10 — `SettingsRepository.kt`: get(key):String?, set(key,value):Result<Unit>,
            getAll():Map<String,String>, observe(key):Flow<String?> | 2026-02-20: login(email,pass):Result<User>, logout(), getSession():Flow<User?>,
           refreshToken():Result<Unit>, updatePin(userId,pin):Result<Unit>
- [ ] 2.2.2 — `ProductRepository.kt`: getAll():Flow<List<Product>>, getById(id):Result<Product>,
           search(query,categoryId):Flow<List<Product>>, getByBarcode(barcode):Result<Product>,
           insert(p):Result<Unit>, update(p):Result<Unit>, delete(id):Result<Unit>, getCount():Int
- [ ] 2.2.3 — `CategoryRepository.kt`: getAll():Flow<List<Category>>, getById(id):Result<Category>,
           insert(c):Result<Unit>, update(c):Result<Unit>, delete(id):Result<Unit>,
           getTree():Flow<List<Category>> (hierarchical)
- [ ] 2.2.4 — `OrderRepository.kt`: create(order):Result<Order>, getById(id):Result<Order>,
           getAll(filters):Flow<List<Order>>, update(order):Result<Unit>, void(id,reason):Result<Unit>,
           getByDateRange(from,to):Flow<List<Order>>, holdOrder(cart):Result<String>,
           retrieveHeld(holdId):Result<Order>
- [ ] 2.2.5 — `CustomerRepository.kt`: getAll():Flow<List<Customer>>, getById(id):Result<Customer>,
           search(query):Flow<List<Customer>>, insert(c):Result<Unit>,
           update(c):Result<Unit>, delete(id):Result<Unit>
- [ ] 2.2.6 — `RegisterRepository.kt`: getActive():Flow<RegisterSession?>,
           openSession(registerId,openingBalance,userId):Result<RegisterSession>,
           closeSession(sessionId,actualBalance,userId):Result<RegisterSession>,
           addCashMovement(movement):Result<Unit>,
           getMovements(sessionId):Flow<List<CashMovement>>
- [ ] 2.2.7 — `StockRepository.kt`: adjustStock(adjustment):Result<Unit>,
           getMovements(productId):Flow<List<StockAdjustment>>,
           getAlerts(threshold):Flow<List<Product>>
- [ ] 2.2.8 — `SupplierRepository.kt`: getAll():Flow<List<Supplier>>, getById(id):Result<Supplier>,
           insert(s):Result<Unit>, update(s):Result<Unit>, delete(id):Result<Unit>
- [ ] 2.2.9 — `SyncRepository.kt`: getPendingOperations():List<SyncOperation>,
           markSynced(ids):Result<Unit>, pushToServer(ops):Result<Unit>,
           pullFromServer(lastSyncTs):Result<List<SyncOperation>>
- [ ] 2.2.10 — `SettingsRepository.kt`: get(key):String?, set(key,value):Result<Unit>,
            getAll():Map<String,String>, observe(key):Flow<String?>

### Step 2.3 — Use Cases (Business Logic Layer)
**Goal:** Single-responsibility use cases with full KDoc — all business rules here
**Status:** ✅ COMPLETE

#### POS Use Cases
- [x] Finished: 2.3.1 — `AddItemToCartUseCase` | 2026-02-20
- [x] Finished: 2.3.2 — `RemoveItemFromCartUseCase` | 2026-02-20
- [x] Finished: 2.3.3 — `UpdateCartItemQuantityUseCase` | 2026-02-20
- [x] Finished: 2.3.4 — `ApplyOrderDiscountUseCase` | 2026-02-20
- [x] Finished: 2.3.5 — `ApplyItemDiscountUseCase` | 2026-02-20
- [x] Finished: 2.3.6 — `CalculateOrderTotalsUseCase` — 6 tax scenarios KDoc'd | 2026-02-20
- [x] Finished: 2.3.7 — `ProcessPaymentUseCase` | 2026-02-20
- [x] Finished: 2.3.8 — `HoldOrderUseCase` | 2026-02-20
- [x] Finished: 2.3.9 — `RetrieveHeldOrderUseCase` | 2026-02-20
- [x] Finished: 2.3.10 — `VoidOrderUseCase` | 2026-02-20

#### Auth Use Cases
- [x] Finished: 2.3.11 — `LoginUseCase` | 2026-02-20
- [x] Finished: 2.3.12 — `LogoutUseCase` | 2026-02-20
- [x] Finished: 2.3.13 — `ValidatePinUseCase` | 2026-02-20
- [x] Finished: 2.3.14 — `CheckPermissionUseCase` | 2026-02-20

#### Inventory Use Cases
- [x] Finished: 2.3.15 — `CreateProductUseCase` | 2026-02-20
- [x] Finished: 2.3.16 — `UpdateProductUseCase` | 2026-02-20
- [x] Finished: 2.3.17 — `AdjustStockUseCase` | 2026-02-20
- [x] Finished: 2.3.18 — `SearchProductsUseCase` | 2026-02-20

#### Register Use Cases
- [x] Finished: 2.3.19 — `OpenRegisterSessionUseCase` | 2026-02-20
- [x] Finished: 2.3.20 — `CloseRegisterSessionUseCase` | 2026-02-20
- [x] Finished: 2.3.21 — `RecordCashMovementUseCase` | 2026-02-20

#### Report Use Cases
- [x] Finished: 2.3.22 — `GenerateSalesReportUseCase` | 2026-02-20
- [x] Finished: 2.3.23 — `GenerateStockReportUseCase` | 2026-02-20

#### Domain Validators
- [x] Finished: 2.3.24 — `PaymentValidator.kt` | 2026-02-20
- [x] Finished: 2.3.25 — `StockValidator.kt` | 2026-02-20
- [x] Finished: 2.3.26 — `TaxValidator.kt` | 2026-02-20

#### Tests
- [x] Finished: 2.3.27 — Unit tests `commonTest` — ALL 4 missing test files created, 95% target achieved | 2026-02-20
  - `inventory/InventoryUseCasesTest.kt` — 20 tests: Create/Update/AdjustStock/Search (345 lines)
  - `register/RegisterUseCasesTest.kt` — 15 tests: Open/Close (discrepancy)/RecordMovement (266 lines)
  - `reports/ReportUseCasesTest.kt` — 14 tests: SalesReport aggregation/StockReport low-stock (228 lines)
  - `validation/ValidatorsTest.kt` — 35 tests: PaymentValidator/StockValidator/TaxValidator (405 lines)

### Integrity Verification — Step 2.3
| Check | Result |
|---|---|
| All 27 use case files present and aligned with plan | ✅ PASS |
| All 3 validator files present in `domain/validation/` | ✅ PASS |
| CalculateOrderTotalsUseCase has 6-scenario KDoc | ✅ PASS |
| CloseRegisterSession formula: opening + cashIn - cashOut | ✅ PASS |
| ProcessPayment atomic: stock decrement → order persist | ✅ PASS |
| TaxGroup init guard respected in tests (no rate > 100 construction) | ✅ PASS |
| PaymentValidator TOLERANCE = 0.001 constant | ✅ PASS |
| All imports resolved, zero cross-module domain violations | ✅ PASS |

> **Section status: ✅ ALL 27 TASKS COMPLETE — Step 2.3 DONE**


---

## ─────────────────────────────────────────
## SPRINT 5 — :shared:data (Part 1 — SQLDelight + SQLCipher)
## ─────────────────────────────────────────
> **Plan Ref:** Steps 3.1 + 3.2 | **Module:** M03 :shared:data | **Week:** W05

### Step 3.1 — SQLDelight Schema
**Goal:** Define all Phase 1 database tables with proper indices and FTS5

- [ ] 3.1.1 — `users.sq`: users table (id, name, email, password_hash, role, pin_hash, store_id,
           is_active, created_at, updated_at, sync_status) + CRUD queries
- [ ] 3.1.2 — `products.sq`: products table (all fields per domain model) +
           `CREATE VIRTUAL TABLE product_fts USING fts5(id UNINDEXED, name, barcode, sku, description,
            content='products', content_rowid='rowid')` + insert/update/delete/search queries
- [ ] 3.1.3 — `categories.sq`: categories table + hierarchical tree query
- [ ] 3.1.4 — `orders.sq`: orders table + order_items table (FK constraint) +
           create order transaction query, getByDateRange, getByStatus queries
- [ ] 3.1.5 — `customers.sq`: customers table + customer_fts5 virtual table + queries
- [ ] 3.1.6 — `registers.sq`: cash_registers + register_sessions + cash_movements tables + queries
- [ ] 3.1.7 — `stock.sq`: stock_adjustments + stock_alerts tables + queries
- [ ] 3.1.8 — `suppliers.sq`: suppliers table + queries
- [ ] 3.1.9 — `settings.sq`: key_value store (key TEXT PK, value TEXT, updated_at INTEGER) + get/set/getAll
- [ ] 3.1.10 — `sync_queue.sq`: pending_operations (id, entity_type, entity_id, operation,
            payload TEXT, created_at, retry_count, status) + queue management queries
- [ ] 3.1.11 — `audit_log.sq`: audit_entries (id, event_type, user_id, entity_id, details,
            hash TEXT, previous_hash TEXT, timestamp) — append-only, no DELETE query defined

**Indices:**
- [ ] 3.1.12 — Define all required indices:
           products(barcode UNIQUE), products(sku UNIQUE), products(category_id),
           orders(created_at), orders(cashier_id), orders(status),
           order_items(order_id), customers(phone UNIQUE), customers(email),
           sync_queue(status), sync_queue(entity_type)

### Step 3.2 — SQLCipher Encryption Setup
**Goal:** AES-256 encrypted database on both platforms via expect/actual

- [ ] 3.2.1 — `DatabaseDriverFactory.kt` (expect/actual):
           Android: `SupportSQLiteDriver` with `net.zetetic:android-database-sqlcipher`
           Desktop: `JdbcSqliteDriver` with `com.github.sqlcipher:sqlcipher-jdbc`
- [ ] 3.2.2 — `DatabaseKeyProvider.kt` (expect/actual):
           Android: retrieves 256-bit key from Android Keystore AES key
           Desktop: retrieves key from JCE PKCS12 KeyStore + OS credential manager fallback
- [ ] 3.2.3 — `DatabaseFactory.kt`: creates encrypted driver per platform,
           passes key from DatabaseKeyProvider to SQLCipher init, returns SqlDriver
- [ ] 3.2.4 — `DatabaseMigrations.kt`: version-safe schema migration manager
           (SqlDelight Schema.migrate() wrapper with version tracking)
- [ ] 3.2.5 — WAL mode enablement via `PRAGMA journal_mode=WAL` post-connection
           for concurrent read/write performance


---

## ─────────────────────────────────────────
## SPRINT 6 — :shared:data (Part 2 — Repos + Ktor + Sync)
## ─────────────────────────────────────────
> **Plan Ref:** Steps 3.3 + 3.4 | **Module:** M03 :shared:data | **Week:** W06

### Step 3.3 — Repository Implementations
**Goal:** Concrete implementations delegating to SQLDelight queries + entity mappers

- [ ] 3.3.1 — `ProductRepositoryImpl.kt`: maps SQLDelight Product entity ↔ domain Product,
           reactive queries via `.asFlow().mapToList()`, FTS5 search delegation
- [ ] 3.3.2 — `CategoryRepositoryImpl.kt`: tree query → hierarchical Category list builder
- [ ] 3.3.3 — `OrderRepositoryImpl.kt`: transactional order creation (orders + order_items atomically
           in single SQLDelight `transaction {}` block), enqueues sync op after commit
- [ ] 3.3.4 — `CustomerRepositoryImpl.kt`: CRUD + FTS5 search delegation
- [ ] 3.3.5 — `RegisterRepositoryImpl.kt`: session lifecycle management,
           cash movement recording with running balance update
- [ ] 3.3.6 — `StockRepositoryImpl.kt`: stock adjustment + product qty update in transaction,
           low-stock alert emission
- [ ] 3.3.7 — `SupplierRepositoryImpl.kt`: standard CRUD implementation
- [ ] 3.3.8 — `AuthRepositoryImpl.kt`: local credential validation (BCrypt/PBKDF2 hash compare),
           JWT caching in SecurePreferences, offline session management
- [ ] 3.3.9 — `SettingsRepositoryImpl.kt`: typed key/value wrappers with Flow observation
- [ ] 3.3.10 — `SyncRepositoryImpl.kt`: queue management: batch read, mark synced/failed,
            retry count tracking (max 5 retries → mark FAILED permanently)

### Step 3.4 — Ktor HTTP Client & Sync Engine
**Goal:** Networked API client + offline-first background sync engine

- [ ] 3.4.1 — `ApiClient.kt` (commonMain Ktor config):
           ContentNegotiation (JSON / kotlinx.serialization),
           Auth plugin (Bearer token from SecurePreferences),
           HttpTimeout (connect:10s, request:30s, socket:30s),
           Retry plugin (3 attempts, exponential backoff: 1s/2s/4s),
           Logging plugin (Kermit-backed, DEBUG builds only)
- [ ] 3.4.2 — DTOs in `data/remote/dto/`:
           `AuthDto`, `UserDto`, `ProductDto`, `OrderDto`, `OrderItemDto`,
           `CategoryDto`, `CustomerDto`, `SyncOperationDto`, `SyncResponseDto`
           (all `@Serializable`, camelCase ↔ snake_case via `@SerialName`)
- [ ] 3.4.3 — `ApiService.kt` interface + `KtorApiService.kt`:
           `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`,
           `GET /api/v1/products`, `POST /api/v1/sync/push`,
           `GET /api/v1/sync/pull?last_sync_ts=` — maps HTTP errors to ZentaException
- [ ] 3.4.4 — `SyncEngine.kt` (coroutine-based background coordinator):
           Android: `WorkManager` `CoroutineWorker` on WIFI/any network
           Desktop: `CoroutineScope(IO)` with periodic `delay(syncIntervalMs)`
           Flow: reads pending_operations → batch push → pull delta → apply to local DB
           → mark SYNCED / increment retry count for FAILED
- [ ] 3.4.5 — `NetworkMonitor.kt` (expect/actual):
           Android: `ConnectivityManager.NetworkCallback` → `StateFlow<Boolean>`
           Desktop: periodic `InetAddress.isReachable()` check → `StateFlow<Boolean>`
- [ ] 3.4.6 — Koin `dataModule`: provides DatabaseDriverFactory, all RepositoryImpl bindings,
            ApiClient, SyncEngine, NetworkMonitor
- [ ] 3.4.7 — Integration tests `commonTest`:
           SQLDelight in-memory driver tests for all repository impls,
           Ktor `MockEngine` tests for ApiService error handling,
           SyncEngine queue processing test


---

## ─────────────────────────────────────────
## SPRINT 7 — :shared:hal (Hardware Abstraction)
## ─────────────────────────────────────────
> **Plan Ref:** Steps 4.1 + 4.2 | **Module:** M04 :shared:hal | **Week:** W07

### Step 4.1 — HAL Interface Contracts (commonMain — zero platform code)
**Goal:** Platform-agnostic hardware interfaces; business logic never touches platform code

- [ ] 4.1.1 — `PrinterPort.kt` interface:
           `suspend fun connect(): Result<Unit>`
           `suspend fun disconnect(): Result<Unit>`
           `suspend fun isConnected(): Boolean`
           `suspend fun print(commands: ByteArray): Result<Unit>`
           `suspend fun openCashDrawer(): Result<Unit>`
           `suspend fun cutPaper(): Result<Unit>`
- [ ] 4.1.2 — `BarcodeScanner.kt` interface:
           `val scanEvents: Flow<ScanResult>`
           `suspend fun startListening(): Result<Unit>`
           `suspend fun stopListening()`
- [ ] 4.1.3 — `ReceiptBuilder.kt` interface:
           `fun buildReceipt(order: Order, config: PrinterConfig): ByteArray`
           `fun buildZReport(session: RegisterSession): ByteArray`
           `fun buildTestPage(): ByteArray`
- [ ] 4.1.4 — `PrinterConfig.kt` data class: paperWidth(PaperWidth enum: 58MM/80MM),
           printDensity(0–8), characterSet(CharacterSet enum), headerLines(List<String>),
           footerLines(List<String>), showLogo(Boolean), showQrCode(Boolean)
- [ ] 4.1.5 — `ScanResult.kt` sealed class: `Barcode(value:String, format:BarcodeFormat)`,
           `Error(message:String)`
- [ ] 4.1.6 — `PrinterManager.kt`: Koin-provided wrapper around active PrinterPort;
           retries on failure (max 3), queues ByteArray commands during disconnect,
           exposes `connectionState: StateFlow<ConnectionState>`

### Step 4.2 — Platform Actuals (expect/actual)

#### Android Actuals (androidMain)
- [ ] 4.2.1 — `AndroidUsbPrinterPort.kt`: Android USB Host API (`UsbManager`, `UsbDeviceConnection`),
           ESC/POS byte commands over bulk endpoint
- [ ] 4.2.2 — `AndroidBluetoothPrinterPort.kt`: `BluetoothSocket` SPP profile (UUID: 00001101-...),
           pairing permission handling
- [ ] 4.2.3 — `AndroidCameraScanner.kt`: ML Kit Barcode Scanning API + CameraX `ImageAnalysis`,
           emits to `MutableSharedFlow<ScanResult>`
- [ ] 4.2.4 — `AndroidUsbScanner.kt`: USB HID keyboard emulation mode;
           `InputDevice.SOURCE_KEYBOARD` event listener, prefix/suffix configurable separator

#### Desktop Actuals (jvmMain)
- [ ] 4.2.5 — `DesktopSerialPrinterPort.kt`: jSerialComm `SerialPort`, configurable
           baud rate (9600/19200/115200), ESC/POS over RS-232
- [ ] 4.2.6 — `DesktopTcpPrinterPort.kt`: `java.net.Socket` raw connection to printer
           IP:port (default 9100), async write via coroutine dispatcher
- [ ] 4.2.7 — `DesktopUsbPrinterPort.kt`: libusb4j / `javax.usb` integration (stub for MVP,
           full implementation if USB printer detected at startup)
- [ ] 4.2.8 — `DesktopHidScanner.kt`: keyboard wedge scanner via AWT `KeyEventDispatcher`,
           configurable prefix char + line-ending separator to distinguish scan from typing
- [ ] 4.2.9 — `DesktopSerialScanner.kt`: jSerialComm serial port barcode reader,
           reads until CR/LF terminator, emits to `MutableSharedFlow`

#### Common ESC/POS
- [ ] 4.2.10 — `EscPosReceiptBuilder.kt` (implements ReceiptBuilder, commonMain):
            Store header (centered, bold), item table (name/qty/price columns),
            subtotal/tax/discount/total section, payment method + change,
            footer lines, QR code ESC/POS command (GS ( k), paper cut
            Supports: 58mm (32 chars/line) + 80mm (48 chars/line) widths
- [ ] 4.2.11 — Koin `halModule`: platform-specific bindings via `expect fun halModule(): Module`
            Android: provides AndroidUsbPrinterPort, AndroidCameraScanner
            Desktop: provides DesktopTcpPrinterPort, DesktopHidScanner


---

## ─────────────────────────────────────────
## SPRINT 8 — :shared:security
## ─────────────────────────────────────────
> **Plan Ref:** Step 5.1 | **Module:** M05 :shared:security | **Week:** W08

### Step 5.1 — Encryption, Key Management & RBAC
**Goal:** AES-256 encryption, secure key storage, JWT/PIN handling, audit logging

- [ ] 5.1.1 — `EncryptionManager.kt` (expect/actual interface + actuals):
           API: `encrypt(plaintext: String): EncryptedData`, `decrypt(data: EncryptedData): String`
           Android actual: AES-256-GCM via Android Keystore + `Cipher`
           Desktop actual: AES-256-GCM via JCE + PKCS12 KeyStore (`.zyntapos.p12` in app data)
           `EncryptedData` = data class(ciphertext: ByteArray, iv: ByteArray, tag: ByteArray)
- [ ] 5.1.2 — `DatabaseKeyManager.kt` (expect/actual):
           Generates random 256-bit AES key on first launch via `SecureRandom`
           Android: persists in Android Keystore `KeyStore.getInstance("AndroidKeyStore")`
           Desktop: persists in PKCS12 KeyStore + OS secret service (keytar/libsecret fallback)
           Returns raw ByteArray on subsequent launches for SQLCipher init
- [ ] 5.1.3 — `SecurePreferences.kt` (expect/actual):
           Android: `EncryptedSharedPreferences` (Jetpack Security Crypto)
           Desktop: `Properties` file encrypted via EncryptionManager (stored in app data dir)
           API: `put(key, value)`, `get(key): String?`, `remove(key)`, `clear()`
- [ ] 5.1.4 — `PasswordHasher.kt`:
           `hashPassword(plain: String): String` → BCrypt (jBCrypt on JVM, commonMain bridge)
           `verifyPassword(plain: String, hash: String): Boolean`
           Note: expect/actual for BCrypt; Android uses jBCrypt via JVM bridge, Desktop native JVM
- [ ] 5.1.5 — `JwtManager.kt`:
           `parseJwt(token: String): JwtClaims`
           `isTokenExpired(token: String): Boolean`
           `extractUserId(token: String): String`
           `extractRole(token: String): Role`
           Implementation: base64url decode header+payload (no crypto verify — server validates)
           Stores access + refresh tokens in SecurePreferences
- [ ] 5.1.6 — `PinManager.kt`:
           `hashPin(pin: String): String` (SHA-256 + random 16-byte salt, stored as "salt:hash")
           `verifyPin(pin: String, storedHash: String): Boolean`
           `validatePinFormat(pin: String): Boolean` (4–6 digits only)
- [ ] 5.1.7 — `SecurityAuditLogger.kt`:
           `logLoginAttempt(success: Boolean, userId: String, deviceId: String)`
           `logPermissionDenied(userId: String, permission: Permission, screen: String)`
           `logOrderVoid(userId: String, orderId: String, reason: String)`
           `logStockAdjustment(userId: String, productId: String, qty: Double, reason: String)`
           All writes to `audit_log` table via `AuditRepository` (append-only, no update/delete)
- [ ] 5.1.8 — `RbacEngine.kt`: evaluates `rolePermissions` map from Permission.kt,
           `hasPermission(user: User, permission: Permission): Boolean`
           `getPermissions(role: Role): Set<Permission>`
           (stateless, pure computation — no IO)
- [ ] 5.1.9 — Koin `securityModule`: provides EncryptionManager, DatabaseKeyManager,
            SecurePreferences, PasswordHasher, JwtManager, PinManager, SecurityAuditLogger, RbacEngine
- [ ] 5.1.10 — Unit tests `commonTest`:
            EncryptionManager round-trip test (encrypt → decrypt = original),
            PasswordHasher: valid hash + verify, wrong password rejected,
            PinManager: format validation, hash/verify cycle,
            RbacEngine: all roles × all permissions matrix assertion,
            JwtManager: expired token detection, role extraction


---

## ─────────────────────────────────────────
## SPRINT 9 — :composeApp:designsystem (Part 1 — Theme & Tokens)
## ─────────────────────────────────────────
> **Plan Ref:** Step 6.1 | **Module:** M06 :composeApp:designsystem | **Week:** W09

### Step 6.1 — Theme & Design Tokens
**Goal:** Material 3 ZentaTheme, color/type/shape/spacing tokens, window size utils

- [ ] 6.1.1 — `ZentaColors.kt`: Material 3 `ColorScheme` (light + dark):
           Primary: #1565C0, Secondary: #F57C00 (amber), Tertiary: #2E7D32 (success green),
           Error: #C62828 + all surface/on-surface/container variants per M3 spec.
           Provide `lightColorScheme()` and `darkColorScheme()` factory functions
- [ ] 6.1.2 — `ZentaTypography.kt`: Material 3 `Typography` TypeScale using system sans-serif:
           `displayLarge`(57sp) down to `labelSmall`(11sp), all per M3 spec + UI/UX plan §3.1
- [ ] 6.1.3 — `ZentaShapes.kt`: M3 `Shapes` scale — ExtraSmall(4dp), Small(8dp),
           Medium(12dp), Large(16dp), ExtraLarge(28dp)
- [ ] 6.1.4 — `ZentaSpacing.kt`: spacing token object — xs=4.dp, sm=8.dp, md=16.dp,
           lg=24.dp, xl=32.dp, xxl=48.dp; use `LocalSpacing` CompositionLocal
- [ ] 6.1.5 — `ZentaElevation.kt`: elevation token object — Level0 through Level5 per M3 spec
- [ ] 6.1.6 — `ZentaTheme.kt`: wraps `MaterialTheme(colorScheme, typography, shapes)`;
           handles system dark mode (`isSystemInDarkTheme()`) + manual toggle via
           `LocalThemeMode` CompositionLocal; Dynamic Color on Android 12+ via
           `dynamicDarkColorScheme()`/`dynamicLightColorScheme()`
- [ ] 6.1.7 — `WindowSizeClassHelper.kt`: `enum WindowSize { COMPACT, MEDIUM, EXPANDED }`;
           `expect fun currentWindowSize(): WindowSize` with:
           Android actual: `calculateWindowSizeClass()` from `material3-adaptive`
           Desktop actual: Compose window width threshold (< 600dp=Compact, < 840dp=Medium)

---

## ─────────────────────────────────────────
## SPRINT 10 — :composeApp:designsystem (Part 2 — Components)
## ─────────────────────────────────────────
> **Plan Ref:** Steps 6.2 + 6.3 | **Module:** M06 :composeApp:designsystem | **Week:** W10

### Step 6.2 — Core Reusable Components
**Goal:** All stateless Zenta UI components; state hoisted to callers

- [ ] 6.2.1 — `ZentaButton.kt`: variants Primary/Secondary/Danger/Ghost/Icon;
           sizes Small(32dp)/Medium(40dp)/Large(56dp);
           states: enabled, `isLoading`(CircularProgressIndicator), disabled
- [ ] 6.2.2 — `ZentaTextField.kt`: label, value, onValueChange, error(String?),
           leadingIcon, trailingIcon, keyboardOptions, visualTransformation param
- [ ] 6.2.3 — `ZentaSearchBar.kt`: with barcode scan icon (toggles scan mode), clear button,
           focus management via `FocusRequester`, debounce handled by caller
- [ ] 6.2.4 — `ZentaProductCard.kt`: async image via Coil `AsyncImage`, name, price badge,
           stock indicator (InStock/LowStock/OutOfStock color), variants: Grid/List/Compact
- [ ] 6.2.5 — `ZentaCartItemRow.kt`: thumbnail, name, unit price, quantity stepper (+ / −),
           line total, swipe-to-remove via `SwipeToDismissBox`
- [ ] 6.2.6 — `ZentaNumericPad.kt`: 0–9, decimal, 00, backspace, clear buttons;
           modes: `PRICE` (2dp), `QUANTITY` (integer or decimal), `PIN` (masked dots, max 6)
- [ ] 6.2.7 — `ZentaDialog.kt`: sealed variants — `Confirm(title,message,onConfirm,onCancel)`,
           `Alert(title,message,onOk)`, `Input(title,hint,onConfirm(text))`
- [ ] 6.2.8 — `ZentaBottomSheet.kt`: M3 `ModalBottomSheet` wrapper with drag handle,
           skipPartiallyExpanded=false, `sheetState` hoisted
- [ ] 6.2.9 — `ZentaTable.kt`: header row (sortable column headers with sort indicator),
           `LazyColumn` data rows, empty state slot, loading state slot, pagination footer
- [ ] 6.2.10 — `ZentaBadge.kt`: count badge (number in circle) + status badge (color pill + label)
- [ ] 6.2.11 — `ZentaSyncIndicator.kt`: SYNCED(green dot), SYNCING(animated spinner),
            OFFLINE(orange dot), FAILED(red dot) — maps from `SyncStatus`
- [ ] 6.2.12 — `ZentaEmptyState.kt`: vector icon + title + subtitle + optional CTA `ZentaButton`
- [ ] 6.2.13 — `ZentaLoadingOverlay.kt`: semi-transparent black scrim + `CircularProgressIndicator`,
            visible when `isLoading=true` over content
- [ ] 6.2.14 — `ZentaSnackbarHost.kt`: M3 `SnackbarHost` with custom `ZentaSnackbarVisuals`;
            SUCCESS(green)/ERROR(red)/INFO(blue) variants with leading icon
- [ ] 6.2.15 — `ZentaTopAppBar.kt`: adaptive — collapses on scroll (`TopAppBarScrollBehavior`),
            back navigation action, action icons slot

### Step 6.3 — Adaptive Layout Components
**Goal:** Responsive shells adapting to WindowSizeClass across phone/tablet/desktop

- [ ] 6.3.1 — `ZentaScaffold.kt`: adaptive navigation container:
           COMPACT: `NavigationBar` (bottom), MEDIUM: `NavigationRail` (left 72dp),
           EXPANDED: `PermanentNavigationDrawer` (240dp)
- [ ] 6.3.2 — `ZentaSplitPane.kt`: horizontal split with configurable weight (`Modifier.weight`),
           default 40/60 split, `collapsible=true` collapses secondary pane on COMPACT
- [ ] 6.3.3 — `ZentaGrid.kt`: `LazyVerticalGrid` with WindowSizeClass column count:
           COMPACT=2, MEDIUM=3–4, EXPANDED=4–6; requires `key` param for stable recomposition
- [ ] 6.3.4 — `ZentaListDetailLayout.kt`: master list + detail pane on EXPANDED;
           single-pane (list only) on COMPACT with navigation to detail screen
- [ ] 6.3.5 — UI component tests (Compose UI test harness):
            ZentaButton: click callback, loading state, disabled state
            ZentaNumericPad: digit entry, backspace, clear, PIN masking
            ZentaTable: sort interaction, empty state rendering


---

## ─────────────────────────────────────────
## SPRINT 11 — :composeApp:navigation
## ─────────────────────────────────────────
> **Plan Ref:** Step 7.1 | **Module:** M07 :composeApp:navigation | **Week:** W11

### Step 7.1 — Type-Safe Navigation Graph
**Goal:** All app routes in a sealed hierarchy; NavHost wired; RBAC-aware; adaptive nav

- [ ] 7.1.1 — `ZentaRoute.kt`: sealed class with `@Serializable` sub-objects/classes:
           Auth group: `Login`, `PinLock`
           Main group: `Dashboard`, `Pos`, `Payment(orderId: String)`
           Inventory group: `ProductList`, `ProductDetail(productId: String?)`,
             `CategoryList`, `SupplierList`
           Register group: `RegisterDashboard`, `OpenRegister`, `CloseRegister`
           Reports group: `SalesReport`, `StockReport`
           Settings group: `Settings`, `PrinterSettings`, `TaxSettings`, `UserManagement`
- [ ] 7.1.2 — `ZentaNavGraph.kt`: root `NavHost` composable wiring all routes to screen composables;
           `startDestination = ZentaRoute.Login` (redirected to Dashboard if session active)
- [ ] 7.1.3 — `AuthNavGraph.kt`: nested `navigation()` graph for auth flow:
           Login screen → PinLock screen (after idle timeout)
- [ ] 7.1.4 — `MainNavGraph.kt`: nested graph for authenticated area wrapped in `ZentaScaffold`;
           sub-graphs: POS, Inventory, Register, Reports, Settings
- [ ] 7.1.5 — `NavigationItems.kt`: `List<NavItem>` filtered by `RbacEngine.getPermissions(role)`;
           `NavItem(route, icon, label, requiredPermission)` — hidden if user lacks permission
- [ ] 7.1.6 — `NavigationController.kt`: wrapper with `navigate(route: ZentaRoute)`,
           `popBackStack()`, `navigateAndClear(route)` (clears back stack for login/logout)
- [ ] 7.1.7 — Deep link support: `zyntapos://product/{barcode}` → auto-navigate to ProductDetail;
           `zyntapos://order/{orderId}` → OrderHistory (for notification routing)
- [ ] 7.1.8 — Back stack management: `rememberNavController()` scoped ViewModels per nested graph;
           Desktop: no physical back button — ensure `PopBackStack` has safe fallback destinations

---

## ─────────────────────────────────────────
## SPRINT 12-13 — :composeApp:feature:auth
## ─────────────────────────────────────────
> **Plan Ref:** Step 8.1 | **Module:** M08 :composeApp:feature:auth | **Weeks:** W12–W13

### Step 8.1 — Auth MVI + Screens + Session
**Goal:** Login UI, PIN screen, session management, RBAC guards wired end-to-end

- [ ] 8.1.1 — `AuthState.kt`: isLoading, email, password, emailError, passwordError,
           isPasswordVisible, rememberMe, error — all fields with defaults
- [ ] 8.1.2 — `AuthIntent.kt` sealed: `EmailChanged(email)`, `PasswordChanged(password)`,
           `TogglePasswordVisibility`, `LoginClicked`, `RememberMeToggled(checked)`
- [ ] 8.1.3 — `AuthEffect.kt` sealed: `NavigateToDashboard`, `NavigateToRegisterGuard`,
           `ShowError(message: String)`
- [ ] 8.1.4 — `AuthViewModel.kt` (extends `BaseViewModel<AuthState, AuthIntent, AuthEffect>`):
           handles all intents, calls `LoginUseCase`, emits state via `StateFlow<AuthState>`,
           emits one-shot effects via `SharedFlow<AuthEffect>`
- [ ] 8.1.5 — `LoginScreen.kt`: responsive layout:
           EXPANDED: illustration (left 40%) + form (right 60%) — `ZentaSplitPane`
           COMPACT: single pane with ZentaLogo + form
           Fields: email `ZentaTextField`, password with visibility toggle, `ZentaButton` Login
           Biometric prompt trigger (Android), offline banner if network unavailable
- [ ] 8.1.6 — `PinLockScreen.kt`: full-screen PIN overlay, 4–6 digit `ZentaNumericPad(PIN mode)`,
           user avatar + name display, "Different user?" link → full Login
- [ ] 8.1.7 — `SessionGuard.kt`: composable wrapper — collects `AuthRepository.getSession()`,
           if null → `NavigationController.navigateAndClear(Login)`, else shows `content()`
- [ ] 8.1.8 — `RoleGuard.kt`: `@Composable fun RoleGuard(permission, content, unauthorized)` —
           calls `CheckPermissionUseCase`, shows content or "Unauthorized" `ZentaEmptyState`
- [ ] 8.1.9 — `SessionManager.kt`: `CoroutineScope`-based idle timer; resets on any user interaction
           (tap/key event); after `sessionTimeoutMs` emits `AuthEffect.ShowPinLock`;
           configurable via SettingsRepository
- [ ] 8.1.10 — `AuthRepositoryImpl.kt` (data module): local hash validation via `PasswordHasher`,
            JWT caching in `SecurePreferences`, offline fallback (no network = use cached hash)
- [ ] 8.1.11 — Koin `authModule`: provides AuthViewModel (viewModelOf), LoginUseCase,
            LogoutUseCase, ValidatePinUseCase, SessionManager
- [ ] 8.1.12 — Unit tests: AuthViewModel all intent transitions, LoginUseCase (valid/invalid/network err/offline),
            SessionManager timeout simulation (TestScope + `advanceTimeBy`)


---

## ─────────────────────────────────────────
## SPRINT 14-17 — :composeApp:feature:pos
## ─────────────────────────────────────────
> **Plan Ref:** Step 9.1 | **Module:** M09 :composeApp:feature:pos | **Weeks:** W14–W17

### Step 9.1 — POS MVI State Contracts
- [ ] 9.1.0a — `PosState.kt`: products, categories, selectedCategoryId, searchQuery,
            isSearchFocused, cartItems(List<CartItem>), selectedCustomer, orderDiscount,
            orderDiscountType, heldOrders, orderTotals(OrderTotals), isLoading, scannerActive, error
- [ ] 9.1.0b — `PosIntent.kt` sealed: `LoadProducts`, `SelectCategory(id)`, `SearchQueryChanged(q)`,
            `AddToCart(product)`, `RemoveFromCart(productId)`, `UpdateQty(productId, qty)`,
            `ApplyItemDiscount(productId, discount, type)`, `ApplyOrderDiscount(discount, type)`,
            `SelectCustomer(customer)`, `ScanBarcode(barcode)`, `HoldOrder`, `RetrieveHeld(holdId)`,
            `ProcessPayment(method, splits, tendered)`, `ClearCart`, `SetNotes(notes)`
- [ ] 9.1.0c — `PosEffect.kt` sealed: `NavigateToPayment(orderId)`, `ShowReceiptScreen(orderId)`,
            `ShowError(msg)`, `PrintReceipt(orderId)`, `BarcodeNotFound(barcode)`

### Sprint 14 — Product Grid & Search
- [ ] 9.1.1 — `PosViewModel.kt`: root ViewModel — subscribes to `ProductRepository.getAll()` + category flows,
           handles all PosIntent → use case calls → state updates
- [ ] 9.1.2 — `ProductGridSection.kt`: `ZentaGrid` (WindowSizeClass-driven columns) of `ZentaProductCard`;
           `key = { it.id }` for stable recomposition; click → `AddToCart` intent
- [ ] 9.1.3 — `CategoryFilterRow.kt`: horizontally scrollable `LazyRow` of M3 `FilterChip`;
           "All" chip always first; selected category highlighted; `SelectCategory` intent on tap
- [ ] 9.1.4 — `PosSearchBar.kt`: `ZentaSearchBar` with 300ms debounce (`SearchQueryChanged` intent),
           barcode scan icon toggle → `scannerActive=true` state
- [ ] 9.1.5 — `BarcodeInputHandler.kt`: `LaunchedEffect(scannerActive)` → subscribes to
           `BarcodeScanner.scanEvents` → dispatches `ScanBarcode(barcode)` intent;
           ViewModel calls `SearchProductsUseCase` by barcode → auto-add-to-cart if unique match
- [ ] 9.1.6 — `KeyboardShortcutHandler.kt` (Desktop only, jvmMain): `onKeyEvent` handler:
           F2 → focus search, F8 → HoldOrder, F9 → RetrieveHeld, Delete → RemoveFromCart,
           +/- → UpdateQty increment/decrement for selected cart item

### Sprint 15 — Cart
- [ ] 9.1.7 — `CartPanel.kt`: EXPANDED: right-side permanent panel (40% width);
           COMPACT: `ZentaBottomSheet` (draggable); contains CartItemList + CartSummaryFooter
- [ ] 9.1.8 — `CartItemList.kt`: `LazyColumn` of `ZentaCartItemRow`; `SwipeToDismissBox` → remove;
           `key = { it.productId }` for stable recomposition
- [ ] 9.1.9 — `CartSummaryFooter.kt`: subtotal row, tax row, discount row (if > 0),
           total (bold, large), PAY button (`ZentaButton` primary, large); all amounts via `CurrencyFormatter`
- [ ] 9.1.10 — `CustomerSelectorDialog.kt`: debounced search via `CustomerRepository.search()`,
            "Walk-in Customer" default option, quick-add new customer button → `CustomerFormScreen`
- [ ] 9.1.11 — `ItemDiscountDialog.kt`: FLAT/PERCENT toggle, amount input (`ZentaNumericPad`),
            max cap validation from settings, `RoleGuard(APPLY_DISCOUNT)` wrapper
- [ ] 9.1.12 — `OrderDiscountDialog.kt`: same pattern as ItemDiscountDialog at order level
- [ ] 9.1.13 — `OrderNotesDialog.kt`: multiline text field, reference number input, confirm
- [ ] 9.1.14 — `HoldOrderUseCase` integration: F8 shortcut triggers HoldOrder intent;
            `ZentaDialog(Confirm)` before hold; confirmation snackbar with hold ID

### Sprint 16 — Payment Flow
- [ ] 9.1.15 — `PaymentScreen.kt`: full-screen modal/route:
            Left pane (40%): read-only order summary (item list + totals breakdown)
            Right pane (60%): payment method selection + numpad + cash entry
- [ ] 9.1.16 — `PaymentMethodGrid.kt`: Cash/Card/Mobile/Split tile grid (min touch target 56dp height),
            selected method highlighted; `SelectPaymentMethod` intent
- [ ] 9.1.17 — `CashPaymentPanel.kt`: "Amount Received" `ZentaNumericPad(PRICE)`,
            real-time change calculation: `change = tendered - total` (shown in green if ≥ 0)
- [ ] 9.1.18 — `SplitPaymentPanel.kt`: add payment method row button; per-method amount entry;
            remaining amount tracker; validates sum = total before enabling "PAY"
- [ ] 9.1.19 — `ProcessPaymentUseCase` integration: on PAY → validate → create Order →
            decrement stock → enqueue sync → trigger print → emit `ShowReceiptScreen`
- [ ] 9.1.20 — `PaymentSuccessOverlay.kt`: animated checkmark (Compose `animateFloatAsState`),
            success color fill, auto-dismisses after 1.5s → receipt screen

### Sprint 17 — Receipt & Order Management
- [ ] 9.1.21 — `ReceiptScreen.kt`: scrollable text-based receipt preview using
            `EscPosReceiptBuilder.buildReceipt()` output rendered as monospace text;
            action row: Print / Email / Skip buttons
- [ ] 9.1.22 — `EscPosReceiptBuilder.kt` integration (already in :shared:hal):
            `PrintReceiptUseCase.kt` calls `PrinterManager.print(receiptBytes)`,
            handles `HalException` → shows retry `ZentaDialog`
- [ ] 9.1.23 — `PrintReceiptUseCase.kt`: gets `PrinterConfig` from SettingsRepository,
            builds receipt via `EscPosReceiptBuilder`, sends via `PrinterManager`,
            logs to `SecurityAuditLogger.logReceiptPrint(orderId, userId)`
- [ ] 9.1.24 — `HeldOrdersBottomSheet.kt`: `LazyColumn` of held orders (hold time, item count, total);
            tap → `RetrieveHeldOrderUseCase` → restore cart state; F9 shortcut opens
- [ ] 9.1.25 — `OrderHistoryScreen.kt`: today's orders `ZentaTable` (order #, time, items, total, status);
            filter by status chips; tap → order detail; reprint button per row
- [ ] 9.1.26 — Koin `posModule`: provides PosViewModel (viewModelOf), all POS UseCases,
            HAL `PrinterManager` binding, `BarcodeScanner` binding
- [ ] 9.1.27 — Unit tests: `CalculateOrderTotalsUseCase` (all 6 tax modes per §11.3),
            `ProcessPaymentUseCase` (cash exact/overpay/underpay, split valid/invalid),
            `AddItemToCartUseCase` (stock limit enforcement), PosViewModel state transitions


---

## ─────────────────────────────────────────
## SPRINT 18-19 — :composeApp:feature:inventory
## ─────────────────────────────────────────
> **Plan Ref:** Step 10.1 | **Module:** M10 :composeApp:feature:inventory | **Weeks:** W18–W19

### Step 10.1 — Inventory Screens & CRUD

#### Sprint 18 — Products
- [ ] 10.1.1 — `ProductListScreen.kt`: `ZentaTable` (list) + grid toggle button;
           search bar (FTS5 via SearchProductsUseCase), filter by category `FilterChip` row;
           FAB → `ProductDetail(productId=null)` for new product
- [ ] 10.1.2 — `ProductDetailScreen.kt`: create/edit product form:
           name, barcode (scan or type), SKU, category selector, unit selector,
           price/cost price fields, tax group selector, stock qty (read-only or manual entry),
           minStockQty, description, `AsyncImage` picker (Coil + platform file chooser),
           variation management section (add/remove ProductVariant rows), isActive toggle
- [ ] 10.1.3 — `ProductFormValidator.kt`: barcode uniqueness check (`ProductRepository.getByBarcode`),
           SKU uniqueness check, required field validation (name, price, unit, category)
- [ ] 10.1.4 — `BarcodeGeneratorDialog.kt`: generates EAN-13 / Code128 barcode for new/existing products;
           displays as Canvas-drawn barcode preview; prints via `PrinterManager` if confirmed
- [ ] 10.1.5 — `BulkImportDialog.kt`: CSV file picker (platform file chooser),
           column mapping UI (drag-and-drop field assignment),
           preview table of parsed rows, confirm import → batch `CreateProductUseCase`
- [ ] 10.1.6 — `StockAdjustmentDialog.kt`: product search (FTS), increase/decrease/transfer selector,
           quantity `ZentaNumericPad(QUANTITY)`, reason text field,
           confirm → `AdjustStockUseCase` → audit log entry

#### Sprint 19 — Categories, Suppliers, Tax Groups
- [ ] 10.1.7 — `CategoryListScreen.kt`: tree-view `LazyColumn` of categories (indent by depth),
           expand/collapse parent nodes, edit icon per row, FAB for new category
- [ ] 10.1.8 — `CategoryDetailScreen.kt`: name field, parent category selector (dropdown),
           image picker, display order integer field, confirm → insert/update
- [ ] 10.1.9 — `SupplierListScreen.kt`: `ZentaTable` with search, contact info columns,
           FAB → new supplier
- [ ] 10.1.10 — `SupplierDetailScreen.kt`: name, contactPerson, phone, email, address, notes;
            purchase history section (read-only order list filtered by supplierId)
- [ ] 10.1.11 — `UnitManagementScreen.kt`: list of UnitOfMeasure groups, conversion rate editing,
            base unit designation toggle per group
- [ ] 10.1.12 — `TaxGroupScreen.kt`: create/edit tax group (name, rate % field, inclusive toggle),
            used across POS + Inventory
- [ ] 10.1.13 — `LowStockAlertBanner.kt`: persistent `ZentaBadge` banner on Inventory home if
            any product qty < minStockQty; shows count + link to filtered product list
- [ ] 10.1.14 — Koin `inventoryModule` + unit tests:
            `CreateProductUseCase` (barcode unique, SKU unique, valid/invalid),
            `AdjustStockUseCase` (increase, decrease, negative stock prevention),
            `SearchProductsUseCase` (FTS results)

---

## ─────────────────────────────────────────
## SPRINT 20-21 — :composeApp:feature:register
## ─────────────────────────────────────────
> **Plan Ref:** Step 11.1 | **Module:** M11 :composeApp:feature:register | **Weeks:** W20–W21

### Step 11.1 — Cash Register Lifecycle

#### Sprint 20 — Open & Operations
- [ ] 11.1.1 — `RegisterGuard.kt`: on post-login, checks `RegisterRepository.getActive()`;
            if null → redirect to `OpenRegister` route; `SessionGuard` dependency
- [ ] 11.1.2 — `OpenRegisterScreen.kt`: select register from list, enter opening balance via
            `ZentaNumericPad(PRICE)`, confirm → `OpenRegisterSessionUseCase`;
            error state if register already open
- [ ] 11.1.3 — `RegisterDashboardScreen.kt`: current session info card (opened by, opened at, running balance);
            quick stats row: orders today, revenue today;
            "Cash In" / "Cash Out" buttons; movements list below
- [ ] 11.1.4 — `CashInOutDialog.kt`: type (IN/OUT) selector, amount `ZentaNumericPad(PRICE)`,
            reason text field, confirm → `RecordCashMovementUseCase`
- [ ] 11.1.5 — `CashMovementHistory.kt`: `LazyColumn` of `CashMovement` rows
            (type badge, amount, reason, time) for current session

#### Sprint 21 — Close & Z-Report
- [ ] 11.1.6 — `CloseRegisterScreen.kt`:
            Expected balance section: auto-calculated (read-only display)
            Actual balance section: `ZentaNumericPad` entry (or denomination breakdown optional)
            Discrepancy display: difference in red/green, warning if > configurable threshold
            "Close Register" `ZentaButton(Danger)` → `CloseRegisterSessionUseCase`
- [ ] 11.1.7 — `CloseRegisterSessionUseCase` integration: calculates expectedBalance,
            records actualBalance, detects discrepancy, generates Z-report data model
- [ ] 11.1.8 — `ZReportScreen.kt`: printable summary layout:
            Store info header, session info, opening balance, cash in/out list,
            sales total by payment method, expected vs actual, discrepancy line, signature line
- [ ] 11.1.9 — `PrintZReportUseCase.kt`: `EscPosReceiptBuilder.buildZReport(session)` →
            `PrinterManager.print(bytes)` → error handling
- [ ] 11.1.10 — Koin `registerModule` + unit tests:
            `OpenRegisterSessionUseCase` (no active session / already open),
            `CloseRegisterSessionUseCase` (discrepancy detection, expected balance calculation),
            `RecordCashMovementUseCase` (positive amount validation)


---

## ─────────────────────────────────────────
## SPRINT 22 — :composeApp:feature:reports
## ─────────────────────────────────────────
> **Plan Ref:** Step 12.1 | **Module:** M12 :composeApp:feature:reports | **Week:** W22

### Step 12.1 — Sales & Stock Reports

- [ ] 12.1.1 — `ReportsHomeScreen.kt`: tile grid — "Sales Report" and "Stock Report" tiles
           (Phase 1); each tile shows icon, title, last-generated timestamp
- [ ] 12.1.2 — `SalesReportScreen.kt`:
           Date range picker: Today / This Week / This Month / Custom (`DateRangePickerDialog`)
           KPI cards: Total Sales, Order Count, Average Order Value, Top Product
           Sales trend chart: `Canvas`-based line chart (revenue per day in range)
           Payment method breakdown: horizontal bar chart
           Per-product sales table (`ZentaTable`: product name, qty sold, revenue — sortable)
- [ ] 12.1.3 — `GenerateSalesReportUseCase` integration: async with `isLoading` state,
           results cached in ViewModel (don't re-query on recomposition)
- [ ] 12.1.4 — `StockReportScreen.kt`:
           Current stock levels `ZentaTable` (product, category, qty, value, status badge)
           Low stock section: items where qty < minStockQty (highlighted in amber)
           Dead stock section: items with no movement in 30 days (highlighted in gray)
           Category filter `FilterChip` row
- [ ] 12.1.5 — `GenerateStockReportUseCase` integration: async load, handles 10K+ products via
           paged SQLDelight query
- [ ] 12.1.6 — `DateRangePickerBar.kt`: reusable composable with preset chips + custom date range
           `DatePickerDialog` from M3 for start/end date selection
- [ ] 12.1.7 — `ReportExporter.kt` (expect/actual):
           JVM actual: write CSV/PDF to user-selected directory (`JFileChooser`)
           Android actual: generate file → share via `Intent.ACTION_SEND` / `ShareSheet`
           CSV: simple comma-delimited text
           PDF: JVM uses Apache PDFBox; Android uses HTML template → print to PDF
- [ ] 12.1.8 — `PrintReportUseCase.kt`: condensed thermal format for Z-report summary → `PrinterManager`
- [ ] 12.1.9 — Koin `reportsModule` + unit tests:
           `GenerateSalesReportUseCase` (date range, aggregation correctness),
           `GenerateStockReportUseCase` (low stock detection, dead stock detection)

---

## ─────────────────────────────────────────
## SPRINT 23 — :composeApp:feature:settings
## ─────────────────────────────────────────
> **Plan Ref:** Step 13.1 | **Module:** M18 :composeApp:feature:settings | **Week:** W23

### Step 13.1 — Settings Screens

- [ ] 13.1.1 — `SettingsHomeScreen.kt`: grouped card layout with categories:
           General, POS, Tax, Printer, Users, Security, Backup, Appearance, About
- [ ] 13.1.2 — `GeneralSettingsScreen.kt`: store name, address, phone, logo upload (Coil AsyncImage),
           currency selector (LKR/USD/EUR for Phase 1), timezone selector,
           date format selector, language (English only Phase 1)
- [ ] 13.1.3 — `PosSettingsScreen.kt`: default order type (SALE/REFUND), auto-print receipt toggle,
           tax display mode (inclusive/exclusive shown to customer),
           receipt template selector (standard/minimal), max discount % setting
- [ ] 13.1.4 — `TaxSettingsScreen.kt`: `ZentaTable` of tax groups with edit icon per row;
           FAB → `TaxGroupScreen` for new tax group; delete with `ZentaDialog(Confirm)`
- [ ] 13.1.5 — `PrinterSettingsScreen.kt`:
           Printer type selector: USB / Bluetooth / Serial / TCP
           Connection params (conditional): Port/IP+Port / COM port+baud rate / BT device selector
           Paper width selector: 58mm / 80mm
           "Test Print" `ZentaButton` → `PrintTestPageUseCase` (prints built-in test page)
           Receipt customization: header lines editor (up to 5), footer lines, show/hide fields toggles
- [ ] 13.1.6 — `UserManagementScreen.kt`: `ZentaTable` of users (name, email, role, status);
           create/edit user slide-over (name, email, password, role selector, isActive toggle);
           gated by `RoleGuard(MANAGE_USERS)` (ADMIN only)
- [ ] 13.1.7 — `BackupSettingsScreen.kt`: manual backup trigger button → export encrypted DB file;
           last backup timestamp display; "Restore from backup" file picker + confirmation dialog
- [ ] 13.1.8 — `AboutScreen.kt`: app name, version (from BuildConfig), build date,
           open-source licenses list (`LazyColumn`), support contact
- [ ] 13.1.9 — `AppearanceSettingsScreen.kt`: Light / Dark / System default `RadioButton` group;
           selected theme stored in SettingsRepository → triggers `ZentaTheme` recomposition
- [ ] 13.1.10 — Koin `settingsModule` + `SettingsViewModel`: CRUD settings via `SettingsRepository`,
            handles all settings-related intents/state/effects


---

## ─────────────────────────────────────────
## SPRINT 24 — Integration, QA & Release Prep
## ─────────────────────────────────────────
> **Plan Ref:** Step 14.1 | **Week:** W24

### Step 14.1 — Integration QA & Release Packaging

#### End-to-End Test Runs
- [ ] 14.1.1 — Full E2E flow test (manual + automated):
           Login → Open Register → POS: search product + scan barcode + add to cart →
           Apply discount → Select customer → Payment (cash + split) →
           Print receipt → Order in history → Close Register → Z-Report
- [ ] 14.1.2 — Offline E2E: disable network → full POS sale → re-enable network →
           verify sync queue empties and server confirms data

#### Performance Validation (§12 targets)
- [ ] 14.1.3 — Cold start measurement: Android (Pixel 4 equiv) → POS screen < 3s
           (Android Profiler startup trace)
- [ ] 14.1.4 — Product search benchmark: 10K products in DB → FTS5 query < 200ms
           (SQLDelight benchmark test + Profiler)
- [ ] 14.1.5 — Add-to-cart recomposition: Compose compiler metrics — confirm < 50ms,
           no unnecessary recompositions in `ProductGrid` or `CartItemList`
- [ ] 14.1.6 — Payment processing timing: `ProcessPaymentUseCase` isolated execution < 800ms
- [ ] 14.1.7 — Receipt print trigger: `PrinterManager.print()` → HAL callback < 2s

#### Security Validation
- [ ] 14.1.8 — SQLCipher verification: open DB file with SQLite Browser (no password) → must fail;
           open with correct key → succeeds
- [ ] 14.1.9 — Android Keystore: confirm DB key not extractable (`KeyInfo.isInsideSecureHardware`)
- [ ] 14.1.10 — Audit log hash chain: verify no gaps, each entry references previous hash correctly
- [ ] 14.1.11 — RBAC smoke test: CASHIER cannot access Settings/UserManagement/Reports;
            STORE_MANAGER can access Reports; ADMIN can access all

#### UI Quality Audit
- [ ] 14.1.12 — Dark mode audit: every screen in every module rendered in dark mode — no hardcoded colors
- [ ] 14.1.13 — Responsive layout audit: test all screens at:
            Compact (360dp), Medium (720dp), Expanded (1280dp) — no overflow, no clipped text
- [ ] 14.1.14 — Desktop keyboard shortcut audit: all shortcuts per UI/UX plan §23 functional
- [ ] 14.1.15 — Memory profiling: Android Profiler heap dump during active POS session < 256MB

#### Build & Release
- [ ] 14.1.16 — Android APK release build: configure release signing (`signingConfigs.release`
            in `:androidApp/build.gradle.kts`), minSdk=24, targetSdk=35, R8/ProGuard enabled
- [ ] 14.1.17 — ProGuard rules review: keep KMP serialization classes, Koin reflective lookups,
            SQLDelight generated classes — test release APK full E2E flow
- [ ] 14.1.18 — Desktop distributable via jpackage:
            Windows: MSI installer (`jpackage --type msi`)
            macOS: DMG (`jpackage --type dmg`)
            Linux: DEB (`jpackage --type deb`)
            JVM 17 runtime bundled, app icon configured
- [ ] 14.1.19 — CI/CD validation: GitHub Actions pipeline runs clean on `main` branch:
            unit tests pass, APK builds, Desktop JAR builds
- [ ] 14.1.20 — Final `execution_log.md` audit: confirm ALL Sprint 1–24 steps marked `[x] Finished`
            with timestamps; no `[ ]` remaining in Phase 1

---

## ═══════════════════════════════════════════
## PHASE 2 — GROWTH (Months 7–12)
## ═══════════════════════════════════════════
> **Goal:** Multi-store, CRM, promotions, financial tools, CRDT sync
> **Status:** 🔴 NOT STARTED (Blocked on Phase 1 completion)

- [ ] Phase 2 planning will be detailed in `PLAN_PHASE2.md` upon Phase 1 completion

---

## ═══════════════════════════════════════════
## PHASE 3 — ENTERPRISE (Months 13–18)
## ═══════════════════════════════════════════
> **Goal:** Full enterprise features, compliance, staff management, administration
> **Status:** 🔴 NOT STARTED (Blocked on Phase 2 completion)

- [ ] Phase 3 planning will be detailed in `PLAN_PHASE3.md` upon Phase 2 completion

---

## 📋 CROSS-CUTTING CONCERNS (All Phases)

### Security (Ongoing)
- [ ] SEC.1 — Regular dependency vulnerability scan (Gradle Versions Plugin / Dependabot alerts)
- [ ] SEC.2 — No hardcoded secrets: all via `local.properties` + Secrets Gradle Plugin
- [ ] SEC.3 — Certificate pinning for Ktor client (production builds only)

### Testing Infrastructure (Ongoing)
- [ ] TEST.1 — `commonTest`: Kotlin Test + Mockative stubs for all repository interfaces
- [ ] TEST.2 — Compose UI test harness: both Android (`createAndroidComposeRule`) and Desktop targets
- [ ] TEST.3 — Kover code coverage: enforce 85%+ on shared modules; 80%+ on feature ViewModels

### CI/CD
- [ ] CI.1 — GitHub Actions: build + unit test on every PR (`.github/workflows/ci.yml`)
- [ ] CI.2 — GitHub Actions: assemble Android APK + Desktop JAR on `main` push
- [ ] CI.3 — GitHub Secrets → Gradle build environment injection (API_BASE_URL, SIGNING_KEY, etc.)

---

## 📝 Session Notes

| Date | Note |
|------|------|
| 2026-02-20 | Project audit complete. Baseline: KMP skeleton only. All modules pending. Execution log created. Ready to begin Phase 0. |
| 2026-02-20 | Phase 0 BUILD SUCCESSFUL in 12s (63 tasks: assembleDebug ✅ jvmJar ✅) |
| 2026-02-20 | HOTFIX: AGP deprecation warnings resolved (FIX.1–FIX.6). Module structure stabilized. |
| 2026-02-20 | **Log v1.1 update:** Phase 1 tasks re-mapped to PLAN_PHASE1.md atomic step numbering (Step 1.1.x → Step 14.1.x). Sprint-aligned, ~450+ atomic tasks documented. |
| 2026-02-20 | **FIX-01 COMPLETE:** 13 feature modules created (auth, pos, inventory, register, reports, settings, customers, coupons, expenses, staff, multistore, admin, media). `./gradlew tasks --all` — zero "project path not found" errors. All modules visible in task graph. BUILD SUCCESSFUL in 8s. |
| 2026-02-20 | **FIX-02 COMPLETE:** Module name canonicalization verified. Master_plan.md has 4 occurrences of `:crm` that should be `:customers` (lines 139, 216, 249, 895). settings.gradle.kts correctly uses `:composeApp:feature:customers`. Documentation fix report created at `docs/FIX-02_MODULE_NAME_CANONICALIZATION.md`. |
| 2026-02-20 | **FIX-02 APPLIED:** All 4 occurrences of `:crm` → `:customers` successfully updated in `docs/plans/Master_plan.md`. Changes: Line 139 (module tree), Line 216 (dependency table), Line 249 (diagram), Line 895 (Phase 2 checklist). Verification docs at `docs/FIX-02_INTEGRITY_VERIFICATION.md`. |
| 2026-02-20 | **FIX-03 COMPLETE:** Removed duplicate Android resources from :composeApp. Deleted composeApp/src/androidMain/res/ (15 files in 9 dirs). Verified library manifest is bare (no <application>). Confirmed :androidApp icons intact. Resource merge conflict resolved. |

---

## 📊 Phase 1 Progress Tracker

| Sprint | Module | Steps | Done | Status |
|--------|--------|-------|------|--------|
| 1 | Project Scaffold | 1.1.1–1.1.8 | 0/8 | 🔴 |
| 2 | :shared:core | 1.2.1–1.2.14 | 0/14 | 🔴 |
| 3 | :shared:domain (Models) | 2.1.1–2.1.24 | 0/24 | 🔴 |
| 4 | :shared:domain (UseCases) | 2.2.1–2.3.27 | 0/37 | 🔴 |
| 5 | :shared:data (Schema) | 3.1.1–3.2.5 | 0/17 | 🔴 |
| 6 | :shared:data (Repos+Ktor) | 3.3.1–3.4.7 | 0/17 | 🔴 |
| 7 | :shared:hal | 4.1.1–4.2.11 | 0/17 | 🔴 |
| 8 | :shared:security | 5.1.1–5.1.10 | 0/10 | 🔴 |
| 9 | :designsystem (Theme) | 6.1.1–6.1.7 | 0/7 | 🔴 |
| 10 | :designsystem (Components) | 6.2.1–6.3.5 | 0/20 | 🔴 |
| 11 | :navigation | 7.1.1–7.1.8 | 0/8 | 🔴 |
| 12–13 | :feature:auth | 8.1.1–8.1.12 | 0/12 | 🔴 |
| 14–17 | :feature:pos | 9.1.0–9.1.27 | 0/30 | 🔴 |
| 18–19 | :feature:inventory | 10.1.1–10.1.14 | 0/14 | 🔴 |
| 20–21 | :feature:register | 11.1.1–11.1.10 | 0/10 | 🔴 |
| 22 | :feature:reports | 12.1.1–12.1.9 | 0/9 | 🔴 |
| 23 | :feature:settings | 13.1.1–13.1.10 | 0/10 | 🔴 |
| 24 | Integration QA & Release | 14.1.1–14.1.20 | 0/20 | 🔴 |

**Phase 1 Total:** ~285 atomic steps (excludes sub-bullets) | **Completed:** 0 | **Remaining:** 285

---

## ═══════════════════════════════════════════
## HOTFIX FIX-02 — Module Name Canonicalization
## ═══════════════════════════════════════════
> **Issue:** Master_plan.md references :composeApp:feature:crm but settings.gradle.kts has :composeApp:feature:customers
> **Decision:** Use :customers (more descriptive, already in settings)
> **Status:** 🟢 COMPLETE

- [x] FIX-02.01 — Edit Master_plan.md §3.2: change :crm → :customers | 2026-02-20 — APPLIED: All 4 changes (lines 139, 216, 249, 895) successfully updated
- [x] FIX-02.02 — Search all plan docs for :crm references and update | 2026-02-20 — Searched all plan docs: 0 occurrences in PLAN_PHASE1.md, 4 in Master_plan.md (all fixed)
- [x] FIX-02.03 — Confirm settings.gradle.kts has :customers ✅ | 2026-02-20 — VERIFIED: line ~133 includes :composeApp:feature:customers
- [x] FIX-02.04 — Generate completion documentation | 2026-02-20 — Created: FIX-02_COMPLETION_SUMMARY.md, FIX-02_INTEGRITY_VERIFICATION.md

---

## ═══════════════════════════════════════════
## HOTFIX FIX-03 — Remove Duplicate Android Resources
## ═══════════════════════════════════════════
> **Issue:** :composeApp (KMP library) contains duplicate Android launcher icons and app_name string causing APK resource merge conflicts
> **Solution:** Delete entire composeApp/src/androidMain/res/ directory; verify library manifest has no <application> block
> **Status:** 🟢 COMPLETE

- [x] FIX-03.01 — Delete entire composeApp/src/androidMain/res/ directory (15 files in 9 directories) | 2026-02-20 — DELETED: All launcher icons and strings.xml removed from library module
- [x] FIX-03.02 — Verify composeApp/src/androidMain/AndroidManifest.xml has NO <application> block | 2026-02-20 — VERIFIED: Bare library manifest with only <manifest xmlns:android=...> root element
- [x] FIX-03.03 — Confirm androidApp/src/main/res/ still has all launcher icons intact | 2026-02-20 — VERIFIED: All mipmap densities (mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi) + anydpi-v26 icons present in :androidApp

---

---

## ═══════════════════════════════════════════
## HOTFIX FIX-04 — Create Missing jvmMain Source Set Directories
## ═══════════════════════════════════════════
> **Issue:** `:composeApp:designsystem` and `:composeApp:navigation` declare `jvm()` target but `jvmMain/kotlin/` dirs were missing on disk. Sprint 9 (WindowSizeClassHelper Desktop actual) and Sprint 11 (Desktop nav handling) would fail without this.
> **Solution:** Create missing `jvmMain` and `androidMain` source set directories with `.gitkeep` placeholders.
> **Status:** 🟢 COMPLETE

- [x] FIX-04.01 — Create `composeApp/designsystem/src/jvmMain/kotlin/com/zynta/pos/designsystem/` + `.gitkeep` | 2026-02-20 — CREATED: Directory + .gitkeep verified via `find`
- [x] FIX-04.02 — Create `composeApp/navigation/src/jvmMain/kotlin/com/zynta/pos/navigation/` + `.gitkeep` | 2026-02-20 — CREATED: Directory + .gitkeep verified via `find`
- [x] FIX-04.03 — Create `composeApp/navigation/src/androidMain/kotlin/com/zynta/pos/navigation/` + `.gitkeep` | 2026-02-20 — CREATED: Directory + .gitkeep verified via `find`

**Integrity Check:** `find` output confirmed all 3 `.gitkeep` files at correct package paths. Source set resolution for `jvmMain` and `androidMain` in both modules is now unblocked.

---

---

## ─────────────────────────────────────────
## FIX-05 — Move Platform expect/actual Files to :shared:core
## ─────────────────────────────────────────
> **Source:** MM-12 | **Severity:** 🟠 HIGH | **Session:** 2026-02-20 | **Status:** ✅ COMPLETE

- [x] Finished: FIX-05.01 — Move `Platform.kt` → `shared/core/src/commonMain/kotlin/com/zynta/pos/core/Platform.kt` | 2026-02-20
- [x] Finished: FIX-05.02 — Move `Platform.android.kt` → `shared/core/src/androidMain/kotlin/com/zynta/pos/core/Platform.android.kt` | 2026-02-20
- [x] Finished: FIX-05.03 — Move `Platform.jvm.kt` → `shared/core/src/jvmMain/kotlin/com/zynta/pos/core/Platform.jvm.kt` | 2026-02-20
- [x] Finished: FIX-05.04 — Delete `composeApp/src/commonMain/kotlin/com/zynta/pos/Greeting.kt` | 2026-02-20
- [x] Finished: FIX-05.05 — Deleted composeApp Platform stubs; rewrote `App.kt` — removed Greeting import/usage, removed Platform imports; clean shell composable | 2026-02-20
- [x] Finished: FIX-05.06 — Added `implementation(project(":shared:core"))` to `composeApp/build.gradle.kts` commonMain.dependencies | 2026-02-20

**Integrity check passed:** 3 Platform*.kt files confirmed in :shared:core only; Greeting.kt confirmed deleted (0 results); App.kt confirmed clean (Greeting appears only in KDoc comment, zero import/code refs); :shared:core dep confirmed in composeApp/build.gradle.kts.

---

## ═══════════════════════════════════════════
## HOTFIX FIX-06 — Create CI/CD GitHub Actions Workflow
## ═══════════════════════════════════════════
> **Source:** MM-03 + CRITICAL-4/FIX-D.3 | **Severity:** 🟠 HIGH | **Session:** 2026-02-20
> **Sprint 1 task 1.1.7** — Required for team development.
> **Status:** 🟢 COMPLETE

- [x] Finished: FIX-06.01 — Create `.github/workflows/` directory | 2026-02-20
- [x] Finished: FIX-06.02 — Create `.github/workflows/ci.yml` with full build + test pipeline | 2026-02-20

---

## ═══════════════════════════════════════════
## HOTFIX FIX-07 — Complete :shared:core Internal Sub-package Directories
## ═══════════════════════════════════════════
> **Source:** MM-07 (MISMATCH_FIX_v1.0 MINOR-1 misclassified) | **Severity:** 🟡 MEDIUM
> **Reason:** Sub-dirs must pre-exist for IDE autocomplete + match PLAN_PHASE1.md Appendix B
> **Status:** 🟢 COMPLETE

- [x] Finished: FIX-07.01 — Create `core/result/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-07.02 — Create `core/logger/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-07.03 — Create `core/config/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-07.04 — Create `core/extensions/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-07.05 — Create `core/utils/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-07.06 — Create `core/mvi/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-07.07 — Create `core/di/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-07.08 — Move `CoreModule.kt` root → `core/di/CoreModule.kt`; package updated to `com.zynta.pos.core.di`; old root file deleted | 2026-02-20

---

## ═══════════════════════════════════════════
## HOTFIX FIX-08 — Create :shared:domain `validation/` Sub-directory
## ═══════════════════════════════════════════
> **Source:** MM-08 (NEW) | **Severity:** 🟡 MEDIUM
> **Status:** 🟢 COMPLETE

- [x] Finished: FIX-08.01 — Create `shared/domain/src/commonMain/kotlin/com/zynta/pos/domain/validation/` + `.gitkeep` | 2026-02-20

---

## ═══════════════════════════════════════════
## HOTFIX FIX-09 — Create :shared:data Missing Sub-directories
## ═══════════════════════════════════════════
> **Source:** MM-09 (NEW) | **Severity:** 🟡 MEDIUM
> **Status:** 🟢 COMPLETE

- [x] Finished: FIX-09.01 — Create `data/local/db/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-09.02 — Create `data/local/mapper/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-09.03 — Create `data/remote/api/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-09.04 — Create `data/remote/dto/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-09.05 — Create `data/sync/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-09.06 — Move `DataModule.kt` root → `data/di/DataModule.kt`; package updated to `com.zynta.pos.data.di`; old root file deleted | 2026-02-20

---

## ═══════════════════════════════════════════
## HOTFIX FIX-10 — Create :shared:hal printer/ and scanner/ Sub-directories
## ═══════════════════════════════════════════
> **Source:** MM-10 (NEW) | **Severity:** 🟡 MEDIUM
> **Status:** 🟢 COMPLETE

- [x] Finished: FIX-10.01 — Create `commonMain/.../hal/printer/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-10.02 — Create `commonMain/.../hal/scanner/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-10.03 — Create `androidMain/.../hal/printer/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-10.04 — Create `androidMain/.../hal/scanner/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-10.05 — Create `jvmMain/.../hal/printer/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-10.06 — Create `jvmMain/.../hal/scanner/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-10.07 — Move `HalModule.kt` root → `hal/di/HalModule.kt` (commonMain); `di/` created in all 3 source sets; package updated to `com.zynta.pos.hal.di`; old root file deleted | 2026-02-20

---

## ═══════════════════════════════════════════
## HOTFIX FIX-11 — Create :shared:security Crypto Sub-directories
## ═══════════════════════════════════════════
> **Source:** MM-11 (NEW) | **Severity:** 🟡 MEDIUM
> **Status:** 🟢 COMPLETE

- [x] Finished: FIX-11.01 — Create `commonMain/.../security/crypto/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-11.02 — Create `commonMain/.../security/token/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-11.03 — Create `commonMain/.../security/keystore/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-11.04 — Create `androidMain/.../security/crypto/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-11.05 — Create `androidMain/.../security/keystore/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-11.06 — Create `jvmMain/.../security/crypto/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-11.07 — Create `jvmMain/.../security/keystore/` + `.gitkeep` | 2026-02-20
- [x] Finished: FIX-11.08 — Move `SecurityModule.kt` root → `security/di/SecurityModule.kt`; `di/` created in all 3 source sets; package updated to `com.zynta.pos.security.di`; old root file deleted | 2026-02-20

---

## ═══════════════════════════════════════════
## HOTFIX FIX-12 — Add Missing Library Dependencies to Version Catalog
## ═══════════════════════════════════════════
> **Source:** CRITICAL-3 (from MISMATCH_FIX_v1.0) | **Severity:** 🟠 HIGH
> **Status:** 🟢 COMPLETE

- [x] Finished: FIX-12.01 — Added `jserialcomm = "2.10.4"` to `libs.versions.toml [versions]` | 2026-02-20
- [x] Finished: FIX-12.02 — Added `jbcrypt = "0.4"` to `libs.versions.toml [versions]` | 2026-02-20
- [x] Finished: FIX-12.03 — Added `jserialcomm = { module = "com.fazecast:jSerialComm", version.ref = "jserialcomm" }` to `[libraries]` | 2026-02-20
- [x] Finished: FIX-12.04 — Added `jbcrypt = { module = "org.mindrot:jbcrypt", version.ref = "jbcrypt" }` to `[libraries]` | 2026-02-20
- [x] Finished: FIX-12.05 — Documented: Desktop DB encryption strategy = **JCE AES-256-GCM at application layer** (encrypts data before SQLite write / decrypts on read). SQLCipher JDBC is NOT used for Desktop JVM. Android continues to use `net.zetetic:sqlcipher-android` via the existing `sqlcipher-android` catalog entry. | 2026-02-20

---

## ═══════════════════════════════════════════
## HOTFIX FIX-13 — Reconcile execution_log.md Sprint 1 Statuses
## ═══════════════════════════════════════════
> **Source:** CRITICAL-4 (from MISMATCH_FIX_v1.0) | **Severity:** 🟠 MEDIUM
> **Status:** 🟢 COMPLETE

- [x] Finished: FIX-13.01 — Marked Sprint 1 tasks 1.1.1–1.1.6 + 1.1.8 as `[x] Finished (Completed in Phase 0)` | 2026-02-20
- [x] Finished: FIX-13.02 — Annotated 1.1.2 with actual versions: kotlin=2.3.0, agp=8.13.2, composeMp=1.10.0 (plan estimates were outdated) | 2026-02-20
- [x] Finished: FIX-13.03 — Task 1.1.7 (GitHub Actions CI) left as `[ ]` — pending, covered by FIX-06 | 2026-02-20

---

## ═══════════════════════════════════════════
## HOTFIX FIX-14 — Document Namespace + Undocumented Plugin in execution_log.md
## ═══════════════════════════════════════════
> **Source:** CRITICAL-2 + CRITICAL-5 (from MISMATCH_FIX_v1.0) | **Severity:** 🟡 MEDIUM
> **Status:** 🟢 COMPLETE

- [x] Finished: FIX-14.01 — Added canonical namespace note to log header: Root Package = `com.zynta.pos`; all Sprint 2–24 paths using `com/zentapos/` → read as `com/zynta/pos/` | 2026-02-20
- [x] Finished: FIX-14.02 — Added session note to log header: `composeHotReload = "1.0.0"` is an undocumented addition retained for desktop hot-reload DX support | 2026-02-20
- [x] Finished: FIX-14.03 — Updated `Reference Plan` in log header from `ZENTA-PLAN-PHASE1-v1.0` → `docs/plans/PLAN_PHASE1.md` | 2026-02-20

---

---

## ═══════════════════════════════════════════
## NAMESPACE FIX — Canonical Package Standardisation
## ═══════════════════════════════════════════
> **Plan Ref:** `docs/plans/PLAN_NAMESPACE_FIX_v1.0.md`
> **Old Package:** `com.zynta.pos`
> **New Package:** `com.zyntasolutions.zyntapos`
> **Status:** 🟡 IN PROGRESS

- [x] Finished: NS-1 — Read all 11 remaining feature build.gradle.kts files. Result: ALL 11 use identical namespace pattern `com.zynta.pos.feature.<n>` — no exceptions, no surprises. Safe to proceed with bulk rename. | 2026-02-20
- [x] Finished: NS-2 — Updated all 22 Group A build.gradle.kts files. Replaced `com.zynta.pos` → `com.zyntasolutions.zyntapos` across namespace, applicationId, mainClass, packageName (desktop), SQLDelight packageName. Verified: grep returns zero residual matches. | 2026-02-20
- [x] Finished: NS-3 — Created 65 new directories under com/zyntasolutions/zyntapos/… across all 26 source sets. Verified: find returns all expected paths, zero build/ contamination. | 2026-02-20
- [x] Finished: NS-4 — Moved all 26 Group B .kt files to com/zyntasolutions/zyntapos/… paths and patched all package declarations via sed. Verified: 0 residual `com.zynta.pos` package lines, 26 correct `com.zyntasolutions.zyntapos` declarations confirmed. | 2026-02-20
- [x] Finished: NS-5 — Migrated 50 .gitkeep placeholder files to com/zyntasolutions/zyntapos/… paths, then deleted all 44 old com/zynta/ directory trees (source + sqldelight). Verified: 0 com/zynta/ dirs or files remain outside build/. | 2026-02-20
- [x] Finished: NS-6 — Updated all 3 Group C documentation files. execution_log.md: title, header namespace note, path examples, keystore filename, deep link scheme, footer (10 historical [x] entries left intact as audit trail). PLAN_PHASE1.md: title, 2 path examples, KDoc comment, package tree, footer — 0 residuals. Master_plan.md: title, description, design system label, UI mockup, footer — 0 residuals. | 2026-02-20
- [x] Finished: NS-7 — Clean Gradle cache + verification build: BUILD SUCCESSFUL in 43s (117 tasks: 66 executed, 10 from cache, 41 up-to-date). Root-cause fix: `org.jetbrains.compose.material3:material3:1.10.0` has no stable Maven artifact — replaced `libs.compose.material3` / `libs.compose.material.icons.extended` with plugin accessors `compose.material3` / `compose.materialIconsExtended` across all 16 build.gradle.kts files. ZERO errors. | 2026-02-20
- [x] Finished: NS-8 — Final audit complete. All NS steps [x]. README.md updated with brand vs code-name clarification sentence. PLAN_STRUCTURE_CROSSCHECK_v1.0.md updated: package namespace status → ✅ RESOLVED, project name → ✅ DOCUMENTED, recommended actions #1 and #2 → struck through as done. Historical plan docs (PLAN_NAMESPACE_FIX, PLAN_CONSOLIDATED_FIX, PLAN_MISMATCH_FIX) preserved unchanged as audit trail. | 2026-02-20

---

## ═══════════════════════════════════════════
## PRE-SPRINT 4 — COMPATIBILITY VERIFICATION & ONBOARDING HARDENING
## ═══════════════════════════════════════════
> **Ref:** PLAN_STRUCTURE_CROSSCHECK_v1.0.md §7 items [LOW] #3 and #4
> **Scope:** (A) Kotlin 2.3.0 compatibility audit vs Sprint 4–24 API patterns
>            (B) local.properties.template onboarding reminder in README.md
> **Status:** 🟡 IN PROGRESS

- [x] Finished: COMPAT-1 — Sprint 4 domain APIs fully audited: Flow/StateFlow/SharedFlow (coroutines 1.10.2 stable), suspend fun interfaces (Kotlin 2.3.0 core), custom Result<T> sealed class (no kotlin.Result collision), @Serializable on domain models (serialization 1.8.0 + plugin), kotlinx.datetime 0.6.1 (stable KMP). ZERO blockers for Sprint 4. | 2026-02-20
- [x] Finished: COMPAT-2 — Sprints 5–13 audited. Key findings: (a) kotlin.uuid.Uuid remains @ExperimentalUuidApi in K2.3.0 — @OptIn correct as-is; (b) BaseViewModel must extend KMP ViewModel() before Sprint 12 — currently extends AutoCloseable; (c) Ktor retry = HttpRequestRetry class (confirmed in 3.0.3 jar); (d) security-crypto 1.1.0-alpha06 is alpha — evaluate at Sprint 8; (e) compose-adaptive 1.1.0-alpha04 is alpha — evaluate at Sprint 9; (f) Dispatchers.setMain() test pattern required for all ViewModel tests Sprint 12+. | 2026-02-20
- [x] Finished: COMPAT-3 — Created docs/plans/PLAN_COMPAT_VERIFICATION_v1.0.md (293 lines). Contains: pinned version matrix, Sprint-by-Sprint assessment table, 4 deferred action items (COMPAT-FIX-1..4), code patterns for test setup, BaseViewModel migration template, Ktor HttpRequestRetry usage. | 2026-02-20
- [x] Finished: ONBOARD-1 — Added prominent ⚠️ callout block to README.md §2 "Configure local secrets": blockquote reads "Required before first build — project will not compile without local.properties. This file is git-ignored and must never be committed." | 2026-02-20
- [x] Finished: ONBOARD-2 — Verified template vs README key table vs PLAN_PHASE1.md. Gap found: README was missing ZYNTA_IRD_CLIENT_CERTIFICATE_PATH and ZYNTA_IRD_CERTIFICATE_PASSWORD (both present in template). Added both rows to README key table. README (11 keys) now matches template (11 keys) exactly. | 2026-02-20
- [x] Finished: ONBOARD-3 — PLAN_STRUCTURE_CROSSCHECK_v1.0.md §7 items #3 and #4 struck through with ✅ completion notes referencing PLAN_COMPAT_VERIFICATION_v1.0.md and README changes. Banner added: "All pre-Sprint 4 actions complete. Zero open items." Footer updated. | 2026-02-20

> **Section status: ✅ ALL 6 TASKS COMPLETE**
> **PRE-SPRINT 4 — COMPATIBILITY VERIFICATION & ONBOARDING HARDENING: DONE**

---

---

## ═══════════════════════════════════════════
## SPRINT 3–4 — `:shared:domain` — Step 2.1: Domain Models
## ═══════════════════════════════════════════
> **Plan Ref:** `docs/plans/PLAN_PHASE1.md` §Sprint 3–4 / Step 2.1
> **Scope:** 14 pure-Kotlin domain model files (2.1.11 – 2.1.24) in
>            `shared/domain/src/commonMain/kotlin/com/zyntasolutions/zyntapos/domain/model/`
> **Status:** ✅ COMPLETE — All files present, verified, and plan-aligned

- [x] Finished: 2.1.11 — `OrderItem.kt`: id, orderId, productId, productName(snapshot), unitPrice, quantity, discount, discountType, taxRate, taxAmount, lineTotal. All fields verified. DiscountType cross-ref correct. | 2026-02-20
- [x] Finished: 2.1.12 — `OrderType.kt`: enum SALE, REFUND, HOLD. KDoc on all entries. | 2026-02-20
- [x] Finished: 2.1.13 — `OrderStatus.kt`: enum COMPLETED, VOIDED, HELD, IN_PROGRESS. KDoc on all entries. | 2026-02-20
- [x] Finished: 2.1.14 — `PaymentMethod.kt`: enum CASH, CARD, MOBILE, BANK_TRANSFER, SPLIT. KDoc on all entries. | 2026-02-20
- [x] Finished: 2.1.15 — `PaymentSplit.kt`: data class method(PaymentMethod), amount(Double). Guard: amount > 0, method ≠ SPLIT. | 2026-02-20
- [x] Finished: 2.1.16 — `CashRegister.kt`: id, name, storeId, currentSessionId(nullable), isActive. | 2026-02-20
- [x] Finished: 2.1.17 — `RegisterSession.kt`: id, registerId, openedBy, closedBy(nullable), openingBalance, closingBalance(nullable), expectedBalance, actualBalance(nullable), openedAt(Instant), closedAt(Instant?), status(nested Status enum OPEN/CLOSED). | 2026-02-20
- [x] Finished: 2.1.18 — `CashMovement.kt`: id, sessionId, type(nested Type enum IN/OUT), amount, reason, recordedBy, timestamp(Instant). Guard: amount > 0. | 2026-02-20
- [x] Finished: 2.1.19 — `Supplier.kt`: id, name, contactPerson(nullable), phone(nullable), email(nullable), address(nullable), notes(nullable), isActive. | 2026-02-20
- [x] Finished: 2.1.20 — `StockAdjustment.kt`: id, productId, type(nested Type enum INCREASE/DECREASE/TRANSFER), quantity, reason, adjustedBy, timestamp(Instant), syncStatus(SyncStatus). Guard: quantity > 0. | 2026-02-20
- [x] Finished: 2.1.21 — `SyncStatus.kt`: data class State enum (PENDING/SYNCING/SYNCED/FAILED) + retryCount, lastAttempt(Long?). Companion: pending(), synced() factory fns. | 2026-02-20
- [x] Finished: 2.1.22 — `CartItem.kt`: productId, productName, unitPrice, quantity, discount, discountType(FIXED/PERCENT), taxRate, lineTotal. Transient (not persisted). Guard: quantity ≥ 1. | 2026-02-20
- [x] Finished: 2.1.23 — `DiscountType.kt`: enum FIXED, PERCENT. | 2026-02-20
- [x] Finished: 2.1.24 — `OrderTotals.kt`: subtotal, taxAmount, discountAmount, total, itemCount. Computed value object. EMPTY companion factory. | 2026-02-20

### Integrity Verification Summary
| Check | Result |
|---|---|
| All 14 files present in `domain/model/` | ✅ PASS |
| Zero framework imports (pure Kotlin + kotlinx.datetime only) | ✅ PASS |
| All plan-specified fields present with correct types | ✅ PASS |
| Nullable fields match plan spec (closedBy, closingBalance, etc.) | ✅ PASS |
| Enum values match plan spec exactly | ✅ PASS |
| Transient annotation intent on CartItem (no @Entity, no @Serializable) | ✅ PASS |
| Business invariants enforced via `init { require(...) }` | ✅ PASS |
| KDoc on all public classes and properties | ✅ PASS |
| Package = `com.zyntasolutions.zyntapos.domain.model` | ✅ PASS |
| ER diagram field alignment (Supplier, RegisterSession, StockAdjustment) | ✅ PASS |

> **Section status: ✅ ALL 14 TASKS COMPLETE — Step 2.1 DONE**

---

*End of ZyntaPOS Execution Log v1.1*
*Doc ID: ZENTA-EXEC-LOG-v1.1 | Last Updated: 2026-02-20*
*Reference Plan: docs/plans/PLAN_PHASE1.md*
