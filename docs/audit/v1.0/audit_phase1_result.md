# Phase 1: Deep Discovery & Component Mapping

> **Audit Version:** v1.0
> **Date:** 2026-02-23
> **Auditor Role:** Staff KMP Solutions Architect, Lead Security Auditor, Principal Engineer
> **Project:** ZyntaPOS-KMM — Cross-platform Point of Sale (KMP + Compose Multiplatform)
> **Root Directory:** `/home/user/ZyntaPOS-KMM/`

---

## 1A. PROJECT TREE

**Build System:** Gradle 8.13.2 · Kotlin 2.3.0 · AGP 8.13.2
**Total Gradle Modules:** 23
**KMP Targets:** Android (minSdk 24 / compileSdk 36) + JVM Desktop (macOS, Windows, Linux)

### Full Hierarchical Tree

> Excludes: `.gradle/`, `build/`, `.idea/`, `.git/`, `*.iml`, `local.properties`

```
ZyntaPOS-KMM/
├── .claude/
│   └── worktrees/
│       └── peaceful-poincare/
├── .editorconfig
├── .gitignore
├── .github/
│   └── workflows/
│       ├── ci.yml                                          # CI pipeline (push/PR on main/develop)
│       └── release.yml                                     # Multi-platform release (macOS DMG, Windows MSI, Linux DEB)
├── .run/
│   └── desktopRun.run.xml                                  # IntelliJ IDEA run config
├── README.md
├── CONTRIBUTING.md
├── gradle_commands.md
├── version.properties
├── local.properties.template
│
├── build.gradle.kts                                        # Root build: Detekt, version properties
├── settings.gradle.kts                                     # Module registry (23 modules), repository management
├── gradle.properties                                       # Gradle JVM args, Android/Compose flags
├── gradle/
│   ├── gradle-daemon-jvm.properties
│   ├── libs.versions.toml                                  # Version catalog (~80 dependencies)
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties                       # Gradle 8.13.2
│
├── config/
│   └── detekt/
│       └── detekt.yml                                      # Static analysis rules
│
├── docs/
│   ├── adr/
│   │   ├── ADR-001-ViewModelBaseClass.md
│   │   ├── ADR-002-DomainModelNaming.md
│   │   ├── ADR-003-SecurePreferences-Consolidation.md
│   │   └── ADR-004-keystore-token-scaffold-removal.md
│   ├── api/
│   │   └── README.md                                       # Placeholder (KDoc + REST contracts)
│   ├── architecture/
│   │   └── README.md                                       # Placeholder (diagrams, dependency maps)
│   ├── compliance/
│   │   └── README.md                                       # Placeholder (PCI-DSS, GDPR, E-Invoicing)
│   ├── ai_workflows/
│   │   └── execution_log.md
│   ├── plans/
│   │   ├── Master_plan.md                                  # 67 KB comprehensive blueprint
│   │   ├── PLAN_PHASE1.md                                  # 67 KB detailed 24-sprint execution plan
│   │   ├── ER_diagram.md
│   │   ├── UI_UX_Main_Plan.md
│   │   ├── PLAN_COMPAT_VERIFICATION_v1.0.md
│   │   ├── PLAN_CONSOLIDATED_FIX_v1.0.md
│   │   ├── PLAN_MISMATCH_FIX_v1.0.md
│   │   ├── PLAN_NAMESPACE_FIX_v1.0.md
│   │   ├── PLAN_STRUCTURE_CROSSCHECK_v1.0.md
│   │   ├── PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md
│   │   └── [additional planning documents]
│   ├── audit_phase_1_result.md ... audit_phase_4_result.md # Previous v1 audits
│   ├── audit_v2_phase_1_result.md ... audit_v2_phase_4_result.md
│   ├── audit_v3_phase_1_result.md ... audit_v3_final_report.md
│   ├── sprint_progress.md
│   └── system-health-tracker.md
│
├── androidApp/                                             # ── Android Application Shell ──
│   ├── build.gradle.kts
│   ├── proguard-rules.pro                                  # R8/ProGuard rules (13 keep-rule blocks)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/com/zyntasolutions/zyntapos/
│       │   ├── MainActivity.kt
│       │   └── ZyntaApplication.kt
│       └── res/
│           ├── drawable/
│           │   ├── ic_launcher_background.xml
│           │   └── ic_launcher_foreground.xml
│           ├── mipmap-anydpi-v26/
│           │   ├── ic_launcher.xml
│           │   └── ic_launcher_round.xml
│           ├── mipmap-hdpi/ ... mipmap-xxxhdpi/            # Launcher icons (all densities)
│           └── values/
│               └── strings.xml
│
├── composeApp/                                             # ── Compose Multiplatform KMP Root ──
│   ├── build.gradle.kts                                    # Desktop packaging (DMG/MSI/DEB), targets
│   ├── src/
│   │   ├── androidMain/
│   │   │   └── AndroidManifest.xml
│   │   ├── commonMain/
│   │   │   └── kotlin/com/zyntasolutions/zyntapos/
│   │   │       └── App.kt                                  # Root @Composable App()
│   │   └── jvmMain/
│   │       └── kotlin/com/zyntasolutions/zyntapos/
│   │           └── Main.kt                                 # Desktop entry point (fun main)
│   │
│   ├── core/                                               # ── composeApp:core ──
│   │   ├── build.gradle.kts
│   │   └── src/commonMain/kotlin/com/zyntasolutions/zyntapos/ui/core/mvi/
│   │       └── BaseViewModel.kt                            # MVI base: BaseViewModel<S,I,E>
│   │
│   ├── designsystem/                                       # ── composeApp:designsystem ──
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── commonMain/kotlin/com/zyntasolutions/zyntapos/designsystem/
│   │       │   ├── DesignSystemModule.kt                   # Koin module stub
│   │       │   ├── components/
│   │       │   │   ├── ZyntaBadge.kt
│   │       │   │   ├── ZyntaBottomSheet.kt
│   │       │   │   ├── ZyntaButton.kt
│   │       │   │   ├── ZyntaCartItemRow.kt
│   │       │   │   ├── ZyntaCurrencyText.kt
│   │       │   │   ├── ZyntaDatePicker.kt
│   │       │   │   ├── ZyntaDialog.kt
│   │       │   │   ├── ZyntaEmptyState.kt
│   │       │   │   ├── ZyntaInfoCard.kt
│   │       │   │   ├── ZyntaLineChart.kt
│   │       │   │   ├── ZyntaLoadingOverlay.kt
│   │       │   │   ├── ZyntaLoadingSkeleton.kt
│   │       │   │   ├── ZyntaNumericPad.kt
│   │       │   │   ├── ZyntaProductCard.kt
│   │       │   │   ├── ZyntaSearchBar.kt
│   │       │   │   ├── ZyntaSectionHeader.kt
│   │       │   │   ├── ZyntaSettingsItem.kt
│   │       │   │   ├── ZyntaSnackbarHost.kt
│   │       │   │   ├── ZyntaStatCard.kt
│   │       │   │   ├── ZyntaStatusChip.kt
│   │       │   │   ├── ZyntaSyncIndicator.kt
│   │       │   │   ├── ZyntaTable.kt
│   │       │   │   ├── ZyntaTextField.kt
│   │       │   │   └── ZyntaTopAppBar.kt
│   │       │   ├── layouts/
│   │       │   │   ├── ZyntaGrid.kt
│   │       │   │   ├── ZyntaListDetailLayout.kt
│   │       │   │   ├── ZyntaPageScaffold.kt
│   │       │   │   ├── ZyntaScaffold.kt                    # Adaptive nav: Bar / Rail / Drawer
│   │       │   │   └── ZyntaSplitPane.kt
│   │       │   ├── theme/
│   │       │   │   ├── ZyntaColors.kt                      # Light/Dark Material 3 color schemes
│   │       │   │   ├── ZyntaShapes.kt                      # Shape scale (ExtraSmall → ExtraLarge)
│   │       │   │   ├── ZyntaTheme.kt                       # Root theme composable
│   │       │   │   └── ZyntaTypography.kt                  # Type scale
│   │       │   ├── tokens/
│   │       │   │   ├── ZyntaElevation.kt                   # Elevation levels 0–5
│   │       │   │   └── ZyntaSpacing.kt                     # 4dp-based spacing grid
│   │       │   └── util/
│   │       │       ├── PlatformFilePicker.kt               # expect class
│   │       │       └── WindowSizeClassHelper.kt            # COMPACT/MEDIUM/EXPANDED
│   │       ├── commonTest/kotlin/com/zyntasolutions/zyntapos/designsystem/
│   │       │   └── DesignSystemComponentTests.kt
│   │       ├── androidMain/kotlin/com/zyntasolutions/zyntapos/designsystem/
│   │       │   ├── theme/ZyntaTheme.android.kt
│   │       │   └── util/
│   │       │       ├── PlatformFilePicker.android.kt
│   │       │       └── WindowSizeClassHelper.android.kt
│   │       └── jvmMain/kotlin/com/zyntasolutions/zyntapos/designsystem/
│   │           ├── theme/ZyntaTheme.desktop.kt
│   │           └── util/
│   │               ├── PlatformFilePicker.desktop.kt
│   │               └── WindowSizeClassHelper.desktop.kt
│   │
│   ├── navigation/                                         # ── composeApp:navigation ──
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── commonMain/kotlin/com/zyntasolutions/zyntapos/navigation/
│   │       │   ├── NavigationModule.kt
│   │       │   ├── ZyntaNavHost.kt
│   │       │   ├── Screen.kt                               # Type-safe route definitions
│   │       │   └── NavShell.kt                             # Platform-adaptive nav shell
│   │       ├── commonTest/
│   │       ├── androidMain/
│   │       └── jvmMain/
│   │
│   └── feature/                                            # ── Feature Modules (13) ──
│       │
│       ├── auth/                                           # Login, PIN, biometric, auto-lock
│       │   ├── build.gradle.kts
│       │   └── src/
│       │       ├── commonMain/kotlin/com/zyntasolutions/zyntapos/feature/auth/
│       │       │   ├── AuthModule.kt                       # Koin DI
│       │       │   ├── AuthViewModel.kt
│       │       │   ├── SignUpViewModel.kt
│       │       │   ├── guard/
│       │       │   │   ├── RoleGuard.kt
│       │       │   │   └── SessionGuard.kt
│       │       │   ├── mvi/
│       │       │   │   ├── AuthEffect.kt
│       │       │   │   ├── AuthIntent.kt
│       │       │   │   └── AuthState.kt
│       │       │   ├── screen/
│       │       │   │   ├── LoginScreen.kt
│       │       │   │   ├── PinLockScreen.kt
│       │       │   │   └── SignUpScreen.kt
│       │       │   └── session/
│       │       │       └── SessionManager.kt
│       │       └── commonTest/kotlin/com/zyntasolutions/zyntapos/feature/auth/
│       │           ├── AuthViewModelTest.kt
│       │           ├── LoginUseCaseTest.kt
│       │           └── SessionManagerTest.kt
│       │
│       ├── pos/                                            # Product grid, cart, payment, receipt
│       │   ├── README.md                                   # Component wrapper pattern docs
│       │   ├── build.gradle.kts
│       │   └── src/
│       │       ├── commonMain/kotlin/com/zyntasolutions/zyntapos/feature/pos/
│       │       │   ├── PosModule.kt
│       │       │   ├── PosViewModel.kt
│       │       │   ├── mvi/
│       │       │   │   ├── PosEffect.kt
│       │       │   │   ├── PosIntent.kt
│       │       │   │   └── PosState.kt
│       │       │   ├── screen/
│       │       │   │   ├── PosScreen.kt
│       │       │   │   └── [sub-screens]
│       │       │   └── component/
│       │       │       ├── PosSearchBar.kt                 # Wraps ZyntaSearchBar + MVI wiring
│       │       │       └── [POS-specific composables]
│       │       ├── commonTest/
│       │       └── jvmMain/                                # Desktop-specific POS logic
│       │
│       ├── inventory/                                      # Product CRUD, categories, stock
│       │   ├── build.gradle.kts
│       │   └── src/commonMain/
│       │
│       ├── register/                                       # Cash session, EOD Z-report
│       │   ├── build.gradle.kts
│       │   └── src/commonMain/
│       │
│       ├── reports/                                        # Sales, stock, PDF/CSV export
│       │   ├── build.gradle.kts
│       │   └── src/
│       │       ├── commonMain/
│       │       ├── androidMain/
│       │       ├── jvmMain/
│       │       └── commonTest/
│       │
│       ├── settings/                                       # Store profile, printer, security, backup
│       │   ├── build.gradle.kts
│       │   └── src/
│       │       ├── commonMain/
│       │       ├── androidMain/
│       │       ├── jvmMain/
│       │       └── commonTest/
│       │
│       ├── customers/                                      # CRM, loyalty, GDPR export/erase
│       │   ├── build.gradle.kts
│       │   └── src/commonMain/
│       │
│       ├── coupons/                                        # Coupon CRUD, promotion engine
│       │   ├── build.gradle.kts
│       │   └── src/commonMain/
│       │
│       ├── expenses/                                       # Expense recording, P&L reporting
│       │   ├── build.gradle.kts
│       │   └── src/commonMain/
│       │
│       ├── staff/                                          # Employees, scheduling, attendance, payroll
│       │   ├── build.gradle.kts
│       │   └── src/commonMain/
│       │
│       ├── multistore/                                     # Store selector, central dashboard
│       │   ├── build.gradle.kts
│       │   └── src/commonMain/
│       │
│       ├── admin/                                          # System health, audit log, DB maintenance
│       │   ├── build.gradle.kts
│       │   └── src/commonMain/
│       │
│       └── media/                                          # Image picker, crop, compression
│           ├── build.gradle.kts
│           └── src/commonMain/
│
└── shared/                                                 # ── Shared Business Logic (5 tiers) ──
    │
    ├── core/                                               # Tier 1: Pure Kotlin utilities
    │   ├── build.gradle.kts
    │   └── src/
    │       ├── commonMain/kotlin/com/zyntasolutions/zyntapos/core/
    │       │   ├── Platform.kt                             # expect class
    │       │   ├── config/
    │       │   │   └── AppConfig.kt                        # BASE_URL, IS_DEBUG, timeouts
    │       │   ├── di/
    │       │   │   └── CoreModule.kt                       # Koin: Logger, CurrencyFormatter, dispatchers
    │       │   ├── extensions/
    │       │   │   ├── DoubleExtensions.kt
    │       │   │   ├── LongExtensions.kt
    │       │   │   └── StringExtensions.kt
    │       │   ├── health/
    │       │   │   └── SystemHealthTracker.kt              # expect class
    │       │   ├── logger/
    │       │   │   └── ZyntaLogger.kt                      # Kermit-backed logger
    │       │   ├── mvi/                                    # NEEDS CLARIFICATION: Agent reported MVI classes here but only BaseViewModel exists in composeApp:core
    │       │   ├── platform/
    │       │   │   └── AppInfoProvider.kt                  # expect class
    │       │   ├── result/
    │       │   │   ├── Result.kt                           # sealed class Result<T> { Success, Error, Loading }
    │       │   │   └── ZyntaException.kt                   # sealed hierarchy (6 subtypes)
    │       │   └── utils/
    │       │       ├── CurrencyFormatter.kt
    │       │       ├── DateTimeUtils.kt
    │       │       └── IdGenerator.kt
    │       ├── commonTest/kotlin/com/zyntasolutions/zyntapos/core/
    │       │   ├── ResultTest.kt
    │       │   ├── ZyntaExceptionTest.kt
    │       │   ├── CurrencyFormatterTest.kt
    │       │   └── DateTimeUtilsTest.kt
    │       ├── androidMain/kotlin/com/zyntasolutions/zyntapos/core/
    │       │   ├── Platform.android.kt                     # actual
    │       │   ├── health/SystemHealthTracker.android.kt   # actual
    │       │   └── platform/AppInfoProvider.android.kt     # actual
    │       └── jvmMain/kotlin/com/zyntasolutions/zyntapos/core/
    │           ├── Platform.jvm.kt                         # actual
    │           ├── health/SystemHealthTracker.jvm.kt       # actual
    │           └── platform/AppInfoProvider.jvm.kt         # actual
    │
    ├── domain/                                             # Tier 2: Business rules, use cases
    │   ├── build.gradle.kts
    │   └── src/
    │       ├── commonMain/kotlin/com/zyntasolutions/zyntapos/domain/
    │       │   ├── DomainModule.kt                         # Koin placeholder
    │       │   ├── formatter/
    │       │   │   └── ReceiptFormatter.kt
    │       │   ├── model/                                  # 26 domain models (ADR-002: no *Entity suffix)
    │       │   │   ├── AuditEntry.kt
    │       │   │   ├── CartItem.kt
    │       │   │   ├── CashMovement.kt
    │       │   │   ├── CashRegister.kt
    │       │   │   ├── Category.kt
    │       │   │   ├── Customer.kt
    │       │   │   ├── DiscountType.kt
    │       │   │   ├── Order.kt
    │       │   │   ├── OrderItem.kt
    │       │   │   ├── OrderStatus.kt
    │       │   │   ├── OrderTotals.kt
    │       │   │   ├── OrderType.kt
    │       │   │   ├── PaymentMethod.kt
    │       │   │   ├── PaymentSplit.kt
    │       │   │   ├── Permission.kt                       # Enum with rolePermissions matrix
    │       │   │   ├── PrinterPaperWidth.kt
    │       │   │   ├── Product.kt
    │       │   │   ├── ProductVariant.kt
    │       │   │   ├── RegisterSession.kt
    │       │   │   ├── Role.kt                             # Enum: OWNER, ADMIN, MANAGER, CASHIER, STAFF
    │       │   │   ├── StockAdjustment.kt
    │       │   │   ├── Supplier.kt
    │       │   │   ├── SyncOperation.kt
    │       │   │   ├── SyncStatus.kt
    │       │   │   ├── TaxGroup.kt
    │       │   │   ├── UnitOfMeasure.kt
    │       │   │   └── User.kt
    │       │   ├── port/
    │       │   │   ├── PasswordHashPort.kt                 # interface
    │       │   │   ├── SecureStorageKeys.kt
    │       │   │   └── SecureStoragePort.kt                # interface
    │       │   ├── printer/
    │       │   │   ├── ReceiptPrinterPort.kt               # interface
    │       │   │   ├── ReportPrinterPort.kt                # interface
    │       │   │   └── ZReportPrinterPort.kt               # interface
    │       │   ├── repository/                             # 14 repository interfaces
    │       │   │   ├── AuditRepository.kt
    │       │   │   ├── AuthRepository.kt
    │       │   │   ├── CategoryRepository.kt
    │       │   │   ├── CustomerRepository.kt
    │       │   │   ├── OrderRepository.kt
    │       │   │   ├── ProductRepository.kt
    │       │   │   ├── RegisterRepository.kt
    │       │   │   ├── SettingsRepository.kt
    │       │   │   ├── StockRepository.kt
    │       │   │   ├── SupplierRepository.kt
    │       │   │   ├── SyncRepository.kt
    │       │   │   ├── TaxGroupRepository.kt
    │       │   │   ├── UnitGroupRepository.kt
    │       │   │   └── UserRepository.kt
    │       │   └── usecase/
    │       │       ├── auth/
    │       │       │   ├── CheckPermissionUseCase.kt
    │       │       │   ├── LoginUseCase.kt
    │       │       │   ├── LogoutUseCase.kt
    │       │       │   └── ValidatePinUseCase.kt
    │       │       ├── inventory/
    │       │       │   ├── AdjustStockUseCase.kt
    │       │       │   ├── CreateProductUseCase.kt
    │       │       │   ├── DeleteCategoryUseCase.kt
    │       │       │   ├── ManageUnitGroupUseCase.kt
    │       │       │   ├── SaveCategoryUseCase.kt
    │       │       │   ├── SaveSupplierUseCase.kt
    │       │       │   ├── SaveTaxGroupUseCase.kt
    │       │       │   ├── SearchProductsUseCase.kt
    │       │       │   └── UpdateProductUseCase.kt
    │       │       ├── pos/
    │       │       │   ├── AddItemToCartUseCase.kt
    │       │       │   ├── ApplyItemDiscountUseCase.kt
    │       │       │   ├── ApplyOrderDiscountUseCase.kt
    │       │       │   ├── CalculateOrderTotalsUseCase.kt
    │       │       │   ├── HoldOrderUseCase.kt
    │       │       │   ├── PrintReceiptUseCase.kt
    │       │       │   ├── ProcessPaymentUseCase.kt
    │       │       │   ├── RemoveItemFromCartUseCase.kt
    │       │       │   ├── RetrieveHeldOrderUseCase.kt
    │       │       │   ├── UpdateCartItemQuantityUseCase.kt
    │       │       │   └── VoidOrderUseCase.kt
    │       │       ├── register/
    │       │       │   ├── CloseRegisterSessionUseCase.kt
    │       │       │   ├── OpenRegisterSessionUseCase.kt
    │       │       │   ├── PrintZReportUseCase.kt
    │       │       │   └── RecordCashMovementUseCase.kt
    │       │       ├── reports/
    │       │       │   ├── GenerateSalesReportUseCase.kt
    │       │       │   ├── GenerateStockReportUseCase.kt
    │       │       │   └── PrintReportUseCase.kt
    │       │       ├── settings/
    │       │       │   ├── PrintTestPageUseCase.kt
    │       │       │   └── SaveUserUseCase.kt
    │       │       └── validation/
    │       │           └── PaymentValidator.kt
    │       └── commonTest/
    │
    ├── data/                                               # Tier 3: SQLDelight, Ktor, repo impls
    │   ├── build.gradle.kts
    │   └── src/
    │       ├── commonMain/
    │       │   ├── kotlin/com/zyntasolutions/zyntapos/data/
    │       │   │   ├── di/
    │       │   │   │   └── DataModule.kt                   # Koin: DB, repos, API, sync
    │       │   │   ├── local/
    │       │   │   │   ├── SyncEnqueuer.kt
    │       │   │   │   ├── db/
    │       │   │   │   │   ├── DatabaseDriverFactory.kt    # expect class
    │       │   │   │   │   ├── DatabaseFactory.kt          # Orchestrates key + driver + migrations
    │       │   │   │   │   ├── DatabaseKeyProvider.kt      # expect class
    │       │   │   │   │   ├── DatabaseMigrations.kt
    │       │   │   │   │   └── SecurePreferencesKeyMigration.kt
    │       │   │   │   └── mapper/
    │       │   │   │       ├── CategoryMapper.kt
    │       │   │   │       ├── CustomerMapper.kt
    │       │   │   │       ├── OrderMapper.kt
    │       │   │   │       ├── ProductMapper.kt
    │       │   │   │       ├── RegisterMapper.kt
    │       │   │   │       ├── StockMapper.kt
    │       │   │   │       ├── SupplierMapper.kt
    │       │   │   │       ├── SyncOperationMapper.kt
    │       │   │   │       └── UserMapper.kt
    │       │   │   ├── remote/
    │       │   │   │   ├── api/
    │       │   │   │   │   ├── ApiClient.kt                # buildApiClient() — Ktor factory
    │       │   │   │   │   ├── ApiService.kt               # interface
    │       │   │   │   │   └── KtorApiService.kt           # implementation
    │       │   │   │   └── dto/
    │       │   │   │       ├── AuthDto.kt
    │       │   │   │       ├── OrderDto.kt
    │       │   │   │       ├── ProductDto.kt
    │       │   │   │       └── SyncDto.kt
    │       │   │   ├── repository/                         # 14 repository implementations
    │       │   │   │   ├── AuditRepositoryImpl.kt
    │       │   │   │   ├── AuthRepositoryImpl.kt
    │       │   │   │   ├── CategoryRepositoryImpl.kt
    │       │   │   │   ├── CustomerRepositoryImpl.kt
    │       │   │   │   ├── OrderRepositoryImpl.kt
    │       │   │   │   ├── ProductRepositoryImpl.kt
    │       │   │   │   ├── RegisterRepositoryImpl.kt
    │       │   │   │   ├── SettingsRepositoryImpl.kt
    │       │   │   │   ├── StockRepositoryImpl.kt
    │       │   │   │   ├── SupplierRepositoryImpl.kt
    │       │   │   │   ├── SyncRepositoryImpl.kt
    │       │   │   │   ├── TaxGroupRepositoryImpl.kt
    │       │   │   │   ├── UnitGroupRepositoryImpl.kt
    │       │   │   │   └── UserRepositoryImpl.kt
    │       │   │   └── sync/
    │       │   │       ├── NetworkMonitor.kt               # expect class
    │       │   │       └── SyncEngine.kt                   # Offline-first push/pull coordinator
    │       │   └── sqldelight/com/zyntasolutions/zyntapos/db/
    │       │       ├── audit_log.sq
    │       │       ├── categories.sq
    │       │       ├── customers.sq
    │       │       ├── orders.sq
    │       │       ├── products.sq
    │       │       ├── registers.sq
    │       │       ├── settings.sq
    │       │       ├── stock.sq
    │       │       ├── suppliers.sq
    │       │       ├── sync_queue.sq
    │       │       ├── tax_groups.sq
    │       │       ├── units_of_measure.sq
    │       │       └── users.sq
    │       ├── androidMain/kotlin/com/zyntasolutions/zyntapos/data/
    │       │   ├── di/AndroidDataModule.kt                 # Platform bindings
    │       │   ├── local/db/
    │       │   │   ├── DatabaseDriverFactory.kt            # actual (Android SQLite)
    │       │   │   └── DatabaseKeyProvider.kt              # actual (Android Keystore)
    │       │   └── sync/
    │       │       ├── NetworkMonitor.kt                   # actual (ConnectivityManager)
    │       │       └── SyncWorker.kt                       # WorkManager CoroutineWorker
    │       ├── jvmMain/kotlin/com/zyntasolutions/zyntapos/data/
    │       │   ├── di/DesktopDataModule.kt                 # Platform bindings
    │       │   ├── local/db/
    │       │   │   ├── DatabaseDriverFactory.kt            # actual (JVM SQLite)
    │       │   │   └── DatabaseKeyProvider.kt              # actual (PKCS12 Keystore)
    │       │   └── sync/
    │       │       └── NetworkMonitor.kt                   # actual (InetAddress check)
    │       ├── commonTest/kotlin/com/zyntasolutions/zyntapos/data/
    │       │   └── remote/ApiServiceTest.kt
    │       └── jvmTest/kotlin/com/zyntasolutions/zyntapos/data/
    │           ├── ProductRepositoryImplTest.kt
    │           ├── TestDatabase.kt
    │           ├── repository/
    │           │   ├── ProductRepositoryIntegrationTest.kt
    │           │   └── SyncRepositoryIntegrationTest.kt
    │           └── sync/
    │               ├── InMemorySecurePreferences.kt
    │               └── SyncEngineIntegrationTest.kt
    │
    ├── hal/                                                # Tier 4: Hardware Abstraction Layer
    │   ├── build.gradle.kts
    │   └── src/
    │       ├── commonMain/kotlin/com/zyntasolutions/zyntapos/hal/
    │       │   ├── di/HalModule.kt
    │       │   ├── printer/ThermalPrinter.kt               # expect interface
    │       │   ├── scanner/BarcodeScanner.kt               # expect interface
    │       │   ├── cashdrawer/CashDrawer.kt                # expect interface
    │       │   └── display/CustomerDisplay.kt              # expect interface
    │       ├── androidMain/kotlin/com/zyntasolutions/zyntapos/hal/
    │       │   ├── di/AndroidHalModule.kt
    │       │   ├── printer/                                # USB, Bluetooth, Network impls
    │       │   ├── scanner/                                # Camera (ML Kit), USB HID, BT SPP
    │       │   └── cashdrawer/                             # Printer kick command
    │       └── jvmMain/kotlin/com/zyntasolutions/zyntapos/hal/
    │           ├── di/DesktopHalModule.kt
    │           ├── printer/                                # USB (libusb4j), Serial (jSerialComm), TCP
    │           ├── scanner/                                # USB HID, Serial
    │           └── cashdrawer/                             # Printer cmd / Serial
    │
    └── security/                                           # Tier 5: AES-256, Keystore, JWT, RBAC
        ├── build.gradle.kts
        └── src/
            ├── commonMain/kotlin/com/zyntasolutions/zyntapos/security/
            │   ├── di/
            │   │   └── SecurityModule.kt                   # Koin: crypto, auth, RBAC
            │   ├── crypto/
            │   │   ├── EncryptionManager.kt                # expect class — AES-256-GCM
            │   │   └── DatabaseKeyManager.kt               # expect class — DEK management
            │   ├── auth/
            │   │   ├── JwtManager.kt                       # Base64url decode, token persistence
            │   │   ├── PasswordHasher.kt                   # expect interface
            │   │   ├── PasswordHasherAdapter.kt            # Implements PasswordHashPort
            │   │   ├── PinManager.kt                       # SHA-256 + 16-byte salt
            │   │   ├── SecureRandomBytes.kt                # expect fun
            │   │   └── Sha256.kt                           # expect fun
            │   ├── prefs/
            │   │   ├── SecurePreferences.kt                # expect class (implements TokenStorage)
            │   │   ├── SecurePreferencesKeys.kt            # Well-known key constants
            │   │   └── TokenStorage.kt                     # interface
            │   ├── rbac/
            │   │   └── RbacEngine.kt                       # Stateless RBAC evaluator
            │   └── audit/
            │       └── SecurityAuditLogger.kt              # Append-only audit events
            ├── commonTest/kotlin/com/zyntasolutions/zyntapos/security/
            │   └── [security unit tests]
            ├── androidMain/kotlin/com/zyntasolutions/zyntapos/security/
            │   ├── crypto/
            │   │   ├── EncryptionManager.android.kt        # actual: Android Keystore AES-256-GCM
            │   │   └── DatabaseKeyManager.android.kt       # actual: Envelope-encrypted DEK
            │   ├── auth/
            │   │   ├── PasswordHasher.android.kt           # actual: BCrypt via jBCrypt
            │   │   ├── SecureRandomBytes.android.kt        # actual: java.security.SecureRandom
            │   │   └── Sha256.android.kt                   # actual: MessageDigest
            │   └── prefs/
            │       └── SecurePreferences.android.kt        # actual: EncryptedSharedPreferences
            └── jvmMain/kotlin/com/zyntasolutions/zyntapos/security/
                ├── crypto/
                │   ├── EncryptionManager.jvm.kt            # actual: JCE + PKCS12 KeyStore
                │   └── DatabaseKeyManager.jvm.kt           # actual: PKCS12 file (~/.zentapos/)
                ├── auth/
                │   ├── PasswordHasher.jvm.kt               # actual: jBCrypt
                │   ├── SecureRandomBytes.jvm.kt            # actual: java.security.SecureRandom
                │   └── Sha256.jvm.kt                       # actual: MessageDigest
                └── prefs/
                    └── SecurePreferences.jvm.kt            # actual: AES-encrypted Properties file
```

### Source Set Summary

| Source Set    | Count | Description                                |
|---------------|-------|--------------------------------------------|
| `commonMain`  | 23    | Shared Kotlin/Multiplatform (all targets)  |
| `androidMain` | 8     | Android-specific `actual` implementations  |
| `jvmMain`     | 8     | Desktop JVM-specific `actual` implementations |
| `commonTest`  | 10    | Shared unit tests (kotlin-test, Mockative) |
| `jvmTest`     | 2     | JVM-only integration tests                 |
| **Total**     | **51**| —                                          |

### Build Configuration Files

| File | Location | Purpose |
|------|----------|---------|
| `build.gradle.kts` (root) | `/build.gradle.kts` | Detekt plugin, version properties, root plugins |
| `settings.gradle.kts` | `/settings.gradle.kts` | 23-module registry, `TYPESAFE_PROJECT_ACCESSORS`, repository management, KSP resolution strategy override |
| `gradle.properties` | `/gradle.properties` | JVM args, Android flags, Compose flags |
| `libs.versions.toml` | `/gradle/libs.versions.toml` | Version catalog (~80 deps, 5 bundles, 12 plugins) |
| `version.properties` | `/version.properties` | Semantic version metadata (Major/Minor/Patch/Label/Build) |
| `gradle-daemon-jvm.properties` | `/gradle/gradle-daemon-jvm.properties` | Daemon JVM config |
| `gradle-wrapper.properties` | `/gradle/wrapper/gradle-wrapper.properties` | Gradle 8.13.2 distribution URL |
| `local.properties.template` | `/local.properties.template` | SDK path template for contributors |
| Module `build.gradle.kts` | 22 files across modules | KMP targets, dependencies, platform config |

### CI/CD Configuration Files

| File | Path | Triggers | Jobs |
|------|------|----------|------|
| `ci.yml` | `.github/workflows/ci.yml` | Push `main`/`develop`, PRs to `main` | Build shared (JVM), Build Android APK, Build Desktop JAR, Lint + Detekt, Run JVM tests |
| `release.yml` | `.github/workflows/release.yml` | Push to `main` | 4 parallel: Android Release APK, macOS DMG, Windows MSI, Linux DEB → GitHub Release |

### ProGuard/R8 Rules

| File | Path | Keep Rules |
|------|------|------------|
| `proguard-rules.pro` | `androidApp/proguard-rules.pro` | 13 rule blocks: kotlinx-serialization `@Serializable` + generated serializers, Koin DI reflective instantiation + ViewModel constructors, SQLDelight generated classes, SQLCipher native library, Ktor HTTP transport, Kotlin/KMP metadata, kotlinx-coroutines dispatcher factories, kotlinx-datetime, Compose runtime stability, Android Keystore security APIs, domain models serialization |

### Desktop Packaging Configuration

**File:** `composeApp/build.gradle.kts` (lines 96–143)

| Format | Target OS | Key Options |
|--------|-----------|-------------|
| DMG    | macOS     | Standard DMG |
| MSI    | Windows   | Directory chooser, per-user/all-users, shortcuts, upgrade UUID `b2f8c3a1-7d4e-4f5a-9b6c-1e2d3f4a5b6c` |
| DEB    | Linux     | Maintainer `dev@zyntapos.com`, Category `Office`, Menu group `Office` |

**JVM runtime args:** `-Xmx512m`, `-Dfile.encoding=UTF-8`, `-Dapp.version=…`, `-Dapp.build.number=…`, `-Dapp.build.date=…`

### Static Analysis

| Tool | Config Path | Notes |
|------|-------------|-------|
| Detekt | `config/detekt/detekt.yml` | Applied via root `build.gradle.kts` |

---

## 1B. DOCS INDEX

| # | Document | Absolute Path | Status | Purpose & Scope | Key Claims (Architecture, Platforms, Dependencies, MVI Flow) |
|---|----------|---------------|--------|-----------------|--------------------------------------------------------------|
| 1 | `README.md` | `/home/user/ZyntaPOS-KMM/README.md` | Complete (11.3 KB) | Executive project overview, quick-start guide, module map | **Arch:** Clean Architecture (UI → Domain ← Data), strict layer separation. **Platforms:** Android minSdk 24 + Desktop JVM (macOS/Windows/Linux). **MVI:** Unidirectional flow; every screen has State/Intent/Effect; canonical BaseViewModel in `:composeApp:core`. **DI:** Koin 4.0+ single cross-platform graph; platform bindings in `androidMain`/`jvmMain`. **HAL:** All hardware I/O behind interfaces; business logic never imports USB/socket directly. **Data:** SQLDelight 2.0+ with SQLCipher AES-256; Ktor 3.0+; offline-first with sync engine. **Security:** AES-256 at rest; platform keystores (Android Keystore / JCE KeyStore). |
| 2 | `CONTRIBUTING.md` | `/home/user/ZyntaPOS-KMM/CONTRIBUTING.md` | Complete (4.3 KB) | Architecture conventions and coding standards | **Arch:** Strict inbound dependency flow `feature → domain ← data`; domain must never import data/composeApp/platform SDKs. **Naming:** Plain domain names per ADR-002 (no `*Entity`); DB types use `*Entity`/`*Table`/`*Row`. **MVI:** `UiState` (immutable data class), `UiIntent` (sealed class), `ViewModel` exposes `StateFlow<UiState>`. **DI:** Feature modules declare in `di/<feature>Module.kt`; never `GlobalContext.get()` outside bootstrap. **Coroutines:** `StateFlow` for UI state, `SharedFlow` for one-shot events; repos return `Flow<T>` or `Result<T>`. **Testing:** Use Cases 95%, Repositories 80%, ViewModels 80% coverage targets. |
| 3 | `ADR-001-ViewModelBaseClass.md` | `/home/user/ZyntaPOS-KMM/docs/adr/ADR-001-ViewModelBaseClass.md` | ACCEPTED (2026-02-21) | Canonical ViewModel base class policy | All VMs MUST extend `com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel`. Direct extension of `androidx.lifecycle.ViewModel` PROHIBITED in feature modules. Provides `updateState{}`, `sendEffect()`, `viewModelScope`, buffered effect Channel. Enforcement: code review gate + future lint rule. |
| 4 | `ADR-002-DomainModelNaming.md` | `/home/user/ZyntaPOS-KMM/docs/adr/ADR-002-DomainModelNaming.md` | ACCEPTED — Option B (2026-02-21) | Domain model naming convention | Domain models use plain, ubiquitous-language names (26 files). `*Entity` suffix PROHIBITED in `shared/domain/`; reserved for persistence layer `shared/data/`. Decision: Dilanka (Tech Lead). |
| 5 | `ADR-003-SecurePreferences-Consolidation.md` | `/home/user/ZyntaPOS-KMM/docs/adr/ADR-003-SecurePreferences-Consolidation.md` | ACCEPTED (2026-02-21) | Single source of truth for encrypted storage | Canonical location: `security.prefs.SecurePreferences` (expect class) in `:shared:security`. Removed duplicate from `:shared:data`. Removed adapter shim classes. `SecurePreferences` implements `TokenStorage`. |
| 6 | `ADR-004-keystore-token-scaffold-removal.md` | `/home/user/ZyntaPOS-KMM/docs/adr/ADR-004-keystore-token-scaffold-removal.md` | ACCEPTED (2026-02-22) | Scaffold directory cleanup rationale | Removed empty `security/keystore/`, `security/token/` (all source sets). `keystore/` absorbed into `EncryptionManager` + `DatabaseKeyManager`. `token/` split across `TokenStorage`, `SecurePreferences`, `JwtManager`. Future: create `KeystoreProvider` only if key rotation/hardware modules needed. |
| 7 | `Master_plan.md` | `/home/user/ZyntaPOS-KMM/docs/plans/Master_plan.md` | APPROVED (67.4 KB) | Comprehensive architectural blueprint (1,200+ lines) | **Arch:** Clean Architecture 3-layer + HAL. 17 functional domains, 87+ feature groups, 450+ features. **Platforms:** Android minSdk 24 + JVM Desktop. **MVI:** `BaseViewModel<State, Intent, Effect>`, unidirectional flow. **DI:** Koin 4.0+ single graph; CoreModule, DataModule (+ android/desktop), SecurityModule, HalModule (+ android/desktop), feature modules. Enforces `named("deviceId")` qualifier. **Data:** SQLDelight + SQLCipher AES-256-CBC; column-level AES-256-GCM; TLS 1.3 + cert pinning. **Sync:** Offline-first; CRDT conflict resolution (LWW, OR-Set, PN-Counter); priority P0–P3; exponential backoff. **RBAC:** Module/Feature/Data level permissions; `Permission.rolePermissions` matrix. **Phases:** Phase 1 (MVP, months 1–6), Phase 2 (Growth, 7–12), Phase 3 (Enterprise, 13–18). **Compliance:** PCI-DSS SAQ-B, GDPR, Sri Lanka E-Invoicing 2026. |
| 8 | `PLAN_PHASE1.md` | `/home/user/ZyntaPOS-KMM/docs/plans/PLAN_PHASE1.md` | READY (67.2 KB) | Detailed 24-sprint Phase 1 execution plan | Sprint-by-sprint breakdown: Sprints 1–2 (scaffold + core), 3–4 (domain), 5–6 (data), 7 (HAL), 8 (security), 9–10 (designsystem), 11 (navigation), 12–13 (auth), 14–17 (POS), 18–19 (inventory), 20–21 (register), 22 (reports), 23 (settings), 24 (integration/QA). DoD: code review, 80%+ coverage for shared, integration tests, manual Android + Desktop testing, no critical lint violations. |
| 9 | `ER_diagram.md` | `/home/user/ZyntaPOS-KMM/docs/plans/ER_diagram.md` | Complete | Entity-relationship diagram for 40+ tables | Documents all SQLDelight tables, relationships, and constraints. |
| 10 | `UI_UX_Main_Plan.md` | `/home/user/ZyntaPOS-KMM/docs/plans/UI_UX_Main_Plan.md` | Complete | UI/UX design system and screen specifications | Responsive layouts, Material 3 tokens, screen wireframes, navigation flows. |
| 11 | POS Feature `README.md` | `/home/user/ZyntaPOS-KMM/composeApp/feature/pos/README.md` | Complete (2.5 KB) | POS feature implementation patterns | Component wrapper pattern (e.g., `PosSearchBar` wraps `ZyntaSearchBar`). MVI intent wiring: raw lambdas → typed `PosIntent`. Debounce in VM (300ms), not composable. Desktop keyboard shortcuts (F2 → search). Maintenance rules: never duplicate UI logic; keep wrappers stateless; decompose if >60 lines. |
| 12 | `docs/architecture/README.md` | `/home/user/ZyntaPOS-KMM/docs/architecture/README.md` | **Placeholder** | Planned: diagrams, dependency maps, sync strategy, security model | Empty stub — content not yet written. |
| 13 | `docs/api/README.md` | `/home/user/ZyntaPOS-KMM/docs/api/README.md` | **Placeholder** | Planned: KDoc API reference, REST endpoint contracts | Empty stub — content not yet written. |
| 14 | `docs/compliance/README.md` | `/home/user/ZyntaPOS-KMM/docs/compliance/README.md` | **Placeholder** | Planned: PCI-DSS, GDPR, E-Invoicing checklists | Empty stub — content not yet written. |
| 15 | `docs/sprint_progress.md` | `/home/user/ZyntaPOS-KMM/docs/sprint_progress.md` | Active | Sprint tracking & progress | Sprint-by-sprint implementation status. |
| 16 | `docs/system-health-tracker.md` | `/home/user/ZyntaPOS-KMM/docs/system-health-tracker.md` | Active | System health monitoring docs | Documents SystemHealthTracker expect/actual architecture. |
| 17 | `docs/ai_workflows/execution_log.md` | `/home/user/ZyntaPOS-KMM/docs/ai_workflows/execution_log.md` | Active | AI-assisted development execution log | Tracks AI-generated code contributions. |

### Previous Audit Reports (for reference)

| Series | Files | Path Pattern |
|--------|-------|--------------|
| v1 | Phase 1–4 | `docs/audit_phase_{1-4}_result.md` |
| v2 | Phase 1–4 | `docs/audit_v2_phase_{1-4}_result.md` |
| v3 | Phase 1–4 + Final | `docs/audit_v3_phase_{1-4}_result.md`, `docs/audit_v3_final_report.md` |

---

## 1C. SHARED COMPONENT CATALOG

### UI & Design System

**Module:** `:composeApp:designsystem`
**Package:** `com.zyntasolutions.zyntapos.designsystem`

#### Theme & Design Tokens

| Component | File | Source Set | Description |
|-----------|------|-----------|-------------|
| `ZyntaTheme` | `theme/ZyntaTheme.kt` | commonMain | Root Material 3 theme composable; supports `ThemeMode` enum (`Light`, `Dark`, `System`); provides `LocalThemeMode` CompositionLocal |
| `ZyntaTheme.android.kt` | `theme/ZyntaTheme.android.kt` | androidMain | Android-specific dynamic color support |
| `ZyntaTheme.desktop.kt` | `theme/ZyntaTheme.desktop.kt` | jvmMain | Desktop-specific theme adjustments |
| `zentaLightColorScheme()` / `zentaDarkColorScheme()` | `theme/ZyntaColors.kt` | commonMain | Material 3 color schemes. Seed: Primary Blue `#1565C0`, Secondary Amber `#F57C00`, Tertiary Green `#2E7D32`, Error Red `#C62828` |
| `ZyntaTypography` | `theme/ZyntaTypography.kt` | commonMain | Material 3 type scale (display → label) using system sans-serif |
| `ZyntaShapes` | `theme/ZyntaShapes.kt` | commonMain | Shape scale: ExtraSmall(4dp), Small(8dp), Medium(12dp), Large(16dp), ExtraLarge(28dp); includes `ShapeFull`, `ShapeNone` |
| `ZyntaSpacingTokens` / `ZyntaSpacing` | `tokens/ZyntaSpacing.kt` | commonMain | 4dp-based grid: xs(4), sm(8), md(16), lg(24), xl(32), xxl(48); POS touch targets: `touchMin(48dp)`, `touchPreferred(56dp)`. Exposed via `LocalSpacing` CompositionLocal |
| `ZyntaElevation` | `tokens/ZyntaElevation.kt` | commonMain | Material 3 elevation levels 0–5dp |

#### Reusable Components (24 components)

| Component | File | Source Set | Description |
|-----------|------|-----------|-------------|
| `ZyntaButton` | `components/ZyntaButton.kt` | commonMain | 5 variants (`Primary`, `Secondary`, `Danger`, `Ghost`, `Icon`); 3 sizes (`Small`/32dp, `Medium`/40dp, `Large`/56dp); loading state; enums: `ZyntaButtonVariant`, `ZyntaButtonSize` |
| `ZyntaTextField` | `components/ZyntaTextField.kt` | commonMain | Outlined text field; error state; leading/trailing icons; keyboard options; visual transformation support |
| `ZyntaDialog` | `components/ZyntaDialog.kt` | commonMain | `ZyntaDialogVariant` sealed class: `Confirm`, `Alert`, `Input`; `ZyntaDialogContent` composable; all stateless |
| `ZyntaSearchBar` | `components/ZyntaSearchBar.kt` | commonMain | Material 3 search field; leading/trailing action slots |
| `ZyntaBadge` | `components/ZyntaBadge.kt` | commonMain | Badge/label for status/category display |
| `ZyntaTopAppBar` | `components/ZyntaTopAppBar.kt` | commonMain | `CenterAlignedTopAppBar` wrapper with ZyntaPOS styling |
| `ZyntaSnackbarHost` | `components/ZyntaSnackbarHost.kt` | commonMain | Material 3 snackbar host with theme integration |
| `ZyntaBottomSheet` | `components/ZyntaBottomSheet.kt` | commonMain | Material 3 modal bottom sheet |
| `ZyntaLoadingOverlay` | `components/ZyntaLoadingOverlay.kt` | commonMain | Full-screen loading indicator with scrim overlay |
| `ZyntaLoadingSkeleton` | `components/ZyntaLoadingSkeleton.kt` | commonMain | Shimmer-animated placeholder for content loading |
| `ZyntaDatePicker` | `components/ZyntaDatePicker.kt` | commonMain | Material 3 date picker wrapper |
| `ZyntaEmptyState` | `components/ZyntaEmptyState.kt` | commonMain | Empty state with icon + message + optional CTA |
| `ZyntaCurrencyText` | `components/ZyntaCurrencyText.kt` | commonMain | Formatted currency display |
| `ZyntaStatusChip` | `components/ZyntaStatusChip.kt` | commonMain | Color-coded status indicator chip |
| `ZyntaSyncIndicator` | `components/ZyntaSyncIndicator.kt` | commonMain | Visual sync progress/status indicator |
| `ZyntaNumericPad` | `components/ZyntaNumericPad.kt` | commonMain | POS numeric keypad for PIN/quantity/price entry |
| `ZyntaProductCard` | `components/ZyntaProductCard.kt` | commonMain | Product card with image, name, price, stock level |
| `ZyntaCartItemRow` | `components/ZyntaCartItemRow.kt` | commonMain | Cart line item with quantity +/- controls |
| `ZyntaInfoCard` | `components/ZyntaInfoCard.kt` | commonMain | Informational card with title, icon, content |
| `ZyntaStatCard` | `components/ZyntaStatCard.kt` | commonMain | Dashboard stat card with large metric display |
| `ZyntaLineChart` | `components/ZyntaLineChart.kt` | commonMain | Line chart for reports/dashboard visualization |
| `ZyntaSectionHeader` | `components/ZyntaSectionHeader.kt` | commonMain | Section header with divider separator |
| `ZyntaSettingsItem` | `components/ZyntaSettingsItem.kt` | commonMain | Settings list item with toggle/switch/arrow |
| `ZyntaTable` | `components/ZyntaTable.kt` | commonMain | Data table for tabular data display |

#### Layout Components (5 layouts)

| Component | File | Source Set | Description |
|-----------|------|-----------|-------------|
| `ZyntaScaffold` | `layouts/ZyntaScaffold.kt` | commonMain | Adaptive navigation scaffold: `NavigationBar` (COMPACT), `NavigationRail` (MEDIUM), `PermanentNavigationDrawer` (EXPANDED). Uses `ZyntaNavItem` data class |
| `ZyntaPageScaffold` | `layouts/ZyntaPageScaffold.kt` | commonMain | Page-level scaffold with standard padding/structure |
| `ZyntaGrid` | `layouts/ZyntaGrid.kt` | commonMain | Responsive grid layout for product/item grids |
| `ZyntaListDetailLayout` | `layouts/ZyntaListDetailLayout.kt` | commonMain | List-detail adaptive layout (sidebar + detail) |
| `ZyntaSplitPane` | `layouts/ZyntaSplitPane.kt` | commonMain | Resizable split pane for desktop layouts |

#### Utility Components

| Component | File | Source Set | Platform |
|-----------|------|-----------|----------|
| `WindowSize` enum + `currentWindowSize()` | `util/WindowSizeClassHelper.kt` | commonMain (expect) | `COMPACT`/`MEDIUM`/`EXPANDED` breakpoints |
| `WindowSizeClassHelper.android.kt` | `util/WindowSizeClassHelper.android.kt` | androidMain (actual) | Android `Configuration` based |
| `WindowSizeClassHelper.desktop.kt` | `util/WindowSizeClassHelper.desktop.kt` | jvmMain (actual) | Window size polling |
| `PlatformFilePicker` | `util/PlatformFilePicker.kt` | commonMain (expect) | Cross-platform file picker |
| `PlatformFilePicker.android.kt` | `util/PlatformFilePicker.android.kt` | androidMain (actual) | Android intent-based |
| `PlatformFilePicker.desktop.kt` | `util/PlatformFilePicker.desktop.kt` | jvmMain (actual) | AWT FileDialog / JFileChooser |

#### Koin Module

| Module | File | Bindings |
|--------|------|----------|
| `DesignSystemModule` | `DesignSystemModule.kt` | Placeholder — no bindings yet |

---

### Security

**Module:** `:shared:security`
**Package:** `com.zyntasolutions.zyntapos.security`

#### Encryption & Key Management

| Component | File | Source Set | Description |
|-----------|------|-----------|-------------|
| `EncryptionManager` (expect class) | `crypto/EncryptionManager.kt` | commonMain | AES-256-GCM encryption/decryption facade. Returns `EncryptedData` (ciphertext + IV + tag) |
| `EncryptionManager` (actual) | `crypto/EncryptionManager.android.kt` | androidMain | Android Keystore-backed AES-256-GCM key |
| `EncryptionManager` (actual) | `crypto/EncryptionManager.jvm.kt` | jvmMain | JCE + PKCS12 KeyStore |
| `DatabaseKeyManager` (expect class) | `crypto/DatabaseKeyManager.kt` | commonMain | 256-bit DEK management for SQLCipher |
| `DatabaseKeyManager` (actual) | `crypto/DatabaseKeyManager.android.kt` | androidMain | Envelope encryption: KEK in Android Keystore wraps DEK |
| `DatabaseKeyManager` (actual) | `crypto/DatabaseKeyManager.jvm.kt` | jvmMain | AES key in PKCS12 file at `~/.zentapos/.db_keystore.p12` |

#### Authentication & Hashing

| Component | File | Source Set | Description |
|-----------|------|-----------|-------------|
| `JwtManager` | `auth/JwtManager.kt` | commonMain | Base64url JWT decode (no sig verification); token CRUD in `SecurePreferences`; extracts `JwtClaims` (userId, role, expiry) |
| `PasswordHasher` (expect interface) | `auth/PasswordHasher.kt` | commonMain | `hash(password): String` / `verify(password, hash): Boolean` |
| `PasswordHasher` (actual) | `auth/PasswordHasher.android.kt` | androidMain | BCrypt via jBCrypt |
| `PasswordHasher` (actual) | `auth/PasswordHasher.jvm.kt` | jvmMain | jBCrypt |
| `PasswordHasherAdapter` | `auth/PasswordHasherAdapter.kt` | commonMain | Adapter bridging `PasswordHasher` → `PasswordHashPort` (domain port) |
| `PinManager` | `auth/PinManager.kt` | commonMain | SHA-256 + 16-byte random salt; format `base64url-salt:hex-hash`; stateless object |
| `secureRandomBytes()` (expect fun) | `auth/SecureRandomBytes.kt` | commonMain | Cryptographic random byte array generation |
| `sha256()` (expect fun) | `auth/Sha256.kt` | commonMain | SHA-256 hash function |

#### Secure Storage

| Component | File | Source Set | Description |
|-----------|------|-----------|-------------|
| `SecurePreferences` (expect class) | `prefs/SecurePreferences.kt` | commonMain | Encrypted key-value store; implements `TokenStorage` interface (ADR-003) |
| `SecurePreferences` (actual) | `prefs/SecurePreferences.android.kt` | androidMain | `EncryptedSharedPreferences` (AndroidX Security Crypto) |
| `SecurePreferences` (actual) | `prefs/SecurePreferences.jvm.kt` | jvmMain | AES-encrypted `Properties` file |
| `TokenStorage` (interface) | `prefs/TokenStorage.kt` | commonMain | Contract: `put(key, value)`, `get(key): String?`, `remove(key)`, `clear()` |
| `SecurePreferencesKeys` | `prefs/SecurePreferencesKeys.kt` | commonMain | Well-known key constants for JWT, user IDs, session data |

#### RBAC & Audit

| Component | File | Source Set | Description |
|-----------|------|-----------|-------------|
| `RbacEngine` | `rbac/RbacEngine.kt` | commonMain | Stateless RBAC evaluator. Methods: `hasPermission(user/role, Permission): Boolean`, `getPermissions(role): Set<Permission>`, `getDeniedPermissions(role): Set<Permission>`. Evaluates `Permission.rolePermissions` static matrix |
| `SecurityAuditLogger` | `audit/SecurityAuditLogger.kt` | commonMain | Writes security events to `AuditRepository` (append-only log). Requires `AuditRepository` + `deviceId: String` (named Koin qualifier) |

---

### Core, Network & MVI

#### MVI Base Classes

**Module:** `:composeApp:core`
**Package:** `com.zyntasolutions.zyntapos.ui.core.mvi`

| Component | File | Source Set | Signature | Description |
|-----------|------|-----------|-----------|-------------|
| `BaseViewModel<S, I, E>` | `BaseViewModel.kt` | commonMain | `abstract class BaseViewModel<S, I, E>(initialState: S) : ViewModel()` | Generic MVI base. **State:** `MutableStateFlow` + `updateState { copy(…) }` (atomic CAS). **Effects:** `Channel<E>(BUFFERED)` + `sendEffect(effect)` (exactly-once delivery). **Intent dispatch:** `dispatch(intent)` → launches `handleIntent(intent)` in `viewModelScope`. Concrete VMs override `suspend fun handleIntent(intent: I)`. Per ADR-001, ALL feature VMs MUST extend this. |

#### Result & Exception Types

**Module:** `:shared:core`
**Package:** `com.zyntasolutions.zyntapos.core.result`

| Component | File | Source Set | Description |
|-----------|------|-----------|-------------|
| `Result<T>` | `result/Result.kt` | commonMain | `sealed class Result<out T>` with 3 variants: `Success<T>(data)`, `Error(exception: ZyntaException, cause: Throwable?)`, `Loading`. Extensions: `onSuccess{}`, `onError{}`, `onLoading{}`, `mapSuccess{}`, `getOrNull()`, `getOrDefault()`, `getOrThrow()` |
| `ZyntaException` | `result/ZyntaException.kt` | commonMain | `sealed class ZyntaException(message, cause?)`. 6 subtypes: `NetworkException(statusCode?)` — `isClientError`/`isServerError` computed props; `DatabaseException(operation)`; `AuthException(reason: AuthFailureReason)` — enum: `INVALID_CREDENTIALS`, `SESSION_EXPIRED`, `ACCOUNT_DISABLED`, `OFFLINE_NO_CACHE`, `TOO_MANY_ATTEMPTS`; `ValidationException(field, rule)`; `HalException(device)`; `SyncException(operationId?, retryCount)` — `MAX_RETRIES=5`, `isMaxRetriesReached` |

#### Logger

| Component | File | Source Set | Description |
|-----------|------|-----------|-------------|
| `ZyntaLogger` | `logger/ZyntaLogger.kt` | commonMain | Kermit-backed logger; `v()`/`d()`/`i()`/`w()`/`e()` methods; `forModule(tag)` creates tagged sub-logger |

#### Utilities

| Component | File | Source Set | Description |
|-----------|------|-----------|-------------|
| `CurrencyFormatter` | `utils/CurrencyFormatter.kt` | commonMain | Locale/currency-aware currency formatting |
| `DateTimeUtils` | `utils/DateTimeUtils.kt` | commonMain | Date/time parsing and formatting functions (kotlinx-datetime) |
| `IdGenerator` | `utils/IdGenerator.kt` | commonMain | UUID/ID generation utilities |
| String extensions | `extensions/StringExtensions.kt` | commonMain | String manipulation helpers |
| Double extensions | `extensions/DoubleExtensions.kt` | commonMain | Numeric formatting helpers |
| Long extensions | `extensions/LongExtensions.kt` | commonMain | Timestamp/Long formatting helpers |

#### Platform Abstractions

| Component | File | Source Set | Description |
|-----------|------|-----------|-------------|
| `Platform` (expect) | `Platform.kt` | commonMain | Platform identification |
| `AppInfoProvider` (expect) | `platform/AppInfoProvider.kt` | commonMain | Build metadata: versionName, versionCode, buildType, appName |
| `SystemHealthTracker` (expect) | `health/SystemHealthTracker.kt` | commonMain | System diagnostics: memory, storage, connectivity |
| `AppConfig` | `config/AppConfig.kt` | commonMain | Central config constants: BASE_URL, IS_DEBUG, timeouts |

#### Ktor API Client

**Module:** `:shared:data`
**Package:** `com.zyntasolutions.zyntapos.data.remote.api`

| Component | File | Source Set | Description |
|-----------|------|-----------|-------------|
| `buildApiClient(prefs)` | `api/ApiClient.kt` | commonMain | Ktor `HttpClient` factory. Plugins: `ContentNegotiation` (kotlinx.serialization JSON), `Auth` (Bearer token from `SecurePreferences`), `HttpTimeout` (connect 10s / request 30s / socket 30s), `HttpRequestRetry` (3 attempts, exponential backoff 1s/2s/4s), `Logging` (Kermit, DEBUG only) |
| `ApiService` (interface) | `api/ApiService.kt` | commonMain | HTTP endpoint contract |
| `KtorApiService` | `api/KtorApiService.kt` | commonMain | `ApiService` implementation using Ktor HttpClient |

#### DTOs (Data Transfer Objects)

| DTO Group | File | Source Set | Description |
|-----------|------|-----------|-------------|
| Auth DTOs | `dto/AuthDto.kt` | commonMain | Login request/response, token refresh |
| Product DTOs | `dto/ProductDto.kt` | commonMain | Product CRUD request/response |
| Order DTOs | `dto/OrderDto.kt` | commonMain | Order lifecycle request/response |
| Sync DTOs | `dto/SyncDto.kt` | commonMain | Sync push/pull request/response |

#### Sync Engine

| Component | File | Source Set | Description |
|-----------|------|-----------|-------------|
| `SyncEngine` | `sync/SyncEngine.kt` | commonMain | Offline-first push/pull coordinator. Scheduled via `SyncWorker` (Android WorkManager) or `startPeriodicSync(scope)` (Desktop). Depends on `ZyntaDatabase`, `ApiService`, `SecurePreferences`, `NetworkMonitor` |
| `NetworkMonitor` (expect) | `sync/NetworkMonitor.kt` | commonMain | Network connectivity monitoring |
| `NetworkMonitor` (actual) | `sync/NetworkMonitor.kt` | androidMain | `ConnectivityManager` based |
| `NetworkMonitor` (actual) | `sync/NetworkMonitor.kt` | jvmMain | `InetAddress` reachability check |
| `SyncEnqueuer` | `local/SyncEnqueuer.kt` | commonMain | Lightweight helper for enqueueing pending_operations rows after local mutations |

#### Database

| Component | File | Source Set | Description |
|-----------|------|-----------|-------------|
| `DatabaseFactory` | `local/db/DatabaseFactory.kt` | commonMain | Orchestrates key provider + driver + migrations; opens `ZyntaDatabase` (SQLCipher AES-256 + WAL mode) |
| `DatabaseDriverFactory` (expect) | `local/db/DatabaseDriverFactory.kt` | commonMain | Platform-specific SQLDelight driver creation |
| `DatabaseKeyProvider` (expect) | `local/db/DatabaseKeyProvider.kt` | commonMain | Database encryption key provider |
| `DatabaseMigrations` | `local/db/DatabaseMigrations.kt` | commonMain | Schema migration manager |
| `SecurePreferencesKeyMigration` | `local/db/SecurePreferencesKeyMigration.kt` | commonMain | Idempotent migration of secure preferences keys at startup |
| 9 Data Mappers | `local/mapper/*.kt` | commonMain | Entity-to-domain mappers: `ProductMapper`, `OrderMapper`, `CustomerMapper`, `CategoryMapper`, `UserMapper`, `RegisterMapper`, `StockMapper`, `SupplierMapper`, `SyncOperationMapper` |
| 13 SQLDelight schemas | `sqldelight/…/*.sq` | commonMain | `audit_log`, `categories`, `customers`, `orders`, `products`, `registers`, `settings`, `stock`, `suppliers`, `sync_queue`, `tax_groups`, `units_of_measure`, `users` |

#### Repositories (14 domain interfaces → 14 data implementations)

| Repository Interface | Implementation | Key Capabilities |
|---------------------|----------------|------------------|
| `AuthRepository` | `AuthRepositoryImpl` | Local BCrypt verification + SecurePreferences JWT cache |
| `ProductRepository` | `ProductRepositoryImpl` | DB CRUD + FTS5 full-text search |
| `OrderRepository` | `OrderRepositoryImpl` | Transactional order + items creation |
| `CustomerRepository` | `CustomerRepositoryImpl` | DB CRUD + FTS5 search |
| `CategoryRepository` | `CategoryRepositoryImpl` | Recursive category tree (CTE) |
| `RegisterRepository` | `RegisterRepositoryImpl` | Session lifecycle + cash movement recording |
| `StockRepository` | `StockRepositoryImpl` | Adjustments + low-stock alert upserts |
| `SupplierRepository` | `SupplierRepositoryImpl` | Basic CRUD |
| `UserRepository` | `UserRepositoryImpl` | Password hashing + lifecycle |
| `SettingsRepository` | `SettingsRepositoryImpl` | Typed key-value wrapper |
| `TaxGroupRepository` | `TaxGroupRepositoryImpl` | CRUD + soft-delete |
| `UnitGroupRepository` | `UnitGroupRepositoryImpl` | CRUD + base-unit promotion |
| `AuditRepository` | `AuditRepositoryImpl` | Append-only audit log |
| `SyncRepository` | `SyncRepositoryImpl` | Batch read + status transitions (PENDING→SYNCED/FAILED) + maintenance (prune, deduplicate) |

#### Domain Ports (Hexagonal Architecture Boundaries)

| Port | File | Implemented By |
|------|------|----------------|
| `SecureStoragePort` | `shared/domain/…/port/SecureStoragePort.kt` | `SecurePreferences` (bound via Koin in `securityModule`) |
| `PasswordHashPort` | `shared/domain/…/port/PasswordHashPort.kt` | `PasswordHasherAdapter` (bound via Koin in `securityModule`) |
| `SecureStorageKeys` | `shared/domain/…/port/SecureStorageKeys.kt` | Constants object — no implementation needed |
| `ReceiptPrinterPort` | `shared/domain/…/printer/ReceiptPrinterPort.kt` | HAL `ThermalPrinter` implementations |
| `ReportPrinterPort` | `shared/domain/…/printer/ReportPrinterPort.kt` | HAL `ThermalPrinter` implementations |
| `ZReportPrinterPort` | `shared/domain/…/printer/ZReportPrinterPort.kt` | HAL `ThermalPrinter` implementations |

---

### DI (Koin)

**Koin Version:** 4.0.4

#### Core Modules

| Module | File | Module Path | Scope | Bindings |
|--------|------|-------------|-------|----------|
| `coreModule` | `shared/core/src/commonMain/…/di/CoreModule.kt` | `:shared:core` | Singleton | `ZyntaLogger(defaultTag="ZyntaPOS")`, `CurrencyFormatter()`, `AppInfoProvider` (via `createAppInfoProvider()`), `SystemHealthTracker` (via `createSystemHealthTracker()`), `CoroutineDispatcher` × 3 (IO, Main, Default via named qualifiers) |
| `securityModule` | `shared/security/src/commonMain/…/di/SecurityModule.kt` | `:shared:security` | Singleton | `EncryptionManager()`, `DatabaseKeyManager()`, `SecurePreferences()`, `SecureStoragePort` → `SecurePreferences`, `PasswordHashPort` → `PasswordHasherAdapter()`, `JwtManager(prefs=get())`, `PinManager` (object), `SecurityAuditLogger(auditRepository=get(), deviceId=get(named("deviceId")))`, `RbacEngine()` |
| `dataModule` | `shared/data/src/commonMain/…/di/DataModule.kt` | `:shared:data` | Singleton | `DatabaseMigrations()`, `SecurePreferencesKeyMigration(prefs)`, `DatabaseFactory(keyProvider, driverFactory, migrations, passwordHasher)`, `ZyntaDatabase` (via `DatabaseFactory.openDatabase()`), `SyncEnqueuer(db)`, all 14 Repository impls, `buildApiClient(prefs)`, `KtorApiService(client)`, `SyncEngine(db, api, prefs, networkMonitor)` |
| `DomainModule` | `shared/domain/src/commonMain/…/DomainModule.kt` | `:shared:domain` | — | **Placeholder** — no bindings yet |
| `DesignSystemModule` | `composeApp/designsystem/src/commonMain/…/DesignSystemModule.kt` | `:composeApp:designsystem` | — | **Placeholder** — no bindings yet |

#### Platform-Specific Modules

| Module | File | Platform | Bindings |
|--------|------|----------|----------|
| `AndroidDataModule` | `shared/data/src/androidMain/…/di/AndroidDataModule.kt` | Android | `DatabaseDriverFactory(context)`, `DatabaseKeyProvider(context)`, `NetworkMonitor(context)`, `String` named `"deviceId"` (Android ID) |
| `DesktopDataModule` | `shared/data/src/jvmMain/…/di/DesktopDataModule.kt` | JVM Desktop | `DatabaseDriverFactory()`, `DatabaseKeyProvider()`, `NetworkMonitor()`, `String` named `"deviceId"` (Desktop UUID) |
| `AndroidHalModule` | `shared/hal/src/androidMain/…/di/AndroidHalModule.kt` | Android | HAL hardware implementations (printer, scanner, cash drawer) |
| `DesktopHalModule` | `shared/hal/src/jvmMain/…/di/DesktopHalModule.kt` | JVM Desktop | HAL hardware implementations (printer, scanner, cash drawer) |

#### Feature Modules

| Module | File Path | Feature |
|--------|-----------|---------|
| `AuthModule` | `composeApp/feature/auth/src/commonMain/…/AuthModule.kt` | Auth ViewModels + session manager |
| `PosModule` | `composeApp/feature/pos/src/commonMain/…/PosModule.kt` | POS ViewModel + use cases |
| `NavigationModule` | `composeApp/navigation/src/commonMain/…/NavigationModule.kt` | Navigation graph |
| Other feature modules | `composeApp/feature/*/src/commonMain/…/*Module.kt` | Per-feature DI |

#### Named Qualifiers

| Qualifier | Value | Type | Binding | Provider Module |
|-----------|-------|------|---------|-----------------|
| `IO_DISPATCHER` | `named("IO")` | `CoroutineDispatcher` | `Dispatchers.IO` | `coreModule` |
| `MAIN_DISPATCHER` | `named("Main")` | `CoroutineDispatcher` | `Dispatchers.Main` | `coreModule` |
| `DEFAULT_DISPATCHER` | `named("Default")` | `CoroutineDispatcher` | `Dispatchers.Default` | `coreModule` |
| `"deviceId"` | `named("deviceId")` | `String` | Android ID / Desktop UUID | Platform data modules |

#### Koin Graph Bootstrap Order

```
coreModule          → Zero deps; loaded first
securityModule      → Depends on: nothing at load time (AuditRepository + deviceId resolved lazily)
dataModule          → Depends on: coreModule, securityModule (for PasswordHashPort, SecureStoragePort)
platformDataModule  → Depends on: coreModule (provides DatabaseDriverFactory, DatabaseKeyProvider, NetworkMonitor, deviceId)
halModule           → Depends on: coreModule, domain
featureModules      → Depends on: coreModule, domain, designsystem, navigation
```

**Enforcement rule (per CONTRIBUTING.md):** `GlobalContext.get()` is PROHIBITED outside bootstrap code.

---

## Key Version Catalog Dependencies

| Category | Library | Version | Notes |
|----------|---------|---------|-------|
| **Toolchain** | Kotlin | 2.3.0 | 100% Kotlin, zero Java |
| **Toolchain** | AGP | 8.13.2 | Android Gradle Plugin |
| **Android** | CompileSDK | 36 | |
| **Android** | MinSDK | 24 | Android 7.0+ |
| **Android** | TargetSDK | 36 | |
| **KotlinX** | Coroutines | 1.10.2 | `core`, `android`, `swing`, `test` |
| **KotlinX** | Serialization | 1.8.0 | `json`, `cbor` |
| **KotlinX** | DateTime | 0.7.1 | |
| **KotlinX** | Collections Immutable | 0.3.8 | |
| **Compose** | Multiplatform | 1.10.0 | |
| **Compose** | Material 3 | 1.10.0-alpha05 | |
| **Compose** | Navigation | 2.9.2 | Type-safe |
| **Compose** | Adaptive | 1.1.0-alpha04 | |
| **Compose** | Hot Reload | 1.0.0 | |
| **AndroidX** | Lifecycle | 2.9.6 | ViewModel + Runtime Compose |
| **AndroidX** | Activity | 1.12.2 | Compose integration |
| **AndroidX** | Core KTX | 1.17.0 | |
| **AndroidX** | Security Crypto | 1.1.0-alpha06 | EncryptedSharedPreferences |
| **AndroidX** | SQLite | 2.5.2 | |
| **AndroidX** | Work | 2.10.1 | SyncWorker |
| **DI** | Koin | 4.0.4 | `core`, `android`, `compose`, `compose-viewmodel`, `test` |
| **Network** | Ktor | 3.0.3 | `core`, `okhttp`, `cio`, `content-negotiation`, `auth`, `logging`, `mock` |
| **Persistence** | SQLDelight | 2.0.2 | `runtime`, `coroutines`, `android-driver`, `sqlite-driver`, `primitive-adapters`, `sqlite-3-25-dialect` |
| **Persistence** | SQLCipher | 4.5.0 | Android AES-256 DB encryption |
| **Image** | Coil | 3.0.4 | `compose`, `network-ktor3` (reserved Phase 2) |
| **Logging** | Kermit | 2.0.4 | `kermit`, `kermit-crashlytics` (reserved Phase 2) |
| **Testing** | JUnit | 4.13.2 | |
| **Testing** | Mockative | 3.0.1 | KMP mock generation |
| **Testing** | Turbine | 1.2.0 | Flow testing |
| **Desktop** | jSerialComm | 2.10.4 | Serial port for printers/scanners |
| **Desktop** | jBCrypt | 0.4 | Password hashing |
| **Desktop** | PDFBox | 3.0.3 | PDF report generation |
| **Android** | CameraX | 1.4.1 | Camera for barcode scanning |
| **Android** | ML Kit Barcode | 17.3.0 | Barcode recognition |
| **Build** | KSP | 2.2.0-2.0.2 (overridden to 2.3.4) | Annotation processing |
| **Build** | Detekt | 1.23.8 | Static analysis |
| **Build** | BuildKonfig | 0.15.2 | Build-time config generation |
| **Build** | Secrets Gradle | 2.0.1 | Secrets management |

### Bundles (Convenience Groups)

| Bundle | Libraries | Used By |
|--------|-----------|---------|
| `kotlinx-common` | coroutines-core, serialization-json, datetime, collections-immutable | Every shared module |
| `ktor-common` | client-core, content-negotiation, auth, logging, serialization-json | `:shared:data` |
| `koin-common` | core, compose, compose-viewmodel | Every module with ViewModels |
| `sqldelight-common` | runtime, coroutines, primitive-adapters | `:shared:data` |
| `testing-common` | kotlin-test, coroutines-test, mockative, turbine, koin-test | All `commonTest` source sets |

---

## Module Dependency Graph

```
                      ┌──────────────┐
                      │ :shared:core │  ← Zero deps (Tier 1)
                      └──────┬───────┘
                             │
                      ┌──────▼───────┐
                      │:shared:domain│  ← Depends on :shared:core (Tier 2)
                      └──────┬───────┘
                    ┌────────┼────────┐
                    │        │        │
             ┌──────▼──┐ ┌──▼────┐ ┌─▼──────────┐
             │:shared:  │ │:shared│ │:shared:    │
             │  data    │ │ :hal  │ │ security   │  ← All depend on :core + :domain (Tier 3)
             └──────┬───┘ └───┬───┘ └─────┬──────┘
                    └─────────┼───────────┘
                              │
        ┌─────────────────────┼─────────────────────────────────┐
        │                     │                                 │
 ┌──────▼──────┐   ┌─────────▼─────────┐           ┌──────────▼──────────┐
 │:composeApp: │   │:composeApp:       │           │:composeApp:core     │
 │designsystem │   │navigation         │           │(BaseViewModel<S,I,E>)│
 │             │   │(type-safe, RBAC)   │           └──────────┬──────────┘
 └──────┬──────┘   └─────────┬─────────┘                      │
        │                     │                                │
        └─────────────────────┼────────────────────────────────┘
                              │
                   ┌──────────▼──────────┐
                   │:composeApp:feature:*│  (13 feature modules)
                   │ auth, pos, inventory│
                   │ register, reports,  │
                   │ settings, customers,│
                   │ coupons, expenses,  │
                   │ staff, multistore,  │
                   │ admin, media        │
                   └─────────────────────┘
                              │
                   ┌──────────▼──────────┐
                   │ :composeApp         │  (Root composable + entry points)
                   └──────────┬──────────┘
                              │
                   ┌──────────▼──────────┐
                   │ :androidApp         │  (Android application shell)
                   └─────────────────────┘
```

---

## Summary Statistics

| Metric | Count |
|--------|-------|
| Total Gradle Modules | 23 |
| Total Source Sets | 51 |
| Build Gradle Files | 24 (1 root + 23 module) |
| CI/CD Workflows | 2 (ci.yml + release.yml) |
| SQLDelight Schema Files | 13 |
| ProGuard Rule Files | 1 |
| Desktop Package Formats | 3 (DMG, MSI, DEB) |
| Feature Modules | 13 |
| Shared Library Tiers | 5 (core, domain, data, hal, security) |
| Design System Components | 24 components + 5 layouts + 6 theme/token files |
| Domain Models | 26 |
| Use Cases | 30 |
| Repository Interfaces | 14 |
| Repository Implementations | 14 |
| Domain Ports | 6 |
| Koin Modules (core) | 5 (core, security, data, domain stub, designsystem stub) |
| Koin Modules (platform) | 4 (androidData, desktopData, androidHal, desktopHal) |
| Named Koin Qualifiers | 4 (IO, Main, Default dispatchers + deviceId) |
| expect/actual Classes | 10+ (EncryptionManager, DatabaseKeyManager, SecurePreferences, PasswordHasher, DatabaseDriverFactory, DatabaseKeyProvider, NetworkMonitor, AppInfoProvider, SystemHealthTracker, PlatformFilePicker, WindowSizeClassHelper) |
| ADRs | 4 (all ACCEPTED) |
| Documentation Files | 17+ substantive documents |
| Test Source Sets | 12 (10 commonTest + 2 jvmTest) |
| Version Catalog Entries | ~80 libraries, 5 bundles, 12 plugins |

---

*End of Phase 1 — Deep Discovery & Component Mapping*
