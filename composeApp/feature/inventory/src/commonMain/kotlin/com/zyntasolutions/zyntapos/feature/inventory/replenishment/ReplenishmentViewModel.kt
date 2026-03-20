package com.zyntasolutions.zyntapos.feature.inventory.replenishment

import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.PurchaseOrderItem
import com.zyntasolutions.zyntapos.domain.model.ReplenishmentRule
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.PurchaseOrderRepository
import com.zyntasolutions.zyntapos.domain.repository.ReplenishmentRuleRepository
import com.zyntasolutions.zyntapos.domain.repository.SupplierRepository
import com.zyntasolutions.zyntapos.domain.repository.WarehouseRepository
import com.zyntasolutions.zyntapos.domain.usecase.inventory.AutoReplenishmentUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.CreatePurchaseOrderUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise.GenerateStockReorderReportUseCase
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * ViewModel for the warehouse-to-store replenishment management screen (C1.5).
 *
 * Covers three tabs:
 * 1. **Reorder Alerts** — products below their min-stock threshold.
 * 2. **Purchase Orders** — PENDING / PARTIAL supplier orders.
 * 3. **Replenishment Rules** — per-product auto-PO configuration.
 */
class ReplenishmentViewModel(
    private val authRepository: AuthRepository,
    private val supplierRepository: SupplierRepository,
    private val warehouseRepository: WarehouseRepository,
    private val purchaseOrderRepository: PurchaseOrderRepository,
    private val replenishmentRuleRepository: ReplenishmentRuleRepository,
    private val generateStockReorderReportUseCase: GenerateStockReorderReportUseCase,
    private val createPurchaseOrderUseCase: CreatePurchaseOrderUseCase,
    private val autoReplenishmentUseCase: AutoReplenishmentUseCase,
) : BaseViewModel<ReplenishmentState, ReplenishmentIntent, ReplenishmentEffect>(
    initialState = ReplenishmentState()
) {

    private var currentUserId: String = "system"
    private var currentStoreId: String = ""

    init {
        viewModelScope.launch {
            val session = authRepository.getSession().first()
            currentUserId = session?.id ?: "system"
            currentStoreId = session?.storeId ?: ""
            loadAllData()
        }
    }

    override suspend fun handleIntent(intent: ReplenishmentIntent) {
        when (intent) {
            is ReplenishmentIntent.SelectTab               -> onSelectTab(intent.tab)
            is ReplenishmentIntent.LoadReorderAlerts       -> loadReorderAlerts()
            is ReplenishmentIntent.CreatePoFromAlert       -> onCreatePoFromAlert(intent.alert)
            is ReplenishmentIntent.LoadPurchaseOrders      -> loadPurchaseOrders()
            is ReplenishmentIntent.SelectOrder             -> updateState { copy(selectedOrder = intent.order) }
            is ReplenishmentIntent.DismissOrderDetail      -> updateState { copy(selectedOrder = null) }
            is ReplenishmentIntent.CancelOrder             -> onCancelOrder(intent.orderId)
            is ReplenishmentIntent.OpenCreatePoDialog      -> updateState {
                copy(showCreatePoDialog = true, createPoSupplierId = "", createPoOrderNumber = "",
                    createPoExpectedDate = null, createPoNotes = "", createPoSourceAlert = null)
            }
            is ReplenishmentIntent.DismissCreatePoDialog   -> updateState { copy(showCreatePoDialog = false) }
            is ReplenishmentIntent.UpdateCreatePoField     -> onUpdateCreatePoField(intent.field, intent.value)
            is ReplenishmentIntent.SetCreatePoExpectedDate -> updateState { copy(createPoExpectedDate = intent.epochMillis) }
            is ReplenishmentIntent.SubmitCreatePo          -> onSubmitCreatePo()
            is ReplenishmentIntent.LoadRules               -> loadRules()
            is ReplenishmentIntent.OpenRuleDialog          -> onOpenRuleDialog(intent.rule)
            is ReplenishmentIntent.DismissRuleDialog       -> updateState { copy(showRuleDialog = false, selectedRule = null) }
            is ReplenishmentIntent.UpdateRuleField         -> onUpdateRuleField(intent.field, intent.value)
            is ReplenishmentIntent.SetRuleAutoApprove      -> updateState { copy(ruleFormAutoApprove = intent.enabled) }
            is ReplenishmentIntent.SetRuleActive           -> updateState { copy(ruleFormIsActive = intent.active) }
            is ReplenishmentIntent.SaveRule                -> onSaveRule()
            is ReplenishmentIntent.DeleteRule              -> onDeleteRule(intent.ruleId)
            is ReplenishmentIntent.RunAutoReplenishment    -> onRunAutoReplenishment()
            is ReplenishmentIntent.DismissAutoReplenishmentResult -> updateState { copy(lastAutoReplenishmentResult = null) }
            is ReplenishmentIntent.DismissError            -> updateState { copy(error = null) }
            is ReplenishmentIntent.DismissSuccess          -> updateState { copy(successMessage = null) }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun loadAllData() {
        loadReorderAlerts()
        loadPurchaseOrders()
        loadRules()
        loadReferenceData()
    }

    private fun loadReorderAlerts() {
        updateState { copy(isLoadingAlerts = true) }
        generateStockReorderReportUseCase()
            .onEach { alerts -> updateState { copy(reorderAlerts = alerts, isLoadingAlerts = false) } }
            .catch { e -> updateState { copy(isLoadingAlerts = false, error = e.message) } }
            .launchIn(viewModelScope)
    }

    private fun loadPurchaseOrders() {
        updateState { copy(isLoadingOrders = true) }
        purchaseOrderRepository.getAll()
            .onEach { orders -> updateState { copy(purchaseOrders = orders, isLoadingOrders = false) } }
            .catch { e -> updateState { copy(isLoadingOrders = false, error = e.message) } }
            .launchIn(viewModelScope)
    }

    private fun loadRules() {
        updateState { copy(isLoadingRules = true) }
        replenishmentRuleRepository.getAll()
            .onEach { rules -> updateState { copy(replenishmentRules = rules, isLoadingRules = false) } }
            .catch { e -> updateState { copy(isLoadingRules = false, error = e.message) } }
            .launchIn(viewModelScope)
    }

    private fun loadReferenceData() {
        supplierRepository.getAll()
            .onEach { list -> updateState { copy(suppliers = list) } }
            .catch { /* non-fatal */ }
            .launchIn(viewModelScope)

        if (currentStoreId.isNotBlank()) {
            warehouseRepository.getByStore(currentStoreId)
                .onEach { list -> updateState { copy(warehouses = list) } }
                .catch { /* non-fatal */ }
                .launchIn(viewModelScope)
        }
    }

    private fun onSelectTab(tab: ReplenishmentTab) {
        updateState { copy(activeTab = tab) }
        when (tab) {
            ReplenishmentTab.REORDER_ALERTS    -> loadReorderAlerts()
            ReplenishmentTab.PURCHASE_ORDERS   -> loadPurchaseOrders()
            ReplenishmentTab.REPLENISHMENT_RULES -> loadRules()
        }
    }

    private fun onCreatePoFromAlert(alert: com.zyntasolutions.zyntapos.domain.model.report.StockReorderData) {
        updateState {
            copy(
                showCreatePoDialog  = true,
                createPoSourceAlert = alert,
                createPoOrderNumber = IdGenerator.newPrefixedId("PO"),
                createPoSupplierId  = "",
                createPoNotes       = "Reorder: ${alert.productName} — current stock ${alert.currentStock}",
                activeTab           = ReplenishmentTab.PURCHASE_ORDERS,
            )
        }
    }

    private fun onUpdateCreatePoField(field: CreatePoField, value: String) {
        updateState {
            when (field) {
                CreatePoField.SUPPLIER_ID    -> copy(createPoSupplierId = value)
                CreatePoField.ORDER_NUMBER   -> copy(createPoOrderNumber = value)
                CreatePoField.NOTES          -> copy(createPoNotes = value)
            }
        }
    }

    private fun onSubmitCreatePo() {
        val state = currentState
        if (state.createPoSupplierId.isBlank()) {
            updateState { copy(error = "Please select a supplier.") }
            return
        }

        val alert = state.createPoSourceAlert
        val items = if (alert != null) {
            listOf(
                PurchaseOrderItem(
                    id               = IdGenerator.newId(),
                    purchaseOrderId  = "",
                    productId        = alert.productId,
                    quantityOrdered  = alert.suggestedReorderQty.toDouble(),
                    quantityReceived = 0.0,
                    unitCost         = 0.0,
                    lineTotal        = 0.0,
                    notes            = "From reorder alert",
                )
            )
        } else {
            // Manual PO without a pre-filled alert — require at least one item
            updateState { copy(error = "Use the Reorder Alerts tab to select a product for this PO.") }
            return
        }

        updateState { copy(isCreatingPo = true) }
        viewModelScope.launch {
            val result = createPurchaseOrderUseCase(
                supplierId   = state.createPoSupplierId,
                orderNumber  = state.createPoOrderNumber,
                items        = items,
                expectedDate = state.createPoExpectedDate,
                notes        = state.createPoNotes.ifBlank { null },
                createdBy    = currentUserId,
            )
            when (result) {
                is Result.Success -> {
                    updateState { copy(isCreatingPo = false, showCreatePoDialog = false, createPoSourceAlert = null) }
                    sendEffect(ReplenishmentEffect.ShowSuccess("Purchase order created."))
                }
                is Result.Error -> {
                    updateState { copy(isCreatingPo = false, error = result.exception.message) }
                }
                is Result.Loading -> Unit
            }
        }
    }

    private fun onCancelOrder(orderId: String) {
        viewModelScope.launch {
            when (val result = purchaseOrderRepository.cancel(orderId)) {
                is Result.Success -> sendEffect(ReplenishmentEffect.ShowSuccess("Purchase order cancelled."))
                is Result.Error   -> updateState { copy(error = result.exception.message) }
                is Result.Loading -> Unit
            }
        }
    }

    private fun onOpenRuleDialog(rule: ReplenishmentRule?) {
        updateState {
            copy(
                showRuleDialog       = true,
                selectedRule         = rule,
                ruleFormProductId    = rule?.productId ?: "",
                ruleFormWarehouseId  = rule?.warehouseId ?: "",
                ruleFormSupplierId   = rule?.supplierId ?: "",
                ruleFormReorderPoint = rule?.reorderPoint?.toString() ?: "",
                ruleFormReorderQty   = rule?.reorderQty?.toString() ?: "",
                ruleFormAutoApprove  = rule?.autoApprove ?: false,
                ruleFormIsActive     = rule?.isActive ?: true,
            )
        }
    }

    private fun onUpdateRuleField(field: RuleField, value: String) {
        updateState {
            when (field) {
                RuleField.PRODUCT_ID    -> copy(ruleFormProductId = value)
                RuleField.WAREHOUSE_ID  -> copy(ruleFormWarehouseId = value)
                RuleField.SUPPLIER_ID   -> copy(ruleFormSupplierId = value)
                RuleField.REORDER_POINT -> copy(ruleFormReorderPoint = value)
                RuleField.REORDER_QTY   -> copy(ruleFormReorderQty = value)
            }
        }
    }

    private fun onSaveRule() {
        val state = currentState

        val reorderPoint = state.ruleFormReorderPoint.toDoubleOrNull()
        val reorderQty   = state.ruleFormReorderQty.toDoubleOrNull()

        when {
            state.ruleFormProductId.isBlank()   -> { updateState { copy(error = "Product is required.") }; return }
            state.ruleFormWarehouseId.isBlank()  -> { updateState { copy(error = "Warehouse is required.") }; return }
            state.ruleFormSupplierId.isBlank()   -> { updateState { copy(error = "Supplier is required.") }; return }
            reorderPoint == null || reorderPoint < 0 -> { updateState { copy(error = "Enter a valid reorder point.") }; return }
            reorderQty == null || reorderQty <= 0    -> { updateState { copy(error = "Reorder quantity must be > 0.") }; return }
        }

        val now = Clock.System.now().toEpochMilliseconds()
        val rule = ReplenishmentRule(
            id           = state.selectedRule?.id ?: IdGenerator.newId(),
            productId    = state.ruleFormProductId,
            warehouseId  = state.ruleFormWarehouseId,
            supplierId   = state.ruleFormSupplierId,
            reorderPoint = reorderPoint!!,
            reorderQty   = reorderQty!!,
            autoApprove  = state.ruleFormAutoApprove,
            isActive     = state.ruleFormIsActive,
            createdAt    = state.selectedRule?.createdAt ?: now,
            updatedAt    = now,
        )

        updateState { copy(isSavingRule = true) }
        viewModelScope.launch {
            when (val result = replenishmentRuleRepository.upsert(rule)) {
                is Result.Success -> {
                    updateState { copy(isSavingRule = false, showRuleDialog = false, selectedRule = null) }
                    sendEffect(ReplenishmentEffect.ShowSuccess("Replenishment rule saved."))
                }
                is Result.Error -> updateState { copy(isSavingRule = false, error = result.exception.message) }
                is Result.Loading -> Unit
            }
        }
    }

    private fun onDeleteRule(ruleId: String) {
        viewModelScope.launch {
            when (val result = replenishmentRuleRepository.delete(ruleId)) {
                is Result.Success -> sendEffect(ReplenishmentEffect.ShowSuccess("Rule deleted."))
                is Result.Error   -> updateState { copy(error = result.exception.message) }
                is Result.Loading -> Unit
            }
        }
    }

    private fun onRunAutoReplenishment() {
        updateState { copy(isRunningAutoReplenishment = true) }
        viewModelScope.launch {
            val result = autoReplenishmentUseCase(triggeredBy = currentUserId)
            updateState { copy(isRunningAutoReplenishment = false, lastAutoReplenishmentResult = result) }
        }
    }
}
