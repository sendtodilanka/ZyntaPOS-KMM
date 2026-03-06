package com.zyntasolutions.zyntapos.feature.admin

import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.usecase.admin.CreateBackupUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.DeleteBackupUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.GetBackupsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.GetDatabaseStatsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.GetSystemHealthUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.RestoreBackupUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.VerifyAuditIntegrityUseCase
import com.zyntasolutions.zyntapos.domain.repository.SystemRepository
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
    private val systemRepository: SystemRepository,
    private val getBackupsUseCase: GetBackupsUseCase,
    private val createBackupUseCase: CreateBackupUseCase,
    private val restoreBackupUseCase: RestoreBackupUseCase,
    private val deleteBackupUseCase: DeleteBackupUseCase,
    private val auditRepository: AuditRepository,
    private val verifyAuditIntegrityUseCase: VerifyAuditIntegrityUseCase,
    private val auditLogger: SecurityAuditLogger,
    private val authRepository: AuthRepository,
) : BaseViewModel<AdminState, AdminIntent, AdminEffect>(AdminState()) {

    private var currentUserId: String = "unknown"

    init {
        viewModelScope.launch {
            currentUserId = authRepository.getSession().first()?.id ?: "unknown"
        }
        observeBackups()
        observeAuditLog()
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
            is AdminIntent.FilterAuditBySuccess -> updateState { copy(auditSuccessFilter = intent.success, auditPage = 0) }
            is AdminIntent.FilterAuditByDateRange -> updateState { copy(auditDateFrom = intent.from, auditDateTo = intent.to, auditPage = 0) }
            AdminIntent.ExportAuditLogCsv -> exportAuditLogCsv()
            AdminIntent.VerifyIntegrity -> verifyIntegrity()
            AdminIntent.NextAuditPage -> updateState { copy(auditPage = (auditPage + 1).coerceAtMost(auditTotalPages - 1)) }
            AdminIntent.PrevAuditPage -> updateState { copy(auditPage = (auditPage - 1).coerceAtLeast(0)) }

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
        when (val result = systemRepository.vacuumDatabase()) {
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
        val cutoff = Clock.System.now().toEpochMilliseconds() - olderThanDays * 24 * 60 * 60 * 1000L
        when (val result = systemRepository.purgeExpiredData(cutoff)) {
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
            sendEffect(AdminEffect.ShareCsvExport(csvContent = sb.toString(), fileName = fileName))
        }.onFailure {
            sendEffect(AdminEffect.ShowSnackbar("Export failed: ${it.message}"))
        }
    }

    /** Wraps a cell value in double-quotes and escapes internal quotes (RFC 4180). */
    private fun String.csvCell(): String = "\"${replace("\"", "\"\"")}\""

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
}
