# ZyntaPOS — Phase 3 Sprint 14: Admin Feature Part 2 — Database Management & Backup/Restore

> **Document ID:** ZYNTA-PLAN-PHASE3-SPRINT14-v1.0
> **Phase:** 3 — Enterprise (Months 13–18)
> **Sprint:** 14 of 24 | Week 14
> **Module(s):** `:composeApp:feature:admin`
> **Author:** Senior KMP Architect & Lead Engineer
> **Reference:** ZYNTA-MASTER-PLAN-v1.0 | ADR-001

---

## Goal

Implement database maintenance and backup/restore screens for `:composeApp:feature:admin`. Backup files are AES-256 encrypted via `CryptoManager` using the platform `BackupDriver` HAL (Sprint 7). Android writes to `getExternalFilesDir(null)/backups/`; JVM Desktop writes to `<appDataDir>/backups/`.

---

## New Screen Files

**Location:** `composeApp/feature/admin/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/admin/screen/`

```
screen/
├── DatabaseManagementScreen.kt    # Table stats + VACUUM + purge expired data
└── BackupManagementScreen.kt      # Backup list + create / restore / delete
```

---

## Database Management Screen

### `DatabaseManagementScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.admin.screen

/**
 * Database maintenance screen.
 *
 * Sections:
 * 1. Overview card:
 *    - DB version, DB size (MB), WAL size (MB)
 *    - Table count, total rows
 *    - Last VACUUM date (or "Never")
 *
 * 2. Table Statistics list:
 *    - Each row: table name | row count | estimated size MB
 *    - Sorted by row count descending
 *    - Tap to expand (no action for now — read-only info)
 *
 * 3. Maintenance Actions:
 *    - "VACUUM Database" button — calls VacuumDatabaseUseCase
 *      Shows loading indicator while isVacuuming = true
 *      After completion: refreshes database stats
 *    - "Purge Expired Data" button — calls PurgeExpiredDataUseCase
 *      Shows loading indicator while isPurging = true
 *      After completion: shows PurgeResultDialog with breakdown
 *
 * RBAC: requires Permission.VIEW_ADMIN_PANEL (ADMIN role).
 */
@Composable
fun DatabaseManagementScreen(
    viewModel: AdminViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var showPurgeResult by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.handleIntent(AdminIntent.LoadDatabaseStats)
        viewModel.effects.collect { effect ->
            when (effect) {
                is AdminEffect.ShowSuccess -> {
                    if (state.purgeResult != null) showPurgeResult = true
                }
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Database Management") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { /* back */ } }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = modifier.padding(padding)
        ) {
            // Overview card
            item {
                DatabaseOverviewCard(stats = state.databaseStats)
            }

            // Table statistics
            item {
                Text("Table Statistics", style = MaterialTheme.typography.titleMedium)
            }

            val stats = state.databaseStats
            if (stats != null) {
                items(
                    stats.tables.sortedByDescending { it.rowCount },
                    key = { it.tableName }
                ) { tableStats ->
                    TableStatsRow(tableStats)
                }
            }

            // Maintenance actions
            item {
                MaintenanceActionsCard(
                    isVacuuming = state.isVacuuming,
                    isPurging = state.isPurging,
                    onVacuum = { viewModel.handleIntent(AdminIntent.VacuumDatabase) },
                    onPurge = { viewModel.handleIntent(AdminIntent.PurgeExpiredData) }
                )
            }
        }
    }

    // Purge result dialog
    if (showPurgeResult) {
        state.purgeResult?.let { result ->
            PurgeResultDialog(
                result = result,
                onDismiss = {
                    showPurgeResult = false
                    viewModel.handleIntent(AdminIntent.DismissSuccess)
                }
            )
        }
    }
}

@Composable
private fun DatabaseOverviewCard(
    stats: com.zyntasolutions.zyntapos.domain.model.DatabaseStats?,
    modifier: Modifier = Modifier
) {
    ZyntaCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Database Overview", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            if (stats == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                return@Column
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DatabaseKpiItem("DB Size", "${stats.dbSizeMb.format(1)} MB", Modifier.weight(1f))
                DatabaseKpiItem("WAL Size", "${stats.walSizeMb.format(1)} MB", Modifier.weight(1f))
                DatabaseKpiItem("Schema", "v${stats.dbVersion}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DatabaseKpiItem("Tables", "${stats.tableCount}", Modifier.weight(1f))
                DatabaseKpiItem("Total Rows", "${stats.totalRows}", Modifier.weight(1f))
                DatabaseKpiItem("Indexes", "${stats.indexCount}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Last VACUUM: ${stats.lastVacuumAt?.let { formatDate(it) } ?: "Never"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DatabaseKpiItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall)
        Text(label, style = MaterialTheme.typography.labelSmall,
             color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TableStatsRow(
    tableStats: com.zyntasolutions.zyntapos.domain.model.TableStats,
    modifier: Modifier = Modifier
) {
    ZyntaCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(tableStats.tableName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = "${tableStats.rowCount} rows",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${tableStats.estimatedSizeMb.format(2)} MB",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun MaintenanceActionsCard(
    isVacuuming: Boolean,
    isPurging: Boolean,
    onVacuum: () -> Unit,
    onPurge: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZyntaCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Maintenance", style = MaterialTheme.typography.titleMedium)

            ZyntaButton(
                text = if (isVacuuming) "Vacuuming…" else "VACUUM Database",
                isLoading = isVacuuming,
                enabled = !isVacuuming && !isPurging,
                onClick = onVacuum,
                modifier = Modifier.fillMaxWidth()
            )

            ZyntaOutlinedButton(
                text = if (isPurging) "Purging…" else "Purge Expired Data",
                isLoading = isPurging,
                enabled = !isVacuuming && !isPurging,
                onClick = onPurge,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "VACUUM reclaims unused database space. Purge removes sync queue and audit entries older than 90 days.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PurgeResultDialog(
    result: com.zyntasolutions.zyntapos.domain.model.PurgeResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Purge Complete") },
        text = {
            Column {
                Text("Deleted ${result.deletedRows} rows, freed ${result.freedMb.format(1)} MB")
                Spacer(Modifier.height(8.dp))
                result.categories.entries.sortedByDescending { it.value }.forEach { (category, count) ->
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(category, style = MaterialTheme.typography.bodySmall)
                        Text("$count rows", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

private fun Double.format(decimals: Int) = "%.${decimals}f".format(this)
```

---

## Backup Management Screen

### `BackupManagementScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.admin.screen

/**
 * Backup management screen.
 *
 * Layout:
 * - "Create Backup" button at top (prominent, with progress)
 * - Backup list (sorted newest first):
 *   Each item: file name | date | size MB | encrypted badge
 *   Actions: Restore (with confirmation dialog) | Delete (with confirmation dialog)
 *
 * Backup file format: `zynta-backup-YYYYMMDD-HHmmss.db.enc`
 * - Stored at: Android: `getExternalFilesDir(null)/backups/`
 *              Desktop: `<appDataDir>/backups/`
 * - Encrypted with AES-256-GCM via `CryptoManager` (`:shared:security`)
 *
 * Restore flow:
 * 1. Show ConfirmRestoreDialog: "This will replace the current database. App will restart."
 * 2. On confirm: call RestoreBackupUseCase → app restarts (platform intent)
 * 3. On failure: show error snackbar
 *
 * Critical: During restore, isRestoring = true prevents all other actions.
 */
@Composable
fun BackupManagementScreen(
    viewModel: AdminViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var restoreTarget by remember { mutableStateOf<Pair<String, String>?>(null) }  // (id, name)
    var deleteTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.handleIntent(AdminIntent.LoadBackups)
        viewModel.effects.collect { effect ->
            when (effect) {
                is AdminEffect.ConfirmRestore -> restoreTarget = effect.backupId to effect.backupName
                is AdminEffect.ConfirmDelete  -> deleteTarget = effect.backupId to effect.backupName
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup Management") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { /* back */ } }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier.padding(padding).padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Create backup button
            CreateBackupCard(
                isBackingUp = state.isBackingUp,
                onCreateBackup = { viewModel.handleIntent(AdminIntent.CreateBackup) }
            )

            Spacer(Modifier.height(16.dp))

            Text("Existing Backups", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (state.backups.isEmpty() && !state.isBackingUp) {
                EmptyStateView(message = "No backups yet. Create your first backup.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                        state.backups.sortedByDescending { it.createdAt },
                        key = { it.id }
                    ) { backup ->
                        BackupCard(
                            backup = backup,
                            onRestore = { viewModel.handleIntent(AdminIntent.RestoreBackup(backup.id)) },
                            onDelete = { viewModel.handleIntent(AdminIntent.DeleteBackup(backup.id)) },
                            isRestoring = state.isRestoring,
                            isEnabled = !state.isBackingUp && !state.isRestoring
                        )
                    }
                }
            }
        }
    }

    // Restore confirmation dialog
    restoreTarget?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            icon = { /* warning icon */ },
            title = { Text("Restore Backup?") },
            text = {
                Text(
                    "This will replace the CURRENT database with the backup:\n\n$name\n\n" +
                    "The application will restart automatically. All unsaved changes will be lost."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.handleIntent(AdminIntent.RestoreBackup(id))
                        restoreTarget = null
                    }
                ) { Text("Restore", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { restoreTarget = null }) { Text("Cancel") } }
        )
    }

    // Delete confirmation dialog
    deleteTarget?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Backup?") },
            text = { Text("Delete backup file:\n\n$name\n\nThis cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.handleIntent(AdminIntent.DeleteBackup(id))
                        deleteTarget = null
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CreateBackupCard(
    isBackingUp: Boolean,
    onCreateBackup: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZyntaCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Database Backup", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Creates an AES-256 encrypted backup of the local database.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            ZyntaButton(
                text = if (isBackingUp) "Creating Backup…" else "Create Backup",
                isLoading = isBackingUp,
                enabled = !isBackingUp,
                onClick = onCreateBackup,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BackupCard(
    backup: com.zyntasolutions.zyntapos.domain.model.BackupInfo,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    isRestoring: Boolean,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    ZyntaCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text(backup.fileName, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "${formatDateTime(backup.createdAt)} • ${backup.sizeMb.format(1)} MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (backup.isEncrypted) {
                    ZyntaStatusBadge(label = "🔒 Encrypted", color = Color(0xFF16A34A))
                }
            }

            BackupStatusIndicator(backup.status)

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ZyntaOutlinedButton(
                    text = if (isRestoring) "Restoring…" else "Restore",
                    onClick = onRestore,
                    enabled = isEnabled && backup.status == BackupStatus.SUCCESS,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDelete,
                    enabled = isEnabled
                ) { /* delete icon */ }
            }
        }
    }
}

@Composable
private fun BackupStatusIndicator(
    status: com.zyntasolutions.zyntapos.domain.model.BackupStatus,
    modifier: Modifier = Modifier
) {
    val (label, color) = when (status) {
        BackupStatus.CREATING   -> "Creating…" to Color(0xFF3B82F6)
        BackupStatus.SUCCESS    -> "Ready" to Color(0xFF16A34A)
        BackupStatus.FAILED     -> "Failed" to Color(0xFFEF4444)
        BackupStatus.RESTORING  -> "Restoring…" to Color(0xFFF59E0B)
    }
    ZyntaStatusBadge(label = label, color = color, modifier = modifier)
}
```

---

## Tasks

- [ ] **14.1** Implement `DatabaseManagementScreen.kt` with `DatabaseOverviewCard`, `TableStatsRow`, `MaintenanceActionsCard`
- [ ] **14.2** Implement `PurgeResultDialog` showing per-category deleted row counts
- [ ] **14.3** Implement `BackupManagementScreen.kt` with `CreateBackupCard`, `BackupCard`, and `BackupStatusIndicator`
- [ ] **14.4** Implement restore confirmation dialog with critical warning ("database will be replaced")
- [ ] **14.5** Implement delete confirmation dialog for backup files
- [ ] **14.6** Wire `DatabaseManagement` and `BackupManagement` routes in the admin nav sub-graph
- [ ] **14.7** Update `AdminDashboardScreen` nav cards to route to these screens (Sprint 13)
- [ ] **14.8** Write `BackupViewModelTest` — test create backup success/failure, confirm restore/delete effects dispatched
- [ ] **14.9** Write `DatabaseManagementViewModelTest` — test vacuum, purge, stats loading
- [ ] **14.10** Verify: `./gradlew :composeApp:feature:admin:assemble && ./gradlew :composeApp:feature:admin:test`

---

## Verification

```bash
./gradlew :composeApp:feature:admin:assemble
./gradlew :composeApp:feature:admin:test
./gradlew :composeApp:feature:admin:detekt
```

---

## Definition of Done

- [ ] `DatabaseManagementScreen` shows DB overview, table stats, VACUUM and Purge actions
- [ ] `BackupManagementScreen` lists backups with restore/delete confirmations
- [ ] Restore dialog includes critical warning about database replacement
- [ ] `BackupCard` correctly shows status (Creating/Ready/Failed/Restoring)
- [ ] Navigation wired from `AdminDashboardScreen`
- [ ] Tests for backup and database management flows pass
- [ ] Commit: `feat(admin): implement database management and backup/restore screens`
