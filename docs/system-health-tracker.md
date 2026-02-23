# System Health Tracker

## Overview

The System Health Tracker provides real-time diagnostics for ZyntaPOS, monitoring memory usage, disk storage, database health, and runtime metrics. It is accessible from **Settings > Support > System Health**.

## Architecture

### Core Interface (`shared/core`)

```
shared/core/src/commonMain/.../health/SystemHealthTracker.kt
```

- **`HealthSnapshot`** — Data class containing all health metrics (memory, disk, database, runtime, network, overall status)
- **`SystemHealthTracker`** — Interface with `snapshot: StateFlow<HealthSnapshot>`, `refresh()`, `startAutoRefresh()`, `stopAutoRefresh()`
- **`DatabaseStatus`** — Enum: `HEALTHY`, `DEGRADED`, `ERROR`, `UNKNOWN`
- **`OverallStatus`** — Enum: `HEALTHY`, `WARNING`, `CRITICAL`, `UNKNOWN`

### Platform Implementations

#### Android (`shared/core/src/androidMain/.../health/SystemHealthTracker.android.kt`)

| Metric | Source |
|--------|--------|
| Heap Memory | `Runtime.getRuntime()` (maxMemory, totalMemory, freeMemory) |
| Disk Storage | `StatFs(Environment.getDataDirectory())` |
| Database Size | Walks `/data/data/<pkg>/databases/` for `.db` files |
| Uptime | `SystemClock.elapsedRealtime()` |
| CPU Cores | `Runtime.availableProcessors()` |
| Platform | `Build.VERSION.RELEASE` + `Build.VERSION.SDK_INT` |

#### Desktop/JVM (`shared/core/src/jvmMain/.../health/SystemHealthTracker.jvm.kt`)

| Metric | Source |
|--------|--------|
| Heap Memory | `Runtime.getRuntime()` (maxMemory, totalMemory, freeMemory) |
| Disk Storage | `File.totalSpace` / `File.usableSpace` on `~/.zyntapos/` |
| Database Size | Walks `~/.zyntapos/` for `.db` files |
| JVM Uptime | `ManagementFactory.getRuntimeMXBean().uptime` |
| CPU Cores | `Runtime.availableProcessors()` |
| Platform | `os.name`, `os.arch`, `java.version` system properties |

### DI Registration

Registered as a singleton in `CoreModule.kt`:

```kotlin
single<SystemHealthTracker> { createSystemHealthTracker() }
```

## Health Metrics

### Overall Status Thresholds

| Status | Condition |
|--------|-----------|
| **HEALTHY** | Heap < 75%, Disk < 85%, DB status healthy |
| **WARNING** | Heap 75-90% OR Disk 85-95% OR DB degraded |
| **CRITICAL** | Heap > 90% OR Disk > 95% OR DB error |

### Auto-Refresh

- Starts when the System Health screen is visible (30-second interval)
- Stops automatically via `DisposableEffect` when the screen is disposed
- Manual refresh available via the toolbar refresh button

## UI Screen

```
composeApp/feature/settings/src/commonMain/.../screen/SystemHealthScreen.kt
```

The screen displays:

1. **Overall Status Banner** — Color-coded card (green/amber/red) with status icon
2. **Memory Card** — Progress bar + heap used/max values
3. **Disk Storage Card** — Progress bar + free/total space
4. **Database Card** — Status indicator + database file size
5. **Runtime Card** — Platform, uptime, CPU cores, network status

## Navigation

- **Route:** `ZyntaRoute.SystemHealthSettings`
- **Settings Home:** Listed under "Support" group with `HealthAndSafety` icon
- **Back navigation:** Returns to Settings Home

---

# Version Naming System

## Overview

ZyntaPOS uses a centralized version configuration with **Semantic Versioning** (semver.org).

## Single Source of Truth

All version information is defined in `version.properties` at the project root:

```properties
VERSION_MAJOR=1
VERSION_MINOR=0
VERSION_PATCH=0
VERSION_LABEL=          # empty for stable, e.g. alpha.1, beta.2, rc.1
VERSION_BUILD=1         # auto-incremented per release
```

## Version String Format

```
MAJOR.MINOR.PATCH[-LABEL] (build BUILD_NUMBER)
```

Examples:
- `1.0.0 (build 1)` — First stable release
- `1.1.0-beta.1 (build 15)` — Beta of next minor release
- `2.0.0-rc.1 (build 42)` — Release candidate for major version

## Version Code Computation

For Android `versionCode` (must be a monotonically increasing integer):

```
versionCode = MAJOR * 10_000 + MINOR * 100 + PATCH
```

Examples:
- `1.0.0` → `10000`
- `1.2.3` → `10203`
- `2.0.0` → `20000`

## How It Flows

```
version.properties
    │
    ▼
build.gradle.kts (root)          ← loads props, sets extra properties
    │
    ├── androidApp/build.gradle.kts
    │       ├── versionCode = rootProject.extra["appVersionCode"]
    │       ├── versionName = rootProject.extra["appVersionName"]
    │       └── BuildConfig fields: APP_VERSION_NAME, APP_VERSION_CODE,
    │           APP_BUILD_NUMBER, BUILD_DATE
    │
    └── composeApp/build.gradle.kts
            ├── packageVersion = appVersion (native distributions)
            └── JVM args: -Dapp.version, -Dapp.build.number, -Dapp.build.date
```

### Android Runtime Access

```kotlin
// In ZyntaApplication.onCreate():
(appInfo as AndroidAppInfoProvider).init(
    version     = BuildConfig.APP_VERSION_NAME,
    buildNumber = BuildConfig.APP_BUILD_NUMBER,
    buildDate   = BuildConfig.BUILD_DATE,
    debug       = BuildConfig.DEBUG,
)
```

### Desktop Runtime Access

JVM system properties are set in `composeApp/build.gradle.kts`:
```kotlin
jvmArgs += listOf(
    "-Dapp.version=$appVersion",
    "-Dapp.build.number=$appBuild",
    "-Dapp.build.date=${java.time.LocalDate.now()}",
)
```

Read by `JvmAppInfoProvider`:
```kotlin
System.getProperty("app.version")
System.getProperty("app.build.number")
System.getProperty("app.build.date")
```

## AppInfoProvider Interface

```kotlin
interface AppInfoProvider {
    val appVersion: String          // "1.0.0"
    val buildNumber: Int            // 1
    val buildDate: String           // "2026-02-23"
    val platformName: String        // "Android 15 (API 35)" or "Linux amd64 (Java 17)"
    val isDebug: Boolean            // true/false
    val fullVersionString: String   // "1.0.0 (build 1)"
}
```

## Release Workflow

1. **Bump version** in `version.properties`:
   - Feature release: increment `VERSION_MINOR`, reset `PATCH` to 0
   - Bug fix: increment `VERSION_PATCH`
   - Breaking change: increment `VERSION_MAJOR`, reset `MINOR` and `PATCH`
   - Pre-release: set `VERSION_LABEL` (e.g., `beta.1`)
   - Always increment `VERSION_BUILD`

2. **Build** — Gradle reads version.properties automatically

3. **Tag** — `git tag v1.0.0` (matches VERSION_NAME)

## Files Modified

| File | Change |
|------|--------|
| `version.properties` | **NEW** — Single source of truth |
| `build.gradle.kts` (root) | Loads version.properties, exports extra properties |
| `androidApp/build.gradle.kts` | Reads from extra properties, generates BuildConfig fields |
| `composeApp/build.gradle.kts` | Reads from extra properties for desktop packaging + JVM args |
| `shared/core/.../AppInfoProvider.kt` | Added `buildNumber`, `fullVersionString` |
| `shared/core/.../AppInfoProvider.android.kt` | Added `buildNumber` field + init param |
| `shared/core/.../AppInfoProvider.jvm.kt` | Reads `app.build.number` system property |
| `androidApp/.../ZyntaApplication.kt` | Passes BuildConfig values to AppInfoProvider |
| `composeApp/feature/settings/.../AboutScreen.kt` | Shows `fullVersionString` |
