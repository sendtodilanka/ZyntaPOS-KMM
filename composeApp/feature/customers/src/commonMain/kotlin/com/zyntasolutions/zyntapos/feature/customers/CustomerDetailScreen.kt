package com.zyntasolutions.zyntapos.feature.customers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaLoadingOverlay
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaLoyaltyTierBadge
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTextField
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Customer create/edit screen with optional purchase history tab.
 *
 * In **create mode** (`customerId == null`) only the Profile tab is shown.
 * In **edit mode** a second "History" tab is available, which loads the
 * customer's purchase history via [CustomerIntent.LoadPurchaseHistory].
 *
 * Stateless — renders [state.editFormState] and dispatches [CustomerIntent]s.
 * Navigation is handled by the parent via [CustomerEffect] callbacks.
 *
 * @param customerId Null = create mode; non-null = edit mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customerId: String?,
    state: CustomerState,
    onIntent: (CustomerIntent) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateToWallet: (String) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    LaunchedEffect(customerId) {
        onIntent(CustomerIntent.SelectCustomer(customerId))
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val form = state.editFormState
    val isEditMode = form.isEditing

    // Load history when user switches to the History tab
    LaunchedEffect(selectedTab, customerId) {
        if (selectedTab == 1 && customerId != null) {
            onIntent(CustomerIntent.LoadPurchaseHistory(customerId))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isEditMode) "Edit Customer" else "New Customer")
                        if (isEditMode && state.currentLoyaltyTier != null) {
                            Spacer(Modifier.width(8.dp))
                            ZyntaLoyaltyTierBadge(tierName = state.currentLoyaltyTier.name)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isEditMode && state.selectedCustomer != null) {
                        // GDPR data export (G10)
                        IconButton(
                            onClick = { onIntent(CustomerIntent.ExportCustomerData(state.selectedCustomer.id)) },
                            enabled = !state.isExporting,
                        ) {
                            Icon(Icons.Filled.FileDownload, contentDescription = "Export Customer Data (GDPR)")
                        }
                        IconButton(onClick = {
                            onNavigateToWallet(state.selectedCustomer.id)
                        }) {
                            Icon(Icons.Filled.AccountBalanceWallet, contentDescription = "Wallet")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                // ── Tabs (edit mode only) ─────────────────────────────────────
                if (isEditMode) {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Profile") },
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text("History")
                                    if (state.purchaseHistory.isNotEmpty()) {
                                        Text(
                                            "(${state.purchaseHistory.size})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }

                when {
                    !isEditMode || selectedTab == 0 -> ProfileTab(
                        form = form,
                        isEditMode = isEditMode,
                        isLoading = state.isLoading,
                        onIntent = onIntent,
                    )
                    selectedTab == 1 -> HistoryTab(
                        orders = state.purchaseHistory,
                        isLoading = state.isPurchaseHistoryLoading,
                    )
                }
            }
            ZyntaLoadingOverlay(isLoading = state.isLoading)
        }
    }

    // ── Delete Confirmation Dialog ─────────────────────────────────────────────
    if (showDeleteDialog && state.selectedCustomer != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Customer") },
            text = { Text("Delete ${state.selectedCustomer.name}? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onIntent(CustomerIntent.DeleteCustomer(state.selectedCustomer.id))
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Profile Tab — existing create/edit form
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileTab(
    form: CustomerFormState,
    isEditMode: Boolean,
    isLoading: Boolean,
    onIntent: (CustomerIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Core Fields ──────────────────────────────────────────────
        ZyntaTextField(
            value = form.name,
            onValueChange = { onIntent(CustomerIntent.UpdateFormField("name", it)) },
            label = "Full Name *",
            error = form.validationErrors["name"],
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaTextField(
            value = form.phone,
            onValueChange = { onIntent(CustomerIntent.UpdateFormField("phone", it)) },
            label = "Phone *",
            error = form.validationErrors["phone"],
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaTextField(
            value = form.email,
            onValueChange = { onIntent(CustomerIntent.UpdateFormField("email", it)) },
            label = "Email",
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaTextField(
            value = form.address,
            onValueChange = { onIntent(CustomerIntent.UpdateFormField("address", it)) },
            label = "Address",
            modifier = Modifier.fillMaxWidth(),
        )

        // ── Demographics ─────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ZyntaTextField(
                value = form.gender,
                onValueChange = { onIntent(CustomerIntent.UpdateFormField("gender", it)) },
                label = "Gender",
                modifier = Modifier.weight(1f),
            )
            ZyntaTextField(
                value = form.birthday,
                onValueChange = { onIntent(CustomerIntent.UpdateFormField("birthday", it)) },
                label = "Birthday (YYYY-MM-DD)",
                modifier = Modifier.weight(1f),
            )
        }

        ZyntaTextField(
            value = form.notes,
            onValueChange = { onIntent(CustomerIntent.UpdateFormField("notes", it)) },
            label = "Notes",
            modifier = Modifier.fillMaxWidth(),
        )

        // ── Credit Settings ──────────────────────────────────────────
        Spacer(Modifier.height(8.dp))
        Text("Credit Settings", style = MaterialTheme.typography.titleSmall)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Credit Enabled", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = form.creditEnabled,
                onCheckedChange = { onIntent(CustomerIntent.UpdateCreditEnabled(it)) },
            )
        }

        if (form.creditEnabled) {
            ZyntaTextField(
                value = form.creditLimit,
                onValueChange = { onIntent(CustomerIntent.UpdateFormField("creditLimit", it)) },
                label = "Credit Limit",
                error = form.validationErrors["creditLimit"],
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Walk-in Customer", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = form.isWalkIn,
                onCheckedChange = { onIntent(CustomerIntent.UpdateIsWalkIn(it)) },
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Save Button ──────────────────────────────────────────────
        ZyntaButton(
            text = if (isEditMode) "Update Customer" else "Create Customer",
            onClick = { onIntent(CustomerIntent.SaveCustomer) },
            isLoading = isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// History Tab — purchase history list
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistoryTab(
    orders: List<Order>,
    isLoading: Boolean,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> CircularProgressIndicator()
            orders.isEmpty() -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                ) {
                    Icon(
                        Icons.Default.ShoppingBag,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "No purchase history",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(ZyntaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            ) {
                item {
                    Text(
                        "${orders.size} order(s)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(orders, key = { it.id }) { order ->
                    PurchaseHistoryRow(order = order)
                }
            }
        }
    }
}

@Composable
private fun PurchaseHistoryRow(order: Order) {
    val localDate = order.createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
    val dateStr = "${localDate.date}"
    val statusColor = when (order.status) {
        OrderStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
        OrderStatus.REFUNDED, OrderStatus.VOIDED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    order.orderNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "%.2f".format(order.total),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${order.items.size} item(s) · $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    order.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                )
            }
        }
    }
}
