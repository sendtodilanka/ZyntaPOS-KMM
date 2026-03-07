# ZyntaPOS — Canonical Namespace Standardisation

> **Document ID:** ZENTA-PLAN-NAMESPACE-FIX-v1.0
> **Status:** APPROVED FOR EXECUTION
> **Author:** Senior KMP Architect & Lead Engineer
> **Created:** 2026-02-20
> **Reference Log:** `docs/ai_workflows/execution_log.md`

---

## Executive Summary

All source files and build configurations currently use the incorrect root package
`com.zynta.pos`. Based on company identity (Zynta Solutions Pvt Ltd /
zyntapos.com) and product brand (ZyntaPOS / zyntapos.com), the canonical
package must be `com.zyntasolutions.zyntapos`. This plan renames every affected
file and declaration before any Sprint 2+ implementation begins.

---

## Canonical Namespace Decision

| Field               | Value                           |
|---------------------|---------------------------------|
| **OLD package**     | `com.zynta.pos`                 |
| **NEW package**     | `com.zyntasolutions.zyntapos`   |
| **OLD folder path** | `com/zynta/pos/`                |
| **NEW folder path** | `com/zyntasolutions/zyntapos/`  |
| Company domain      | zyntapos.com              |
| App name            | zyntapos                        |

---

## Scope — Complete Change Inventory

### Group A — Build Files (namespace / applicationId / packageName)

22 `build.gradle.kts` files require string replacement only (no folder moves).

| # | File | Changes Required |
|---|------|-----------------|
| A-01 | `androidApp/build.gradle.kts` | `namespace`, `applicationId` |
| A-02 | `composeApp/build.gradle.kts` | `namespace`, `mainClass`, `packageName` (desktop) |
| A-03 | `shared/core/build.gradle.kts` | `namespace` |
| A-04 | `shared/domain/build.gradle.kts` | `namespace` |
| A-05 | `shared/data/build.gradle.kts` | `namespace`, SQLDelight `packageName.set(...)` |
| A-06 | `shared/hal/build.gradle.kts` | `namespace` |
| A-07 | `shared/security/build.gradle.kts` | `namespace` |
| A-08 | `composeApp/designsystem/build.gradle.kts` | `namespace` |
| A-09 | `composeApp/navigation/build.gradle.kts` | `namespace` |
| A-10 | `composeApp/feature/admin/build.gradle.kts` | `namespace` |
| A-11 | `composeApp/feature/auth/build.gradle.kts` | `namespace` |
| A-12 | `composeApp/feature/coupons/build.gradle.kts` | `namespace` |
| A-13 | `composeApp/feature/customers/build.gradle.kts` | `namespace` |
| A-14 | `composeApp/feature/expenses/build.gradle.kts` | `namespace` |
| A-15 | `composeApp/feature/inventory/build.gradle.kts` | `namespace` |
| A-16 | `composeApp/feature/media/build.gradle.kts` | `namespace` |
| A-17 | `composeApp/feature/multistore/build.gradle.kts` | `namespace` |
| A-18 | `composeApp/feature/pos/build.gradle.kts` | `namespace` |
| A-19 | `composeApp/feature/register/build.gradle.kts` | `namespace` |
| A-20 | `composeApp/feature/reports/build.gradle.kts` | `namespace` |
| A-21 | `composeApp/feature/settings/build.gradle.kts` | `namespace` |
| A-22 | `composeApp/feature/staff/build.gradle.kts` | `namespace` |

---

### Group B — Kotlin Source Files (package declaration + physical folder rename)

Each file requires: (1) update `package` declaration, (2) move file to new folder path.

| # | Current Path | New Path |
|---|-------------|----------|
| B-01 | `androidApp/src/main/kotlin/com/zynta/pos/MainActivity.kt` | `…/com/zyntasolutions/zyntapos/MainActivity.kt` |
| B-02 | `composeApp/src/commonMain/kotlin/com/zynta/pos/App.kt` | `…/com/zyntasolutions/zyntapos/App.kt` |
| B-03 | `composeApp/src/jvmMain/kotlin/com/zynta/pos/main.kt` | `…/com/zyntasolutions/zyntapos/main.kt` |
| B-04 | `shared/core/src/commonMain/kotlin/com/zynta/pos/core/Platform.kt` | `…/com/zyntasolutions/zyntapos/core/Platform.kt` |
| B-05 | `shared/core/src/commonMain/kotlin/com/zynta/pos/core/di/CoreModule.kt` | `…/com/zyntasolutions/zyntapos/core/di/CoreModule.kt` |
| B-06 | `shared/core/src/androidMain/kotlin/com/zynta/pos/core/Platform.android.kt` | `…/com/zyntasolutions/zyntapos/core/Platform.android.kt` |
| B-07 | `shared/core/src/jvmMain/kotlin/com/zynta/pos/core/Platform.jvm.kt` | `…/com/zyntasolutions/zyntapos/core/Platform.jvm.kt` |
| B-08 | `shared/data/src/commonMain/kotlin/com/zynta/pos/data/di/DataModule.kt` | `…/com/zyntasolutions/zyntapos/data/di/DataModule.kt` |
| B-09 | `shared/domain/src/commonMain/kotlin/com/zynta/pos/domain/DomainModule.kt` | `…/com/zyntasolutions/zyntapos/domain/DomainModule.kt` |
| B-10 | `shared/hal/src/commonMain/kotlin/com/zynta/pos/hal/di/HalModule.kt` | `…/com/zyntasolutions/zyntapos/hal/di/HalModule.kt` |
| B-11 | `shared/security/src/commonMain/kotlin/com/zynta/pos/security/di/SecurityModule.kt` | `…/com/zyntasolutions/zyntapos/security/di/SecurityModule.kt` |
| B-12 | `composeApp/designsystem/src/commonMain/kotlin/com/zynta/pos/designsystem/DesignSystemModule.kt` | `…/com/zyntasolutions/zyntapos/designsystem/DesignSystemModule.kt` |
| B-13 | `composeApp/navigation/src/commonMain/kotlin/com/zynta/pos/navigation/NavigationModule.kt` | `…/com/zyntasolutions/zyntapos/navigation/NavigationModule.kt` |
| B-14 | `composeApp/feature/admin/src/commonMain/kotlin/com/zynta/pos/feature/admin/AdminModule.kt` | `…/com/zyntasolutions/zyntapos/feature/admin/AdminModule.kt` |
| B-15 | `composeApp/feature/auth/src/commonMain/kotlin/com/zynta/pos/feature/auth/AuthModule.kt` | `…/com/zyntasolutions/zyntapos/feature/auth/AuthModule.kt` |
| B-16 | `composeApp/feature/coupons/src/commonMain/kotlin/com/zynta/pos/feature/coupons/CouponsModule.kt` | `…/com/zyntasolutions/zyntapos/feature/coupons/CouponsModule.kt` |
| B-17 | `composeApp/feature/customers/src/commonMain/kotlin/com/zynta/pos/feature/customers/CustomersModule.kt` | `…/com/zyntasolutions/zyntapos/feature/customers/CustomersModule.kt` |
| B-18 | `composeApp/feature/expenses/src/commonMain/kotlin/com/zynta/pos/feature/expenses/ExpensesModule.kt` | `…/com/zyntasolutions/zyntapos/feature/expenses/ExpensesModule.kt` |
| B-19 | `composeApp/feature/inventory/src/commonMain/kotlin/com/zynta/pos/feature/inventory/InventoryModule.kt` | `…/com/zyntasolutions/zyntapos/feature/inventory/InventoryModule.kt` |
| B-20 | `composeApp/feature/media/src/commonMain/kotlin/com/zynta/pos/feature/media/MediaModule.kt` | `…/com/zyntasolutions/zyntapos/feature/media/MediaModule.kt` |
| B-21 | `composeApp/feature/multistore/src/commonMain/kotlin/com/zynta/pos/feature/multistore/MultistoreModule.kt` | `…/com/zyntasolutions/zyntapos/feature/multistore/MultistoreModule.kt` |
| B-22 | `composeApp/feature/pos/src/commonMain/kotlin/com/zynta/pos/feature/pos/PosModule.kt` | `…/com/zyntasolutions/zyntapos/feature/pos/PosModule.kt` |
| B-23 | `composeApp/feature/register/src/commonMain/kotlin/com/zynta/pos/feature/register/RegisterModule.kt` | `…/com/zyntasolutions/zyntapos/feature/register/RegisterModule.kt` |
| B-24 | `composeApp/feature/reports/src/commonMain/kotlin/com/zynta/pos/feature/reports/ReportsModule.kt` | `…/com/zyntasolutions/zyntapos/feature/reports/ReportsModule.kt` |
| B-25 | `composeApp/feature/settings/src/commonMain/kotlin/com/zynta/pos/feature/settings/SettingsModule.kt` | `…/com/zyntasolutions/zyntapos/feature/settings/SettingsModule.kt` |
| B-26 | `composeApp/feature/staff/src/commonMain/kotlin/com/zynta/pos/feature/staff/StaffModule.kt` | `…/com/zyntasolutions/zyntapos/feature/staff/StaffModule.kt` |

---

### Group C — Documentation Updates

| # | File | Change |
|---|------|--------|
| C-01 | `docs/ai_workflows/execution_log.md` | Update FIX-14.01 canonical namespace note: `com.zynta.pos` → `com.zyntasolutions.zyntapos` |
| C-02 | `docs/plans/PLAN_PHASE1.md` | Update all file path examples: `com/zentapos/` → `com/zyntasolutions/zyntapos/` |
| C-03 | `docs/plans/Master_plan.md` | Update any `com.zentapos` / `com.zynta.pos` package references in code samples |

---

## Execution Steps (Ordered)

```
Step NS-1   Read remaining 11 feature build.gradle.kts files to confirm all use
            the same namespace pattern (no surprises before editing)

Step NS-2   Update all 22 Group A build.gradle.kts files (string replacements only)

Step NS-3   Create new folder trees (com/zyntasolutions/zyntapos/…) in all
            source sets that contain .kt files

Step NS-4   Move + update package declarations for all 26 Group B .kt files

Step NS-5   Delete old empty com/zynta/pos/ folder trees

Step NS-6   Update Group C documentation files (3 files)

Step NS-7   Clean Gradle build cache and execute verification build:
            ./gradlew clean :shared:core:jvmJar :shared:domain:jvmJar
            :composeApp:jvmJar :androidApp:assembleDebug --stacktrace
            → ZERO errors = PASS

Step NS-8   Update execution_log.md with all steps marked [x] Finished
```

---

## Risk & Rollback

| Risk | Mitigation |
|------|-----------|
| Git history loses file identity on move | Use `git mv` semantics (move + commit in one step) so history is preserved |
| Gradle config cache stale after rename | `./gradlew clean` clears it; config cache auto-invalidates on build file changes |
| Android Studio still shows old paths | File → Invalidate Caches → Restart after execution |
| Missed occurrence causes compile error | Build in Step NS-7 will surface any remaining `com.zynta.pos` reference immediately |

---

## Definition of Done

- [ ] `grep -r "com.zynta.pos" --include="*.kt" --include="*.gradle.kts"` returns **zero results**
- [ ] `grep -r "com.zentapos" --include="*.kt" --include="*.gradle.kts"` returns **zero results**
- [ ] `./gradlew :androidApp:assembleDebug :composeApp:jvmJar` exits with **BUILD SUCCESSFUL**
- [ ] `applicationId` in `androidApp/build.gradle.kts` = `com.zyntasolutions.zyntapos`
- [ ] All docs updated with new canonical namespace
- [ ] execution_log.md fully updated
