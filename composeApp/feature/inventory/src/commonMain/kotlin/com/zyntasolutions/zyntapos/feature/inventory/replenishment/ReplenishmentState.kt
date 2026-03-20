package com.zyntasolutions.zyntapos.feature.inventory.replenishment

import androidx.compose.runtime.Immutable
import com.zyntasolutions.zyntapos.domain.model.PurchaseOrder
import com.zyntasolutions.zyntapos.domain.model.ReplenishmentRule
import com.zyntasolutions.zyntapos.domain.model.Supplier
import com.zyntasolutions.zyntapos.domain.model.Warehouse
import com.zyntasolutions.zyntapos.domain.model.report.StockReorderData
import com.zyntasolutions.zyntapos.domain.usecase.inventory.ReplenishmentResult

/**
 * Immutable UI state for the replenishment management screen (C1.5).
 *
 * The screen has three sub-views navigated via tabs:
 * 1. **Reorder Alerts** — products below their reorder threshold.
 * 2. **Purchase Orders** — PENDING / PARTIAL orders from suppliers.
 * 3. **Replenishment Rules** — per-product auto-PO configuration.
 */
@Immutable
data class ReplenishmentState(

    // ── Tab selection ─────────────────────────────────────────────────────────
    val activeTab: ReplenishmentTab = ReplenishmentTab.REORDER_ALERTS,

    // ── Reorder Alerts tab ────────────────────────────────────────────────────
    val reorderAlerts: List<StockReorderData> = emptyList(),
    val isLoadingAlerts: Boolean = false,

    // ── Purchase Orders tab ───────────────────────────────────────────────────
    val purchaseOrders: List<PurchaseOrder> = emptyList(),
    val selectedOrder: PurchaseOrder? = null,
    val isLoadingOrders: Boolean = false,

    // ── Create PO dialog ──────────────────────────────────────────────────────
    val showCreatePoDialog: Boolean = false,
    val createPoSupplierId: String = "",
    val createPoOrderNumber: String = "",
    val createPoExpectedDate: Long? = null,
    val createPoNotes: String = "",
    /** Pre-filled from a reorder alert when the user taps "Create PO". */
    val createPoSourceAlert: StockReorderData? = null,
    val isCreatingPo: Boolean = false,

    // ── Replenishment Rules tab ───────────────────────────────────────────────
    val replenishmentRules: List<ReplenishmentRule> = emptyList(),
    val selectedRule: ReplenishmentRule? = null,
    val showRuleDialog: Boolean = false,
    val ruleFormProductId: String = "",
    val ruleFormWarehouseId: String = "",
    val ruleFormSupplierId: String = "",
    val ruleFormReorderPoint: String = "",
    val ruleFormReorderQty: String = "",
    val ruleFormAutoApprove: Boolean = false,
    val ruleFormIsActive: Boolean = true,
    val isLoadingRules: Boolean = false,
    val isSavingRule: Boolean = false,

    // ── Auto-replenishment run ────────────────────────────────────────────────
    val isRunningAutoReplenishment: Boolean = false,
    val lastAutoReplenishmentResult: ReplenishmentResult? = null,

    // ── Reference data (for pickers) ─────────────────────────────────────────
    val suppliers: List<Supplier> = emptyList(),
    val warehouses: List<Warehouse> = emptyList(),

    // ── Common ────────────────────────────────────────────────────────────────
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)

enum class ReplenishmentTab {
    REORDER_ALERTS,
    PURCHASE_ORDERS,
    REPLENISHMENT_RULES,
}
