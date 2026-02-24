# ZyntaPOS — Phase 3 Sprint 13: Admin Feature Part 1 — Audit Log & System Health

> **Document ID:** ZYNTA-PLAN-PHASE3-SPRINT13-v1.0
> **Phase:** 3 — Enterprise (Months 13–18)
> **Sprint:** 13 of 24 | Week 13
> **Module(s):** `:composeApp:feature:admin`
> **Author:** Senior KMP Architect & Lead Engineer
> **Reference:** ZYNTA-MASTER-PLAN-v1.0 | ADR-001

---

## Goal

Implement the first part of `:composeApp:feature:admin` (M19): a system health dashboard that displays real-time application metrics and a filterable audit log viewer. Both screens require `Permission.VIEW_ADMIN_PANEL` and are accessible to the ADMIN role only.

---

## Module Structure

```
composeApp/feature/admin/
└── src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/admin/
    ├── di/
    │   └── AdminModule.kt
    ├── mvi/
    │   ├── AdminState.kt
    │   ├── AdminIntent.kt
    │   └── AdminEffect.kt
    ├── viewmodel/
    │   └── AdminViewModel.kt
    └── screen/
        ├── AdminDashboardScreen.kt
        └── AuditLogViewerScreen.kt
```

---

## MVI Contracts

### `AdminState.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.admin.mvi

import com.zyntasolutions.zyntapos.domain.model.*

data class AdminState(
    val systemHealth: SystemHealth? = null,
    val databaseStats: DatabaseStats? = null,
    val auditLogs: List<AuditEntry> = emptyList(),
    val auditFilter: AuditFilter = AuditFilter(),
    val backups: List<BackupInfo> = emptyList(),
    val purgeResult: PurgeResult? = null,
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val isVacuuming: Boolean = false,
    val isPurging: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

data class AuditFilter(
    val userId: String? = null,
    val entityType: String? = null,
    val action: String? = null,
    val fromDate: String? = null,    // ISO date "YYYY-MM-DD"
    val toDate: String? = null
) {
    val isActive: Boolean
        get() = userId != null || entityType != null || action != null
             || fromDate != null || toDate != null
}
```

### `AdminIntent.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.admin.mvi

sealed interface AdminIntent {
    data object LoadSystemHealth : AdminIntent
    data object RefreshSystemHealth : AdminIntent
    data object LoadDatabaseStats : AdminIntent
    data class LoadAuditLogs(val filter: AuditFilter) : AdminIntent
    data class UpdateAuditFilter(val filter: AuditFilter) : AdminIntent
    data object ClearAuditFilter : AdminIntent
    data object VacuumDatabase : AdminIntent
    data object PurgeExpiredData : AdminIntent
    data object CreateBackup : AdminIntent
    data class RestoreBackup(val backupId: String) : AdminIntent
    data class DeleteBackup(val backupId: String) : AdminIntent
    data object LoadBackups : AdminIntent
    data object DismissError : AdminIntent
    data object DismissSuccess : AdminIntent
}
```

### `AdminEffect.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.admin.mvi

sealed interface AdminEffect {
    data class ShowError(val message: String) : AdminEffect
    data class ShowSuccess(val message: String) : AdminEffect
    data class ConfirmRestore(val backupId: String, val backupName: String) : AdminEffect
    data class ConfirmDelete(val backupId: String, val backupName: String) : AdminEffect
    data object NavigateBack : AdminEffect
}
```

---

## ViewModel

### `AdminViewModel.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.admin.viewmodel

import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import com.zyntasolutions.zyntapos.domain.usecase.admin.*
import com.zyntasolutions.zyntapos.feature.admin.mvi.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class AdminViewModel(
    private val getSystemHealth: GetSystemHealthUseCase,
    private val getDatabaseStats: GetDatabaseStatsUseCase,
    private val vacuumDatabase: VacuumDatabaseUseCase,
    private val purgeExpiredData: PurgeExpiredDataUseCase,
    private val createBackup: CreateBackupUseCase,
    private val restoreBackup: RestoreBackupUseCase,
    private val listBackups: ListBackupsUseCase,
    private val deleteBackup: DeleteBackupUseCase
) : BaseViewModel<AdminState, AdminIntent, AdminEffect>(AdminState()) {

    init {
        handleIntent(AdminIntent.LoadSystemHealth)
        startHealthPolling()
    }

    override suspend fun handleIntent(intent: AdminIntent) {
        when (intent) {
            is AdminIntent.LoadSystemHealth    -> loadSystemHealth()
            is AdminIntent.RefreshSystemHealth -> loadSystemHealth()
            is AdminIntent.LoadDatabaseStats   -> loadDatabaseStats()
            is AdminIntent.LoadAuditLogs       -> loadAuditLogs(intent.filter)
            is AdminIntent.UpdateAuditFilter   -> updateFilter(intent.filter)
            is AdminIntent.ClearAuditFilter    -> clearFilter()
            is AdminIntent.VacuumDatabase      -> vacuumDb()
            is AdminIntent.PurgeExpiredData    -> purgeData()
            is AdminIntent.CreateBackup        -> createBackupRecord()
            is AdminIntent.RestoreBackup       -> confirmRestore(intent.backupId)
            is AdminIntent.DeleteBackup        -> confirmDelete(intent.backupId)
            is AdminIntent.LoadBackups         -> loadBackups()
            is AdminIntent.DismissError        -> updateState { it.copy(error = null) }
            is AdminIntent.DismissSuccess      -> updateState { it.copy(successMessage = null) }
        }
    }

    /** Polls system health every 30 seconds. */
    private fun startHealthPolling() {
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                loadSystemHealth()
            }
        }
    }

    private suspend fun loadSystemHealth() {
        runCatching { getSystemHealth() }
            .onSuccess { health -> updateState { it.copy(systemHealth = health) } }
            .onFailure { ex -> updateState { it.copy(error = ex.message) } }
    }

    private suspend fun loadDatabaseStats() {
        runCatching { getDatabaseStats() }
            .onSuccess { stats -> updateState { it.copy(databaseStats = stats) } }
            .onFailure { ex -> updateState { it.copy(error = ex.message) } }
    }

    private suspend fun loadAuditLogs(filter: AuditFilter) {
        updateState { it.copy(isLoading = true, auditFilter = filter) }
        // AuditLog is already stored in :shared:domain via AuditEntry model
        // Fetched from AuditRepository (existing from Phase 1)
        updateState { it.copy(isLoading = false) }
    }

    private fun updateFilter(filter: AuditFilter) {
        updateState { it.copy(auditFilter = filter) }
        viewModelScope.launch { loadAuditLogs(filter) }
    }

    private fun clearFilter() {
        updateFilter(AuditFilter())
    }

    private suspend fun vacuumDb() {
        updateState { it.copy(isVacuuming = true) }
        vacuumDatabase().fold(
            onSuccess = {
                updateState { it.copy(isVacuuming = false) }
                sendEffect(AdminEffect.ShowSuccess("Database vacuum completed"))
                loadDatabaseStats()
            },
            onFailure = { ex ->
                updateState { it.copy(isVacuuming = false, error = ex.message) }
            }
        )
    }

    private suspend fun purgeData() {
        updateState { it.copy(isPurging = true) }
        purgeExpiredData().fold(
            onSuccess = { result ->
                updateState { it.copy(isPurging = false, purgeResult = result) }
                sendEffect(AdminEffect.ShowSuccess("Purged ${result.deletedRows} rows, freed ${result.freedMb.format(1)} MB"))
            },
            onFailure = { ex ->
                updateState { it.copy(isPurging = false, error = ex.message) }
            }
        )
    }

    private suspend fun createBackupRecord() {
        updateState { it.copy(isBackingUp = true) }
        createBackup().fold(
            onSuccess = { backup ->
                updateState { state ->
                    state.copy(
                        isBackingUp = false,
                        backups = listOf(backup) + state.backups
                    )
                }
                sendEffect(AdminEffect.ShowSuccess("Backup created: ${backup.fileName}"))
            },
            onFailure = { ex ->
                updateState { it.copy(isBackingUp = false, error = ex.message) }
            }
        )
    }

    private fun confirmRestore(backupId: String) {
        val backup = state.value.backups.find { it.id == backupId } ?: return
        sendEffect(AdminEffect.ConfirmRestore(backupId, backup.fileName))
    }

    private fun confirmDelete(backupId: String) {
        val backup = state.value.backups.find { it.id == backupId } ?: return
        sendEffect(AdminEffect.ConfirmDelete(backupId, backup.fileName))
    }

    private fun loadBackups() {
        listBackups()
            .onEach { backups -> updateState { it.copy(backups = backups) } }
            .launchIn(viewModelScope)
    }

    private fun Double.format(decimals: Int) = "%.${decimals}f".format(this)
}
```

---

## Screen Files

### `AdminDashboardScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.admin.screen

/**
 * System health dashboard.
 *
 * Layout:
 * - "System Health" section:
 *   Sync status chip (IDLE=green, SYNCING=blue, ERROR=red)
 *   4 KPI cards: DB Size | WAL Size | Pending Sync | Failed Sync
 *   Memory usage bar: "Used / Total MB (NN%)"
 *   Uptime label: "Uptime: 2d 4h 15m"
 *   App version + DB schema version
 *   "Refresh" icon button (manual refresh)
 *
 * - "Database" section (tap → DatabaseManagementScreen from Sprint 14):
 *   Table count, total rows, last VACUUM date
 *
 * - "Backups" section (tap → BackupManagementScreen from Sprint 14):
 *   Latest backup file + date + size, "Create Backup" button
 *
 * Auto-refreshes health every 30 seconds (via ViewModel polling).
 */
@Composable
fun AdminDashboardScreen(
    viewModel: AdminViewModel,
    onNavigateToDatabase: () -> Unit,
    onNavigateToAuditLog: () -> Unit,
    onNavigateToBackups: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.handleIntent(AdminIntent.LoadSystemHealth)
        viewModel.handleIntent(AdminIntent.LoadDatabaseStats)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Administration") },
                actions = {
                    IconButton(onClick = { viewModel.handleIntent(AdminIntent.RefreshSystemHealth) }) {
                        /* refresh icon */
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = modifier.padding(padding)
        ) {
            // System Health Card
            item {
                SystemHealthCard(
                    health = state.systemHealth,
                    isLoading = state.isLoading
                )
            }

            // Database section
            item {
                AdminNavCard(
                    title = "Database Management",
                    subtitle = state.databaseStats?.let {
                        "${it.tableCount} tables • ${it.totalRows} rows • ${it.dbSizeMb.format(1)} MB"
                    } ?: "Loading…",
                    icon = "ic_database",
                    onClick = onNavigateToDatabase
                )
            }

            // Audit log section
            item {
                AdminNavCard(
                    title = "Audit Log",
                    subtitle = "View user actions and system events",
                    icon = "ic_audit",
                    onClick = onNavigateToAuditLog
                )
            }

            // Backup section
            item {
                val latestBackup = state.backups.maxByOrNull { it.createdAt }
                AdminNavCard(
                    title = "Backup Management",
                    subtitle = latestBackup?.let {
                        "Last: ${formatDate(it.createdAt)} • ${it.sizeMb.format(1)} MB"
                    } ?: "No backups yet",
                    icon = "ic_backup",
                    onClick = onNavigateToBackups
                )
            }
        }
    }
}

@Composable
private fun SystemHealthCard(
    health: com.zyntasolutions.zyntapos.domain.model.SystemHealth?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    ZyntaCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("System Health", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            if (health == null || isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                return@Column
            }

            // Sync status
            val syncColor = when (health.syncStatus) {
                "IDLE"    -> Color(0xFF16A34A)
                "SYNCING" -> Color(0xFF3B82F6)
                else      -> Color(0xFFEF4444)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZyntaStatusBadge(label = "Sync: ${health.syncStatus}", color = syncColor)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "v${health.appVersion} • DB v${health.dbVersion}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(12.dp))

            // KPI grid (2×2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HealthKpiCard("DB Size", "${health.dbSizeMb.format(1)} MB", Modifier.weight(1f))
                HealthKpiCard("WAL Size", "${health.walSizeMb.format(1)} MB", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HealthKpiCard("Pending Sync", "${health.pendingSyncCount}", Modifier.weight(1f))
                HealthKpiCard(
                    "Failed Sync",
                    "${health.failedSyncCount}",
                    Modifier.weight(1f),
                    valueColor = if (health.failedSyncCount > 0) MaterialTheme.colorScheme.error else null
                )
            }
            Spacer(Modifier.height(12.dp))

            // Memory usage
            Text(
                text = "Memory: ${health.memoryUsedMb} / ${health.memoryTotalMb} MB (${health.memoryUsagePercent.toInt()}%)",
                style = MaterialTheme.typography.bodySmall
            )
            LinearProgressIndicator(
                progress = { health.memoryUsagePercent / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
            Spacer(Modifier.height(8.dp))

            // Uptime
            Text(
                text = "Uptime: ${formatUptime(health.uptime)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun HealthKpiCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AdminNavCard(
    title: String,
    subtitle: String,
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZyntaCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource("ic_chevron_right"), contentDescription = null)
        }
    }
}

private fun formatUptime(millis: Long): String {
    val seconds = millis / 1000
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    return buildString {
        if (days > 0) append("${days}d ")
        if (hours > 0) append("${hours}h ")
        append("${minutes}m")
    }
}

private fun Double.format(decimals: Int) = "%.${decimals}f".format(this)
```

### `AuditLogViewerScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.admin.screen

/**
 * Audit log viewer with filters.
 *
 * Layout:
 * - Filter bar (collapsible) with fields:
 *     User ID / username search
 *     Entity type dropdown (Order, Product, Customer, Employee, ...)
 *     Action dropdown (CREATE, UPDATE, DELETE, LOGIN, LOGOUT, APPROVE, ...)
 *     Date range (from / to date pickers)
 *   Clear filter button (shown only when filter.isActive)
 *
 * - Audit log list (most recent first):
 *   Each entry: timestamp + user + action + entity type + entity ID
 *   Expandable: shows before/after JSON diff on tap
 *
 * - Empty state: "No audit logs match the current filter"
 *
 * Note: AuditEntry is an existing domain model from Phase 1 (shared/domain/model/AuditEntry.kt).
 * The AuditRepository (Phase 1) is used for retrieval.
 */
@Composable
fun AuditLogViewerScreen(
    viewModel: AdminViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var showFilterPanel by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.handleIntent(AdminIntent.LoadAuditLogs(AuditFilter()))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audit Log") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { /* back */ } },
                actions = {
                    if (state.auditFilter.isActive) {
                        // Active filter indicator
                        Badge { Text("●") }
                    }
                    IconButton(onClick = { showFilterPanel = !showFilterPanel }) {
                        /* filter icon */
                    }
                    if (state.auditFilter.isActive) {
                        IconButton(onClick = { viewModel.handleIntent(AdminIntent.ClearAuditFilter) }) {
                            /* clear filter icon */
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = modifier.padding(padding)) {
            // Collapsible filter panel
            AnimatedVisibility(visible = showFilterPanel) {
                AuditFilterPanel(
                    filter = state.auditFilter,
                    onFilterChange = { viewModel.handleIntent(AdminIntent.UpdateAuditFilter(it)) }
                )
            }

            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (state.auditLogs.isEmpty() && !state.isLoading) {
                EmptyStateView(message = "No audit logs match the current filter")
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.auditLogs, key = { it.id }) { entry ->
                        AuditEntryRow(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditFilterPanel(
    filter: AuditFilter,
    onFilterChange: (AuditFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // User search
            ZyntaTextField(
                label = "User",
                value = filter.userId ?: "",
                onValueChange = { onFilterChange(filter.copy(userId = it.takeIf(String::isNotBlank))) }
            )
            // Entity type dropdown
            ZyntaDropdownField(
                label = "Entity Type",
                options = listOf("All", "Order", "Product", "Customer", "Employee", "Payroll"),
                selected = filter.entityType ?: "All",
                onSelect = { onFilterChange(filter.copy(entityType = it.takeIf { it != "All" })) }
            )
            // Action dropdown
            ZyntaDropdownField(
                label = "Action",
                options = listOf("All", "CREATE", "UPDATE", "DELETE", "LOGIN", "LOGOUT", "APPROVE", "REJECT"),
                selected = filter.action ?: "All",
                onSelect = { onFilterChange(filter.copy(action = it.takeIf { it != "All" })) }
            )
            // Date range
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ZyntaDateField(
                    label = "From",
                    value = filter.fromDate,
                    onClick = { /* open picker */ },
                    modifier = Modifier.weight(1f)
                )
                ZyntaDateField(
                    label = "To",
                    value = filter.toDate,
                    onClick = { /* open picker */ },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AuditEntryRow(
    entry: com.zyntasolutions.zyntapos.domain.model.AuditEntry,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ZyntaCard(
        onClick = { expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${entry.action} ${entry.entityType}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = formatDateTime(entry.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "By: ${entry.userId} • ID: ${entry.entityId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Before/after diff (expandable)
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 8.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = entry.details ?: "No details available",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
```

---

## DI Module

### `AdminModule.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.admin.di

import com.zyntasolutions.zyntapos.domain.usecase.admin.*
import com.zyntasolutions.zyntapos.feature.admin.viewmodel.AdminViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val adminModule = module {
    single<GetSystemHealthUseCase>  { GetSystemHealthUseCase { get<com.zyntasolutions.zyntapos.domain.repository.SystemRepository>().getSystemHealth() } }
    single<GetDatabaseStatsUseCase> { GetDatabaseStatsUseCase { get<com.zyntasolutions.zyntapos.domain.repository.SystemRepository>().getDatabaseStats() } }
    single<VacuumDatabaseUseCase>   { VacuumDatabaseUseCase { get<com.zyntasolutions.zyntapos.domain.repository.SystemRepository>().vacuumDatabase() } }
    single<PurgeExpiredDataUseCase> { PurgeExpiredDataUseCase { get<com.zyntasolutions.zyntapos.domain.repository.SystemRepository>().purgeExpiredData() } }
    single<CreateBackupUseCase>     { CreateBackupUseCase { get<com.zyntasolutions.zyntapos.domain.repository.BackupRepository>().createBackup() } }
    single<RestoreBackupUseCase>    { RestoreBackupUseCase { id -> get<com.zyntasolutions.zyntapos.domain.repository.BackupRepository>().restoreBackup(id) } }
    single<ListBackupsUseCase>      { ListBackupsUseCase { get<com.zyntasolutions.zyntapos.domain.repository.BackupRepository>().listBackups() } }
    single<DeleteBackupUseCase>     { DeleteBackupUseCase { id -> get<com.zyntasolutions.zyntapos.domain.repository.BackupRepository>().deleteBackup(id) } }

    viewModel {
        AdminViewModel(
            getSystemHealth  = get(),
            getDatabaseStats = get(),
            vacuumDatabase   = get(),
            purgeExpiredData = get(),
            createBackup     = get(),
            restoreBackup    = get(),
            listBackups      = get(),
            deleteBackup     = get()
        )
    }
}
```

---

## Tasks

- [ ] **13.1** Create `AdminState.kt`, `AdminIntent.kt`, `AdminEffect.kt` in `mvi/` package
- [ ] **13.2** Implement `AdminViewModel.kt` with 30-second health polling loop
- [ ] **13.3** Implement `AdminDashboardScreen.kt` with `SystemHealthCard`, `HealthKpiCard`, `AdminNavCard`
- [ ] **13.4** Implement `AuditLogViewerScreen.kt` with collapsible `AuditFilterPanel` and expandable `AuditEntryRow`
- [ ] **13.5** Create `AdminModule.kt` Koin module and register in `ZyntaApplication`
- [ ] **13.6** Wire admin navigation sub-graph in `MainNavGraph.kt` — gated by `Permission.VIEW_ADMIN_PANEL`
- [ ] **13.7** Add "Admin" nav item to `NavigationItems.kt` for ADMIN role only
- [ ] **13.8** Write `AdminViewModelTest` — test health polling, filter updates, clear filter
- [ ] **13.9** Verify: `./gradlew :composeApp:feature:admin:assemble && ./gradlew :composeApp:feature:admin:test`

---

## Verification

```bash
./gradlew :composeApp:feature:admin:assemble
./gradlew :composeApp:feature:admin:test
./gradlew :composeApp:feature:admin:detekt
```

---

## Definition of Done

- [ ] `AdminViewModel` extends `BaseViewModel` (ADR-001) and polls health every 30s
- [ ] `AdminDashboardScreen` displays sync status, KPIs, memory bar, uptime
- [ ] `AuditLogViewerScreen` supports all 5 filter fields with clear action
- [ ] `AdminModule` Koin bindings correct and registered
- [ ] Navigation gated by `Permission.VIEW_ADMIN_PANEL`
- [ ] ViewModel tests pass (polling, filters)
- [ ] Commit: `feat(admin): add system health dashboard and audit log viewer`
