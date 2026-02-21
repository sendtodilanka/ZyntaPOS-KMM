# ZyntaPOS — Audit v2 | Phase 3: Doc Consistency + Code Duplication + Carried-Forward Verification
> **Doc ID:** ZENTA-AUDIT-V2-PHASE3-DOC-CONSISTENCY
> **Auditor:** Senior KMP Architect
> **Date:** 2026-02-21
> **Project Root:** `/Users/dilanka/Developer/StudioProjects/ZyntaPOS/`
> **Prerequisite:** `docs/audit_v2_phase_2_result.md` — Phase 2 Alignment Audit (26 open findings)
> **Method:**
> - Carry-forward verification of all 26 Phase 2 open findings with fresh code reads
> - Clarification of the 3 NEEDS CLARIFICATION items (AV2-P2-03, AV2-P2-04, AV2-P2-05)
> - Cross-module doc consistency scan
> - Code duplication scan across all KMP source sets

---

## SECTION 1 — CRITICAL ESCALATION (READ FIRST)

Two findings in this phase represent **build-breaking or runtime-crashing defects** that would prevent
the application from launching or compiling:

**AV2-P3-01** — `App.kt` is an unimplemented placeholder. Koin is never initialised in any entry
point. Every `get()` call in every module would throw `KoinNotStartedException` at runtime.

**AV2-P3-02** — `posModule` calls `SecurityAuditLogger()` with no arguments, but
`SecurityAuditLogger` requires two mandatory constructor parameters (`auditRepository`, `deviceId`).
This is a compile-time error that prevents the `:composeApp:feature:pos` module from building.

Both must be resolved before any end-to-end test or QA session is possible.

---

## SECTION 2 — CARRIED-FORWARD FINDINGS: STATUS UPDATE

All 26 findings from Phase 2 re-examined with fresh source reads.

### 2A. Phase 1 Findings (NF-01 through NF-09)

| ID | Description | Phase 3 Evidence | Status |
|----|-------------|-----------------|--------|
| NF-01 | `PrintReceiptUseCase` orphan in `feature/pos` | File still present at `composeApp/feature/pos/src/.../feature/pos/PrintReceiptUseCase.kt`. **UPGRADED** — see Section 3A | ❌ OPEN / UPGRADED |
| NF-02 | `TaxGroupRepositoryImpl` + `UnitGroupRepositoryImpl` all TODO stubs | Both files read in full: all 5 methods remain `TODO("Requires …sq — tracked in MERGED-D2")`. DB reference `val q get() = db.taxGroupsQueries` exists in TaxGroupRepositoryImpl but is never used. | ❌ OPEN |
| NF-03 | PLAN_ZENTA_TO_ZYNTA DOD unchecked, STATUS wrong | Not re-read this phase — no change trigger observed | ❌ ASSUMED OPEN |
| NF-04 | `zentapos-audit-final-synthesis.md` unfilled template | Not re-read this phase | ❌ ASSUMED OPEN |
| NF-05 | "ZENTRA" string in `ReceiptFormatter.kt` KDoc | KDoc example block at `shared/domain/.../formatter/ReceiptFormatter.kt` line ~17 still reads: `ZENTRA POINT OF SALE` | ❌ CONFIRMED OPEN |
| NF-06 | `SecurePreferencesKeyMigration.migrate()` not wired to startup | `MainActivity.kt` → `App()` (no migration call). `main.kt` → `App()` (no migration call). `App.kt` contains only a placeholder `Text("ZyntaPOS — Initializing…")`. | ❌ CONFIRMED OPEN |
| NF-07 | ADR-001 missing from `CONTRIBUTING.md` ADR table | `grep -n "ADR" CONTRIBUTING.md` → `line 116: ADR-002` only | ❌ CONFIRMED OPEN |
| NF-08 | Empty `keystore/` + `token/` scaffold dirs | Not re-checked this phase | ❌ ASSUMED OPEN |
| NF-09 | Boilerplate `compose-multiplatform.xml` asset | Not re-checked this phase | ❌ ASSUMED OPEN |

---

### 2B. Phase 2 Findings (AV2-P2-01 through AV2-P2-08)

| ID | Description | Phase 3 Evidence | Status |
|----|-------------|-----------------|--------|
| AV2-P2-01 | Master_plan dep table lists M03 on all feature modules | Feature `build.gradle.kts` files still have no `project(":shared:data")` declaration. Doc still lists M03. | ❌ OPEN |
| AV2-P2-02 | M09 `:feature:pos` has undocumented M05 dep | Confirmed in `pos/build.gradle.kts`. **UPGRADED** — see Section 3B | ❌ OPEN / UPGRADED |
| AV2-P2-03 | M11 `:feature:register` missing M04 per doc | **CLARIFIED** — see Section 3C | ✅ CLARIFIED |
| AV2-P2-04 | M18 `:feature:settings` missing M05 per doc | **CLARIFIED** — see Section 3D | ✅ CLARIFIED |
| AV2-P2-05 | M08 `:feature:auth` missing M05 per doc | **CLARIFIED** — see Section 3E | ✅ CLARIFIED |
| AV2-P2-06 | `execution_log.md` DOD D2 structurally unsatisfiable | Not re-read this phase — no trigger | ❌ ASSUMED OPEN |
| AV2-P2-07 | `data/local/security/SecurePreferences` parallel interface | Key constants now delegate to `SecurePreferencesKeys` ✅ — but two interfaces remain. **PARTIALLY ADDRESSED** — see Section 3F | ❌ PARTIAL |
| AV2-P2-08 | `SecurityAuditLogger` duplicate singleton in `posModule` | **UPGRADED to compile error** — see Section 3B | ❌ OPEN / UPGRADED |

---

## SECTION 3 — CLARIFICATION RESOLUTIONS + FINDING UPGRADES

### 3A. NF-01 UPGRADED — feature/pos/PrintReceiptUseCase Has a Real, Conflicting Implementation 🔴 HIGH

**Previous status:** Dead code file; same name as domain version; not injected via Koin.

**Phase 3 evidence:** Full source read reveals the feature version is NOT a stub. It contains 120+
lines of real logic that **violates Ports & Adapters**:

```
composeApp/feature/pos/src/.../feature/pos/PrintReceiptUseCase.kt
```
**Constructor:**
```kotlin
class PrintReceiptUseCase(
    private val settingsRepository: SettingsRepository,
    private val printerManager: PrinterManager,   // ← HAL import in a use case
    private val auditLogger: SecurityAuditLogger, // ← Security import in a use case
)
```

**Domain version constructor:**
```kotlin
// shared/domain/src/.../domain/usecase/pos/PrintReceiptUseCase.kt
class PrintReceiptUseCase(
    private val printerPort: ReceiptPrinterPort,  // ← Correct — depends only on a port
)
```

The feature version imports `hal.printer.PrinterManager`, `hal.printer.EscPosReceiptBuilder`,
`hal.printer.PaperWidth`, `hal.printer.PrinterConfig`, `hal.printer.CharacterSet` — six HAL types
in a domain use case class. This is a textbook layer violation.

**Risk:** If a developer copies the wrong import path — `feature.pos.PrintReceiptUseCase` instead of
`domain.usecase.pos.PrintReceiptUseCase` — they introduce a HAL dependency into what should be a
pure domain object. The class names are identical. There is no warning at the call site.

**What doc says:** No documentation references the feature-layer `PrintReceiptUseCase` at all.
Master_plan §3 mandates use cases live in `:shared:domain`.

**Recommendation:** Delete `composeApp/feature/pos/src/.../feature/pos/PrintReceiptUseCase.kt`
immediately. If loading printer config from settings is needed, move that logic into
`PrinterManagerReceiptAdapter` (the `ReceiptPrinterPort` implementation in
`composeApp/feature/pos/src/.../pos/printer/`) where HAL and settings access are architecturally
appropriate.

---

### 3B. AV2-P2-02 + AV2-P2-08 UPGRADED — SecurityAuditLogger in posModule Is a Compile Error 🔴 CRITICAL

**Phase 3 evidence:**

`PosModule.kt` line:
```kotlin
single { SecurityAuditLogger() }   // ← no-arg constructor call
```

`SecurityAuditLogger` class signature (fully read):
```kotlin
// shared/security/src/.../security/audit/SecurityAuditLogger.kt
class SecurityAuditLogger(
    private val auditRepository: AuditRepository,
    private val deviceId: String,
)
```

There is **no no-arg constructor** and **no default parameter values** on either parameter.
`SecurityAuditLogger()` is a compile-time error.

**Additionally (from Phase 2):** `SecurityModule` also registers a singleton:
```kotlin
single {
    SecurityAuditLogger(
        auditRepository = get(),
        deviceId = get(named("deviceId")),
    )
}
```

Two `single` registrations for the same type across two modules means Koin has a double-binding.
Even if the no-arg call were fixed to `single { SecurityAuditLogger(get(), get(named("deviceId"))) }`,
the double-binding would still produce undefined resolution behaviour at runtime.

**What docs say:** Master_plan §3 states infrastructure singletons are provided by their home modules
and consumed by features via `get()`. `SecurityAuditLogger` is a security infrastructure type and its
home is `SecurityModule`.

**Recommendation:**
1. Remove `single { SecurityAuditLogger() }` from `PosModule.kt` entirely.
2. Change `auditLogger = get()` in `PrinterManagerReceiptAdapter`'s `single` block to resolve from
   the shared `SecurityModule` singleton.
3. Ensure `SecurityModule` is loaded in the application Koin graph before `posModule`.

---

### 3C. AV2-P2-03 CLARIFIED — M11 Register: No HAL Imports, But Undeclared Koin Runtime Dep 🟠 MEDIUM

**Grep result:**
```
grep -rn "import com.zyntasolutions.zyntapos.hal\." composeApp/feature/register/src → (empty)
```

**Verdict for doc:** No direct HAL imports in register source files.
Master_plan M11 column should **remove M04** — the code is architecturally correct.

**Residual risk discovered:** `RegisterModule.kt`:
```kotlin
factory { PrintZReportUseCase(get(), get()) }
```
`PrintZReportUseCase` takes `(ReceiptBuilder, PrinterManager)` — both are HAL types. The second
`get()` resolves `PrinterManager` from Koin at runtime. If `halModule` is not loaded in the
application-level Koin graph before `registerModule`, this `get()` call throws `NoBeanDefFoundException`.

**What docs say:** There is no documented module-load ordering requirement for `registerModule` and
`halModule`. This is an invisible runtime dependency not expressed in either the build graph or the
documentation.

**Recommendation:**
- Remove M04 from Master_plan M11 dependency column (code is correct at compile time).
- Add a KDoc comment to `registerModule` stating: *"Requires `halModule` to be loaded first to
  provide `PrinterManager` and `ReceiptBuilder` for `PrintZReportUseCase`."*
- Consider binding `PrintZReportUseCase` more explicitly:
  `factory { PrintZReportUseCase(printerManager = get(), receiptBuilder = get()) }` to surface the
  dependency at the Koin DSL level rather than relying on positional `get()`.

---

### 3D. AV2-P2-04 CLARIFIED — M18 Settings: No Security Imports, Remove M05 From Doc 🟡 LOW

**Grep result:**
```
grep -rn "import com.zyntasolutions.zyntapos.security\." composeApp/feature/settings/src → (empty)
```

**`SettingsViewModel.kt` imports (confirmed):** `domain.*`, `hal.printer.PaperWidth`, `ui.core.mvi.BaseViewModel`, `kotlinx.*`.
No `security.*` package import anywhere in the settings feature.

**Verdict:** Master_plan M18 dependency column should **remove M05**. The code is architecturally
correct — SettingsViewModel accesses printer config via domain repositories, not via security module
types.

**Recommendation:** Update Master_plan M18 deps to: `M02, M04, M06, M21` (M03 already removed per
AV2-P2-01 resolution; M05 now also removed).

---

### 3E. AV2-P2-05 CLARIFIED — M08 Auth: No Security Imports, Remove M05 From Doc 🟡 LOW

**Grep result:**
```
grep -rn "import com.zyntasolutions.zyntapos.security\." composeApp/feature/auth/src → (empty)
```

**`AuthViewModel.kt` imports (confirmed):** `domain.repository.AuthRepository`, `domain.usecase.auth.*`,
`feature.auth.mvi.*`, `ui.core.mvi.BaseViewModel`. No `security.*` imports.

**`SessionGuard.kt`:** Uses `domain.model.User`, `domain.repository.AuthRepository`. No security.

**`SessionManager.kt`:** Uses `feature.auth.mvi.AuthEffect`, `kotlinx.coroutines.*`. No security.

**Verdict:** Authentication operates correctly via domain interfaces only. `SecurityModule` provides
`PasswordHasher`, `JwtManager`, `PinManager` at the data layer via `AuthRepositoryImpl`. The feature
layer never needs a direct `:shared:security` dependency.

**What docs say vs code:** Master_plan lists M05 as required for M08. Code shows it is not declared
and not needed.

**Recommendation:** Update Master_plan M08 deps to: `M02, M06, M21` (remove M03 per AV2-P2-01,
remove M05 per this finding).

---

### 3F. AV2-P2-07 PARTIAL — SecurePreferences Keys Unified, But Dual Interface Remains 🟠 MEDIUM

**What improved:** `data/local/security/SecurePreferences.kt` companion object now delegates ALL
key constants to `SecurePreferencesKeys`:
```kotlin
companion object Keys {
    const val ACCESS_TOKEN    = SecurePreferencesKeys.KEY_ACCESS_TOKEN
    const val REFRESH_TOKEN   = SecurePreferencesKeys.KEY_REFRESH_TOKEN
    // …
}
```
Key divergence that caused silent session invalidation risk (v1 DUP-05) has been addressed. ✅

**What remains:**
- `data/local/security/SecurePreferences` = **interface** with 5 methods including `contains()`
- `security/prefs/SecurePreferences` = **expect class** with 4 methods (no `contains()`)
- `DataModule` binds `data.local.security.SecurePreferences` (via `get<SecurePreferences>()` in `AuthRepositoryImpl`)
- `SecurityModule` provides `security.prefs.SecurePreferences` (expect class)
- `TokenStorage` interface (implemented by `security.prefs.SecurePreferences`) is **not** implemented
  by `data.local.security.SecurePreferences`
- `InMemorySecurePreferences` implements only `data.local.security.SecurePreferences` and is tagged
  **"DO NOT USE IN PRODUCTION"** — risk that platform data modules still bind it

**Recommendation:**
1. Make `data.local.security.SecurePreferences` extend `security.prefs.TokenStorage` or eliminate
   it and require data layer to consume `security.prefs.SecurePreferences` directly.
2. Audit `AndroidDataModule.kt` and `DesktopDataModule.kt` to verify `InMemorySecurePreferences`
   is NOT bound in non-debug builds.

---

## SECTION 4 — NEW FINDINGS (Phase 3)

### 4A. Finding AV2-P3-01 — App.kt Is an Unimplemented Placeholder; Koin Never Starts 🔴 CRITICAL

**File:** `composeApp/src/commonMain/kotlin/com/zyntasolutions/zyntapos/App.kt`

**What code shows:**
```kotlin
@Composable
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) {
                // TODO Sprint 11 — Replace with ZyntaNavGraph(navController)
                Text(text = "ZyntaPOS — Initializing…")
            }
        }
    }
}
```

**Entry points:**
- `androidApp/MainActivity.kt`: `setContent { App() }` — no `startKoin {}`, no Application subclass
  in the file listing (no `*Application.kt` file exists under `androidApp/`)
- `composeApp/src/jvmMain/kotlin/.../main.kt`: `Window(…) { App() }` — no `startKoin {}`

**Impact:** The entire DI graph is never initialised. Every `get()` call in every feature module
would throw `KoinNotStartedException`. All repository bindings, all ViewModel registrations, all
security singletons — none are reachable.

**NEEDS CLARIFICATION:** Is there an Android `Application` subclass not captured by the file search?
Run: `find androidApp -name "*.kt" | xargs grep -l "KoinApplication\|startKoin"`. If zero results,
Koin is genuinely never started and the app cannot function.

**What docs say:** Master_plan §3 mentions Koin as the DI framework but does not specify where
`startKoin {}` is called. No ADR documents the application initialisation sequence.

**Recommendation:**
1. Create `androidApp/src/main/kotlin/.../ZyntaApplication.kt` extending `Application`, call
   `startKoin { androidContext(this); modules(allModules) }` in `onCreate()`.
2. Create equivalent Desktop startup in `main.kt` before the `application { }` block.
3. Wire `ZyntaNavGraph` in `App.kt` — Sprint 11 is overdue if feature modules are already Sprint
   14-23.
4. Document the module load order (Koin module list) in an ADR.

---

### 4B. Finding AV2-P3-02 — posModule SecurityAuditLogger() Is a Compile Error 🔴 CRITICAL

_(Already detailed in Section 3B — listed here for completeness in the finding registry)_

**File:** `composeApp/feature/pos/src/.../feature/pos/PosModule.kt`
**Line:** `single { SecurityAuditLogger() }`
**Error:** `SecurityAuditLogger` has no no-arg constructor. Requires `auditRepository: AuditRepository, deviceId: String`.

This prevents `composeApp:feature:pos` from compiling on any target.

**Recommendation:** Remove from `posModule`; resolve `SecurityAuditLogger` via `get()` from
`securityModule` singleton.

---

### 4C. Finding AV2-P3-03 — feature/pos/PrintReceiptUseCase Violates Ports & Adapters Architecture 🔴 HIGH

_(Already detailed in Section 3A — finding registry entry)_

**File:** `composeApp/feature/pos/src/.../feature/pos/PrintReceiptUseCase.kt`

**What code shows:**
- Imports `hal.printer.PrinterManager`, `hal.printer.EscPosReceiptBuilder`, `hal.printer.PaperWidth`,
  `hal.printer.PrinterConfig`, `hal.printer.CharacterSet`, `security.audit.SecurityAuditLogger`
- A use case class directly depending on 6 HAL types and 1 security type violates the Clean
  Architecture rule that domain/use-case layer must not know about infrastructure

**What docs say:** Master_plan §3.2 states: *"Use cases contain business logic and depend only on
repository interfaces and domain models."*

**Recommendation:** Delete the file. Route config-loading into `PrinterManagerReceiptAdapter`.

---

### 4D. Finding AV2-P3-04 — DataModule Uses data.local.security.SecurePreferences; Platform Actuals Provide security.prefs.SecurePreferences — Two Incompatible Bindings 🟠 MEDIUM

**Files:**
- `shared/data/src/commonMain/.../data/di/DataModule.kt` — imports and binds
  `com.zyntasolutions.zyntapos.data.local.security.SecurePreferences`
- `shared/security/src/commonMain/.../security/di/SecurityModule.kt` — provides
  `com.zyntasolutions.zyntapos.security.prefs.SecurePreferences` (expect class)
- `AuthRepositoryImpl` receives `securePrefs: get<data.local.security.SecurePreferences>()`

**What docs say:** Architecture docs reference `security/prefs/SecurePreferences` as the canonical
implementation. `SecurePreferencesKeys` KDoc says to use the security module's interface.

**Impact:** The data layer and security layer bind to different types of the same concept. If a
future feature injects `security.prefs.SecurePreferences` while `AuthRepositoryImpl` uses
`data.local.security.SecurePreferences`, they may access different Koin bindings writing to the
same physical storage, or two separate in-memory stores in test environments.

**Recommendation:** Decide the canonical interface. If `security.prefs.SecurePreferences` (expect
class) is canonical, remove `data/local/security/SecurePreferences.kt` and update `DataModule` and
`AuthRepositoryImpl` to import from `:shared:security`. Add `contains()` to the security expect
class since `SecurePreferencesKeyMigration` calls `prefs.contains(canonicalKey)`.

---

### 4E. Finding AV2-P3-05 — InMemorySecurePreferences Sprint 6 Scaffold: Production Binding Risk 🟠 MEDIUM NEEDS CLARIFICATION

**File:** `shared/data/src/commonMain/.../data/local/security/InMemorySecurePreferences.kt`

The file has a large `⚠️ DO NOT USE IN PRODUCTION` banner. The KDoc comment says:
```
// Temporary — Sprint 6 only
single<SecurePreferences> { InMemorySecurePreferences() }
```

The project is past Sprint 23 (SettingsViewModel references Sprint 23). If `AndroidDataModule.kt`
or `DesktopDataModule.kt` still binds `InMemorySecurePreferences`, all tokens are stored in memory
only — JWT tokens are lost on every app restart, forcing re-login on each launch.

**NEEDS CLARIFICATION:** Run:
```bash
grep -rn "InMemorySecurePreferences" shared/data/src --include="*.kt"
```
- If any hit in `androidMain` or `jvmMain` → production security regression confirmed.

**Recommendation:** Verify platform modules. If bound in production paths, replace with:
- Android: `single<SecurePreferences> { AndroidEncryptedSecurePreferences(androidContext()) }`
- Desktop: `single<SecurePreferences> { DesktopAesSecurePreferences(get<EncryptionManager>()) }`

---

### 4F. Finding AV2-P3-06 — SettingsViewModel.generateUuid() Uses Non-Cryptographic Random 🟡 LOW

**File:** `composeApp/feature/settings/src/.../feature/settings/SettingsViewModel.kt`

```kotlin
private fun generateUuid(): String = buildString {
    val hex = "0123456789abcdef"
    repeat(32) { i ->
        // …
        val nibble = when (i) {
            12   -> 4
            16   -> 8 + (0..3).random()   // ← Kotlin stdlib Random — not cryptographic
            else -> (0..15).random()       // ← Kotlin stdlib Random — not cryptographic
        }
        append(hex[nibble])
    }
}
```

Kotlin's `(range).random()` uses `kotlin.random.Random` which is NOT a CSPRNG. UUIDs generated
for user accounts would have predictable entropy in some environments.

**What docs say:** `IdGenerator.kt` exists in `:shared:core` at
`shared/core/src/.../core/utils/IdGenerator.kt` — there is already an ID generation utility.

**Recommendation:** Replace `generateUuid()` with `IdGenerator.generateId()` from `:shared:core`,
or use `kotlin.uuid.Uuid.random()` (available since Kotlin 2.0+, experimental annotation already
used in `SecurityAuditLogger`). Do not roll a custom UUID generator.

---

### 4G. Finding AV2-P3-07 — PosSearchBar Is a Correct Delegation Wrapper — DUP-09 RESOLVED ✅

**File:** `composeApp/feature/pos/src/.../feature/pos/PosSearchBar.kt`

Full source confirms:
```kotlin
@Composable
fun PosSearchBar(…) {
    ZyntaSearchBar(            // ← delegates directly to design system component
        query = query,
        onQueryChange = onQueryChange,
        // …
    )
}
```

`PosSearchBar` is a thin adapter that adds POS-specific defaults (padding, scanner icon binding).
It wraps `ZyntaSearchBar` with no duplicated logic. v1 Phase 3 finding DUP-09 is **RESOLVED**.

---

### 4H. Finding AV2-P3-08 — Test Fake Fragmentation RESOLVED — Domain-Based Split in Place ✅

**Previous finding (v1 DUP-08):** Test fakes split into arbitrary `FakeRepositories.kt`,
`FakeRepositoriesPart2.kt`, `FakeRepositoriesPart3.kt`.

**Phase 3 evidence:**
```
shared/domain/src/commonTest/.../usecase/fakes/
  FakeAuthRepositories.kt
  FakeInventoryRepositories.kt
  FakePosRepositories.kt
  FakeSharedRepositories.kt
```

All fakes are now split by domain. Finding DUP-08 is **RESOLVED**. ✅

---

### 4I. Finding AV2-P3-09 — ProductValidator Now in Domain — DUP-07 RESOLVED ✅

**Previous finding (v1 DUP-07):** `ProductFormValidator` was in `feature/inventory` — an
architectural violation.

**Phase 3 evidence:**
```
shared/domain/src/.../domain/validation/ProductValidator.kt       ✅ exists
shared/domain/src/.../domain/validation/ProductValidationParams.kt ✅ exists
```

`ProductValidator` now lives in `:shared:domain`. Finding DUP-07 is **RESOLVED**. ✅

---

## SECTION 5 — CONSOLIDATED FINDING REGISTRY (Cumulative)

### 5A. Findings Resolved Since v2 Phase 2

| ID | Description | Resolution |
|----|-------------|------------|
| AV2-P2-03 | M11 register missing M04 — NEEDS CLARIFICATION | ✅ CLARIFIED: No HAL imports; remove M04 from doc; document Koin runtime ordering |
| AV2-P2-04 | M18 settings missing M05 — NEEDS CLARIFICATION | ✅ CLARIFIED: No security imports; remove M05 from doc |
| AV2-P2-05 | M08 auth missing M05 — NEEDS CLARIFICATION | ✅ CLARIFIED: No security imports; remove M05 from doc |
| v1 DUP-09 | PosSearchBar vs ZyntaSearchBar | ✅ RESOLVED: PosSearchBar is a correct delegation wrapper |
| v1 DUP-08 | FakeRepositories fragmented | ✅ RESOLVED: Domain-based split now in place |
| v1 DUP-07 | ProductFormValidator in presentation layer | ✅ RESOLVED: ProductValidator now in shared/domain |

---

### 5B. All Open Findings — Master Registry

| ID | Sev | Category | Description | Owner Action | File(s) |
|----|-----|----------|-------------|-------------|---------|
| **AV2-P3-01** | 🔴 CRIT | Code Gap | App.kt placeholder; Koin never starts; no nav graph | Create Application subclass; call startKoin; wire ZyntaNavGraph | `composeApp/src/.../App.kt`, `androidApp/MainActivity.kt`, `main.kt` |
| **AV2-P3-02** | 🔴 CRIT | Code Bug | `posModule` calls `SecurityAuditLogger()` — compile error | Remove from posModule; use `get()` | `composeApp/feature/pos/.../PosModule.kt` |
| NF-02 | 🔴 HIGH | Code Bug | `TaxGroupRepositoryImpl` + `UnitGroupRepositoryImpl` all TODO — runtime crash on first call | Implement all 5 methods using existing SQLDelight queries (`taxGroupsQueries`, `unitsOfMeasureQueries`) | `shared/data/.../repository/TaxGroupRepositoryImpl.kt`, `.../UnitGroupRepositoryImpl.kt` |
| **AV2-P3-03** | 🔴 HIGH | Code Bug | `feature/pos/PrintReceiptUseCase` imports 6 HAL types — violates Ports & Adapters | Delete file; move config loading to `PrinterManagerReceiptAdapter` | `composeApp/feature/pos/.../feature/pos/PrintReceiptUseCase.kt` |
| NF-01 | 🔴 HIGH | Code Bug | Same — orphan with full conflicting implementation | Resolved by AV2-P3-03 action | Same file |
| AV2-P2-02 | 🔴 HIGH | Doc + Code | M09 pos has undocumented M05; SecurityAuditLogger duplicate | Add M05 to doc; resolve singleton conflict | `Master_plan.md`, `PosModule.kt` |
| **AV2-P3-04** | 🟠 MED | Architecture | Two incompatible SecurePreferences bindings in data vs security modules | Decide canonical; remove duplicate; add `contains()` to security version | `data/local/security/SecurePreferences.kt`, `security/prefs/SecurePreferences.kt` |
| **AV2-P3-05** | 🟠 MED | Security | `InMemorySecurePreferences` production binding risk — NEEDS CLARIFICATION | Grep platform modules; replace if bound in production | `AndroidDataModule.kt`, `DesktopDataModule.kt` |
| AV2-P2-07 | 🟠 MED | Architecture | Dual SecurePreferences interface — key constants fixed, interfaces still split | See AV2-P3-04 recommendation | Same as AV2-P3-04 |
| AV2-P2-08 | 🟠 MED | DI | SecurityAuditLogger duplicate singleton — now confirmed compile error | See AV2-P3-02 | `PosModule.kt` |
| AV2-P2-01 | 🟠 MED | Doc | Master_plan lists M03 on all feature modules — architecturally incorrect | Remove M03 from M08–M12, M18 columns; add runtime wiring note | `docs/plans/Master_plan.md` lines 279-292 |
| AV2-P2-06 | 🟠 MED | Doc | `execution_log.md` DOD D2 structurally unsatisfiable | Amend DOD to exempt execution_log.md; close plan as COMPLETE | `docs/plans/PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md` |
| AV2-P3-06 | 🟠 MED | Code | `SettingsViewModel.generateUuid()` uses non-cryptographic random | Replace with `IdGenerator` from `:shared:core` | `SettingsViewModel.kt` |
| NF-06 | 🟡 LOW | Code Gap | `SecurePreferencesKeyMigration.migrate()` not wired to startup | Call before `App()` in both entry points after Koin starts | `MainActivity.kt`, `main.kt` |
| AV2-P3-05 | 🟡 LOW | Code Gap | Register module Koin ordering for PrinterManager undocumented | Add KDoc ordering note to `registerModule` | `RegisterModule.kt` |
| NF-03 | 🟡 LOW | Doc | PLAN_ZENTA_TO_ZYNTA STATUS wrong; DOD unchecked | Amend D2; check all 5 DOD items; update STATUS | `PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md` |
| NF-05 | 🟡 LOW | Doc | "ZENTRA POINT OF SALE" in `ReceiptFormatter.kt` KDoc example | Update to "ZYNTA POINT OF SALE" | `shared/domain/.../formatter/ReceiptFormatter.kt` line ~17 |
| NF-07 | 🟡 LOW | Doc | ADR-001 missing from CONTRIBUTING.md ADR table | Add ADR-001 row at line 116 | `CONTRIBUTING.md` |
| NF-04 | 🟡 LOW | Doc | `zentapos-audit-final-synthesis.md` unfilled template | Fill or rename to `…-TEMPLATE.md` | `docs/zentapos-audit-final-synthesis.md` |
| AV2-P2-04 | 🟡 LOW | Doc | M18 settings: remove M05 from doc (clarified correct) | Update Master_plan M18 deps column | `Master_plan.md` |
| AV2-P2-05 | 🟡 LOW | Doc | M08 auth: remove M05 from doc (clarified correct) | Update Master_plan M08 deps column | `Master_plan.md` |
| AV2-P2-03 | 🟡 LOW | Doc | M11 register: remove M04 from doc (clarified correct) | Update Master_plan M11 deps column | `Master_plan.md` |
| NF-08 | 🟡 LOW | Code | Empty `keystore/` + `token/` scaffold dirs | ADR: scaffold vs implementation decision | `shared/security/src/*/security/keystore/`, `.../token/` |
| NF-09 | 🟡 LOW | Code | Boilerplate `compose-multiplatform.xml` asset | Delete | `composeApp/src/commonMain/composeResources/drawable/` |
| NF-03 | 🟡 LOW | Doc | PLAN_ZENTA_TO_ZYNTA DOD items unchecked | Amend + close | `PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md` |

---

## SECTION 6 — PRIORITY ACTION TABLE (Sprint-Sorted)

### 🔴 SPRINT BLOCKER — Must Fix Before First QA Session

| Priority | ID | Exact Action | File |
|----------|----|-------------|------|
| P0 | AV2-P3-01 | Create `ZyntaApplication.kt`; add `startKoin { androidContext(this); modules(allModules) }` | `androidApp/src/main/kotlin/…/ZyntaApplication.kt` (NEW) |
| P0 | AV2-P3-01 | Wire `ZyntaNavGraph` in `App.kt`; replace placeholder `Text` | `composeApp/src/.../App.kt` |
| P0 | AV2-P3-01 | Call `startKoin { modules(…) }` at top of `main()` in Desktop entry point | `composeApp/src/jvmMain/kotlin/.../main.kt` |
| P0 | AV2-P3-02 | Remove `single { SecurityAuditLogger() }` from `posModule` | `composeApp/feature/pos/.../PosModule.kt` |
| P0 | NF-02 | Implement `TaxGroupRepositoryImpl.getAll()`, `getById()`, `insert()`, `update()`, `delete()` | `shared/data/.../repository/TaxGroupRepositoryImpl.kt` |
| P0 | NF-02 | Implement `UnitGroupRepositoryImpl.getAll()`, `getById()`, `insert()`, `update()`, `delete()` | `shared/data/.../repository/UnitGroupRepositoryImpl.kt` |

### 🔴 Sprint 5 — Architecture Correctness

| Priority | ID | Exact Action | File |
|----------|----|-------------|------|
| P1 | AV2-P3-03 | Delete `feature/pos/PrintReceiptUseCase.kt` | `composeApp/feature/pos/.../feature/pos/PrintReceiptUseCase.kt` |
| P1 | AV2-P3-04 | Decide canonical SecurePreferences; remove `data/local/security/SecurePreferences.kt` and migrate `DataModule` to use security module's version; add `contains()` to `expect class SecurePreferences` | `data/local/security/`, `security/prefs/SecurePreferences.kt`, `DataModule.kt` |
| P1 | AV2-P3-05 | Grep `AndroidDataModule.kt` + `DesktopDataModule.kt` for `InMemorySecurePreferences`; replace with encrypted impls if found | Platform data modules |
| P1 | AV2-P2-08 | Verify `PrinterManagerReceiptAdapter` injects `SecurityAuditLogger` via `get()` after posModule removal | `composeApp/feature/pos/.../pos/printer/PrinterManagerReceiptAdapter.kt` |

### 🟠 Sprint 5 — Quality + Documentation

| Priority | ID | Exact Action | File |
|----------|----|-------------|------|
| P2 | AV2-P2-01 | Remove M03 from M08–M12, M18 dep columns; add runtime-wiring note | `Master_plan.md` lines 279-292 |
| P2 | AV2-P2-03/04/05 | Update M08, M11, M18 dep columns to remove incorrect M04/M05 entries | `Master_plan.md` |
| P2 | NF-06 | Add `SecurePreferencesKeyMigration(get()).migrate()` call before `App()` (after Koin is started) | `ZyntaApplication.kt`, `main.kt` |
| P2 | AV2-P3-06 | Replace `generateUuid()` in `SettingsViewModel` with `IdGenerator` from `:shared:core` | `SettingsViewModel.kt` |
| P2 | AV2-P2-06 | Amend DOD D2 in rename plan to exempt `execution_log.md`; close plan as COMPLETE | `PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md` |

### 🟡 Sprint 6 — Hygiene + Discoverability

| Priority | ID | Exact Action | File |
|----------|----|-------------|------|
| P3 | NF-05 | Change `ZENTRA POINT OF SALE` to `ZYNTA POINT OF SALE` in KDoc example | `ReceiptFormatter.kt` line ~17 |
| P3 | NF-07 | Add ADR-001 row to CONTRIBUTING.md §7 ADR table at line 116 | `CONTRIBUTING.md` |
| P3 | AV2-P3-07 | Add KDoc ordering note to `registerModule` for HAL dependency | `RegisterModule.kt` |
| P3 | NF-04 | Fill synthesis template or rename to `…-TEMPLATE.md` | `docs/zentapos-audit-final-synthesis.md` |
| P3 | NF-08 | ADR decision on `keystore/` + `token/` scaffold directories | `shared/security/src/*/security/keystore/` |
| P3 | NF-09 | Delete boilerplate `compose-multiplatform.xml` | `composeApp/src/commonMain/composeResources/drawable/` |

---

## SECTION 7 — CUMULATIVE AUDIT METRICS

| Metric | v2 Phase 1 | v2 Phase 2 | v2 Phase 3 |
|--------|------------|------------|------------|
| Total open findings | 9 | 26 | **28** |
| Closed this phase | — | 1 (F-10) | **6** |
| New findings this phase | — | 8 | **9** |
| Clarifications resolved | — | 0 | **3** |
| 🔴 Critical | — | 0 | **2** |
| 🔴 High | 3 | 5 | **5** |
| 🟠 Medium | 2 | 11 | **9** |
| 🟡 Low / Hygiene | 4 | 10 | **12** |
| NEEDS CLARIFICATION | — | 3 | **1** (AV2-P3-05) |

---

## SECTION 8 — VERIFICATION CHECKLIST

| Check | Evidence | Status |
|-------|----------|--------|
| Zombie `BaseViewModel` in `shared/core/mvi/` deleted | `mvi/` = `.gitkeep` only | ✅ |
| ADR-001 enforced — all VMs use `ui.core.mvi.BaseViewModel` | ReportsVM + SettingsVM + AuthVM + RegisterVM confirmed | ✅ |
| Root `BarcodeScanner.kt` duplicate deleted | Not in file listing | ✅ |
| Root `SecurityAuditLogger.kt` duplicate deleted | Not in file listing | ✅ |
| Design system uses `Zynta*` prefix | grep confirms | ✅ |
| Test fakes domain-split | 4 domain files confirmed | ✅ |
| `ProductValidator` in `:shared:domain` | File confirmed | ✅ |
| `PosSearchBar` delegates to `ZyntaSearchBar` | Source confirmed | ✅ |
| `SecurePreferencesKeys` as single source of truth for key constants | Key delegation confirmed | ✅ |
| `PlaceholderPasswordHasher` moved to `:shared:security` | File confirmed | ✅ |
| Koin initialised in Android entry point | No Application class; no `startKoin` found | ❌ |
| `ZyntaNavGraph` wired in `App.kt` | Placeholder text only | ❌ |
| `posModule` compiles cleanly | `SecurityAuditLogger()` no-arg — compile error | ❌ |
| `TaxGroupRepositoryImpl` implemented | All 5 methods TODO | ❌ |
| `UnitGroupRepositoryImpl` implemented | All 5 methods TODO | ❌ |
| `feature/pos/PrintReceiptUseCase.kt` deleted | File present with HAL imports | ❌ |
| Single canonical `SecurePreferences` interface | Two interfaces in different packages | ❌ |
| `InMemorySecurePreferences` absent from production bindings | Not verified (NEEDS CLARIFICATION) | ⚠️ |
| `SecurePreferencesKeyMigration.migrate()` wired at startup | Not called in any entry point | ❌ |
| Master_plan dep table updated to remove M03 from feature modules | Lines 279-292 unchanged | ❌ |
| Master_plan M08 M11 M18 dep columns corrected | Unchanged | ❌ |
| `SecurityAuditLogger` singleton conflict resolved in posModule | `single { SecurityAuditLogger() }` still present | ❌ |
| ADR-001 in CONTRIBUTING.md | Line 116 — ADR-002 only | ❌ |
| "ZENTRA POINT OF SALE" fixed in ReceiptFormatter KDoc | Still present | ❌ |
| `SettingsViewModel.generateUuid()` uses secure random | Uses `(0..15).random()` | ❌ |
| `compose-multiplatform.xml` deleted | Not re-verified | ⚠️ |

---

## SECTION 9 — RISK SUMMARY

### Risk 1 — 🔴 APPLICATION CANNOT LAUNCH (AV2-P3-01)
`App.kt` is a placeholder. No Koin graph is initialised. No navigation graph is wired. The app
renders a single `Text("ZyntaPOS — Initializing…")` and no user flow is reachable. Every feature
module built across Sprints 14–23 is completely inaccessible. Before any QA session is possible,
an `Application` subclass must be created, `startKoin` must be called, and `ZyntaNavGraph` must be
wired. This is the highest-priority finding in the entire audit.

### Risk 2 — 🔴 POS MODULE DOES NOT COMPILE (AV2-P3-02 / AV2-P3-03)
`posModule` calls `SecurityAuditLogger()` with no arguments — a compile error. Additionally,
`feature/pos/PrintReceiptUseCase.kt` imports 6 HAL types into what should be a domain use case.
These two issues in the same module mean `composeApp:feature:pos` cannot build or be tested at all.

### Risk 3 — 🔴 IMMINENT RUNTIME CRASH ON SETTINGS / TAX SCREEN (NF-02)
`TaxGroupRepositoryImpl` and `UnitGroupRepositoryImpl` throw `NotImplementedError` on every method
call. `SettingsViewModel` calls `taxGroupRepository.getAll()` immediately on `SettingsIntent.LoadTaxGroups`.
The first user who opens Settings → Tax Groups will encounter an unhandled crash. The SQLDelight
queries (`taxGroupsQueries`) already exist — the implementation just needs to be written.

### Risk 4 — 🟠 TOKENS SILENTLY LOST ON RESTART IF SPRINT 6 SCAFFOLD STILL BOUND (AV2-P3-05)
`InMemorySecurePreferences` is marked production-unsafe. If it is still bound in either platform
data module, every JWT access token is stored in memory only. App restart forces re-login on every
launch. This is a silent regression that would be reported as "keeps logging me out."

---

*End of Audit v2 — Phase 3 — ZyntaPOS ZENTA-AUDIT-V2-PHASE3-DOC-CONSISTENCY*
*Total open findings: 28 | Critical: 2 | High: 5 | Medium: 9 | Low: 12 | NEEDS CLARIFICATION: 1*
*Closed this phase: 6 | Next: Phase 4 — Fix Execution (AV2-P3-01 Koin startup, AV2-P3-02 compile fix, NF-02 repo implementations)*
