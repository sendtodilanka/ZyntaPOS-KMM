package com.zyntasolutions.zyntapos.debug.mvi

import com.zyntasolutions.zyntapos.debug.model.SeedProfile

/**
 * All user actions in the Debug Console.
 *
 * ### Destructive actions
 * Actions tagged `Request*` show a [ConfirmDestructiveDialog] before proceeding.
 * The corresponding `Confirm*` intent is dispatched only after the user types
 * the required confirmation word.
 *
 * ### Security rule — credentials
 * [SetAdminCredentials] carries a plain-text password to pass directly to the
 * domain use case. The password MUST NOT be stored in [DebugState] or logged.
 * The ViewModel discards it immediately after the use-case call.
 */
sealed class DebugIntent {

    // ── General ──────────────────────────────────────────────────────────────
    data class SelectTab(val tab: DebugTab) : DebugIntent()
    data object DismissActionResult : DebugIntent()
    data object DismissDestructiveDialog : DebugIntent()
    data object LoadInitialData : DebugIntent()

    // ── Seeds tab ─────────────────────────────────────────────────────────────
    data class SelectSeedProfile(val profile: SeedProfile) : DebugIntent()
    data object RunSeedProfile : DebugIntent()

    /** Opens the masked-input dialog to create or update the ADMIN account. */
    data object ShowAdminSetupDialog : DebugIntent()
    data object DismissAdminSetupDialog : DebugIntent()

    /**
     * Carries the runtime-collected admin credentials.
     * PASSWORD IS NEVER STORED IN STATE — forwarded directly to UserRepository.create().
     */
    data class SetAdminCredentials(val email: String, val password: String) : DebugIntent()

    // ── Seeds — destructive ───────────────────────────────────────────────────
    data object RequestClearSeedData : DebugIntent()
    data object ConfirmClearSeedData : DebugIntent()

    // ── Database tab ──────────────────────────────────────────────────────────
    data object LoadTableCounts : DebugIntent()
    data object VacuumDatabase : DebugIntent()
    data object ExportDatabase : DebugIntent()

    // ── Database — destructive ────────────────────────────────────────────────
    data object RequestResetDatabase : DebugIntent()
    data object ConfirmResetDatabase : DebugIntent()
    data class RequestClearTable(val tableName: String) : DebugIntent()
    data class ConfirmClearTable(val tableName: String) : DebugIntent()

    // ── Auth tab ──────────────────────────────────────────────────────────────
    data object LoadUsers : DebugIntent()
    data class DeactivateUser(val userId: String) : DebugIntent()
    data object ForceTokenExpiry : DebugIntent()

    // ── Auth — destructive ────────────────────────────────────────────────────
    data object RequestClearSession : DebugIntent()
    data object ConfirmClearSession : DebugIntent()

    // ── Network tab ───────────────────────────────────────────────────────────
    data class SetOfflineModeForced(val forced: Boolean) : DebugIntent()
    data object ForceSyncNow : DebugIntent()
    data object LoadSyncQueueDepth : DebugIntent()

    // ── Network — destructive ─────────────────────────────────────────────────
    data object RequestClearSyncQueue : DebugIntent()
    data object ConfirmClearSyncQueue : DebugIntent()

    // ── Diagnostics tab ───────────────────────────────────────────────────────
    data object LoadAuditLog : DebugIntent()
    data object LoadSystemHealth : DebugIntent()
    data object ExportLogs : DebugIntent()

    // ── UI / UX tab ───────────────────────────────────────────────────────────
    /** [theme] is one of "LIGHT", "DARK", or null to restore system default. */
    data class SetThemeOverride(val theme: String?) : DebugIntent()
    data class SetFontScale(val scale: Float) : DebugIntent()
}
