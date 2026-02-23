# Phase 3: Architecture, MVI Purity & Component Reuse Audit

> **Audit Version:** v1.0
> **Date:** 2026-02-23
> **Auditor Role:** Staff KMP Solutions Architect, Lead Security Auditor, Principal Engineer
> **Project:** ZyntaPOS-KMM — Cross-platform Point of Sale (KMP + Compose Multiplatform)
> **Root Directory:** `/home/user/ZyntaPOS-KMM/`
> **Basis:** Phase 2 result at `docs/audit/v1.0/audit_phase2_result.md`

---

## ARCHITECTURE (MVI & CLEAN ARCH) VIOLATIONS

### A-01: DashboardScreen Bypasses MVI — Directly Injects Repositories into Composable (CRITICAL)

❌ `composeApp/src/commonMain/kotlin/com/zyntasolutions/zyntapos/DashboardScreen.kt` — **Clean Architecture Violation + MVI Bypass**

**Offending code (lines 91–114):**
```kotlin
@Composable
fun DashboardScreen(
    onNavigateToPos: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val orderRepository: OrderRepository = koinInject()       // ❌ Direct repo injection
    val productRepository: ProductRepository = koinInject()   // ❌ Direct repo injection
    val registerRepository: RegisterRepository = koinInject()  // ❌ Direct repo injection
    val authRepository: AuthRepository = koinInject()          // ❌ Direct repo injection
    val currencyFormatter: CurrencyFormatter = koinInject()

    var currentUser by remember { mutableStateOf<User?>(null) }       // ❌ Local mutable state
    var todaysSales by remember { mutableStateOf(0.0) }               // ❌ Local mutable state
    var totalOrders by remember { mutableStateOf(0L) }                // ❌ Local mutable state
    var lowStockCount by remember { mutableStateOf(0L) }              // ❌ Local mutable state
    var lowStockNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeRegisters by remember { mutableStateOf(0L) }
    var recentOrders by remember { mutableStateOf<List<RecentOrder>>(emptyList()) }
    var weeklySalesData by remember { mutableStateOf<List<ChartDataPoint>>(emptyList()) }
    var todaySparkline by remember { mutableStateOf<List<Float>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // All data fetching + business aggregation here ...
    }
}
```

**Violations:**
1. **Clean Architecture:** Composable directly injects 4 repository interfaces via `koinInject()` — bypasses ViewModel/UseCase layer entirely
2. **MVI Pattern:** Uses 10 `remember { mutableStateOf }` for business data instead of a single immutable `DashboardState` data class
3. **Testability:** Impossible to unit test business logic (sales aggregation, low stock filtering, weekly bucketing) without a Compose test framework
4. **CONTRIBUTING.md §MVI:** Violates "every screen has State/Intent/Effect" and "ViewModel exposes `StateFlow<UiState>`"

**Recommendation:** Create `DashboardViewModel` following the project's MVI pattern:

```kotlin
// DashboardState.kt
@Immutable
data class DashboardState(
    val currentUser: User? = null,
    val todaysSales: Double = 0.0,
    val totalOrders: Long = 0L,
    val lowStockCount: Long = 0L,
    val lowStockNames: List<String> = emptyList(),
    val activeRegisters: Long = 0L,
    val recentOrders: List<RecentOrder> = emptyList(),
    val weeklySalesData: List<ChartDataPoint> = emptyList(),
    val todaySparkline: List<Float> = emptyList(),
    val isLoading: Boolean = true,
)

// DashboardViewModel.kt
class DashboardViewModel(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val registerRepository: RegisterRepository,
    private val authRepository: AuthRepository,
    private val currencyFormatter: CurrencyFormatter,
) : BaseViewModel<DashboardState, DashboardIntent, DashboardEffect>(DashboardState()) {
    init { dispatch(DashboardIntent.LoadDashboard) }
    override suspend fun handleIntent(intent: DashboardIntent) { ... }
}

// DashboardScreen.kt — refactored
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = koinViewModel(),
    ...
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Pure rendering from state, no repository access
}
```

---

### A-02: Hardcoded Default Admin Credential in Source Code (CRITICAL — Security)

❌ `shared/data/src/commonMain/kotlin/com/zyntasolutions/zyntapos/data/local/db/DatabaseFactory.kt:125,134` — **Hardcoded credential in production code**

**Offending code:**
```kotlin
// Line 125 (comment):
* Default credentials: admin@zentapos.com / admin123

// Line 134 (seed logic):
val passwordHash = passwordHasher.hash("admin123")
```

**Violations:**
1. **OWASP A02:2021 (Cryptographic Failures):** Plaintext password `"admin123"` in source code
2. **PCI-DSS SAQ-B:** Known credential in every deployment
3. **Git history exposure:** Credential permanently in version control

**Recommendation:** Guard with `AppConfig.IS_DEBUG`:
```kotlin
private fun seedDefaultAdminIfEmpty(db: ZyntaDatabase) {
    if (!AppConfig.IS_DEBUG) return  // Production: require initialization flow
    // ... existing seed logic for debug builds only
}
```
For production, implement a first-launch initialization flow that generates a random admin password and forces immediate change.

---

### A-03: ViewModel Clean Architecture — All Feature ViewModels COMPLIANT

✅ All 7 feature ViewModels access only domain-layer interfaces:

| ViewModel | File | Dependencies | Status |
|-----------|------|-------------|--------|
| `AuthViewModel` | `feature/auth/.../AuthViewModel.kt` | `LoginUseCase`, `LogoutUseCase`, `AuthRepository` (interface) | ✅ Clean |
| `SignUpViewModel` | `feature/auth/.../SignUpViewModel.kt` | `UserRepository` (interface) | ✅ Clean |
| `PosViewModel` | `feature/pos/.../PosViewModel.kt` | 11 use cases + 3 repository interfaces | ✅ Clean |
| `InventoryViewModel` | `feature/inventory/.../InventoryViewModel.kt` | 4 use cases + 2 repository interfaces | ✅ Clean |
| `RegisterViewModel` | `feature/register/.../RegisterViewModel.kt` | 5 use cases + 1 repository interface | ✅ Clean |
| `ReportsViewModel` | `feature/reports/.../ReportsViewModel.kt` | 3 use cases only | ✅ Clean |
| `SettingsViewModel` | `feature/settings/.../SettingsViewModel.kt` | 3 use cases + 3 repository interfaces | ✅ Clean |

Zero imports from `com.zyntasolutions.zyntapos.data.*` in any ViewModel. Zero Ktor/SQLDelight/DAO references.

---

### A-04: MVI State Immutability — All Feature ViewModels COMPLIANT

✅ BaseViewModel contract (`composeApp/core/.../BaseViewModel.kt`):
- Line 65: `private val _state = MutableStateFlow(initialState)` — **PRIVATE**
- Line 71: `val state: StateFlow<S> = _state.asStateFlow()` — **PUBLIC as immutable**
- Line 89: `protected fun updateState(transform: S.() -> S)` — only mutation path
- Line 126: `fun dispatch(intent: I)` — only public entry point

All 6 feature MVI triads (State/Intent/Effect) verified:

| Feature | State (immutable data class) | Intent (sealed class) | Effect (sealed class) | Status |
|---------|-----|------|------|--------|
| Auth | `AuthState` | `AuthIntent` | `AuthEffect` | ✅ |
| POS | `PosState` | `PosIntent` | `PosEffect` | ✅ |
| Inventory | `InventoryState` | `InventoryIntent` | `InventoryEffect` | ✅ |
| Register | `RegisterState` | `RegisterIntent` | `RegisterEffect` | ✅ |
| Reports | `ReportsState` | `ReportsIntent` | `ReportsEffect` | ✅ |
| Settings | `SettingsState` | `SettingsIntent` | `SettingsEffect` | ✅ |

No public `fun setX()` methods. No mutable state exposed. All side-effects via `sendEffect()` channel. All state updates via `updateState { copy(...) }`.

---

### A-05: KMP Boundary Leaks — Zero Illegal Platform Imports in commonMain

✅ Verified zero `import android.*`, `import java.*`, `import javax.*` across ALL `commonMain` source sets:
- `shared/core/src/commonMain/` — ✅ Clean
- `shared/domain/src/commonMain/` — ✅ Clean
- `shared/data/src/commonMain/` — ✅ Clean
- `shared/security/src/commonMain/` — ✅ Clean
- `shared/hal/src/commonMain/` — ✅ Clean
- `composeApp/core/src/commonMain/` — ✅ Clean
- `composeApp/designsystem/src/commonMain/` — ✅ Clean
- `composeApp/navigation/src/commonMain/` — ✅ Clean
- `composeApp/feature/*/src/commonMain/` — ✅ Clean

**Note on `androidx.lifecycle` and `androidx.navigation` in commonMain:** These use the KMP-compatible JetBrains artifacts (`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel`, `org.jetbrains.androidx.navigation:navigation-compose`) which are officially multiplatform. This is the standard Compose Multiplatform approach and is **not a violation** — these libraries target both Android and JVM/Desktop.

---

### A-06: Gradle `api` vs `implementation` — All Modules COMPLIANT

✅ Verified correct usage across all 23 modules:

| Module | `api()` (public types) | `implementation()` (internal) | Status |
|--------|----------------------|------------------------------|--------|
| `:shared:core` | kotlinx bundles, kermit, koin.core | coroutines-android, koin-android | ✅ |
| `:shared:domain` | `:shared:core`, kotlinx.datetime | — | ✅ |
| `:shared:data` | `:shared:domain` | ktor, sqldelight, serialization | ✅ |
| `:shared:hal` | `:shared:core`, `:shared:domain` | jserialcomm, camerax, mlkit | ✅ |
| `:shared:security` | `:shared:core`, `:shared:domain` | jbcrypt, security-crypto | ✅ |
| `:composeApp:core` | lifecycle-viewmodel, coroutines.core | — | ✅ |
| `:composeApp:designsystem` | compose.*, `:shared:core` | coil, collections-immutable | ✅ |
| `:composeApp:navigation` | compose.navigation, `:shared:domain`, `:shared:security` | koin, serialization | ✅ |
| Feature modules (7) | compose.runtime, compose.material3 | business modules, koin | ✅ |

**Key architectural win:** `:shared:data` hides SQLDelight and Ktor behind `implementation()`, preventing transitive leakage to feature modules. Features can only access domain interfaces.

---

## COMPOSE MULTIPLATFORM & STATE ISSUES

### S-01: Missing `@Immutable` on ALL MVI State Classes (HIGH)

⚠️ **6 MVI State data classes lack `@Immutable` annotation** — causes unnecessary recomposition when state objects are passed as composable parameters.

| State Class | File | Fields | Status |
|------------|------|--------|--------|
| `AuthState` | `feature/auth/.../mvi/AuthState.kt` | 8 immutable fields | ⚠️ Missing `@Immutable` |
| `PosState` | `feature/pos/.../PosState.kt` | ~15 fields (incl. `List<Product>`, `List<CartItem>`) | ⚠️ Missing `@Immutable` |
| `InventoryState` | `feature/inventory/.../InventoryState.kt` | ~12 fields | ⚠️ Missing `@Immutable` |
| `RegisterState` | `feature/register/.../RegisterState.kt` | ~10 fields | ⚠️ Missing `@Immutable` |
| `ReportsState` | `feature/reports/.../ReportsState.kt` | ~10 fields | ⚠️ Missing `@Immutable` |
| `SettingsState` | `feature/settings/.../SettingsState.kt` | ~20 fields | ⚠️ Missing `@Immutable` |

**Current status:** Only `@Stable` on `NavigationController` — zero `@Immutable` annotations in the entire codebase.

**Recommendation:** Add to every MVI state class:
```kotlin
import androidx.compose.runtime.Immutable

@Immutable
data class AuthState(
    val isLoading: Boolean = false,
    val email: String = "",
    // ...
)
```

**Impact:** Without `@Immutable`, Compose cannot guarantee stability of these types. Every time state flows to child composables, Compose must conservatively recompose them even if the data hasn't changed.

---

### S-02: Missing `@Immutable` on Domain Model Data Classes Used in UI (HIGH)

⚠️ **27 domain model data classes** in `shared/domain/.../model/` lack `@Immutable` despite being used directly in Compose UI state (e.g., `PosState.products: List<Product>`, `PosState.cartItems: List<CartItem>`).

Key models needing annotation:

| Model | Used In | Impact |
|-------|---------|--------|
| `Product` | POS grid, Inventory list | Every product card recomposes unnecessarily |
| `CartItem` | POS cart panel | Cart list recomposes on any state change |
| `Order` | Order history, Reports | Order list items recompose unnecessarily |
| `Category` | Category filter chips | All chips recompose on selection change |
| `Customer` | Customer selector | Customer list recomposes unnecessarily |
| `OrderTotals` | Payment panel | Totals display recomposes unnecessarily |
| `CashMovement` | Register dashboard | Movement list recomposes unnecessarily |

**Recommendation:** Add `@Immutable` to all domain model data classes that appear in Compose state. Since these are in `:shared:domain` (no Compose dependency), add `compose.runtime` as `compileOnly` dependency to access the annotation:

```kotlin
// shared/domain/build.gradle.kts
commonMain.dependencies {
    api(project(":shared:core"))
    compileOnly(libs.compose.runtime)  // For @Immutable annotation only
}
```

Alternative: Create a `@DomainImmutable` typealias or use `@Stable` from a shared annotation module.

---

### S-03: Inconsistent `collectAsState()` vs `collectAsStateWithLifecycle()` (MEDIUM)

⚠️ **5 screens use `collectAsState()`** instead of lifecycle-aware `collectAsStateWithLifecycle()`:

| File | Line | Current | Should Be |
|------|------|---------|-----------|
| `feature/pos/.../PosScreen.kt` | 45 | `viewModel.state.collectAsState()` | `collectAsStateWithLifecycle()` |
| `feature/reports/.../ReportsHomeScreen.kt` | 60 | `viewModel.state.collectAsState()` | `collectAsStateWithLifecycle()` |
| `feature/reports/.../SalesReportScreen.kt` | 95 | `viewModel.state.collectAsState()` | `collectAsStateWithLifecycle()` |
| `feature/reports/.../StockReportScreen.kt` | 89 | `viewModel.state.collectAsState()` | `collectAsStateWithLifecycle()` |
| `feature/settings/.../SystemHealthScreen.kt` | 67 | `healthTracker.snapshot.collectAsState()` | `collectAsStateWithLifecycle()` |

**Screens already using `collectAsStateWithLifecycle()` (correct):**
- `feature/auth/.../LoginScreen.kt:62` ✅
- `feature/auth/.../SignUpScreen.kt:49` ✅
- `feature/auth/.../SessionGuard.kt:36` ✅
- `feature/register/.../RegisterDashboardScreen.kt:54` ✅
- `feature/register/.../RegisterGuard.kt:45` ✅
- `feature/register/.../OpenRegisterScreen.kt:49` ✅
- `feature/register/.../CloseRegisterScreen.kt:59` ✅
- `feature/register/.../ZReportScreen.kt:54` ✅

**Impact:** `collectAsState()` continues collecting when the composable is in the background (Android pause/stop), wasting CPU and potentially causing memory leaks for long-running flows.

**Recommendation:**
```kotlin
// Change from:
val state by viewModel.state.collectAsState()

// To:
val state by viewModel.state.collectAsStateWithLifecycle()
```

---

### S-04: LazyColumn/LazyRow Keying — Excellent Across All Screens

✅ All lazy list usages provide stable, unique `key` parameters:

| Screen | Component | Key Strategy | Status |
|--------|-----------|-------------|--------|
| POS Cart | `LazyColumn` | `key = { item.productId }` | ✅ Stable ID |
| POS Products | `ZyntaGrid` (design system) | `key = { it.id }` (required parameter) | ✅ Enforced by API |
| POS Categories | `LazyRow` | `key = { it.id }` + `key = "__all__"` sentinel | ✅ Unique |
| Inventory Products | `ZyntaTable` | `rowKey = { it.id }` | ✅ Stable ID |
| Register Movements | `LazyColumn` | `key = { it.id }` | ✅ Stable ID |
| Reports Stock | `LazyColumn` | `key = { "prefix-${it.id}" }` per tab | ✅ Prefixed |
| Dashboard | `LazyColumn` + `LazyRow` | `key = { it.id }` | ✅ Stable ID |
| Categories | `LazyColumn` | `key = root.id` + `"${root.id}_children"` | ✅ Compound |

**Design system enforcement:** `ZyntaGrid` requires a `key: (T) -> Any` parameter — making stable keys mandatory for all product grids. This is excellent API design.

---

## DUPLICATIONS & BYPASSED CORE COMPONENTS

### B-01: Raw `OutlinedButton` / `FilledTonalButton` Instead of `ZyntaButton` (HIGH — 16 instances)

🛑 **16 instances** across 3 feature modules use raw Material3 button variants instead of `ZyntaButton`:

**`:composeApp:feature:inventory` (9 instances):**

| File | Line | Raw Component | Context |
|------|------|--------------|---------|
| `SupplierDetailScreen.kt` | 214 | `OutlinedButton` | Cancel action |
| `CategoryDetailScreen.kt` | 231 | `OutlinedButton` | Cancel action |
| `StockAdjustmentDialog.kt` | 181 | `FilledTonalButton` | Confirm action |
| `ProductDetailScreen.kt` | 64 | `FilledTonalButton` | Save action |
| `ProductDetailScreen.kt` | 394 | `FilledTonalButton` | Add Variant |
| `BulkImportDialog.kt` | 91 | `FilledTonalButton` | Import action |
| `BulkImportDialog.kt` | 146 | `OutlinedButton` | Cancel action |
| `BarcodeGeneratorDialog.kt` | 156 | `OutlinedButton` | Cancel action |
| `BarcodeGeneratorDialog.kt` | 168 | `FilledTonalButton` | Generate action |

**`:composeApp:feature:pos` (4 instances):**

| File | Line | Raw Component | Context |
|------|------|--------------|---------|
| `HeldOrdersBottomSheet.kt` | 206 | `FilledTonalButton` | Retrieve held order |
| `ReceiptScreen.kt` | 174 | `OutlinedButton` | Secondary action |
| `CashPaymentPanel.kt` | 182 | `OutlinedButton` | Cancel payment |
| `SplitPaymentPanel.kt` | 129, 225 | `OutlinedButton` | Cancel/Remove split |

**`:composeApp:feature:register` (3 instances):**

| File | Line | Raw Component | Context |
|------|------|--------------|---------|
| `RegisterDashboardScreen.kt` | 717 | `OutlinedButton` | Secondary action |
| `CashInOutDialog.kt` | 98 | `OutlinedButton` | Cancel |

**Bypassed standard:** `ZyntaButton` supports `Secondary` and `Ghost` variants that should replace raw `OutlinedButton`, and `Primary`/`Secondary` for `FilledTonalButton`.

**Recommendation:** Replace all instances:
```kotlin
// From:
OutlinedButton(onClick = onCancel) { Text("Cancel") }

// To:
ZyntaButton(
    text = "Cancel",
    onClick = onCancel,
    variant = ZyntaButtonVariant.Ghost,  // or Secondary
)
```

```kotlin
// From:
FilledTonalButton(onClick = onConfirm) { Text("Confirm") }

// To:
ZyntaButton(
    text = "Confirm",
    onClick = onConfirm,
    variant = ZyntaButtonVariant.Secondary,
)
```

---

### B-02: Raw `IconButton` / `TextButton` Instead of `ZyntaButton` (MEDIUM — 4 instances)

🛑 Raw Material3 `IconButton` and `TextButton` imports in feature modules:

| File | Line | Raw Component | Status |
|------|------|--------------|--------|
| `feature/settings/.../SystemHealthScreen.kt` | 30 | `import IconButton` | 🛑 Bypass |
| `feature/reports/.../SalesReportScreen.kt` | 31 | `import IconButton` | 🛑 Bypass |
| `feature/reports/.../StockReportScreen.kt` | 31 | `import IconButton` | 🛑 Bypass |
| `feature/reports/.../DateRangePickerBar.kt` | 19 | `import TextButton` | 🛑 Bypass |

**Recommendation:** `ZyntaButton` has an `Icon` variant (`ZyntaButtonVariant.Icon`). Replace raw `IconButton` with:
```kotlin
ZyntaButton(
    icon = Icons.Default.FilterList,
    onClick = onFilter,
    variant = ZyntaButtonVariant.Icon,
)
```

For `TextButton`, use `ZyntaButton` with `Ghost` variant.

---

### B-03: Raw `Card` Instead of `ZyntaInfoCard` / `ZyntaStatCard` (MEDIUM — 8 instances)

🛑 Raw Material3 `Card` used instead of Zynta card components:

| File | Import Line | Status |
|------|-------------|--------|
| `feature/settings/.../PosSettingsScreen.kt` | 18 | 🛑 Raw `Card` import |
| `feature/settings/.../AppearanceSettingsScreen.kt` | 12 | 🛑 Raw `Card` import |
| `feature/settings/.../SettingsHomeScreen.kt` | 25 | 🛑 Raw `Card` import |
| `feature/settings/.../AboutScreen.kt` | 12 | 🛑 Raw `Card` import |
| `feature/settings/.../GeneralSettingsScreen.kt` | 16 | 🛑 Raw `Card` import |
| `feature/settings/.../PrinterSettingsScreen.kt` | 19 | 🛑 Raw `Card` import |
| `feature/settings/.../SystemHealthScreen.kt` | 25 | 🛑 Raw `Card` import |
| `feature/reports/.../ReportsHomeScreen.kt` | 20 | 🛑 Raw `Card` import |
| `feature/reports/.../SalesReportScreen.kt` | 26 | 🛑 Raw `Card` import |
| `feature/reports/.../StockReportScreen.kt` | 25 | 🛑 Raw `Card` import |

**Bypassed standard:** The design system provides `ZyntaInfoCard` (informational content), `ZyntaStatCard` (metric display), and `ZyntaSettingsItem` (settings rows) which enforce consistent elevation, padding, and shape tokens.

**Recommendation:** Evaluate each `Card` usage and replace with the appropriate Zynta component. If none of the existing card variants fit the use case, extend the design system rather than using raw Material3.

---

### B-04: Raw `Badge` Instead of `ZyntaBadge` (LOW — 2 instances)

🛑 Raw Material3 `Badge` in feature modules:

| File | Line | Status |
|------|------|--------|
| `feature/pos/.../PosScreen.kt` | 9 | 🛑 `import Badge` |
| `feature/reports/.../StockReportScreen.kt` | 23 | 🛑 `import Badge` |

**Recommendation:** Replace with `ZyntaBadge` which provides theme-consistent styling.

---

### B-05: Raw `Scaffold` Instead of `ZyntaPageScaffold` (LOW — 1 instance)

🛑 `feature/pos/.../PosScreen.kt:14` — `import Scaffold`

The POS screen uses raw `Scaffold` instead of `ZyntaPageScaffold` which provides consistent page-level layout with standard padding.

**Recommendation:** Evaluate if `ZyntaPageScaffold` fits the POS layout. POS screens have unique split-pane requirements, so raw `Scaffold` may be acceptable here with a documented exception.

---

### B-06: `DashboardScreen` Uses Raw `Card` + `IconButton` Instead of Design System (MEDIUM)

🛑 `composeApp/src/commonMain/.../DashboardScreen.kt` — imports raw `Card`, `CardDefaults`, `IconButton`, `Surface` (lines 29–35)

While the DashboardScreen correctly uses `ZyntaStatCard`, `ZyntaInfoCard`, `ZyntaLineChart`, `ZyntaSectionHeader`, and `ZyntaLoadingOverlay` from the design system (lines 53–61), it also uses raw Material3 `Card` and `IconButton` for some elements.

**Recommendation:** Replace remaining raw components during the DashboardViewModel refactor (see A-01).

---

### B-07: Base64 Encoding Helper Duplication Across Platforms (LOW)

📄 `shared/data/src/androidMain/.../DatabaseKeyProvider.kt` ↔ `shared/security/src/jvmMain/.../SecurePreferences.jvm.kt` — **Base64 utility duplication**

**Android (DatabaseKeyProvider.kt):**
```kotlin
private fun ByteArray.toBase64(): String =
    android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)
private fun String.fromBase64(): ByteArray =
    android.util.Base64.decode(this, android.util.Base64.NO_WRAP)
```

**Desktop (SecurePreferences.jvm.kt):**
```kotlin
private fun encoded(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
private fun decoded(s: String): ByteArray = Base64.getDecoder().decode(s)
```

**Recommendation:** Extract to `commonMain` with `expect/actual`:
```kotlin
// shared/security/src/commonMain/.../util/Base64Utils.kt
expect object Base64Utils {
    fun encode(bytes: ByteArray): String
    fun decode(text: String): ByteArray
}
```

---

### B-08: No Security/Network Bypasses Detected

✅ **Zero direct HTTP client creation** outside `shared/data/.../ApiClient.kt`
✅ **Zero raw SharedPreferences/DataStore** usage outside `SecurePreferences.*`
✅ **Zero raw Cipher/KeyGenerator** usage outside `shared/security/crypto/`
✅ **Zero hardcoded API keys or tokens** (except admin password in A-02)
✅ **Zero raw JSON configuration** outside central Ktor setup

The security architecture is well-enforced.

---

## CONSOLIDATED FINDINGS BY PRIORITY

### CRITICAL (Must Fix Before Production)

| # | Finding | Category | Files | Effort |
|---|---------|----------|-------|--------|
| A-01 | DashboardScreen bypasses MVI — injects repos directly, 10 local `mutableStateOf` | Clean Arch + MVI | 1 file | 3–4 hrs |
| A-02 | Hardcoded `admin123` password in DatabaseFactory | Security | 1 file | 30 min |

### HIGH (Should Fix This Sprint)

| # | Finding | Category | Files | Effort |
|---|---------|----------|-------|--------|
| S-01 | Missing `@Immutable` on 6 MVI State classes | Recomposition | 6 files | 15 min |
| S-02 | Missing `@Immutable` on 27 domain model classes | Recomposition | 27 files | 45 min |
| B-01 | Raw `OutlinedButton`/`FilledTonalButton` instead of `ZyntaButton` (16 instances) | Design System | 10 files | 2 hrs |

### MEDIUM (Fix Next Sprint)

| # | Finding | Category | Files | Effort |
|---|---------|----------|-------|--------|
| S-03 | Inconsistent `collectAsState()` vs `collectAsStateWithLifecycle()` (5 screens) | Lifecycle | 5 files | 15 min |
| B-02 | Raw `IconButton`/`TextButton` instead of `ZyntaButton` (4 instances) | Design System | 4 files | 30 min |
| B-03 | Raw `Card` instead of `ZyntaInfoCard`/`ZyntaStatCard` (10 imports) | Design System | 10 files | 2 hrs |
| B-06 | DashboardScreen uses raw Card + IconButton | Design System | 1 file | Included in A-01 |

### LOW (Backlog)

| # | Finding | Category | Files | Effort |
|---|---------|----------|-------|--------|
| B-04 | Raw `Badge` instead of `ZyntaBadge` (2 instances) | Design System | 2 files | 15 min |
| B-05 | Raw `Scaffold` instead of `ZyntaPageScaffold` in POS | Design System | 1 file | 30 min |
| B-07 | Base64 helper duplication across platforms | DRY | 2 files | 1 hr |

---

## STRENGTHS IDENTIFIED

1. **Excellent Clean Architecture in Feature ViewModels:** All 7 feature ViewModels strictly access only domain interfaces — zero data-layer leakage
2. **Rigorous MVI Pattern:** Immutable state classes, sealed intents/effects, `dispatch()`-only entry point, `updateState { copy() }` pattern consistently applied
3. **Zero KMP Boundary Violations:** No `android.*`, `java.*`, or `javax.*` imports in any `commonMain` source set
4. **Perfect Gradle api/implementation Usage:** Data layer properly hides SQLDelight and Ktor; Compose types correctly exposed via `api()`
5. **Excellent Lazy List Keying:** Stable, unique keys across all lazy lists; `ZyntaGrid` enforces keys via required parameter
6. **Strong Security Encapsulation:** All crypto, auth, and secure storage access goes through the security module — no bypasses detected
7. **Design System Adoption:** `DashboardScreen`, `LoginScreen`, `PosScreen` heavily use Zynta design system components (stat cards, charts, section headers, loading overlays, search bars, buttons)

---

*End of Phase 3 — Architecture, MVI Purity & Component Reuse Audit*
