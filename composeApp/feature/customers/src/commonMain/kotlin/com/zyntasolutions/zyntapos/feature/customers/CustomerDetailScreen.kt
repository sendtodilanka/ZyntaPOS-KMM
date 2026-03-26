package com.zyntasolutions.zyntapos.feature.customers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaLoadingOverlay
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaLoyaltyTierBadge
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTextField
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.Customer
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
    val s = LocalStrings.current
    LaunchedEffect(customerId) {
        onIntent(CustomerIntent.SelectCustomer(customerId))
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
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
                        Text(if (isEditMode) s[StringResource.CUSTOMERS_EDIT] else s[StringResource.CUSTOMERS_NEW])
                        if (isEditMode && state.currentLoyaltyTier != null) {
                            Spacer(Modifier.width(8.dp))
                            ZyntaLoyaltyTierBadge(tierName = state.currentLoyaltyTier.name)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = s[StringResource.COMMON_BACK])
                    }
                },
                actions = {
                    if (isEditMode && state.selectedCustomer != null) {
                        // GDPR data export (G10)
                        IconButton(
                            onClick = { onIntent(CustomerIntent.ExportCustomerData(state.selectedCustomer.id)) },
                            enabled = !state.isExporting,
                        ) {
                            Icon(Icons.Filled.FileDownload, contentDescription = s[StringResource.CUSTOMERS_EXPORT_GDPR])
                        }
                        // Merge customer (C4.3)
                        if (!state.selectedCustomer.isWalkIn) {
                            IconButton(onClick = {
                                onIntent(CustomerIntent.LoadCustomers)
                                showMergeDialog = true
                            }) {
                                Icon(Icons.Filled.CallMerge, contentDescription = s[StringResource.CUSTOMERS_MERGE])
                            }
                        }
                        IconButton(onClick = {
                            onNavigateToWallet(state.selectedCustomer.id)
                        }) {
                            Icon(Icons.Filled.AccountBalanceWallet, contentDescription = s[StringResource.CUSTOMERS_WALLET])
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = s[StringResource.COMMON_DELETE], tint = MaterialTheme.colorScheme.error)
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
                            text = { Text(s[StringResource.CUSTOMERS_PROFILE]) },
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(s[StringResource.CUSTOMERS_HISTORY])
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

    // ── Merge Customer Dialog ──────────────────────────────────────────────────
    if (showMergeDialog && state.selectedCustomer != null) {
        MergeCustomerDialog(
            targetCustomer = state.selectedCustomer,
            candidates = state.customers.filter {
                it.id != state.selectedCustomer.id && !it.isWalkIn
            },
            isLoading = state.isLoading,
            onMerge = { sourceId ->
                onIntent(CustomerIntent.MergeCustomers(
                    targetId = state.selectedCustomer.id,
                    sourceId = sourceId,
                ))
                showMergeDialog = false
            },
            onDismiss = { showMergeDialog = false },
        )
    }

    // ── Delete Confirmation Dialog ─────────────────────────────────────────────
    if (showDeleteDialog && state.selectedCustomer != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(s[StringResource.CUSTOMERS_DELETE]) },
            text = { Text("Delete ${state.selectedCustomer.name}? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onIntent(CustomerIntent.DeleteCustomer(state.selectedCustomer.id))
                    },
                ) {
                    Text(s[StringResource.COMMON_DELETE], color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(s[StringResource.COMMON_CANCEL])
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
    val s = LocalStrings.current
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
            label = s[StringResource.CUSTOMERS_FULL_NAME],
            error = form.validationErrors["name"],
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaTextField(
            value = form.phone,
            onValueChange = { onIntent(CustomerIntent.UpdateFormField("phone", it)) },
            label = s[StringResource.CUSTOMERS_PHONE],
            error = form.validationErrors["phone"],
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaTextField(
            value = form.email,
            onValueChange = { onIntent(CustomerIntent.UpdateFormField("email", it)) },
            label = s[StringResource.CUSTOMERS_EMAIL],
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaTextField(
            value = form.address,
            onValueChange = { onIntent(CustomerIntent.UpdateFormField("address", it)) },
            label = s[StringResource.CUSTOMERS_ADDRESS],
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
                label = s[StringResource.CUSTOMERS_GENDER],
                modifier = Modifier.weight(1f),
            )
            ZyntaTextField(
                value = form.birthday,
                onValueChange = { onIntent(CustomerIntent.UpdateFormField("birthday", it)) },
                label = s[StringResource.CUSTOMERS_BIRTHDAY],
                modifier = Modifier.weight(1f),
            )
        }

        ZyntaTextField(
            value = form.notes,
            onValueChange = { onIntent(CustomerIntent.UpdateFormField("notes", it)) },
            label = s[StringResource.CUSTOMERS_NOTES],
            modifier = Modifier.fillMaxWidth(),
        )

        // ── Credit Settings ──────────────────────────────────────────
        Spacer(Modifier.height(8.dp))
        Text(s[StringResource.CUSTOMERS_CREDIT_SETTINGS], style = MaterialTheme.typography.titleSmall)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s[StringResource.CUSTOMERS_CREDIT_ENABLED], style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = form.creditEnabled,
                onCheckedChange = { onIntent(CustomerIntent.UpdateCreditEnabled(it)) },
            )
        }

        if (form.creditEnabled) {
            ZyntaTextField(
                value = form.creditLimit,
                onValueChange = { onIntent(CustomerIntent.UpdateFormField("creditLimit", it)) },
                label = s[StringResource.CUSTOMERS_CREDIT_LIMIT],
                error = form.validationErrors["creditLimit"],
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s[StringResource.CUSTOMERS_WALK_IN], style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = form.isWalkIn,
                onCheckedChange = { onIntent(CustomerIntent.UpdateIsWalkIn(it)) },
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Save Button ──────────────────────────────────────────────
        ZyntaButton(
            text = if (isEditMode) s[StringResource.CUSTOMERS_UPDATE] else s[StringResource.CUSTOMERS_CREATE],
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
    val s = LocalStrings.current
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
                        s[StringResource.CUSTOMERS_NO_HISTORY],
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
        OrderStatus.VOIDED -> MaterialTheme.colorScheme.error
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

// ─────────────────────────────────────────────────────────────────────────────
// MergeCustomerDialog — select a source customer to merge into the target (C4.3)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Dialog for merging two customer profiles.
 *
 * Displays a searchable list of candidates (all non-walk-in customers except
 * the merge target). On selection shows a confirmation step before dispatching
 * [CustomerIntent.MergeCustomers].
 *
 * **Merge semantics (performed by [MergeCustomersUseCase]):**
 * - Source customer loyalty points, wallet balance, and order history are
 *   combined with the target customer.
 * - Source customer is soft-deleted after merge.
 *
 * @param targetCustomer  The customer that will receive merged data.
 * @param candidates      Filterable list of source customer candidates.
 * @param isLoading       True while candidates are loading.
 * @param onMerge         Called with the selected source customer ID.
 * @param onDismiss       Dismissal callback.
 */
@Composable
private fun MergeCustomerDialog(
    targetCustomer: Customer,
    candidates: List<Customer>,
    isLoading: Boolean,
    onMerge: (sourceId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf<Customer?>(null) }

    val filtered = if (searchQuery.isBlank()) {
        candidates
    } else {
        val q = searchQuery.lowercase()
        candidates.filter {
            it.name.lowercase().contains(q) ||
                it.phone.contains(q) ||
                it.email?.lowercase()?.contains(q) == true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (selectedSource == null) s[StringResource.CUSTOMERS_MERGE] else s[StringResource.CUSTOMERS_CONFIRM_MERGE],
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            if (selectedSource != null) {
                // ── Step 2: Confirmation ──────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                    Text(
                        "Merge \"${selectedSource!!.name}\" into \"${targetCustomer.name}\"?",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            "• \"${selectedSource!!.name}\" will be deleted after merge\n" +
                                "• Loyalty points and wallet balance will be combined\n" +
                                "• This action cannot be undone",
                            modifier = Modifier.padding(ZyntaSpacing.sm),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            } else {
                // ── Step 1: Customer search ───────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                    Text(
                        "Select a customer to merge into \"${targetCustomer.name}\":",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by name, phone, email…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    when {
                        isLoading -> Box(
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }
                        filtered.isEmpty() -> Text(
                            s[StringResource.CUSTOMERS_NO_MATCHING],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                            items(filtered.take(10), key = { it.id }) { candidate ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .clickable { selectedSource = candidate },
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = MaterialTheme.shapes.small,
                                ) {
                                    Column(
                                        modifier = Modifier.padding(
                                            horizontal = ZyntaSpacing.sm,
                                            vertical = 6.dp,
                                        ),
                                    ) {
                                        Text(
                                            candidate.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        if (candidate.phone.isNotBlank()) {
                                            Text(
                                                candidate.phone,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedSource != null) {
                TextButton(
                    onClick = { onMerge(selectedSource!!.id) },
                ) {
                    Text(s[StringResource.CUSTOMERS_MERGE], color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (selectedSource != null) selectedSource = null
                    else onDismiss()
                },
            ) {
                Text(if (selectedSource != null) s[StringResource.COMMON_BACK] else s[StringResource.COMMON_CANCEL])
            }
        },
    )
}
