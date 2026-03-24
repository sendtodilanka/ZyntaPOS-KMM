package com.zyntasolutions.zyntapos.feature.admin

import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.usecase.admin.CreateBackupUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.DeleteBackupUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.GetBackupsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.GetDatabaseStatsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.GetConflictCountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.GetSystemHealthUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.GetUnresolvedConflictsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.ResolveConflictUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.RestoreBackupUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.VerifyAuditIntegrityUseCase
import com.zyntasolutions.zyntapos.domain.model.SyncConflict
import com.zyntasolutions.zyntapos.domain.usecase.admin.PurgeExpiredDataUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.VacuumDatabaseUseCase
import com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Root ViewModel for the Admin feature (Sprints 13–15).
 *
 * ### Reactive bindings (init)
 * - [GetBackupsUseCase] — backup list observed reactively
 * - [AuditRepository.observeAll] — full audit log observed reactively
 *
 * ### Suspend operations (handleIntent)
 * - System health refresh, DB stats, vacuum, purge, backup create/restore/delete
 */
class AdminViewModel(
    private val getSystemHealthUseCase: GetSystemHealthUseCase,
    private val getDatabaseStatsUseCase: GetDatabaseStatsUseCase,
    private val vacuumDatabaseUseCase: VacuumDatabaseUseCase,
    private val purgeExpiredDataUseCase: PurgeExpiredDataUseCase,
    private val getBackupsUseCase: GetBackupsUseCase,
    private val createBackupUseCase: CreateBackupUseCase,
    private val restoreBackupUseCase: RestoreBackupUseCase,
    private val deleteBackupUseCase: DeleteBackupUseCase,
    private val auditRepository: AuditRepository,
    private val verifyAuditIntegrityUseCase: VerifyAuditIntegrityUseCase,
    private val auditLogger: SecurityAuditLogger,
    private val authRepository: AuthRepository,
    private val analytics: AnalyticsTracker,
    // Conflict resolution (C6.1 Item 6)
    private val getUnresolvedConflictsUseCase: GetUnresolvedConflictsUseCase,
    private val resolveConflictUseCase: ResolveConflictUseCase,
    private val getConflictCountUseCase: GetConflictCountUseCase,
) : BaseViewModel<AdminState, AdminIntent, AdminEffect>(AdminState()) {

    private var currentUserId: String = "unknown"

    init {
        analytics.logScreenView("Admin", "AdminViewModel")
        viewModelScope.launch {
            currentUserId = authRepository.getSession().first()?.id ?: "unknown"
        }
        observeBackups()
        observeAuditLog()
        observeConflicts()
        // Auto-load health on start
        viewModelScope.let {
            dispatch(AdminIntent.RefreshSystemHealth)
            dispatch(AdminIntent.RefreshDatabaseStats)
            dispatch(AdminIntent.VerifyIntegrity)
        }
    }

    private fun observeBackups() {
        getBackupsUseCase()
            .onEach { list -> updateState { copy(backups = list) } }
            .launchIn(viewModelScope)
    }

    private fun observeAuditLog() {
        auditRepository.observeAll()
            .onEach { entries ->
                val totalPages = ((entries.size + 49) / 50).coerceAtLeast(1)
                updateState {
                    copy(
                        auditEntries = entries,
                        auditTotalPages = totalPages,
                        auditPage = auditPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0)),
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override suspend fun handleIntent(intent: AdminIntent) {
        when (intent) {
            is AdminIntent.SwitchTab -> updateState { copy(activeTab = intent.tab) }

            // System Health
            AdminIntent.RefreshSystemHealth -> refreshSystemHealth()
            AdminIntent.RefreshDatabaseStats -> refreshDatabaseStats()
            AdminIntent.RunVacuum -> runVacuum()
            is AdminIntent.PurgeExpiredData -> purgeExpiredData(intent.olderThanDays)

            // Backups
            AdminIntent.CreateBackup -> createBackup()
            is AdminIntent.RestoreBackup -> updateState { copy(showRestoreConfirm = intent.backup) }
            AdminIntent.ConfirmRestore -> confirmRestore()
            AdminIntent.CancelRestore -> updateState { copy(showRestoreConfirm = null) }
            is AdminIntent.DeleteBackup -> updateState { copy(showDeleteConfirm = intent.backup) }
            AdminIntent.ConfirmDelete -> confirmDelete()
            AdminIntent.CancelDelete -> updateState { copy(showDeleteConfirm = null) }

            // Audit Log
            AdminIntent.RefreshAuditLog -> Unit // reactive — driven by observeAuditLog()
            is AdminIntent.FilterAuditByUser -> updateState { copy(auditUserFilter = intent.userId, auditPage = 0) }
            is AdminIntent.FilterAuditByEventType -> updateState { copy(auditEventTypeFilter = intent.eventType, auditPage = 0) }
            is AdminIntent.FilterAuditByRole -> updateState { copy(auditRoleFilter = intent.role, auditPage = 0) }
            is AdminIntent.FilterAuditBySuccess -> updateState { copy(auditSuccessFilter = intent.success, auditPage = 0) }
            is AdminIntent.FilterAuditByDateRange -> updateState { copy(auditDateFrom = intent.from, auditDateTo = intent.to, auditPage = 0) }
            AdminIntent.ExportAuditLogCsv -> exportAuditLogCsv()
            AdminIntent.ExportAuditLogJson -> exportAuditLogJson()
            AdminIntent.VerifyIntegrity -> verifyIntegrity()
            AdminIntent.NextAuditPage -> updateState { copy(auditPage = (auditPage + 1).coerceAtMost(auditTotalPages - 1)) }
            AdminIntent.PrevAuditPage -> updateState { copy(auditPage = (auditPage - 1).coerceAtLeast(0)) }

            // Conflicts
            AdminIntent.RefreshConflicts -> Unit // reactive — driven by observeConflicts()
            is AdminIntent.FilterConflictsByEntityType -> updateState { copy(conflictEntityTypeFilter = intent.entityType) }
            is AdminIntent.SelectConflict -> updateState { copy(selectedConflict = intent.conflict) }
            AdminIntent.DismissConflictDetail -> updateState { copy(selectedConflict = null) }
            is AdminIntent.ResolveConflictKeepLocal -> resolveConflict(intent.conflictId, SyncConflict.Resolution.LOCAL)
            is AdminIntent.ResolveConflictAcceptServer -> resolveConflict(intent.conflictId, SyncConflict.Resolution.SERVER)
            is AdminIntent.ResolveConflictManual -> resolveConflictManual(intent.conflictId, intent.value)

            // UI
            AdminIntent.DismissError -> updateState { copy(error = null) }
            AdminIntent.DismissSuccess -> updateState { copy(successMessage = null) }
        }
    }

    // ── System Health ─────────────────────────────────────────────────────

    private suspend fun refreshSystemHealth() {
        updateState { copy(isLoading = true) }
        when (val result = getSystemHealthUseCase()) {
            is Result.Success -> updateState { copy(isLoading = false, systemHealth = result.data) }
            is Result.Error -> updateState { copy(isLoading = false, error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }

    private suspend fun refreshDatabaseStats() {
        when (val result = getDatabaseStatsUseCase()) {
            is Result.Success -> updateState { copy(databaseStats = result.data) }
            is Result.Error -> updateState { copy(error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }

    private suspend fun runVacuum() {
        updateState { copy(isLoading = true) }
        when (val result = vacuumDatabaseUseCase()) {
            is Result.Success -> {
                updateState {
                    copy(
                        isLoading = false,
                        lastVacuumResult = result.data,
                        successMessage = "Vacuum completed: ${result.data.bytesFreed / 1024} KB freed.",
                    )
                }
                refreshDatabaseStats()
            }
            is Result.Error -> updateState { copy(isLoading = false, error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }

    private suspend fun purgeExpiredData(olderThanDays: Int) {
        updateState { copy(isLoading = true) }
        val cutoff = Clock.System.now().toEpochMilliseconds() - olderThanDays.toLong() * 24 * 60 * 60 * 1000L
        when (val result = purgeExpiredDataUseCase(cutoff)) {
            is Result.Success -> {
                updateState {
                    copy(
                        isLoading = false,
                        lastPurgeResult = result.data,
                        successMessage = "Purge complete: ${result.data.bytesFreed} records removed.",
                    )
                }
                auditLogger.logDataPurged(currentUserId, result.data.bytesFreed)
            }
            is Result.Error -> updateState { copy(isLoading = false, error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }

    // ── Audit CSV Export ──────────────────────────────────────────────────────

    private suspend fun exportAuditLogCsv() {
        val s = currentState
        // Apply the same filters as the UI to export only the visible filtered set
        val filtered = s.auditEntries
            .filter { e ->
                (s.auditUserFilter.isBlank() || e.userId.contains(s.auditUserFilter, ignoreCase = true)) &&
                (s.auditEventTypeFilter == null || e.eventType == s.auditEventTypeFilter) &&
                (s.auditRoleFilter == null || e.userRole == s.auditRoleFilter) &&
                (s.auditSuccessFilter == null || e.success == s.auditSuccessFilter) &&
                (s.auditDateFrom == null || e.createdAt >= s.auditDateFrom) &&
                (s.auditDateTo == null || e.createdAt <= s.auditDateTo)
            }

        if (filtered.isEmpty()) {
            sendEffect(AdminEffect.ShowSnackbar("No entries to export."))
            return
        }

        runCatching {
            val sb = StringBuilder()
            sb.appendLine("id,eventType,userId,userName,userRole,deviceId,entityType,entityId,success,ipAddress,createdAt")
            for (e in filtered) {
                sb.appendLine(
                    listOf(
                        e.id.csvCell(),
                        e.eventType.name.csvCell(),
                        e.userId.csvCell(),
                        e.userName.csvCell(),
                        e.userRole?.name.orEmpty().csvCell(),
                        e.deviceId.csvCell(),
                        e.entityType.orEmpty().csvCell(),
                        e.entityId.orEmpty().csvCell(),
                        e.success.toString().csvCell(),
                        e.ipAddress.orEmpty().csvCell(),
                        e.createdAt.toEpochMilliseconds().toString().csvCell(),
                    ).joinToString(",")
                )
            }
            val fileName = "audit_log_${Clock.System.now().toEpochMilliseconds()}.csv"
            sendEffect(AdminEffect.ShareAuditExport(content = sb.toString(), fileName = fileName, format = "csv"))
        }.onFailure {
            sendEffect(AdminEffect.ShowSnackbar("Export failed: ${it.message}"))
        }
    }

    /** Wraps a cell value in double-quotes and escapes internal quotes (RFC 4180). */
    private fun String.csvCell(): String = "\"${replace("\"", "\"\"")}\""

    // ── Audit JSON Export ───────────────────────────────────────────────────────

    private suspend fun exportAuditLogJson() {
        val s = currentState
        val filtered = s.auditEntries
            .filter { e ->
                (s.auditUserFilter.isBlank() || e.userId.contains(s.auditUserFilter, ignoreCase = true)) &&
                (s.auditEventTypeFilter == null || e.eventType == s.auditEventTypeFilter) &&
                (s.auditRoleFilter == null || e.userRole == s.auditRoleFilter) &&
                (s.auditSuccessFilter == null || e.success == s.auditSuccessFilter) &&
                (s.auditDateFrom == null || e.createdAt >= s.auditDateFrom) &&
                (s.auditDateTo == null || e.createdAt <= s.auditDateTo)
            }

        if (filtered.isEmpty()) {
            sendEffect(AdminEffect.ShowSnackbar("No entries to export."))
            return
        }

        runCatching {
            val sb = StringBuilder()
            sb.append("[\n")
            filtered.forEachIndexed { index, e ->
                sb.append("  {\n")
                sb.append("    \"id\": ${e.id.jsonString()},\n")
                sb.append("    \"eventType\": ${e.eventType.name.jsonString()},\n")
                sb.append("    \"userId\": ${e.userId.jsonString()},\n")
                sb.append("    \"userName\": ${e.userName.jsonString()},\n")
                sb.append("    \"userRole\": ${e.userRole?.name?.jsonString() ?: "null"},\n")
                sb.append("    \"deviceId\": ${e.deviceId.jsonString()},\n")
                sb.append("    \"entityType\": ${e.entityType?.jsonString() ?: "null"},\n")
                sb.append("    \"entityId\": ${e.entityId?.jsonString() ?: "null"},\n")
                sb.append("    \"success\": ${e.success},\n")
                sb.append("    \"ipAddress\": ${e.ipAddress?.jsonString() ?: "null"},\n")
                sb.append("    \"payload\": ${e.payload.jsonString()},\n")
                sb.append("    \"previousValue\": ${e.previousValue?.jsonString() ?: "null"},\n")
                sb.append("    \"newValue\": ${e.newValue?.jsonString() ?: "null"},\n")
                sb.append("    \"createdAt\": ${e.createdAt.toEpochMilliseconds()}\n")
                sb.append("  }")
                if (index < filtered.size - 1) sb.append(",")
                sb.append("\n")
            }
            sb.append("]")
            val fileName = "audit_log_${Clock.System.now().toEpochMilliseconds()}.json"
            sendEffect(AdminEffect.ShareAuditExport(content = sb.toString(), fileName = fileName, format = "json"))
        }.onFailure {
            sendEffect(AdminEffect.ShowSnackbar("Export failed: ${it.message}"))
        }
    }

    /** Escapes a string value for JSON output (handles backslash, quotes, control chars). */
    private fun String.jsonString(): String {
        val escaped = this
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    // ── Audit Integrity ───────────────────────────────────────────────────────

    private suspend fun verifyIntegrity() {
        updateState { copy(isVerifyingIntegrity = true) }
        runCatching {
            val report = verifyAuditIntegrityUseCase()
            updateState { copy(isVerifyingIntegrity = false, integrityReport = report) }
        }.onFailure {
            updateState { copy(isVerifyingIntegrity = false) }
        }
    }

    // ── Backups ───────────────────────────────────────────────────────────

    private suspend fun createBackup() {
        updateState { copy(isCreatingBackup = true) }
        val now = Clock.System.now().toEpochMilliseconds()
        val backupId = IdGenerator.newId()
        when (val result = createBackupUseCase(backupId, now)) {
            is Result.Success -> {
                updateState { copy(isCreatingBackup = false, successMessage = "Backup created successfully.") }
                auditLogger.logBackupCreated(currentUserId, backupId)
            }
            is Result.Error -> {
                updateState { copy(isCreatingBackup = false, error = result.exception.message) }
            }
            is Result.Loading -> Unit
        }
    }

    private suspend fun confirmRestore() {
        val backup = currentState.showRestoreConfirm ?: return
        updateState { copy(isLoading = true, showRestoreConfirm = null) }
        when (val result = restoreBackupUseCase(backup.id)) {
            is Result.Success -> {
                updateState { copy(isLoading = false, successMessage = "Restore complete. Restart required.") }
                auditLogger.logBackupRestored(currentUserId, backup.id)
                sendEffect(AdminEffect.RestartRequired)
            }
            is Result.Error -> updateState { copy(isLoading = false, error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }

    private suspend fun confirmDelete() {
        val backup = currentState.showDeleteConfirm ?: return
        updateState { copy(isLoading = true, showDeleteConfirm = null) }
        when (val result = deleteBackupUseCase(backup.id)) {
            is Result.Success -> updateState { copy(isLoading = false, successMessage = "Backup deleted.") }
            is Result.Error -> updateState { copy(isLoading = false, error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }

    // ── Conflicts (C6.1 Item 6) ──────────────────────────────────────────

    private fun observeConflicts() {
        getUnresolvedConflictsUseCase()
            .onEach { conflicts ->
                updateState { copy(conflicts = conflicts, unresolvedConflictCount = conflicts.size) }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun resolveConflict(conflictId: String, resolution: SyncConflict.Resolution) {
        val conflict = state.value.selectedConflict ?: return
        val value = when (resolution) {
            SyncConflict.Resolution.LOCAL -> conflict.localValue ?: ""
            SyncConflict.Resolution.SERVER -> conflict.serverValue ?: ""
            else -> ""
        }
        when (val result = resolveConflictUseCase(conflictId, resolution, value)) {
            is Result.Success -> updateState {
                copy(selectedConflict = null, successMessage = "Conflict resolved (${resolution.name})")
            }
            is Result.Error -> updateState { copy(error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }

    private suspend fun resolveConflictManual(conflictId: String, value: String) {
        when (val result = resolveConflictUseCase(conflictId, SyncConflict.Resolution.MANUAL, value)) {
            is Result.Success -> updateState {
                copy(selectedConflict = null, successMessage = "Conflict resolved (MANUAL)")
            }
            is Result.Error -> updateState { copy(error = result.exception.message) }
            is Result.Loading -> Unit
        }
    }
}
