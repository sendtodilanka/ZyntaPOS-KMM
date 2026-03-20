package com.zyntasolutions.zyntapos.feature.inventory.replenishment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.StatusChipVariant
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButtonVariant
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaLoadingOverlay
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaStatusChip
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTextField
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.PurchaseOrder
import com.zyntasolutions.zyntapos.domain.model.ReplenishmentRule
import com.zyntasolutions.zyntapos.domain.model.report.StockReorderData
import com.zyntasolutions.zyntapos.domain.usecase.inventory.ReplenishmentResult
import org.koin.compose.viewmodel.koinViewModel

/**
 * Root composable for warehouse-to-store replenishment management (C1.5).
 *
 * Three-tab layout:
 * 1. **Reorder Alerts** — products below min-stock threshold; tap to create a PO.
 * 2. **Purchase Orders** — active PENDING/PARTIAL supplier orders.
 * 3. **Replenishment Rules** — per-product auto-PO configuration.
 */
@Composable
fun ReplenishmentScreen(
    modifier: Modifier = Modifier,
    viewModel: ReplenishmentViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ReplenishmentEffect.ShowError   -> snackbarHostState.showSnackbar(effect.message)
                is ReplenishmentEffect.ShowSuccess -> snackbarHostState.showSnackbar(effect.message)
                is ReplenishmentEffect.NavigateBack -> Unit
                is ReplenishmentEffect.NavigateToPurchaseOrder -> Unit
            }
        }
    }

    ZyntaPageScaffold(
        title = "Replenishment",
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        actions = {
            if (state.activeTab == ReplenishmentTab.PURCHASE_ORDERS) {
                IconButton(onClick = { viewModel.dispatch(ReplenishmentIntent.OpenCreatePoDialog) }) {
                    Icon(Icons.Default.Add, contentDescription = "Create Purchase Order")
                }
            }
            IconButton(
                onClick = { viewModel.dispatch(ReplenishmentIntent.RunAutoReplenishment) },
                enabled = !state.isRunningAutoReplenishment,
            ) {
                if (state.isRunningAutoReplenishment) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Run Auto-Replenishment")
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            // ── Tab bar ───────────────────────────────────────────────────────
            TabRow(selectedTabIndex = state.activeTab.ordinal) {
                ReplenishmentTab.entries.forEach { tab ->
                    Tab(
                        selected  = state.activeTab == tab,
                        onClick   = { viewModel.dispatch(ReplenishmentIntent.SelectTab(tab)) },
                        text      = { Text(tab.label()) },
                    )
                }
            }

            // ── Tab content ───────────────────────────────────────────────────
            when (state.activeTab) {
                ReplenishmentTab.REORDER_ALERTS    -> ReorderAlertsTab(state, viewModel)
                ReplenishmentTab.PURCHASE_ORDERS   -> PurchaseOrdersTab(state, viewModel)
                ReplenishmentTab.REPLENISHMENT_RULES -> ReplenishmentRulesTab(state, viewModel)
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (state.showCreatePoDialog) {
        CreatePoDialog(state = state, viewModel = viewModel)
    }

    if (state.showRuleDialog) {
        RuleEditDialog(state = state, viewModel = viewModel)
    }

    state.lastAutoReplenishmentResult?.let { result ->
        AutoReplenishmentResultDialog(result = result, onDismiss = {
            viewModel.dispatch(ReplenishmentIntent.DismissAutoReplenishmentResult)
        })
    }

    if (state.isCreatingPo || state.isSavingRule || state.isRunningAutoReplenishment) {
        ZyntaLoadingOverlay()
    }
}

private fun ReplenishmentTab.label(): String = when (this) {
    ReplenishmentTab.REORDER_ALERTS      -> "Reorder Alerts"
    ReplenishmentTab.PURCHASE_ORDERS     -> "Purchase Orders"
    ReplenishmentTab.REPLENISHMENT_RULES -> "Rules"
}

// ── Reorder Alerts tab ────────────────────────────────────────────────────────

@Composable
private fun ReorderAlertsTab(state: ReplenishmentState, viewModel: ReplenishmentViewModel) {
    if (state.isLoadingAlerts) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (state.reorderAlerts.isEmpty()) {
        ZyntaEmptyState(
            icon     = Icons.Default.CheckCircle,
            title    = "All stock levels are healthy",
            subtitle = "No products are below their reorder threshold.",
        )
        return
    }
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(ZyntaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
    ) {
        items(state.reorderAlerts, key = { it.productId }) { alert ->
            ReorderAlertCard(
                alert    = alert,
                onCreate = { viewModel.dispatch(ReplenishmentIntent.CreatePoFromAlert(alert)) },
            )
        }
    }
}

@Composable
private fun ReorderAlertCard(alert: StockReorderData, onCreate: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.padding(ZyntaSpacing.md),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(alert.productName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Stock: ${alert.currentStock}  |  Reorder at: ${alert.reorderPoint}  |  Suggest: ${alert.suggestedReorderQty}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(ZyntaSpacing.sm))
            FilledTonalButton(onClick = onCreate) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Create PO")
            }
        }
    }
}

// ── Purchase Orders tab ───────────────────────────────────────────────────────

@Composable
private fun PurchaseOrdersTab(state: ReplenishmentState, viewModel: ReplenishmentViewModel) {
    if (state.isLoadingOrders) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (state.purchaseOrders.isEmpty()) {
        ZyntaEmptyState(
            icon     = Icons.Default.ShoppingCart,
            title    = "No purchase orders",
            subtitle = "Create a PO from a reorder alert or using the + button.",
        )
        return
    }
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(ZyntaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
    ) {
        items(state.purchaseOrders, key = { it.id }) { order ->
            PurchaseOrderCard(
                order    = order,
                onSelect = { viewModel.dispatch(ReplenishmentIntent.SelectOrder(order)) },
                onCancel = if (!order.status.isTerminal) {{ viewModel.dispatch(ReplenishmentIntent.CancelOrder(order.id)) }} else null,
            )
        }
    }

    // Order detail bottom sheet
    state.selectedOrder?.let { order ->
        PurchaseOrderDetailSheet(
            order     = order,
            onDismiss = { viewModel.dispatch(ReplenishmentIntent.DismissOrderDetail) },
            onCancel  = if (!order.status.isTerminal) {{ viewModel.dispatch(ReplenishmentIntent.CancelOrder(order.id)) }} else null,
        )
    }
}

@Composable
private fun PurchaseOrderCard(
    order: PurchaseOrder,
    onSelect: () -> Unit,
    onCancel: (() -> Unit)?,
) {
    val chipVariant = when (order.status) {
        PurchaseOrder.Status.PENDING  -> StatusChipVariant.Warning
        PurchaseOrder.Status.PARTIAL  -> StatusChipVariant.Info
        PurchaseOrder.Status.RECEIVED -> StatusChipVariant.Success
        PurchaseOrder.Status.CANCELLED -> StatusChipVariant.Error
    }
    Card(onClick = onSelect, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.padding(ZyntaSpacing.md),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(order.orderNumber, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "${order.items.size} item(s)  |  Total: ${order.totalAmount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(ZyntaSpacing.sm))
            Column(horizontalAlignment = Alignment.End) {
                ZyntaStatusChip(label = order.status.name, variant = chipVariant)
                if (onCancel != null) {
                    TextButton(onClick = onCancel, contentPadding = PaddingValues(0.dp)) {
                        Text("Cancel", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseOrderDetailSheet(
    order: PurchaseOrder,
    onDismiss: () -> Unit,
    onCancel: (() -> Unit)?,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(ZyntaSpacing.md)) {
            Text("Purchase Order: ${order.orderNumber}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(ZyntaSpacing.sm))
            Text("Status: ${order.status.name}")
            Text("Items: ${order.items.size}")
            if (order.notes != null) Text("Notes: ${order.notes}")
            Spacer(Modifier.height(ZyntaSpacing.md))

            if (order.items.isNotEmpty()) {
                Text("Line Items", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                order.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(item.productId, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Ord: ${item.quantityOrdered.toInt()}  Rcv: ${item.quantityReceived.toInt()}")
                    }
                    HorizontalDivider()
                }
            }

            if (onCancel != null) {
                Spacer(Modifier.height(ZyntaSpacing.md))
                ZyntaButton(
                    text    = "Cancel Order",
                    onClick = { onCancel(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                    variant = ZyntaButtonVariant.Danger,
                )
            }
            Spacer(Modifier.height(ZyntaSpacing.md))
        }
    }
}

// ── Replenishment Rules tab ───────────────────────────────────────────────────

@Composable
private fun ReplenishmentRulesTab(state: ReplenishmentState, viewModel: ReplenishmentViewModel) {
    Box(Modifier.fillMaxSize()) {
        if (state.isLoadingRules) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (state.replenishmentRules.isEmpty()) {
            ZyntaEmptyState(
                icon     = Icons.Default.Settings,
                title    = "No replenishment rules",
                subtitle = "Add rules to automate purchase order creation when stock is low.",
            )
        } else {
            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(ZyntaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            ) {
                items(state.replenishmentRules, key = { it.id }) { rule ->
                    ReplenishmentRuleCard(
                        rule     = rule,
                        onEdit   = { viewModel.dispatch(ReplenishmentIntent.OpenRuleDialog(rule)) },
                        onDelete = { viewModel.dispatch(ReplenishmentIntent.DeleteRule(rule.id)) },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick  = { viewModel.dispatch(ReplenishmentIntent.OpenRuleDialog(null)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(ZyntaSpacing.md),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Rule")
        }
    }
}

@Composable
private fun ReplenishmentRuleCard(
    rule: ReplenishmentRule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.padding(ZyntaSpacing.md),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    rule.productName ?: rule.productId,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Warehouse: ${rule.warehouseName ?: rule.warehouseId}  |  Supplier: ${rule.supplierName ?: rule.supplierId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Reorder at ≤${rule.reorderPoint.toInt()}  →  Order ${rule.reorderQty.toInt()} units",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (rule.autoApprove) {
                    Text(
                        "Auto-approve enabled",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column {
                IconButton(onClick = onEdit)   { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
            }
        }
    }
}

// ── Create PO dialog ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePoDialog(state: ReplenishmentState, viewModel: ReplenishmentViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.dispatch(ReplenishmentIntent.DismissCreatePoDialog) },
        title = { Text("Create Purchase Order") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                state.createPoSourceAlert?.let { alert ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Text(
                            "Product: ${alert.productName}\nSuggest: ${alert.suggestedReorderQty} units",
                            modifier = Modifier.padding(ZyntaSpacing.sm),
                            style    = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                if (state.suppliers.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    val selected = state.suppliers.firstOrNull { it.id == state.createPoSupplierId }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value         = selected?.name ?: "Select Supplier",
                            onValueChange = {},
                            readOnly      = true,
                            label         = { Text("Supplier *") },
                            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier      = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            state.suppliers.forEach { supplier ->
                                DropdownMenuItem(
                                    text    = { Text(supplier.name) },
                                    onClick = {
                                        viewModel.dispatch(ReplenishmentIntent.UpdateCreatePoField(CreatePoField.SUPPLIER_ID, supplier.id))
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                } else {
                    ZyntaTextField(
                        value         = state.createPoSupplierId,
                        onValueChange = { viewModel.dispatch(ReplenishmentIntent.UpdateCreatePoField(CreatePoField.SUPPLIER_ID, it)) },
                        label         = "Supplier ID *",
                    )
                }

                ZyntaTextField(
                    value         = state.createPoOrderNumber,
                    onValueChange = { viewModel.dispatch(ReplenishmentIntent.UpdateCreatePoField(CreatePoField.ORDER_NUMBER, it)) },
                    label         = "Order Number (auto if blank)",
                )
                ZyntaTextField(
                    value         = state.createPoNotes,
                    onValueChange = { viewModel.dispatch(ReplenishmentIntent.UpdateCreatePoField(CreatePoField.NOTES, it)) },
                    label         = "Notes",
                    singleLine    = false,
                    maxLines      = 3,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { viewModel.dispatch(ReplenishmentIntent.SubmitCreatePo) },
                enabled  = state.createPoSupplierId.isNotBlank() && !state.isCreatingPo,
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dispatch(ReplenishmentIntent.DismissCreatePoDialog) }) { Text("Cancel") }
        },
    )
}

// ── Rule edit dialog ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditDialog(state: ReplenishmentState, viewModel: ReplenishmentViewModel) {
    val isEdit = state.selectedRule != null
    AlertDialog(
        onDismissRequest = { viewModel.dispatch(ReplenishmentIntent.DismissRuleDialog) },
        title = { Text(if (isEdit) "Edit Replenishment Rule" else "New Replenishment Rule") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                ZyntaTextField(
                    value         = state.ruleFormProductId,
                    onValueChange = { viewModel.dispatch(ReplenishmentIntent.UpdateRuleField(RuleField.PRODUCT_ID, it)) },
                    label         = "Product ID *",
                    enabled       = !isEdit,
                )

                if (state.warehouses.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    val selected = state.warehouses.firstOrNull { it.id == state.ruleFormWarehouseId }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value         = selected?.name ?: "Select Warehouse",
                            onValueChange = {},
                            readOnly      = true,
                            label         = { Text("Warehouse *") },
                            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier      = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            state.warehouses.forEach { wh ->
                                DropdownMenuItem(
                                    text    = { Text(wh.name) },
                                    onClick = {
                                        viewModel.dispatch(ReplenishmentIntent.UpdateRuleField(RuleField.WAREHOUSE_ID, wh.id))
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                } else {
                    ZyntaTextField(
                        value         = state.ruleFormWarehouseId,
                        onValueChange = { viewModel.dispatch(ReplenishmentIntent.UpdateRuleField(RuleField.WAREHOUSE_ID, it)) },
                        label         = "Warehouse ID *",
                        enabled       = !isEdit,
                    )
                }

                if (state.suppliers.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    val selected = state.suppliers.firstOrNull { it.id == state.ruleFormSupplierId }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value         = selected?.name ?: "Select Supplier",
                            onValueChange = {},
                            readOnly      = true,
                            label         = { Text("Supplier *") },
                            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier      = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            state.suppliers.forEach { supplier ->
                                DropdownMenuItem(
                                    text    = { Text(supplier.name) },
                                    onClick = {
                                        viewModel.dispatch(ReplenishmentIntent.UpdateRuleField(RuleField.SUPPLIER_ID, supplier.id))
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                } else {
                    ZyntaTextField(
                        value         = state.ruleFormSupplierId,
                        onValueChange = { viewModel.dispatch(ReplenishmentIntent.UpdateRuleField(RuleField.SUPPLIER_ID, it)) },
                        label         = "Supplier ID *",
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                    ZyntaTextField(
                        value         = state.ruleFormReorderPoint,
                        onValueChange = { viewModel.dispatch(ReplenishmentIntent.UpdateRuleField(RuleField.REORDER_POINT, it)) },
                        label         = "Reorder Point *",
                        modifier      = Modifier.weight(1f),
                    )
                    ZyntaTextField(
                        value         = state.ruleFormReorderQty,
                        onValueChange = { viewModel.dispatch(ReplenishmentIntent.UpdateRuleField(RuleField.REORDER_QTY, it)) },
                        label         = "Reorder Qty *",
                        modifier      = Modifier.weight(1f),
                    )
                }

                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier              = Modifier.fillMaxWidth(),
                ) {
                    Text("Auto-approve PO", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked         = state.ruleFormAutoApprove,
                        onCheckedChange = { viewModel.dispatch(ReplenishmentIntent.SetRuleAutoApprove(it)) },
                    )
                }

                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier              = Modifier.fillMaxWidth(),
                ) {
                    Text("Rule active", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked         = state.ruleFormIsActive,
                        onCheckedChange = { viewModel.dispatch(ReplenishmentIntent.SetRuleActive(it)) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.dispatch(ReplenishmentIntent.SaveRule) },
                enabled = !state.isSavingRule,
            ) { Text(if (isEdit) "Save" else "Add") }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dispatch(ReplenishmentIntent.DismissRuleDialog) }) { Text("Cancel") }
        },
    )
}

// ── Auto-replenishment result dialog ──────────────────────────────────────────

@Composable
private fun AutoReplenishmentResultDialog(
    result: ReplenishmentResult,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (result.hasErrors) Icons.Default.Warning else Icons.Default.CheckCircle,
                contentDescription = null,
            )
        },
        title  = { Text("Auto-Replenishment Complete") },
        text   = {
            Column {
                Text("Rules evaluated: ${result.rulesEvaluated}")
                Text("Purchase orders created: ${result.ordersCreated}")
                Text("Skipped: ${result.rulesSkipped}")
                if (result.errors.isNotEmpty()) {
                    Spacer(Modifier.height(ZyntaSpacing.sm))
                    Text("Errors:", style = MaterialTheme.typography.labelMedium)
                    result.errors.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
