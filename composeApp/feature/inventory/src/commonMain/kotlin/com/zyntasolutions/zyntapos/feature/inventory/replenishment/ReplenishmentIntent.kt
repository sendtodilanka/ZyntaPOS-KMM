package com.zyntasolutions.zyntapos.feature.inventory.replenishment

import com.zyntasolutions.zyntapos.domain.model.PurchaseOrder
import com.zyntasolutions.zyntapos.domain.model.ReplenishmentRule
import com.zyntasolutions.zyntapos.domain.model.report.StockReorderData

/** All user actions for the replenishment management screen (C1.5). */
sealed interface ReplenishmentIntent {

    // ── Navigation ────────────────────────────────────────────────────────────
    data class SelectTab(val tab: ReplenishmentTab)          : ReplenishmentIntent

    // ── Reorder Alerts ────────────────────────────────────────────────────────
    data object LoadReorderAlerts                            : ReplenishmentIntent
    data class CreatePoFromAlert(val alert: StockReorderData): ReplenishmentIntent

    // ── Purchase Orders ───────────────────────────────────────────────────────
    data object LoadPurchaseOrders                           : ReplenishmentIntent
    data class SelectOrder(val order: PurchaseOrder)         : ReplenishmentIntent
    data object DismissOrderDetail                           : ReplenishmentIntent
    data class CancelOrder(val orderId: String)              : ReplenishmentIntent

    // ── Create PO dialog ──────────────────────────────────────────────────────
    data object OpenCreatePoDialog                           : ReplenishmentIntent
    data object DismissCreatePoDialog                        : ReplenishmentIntent
    data class UpdateCreatePoField(val field: CreatePoField, val value: String) : ReplenishmentIntent
    data class SetCreatePoExpectedDate(val epochMillis: Long?): ReplenishmentIntent
    data object SubmitCreatePo                               : ReplenishmentIntent

    // ── Replenishment Rules ───────────────────────────────────────────────────
    data object LoadRules                                    : ReplenishmentIntent
    data class OpenRuleDialog(val rule: ReplenishmentRule?)  : ReplenishmentIntent
    data object DismissRuleDialog                            : ReplenishmentIntent
    data class UpdateRuleField(val field: RuleField, val value: String) : ReplenishmentIntent
    data class SetRuleAutoApprove(val enabled: Boolean)      : ReplenishmentIntent
    data class SetRuleActive(val active: Boolean)            : ReplenishmentIntent
    data object SaveRule                                     : ReplenishmentIntent
    data class DeleteRule(val ruleId: String)                : ReplenishmentIntent

    // ── Auto-replenishment run ────────────────────────────────────────────────
    data object RunAutoReplenishment                         : ReplenishmentIntent
    data object DismissAutoReplenishmentResult               : ReplenishmentIntent

    // ── Common ────────────────────────────────────────────────────────────────
    data object DismissError                                 : ReplenishmentIntent
    data object DismissSuccess                               : ReplenishmentIntent
}

enum class CreatePoField { SUPPLIER_ID, ORDER_NUMBER, NOTES }
enum class RuleField     { PRODUCT_ID, WAREHOUSE_ID, SUPPLIER_ID, REORDER_POINT, REORDER_QTY }
