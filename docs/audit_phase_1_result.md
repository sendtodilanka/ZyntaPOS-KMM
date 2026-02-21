# ZyntaPOS — Phase 1 Discovery Report
> **Doc ID:** ZENTA-PHASE1-DISCOVERY-v1.0
> **Auditor:** Senior KMP Architect
> **Date:** 2026-02-21
> **Scope:** Full project tree scan + complete docs index + cross-reference findings
> **Project Root:** `/Users/dilanka/Developer/StudioProjects/ZyntaPOS/`

---

## PROJECT TREE

```
ZyntaPOS/
├── .github/
│   └── workflows/
│       └── ci.yml
├── androidApp/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/com/zyntasolutions/zyntapos/
│       │   └── MainActivity.kt
│       └── res/
│           ├── drawable/ic_launcher_background.xml
│           ├── drawable-v24/ic_launcher_foreground.xml
│           ├── mipmap-anydpi-v26/{ic_launcher,ic_launcher_round}.xml
│           ├── mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/{ic_launcher,ic_launcher_round}.png
│           └── values/strings.xml
├── composeApp/
│   ├── build.gradle.kts
│   ├── src/
│   │   ├── androidMain/
│   │   │   ├── AndroidManifest.xml              ← bare library manifest (no <application>)
│   │   │   └── kotlin/com/...                   ← empty (no res/ dir — FIX-03 applied)
│   │   ├── commonMain/
│   │   │   ├── composeResources/drawable/compose-multiplatform.xml
│   │   │   └── kotlin/com/zyntasolutions/zyntapos/
│   │   │       └── App.kt
│   │   └── jvmMain/
│   │       └── kotlin/.../main.kt
│   ├── core/
│   │   ├── build.gradle.kts
│   │   └── src/commonMain/kotlin/com/zyntasolutions/zyntapos/ui/core/mvi/
│   │       └── BaseViewModel.kt                 ⚠️ DUPLICATE — see Finding F-01
│   ├── designsystem/
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── androidMain/kotlin/.../designsystem/
│   │       │   ├── theme/ZentaTheme.android.kt
│   │       │   └── util/WindowSizeClassHelper.android.kt
│   │       ├── commonMain/kotlin/.../designsystem/
│   │       │   ├── DesignSystemModule.kt
│   │       │   ├── components/  (15 files: ZentaBadge, ZentaButton, ZentaCartItemRow,
│   │       │   │                 ZentaBottomSheet, ZentaDialog, ZentaEmptyState,
│   │       │   │                 ZentaLoadingOverlay, ZentaNumericPad, ZentaProductCard,
│   │       │   │                 ZentaSearchBar, ZentaSnackbarHost, ZentaSyncIndicator,
│   │       │   │                 ZentaTable, ZentaTextField, ZentaTopAppBar)
│   │       │   ├── layouts/  (ZentaGrid, ZentaListDetailLayout, ZentaScaffold, ZentaSplitPane)
│   │       │   ├── theme/  (ZentaColors, ZentaShapes, ZentaTheme, ZentaTypography)
│   │       │   ├── tokens/  (ZentaElevation, ZentaSpacing)
│   │       │   └── util/WindowSizeClassHelper.kt
│   │       ├── commonMain/composeResources/font/.gitkeep
│   │       └── jvmMain/kotlin/.../designsystem/
│   │           ├── theme/ZentaTheme.desktop.kt
│   │           └── util/WindowSizeClassHelper.desktop.kt
│   ├── navigation/
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── androidMain/kotlin/.../navigation/.gitkeep
│   │       ├── commonMain/kotlin/.../navigation/
│   │       │   ├── AuthNavGraph.kt
│   │       │   ├── MainNavGraph.kt
│   │       │   ├── MainNavScreens.kt
│   │       │   ├── NavigationController.kt
│   │       │   ├── NavigationItems.kt
│   │       │   ├── NavigationModule.kt
│   │       │   ├── ZentaNavGraph.kt
│   │       │   └── ZentaRoute.kt
│   │       └── jvmMain/kotlin/.../navigation/.gitkeep
│   └── feature/
│       ├── auth/
│       │   ├── build.gradle.kts
│       │   └── src/commonMain/kotlin/.../feature/auth/
│       │       ├── AuthModule.kt, AuthViewModel.kt
│       │       ├── guard/ (RoleGuard.kt, SessionGuard.kt)
│       │       ├── mvi/ (AuthEffect.kt, AuthIntent.kt, AuthState.kt)
│       │       ├── screen/ (LoginScreen.kt, PinLockScreen.kt)
│       │       └── session/SessionManager.kt
│       ├── pos/
│       │   ├── build.gradle.kts
│       │   └── src/
│       │       ├── commonMain/kotlin/.../feature/pos/  (31 files):
│       │       │   BarcodeInputHandler, CartContent, CartItemList, CartPanel,
│       │       │   CartSummaryFooter, CashPaymentPanel, CategoryFilterRow,
│       │       │   CustomerSelectorDialog, HeldOrdersBottomSheet, HoldOrderDialog,
│       │       │   ItemDiscountDialog, OrderDiscountDialog, OrderHistoryScreen,
│       │       │   OrderNotesDialog, PaymentMethodGrid, PaymentScreen,
│       │       │   PaymentSuccessOverlay, PosEffect, PosIntent, PosModule,
│       │       │   PosSearchBar, PosState, PosViewModel,
│       │       │   PrintReceiptUseCase,  ⚠️ USE CASE IN FEATURE — see Finding F-02
│       │       │   ProductGridSection, ReceiptScreen, SplitPaymentPanel
│       │       └── jvmMain/kotlin/.../feature/pos/
│       │           └── KeyboardShortcutHandler.kt
│       ├── inventory/
│       │   ├── build.gradle.kts
│       │   └── src/commonMain/kotlin/.../feature/inventory/  (18 files):
│       │       BarcodeGeneratorDialog, BulkImportDialog, CategoryDetailScreen,
│       │       CategoryListScreen, InventoryEffect, InventoryIntent, InventoryModule,
│       │       InventoryState, InventoryViewModel, LowStockAlertBanner,
│       │       ProductDetailScreen, ProductFormValidator, ProductListScreen,
│       │       StockAdjustmentDialog, SupplierDetailScreen, SupplierListScreen,
│       │       TaxGroupScreen, UnitManagementScreen
│       ├── register/
│       │   ├── build.gradle.kts
│       │   └── src/commonMain/kotlin/.../feature/register/  (12 files):
│       │       CashInOutDialog, CashMovementHistory, CloseRegisterScreen,
│       │       OpenRegisterScreen, RegisterDashboardScreen, RegisterEffect,
│       │       RegisterGuard, RegisterIntent, RegisterModule, RegisterState,
│       │       RegisterViewModel, ZReportScreen
│       ├── reports/
│       │   ├── build.gradle.kts
│       │   └── src/commonMain/kotlin/.../feature/reports/  (10 files):
│       │       DateRangePickerBar, ReportExporter, ReportsEffect, ReportsHomeScreen,
│       │       ReportsIntent, ReportsModule, ReportsState, ReportsViewModel,
│       │       SalesReportScreen, StockReportScreen
│       ├── settings/
│       │   ├── build.gradle.kts
│       │   └── src/commonMain/kotlin/.../feature/settings/
│       │       ├── SettingsEffect.kt, SettingsIntent.kt, SettingsKeys.kt
│       │       ├── SettingsModule.kt, SettingsState.kt, SettingsViewModel.kt
│       │       └── screen/ (AboutScreen, AppearanceSettingsScreen, BackupSettingsScreen,
│       │                     GeneralSettingsScreen, PosSettingsScreen, PrinterSettingsScreen,
│       │                     SettingsHomeScreen, TaxSettingsScreen, UserManagementScreen)
│       ├── customers/  → CustomersModule.kt only                ← Phase 2 stub
│       ├── coupons/    → CouponsModule.kt only                  ← Phase 2 stub
│       ├── expenses/   → ExpensesModule.kt only                 ← Phase 2 stub
│       ├── multistore/ → MultistoreModule.kt only               ← Phase 2 stub
│       ├── admin/      → AdminModule.kt only                    ← Phase 3 stub
│       ├── staff/      → StaffModule.kt only                    ← Phase 3 stub
│       └── media/      → MediaModule.kt only                    ← Phase 3 stub
├── shared/
│   ├── core/
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── androidMain/kotlin/.../core/Platform.android.kt
│   │       ├── commonMain/kotlin/.../core/
│   │       │   ├── Platform.kt
│   │       │   ├── config/AppConfig.kt
│   │       │   ├── di/CoreModule.kt
│   │       │   ├── extensions/ (DoubleExtensions, LongExtensions, StringExtensions)
│   │       │   ├── logger/ZentaLogger.kt
│   │       │   ├── mvi/BaseViewModel.kt           ⚠️ DUPLICATE — see Finding F-01
│   │       │   ├── result/ (Result.kt, ZentaException.kt)
│   │       │   └── utils/ (CurrencyFormatter, DateTimeUtils, IdGenerator)
│   │       └── jvmMain/kotlin/.../core/Platform.jvm.kt
│   ├── domain/
│   │   ├── build.gradle.kts
│   │   └── src/commonMain/kotlin/.../domain/
│   │       ├── DomainModule.kt
│   │       ├── model/ (25 models: AuditEntry, CartItem, CashMovement, CashRegister,
│   │       │           Category, Customer, DiscountType, Order, OrderItem, OrderStatus,
│   │       │           OrderTotals, OrderType, PaymentMethod, PaymentSplit, Permission,
│   │       │           Product, ProductVariant, RegisterSession, Role, StockAdjustment,
│   │       │           Supplier, SyncOperation, SyncStatus, TaxGroup, UnitOfMeasure, User)
│   │       ├── repository/ (14 interfaces: AuditRepository, AuthRepository,
│   │       │                CategoryRepository, CustomerRepository, OrderRepository,
│   │       │                ProductRepository, RegisterRepository, SettingsRepository,
│   │       │                StockRepository, SupplierRepository, SyncRepository,
│   │       │                TaxGroupRepository, UnitGroupRepository, UserRepository)
│   │       ├── usecase/
│   │       │   ├── auth/   (CheckPermissionUseCase, LoginUseCase, LogoutUseCase, ValidatePinUseCase)
│   │       │   ├── inventory/ (AdjustStockUseCase, CreateProductUseCase, DeleteCategoryUseCase,
│   │       │   │              ManageUnitGroupUseCase, SaveCategoryUseCase, SaveSupplierUseCase,
│   │       │   │              SaveTaxGroupUseCase, SearchProductsUseCase, UpdateProductUseCase)
│   │       │   ├── pos/ (AddItemToCartUseCase, ApplyItemDiscountUseCase, ApplyOrderDiscountUseCase,
│   │       │   │         CalculateOrderTotalsUseCase, HoldOrderUseCase, ProcessPaymentUseCase,
│   │       │   │         RemoveItemFromCartUseCase, RetrieveHeldOrderUseCase,
│   │       │   │         UpdateCartItemQuantityUseCase, VoidOrderUseCase)
│   │       │   ├── register/ (CloseRegisterSessionUseCase, OpenRegisterSessionUseCase,
│   │       │   │              PrintZReportUseCase, RecordCashMovementUseCase)
│   │       │   ├── reports/ (GenerateSalesReportUseCase, GenerateStockReportUseCase,
│   │       │   │             PrintReportUseCase)
│   │       │   └── settings/ (PrintTestPageUseCase, SaveUserUseCase)
│   │       └── validation/ (PaymentValidator, StockValidator, TaxValidator)
│   ├── data/
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── androidMain/kotlin/.../data/
│   │       │   ├── di/AndroidDataModule.kt
│   │       │   ├── local/db/ (DatabaseDriverFactory, DatabaseKeyProvider)
│   │       │   └── sync/NetworkMonitor.kt, SyncWorker.kt
│   │       ├── commonMain/
│   │       │   ├── kotlin/.../data/
│   │       │   │   ├── di/DataModule.kt
│   │       │   │   ├── local/
│   │       │   │   │   ├── SyncEnqueuer.kt
│   │       │   │   │   ├── db/ (DatabaseDriverFactory, DatabaseFactory,
│   │       │   │   │   │        DatabaseKeyProvider, DatabaseMigrations)
│   │       │   │   │   ├── mapper/ (CategoryMapper, CustomerMapper, OrderMapper,
│   │       │   │   │   │            ProductMapper, RegisterMapper, StockMapper,
│   │       │   │   │   │            SupplierMapper, SyncOperationMapper, UserMapper)
│   │       │   │   │   └── security/ (InMemorySecurePreferences, PasswordHasher,
│   │       │   │   │                   PlaceholderPasswordHasher, SecurePreferences)
│   │       │   │   ├── remote/
│   │       │   │   │   ├── api/ (ApiClient, ApiService, KtorApiService)
│   │       │   │   │   └── dto/ (AuthDto, OrderDto, ProductDto, SyncDto)
│   │       │   │   ├── repository/ (10 impls: AuthRepositoryImpl, CategoryRepositoryImpl,
│   │       │   │   │                CustomerRepositoryImpl, OrderRepositoryImpl,
│   │       │   │   │                ProductRepositoryImpl, RegisterRepositoryImpl,
│   │       │   │   │                SettingsRepositoryImpl, StockRepositoryImpl,
│   │       │   │   │                SupplierRepositoryImpl, SyncRepositoryImpl)
│   │       │   │   └── sync/ (NetworkMonitor, SyncEngine)
│   │       │   └── sqldelight/com/zyntasolutions/zyntapos/db/
│   │       │       └── *.sq files: audit_log, categories, customers, orders, products,
│   │       │                        registers, settings, stock, suppliers, sync_queue, users
│   │       └── jvmMain/kotlin/.../data/
│   │           ├── di/DesktopDataModule.kt
│   │           ├── local/db/ (DatabaseDriverFactory, DatabaseKeyProvider)
│   │           └── sync/NetworkMonitor.kt
│   ├── hal/
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── androidMain/kotlin/.../hal/
│   │       │   ├── di/HalModule.android.kt
│   │       │   ├── printer/ (AndroidBluetoothPrinterPort, AndroidUsbPrinterPort)
│   │       │   └── scanner/ (AndroidCameraScanner, AndroidUsbScanner)
│   │       ├── commonMain/kotlin/.../hal/
│   │       │   ├── BarcodeScanner.kt              ⚠️ DUPLICATE — see Finding F-03
│   │       │   ├── di/HalModule.kt
│   │       │   ├── printer/ (EscPosReceiptBuilder, NullPrinterPort, PrinterConfig,
│   │       │   │             PrinterManager, PrinterPort, ReceiptBuilder)
│   │       │   └── scanner/ (BarcodeScanner.kt,   ⚠️ DUPLICATE — see Finding F-03
│   │       │                  ScanResult.kt)
│   │       └── jvmMain/kotlin/.../hal/
│   │           ├── di/HalModule.jvm.kt
│   │           ├── printer/ (DesktopSerialPrinterPort, DesktopTcpPrinterPort,
│   │           │             DesktopUsbPrinterPort)
│   │           └── scanner/ (DesktopHidScanner, DesktopSerialScanner)
│   └── security/
│       ├── build.gradle.kts
│       └── src/
│           ├── androidMain/kotlin/.../security/
│           │   ├── auth/ (PasswordHasher.android.kt, SecureRandomBytes.android.kt,
│           │   │          Sha256.android.kt)
│           │   ├── crypto/ (DatabaseKeyManager.android.kt, EncryptionManager.android.kt)
│           │   ├── keystore/ .gitkeep only                      ⚠️ EMPTY — see Finding F-07
│           │   └── prefs/SecurePreferences.android.kt
│           ├── commonMain/kotlin/.../security/
│           │   ├── SecurityAuditLogger.kt                       ⚠️ DUPLICATE — see Finding F-04
│           │   ├── audit/SecurityAuditLogger.kt                 ⚠️ DUPLICATE — see Finding F-04
│           │   ├── auth/ (JwtManager, PasswordHasher, PinManager,
│           │   │          SecureRandomBytes, Sha256)
│           │   ├── crypto/ (DatabaseKeyManager, EncryptionManager)
│           │   ├── di/SecurityModule.kt
│           │   ├── keystore/ .gitkeep only                      ⚠️ EMPTY — see Finding F-07
│           │   ├── prefs/ (SecurePreferences, TokenStorage)
│           │   ├── rbac/RbacEngine.kt
│           │   └── token/ .gitkeep only                         ⚠️ EMPTY — see Finding F-08
│           └── jvmMain/kotlin/.../security/
│               ├── auth/ (PasswordHasher.jvm.kt, SecureRandomBytes.jvm.kt, Sha256.jvm.kt)
│               ├── crypto/ (DatabaseKeyManager.jvm.kt, EncryptionManager.jvm.kt)
│               ├── keystore/ .gitkeep only                      ⚠️ EMPTY — see Finding F-07
│               └── prefs/SecurePreferences.jvm.kt
├── docs/
│   ├── phase_1_result.md  ← this document
│   ├── FIX-02_COMPLETION_SUMMARY.md
│   ├── FIX-02_INTEGRITY_VERIFICATION.md
│   ├── FIX-02_MODULE_NAME_CANONICALIZATION.md
│   ├── FIX-03_COMPLETION_SUMMARY.md
│   ├── FIX-03_INTEGRITY_VERIFICATION.md
│   ├── ai_workflows/execution_log.md
│   ├── api/README.md
│   ├── architecture/README.md
│   ├── compliance/README.md
│   └── plans/
│       ├── ER_diagram.md
│       ├── Master_plan.md
│       ├── PLAN_COMPAT_VERIFICATION_v1.0.md
│       ├── PLAN_CONSOLIDATED_FIX_v1.0.md
│       ├── PLAN_MISMATCH_FIX_v1.0.md
│       ├── PLAN_NAMESPACE_FIX_v1.0.md
│       ├── PLAN_PHASE1.md
│       ├── PLAN_STRUCTURE_CROSSCHECK_v1.0.md
│       ├── UI_UX_Main_Plan.md
│       └── ZyntaPOS_Junior_Developer_Guide.docx
├── gradle/
│   ├── libs.versions.toml
│   ├── gradle-daemon-jvm.properties
│   └── wrapper/gradle-wrapper.{jar,properties}
├── build.gradle.kts
├── settings.gradle.kts        ← 22 modules registered
├── gradle.properties
├── local.properties
├── local.properties.template
├── gradlew / gradlew.bat
└── README.md
```

**Module Registry Summary (settings.gradle.kts — 22 modules):**
| # | Module | Type |
|---|--------|------|
| 1 | `:androidApp` | Android Application |
| 2 | `:composeApp` | KMP Library (root) |
| 3 | `:shared:core` | KMP Library |
| 4 | `:shared:domain` | KMP Library |
| 5 | `:shared:data` | KMP Library |
| 6 | `:shared:hal` | KMP Library |
| 7 | `:shared:security` | KMP Library |
| 8 | `:composeApp:core` | KMP Library |
| 9 | `:composeApp:designsystem` | KMP Library |
| 10 | `:composeApp:navigation` | KMP Library |
| 11 | `:composeApp:feature:auth` | KMP Library |
| 12 | `:composeApp:feature:pos` | KMP Library |
| 13 | `:composeApp:feature:inventory` | KMP Library |
| 14 | `:composeApp:feature:register` | KMP Library |
| 15 | `:composeApp:feature:reports` | KMP Library |
| 16 | `:composeApp:feature:settings` | KMP Library |
| 17 | `:composeApp:feature:customers` | KMP Library (Phase 2 stub) |
| 18 | `:composeApp:feature:coupons` | KMP Library (Phase 2 stub) |
| 19 | `:composeApp:feature:expenses` | KMP Library (Phase 2 stub) |
| 20 | `:composeApp:feature:staff` | KMP Library (Phase 3 stub) |
| 21 | `:composeApp:feature:multistore` | KMP Library (Phase 2 stub) |
| 22 | `:composeApp:feature:admin` | KMP Library (Phase 3 stub) |
| +  | `:composeApp:feature:media` | KMP Library (Phase 3 stub) |

> ⚠️ **Finding F-09:** settings.gradle.kts registers `:composeApp:feature:media` but the table above counts 22 — the header comment says "22 modules". Including `:media` the actual count is **23**. See Finding F-09.

---

## DOCS INDEX

| Doc | Path | Purpose / Scope | Key Claims |
|-----|------|-----------------|------------|
| **Master_plan.md** | `docs/plans/Master_plan.md` | Enterprise master blueprint. Authoritative spec for all 3 phases (18 months). | Architecture: Clean Architecture + MVI + KMP (Android + Desktop JVM). 20 module IDs (M01–M20, with gaps). Package: `com.zyntasolutions.zyntapos`. M07 `:composeApp:navigation` missing from §4.1 registry table (gap). M08=auth, M09=pos, M10=inventory, M11=register, M12=reports, M13=customers, M18=settings. 7-layer security model. CRDT sync. SQLDelight + SQLCipher AES-256. |
| **PLAN_PHASE1.md** | `docs/plans/PLAN_PHASE1.md` | 6-month MVP execution plan (24 sprints × 1 week). | Kotlin 2.1+ (DRIFTED — actual 2.3.0). Compose Multiplatform 1.7+ (DRIFTED — actual 1.10.0). Koin 4.0+, Ktor 3.0.3, SQLDelight 2.0.2. minSdk 24, JVM 17+. MVI pattern with `BaseViewModel<Intent,State,SideEffect>`. Sprint 11 = `:composeApp:navigation`. Sprint 24 = Integration QA + release. |
| **ER_diagram.md** | `docs/plans/ER_diagram.md` | Entity-Relationship diagram for all 10 database domains. 63 entities, 80 relationships across all phases. | DB: SQLDelight (SQLite local) + PostgreSQL (cloud). 10 domains: IAM, Store, Product/Inventory, Sales, CRM, Cash Register, Procurement, Staff, Coupons, System/Sync. Phase 1 entities: IAM (6), Store (4), Product (12), Sales (8), Register (7), Procurement (4), System (5) = 46 entities. CRDT metadata columns on sync-enabled tables. |
| **UI_UX_Main_Plan.md** | `docs/plans/UI_UX_Main_Plan.md` | Enterprise UI/UX blueprint. All screens, interactions, responsive layouts. | Platforms: Desktop JVM (Win/Mac/Linux), Android Tablet, Android Phone. 3 breakpoints via `WindowSizeClass`. Material 3. 14 named screens (Auth, Dashboard, POS, Payment, Inventory, Register, CRM, Reports, Coupons, Multi-Store, Staff, Expenses, Settings, Admin). Min touch target 48dp. POS interaction ≤2 taps. |
| **PLAN_STRUCTURE_CROSSCHECK_v1.0.md** | `docs/plans/PLAN_STRUCTURE_CROSSCHECK_v1.0.md` | Latest audit doc verifying filesystem vs docs alignment. Status as of 2026-02-20. | All 22 modules in settings.gradle.kts ✅. Package namespace resolved to `com.zyntasolutions.zyntapos` ✅. Sprint 1–3 source files present ✅. Phase 1 Sprint 4–24 = NOT STARTED (expected). Dependency version drift acknowledged (intentional). |
| **PLAN_CONSOLIDATED_FIX_v1.0.md** | `docs/plans/PLAN_CONSOLIDATED_FIX_v1.0.md` | Supersedes PLAN_MISMATCH_FIX_v1.0. Master list of 18 issues (5 critical + 13 mismatches). | PLAN_MISMATCH_FIX_v1.0 missed 9 issues. Gap MM-03 (ci.yml missing) had no fix task — ci.yml now EXISTS on disk (resolved outside this doc). Declares FIX-01 through FIX-14. |
| **PLAN_MISMATCH_FIX_v1.0.md** | `docs/plans/PLAN_MISMATCH_FIX_v1.0.md` | Original mismatch audit (superseded). | 5 critical mismatches including feature modules non-existent, package `com.zentapos` vs `com.zynta.pos`. **SUPERSEDED** by PLAN_CONSOLIDATED_FIX_v1.0.md — do not execute. |
| **PLAN_NAMESPACE_FIX_v1.0.md** | `docs/plans/PLAN_NAMESPACE_FIX_v1.0.md` | Canonical namespace migration plan. OLD: `com.zynta.pos` → NEW: `com.zyntasolutions.zyntapos`. | 22 build.gradle.kts files + all source folder paths affected. Confirms canonical package = `com.zyntasolutions.zyntapos`. All 22 groups completed per PLAN_STRUCTURE_CROSSCHECK_v1.0.md. |
| **PLAN_COMPAT_VERIFICATION_v1.0.md** | `docs/plans/PLAN_COMPAT_VERIFICATION_v1.0.md` | Kotlin 2.3.0 compatibility matrix. Verifies Sprint 4–24 API patterns against actual pinned deps. | Kotlin 2.3.0 (plan said 2.1.0). Compose MP 1.10.0 (plan said 1.7.3). `compose-adaptive 1.1.0-alpha04` ⚠️ ALPHA. `androidx-security-crypto 1.1.0-alpha06` ⚠️ ALPHA. Sprint 4 = CLEAR (no blockers). `@ExperimentalUuidApi` on `IdGenerator.kt` — correct, do not remove. |
| **FIX-02_MODULE_NAME_CANONICALIZATION.md** | `docs/FIX-02_MODULE_NAME_CANONICALIZATION.md` | Fix plan for `:crm` → `:customers` rename in docs. | 4 locations in Master_plan.md updated. settings.gradle.kts was already correct. |
| **FIX-02_COMPLETION_SUMMARY.md** | `docs/FIX-02_COMPLETION_SUMMARY.md` | Verification that FIX-02 was applied. | Master_plan.md lines 139, 216, 249, 895 updated. Status: ✅ COMPLETE. |
| **FIX-02_INTEGRITY_VERIFICATION.md** | `docs/FIX-02_INTEGRITY_VERIFICATION.md` | Post-fix integrity check for FIX-02. | Confirms no remaining `:crm` references. |
| **FIX-03_COMPLETION_SUMMARY.md** | `docs/FIX-03_COMPLETION_SUMMARY.md` | Verification that composeApp/src/androidMain/res/ was deleted. | Deleted 15 files / 9 directories. `:composeApp` AndroidManifest.xml confirmed as bare library manifest. `:androidApp` res/ intact. Status: ✅ COMPLETE. |
| **FIX-03_INTEGRITY_VERIFICATION.md** | `docs/FIX-03_INTEGRITY_VERIFICATION.md` | Post-fix integrity check for FIX-03. | Confirms no res/ in composeApp/src/androidMain/. |
| **execution_log.md** | `docs/ai_workflows/execution_log.md` | AI agent execution log tracking Phase 0 completion and Phase 1 pending. | Canonical namespace = `com.zyntasolutions.zyntapos` (corrected from original `com/zentapos/`). Phase 0 complete. Phase 1 Sprint 4–24 pending. `composeHotReload = "1.0.0"` undocumented addition retained for desktop DX. |
| **docs/api/README.md** | `docs/api/README.md` | Placeholder — KDoc/REST contract directory. | Empty — to be populated during execution. |
| **docs/architecture/README.md** | `docs/architecture/README.md` | Placeholder — ADRs, module dependency maps. | Empty — to be populated during execution. |
| **docs/compliance/README.md** | `docs/compliance/README.md` | Placeholder — Compliance documentation. | Empty. |
| **ZyntaPOS_Junior_Developer_Guide.docx** | `docs/plans/ZyntaPOS_Junior_Developer_Guide.docx` | Binary Word document — not machine-readable in this audit. | NEEDS CLARIFICATION: Content unknown. Could duplicate/conflict with other docs. |

---

## CRITICAL FINDINGS

### F-01 — DUAL `BaseViewModel` — Architectural Ambiguity
**Severity:** 🔴 HIGH — Clean Architecture Violation Risk

| | Doc Says | Code Shows |
|--|----------|------------|
| Master_plan.md §4.1 | M07=`:composeApp:core` provides `BaseViewModel` for feature modules | Two `BaseViewModel.kt` files exist |
| PLAN_PHASE1.md §8 | Single `BaseViewModel<Intent,State,SideEffect>` in `:composeApp:core` | `:shared:core` also has one |

**Files in conflict:**
- `shared/core/src/commonMain/kotlin/com/zyntasolutions/zyntapos/core/mvi/BaseViewModel.kt`
- `composeApp/core/src/commonMain/kotlin/com/zyntasolutions/zyntapos/ui/core/mvi/BaseViewModel.kt`

**Recommendation:** Determine the canonical home. Clean Architecture places ViewModel infrastructure in the presentation layer (`:composeApp:core`), not in `:shared:core`. The `:shared:core` copy should either be deleted or — if it serves a different role — renamed (e.g., `BaseUseCase` or `MviStore`). Whichever is authoritative, remove the other and update all imports.

---

### F-02 — `PrintReceiptUseCase` in Feature Module — Clean Architecture Violation
**Severity:** 🟠 MEDIUM

| | Doc Says | Code Shows |
|--|----------|------------|
| Master_plan.md §3 | Use cases live in `:shared:domain` | `PrintReceiptUseCase.kt` is in `:composeApp:feature:pos` |
| PLAN_PHASE1.md §4 | Domain layer = shared, no UI dependencies | Feature module imports HAL directly |

**File:** `composeApp/feature/pos/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/pos/PrintReceiptUseCase.kt`

**Recommendation:** Move `PrintReceiptUseCase` to `shared/domain/src/commonMain/kotlin/.../domain/usecase/pos/PrintReceiptUseCase.kt`. If it depends on `shared/hal` printer types, that dependency is valid (domain → hal is permitted per Master_plan §4.2). If it contains UI/Compose imports, those must be extracted first.

---

### F-03 — DUPLICATE `BarcodeScanner.kt` in `:shared:hal`
**Severity:** 🟠 MEDIUM — Will cause Kotlin compilation conflict

| | |
|--|--|
| File 1 | `shared/hal/src/commonMain/kotlin/com/zyntasolutions/zyntapos/hal/BarcodeScanner.kt` |
| File 2 | `shared/hal/src/commonMain/kotlin/com/zyntasolutions/zyntapos/hal/scanner/BarcodeScanner.kt` |

Both are in `commonMain`. Kotlin will attempt to compile both under the same module. If they declare the same class/interface name in different packages, calls are ambiguous. If they're in the same package, it's a direct conflict.

**Recommendation:** Keep only `hal/scanner/BarcodeScanner.kt` (correct package structure, consistent with `hal/printer/` layout). Delete the root-level `hal/BarcodeScanner.kt`. Update any import references.

---

### F-04 — DUPLICATE `SecurityAuditLogger.kt` in `:shared:security`
**Severity:** 🟠 MEDIUM

| | |
|--|--|
| File 1 | `shared/security/src/commonMain/kotlin/com/zyntasolutions/zyntapos/security/SecurityAuditLogger.kt` |
| File 2 | `shared/security/src/commonMain/kotlin/com/zyntasolutions/zyntapos/security/audit/SecurityAuditLogger.kt` |

**Recommendation:** Keep only `security/audit/SecurityAuditLogger.kt` (subdirectory aligns with the rest of the module's structure). Delete the root-level copy. Update all import sites.

---

### F-05 — Missing Repository Implementations: `TaxGroupRepository` and `UnitGroupRepository`
**Severity:** 🟠 MEDIUM — Will cause Koin DI runtime failures

| | Doc Says | Code Shows |
|--|----------|------------|
| Master_plan.md §7 | All domain repository interfaces have data-layer implementations | No `TaxGroupRepositoryImpl.kt` or `UnitGroupRepositoryImpl.kt` found in `:shared:data` |
| ER_diagram.md | `tax_groups` and `unit_of_measure` tables exist as Phase 1 entities | SQLDelight `.sq` files confirmed present for `categories`, `products` etc., but no dedicated tax/unit `.sq` files found |

**Domain interfaces present:**
- `shared/domain/.../repository/TaxGroupRepository.kt` ✅
- `shared/domain/.../repository/UnitGroupRepository.kt` ✅

**Data implementations found:** AuthRepositoryImpl, CategoryRepositoryImpl, CustomerRepositoryImpl, OrderRepositoryImpl, ProductRepositoryImpl, RegisterRepositoryImpl, SettingsRepositoryImpl, StockRepositoryImpl, SupplierRepositoryImpl, SyncRepositoryImpl — **TaxGroup and UnitGroup missing**.

**Recommendation:** Create `TaxGroupRepositoryImpl.kt` and `UnitGroupRepositoryImpl.kt` in `shared/data/src/commonMain/kotlin/.../data/repository/`. Also verify SQLDelight queries for tax_groups and unit_of_measure tables exist (possibly folded into `categories.sq` or `products.sq` — needs clarification).

---

### F-06 — Kotlin / Compose Version Drift from Plan
**Severity:** 🟡 LOW — Documented, but plan references are stale

| Dependency | PLAN_PHASE1.md Claims | Actual (libs.versions.toml / .kotlin metadata) |
|------------|----------------------|------------------------------------------------|
| Kotlin | `2.1+` | **2.3.0** |
| Compose MP | `1.7+` | **1.10.0** |
| Koin | `4.0+` | **4.0.4** |
| compose-adaptive | not mentioned | **1.1.0-alpha04** ⚠️ ALPHA |
| androidx-security-crypto | not mentioned | **1.1.0-alpha06** ⚠️ ALPHA |

PLAN_COMPAT_VERIFICATION_v1.0.md acknowledges the drift and declares Sprint 4 safe. The two alpha dependencies are the active risk.

**Recommendation:** For `compose-adaptive 1.1.0-alpha04` — acceptable for desktop nav rail layouts but must not be used in production-critical flows without a fallback. For `androidx-security-crypto 1.1.0-alpha06` — if used for `EncryptedSharedPreferences` in `SecurePreferences.android.kt`, verify Android Keystore fallback exists for API 24–27 devices (AES-256-GCM support varies). Update PLAN_PHASE1.md §6 to reflect actual pinned versions.

---

### F-07 — Empty `keystore/` Directories in `:shared:security`
**Severity:** 🟡 LOW — Planned future work, but risk of silent skip

The following `keystore/` directories contain only `.gitkeep`:
- `shared/security/src/commonMain/kotlin/.../security/keystore/`
- `shared/security/src/androidMain/kotlin/.../security/keystore/`
- `shared/security/src/jvmMain/kotlin/.../security/keystore/`

Master_plan.md §5 (7-layer security model) requires platform Keystore integration (Android Keystore, JCE/PKCS12 on JVM). `DatabaseKeyManager.android.kt` and `DatabaseKeyManager.jvm.kt` exist in `crypto/`, so key management may already be implemented there. NEEDS CLARIFICATION: Is the `keystore/` directory reserved for a separate Keystore abstraction, or is it a residual empty scaffold?

**Recommendation:** Either populate `keystore/` with the expected `KeystoreProvider.kt` (expect/actual) or delete the empty directories and document in execution_log.md that key management is consolidated in `crypto/DatabaseKeyManager`.

---

### F-08 — Empty `token/` Directory in `:shared:security`
**Severity:** 🟡 LOW — JWT token storage incomplete

`shared/security/src/commonMain/kotlin/.../security/token/` contains only `.gitkeep`.

`JwtManager.kt` and `TokenStorage.kt` exist in `auth/` and `prefs/` respectively. NEEDS CLARIFICATION: Is `token/` meant to hold a `TokenRepository` or `RefreshToken` model?

**Recommendation:** Either create the planned `token/` contents or remove the directory and update execution_log.md.

---

### F-09 — Module Count Discrepancy: 22 vs 23
**Severity:** 🟡 LOW — Documentation inconsistency

| | Says |
|--|------|
| settings.gradle.kts header comment | "22 modules" |
| Actual `include()` calls counted | **23** (`:media` is included but not in the count) |
| Master_plan.md §4.1 | Lists M01–M20 with gaps (no M07 visible in module registry table) |

**Recommendation:** Update the settings.gradle.kts comment from "22" to "23". Also confirm M07 in Master_plan.md §4.1 — it should be `:composeApp:navigation` (which exists in settings.gradle.kts but has no M-number in the registry table).

---

### F-10 — `PLAN_MISMATCH_FIX_v1.0.md` Not Marked Superseded
**Severity:** 🟡 LOW — Future confusion risk

PLAN_CONSOLIDATED_FIX_v1.0.md explicitly states it supersedes PLAN_MISMATCH_FIX_v1.0.md, but the latter file's header does not reflect this.

**Recommendation:** Add `**STATUS: SUPERSEDED by PLAN_CONSOLIDATED_FIX_v1.0.md — DO NOT EXECUTE**` to the top of `PLAN_MISMATCH_FIX_v1.0.md`.

---

### F-11 — `ZyntaPOS_Junior_Developer_Guide.docx` — Unaudited Binary
**Severity:** NEEDS CLARIFICATION

The file `docs/plans/ZyntaPOS_Junior_Developer_Guide.docx` is a binary Word document. Its content is unknown and could contain outdated module names, package paths, or architectural guidance that contradicts the resolved canonical state.

**Recommendation:** Convert to Markdown or extract key claims. Verify it does not reference `com.zynta.pos`, `:crm`, or any pre-fix module names.

---

## VERIFICATION CHECKLIST (Phase 0 — Baseline State)

| Check | Status | Notes |
|-------|--------|-------|
| 22+ modules declared in settings.gradle.kts | ✅ 23 modules | Count mismatch in header comment (F-09) |
| All module directories exist on disk | ✅ | All 23 physical dirs confirmed |
| Package = `com.zyntasolutions.zyntapos` | ✅ | Consistent across all source files scanned |
| `:composeApp` has no res/ directory | ✅ | FIX-03 applied |
| `:androidApp` res/ intact | ✅ | All launcher icons present |
| Master_plan.md uses `:customers` not `:crm` | ✅ | FIX-02 applied |
| Phase 1 Sprint 4–24 source | 🔴 NOT STARTED | Expected — Phase 0 complete |
| Dual BaseViewModel resolved | ❌ | F-01 — needs decision |
| PrintReceiptUseCase in correct module | ❌ | F-02 — wrong layer |
| BarcodeScanner.kt deduplicated | ❌ | F-03 — compilation risk |
| SecurityAuditLogger.kt deduplicated | ❌ | F-04 — compilation risk |
| TaxGroup/UnitGroup repo impls exist | ❌ | F-05 — DI will fail |
| Alpha dependencies evaluated | ⚠️ | F-06 — documented in compat doc |
| keystore/ and token/ directories resolved | ⚠️ | F-07, F-08 |

---

## PHASE 1 READINESS VERDICT

**Phase 0 (scaffold) is complete.** The project has a clean, consistent foundation:
- All 23 modules physically exist with correct package structure
- Namespace canonicalization complete (`com.zyntasolutions.zyntapos`)
- Resource merge conflict resolved
- Design system, navigation, and all Phase 1 feature stubs scaffolded

**Three issues must be resolved before Sprint 4 implementation begins:**

1. **F-01 (Dual BaseViewModel)** — Decide canonical home, delete duplicate. Every Sprint 4+ ViewModel inherits from this class; ambiguity now = compiler errors later.
2. **F-03 (Duplicate BarcodeScanner.kt)** — Will cause a Kotlin compile error as soon as HAL is imported.
3. **F-04 (Duplicate SecurityAuditLogger.kt)** — Same risk as F-03.

**F-02 and F-05 should be addressed in Sprint 5 (`:shared:domain` use cases) and Sprint 5–6 (`:shared:data` repositories) respectively.**
