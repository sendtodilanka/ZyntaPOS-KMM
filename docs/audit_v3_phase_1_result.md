# ZyntaPOS — Architecture Audit v3 | Phase 1: Discovery

> **Auditor:** Senior KMP Architect (AI-assisted)
> **Date:** 2026-02-22
> **Project Root:** `/Users/dilanka/Developer/StudioProjects/ZyntaPOS/`
> **Status:** ✅ PHASE 1 COMPLETE — Ready for Phase 2 (Alignment Audit)
> **Scope:** Full project tree scan + complete docs index. No code analysis yet.

---

## TABLE OF CONTENTS

1. [Project Tree](#1-project-tree)
2. [Docs Index](#2-docs-index)
3. [Phase 1 Observations](#3-phase-1-observations)

---

## 1. PROJECT TREE

> Excluded: `.gradle/`, `build/`, `.idea/`, `.git/`, `*.iml`, `.kotlin/metadata/`

```
ZyntaPOS/
│
├── .github/
│   └── workflows/
│       └── ci.yml
│
├── androidApp/                                 [MODULE — Android App Entry Point]
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/com/zyntasolutions/zyntapos/
│       │   ├── MainActivity.kt
│       │   └── ZyntaApplication.kt
│       └── res/                               [Launcher icons, strings — canonical location]
│           ├── drawable/, drawable-v24/
│           ├── mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi,anydpi-v26}/
│           └── values/strings.xml
│
├── composeApp/                                [MODULE GROUP — Presentation Layer]
│   ├── build.gradle.kts
│   │
│   ├── core/                                  [MODULE — MVI Base]
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── commonMain/kotlin/.../ui/core/mvi/
│   │       │   └── BaseViewModel.kt           ← CANONICAL — ADR-001 enforced
│   │       └── commonTest/kotlin/.../ui/core/mvi/
│   │
│   ├── designsystem/                          [MODULE — Material 3 Design System]
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── commonMain/
│   │       │   ├── composeResources/font/     [.gitkeep — no custom fonts yet]
│   │       │   └── kotlin/.../designsystem/
│   │       │       ├── DesignSystemModule.kt
│   │       │       ├── components/
│   │       │       │   ├── ZyntaBadge.kt
│   │       │       │   ├── ZyntaBottomSheet.kt
│   │       │       │   ├── ZyntaButton.kt
│   │       │       │   ├── ZyntaCartItemRow.kt
│   │       │       │   ├── ZyntaDialog.kt
│   │       │       │   ├── ZyntaEmptyState.kt
│   │       │       │   ├── ZyntaLoadingOverlay.kt
│   │       │       │   ├── ZyntaNumericPad.kt
│   │       │       │   ├── ZyntaProductCard.kt
│   │       │       │   ├── ZyntaSearchBar.kt
│   │       │       │   ├── ZyntaSnackbarHost.kt
│   │       │       │   ├── ZyntaSyncIndicator.kt
│   │       │       │   ├── ZyntaTable.kt
│   │       │       │   ├── ZyntaTextField.kt
│   │       │       │   └── ZyntaTopAppBar.kt
│   │       │       ├── layouts/
│   │       │       │   ├── ZyntaGrid.kt
│   │       │       │   ├── ZyntaListDetailLayout.kt
│   │       │       │   ├── ZyntaScaffold.kt
│   │       │       │   └── ZyntaSplitPane.kt
│   │       │       ├── theme/
│   │       │       │   ├── ZyntaColors.kt
│   │       │       │   ├── ZyntaShapes.kt
│   │       │       │   ├── ZyntaTheme.kt
│   │       │       │   └── ZyntaTypography.kt
│   │       │       └── tokens/
│   │       │           ├── ZyntaElevation.kt
│   │       │           └── ZyntaSpacing.kt
│   │       ├── androidMain/.../designsystem/
│   │       │   ├── theme/ZyntaTheme.android.kt
│   │       │   └── util/WindowSizeClassHelper.android.kt
│   │       ├── jvmMain/.../designsystem/
│   │       │   ├── theme/ZyntaTheme.desktop.kt
│   │       │   └── util/WindowSizeClassHelper.desktop.kt
│   │       └── commonTest/.../designsystem/
│   │           └── DesignSystemComponentTests.kt
│   │
│   ├── navigation/                            [MODULE — Type-Safe Navigation]
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── commonMain/kotlin/.../navigation/
│   │       │   ├── AuthNavGraph.kt
│   │       │   ├── MainNavGraph.kt
│   │       │   ├── MainNavScreens.kt
│   │       │   ├── NavigationController.kt
│   │       │   ├── NavigationItems.kt
│   │       │   ├── NavigationModule.kt
│   │       │   ├── ZyntaNavGraph.kt
│   │       │   └── ZyntaRoute.kt
│   │       ├── androidMain/kotlin/.../navigation/   [.gitkeep — no Android-specific nav]
│   │       ├── jvmMain/kotlin/.../navigation/
│   │       └── commonTest/kotlin/.../navigation/    [.gitkeep — no nav tests yet]
│   │
│   └── feature/                               [FEATURE MODULES — 13 total]
│       │
│       ├── auth/                              ✅ IMPLEMENTED
│       │   └── src/commonMain/.../feature/auth/
│       │       ├── AuthModule.kt
│       │       ├── AuthViewModel.kt
│       │       ├── guard/
│       │       │   ├── RoleGuard.kt
│       │       │   └── SessionGuard.kt
│       │       ├── mvi/
│       │       │   ├── AuthEffect.kt
│       │       │   ├── AuthIntent.kt
│       │       │   └── AuthState.kt
│       │       ├── screen/
│       │       │   ├── LoginScreen.kt
│       │       │   └── PinLockScreen.kt
│       │       └── session/SessionManager.kt
│       │       [+ commonTest: AuthViewModelTest, LoginUseCaseTest, SessionManagerTest]
│       │
│       ├── pos/                               ✅ IMPLEMENTED
│       │   ├── README.md
│       │   └── src/
│       │       ├── commonMain/.../feature/pos/
│       │       │   ├── BarcodeInputHandler.kt
│       │       │   ├── CartContent.kt, CartItemList.kt, CartPanel.kt, CartSummaryFooter.kt
│       │       │   ├── CashPaymentPanel.kt, SplitPaymentPanel.kt
│       │       │   ├── CategoryFilterRow.kt
│       │       │   ├── CustomerSelectorDialog.kt
│       │       │   ├── HeldOrdersBottomSheet.kt, HoldOrderDialog.kt
│       │       │   ├── ItemDiscountDialog.kt, OrderDiscountDialog.kt
│       │       │   ├── OrderHistoryScreen.kt, OrderNotesDialog.kt
│       │       │   ├── PaymentMethodGrid.kt, PaymentScreen.kt, PaymentSuccessOverlay.kt
│       │       │   ├── PosEffect.kt, PosIntent.kt, PosModule.kt, PosSearchBar.kt
│       │       │   ├── PosState.kt, PosViewModel.kt
│       │       │   ├── ProductGridSection.kt
│       │       │   ├── ReceiptScreen.kt
│       │       │   └── printer/PrinterManagerReceiptAdapter.kt
│       │       ├── jvmMain/.../feature/pos/
│       │       │   └── KeyboardShortcutHandler.kt
│       │       └── commonTest/.../feature/pos/
│       │           └── PosViewModelTest.kt
│       │
│       ├── inventory/                         ✅ IMPLEMENTED
│       │   └── src/commonMain/.../feature/inventory/
│       │       ├── BarcodeGeneratorDialog.kt, BulkImportDialog.kt
│       │       ├── CategoryDetailScreen.kt, CategoryListScreen.kt
│       │       ├── InventoryEffect.kt, InventoryIntent.kt, InventoryModule.kt
│       │       ├── InventoryState.kt, InventoryViewModel.kt
│       │       ├── LowStockAlertBanner.kt
│       │       ├── ProductDetailScreen.kt, ProductListScreen.kt
│       │       ├── StockAdjustmentDialog.kt
│       │       ├── SupplierDetailScreen.kt, SupplierListScreen.kt
│       │       ├── TaxGroupScreen.kt
│       │       └── UnitManagementScreen.kt
│       │
│       ├── register/                          ✅ IMPLEMENTED
│       │   └── src/commonMain/.../feature/register/
│       │       ├── CashInOutDialog.kt, CashMovementHistory.kt
│       │       ├── CloseRegisterScreen.kt, OpenRegisterScreen.kt
│       │       ├── RegisterDashboardScreen.kt
│       │       ├── RegisterEffect.kt, RegisterGuard.kt, RegisterIntent.kt
│       │       ├── RegisterModule.kt, RegisterState.kt, RegisterViewModel.kt
│       │       ├── ZReportScreen.kt
│       │       └── printer/ZReportPrinterAdapter.kt
│       │
│       ├── reports/                           ✅ IMPLEMENTED
│       │   └── src/
│       │       ├── commonMain/.../feature/reports/
│       │       │   ├── DateRangePickerBar.kt
│       │       │   ├── ReportExporter.kt (interface)
│       │       │   ├── ReportsEffect.kt, ReportsHomeScreen.kt, ReportsIntent.kt
│       │       │   ├── ReportsModule.kt, ReportsState.kt, ReportsViewModel.kt
│       │       │   ├── SalesReportScreen.kt, StockReportScreen.kt
│       │       │   └── printer/ReportPrinterAdapter.kt
│       │       ├── androidMain/.../feature/reports/
│       │       │   ├── AndroidReportExporter.kt
│       │       │   └── AndroidReportsModule.kt
│       │       └── jvmMain/.../feature/reports/
│       │           ├── JvmReportExporter.kt
│       │           └── JvmReportsModule.kt
│       │
│       ├── settings/                          ✅ IMPLEMENTED
│       │   └── src/commonMain/.../feature/settings/
│       │       ├── PrintTestPageUseCaseImpl.kt
│       │       ├── SettingsEffect.kt, SettingsIntent.kt, SettingsKeys.kt
│       │       ├── SettingsModule.kt, SettingsState.kt, SettingsViewModel.kt
│       │       └── screen/
│       │           ├── AboutScreen.kt, AppearanceSettingsScreen.kt
│       │           ├── BackupSettingsScreen.kt, GeneralSettingsScreen.kt
│       │           ├── PosSettingsScreen.kt, PrinterSettingsScreen.kt
│       │           ├── SettingsHomeScreen.kt, TaxSettingsScreen.kt
│       │           └── UserManagementScreen.kt
│       │       [+ commonTest: SettingsViewModelTest]
│       │
│       ├── customers/                         🔲 SCAFFOLD — Phase 2
│       │   └── src/commonMain/.../feature/customers/CustomersModule.kt
│       ├── coupons/                           🔲 SCAFFOLD — Phase 2
│       │   └── src/commonMain/.../feature/coupons/CouponsModule.kt
│       ├── multistore/                        🔲 SCAFFOLD — Phase 2
│       │   └── src/commonMain/.../feature/multistore/MultistoreModule.kt
│       ├── expenses/                          🔲 SCAFFOLD — Phase 2
│       │   └── src/commonMain/.../feature/expenses/ExpensesModule.kt
│       ├── staff/                             🔲 SCAFFOLD — Phase 3
│       │   └── src/commonMain/.../feature/staff/StaffModule.kt
│       ├── admin/                             🔲 SCAFFOLD — Phase 3
│       │   └── src/commonMain/.../feature/admin/AdminModule.kt
│       └── media/                             🔲 SCAFFOLD — Phase 3
│           └── src/commonMain/.../feature/media/MediaModule.kt
│
├── shared/                                    [MODULE GROUP — Business Logic]
│   │
│   ├── core/                                  [MODULE — Cross-Cutting Utilities]
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── commonMain/kotlin/.../core/
│   │       │   ├── Platform.kt               [expect class]
│   │       │   ├── config/AppConfig.kt
│   │       │   ├── di/CoreModule.kt
│   │       │   ├── extensions/
│   │       │   │   ├── DoubleExtensions.kt
│   │       │   │   ├── LongExtensions.kt
│   │       │   │   └── StringExtensions.kt
│   │       │   ├── logger/ZyntaLogger.kt
│   │       │   ├── mvi/                      [.gitkeep — EMPTY, no BaseViewModel here]
│   │       │   ├── result/
│   │       │   │   ├── Result.kt
│   │       │   │   └── ZyntaException.kt
│   │       │   └── utils/
│   │       │       ├── CurrencyFormatter.kt
│   │       │       ├── DateTimeUtils.kt
│   │       │       └── IdGenerator.kt
│   │       ├── androidMain/kotlin/.../core/Platform.android.kt
│   │       ├── jvmMain/kotlin/.../core/Platform.jvm.kt
│   │       └── commonTest/kotlin/.../core/
│   │           ├── result/ResultTest.kt, ZyntaExceptionTest.kt
│   │           └── utils/CurrencyFormatterTest.kt, DateTimeUtilsTest.kt
│   │
│   ├── domain/                                [MODULE — Pure Business Logic]
│   │   ├── build.gradle.kts
│   │   └── src/commonMain/kotlin/.../domain/
│   │       ├── DomainModule.kt
│   │       ├── formatter/ReceiptFormatter.kt
│   │       ├── model/                         [26 domain models — ADR-002 plain names]
│   │       │   ├── AuditEntry.kt, CartItem.kt, CashMovement.kt, CashRegister.kt
│   │       │   ├── Category.kt, Customer.kt, DiscountType.kt
│   │       │   ├── Order.kt, OrderItem.kt, OrderStatus.kt, OrderTotals.kt, OrderType.kt
│   │       │   ├── PaymentMethod.kt, PaymentSplit.kt, Permission.kt
│   │       │   ├── PrinterPaperWidth.kt, Product.kt, ProductVariant.kt
│   │       │   ├── RegisterSession.kt, Role.kt, StockAdjustment.kt
│   │       │   ├── Supplier.kt, SyncOperation.kt, SyncStatus.kt
│   │       │   ├── TaxGroup.kt, UnitOfMeasure.kt, User.kt
│   │       ├── port/
│   │       │   ├── PasswordHashPort.kt
│   │       │   ├── SecureStorageKeys.kt
│   │       │   └── SecureStoragePort.kt
│   │       ├── printer/
│   │       │   ├── ReceiptPrinterPort.kt
│   │       │   ├── ReportPrinterPort.kt
│   │       │   └── ZReportPrinterPort.kt
│   │       ├── repository/                    [14 interface contracts]
│   │       │   ├── AuditRepository.kt, AuthRepository.kt, CategoryRepository.kt
│   │       │   ├── CustomerRepository.kt, OrderRepository.kt, ProductRepository.kt
│   │       │   ├── RegisterRepository.kt, SettingsRepository.kt, StockRepository.kt
│   │       │   ├── SupplierRepository.kt, SyncRepository.kt, TaxGroupRepository.kt
│   │       │   ├── UnitGroupRepository.kt, UserRepository.kt
│   │       ├── usecase/
│   │       │   ├── auth/    [CheckPermission, Login, Logout, ValidatePin]
│   │       │   ├── inventory/ [AdjustStock, CreateProduct, DeleteCategory, ManageUnitGroup,
│   │       │   │              SaveCategory, SaveSupplier, SaveTaxGroup, SearchProducts, UpdateProduct]
│   │       │   ├── pos/     [AddItemToCart, ApplyItemDiscount, ApplyOrderDiscount,
│   │       │   │             CalculateOrderTotals, HoldOrder, PrintReceipt, ProcessPayment,
│   │       │   │             RemoveItemFromCart, RetrieveHeldOrder, UpdateCartItemQuantity, VoidOrder]
│   │       │   ├── register/ [CloseRegisterSession, OpenRegisterSession, PrintZReport, RecordCashMovement]
│   │       │   ├── reports/  [GenerateSalesReport, GenerateStockReport, PrintReport]
│   │       │   └── settings/ [PrintTestPage, SaveUser]
│   │       └── validation/
│   │           ├── PaymentValidator.kt, ProductValidationParams.kt, ProductValidator.kt
│   │           ├── StockValidator.kt, TaxValidator.kt
│   │       [+ commonTest: AuthUseCasesTest, InventoryUseCasesTest, PosUseCasesTests,
│   │                      RegisterUseCasesTest, ReportUseCasesTest, ValidatorsTest,
│   │                      Fakes: FakeAuthRepositories, FakeInventoryRepositories,
│   │                             FakePosRepositories, FakeSharedRepositories]
│   │
│   ├── data/                                  [MODULE — Repository Implementations]
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── commonMain/kotlin/.../data/
│   │       │   ├── di/DataModule.kt
│   │       │   ├── local/
│   │       │   │   ├── SyncEnqueuer.kt
│   │       │   │   ├── db/
│   │       │   │   │   ├── DatabaseDriverFactory.kt   [expect class]
│   │       │   │   │   ├── DatabaseFactory.kt
│   │       │   │   │   ├── DatabaseKeyProvider.kt     [expect class]
│   │       │   │   │   ├── DatabaseMigrations.kt
│   │       │   │   │   └── SecurePreferencesKeyMigration.kt
│   │       │   │   ├── mapper/
│   │       │   │   │   ├── CategoryMapper.kt, CustomerMapper.kt, OrderMapper.kt
│   │       │   │   │   ├── ProductMapper.kt, RegisterMapper.kt, StockMapper.kt
│   │       │   │   │   ├── SupplierMapper.kt, SyncOperationMapper.kt, UserMapper.kt
│   │       │   │   └── security/                     [EMPTY DIR — stubs removed per ADR-003]
│   │       │   ├── remote/
│   │       │   │   ├── api/ApiClient.kt, ApiService.kt, KtorApiService.kt
│   │       │   │   └── dto/AuthDto.kt, OrderDto.kt, ProductDto.kt, SyncDto.kt
│   │       │   ├── repository/                       [14 implementations]
│   │       │   │   ├── AuditRepositoryImpl.kt, AuthRepositoryImpl.kt
│   │       │   │   ├── CategoryRepositoryImpl.kt, CustomerRepositoryImpl.kt
│   │       │   │   ├── OrderRepositoryImpl.kt, ProductRepositoryImpl.kt
│   │       │   │   ├── RegisterRepositoryImpl.kt, SettingsRepositoryImpl.kt
│   │       │   │   ├── StockRepositoryImpl.kt, SupplierRepositoryImpl.kt
│   │       │   │   ├── SyncRepositoryImpl.kt, TaxGroupRepositoryImpl.kt
│   │       │   │   ├── UnitGroupRepositoryImpl.kt, UserRepositoryImpl.kt
│   │       │   └── sync/NetworkMonitor.kt [expect], SyncEngine.kt
│   │       ├── commonMain/sqldelight/.../db/         [13 .sq schema files]
│   │       │   ├── audit_log.sq, categories.sq, customers.sq
│   │       │   ├── orders.sq, products.sq, registers.sq, settings.sq
│   │       │   ├── stock.sq, suppliers.sq, sync_queue.sq
│   │       │   ├── tax_groups.sq, units_of_measure.sq, users.sq
│   │       ├── androidMain/.../data/
│   │       │   ├── di/AndroidDataModule.kt
│   │       │   ├── local/db/DatabaseDriverFactory.kt [actual], DatabaseKeyProvider.kt [actual]
│   │       │   └── sync/NetworkMonitor.kt [actual], SyncWorker.kt
│   │       ├── jvmMain/.../data/
│   │       │   ├── di/DesktopDataModule.kt
│   │       │   ├── local/db/DatabaseDriverFactory.kt [actual], DatabaseKeyProvider.kt [actual]
│   │       │   └── sync/NetworkMonitor.kt [actual]
│   │       ├── commonTest/.../data/remote/ApiServiceTest.kt
│   │       └── jvmTest/.../data/
│   │           ├── ProductRepositoryImplTest.kt, TestDatabase.kt
│   │           ├── repository/ProductRepositoryIntegrationTest.kt
│   │           │              SyncRepositoryIntegrationTest.kt
│   │           └── sync/InMemorySecurePreferences.kt, SyncEngineIntegrationTest.kt
│   │
│   ├── hal/                                   [MODULE — Hardware Abstraction Layer]
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── commonMain/kotlin/.../hal/
│   │       │   ├── di/HalModule.kt           [expect]
│   │       │   ├── printer/
│   │       │   │   ├── EscPosReceiptBuilder.kt
│   │       │   │   ├── NullPrinterPort.kt
│   │       │   │   ├── PrinterConfig.kt
│   │       │   │   ├── PrinterManager.kt
│   │       │   │   ├── PrinterPort.kt
│   │       │   │   └── ReceiptBuilder.kt
│   │       │   └── scanner/
│   │       │       ├── BarcodeScanner.kt
│   │       │       └── ScanResult.kt
│   │       ├── androidMain/kotlin/.../hal/
│   │       │   ├── di/HalModule.android.kt   [actual]
│   │       │   ├── printer/AndroidBluetoothPrinterPort.kt, AndroidUsbPrinterPort.kt
│   │       │   └── scanner/AndroidCameraScanner.kt, AndroidUsbScanner.kt
│   │       └── jvmMain/kotlin/.../hal/
│   │           ├── di/HalModule.jvm.kt       [actual]
│   │           ├── printer/DesktopSerialPrinterPort.kt, DesktopTcpPrinterPort.kt,
│   │           │          DesktopUsbPrinterPort.kt
│   │           └── scanner/DesktopHidScanner.kt, DesktopSerialScanner.kt
│   │
│   └── security/                              [MODULE — Security & Crypto]
│       ├── build.gradle.kts
│       └── src/
│           ├── commonMain/kotlin/.../security/
│           │   ├── audit/SecurityAuditLogger.kt
│           │   ├── auth/
│           │   │   ├── JwtManager.kt
│           │   │   ├── PasswordHasher.kt          [expect]
│           │   │   ├── PasswordHasherAdapter.kt
│           │   │   ├── PinManager.kt
│           │   │   ├── PlaceholderPasswordHasher.kt
│           │   │   ├── SecureRandomBytes.kt       [expect]
│           │   │   └── Sha256.kt                  [expect]
│           │   ├── crypto/
│           │   │   ├── DatabaseKeyManager.kt      [expect]
│           │   │   └── EncryptionManager.kt       [expect]
│           │   ├── di/SecurityModule.kt
│           │   ├── keystore/                      [EMPTY DIR — ADR-004: scaffolds removed]
│           │   ├── prefs/
│           │   │   ├── SecurePreferences.kt       [expect — canonical per ADR-003]
│           │   │   ├── SecurePreferencesKeys.kt
│           │   │   └── TokenStorage.kt
│           │   ├── rbac/RbacEngine.kt
│           │   └── token/                         [EMPTY DIR — ADR-004: scaffolds removed]
│           ├── androidMain/kotlin/.../security/
│           │   ├── auth/PasswordHasher.android.kt, SecureRandomBytes.android.kt, Sha256.android.kt
│           │   ├── crypto/DatabaseKeyManager.android.kt, EncryptionManager.android.kt
│           │   ├── keystore/                      [EMPTY DIR]
│           │   └── prefs/SecurePreferences.android.kt
│           ├── jvmMain/kotlin/.../security/
│           │   ├── auth/PasswordHasher.jvm.kt, SecureRandomBytes.jvm.kt, Sha256.jvm.kt
│           │   ├── crypto/DatabaseKeyManager.jvm.kt, EncryptionManager.jvm.kt
│           │   ├── keystore/                      [EMPTY DIR]
│           │   └── prefs/SecurePreferences.jvm.kt
│           └── commonTest/kotlin/.../security/
│               ├── EncryptionManagerTest.kt, FakeSecurePreferences.kt
│               ├── JwtManagerTest.kt, PasswordHasherTest.kt
│               ├── PinManagerTest.kt, RbacEngineTest.kt
│
├── docs/                                      [DOCUMENTATION ROOT — see Section 2]
│   ├── adr/
│   │   ├── ADR-001-ViewModelBaseClass.md
│   │   ├── ADR-002-DomainModelNaming.md
│   │   ├── ADR-003-SecurePreferences-Consolidation.md
│   │   └── ADR-004-keystore-token-scaffold-removal.md
│   ├── ai_workflows/execution_log.md
│   ├── api/README.md
│   ├── architecture/README.md
│   ├── compliance/README.md
│   ├── plans/
│   │   ├── ER_diagram.md
│   │   ├── Master_plan.md
│   │   ├── PLAN_COMPAT_VERIFICATION_v1.0.md
│   │   ├── PLAN_CONSOLIDATED_FIX_v1.0.md
│   │   ├── PLAN_MISMATCH_FIX_v1.0.md
│   │   ├── PLAN_NAMESPACE_FIX_v1.0.md
│   │   ├── PLAN_PHASE1.md
│   │   ├── PLAN_STRUCTURE_CROSSCHECK_v1.0.md
│   │   ├── PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md
│   │   ├── UI_UX_Main_Plan.md
│   │   └── ZyntaPOS_Junior_Developer_Guide.docx
│   ├── FIX-02_COMPLETION_SUMMARY.md
│   ├── FIX-02_INTEGRITY_VERIFICATION.md
│   ├── FIX-02_MODULE_NAME_CANONICALIZATION.md
│   ├── FIX-03_COMPLETION_SUMMARY.md
│   ├── FIX-03_INTEGRITY_VERIFICATION.md
│   ├── action_prompts_v1.md
│   ├── audit_phase_{1,2,3,4}_result.md       [Audit v1 series — 4 files]
│   ├── audit_v2_{phase_1,2,3,4}_result.md    [Audit v2 series — 4 files]
│   ├── audit_v2_final_result.md
│   ├── sprint_progress.md
│   └── zentapos-audit-final-synthesis.md
│
├── gradle/
│   ├── gradle-daemon-jvm.properties
│   ├── libs.versions.toml                    [Version catalog]
│   └── wrapper/gradle-wrapper.{jar,properties}
│
├── build.gradle.kts                           [Root build file]
├── CONTRIBUTING.md
└── README.md
```

---

## 2. DOCS INDEX

| # | Doc | Path | Purpose / Scope | Key Claims |
|---|-----|------|-----------------|------------|
| 1 | **ADR-001** | `docs/adr/ADR-001-ViewModelBaseClass.md` | ACCEPTED policy: all ViewModels MUST use canonical BaseViewModel | Canonical class: `composeApp/core/src/commonMain/.../ui/core/mvi/BaseViewModel.kt`; direct `androidx.lifecycle.ViewModel` extension PROHIBITED in any feature module; zombie duplicate in `:shared:core` deleted; `ReportsViewModel` and `SettingsViewModel` migrated; future Lint rule planned |
| 2 | **ADR-002** | `docs/adr/ADR-002-DomainModelNaming.md` | ACCEPTED (Option B): plain names for domain models | 26 domain models confirmed in `shared/domain/model/`; `*Entity` suffix PROHIBITED in `:shared:domain`; `*Entity` RESERVED for ORM/persistence in `:shared:data`; no code changes required; enforced via code review |
| 3 | **ADR-003** | `docs/adr/ADR-003-SecurePreferences-Consolidation.md` | ACCEPTED: single canonical `SecurePreferences` location | Canonical: `security.prefs.SecurePreferences` (expect class) in `:shared:security`; `data.local.security.SecurePreferences` interface DELETED from `:shared:data`; adapter shims removed; `SecurePreferences` now implements `TokenStorage` interface |
| 4 | **ADR-004** | `docs/adr/ADR-004-keystore-token-scaffold-removal.md` | ACCEPTED: `.gitkeep` scaffold removal from `keystore/` and `token/` | Removed 4 `.gitkeep` files from `security/keystore/` (3 source sets) and `security/token/` (commonMain); keystore role absorbed by `crypto/EncryptionManager` + `crypto/DatabaseKeyManager`; token role absorbed by `prefs/TokenStorage` + `auth/JwtManager`; rationale comment added to `SecurityModule.kt` |
| 5 | **Master Plan** | `docs/plans/Master_plan.md` | Enterprise blueprint for entire ZyntaPOS system | 17 functional domains; 87+ feature groups; 450+ features; Clean Architecture + MVI; CRDT offline sync; HAL; 7-layer security (PCI-DSS, GDPR, SL E-Invoicing 2026); 21 modules (M01-M21); Phase 1 MVP: M01-M12, M18, M21; Phase 2 Growth: M13-M16; Phase 3 Enterprise: M17, M19, M20; BaseViewModel in `:composeApp:core`; package root `com.zyntasolutions.zyntapos`; targets: Android + Desktop JVM |
| 6 | **ER Diagram** | `docs/plans/ER_diagram.md` | Full database schema specification | 63 entities across 10 domains; 80 relationships; SQLDelight (local) + PostgreSQL (cloud); SQLCipher AES-256; base entity template with `id TEXT PK`, `sync_id`, `sync_version`, `sync_status`, `deleted_at` (soft delete); 13 local `.sq` files implemented vs 63 planned entities — **significant schema gap** |
| 7 | **UI/UX Plan** | `docs/plans/UI_UX_Main_Plan.md` | Full UI/UX specification for all 14 screens | Platforms: Desktop JVM + Android Tablet + Android Phone; Compose Multiplatform + Material 3; 23 sections covering all feature screens, responsive breakpoints, keyboard shortcuts, offline-first UX, accessibility; DesignSystem token library defined |
| 8 | **Phase 1 Plan** | `docs/plans/PLAN_PHASE1.md` | 24-sprint execution plan for Phase 1 MVP | Scope: 6 months, single-store offline-first; 13 modules; ~450+ tasks; 24 sprints × 1 week; deliverables: Android APK + Desktop JAR; references Master Plan + UI/UX Plan |
| 9 | **Sprint Progress** | `docs/sprint_progress.md` | Single source of truth for module readiness | Last updated: 2026-02-22; Phase 1 (11 modules): ✅ 100% implemented; Phase 2 (4 modules): 🔲 0% scaffold; Phase 3 (3 modules): 🔲 0% scaffold; overall 61% (11/18 modules); 7 scaffold modules have no ViewModel/Screens/nav wiring |
| 10 | **FIX-02 Summary** | `docs/FIX-02_COMPLETION_SUMMARY.md` | Fix record: `:feature:crm` → `:feature:customers` rename | 4 Master Plan occurrences updated; `settings.gradle.kts` already correct (`:composeApp:feature:customers`); zero code changes; completed 2026-02-20 |
| 11 | **FIX-02 Verification** | `docs/FIX-02_INTEGRITY_VERIFICATION.md` | Integrity proof for FIX-02 | Verifies all 4 rename sites in `Master_plan.md`; confirms no stale `:feature:crm` references remain |
| 12 | **FIX-02 Canonicalization** | `docs/FIX-02_MODULE_NAME_CANONICALIZATION.md` | Canonical module name registry | Documents `:composeApp:feature:customers` as the canonical Gradle path |
| 13 | **FIX-03 Summary** | `docs/FIX-03_COMPLETION_SUMMARY.md` | Fix record: duplicate Android resources removed from `:composeApp` | Deleted `composeApp/src/androidMain/res/` (launcher icons + strings.xml); resources correctly owned by `:androidApp`; resolved APK resource merge conflict |
| 14 | **FIX-03 Verification** | `docs/FIX-03_INTEGRITY_VERIFICATION.md` | Integrity proof for FIX-03 | Confirms resource directory deleted; `:androidApp/src/main/res/` remains as sole owner |
| 15 | **Action Prompts** | `docs/action_prompts_v1.md` | AI workflow prompts for code generation | Developer-facing prompt templates for Claude-assisted development |
| 16 | **AI Execution Log** | `docs/ai_workflows/execution_log.md` | Audit trail of AI-assisted changes | Session-by-session log of all FIX-02/03 and ADR changes |
| 17 | **Audit v1 (Phase 1-4)** | `docs/audit_phase_{1,2,3,4}_result.md` | First-generation audit results | Historical — superseded by v2 and v3 |
| 18 | **Audit v2 (Phase 1-4)** | `docs/audit_v2_phase_{1,2,3,4}_result.md` | Second-generation audit results | Historical — superseded by v3 |
| 19 | **Audit v2 Final** | `docs/audit_v2_final_result.md` | Final synthesis of v2 audit | Cross-phase findings, confirmed fixes, residual open items |
| 20 | **Final Synthesis Template** | `docs/zentapos-audit-final-synthesis.md` | Template for cross-phase audit synthesis | Defines format for mismatch detection, deduplication, consolidated reporting |
| 21 | **API README** | `docs/api/README.md` | API documentation placeholder | NEEDS CLARIFICATION: scope unknown (REST API? internal module API?) |
| 22 | **Architecture README** | `docs/architecture/README.md` | Architecture diagrams placeholder | NEEDS CLARIFICATION: no diagrams present — scaffold only |
| 23 | **Compliance README** | `docs/compliance/README.md` | PCI-DSS / GDPR / SL E-Invoicing placeholder | NEEDS CLARIFICATION: no content — scaffold only |
| 24 | **ER Diagram Plan (COMPAT)** | `docs/plans/PLAN_COMPAT_VERIFICATION_v1.0.md` | Compatibility verification plan | Verifies class/method compatibility across KMP source sets |
| 25 | **Consolidated Fix Plan** | `docs/plans/PLAN_CONSOLIDATED_FIX_v1.0.md` | Aggregated fix plan | Consolidates all identified mismatches into actionable fixes |
| 26 | **Mismatch Fix Plan** | `docs/plans/PLAN_MISMATCH_FIX_v1.0.md` | Specific mismatch corrections | Maps doc-vs-code mismatches to targeted edits |
| 27 | **Namespace Fix Plan** | `docs/plans/PLAN_NAMESPACE_FIX_v1.0.md` | Package/namespace corrections | Addresses package naming inconsistencies |
| 28 | **Structure Crosscheck** | `docs/plans/PLAN_STRUCTURE_CROSSCHECK_v1.0.md` | Module structure cross-check | Systematic verification of module directory structure vs plan |
| 29 | **Zenta→Zynta Rename** | `docs/plans/PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md` | Brand rename execution plan | Migration from `zenta` to `zynta` namespace; `ZentaPOS` → `ZyntaPOS` product name |
| 30 | **Junior Dev Guide** | `docs/plans/ZyntaPOS_Junior_Developer_Guide.docx` | Onboarding documentation | Binary DOCX — not read during scan; NEEDS CLARIFICATION: content unknown |

---

## 3. PHASE 1 OBSERVATIONS

> These are **raw observations only** — no recommendations yet. Recommendations are Phase 2 output.

### 3.1 Architecture Snapshot

| Concern | Status |
|---------|--------|
| Root package | `com.zyntasolutions.zyntapos` (all modules consistent) |
| Platforms | Android (androidMain) + Desktop JVM (jvmMain) — two platforms as planned |
| Source sets observed | `commonMain`, `androidMain`, `jvmMain`, `commonTest`, `jvmTest` |
| DI framework | Koin (expect/actual modules per platform) |
| Database | SQLDelight (13 `.sq` files found) |
| Networking | Ktor client (`KtorApiService.kt`) |
| HAL pattern | expect/actual — confirmed in `hal/`, `security/`, `data/local/db/` |
| BaseViewModel location | `composeApp/core/.../ui/core/mvi/BaseViewModel.kt` — matches ADR-001 |
| `shared/core/mvi/` directory | Present but **empty** (`.gitkeep` gone, no files) — consistent with ADR-001 |

### 3.2 Module Count Reconciliation

| Source | Module Count |
|--------|-------------|
| Master Plan claims | 21 modules (M01-M21) |
| Sprint Progress claims | 18 modules |
| Physically observed in filesystem | **18 Gradle modules** (5 shared + 3 composeApp infra + 1 androidApp + 13 features — minus `composeApp` root which is a group, not a module) |
| NEEDS CLARIFICATION | Master Plan's M01-M21 numbering does not obviously map to 18 physical modules. 3-module discrepancy needs resolution in Phase 2. |

### 3.3 SQLDelight Schema vs ER Diagram Gap

| Metric | ER Diagram Claim | SQLDelight `.sq` Files Found |
|--------|-----------------|------------------------------|
| Total entities planned | 63 entities across 10 domains | 13 `.sq` files |
| Phase 1 entities (Domains 1-4, 6-7, 10) | ~40 entities | 13 tables (users, categories, customers, orders, products, registers, settings, stock, suppliers, sync_queue, tax_groups, units_of_measure, audit_log) |

**Observation:** The ER diagram specifies a richer schema (warehouses, warehouse_racks, stores, unit_groups, units, product_variations, stock_entries, payments, payment_methods, order_items, order_tax_details, refunds, held_carts, sessions, roles, permissions, etc.) than what appears in the `.sq` files. This is a major area for Phase 2 verification.

### 3.4 Empty / Scaffold Directories Noted

| Path | Content | Doc Reference |
|------|---------|---------------|
| `shared/core/src/commonMain/.../core/mvi/` | **Empty** (no `.gitkeep`) | ADR-001 deleted zombie BaseViewModel |
| `shared/data/src/*/data/local/security/` | **Empty** dirs (androidMain + jvmMain) | ADR-003 deleted stubs |
| `shared/security/src/*/security/keystore/` | **Empty** dirs (3 source sets) | ADR-004 |
| `shared/security/src/commonMain/.../security/token/` | **Empty** dir | ADR-004 |
| `composeApp/designsystem/.../composeResources/font/` | `.gitkeep` only | No custom fonts loaded |
| `composeApp/navigation/src/androidMain/` | `.gitkeep` only | No Android-specific nav code |
| `composeApp/navigation/src/commonTest/` | `.gitkeep` only | No navigation tests |
| `composeApp/feature/reports/src/commonTest/` | Directory exists, **no test files** | NEEDS CLARIFICATION |

### 3.5 Brand Name Consistency

The project exhibits two name variants in use simultaneously:

| Variant | Occurrence |
|---------|-----------|
| `ZyntaPOS` / `Zynta` | Package root `com.zyntasolutions.zyntapos`, all class prefixes (`Zynta*`), `ZyntaApplication`, module dirs |
| `ZentaPOS` / `Zenta` | Doc IDs (`ZENTA-MASTER-PLAN-v1.0`), audit doc names (`zentapos-audit-final-synthesis.md`), `FIX-02/03` references, `PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md` |

**Observation:** A rename plan exists (`PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md`). Code is already on `Zynta`. Docs retain legacy `Zenta` prefixes in Document IDs and file names. Phase 2 should confirm whether doc-level rename is complete or intentionally deferred.

### 3.6 Navigation Module — Potential Gap

`composeApp/navigation/` contains `ZyntaNavGraph.kt`, `AuthNavGraph.kt`, `MainNavGraph.kt`, `ZyntaRoute.kt`, `NavigationController.kt`, `NavigationItems.kt`, `MainNavScreens.kt`. Sprint Progress notes: *"Type-safe nav graph skeleton (routes defined, graphs incomplete)"*. The `commonTest/` directory is empty (`.gitkeep` only). Phase 2 should verify whether all 11 implemented feature modules have navigation entries wired.

### 3.7 Docs Scaffold Gaps (No Content)

Three `docs/` subdirectories are effectively empty:

- `docs/api/` — `README.md` + `.gitkeep`. No API specs. Master Plan §11 specifies a full API & Integration Layer.
- `docs/architecture/` — `README.md` + `.gitkeep`. No diagrams. Master Plan §3 promises architecture diagrams.
- `docs/compliance/` — `README.md` + `.gitkeep`. No compliance docs. Master Plan §18 specifies PCI-DSS, GDPR, SL E-Invoicing governance.

---

## PHASE 1 COMPLETE

**Next Step → Phase 2: Alignment Audit**

Verify every key claim in the Docs Index against the actual codebase. Priority targets:
1. BaseViewModel superclass verification across all 11 feature ViewModels (ADR-001 compliance)
2. `*Entity` suffix audit in `shared/domain/model/` (ADR-002 compliance)
3. `SecurePreferences` import verification — no stale `data.local.security` references (ADR-003)
4. SQLDelight schema vs ER Diagram entity count reconciliation
5. Sprint Progress module status vs actual filesystem content (especially navigation wiring)
6. Master Plan 21-module claim vs 18 physical modules — identify the 3 discrepancy
7. `PlaceholderPasswordHasher.kt` still present in `shared/security/` — stub or production?
