package com.zyntasolutions.zyntapos.debug.mvi

import com.zyntasolutions.zyntapos.debug.model.SeedProfile
import com.zyntasolutions.zyntapos.debug.model.UserSummary
import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.seed.SeedRunner

/**
 * Immutable UI state for the Debug Console.
 *
 * Each tab's data is namespaced as a distinct group of fields.
 * The ViewModel populates fields on demand when the relevant tab is selected.
 *
 * ### Security rule
 * No password, PIN, or token value is ever stored in this state.
 * Credentials collected from dialogs are forwarded directly to use-case calls
 * and discarded immediately after.
 */
data class DebugState(
    val activeTab: DebugTab = DebugTab.Seeds,
    val isLoading: Boolean = false,

    /** Non-null while a snackbar banner should be shown. */
    val actionResult: ActionResult? = null,

    /**
     * Non-null when a destructive action is awaiting typed-word confirmation.
     * The intent stored here is dispatched after the user confirms.
     */
    val pendingDestructiveIntent: DebugIntent? = null,

    // ── Seeds tab ────────────────────────────────────────────────────────────
    val selectedProfile: SeedProfile = SeedProfile.Demo,
    val seedSummary: SeedRunner.SeedSummary? = null,
    val showAdminSetupDialog: Boolean = false,

    // ── Database tab ─────────────────────────────────────────────────────────
    val tableRowCounts: Map<String, Long> = emptyMap(),

    // ── Auth tab ──────────────────────────────────────────────────────────────
    val currentUserEmail: String? = null,
    val currentUserRole: String? = null,
    val currentUserId: String? = null,
    val hasPinConfigured: Boolean = false,
    val allUsers: List<UserSummary> = emptyList(),

    // ── Network tab ───────────────────────────────────────────────────────────
    val isOfflineModeForced: Boolean = false,
    val pendingOpsCount: Int = 0,

    // ── Diagnostics tab ───────────────────────────────────────────────────────
    val auditEntries: List<AuditEntry> = emptyList(),
    val logLines: List<String> = emptyList(),
    val dbFileSizeKb: Long = 0L,

    // ── UI/UX tab ─────────────────────────────────────────────────────────────
    /** One of "LIGHT", "DARK", or null (= system default). */
    val themeOverride: String? = null,
    val fontScaleOverride: Float = 1.0f,
) {
    /**
     * One-shot result shown in [ActionResultBanner] after any debug action completes.
     */
    data class ActionResult(
        val message: String,
        val isError: Boolean,
    )
}
