package com.zyntasolutions.zyntapos.feature.admin

import com.zyntasolutions.zyntapos.domain.model.AuditEventType
import com.zyntasolutions.zyntapos.domain.model.BackupInfo
import kotlinx.datetime.Instant

/**
 * All user-triggered and system-driven events for the Admin feature.
 *
 * Dispatched by Composable screens → [AdminViewModel.handleIntent].
 *
 * ### Categories
 * - **Tab:** [SwitchTab]
 * - **System Health:** [RefreshSystemHealth], [RefreshDatabaseStats],
 *   [RunVacuum], [PurgeExpiredData]
 * - **Backups:** [CreateBackup], [RestoreBackup], [ConfirmRestore], [CancelRestore],
 *   [DeleteBackup], [ConfirmDelete], [CancelDelete]
 * - **Audit Log:** [RefreshAuditLog], [FilterAuditByUser]
 * - **UI:** [DismissError], [DismissSuccess]
 */
sealed interface AdminIntent {

    // ── Tab Navigation ─────────────────────────────────────────────────────
    data class SwitchTab(val tab: AdminTab) : AdminIntent

    // ── System Health ──────────────────────────────────────────────────────
    data object RefreshSystemHealth : AdminIntent
    data object RefreshDatabaseStats : AdminIntent
    data object RunVacuum : AdminIntent
    /** @param olderThanDays Purge soft-deleted records older than this many days. */
    data class PurgeExpiredData(val olderThanDays: Int = 30) : AdminIntent

    // ── Backups ────────────────────────────────────────────────────────────
    data object CreateBackup : AdminIntent
    /** Opens the restore confirmation dialog. */
    data class RestoreBackup(val backup: BackupInfo) : AdminIntent
    data object ConfirmRestore : AdminIntent
    data object CancelRestore : AdminIntent
    /** Opens the delete confirmation dialog. */
    data class DeleteBackup(val backup: BackupInfo) : AdminIntent
    data object ConfirmDelete : AdminIntent
    data object CancelDelete : AdminIntent

    // ── Audit Log ──────────────────────────────────────────────────────────
    data object RefreshAuditLog : AdminIntent
    data class FilterAuditByUser(val userId: String) : AdminIntent
    /** Null clears the event type filter (show all). */
    data class FilterAuditByEventType(val eventType: AuditEventType?) : AdminIntent
    /** Null = show all; true = success only; false = failed only. */
    data class FilterAuditBySuccess(val success: Boolean?) : AdminIntent
    /** Set the date range filter. Null values clear the respective bound. */
    data class FilterAuditByDateRange(val from: Instant?, val to: Instant?) : AdminIntent
    data object ExportAuditLogCsv : AdminIntent
    data object VerifyIntegrity : AdminIntent
    data object NextAuditPage : AdminIntent
    data object PrevAuditPage : AdminIntent

    // ── UI Feedback ────────────────────────────────────────────────────────
    data object DismissError : AdminIntent
    data object DismissSuccess : AdminIntent
}
