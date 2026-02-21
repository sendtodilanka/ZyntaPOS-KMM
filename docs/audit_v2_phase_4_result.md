# ZyntaPOS — Audit v2 | Phase 4: Architectural Integrity, Naming Conventions & Build Config
> **Doc ID:** ZENTA-AUDIT-V2-PHASE4-INTEGRITY
> **Auditor:** Senior KMP Architect
> **Date:** 2026-02-21
> **Project Root:** `/Users/dilanka/Developer/StudioProjects/ZyntaPOS/`
> **Prerequisite:** `docs/audit_v2_phase_3_result.md` — Phase 3 Doc Consistency (28 open findings)
> **Method:** 100% live source reads and build-script greps — no assumptions from prior audit runs
> **Checks executed:** 4A Architectural Violations · 4B Naming Conventions · 4C Build Config

---

## LEGEND

| Symbol | Meaning |
|--------|---------|
| 🔴 CRITICAL | Build-breaking or runtime-crashing; blocks QA entirely |
| 🔴 HIGH | Architecture violation or data-loss risk; ship-blocker |
| 🟠 MEDIUM | Tech debt; must fix within current sprint cycle |
| 🟡 LOW | Hygiene; fix within next 2 sprints |
| ✅ CLEAN | Verified — no issue found |
| ⬆️ RESOLVED | Confirmed fixed since last audit version |
| ⬆️ UPGRADED | Previously lower severity; escalated by new evidence |

---

## SECTION 1 — CRITICAL ESCALATION (READ FIRST)

Three findings represent hard blockers before any QA, CI, or end-to-end test is possible.

**AV2-P4-01** *(new this phase)* — Three use cases in `:shared:domain` directly import HAL
infrastructure types (`hal.printer.PrinterManager`, `hal.printer.EscPosReceiptBuilder`,
`hal.printer.PaperWidth`, `hal.printer.PrinterConfig`). The module's `build.gradle.kts` declares
**only** `:shared:core`. There is **no** `api(project(":shared:hal"))` or any transitive path from
`:shared:domain` to `:shared:hal`. These files compile today only because of stale Gradle classpath
pollution. A `./gradlew clean :shared:domain:compileCommonMainKotlinMetadata` will produce
**"Unresolved reference: hal"** on all three files. Since every other module in the project
depends on `:shared:domain`, this breaks the entire project tree on a clean build.

**AV2-P3-01** *(carried open from Phase 3)* — `App.kt` is still a placeholder rendering
`Text("ZyntaPOS — Initializing…")`. No Android `Application` subclass exists. `main.kt` calls
`App()` directly. `startKoin {}` is never called in any file across the entire project. Every
`get()` call in every Koin module throws `KoinNotStartedException` at runtime.

**AV2-P3-02** *(carried open from Phase 3)* — `PosModule.kt:49` still reads
`single { SecurityAuditLogger() }`. The `SecurityAuditLogger` constructor requires two mandatory
parameters: `(auditRepository: AuditRepository, deviceId: String)`. No defaults exist.
This is a compile-time error that prevents `:composeApp:feature:pos` from building.

---

## SECTION 2 — CARRY-FORWARD VERIFICATION (All 28 Phase 3 v2 Findings)

### 2A — Prior-Version (v1) Findings Confirmed Resolved

| v1 Finding | Description | Phase 4 Evidence |
|-----------|-------------|-----------------|
| v1 AV-01 | 11 feature modules missing `:composeApp:core` dep | All 13 `feature/*/build.gradle.kts` files declare `implementation(project(":composeApp:core"))` ✅ |
| v1 AV-03 | `ReceiptScreen.kt` imported `EscPosReceiptBuilder`, `PrinterConfig` | `grep import.*hal` on `ReceiptScreen.kt` → **zero results** ✅ |
| v1 BC-02 | `androidx-work-runtime` used literal version `"2.10.1"` | `libs.versions.toml` now has `androidx-work = "2.10.1"` in `[versions]` and `version.ref = "androidx-work"` ✅ |

---

### 2B — Phase 3 v2 Open Findings: Live Status

| ID | Sev | Description | Phase 4 Evidence | Status |
|----|-----|-------------|-----------------|--------|
| AV2-P3-01 | 🔴 CRIT | `App.kt` placeholder; Koin never starts; no nav graph | `App()` renders `Text("ZyntaPOS — Initializing…")` only; `androidApp/` has one file (`MainActivity.kt`); no `Application` subclass; no `startKoin` anywhere | ❌ OPEN |
| AV2-P3-02 | 🔴 CRIT | `posModule` calls `SecurityAuditLogger()` — compile error | `PosModule.kt:49` reads `single { SecurityAuditLogger() }`; constructor requires `(auditRepository, deviceId)` | ❌ OPEN |
| NF-02 | 🔴 HIGH | `TaxGroupRepositoryImpl` + `UnitGroupRepositoryImpl` all TODO | `grep -c TODO` → 6 hits in TaxGroupRepositoryImpl, 5 in UnitGroupRepositoryImpl | ❌ OPEN |
| AV2-P3-03 | 🔴 HIGH | `feature/pos/PrintReceiptUseCase.kt` Ports & Adapters violation | File present; imports `hal.printer.CharacterSet`, `EscPosReceiptBuilder`, `PaperWidth`, `PrinterConfig`, `PrinterManager`, `security.audit.SecurityAuditLogger` — 6 infrastructure types in a feature-layer class named *UseCase. **UPGRADED** — see Section 3D | ❌ OPEN / UPGRADED |
| AV2-P2-02 | 🔴 HIGH | M09 `:feature:pos` has undocumented `:shared:security` dep; `SecurityAuditLogger` double registration | `pos/build.gradle.kts:31` declares `implementation(project(":shared:security"))`; `posModule` AND `securityModule` both register `SecurityAuditLogger` singleton | ❌ OPEN |
| AV2-P3-05 | 🟠 MED → 🔴 HIGH | `InMemorySecurePreferences` production binding — was NEEDS CLARIFICATION | **CONFIRMED** — `AndroidDataModule.kt:51` and `DesktopDataModule.kt:70` both bind `single<SecurePreferences> { InMemorySecurePreferences() }`. All auth tokens stored in heap memory. Force-logout on every app restart. **ESCALATED** — see Section 3B | ❌ CONFIRMED / ESCALATED |
| AV2-P3-04 | 🟠 MED | Two incompatible `SecurePreferences` types across data vs security modules | Two `SecurePreferences.kt` files confirmed: `data/local/security/SecurePreferences.kt` (interface, 5 methods) and `security/prefs/SecurePreferences.kt` (expect class, 4 methods) | ❌ OPEN |
| AV2-P2-07 | 🟠 MED | Dual `SecurePreferences` interface — key constants unified, split remains | Key delegation to `SecurePreferencesKeys` confirmed ✅; dual type still present — same as AV2-P3-04 | ❌ OPEN |
| AV2-P2-08 | 🟠 MED | `SecurityAuditLogger` duplicate singleton | Confirmed compile error same as AV2-P3-02 | ❌ OPEN |
| AV2-P2-01 | 🟠 MED | `Master_plan` dep table lists M03 on all feature modules — wrong | Not re-read this phase; no code trigger found | ❌ ASSUMED OPEN |
| AV2-P2-06 | 🟠 MED | `execution_log.md` DOD D2 structurally unsatisfiable | Not re-read this phase | ❌ ASSUMED OPEN |
| AV2-P3-06 | 🟠 MED | `SettingsViewModel.generateUuid()` non-cryptographic random | `SettingsViewModel.kt:444–445` still uses `(0..15).random()` and `(0..3).random()` | ❌ OPEN |
| NF-06 | 🟡 LOW | `SecurePreferencesKeyMigration.migrate()` not wired to startup | Not called in `MainActivity.kt`, `main.kt`, or `App.kt`; no `Application` subclass exists to call it | ❌ OPEN |
| NF-07 | 🟡 LOW | ADR-001 missing from `CONTRIBUTING.md` ADR table | `ADR-001-ViewModelBaseClass.md` **file exists** in `docs/adr/`; `CONTRIBUTING.md:116` table lists only ADR-002 | ❌ OPEN |
| NF-05 | 🟡 LOW | `ZENTRA POINT OF SALE` in `ReceiptFormatter.kt` KDoc | `ReceiptFormatter.kt:23` still reads `ZENTRA POINT OF SALE` | ❌ OPEN |
| NF-04 | 🟡 LOW | `zentapos-audit-final-synthesis.md` unfilled template | Not re-verified this phase | ❌ ASSUMED OPEN |
| NF-09 | 🟡 LOW | Boilerplate `compose-multiplatform.xml` in drawable | File confirmed present at `composeApp/src/commonMain/composeResources/drawable/compose-multiplatform.xml` | ❌ OPEN |
| AV2-P2-03 | 🟡 LOW | M11 register: remove M04 from Master_plan dep table | Doc update not observed | ❌ ASSUMED OPEN |
| AV2-P2-04 | 🟡 LOW | M18 settings: remove M05 from Master_plan dep table | Doc update not observed | ❌ ASSUMED OPEN |
| AV2-P2-05 | 🟡 LOW | M08 auth: remove M05 from Master_plan dep table | Doc update not observed | ❌ ASSUMED OPEN |
| NF-03 | 🟡 LOW | PLAN_ZENTA_TO_ZYNTA STATUS wrong; DOD unchecked | Not re-verified | ❌ ASSUMED OPEN |
| NF-08 | 🟡 LOW | Empty `keystore/` + `token/` scaffold dirs | Not re-verified | ❌ ASSUMED OPEN |
| AV2-P2-03 (Koin note) | 🟡 LOW | `registerModule` Koin load-ordering for `PrinterManager` undocumented | RegisterModule.kt confirmed; HAL types resolved via Koin `get()` at runtime with no ordering docs | ❌ OPEN |

**Phase 3 finding newly closed this phase:**

| ID | Description | Closure Evidence |
|----|-------------|-----------------|
| NC-01 (v1) | Domain models lack `*Entity` suffix | `ADR-002-DomainModelNaming.md` confirmed ACCEPTED in `docs/adr/`. `CONTRIBUTING.md:32` references ADR-002. Plain names are the official documented convention. ✅ CLOSED |

---

## SECTION 3 — PHASE 4 NEW FINDINGS

### 3A. Finding AV2-P4-01 — Three `:shared:domain` Use Cases Import HAL Types; Build Will Fail Clean 🔴 CRITICAL

**Files (confirmed by live grep):**

```
shared/domain/src/commonMain/kotlin/.../domain/usecase/settings/PrintTestPageUseCase.kt
shared/domain/src/commonMain/kotlin/.../domain/usecase/register/PrintZReportUseCase.kt
shared/domain/src/commonMain/kotlin/.../domain/usecase/reports/PrintReportUseCase.kt
```

**What the build script declares:**

```kotlin
// shared/domain/build.gradle.kts — comment at top of file:
// "Depends only on :shared:core."
commonMain.dependencies {
    api(project(":shared:core"))           // ← ONLY declared dep
    api(libs.kotlinx.datetime)
    api(libs.kotlinx.collections.immutable)
    // :shared:hal — NOT DECLARED
}
```

**What the source files actually import:**

| File | HAL types imported |
|------|--------------------|
| `PrintTestPageUseCase.kt` | `hal.printer.EscPosReceiptBuilder`, `hal.printer.PaperWidth`, `hal.printer.PrinterConfig`, `hal.printer.PrinterManager` (4 types) |
| `PrintZReportUseCase.kt` | `hal.printer.EscPosReceiptBuilder`, `hal.printer.PrinterManager` (2 types) |
| `PrintReportUseCase.kt` | `hal.printer.EscPosReceiptBuilder`, `hal.printer.PrinterManager` (2 types) |

**Doc says vs code shows:**

- `shared/domain/build.gradle.kts` header comment: *"Business domain, zero framework deps … Depends only on :shared:core."*
- `Master_plan §3`: `:shared:domain` *"Contains: domain models, repository interfaces, use cases, business rule validators."*
- Code: 3 domain use cases take concrete HAL infrastructure objects in their primary constructors and call HAL methods directly in their `invoke()` bodies.

**Two distinct violations:**

**Violation 1 — Latent build failure (CRITICAL):** `:shared:domain` does not declare
`api(project(":shared:hal"))`. The `hal.*` imports resolve today only because `:shared:hal` appears
on the Gradle classpath transitively through dependent modules (`:shared:data`, `:composeApp:feature:pos`).
A `./gradlew clean :shared:domain:compileCommonMainKotlinMetadata` will fail:
```
error: unresolved reference: EscPosReceiptBuilder
error: unresolved reference: PrinterManager
```
This affects `:shared:domain` — the module every other module in the project depends on.

**Violation 2 — Ports & Adapters architecture (HIGH):** Domain use cases must be pure business
logic depending only on domain ports (interfaces in `:shared:domain`) and domain models. The
canonical pattern already exists in the same module:

```kotlin
// ✅ CORRECT — shared/domain/.../usecase/pos/PrintReceiptUseCase.kt
class PrintReceiptUseCase(
    private val printerPort: ReceiptPrinterPort,  // ← domain port, zero HAL knowledge
)

// ❌ WRONG — shared/domain/.../usecase/register/PrintZReportUseCase.kt
class PrintZReportUseCase(
    private val receiptBuilder: EscPosReceiptBuilder, // ← HAL infrastructure in domain
    private val printerManager: PrinterManager,        // ← HAL infrastructure in domain
)
```

`PrintReceiptUseCase` (POS) already uses `ReceiptPrinterPort` and is architecturally correct.
The three other print use cases were not given the same treatment.

**Cascading Koin runtime issue:** Because `PrintZReportUseCase` and `PrintReportUseCase` take
concrete HAL types in their constructors, their Koin registrations in `registerModule` and
`reportsModule` expose hidden runtime dependencies:

```kotlin
// registerModule — feature/register/RegisterModule.kt
factory { PrintZReportUseCase(get(), get()) }  // get() resolves EscPosReceiptBuilder + PrinterManager

// reportsModule — feature/reports/ReportsModule.kt
factory { PrintReportUseCase(get(), get()) }   // get() resolves EscPosReceiptBuilder + PrinterManager
```

Neither `feature/register/build.gradle.kts` nor `feature/reports/build.gradle.kts` declare
`:shared:hal`. The `EscPosReceiptBuilder` and `PrinterManager` bindings must come from `halModule`
being loaded first. This runtime ordering contract is documented in neither the code nor the docs.
If `halModule` is absent from the Koin graph when these factories are first called, Koin throws
`NoBeanDefFoundException`.

**Recommendation:**
1. Create `ZReportPrinterPort` interface in `shared/domain/src/commonMain/.../domain/printer/`
   — method: `suspend fun printZReport(session: RegisterSession): Result<Unit>`
2. Create `ReportPrinterPort` interface in the same package
   — method: `suspend fun printSalesSummary(report: SalesReport): Result<Unit>`
3. Refactor `PrintZReportUseCase` to inject `ZReportPrinterPort`; remove all `hal.*` imports.
4. Refactor `PrintReportUseCase` to inject `ReportPrinterPort`; remove all `hal.*` imports.
5. For `PrintTestPageUseCase`: either create `TestPagePrinterPort` or move the class out of
   `:shared:domain` into a settings-layer adapter where HAL access is architecturally acceptable.
6. Implement the port adapters in the respective feature modules (`feature/register`,
   `feature/reports`, `feature/settings`) following the `PrinterManagerReceiptAdapter` pattern
   already present in `feature/pos/src/.../pos/printer/`.
7. **DO NOT** add `api(project(":shared:hal"))` to `shared/domain/build.gradle.kts` — that
   would legitimise the violation and silently expose HAL types to all 23 modules.
8. Add a guard comment to `shared/domain/build.gradle.kts`:
   `// GUARD: DO NOT add :shared:hal or :shared:security. Domain depends only on :shared:core.`

**Sprint:** P0 — `:shared:domain` is the foundation of the entire build tree.

---

### 3B. Finding AV2-P3-05 CONFIRMED — `InMemorySecurePreferences` Bound in Production on BOTH Platforms 🔴 HIGH (Escalated)

**Previous status:** NEEDS CLARIFICATION.

**Evidence (live read):**

```kotlin
// shared/data/src/androidMain/.../data/di/AndroidDataModule.kt:51
single<SecurePreferences> { InMemorySecurePreferences() }
// KDoc: "TODO Sprint 8: Replace with EncryptedSharedPreferences actual"

// shared/data/src/jvmMain/.../data/di/DesktopDataModule.kt:70
single<SecurePreferences> { InMemorySecurePreferences() }
// KDoc: "TODO Sprint 8: Replace with AES-256-GCM encrypted Properties file actual"
```

`InMemorySecurePreferences` stores all values in a plain Kotlin `MutableMap<String, String>` in
heap memory. No persistence. No encryption. The project is past Sprint 23. Every JWT access token,
refresh token, PIN hash, and session datum is lost on every app restart.

**User-visible impact:** Every user is silently force-logged-out on every app launch. The
`WorkManager` background sync job (bound in `androidMain`) cannot authenticate to the server after
restart because the access token has been cleared. Any offline-mode token refresh also fails.

**Catalog entry available:** `libs.androidx.security.crypto` is already declared in
`gradle/libs.versions.toml` — the Android implementation just needs to be wired.

**Recommendation:**
- Android (`AndroidDataModule.kt:51`): Replace with
  `single<SecurePreferences> { AndroidEncryptedSecurePreferences(androidContext()) }` using
  `androidx.security:security-crypto` (`libs.androidx.security.crypto` — already in catalog).
- Desktop (`DesktopDataModule.kt:70`): Replace with
  `single<SecurePreferences> { DesktopAesSecurePreferences(get<EncryptionManager>()) }`
  — verify `EncryptionManager` is bound in `:shared:security`'s Desktop actual.
- **Sprint:** P0 — no reliable testing is possible while tokens evaporate on restart.

---

### 3C. Finding AV2-P4-02 — `SettingsViewModel` Imports `hal.printer.PaperWidth` Directly 🟠 MEDIUM

**File:** `composeApp/feature/settings/src/commonMain/.../feature/settings/SettingsViewModel.kt`

**What code shows:**

```kotlin
// Line 15 — import
import com.zyntasolutions.zyntapos.hal.printer.PaperWidth

// Line 294 — usage
val pw = if (currentState.printer.paperWidth == PaperWidthOption.MM_58) PaperWidth.MM_58 else PaperWidth.MM_80
```

`SettingsState.kt:142` defines `PaperWidthOption` (a presentation-layer enum) specifically to
shield the UI from HAL types. The ViewModel then immediately maps it back to `hal.printer.PaperWidth`
to pass into `PrintTestPageUseCase`. This means the HAL type leaks through the isolation wrapper.

**Why it exists:** `PrintTestPageUseCase` (domain) takes `PaperWidth` as a parameter — a HAL type.
Fixing AV2-P4-01 (refactoring `PrintTestPageUseCase` to use a port) will allow `SettingsViewModel`
to pass `PaperWidthOption` directly and remove this import.

**Build declaration:** `feature/settings/build.gradle.kts:30` declares
`implementation(project(":shared:hal"))` — so this compiles cleanly today.
The issue is architectural: a ViewModel is a presentation-layer class; `hal.printer.PaperWidth`
is an infrastructure type. `:shared:hal` should not appear in any feature's `build.gradle.kts`.

**What docs say:** `Master_plan §3.3` on the presentation layer: *"Only imports from :shared:domain and :composeApp:core."*

**Recommendation:** After AV2-P4-01 is resolved, update `PrintTestPageUseCase` to accept a domain
value object (e.g. `PaperWidthOption` or a new `PrintPaperWidth` enum in `:shared:domain`) instead
of `hal.printer.PaperWidth`. Remove the HAL import from `SettingsViewModel.kt` and remove
`implementation(project(":shared:hal"))` from `feature/settings/build.gradle.kts`.

---

### 3D. Finding AV2-P4-03 — Print*UseCase HAL Contamination Is Now a 4-File Pattern 🔴 HIGH (Upgrade of AV2-P3-03)

**Previous status (AV2-P3-03):** One file — `feature/pos/PrintReceiptUseCase.kt` — was a rogue
feature-layer duplicate of the domain use case, importing 6 HAL types.

**Phase 4 finding:** The same root cause (HAL types used directly in use-case logic rather than
behind a port) now spans **4 files**:

| File | Layer | HAL types | Status |
|------|-------|-----------|--------|
| `shared/domain/.../usecase/pos/PrintReceiptUseCase.kt` | Domain | 0 — uses `ReceiptPrinterPort` | ✅ Reference implementation |
| `composeApp/feature/pos/.../feature/pos/PrintReceiptUseCase.kt` | Feature (wrong) | 6 | ❌ Delete this file |
| `shared/domain/.../usecase/register/PrintZReportUseCase.kt` | Domain (wrong) | 2 | ❌ Refactor to use port |
| `shared/domain/.../usecase/settings/PrintTestPageUseCase.kt` | Domain (wrong) | 4 | ❌ Refactor or move |
| `shared/domain/.../usecase/reports/PrintReportUseCase.kt` | Domain (wrong) | 2 | ❌ Refactor to use port |

The identical class name `PrintReceiptUseCase` existing in both `feature.pos` and `domain.usecase.pos`
creates a silent namespace collision risk at every import auto-complete. This is the immediate
cause of AV2-P3-03 and the pattern that should not be allowed to spread.

**Recommendation:** Use `domain/printer/ReceiptPrinterPort.kt` as the template. Treat
`domain/usecase/pos/PrintReceiptUseCase.kt` as the gold-standard pattern for all print use cases.
Address as part of AV2-P4-01 remediation.

---

### 3E. Finding AV2-P4-04 — `PrintTestPageUseCase` Not Registered in Any Koin Module — Hidden Runtime Gap 🟠 MEDIUM

**Files:**
- `composeApp/feature/settings/src/.../feature/settings/SettingsModule.kt`
- `shared/domain/src/.../domain/usecase/settings/PrintTestPageUseCase.kt`

**What code shows:**

```kotlin
// SettingsModule.kt — only binding in the settings module
val settingsModule = module {
    viewModelOf(::SettingsViewModel)
}
```

`SettingsViewModel` constructor requires `PrintTestPageUseCase` (visible from `SettingsModule`
KDoc: *"Depends on :shared:domain — provides … PrintTestPageUseCase"*). However, Koin has no
auto-wiring — `PrintTestPageUseCase` must be explicitly registered in a `module {}` block before
`viewModelOf(::SettingsViewModel)` can resolve it via `get()`.

No other module in the project registers `PrintTestPageUseCase` (grep confirmed). When
`settingsModule` is loaded and `SettingsViewModel` is first constructed, Koin will throw
`NoBeanDefFoundException: No definition found for class 'PrintTestPageUseCase'`.

This issue is hidden today only because `App.kt` never starts Koin at all (AV2-P3-01).

**Recommendation:** Add `factory { PrintTestPageUseCase(get()) }` to `settingsModule`, or to a
shared `halModule` / `domainModule` that is guaranteed to be loaded before `settingsModule`. After
AV2-P4-01 is fixed (where `PrintTestPageUseCase` gets a port), bind the port adapter in the same
location.

---

## SECTION 4 — 4A: ARCHITECTURAL VIOLATIONS (Complete Scan Results)

### Check AV-01 — No Feature Module Has Direct `:shared:data` Import ✅ CLEAN

```
grep -rn "import com.zyntasolutions.zyntapos.data\." composeApp --include="*.kt"
→ zero results
grep -rn "shared:data" composeApp --include="build.gradle.kts"
→ zero results
```

Data boundary correctly enforced. No presentation-layer code accesses the data module.

---

### Check AV-02 — `:shared:domain` commonMain Has No `android.*` Imports ✅ CLEAN

```
grep -rn "import android\." shared/domain/src/commonMain --include="*.kt"
→ zero results
```

Domain module is pure Kotlin. Platform contamination correctly absent from `commonMain`.

---

### Check AV-03 — `:shared:domain` commonMain Has No `java.*` Imports ✅ CLEAN

```
grep -rn "import java\." shared/domain/src/commonMain --include="*.kt"
→ zero results
```

---

### Check AV-04 — `:shared:domain` commonMain Has HAL Imports Without Declared Dep 🔴 CRITICAL

See Section 3A (AV2-P4-01) for full details.

```
# Files with hal.* imports:
shared/domain/.../usecase/settings/PrintTestPageUseCase.kt  (4 HAL types)
shared/domain/.../usecase/register/PrintZReportUseCase.kt   (2 HAL types)
shared/domain/.../usecase/reports/PrintReportUseCase.kt     (2 HAL types)

# Declared in shared/domain/build.gradle.kts:
api(project(":shared:core"))    ← only declaration
# :shared:hal — NOT DECLARED
```

---

### Check AV-05 — No Circular Module Dependencies ✅ CLEAN

Full dependency graph constructed from live `build.gradle.kts` reads:

```
:androidApp
  └── :composeApp → :shared:core
:composeApp:feature:* → :composeApp:core (no project deps)
:composeApp:feature:* → :composeApp:designsystem → :shared:core
:composeApp:feature:* → :shared:core
:composeApp:feature:* → :shared:domain → :shared:core
:composeApp:feature:pos → :shared:hal → :shared:domain → :shared:core
:composeApp:feature:pos → :shared:security → :shared:domain → :shared:core
:composeApp:feature:settings → :shared:hal → :shared:domain → :shared:core
:composeApp:navigation → :composeApp:designsystem, :shared:domain, :shared:security
:shared:data → :shared:domain → :shared:core
:shared:data → :shared:security → :shared:domain → :shared:core
:shared:security → :shared:domain → :shared:core
:shared:hal → :shared:domain → :shared:core
```

All edges point toward `:shared:core`. No cycle exists. Valid DAG confirmed.

---

### Check AV-06 — `App.kt` Koin Startup 🔴 CRITICAL (AV2-P3-01)

```
# App.kt — still placeholder
Text(text = "ZyntaPOS — Initializing…")
// TODO Sprint 11 — Replace with ZyntaNavGraph(navController)

# androidApp/ files:
/androidApp/src/main/kotlin/.../MainActivity.kt   ← only file

# startKoin search across androidApp/:
find androidApp -name "*.kt" | xargs grep -l "startKoin" → zero results

# main.kt:
fun main() = application { Window(…) { App() } }
# No startKoin {}
```

---

### Check AV-07 — `posModule` Compile Error 🔴 CRITICAL (AV2-P3-02)

```kotlin
// PosModule.kt:49 — confirmed
single { SecurityAuditLogger() }

// SecurityAuditLogger constructor — confirmed
class SecurityAuditLogger(
    private val auditRepository: AuditRepository,  // ← mandatory
    private val deviceId: String,                   // ← mandatory, no default
)
// No no-arg constructor. Compile error.
```

Additionally, `SecurityModule.kt` also registers a `SecurityAuditLogger` singleton:
```kotlin
// SecurityModule.kt:86
single { SecurityAuditLogger(auditRepository = get(), deviceId = get(named("deviceId"))) }
```

Two Koin `single` registrations for the same type = double binding = non-deterministic resolution.

---

### Check AV-08 — `ReceiptScreen.kt` HAL Imports ⬆️ RESOLVED

```
grep -n "import.*hal\." ReceiptScreen.kt → zero results
```

`ReceiptScreen.kt` now imports only from `designsystem`, `kotlinx`, and standard Compose.
This v1 AV-03 finding is confirmed resolved.

---

### Check AV-09 — `feature/pos/PrintReceiptUseCase.kt` Still Present 🔴 HIGH (AV2-P3-03)

```
find composeApp/feature/pos -name "PrintReceiptUseCase.kt"
→ .../feature/pos/src/commonMain/.../feature/pos/PrintReceiptUseCase.kt
```

Imports confirmed: `hal.printer.CharacterSet`, `hal.printer.EscPosReceiptBuilder`,
`hal.printer.PaperWidth`, `hal.printer.PrinterConfig`, `hal.printer.PrinterManager`,
`security.audit.SecurityAuditLogger` — 6 infrastructure types in a feature-layer `*UseCase` class.

---

### Check AV-10 — `InMemorySecurePreferences` Production Status 🔴 HIGH (AV2-P3-05 Confirmed)

```
AndroidDataModule.kt:51  → single<SecurePreferences> { InMemorySecurePreferences() }
DesktopDataModule.kt:70  → single<SecurePreferences> { InMemorySecurePreferences() }
```

Both production platform bindings confirmed. See Section 3B.

---

### Check AV-11 — `:shared:data → :shared:security` Dependency Note 🟡 LOW

```
shared/data/build.gradle.kts:30  implementation(project(":shared:security"))
```

`AuthRepositoryImpl` and `UserRepositoryImpl` import `security.auth.PasswordHasher` directly.
Direction is valid (`data → security → domain → core`) — no cycle. This is an architectural
note: `PasswordHasher` could be abstracted behind a domain port (`PasswordHashPort`) to eliminate
the `:shared:data → :shared:security` coupling entirely. Low priority given no circular risk.

---

## SECTION 5 — 4B: NAMING CONVENTIONS

### NC-01 — `*Entity` Suffix on Domain Models ✅ CLOSED (ADR-002 Accepted)

**What v1 flagged:** 26 domain model files lack `*Entity` suffix.

**What Phase 4 confirms:** `docs/adr/ADR-002-DomainModelNaming.md` exists and is ACCEPTED.
`CONTRIBUTING.md:32` references it as the authority. The convention is plain names (`Product`,
`Order`, `User` — no suffix). No `*Entity.kt` files exist anywhere in the project and none
are expected. **Finding NC-01 from v1 Phase 4 is permanently closed.**

---

### NC-02 — `PrintReceiptUseCase` in Feature Layer 🔴 HIGH (AV2-P3-03)

```
find composeApp/feature -name "*UseCase.kt"
→ composeApp/feature/pos/.../feature/pos/PrintReceiptUseCase.kt   ← only result
```

1 `*UseCase` file in the feature layer. All other 33 use cases correctly live in `:shared:domain`.
Resolves when the file is deleted (AV2-P4-01 action).

---

### NC-03 — All `*ViewModel`, `*Repository`, `*RepositoryImpl`, `*Screen`, `*Dto` Patterns ✅ CLEAN

| Pattern | Count | Violations |
|---------|-------|------------|
| `*ViewModel` (feature layer, production) | 6 | 0 |
| `BaseViewModel` (`:composeApp:core`) | 1 | 0 |
| `*Repository` interfaces (`:shared:domain`) | 14 | 0 |
| `*RepositoryImpl` (`:shared:data`) | 14 | 0 |
| `*UseCase` (`:shared:domain`) | 33 | 0 (NC-02 feature violation tracked separately) |
| `*Screen` | 29 | 0 |
| `*Dto` | 4 | 0 |

> **Note:** Repository count increased from v1 (12 interfaces, 10 impls) to 14 each — new repositories
> were added for `AuditRepository`, `StockRepository` and others. Counts are consistent (14 = 14).

---

### NC-04 — 7 Feature Modules Are Empty Shells (No ViewModel, No Screens) 🟡 LOW

```
admin:      0 ViewModels   coupons:    0 ViewModels   customers: 0 ViewModels
expenses:   0 ViewModels   media:      0 ViewModels   multistore:0 ViewModels
staff:      0 ViewModels
```

These 7 modules are registered in `settings.gradle.kts`, have `build.gradle.kts` and package
structure, but contain no ViewModel, no DI module, and no Screen composables. They represent 7
features fully planned in `Master_plan` with zero implementation. Not a naming violation — a scope
gap between planning and execution. Since `App.kt` has no navigation graph (AV2-P3-01), the gap
is hidden. It will surface the moment navigation is wired.

**Recommendation:** Mark these 7 modules as `SCAFFOLD — Not Started` in the `Master_plan`
dependency table. Add a `docs/sprint_progress.md` tracking module implementation status so sprint
planning does not assume implemented = registered.

---

### NC-05 — Gradle Module Names Match `settings.gradle.kts` ✅ CLEAN

23 `include(...)` entries vs 23 physical directories with `build.gradle.kts` files.

```
Physical feature dirs: admin, auth, coupons, customers, expenses, inventory,
                       media, multistore, pos, register, reports, settings, staff (13)
Physical shared dirs:  core, data, domain, hal, security (5)
Other modules:         :composeApp, :composeApp:core, :composeApp:designsystem,
                       :composeApp:navigation, :androidApp (5)
Total: 23 — exact match
```

Zero phantom registrations. Zero unregistered modules.

---

## SECTION 6 — 4C: BUILD CONFIGURATION

### BC-01 — `settings.gradle.kts` Coverage ✅ CLEAN

23 modules declared. 23 physical directories confirmed. Perfect 1:1 correspondence.

---

### BC-02 — `libs.versions.toml` Literal Version Strings ✅ CLEAN

```
grep -n "version = \"" gradle/libs.versions.toml → zero results
```

Every library and plugin entry uses `version.ref`. No literal strings remain.

> **Previously open (v1 BC-02):** `androidx-work-runtime` used literal `version = "2.10.1"`.
> **Now confirmed resolved:** `libs.versions.toml` has `androidx-work = "2.10.1"` in `[versions]`
> and `version.ref = "androidx-work"` in the library entry. ⬆️ RESOLVED

---

### BC-03 — All `libs.*` Catalog Accessors Resolve ✅ CLEAN

Full scan of all 23 `build.gradle.kts` files:
- All `libs.bundles.*` (`koin.common`, `kotlinx.common`, `ktor.common`, `sqldelight.common`,
  `testing.common`) have matching `[bundles]` entries.
- All `libs.plugins.*` references resolve to `[plugins]` entries.
- `libs.plugins.androidLibrary` appears in root `build.gradle.kts` with `apply false` — correctly
  registered for conditional use; not orphaned.
- `androidKmpLibrary` plugin has no version entry — intentional (bundled within AGP). ✅
- No `libs.*` accessor found without a catalog entry.
- No catalog entry found unreferenced (all entries have at least one consumer).

---

### BC-04 — New Rule: `:shared:domain` Build Script Guard 🔴 CRITICAL (Prevention)

As established in AV2-P4-01, the correct fix for the three domain use cases importing HAL is to
refactor them behind port interfaces — **not** to add `:shared:hal` to `:shared:domain`'s deps.

If `api(project(":shared:hal"))` or `api(project(":shared:security"))` is added to
`shared/domain/build.gradle.kts` as a short-cut fix:
- All 23 modules that depend on `:shared:domain` gain transitive HAL visibility — any module can
  then import HAL types without a compile error, erasing the entire Ports & Adapters boundary.
- The build script comment *"Depends only on :shared:core"* becomes permanently misleading.
- The pattern cannot be reversed without touching all 23 dependent modules.

**Recommendation:** Add the following guard comment to `shared/domain/build.gradle.kts`:
```kotlin
commonMain.dependencies {
    // GUARD: Only :shared:core is permitted here.
    // DO NOT add :shared:hal, :shared:security, or :shared:data.
    // HAL and security types must be accessed via port interfaces defined in this module.
    // See docs/adr/ADR-001-ViewModelBaseClass.md and Master_plan §3 for the layering contract.
    api(project(":shared:core"))
    ...
}
```

---

## SECTION 7 — CONSOLIDATED FINDING REGISTRY (All Open, All Phases)

| ID | Sev | Phase | Category | Short Description | Key File(s) |
|----|-----|-------|----------|-------------------|------------|
| AV2-P4-01 | 🔴 CRIT | P4 NEW | Arch + Build | 3 domain use cases import HAL; undeclared dep; latent build failure | `shared/domain/.../PrintTestPageUseCase.kt`, `PrintZReportUseCase.kt`, `PrintReportUseCase.kt` |
| AV2-P3-01 | 🔴 CRIT | P3 | Code Gap | App.kt placeholder; Koin never starts; no nav graph | `App.kt`, `MainActivity.kt`, `main.kt` |
| AV2-P3-02 | 🔴 CRIT | P3 | Code Bug | `posModule` calls `SecurityAuditLogger()` — compile error | `PosModule.kt:49` |
| NF-02 | 🔴 HIGH | P1 | Code Bug | `TaxGroupRepositoryImpl` + `UnitGroupRepositoryImpl` all TODO — runtime crash | `TaxGroupRepositoryImpl.kt`, `UnitGroupRepositoryImpl.kt` |
| AV2-P4-03 | 🔴 HIGH | P4 UPG | Architecture | Print*UseCase HAL contamination — 4-file pattern | `feature/pos/PrintReceiptUseCase.kt` + 3 domain use cases |
| AV2-P3-05 | 🔴 HIGH | P3→P4 | Security | `InMemorySecurePreferences` bound in production — tokens lost on restart | `AndroidDataModule.kt:51`, `DesktopDataModule.kt:70` |
| AV2-P2-02 | 🔴 HIGH | P2 | Arch + DI | `posModule` + `securityModule` double-bind `SecurityAuditLogger`; undocumented M05 dep | `PosModule.kt`, `SecurityModule.kt` |
| AV2-P4-02 | 🟠 MED | P4 NEW | Architecture | `SettingsViewModel` imports `hal.printer.PaperWidth` directly | `SettingsViewModel.kt:15` |
| AV2-P4-04 | 🟠 MED | P4 NEW | DI Gap | `PrintTestPageUseCase` not registered in any Koin module — runtime gap | `SettingsModule.kt`, `PrintTestPageUseCase.kt` |
| AV2-P3-04 | 🟠 MED | P3 | Architecture | Dual `SecurePreferences` types in data vs security modules | `data/local/security/SecurePreferences.kt`, `security/prefs/SecurePreferences.kt` |
| AV2-P2-07 | 🟠 MED | P2 | Architecture | Dual `SecurePreferences` interface — partially fixed; split remains | same as above |
| AV2-P2-08 | 🟠 MED | P2 | DI | `SecurityAuditLogger` duplicate singleton — confirmed compile error | `PosModule.kt:49` (same as P3-02) |
| AV2-P2-01 | 🟠 MED | P2 | Doc | Master_plan lists M03 on all feature modules — no feature has `:shared:data` dep | `Master_plan.md lines 279-292` |
| AV2-P2-06 | 🟠 MED | P2 | Doc | `execution_log.md` DOD D2 structurally unsatisfiable | `PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md` |
| AV2-P3-06 | 🟠 MED | P3 | Code | `generateUuid()` uses `kotlin.random.Random` — not CSPRNG | `SettingsViewModel.kt:438–445` |
| AV-11 | 🟡 LOW | P4 NOTE | Architecture | `:shared:data` declares `:shared:security` — `PasswordHasher` not behind a port | `shared/data/build.gradle.kts:30` |
| NC-04 | 🟡 LOW | P4 NEW | Scope | 7 feature modules are empty shells with no ViewModel or Screens | `feature/{admin,coupons,customers,expenses,media,multistore,staff}` |
| NF-06 | 🟡 LOW | P1 | Code Gap | `SecurePreferencesKeyMigration.migrate()` not wired to startup | `MainActivity.kt`, `main.kt` |
| NF-07 | 🟡 LOW | P1 | Doc | ADR-001 file exists; missing from `CONTRIBUTING.md` ADR table | `CONTRIBUTING.md:116` |
| NF-05 | 🟡 LOW | P1 | Doc | `ZENTRA POINT OF SALE` in `ReceiptFormatter.kt` KDoc | `ReceiptFormatter.kt:23` |
| NF-04 | 🟡 LOW | P1 | Doc | `zentapos-audit-final-synthesis.md` unfilled template | `docs/zentapos-audit-final-synthesis.md` |
| NF-09 | 🟡 LOW | P1 | Code | Boilerplate `compose-multiplatform.xml` in drawable resources | `composeApp/src/commonMain/composeResources/drawable/` |
| AV2-P2-03 | 🟡 LOW | P2 | Doc | M11 register: remove M04 from Master_plan dep column | `Master_plan.md` |
| AV2-P2-04 | 🟡 LOW | P2 | Doc | M18 settings: remove M05 from Master_plan dep column | `Master_plan.md` |
| AV2-P2-05 | 🟡 LOW | P2 | Doc | M08 auth: remove M05 from Master_plan dep column | `Master_plan.md` |
| NF-03 | 🟡 LOW | P1 | Doc | PLAN_ZENTA_TO_ZYNTA STATUS wrong; DOD unchecked | `PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md` |
| NF-08 | 🟡 LOW | P1 | Code | Empty `keystore/` + `token/` scaffold directories | `shared/security/src/*/security/keystore/`, `.../token/` |
| Koin-ord | 🟡 LOW | P3 | DI | `registerModule` HAL load-ordering undocumented | `RegisterModule.kt` |
| BC-04 | — | P4 | Build Rule | Guard comment needed in `shared/domain/build.gradle.kts` | `shared/domain/build.gradle.kts` |

**Findings closed this phase:** NC-01 (v1) — Domain model `*Entity` suffix → confirmed superseded by ADR-002.

---

## SECTION 8 — SPRINT ACTION PLAN

### 🔴 P0 — Sprint Blocker (Before ANY CI Run or QA Session)

| # | ID | Exact Action | File |
|---|----|--------------|----- |
| 1 | AV2-P4-01 | Create `ZReportPrinterPort` + `ReportPrinterPort` interfaces in `shared/domain/.../domain/printer/` | New files |
| 2 | AV2-P4-01 | Refactor `PrintZReportUseCase`: inject `ZReportPrinterPort`; delete `hal.*` imports | `shared/domain/.../usecase/register/PrintZReportUseCase.kt` |
| 3 | AV2-P4-01 | Refactor `PrintReportUseCase`: inject `ReportPrinterPort`; delete `hal.*` imports | `shared/domain/.../usecase/reports/PrintReportUseCase.kt` |
| 4 | AV2-P4-01 | Refactor `PrintTestPageUseCase`: create `TestPagePrinterPort` or move class out of domain | `shared/domain/.../usecase/settings/PrintTestPageUseCase.kt` |
| 5 | AV2-P4-01 | Add guard comment to `shared/domain/build.gradle.kts` commonMain deps block | `shared/domain/build.gradle.kts` |
| 6 | AV2-P3-05 | Replace `InMemorySecurePreferences` with `AndroidEncryptedSecurePreferences(androidContext())` using `libs.androidx.security.crypto` | `shared/data/src/androidMain/.../di/AndroidDataModule.kt:51` |
| 7 | AV2-P3-05 | Replace `InMemorySecurePreferences` with `DesktopAesSecurePreferences(get<EncryptionManager>())` | `shared/data/src/jvmMain/.../di/DesktopDataModule.kt:70` |
| 8 | AV2-P3-01 | Create `ZyntaApplication.kt`; call `startKoin { androidContext(this); modules(allModules) }` | `androidApp/src/main/kotlin/.../ZyntaApplication.kt` (NEW) |
| 9 | AV2-P3-01 | Call `startKoin { modules(allModules) }` at top of `main()` in `main.kt` before `application {}` | `composeApp/src/jvmMain/.../main.kt` |
| 10 | AV2-P3-01 | Replace `Text("ZyntaPOS — Initializing…")` with `ZyntaNavGraph(navController)` in `App.kt` | `composeApp/src/commonMain/.../App.kt` |
| 11 | AV2-P3-02 | Remove `single { SecurityAuditLogger() }` from `posModule`; resolve via `get()` from `securityModule` | `composeApp/feature/pos/.../PosModule.kt:49` |

### 🔴 P1 — Sprint 5 Architecture Correctness

| # | ID | Exact Action | File |
|---|----|--------------|----- |
| 1 | AV2-P4-01 | Implement `ZReportPrinterAdapter` in `feature/register/.../printer/` (follows `PrinterManagerReceiptAdapter` pattern) | New file in `feature/register` |
| 2 | AV2-P4-01 | Implement `ReportPrinterAdapter` in `feature/reports/.../printer/` | New file in `feature/reports` |
| 3 | AV2-P4-04 | Register `PrintTestPageUseCase` (or its port) in `settingsModule` | `SettingsModule.kt` |
| 4 | AV2-P4-03 | Delete `composeApp/feature/pos/.../feature/pos/PrintReceiptUseCase.kt` | Delete file |
| 5 | AV2-P4-02 | After AV2-P4-01: remove `hal.printer.PaperWidth` import + HAL-mapping from `SettingsViewModel.kt` | `SettingsViewModel.kt:15, 294` |
| 6 | AV2-P4-02 | Remove `implementation(project(":shared:hal"))` from `feature/settings/build.gradle.kts` | `feature/settings/build.gradle.kts:30` |
| 7 | NF-02 | Implement all 5 methods in `TaxGroupRepositoryImpl` using existing `taxGroupsQueries` | `shared/data/.../repository/TaxGroupRepositoryImpl.kt` |
| 8 | NF-02 | Implement all 5 methods in `UnitGroupRepositoryImpl` using existing `unitsOfMeasureQueries` | `shared/data/.../repository/UnitGroupRepositoryImpl.kt` |
| 9 | AV2-P3-04 | Consolidate `SecurePreferences`: eliminate `data/local/security/SecurePreferences.kt`; add `contains()` to security expect class; update `DataModule` + `AuthRepositoryImpl` | `data/local/security/`, `security/prefs/SecurePreferences.kt` |

### 🟠 P2 — Sprint 5 Quality

| # | ID | Exact Action | File |
|---|----|--------------|----- |
| 1 | AV2-P3-06 | Replace `generateUuid()` with `IdGenerator.generateId()` from `:shared:core` | `SettingsViewModel.kt:438–450` |
| 2 | NF-06 | Wire `SecurePreferencesKeyMigration(get()).migrate()` in `ZyntaApplication.kt` after `startKoin` | `ZyntaApplication.kt` |
| 3 | NC-04 | Add `SCAFFOLD — Not Started` status to 7 empty feature modules in `Master_plan.md` | `Master_plan.md` |
| 4 | AV2-P2-01 | Remove M03 from all feature module dep columns in `Master_plan.md` | `Master_plan.md lines 279-292` |
| 5 | AV2-P2-03/04/05 | Remove incorrect M04 (M11), M05 (M18), M05 (M08) entries from `Master_plan.md` dep table | `Master_plan.md` |
| 6 | Koin-ord | Add KDoc ordering note to `registerModule` + `reportsModule`: *"Requires halModule to provide PrinterManager + EscPosReceiptBuilder"* | `RegisterModule.kt`, `ReportsModule.kt` |

### 🟡 P3 — Sprint 6 Hygiene

| # | ID | Exact Action | File |
|---|----|--------------|----- |
| 1 | NF-07 | Add `ADR-001-ViewModelBaseClass.md` row to `CONTRIBUTING.md:116` ADR table | `CONTRIBUTING.md` |
| 2 | NF-05 | Change `ZENTRA POINT OF SALE` → `ZYNTA POINT OF SALE` | `shared/domain/.../formatter/ReceiptFormatter.kt:23` |
| 3 | NF-09 | Delete `compose-multiplatform.xml` | `composeApp/src/commonMain/composeResources/drawable/` |
| 4 | BC-04 | Guard comment added to `shared/domain/build.gradle.kts` | `shared/domain/build.gradle.kts` |
| 5 | AV-11 | Review `PasswordHashPort` abstraction for `PasswordHasher` at Sprint 6 arch review | `shared/domain`, `shared/data`, `shared/security` |
| 6 | NF-04 | Fill synthesis template or rename to `…-TEMPLATE.md` | `docs/zentapos-audit-final-synthesis.md` |
| 7 | NF-08 | ADR decision on `keystore/` + `token/` scaffold dirs | `shared/security/src/*/security/` |

---

## SECTION 9 — VERIFICATION CHECKLIST (Full State as of Phase 4)

| Check | Evidence | Status |
|-------|----------|--------|
| Zombie `shared/core/mvi/BaseViewModel.kt` deleted | Directory is `.gitkeep` only | ✅ |
| All 6 feature ViewModels extend `ui.core.mvi.BaseViewModel` | Confirmed for all present VMs | ✅ |
| All 13 feature modules declare `:composeApp:core` | Confirmed in all `build.gradle.kts` | ✅ |
| `ReceiptScreen.kt` has no HAL imports | `grep import.*hal` → zero results | ✅ |
| Root `BarcodeScanner.kt` duplicate deleted | Not in file listing | ✅ |
| Root `SecurityAuditLogger.kt` duplicate deleted | Not in file listing | ✅ |
| Design system uses `Zynta*` prefix throughout | `ZyntaDialogContent`, `ZyntaSpacing` confirmed | ✅ |
| Test fakes split by domain (`Fake*Repositories.kt`) | 4 domain-split fake files confirmed | ✅ |
| `ProductValidator` in `:shared:domain` | `shared/domain/.../validation/ProductValidator.kt` confirmed | ✅ |
| `PosSearchBar` delegates to `ZyntaSearchBar` | Source confirmed (Phase 3) | ✅ |
| `SecurePreferencesKeys` single source of truth for key constants | Key delegation confirmed | ✅ |
| `PlaceholderPasswordHasher` moved to `:shared:security` | File confirmed | ✅ |
| `androidx-work-runtime` uses `version.ref` | `libs.versions.toml` confirmed | ✅ |
| ADR-002 accepted — plain domain model names | `docs/adr/ADR-002-DomainModelNaming.md` confirmed ACCEPTED | ✅ |
| No circular module dependencies | Full DAG verified | ✅ |
| No `android.*` in `shared/domain/commonMain` | grep → zero results | ✅ |
| No `java.*` in `shared/domain/commonMain` | grep → zero results | ✅ |
| No feature module imports `:shared:data` | grep → zero results | ✅ |
| All `libs.*` catalog refs resolve | Full scan confirmed | ✅ |
| All 23 modules in `settings.gradle.kts` have physical dirs | 23 = 23 | ✅ |
| `PrintTestPageUseCase` / `PrintZReportUseCase` / `PrintReportUseCase` use port interfaces | All 3 import `hal.*` directly — ports do not exist yet | ❌ |
| `:shared:domain` declares only `:shared:core` dep | Confirmed — HAL imports are ghosted | ❌ LATENT FAIL |
| `PrintTestPageUseCase` registered in Koin | Not registered in any module | ❌ |
| Koin initialised in Android entry point | No `Application` subclass; no `startKoin` anywhere | ❌ |
| `ZyntaNavGraph` wired in `App.kt` | `Text("ZyntaPOS — Initializing…")` only | ❌ |
| `posModule` compiles cleanly | `SecurityAuditLogger()` no-arg call at line 49 | ❌ |
| `TaxGroupRepositoryImpl` implemented | 6 TODO methods | ❌ |
| `UnitGroupRepositoryImpl` implemented | 5 TODO methods | ❌ |
| `feature/pos/PrintReceiptUseCase.kt` deleted | File present with 6 HAL imports | ❌ |
| Single canonical `SecurePreferences` type | Two parallel types in different packages | ❌ |
| `InMemorySecurePreferences` absent from production | Bound in both platform data modules | ❌ |
| `SecurePreferencesKeyMigration.migrate()` wired at startup | Not called anywhere | ❌ |
| `SettingsViewModel.generateUuid()` uses CSPRNG | Uses `(0..15).random()` | ❌ |
| `SettingsViewModel` has no direct HAL imports | Imports `hal.printer.PaperWidth` at line 15 | ❌ |
| `feature/settings/build.gradle.kts` has no `:shared:hal` dep | Line 30 declares it | ❌ |
| `compose-multiplatform.xml` deleted | File still present | ❌ |
| `ZENTRA POINT OF SALE` fixed in `ReceiptFormatter.kt` | Still at line 23 | ❌ |
| ADR-001 row in `CONTRIBUTING.md` table | ADR file exists; table row missing | ❌ |
| 7 empty feature modules documented as scaffold | No annotation found | ❌ |

---

## SECTION 10 — CUMULATIVE AUDIT METRICS

| Metric | v2 Phase 1 | v2 Phase 2 | v2 Phase 3 | **v2 Phase 4** |
|--------|-----------|-----------|-----------|----------------|
| Total open findings | 9 | 26 | 28 | **31** |
| Closed this phase | — | 1 | 6 | **1** (NC-01 via ADR-002) |
| New findings this phase | — | 8 | 9 | **5** (AV2-P4-01, AV2-P4-02, AV2-P4-03 upgrade, AV2-P4-04, AV-11 note) |
| Escalated this phase | — | — | — | **1** (AV2-P3-05: NEEDS CLARIFICATION → CONFIRMED HIGH) |
| 🔴 Critical | — | 0 | 2 | **3** (AV2-P4-01 new; AV2-P3-01, AV2-P3-02 carried) |
| 🔴 High | 3 | 5 | 5 | **6** (AV2-P3-05 confirmed; AV2-P4-03 upgraded) |
| 🟠 Medium | 2 | 11 | 9 | **10** (AV2-P4-02, AV2-P4-04 new) |
| 🟡 Low / Hygiene | 4 | 10 | 12 | **12** (NC-04 new; AV-11 note) |
| NEEDS CLARIFICATION | — | 3 | 1 | **0** (AV2-P3-05 resolved) |
| Functional code fixes shipped (all phases) | — | — | — | **0** |

---

*End of Audit v2 — Phase 4 — ZyntaPOS ZENTA-AUDIT-V2-PHASE4-INTEGRITY*
*Total open findings: 31 | Critical: 3 | High: 6 | Medium: 10 | Low: 12*
*Closed this phase: 1 (NC-01 — domain model naming superseded by ADR-002)*
*Prior-version resolutions confirmed: v1 AV-01, v1 AV-03, v1 BC-02*
*Next: Phase 5 — Fix Execution Tracker (AV2-P4-01 domain HAL refactor, AV2-P3-05 encrypted prefs, AV2-P3-01 Koin startup, AV2-P3-02 posModule compile fix)*
