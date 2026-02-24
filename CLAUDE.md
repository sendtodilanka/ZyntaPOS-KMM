# CLAUDE.md — ZyntaPOS KMM

This file gives AI assistants the context needed to work effectively in this codebase. Read it before making any changes.

---

## Project Overview

**ZyntaPOS** is an enterprise-grade, offline-first Point of Sale system built with **Kotlin Multiplatform (KMM)** and **Compose Multiplatform**. It targets Android tablets (minSdk 24) and JVM desktop (macOS, Windows, Linux) with 100% shared business logic.

- **Company:** Zynta Solutions Pvt Ltd
- **Package:** `com.zyntasolutions.zyntapos`
- **Design system prefix:** `Zynta*`
- **Version:** 1.0.0 MVP (Phase 1 in progress)
- **Languages:** 100% Kotlin — no Java

---

## Repository Layout

```
ZyntaPOS-KMM/
├── androidApp/                        # Android application shell (AGP 9.0+)
├── composeApp/                        # Compose Multiplatform KMP root
│   ├── src/                           # App() root composable, platform stubs
│   ├── core/                          # BaseViewModel<S,I,E> for all features
│   ├── designsystem/                  # Material 3 theme + reusable Zynta* components
│   ├── navigation/                    # Type-safe NavHost, RBAC route gating
│   └── feature/                       # 15 feature modules (see Module Map below)
├── shared/                            # 100% cross-platform business logic
│   ├── core/                          # MVI base, Result<T>, utilities (pure Kotlin)
│   ├── domain/                        # Domain models, use-case interfaces, repo contracts
│   ├── data/                          # SQLDelight (encrypted), Ktor HTTP, sync engine
│   ├── hal/                           # Hardware Abstraction Layer (printer, scanner, cash drawer)
│   ├── security/                      # AES-256-GCM, Keystore/JCE, RBAC, session mgmt
│   └── seed/                          # Debug-only seed data (SeedRunner + JSON fixtures)
├── tools/
│   └── debug/                         # In-app 6-tab developer console
├── docs/
│   ├── adr/                           # Architecture Decision Records (ADR-001 … ADR-004)
│   ├── architecture/                  # Diagrams and module dependency graphs
│   ├── audit/                         # Phase 1–4 audit reports
│   └── ai_workflows/                  # AI execution logs
├── config/detekt/                     # Static analysis rules (detekt.yml)
├── gradle/libs.versions.toml          # Centralized version catalog (250 lines)
├── gradle_commands.md                 # Full Gradle command reference
├── build.gradle.kts                   # Root build (plugins, Detekt, version props)
├── settings.gradle.kts                # Module registry (26 modules)
├── gradle.properties                  # Build cache, parallelism, JVM memory
├── version.properties                 # Semantic version (1.0.0, BUILD=1)
├── local.properties.template          # Secrets template (local.properties is git-ignored)
├── CONTRIBUTING.md                    # Architecture conventions and code review rules
└── README.md                          # Executive summary and setup guide
```

---

## Module Map (26 Modules)

### Shared Modules (KMP — Pure Business Logic)

| Module | Purpose |
|--------|---------|
| `:shared:core` | Pure Kotlin utilities, MVI base classes, `Result<T>`, `CurrencyUtils`, `DateTimeUtils`, `ValidationUtils`, Koin `coreModule` |
| `:shared:domain` | Domain models (26), repository interfaces, use-case classes, business-rule validators — **no framework deps** |
| `:shared:data` | SQLDelight schema + DAOs, Ktor HTTP client, repository implementations, offline sync engine, `ConflictResolver` |
| `:shared:hal` | `PrinterManager`, `BarcodeScanner`, `CashDrawerController` — `expect/actual` platform drivers, `EscPosEncoder` |
| `:shared:security` | `SecureKeyStorage` (Keystore/JCE), `CryptoManager` (AES-256-GCM), `PinHasher` (PBKDF2), `TokenManager`, `RbacEngine`, `SessionManager` |
| `:shared:seed` | **Debug-only.** `SeedRunner` + JSON fixtures (8 categories, 5 suppliers, 25 products, 15 customers). Use as `debugImplementation` only. |

### UI Infrastructure (Compose Multiplatform)

| Module | Purpose |
|--------|---------|
| `:composeApp` | Root KMP library — `App()` composable, JVM `main.kt`, Android library target |
| `:composeApp:core` | `BaseViewModel<State, Intent, Effect>` — all feature VMs extend this |
| `:composeApp:designsystem` | `ZyntaTheme`, Material 3 tokens, `ZyntaButton/Card/TextField`, `NumericKeypad`, `ReceiptPreview`, responsive breakpoints |
| `:composeApp:navigation` | Type-safe `NavRoute` sealed hierarchy, `ZyntaNavHost`, adaptive nav shell (Rail vs Bottom Bar), RBAC route gating |

### Feature Modules (15)

| Module | Purpose |
|--------|---------|
| `:composeApp:feature:auth` | Login, PIN quick-switch, biometric, auto-lock screen |
| `:composeApp:feature:dashboard` | Home KPI dashboard (sales, orders, low-stock alerts, charts) — 3 responsive layout variants |
| `:composeApp:feature:onboarding` | First-run wizard (business name → admin account); shown exactly once |
| `:composeApp:feature:pos` | Product grid, cart, discounts, payment (split/cash/card), receipt, hold orders, refund |
| `:composeApp:feature:inventory` | Product CRUD, category mgmt, stock levels, adjustments, barcode label print |
| `:composeApp:feature:register` | Cash session open/close, cash in/out, EOD Z-report |
| `:composeApp:feature:reports` | Sales summary, product performance, stock report, CSV/PDF export |
| `:composeApp:feature:settings` | Store profile, tax config, printer setup, user mgmt, security policy, backup/restore |
| `:composeApp:feature:customers` | Customer directory, loyalty accounts, GDPR export |
| `:composeApp:feature:coupons` | Coupon CRUD, promotion rule engine (BOGO / % / threshold) |
| `:composeApp:feature:expenses` | Expense log, P&L statement, cash-flow view |
| `:composeApp:feature:staff` | Employee profiles, shift scheduling, attendance, payroll |
| `:composeApp:feature:multistore` | Store selector, central KPI dashboard, inter-store transfers |
| `:composeApp:feature:admin` | System health, audit-log viewer, DB maintenance, backup management |
| `:composeApp:feature:media` | Product image picker, crop, compression pipeline |

### Platform Apps / Tools

| Module | Purpose |
|--------|---------|
| `:androidApp` | Android application shell (pure `com.android.application` — required by AGP 9.0+ to be separate from KMP) |
| `:tools:debug` | 6-tab Debug Console (Seeds, Database, Auth, Network, Diagnostics, UI/UX). Always compiled; Koin bindings loaded only when `BuildConfig.DEBUG = true`. |

### Dependency Direction (enforced)

```
Feature Modules → :composeApp:navigation / designsystem / core
                       ↓
               :shared:domain  (use cases, repo interfaces)
                       ↓
               :shared:data    (implementations)
                       ↓
          :shared:security  /  :shared:hal  /  :shared:core
```

**Architecture guard:** `:shared:domain` permits only `:shared:core` as a dependency. It must never import from `:shared:data`, `:shared:hal`, `:shared:security`, or any feature module.

---

## Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin (100%, no Java) | 2.3.0 |
| Multiplatform | Kotlin Multiplatform + Compose Multiplatform | 1.10.0 |
| UI | Material 3 | 1.10.0-alpha05 |
| State Management | MVI (StateFlow / SharedFlow) | Custom BaseViewModel |
| DI | Koin | 4.0.4 |
| Local DB | SQLDelight 2.0.2 on SQLite + SQLCipher 4.5 (AES-256) | 2.0.2 |
| Networking | Ktor Client | 3.0.3 |
| Serialization | kotlinx-serialization | 1.8.0 |
| Date/Time | kotlinx-datetime | **0.6.1 — pinned** (0.7.1 has binary incompatibility) |
| Image Loading | Coil | 3.0.4 |
| Logging | Kermit | 2.0.4 |
| Testing | Kotlin Test, Mockative 3 (KSP), Turbine, Koin-test | Latest |
| Static Analysis | Detekt | 1.23.8 |
| Secrets | Secrets Gradle Plugin | 2.0.1 |
| Build | Gradle 8.x + Version Catalog | `libs.versions.toml` |
| Android | AGP 8.13.2, minSdk 24, compileSdk/targetSdk 36 | |
| Desktop Serial | jSerialComm | 2.10.4 |
| ML Kit (Android) | Barcode Scanning | 17.3.0 |
| KSP | Forced to 2.3.4 for Mockative 3.0.1 compat with Kotlin 2.3.0 | 2.3.4 |

---

## Architecture Patterns

### Clean Architecture (strict layering)

```
┌─────────────────────────────────────────┐
│  Presentation (Compose Multiplatform)   │
│  :composeApp:feature:* (MVI per screen) │
└────────────────────┬────────────────────┘
                     ↓ (use cases, repo interfaces)
┌─────────────────────────────────────────┐
│  Domain (Pure Business Logic)           │
│  :shared:domain                         │
│  • Models (26 plain Kotlin data classes)│
│  • Use Cases (interfaces + impls)       │
│  • Repository Contracts (interfaces)    │
└────────────────────┬────────────────────┘
                     ↓ (implements contracts)
┌─────────────────────────────────────────┐
│  Data / Infrastructure                  │
│  :shared:data   :shared:security        │
│  :shared:hal    :shared:core            │
└─────────────────────────────────────────┘
```

### MVI Pattern (mandatory for all screens)

Every feature screen follows strict MVI. The `BaseViewModel` lives at:
`composeApp/core/src/commonMain/.../ui/core/mvi/BaseViewModel.kt`

```kotlin
// State — immutable snapshot of screen
data class MyFeatureState(
    val items: List<Item> = emptyList(),
    val isLoading: Boolean = false
)

// Intent — sealed class of all user actions
sealed class MyFeatureIntent {
    data object LoadItems : MyFeatureIntent()
    data class SelectItem(val id: String) : MyFeatureIntent()
}

// Effect — one-shot side effects (navigation, toasts)
sealed class MyFeatureEffect {
    data class ShowError(val message: String) : MyFeatureEffect()
    data object NavigateBack : MyFeatureEffect()
}

// ViewModel — MUST extend BaseViewModel (ADR-001)
class MyFeatureViewModel(
    private val useCase: GetItemsUseCase
) : BaseViewModel<MyFeatureState, MyFeatureIntent, MyFeatureEffect>(
    initialState = MyFeatureState()
) {
    override suspend fun handleIntent(intent: MyFeatureIntent) {
        when (intent) {
            is MyFeatureIntent.LoadItems -> loadItems()
            is MyFeatureIntent.SelectItem -> selectItem(intent.id)
        }
    }

    private suspend fun loadItems() {
        updateState { it.copy(isLoading = true) }
        useCase.execute().fold(
            onSuccess = { updateState { s -> s.copy(items = it, isLoading = false) } },
            onFailure = { sendEffect(MyFeatureEffect.ShowError(it.message ?: "")) }
        )
    }
}
```

**BaseViewModel provides:**
- `StateFlow<State>` for UI observation
- `Channel<Effect>(BUFFERED)` for one-shot side effects
- `updateState { }` — atomic, thread-safe state mutation
- `viewModelScope` — structured coroutine lifecycle
- `abstract suspend fun handleIntent(I)` — single MVI entry point

---

## Critical Naming Conventions

### ADR-002: Domain Model Names (ACCEPTED)

Domain models use **plain, ubiquitous-language names** — no `*Entity` suffix. This boundary is enforced in code review.

```kotlin
// ✅ CORRECT — in :shared:domain/model/
data class Product(val id: String, val name: String, val price: Double, ...)
data class Order(val id: String, val items: List<OrderItem>, ...)

// ❌ WRONG — *Entity suffix is banned in domain layer
data class ProductEntity(...)

// ✅ OK — *Entity suffix is reserved for :shared:data ORM mapping types
// e.g., shared/data/mapper/ProductEntity.kt (hand-written DB mapper)
// SQLDelight auto-generates: Products (table type), ProductsQueries
```

**Code review rule:** If you see `*Entity` in `shared/domain/model/`, request a rename citing ADR-002.

**Domain models (26 files):** `Product`, `Order`, `OrderItem`, `Customer`, `Category`, `User`, `Role`, `Permission`, `CashRegister`, `RegisterSession`, `CashMovement`, `PaymentMethod`, `PaymentSplit`, `Supplier`, `TaxGroup`, `UnitOfMeasure`, `StockAdjustment`, `SyncOperation`, `SyncStatus`, `OrderStatus`, `OrderType`, `DiscountType`, `CartItem`, `OrderTotals`, `ProductVariant`, `AuditEntry`

### ADR-001: ViewModel Base Class (ACCEPTED)

All ViewModels **MUST** extend `BaseViewModel`. Direct extension of `androidx.lifecycle.ViewModel` is **prohibited** in any feature module.

```kotlin
// ✅ CORRECT
import com.zynta.pos.ui.core.mvi.BaseViewModel

class MyFeatureViewModel(...) : BaseViewModel<MyState, MyIntent, MyEffect>(MyState.Initial) { ... }

// ❌ WRONG — direct ViewModel extension is rejected in code review
class MyFeatureViewModel(...) : ViewModel() { ... }
```

### Other Naming Conventions

- **Koin DI modules:** declare in `<feature>/di/` package, named `<feature>Module`
- **Design system components:** prefix with `Zynta*` (e.g., `ZyntaButton`, `ZyntaCard`)
- **Use cases:** suffix with `UseCase` (e.g., `GetProductsUseCase`, `CalculateOrderTotalsUseCase`)
- **Repository impls:** suffix with `Impl` (e.g., `ProductRepositoryImpl`)
- **Branches:** `feature/M##-short-description`
- **Commits:** Conventional Commits format (see below)

---

## Dependency Injection (Koin)

- Single cross-platform DI graph
- Platform-specific bindings registered in `androidMain` / `jvmMain` Koin modules
- Feature modules declare Koin modules in their `di/` package
- Never use `GlobalContext.get()` outside of DI bootstrap code
- **Recent refactor:** `loadKoinModules()` global call replaced with `koin.loadModules()` (PR #21) for better testability

---

## Database Layer

**SQLDelight 2.0.2 on SQLite, encrypted with SQLCipher 4.5 (AES-256)**

**13 tables:** `products`, `orders`, `order_items`, `categories`, `customers`, `suppliers`, `registers`, `settings`, `stock`, `audit_log`, `sync_queue` + FTS5 virtual table `products_fts`

**Key patterns:**
- Products table has `products_fts` (FTS5) with `AFTER INSERT/UPDATE/DELETE` triggers for full-text search
- `sync_queue` table tracks pending offline operations with `sync_status`
- All writes go to local SQLite immediately; background sync engine pushes to cloud
- DB passphrase stored in Android Keystore / JCE KeyStore — **never written to disk in plaintext**

**Code generation:** Run `./gradlew generateSqlDelightInterface` after modifying `.sq` files.

**Schema files:** `shared/data/src/commonMain/sqldelight/` — 13 `.sq` files

---

## Security Architecture

| Component | Implementation |
|-----------|---------------|
| DB Encryption | SQLCipher 4.5, AES-256, passphrase from Keystore |
| Key Storage (Android) | Android Keystore via `androidx.security.crypto` |
| Key Storage (JVM) | JCE KeyStore |
| PIN Hashing | PBKDF2 (via `PinHasher` in `:shared:security`) |
| JWT Tokens | `TokenManager` + `TokenStorage` interface (testable in commonTest via `FakeSecurePreferences`) |
| RBAC | `RbacEngine` + `SessionManager` in `:shared:security` |
| Secrets Injection | Gradle Secrets Plugin: `ZYNTA_*` keys from `local.properties` → `BuildConfig` fields |

**ADR-003:** `SecurePreferences` is canonical in `:shared:security` only. The old `data.local.security.SecurePreferences` interface was deleted. Do not recreate it in `:shared:data`.

**ADR-004:** Keystore token scaffold was removed. Use `TokenStorage` interface backed by `SecurePreferences`.

---

## Build System

### Key Gradle Properties

```properties
# gradle.properties
kotlin.code.style=official
org.gradle.caching=true
org.gradle.parallel=true
org.gradle.configuration-cache=false    # disabled — KMP/Compose CC bugs
org.gradle.jvmargs=-Xmx4g -Xms512m -XX:MaxMetaspaceSize=1g
kotlin.incremental=true
kotlin.incremental.multiplatform=true
sqldelight.verifyMigrations=true
```

### Version Catalog

All dependency versions are in `gradle/libs.versions.toml`. Always add new deps through the catalog — never hardcode versions in module build files.

**Critical pin:** `kotlinx-datetime` is **pinned at 0.6.1** globally in `build.gradle.kts`. Do not upgrade to 0.7.1 (binary incompatibility). The root build script forces this via `resolutionStrategy`.

### Always use the Gradle wrapper

```bash
./gradlew <task>   # correct
gradle <task>      # wrong — may use wrong version
```

---

## Common Gradle Commands

### Build

```bash
./gradlew assemble                              # All modules (debug)
./gradlew :composeApp:assembleDebug             # Android debug APK
./gradlew :composeApp:run                       # Run desktop app
./gradlew :composeApp:packageDistributionForCurrentOS  # Desktop installer
```

### Test

```bash
./gradlew test                                  # All tests, all modules
./gradlew allTests                              # All KMP target tests
./gradlew jvmTest                               # JVM/Desktop tests only
./gradlew testDebugUnitTest                     # Android unit tests only

# Per-module
./gradlew :shared:domain:test
./gradlew :shared:data:jvmTest
./gradlew :composeApp:feature:pos:test

# Single test class
./gradlew :shared:domain:test --tests "com.zentapos.domain.usecase.CalculateOrderTotalsUseCaseTest"
```

### Code Quality

```bash
./gradlew detekt                                # Static analysis (full project)
./gradlew lint                                  # Android Lint
./gradlew :composeApp:feature:pos:lint          # Lint single module
```

### SQLDelight

```bash
./gradlew generateSqlDelightInterface           # Generate all DB interfaces
./gradlew :shared:data:generateCommonMainZyntaPosDatabaseInterface
./gradlew verifySqlDelightMigration
```

### CI Pipeline Commands (from `gradle_commands.md`)

```bash
# Pre-commit check (~2 min)
./gradlew :shared:core:test :shared:domain:test :shared:security:test --parallel --continue

# Full verification before PR merge (~8 min)
./gradlew clean test lint --parallel --continue --stacktrace

# Full CI pipeline (~15 min)
./gradlew clean test testDebugUnitTest jvmTest lint assembleDebug \
          :composeApp:packageUberJarForCurrentOS --parallel --continue --stacktrace
```

### Clean

```bash
./gradlew clean                                 # Clean all build outputs
./gradlew --stop                                # Stop stuck Gradle daemon
./gradlew build --refresh-dependencies          # Force re-download deps
```

---

## Testing Standards

| Layer | Coverage Target | Tools |
|-------|----------------|-------|
| Use Cases | 95% | Kotlin Test + Mockative 3 |
| Repositories | 80% | Kotlin Test + Mockative 3 |
| ViewModels | 80% | Kotlin Test + Turbine (for Flows) |
| Compose UI | Critical flows only | Compose UI Test (Phase 2) |

**Test organization:**
- `src/commonTest/` — shared cross-platform tests (domain logic, use cases)
- `src/jvmTest/` — JVM integration tests (SQLDelight with in-memory DB)
- `src/androidTest/` — reserved for Phase 2 instrumented tests

**Mocking:** Mockative 3 (KSP-based, compatible with Kotlin 2.3.0 via KSP 2.3.4). Use `mock<Interface>` — no hand-written fakes unless testing boundary contracts.

**Flow testing:** Use Turbine for `StateFlow` / `SharedFlow` assertions in ViewModel tests.

---

## Commit Style

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(pos): add hold order use case
fix(inventory): correct stock adjustment calculation
refactor(data): rename mapper to align with ADR-002
docs(adr): accept ADR-003 SecurePreferences consolidation
build(gradle): pin kotlinx-datetime to 0.6.1
test(domain): add CalculateOrderTotalsUseCase coverage
```

Scopes match module names: `pos`, `inventory`, `auth`, `domain`, `data`, `security`, `hal`, `core`, `navigation`, `designsystem`, `settings`, `register`, `reports`, `customers`, `staff`, `admin`, `debug`, `gradle`, `ci`.

---

## Secrets & Local Configuration

**`local.properties` is git-ignored — never commit it.**
Copy `local.properties.template` and fill in values before first build.

| Key | Description |
|-----|-------------|
| `sdk.dir` | Android SDK path |
| `ZYNTA_API_BASE_URL` | Backend REST API base URL |
| `ZYNTA_API_CLIENT_ID` | OAuth2 client ID |
| `ZYNTA_API_CLIENT_SECRET` | OAuth2 client secret |
| `ZYNTA_DB_PASSPHRASE` | AES-256 passphrase for SQLCipher (generate: `openssl rand -hex 32`) |
| `ZYNTA_FCM_SERVER_KEY` | Firebase Cloud Messaging server key |
| `ZYNTA_SENTRY_DSN` | Sentry crash reporting DSN |
| `ZYNTA_IRD_API_ENDPOINT` | IRD e-invoice API endpoint (Sri Lanka) |
| `ZYNTA_IRD_CLIENT_CERTIFICATE_PATH` | Absolute path to IRD `.p12` certificate |
| `ZYNTA_IRD_CERTIFICATE_PASSWORD` | IRD certificate password |

---

## CI/CD

**Two GitHub Actions workflows:**

### `.github/workflows/ci.yml` — Continuous Integration
- Triggers: push to `main`/`develop`, PR to `main`
- Runner: `ubuntu-latest`, JDK 21 (Temurin), 60-min timeout
- Steps: build shared modules → build Android debug APK → build Desktop JVM JAR → Android Lint → Detekt → all tests → upload artifacts (APK, test reports, lint reports, 7-day retention)

### `.github/workflows/release.yml` — Release
- Triggers: push to `main`
- Runs on 4 matrix runners: ubuntu, macos, windows
- Produces: Android APK (signed), macOS DMG, Windows MSI, Linux DEB
- Creates GitHub Release tagged `v1.0.0-build.{run_number}`

---

## Architecture Decision Records (ADRs)

All structural decisions are documented in `docs/adr/`. Create a new ADR before introducing new conventions, modules, or tech.

| ADR | Title | Status |
|-----|-------|--------|
| ADR-001 | ViewModel Base Class Policy — all VMs extend `BaseViewModel<S,I,E>` | ACCEPTED |
| ADR-002 | Domain Model Naming — no `*Entity` suffix in `:shared:domain` | ACCEPTED |
| ADR-003 | `SecurePreferences` Interface Consolidation — canonical in `:shared:security` only | ACCEPTED |
| ADR-004 | Keystore Token Scaffold Removal — use `TokenStorage` interface | ACCEPTED |

---

## Common Pitfalls to Avoid

1. **Do not extend `androidx.lifecycle.ViewModel` directly.** Always extend `BaseViewModel<S,I,E>` (ADR-001).
2. **Do not add `*Entity` suffix to domain models.** Use plain names in `shared/domain/model/` (ADR-002).
3. **Do not import `:shared:data`, `:shared:hal`, or `:shared:security` from `:shared:domain`.** Only `:shared:core` is permitted.
4. **Do not hardcode `kotlinx-datetime` version.** It is globally pinned to 0.6.1 in root `build.gradle.kts`.
5. **Do not put security primitives in `:shared:data`.** Encrypted key-value storage belongs only in `:shared:security` (ADR-003).
6. **Do not commit `local.properties`.** It contains encryption keys and API credentials.
7. **Do not use `loadKoinModules(global=true)`.** Use `koin.loadModules()` instead (PR #21 migration).
8. **Do not use `GlobalContext.get()` outside DI bootstrap code.**
9. **Do not add `:shared:seed` as a regular dependency.** It must be `debugImplementation` only.
10. **Do not run bare `gradle` — always use `./gradlew`** to ensure the correct Gradle wrapper version.

---

## Development Phases

| Phase | Status | Scope |
|-------|--------|-------|
| Phase 0 — Foundation | Complete | Build system, module scaffold, secrets, CI skeleton |
| Phase 1 — MVP | In Progress | Single-store POS, offline sync, core features |
| Phase 2 — Growth | Planned | Multi-store, CRM, promotions, CRDT sync |
| Phase 3 — Enterprise | Planned | Staff/HR, admin, e-invoicing (IRD), analytics |

See `docs/ai_workflows/execution_log.md` for the granular task checklist.

---

## Quick Reference: Where Things Live

| What you need | Where to look |
|---------------|---------------|
| Domain model definitions | `shared/domain/src/commonMain/.../model/` |
| Repository interfaces | `shared/domain/src/commonMain/.../repository/` |
| Repository implementations | `shared/data/src/commonMain/.../repository/` |
| SQLDelight schema files | `shared/data/src/commonMain/sqldelight/` |
| BaseViewModel | `composeApp/core/src/commonMain/.../ui/core/mvi/BaseViewModel.kt` |
| Design system components | `composeApp/designsystem/src/commonMain/.../` |
| Navigation routes | `composeApp/navigation/src/commonMain/.../` |
| Koin DI modules | `<feature>/src/commonMain/.../di/<Feature>Module.kt` |
| Security utilities | `shared/security/src/commonMain/.../` |
| HAL interfaces | `shared/hal/src/commonMain/.../` |
| Seed/test data | `shared/seed/src/commonMain/.../` |
| Debug console | `tools/debug/src/commonMain/.../` |
| Gradle commands | `gradle_commands.md` |
| Architecture decisions | `docs/adr/ADR-NNN-*.md` |
| Version catalog | `gradle/libs.versions.toml` |
