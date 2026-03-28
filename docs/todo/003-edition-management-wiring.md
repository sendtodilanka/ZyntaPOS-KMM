# TODO-003: Edition Management — Nav Graph Wiring & Production Readiness

**Status:** Completed
**Completed:** 2026-03-02
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

## Enterprise License Architecture — Industry Research & Recommendations

This section documents the research and architectural decisions behind ZyntaPOS's license model, using proven patterns from world-leading POS systems and enterprise software as reference. It informs the TODO-007 server-side implementation.

### The Multi-Outlet Scenario (Reference Case)

> **1 corporate entity → 6 retail outlets → 5 POS terminals per outlet = 30 active terminals**

This is the canonical enterprise deployment ZyntaPOS must support. All license design decisions below are validated against this scenario.

---

### How World-Leading POS & Enterprise Software Handle This

#### Oracle MICROS (Hospitality / Retail Enterprise)
- **Hierarchical model:** Enterprise License → Site License → Terminal License — 3 independent tiers
- Each site receives a **site activation key**; each terminal gets a **terminal license token** bound to its hardware
- License validation requires a periodic **heartbeat to Oracle License Server**; offline mode continues operating but flags unchecked status
- **Hardware binding:** license token is cryptographically tied to motherboard serial + NIC MAC address
- Central admin console manages all properties/sites from a single pane of glass

#### Revel Systems (iPad POS, Multi-location)
- **Per-terminal SaaS** subscription model (~$99/terminal/month)
- Management Console hierarchy: Revel HQ → Location → Terminal
- Terminal licenses are **device-bound JWTs** stored in iOS Keychain
- **Online validation on every app launch** with a **72-hour offline grace period**
- Remote terminal deactivation available from the admin portal in real time

#### Toast POS (Restaurant / Retail)
- **Flat unlimited terminal pricing** per location — number of terminals does not affect price
- Central Toast Admin portal manages all locations under one corporate account
- Each terminal receives a **pairing code** from the backend on first launch; device registers and receives a signed token
- Token payload: `{location_id, terminal_id, edition, features[], expiry}` — signed by Toast's private key
- Hardware fingerprint included to prevent token cloning between devices

#### Lightspeed POS (Retail Chains)
- **Per-location subscription** with unlimited terminals per location
- Single admin sign-on for the chain's IT admin across all locations
- License token is a **JWT signed with RS256**, verified locally using a public key embedded in the app
- **30-day offline grace period** before features degrade — the most generous in the industry

#### Microsoft / Adobe / JetBrains (Enterprise Software — Non-POS Reference)
- **Named user vs concurrent (floating) licenses** — fundamental distinction to understand
  - *Named*: one license = one specific user/device; simple, predictable
  - *Floating/Concurrent*: pool of N licenses shared by M > N users; efficient but requires always-on license server
- **Corporate agreements** at the top tier, sub-assigned to departments, sites, or seats
- Admin console controls activation, revocation, and seat reassignment centrally
- **Machine binding or user identity binding** — not both simultaneously (prevents abuse)
- Concurrent (floating) model: if all slots are consumed, new logins are rejected (FlexNet / Adobe Admin Console)

---

### Recommended 3-Tier License Hierarchy for ZyntaPOS

All tokens are **RS256-signed JWTs** — private key held exclusively by `license.zyntapos.com`; public key embedded in the app for local verification without a server round-trip.

```
Corporate License Token
  └── Outlet License Token  (one per retail location)
        └── Terminal License Token  (one per POS device, hardware-bound)
```

#### Corporate Token (held by chain's IT admin)
```json
{
  "iss": "license.zyntapos.com",
  "sub": "corp_<uuid>",
  "edition": "ENTERPRISE",
  "max_outlets": 6,
  "max_terminals_per_outlet": 5,
  "features": ["ALL"],
  "issued_at": "2026-01-01T00:00:00Z",
  "expires_at": "2027-01-01T00:00:00Z",
  "sig": "<RS256 signature>"
}
```

#### Outlet Token (per retail location)
```json
{
  "corporate_id": "corp_<uuid>",
  "outlet_id": "outlet_<uuid>",
  "max_terminals": 5,
  "location_name": "Colombo Branch 01",
  "timezone": "Asia/Colombo",
  "expires_at": "2027-01-01T00:00:00Z",
  "sig": "<RS256 signature>"
}
```

#### Terminal Token (device-bound, stored in Android Keystore)
```json
{
  "outlet_id": "outlet_<uuid>",
  "terminal_id": "term_<uuid>",
  "device_fingerprint": "<sha256(android_id + hardware_serial)>",
  "activated_at": "2026-03-01T00:00:00Z",
  "expires_at": "2027-01-01T00:00:00Z",
  "sig": "<RS256 signature>"
}
```

> **Cross-reference:** TODO-007 (lines 212–252) defines the Kotlin data models (`License`, `Edition`, `LicenseStatus`) and heartbeat payload that back these tokens.

---

### Security Layer

| Threat | Mitigation |
|--------|-----------|
| Token cloning between devices | Hardware fingerprint binding (Android ID + hardware serial, SHA-256) |
| Token tampering | RS256 JWT signature — verified locally using embedded public key |
| Offline `feature_configs` manipulation | Table HMAC integrity check on every read (Phase 1) |
| License key sharing between outlets | Each outlet token contains unique `outlet_id` + terminal count cap |
| Man-in-the-middle on license server | SPKI certificate pinning on `license.zyntapos.com` (strategy: ADR-011) |
| Private key extraction | Server holds private key — app holds only the public key (can verify, cannot forge) |
| Clock manipulation to extend expiry | Server-issued timestamps + NTP cross-check; offline grace is hard-capped at 14 days |

---

### Online vs Offline Strategy

ZyntaPOS is offline-first. License validation **must never require constant connectivity**.

> **Cross-reference:** TODO-007 (lines 269–277) already defines the Kotlin grace period logic. This table is the product/UX specification for it.

| State | Duration | Behavior |
|-------|---------|---------|
| Online + valid token | N/A | Full operation; heartbeat sent every 24h |
| Offline | 0–7 days | Full operation; no warnings shown |
| Offline | 7–14 days | Full operation; persistent warning banner shown |
| Offline | > 14 days | STANDARD features only; Premium/Enterprise locked |
| License expired | — | STANDARD features only; no data loss; renewal CTA displayed |
| Invalid device fingerprint | — | App blocked: "This terminal is not activated on this device" |
| Remote deactivation received | — | Graceful logout on next sync; cannot start new transactions |

---

### Fixed vs Floating Terminal Licenses

| Model | How It Works | Verdict for ZyntaPOS |
|-------|-------------|----------------------|
| **Fixed** | Each device gets one permanent token | ✅ **Recommended** — simple, offline-safe |
| **Floating / Concurrent** | Pool of N slots; any device can occupy a slot | ❌ **Not suitable** — requires real-time server contact to claim/release slots; incompatible with offline-first |

**Decision:** ZyntaPOS uses fixed terminal licenses. For the 6-outlet / 30-terminal chain, this means exactly 30 terminal tokens are issued — one per Android device, stored in Android Keystore.

---

### Admin Console Features (Corporate IT Admin)

The chain's IT admin requires a web-based **Corporate Admin Console** (outside the POS app itself; part of the Zynta backend infrastructure):

| Feature | Description |
|---------|-------------|
| All-outlet dashboard | Live view of all 6 outlets, terminal count, online/offline status, last heartbeat timestamp |
| Terminal activation | Generate activation codes, assign to outlets, track activated vs unactivated terminals |
| Remote deactivation | Kill switch for a terminal (e.g., stolen device); propagated on next sync |
| License transfer | Move a terminal license from Outlet A to Outlet B (e.g., for temporary overflow POS) |
| Expiry alerts | Automated reminders at 90 / 60 / 30 days before license expiry |
| Usage analytics | Which features are actively used per terminal, transaction volumes per outlet |
| Audit log | Full trail of activation/deactivation events with timestamps and admin identity |

---

### Pricing Model Recommendation (ENTERPRISE Multi-Location)

```
ENTERPRISE Multi-Location Plan — Example Pricing
──────────────────────────────────────────────────────────
Corporate seat (chain-level admin access):   $500 / year
Per outlet:                                  $300 / outlet × 6  =  $1,800 / year
Per terminal:                                 $80 / terminal × 30 = $2,400 / year
──────────────────────────────────────────────────────────
Total annual:                                              $4,700 / year
──────────────────────────────────────────────────────────
Includes: All 23 feature flags, multi-store KPI dashboard,
          inter-store transfers, e-invoicing, staff/HR,
          admin console, unlimited software updates, support
```

This is aligned with Lightspeed (~$119/location/month) and Revel (~$99/terminal/month) pricing for equivalent enterprise tiers.

---

### ZyntaPOS License Implementation Roadmap

| Phase | Scope | Status |
|-------|-------|--------|
| **Phase 1 (Now)** | Wire EditionManagementScreen (this TODO); add `feature_configs` HMAC tamper detection | TODO-003 |
| **Phase 2** | License server on `license.zyntapos.com`; RS256 token issuance; terminal activation flow on first launch; 14-day offline grace enforcement | TODO-007 |
| **Phase 3** | Corporate Admin Console (web); license transfer between outlets; remote deactivation; usage analytics pipeline; floating license option (optional) | Future |

---

## Validation Checklist

- [x] `editionManagement` added to `MainNavScreens` data class
- [x] `MainNavGraph.kt` placeholder replaced with real `screens.editionManagement()` call
- [x] `App.kt` wires `EditionManagementScreen` to the `editionManagement` lambda
- [x] `NavGraphCompletenessTest` updated — no longer excluded as placeholder
- [x] Settings screen includes "Edition Management" in its menu items
- [x] App compiles and runs without errors
- [x] Edition Management screen navigates correctly from Settings
- [x] Feature toggles work for Premium/Enterprise features
- [x] Standard features show as always-enabled (disabled switch)
- [x] Back navigation returns to Settings screen
- [x] No "Coming soon" text anywhere in the app
