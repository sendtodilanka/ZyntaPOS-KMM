# ZyntaPOS — Phase 3 Sprint 23: Custom RBAC Role Editor, Full i18n (SI/TA), Advanced Settings

> **Document ID:** ZYNTA-PLAN-PHASE3-SPRINT23-v1.0
> **Phase:** 3 — Enterprise (Months 13–18)
> **Sprint:** 23 of 24 | Week 23
> **Module(s):** `:composeApp:feature:settings`, `:shared:core`, `:shared:security`
> **Author:** Senior KMP Architect & Lead Engineer
> **Reference:** ZYNTA-MASTER-PLAN-v1.0 | ADR-001

---

## Goal

Implement the custom RBAC role editor UI (role list + permission tree editor), complete all Sinhala and Tamil translations (replacing stubs from Phase 2), update `LocalizationManager` with key validation + Noto Sans font loading, and deliver three advanced settings screens: Security Policy, Data Retention, and Audit Policy.

---

## Custom RBAC Role Editor

### New Screens

**Location:** `composeApp/feature/settings/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/settings/screen/`

```
screen/
├── RoleListScreen.kt              # List all roles + add custom role button
└── RoleEditorScreen.kt            # Permission tree editor for a custom role
```

### New Use Cases

**Location:** `shared/domain/src/commonMain/kotlin/com/zyntasolutions/zyntapos/domain/usecase/rbac/`

```kotlin
// GetRolesUseCase.kt
fun interface GetRolesUseCase {
    operator fun invoke(): Flow<List<Role>>
}

// SaveCustomRoleUseCase.kt
fun interface SaveCustomRoleUseCase {
    suspend operator fun invoke(role: Role): Result<Role>
}

// DeleteCustomRoleUseCase.kt
fun interface DeleteCustomRoleUseCase {
    suspend operator fun invoke(roleId: String): Result<Unit>
}

// CloneRoleUseCase.kt
fun interface CloneRoleUseCase {
    suspend operator fun invoke(sourceRoleId: String, newName: String): Result<Role>
}

// GetPermissionsTreeUseCase.kt
fun interface GetPermissionsTreeUseCase {
    suspend operator fun invoke(): List<PermissionGroup>
}
```

### `PermissionGroup.kt` (supporting model)

```kotlin
package com.zyntasolutions.zyntapos.domain.model

/**
 * Permissions grouped by module for the role editor permission tree.
 */
data class PermissionGroup(
    val module: String,          // "POS", "Inventory", "Staff", "Admin", etc.
    val displayName: String,
    val permissions: List<PermissionItem>
)

data class PermissionItem(
    val permission: Permission,
    val displayName: String,
    val description: String
)
```

---

### `RoleListScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.settings.screen

/**
 * Role list screen.
 *
 * Layout:
 * - "System Roles" section (read-only):
 *   ADMIN, STORE_MANAGER, CASHIER, ACCOUNTANT, STOCK_MANAGER
 *   Each shows permission count badge; no edit/delete
 * - "Custom Roles" section:
 *   Custom roles with edit + delete buttons
 *   Delete: disabled if any active users have this role
 * - FAB: "Create Custom Role" → RoleEditorScreen(roleId=null)
 *
 * RBAC: requires Permission.ADMIN_ACCESS (ADMIN role).
 */
@Composable
fun RoleListScreen(
    viewModel: SettingsViewModel,
    onNavigateToEditor: (roleId: String?) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.handleIntent(SettingsIntent.LoadRoles)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Roles & Permissions") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { /* back */ } }
            )
        },
        floatingActionButton = {
            ZyntaFab(text = "Create Role", onClick = { onNavigateToEditor(null) })
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier.padding(padding)
        ) {
            // System roles
            item { Text("System Roles", style = MaterialTheme.typography.titleSmall) }
            items(state.systemRoles) { role ->
                RoleCard(
                    role = role,
                    isSystem = true,
                    onEdit = {},
                    onDelete = {}
                )
            }

            // Custom roles
            if (state.customRoles.isNotEmpty()) {
                item { Text("Custom Roles", style = MaterialTheme.typography.titleSmall) }
                items(state.customRoles) { role ->
                    RoleCard(
                        role = role,
                        isSystem = false,
                        onEdit = { onNavigateToEditor(role.id) },
                        onDelete = { viewModel.handleIntent(SettingsIntent.DeleteRole(role.id)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleCard(
    role: com.zyntasolutions.zyntapos.domain.model.Role,
    isSystem: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZyntaCard(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(role.name, style = MaterialTheme.typography.titleSmall)
                    if (isSystem) {
                        Spacer(Modifier.width(8.dp))
                        ZyntaStatusBadge(label = "System", color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                Text(
                    text = "${role.permissions.size} permissions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isSystem) {
                IconButton(onClick = onEdit) { /* edit icon */ }
                IconButton(onClick = onDelete) { /* delete icon */ }
            }
        }
    }
}
```

---

### `RoleEditorScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.settings.screen

/**
 * Custom role editor screen.
 *
 * Layout:
 * - Role name text field (required)
 * - "Clone from existing role" dropdown (optional starting point)
 * - Permission tree: grouped by module
 *   Module header (checkable — selects/deselects all module permissions)
 *   Individual permission rows: name, description, checkbox
 * - Save button → SaveCustomRoleUseCase
 *
 * Permission groups (GetPermissionsTreeUseCase output — names sourced from the
 * canonical [com.zyntasolutions.zyntapos.domain.model.Permission] enum):
 *   POS:         PROCESS_SALE, VOID_ORDER, APPLY_DISCOUNT, HOLD_ORDER, PROCESS_REFUND
 *   Register:    OPEN_REGISTER, CLOSE_REGISTER, RECORD_CASH_MOVEMENT
 *   Inventory:   MANAGE_PRODUCTS, MANAGE_CATEGORIES, ADJUST_STOCK, MANAGE_SUPPLIERS, MANAGE_STOCKTAKE
 *   Staff:       MANAGE_STAFF
 *   Reports:     VIEW_REPORTS, EXPORT_REPORTS, PRINT_INVOICE
 *   Customers:   MANAGE_CUSTOMERS, MANAGE_CUSTOMER_GROUPS, MANAGE_WALLETS, MANAGE_LOYALTY
 *   Coupons:     MANAGE_COUPONS
 *   Expenses:    MANAGE_EXPENSES, APPROVE_EXPENSES
 *   Settings:    MANAGE_USERS, MANAGE_SETTINGS, MANAGE_TAX, MANAGE_HARDWARE
 *   Admin:       ADMIN_ACCESS, MANAGE_BACKUP, VIEW_AUDIT_LOG
 *   Accounting:  MANAGE_ACCOUNTING
 *   Multi-Store: MANAGE_WAREHOUSES, MANAGE_STOCK_TRANSFERS
 *
 * IRD e-invoicing permissions deferred to Phase 4 (see CLAUDE.md note).
 */
@Composable
fun RoleEditorScreen(
    roleId: String?,            // null = create new
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var roleName by remember { mutableStateOf("") }
    var selectedPermissions by remember { mutableStateOf<Set<Permission>>(emptySet()) }

    LaunchedEffect(roleId) {
        if (roleId != null) {
            val existing = state.customRoles.find { it.id == roleId }
            if (existing != null) {
                roleName = existing.name
                selectedPermissions = existing.permissions.toSet()
            }
        }
        viewModel.handleIntent(SettingsIntent.LoadPermissionsTree)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (roleId == null) "Create Role" else "Edit Role") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { /* back */ } },
                actions = {
                    TextButton(
                        onClick = {
                            val role = buildRole(roleId, roleName, selectedPermissions)
                            viewModel.handleIntent(SettingsIntent.SaveCustomRole(role))
                        },
                        enabled = roleName.isNotBlank()
                    ) { Text("Save") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier.padding(padding)
        ) {
            // Role name input
            item {
                ZyntaTextField(
                    label = "Role Name *",
                    value = roleName,
                    onValueChange = { roleName = it },
                    isError = roleName.isBlank(),
                    errorMessage = if (roleName.isBlank()) "Role name is required" else null
                )
            }

            // Permission groups
            state.permissionsTree.forEach { group ->
                val groupPermissions = group.permissions.map { it.permission }
                val allSelected = groupPermissions.all { it in selectedPermissions }
                val someSelected = groupPermissions.any { it in selectedPermissions }

                item(key = "group_${group.module}") {
                    PermissionGroupHeader(
                        group = group,
                        isChecked = allSelected,
                        isIndeterminate = someSelected && !allSelected,
                        onToggle = { checked ->
                            selectedPermissions = if (checked) {
                                selectedPermissions + groupPermissions.toSet()
                            } else {
                                selectedPermissions - groupPermissions.toSet()
                            }
                        }
                    )
                }

                items(group.permissions, key = { "perm_${it.permission}" }) { item ->
                    PermissionRow(
                        item = item,
                        isChecked = item.permission in selectedPermissions,
                        onToggle = { checked ->
                            selectedPermissions = if (checked) {
                                selectedPermissions + item.permission
                            } else {
                                selectedPermissions - item.permission
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionGroupHeader(
    group: PermissionGroup,
    isChecked: Boolean,
    isIndeterminate: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            TriStateCheckbox(
                state = when {
                    isChecked -> ToggleableState.On
                    isIndeterminate -> ToggleableState.Indeterminate
                    else -> ToggleableState.Off
                },
                onClick = { onToggle(!isChecked) }
            )
            Spacer(Modifier.width(8.dp))
            Text(group.displayName, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Text(
                "${group.permissions.size} permissions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun PermissionRow(
    item: PermissionItem,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggle(!isChecked) }
            .padding(start = 32.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Checkbox(checked = isChecked, onCheckedChange = onToggle)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(item.displayName, style = MaterialTheme.typography.bodyMedium)
            Text(
                item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

---

## i18n Completion

### Sinhala and Tamil Translation Tables

**Location:** `shared/core/src/commonMain/kotlin/com/zyntasolutions/zyntapos/core/i18n/`

Files: `SinhalaStrings.kt`, `TamilStrings.kt`

> **Architecture note (reconciled 2026-04-26):** This codebase ships translations as
> Kotlin `Map<StringResource, String>` tables — see `EnglishStrings.kt` (~2104 lines).
> The `LocalizationManager` registers each table via `registerStrings(locale, table)`
> at startup (see `composeApp/src/commonMain/.../App.kt`). The Phase 2 stub mention in
> earlier drafts of this plan was incorrect — neither `SinhalaStrings.kt` nor
> `TamilStrings.kt` exists yet. The full surface to translate is the
> ~1997-entry `StringResource` enum (not the "800+" originally cited).

Each translation file follows the exact pattern below:

```kotlin
// SinhalaStrings.kt
package com.zyntasolutions.zyntapos.core.i18n

@Suppress("detekt:LargeClass")
internal object SinhalaStrings {
    val table: Map<StringResource, String> = mapOf(
        // Phase 3 sample additions — Staff, Admin, Media (IRD e-invoicing
        // translations are deferred to Phase 4 along with the IRD code path).
        StringResource.STAFF_EMPLOYEES to "සේවකයින්",
        StringResource.STAFF_ATTENDANCE to "පැමිණීම",
        StringResource.STAFF_LEAVE to "නිවාඩු",
        StringResource.STAFF_PAYROLL to "ගෙවීම්",
        StringResource.STAFF_SHIFT to "ෂිෆ්ට්",
        StringResource.STAFF_CLOCK_IN to "කාලය ලොග් කිරීම",
        StringResource.STAFF_CLOCK_OUT to "කාලය ලොග් කිරීම අවසන්",
        StringResource.STAFF_GENERATE_PAYROLL to "ගෙවීම් ජනනය කරන්න",
        StringResource.ADMIN_SYSTEM_HEALTH to "පද්ධති සෞඛ්‍යය",
        StringResource.ADMIN_BACKUP to "අනුග්‍රාහකය",
        StringResource.ADMIN_RESTORE to "යථා තත්ත්වයට ගෙන ඒම",
        StringResource.ADMIN_AUDIT_LOG to "විගණන ලොගය",
        StringResource.ADMIN_DATABASE to "දත්ත ගබඩාව",
        StringResource.MEDIA_LIBRARY to "මාධ්‍ය පුස්තකාලය",
        StringResource.MEDIA_UPLOAD to "උඩුගත කිරීම",
        StringResource.MEDIA_CROP to "කැපීම",
        // ... remaining ~1980 keys to fill — needs native-speaker review.
    )
}

// TamilStrings.kt (parallel structure)
internal object TamilStrings {
    val table: Map<StringResource, String> = mapOf(
        StringResource.STAFF_EMPLOYEES to "ஊழியர்கள்",
        StringResource.STAFF_ATTENDANCE to "வருகை",
        // ... remaining ~1980 keys to fill — needs native-speaker review.
    )
}
```

> **IRD / e-invoicing rows removed (2026-04-26):** Earlier drafts listed
> `einvoice.title`, `einvoice.submit`, `einvoice.compliance` rows in both SI/TA
> tables. IRD e-invoicing has been deferred to Phase 4 (see CLAUDE.md), so the
> matching `StringResource` keys may not exist; do not translate them as part of
> this sprint.

### `LocalizationManager` reconciliation

The font-loading and key-validation utilities have **already been implemented**
under different names than the original Sprint 23 spec. Current API:

```kotlin
// Already present in shared/core/.../i18n/LocalizationManager.kt:

fun fontFamilyForLanguage(languageCode: String): String? = when (languageCode) {
    "si" -> "Noto Sans Sinhala"
    "ta" -> "Noto Sans Tamil"
    "hi" -> "Noto Sans Devanagari"
    "ar" -> "Noto Sans Arabic"
    "ja" -> "Noto Sans JP"
    "zh" -> "Noto Sans SC"
    else -> null   // Latin-script languages use the system default
}

fun validateKeys(locale: SupportedLocale): List<String> {
    val missing = missingKeys(locale)
    return missing.map { it.name }
}

fun missingKeys(locale: SupportedLocale): Set<StringResource> {
    val table = tables[locale] ?: return StringResource.entries.toSet()
    return StringResource.entries.filter { it !in table }.toSet()
}
```

> **Note:** The current `fontFamilyForLanguage` returns the font *name* as a
> `String?`; the platform layer is expected to map the name to a Compose
> `FontFamily`. Sprint 23 task 23.8 should therefore be reduced to:
>   1. Bundle Noto Sans Sinhala + Tamil `.ttf` files (Task 23.7).
>   2. Wire each platform's `Font(...)` resolution to the name returned by
>      `fontFamilyForLanguage` (Android: `androidx.compose.ui.text.googlefonts`;
>      JVM: classpath resource lookup).
>   3. No method renames are required.

**Font files location:** `composeApp/src/{androidMain,jvmMain}/resources/fonts/`
- `NotoSansSinhala-Regular.ttf` — Noto Sans Sinhala (Google Fonts, OFL license)
- `NotoSansTamil-Regular.ttf` — Noto Sans Tamil (Google Fonts, OFL license)

---

## Advanced Settings Screens

### `SecurityPolicySettingsScreen.kt`

```kotlin
/**
 * Security policy configuration screen.
 *
 * Settings (all stored in `settings` table):
 * - Session timeout: 5 / 15 / 30 / 60 minutes (dropdown)
 * - PIN complexity: 4-digit / 6-digit / alphanumeric (dropdown)
 * - Failed login lockout: after 3 / 5 / 10 attempts (dropdown)
 * - Lockout duration: 5 / 15 / 30 minutes (dropdown)
 * - Biometric authentication: toggle (requires device biometric support)
 *
 * Changes take effect on next session. Current session is not invalidated.
 */
@Composable
fun SecurityPolicySettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
)
```

### `DataRetentionSettingsScreen.kt`

```kotlin
/**
 * Data retention settings screen.
 *
 * Settings:
 * - Audit log retention: 30 / 90 / 180 / 365 days
 * - Sync queue retention: 7 / 14 / 30 days
 * - Report data retention: 6 / 12 / 24 months
 * - "Run Purge Now" button → calls PurgeExpiredDataUseCase immediately
 *
 * Shows estimated rows and MB that would be freed based on current settings.
 */
@Composable
fun DataRetentionSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
)
```

### `AuditPolicySettingsScreen.kt`

```kotlin
/**
 * Audit policy configuration screen.
 *
 * Toggles for which action types generate audit log entries:
 * - User login / logout (default: on)
 * - Product CRUD (default: on)
 * - Order create / void / refund (default: on)
 * - Customer CRUD (default: on)
 * - Settings changes (default: on)
 * - Payroll generation / payment (default: on)
 * - Backup / restore (default: on)
 * - Role changes (default: on, cannot be disabled)
 *
 * Settings stored in `settings` table as "audit.{action}.enabled" keys.
 */
@Composable
fun AuditPolicySettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
)
```

---

## Navigation Additions

```kotlin
// In SettingsGraph in MainNavGraph.kt:
composable<ZyntaRoute.RoleList>         { RoleListScreen(...) }
composable<ZyntaRoute.RoleEditor>       { RoleEditorScreen(...) }
composable<ZyntaRoute.SecurityPolicy>   { SecurityPolicySettingsScreen(...) }
composable<ZyntaRoute.DataRetention>    { DataRetentionSettingsScreen(...) }
composable<ZyntaRoute.AuditPolicy>      { AuditPolicySettingsScreen(...) }
```

Add to `ZyntaRoute.kt` (Sprint 7 additions should include these — add if missing):

```kotlin
data object RoleList : ZyntaRoute()
data class RoleEditor(val roleId: String? = null) : ZyntaRoute()
data object SecurityPolicy : ZyntaRoute()
data object DataRetention : ZyntaRoute()
data object AuditPolicy : ZyntaRoute()
```

---

## Tasks

> Status legend: ✅ done, ⚠️ partial, ❌ not started.
> Status reconciled with codebase on **2026-04-26** after PR #640.
> See `docs/audit/sprint23-integrity-audit-2026-04-26-1650.md` for the full audit.

- [x] **23.1** Create `PermissionGroup.kt` and `PermissionItem.kt` domain models. ✅ Done — single file at `shared/domain/.../model/PermissionGroup.kt` (15 LOC, both classes).
- [ ] **23.2** Create `GetCustomRolesUseCase`, `SaveCustomRoleUseCase`, `DeleteCustomRoleUseCase`, `CloneRoleUseCase`, `GetPermissionsTreeUseCase` interfaces. ⚠️ Partial — 5 files exist; `GetCustomRolesUseCase` is a `class` returning `Flow<List<CustomRole>>`; `SaveCustomRoleUseCase` is a `class` with extra `isUpdate: Boolean` parameter and `Result<Unit>` return; `CloneRoleUseCase` and `GetPermissionsTreeUseCase` are bare `fun interface` stubs.
- [ ] **23.3** Implement `GetPermissionsTreeUseCaseImpl` returning all permissions grouped by module. ❌ Not done — interface exists, no impl class anywhere; not bound in Koin.
- [ ] **23.4** Implement `RoleListScreen.kt` with system/custom role separation. ❌ Not done — file does not exist; legacy single-screen `RbacManagementScreen.kt` covers this functionally without the dedicated split.
- [ ] **23.5** Implement `RoleEditorScreen.kt` with `PermissionGroupHeader` (tri-state) and `PermissionRow` checkboxes. ❌ Not done — file does not exist; zero `TriStateCheckbox` usage anywhere in the settings module.
- [ ] **23.6** Complete `SinhalaStrings.kt` and `TamilStrings.kt` with all ~1997 Phase 1–3 `StringResource` keys. (Format: Kotlin `Map<StringResource, String>` matching `EnglishStrings.kt`.) ❌ Not done — neither file exists.
- [ ] **23.7** Add Noto Sans Sinhala + Tamil font files to `composeApp/src/{androidMain,jvmMain}/resources/fonts/`. ❌ Not done.
- [x] **23.8** Update `LocalizationManager` with `fontFamilyForLanguage()` and `validateKeys()`. ✅ Effectively done — both methods already exist (note: `fontFamilyForLanguage` returns the font *name*; platform layer must map the name to a Compose `FontFamily`).
- [x] **23.9** Implement `SecurityPolicySettingsScreen.kt`, `DataRetentionSettingsScreen.kt`, `AuditPolicySettingsScreen.kt`. ⚠️ Partial — read-only shells shipped (Batch 14, PR #640); persistence layer (`settings` table read/write), dropdowns, biometric toggle, and `SettingsViewModel` injection are deferred to Sprint 24.
- [ ] **23.10** Wire all 5 new settings routes in `MainNavGraph.kt`. ⚠️ 3/5 — SecurityPolicy / DataRetention / AuditPolicy fully wired; `RoleList` + `RoleEditor` still missing.
- [ ] **23.11** Write `RoleEditorViewModelTest` — test permission selection, indeterminate state, save role. ❌ Not done.
- [ ] **23.12** Write `LocalizationTest` — validate all SI/TA keys present vs EN baseline. ⚠️ Stub only — `LocalizationManagerTest.kt` exercises `missingKeys` / `registerStrings` with synthetic data; cannot assert real coverage until 23.6 lands.
- [x] **23.13** Verify: `./gradlew :shared:core:assemble && ./gradlew :composeApp:feature:settings:assemble`. ✅ Verified by CI on PR #640.

---

## Verification

```bash
./gradlew :shared:domain:assemble
./gradlew :shared:core:assemble
./gradlew :composeApp:feature:settings:assemble
./gradlew :composeApp:feature:settings:test
./gradlew :shared:core:test
```

---

## Definition of Done

> Status reconciled 2026-04-26 (post-Batch 14 / PR #640).

- [ ] `RoleListScreen` correctly distinguishes system roles (read-only) from custom roles (editable). ❌ File does not exist.
- [ ] `RoleEditorScreen` permission tree has tri-state module checkboxes and individual permission rows. ❌ File does not exist; no tri-state UI anywhere in the module.
- [ ] `SaveCustomRoleUseCase` persists role to `custom_roles` table with permissions. ⚠️ Use case exists with diverged signature (`(CustomRole, isUpdate): Result<Unit>`); end-to-end persistence not yet validated.
- [ ] `SinhalaStrings.kt` and `TamilStrings.kt` have zero missing keys vs `EnglishStrings.kt` baseline. ❌ Both files absent.
- [ ] Noto Sans Sinhala and Tamil fonts load without errors. ❌ Font assets absent.
- [ ] `LocalizationManager.validateKeys()` reports no missing keys in debug build. ⚠️ Method exists; cannot validate without SI/TA tables.
- [ ] All three advanced settings screens implement correct read/write to `settings` table. ❌ Read-only shells only — KDocs explicitly defer persistence to Sprint 24.
- [ ] Role editor tests pass (permission selection, tri-state, save)
- [ ] Localization test (no missing keys) passes
- [ ] Commit: `feat(settings): add custom role editor, complete SI/TA translations, and advanced security settings`
