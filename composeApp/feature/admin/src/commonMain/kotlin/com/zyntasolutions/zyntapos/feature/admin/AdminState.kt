package com.zyntasolutions.zyntapos.feature.admin

import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.BackupInfo
import com.zyntasolutions.zyntapos.domain.model.IntegrityReport
import com.zyntasolutions.zyntapos.domain.model.DatabaseStats
import com.zyntasolutions.zyntapos.domain.model.PurgeResult
import com.zyntasolutions.zyntapos.domain.model.SystemHealth

/** Active section in the Admin feature tab bar. */
enum class AdminTab { SYSTEM_HEALTH, BACKUPS, AUDIT_LOG }

/**
 * Immutable UI state for the Admin feature (Sprints 13–15).
 *
 * Three logical sections:
 * - **SYSTEM_HEALTH** — live metrics, DB stats, vacuum / purge actions
 * - **BACKUPS** — list of backups with create / restore / delete
 * - **AUDIT_LOG** — security event stream with user filter
 */
data class AdminState(

    val activeTab: AdminTab = AdminTab.SYSTEM_HEALTH,

    // ── System Health ─────────────────────────────────────────────────────
    val systemHealth: SystemHealth? = null,
    val databaseStats: DatabaseStats? = null,
    val lastPurgeResult: PurgeResult? = null,
    val lastVacuumResult: PurgeResult? = null,

    // ── Backups ───────────────────────────────────────────────────────────
    val backups: List<BackupInfo> = emptyList(),
    val isCreatingBackup: Boolean = false,
    val showRestoreConfirm: BackupInfo? = null,
    val showDeleteConfirm: BackupInfo? = null,

    // ── Audit Log ─────────────────────────────────────────────────────────
    val auditEntries: List<AuditEntry> = emptyList(),
    val auditUserFilter: String = "",
    val auditPage: Int = 0,
    val auditTotalPages: Int = 1,
    val integrityReport: IntegrityReport? = null,
    val isVerifyingIntegrity: Boolean = false,

    // ── Global ────────────────────────────────────────────────────────────
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)
