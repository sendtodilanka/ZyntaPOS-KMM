# ZyntaPOS — Phase 3 Sprint 15: Admin Feature Part 3 — Module Control & Developer Console

> **Document ID:** ZYNTA-PLAN-PHASE3-SPRINT15-v1.0
> **Phase:** 3 — Enterprise (Months 13–18)
> **Sprint:** 15 of 24 | Week 15
> **Module(s):** `:composeApp:feature:admin`, `:tools:debug`
> **Author:** Senior KMP Architect & Lead Engineer
> **Reference:** ZYNTA-MASTER-PLAN-v1.0 | ADR-001

---

## Goal

Implement the final admin screens: a module management screen that toggles feature modules on/off per store (backed by the `settings` table), and integrate the existing `:tools:debug` developer console as a tab within the admin area (debug builds only). This completes the `:composeApp:feature:admin` module (M19).

---

## New Screen Files

**Location:** `composeApp/feature/admin/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/admin/screen/`

```
screen/
└── ModuleManagementScreen.kt     # Toggle feature modules on/off per store
```

---

## Module Toggle Architecture

Feature module toggles are stored in the `settings` table with keys in the format `module.{name}.enabled`:

```
module.customers.enabled   = "true"
module.coupons.enabled     = "true"
module.expenses.enabled    = "true"
module.staff.enabled       = "true"
module.reports.enabled     = "true"
module.multistore.enabled  = "true"
module.media.enabled       = "true"
module.einvoice.enabled    = "false"   // disabled by default until IRD cert configured
```

These are read at app startup by `AppModuleConfig` and cached in memory. Navigation items are filtered based on enabled state (in addition to RBAC filtering).

### `AppModuleConfig.kt` (new file in `:shared:domain/model`)

```kotlin
package com.zyntasolutions.zyntapos.domain.model

/**
 * Runtime module configuration read from the `settings` table.
 * Used by NavigationItems to determine which feature modules are visible.
 */
data class AppModuleConfig(
    val customersEnabled: Boolean = true,
    val couponsEnabled: Boolean = true,
    val expensesEnabled: Boolean = true,
    val staffEnabled: Boolean = true,
    val reportsEnabled: Boolean = true,
    val multistoreEnabled: Boolean = true,
    val mediaEnabled: Boolean = true,
    val einvoiceEnabled: Boolean = false
) {
    fun isEnabled(module: String): Boolean = when (module) {
        "customers"  -> customersEnabled
        "coupons"    -> couponsEnabled
        "expenses"   -> expensesEnabled
        "staff"      -> staffEnabled
        "reports"    -> reportsEnabled
        "multistore" -> multistoreEnabled
        "media"      -> mediaEnabled
        "einvoice"   -> einvoiceEnabled
        else         -> true    // Unknown modules are enabled by default
    }
}
```

### Module toggle use cases (new in `shared/domain/.../usecase/admin/`)

```kotlin
// GetModuleConfigUseCase.kt
fun interface GetModuleConfigUseCase {
    suspend operator fun invoke(): AppModuleConfig
}

// SetModuleEnabledUseCase.kt
fun interface SetModuleEnabledUseCase {
    suspend operator fun invoke(moduleName: String, enabled: Boolean): Result<Unit>
}
```

These use cases read/write from `SettingsRepository` (existing Phase 1):

```kotlin
// Implemented via SettingsRepository:
// getModuleConfig(): reads all module.*.enabled keys → AppModuleConfig
// setModuleEnabled(): writes "module.{name}.enabled" = enabled.toString()
```

---

## Module Management Screen

### `ModuleManagementScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.admin.screen

/**
 * Module management screen.
 *
 * Displays a list of toggleable feature modules with:
 * - Module name + description
 * - Toggle switch (enabled/disabled)
 * - Warning badge for modules that require additional configuration
 *   (e.g., E-Invoice requires IRD certificate; Multi-Store requires cloud sync)
 * - System modules (Auth, POS, Inventory, Settings, Register) are marked as "Core"
 *   and cannot be disabled — shown with a lock icon and disabled toggle
 *
 * Toggle action:
 * - Immediately updates the settings DB via SetModuleEnabledUseCase
 * - Shows toast: "{Module} enabled / disabled. Restart may be required for some changes."
 * - Navigation items are re-evaluated on next screen recomposition
 *
 * RBAC: requires Permission.VIEW_ADMIN_PANEL (ADMIN role only).
 */
@Composable
fun ModuleManagementScreen(
    viewModel: AdminViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val moduleConfig = state.moduleConfig

    LaunchedEffect(Unit) {
        viewModel.handleIntent(AdminIntent.LoadModuleConfig)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Module Management") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { /* back */ } }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier.padding(padding)
        ) {
            // Core modules section (read-only)
            item {
                ModuleSectionHeader("Core Modules (Always Active)")
            }
            items(CORE_MODULES) { module ->
                CoreModuleRow(module)
            }

            // Optional modules section
            item {
                ModuleSectionHeader("Optional Modules")
            }
            items(OPTIONAL_MODULES) { module ->
                OptionalModuleRow(
                    module = module,
                    isEnabled = moduleConfig?.isEnabled(module.key) ?: module.defaultEnabled,
                    onToggle = { enabled ->
                        viewModel.handleIntent(AdminIntent.SetModuleEnabled(module.key, enabled))
                    }
                )
            }
        }
    }
}

data class ModuleInfo(
    val key: String,
    val displayName: String,
    val description: String,
    val defaultEnabled: Boolean = true,
    val requiresConfig: Boolean = false,
    val configWarning: String? = null
)

private val CORE_MODULES = listOf(
    ModuleInfo("auth",      "Authentication",   "Login, PIN, session management"),
    ModuleInfo("pos",       "Point of Sale",    "Sales, cart, payment processing"),
    ModuleInfo("inventory", "Inventory",        "Products, categories, stock management"),
    ModuleInfo("settings",  "Settings",         "Store configuration, user management"),
    ModuleInfo("register",  "Cash Register",    "Cash sessions, Z-reports")
)

private val OPTIONAL_MODULES = listOf(
    ModuleInfo("customers",  "Customers & CRM",  "Customer profiles, loyalty accounts"),
    ModuleInfo("coupons",    "Coupons & Promotions", "Discount rules, promotional campaigns"),
    ModuleInfo("expenses",   "Expense Tracking",  "Expense log, P&L statements"),
    ModuleInfo("reports",    "Advanced Reports",  "Sales analytics, stock reports, exports"),
    ModuleInfo("staff",      "Staff & HR",        "Employees, attendance, payroll"),
    ModuleInfo("multistore", "Multi-Store",       "Multi-location management, transfers",
               requiresConfig = true, configWarning = "Requires active cloud sync"),
    ModuleInfo("media",      "Media Manager",     "Product images, media library"),
    ModuleInfo("einvoice",   "E-Invoicing (IRD)", "Sri Lanka IRD e-invoice submission",
               defaultEnabled = false, requiresConfig = true,
               configWarning = "Requires IRD certificate configuration")
)

@Composable
private fun ModuleSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun CoreModuleRow(module: ModuleInfo, modifier: Modifier = Modifier) {
    ZyntaCard(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(module.displayName, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    ZyntaStatusBadge(label = "Core", color = MaterialTheme.colorScheme.tertiary)
                }
                Text(
                    module.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                painterResource("ic_lock"),
                contentDescription = "Always active",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OptionalModuleRow(
    module: ModuleInfo,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ZyntaCard(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(module.displayName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    module.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (module.requiresConfig && module.configWarning != null) {
                    Text(
                        text = "⚠ ${module.configWarning}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF59E0B)
                    )
                }
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}
```

---

## Developer Console Integration

The `:tools:debug` module provides a 6-tab developer console (Seeds, Database, Auth, Network, Diagnostics, UI/UX). In admin builds, it is accessible from the Admin Dashboard.

### Updates to `AdminDashboardScreen.kt` (Sprint 13 addition)

```kotlin
// Added to AdminDashboardScreen's LazyColumn items (debug builds only):
if (BuildConfig.DEBUG) {
    item {
        AdminNavCard(
            title = "Developer Console",
            subtitle = "Seeds, DB browser, network inspector, diagnostics",
            icon = "ic_developer_mode",
            onClick = onNavigateToDeveloperConsole
        )
    }
}
```

### Navigation addition in `MainNavGraph.kt`

```kotlin
// Admin sub-graph additions
composable<ZyntaRoute.ModuleManagement> {
    val vm = koinViewModel<AdminViewModel>()
    ModuleManagementScreen(viewModel = vm, onNavigateBack = { navController.popBackStack() })
}

// Debug console integration (debug build only)
if (BuildConfig.DEBUG) {
    composable<ZyntaRoute.DeveloperConsole> {
        // Delegates to DebugConsoleScreen from :tools:debug module
        DebugConsoleScreen(onNavigateBack = { navController.popBackStack() })
    }
}
```

---

## AdminState and AdminIntent Updates

Add to `AdminState.kt`:

```kotlin
// Add to AdminState
val moduleConfig: AppModuleConfig? = null
```

Add to `AdminIntent.kt`:

```kotlin
// Add to AdminIntent
data object LoadModuleConfig : AdminIntent
data class SetModuleEnabled(val moduleName: String, val enabled: Boolean) : AdminIntent
```

Add to `AdminViewModel.kt`:

```kotlin
is AdminIntent.LoadModuleConfig -> loadModuleConfig()
is AdminIntent.SetModuleEnabled -> setModuleEnabled(intent.moduleName, intent.enabled)

private suspend fun loadModuleConfig() {
    runCatching { getModuleConfig() }
        .onSuccess { config -> updateState { it.copy(moduleConfig = config) } }
        .onFailure { ex -> updateState { it.copy(error = ex.message) } }
}

private suspend fun setModuleEnabled(moduleName: String, enabled: Boolean) {
    setModuleEnabledUseCase(moduleName, enabled).fold(
        onSuccess = {
            loadModuleConfig()   // reload to reflect change
            sendEffect(AdminEffect.ShowSuccess(
                "$moduleName ${if (enabled) "enabled" else "disabled"}"
            ))
        },
        onFailure = { ex -> updateState { it.copy(error = ex.message) } }
    )
}
```

---

## AdminModule.kt Updates

```kotlin
// Additional bindings in adminModule
single<GetModuleConfigUseCase>  { get() }   // reads from SettingsRepository
single<SetModuleEnabledUseCase> { get() }   // writes to SettingsRepository
```

---

## Navigation Route Addition

```kotlin
// In ZyntaRoute.kt (already added in Sprint 7):
data object ModuleManagement : ZyntaRoute()
data object DeveloperConsole : ZyntaRoute()   // debug only
```

---

## Tasks

- [ ] **15.1** Create `AppModuleConfig.kt` domain model in `:shared:domain/model/`
- [ ] **15.2** Create `GetModuleConfigUseCase.kt` and `SetModuleEnabledUseCase.kt` in `shared/domain/.../usecase/admin/`
- [ ] **15.3** Implement module toggle logic in `SettingsRepositoryImpl` (read/write `module.*.enabled` keys)
- [ ] **15.4** Implement `ModuleManagementScreen.kt` with `CoreModuleRow` and `OptionalModuleRow`
- [ ] **15.5** Update `AdminState` / `AdminIntent` / `AdminViewModel` with module config support
- [ ] **15.6** Update `AdminDashboardScreen` to add Module Management nav card (and Developer Console in debug)
- [ ] **15.7** Wire `ModuleManagement` and `DeveloperConsole` routes in `MainNavGraph.kt`
- [ ] **15.8** Update `NavigationItems.kt` to check `AppModuleConfig.isEnabled()` in addition to RBAC
- [ ] **15.9** Write `ModuleConfigViewModelTest` — test toggle enable/disable, config loading
- [ ] **15.10** Verify: `./gradlew :composeApp:feature:admin:assemble && ./gradlew :shared:domain:assemble`

---

## Verification

```bash
./gradlew :shared:domain:assemble
./gradlew :composeApp:feature:admin:assemble
./gradlew :composeApp:feature:admin:test
./gradlew :composeApp:navigation:assemble
```

---

## Definition of Done

- [ ] `AppModuleConfig` domain model created (plain name, ADR-002)
- [ ] `GetModuleConfigUseCase` and `SetModuleEnabledUseCase` created (SAM, no defaults)
- [ ] `ModuleManagementScreen` correctly differentiates core vs optional modules
- [ ] E-Invoice module shows "requires IRD certificate" warning when enabled
- [ ] Developer console accessible from Admin in debug builds
- [ ] Navigation items respect `AppModuleConfig.isEnabled()` check
- [ ] Module toggle tests pass
- [ ] Commit: `feat(admin): add module management screen and developer console integration`
