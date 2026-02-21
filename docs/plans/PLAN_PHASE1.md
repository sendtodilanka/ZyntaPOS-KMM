# ZyntaPOS — Phase 1 MVP Execution Plan

> **Document ID:** ZENTA-PLAN-PHASE1-v1.0
> **Status:** READY FOR EXECUTION
> **Scope:** Months 1–6 | Single-Store Offline-First MVP
> **Architecture:** KMP (Android + Desktop JVM)
> **Author:** Senior KMP Architect & Lead Engineer
> **Created:** 2026-02-20
> **Reference Plans:** ZENTA-MASTER-PLAN-v1.0 | ZENTA-UI-UX-PLAN-v1.0

---

## Executive Summary

Phase 1 delivers a **fully functional single-store POS** with offline capability, encrypted local persistence, thermal printing, barcode scanning, cash register management, basic reporting, and a complete authentication system — targeting both Android (tablet + phone) and Desktop (JVM) platforms from a single shared codebase.

**13 modules** | **~450+ tasks** | **6 months** | **Deliverables:** Android APK + Desktop JAR

---

## Table of Contents

1. [Phase 1 Scope & Boundaries](#1-phase-1-scope--boundaries)
2. [Module Dependency Graph](#2-module-dependency-graph)
3. [Sprint Breakdown (24 Sprints × 1 Week)](#3-sprint-breakdown)
4. [Step-by-Step Execution Plan](#4-step-by-step-execution-plan)
   - [Sprint 1–2: Project Scaffold & :shared:core](#sprint-1-2)
   - [Sprint 3–4: :shared:domain](#sprint-3-4)
   - [Sprint 5–6: :shared:data (Local DB)](#sprint-5-6)
   - [Sprint 7: :shared:hal (Hardware Abstraction)](#sprint-7)
   - [Sprint 8: :shared:security](#sprint-8)
   - [Sprint 9–10: :composeApp:designsystem](#sprint-9-10)
   - [Sprint 11: :composeApp:navigation](#sprint-11)
   - [Sprint 12–13: :composeApp:feature:auth](#sprint-12-13)
   - [Sprint 14–17: :composeApp:feature:pos](#sprint-14-17)
   - [Sprint 18–19: :composeApp:feature:inventory](#sprint-18-19)
   - [Sprint 20–21: :composeApp:feature:register](#sprint-20-21)
   - [Sprint 22: :composeApp:feature:reports](#sprint-22)
   - [Sprint 23: :composeApp:feature:settings](#sprint-23)
   - [Sprint 24: Integration, QA & Release Prep](#sprint-24)
5. [Gradle Module Configuration](#5-gradle-module-configuration)
6. [Tech Stack & Exact Versions](#6-tech-stack--exact-versions)
7. [Database Schema (SQLDelight)](#7-database-schema)
8. [MVI Pattern Contract](#8-mvi-pattern-contract)
9. [Security Implementation Plan](#9-security-implementation-plan)
10. [HAL Interface Contracts](#10-hal-interface-contracts)
11. [Testing Strategy (Phase 1)](#11-testing-strategy)
12. [Performance Targets & Validation](#12-performance-targets)
13. [Risk Register (Phase 1 Specific)](#13-risk-register)
14. [Definition of Done (DoD)](#14-definition-of-done)

---

## 1. Phase 1 Scope & Boundaries

### 1.1 In-Scope Modules

| Module ID | Module | Priority | Sprint Target |
|-----------|--------|----------|---------------|
| M01 | `:shared:core` | P0 | 1–2 |
| M02 | `:shared:domain` | P0 | 3–4 |
| M03 | `:shared:data` | P0 | 5–6 |
| M04 | `:shared:hal` | P0 | 7 |
| M05 | `:shared:security` | P0 | 8 |
| M06 | `:composeApp:designsystem` | P0 | 9–10 |
| M07 | `:composeApp:navigation` | P0 | 11 |
| M08 | `:composeApp:feature:auth` | P0 | 12–13 |
| M09 | `:composeApp:feature:pos` | P0 | 14–17 |
| M10 | `:composeApp:feature:inventory` | P1 | 18–19 |
| M11 | `:composeApp:feature:register` | P0 | 20–21 |
| M12 | `:composeApp:feature:reports` | P1 | 22 |
| M18 | `:composeApp:feature:settings` | P1 | 23 |

### 1.2 Out-of-Scope for Phase 1

- CRM / Customer Loyalty (Phase 2)
- Coupons & Promotions Engine (Phase 2)
- Multi-Store / Central Dashboard (Phase 2)
- Expense Tracking & Accounting (Phase 2)
- Staff Management, Payroll, Scheduling (Phase 3)
- Sri Lanka E-Invoicing 2026 (Phase 3)
- Media Manager, System Admin (Phase 3)
- CRDT advanced conflict resolution (basic sync only in Phase 1)
- Customer display (secondary display) support

### 1.3 Phase 1 Deliverables

- ✅ Android APK (minSdk 24, targets API 35)
- ✅ Desktop JAR/distributable (JVM 17+, Win/Mac/Linux)
- ✅ SQLCipher-encrypted offline database
- ✅ JWT-based authentication with RBAC (5 default roles)
- ✅ Full POS checkout: product grid → cart → payment → receipt
- ✅ Thermal printer support: ESC/POS 80mm/58mm
- ✅ Barcode scanner: USB/Bluetooth (Android) + Serial/HID (Desktop)
- ✅ Cash register: open/close shift, cash in/out, Z-report
- ✅ Inventory: product CRUD, categories, suppliers, stock adjustment
- ✅ Reports: daily/weekly sales report, stock report
- ✅ Settings: store info, printer config, tax config, user management
- ✅ Offline-first: 100% POS operations without network
- ✅ Basic cloud sync: push/pull via Ktor REST

---

## 2. Module Dependency Graph

```
                        ┌─────────────────────┐
                        │   :shared:core       │  ← No dependencies
                        │  (constants, ext,    │
                        │   crypto primitives) │
                        └──────────┬──────────┘
                                   │
                        ┌──────────▼──────────┐
                        │   :shared:domain     │  ← depends on :shared:core
                        │  (models, usecases,  │
                        │   repo interfaces)   │
                        └──────────┬──────────┘
                                   │
            ┌──────────────────────┼──────────────────────┐
            │                      │                      │
  ┌─────────▼──────┐    ┌──────────▼───────┐   ┌─────────▼──────┐
  │  :shared:data  │    │   :shared:hal    │   │ :shared:security│
  │  (SQLDelight,  │    │  (expect/actual  │   │ (encryption,    │
  │   Ktor, sync)  │    │   printer/scan)  │   │  tokens, keys)  │
  └─────────┬──────┘    └──────────┬───────┘   └─────────┬──────┘
            │                      │                      │
            └──────────────────────┼──────────────────────┘
                                   │
                     ┌─────────────▼────────────┐
                     │  :composeApp:designsystem │  ← Pure UI, no business logic
                     │  (ZyntaTheme, components, │
                     │   tokens, layouts)        │
                     └─────────────┬─────────────┘
                                   │
                     ┌─────────────▼────────────┐
                     │  :composeApp:navigation   │  ← Type-safe nav graph
                     └──────┬────────────────────┘
                            │
        ┌───────────────────┼──────────────────────────────────────┐
        │                   │                │          │           │
  ┌─────▼──┐  ┌─────────────▼─┐  ┌──────────▼──┐  ┌───▼────┐  ┌──▼──────┐
  │:auth   │  │:pos            │  │:inventory   │  │:register│  │:reports │
  │        │  │(POS, cart,    │  │(products,   │  │(cash    │  │:settings│
  │        │  │ payment,      │  │ categories) │  │ shifts) │  │         │
  │        │  │ receipt)      │  │             │  │         │  │         │
  └────────┘  └───────────────┘  └─────────────┘  └─────────┘  └─────────┘
```

---

## 3. Sprint Breakdown

| Sprint | Weeks | Module(s) | Key Goal |
|--------|-------|-----------|----------|
| 1 | W01 | Project Setup | Gradle multi-module scaffold, CI, version catalog |
| 2 | W02 | `:shared:core` | Constants, extensions, base Result type, logging |
| 3 | W03 | `:shared:domain` (Part 1) | Domain models: User, Product, Order, Payment |
| 4 | W04 | `:shared:domain` (Part 2) | Use cases, repo interfaces, validators |
| 5 | W05 | `:shared:data` (Part 1) | SQLDelight schema, DAOs, SQLCipher setup |
| 6 | W06 | `:shared:data` (Part 2) | Ktor client, repository impls, basic sync |
| 7 | W07 | `:shared:hal` | Printer & scanner interfaces + platform actuals |
| 8 | W08 | `:shared:security` | Encryption, JWT handling, key storage |
| 9 | W09 | `:composeApp:designsystem` (Part 1) | ZyntaTheme, tokens, typography, colors |
| 10 | W10 | `:composeApp:designsystem` (Part 2) | All Zynta components, responsive layouts |
| 11 | W11 | `:composeApp:navigation` | NavGraph, type-safe routes, adaptive nav |
| 12 | W12 | `:composeApp:feature:auth` (Part 1) | Login screen UI + ViewModel + MVI |
| 13 | W13 | `:composeApp:feature:auth` (Part 2) | Session management, RBAC guard, PIN lock |
| 14 | W14 | `:composeApp:feature:pos` (Part 1) | Product grid, category nav, search/barcode |
| 15 | W15 | `:composeApp:feature:pos` (Part 2) | Cart: add/remove/qty/discount/notes |
| 16 | W16 | `:composeApp:feature:pos` (Part 3) | Payment flow: methods, numpad, change calc |
| 17 | W17 | `:composeApp:feature:pos` (Part 4) | Receipt: generation, ESC/POS print, hold/retrieve |
| 18 | W18 | `:composeApp:feature:inventory` (Part 1) | Product list/grid, CRUD, variations |
| 19 | W19 | `:composeApp:feature:inventory` (Part 2) | Categories, suppliers, stock adjustment |
| 20 | W20 | `:composeApp:feature:register` (Part 1) | Register setup, open shift, cash in/out |
| 21 | W21 | `:composeApp:feature:register` (Part 2) | Close shift, Z-report, discrepancy detection |
| 22 | W22 | `:composeApp:feature:reports` | Sales report, stock report, export CSV/PDF |
| 23 | W23 | `:composeApp:feature:settings` | Store info, POS config, tax, printer, users |
| 24 | W24 | Integration QA & Release | E2E testing, APK/JAR packaging, perf validation |

---

## 4. Step-by-Step Execution Plan

---

### Sprint 1–2: Project Scaffold & `:shared:core` {#sprint-1-2}

#### Step 1.1 — Root Project Scaffold
**Goal:** Initialize Gradle multi-module KMP project structure.

```
Tasks:
  1.1.1  Create root build.gradle.kts with KMP + Compose Multiplatform plugins
  1.1.2  Create gradle/libs.versions.toml (Version Catalog) with ALL Phase 1 deps
  1.1.3  Create settings.gradle.kts declaring all 13 modules
  1.1.4  Create gradle.properties with KMP flags & build optimizations
  1.1.5  Create local.properties.template (secrets: API keys, DB password)
  1.1.6  Initialize .gitignore (local.properties, *.keystore, build/)
  1.1.7  Create GitHub Actions CI workflow: build + test on push
  1.1.8  Create root /docs/ai_workflows/execution_log.md
```

**Files to Create:**
- `build.gradle.kts` (root)
- `settings.gradle.kts`
- `gradle/libs.versions.toml`
- `gradle.properties`
- `local.properties.template`
- `.github/workflows/ci.yml`
- `docs/ai_workflows/execution_log.md`

---

#### Step 1.2 — `:shared:core` Module
**Goal:** Cross-platform foundation: constants, extensions, error handling, logging.

```
Tasks:
  1.2.1  Create shared/core/build.gradle.kts (commonMain only, no Android/Desktop specific)
  1.2.2  Implement sealed class Result<T> with Success, Error, Loading states
  1.2.3  Implement ZyntaException hierarchy (NetworkException, DatabaseException, etc.)
  1.2.4  Implement ZyntaLogger (Kermit wrapper) with log levels + platform tags
  1.2.5  Create AppConfig (baseUrl, dbName, dbVersion, syncIntervalMs constants)
  1.2.6  Create extension functions: String (validation, formatting), 
         Double (currency rounding), Long (timestamp helpers)
  1.2.7  Create IdGenerator (UUID v4 cross-platform via kotlin.uuid)
  1.2.8  Create DateTimeUtils (kotlinx.datetime wrappers: now(), toIso(), fromIso())
  1.2.9  Create CurrencyFormatter (locale-aware, configurable symbol + decimals)
         ⚠️ HOTFIX-01 (2026-02-22): `ReceiptFormatter` (`:shared:domain`) called
         `CurrencyFormatter.format(...)` as static — compile error. `CurrencyFormatter`
         is an instance-only class (no companion object). Fixed via Constructor Injection
         in `ReceiptFormatter`; Koin binding updated in `PosModule.kt`. See Appendix D.
  1.2.10 Unit tests for all utilities (commonTest)
```

**File Structure:**
```
shared/core/src/commonMain/kotlin/com/zyntasolutions/zyntapos/core/
  ├── result/Result.kt
  ├── result/ZyntaException.kt
  ├── logger/ZyntaLogger.kt
  ├── config/AppConfig.kt
  ├── extensions/StringExtensions.kt
  ├── extensions/DoubleExtensions.kt
  ├── extensions/LongExtensions.kt
  ├── utils/IdGenerator.kt
  ├── utils/DateTimeUtils.kt
  └── utils/CurrencyFormatter.kt
shared/core/src/commonTest/kotlin/com/zyntasolutions/zyntapos/core/
  ├── result/ResultTest.kt
  ├── utils/DateTimeUtilsTest.kt
  └── utils/CurrencyFormatterTest.kt
```

---

### Sprint 3–4: `:shared:domain` {#sprint-3-4}

#### Step 2.1 — Domain Models
**Goal:** Define all Phase 1 domain entities as pure Kotlin data classes.

```
Tasks:
  2.1.1  User.kt — id, name, email, role(Role enum), storeId, isActive, pin
  2.1.2  Role.kt — enum: ADMIN, STORE_MANAGER, CASHIER, ACCOUNTANT, STOCK_MANAGER
  2.1.3  Permission.kt — enum + Permission sets per Role (RBAC mapping)
  2.1.4  Product.kt — id, name, barcode, sku, categoryId, unitId, price, 
                      costPrice, taxGroupId, stockQty, imageUrl, isActive
  2.1.5  ProductVariant.kt — id, productId, name, attributes(Map), price, stock
  2.1.6  Category.kt — id, name, parentId(nullable), imageUrl, displayOrder
  2.1.7  Unit.kt (UnitOfMeasure) — id, name, abbreviation, baseUnit, conversionRate
  2.1.8  TaxGroup.kt — id, name, rate, isInclusive
  2.1.9  Customer.kt — id, name, phone, email, address, groupId, notes
  2.1.10 Order.kt — id, orderNumber, type(OrderType), status, items, 
                    subtotal, taxAmount, discountAmount, total, paymentMethod,
                    customerId(nullable), cashierId, storeId, createdAt, notes
  2.1.11 OrderItem.kt — id, orderId, productId, productName, unitPrice, 
                        qty, discount, taxRate, lineTotal
  2.1.12 OrderType.kt — enum: SALE, REFUND, HOLD
  2.1.13 PaymentMethod.kt — enum: CASH, CARD, MOBILE, BANK_TRANSFER, SPLIT
  2.1.14 PaymentSplit.kt — for split payment: method + amount pairs
  2.1.15 CashRegister.kt — id, name, storeId, currentBalance, status(OPEN/CLOSED)
  2.1.16 RegisterSession.kt — id, registerId, openedBy, closedBy, 
                              openingBalance, closingBalance, expectedBalance,
                              actualBalance, openedAt, closedAt, status
  2.1.17 CashMovement.kt — id, sessionId, type(IN/OUT), amount, reason, timestamp
  2.1.18 Supplier.kt — id, name, phone, email, address, notes
  2.1.19 StockAdjustment.kt — id, productId, type(INCREASE/DECREASE/TRANSFER),
                               qty, reason, adjustedBy, timestamp
  2.1.20 SyncStatus.kt — enum: PENDING, SYNCING, SYNCED, FAILED + metadata
```

---

#### Step 2.2 — Repository Interfaces
**Goal:** Define pure interfaces — no implementation details.

```
Tasks:
  2.2.1  AuthRepository — login, logout, getSession, refreshToken, updatePin
  2.2.2  ProductRepository — getAll(Flow), getById, search(query, categoryId),
                             getByBarcode, insert, update, delete, getCount
  2.2.3  CategoryRepository — getAll(Flow), getById, insert, update, delete, getTree
  2.2.4  OrderRepository — create, getById, getAll(Flow, filters), 
                           update, void, getByDateRange, holdOrder, retrieveHeld
  2.2.5  CustomerRepository — getAll(Flow), getById, search, insert, update, delete
  2.2.6  RegisterRepository — getActive, openSession, closeSession, 
                              addCashMovement, getMovements(sessionId)
  2.2.7  StockRepository — adjustStock, getMovements, getAlerts(threshold)
  2.2.8  SupplierRepository — getAll(Flow), getById, insert, update, delete
  2.2.9  SyncRepository — getPendingOperations, markSynced, pushToServer, pullFromServer
  2.2.10 SettingsRepository — get(key), set(key, value), getAll
```

---

#### Step 2.3 — Use Cases (Business Logic Layer)
**Goal:** Encapsulate all business rules in single-responsibility use cases.

```
POS Use Cases:
  2.3.1  AddItemToCartUseCase — validates stock, applies unit conversion, returns updated cart
  2.3.2  RemoveItemFromCartUseCase
  2.3.3  UpdateCartItemQuantityUseCase — enforces stock limits
  2.3.4  ApplyOrderDiscountUseCase — validates discount ≤ configurable max %
  2.3.5  ApplyItemDiscountUseCase — role-gated (requires CASHIER+ permission)
  2.3.6  CalculateOrderTotalsUseCase — subtotal, per-item tax, order tax, 
                                       inclusive/exclusive handling, rounding
  2.3.7  ProcessPaymentUseCase — validates tender, calculates change, 
                                  handles split payment, creates Order
  2.3.8  HoldOrderUseCase — saves cart state with hold ID
  2.3.9  RetrieveHeldOrderUseCase
  2.3.10 VoidOrderUseCase — requires manager role, reverses stock

Auth Use Cases:
  2.3.11 LoginUseCase — validates credentials, creates session, assigns role
  2.3.12 LogoutUseCase — destroys session, clears sensitive state
  2.3.13 ValidatePinUseCase — cashier PIN quick-switch
  2.3.14 CheckPermissionUseCase — evaluates Role + Permission for action gating

Inventory Use Cases:
  2.3.15 CreateProductUseCase — validates barcode uniqueness, SKU uniqueness
  2.3.16 UpdateProductUseCase
  2.3.17 AdjustStockUseCase — records adjustment, triggers low-stock check
  2.3.18 SearchProductsUseCase — FTS5 across name, barcode, SKU, category

Register Use Cases:
  2.3.19 OpenRegisterSessionUseCase — requires no active session, sets opening balance
  2.3.20 CloseRegisterSessionUseCase — calculates expected vs actual, generates Z-report
  2.3.21 RecordCashMovementUseCase — validates amounts, records reason

Report Use Cases:
  2.3.22 GenerateSalesReportUseCase — aggregates orders by date range
  2.3.23 GenerateStockReportUseCase — current levels, low-stock, dead stock
```

**KDoc requirement:** All use cases must have full KDoc with `@param`, `@return`, `@throws`, and business rule descriptions.

---

### Sprint 5–6: `:shared:data` {#sprint-5-6}

#### Step 3.1 — SQLDelight Schema
**Goal:** Define all Phase 1 database tables with proper indices and FTS5.

```sql
-- Tables to define in .sq files:

  3.1.1  users.sq        — users table + queries
  3.1.2  products.sq     — products table + ProductFts5 virtual table + queries
  3.1.3  categories.sq   — categories table + queries
  3.1.4  orders.sq       — orders + order_items tables + queries
  3.1.5  customers.sq    — customers table + FTS5 + queries
  3.1.6  registers.sq    — cash_registers + register_sessions + cash_movements
  3.1.7  stock.sq        — stock_adjustments + stock_alerts
  3.1.8  suppliers.sq    — suppliers table + queries
  3.1.9  settings.sq     — key_value store (key TEXT PK, value TEXT, updated_at INTEGER)
  3.1.10 sync_queue.sq   — pending_operations table (id, entity_type, entity_id, 
                           operation, payload, created_at, retry_count, status)
  3.1.11 audit_log.sq    — audit_entries table (immutable append-only)

-- Indexes:
  - products: barcode (UNIQUE), sku (UNIQUE), category_id
  - orders: created_at, cashier_id, status
  - order_items: order_id
  - customers: phone (UNIQUE), email
  - sync_queue: status, entity_type
```

#### Step 3.2 — SQLCipher Setup
```
Tasks:
  3.2.1  Configure SQLDelight driver with SQLCipher (Android: SupportSQLiteDriver, 
         Desktop: JdbcSqliteDriver with SQLCipher native)
  3.2.2  Implement DatabaseKeyProvider (expect/actual):
         - Android: fetch from Android Keystore
         - Desktop: fetch from JCE KeyStore + OS credential manager
  3.2.3  DatabaseFactory.kt — creates encrypted driver for each platform
  3.2.4  Migration manager — version-safe schema migrations
  3.2.5  WAL mode enablement for concurrent read/write performance
```

#### Step 3.3 — Repository Implementations
```
Tasks:
  3.3.1  ProductRepositoryImpl — delegates to SQLDelight queries, maps entities
  3.3.2  CategoryRepositoryImpl
  3.3.3  OrderRepositoryImpl — transactional order creation (order + items atomically)
  3.3.4  CustomerRepositoryImpl
  3.3.5  RegisterRepositoryImpl
  3.3.6  StockRepositoryImpl
  3.3.7  SupplierRepositoryImpl
  3.3.8  AuthRepositoryImpl — local credential validation + JWT caching
  3.3.9  SettingsRepositoryImpl — key/value typed wrappers
  3.3.10 SyncRepositoryImpl — queue management + Ktor push/pull
```

#### Step 3.4 — Ktor HTTP Client
```
Tasks:
  3.4.1  Configure KtorClient (commonMain) with:
         - ContentNegotiation (JSON / kotlinx.serialization)
         - Auth plugin (Bearer token injection)
         - HttpTimeout (connect: 10s, request: 30s)
         - Retry plugin (3 attempts, exponential backoff)
         - Logging plugin (Kermit-backed, DEBUG only)
  3.4.2  Define DTOs for all API endpoints (Phase 1 scope)
  3.4.3  Implement ApiService interface + KtorApiService implementation
  3.4.4  Implement SyncEngine:
         - Background coroutine scope (lifecycle-aware)
         - Reads pending_operations queue
         - Pushes to /api/v1/sync/push
         - Pulls delta from /api/v1/sync/pull with last_sync_timestamp
         - Marks operations as SYNCED / retries FAILED (max 5)
  3.4.5  Network connectivity monitor (expect/actual):
         - Android: ConnectivityManager.NetworkCallback
         - Desktop: InetAddress reachability check (periodic)
```

---

### Sprint 7: `:shared:hal` {#sprint-7}

#### Step 4.1 — Hardware Abstraction Interfaces

```kotlin
// commonMain — pure interfaces, zero platform code

interface PrinterPort {
    suspend fun connect(): Result<Unit>
    suspend fun disconnect(): Result<Unit>
    suspend fun isConnected(): Boolean
    suspend fun print(commands: ByteArray): Result<Unit>
    suspend fun openCashDrawer(): Result<Unit>
    suspend fun cutPaper(): Result<Unit>
}

interface BarcodeScanner {
    val scanEvents: Flow<ScanResult>
    suspend fun startListening(): Result<Unit>
    suspend fun stopListening()
}

interface ReceiptBuilder {
    fun buildReceipt(order: Order, config: PrinterConfig): ByteArray
    fun buildZReport(session: RegisterSession): ByteArray
    fun buildTestPage(): ByteArray
}

data class PrinterConfig(
    val paperWidth: PaperWidth,   // PAPER_58MM, PAPER_80MM
    val printDensity: Int,        // 0-8
    val characterSet: CharacterSet,
    val headerLines: List<String>,
    val footerLines: List<String>,
    val showLogo: Boolean,
    val showQrCode: Boolean
)

sealed class ScanResult {
    data class Barcode(val value: String, val format: BarcodeFormat) : ScanResult()
    data class Error(val message: String) : ScanResult()
}
```

#### Step 4.2 — Platform Actuals

```
Android actuals:
  4.2.1  AndroidUsbPrinterPort — USB Host API, ESC/POS byte commands
  4.2.2  AndroidBluetoothPrinterPort — BluetoothSocket, SPP profile
  4.2.3  AndroidCameraScanner — ML Kit Barcode Scanning (CameraX)
  4.2.4  AndroidUsbScanner — USB HID keyboard emulation listener

Desktop actuals:
  4.2.5  DesktopSerialPrinterPort — jSerialComm, ESC/POS over COM port
  4.2.6  DesktopTcpPrinterPort — Socket connection to network printer (IP:port)
  4.2.7  DesktopUsbPrinterPort — libusb4j / javax.usb
  4.2.8  DesktopHidScanner — keyboard wedge scanner via event capture
  4.2.9  DesktopSerialScanner — jSerialComm serial barcode reader

Common:
  4.2.10 EscPosReceiptBuilder — implements ReceiptBuilder, generates ESC/POS bytes
         for 58mm and 80mm paper widths, handles UTF-8 + special characters
  4.2.11 PrinterManager — KOIN-provided, wraps active printer port,
         retries on failure, queues commands during disconnect
```

---

### Sprint 8: `:shared:security` {#sprint-8}

#### Step 5.1 — Encryption & Key Management

```
Tasks:
  5.1.1  EncryptionManager (expect/actual):
         - Android: AES-256-GCM via Android Keystore + Cipher
         - Desktop: AES-256-GCM via JCE + PKCS12 KeyStore
         - API: encrypt(plaintext): EncryptedData, decrypt(data): String
  5.1.2  DatabaseKeyManager:
         - Generates random 256-bit key on first launch
         - Stores key in platform secure storage (Keystore/KeyStore)
         - Retrieves on subsequent launches
  5.1.3  SecurePreferences (expect/actual):
         - Android: EncryptedSharedPreferences
         - Desktop: Properties file encrypted with DatabaseKey
  5.1.4  PasswordHasher:
         - BCrypt implementation (jBCrypt on JVM, expect/actual bridge)
         - verifyPassword(plain, hash): Boolean
         - hashPassword(plain): String
  5.1.5  JwtManager:
         - parseJwt(token): JwtClaims
         - isTokenExpired(token): Boolean
         - extractUserId(token): String
         - extractRole(token): Role
  5.1.6  PinManager:
         - hashPin(pin: String): String (SHA-256 + stored salt)
         - verifyPin(pin: String, hash: String): Boolean
         - generatePin(): String (4–6 digit)
  5.1.7  SecurityAuditLogger:
         - logLoginAttempt(success, userId, deviceId)
         - logPermissionDenied(userId, permission, screen)
         - logOrderVoid(userId, orderId, reason)
         - All writes to audit_log table (append-only)
```

---

### Sprint 9–10: `:composeApp:designsystem` {#sprint-9-10}

#### Step 6.1 — Theme & Tokens

```
Tasks:
  6.1.1  ZyntaColors.kt — Material 3 ColorScheme (light + dark):
         - Primary: #1565C0 (blue), Secondary: #F57C00 (amber), 
           Tertiary: #2E7D32 (green for success), Error: #C62828
         - All surface/on-surface/container variants per M3 spec
  6.1.2  ZyntaTypography.kt — Material 3 TypeScale using system sans-serif:
         Display/Headline/Title/Body/Label at all sizes per UI/UX plan §3.1
  6.1.3  ZyntaShapes.kt — M3 shape scale (Extra Small 4dp → Extra Large 28dp)
  6.1.4  ZyntaSpacing.kt — spacing tokens: xs(4), sm(8), md(16), lg(24), xl(32), xxl(48)
  6.1.5  ZyntaElevation.kt — elevation levels 0–5 per M3 spec
  6.1.6  ZyntaTheme.kt — wraps MaterialTheme, provides ZyntaColors, 
                          handles system dark mode + manual toggle
  6.1.7  WindowSizeClassHelper.kt — CompactMediumExpanded enum + current() expect/actual
```

#### Step 6.2 — Core Components

```
Tasks:
  6.2.1  ZyntaButton.kt — variants: Primary, Secondary, Danger, Ghost, Icon
                          sizes: Small(32dp), Medium(40dp), Large(56dp)
                          states: enabled, loading(CircularProgress), disabled
  6.2.2  ZyntaTextField.kt — label, value, error message, leading/trailing icons,
                              keyboard type support, validation state display
  6.2.3  ZyntaSearchBar.kt — with barcode scan icon, clear button, focus management
  6.2.4  ZyntaProductCard.kt — image(Coil), name, price, stock indicator badge,
                                variants: Grid (square), List, Compact
  6.2.5  ZyntaCartItemRow.kt — thumbnail, name, price, qty stepper (+/-), remove
  6.2.6  ZyntaNumericPad.kt — 0-9, decimal, 00, backspace, clear; 
                               modes: PRICE, QUANTITY, PIN (masked)
  6.2.7  ZyntaDialog.kt — Confirm (title, message, confirm/cancel), 
                           Alert (title, message, ok), 
                           Input (title, single text field + confirm)
  6.2.8  ZyntaBottomSheet.kt — M3 ModalBottomSheet wrapper with handle
  6.2.9  ZyntaTable.kt — sortable columns, paginated rows, empty state
  6.2.10 ZyntaBadge.kt — count badge, status badge (color + label)
  6.2.11 ZyntaSyncIndicator.kt — SYNCED(green dot), SYNCING(spinner), 
                                  OFFLINE(orange dot), FAILED(red dot)
  6.2.12 ZyntaEmptyState.kt — icon + title + subtitle + optional CTA button
  6.2.13 ZyntaLoadingOverlay.kt — semi-transparent scrim + CircularProgressIndicator
  6.2.14 ZyntaSnackbarHost.kt — success/error/info variants with icon
  6.2.15 ZyntaTopAppBar.kt — adaptive: collapses on scroll, back/menu actions
```

#### Step 6.3 — Adaptive Layouts

```
Tasks:
  6.3.1  ZyntaScaffold.kt — adaptive navigation:
         - Compact: bottomNavigationBar
         - Medium: NavigationRail (left)
         - Expanded: NavigationDrawer (persistent, 240dp)
  6.3.2  ZyntaSplitPane.kt — horizontal split with configurable weight (default 40/60),
                              collapsible on Compact
  6.3.3  ZyntaGrid.kt — LazyVerticalGrid with WindowSizeClass-driven column count:
                         Compact=2, Medium=3-4, Expanded=4-6
  6.3.4  ZyntaListDetailLayout.kt — master list + detail pane for Expanded
```

---

### Sprint 11: `:composeApp:navigation` {#sprint-11}

#### Step 7.1 — Type-Safe Navigation

```kotlin
// Type-safe route definitions
sealed class ZyntaRoute {
    // Auth
    @Serializable object Login : ZyntaRoute()
    @Serializable object PinLock : ZyntaRoute()

    // Main
    @Serializable object Dashboard : ZyntaRoute()
    @Serializable object Pos : ZyntaRoute()
    @Serializable data class Payment(val orderId: String) : ZyntaRoute()

    // Inventory
    @Serializable object ProductList : ZyntaRoute()
    @Serializable data class ProductDetail(val productId: String?) : ZyntaRoute()
    @Serializable object CategoryList : ZyntaRoute()
    @Serializable object SupplierList : ZyntaRoute()

    // Register
    @Serializable object RegisterDashboard : ZyntaRoute()
    @Serializable object OpenRegister : ZyntaRoute()
    @Serializable object CloseRegister : ZyntaRoute()

    // Reports
    @Serializable object SalesReport : ZyntaRoute()
    @Serializable object StockReport : ZyntaRoute()

    // Settings
    @Serializable object Settings : ZyntaRoute()
    @Serializable object PrinterSettings : ZyntaRoute()
    @Serializable object TaxSettings : ZyntaRoute()
    @Serializable object UserManagement : ZyntaRoute()
}
```

```
Tasks:
  7.1.1  ZyntaNavGraph.kt — main NavHost wiring all routes
  7.1.2  AuthNavGraph.kt — nested graph: Login → PinLock
  7.1.3  MainNavGraph.kt — nested graph for authenticated area with ZyntaScaffold
  7.1.4  NavigationItems.kt — nav items per role (RBAC-filtered destinations)
  7.1.5  NavigationController.kt — wrapper with type-safe navigate(), popBackStack()
  7.1.6  DeepLink support — for hardware scanner barcode → auto-navigate to product
  7.1.7  Adaptive navigation handler — swaps BottomBar/Rail/Drawer by WindowSizeClass
  7.1.8  Back stack management — proper scoped ViewModels per nested graph
```

---

### Sprint 12–13: `:composeApp:feature:auth` {#sprint-12-13}

#### Step 8.1 — MVI Contract

```kotlin
// Auth MVI — to be replicated for all feature modules

data class AuthState(
    val isLoading: Boolean = false,
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isPasswordVisible: Boolean = false,
    val rememberMe: Boolean = false,
    val error: String? = null
)

sealed class AuthIntent {
    data class EmailChanged(val email: String) : AuthIntent()
    data class PasswordChanged(val password: String) : AuthIntent()
    object TogglePasswordVisibility : AuthIntent()
    object LoginClicked : AuthIntent()
    object ForgotPasswordClicked : AuthIntent()
    data class RememberMeToggled(val checked: Boolean) : AuthIntent()
}

sealed class AuthEffect {
    object NavigateToDashboard : AuthEffect()
    object NavigateToRegisterGuard : AuthEffect()  // if no open register
    data class ShowError(val message: String) : AuthEffect()
}
```

```
Tasks:
  8.1.1  AuthViewModel.kt — handles intents, calls LoginUseCase, emits state/effects
  8.1.2  LoginScreen.kt — responsive: split layout (Expanded: illustration + form),
                           single pane (Compact), see UI/UX plan §5
  8.1.3  PinLockScreen.kt — PIN entry after session idle timeout
  8.1.4  SessionGuard.kt — composable that checks auth state, redirects to login
  8.1.5  RoleGuard.kt — composable that checks Permission, shows "Unauthorized" or content
  8.1.6  SessionManager.kt — tracks idle time, triggers PinLock after configurable timeout
  8.1.7  AuthModule.kt (Koin) — provides AuthViewModel, LoginUseCase, AuthRepository
  8.1.8  AuthRepositoryImpl.kt — local validation + JWT caching in SecurePreferences
  8.1.9  Unit tests: AuthViewModel (all intents), LoginUseCase (valid/invalid/network err)
```

---

### Sprint 14–17: `:composeApp:feature:pos` {#sprint-14-17}

#### Step 9.1 — POS MVI State

```kotlin
data class PosState(
    val products: List<Product> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val searchQuery: String = "",
    val isSearchFocused: Boolean = false,
    val cartItems: List<CartItem> = emptyList(),
    val selectedCustomer: Customer? = null,
    val orderDiscount: Double = 0.0,
    val orderDiscountType: DiscountType = DiscountType.FIXED,
    val heldOrders: List<HeldOrder> = emptyList(),
    val orderTotals: OrderTotals = OrderTotals(),
    val isLoading: Boolean = false,
    val scannerActive: Boolean = false,
    val error: String? = null
)

data class OrderTotals(
    val subtotal: Double = 0.0,
    val taxAmount: Double = 0.0,
    val discountAmount: Double = 0.0,
    val total: Double = 0.0,
    val itemCount: Int = 0
)
```

```
Sprint 14 Tasks — Product Grid & Search:
  9.1.1  PosViewModel.kt — root ViewModel, subscribes to product/category Flows
  9.1.2  ProductGridSection.kt — ZyntaGrid with ZyntaProductCard, 
                                  Compact: 2col, Medium: 3-4col, Expanded: 4-6col
  9.1.3  CategoryFilterRow.kt — horizontal scrollable chip row, "All" + category names
  9.1.4  PosSearchBar.kt — ZyntaSearchBar with barcode scan mode toggle
  9.1.5  BarcodeInputHandler.kt — intercepts scanner events → auto-add-to-cart
  9.1.6  KeyboardShortcutHandler.kt (Desktop) — F2 focus search, +/- qty, Delete remove

Sprint 15 Tasks — Cart:
  9.1.7  CartPanel.kt — right panel on Expanded, bottom sheet on Compact
  9.1.8  CartItemList.kt — LazyColumn of ZyntaCartItemRow with swipe-to-remove
  9.1.9  CartSummaryFooter.kt — subtotal, tax, discount, total, PAY button
  9.1.10 CustomerSelectorDialog.kt — search customers, walk-in default, quick-add
  9.1.11 ItemDiscountDialog.kt — flat/percent, role-gated, max cap validation
  9.1.12 OrderDiscountDialog.kt — same logic at order level
  9.1.13 OrderNotesDialog.kt — free text, reference number input
  9.1.14 HoldOrderUseCase integration — F8 shortcut, hold button, confirmation

Sprint 16 Tasks — Payment Flow:
  9.1.15 PaymentScreen.kt — full-screen modal, see UI/UX plan §8
         Left pane: Order Summary (read-only item list + totals)
         Right pane: Payment method grid + NumericPad
  9.1.16 PaymentMethodGrid.kt — Cash, Card, Mobile, Split tiles (min 56dp height)
  9.1.17 CashPaymentPanel.kt — amount received input, change calculation (real-time)
  9.1.18 SplitPaymentPanel.kt — multiple method rows, per-method amounts, validation
  9.1.19 ProcessPaymentUseCase integration — validates, creates order, triggers print
  9.1.20 PaymentSuccessOverlay.kt — animated success state before receipt

Sprint 17 Tasks — Receipt & Order Management:
  9.1.21 ReceiptScreen.kt — preview receipt content (text-based), print/email/SMS
  9.1.22 ESCPosReceiptBuilder.kt — formats order into ESC/POS byte commands:
         store header, items table, totals, payment method, change, footer, QR code
  9.1.23 PrintReceiptUseCase.kt — gets active printer via PrinterManager, prints, 
                                   handles error (show retry dialog)
  9.1.24 HeldOrdersBottomSheet.kt — list held orders, tap to retrieve (F9 shortcut)
  9.1.25 OrderHistoryScreen.kt — today's orders list, filter by status, reprint
  9.1.26 PosModule.kt (Koin) — all POS ViewModels, UseCases, HAL bindings
  9.1.27 Unit tests: CalculateOrderTotalsUseCase (all tax modes), 
                     ProcessPaymentUseCase (cash, split, edge cases),
                     AddItemToCartUseCase (stock limit)
```

---

### Sprint 18–19: `:composeApp:feature:inventory` {#sprint-18-19}

#### Step 10.1 — Inventory Screens

```
Sprint 18 Tasks — Products:
  10.1.1 ProductListScreen.kt — ZyntaTable (list) + grid toggle, search, filter by category
  10.1.2 ProductDetailScreen.kt — create/edit form: all product fields, 
                                   image picker (Coil + platform file chooser),
                                   variation management, tax group selector
  10.1.3 ProductFormValidator.kt — barcode uniqueness check, SKU check, required fields
  10.1.4 BarcodeGeneratorDialog.kt — generate EAN-13 / Code128 barcode for new products
  10.1.5 BulkImportDialog.kt — CSV import with column mapping (basic, no server needed)
  10.1.6 StockAdjustmentDialog.kt — increase/decrease/reason form → AdjustStockUseCase

Sprint 19 Tasks — Categories & Suppliers:
  10.1.7  CategoryListScreen.kt — tree view of categories, CRUD
  10.1.8  CategoryDetailScreen.kt — name, parent selector, image, display order
  10.1.9  SupplierListScreen.kt — ZyntaTable, search
  10.1.10 SupplierDetailScreen.kt — contact info, notes, purchase history (read-only)
  10.1.11 UnitManagementScreen.kt — unit groups, conversion rates
  10.1.12 TaxGroupScreen.kt — create/edit tax groups (name, rate, inclusive toggle)
  10.1.13 LowStockAlertBanner.kt — persistent banner on Inventory home if items < threshold
  10.1.14 InventoryModule.kt (Koin) + tests for CreateProductUseCase, AdjustStockUseCase
```

---

### Sprint 20–21: `:composeApp:feature:register` {#sprint-20-21}

```
Sprint 20 Tasks — Open & Operations:
  11.1.1  RegisterGuard.kt — on login, checks if register is open; if not → OpenRegister
  11.1.2  OpenRegisterScreen.kt — select register, enter opening balance (ZyntaNumericPad),
                                   confirm → OpenRegisterSessionUseCase
  11.1.3  RegisterDashboardScreen.kt — current session info, balance, cash in/out buttons,
                                        quick stats (orders today, revenue)
  11.1.4  CashInOutDialog.kt — amount entry (NumericPad), reason, confirms movement
  11.1.5  CashMovementHistory.kt — LazyColumn of movements for current session

Sprint 21 Tasks — Close & Reporting:
  11.1.6  CloseRegisterScreen.kt — expected balance (auto-calculated), 
                                    actual balance entry (per denomination optional),
                                    discrepancy display, confirm close
  11.1.7  CloseRegisterSessionUseCase integration + ZReport generation
  11.1.8  ZReportScreen.kt — printable summary: opening, cash in/out, sales, 
                              expected vs actual, discrepancy, signature line
  11.1.9  PrintZReportUseCase.kt — ESC/POS formatted Z-report via PrinterManager
  11.1.10 RegisterModule.kt (Koin) + tests for OpenRegisterSessionUseCase, 
           CloseRegisterSessionUseCase, RecordCashMovementUseCase
```

---

### Sprint 22: `:composeApp:feature:reports` {#sprint-22}

```
Tasks:
  12.1.1  ReportsHomeScreen.kt — tile grid: Sales Report, Stock Report (Phase 1 only)
  12.1.2  SalesReportScreen.kt:
           - Date range picker (today, week, month, custom)
           - KPIs: total sales, order count, average order value, top products
           - Sales trend chart (LineChart via Canvas API or lightweight lib)
           - Sales by payment method breakdown
           - Per-item sales table (sortable)
  12.1.3  GenerateSalesReportUseCase integration
  12.1.4  StockReportScreen.kt:
           - Current stock levels table (product, qty, value)
           - Low stock alert section (items below threshold)
           - Dead stock section (no movement in 30 days)
           - Filter by category
  12.1.5  GenerateStockReportUseCase integration
  12.1.6  ExportDialog.kt — CSV (simple) + PDF (using iText or PDFBox on JVM,
                             PDF generation via HTML template on Android)
  12.1.7  PrintReportUseCase.kt — sends report to thermal printer (condensed format)
  12.1.8  ReportsModule.kt (Koin) + tests for both report use cases
```

---

### Sprint 23: `:composeApp:feature:settings` {#sprint-23}

```
Tasks:
  13.1.1  SettingsHomeScreen.kt — grouped settings categories with icons
  13.1.2  GeneralSettingsScreen.kt — store name, address, phone, logo upload, currency,
                                      timezone, date format, language (English only Phase 1)
  13.1.3  PosSettingsScreen.kt — default order type, auto-print toggle, 
                                   tax display mode, receipt template
  13.1.4  TaxSettingsScreen.kt — list of tax groups, CRUD (links to TaxGroupScreen)
  13.1.5  PrinterSettingsScreen.kt:
           - Printer type selector (USB/Bluetooth/Serial/TCP)
           - Connection parameters (port, baud rate, IP/port)
           - Paper width selector (58mm / 80mm)
           - Test print button → PrintTestPageUseCase
             ⚠️ HOTFIX-02 (2026-02-22): `PrintTestPageUseCase` `fun interface` had illegal
             default `= PrinterPaperWidth.MM_80` on abstract method — compile error.
             Fixed: default removed (Option A). `SettingsViewModel.testPrint()` already
             passes `paperWidth` explicitly from `state.printer.paperWidth`. Zero call-site
             changes required. See Appendix D.
           - Receipt customization: header, footer, show/hide fields
  13.1.6  UserManagementScreen.kt — list users, create/edit/deactivate, role assignment
           (Admin only — gated by RoleGuard)
  13.1.7  BackupSettingsScreen.kt — manual backup trigger, backup status, 
                                     restore from backup (local file picker)
  13.1.8  AboutScreen.kt — version, build info, licenses
  13.1.9  AppearanceSettings.kt — dark/light/system mode toggle
  13.1.10 SettingsModule.kt (Koin)
```

---

### Sprint 24: Integration, QA & Release Prep {#sprint-24}

```
Tasks:
  14.1.1  Full E2E Test Run — critical flows: Login → Open Register → POS Sale → 
           Payment → Receipt → Close Register → Sales Report
  14.1.2  Performance Validation:
           - Cold start < 3s on mid-range Android (Pixel 4 or equiv)
           - Product search < 200ms on 10K products
           - Add-to-cart < 50ms
           - Payment processing < 800ms
           - Receipt printing < 2s
  14.1.3  Memory profiling — confirm < 256MB during active POS session (Android)
  14.1.4  SQLCipher encryption verification — confirm DB is unreadable without key
  14.1.5  Offline mode validation — disable network, run full POS flow, re-enable, 
           verify sync completes correctly
  14.1.6  Dark mode audit — all 13 modules tested in dark mode
  14.1.7  Responsive layout audit — test on Compact (360dp), Medium (720dp), 
           Expanded (1280dp) screen widths
  14.1.8  Keyboard shortcut audit (Desktop) — all shortcuts per UI/UX plan §23
  14.1.9  Android APK build & signing — release keystore, Gradle signing config
  14.1.10 Desktop distributable — jpackage for Win (MSI), Mac (DMG), Linux (DEB)
  14.1.11 ProGuard rules review (Android)
  14.1.12 Final execution_log.md audit — all steps marked [x] Finished
```

---

## 5. Gradle Module Configuration

### 5.1 Version Catalog (`gradle/libs.versions.toml`)

```toml
[versions]
kotlin = "2.1.0"
agp = "8.7.0"
composeMp = "1.7.3"
koin = "4.0.0"
ktor = "3.0.3"
sqldelight = "2.0.2"
coroutines = "1.9.0"
serialization = "1.7.3"
datetime = "0.6.1"
coil = "3.0.4"
kermit = "2.0.4"
mockative = "3.0.1"
jserialcomm = "2.10.4"
itext = "7.2.5"
material3 = "1.3.1"
navigationCompose = "2.8.5"
windowSizeClass = "1.3.1"
kotlinxUuid = "0.0.26"

[libraries]
# KMP Core
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "datetime" }

# Compose
compose-multiplatform = { module = "org.jetbrains.compose:compose-gradle-plugin", version.ref = "composeMp" }
compose-material3 = { module = "org.jetbrains.compose.material3:material3", version.ref = "composeMp" }
compose-navigation = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
compose-window-size = { module = "org.jetbrains.compose.material3.adaptive:adaptive", version.ref = "composeMp" }

# DI
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-compose = { module = "io.insert-koin:koin-compose", version.ref = "koin" }
koin-android = { module = "io.insert-koin:koin-android", version.ref = "koin" }

# Networking
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
ktor-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-auth = { module = "io.ktor:ktor-client-auth", version.ref = "ktor" }
ktor-logging = { module = "io.ktor:ktor-client-logging", version.ref = "ktor" }
ktor-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }

# Database
sqldelight-runtime = { module = "app.cash.sqldelight:runtime", version.ref = "sqldelight" }
sqldelight-coroutines = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
sqldelight-android-driver = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-sqlite-driver = { module = "app.cash.sqldelight:sqlite-driver", version.ref = "sqldelight" }

# Logging
kermit = { module = "co.touchlab:kermit", version.ref = "kermit" }

# Image
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-network-ktor = { module = "io.coil-kt.coil3:coil-network-ktor3", version.ref = "coil" }

# Desktop specific
jserialcomm = { module = "com.fazecast:jSerialComm", version.ref = "jserialcomm" }

# Testing
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
mockative = { module = "io.mockative:mockative", version.ref = "mockative" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }

[bundles]
ktor-client-common = ["ktor-client-core", "ktor-content-negotiation", 
                       "ktor-serialization-json", "ktor-auth", "ktor-logging"]
sqldelight-common = ["sqldelight-runtime", "sqldelight-coroutines"]

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "composeMp" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
```

---

## 6. Tech Stack & Exact Versions

| Layer | Technology | Version | Justification |
|-------|-----------|---------|---------------|
| Language | Kotlin | 2.1.0 | K2 compiler, improved KMP |
| Build | Gradle KTS | 8.5+ | Type-safe build scripts |
| UI | Compose Multiplatform | 1.7.3 | Shared UI, M3 support |
| Design | Material 3 | Latest in CMP | Design system alignment |
| DI | Koin | 4.0.0 | KMP native, no reflection |
| Networking | Ktor Client | 3.0.3 | KMP native HTTP |
| Local DB | SQLDelight | 2.0.2 | Type-safe SQL, KMP |
| DB Encryption | SQLCipher | via driver wrappers | AES-256 |
| Async | Coroutines | 1.9.0 | KMP first-class |
| Serialization | kotlinx.serialization | 1.7.3 | KMP native |
| DateTime | kotlinx.datetime | 0.6.1 | KMP native |
| Image | Coil 3 | 3.0.4 | KMP + Ktor network |
| Logging | Kermit | 2.0.4 | KMP native |
| Navigation | Compose Navigation | 2.8.5 | Type-safe routes |
| Testing | Kotlin Test + Mockative | Latest | KMP mocking |
| Serial (Desktop) | jSerialComm | 2.10.4 | Printer/scanner serial |
| Min Android SDK | API 24 | (Android 7.0) | 94%+ coverage |
| Desktop JVM | JVM 17 | LTS | Required for jpackage |

---

## 7. Database Schema

### Core Tables (Phase 1)

```sql
-- Users
CREATE TABLE users (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    email TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL,  -- Role enum value
    pin_hash TEXT,
    store_id TEXT NOT NULL,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    sync_status TEXT NOT NULL DEFAULT 'SYNCED'
);

-- Products with FTS5
CREATE TABLE products (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    barcode TEXT UNIQUE,
    sku TEXT UNIQUE,
    category_id TEXT,
    unit_id TEXT NOT NULL,
    price REAL NOT NULL,
    cost_price REAL NOT NULL DEFAULT 0.0,
    tax_group_id TEXT,
    stock_qty REAL NOT NULL DEFAULT 0.0,
    min_stock_qty REAL NOT NULL DEFAULT 0.0,
    image_url TEXT,
    description TEXT,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    sync_status TEXT NOT NULL DEFAULT 'PENDING'
);

CREATE VIRTUAL TABLE product_fts USING fts5(
    id UNINDEXED, name, barcode, sku, description,
    content='products', content_rowid='rowid'
);

-- Orders
CREATE TABLE orders (
    id TEXT PRIMARY KEY NOT NULL,
    order_number TEXT NOT NULL,
    type TEXT NOT NULL,         -- SALE, REFUND, HOLD
    status TEXT NOT NULL,       -- COMPLETED, VOIDED, HELD
    customer_id TEXT,
    cashier_id TEXT NOT NULL,
    store_id TEXT NOT NULL,
    register_session_id TEXT,
    subtotal REAL NOT NULL,
    tax_amount REAL NOT NULL,
    discount_amount REAL NOT NULL DEFAULT 0.0,
    total REAL NOT NULL,
    payment_method TEXT NOT NULL,
    amount_tendered REAL,
    change_amount REAL,
    notes TEXT,
    reference TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    sync_status TEXT NOT NULL DEFAULT 'PENDING'
);

CREATE TABLE order_items (
    id TEXT PRIMARY KEY NOT NULL,
    order_id TEXT NOT NULL REFERENCES orders(id),
    product_id TEXT NOT NULL,
    product_name TEXT NOT NULL,   -- snapshot at time of sale
    unit_price REAL NOT NULL,
    quantity REAL NOT NULL,
    discount REAL NOT NULL DEFAULT 0.0,
    tax_rate REAL NOT NULL DEFAULT 0.0,
    tax_amount REAL NOT NULL DEFAULT 0.0,
    line_total REAL NOT NULL
);

-- Cash Register
CREATE TABLE cash_registers (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    store_id TEXT NOT NULL,
    current_session_id TEXT,
    is_active INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE register_sessions (
    id TEXT PRIMARY KEY NOT NULL,
    register_id TEXT NOT NULL REFERENCES cash_registers(id),
    opened_by TEXT NOT NULL REFERENCES users(id),
    closed_by TEXT,
    opening_balance REAL NOT NULL,
    closing_balance REAL,
    expected_balance REAL,
    actual_balance REAL,
    total_sales REAL,
    total_refunds REAL,
    status TEXT NOT NULL DEFAULT 'OPEN',  -- OPEN, CLOSED
    opened_at INTEGER NOT NULL,
    closed_at INTEGER,
    notes TEXT
);

CREATE TABLE cash_movements (
    id TEXT PRIMARY KEY NOT NULL,
    session_id TEXT NOT NULL REFERENCES register_sessions(id),
    type TEXT NOT NULL,  -- CASH_IN, CASH_OUT
    amount REAL NOT NULL,
    reason TEXT NOT NULL,
    performed_by TEXT NOT NULL REFERENCES users(id),
    timestamp INTEGER NOT NULL
);

-- Sync Queue
CREATE TABLE sync_queue (
    id TEXT PRIMARY KEY NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    operation TEXT NOT NULL,  -- INSERT, UPDATE, DELETE
    payload TEXT NOT NULL,    -- JSON serialized entity
    created_at INTEGER NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_retry_at INTEGER,
    status TEXT NOT NULL DEFAULT 'PENDING',  -- PENDING, SYNCING, SYNCED, FAILED
    error_message TEXT
);

-- Audit Log (append-only)
CREATE TABLE audit_log (
    id TEXT PRIMARY KEY NOT NULL,
    timestamp INTEGER NOT NULL,
    user_id TEXT NOT NULL,
    store_id TEXT NOT NULL,
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    old_value TEXT,
    new_value TEXT,
    device_id TEXT,
    metadata TEXT  -- JSON
);
-- No UPDATE/DELETE queries generated for audit_log
```

**Indices:**
```sql
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_active ON products(is_active);
CREATE INDEX idx_orders_created_at ON orders(created_at);
CREATE INDEX idx_orders_cashier ON orders(cashier_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_sync_queue_status ON sync_queue(status);
CREATE INDEX idx_sync_queue_entity ON sync_queue(entity_type, status);
CREATE INDEX idx_audit_log_timestamp ON audit_log(timestamp);
```

---

## 8. MVI Pattern Contract

### Standard ViewModel Template

```kotlin
/**
 * Base ViewModel for all ZyntaPOS feature screens.
 * Enforces MVI (Model-View-Intent) pattern with:
 * - [state] as immutable StateFlow consumed by the UI
 * - [effects] as one-shot SharedFlow for navigation and UI events
 * - [processIntent] as the single entry point for all UI interactions
 */
abstract class ZyntaViewModel<S, I, E>(
    initialState: S
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<E>(extraBufferCapacity = 64)
    val effects: SharedFlow<E> = _effects.asSharedFlow()

    /** Only public API for UI to send interactions */
    abstract fun processIntent(intent: I)

    protected fun updateState(reducer: S.() -> S) {
        _state.update { it.reducer() }
    }

    protected fun emitEffect(effect: E) {
        viewModelScope.launch { _effects.emit(effect) }
    }

    protected fun launchSafe(
        onError: (Throwable) -> Unit = { ZyntaLogger.e("ViewModel", it) },
        block: suspend CoroutineScope.() -> Unit
    ) = viewModelScope.launch {
        runCatching { block() }.onFailure(onError)
    }
}
```

---

## 9. Security Implementation Plan

### 9.1 Authentication Flow

```
1. User enters email + password on LoginScreen
2. LoginUseCase calls AuthRepository.login(email, password)
3. AuthRepositoryImpl:
   a. Checks if offline → validate against local bcrypt hash
   b. If online → POST /api/v1/auth/login, receive JWT + refresh token
   c. Stores JWT in SecurePreferences (encrypted)
   d. Caches user profile + role in local DB
4. Session created, navigate to Dashboard (or RegisterGuard)
5. JWT injected automatically in all Ktor requests via Auth plugin
6. On 401 response → refresh token flow → if refresh fails → force logout

Session Timeout:
- Configurable idle timeout (default: 15 min for Cashier, 30 min for Manager)
- SessionManager tracks last interaction timestamp
- On timeout → PinLockScreen (no re-login required, just PIN)
- PIN validated locally via PinManager.verifyPin()
```

### 9.2 Database Encryption

```
Key Generation (first launch only):
1. Generate 256-bit random key via SecureRandom
2. Store in Android Keystore (AndroidKeyStore provider) / JCE PKCS12 KeyStore (Desktop)
3. Retrieve key on every app launch to open SQLCipher database

No key is ever stored in plaintext or in the APK/JAR.
```

### 9.3 RBAC Permission Matrix (Phase 1)

| Screen / Action | Admin | Manager | Cashier | Accountant | Stock Mgr |
|----------------|-------|---------|---------|------------|-----------|
| POS - Full Access | ✅ | ✅ | ✅ | ❌ | ❌ |
| Apply Discount | ✅ | ✅ | ⚠️ (limited%) | ❌ | ❌ |
| Void Order | ✅ | ✅ | ❌ | ❌ | ❌ |
| Open Register | ✅ | ✅ | ✅ | ❌ | ❌ |
| Close Register | ✅ | ✅ | ❌ | ❌ | ❌ |
| Inventory CRUD | ✅ | ✅ | ❌ | ❌ | ✅ |
| Stock Adjustment | ✅ | ✅ | ❌ | ❌ | ✅ |
| Reports | ✅ | ✅ | ❌ | ✅ | ⚠️ (stock only) |
| Settings | ✅ | ⚠️ (limited) | ❌ | ❌ | ❌ |
| User Management | ✅ | ❌ | ❌ | ❌ | ❌ |

---

## 10. HAL Interface Contracts

### 10.1 Printer Connection Matrix

| Platform | Connection Type | Implementation Class |
|----------|---------------|----------------------|
| Android | USB | `AndroidUsbPrinterPort` |
| Android | Bluetooth | `AndroidBluetoothPrinterPort` |
| Desktop | Serial (COM) | `DesktopSerialPrinterPort` |
| Desktop | TCP/IP | `DesktopTcpPrinterPort` |
| Desktop | USB | `DesktopUsbPrinterPort` |

### 10.2 ESC/POS Command Support

| Command | Description | Required |
|---------|-------------|---------|
| `ESC @` | Initialize printer | ✅ |
| `ESC E` | Bold on/off | ✅ |
| `ESC a` | Alignment (L/C/R) | ✅ |
| `GS !` | Character size | ✅ |
| `GS V` | Paper cut (full/partial) | ✅ |
| `ESC p` | Cash drawer pulse | ✅ |
| `GS k` | Barcode print (Code128) | Phase 2 |
| `GS ( k` | QR code print | ✅ |

---

## 11. Testing Strategy (Phase 1)

### 11.1 Coverage Targets

| Module | Target Coverage | Test Type |
|--------|----------------|-----------|
| `:shared:domain` use cases | 95% | Unit (commonTest) |
| Tax calculation | 100% | Unit |
| Payment processing | 95% | Unit |
| `:shared:data` repositories | 80% | Integration (in-memory SQLDelight driver) |
| Sync engine | 85% | Integration (Ktor MockEngine) |
| `:shared:security` | 90% | Unit |
| Feature ViewModels | 80% | Unit (Mockative for use case mocks) |

### 11.2 Test Infrastructure

```
commonTest:
  - Kotlin Test (assertions)
  - Mockative (mock generation via KSP)
  - kotlinx-coroutines-test (TestScope, runTest, advanceTimeBy)
  - SQLDelight in-memory driver for repository tests
  - Ktor MockEngine for API client tests

androidInstrumentedTest:
  - Compose UI Testing (createComposeRule)
  - Critical flows: Login, POS add-to-cart, Payment

desktopTest:
  - Compose UI Testing (desktop variant)
```

### 11.3 Critical Test Scenarios

```
Authentication:
  ✅ Valid credentials → session created
  ✅ Invalid password → error state (no session)
  ✅ Account inactive → specific error
  ✅ Network offline → local credential validation
  ✅ Token expired → refresh flow

Tax Calculation (CalculateOrderTotalsUseCase):
  ✅ Single item, exclusive tax
  ✅ Single item, inclusive tax
  ✅ Multiple items, mixed tax groups
  ✅ Order discount applied before tax
  ✅ Rounding: total ≤ sum of line totals (no rounding up)
  ✅ Zero tax rate

Payment Processing:
  ✅ Cash: exact amount
  ✅ Cash: overpayment (change > 0)
  ✅ Cash: underpayment (rejected)
  ✅ Split: two methods summing to total
  ✅ Split: amounts don't sum to total (rejected)
  ✅ Stock decremented on successful payment
  ✅ Order written to sync queue after creation
```

---

## 12. Performance Targets

| Metric | Target | Validation Method |
|--------|--------|------------------|
| Cold start → POS screen | < 3s | Manual timing + Android Profiler |
| Product search (10K products) | < 200ms | FTS5 benchmark test |
| Add to cart | < 50ms | Compose recomposition trace |
| Payment processing (local) | < 800ms | Use case execution test |
| Receipt print trigger | < 2s | HAL timing test |
| DB write (order creation) | < 100ms | SQLDelight transaction test |
| Memory (active POS, Android) | < 256MB | Android Profiler heap |
| Offline sync push (50 ops) | < 10s | Integration test |

---

## 13. Risk Register (Phase 1 Specific)

| # | Risk | Probability | Impact | Mitigation |
|---|------|------------|--------|------------|
| R1 | SQLCipher KMP driver compatibility | Medium | High | Test on both platforms in Sprint 5; fallback to plaintext during dev |
| R2 | ESC/POS command set varies by printer brand | High | Medium | Implement configurable command map; test with 3 printer models |
| R3 | Compose Multiplatform Desktop rendering performance on large product grids | Medium | Medium | LazyGrid + `key` param + stable data classes; benchmark in Sprint 14 |
| R4 | Android Keystore unavailable on emulator | Low | Low | Use fallback SharedPreferences encryption during testing |
| R5 | jSerialComm native library conflicts | Low | Medium | Isolate in Desktop-only submodule; use provided scope |
| R6 | Barcode scanner HID input conflicts with keyboard | Medium | Medium | Scan prefix/suffix detection with configurable separator |
| R7 | KMP Navigation type-safe routes serialization | Low | Medium | Test complex route params in Sprint 11 |
| R8 | BCrypt not available in KMP commonMain | Medium | Medium | Use expect/actual: JVM BCrypt (jBCrypt), bridge for Android |

---

## 14. Definition of Done (DoD)

A module is considered **DONE** when ALL of the following are true:

```
Code Quality:
  □ All public APIs have KDoc (param, return, throws, business rule description)
  □ No compiler warnings in the module
  □ Code passes ktlint formatting check
  □ SOLID principles verified in code review

Testing:
  □ Unit test coverage meets module target (§11.1)
  □ All critical test scenarios pass (§11.3 where applicable)
  □ No flaky tests (3 consecutive clean runs)

Functionality:
  □ All tasks in the sprint checklist marked complete
  □ Feature works offline (no network required for core operations)
  □ Dark mode: all screens render correctly
  □ Responsive: tested at Compact (360dp), Medium (720dp), Expanded (1280dp)

Security:
  □ No hardcoded secrets, passwords, or API keys
  □ All sensitive data routes through SecurePreferences or Keystore
  □ Role-based access gating verified for protected screens

Performance:
  □ Relevant performance targets met (§12)
  □ No unnecessary recompositions (Compose compiler metrics checked)

Logging:
  □ execution_log.md updated with [x] Finished status and timestamp
```

---

## Appendix A: File Naming Conventions

| Type | Convention | Example |
|------|-----------|---------|
| Domain Model | `{Entity}.kt` | `Product.kt`, `Order.kt` |
| Use Case | `{Verb}{Entity}UseCase.kt` | `ProcessPaymentUseCase.kt` |
| Repository Interface | `{Entity}Repository.kt` | `ProductRepository.kt` |
| Repository Impl | `{Entity}RepositoryImpl.kt` | `ProductRepositoryImpl.kt` |
| ViewModel | `{Feature}ViewModel.kt` | `PosViewModel.kt` |
| Screen | `{Feature}Screen.kt` | `LoginScreen.kt` |
| Component | `Zynta{Component}.kt` | `ZyntaButton.kt` |
| MVI State | `{Feature}State.kt` | `PosState.kt` |
| MVI Intent | `{Feature}Intent.kt` | `PosIntent.kt` |
| MVI Effect | `{Feature}Effect.kt` | `PosEffect.kt` |
| Koin Module | `{Feature}Module.kt` | `PosModule.kt` |

---

## Appendix B: Package Structure

```
com.zyntasolutions.zyntapos
  ├── core/           (shared:core)
  │   ├── result/
  │   ├── logger/
  │   ├── config/
  │   ├── extensions/
  │   └── utils/
  ├── domain/         (shared:domain)
  │   ├── model/
  │   ├── usecase/
  │   │   ├── auth/
  │   │   ├── pos/
  │   │   ├── inventory/
  │   │   ├── register/
  │   │   └── reports/
  │   ├── repository/
  │   └── validation/
  ├── data/           (shared:data)
  │   ├── local/
  │   │   ├── db/
  │   │   └── mapper/
  │   ├── remote/
  │   │   ├── api/
  │   │   └── dto/
  │   ├── repository/
  │   └── sync/
  ├── hal/            (shared:hal)
  │   ├── printer/
  │   └── scanner/
  ├── security/       (shared:security)
  │   ├── crypto/
  │   ├── token/
  │   └── keystore/
  └── ui/             (composeApp)
      ├── designsystem/
      ├── navigation/
      └── feature/
          ├── auth/
          ├── pos/
          ├── inventory/
          ├── register/
          ├── reports/
          └── settings/
```

---

---

## Appendix D: Phase 1 Hotfixes

Compile errors and architectural violations discovered during Phase 1 execution and resolved post-sprint. Each hotfix references the affected sprint step and links to the master resolution in `Master_plan.md` Appendix C.

---

### HOTFIX-01 — ReceiptFormatter: Static CurrencyFormatter Call (2026-02-22)

**Affects:** Sprint 1–2 (step 1.2.9 — CurrencyFormatter) + Sprint 3–4 (ReceiptFormatter)
**Error:** `:shared:domain:assemble` — unresolved reference; `CurrencyFormatter` has no companion object
**Fix:** Constructor Injection — `CurrencyFormatter` added as constructor parameter to `ReceiptFormatter`; `PosModule.kt` Koin binding updated to `factory { ReceiptFormatter(currencyFormatter = get()) }`
**Files changed:** `shared/domain/.../formatter/ReceiptFormatter.kt`, `composeApp/feature/pos/.../PosModule.kt`
**Full resolution:** Master_plan.md Appendix C.1

---

### HOTFIX-02 — PrintTestPageUseCase: Illegal Default on fun interface (2026-02-22)

**Affects:** Sprint 23 (step 13.1.5 — PrinterSettingsScreen / PrintTestPageUseCase)
**Error:** `:shared:domain:assemble` — *"Functional interface abstract method cannot have a default value"*
**Fix:** Option A — removed `= PrinterPaperWidth.MM_80` default. `SettingsViewModel.testPrint()` was already architecturally correct (passes `paperWidth` explicitly from state). Zero call-site, impl, or test changes required.
**Files changed:** `shared/domain/.../usecase/settings/PrintTestPageUseCase.kt`
**Full resolution:** Master_plan.md Appendix C.2

---

*End of PLAN_PHASE1.md — ZyntaPOS Phase 1 MVP*
*Document ID: ZENTA-PLAN-PHASE1-v1.0 | Status: READY FOR EXECUTION*
