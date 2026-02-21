# ZyntaPOS — Audit v2 | Phase 1: Discovery
> **Doc ID:** ZENTA-AUDIT-V2-PHASE1-DISCOVERY  
> **Auditor:** Senior KMP Architect  
> **Date:** 2026-02-21  
> **Project Root:** `/Users/dilanka/Developer/StudioProjects/ZyntaPOS/`  
> **Scope:** Full re-scan of project tree and docs index since v1 Phases 1–4 completed.  
> **Prerequisite:** Audit v1 (Phases 1–4) — results in `docs/audit_phase_{1..4}_result.md`

---

## SECTION 1 — PROJECT TREE

### 1A. Module Registry

`settings.gradle.kts` declares **23 modules** (header comment now correct):

| # | Gradle Path | Physical Directory | Role |
|---|---|---|---|
| 1 | `:androidApp` | `androidApp/` | Android application shell |
| 2 | `:composeApp` | `composeApp/src/` | KMP library root (App.kt, main.kt) |
| 3 | `:shared:core` | `shared/core/` | Cross-cutting utilities |
| 4 | `:shared:domain` | `shared/domain/` | Business rules, models, use cases |
| 5 | `:shared:data` | `shared/data/` | Repository impls, SQLDelight, Ktor |
| 6 | `:shared:hal` | `shared/hal/` | Hardware abstraction layer |
| 7 | `:shared:security` | `shared/security/` | Crypto, JWT, RBAC, audit |
| 8 | `:composeApp:core` | `composeApp/core/` | Canonical BaseViewModel |
| 9 | `:composeApp:designsystem` | `composeApp/designsystem/` | Material 3 theme + components |
| 10 | `:composeApp:navigation` | `composeApp/navigation/` | Type-safe NavHost |
| 11 | `:composeApp:feature:auth` | `composeApp/feature/auth/` | Login, PIN, session |
| 12 | `:composeApp:feature:pos` | `composeApp/feature/pos/` | POS checkout |
| 13 | `:composeApp:feature:inventory` | `composeApp/feature/inventory/` | Product/stock CRUD |
| 14 | `:composeApp:feature:register` | `composeApp/feature/register/` | Cash register lifecycle |
| 15 | `:composeApp:feature:reports` | `composeApp/feature/reports/` | Sales/stock reports |
| 16 | `:composeApp:feature:settings` | `composeApp/feature/settings/` | Configuration screens |
| 17 | `:composeApp:feature:customers` | `composeApp/feature/customers/` | CRM stub |
| 18 | `:composeApp:feature:coupons` | `composeApp/feature/coupons/` | Promotions stub |
| 19 | `:composeApp:feature:expenses` | `composeApp/feature/expenses/` | Accounting stub |
| 20 | `:composeApp:feature:staff` | `composeApp/feature/staff/` | HR stub |
| 21 | `:composeApp:feature:multistore` | `composeApp/feature/multistore/` | Multi-store stub |
| 22 | `:composeApp:feature:admin` | `composeApp/feature/admin/` | Admin stub |
| 23 | `:composeApp:feature:media` | `composeApp/feature/media/` | Media stub |

All 23 physical directories confirmed on disk. ✅

---

### 1B. Notable Changes Since v1 Audit

#### Deleted (Fixes Applied)
| File | Reason |
|---|---|
| `shared/core/src/commonMain/.../core/mvi/BaseViewModel.kt` | F-01 resolved — zombie ViewModel deleted; only `.gitkeep` remains in `core/mvi/` |
| `shared/hal/src/commonMain/.../hal/BarcodeScanner.kt` (root) | F-03 resolved — root-level duplicate removed |
| `shared/security/src/commonMain/.../security/SecurityAuditLogger.kt` (root) | F-04 resolved — root-level duplicate removed; `audit/` copy is canonical |

#### Added Since v1
| File | Purpose |
|---|---|
| `docs/adr/ADR-001-ViewModelBaseClass.md` | Architecture decision — all ViewModels extend `:composeApp:core` BaseViewModel |
| `docs/adr/ADR-002-DomainModelNaming.md` | Architecture decision — no `*Entity` suffix in domain layer |
| `docs/plans/PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md` | Hotfix rename plan: design system `Zenta*` → `Zynta*` |
| `docs/zentapos-audit-final-synthesis.md` | **Prompt template only** — not a completed synthesis report |
| `CONTRIBUTING.md` | Developer guide with naming conventions and ADR links |
| `composeApp/feature/pos/README.md` | Documents `PosSearchBar` as intentional wrapper over `ZyntaSearchBar` |
| `shared/domain/src/.../domain/printer/ReceiptPrinterPort.kt` | Ports & Adapters output port interface |
| `shared/domain/src/.../domain/formatter/ReceiptFormatter.kt` | Pure-domain receipt text formatter (screen preview) |
| `shared/domain/src/.../domain/usecase/pos/PrintReceiptUseCase.kt` | **Canonical** domain use case (delegates to `ReceiptPrinterPort`) |
| `shared/domain/src/.../domain/validation/ProductValidator.kt` | Domain product validation (Phase 3 DUP-07 resolved) |
| `shared/domain/src/.../domain/validation/ProductValidationParams.kt` | Validation parameter object |
| `composeApp/feature/pos/src/.../feature/pos/printer/PrinterManagerReceiptAdapter.kt` | Adapter implementing `ReceiptPrinterPort` using HAL + Security |
| `shared/data/src/.../data/repository/TaxGroupRepositoryImpl.kt` | Impl present — all methods are `TODO` stubs |
| `shared/data/src/.../data/repository/UnitGroupRepositoryImpl.kt` | Impl present — all methods are `TODO` stubs |
| `shared/data/src/.../data/repository/AuditRepositoryImpl.kt` | Impl present (newly added) |
| `shared/data/src/.../data/repository/UserRepositoryImpl.kt` | Impl present (newly added) |
| `shared/data/src/.../sqldelight/.../db/tax_groups.sq` | SQLDelight schema — complete with queries |
| `shared/data/src/.../sqldelight/.../db/units_of_measure.sq` | SQLDelight schema — complete with queries |
| `shared/data/src/.../data/local/db/SecurePreferencesKeyMigration.kt` | One-time key migration utility (DC-03 fix) |
| `shared/security/src/.../security/prefs/SecurePreferencesKeys.kt` | Canonical key registry (DC-03 fix) |
| `shared/domain/src/.../domain/usecase/fakes/FakeAuthRepositories.kt` | Reorganized test fakes (DUP-08 resolved) |
| `shared/domain/src/.../domain/usecase/fakes/FakeInventoryRepositories.kt` | Reorganized test fakes |
| `shared/domain/src/.../domain/usecase/fakes/FakePosRepositories.kt` | Reorganized test fakes |
| `shared/domain/src/.../domain/usecase/fakes/FakeSharedRepositories.kt` | Reorganized test fakes |

#### Design System Rename — Executed
All 15 component files, 4 layout files, 4 theme files, and 2 token files in `:composeApp:designsystem` now carry the `Zynta*` prefix (e.g., `ZyntaButton.kt`, `ZyntaTheme.kt`). The rename plan `PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md` has been executed in code, but its Definition of Done checklist remains unchecked.

---

### 1C. Full Source File Map

```
androidApp/
  build.gradle.kts
  src/main/
    AndroidManifest.xml
    kotlin/com/zyntasolutions/zyntapos/MainActivity.kt
    res/                                        [all launcher icons intact]

composeApp/
  build.gradle.kts
  src/
    androidMain/AndroidManifest.xml             [library manifest — no <application>]
    commonMain/composeResources/drawable/compose-multiplatform.xml  ⚠️ boilerplate
    commonMain/kotlin/.../App.kt
    jvmMain/kotlin/.../main.kt
  core/
    build.gradle.kts
    src/commonMain/kotlin/.../ui/core/mvi/BaseViewModel.kt  ✅ canonical
  designsystem/
    build.gradle.kts
    src/
      androidMain/.../theme/ZyntaTheme.android.kt
      androidMain/.../util/WindowSizeClassHelper.android.kt
      commonMain/kotlin/.../
        components/: ZyntaBadge, ZyntaBottomSheet, ZyntaButton, ZyntaCartItemRow,
                     ZyntaDialog, ZyntaEmptyState, ZyntaLoadingOverlay, ZyntaNumericPad,
                     ZyntaProductCard, ZyntaSearchBar, ZyntaSnackbarHost, ZyntaSyncIndicator,
                     ZyntaTable, ZyntaTextField, ZyntaTopAppBar  [15 components]
        layouts/:   ZyntaGrid, ZyntaListDetailLayout, ZyntaScaffold, ZyntaSplitPane
        theme/:     ZyntaColors, ZyntaShapes, ZyntaTheme, ZyntaTypography
        tokens/:    ZyntaElevation, ZyntaSpacing
        util/:      WindowSizeClassHelper.kt
      commonTest/.../DesignSystemComponentTests.kt
      jvmMain/.../theme/ZyntaTheme.desktop.kt
  navigation/
    src/commonMain/kotlin/.../navigation/
      AuthNavGraph.kt, MainNavGraph.kt, MainNavScreens.kt, NavigationController.kt,
      NavigationItems.kt, NavigationModule.kt, ZyntaNavGraph.kt, ZyntaRoute.kt
  feature/
    auth/     [AuthViewModel, LoginScreen, PinLockScreen, SessionManager, guards, MVI, tests]
    pos/
      README.md                               ✅ documents PosSearchBar pattern
      build.gradle.kts
      src/commonMain/kotlin/.../feature/pos/
        BarcodeInputHandler.kt, CartContent.kt, CartItemList.kt, CartPanel.kt,
        CartSummaryFooter.kt, CashPaymentPanel.kt, CategoryFilterRow.kt,
        CustomerSelectorDialog.kt, HeldOrdersBottomSheet.kt, HoldOrderDialog.kt,
        ItemDiscountDialog.kt, OrderDiscountDialog.kt, OrderHistoryScreen.kt,
        OrderNotesDialog.kt, PaymentMethodGrid.kt, PaymentScreen.kt,
        PaymentSuccessOverlay.kt, PosEffect.kt, PosIntent.kt, PosModule.kt,
        PosSearchBar.kt, PosState.kt, PosViewModel.kt,
        PrintReceiptUseCase.kt                ⚠️ ORPHAN — duplicate of domain version
        ProductGridSection.kt, ReceiptScreen.kt, SplitPaymentPanel.kt
        printer/PrinterManagerReceiptAdapter.kt  ✅ correct Ports & Adapters adapter
      src/jvmMain/.../pos/KeyboardShortcutHandler.kt
      src/commonTest/.../pos/PosViewModelTest.kt
    inventory/ [18 files: screens, VM, MVI, dialogs]
    register/  [12 files: screens, VM, MVI]
    reports/
      src/commonMain/     [10 files + ReportExporter interface]
      src/androidMain/    [AndroidReportExporter, AndroidReportsModule]
      src/jvmMain/        [JvmReportExporter]
    settings/  [9 screens, VM, MVI, SettingsKeys.kt]
    customers/ [stub — CustomersModule.kt only]
    coupons/   [stub — CouponsModule.kt only]
    expenses/  [stub — ExpensesModule.kt only]
    staff/     [stub — StaffModule.kt only]
    multistore/ [stub — MultistoreModule.kt only]
    admin/     [stub — AdminModule.kt only]
    media/     [stub — MediaModule.kt only]

shared/
  core/
    src/commonMain/kotlin/.../core/
      config/AppConfig.kt
      di/CoreModule.kt
      extensions/: DoubleExtensions, LongExtensions, StringExtensions
      logger/ZyntaLogger.kt
      mvi/.gitkeep                            ✅ BaseViewModel.kt DELETED (F-01 resolved)
      result/: Result.kt, ZyntaException.kt
      utils/: CurrencyFormatter, DateTimeUtils, IdGenerator
    src/{androidMain,jvmMain}/Platform.*.kt
    src/commonTest/: ResultTest, ZyntaExceptionTest, CurrencyFormatterTest, DateTimeUtilsTest

  domain/
    src/commonMain/kotlin/.../domain/
      DomainModule.kt
      formatter/ReceiptFormatter.kt           ✅ NEW — pure-domain receipt text formatter
      model/ [26 models: AuditEntry, CartItem, CashMovement, CashRegister, Category,
              Customer, DiscountType, Order, OrderItem, OrderStatus, OrderTotals, OrderType,
              PaymentMethod, PaymentSplit, Permission, Product, ProductVariant,
              RegisterSession, Role, StockAdjustment, Supplier, SyncOperation,
              SyncStatus, TaxGroup, UnitOfMeasure, User]
      printer/ReceiptPrinterPort.kt           ✅ NEW — output port interface
      repository/ [14 interfaces: AuditRepository, AuthRepository, CategoryRepository,
                   CustomerRepository, OrderRepository, ProductRepository,
                   RegisterRepository, SettingsRepository, StockRepository,
                   SupplierRepository, SyncRepository, TaxGroupRepository,
                   UnitGroupRepository, UserRepository]
      usecase/
        auth/: CheckPermissionUseCase, LoginUseCase, LogoutUseCase, ValidatePinUseCase
        inventory/: AdjustStockUseCase, CreateProductUseCase, DeleteCategoryUseCase,
                    ManageUnitGroupUseCase, SaveCategoryUseCase, SaveSupplierUseCase,
                    SaveTaxGroupUseCase, SearchProductsUseCase, UpdateProductUseCase
        pos/: AddItemToCartUseCase, ApplyItemDiscountUseCase, ApplyOrderDiscountUseCase,
              CalculateOrderTotalsUseCase, HoldOrderUseCase,
              PrintReceiptUseCase (canonical),  ✅
              ProcessPaymentUseCase, RemoveItemFromCartUseCase, RetrieveHeldOrderUseCase,
              UpdateCartItemQuantityUseCase, VoidOrderUseCase
        register/: CloseRegisterSessionUseCase, OpenRegisterSessionUseCase,
                   PrintZReportUseCase, RecordCashMovementUseCase
        reports/: GenerateSalesReportUseCase, GenerateStockReportUseCase, PrintReportUseCase
        settings/: PrintTestPageUseCase, SaveUserUseCase
      validation/: PaymentValidator, ProductValidationParams, ProductValidator, ✅
                   StockValidator, TaxValidator
    src/commonTest/
      fakes/: FakeAuthRepositories, FakeInventoryRepositories, ✅ reorganized
              FakePosRepositories, FakeSharedRepositories
      usecase/: 15 test files across all use case categories

  data/
    src/commonMain/kotlin/.../data/
      di/DataModule.kt                        [all 14 repos registered]
      local/
        SyncEnqueuer.kt
        db/: DatabaseDriverFactory (expect), DatabaseFactory, DatabaseKeyProvider (expect),
             DatabaseMigrations, SecurePreferencesKeyMigration.kt ✅ NEW
        mapper/ [9 mappers: Category, Customer, Order, Product, Register,
                 Stock, Supplier, SyncOperation, User]
        security/: InMemorySecurePreferences.kt, SecurePreferences.kt
      remote/
        api/: ApiClient.kt, ApiService.kt, KtorApiService.kt
        dto/: AuthDto, OrderDto, ProductDto, SyncDto
      repository/ [14 impls]:
        AuditRepositoryImpl ✅, AuthRepositoryImpl, CategoryRepositoryImpl,
        CustomerRepositoryImpl, OrderRepositoryImpl, ProductRepositoryImpl,
        RegisterRepositoryImpl, SettingsRepositoryImpl, StockRepositoryImpl,
        SupplierRepositoryImpl, SyncRepositoryImpl,
        TaxGroupRepositoryImpl ⚠️ (all methods TODO),
        UnitGroupRepositoryImpl ⚠️ (all methods TODO),
        UserRepositoryImpl ✅
      sync/: NetworkMonitor (expect), SyncEngine
    src/commonMain/sqldelight/.../db/ [13 .sq files]:
      audit_log, categories, customers, orders, products, registers, settings,
      stock, suppliers, sync_queue, tax_groups ✅ NEW, units_of_measure ✅ NEW, users
    src/androidMain/: AndroidDataModule, DatabaseDriverFactory, DatabaseKeyProvider,
                       NetworkMonitor, SyncWorker
    src/jvmMain/: DesktopDataModule, DatabaseDriverFactory, DatabaseKeyProvider, NetworkMonitor
    src/jvmTest/: ProductRepositoryImplTest, TestDatabase, integration tests

  hal/
    src/commonMain/kotlin/.../hal/
      di/HalModule.kt
      printer/: EscPosReceiptBuilder, NullPrinterPort, PrinterConfig, PrinterManager,
                PrinterPort, ReceiptBuilder
      scanner/BarcodeScanner.kt ✅ (root-level duplicate deleted — F-03 resolved)
              ScanResult.kt
    src/androidMain/: Android BT+USB printer, Camera+USB scanner actuals
    src/jvmMain/: Desktop Serial+TCP+USB printer, HID+Serial scanner actuals

  security/
    src/commonMain/kotlin/.../security/
      audit/SecurityAuditLogger.kt ✅ (root-level duplicate deleted — F-04 resolved)
      auth/: JwtManager, PasswordHasher (expect), PinManager, PlaceholderPasswordHasher,
             SecureRandomBytes (expect), Sha256 (expect)
      crypto/: DatabaseKeyManager (expect), EncryptionManager (expect)
      di/SecurityModule.kt
      keystore/.gitkeep                       ⚠️ F-07 unresolved — empty scaffold
      prefs/: SecurePreferences.kt, SecurePreferencesKeys.kt ✅ NEW, TokenStorage.kt
      rbac/RbacEngine.kt
      token/.gitkeep                          ⚠️ F-08 unresolved — empty scaffold
    src/androidMain/: PasswordHasher, SecureRandomBytes, Sha256, DatabaseKeyManager,
                       EncryptionManager, SecurePreferences actuals
    src/jvmMain/:     PasswordHasher, SecureRandomBytes, Sha256, DatabaseKeyManager,
                       EncryptionManager, SecurePreferences actuals
    src/commonTest/: 6 test files

docs/
  adr/
    ADR-001-ViewModelBaseClass.md             ✅ NEW — BaseViewModel policy, ACCEPTED
    ADR-002-DomainModelNaming.md              ✅ NEW — no *Entity in domain, ACCEPTED
  plans/
    ER_diagram.md
    Master_plan.md
    PLAN_COMPAT_VERIFICATION_v1.0.md
    PLAN_CONSOLIDATED_FIX_v1.0.md
    PLAN_MISMATCH_FIX_v1.0.md                ⚠️ F-10 — still not marked SUPERSEDED
    PLAN_NAMESPACE_FIX_v1.0.md
    PLAN_PHASE1.md
    PLAN_STRUCTURE_CROSSCHECK_v1.0.md
    PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md       ✅ NEW — rename plan (executed in code)
    UI_UX_Main_Plan.md
    ZyntaPOS_Junior_Developer_Guide.docx     ⚠️ F-11 — unaudited binary
  FIX-02_COMPLETION_SUMMARY.md
  FIX-02_INTEGRITY_VERIFICATION.md
  FIX-02_MODULE_NAME_CANONICALIZATION.md
  FIX-03_COMPLETION_SUMMARY.md
  FIX-03_INTEGRITY_VERIFICATION.md
  ai_workflows/execution_log.md
  api/README.md                               [empty placeholder]
  architecture/README.md                      [empty placeholder]
  audit_phase_{1..4}_result.md               [v1 audit outputs]
  compliance/README.md                        [empty placeholder]
  zentapos-audit-final-synthesis.md           ⚠️ prompt template — not a completed report

Root:
  CONTRIBUTING.md                            ✅ NEW — naming conventions + ADR links
  README.md
  build.gradle.kts
  settings.gradle.kts                        [23 modules, correct header count]
  gradle/libs.versions.toml
  gradle/gradle-daemon-jvm.properties
  gradle/wrapper/
  .github/workflows/ci.yml
  local.properties.template
  gradlew / gradlew.bat
```

---

## SECTION 2 — DOCS INDEX

| Doc | Path | Purpose / Scope | Key Claims |
|---|---|---|---|
| **Master_plan.md** | `docs/plans/` | Enterprise master blueprint | Architecture: Clean Arch + MVI + KMP; Package: `com.zyntasolutions.zyntapos`; 20 module IDs (M01–M20 — M07 nav gap in registry); 17 domains, 87+ feature groups; SQLDelight + SQLCipher AES-256 |
| **PLAN_PHASE1.md** | `docs/plans/` | 6-month MVP execution plan (24 sprints) | Kotlin 2.1+ (actual: 2.3.0); Compose MP 1.7+ (actual: 1.10.0); full POS/Inventory/Register/Reports/Settings; SQLCipher; offline-first |
| **ER_diagram.md** | `docs/plans/` | Entity-relationship diagram | 63 entities, 80 relationships; 10 domains; 46 Phase 1 entities; CRDT metadata columns |
| **UI_UX_Main_Plan.md** | `docs/plans/` | UI/UX blueprint | Platforms: Desktop JVM + Android; Material 3; 14 named screens; 3 breakpoints; min touch 48dp; POS ≤2 taps |
| **PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md** | `docs/plans/` | Design system prefix rename plan (Zenta→Zynta) | 29 designsystem .kt files; 56 consumer .kt files; 13 .md files; 4-phase execution; DOD = 5 unchecked items |
| **PLAN_COMPAT_VERIFICATION_v1.0.md** | `docs/plans/` | Kotlin 2.3.0 compatibility matrix | Kotlin 2.3.0; Compose MP 1.10.0; `compose-adaptive 1.1.0-alpha04` ⚠️; `androidx-security-crypto 1.1.0-alpha06` ⚠️; Sprint 4 clear |
| **PLAN_CONSOLIDATED_FIX_v1.0.md** | `docs/plans/` | Master fix list (18 issues) | FIX-01 through FIX-14; supersedes PLAN_MISMATCH_FIX |
| **PLAN_MISMATCH_FIX_v1.0.md** | `docs/plans/` | Original mismatch audit (**SUPERSEDED**) | STATUS header still reads `🔴 REQUIRES IMMEDIATE ACTION` — misleading |
| **PLAN_STRUCTURE_CROSSCHECK_v1.0.md** | `docs/plans/` | Module/file alignment check (2026-02-20) | 22 modules (stale — actual 23); Sprint 1–3 files present; Sprint 4–24 not started |
| **PLAN_NAMESPACE_FIX_v1.0.md** | `docs/plans/` | Namespace migration log | `com.zynta.pos` → `com.zyntasolutions.zyntapos` — all 22 groups complete |
| **ADR-001-ViewModelBaseClass.md** | `docs/adr/` | ViewModel base class policy (ACCEPTED) | All ViewModels MUST extend `ui.core.mvi.BaseViewModel`; `ReportsViewModel` + `SettingsViewModel` migrated; zombie deleted |
| **ADR-002-DomainModelNaming.md** | `docs/adr/` | Domain model naming (ACCEPTED — Option B) | No `*Entity` suffix in `shared/domain`; `*Entity` reserved for `shared/data` persistence types; 26 models confirmed plain-named |
| **CONTRIBUTING.md** | root | Developer onboarding guide | Naming rules (ADR-002 linked); MVI pattern; Koin DI conventions; test targets; ADR table (ADR-002 listed; **ADR-001 missing**) |
| **composeApp/feature/pos/README.md** | `composeApp/feature/pos/` | POS module guide | Documents `PosSearchBar` as intentional thin wrapper; debounce in VM; F2 keyboard shortcut via FocusRequester |
| **zentapos-audit-final-synthesis.md** | `docs/` | **Prompt template only — no actual content** | Contains "paste your 4 phase results here" placeholder; not a completed synthesis |
| **execution_log.md** | `docs/ai_workflows/` | AI agent execution log | Phase 0 complete; Sprint 4–24 pending; `composeHotReload 1.0.0` retained |
| **ZyntaPOS_Junior_Developer_Guide.docx** | `docs/plans/` | Binary Word document | Content unaudited; may contain stale `Zenta*`/`com.zynta.pos` references |

---

## SECTION 3 — FINDINGS

### STATUS DELTA: v1 Findings → v2

| v1 Finding | Status | Evidence |
|---|---|---|
| F-01 — Dual BaseViewModel | ✅ RESOLVED | `shared/core/.../mvi/` contains only `.gitkeep`; ADR-001 documents the decision |
| F-03 — BarcodeScanner root duplicate | ✅ RESOLVED | Root file absent; `hal/scanner/BarcodeScanner.kt` is sole copy |
| F-04 — SecurityAuditLogger root duplicate | ✅ RESOLVED | Root file absent; `security/audit/SecurityAuditLogger.kt` is sole copy |
| F-09 — Module count discrepancy | ✅ RESOLVED | `settings.gradle.kts` header now reads "23 modules" |
| P2-01 — composeApp:core missing in feature build.gradle | ✅ RESOLVED | `pos`, `settings`, `reports` all declare `implementation(project(":composeApp:core"))` |
| P2-02 — ReportsVM/SettingsVM wrong base class | ✅ RESOLVED | ADR-001: both migrated to `ui.core.mvi.BaseViewModel` |
| P2-03 — settings missing `:shared:hal` dep | ✅ RESOLVED | `settings/build.gradle.kts` has `implementation(project(":shared:hal"))` |
| P2-04/F-05 — TaxGroupRepositoryImpl missing | ⚠️ PARTIAL | File exists; all 5 methods are `TODO` stubs referencing "MERGED-D2"; schema exists but impl is not written |
| P2-05 — AuditRepositoryImpl + UserRepositoryImpl missing | ✅ RESOLVED | Both files exist in `shared/data/src/.../data/repository/` |
| P2-06 — tax_groups.sq + units_of_measure.sq missing | ✅ RESOLVED | Both schema files exist with complete queries |
| F-02/AV-02a — PrintReceiptUseCase in feature/pos | ⚠️ PARTIAL | Domain version added; **feature/pos version still exists** (duplicate) |
| DUP-03 — BaseViewModel dual copy | ✅ RESOLVED | Zombie deleted; `composeApp/core` version is sole copy |
| DUP-07 — ProductFormValidator in feature/inventory | ✅ RESOLVED | `ProductValidator.kt` + `ProductValidationParams.kt` now in `shared/domain/validation/` |
| DUP-08 — FakeRepositories fragmented into Part1/2/3 | ✅ RESOLVED | Reorganized into 4 domain-grouped files |
| DUP-09 — PosSearchBar wrapper vs reimplementation | ✅ RESOLVED | `feature/pos/README.md` documents it as intentional wrapper with rationale |
| NC-01 — No ADR for domain model naming | ✅ RESOLVED | ADR-002 created and accepted |
| BC-02 — androidx-work literal version | ✅ RESOLVED | `libs.versions.toml` has `androidx-work = "2.10.1"` version entry; library uses `version.ref` |
| DC-03 — SecurePreferences key mismatch | ✅ RESOLVED | `SecurePreferencesKeys.kt` (canonical key registry) + `SecurePreferencesKeyMigration.kt` created |
| P2-09 — compose-multiplatform.xml boilerplate | ❌ STILL OPEN | File still present at `composeApp/src/commonMain/composeResources/drawable/compose-multiplatform.xml` |
| F-07 — Empty keystore/ directories | ❌ STILL OPEN | All three `keystore/` dirs still contain only `.gitkeep` |
| F-08 — Empty token/ directory | ❌ STILL OPEN | `security/token/` still contains only `.gitkeep` |
| F-10 — PLAN_MISMATCH_FIX_v1.0.md not marked SUPERSEDED | ❌ STILL OPEN | Header still reads `🔴 REQUIRES IMMEDIATE ACTION` |
| F-11 — Junior Developer Guide unaudited | ❌ STILL OPEN | `ZyntaPOS_Junior_Developer_Guide.docx` still unaudited |
| DC-05 — Master_plan versions stale | ❌ STILL OPEN | Master_plan §15.1 not verified in this phase |

---

### NEW FINDINGS (v2)

---

#### NF-01 — `PrintReceiptUseCase` ORPHAN DUPLICATE in `:composeApp:feature:pos` 🔴 CRITICAL

**Files:**
- `composeApp/feature/pos/src/commonMain/kotlin/.../feature/pos/PrintReceiptUseCase.kt` — 113 lines, package `feature.pos`, full HAL+Security deps
- `shared/domain/src/commonMain/kotlin/.../domain/usecase/pos/PrintReceiptUseCase.kt` — 46 lines, package `domain.usecase.pos`, delegates to `ReceiptPrinterPort`

**Situation:** The Ports & Adapters refactor added the canonical domain version (`shared/domain`) and the correct adapter (`PrinterManagerReceiptAdapter`). However, the original feature-layer `PrintReceiptUseCase` was **not deleted**. Both classes have the same simple name `PrintReceiptUseCase`. The Koin DI module in `PosModule.kt` must be checked — if it still registers the feature version, the canonical domain use case is never used.

**Risk:** Two `PrintReceiptUseCase` classes in different packages will confuse developers via IDE auto-import. If the Koin module binds the wrong one, the Ports & Adapters refactor provides zero benefit and `ReceiptPrinterPort` is never injected. This is a **correctness blocker** that defeats an architectural improvement.

**What docs say:** Master_plan §3 → use cases live in `:shared:domain`. ADR-001 enforces separation. Phase 2 P2-03 (RESOLVED) specifically called for this class to move.

**Recommendation:** Delete `composeApp/feature/pos/src/commonMain/kotlin/.../feature/pos/PrintReceiptUseCase.kt`. Verify `PosModule.kt` registers `PrintReceiptUseCase` from `domain.usecase.pos` and injects `PrinterManagerReceiptAdapter` as the `ReceiptPrinterPort`.

---

#### NF-02 — `TaxGroupRepositoryImpl` + `UnitGroupRepositoryImpl` — All Methods are `TODO` Stubs 🔴 CRITICAL

**Files:**
- `shared/data/src/commonMain/kotlin/.../data/repository/TaxGroupRepositoryImpl.kt` — all 5 methods: `TODO("Requires tax_groups.sq — tracked in MERGED-D2")`
- `shared/data/src/commonMain/kotlin/.../data/repository/UnitGroupRepositoryImpl.kt` — all 5 methods: `TODO("Requires units_of_measure.sq SQLDelight schema — tracked in MERGED-D2")`

**Situation:** Both `TODO` comments reference "MERGED-D2" as a blocking prerequisite. However, both SQLDelight schema files (`tax_groups.sq` and `units_of_measure.sq`) now exist with complete queries. The "MERGED-D2" blocker is resolved, but the impl bodies were never written.

**Risk:** Both repositories are registered in `DataModule.kt` via Koin:
```kotlin
single<TaxGroupRepository> { TaxGroupRepositoryImpl(db = get(), syncEnqueuer = get()) }
single<UnitGroupRepository> { UnitGroupRepositoryImpl(db = get(), syncEnqueuer = get()) }
```
Any ViewModel that injects `TaxGroupRepository` or `UnitGroupRepository` will crash at runtime with `NotImplementedError: An operation is not implemented: ...` the moment a method is called. `SettingsViewModel` injects `TaxGroupRepository` for the Tax Settings screen — this is an **imminent runtime crash**.

**Recommendation:** Implement all 5 methods in each repository using the now-available SQLDelight query interfaces (`taxGroupsQueries`, `unitsOfMeasureQueries`). The schema files provide all needed queries — `getAllTaxGroups`, `insertTaxGroup`, `updateTaxGroup`, `softDeleteTaxGroup`, `getTaxGroupById` for tax groups; equivalent queries for units. Target: Sprint 5 or first available dev cycle.

---

#### NF-03 — `PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md` — Executed in Code, DOD Unchecked 🟠 MEDIUM

**File:** `docs/plans/PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md`

**Situation:** All designsystem files on disk use `Zynta*` identifiers — the rename has been executed in code. However, the plan's Definition of Done section shows 5 unchecked checkboxes:
```
- [ ] grep ... → 0 results
- [ ] grep docs/ → 0 results
- [ ] ./gradlew :composeApp:designsystem:compileKotlinJvm → BUILD SUCCESSFUL
- [ ] ./gradlew :composeApp:feature:pos:compileKotlinJvm → BUILD SUCCESSFUL
- [ ] execution_log.md updated with [x] CLOSED entry
```
The plan is marked `STATUS: APPROVED FOR EXECUTION` — making it appear work is pending when it's done.

**Risk:** Junior developers reading this plan will attempt to re-execute the rename, causing regressions.

**Recommendation:** Check all 5 DOD items, update `STATUS:` to `STATUS: COMPLETE`, and append a closure entry to `execution_log.md`.

---

#### NF-04 — `zentapos-audit-final-synthesis.md` is a Prompt Template — Not a Report 🟠 MEDIUM

**File:** `docs/zentapos-audit-final-synthesis.md`

**Situation:** This file contains the prompt instructions for generating a cross-phase synthesis, including phrases like `[PHASE 1 OUTPUT — paste here]` and `[Step 1 — Cross-Phase Mismatch Detection]`. It was committed to the repository as if it were a completed audit document.

**Risk:** Developers and architects treating this as a completed synthesis will find no actual findings. The filename implies a final authoritative report that does not exist.

**Recommendation:** Either (a) run the synthesis and replace the template content with actual results, or (b) rename the file to `zentapos-audit-final-synthesis-TEMPLATE.md` to signal it is a prompt, not a report. Option (a) is preferred for completeness.

---

#### NF-05 — `ReceiptFormatter.kt` Contains Stale "ZENTRA" Branding 🟡 LOW

**File:** `shared/domain/src/commonMain/kotlin/.../domain/formatter/ReceiptFormatter.kt`

**Situation:** The KDoc example output inside this file shows:
```
*             ZENTRA POINT OF SALE
```
`PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md` §1C lists `.md` files for brand alignment but does not include `.kt` files in `shared/domain/`. This stale string was missed by the rename plan.

**Recommendation:** Update the example output in the KDoc comment to `ZYNTA POINT OF SALE` or `ZyntaPOS`. Run `grep -r "ZENTRA" --include="*.kt"` project-wide to catch any remaining occurrences.

---

#### NF-06 — `SecurePreferencesKeyMigration` Not Verified as Wired to App Startup 🟡 LOW

**File:** `shared/data/src/commonMain/kotlin/.../data/local/db/SecurePreferencesKeyMigration.kt`

**Situation:** The KDoc states: *"Call `migrate()` once during application startup, before any auth operation."* The migration class exists and is correct, but the call site was not found in:
- `androidApp/src/main/kotlin/.../MainActivity.kt`
- `composeApp/src/jvmMain/kotlin/.../main.kt`
- Any platform Koin app-start module

**Risk:** If `migrate()` is never called, users upgrading from a pre-canonical-key build will have their auth tokens silently invalidated — every user will be force-logged-out on upgrade. The migration is **only useful if it runs**.

**Recommendation:** Verify that `SecurePreferencesKeyMigration(get()).migrate()` (or equivalent) is called in the application bootstrap for both Android and Desktop targets before any `AuthRepository` operation.

---

#### NF-07 — `CONTRIBUTING.md` ADR Table Omits ADR-001 🟡 LOW

**File:** `CONTRIBUTING.md` — §7 Architecture Decision Records

**Situation:** The ADR table in CONTRIBUTING.md lists only ADR-002:
```
| ADR-002 | Domain Model Naming Convention | ACCEPTED |
```
ADR-001 (ViewModel base class policy — the most enforcement-critical ADR) is absent.

**Recommendation:** Add ADR-001 to the table:
```
| ADR-001 | ViewModel Base Class Policy | ACCEPTED |
```

---

#### NF-08 — Empty `keystore/` and `token/` Scaffold Directories Persist (F-07 / F-08) 🟡 LOW

**Directories:**
- `shared/security/src/commonMain/kotlin/.../security/keystore/` (`.gitkeep` only)
- `shared/security/src/androidMain/kotlin/.../security/keystore/` (`.gitkeep` only)
- `shared/security/src/jvmMain/kotlin/.../security/keystore/` (`.gitkeep` only)
- `shared/security/src/commonMain/kotlin/.../security/token/` (`.gitkeep` only)

Raised as F-07 and F-08 in v1 Phase 1, both remain at "NEEDS CLARIFICATION" — no decision documented in any ADR or execution log.

**Recommendation:** Make an explicit architectural decision: (a) If `keystore/` is for a future `KeystoreProvider` expect/actual — create the expect declaration now or add a GitHub issue; (b) If it's residual scaffold — delete it and log the decision. Same for `token/`. Either path must produce a documented outcome.

---

#### NF-09 — Boilerplate `compose-multiplatform.xml` Asset Persists (P2-09) 🟡 LOW

**File:** `composeApp/src/commonMain/composeResources/drawable/compose-multiplatform.xml`

Flagged in v1 Phase 2 (P2-09) as JetBrains project template clutter. Still present.

**Recommendation:** Delete the file. Run `./gradlew :composeApp:compileKotlinJvm` afterward to confirm no `composeResources` reference remains.

---

## SECTION 4 — PRE-SPRINT-5 ACTION PLAN

| Priority | ID | Finding | Action | File(s) |
|---|---|---|---|---|
| 🔴 P0 | NF-01 | `PrintReceiptUseCase` orphan in `feature/pos` | Delete feature version; verify `PosModule.kt` DI binding | `composeApp/feature/pos/.../PrintReceiptUseCase.kt` |
| 🔴 P0 | NF-02 | `TaxGroupRepositoryImpl` all TODO stubs | Implement all 5 methods using `tax_groups.sq` queries | `shared/data/.../repository/TaxGroupRepositoryImpl.kt` |
| 🔴 P0 | NF-02 | `UnitGroupRepositoryImpl` all TODO stubs | Implement all 5 methods using `units_of_measure.sq` queries | `shared/data/.../repository/UnitGroupRepositoryImpl.kt` |
| 🟠 P1 | NF-03 | PLAN_ZENTA_TO_ZYNTA DOD unchecked | Check all 5 DOD items; update STATUS; close in execution_log | `docs/plans/PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md` |
| 🟠 P1 | NF-04 | Synthesis doc is an unfilled template | Fill with actual cross-phase findings OR rename to TEMPLATE | `docs/zentapos-audit-final-synthesis.md` |
| 🟡 P2 | NF-06 | Key migration not verified as wired | Add `SecurePreferencesKeyMigration(get()).migrate()` to app startup | `androidApp/MainActivity.kt`, `composeApp/main.kt` |
| 🟡 P2 | NF-05 | "ZENTRA" string in ReceiptFormatter | Update KDoc example + grep project for remaining occurrences | `shared/domain/.../formatter/ReceiptFormatter.kt` |
| 🟡 P3 | NF-07 | ADR-001 missing from CONTRIBUTING.md | Add ADR-001 row to §7 ADR table | `CONTRIBUTING.md` |
| 🟡 P3 | F-10 | PLAN_MISMATCH_FIX not marked SUPERSEDED | Add `⚠️ SUPERSEDED` banner to top of file | `docs/plans/PLAN_MISMATCH_FIX_v1.0.md` |
| 🟡 P3 | NF-08 | Empty `keystore/` + `token/` dirs | Make explicit decision — scaffold or delete — document outcome | `shared/security/src/*/security/keystore/`, `token/` |
| 🟡 P3 | NF-09 | Boilerplate compose-multiplatform.xml | Delete file | `composeApp/src/commonMain/composeResources/drawable/` |
| NC | F-11 | Junior Developer Guide unaudited | Extract text; audit for stale `Zenta*` / `com.zynta.pos` names | `docs/plans/ZyntaPOS_Junior_Developer_Guide.docx` |

---

## SECTION 5 — CUMULATIVE STATUS SNAPSHOT

| Category | v1 Phase 4 | v2 Phase 1 |
|---|---|---|
| Total open findings | 47 | **19** |
| Closed since v1 | — | **28** |
| New findings | — | **9** |
| 🔴 Critical | ~8 | **3** |
| 🟠 Medium | ~12 | **2** |
| 🟡 Low | ~27 | **8** |
| Documentation only | ~6 | **6** |

**The most acute risk before Sprint 5:** `TaxGroupRepositoryImpl` and `UnitGroupRepositoryImpl` are registered in Koin with `TODO` method bodies and will throw `NotImplementedError` the moment any Settings or Inventory screen attempts to load tax groups or units. This must be resolved before any integration testing.

**Second acute risk:** `PrintReceiptUseCase` duplicate — the entire Ports & Adapters refactor is bypassed if `PosModule.kt` still injects the feature-layer version.

---

## SECTION 6 — VERIFICATION CHECKLIST

| Check | Status |
|---|---|
| All 23 modules physically present | ✅ |
| Package `com.zyntasolutions.zyntapos` consistent | ✅ |
| `:composeApp` has no Android `res/` directory | ✅ |
| `:androidApp` `res/` intact | ✅ |
| `BaseViewModel.kt` in `shared/core` deleted | ✅ |
| `BarcodeScanner.kt` root copy deleted | ✅ |
| `SecurityAuditLogger.kt` root copy deleted | ✅ |
| Design system uses `Zynta*` prefix | ✅ |
| `PrintReceiptUseCase` in `shared/domain` | ✅ (canonical added) |
| `PrintReceiptUseCase` in `feature/pos` deleted | ❌ (orphan still present) |
| `TaxGroupRepositoryImpl` implemented | ❌ (all TODO stubs) |
| `UnitGroupRepositoryImpl` implemented | ❌ (all TODO stubs) |
| `tax_groups.sq` schema exists | ✅ |
| `units_of_measure.sq` schema exists | ✅ |
| `SecurePreferencesKeyMigration` wired to startup | ⚠️ UNVERIFIED |
| Module count in `settings.gradle.kts` = 23 | ✅ |
| ADR-001 + ADR-002 created and accepted | ✅ |
| `PLAN_ZENTA_TO_ZYNTA_RENAME` DOD complete | ❌ (unchecked) |
| `zentapos-audit-final-synthesis.md` filled | ❌ (template only) |
| `PLAN_MISMATCH_FIX_v1.0.md` marked SUPERSEDED | ❌ |
| `keystore/` + `token/` scaffold resolved | ❌ |
| Boilerplate `compose-multiplatform.xml` deleted | ❌ |

---

*End of Audit v2 — Phase 1 — ZyntaPOS ZENTA-AUDIT-V2-PHASE1-DISCOVERY*  
*Next: Phase 2 — Alignment Audit (Docs → Code forward check, Code → Docs reverse check)*
