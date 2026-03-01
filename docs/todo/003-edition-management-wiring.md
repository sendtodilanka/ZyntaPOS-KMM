# TODO-003: Edition Management — Nav Graph Wiring & Production Readiness

**Status:** In Progress
**Priority:** HIGH — Last placeholder in the entire nav graph; blocking production readiness
**Phase:** Phase 1 (MVP) — Must be completed before final testing
**Created:** 2026-03-01

---

## Problem Statement

The `EditionManagementScreen` and `EditionManagementViewModel` are **fully implemented** in `:composeApp:feature:settings/edition/`, and the ViewModel is registered in Koin (`SettingsModule.kt:63`). However, the screen is **not wired** into the navigation graph.

**Current state at `MainNavGraph.kt:332-335`:**
```kotlin
composable<ZyntaRoute.EditionManagement> {
    // Placeholder: EditionManagementScreen will be wired by Agent 7
    Text("Edition Management — Coming soon") // temporary
}
```

This is the **only remaining placeholder** across all 16 feature modules and 60+ routes in the entire app.

---

## What Already Exists (Fully Implemented)

### EditionManagementViewModel
**File:** `composeApp/feature/settings/src/commonMain/.../edition/EditionManagementViewModel.kt`
- Extends `BaseViewModel<EditionManagementState, EditionManagementIntent, EditionManagementEffect>`
- Loads all 23 `FeatureConfig` rows reactively via `GetAllFeatureConfigsUseCase`
- Toggle feature flags via `SetFeatureEnabledUseCase`
- STANDARD features protected (cannot be disabled — enforced by use case)
- Effects: `ShowError`, `ShowSuccess`

### EditionManagementScreen
**File:** `composeApp/feature/settings/src/commonMain/.../edition/EditionManagementScreen.kt`
- Full Compose UI with `LazyColumn` grouped by edition tier (Standard / Premium / Enterprise)
- Section headers with `MaterialTheme.typography.titleMedium`
- `FeatureToggleRow` composable: `ListItem` with `Switch`, disabled for STANDARD features
- Snackbar for success/error effects
- Back navigation via `onNavigateBack` callback
- ViewModel injected via `koinViewModel()`

### Koin Registration
**File:** `composeApp/feature/settings/src/commonMain/.../SettingsModule.kt:63`
```kotlin
viewModelOf(::EditionManagementViewModel)
```

### Domain Models (Already Exist)
- `ZyntaFeature` — 23-member enum of all feature flags
- `ZyntaEdition` — `STANDARD`, `PREMIUM`, `ENTERPRISE`
- `FeatureConfig` — data class with `feature`, `isEnabled`, `enabledAt`, `disabledAt`
- `GetAllFeatureConfigsUseCase` — returns `Flow<List<FeatureConfig>>`
- `SetFeatureEnabledUseCase` — toggle with STANDARD guard

### SQLDelight Schema (Already Exists)
- `feature_configs` table with all 23 feature flags seeded

---

## What Needs to Be Done

### 1. Add `editionManagement` to `MainNavScreens`

**File:** `composeApp/navigation/src/commonMain/.../MainNavScreens.kt`

Add after `rbacManagement`:
```kotlin
val editionManagement: @Composable (
    onNavigateUp: () -> Unit,
) -> Unit,
```

### 2. Wire in `MainNavGraph.kt`

**File:** `composeApp/navigation/src/commonMain/.../MainNavGraph.kt`

Replace lines 332-336:
```kotlin
composable<ZyntaRoute.EditionManagement> {
    screens.editionManagement(
        { navigationController.navigateUp(ZyntaRoute.Settings) },
    )
}
```

### 3. Wire in `App.kt`

**File:** `composeApp/src/commonMain/.../App.kt`

Add after `rbacManagement` lambda:
```kotlin
editionManagement = { onNavigateUp ->
    EditionManagementScreen(
        onNavigateBack = onNavigateUp,
    )
},
```

### 4. Update `NavGraphCompletenessTest`

**File:** `composeApp/navigation/src/commonTest/.../NavGraphCompletenessTest.kt`

Remove `editionManagement` from the placeholder exclusion list (lines 124-125) and add it to the wired screen assertions.

### 5. Add Settings Navigation Route

Verify that the Settings screen routes to `EditionManagement` when the edition management item is tapped. Check `SettingsScreen.kt` for the settings menu items list and ensure `EditionManagement` is included.

---

## Edition Tiers (Reference)

| Edition | Features Included | Target Customer |
|---------|-------------------|-----------------|
| **STANDARD** | Core POS, basic inventory, register, basic reports | Small single-store shops |
| **PREMIUM** | + CRM, coupons, advanced reports, expenses, media | Growing businesses |
| **ENTERPRISE** | + Multi-store, accounting, e-invoicing, staff/HR, admin | Large multi-location |

### Feature Flags (23 Total — from `ZyntaFeature` enum)

**STANDARD (always enabled, cannot be toggled):**
- POS, Inventory, Register, Basic Reports, Settings, Auth, Dashboard

**PREMIUM (toggleable):**
- CRM, Coupons, Expenses, Advanced Reports, Media, Barcode Label Printing

**ENTERPRISE (toggleable):**
- Multi-store, Accounting, E-Invoicing, Staff/HR, Admin Console, Stocktake, Warehouse Racks, Financial Statements

---

## Connection to License System (TODO-007)

The Edition Management screen is the **client-side counterpart** to the server-side license system described in TODO-007. The flow is:

1. Customer purchases a license (STARTER / PROFESSIONAL / ENTERPRISE edition)
2. License server validates and returns the allowed feature set
3. POS app updates `feature_configs` table based on license
4. `EditionManagementScreen` displays current feature status
5. For ADMIN: shows which features are enabled/disabled by their license tier
6. Premium/Enterprise features can be toggled (for trial/demo purposes, or if license allows)

In production, the toggle capability on this screen should be **controlled by the license**:
- If license is STARTER, Premium/Enterprise toggles should be disabled with "Upgrade required" message
- If license is PROFESSIONAL, Enterprise toggles should be disabled
- If license is ENTERPRISE, all toggles are available

This license-gating logic will be implemented when TODO-007 (Infrastructure & Deployment / License System) is built.

---

## Validation Checklist

- [ ] `editionManagement` added to `MainNavScreens` data class
- [ ] `MainNavGraph.kt` placeholder replaced with real `screens.editionManagement()` call
- [ ] `App.kt` wires `EditionManagementScreen` to the `editionManagement` lambda
- [ ] `NavGraphCompletenessTest` updated — no longer excluded as placeholder
- [ ] Settings screen includes "Edition Management" in its menu items
- [ ] App compiles and runs without errors
- [ ] Edition Management screen navigates correctly from Settings
- [ ] Feature toggles work for Premium/Enterprise features
- [ ] Standard features show as always-enabled (disabled switch)
- [ ] Back navigation returns to Settings screen
- [ ] No "Coming soon" text anywhere in the app
