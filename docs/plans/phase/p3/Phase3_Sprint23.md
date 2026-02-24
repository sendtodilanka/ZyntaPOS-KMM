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
 *   ADMIN, MANAGER, CASHIER, CUSTOMER_SERVICE, REPORTER
 *   Each shows permission count badge; no edit/delete
 * - "Custom Roles" section:
 *   Custom roles with edit + delete buttons
 *   Delete: disabled if any active users have this role
 * - FAB: "Create Custom Role" → RoleEditorScreen(roleId=null)
 *
 * RBAC: requires Permission.VIEW_ADMIN_PANEL (ADMIN role).
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
 * Permission groups (GetPermissionsTreeUseCase output):
 *   POS:         POS_ACCESS, CREATE_ORDER, VOID_ORDER, APPLY_DISCOUNT, PROCESS_PAYMENT
 *   Inventory:   VIEW_INVENTORY, MANAGE_PRODUCTS, MANAGE_CATEGORIES, MANAGE_STOCK
 *   Staff:       VIEW_STAFF, MANAGE_STAFF, MANAGE_ATTENDANCE, APPROVE_LEAVE, MANAGE_PAYROLL
 *   Reports:     VIEW_REPORTS, EXPORT_REPORTS
 *   Customers:   VIEW_CUSTOMERS, MANAGE_CUSTOMERS
 *   Settings:    VIEW_SETTINGS, MANAGE_SETTINGS, VIEW_ADMIN_PANEL
 *   Admin:       MANAGE_BACKUPS, VIEW_AUDIT_LOGS, MANAGE_MEDIA
 *   E-Invoice:   MANAGE_EINVOICE, VIEW_ACCOUNTING
 *   Multi-Store: MANAGE_MULTI_STORE
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

### Sinhala and Tamil Translation Files

**Location:** `shared/core/src/commonMain/resources/`

Files: `strings_si.json`, `strings_ta.json`

These files contain the complete translation for all 800+ string keys used in the app. Below are the Phase 3 additions (added on top of Phase 2 stubs):

```json
// strings_si.json — Phase 3 additions (Staff, Admin, Media, E-Invoice)
{
  "staff.employees": "සේවකයින්",
  "staff.attendance": "පැමිණීම",
  "staff.leave": "නිවාඩු",
  "staff.payroll": "ගෙවීම්",
  "staff.shift": "ෂිෆ්ට්",
  "staff.clock_in": "කාලය ලොග් කිරීම",
  "staff.clock_out": "කාලය ලොග් කිරීම අවසන්",
  "staff.generate_payroll": "ගෙවීම් ජනනය කරන්න",
  "admin.system_health": "පද්ධති සෞඛ්‍යය",
  "admin.backup": "අනුග්‍රාහකය",
  "admin.restore": "යථා තත්ත්වයට ගෙන ඒම",
  "admin.audit_log": "විගණන ලොගය",
  "admin.database": "දත්ත ගබඩාව",
  "media.library": "මාධ්‍ය පුස්තකාලය",
  "media.upload": "උඩුගත කිරීම",
  "media.crop": "කැපීම",
  "einvoice.title": "ඊ-ඉන්වොයිස්",
  "einvoice.submit": "IRD වෙත ඉදිරිපත් කිරීම",
  "einvoice.compliance": "අනුකූලතා වාර්තාව"
}

// strings_ta.json — Phase 3 additions
{
  "staff.employees": "ஊழியர்கள்",
  "staff.attendance": "வருகை",
  "staff.leave": "விடுப்பு",
  "staff.payroll": "சம்பள பட்டியல்",
  "staff.shift": "பணி நேரம்",
  "staff.clock_in": "நேர பதிவு",
  "staff.clock_out": "நேர பதிவு முடிவு",
  "staff.generate_payroll": "சம்பளம் உருவாக்கு",
  "admin.system_health": "கணினி ஆரோக்கியம்",
  "admin.backup": "காப்புப்பிரதி",
  "admin.restore": "மீட்டமை",
  "admin.audit_log": "தணிக்கை பதிவு",
  "media.library": "மீடியா நூலகம்",
  "media.upload": "பதிவேற்றம்",
  "media.crop": "வெட்டுதல்",
  "einvoice.title": "இ-இன்வாய்ஸ்",
  "einvoice.submit": "IRD சமர்பிக்கவும்",
  "einvoice.compliance": "இணக்க அறிக்கை"
}
```

### `LocalizationManager` Updates

```kotlin
// In LocalizationManager (shared/core):
// Add font loading for Sinhala and Tamil scripts:

fun loadFontForLanguage(language: String): FontFamily {
    return when (language) {
        "si" -> FontFamily(Font("fonts/NotoSansSinhala-Regular.ttf"))
        "ta" -> FontFamily(Font("fonts/NotoSansTamil-Regular.ttf"))
        else -> FontFamily.Default
    }
}

// Add key validation (dev/debug builds only):
fun validateKeys(targetLanguage: String): List<String> {
    val englishKeys = loadJson("strings_en.json").keys
    val targetKeys  = loadJson("strings_${targetLanguage}.json").keys
    val missing = englishKeys - targetKeys
    if (missing.isNotEmpty()) {
        Logger.withTag("Localization").w { "[$targetLanguage] ${missing.size} missing keys: ${missing.take(5)}" }
    }
    return missing.toList()
}
```

**Font files location:** `shared/core/src/commonMain/resources/fonts/`
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

- [ ] **23.1** Create `PermissionGroup.kt` and `PermissionItem.kt` domain models
- [ ] **23.2** Create `GetPermissionsTreeUseCase`, `GetRolesUseCase`, `SaveCustomRoleUseCase`, `DeleteCustomRoleUseCase`, `CloneRoleUseCase` interfaces
- [ ] **23.3** Implement `GetPermissionsTreeUseCaseImpl` returning all permissions grouped by module
- [ ] **23.4** Implement `RoleListScreen.kt` with system/custom role separation
- [ ] **23.5** Implement `RoleEditorScreen.kt` with `PermissionGroupHeader` (tri-state) and `PermissionRow` checkboxes
- [ ] **23.6** Complete `strings_si.json` and `strings_ta.json` with all 800+ Phase 1–3 keys
- [ ] **23.7** Add Noto Sans Sinhala + Tamil font files to `shared/core/src/commonMain/resources/fonts/`
- [ ] **23.8** Update `LocalizationManager` with `loadFontForLanguage()` and `validateKeys()` (debug only)
- [ ] **23.9** Implement `SecurityPolicySettingsScreen.kt`, `DataRetentionSettingsScreen.kt`, `AuditPolicySettingsScreen.kt`
- [ ] **23.10** Wire all 5 new settings routes in `MainNavGraph.kt`
- [ ] **23.11** Write `RoleEditorViewModelTest` — test permission selection, indeterminate state, save role
- [ ] **23.12** Write `LocalizationTest` — validate all SI/TA keys present vs EN baseline
- [ ] **23.13** Verify: `./gradlew :shared:core:assemble && ./gradlew :composeApp:feature:settings:assemble`

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

- [ ] `RoleListScreen` correctly distinguishes system roles (read-only) from custom roles (editable)
- [ ] `RoleEditorScreen` permission tree has tri-state module checkboxes and individual permission rows
- [ ] `SaveCustomRoleUseCase` persists role to `roles` table with permissions
- [ ] `strings_si.json` and `strings_ta.json` have zero missing keys vs `strings_en.json`
- [ ] Noto Sans Sinhala and Tamil fonts load without errors
- [ ] `LocalizationManager.validateKeys()` reports no missing keys in debug build
- [ ] All three advanced settings screens implement correct read/write to `settings` table
- [ ] Role editor tests pass (permission selection, tri-state, save)
- [ ] Localization test (no missing keys) passes
- [ ] Commit: `feat(settings): add custom role editor, complete SI/TA translations, and advanced security settings`
