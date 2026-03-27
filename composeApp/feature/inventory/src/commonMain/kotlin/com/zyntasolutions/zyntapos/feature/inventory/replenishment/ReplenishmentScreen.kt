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
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.StringResolver
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
    val s = LocalStrings.current
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
        title = s[StringResource.INVENTORY_REPLENISHMENT],
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        actions = {
            if (state.activeTab == ReplenishmentTab.PURCHASE_ORDERS) {
                IconButton(onClick = { viewModel.dispatch(ReplenishmentIntent.OpenCreatePoDialog) }) {
                    Icon(Icons.Default.Add, contentDescription = s[StringResource.INVENTORY_CREATE_PURCHASE_ORDER])
                }
            }
            IconButton(
                onClick = { viewModel.dispatch(ReplenishmentIntent.RunAutoReplenishment) },
                enabled = !state.isRunningAutoReplenishment,
            ) {
                if (state.isRunningAutoReplenishment) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = s[StringResource.INVENTORY_RUN_AUTO_REPLENISHMENT])
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
                        text      = { val s = LocalStrings.current; Text(tab.label(s)) },
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

    ZyntaLoadingOverlay(
        isLoading = state.isCreatingPo || state.isSavingRule || state.isRunningAutoReplenishment,
    )
}

private fun ReplenishmentTab.label(s: StringResolver): String = when (this) {
    ReplenishmentTab.REORDER_ALERTS      -> s[StringResource.INVENTORY_REPLENISHMENT_TAB_ALERTS]
    ReplenishmentTab.PURCHASE_ORDERS     -> s[StringResource.INVENTORY_REPLENISHMENT_TAB_ORDERS]
    ReplenishmentTab.REPLENISHMENT_RULES -> s[StringResource.INVENTORY_REPLENISHMENT_TAB_RULES]
}

// ── Reorder Alerts tab ────────────────────────────────────────────────────────

@Composable
private fun ReorderAlertsTab(state: ReplenishmentState, viewModel: ReplenishmentViewModel) {
    val s = LocalStrings.current
    if (state.isLoadingAlerts) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (state.reorderAlerts.isEmpty()) {
        ZyntaEmptyState(
            icon     = Icons.Default.CheckCircle,
            title    = s[StringResource.INVENTORY_STOCK_HEALTHY],
            subtitle = s[StringResource.INVENTORY_STOCK_HEALTHY_SUBTITLE],
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
    val s = LocalStrings.current
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
                    s[StringResource.INVENTORY_REORDER_INFO_FORMAT, "${alert.currentStock}", "${alert.reorderPoint}", "${alert.suggestedReorderQty}"],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(ZyntaSpacing.sm))
            FilledTonalButton(onClick = onCreate) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(s[StringResource.INVENTORY_CREATE_PO])
            }
        }
    }
}

// ── Purchase Orders tab ───────────────────────────────────────────────────────

@Composable
private fun PurchaseOrdersTab(state: ReplenishmentState, viewModel: ReplenishmentViewModel) {
    val s = LocalStrings.current
    if (state.isLoadingOrders) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (state.purchaseOrders.isEmpty()) {
        ZyntaEmptyState(
            icon     = Icons.Default.ShoppingCart,
            title    = s[StringResource.INVENTORY_NO_PURCHASE_ORDERS],
            subtitle = s[StringResource.INVENTORY_NO_PURCHASE_ORDERS_SUBTITLE],
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
    val s = LocalStrings.current
    val (chipVariant, chipIcon) = when (order.status) {
        PurchaseOrder.Status.PENDING  -> StatusChipVariant.Warning to Icons.Default.HourglassEmpty
        PurchaseOrder.Status.PARTIAL  -> StatusChipVariant.Info to Icons.Default.LocalShipping
        PurchaseOrder.Status.RECEIVED -> StatusChipVariant.Success to Icons.Default.CheckCircle
        PurchaseOrder.Status.CANCELLED -> StatusChipVariant.Error to Icons.Default.Cancel
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
                ZyntaStatusChip(label = order.status.name, variant = chipVariant, icon = chipIcon)
                if (onCancel != null) {
                    TextButton(onClick = onCancel, contentPadding = PaddingValues(0.dp)) {
                        Text(s[StringResource.COMMON_CANCEL], style = MaterialTheme.typography.labelSmall)
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
    val s = LocalStrings.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(ZyntaSpacing.md)) {
            Text(s[StringResource.INVENTORY_PO_ORDER_FORMAT, order.orderNumber], style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(ZyntaSpacing.sm))
            Text(s[StringResource.INVENTORY_PO_STATUS_FORMAT, order.status.name])
            Text(s[StringResource.INVENTORY_PO_ITEMS_FORMAT, "${order.items.size}"])
            order.notes?.let { notes -> Text(s[StringResource.INVENTORY_PO_NOTES_FORMAT, notes]) }
            Spacer(Modifier.height(ZyntaSpacing.md))

            if (order.items.isNotEmpty()) {
                Text(s[StringResource.INVENTORY_LINE_ITEMS], style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                order.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(item.productId, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s[StringResource.INVENTORY_PO_QTY_FORMAT, "${item.quantityOrdered.toInt()}", "${item.quantityReceived.toInt()}"])
                    }
                    HorizontalDivider()
                }
            }

            if (onCancel != null) {
                Spacer(Modifier.height(ZyntaSpacing.md))
                ZyntaButton(
                    text    = s[StringResource.INVENTORY_CANCEL_ORDER],
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
    val s = LocalStrings.current
    Box(Modifier.fillMaxSize()) {
        if (state.isLoadingRules) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (state.replenishmentRules.isEmpty()) {
            ZyntaEmptyState(
                icon     = Icons.Default.Settings,
                title    = s[StringResource.INVENTORY_NO_REPLENISHMENT_RULES],
                subtitle = s[StringResource.INVENTORY_NO_REPLENISHMENT_RULES_SUBTITLE],
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
            Icon(Icons.Default.Add, contentDescription = s[StringResource.INVENTORY_ADD_RULE])
        }
    }
}

@Composable
private fun ReplenishmentRuleCard(
    rule: ReplenishmentRule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val s = LocalStrings.current
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
                        s[StringResource.INVENTORY_AUTO_APPROVE_ENABLED],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column {
                IconButton(onClick = onEdit)   { Icon(Icons.Default.Edit, contentDescription = s[StringResource.COMMON_EDIT]) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = s[StringResource.COMMON_DELETE]) }
            }
        }
    }
}

// ── Create PO dialog ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePoDialog(state: ReplenishmentState, viewModel: ReplenishmentViewModel) {
    val s = LocalStrings.current
    AlertDialog(
        onDismissRequest = { viewModel.dispatch(ReplenishmentIntent.DismissCreatePoDialog) },
        title = { Text(s[StringResource.INVENTORY_CREATE_PURCHASE_ORDER]) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                state.createPoSourceAlert?.let { alert ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Text(
                            s[StringResource.INVENTORY_PO_PRODUCT_SUGGEST_FORMAT, alert.productName, "${alert.suggestedReorderQty}"],
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
                            value         = selected?.name ?: s[StringResource.INVENTORY_PO_SELECT_SUPPLIER],
                            onValueChange = {},
                            readOnly      = true,
                            label         = { Text(s[StringResource.INVENTORY_PO_SUPPLIER_LABEL]) },
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
                        label         = s[StringResource.INVENTORY_PO_SUPPLIER_ID_LABEL],
                    )
                }

                ZyntaTextField(
                    value         = state.createPoOrderNumber,
                    onValueChange = { viewModel.dispatch(ReplenishmentIntent.UpdateCreatePoField(CreatePoField.ORDER_NUMBER, it)) },
                    label         = s[StringResource.INVENTORY_PO_ORDER_NUMBER_LABEL],
                )
                ZyntaTextField(
                    value         = state.createPoNotes,
                    onValueChange = { viewModel.dispatch(ReplenishmentIntent.UpdateCreatePoField(CreatePoField.NOTES, it)) },
                    label         = s[StringResource.INVENTORY_PO_NOTES_LABEL],
                    singleLine    = false,
                    maxLines      = 3,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { viewModel.dispatch(ReplenishmentIntent.SubmitCreatePo) },
                enabled  = state.createPoSupplierId.isNotBlank() && !state.isCreatingPo,
            ) { Text(s[StringResource.INVENTORY_CREATE]) }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dispatch(ReplenishmentIntent.DismissCreatePoDialog) }) { Text(s[StringResource.COMMON_CANCEL]) }
        },
    )
}

// ── Rule edit dialog ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditDialog(state: ReplenishmentState, viewModel: ReplenishmentViewModel) {
    val s = LocalStrings.current
    val isEdit = state.selectedRule != null
    AlertDialog(
        onDismissRequest = { viewModel.dispatch(ReplenishmentIntent.DismissRuleDialog) },
        title = { Text(if (isEdit) s[StringResource.INVENTORY_EDIT_REPLENISHMENT_RULE] else s[StringResource.INVENTORY_NEW_REPLENISHMENT_RULE]) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                ZyntaTextField(
                    value         = state.ruleFormProductId,
                    onValueChange = { viewModel.dispatch(ReplenishmentIntent.UpdateRuleField(RuleField.PRODUCT_ID, it)) },
                    label         = s[StringResource.INVENTORY_PO_PRODUCT_ID_LABEL],
                    enabled       = !isEdit,
                )

                if (state.warehouses.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    val selected = state.warehouses.firstOrNull { it.id == state.ruleFormWarehouseId }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value         = selected?.name ?: s[StringResource.INVENTORY_PO_SELECT_WAREHOUSE],
                            onValueChange = {},
                            readOnly      = true,
                            label         = { Text(s[StringResource.INVENTORY_PO_WAREHOUSE_LABEL]) },
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
                        label         = s[StringResource.INVENTORY_PO_WAREHOUSE_ID_LABEL],
                        enabled       = !isEdit,
                    )
                }

                if (state.suppliers.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    val selected = state.suppliers.firstOrNull { it.id == state.ruleFormSupplierId }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value         = selected?.name ?: s[StringResource.INVENTORY_PO_SELECT_SUPPLIER],
                            onValueChange = {},
                            readOnly      = true,
                            label         = { Text(s[StringResource.INVENTORY_PO_SUPPLIER_LABEL]) },
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
                        label         = s[StringResource.INVENTORY_PO_SUPPLIER_ID_LABEL],
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                    ZyntaTextField(
                        value         = state.ruleFormReorderPoint,
                        onValueChange = { viewModel.dispatch(ReplenishmentIntent.UpdateRuleField(RuleField.REORDER_POINT, it)) },
                        label         = s[StringResource.INVENTORY_PO_REORDER_POINT_LABEL],
                        modifier      = Modifier.weight(1f),
                    )
                    ZyntaTextField(
                        value         = state.ruleFormReorderQty,
                        onValueChange = { viewModel.dispatch(ReplenishmentIntent.UpdateRuleField(RuleField.REORDER_QTY, it)) },
                        label         = s[StringResource.INVENTORY_PO_REORDER_QTY_LABEL],
                        modifier      = Modifier.weight(1f),
                    )
                }

                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier              = Modifier.fillMaxWidth(),
                ) {
                    Text(s[StringResource.INVENTORY_AUTO_APPROVE_PO], style = MaterialTheme.typography.bodyMedium)
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
                    Text(s[StringResource.INVENTORY_RULE_ACTIVE], style = MaterialTheme.typography.bodyMedium)
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
            ) { Text(if (isEdit) s[StringResource.COMMON_SAVE] else s[StringResource.COMMON_ADD]) }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dispatch(ReplenishmentIntent.DismissRuleDialog) }) { Text(s[StringResource.COMMON_CANCEL]) }
        },
    )
}

// ── Auto-replenishment result dialog ──────────────────────────────────────────

@Composable
private fun AutoReplenishmentResultDialog(
    result: ReplenishmentResult,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (result.hasErrors) Icons.Default.Warning else Icons.Default.CheckCircle,
                contentDescription = null,
            )
        },
        title  = { Text(s[StringResource.INVENTORY_AUTO_REPLENISHMENT_COMPLETE]) },
        text   = {
            Column {
                Text(s[StringResource.INVENTORY_AUTO_RULES_EVALUATED_FORMAT, "${result.rulesEvaluated}"])
                Text(s[StringResource.INVENTORY_AUTO_ORDERS_CREATED_FORMAT, "${result.ordersCreated}"])
                Text(s[StringResource.INVENTORY_AUTO_SKIPPED_FORMAT, "${result.rulesSkipped}"])
                if (result.errors.isNotEmpty()) {
                    Spacer(Modifier.height(ZyntaSpacing.sm))
                    Text(s[StringResource.INVENTORY_AUTO_ERRORS_LABEL], style = MaterialTheme.typography.labelMedium)
                    result.errors.forEach { Text(s[StringResource.INVENTORY_AUTO_ERROR_ITEM_FORMAT, it], style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(s[StringResource.COMMON_OK]) } },
    )
}
