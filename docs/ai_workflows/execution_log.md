# ZentaPOS — AI Execution Log
> **Doc ID:** ZENTA-EXEC-LOG-v1.0  
> **Architecture:** KMP — Desktop (JVM) + Android  
> **Strategy:** Clean Architecture · MVI · Koin · SQLDelight · Compose Multiplatform  
> **Log Created:** 2026-02-20  
> **Status:** 🟡 PENDING EXECUTION

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
- **Missing:** All feature modules (auth, pos, inventory, register, reports, settings, etc.)
- **Missing:** Koin DI, SQLDelight, Ktor, kotlinx-datetime, Kermit, Coil, SQLCipher
- **Missing:** MVI base infrastructure, Design System, Navigation graph
- **Missing:** `docs/` directory structure
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
> **Status:** 🔴 NOT STARTED

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
- [ ] P0.3.1 — Configure `local.properties` with API key placeholders; add Secrets Gradle Plugin wiring
- [ ] P0.3.2 — Update `.gitignore`: exclude `local.properties`, `*.jks`, `*.keystore`, build outputs
- [ ] P0.3.3 — Create `README.md` with architecture overview, setup guide, and module map
- [ ] P0.3.4 — Verify full project sync and clean build succeeds (both Android + JVM targets)

---

## ═══════════════════════════════════════════
## PHASE 1 — MVP (Months 1–6)
## ═══════════════════════════════════════════
> **Goal:** Fully functional single-store POS with offline capability on Android + Desktop  
> **Status:** 🔴 NOT STARTED

---

### M01 — :shared:core
> Constants, extensions, base MVI, crypto utilities, result types

- [ ] M01.1 — Create `AppConstants.kt`: API endpoints, timeout configs, pagination defaults
- [ ] M01.2 — Create `Result.kt` sealed class: `Success`, `Error`, `Loading` with extension functions
- [ ] M01.3 — Create MVI base: `UiState`, `UiIntent`, `UiEffect` interfaces + `BaseViewModel` with `StateFlow`
- [ ] M01.4 — Create `DateTimeUtils.kt`: kotlinx-datetime wrappers for formatting, comparison, ISO parsing
- [ ] M01.5 — Create `CurrencyUtils.kt`: multi-currency formatting, rounding modes, tax precision
- [ ] M01.6 — Create `ValidationUtils.kt`: email, phone, TIN, GSTIN, quantity, price validators
- [ ] M01.7 — Create Koin module: `coreModule` DI bindings (dispatchers, logger)
- [ ] M01.8 — Write unit tests for `Result`, `CurrencyUtils`, `ValidationUtils`

### M02 — :shared:domain
> Use cases, domain models, repository interfaces, business rules

#### M02.A — Domain Models
- [ ] M02.A.1 — `User.kt`, `Role.kt`, `Permission.kt`, `Store.kt` (RBAC foundation)
- [ ] M02.A.2 — `Product.kt`, `Category.kt`, `ProductVariant.kt`, `PriceLevel.kt`
- [ ] M02.A.3 — `Order.kt`, `OrderItem.kt`, `OrderStatus.kt`, `Discount.kt`
- [ ] M02.A.4 — `Payment.kt`, `PaymentMethod.kt`, `SplitPayment.kt`
- [ ] M02.A.5 — `Customer.kt`, `LoyaltyAccount.kt`
- [ ] M02.A.6 — `TaxGroup.kt`, `TaxRate.kt`, `TaxCalculationResult.kt`
- [ ] M02.A.7 — `CashRegister.kt`, `CashSession.kt`, `CashMovement.kt`
- [ ] M02.A.8 — `Stock.kt`, `StockAdjustment.kt`, `StockTransfer.kt`
- [ ] M02.A.9 — `AuditLog.kt` (tamper-evident audit trail model)
- [ ] M02.A.10 — `SyncRecord.kt`, `SyncConflict.kt` (CRDT metadata)

#### M02.B — Repository Interfaces
- [ ] M02.B.1 — `AuthRepository`, `UserRepository`
- [ ] M02.B.2 — `ProductRepository`, `CategoryRepository`
- [ ] M02.B.3 — `OrderRepository`, `CartRepository`
- [ ] M02.B.4 — `PaymentRepository`
- [ ] M02.B.5 — `CustomerRepository`
- [ ] M02.B.6 — `StockRepository`
- [ ] M02.B.7 — `CashRegisterRepository`
- [ ] M02.B.8 — `ReportRepository`
- [ ] M02.B.9 — `SettingsRepository`, `SyncRepository`
- [ ] M02.B.10 — `AuditRepository`

#### M02.C — Use Cases (Auth + POS + Inventory + Register)
- [ ] M02.C.1 — `LoginUseCase`, `LogoutUseCase`, `RefreshTokenUseCase`, `ValidatePinUseCase`
- [ ] M02.C.2 — `GetProductsUseCase`, `SearchProductsUseCase`, `GetProductByBarcodeUseCase`
- [ ] M02.C.3 — `AddToCartUseCase`, `UpdateCartItemUseCase`, `RemoveFromCartUseCase`, `ClearCartUseCase`
- [ ] M02.C.4 — `ApplyDiscountUseCase`, `ApplyCouponUseCase`
- [ ] M02.C.5 — `CalculateTaxUseCase` (multi-group, inclusive/exclusive, rounding to 2dp)
- [ ] M02.C.6 — `ProcessSaleUseCase` (atomic: stock deduct + order save + payment record)
- [ ] M02.C.7 — `ProcessRefundUseCase`, `VoidOrderUseCase`
- [ ] M02.C.8 — `OpenCashRegisterUseCase`, `CloseCashRegisterUseCase`, `RecordCashMovementUseCase`
- [ ] M02.C.9 — `AdjustStockUseCase`, `TransferStockUseCase`, `GetStockLevelsUseCase`
- [ ] M02.C.10 — `GetSalesSummaryUseCase`, `GetStockReportUseCase`

#### M02.D — Business Rule Validators
- [ ] M02.D.1 — `TaxValidator`: tax group completeness, rate range (0–100%)
- [ ] M02.D.2 — `PaymentValidator`: split payment total equals order total, method availability
- [ ] M02.D.3 — `StockValidator`: negative stock prevention, min quantity rules
- [ ] M02.D.4 — Unit tests for all use cases (Mockative for repository mocks, 95% coverage targets)

### M03 — :shared:data
> SQLDelight schema, DAOs, Ktor client, repository implementations, sync engine

#### M03.A — SQLDelight Database Schema
- [ ] M03.A.1 — `Users.sq`: users, sessions, refresh_tokens, pin_hash
- [ ] M03.A.2 — `Products.sq`: products, categories, variants, price_levels, barcodes
- [ ] M03.A.3 — `Orders.sq`: orders, order_items, discounts, void_records
- [ ] M03.A.4 — `Payments.sq`: payments, split_payments, payment_methods
- [ ] M03.A.5 — `Customers.sq`: customers, loyalty_accounts, loyalty_transactions
- [ ] M03.A.6 — `Tax.sq`: tax_groups, tax_rates, tax_exemptions
- [ ] M03.A.7 — `Stock.sq`: stock_levels, adjustments, transfers, alerts
- [ ] M03.A.8 — `CashRegister.sq`: registers, sessions, cash_movements
- [ ] M03.A.9 — `AuditLog.sq`: audit_log (append-only, hash chain column)
- [ ] M03.A.10 — `SyncQueue.sq`: sync_queue, sync_conflicts, vector_clocks
- [ ] M03.A.11 — Configure FTS5 virtual tables for product search (name, barcode, description)
- [ ] M03.A.12 — Configure SQLCipher encryption driver (AES-256) for `DatabaseDriverFactory` (expect/actual)

#### M03.B — DAO Implementations (SQLDelight Queries)
- [ ] M03.B.1 — `UserDao`: CRUD + session management queries
- [ ] M03.B.2 — `ProductDao`: paginated list, FTS search, barcode lookup, Flow-based reactive queries
- [ ] M03.B.3 — `OrderDao`: create order, get by status, update status, Flow stream
- [ ] M03.B.4 — `PaymentDao`: record payment, get by order
- [ ] M03.B.5 — `CustomerDao`: CRUD, loyalty balance update
- [ ] M03.B.6 — `StockDao`: get levels, adjust, transfer, low-stock alert query
- [ ] M03.B.7 — `CashRegisterDao`: session lifecycle, cash movement recording
- [ ] M03.B.8 — `AuditDao`: append-only insert, paginated fetch, hash chain verification

#### M03.C — Ktor HTTP Client
- [ ] M03.C.1 — Create `ApiClient.kt`: Ktor config (ContentNegotiation/JSON, Auth, Logging, Retry, Timeout)
- [ ] M03.C.2 — Create auth interceptor: Bearer token injection + 401 refresh token flow
- [ ] M03.C.3 — Create DTOs: `AuthDto`, `ProductDto`, `OrderDto`, `PaymentDto`, `SyncDto`
- [ ] M03.C.4 — Create API service interfaces: `AuthApiService`, `ProductApiService`, `OrderApiService`, `SyncApiService`
- [ ] M03.C.5 — Create API service implementations with error mapping to domain `Result`

#### M03.D — Repository Implementations
- [ ] M03.D.1 — `AuthRepositoryImpl`: local session + remote auth, PIN hash (PBKDF2)
- [ ] M03.D.2 — `ProductRepositoryImpl`: offline-first (local first, remote pull on sync)
- [ ] M03.D.3 — `OrderRepositoryImpl`: local save, queue for sync
- [ ] M03.D.4 — `PaymentRepositoryImpl`: local record + remote push
- [ ] M03.D.5 — `CustomerRepositoryImpl`: CRUD + loyalty operations
- [ ] M03.D.6 — `StockRepositoryImpl`: local adjustment + sync queue
- [ ] M03.D.7 — `CashRegisterRepositoryImpl`: session management
- [ ] M03.D.8 — `ReportRepositoryImpl`: aggregate queries via DAO

#### M03.E — Sync Engine (Basic Push/Pull)
- [ ] M03.E.1 — `SyncQueue.kt`: enqueue operation, dequeue batch, mark success/failure
- [ ] M03.E.2 — `SyncManager.kt`: coroutine-based background sync coordinator (WorkManager on Android, coroutine on Desktop)
- [ ] M03.E.3 — `ConflictResolver.kt`: Last-Write-Wins strategy (Phase 1), CRDT foundation for Phase 2
- [ ] M03.E.4 — Koin `dataModule` DI bindings (DAOs, repositories, ApiClient, SyncManager)
- [ ] M03.E.5 — Integration tests: SQLDelight in-memory DB tests, Ktor MockEngine tests

### M04 — :shared:hal
> Hardware Abstraction Layer using expect/actual

- [ ] M04.1 — Define `PrinterManager` interface: `connect()`, `printReceipt(ReceiptData)`, `printReport(ReportData)`, `openCashDrawer()`, `disconnect()`
- [ ] M04.2 — Define `BarcodeScanner` interface: `startScan()`, `stopScan()`, Flow-based `barcodeStream: Flow<String>`
- [ ] M04.3 — Define `CashDrawerController` interface: `pulse()`, `isOpen(): Boolean`
- [ ] M04.4 — Define `ReceiptData` model: items, totals, tax lines, payment info, store info, QR data
- [ ] M04.5 — `expect class PrinterDriverFactory` with `actual` stubs for androidMain and jvmMain
- [ ] M04.6 — `androidMain` actual: USB ESC/POS printer via Android USB Host API (stub + interface)
- [ ] M04.7 — `jvmMain` actual: TCP/IP ESC/POS printer via raw socket (stub + interface)  
- [ ] M04.8 — `androidMain` actual: Camera-based barcode scanner via ML Kit (CameraX integration)
- [ ] M04.9 — `jvmMain` actual: USB HID barcode scanner via keyboard event simulation
- [ ] M04.10 — ESC/POS command encoder: `EscPosEncoder.kt` in `commonMain` (text, cut, barcode, QR, image)
- [ ] M04.11 — Koin `halModule` DI bindings

### M05 — :shared:security
> Encryption, secure key storage, token management, RBAC engine

- [ ] M05.1 — `expect class SecureKeyStorage` with `actual` for Android Keystore and JVM JCE KeyStore
- [ ] M05.2 — `CryptoManager.kt`: AES-256-GCM encrypt/decrypt for sensitive fields (commonMain interface, platform actual)
- [ ] M05.3 — `PinHasher.kt`: PBKDF2-HMAC-SHA256 pin hashing with salt (100,000 iterations)
- [ ] M05.4 — `TokenManager.kt`: JWT parsing, expiry check, secure storage + retrieval
- [ ] M05.5 — `RbacEngine.kt`: permission matrix evaluation, role hierarchy resolution, store scoping
- [ ] M05.6 — `SessionManager.kt`: auto-logout timer, session state Flow, device binding
- [ ] M05.7 — `BiometricAuthHelper` expect/actual: Android BiometricPrompt, JVM no-op/password fallback
- [ ] M05.8 — Koin `securityModule` DI bindings
- [ ] M05.9 — Unit tests: crypto round-trip, PIN hash verification, RBAC permission matrix

### M06 — :composeApp:designsystem
> Material 3 theme, typography, color tokens, reusable components

- [ ] M06.1 — `ZentaTheme.kt`: `MaterialTheme` wrapper with Light/Dark color schemes, Dynamic Color (Android 12+)
- [ ] M06.2 — `ZentaColorScheme.kt`: brand color tokens (primary, secondary, tertiary, error + dark variants)
- [ ] M06.3 — `ZentaTypography.kt`: Material 3 type scale with custom font (Roboto Flex or similar)
- [ ] M06.4 — `ZentaShapes.kt`: shape definitions (small/medium/large corner radii for POS touch targets)
- [ ] M06.5 — `Spacing.kt`: 4dp grid system constants (`dp4`, `dp8`, `dp12`, `dp16`, `dp24`, `dp32`, `dp48`)
- [ ] M06.6 — Component: `ZentaButton` (primary/secondary/destructive/ghost variants, loading state)
- [ ] M06.7 — Component: `ZentaTextField` (outlined, error state, leading/trailing icons, keyboard options)
- [ ] M06.8 — Component: `ZentaCard` (elevated, outlined, product card variants)
- [ ] M06.9 — Component: `ZentaTopBar` (platform-adaptive: desktop title bar vs Android TopAppBar)
- [ ] M06.10 — Component: `ZentaSnackbar`, `ZentaDialog` (confirmation, info, error variants)
- [ ] M06.11 — Component: `ZentaBadge`, `ZentaChip`, `ZentaStatusPill` (order status colors)
- [ ] M06.12 — Component: `NumericKeypad` (POS cash input widget, PIN input variant)
- [ ] M06.13 — Component: `ReceiptPreview` composable (scrollable receipt layout)
- [ ] M06.14 — Component: `LoadingOverlay`, `EmptyState`, `ErrorState` with retry action
- [ ] M06.15 — Responsive layout util: `WindowSizeClass` detection for tablet vs desktop vs phone breakpoints
- [ ] M06.16 — UI tests for all components (screenshot + interaction)

### M07 — :composeApp:navigation
> Type-safe navigation graph with Compose Navigation

- [ ] M07.1 — Define sealed `NavRoute` hierarchy: `Auth`, `Pos`, `Inventory`, `Register`, `Reports`, `Settings`
- [ ] M07.2 — Create `ZentaNavHost.kt`: root `NavHost` with all top-level routes registered
- [ ] M07.3 — Platform-adaptive navigation shell: `NavigationRail` (Desktop/Tablet) vs `NavigationBar` (Phone)
- [ ] M07.4 — Deep link support: `zentapos://` scheme for hardware scanner triggers and notification routing
- [ ] M07.5 — `NavigationViewModel.kt`: auth-gated navigation, RBAC-based route filtering
- [ ] M07.6 — Back stack management: desktop-style nested navigation for multi-pane layouts

### M08 — :composeApp:feature:auth
> Login, PIN entry, session UI, offline login support

- [ ] M08.1 — `AuthState.kt`, `AuthIntent.kt`, `AuthEffect.kt` (MVI contracts)
- [ ] M08.2 — `AuthViewModel.kt`: `LoginUseCase` + `ValidatePinUseCase` invocation, state emission
- [ ] M08.3 — `LoginScreen.kt`: username/password form, biometric trigger, "Remember Me", error display
- [ ] M08.4 — `PinScreen.kt`: numeric keypad PIN entry (cashier quick-switch)
- [ ] M08.5 — `OfflineLoginBanner`: connectivity indicator with offline mode confirmation
- [ ] M08.6 — `LockScreen.kt`: auto-lock after idle timeout, PIN re-entry
- [ ] M08.7 — Koin `authModule` (ViewModel injection)
- [ ] M08.8 — Unit tests: AuthViewModel state transitions, LoginUseCase mock flows

### M09 — :composeApp:feature:pos
> POS screen, cart management, payment flow, receipt generation

- [ ] M09.1 — `PosState.kt`, `PosIntent.kt`, `PosEffect.kt` (MVI contracts)
- [ ] M09.2 — `PosViewModel.kt`: cart management, product search, discount, tax calculation, payment orchestration
- [ ] M09.3 — `PosScreen.kt`: two-pane layout (product grid left, cart right) — responsive for tablet/desktop
- [ ] M09.4 — `ProductGrid.kt`: `LazyVerticalGrid` with `key=` for stable recomposition, category filter tabs
- [ ] M09.5 — `ProductSearchBar.kt`: FTS-powered debounced search (300ms), barcode scan trigger
- [ ] M09.6 — `CartPanel.kt`: cart items list, quantity +/-, remove, discount badge, subtotal/tax/total summary
- [ ] M09.7 — `DiscountSheet.kt`: percentage/fixed/manual discount entry bottom sheet
- [ ] M09.8 — `PaymentSheet.kt`: payment method selection, split payment UI, cash tendered/change calculator
- [ ] M09.9 — `ReceiptScreen.kt`: receipt preview with print/email/WhatsApp/skip options
- [ ] M09.10 — `CustomerLinkSheet.kt`: search/attach customer to order, loyalty points preview
- [ ] M09.11 — `OrderHoldSheet.kt`: hold current order, retrieve held orders
- [ ] M09.12 — `RefundScreen.kt`: select order, select items for partial/full refund, process refund flow
- [ ] M09.13 — Barcode scanner integration: `LaunchedEffect` subscribing to `BarcodeScanner.barcodeStream`
- [ ] M09.14 — Koin `posModule`, UI tests for cart flows and payment flows

### M10 — :composeApp:feature:inventory
> Product catalog management, categories, stock viewing

- [ ] M10.1 — `InventoryState.kt`, `InventoryIntent.kt`, `InventoryEffect.kt`
- [ ] M10.2 — `InventoryViewModel.kt`: paginated product list, search, filter, CRUD operations
- [ ] M10.3 — `InventoryListScreen.kt`: searchable/filterable product list with stock level badges
- [ ] M10.4 — `ProductDetailScreen.kt`: full product form (name, SKU, barcode, category, tax group, variants, images)
- [ ] M10.5 — `ProductFormScreen.kt`: create/edit product with validation, image picker (Coil)
- [ ] M10.6 — `CategoryManagementScreen.kt`: tree-style category CRUD
- [ ] M10.7 — `StockLevelScreen.kt`: current stock per product/location, low-stock highlights
- [ ] M10.8 — `StockAdjustmentScreen.kt`: manual adjustment entry with reason code + audit trail trigger
- [ ] M10.9 — Barcode label print trigger from product detail
- [ ] M10.10 — Koin `inventoryModule`, unit + UI tests

### M11 — :composeApp:feature:register
> Cash register open/close, cash movements, EOD reconciliation

- [ ] M11.1 — `RegisterState.kt`, `RegisterIntent.kt`, `RegisterEffect.kt`
- [ ] M11.2 — `RegisterViewModel.kt`: session lifecycle, cash calculation, discrepancy detection
- [ ] M11.3 — `OpenRegisterScreen.kt`: opening float entry, confirmation
- [ ] M11.4 — `RegisterDashboard.kt`: live session summary (sales count, cash balance, card totals)
- [ ] M11.5 — `CashMovementScreen.kt`: cash in / cash out with reason and amount
- [ ] M11.6 — `CloseRegisterScreen.kt`: counted cash entry, system vs counted comparison, discrepancy flag
- [ ] M11.7 — `EODReportScreen.kt`: end-of-day Z-report summary with print option
- [ ] M11.8 — Koin `registerModule`, unit tests for discrepancy logic

### M12 — :composeApp:feature:reports (Phase 1 scope: Sales + Stock)
> Sales summary, product performance, stock report

- [ ] M12.1 — `ReportsState.kt`, `ReportsIntent.kt`, `ReportsEffect.kt`
- [ ] M12.2 — `ReportsViewModel.kt`: date range selection, async report loading
- [ ] M12.3 — `SalesSummaryScreen.kt`: daily/weekly/monthly sales totals, chart (recharts/Compose Canvas)
- [ ] M12.4 — `ProductSalesReport.kt`: top products table, sortable columns
- [ ] M12.5 — `StockReportScreen.kt`: current stock snapshot, valuation, low-stock list
- [ ] M12.6 — `DateRangePickerBar.kt`: reusable date range selector component
- [ ] M12.7 — Export to CSV/PDF: `ReportExporter.kt` (JVM: file, Android: share sheet)
- [ ] M12.8 — Koin `reportsModule`

### M18 — :composeApp:feature:settings (Phase 1 core scope)
> Store settings, tax configuration, printer setup, user management, security

- [ ] M18.1 — `SettingsState.kt`, `SettingsIntent.kt`, `SettingsEffect.kt`
- [ ] M18.2 — `SettingsViewModel.kt`: settings CRUD, validation
- [ ] M18.3 — `StoreProfileScreen.kt`: store name, address, logo, TIN, currency
- [ ] M18.4 — `TaxConfigScreen.kt`: tax group CRUD, rate entry, inclusive/exclusive toggle
- [ ] M18.5 — `PrinterSetupScreen.kt`: printer type (USB/TCP), IP/port entry, test print
- [ ] M18.6 — `UserManagementScreen.kt`: list users, create/edit/deactivate, assign role
- [ ] M18.7 — `RolePermissionsScreen.kt`: permission matrix display per role
- [ ] M18.8 — `SecuritySettingsScreen.kt`: PIN policy, auto-lock timeout, session duration
- [ ] M18.9 — `BackupRestoreScreen.kt`: manual backup trigger, restore from file
- [ ] M18.10 — `AppearanceScreen.kt`: Dark/Light mode toggle, theme color selection
- [ ] M18.11 — Koin `settingsModule`

### Phase 1 — Integration & QA
- [ ] P1.QA.1 — End-to-end integration test: full sale flow (product search → cart → payment → receipt → stock deducted)
- [ ] P1.QA.2 — End-to-end integration test: offline sale → sync queue → reconnect → data confirmed on server
- [ ] P1.QA.3 — HAL integration test: ESC/POS receipt print via test printer (Android USB + Desktop TCP)
- [ ] P1.QA.4 — Performance test: product search latency < 200ms on 50K products (FTS5)
- [ ] P1.QA.5 — Security audit: SQLCipher encryption verified, Keystore key not extractable, audit log hash chain valid
- [ ] P1.QA.6 — Build validation: Android APK (minSdk 24) + Desktop JAR both produce runnable artifacts
- [ ] P1.QA.7 — RBAC smoke test: cashier cannot access settings/reports, manager can, admin can

---

## ═══════════════════════════════════════════
## PHASE 2 — GROWTH (Months 7–12)
## ═══════════════════════════════════════════
> **Goal:** Multi-store, CRM, promotions, financial tools, CRDT sync  
> **Status:** 🔴 NOT STARTED (Blocked on Phase 1 completion)

### M13 — :composeApp:feature:crm
- [ ] M13.1 — Customer domain models: `LoyaltyTier`, `PointsTransaction`, `CustomerSegment`
- [ ] M13.2 — Use cases: `GetCustomerHistoryUseCase`, `IssueLoyaltyPointsUseCase`, `RedeemPointsUseCase`
- [ ] M13.3 — `CustomerListScreen.kt`: searchable customer directory with loyalty tier badges
- [ ] M13.4 — `CustomerProfileScreen.kt`: purchase history, loyalty balance, points log, GDPR export
- [ ] M13.5 — `CustomerFormScreen.kt`: create/edit with opt-in consent checkboxes
- [ ] M13.6 — `LoyaltyConfigScreen.kt` (Settings): points-per-currency rules, tier thresholds, expiry policy
- [ ] M13.7 — GDPR: `CustomerDataExportUseCase`, `CustomerEraseUseCase` (soft-delete + anonymize)
- [ ] M13.8 — Unit tests for loyalty calculation engine (points accrual, tier promotion)

### M14 — :composeApp:feature:coupons
- [ ] M14.1 — Domain models: `Coupon`, `CouponRedemption`, `Promotion`, `PromotionRule`
- [ ] M14.2 — Use cases: `ValidateCouponUseCase`, `ApplyPromotionUseCase`, `GetActivePromotionsUseCase`
- [ ] M14.3 — Promotion rule engine: BOGO, percentage discount, fixed amount, minimum order threshold
- [ ] M14.4 — `CouponManagementScreen.kt`: CRUD coupons, usage stats
- [ ] M14.5 — `PromotionBuilderScreen.kt`: rule-based promotion wizard
- [ ] M14.6 — POS integration: auto-apply eligible promotions at checkout
- [ ] M14.7 — Unit tests: rule engine edge cases, stacking rules, expiry validation

### M15 — :composeApp:feature:multistore
- [ ] M15.1 — Multi-tenant domain models: store hierarchy, inter-store transfer, central inventory
- [ ] M15.2 — Use cases: `SwitchStoreUseCase`, `GetAllStoresUseCase`, `GetCentralDashboardUseCase`
- [ ] M15.3 — `StoreSelectorScreen.kt`: store list with connectivity status indicators
- [ ] M15.4 — `CentralDashboardScreen.kt`: aggregated KPIs across all stores (sales, stock, staff)
- [ ] M15.5 — `InterStoreTransferScreen.kt`: stock transfer request + approval flow
- [ ] M15.6 — Implement CRDT vector clocks in `SyncRecord` for proper multi-store conflict resolution
- [ ] M15.7 — WebSocket support in Ktor client for real-time central dashboard updates

### M16 — :composeApp:feature:expenses
- [ ] M16.1 — Domain models: `Expense`, `ExpenseCategory`, `ExpenseReport`, `AccountEntry`
- [ ] M16.2 — Use cases: `RecordExpenseUseCase`, `GetExpenseSummaryUseCase`, `GeneratePLUseCase`
- [ ] M16.3 — `ExpenseListScreen.kt`: categorized expense log with date filter
- [ ] M16.4 — `ExpenseFormScreen.kt`: amount, category, receipt image (Coil), notes
- [ ] M16.5 — `ProfitLossScreen.kt`: P&L statement for selected period
- [ ] M16.6 — `CashFlowScreen.kt`: cash in/out timeline visualization

### M12 Extended — :composeApp:feature:reports (Financial + Customer)
- [ ] M12.EX.1 — `FinancialReportScreen.kt`: P&L, gross margin, expense breakdown
- [ ] M12.EX.2 — `CustomerReportScreen.kt`: top customers, LTV, churn, cohort analysis
- [ ] M12.EX.3 — `TaxReportScreen.kt`: tax collected by group, period, inclusive/exclusive split

### Phase 2 — Multi-language & Advanced Sync
- [ ] P2.L10N.1 — Compose Multiplatform resource strings: `strings.xml` for English, Sinhala, Tamil
- [ ] P2.L10N.2 — RTL layout support verification for future Arabic support
- [ ] P2.SYNC.1 — Full CRDT merge implementation: G-Counter, LWW-Register for order/stock records
- [ ] P2.SYNC.2 — Conflict resolution UI: admin notification + manual override screen for sync conflicts
- [ ] P2.QA.1 — Multi-store integration tests, CRDT merge correctness tests

---

## ═══════════════════════════════════════════
## PHASE 3 — ENTERPRISE (Months 13–18)
## ═══════════════════════════════════════════
> **Goal:** Full enterprise features, compliance, staff management, administration  
> **Status:** 🔴 NOT STARTED (Blocked on Phase 2 completion)

### M17 — :composeApp:feature:staff
- [ ] M17.1 — Domain models: `Employee`, `Shift`, `AttendanceRecord`, `PayrollEntry`, `Schedule`
- [ ] M17.2 — Use cases: `ClockInUseCase`, `ClockOutUseCase`, `GeneratePayrollUseCase`, `GetAttendanceUseCase`
- [ ] M17.3 — `StaffListScreen.kt`: employee directory with active/inactive status
- [ ] M17.4 — `EmployeeProfileScreen.kt`: personal info, role, wage details, document uploads
- [ ] M17.5 — `AttendanceScreen.kt`: daily clock-in/out log, late/absent flags
- [ ] M17.6 — `ScheduleScreen.kt`: weekly shift planner (drag-and-drop calendar grid)
- [ ] M17.7 — `PayrollScreen.kt`: period payroll summary, deductions, net pay, export
- [ ] M17.8 — `PerformanceDashboard.kt`: sales per staff member, conversion rate

### M19 — :composeApp:feature:admin
- [ ] M19.1 — `SystemHealthScreen.kt`: DB size, sync queue depth, last sync time, error log
- [ ] M19.2 — `AuditLogScreen.kt`: searchable/filterable audit trail viewer (tamper-evident indicator)
- [ ] M19.3 — `BackupManagementScreen.kt`: scheduled backups, cloud upload, restore wizard
- [ ] M19.4 — `DatabaseMaintenanceScreen.kt`: VACUUM trigger, WAL checkpoint, index rebuild
- [ ] M19.5 — `ModuleMarketplaceScreen.kt`: placeholder for future plugin system
- [ ] M19.6 — Admin-only RBAC: all admin screens gate-kept by `Permission.SYSTEM_ADMIN`

### M20 — :composeApp:feature:media
- [ ] M20.1 — `MediaManagerScreen.kt`: product image browser, upload, crop (Coil + platform file picker)
- [ ] M20.2 — `ImagePickerHelper` expect/actual: Android photo picker, JVM JFileChooser
- [ ] M20.3 — Image compression pipeline: resize to max 800×800, WebP encoding before local store

### E-Invoicing — Sri Lanka 2026 Compliance
- [ ] EI.1 — `EInvoice.kt` domain model: IRD-compliant invoice fields, QR payload spec
- [ ] EI.2 — `EInvoiceGeneratorUseCase`: build IRD-format invoice from `Order`
- [ ] EI.3 — Digital signature integration: JCA-based signing with government certificate
- [ ] EI.4 — `EInvoiceApiService`: real-time report submission to IRD API endpoint
- [ ] EI.5 — `EInvoiceAuditLog`: immutable append-only log for submitted invoices (7-year retention)
- [ ] EI.6 — `EInvoiceConfigScreen.kt` in Settings: IRD credentials, certificate upload, test submission

### Phase 3 — Advanced Analytics & Performance
- [ ] P3.ANALYTICS.1 — `AnalyticsDashboardScreen.kt`: custom KPI widgets, trend charts
- [ ] P3.ANALYTICS.2 — `WarehouseLocationScreen.kt`: rack/bin location management for stock
- [ ] P3.PERF.1 — Full performance audit: cold start < 3s, POS memory < 256MB on mid-range Android
- [ ] P3.PERF.2 — Paging 3 / SQLDelight offset pagination for all large lists (orders, audit log)
- [ ] P3.SECURITY.1 — PCI-DSS SAQ-B compliance checklist verification
- [ ] P3.SECURITY.2 — Penetration test simulation: SQLi prevention, token exposure, key extraction attempt
- [ ] P3.QA.1 — Full regression test suite: all 3 phases, automated CI/CD pipeline (GitHub Actions)
- [ ] P3.QA.2 — Load test: 50+ stores, 5 concurrent registers per store, 100K product DB

---

## 📋 CROSS-CUTTING CONCERNS (All Phases)

### Security (Ongoing)
- [ ] SEC.1 — Regular dependency vulnerability scan (Gradle Versions Plugin / Dependabot)
- [ ] SEC.2 — Secrets rotation mechanism for API keys (local.properties → Vault in production)
- [ ] SEC.3 — Certificate pinning for Ktor client (production builds only)

### Testing Infrastructure (Ongoing)
- [ ] TEST.1 — Configure `commonTest` with Kotlin Test + Mockative stubs for all repository interfaces
- [ ] TEST.2 — Configure Compose UI test harness for both Android and Desktop targets
- [ ] TEST.3 — Code coverage reporting via Kover plugin, enforce 85%+ threshold

### CI/CD (Phase 1 End)
- [ ] CI.1 — GitHub Actions workflow: build + unit test on every PR
- [ ] CI.2 — GitHub Actions: assemble Android APK + Desktop JAR on `main` push
- [ ] CI.3 — Secrets management: GitHub Secrets → Gradle build environment injection

---

## 📌 EXECUTION STATUS LEGEND

| Symbol | Meaning |
|--------|---------|
| `[ ]` | Not Started |
| `[~]` | In Progress |
| `[x]` | Completed |
| `[!]` | Blocked/Issue |
| 🔴 | Phase/Section not started |
| 🟡 | Phase/Section in progress |
| 🟢 | Phase/Section complete |

---

## 📝 Session Notes

> _Use this section to record decisions, blockers, and architectural choices made during execution._

| Date | Note |
|------|------|
| 2026-02-20 | Project audit complete. Baseline: KMP skeleton only. All modules pending. Execution log created. Ready to begin Phase 0. |

---

*End of ZentaPOS Execution Log v1.0*
