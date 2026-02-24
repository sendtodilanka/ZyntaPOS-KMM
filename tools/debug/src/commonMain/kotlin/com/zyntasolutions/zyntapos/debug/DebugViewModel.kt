package com.zyntasolutions.zyntapos.debug

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.debug.actions.AuthActionHandler
import com.zyntasolutions.zyntapos.debug.actions.DatabaseActionHandler
import com.zyntasolutions.zyntapos.debug.actions.DiagnosticsActionHandler
import com.zyntasolutions.zyntapos.debug.actions.NetworkActionHandler
import com.zyntasolutions.zyntapos.debug.actions.SeedActionHandler
import com.zyntasolutions.zyntapos.debug.mvi.DebugEffect
import com.zyntasolutions.zyntapos.debug.mvi.DebugIntent
import com.zyntasolutions.zyntapos.debug.mvi.DebugState
import com.zyntasolutions.zyntapos.debug.mvi.DebugTab
import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.AuditEventType
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.datetime.Clock

/**
 * MVI ViewModel for the Debug Console.
 *
 * Extends [BaseViewModel] per ADR-001. Each intent is handled by a dedicated
 * private suspend function to keep [handleIntent] readable.
 *
 * ### Security invariants enforced here:
 * - [DebugIntent.SetAdminCredentials] password is forwarded to the use case and
 *   immediately discarded; it is NEVER stored in [DebugState].
 * - Destructive intents ([DebugIntent.ConfirmResetDatabase] etc.) are only
 *   reachable after the [DebugState.pendingDestructiveIntent] confirm dialog flow.
 * - Every mutating action writes an [AuditEntry] via [AuditRepository].
 */
class DebugViewModel(
    private val seedHandler: SeedActionHandler,
    private val databaseHandler: DatabaseActionHandler,
    private val authHandler: AuthActionHandler,
    private val networkHandler: NetworkActionHandler,
    private val diagnosticsHandler: DiagnosticsActionHandler,
    private val auditRepository: AuditRepository,
) : BaseViewModel<DebugState, DebugIntent, DebugEffect>(DebugState()) {

    init {
        dispatch(DebugIntent.LoadInitialData)
    }

    override suspend fun handleIntent(intent: DebugIntent) {
        when (intent) {

            // ── General ──────────────────────────────────────────────────────
            is DebugIntent.SelectTab -> {
                updateState { copy(activeTab = intent.tab) }
                // Lazy-load tab data on first activation
                when (intent.tab) {
                    DebugTab.Database    -> dispatch(DebugIntent.LoadTableCounts)
                    DebugTab.Auth        -> dispatch(DebugIntent.LoadUsers)
                    DebugTab.Network     -> dispatch(DebugIntent.LoadSyncQueueDepth)
                    DebugTab.Diagnostics -> {
                        dispatch(DebugIntent.LoadAuditLog)
                        dispatch(DebugIntent.LoadSystemHealth)
                    }
                    else -> Unit
                }
            }

            is DebugIntent.DismissActionResult -> updateState { copy(actionResult = null) }
            is DebugIntent.DismissDestructiveDialog -> updateState { copy(pendingDestructiveIntent = null) }

            is DebugIntent.LoadInitialData -> loadInitialData()

            // ── Seeds ─────────────────────────────────────────────────────────
            is DebugIntent.SelectSeedProfile -> updateState { copy(selectedProfile = intent.profile) }
            is DebugIntent.RunSeedProfile    -> runSeedProfile()
            is DebugIntent.ShowAdminSetupDialog -> updateState { copy(showAdminSetupDialog = true) }
            is DebugIntent.DismissAdminSetupDialog -> updateState { copy(showAdminSetupDialog = false) }
            is DebugIntent.SetAdminCredentials -> setAdminCredentials(intent.email, intent.password)

            // Seeds — destructive gate
            is DebugIntent.RequestClearSeedData ->
                updateState { copy(pendingDestructiveIntent = DebugIntent.ConfirmClearSeedData) }
            is DebugIntent.ConfirmClearSeedData -> confirmClearSeedData()

            // ── Database ──────────────────────────────────────────────────────
            is DebugIntent.LoadTableCounts -> loadTableCounts()
            is DebugIntent.VacuumDatabase  -> vacuumDatabase()
            is DebugIntent.ExportDatabase  -> exportDatabase()

            // Database — destructive gate
            is DebugIntent.RequestResetDatabase ->
                updateState { copy(pendingDestructiveIntent = DebugIntent.ConfirmResetDatabase) }
            is DebugIntent.ConfirmResetDatabase -> confirmResetDatabase()
            is DebugIntent.RequestClearTable ->
                updateState { copy(pendingDestructiveIntent = DebugIntent.ConfirmClearTable(intent.tableName)) }
            is DebugIntent.ConfirmClearTable -> showActionResult("Clear individual table not yet supported", isError = true)

            // ── Auth ──────────────────────────────────────────────────────────
            is DebugIntent.LoadUsers        -> loadUsers()
            is DebugIntent.DeactivateUser   -> deactivateUser(intent.userId)
            is DebugIntent.ForceTokenExpiry -> forceTokenExpiry()

            // Auth — destructive gate
            is DebugIntent.RequestClearSession ->
                updateState { copy(pendingDestructiveIntent = DebugIntent.ConfirmClearSession) }
            is DebugIntent.ConfirmClearSession -> confirmClearSession()

            // ── Network ───────────────────────────────────────────────────────
            is DebugIntent.SetOfflineModeForced -> updateState { copy(isOfflineModeForced = intent.forced) }
            is DebugIntent.ForceSyncNow         -> forceSyncNow()
            is DebugIntent.LoadSyncQueueDepth   -> loadSyncQueueDepth()

            // Network — destructive gate
            is DebugIntent.RequestClearSyncQueue ->
                updateState { copy(pendingDestructiveIntent = DebugIntent.ConfirmClearSyncQueue) }
            is DebugIntent.ConfirmClearSyncQueue -> confirmClearSyncQueue()

            // ── Diagnostics ───────────────────────────────────────────────────
            is DebugIntent.LoadAuditLog     -> loadAuditLog()
            is DebugIntent.LoadSystemHealth -> loadSystemHealth()
            is DebugIntent.ExportLogs       -> exportLogs()

            // ── UI/UX ─────────────────────────────────────────────────────────
            is DebugIntent.SetThemeOverride -> updateState { copy(themeOverride = intent.theme) }
            is DebugIntent.SetFontScale     -> updateState { copy(fontScaleOverride = intent.scale) }
        }
    }

    // ── Seeds ──────────────────────────────────────────────────────────────────

    private suspend fun loadInitialData() {
        val user = authHandler.getCurrentUser()
        updateState {
            copy(
                currentUserEmail = user?.email,
                currentUserRole = user?.role?.name,
                currentUserId = user?.id,
                hasPinConfigured = user?.pinHash != null,
            )
        }
    }

    private suspend fun runSeedProfile() {
        updateState { copy(isLoading = true) }
        val profile = currentState.selectedProfile
        when (val result = seedHandler.runProfile(profile)) {
            is Result.Success -> {
                updateState { copy(isLoading = false, seedSummary = result.data) }
                auditDebugAction("seed_run", "profile=${profile.name}")
                showActionResult(
                    "Seeded: ${result.data.totalInserted} inserted, " +
                        "${result.data.totalSkipped} skipped, ${result.data.totalFailed} failed",
                    isError = !result.data.isSuccess,
                )
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                showActionResult("Seed failed: ${result.exception.message}", isError = true)
            }
            is Result.Loading -> Unit
        }
    }

    private suspend fun confirmClearSeedData() {
        updateState { copy(pendingDestructiveIntent = null, isLoading = true) }
        // Seed data is identified by "seed-" ID prefix; clearing requires a DB reset.
        // For Phase 1, inform user to use Database > Reset Database instead.
        updateState { copy(isLoading = false) }
        showActionResult("Use Database → Reset Database to clear all data including seed records", isError = false)
    }

    private suspend fun setAdminCredentials(email: String, password: String) {
        // Password variable is used directly and goes out of scope immediately after.
        updateState { copy(isLoading = true, showAdminSetupDialog = false) }
        when (val result = authHandler.createAdminUser(email, "Admin", password)) {
            is Result.Success -> {
                updateState { copy(isLoading = false) }
                auditDebugAction("admin_user_created", "email=${email}")
                showActionResult("Admin account created: $email", isError = false)
                // Reload user list to reflect new admin
                loadUsers()
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                showActionResult("Admin setup failed: ${result.exception.message}", isError = true)
            }
            is Result.Loading -> Unit
        }
        // password reference goes out of scope here — never stored
    }

    // ── Database ───────────────────────────────────────────────────────────────

    private suspend fun loadTableCounts() {
        when (val result = databaseHandler.getTableRowCounts()) {
            is Result.Success -> updateState { copy(tableRowCounts = result.data) }
            is Result.Error   -> showActionResult("Could not read table counts: ${result.exception.message}", isError = true)
            is Result.Loading -> Unit
        }
    }

    private suspend fun vacuumDatabase() {
        updateState { copy(isLoading = true) }
        when (val result = databaseHandler.vacuum()) {
            is Result.Success -> {
                updateState { copy(isLoading = false) }
                auditDebugAction("db_vacuum", "")
                showActionResult("VACUUM completed successfully", isError = false)
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                showActionResult("VACUUM failed: ${result.exception.message}", isError = true)
            }
            is Result.Loading -> Unit
        }
    }

    private suspend fun exportDatabase() {
        updateState { copy(isLoading = true) }
        updateState { copy(isLoading = false) }
        showActionResult("Database export: use device file manager to access the DB file directly", isError = false)
    }

    private suspend fun confirmResetDatabase() {
        updateState { copy(pendingDestructiveIntent = null, isLoading = true) }
        when (val result = databaseHandler.resetDatabase()) {
            is Result.Success -> {
                updateState { copy(isLoading = false, tableRowCounts = emptyMap(), seedSummary = null) }
                auditDebugAction("db_reset", "")
                showActionResult("Database reset. All data has been erased.", isError = false)
                sendEffect(DebugEffect.NavigateUp) // Force re-login after full reset
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                showActionResult("Reset failed: ${result.exception.message}", isError = true)
            }
            is Result.Loading -> Unit
        }
    }

    private suspend fun loadSystemHealth() {
        when (val result = databaseHandler.getDatabaseFileSizeKb()) {
            is Result.Success -> updateState { copy(dbFileSizeKb = result.data) }
            is Result.Error   -> Unit // non-critical
            is Result.Loading -> Unit
        }
    }

    // ── Auth ───────────────────────────────────────────────────────────────────

    private suspend fun loadUsers() {
        when (val result = authHandler.getAllUsers()) {
            is Result.Success -> updateState { copy(allUsers = result.data) }
            is Result.Error   -> showActionResult("Failed to load users: ${result.exception.message}", isError = true)
            is Result.Loading -> Unit
        }
    }

    private suspend fun deactivateUser(userId: String) {
        when (val result = authHandler.deactivateUser(userId)) {
            is Result.Success -> {
                auditDebugAction("user_deactivated", "userId=$userId")
                showActionResult("User deactivated", isError = false)
                loadUsers()
            }
            is Result.Error -> showActionResult("Deactivation failed: ${result.exception.message}", isError = true)
            is Result.Loading -> Unit
        }
    }

    private fun forceTokenExpiry() {
        // Token management is handled by SecureKeyStorage; this is a UI-level note.
        showActionResult("Force token expiry: log out and log back in to trigger token refresh", isError = false)
    }

    private suspend fun confirmClearSession() {
        updateState { copy(pendingDestructiveIntent = null, isLoading = true) }
        when (val result = authHandler.clearSession()) {
            is Result.Success -> {
                updateState { copy(isLoading = false, currentUserEmail = null, currentUserRole = null) }
                auditDebugAction("session_cleared", "")
                sendEffect(DebugEffect.NavigateUp)
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                showActionResult("Session clear failed: ${result.exception.message}", isError = true)
            }
            is Result.Loading -> Unit
        }
    }

    // ── Network ────────────────────────────────────────────────────────────────

    private suspend fun loadSyncQueueDepth() {
        when (val result = networkHandler.getPendingOperations()) {
            is Result.Success -> updateState { copy(pendingOpsCount = result.data.size) }
            is Result.Error   -> Unit // non-critical
            is Result.Loading -> Unit
        }
    }

    private suspend fun forceSyncNow() {
        updateState { copy(isLoading = true) }
        when (val result = networkHandler.forceSyncNow()) {
            is Result.Success -> {
                updateState { copy(isLoading = false) }
                showActionResult("Sync triggered successfully", isError = false)
                loadSyncQueueDepth()
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                showActionResult("Sync failed: ${result.exception.message}", isError = true)
            }
            is Result.Loading -> Unit
        }
    }

    private suspend fun confirmClearSyncQueue() {
        updateState { copy(pendingDestructiveIntent = null, isLoading = true) }
        when (val result = networkHandler.clearSyncQueue()) {
            is Result.Success -> {
                updateState { copy(isLoading = false, pendingOpsCount = 0) }
                auditDebugAction("sync_queue_cleared", "")
                showActionResult("Sync queue cleared", isError = false)
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                showActionResult("Clear sync queue failed: ${result.exception.message}", isError = true)
            }
            is Result.Loading -> Unit
        }
    }

    // ── Diagnostics ────────────────────────────────────────────────────────────

    private suspend fun loadAuditLog() {
        when (val result = diagnosticsHandler.getAuditLog()) {
            is Result.Success -> updateState { copy(auditEntries = result.data) }
            is Result.Error   -> showActionResult("Failed to load audit log: ${result.exception.message}", isError = true)
            is Result.Loading -> Unit
        }
        updateState { copy(logLines = diagnosticsHandler.getLogLines()) }
    }

    private fun exportLogs() {
        showActionResult("Log export: use logcat (Android) or terminal output (Desktop) to capture logs", isError = false)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun showActionResult(message: String, isError: Boolean) {
        updateState { copy(actionResult = DebugState.ActionResult(message, isError)) }
    }

    private suspend fun auditDebugAction(action: String, payload: String) {
        try {
            val userId = currentState.currentUserId ?: "debug"
            auditRepository.insert(
                AuditEntry(
                    id         = "debug-audit-${Clock.System.now().epochSeconds}-$action",
                    eventType  = AuditEventType.SETTINGS_CHANGED,
                    userId     = userId,
                    deviceId   = "debug-console",
                    payload    = """{"debug_action":"$action","detail":"$payload"}""",
                    success    = true,
                    createdAt  = Clock.System.now(),
                )
            )
        } catch (_: Exception) {
            // Audit failures must never break debug tool functionality
        }
    }
}
