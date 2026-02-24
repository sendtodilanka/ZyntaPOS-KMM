# ZyntaPOS — Debug Module Implementation Plan

> **Document ID:** ZYNTA-PLAN-DEBUG-v1.0
> **Status:** READY FOR IMPLEMENTATION
> **Scope:** `:tools:debug` — Debug-only developer tooling module
> **Architecture:** KMP (Android + Desktop JVM)
> **Author:** Senior KMP Architect
> **Created:** 2026-02-24
> **Reference Plans:** ZYNTA-PLAN-PHASE1-v1.0 | ADR-001 | ADR-003

---

## Table of Contents

1. [Overview & Goals](#1-overview--goals)
2. [Strict Rules & Constraints](#2-strict-rules--constraints)
3. [Module Structure](#3-module-structure)
4. [Tab Categories & Features](#4-tab-categories--features)
   - [Tab 1: Seeds](#tab-1-seeds)
   - [Tab 2: Database](#tab-2-database)
   - [Tab 3: Auth](#tab-3-auth)
   - [Tab 4: Network](#tab-4-network)
   - [Tab 5: Diagnostics](#tab-5-diagnostics)
   - [Tab 6: UI / UX](#tab-6-ui--ux)
5. [Screen Architecture (MVI)](#5-screen-architecture-mvi)
6. [Navigation Wiring](#6-navigation-wiring)
7. [Koin DI Wiring](#7-koin-di-wiring)
8. [Security Model](#8-security-model)
9. [Implementation Steps](#9-implementation-steps)
10. [Testing Strategy](#10-testing-strategy)
11. [File Checklist](#11-file-checklist)

---

## 1. Overview & Goals

The `:tools:debug` module provides a **debug-only developer console** accessible from within the running app.
It is surfaced as a full-screen `DebugScreen` composed of a **tab-based layout** where each tab groups
related developer actions.

### Goals
- Eliminate all manual DB wipes, log digging, and hardcoded test seeds during development
- Give every developer a self-contained, in-app tool for common debug tasks
- Enforce zero production footprint — the module must be **completely absent** from release builds
- Remain consistent with existing **MVI**, **Clean Architecture**, **Koin DI**, and **RBAC** patterns

---

## 2. Strict Rules & Constraints

| Rule | Enforcement |
|------|-------------|
| **Zero production footprint** | `debugImplementation` only; Gradle conditional include; `expect/actual` no-op for release |
| **No hardcoded credentials** | Passwords collected at runtime via masked `TextField`; never stored or logged |
| **RBAC gated** | `DebugScreen` only renders for `Role.ADMIN`; router guard rejects others |
| **Audit every action** | All `DebugAction` executions write an `AuditEntry` via `AuditRepository` |
| **Destructive confirmation** | Any action tagged `Destructive` shows a typed-confirmation dialog before execution |
| **No domain layer imports in debug** | Debug module depends on `:shared:domain` interfaces only — never on `:shared:data` internals |
| **Follows ADR-001** | `DebugViewModel` extends `BaseViewModel<DebugState, DebugIntent, DebugEffect>` |
| **Secrets plugin** | Feature flags (`ENABLE_DEBUG_SCREEN`) injected via Secrets plugin from `local.properties` |

---

## 3. Module Structure

```
:tools:debug
└── src/
    ├── commonMain/kotlin/com/zyntasolutions/zyntapos/debug/
    │   ├── DebugScreen.kt               ← Tab host + entry point
    │   ├── DebugViewModel.kt            ← Single MVI VM for all tabs
    │   ├── DebugState.kt               ← Immutable UI state
    │   ├── DebugIntent.kt              ← Sealed intent hierarchy
    │   ├── DebugEffect.kt              ← One-shot effects (toast, navigation)
    │   ├── DebugModule.kt              ← Koin bindings (debug only)
    │   ├── components/
    │   │   ├── DebugTabRow.kt          ← Scrollable tab row component
    │   │   ├── ConfirmDestructiveDialog.kt
    │   │   ├── PasswordInputDialog.kt
    │   │   └── ActionResultBanner.kt
    │   └── tabs/
    │       ├── SeedsTab.kt
    │       ├── DatabaseTab.kt
    │       ├── AuthTab.kt
    │       ├── NetworkTab.kt
    │       ├── DiagnosticsTab.kt
    │       └── UiUxTab.kt
    │
    ├── androidMain/kotlin/.../debug/
    │   └── DebugNavGraph.actual.kt     ← Registers route in debug builds
    │
    ├── jvmMain/kotlin/.../debug/
    │   └── DebugNavGraph.actual.kt     ← Desktop variant
    │
    └── commonTest/kotlin/.../debug/
        ├── DebugViewModelTest.kt
        └── tabs/
            ├── SeedsTabTest.kt
            ├── DatabaseTabTest.kt
            └── AuthTabTest.kt

:composeApp/navigation/
└── commonMain/.../DebugNavGraph.kt     ← expect declaration (no-op in release actual)
```

### Gradle wiring

```kotlin
// settings.gradle.kts
// Included conditionally — absent from release build graph
val isDebugBuild = gradle.startParameter.taskNames.none { "release" in it.lowercase() }
if (isDebugBuild) {
    include(":tools:debug")
}

// androidApp/build.gradle.kts
dependencies {
    debugImplementation(project(":tools:debug"))
}

// composeApp/build.gradle.kts
dependencies {
    debugImplementation(project(":tools:debug"))
}
```

---

## 4. Tab Categories & Features

The `DebugScreen` uses a **horizontally scrollable `TabRow`** with 6 tabs.
Each tab is an independent Composable receiving state slices and dispatching intents.

```
┌──────────────────────────────────────────────────────────────┐
│  🛠 Debug Console                            [ADMIN badge]   │
├──────────────────────────────────────────────────────────────┤
│ [Seeds] [Database] [Auth] [Network] [Diagnostics] [UI / UX] │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│   < tab content >                                            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

### Tab 1: Seeds

**Purpose:** Manage seed data profiles without hardcoded values.

| Feature | Description | Destructive? |
|---------|-------------|:------------:|
| Select seed profile | Dropdown: Demo / Retail / Restaurant / Custom | — |
| Run selected profile | Inserts missing records (idempotent) | — |
| Clear seed data | Removes all records inserted by seeder | ✓ |
| Admin account setup | Masked password field; creates ADMIN user at runtime | — |
| View seed status | Shows inserted / skipped / failed counts per entity | — |
| Custom seed file | Load a `*.seed.json` from device storage / file picker | — |

**Intent slice:**
```kotlin
sealed class DebugIntent {
    data class RunSeedProfile(val profile: SeedProfile) : DebugIntent()
    object ClearSeedData : DebugIntent()
    data class SetAdminCredentials(val email: String, val password: String) : DebugIntent()
    data class LoadCustomSeedFile(val path: String) : DebugIntent()
}
```

**No hardcoded credentials rule:** `SetAdminCredentials` is collected exclusively from a
runtime `PasswordInputDialog` — the value is passed directly to `UserRepository.create()` and
never stored in state, logs, or `BuildConfig`.

---

### Tab 2: Database

**Purpose:** Inspect and manage the local SQLDelight + SQLCipher database.

| Feature | Description | Destructive? |
|---------|-------------|:------------:|
| Reset database | Drops all tables, recreates schema | ✓ |
| Export database | Copies `.db` file to Downloads / Desktop export path | — |
| Run pending migrations | Force-executes any queued schema migrations | — |
| View table row counts | Live counts per table (read-only query) | — |
| Clear specific table | Truncate a single selected table | ✓ |
| Vacuum database | Runs SQLite `VACUUM` to reclaim space | — |
| Toggle WAL mode | Enables / disables Write-Ahead Logging | — |

**Confirmation pattern for destructive actions:**
```
User selects "Reset Database"
  → ConfirmDestructiveDialog shown
  → User types "RESET" to unlock confirm button
  → DebugIntent.ResetDatabase dispatched
  → AuditRepository.log("DEBUG: database reset by ${currentUser.email}")
  → DatabaseFactory.dropAndRecreate()
```

---

### Tab 3: Auth

**Purpose:** Debug authentication flows and role switching without real user switching overhead.

| Feature | Description | Destructive? |
|---------|-------------|:------------:|
| View current session | Displays userId, role, token expiry, PIN set status | — |
| Clear session | Logs out current user and clears JWT from SecureKeyStorage | ✓ |
| Impersonate role | Temporarily overrides role in-session (ADMIN / MANAGER / CASHIER / STAFF) | — |
| Force token expiry | Sets token expiry to now — triggers auth refresh flow | — |
| Reset PIN | Clears stored PIN hash for current user | ✓ |
| Create test user | Creates a user with selected role; runtime password input | — |
| List all users | Shows username, role, last-login (no password hashes) | — |

**Role impersonation rule:** Impersonation is in-memory only (`DebugState.impersonatedRole`);
it does NOT write to `UserRepository` or `SecureKeyStorage`. Cleared on screen exit.

---

### Tab 4: Network

**Purpose:** Inspect and control Ktor API client and offline sync behaviour.

| Feature | Description | Destructive? |
|---------|-------------|:------------:|
| View base URL | Shows current `ApiService` base URL from `BuildConfig` | — |
| Toggle offline mode | Overrides `NetworkMonitor` to report no connectivity | — |
| View sync queue | Lists all `pending_operations` rows with status | — |
| Clear sync queue | Deletes all pending sync operations | ✓ |
| Force sync now | Triggers `SyncEngine.syncNow()` regardless of connectivity state | — |
| Simulate network error | Injects a Ktor mock interceptor returning 500 for next N requests | — |
| View last API response | Shows the most recent raw JSON response (debug log) | — |

---

### Tab 5: Diagnostics

**Purpose:** Inspect runtime health, logs, and audit trail.

| Feature | Description | Destructive? |
|---------|-------------|:------------:|
| View audit log | Paginated list of `AuditEntry` records | — |
| Export audit log | CSV export of audit entries | — |
| View Kermit logs | In-app log viewer (INFO / WARN / ERROR filter) | — |
| Export logs | Writes Kermit log buffer to a `.txt` file | — |
| System health snapshot | DB file size, WAL size, sync queue depth, memory, last sync time | — |
| View pending operations | Shows queued background operations from `WorkManager` / coroutines | — |
| Clear Kermit log buffer | Clears in-memory log ring buffer | ✓ |
| App version info | Displays `BuildConfig.VERSION_NAME`, `VERSION_CODE`, build type, git SHA | — |

---

### Tab 6: UI / UX

**Purpose:** Test UI rendering across themes, locales, and window sizes without device switching.

| Feature | Description | Destructive? |
|---------|-------------|:------------:|
| Toggle theme | Force Light / Dark / System | — |
| Override locale | Select from supported locales (en, si, ta) to test i18n strings | — |
| Simulate window size | Override window size class: Compact / Medium / Expanded | — |
| Toggle dynamic colour | Enable / disable Material 3 dynamic colour | — |
| Show layout bounds | Overlay composable bounds (debug draw) | — |
| Show recomposition count | Overlay recomposition hit counts per composable | — |
| Font scale override | Override system font scale (0.85× / 1.0× / 1.15× / 1.3×) | — |
| Navigation style | Force Bottom Bar / Rail regardless of window size | — |

**Implementation note:** All UI overrides are stored in `DebugState` and injected into
`LocalDebugOverrides` CompositionLocal — they affect only the running session and never
persist to `SettingsRepository`.

---

## 5. Screen Architecture (MVI)

Following ADR-001, `DebugViewModel` extends `BaseViewModel`.

```kotlin
// DebugState.kt
data class DebugState(
    val activeTab: DebugTab = DebugTab.Seeds,
    val isLoading: Boolean = false,
    val lastActionResult: ActionResult? = null,

    // Seeds
    val selectedProfile: SeedProfile = SeedProfile.Demo,
    val seedResult: SeedRunResult? = null,

    // Database
    val tableRowCounts: Map<String, Long> = emptyMap(),

    // Auth
    val currentSession: SessionSnapshot? = null,
    val impersonatedRole: Role? = null,
    val allUsers: List<UserSummary> = emptyList(),

    // Network
    val isOfflineModeForced: Boolean = false,
    val syncQueueDepth: Int = 0,
    val lastApiResponse: String? = null,

    // Diagnostics
    val auditEntries: List<AuditEntry> = emptyList(),
    val systemHealth: SystemHealthSnapshot? = null,
    val logLines: List<LogLine> = emptyList(),

    // UI/UX
    val themeOverride: ThemeOverride = ThemeOverride.System,
    val localeOverride: String? = null,
    val windowSizeOverride: WindowSizeClass? = null,
    val fontScaleOverride: Float? = null,
)

enum class DebugTab { Seeds, Database, Auth, Network, Diagnostics, UiUx }
```

```kotlin
// DebugEffect.kt
sealed class DebugEffect {
    data class ShowToast(val message: String) : DebugEffect()
    data class ShowConfirmDialog(val action: DebugIntent, val confirmWord: String) : DebugEffect()
    object NavigateToLogin : DebugEffect()     // after ClearSession
    data class ShareFile(val path: String) : DebugEffect()
}
```

---

## 6. Navigation Wiring

```kotlin
// :composeApp:navigation — expect declaration
// commonMain/kotlin/.../DebugNavGraph.kt
expect fun NavGraphBuilder.debugNavGraph(navController: NavigationController)

// ZyntaRoute.kt — add one route
sealed class ZyntaRoute {
    // ... existing routes
    data object Debug : ZyntaRoute()   // admin-only
}
```

```kotlin
// :tools:debug — actual (Android + JVM share same implementation via commonMain)
// DebugNavGraph.actual.kt
actual fun NavGraphBuilder.debugNavGraph(navController: NavigationController) {
    composable<ZyntaRoute.Debug> {
        val role = LocalCurrentUserRole.current
        if (role != Role.ADMIN) {
            LaunchedEffect(Unit) { navController.popBackStack() }
            return@composable
        }
        val vm: DebugViewModel = koinViewModel()
        DebugScreen(viewModel = vm, onNavigateUp = { navController.popBackStack() })
    }
}

// Release actual (in :composeApp:navigation release source set)
actual fun NavGraphBuilder.debugNavGraph(navController: NavigationController) = Unit
```

```kotlin
// Entry point — add to settings gear or hidden long-press in AdminScreen
// Only visible when BuildConfig.ENABLE_DEBUG_SCREEN == true
if (BuildConfig.ENABLE_DEBUG_SCREEN) {
    DebugMenuEntry(onClick = { navController.navigate(ZyntaRoute.Debug) })
}
```

---

## 7. Koin DI Wiring

```kotlin
// DebugModule.kt
val debugModule = module {
    // ViewModel
    viewModel {
        DebugViewModel(
            seedRunner         = get(),
            databaseManager    = get(),
            authRepository     = get(),
            userRepository     = get(),
            auditRepository    = get(),
            syncRepository     = get(),
            settingsRepository = get(),
            networkMonitor     = get(),
            logger             = get(),
        )
    }

    // Action handlers (thin wrappers over existing repositories)
    factory<SeedActionHandler>      { SeedActionHandlerImpl(get(), get()) }
    factory<DatabaseActionHandler>  { DatabaseActionHandlerImpl(get()) }
    factory<AuthActionHandler>      { AuthActionHandlerImpl(get(), get()) }
    factory<NetworkActionHandler>   { NetworkActionHandlerImpl(get(), get()) }
    factory<DiagnosticsHandler>     { DiagnosticsHandlerImpl(get(), get()) }
}
```

```kotlin
// Loaded in debug entry points only:

// AndroidApp (debug variant Application class)
startKoin {
    modules(
        coreModule, securityModule, halModule(),
        androidDataModule, dataModule, navigationModule,
        /* feature modules... */,
        debugModule       // ← appended last
    )
}

// Desktop main() debug build
startKoin {
    modules(
        coreModule, securityModule, halModule(),
        desktopDataModule, dataModule, navigationModule,
        /* feature modules... */,
        debugModule       // ← appended last
    )
}
```

---

## 8. Security Model

```
┌─────────────────────────────────────────────────────┐
│  Access Control                                     │
│                                                     │
│  BuildConfig.ENABLE_DEBUG_SCREEN = false  (release) │
│    → DebugNavGraph.actual = no-op                   │
│    → :tools:debug not on classpath                  │
│    → Route never registered                         │
│                                                     │
│  BuildConfig.ENABLE_DEBUG_SCREEN = true   (debug)   │
│    → Route registered                               │
│    → RBAC check: Role.ADMIN only                    │
│    → Audit log: every action written                │
│    → Destructive: typed-confirmation required       │
│    → Credentials: runtime TextField only            │
└─────────────────────────────────────────────────────┘
```

### Credential Handling Policy
1. Password collected in `PasswordInputDialog` using `KeyboardType.Password` + `VisualTransformation.Password`
2. Passed as a transient `String` directly to the use case / repository method call
3. Intent carries the value but `DebugState` **never stores credentials**
4. ViewModel processes intent and discards the value immediately after the use case call
5. Kermit logger is **excluded** from logging any intent carrying a password field

---

## 9. Implementation Steps

### Step 1 — Module scaffold
- [ ] Create `:tools:debug` directory structure
- [ ] Add `build.gradle.kts` with `kotlinMultiplatform`, `composeMultiplatform`, `debugImplementation` deps
- [ ] Register conditionally in `settings.gradle.kts`
- [ ] Add `ENABLE_DEBUG_SCREEN=true` to `local.properties` and `false` to release variant config

### Step 2 — MVI skeleton
- [ ] Create `DebugState`, `DebugIntent`, `DebugEffect`, `DebugTab` enum
- [ ] Create `DebugViewModel` extending `BaseViewModel<DebugState, DebugIntent, DebugEffect>`
- [ ] Create `DebugModule` (Koin bindings)
- [ ] Wire `debugModule` into debug-flavoured entry points

### Step 3 — Navigation
- [ ] Add `ZyntaRoute.Debug` to `ZyntaRoute.kt`
- [ ] Create `expect fun NavGraphBuilder.debugNavGraph(...)` in `:composeApp:navigation`
- [ ] Implement `actual` in `:tools:debug` (commonMain — shared across Android + Desktop)
- [ ] Implement release `actual` no-op in `:composeApp:navigation` release source set
- [ ] Add `DebugMenuEntry` to `AdminScreen` (gated by `BuildConfig.ENABLE_DEBUG_SCREEN`)

### Step 4 — Shared components
- [ ] `DebugTabRow` — scrollable `TabRow` with 6 tabs + icon per tab
- [ ] `ConfirmDestructiveDialog` — typed-word confirmation, confirm button disabled until word matched
- [ ] `PasswordInputDialog` — masked input, submit on keyboard action, never exposes value outside dialog
- [ ] `ActionResultBanner` — success/error snackbar-style banner shown after each action

### Step 5 — Tab implementations (one per tab)
- [ ] **SeedsTab** — profile dropdown, run button, status card, admin setup section
- [ ] **DatabaseTab** — table count grid, action buttons with destructive guards
- [ ] **AuthTab** — session info card, role chip selector, user list
- [ ] **NetworkTab** — offline toggle, sync queue depth, force-sync button, response viewer
- [ ] **DiagnosticsTab** — system health grid, log viewer with filter chips, export buttons
- [ ] **UiUxTab** — theme toggle, locale dropdown, window size chip group, font scale slider

### Step 6 — Action handlers
- [ ] `SeedActionHandler` — wraps `SeedRunner`; reports `SeedRunResult`
- [ ] `DatabaseActionHandler` — wraps `DatabaseFactory`; table queries, vacuum, WAL toggle
- [ ] `AuthActionHandler` — wraps `AuthRepository`, `UserRepository`, `SecureKeyStorage`
- [ ] `NetworkActionHandler` — wraps `NetworkMonitor`, `SyncEngine`, `SyncRepository`
- [ ] `DiagnosticsHandler` — wraps `AuditRepository`, Kermit log buffer, `DatabaseFactory` stats

### Step 7 — Tests
- [ ] `DebugViewModelTest` — all intents, state transitions, effect emissions
- [ ] Per-tab action handler tests with hand-rolled fakes
- [ ] Confirm destructive gate test (verify action NOT executed without confirmation)

---

## 10. Testing Strategy

Consistent with existing test patterns (hand-rolled fakes, Turbine, `runTest`):

```kotlin
class DebugViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val fakeSeedRunner = FakeSeedRunner()
    private val fakeDatabaseManager = FakeDatabaseManager()
    private val fakeAuditRepository = FakeAuditRepository()

    @Test
    fun `RunSeedProfile inserts records and emits result state`() = runTest {
        fakeSeedRunner.resultToReturn = SeedRunResult(inserted = 48, skipped = 0, failed = 0)
        viewModel.send(DebugIntent.RunSeedProfile(SeedProfile.Demo))
        advanceUntilIdle()
        assertEquals(48, viewModel.state.value.seedResult?.inserted)
        assertTrue(fakeAuditRepository.lastEntry?.message?.contains("seed") == true)
    }

    @Test
    fun `ResetDatabase without confirmation does NOT execute`() = runTest {
        // Intent must go through ConfirmDestructiveDialog — direct dispatch rejected
        viewModel.effects.test {
            viewModel.send(DebugIntent.ResetDatabase)
            val effect = awaitItem()
            assertIs<DebugEffect.ShowConfirmDialog>(effect)
        }
        assertFalse(fakeDatabaseManager.wasReset)
    }

    @Test
    fun `SetAdminCredentials is never stored in state`() = runTest {
        viewModel.send(DebugIntent.SetAdminCredentials("admin@test.com", "s3cr3t"))
        advanceUntilIdle()
        // State must not contain any credential field
        val state = viewModel.state.value
        assertNull(state.toString().takeIf { "s3cr3t" in it })
    }
}
```

---

## 11. File Checklist

```
:tools:debug
  commonMain
    DebugScreen.kt
    DebugViewModel.kt
    DebugState.kt
    DebugIntent.kt
    DebugEffect.kt
    DebugModule.kt
    components/
      DebugTabRow.kt
      ConfirmDestructiveDialog.kt
      PasswordInputDialog.kt
      ActionResultBanner.kt
    tabs/
      SeedsTab.kt
      DatabaseTab.kt
      AuthTab.kt
      NetworkTab.kt
      DiagnosticsTab.kt
      UiUxTab.kt
    actions/
      SeedActionHandler.kt          (interface + impl)
      DatabaseActionHandler.kt      (interface + impl)
      AuthActionHandler.kt          (interface + impl)
      NetworkActionHandler.kt       (interface + impl)
      DiagnosticsHandler.kt         (interface + impl)
  commonTest
    DebugViewModelTest.kt
    tabs/
      SeedsTabTest.kt
      DatabaseTabTest.kt
      AuthTabTest.kt

:composeApp:navigation
  commonMain
    DebugNavGraph.kt               (expect)
  releaseMain
    DebugNavGraph.release.kt       (actual no-op)

:tools:debug
  commonMain
    DebugNavGraph.actual.kt        (actual — shared Android+Desktop)
```

---

## Summary

| Aspect | Decision |
|--------|----------|
| **Module** | `:tools:debug` — `debugImplementation` only |
| **UI** | `TabRow` with 6 scrollable tabs |
| **Architecture** | MVI — `BaseViewModel` (ADR-001 compliant) |
| **DI** | Koin `debugModule` loaded only in debug entry points |
| **Access control** | `BuildConfig.ENABLE_DEBUG_SCREEN` + `Role.ADMIN` RBAC guard |
| **Credentials** | Runtime `PasswordInputDialog` only; never in state/logs/config |
| **Destructive actions** | Typed-word `ConfirmDestructiveDialog` required |
| **Audit** | Every action logged to `AuditRepository` |
| **Tabs** | Seeds · Database · Auth · Network · Diagnostics · UI/UX |
| **Tests** | Hand-rolled fakes + Turbine; credential-in-state assertion |
