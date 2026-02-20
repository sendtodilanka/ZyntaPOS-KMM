# ZyntaPOS — Kotlin 2.3.0 Compatibility Verification
> **Doc ID:** ZENTA-COMPAT-VERIFY-v1.0
> **Date:** 2026-02-20
> **Scope:** Sprint 4–24 API patterns vs pinned dependency versions
> **Verdict:** Sprint 4 is ✅ CLEAR. Two pre-Sprint-12 action items identified.
> **Reference:** `docs/plans/PLAN_PHASE1.md`, `gradle/libs.versions.toml`

---

## Pinned Version Matrix (Actual)

| Dependency | Pinned Version | Plan Estimated | Delta |
|------------|---------------|----------------|-------|
| Kotlin | **2.3.0** | 2.1.0 | +2 minor |
| Compose Multiplatform | **1.10.0** | 1.7.3 | +3 minor |
| kotlinx-coroutines | **1.10.2** | (unspecified) | — |
| kotlinx-serialization | **1.8.0** | (unspecified) | — |
| kotlinx-datetime | **0.6.1** | 0.6+ | ✅ |
| Koin | **4.0.4** | 4.0.0 | +patch |
| Ktor | **3.0.3** | 3.0.3 | ✅ exact |
| SQLDelight | **2.0.2** | 2.0.2 | ✅ exact |
| Mockative | **3.0.1** | 3.0.1 | ✅ exact |
| AGP | **8.13.2** | (unspecified) | — |
| lifecycle-viewmodel (KMP) | **2.9.6** | (unspecified) | — |
| compose-adaptive | **1.1.0-alpha04** | (unspecified) | ⚠️ alpha |
| androidx-security-crypto | **1.1.0-alpha06** | (unspecified) | ⚠️ alpha |

---

## Sprint-by-Sprint Compatibility Assessment

---

### SPRINT 4 — `:shared:domain` Repository Interfaces + Use Cases
**Status: ✅ FULLY SAFE — No action required**

| API Pattern | Version Used | Compatibility | Notes |
|------------|-------------|---------------|-------|
| `Flow<T>`, `StateFlow<T>`, `SharedFlow<T>` | coroutines 1.10.2 | ✅ Stable | No changes affecting usage |
| `suspend fun` in interfaces | Kotlin 2.3.0 | ✅ Stable | Core language feature |
| Custom `Result<T>` sealed class | (project-defined) | ✅ Clean | Avoids `kotlin.Result` wrapper issues |
| `kotlinx.datetime.LocalDateTime` / `Instant` | datetime 0.6.1 | ✅ Stable | KMP-compatible since 0.4 |
| `@Serializable` on domain DTOs | serialization 1.8.0 | ✅ Stable | Plugin applied at root level |
| `Map<String, String>` in `ProductVariant.attributes` | Kotlin 2.3.0 | ✅ Stable | Serializable with 1.8.0 |
| `Set<Permission>` in RBAC map | Kotlin 2.3.0 | ✅ Stable | Immutable collections supported |
| Mockative 3.0.1 KSP processor | KSP 2.x (bundled with K 2.3.0) | ✅ Verified | Build passes (NS-7 confirmed) |

**Verdict: Begin Sprint 4 immediately. Zero compatibility blockers.**

---

### SPRINT 2 — `IdGenerator.kt` — `@ExperimentalUuidApi`
**Status: ✅ CORRECT AS-IS — Keep `@OptIn` annotation**

`kotlin.uuid.Uuid` was introduced as `@ExperimentalUuidApi` in Kotlin 2.0.0 and remains
experimental in Kotlin 2.3.0. The current `IdGenerator.kt` correctly uses
`@OptIn(ExperimentalUuidApi::class)` on each call site. This is the required pattern — do not
remove the annotation.

When JetBrains stabilises `kotlin.uuid.Uuid` (planned for a future release), the `@OptIn`
annotations will produce compiler warnings and can be removed at that time.

```kotlin
// CORRECT — keep as-is in Kotlin 2.3.0
@OptIn(ExperimentalUuidApi::class)
fun newId(): String = Uuid.random().toString()
```

---

### SPRINT 2 — `BaseViewModel` — `Dispatchers.Main.immediate`
**Status: ✅ SAFE for production | ⚠️ Requires test setup pattern in Sprint 12–13**

`Dispatchers.Main.immediate` is valid in commonMain provided the platform-specific
Main dispatcher is initialised:
- **Android:** `kotlinx-coroutines-android` provides Main dispatcher automatically ✅
- **Desktop JVM:** `kotlinx-coroutines-swing` provides `SwingDispatcher` as Main ✅

**Test-only concern (Sprint 12–13):** When unit-testing `*ViewModel` classes in `commonTest`
(which runs on the JVM without Android/Swing), `Dispatchers.Main` will throw
`"Module with the Main dispatcher had failed to initialize"`.

**Required test setup pattern for every ViewModel test** (Sprint 12 onward):

```kotlin
// in every ViewModel test class
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

class AuthViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }
}
```

**Action:** Add this note to Sprint 12 (step 8.1.12) in `PLAN_PHASE1.md` execution guidance.
This is a documentation clarification — the code itself is correct.

---

### PRE-SPRINT 12 — `BaseViewModel` extends `AutoCloseable` — MUST FIX BEFORE ViewModels
**Status: ⚠️ ACTION REQUIRED before Sprint 12 execution**

**Issue:** `BaseViewModel<S,I,E>` currently extends `AutoCloseable`. For Koin 4.0.4's
`viewModelOf {}` to bind the ViewModel to the Compose lifecycle (automatic `close()` on
screen departure), the class must extend `org.jetbrains.androidx.lifecycle.ViewModel`
from the KMP lifecycle artifact (`lifecycle-viewmodel 2.9.6`).

**Current code (Sprint 2 implementation):**
```kotlin
abstract class BaseViewModel<S : UiState, I : UiIntent, E : UiEffect>(
    initialState: S,
) : AutoCloseable {
    protected val viewModelScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    ...
    override fun close() { viewModelScope.cancel() }
}
```

**Required fix (before Sprint 12):**
```kotlin
import org.jetbrains.androidx.lifecycle.ViewModel
import org.jetbrains.androidx.lifecycle.viewModelScope  // lifecycle-aware scope

abstract class BaseViewModel<S : UiState, I : UiIntent, E : UiEffect>(
    initialState: S,
) : ViewModel() {
    // viewModelScope is provided by ViewModel() — no manual CoroutineScope needed
    // It uses Dispatchers.Main.immediate internally and cancels automatically
    ...
    override fun onCleared() {
        super.onCleared()
        // custom cleanup here
    }
}
```

**Why this matters:**
- Without this, `viewModelOf(::AuthViewModel)` compiles but does NOT get lifecycle-scoped
  coroutine cancellation — memory leaks will occur on screen navigation.
- The `lifecycle-viewmodel 2.9.6` artifact is already in `libs.versions.toml` and
  `libs.androidx-lifecycle-viewmodel` is already declared. No new dependency needed.
- The `viewModelScope` from `ViewModel()` is already backed by
  `Dispatchers.Main.immediate + SupervisorJob` — identical semantics to current manual scope.

**Impact:** Sprint 4 (pure domain) is unaffected. This fix must be applied at the
**start of Sprint 12** before `AuthViewModel` is wired.

**Recommended:** Create a focused micro-fix step in `execution_log.md` before Sprint 12 begins:
```
PRE-12.FIX — Update BaseViewModel: extend ViewModel() from lifecycle-viewmodel,
             remove manual CoroutineScope, use provided viewModelScope
```

---

### SPRINT 6 — Ktor `HttpRequestRetry` Plugin in Ktor 3.0.3
**Status: ✅ COMPATIBLE — API confirmed present**

The plan calls for "Retry plugin (3 attempts, exponential backoff)". Ktor 3.x renamed the
plugin from `HttpPlainText` to `HttpRequestRetry`. Confirmed present in cached jar.

**Correct Ktor 3.x usage pattern** (aligns with PLAN_PHASE1.md §4, step 3.4.1):
```kotlin
install(HttpRequestRetry) {
    retryOnServerErrors(maxRetries = 3)
    exponentialDelay()  // 1s → 2s → 4s
}
```

⚠️ **Plan wording update needed:** The plan text says "Retry plugin (3 attempts,
exponential backoff)" without specifying the class name. Ensure Sprint 6 implementation
uses `HttpRequestRetry` (not `Retry` or `HttpRetry`).

---

### SPRINT 8 — `EncryptedSharedPreferences` from alpha artifact
**Status: ⚠️ ALPHA RISK — Monitor at Sprint 8 start**

`androidx-security-crypto:1.1.0-alpha06` is still in alpha as of 2026-02-20.
Known risk: alpha APIs may change before stabilisation.

**Recommended mitigation at Sprint 8:**
Option A — Upgrade to `1.1.0-rc01` if released by Sprint 8 start.
Option B — Replace `EncryptedSharedPreferences` with `DataStore<Preferences>` +
           manual AES-256-GCM encryption via `EncryptionManager` (plan step 5.1.1).
           This is architecturally cleaner and avoids the alpha dependency entirely.

**Action:** Evaluate at Sprint 8 start. No change needed now.

---

### SPRINT 9–10 — `compose-adaptive:1.1.0-alpha04`
**Status: ⚠️ ALPHA RISK — Monitor at Sprint 9 start**

`org.jetbrains.compose.material3.adaptive:adaptive:1.1.0-alpha04` is alpha.
APIs used by the plan: `WindowSizeClass`, `NavigationSuiteScaffold` layout,
`calculateWindowSizeClass()`.

These APIs are well-established and unlikely to break, but the alpha status means
Jetpack/JetBrains may rename or restructure them before 1.1.0 stable.

**Action:** At Sprint 9 start, check if a stable or beta version is available and upgrade
if possible.

---

### SPRINT 11 — `@Serializable` sealed class `ZentaRoute`
**Status: ✅ COMPATIBLE — Confirmed pattern for Compose MP 1.10.0**

Type-safe navigation with `@Serializable` sealed class routes works with Compose MP 1.10.0.
The bundled `navigation-compose` version in Compose MP 1.10.0 supports:

```kotlin
@Serializable sealed class ZentaRoute
@Serializable object Login : ZentaRoute()
@Serializable data class ProductDetail(val productId: String?) : ZentaRoute()
```

Required: `kotlinx.serialization 1.8.0` — present in catalog ✅
Required: `kotlinSerialization` plugin on `:composeApp:navigation` module ✅

---

### SPRINT 13 — `advanceTimeBy` in coroutines-test 1.10.2
**Status: ✅ BOTH OVERLOADS AVAILABLE**

In coroutines-test 1.10.2:
- `TestScope.advanceTimeBy(delayTimeMillis: Long)` — available ✅
- `TestScope.advanceTimeBy(delayTime: Duration)` — available ✅

The plan's `SessionManager` test using `advanceTimeBy` works with both. Prefer the
`Duration` form for clarity:

```kotlin
// Preferred in new tests:
advanceTimeBy(30.seconds)

// Also valid:
advanceTimeBy(30_000L)
```

---

## Summary Table

| Sprint | Risk Level | Finding | Action Required | Timing |
|--------|-----------|---------|-----------------|--------|
| Sprint 4 | ✅ CLEAR | No issues | None | Start now |
| Sprint 2 existing | ✅ OK | `@OptIn(ExperimentalUuidApi)` is correct | None | — |
| Pre-Sprint 12 | ⚠️ MUST FIX | `BaseViewModel` must extend KMP `ViewModel` | Fix before S12 | Sprint 11 end |
| Sprint 6 | ✅ OK | Ktor `HttpRequestRetry` API confirmed | Use correct class name | Sprint 6 |
| Sprint 8 | ⚠️ MONITOR | `security-crypto:1.1.0-alpha06` is alpha | Evaluate at S8 start | Sprint 8 |
| Sprint 9-10 | ⚠️ MONITOR | `compose-adaptive:1.1.0-alpha04` is alpha | Evaluate at S9 start | Sprint 9 |
| Sprint 11 | ✅ OK | `@Serializable ZentaRoute` confirmed | None | — |
| Sprint 13 | ✅ OK | `advanceTimeBy` both overloads available | Use Duration form | Sprint 13 |
| Sprint 12+ tests | ✅ OK with pattern | `Dispatchers.setMain()` required in test setUp | Add to test template | Sprint 12 |

---

## Immediate Actions Before Sprint 4

**None.** Sprint 4 has zero compatibility blockers.

## Deferred Actions (logged for future sprints)

| ID | When | Action |
|----|------|--------|
| COMPAT-FIX-1 | Sprint 11 end | Update `BaseViewModel` to extend KMP `ViewModel` from `lifecycle-viewmodel 2.9.6` |
| COMPAT-FIX-2 | Sprint 8 start | Evaluate `security-crypto` alpha → upgrade or replace with DataStore |
| COMPAT-FIX-3 | Sprint 9 start | Check `compose-adaptive` for stable/beta release |
| COMPAT-FIX-4 | Sprint 12 start | Add `Dispatchers.setMain(UnconfinedTestDispatcher())` to ViewModel test template |

---

*End of Compatibility Verification — ZyntaPOS v1.0*
*Doc ID: ZENTA-COMPAT-VERIFY-v1.0 | 2026-02-20*
