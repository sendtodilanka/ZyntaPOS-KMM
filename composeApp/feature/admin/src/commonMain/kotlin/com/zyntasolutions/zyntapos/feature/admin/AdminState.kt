package com.zyntasolutions.zyntapos.feature.admin

import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.AuditEventType
import com.zyntasolutions.zyntapos.domain.model.BackupInfo
import com.zyntasolutions.zyntapos.domain.model.IntegrityReport
import com.zyntasolutions.zyntapos.domain.model.DatabaseStats
import com.zyntasolutions.zyntapos.domain.model.PurgeResult
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.SystemHealth
import kotlinx.datetime.Instant

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
    /** Null = show all event types; non-null = show only matching type. */
    val auditEventTypeFilter: AuditEventType? = null,
    /** Null = show all roles; non-null = show only entries from users with this role. */
    val auditRoleFilter: Role? = null,
    /** Null = show all; true = success only; false = failed only. */
    val auditSuccessFilter: Boolean? = null,
    /** Null = no start bound; entries on or after this instant are shown. */
    val auditDateFrom: Instant? = null,
    /** Null = no end bound; entries on or before this instant are shown. */
    val auditDateTo: Instant? = null,
    val auditPage: Int = 0,
    val auditTotalPages: Int = 1,
    val integrityReport: IntegrityReport? = null,
    val isVerifyingIntegrity: Boolean = false,

    // ── Global ────────────────────────────────────────────────────────────
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)
