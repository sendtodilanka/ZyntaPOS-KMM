# Sprint 23 Integrity Audit — RBAC Role Editor Partial
**Generated:** 2026-04-26 16:50

This partial audit verifies the **Custom RBAC Role Editor** claims in the Sprint 23 plan documents
against the current ZyntaPOS-KMM codebase.

## Sources
- Spec: `docs/plans/phase/p3/Phase3_Sprint23.md` lines 18–383, tasks 23.1–23.5
- Scaffold: `docs/plan/phase3-sprint23-scaffold-20260423-1722.md` Section 2 (lines 19–71)

---

## Section 1: Screen files — RoleListScreen / RoleEditorScreen
**Plan source:** Sprint23.md lines 20–28, 84–193, 197–381
**Status:** ❌ missing (legacy alternative exists)

### Plan claim
> New screens at `composeApp/feature/settings/.../screen/`:
> - `RoleListScreen.kt` (FAB "Create Role", system + custom role separation)
> - `RoleEditorScreen.kt` (permission tree with `TriStateCheckbox`, `PermissionGroupHeader`, `PermissionRow`)

### Codebase reality
- Searched: `composeApp/feature/settings/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/settings/screen/`
- **Neither `RoleListScreen.kt` nor `RoleEditorScreen.kt` exists.**
- Instead a single legacy file exists:
  `composeApp/feature/settings/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/settings/screen/RbacManagementScreen.kt` (481 LOC)
- `RbacManagementScreen` combines list + edit-via-bottom-sheet on one screen and uses `Checkbox`, **not** `TriStateCheckbox`. No `PermissionGroupHeader`, no `PermissionRow`, no module-grouped permissions.
- `grep "TriStateCheckbox|isIndeterminate|ToggleableState"` returns zero matches in the entire settings module.
- `grep "PermissionGroup"` returns zero matches in `composeApp/feature/settings/src`.

### Verdict
The plan's two-screen split (list + dedicated tri-state tree editor) has **not** been implemented. The existing `RbacManagementScreen` is a single-screen legacy implementation that pre-dates the plan and lacks: tri-state header checkboxes, the `PermissionGroup` module grouping, and the dedicated `RoleEditorScreen` route. Tasks 23.4 and 23.5 are NOT done.

=== SECTION 1 COMPLETE ===

---

## Section 2: Use cases (5)
**Plan source:** Sprint23.md lines 30–59, task 23.2 / 23.3
**Status:** ⚠️ partial — 4 of 5 names match, signatures partially diverge, no impls for 2 interfaces

### Plan claim
Five `fun interface` use cases under `shared/domain/.../usecase/rbac/`:
1. `GetRolesUseCase : Flow<List<Role>>`
2. `SaveCustomRoleUseCase(role: Role): Result<Role>`
3. `DeleteCustomRoleUseCase(roleId: String): Result<Unit>`
4. `CloneRoleUseCase(sourceRoleId, newName): Result<Role>`
5. `GetPermissionsTreeUseCase(): List<PermissionGroup>`

### Codebase reality
Files found in `shared/domain/src/commonMain/kotlin/com/zyntasolutions/zyntapos/domain/usecase/rbac/`:
| Plan name | Actual file | Class kind | Notes |
|-----------|-------------|-----------|-------|
| GetRolesUseCase | **GetCustomRolesUseCase.kt** (renamed) | concrete `class` | Returns `Flow<List<CustomRole>>` (not `List<Role>`) — only custom roles, system roles excluded. |
| SaveCustomRoleUseCase | SaveCustomRoleUseCase.kt | concrete `class` | Signature `(CustomRole, isUpdate: Boolean): Result<Unit>` — uses `CustomRole` not `Role`, returns `Unit` not `Role`, adds an extra `isUpdate` flag. |
| DeleteCustomRoleUseCase | DeleteCustomRoleUseCase.kt | concrete `class` | Matches `(id: String): Result<Unit>`. |
| CloneRoleUseCase | CloneRoleUseCase.kt | `fun interface` | Matches signature, returns `Result<CustomRole>` (plan said `Result<Role>` — `CustomRole` is the correct type). **No implementation class found anywhere in the repo.** |
| GetPermissionsTreeUseCase | GetPermissionsTreeUseCase.kt | `fun interface` | Matches signature `suspend (): List<PermissionGroup>`. **No implementation class found** (task 23.3 explicitly required `GetPermissionsTreeUseCaseImpl`). |

DI verification (`SettingsModule.kt`): only `SaveCustomRoleUseCase` and `DeleteCustomRoleUseCase` are bound via `factoryOf(::...)`. `CloneRoleUseCase`, `GetPermissionsTreeUseCase`, and `GetCustomRolesUseCase` are NOT registered.

### Verdict
Three out of five use cases have working concrete classes; two (`CloneRoleUseCase`, `GetPermissionsTreeUseCase`) are interface-only stubs with no implementations and no DI bindings. Several signatures diverge from the plan: the plan repeatedly says `Role` where `CustomRole` is the correct type. Task 23.2 partially done; task 23.3 (impl of `GetPermissionsTreeUseCaseImpl`) is **not done**.

=== SECTION 2 COMPLETE ===

---

## Section 3: Domain models — PermissionGroup / PermissionItem
**Plan source:** Sprint23.md lines 61–80, task 23.1
**Status:** ✅ matches

### Plan claim
Two data classes in `:shared:domain` model package:
- `PermissionGroup(module, displayName, permissions: List<PermissionItem>)`
- `PermissionItem(permission: Permission, displayName: String, description: String)`

### Codebase reality
- `shared/domain/src/commonMain/kotlin/com/zyntasolutions/zyntapos/domain/model/PermissionGroup.kt` exists (15 lines) and contains both data classes.
- Field-by-field signatures match the plan exactly.

### Verdict
Task 23.1 is **DONE**. Models are present and signatures align with the spec.

=== SECTION 3 COMPLETE ===

---

## Section 4: Existing RBAC infrastructure (Role, CustomRole, Permission, RbacEngine)
**Plan source:** Scaffold doc lines 19–52
**Status:** ⚠️ partial — scaffold doc claims accurate; plan permission names don't match

### Scaffold doc claims (verified)
- `RbacEngine.kt` exists at the cited path; stateless, three evaluation modes (`hasPermission`, `hasPermissionAtStore`, dynamic `resolvePermissions` with `customRoles` + `builtInOverrides`). ✅ Matches.
- `Role` enum has 5 values: `ADMIN, STORE_MANAGER, CASHIER, ACCOUNTANT, STOCK_MANAGER`. ✅ Matches scaffold doc, **but** the spec doc (lines 94, 215–222) instead claims roles are `ADMIN, MANAGER, CASHIER, CUSTOMER_SERVICE, REPORTER` and references permissions like `POS_ACCESS, CREATE_ORDER, PROCESS_PAYMENT, VIEW_INVENTORY, MANAGE_STOCK, VIEW_STAFF, MANAGE_ATTENDANCE, APPROVE_LEAVE, MANAGE_PAYROLL, VIEW_CUSTOMERS, VIEW_SETTINGS, VIEW_ADMIN_PANEL, MANAGE_BACKUPS, VIEW_AUDIT_LOGS, MANAGE_MEDIA, MANAGE_EINVOICE, VIEW_ACCOUNTING, MANAGE_MULTI_STORE`.
  None of those permission names exist in `Permission.kt`. The actual enum uses `PROCESS_SALE, MANAGE_PRODUCTS, ADJUST_STOCK, MANAGE_STAFF, MANAGE_CUSTOMERS, VIEW_AUDIT_LOG, MANAGE_BACKUP, MANAGE_ACCOUNTING, ADMIN_ACCESS`, etc.
- `CustomRole.kt` exists at cited path with fields `(id, name, description, permissions: Set<Permission>, createdAt, updatedAt)`. ✅ Matches.
- Scaffold doc says **42 permissions**; actual file has **45** (`grep -cE '^\s+[A-Z_]+,$'`). Minor drift.

### Verdict
Scaffold doc accurately describes the existing infrastructure. The Sprint23.md spec doc, however, references a **fictional role/permission taxonomy** that does not exist in the codebase — anyone implementing screens against the spec verbatim would hit ~18 unresolved references. The plan needs to be reconciled with the canonical `Role` and `Permission` enums.

=== SECTION 4 COMPLETE ===

---

## Section 5: Existing RbacManagementScreen scaffold claim
**Plan source:** Scaffold doc lines 54–69
**Status:** ✅ matches (claim verified)

### Scaffold claim
> "Already scaffolded in `RbacManagementScreen.kt` (lines 77–100); existing screen imports custom role UI components, uses `SettingsViewModel` with `RbacState` (line 80), bottom sheet for role editor form already wired (line 99), hardcoded strings present in current UI (to be migrated)."

### Codebase reality
- File exists at the cited path (481 LOC).
- Imports `SettingsState.RbacState`, `SettingsIntent`, `SettingsEffect` — uses the existing `SettingsViewModel`. ✅
- `ZyntaBottomSheet` rendered when `state.isCreatingCustomRole || state.editingCustomRole != null` (around line 173). ✅
- Strings are pulled via `LocalStrings.current[StringResource.SETTINGS_RBAC_*]` — **not hardcoded**, contrary to the scaffold's claim that "Hardcoded strings present in current UI (to be migrated)". This part of the scaffold doc is stale.
- Screen handles built-in role permission overrides AND custom role create/edit/delete in one screen — i.e., the scaffold is functionally complete for the legacy approach.
- Navigation route `ZyntaRoute.RbacManagement` is wired in `MainNavGraph.kt:425` and reachable from `SettingsHomeScreen`'s `RBAC_MANAGEMENT` action (`MainNavGraph.kt:336`).

### Verdict
The screen exists and is wired. The scaffold doc's claim is correct in spirit but understates the implementation maturity (strings are already i18n'd; built-in role overrides + custom role CRUD already work). The scaffold's "hardcoded strings to be migrated" remark is outdated.

=== SECTION 5 COMPLETE ===

---

## Section 6: Tasks 23.1–23.5 status summary
**Plan source:** Sprint23.md lines 574–578

| Task | Description | Status | Notes |
|------|-------------|--------|-------|
| 23.1 | Create `PermissionGroup.kt` and `PermissionItem.kt` domain models | ✅ Done | Both classes in single file; matches spec. |
| 23.2 | Create 5 use case interfaces | ⚠️ Partial | All 5 files exist; `GetRolesUseCase` is renamed `GetCustomRolesUseCase` and is a concrete `class` returning `Flow<List<CustomRole>>` (not `Flow<List<Role>>`); `SaveCustomRoleUseCase` is a `class` not `fun interface` and adds `isUpdate` param + `Result<Unit>` return. `CloneRoleUseCase` and `GetPermissionsTreeUseCase` are stub `fun interface`s. |
| 23.3 | Implement `GetPermissionsTreeUseCaseImpl` returning all permissions grouped by module | ❌ Not done | Interface exists but no `Impl` class anywhere in repo; not bound in `SettingsModule` Koin DI. |
| 23.4 | Implement `RoleListScreen.kt` with system/custom role separation | ❌ Not done as specified | File does not exist; functionality lives inside `RbacManagementScreen.kt` as one-screen list + bottom-sheet, without the dedicated screen split or FAB-based navigation to a separate editor. |
| 23.5 | Implement `RoleEditorScreen.kt` with `PermissionGroupHeader` (tri-state) and `PermissionRow` | ❌ Not done | File does not exist. No `TriStateCheckbox` / `ToggleableState` usage anywhere in the settings module. No module-grouped permission tree UI. Editor functionality exists only as a bottom-sheet form using flat `Checkbox` rows. |

### Summary metrics
- Files expected by plan: 7 (2 screens, 5 use cases, 1 model file)
- Files present: 6 (5 use cases — 1 renamed; 1 model file)
- Files missing: 2 dedicated screen files
- Use case impls missing: 2 (`GetPermissionsTreeUseCaseImpl`, any `CloneRoleUseCase` impl)
- Plan claim drift: spec lists fictional roles (`MANAGER, CUSTOMER_SERVICE, REPORTER`) and permissions (`POS_ACCESS, CREATE_ORDER, VIEW_ADMIN_PANEL, MANAGE_BACKUPS, MANAGE_EINVOICE, MANAGE_MULTI_STORE`, etc.) that do not exist.

### Net verdict
RBAC Role Editor work is roughly **40% complete** when measured against the Sprint 23 spec:
- Domain layer is in place (models + interfaces).
- Two of five use cases lack implementations.
- The dedicated tri-state tree editor screen and the dedicated role-list screen are **not implemented**; the legacy `RbacManagementScreen` covers only flat permission CRUD.
- Sprint 23 spec doc itself contains structural inaccuracies (wrong role/permission names) that will block any verbatim implementation; it must be reconciled with the canonical enums in `Role.kt` and `Permission.kt` before tasks 23.4/23.5 can begin.

=== SECTION 6 COMPLETE ===
