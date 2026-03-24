package com.zyntasolutions.zyntapos.feature.customers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaLoadingSkeleton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaLoyaltyTierBadge
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaSearchBar
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.CustomerGroup
import com.zyntasolutions.zyntapos.domain.model.LoyaltyTier

/**
 * Customer directory screen — search, group filter, sortable list.
 *
 * Stateless: receives [state] from [CustomerViewModel] and dispatches [onIntent].
 * Navigation callbacks are forwarded from [CustomerEffect] at the caller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerListScreen(
    state: CustomerState,
    onIntent: (CustomerIntent) -> Unit,
    onNavigateToDetail: (String?) -> Unit,
    onNavigateToGroups: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    LaunchedEffect(Unit) {
        onIntent(CustomerIntent.LoadCustomers)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customers") },
                actions = {
                    IconButton(onClick = onNavigateToGroups) {
                        Icon(Icons.Filled.Group, contentDescription = "Customer Groups")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigateToDetail(null) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New Customer") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // ── Search bar ─────────────────────────────────────────────────
            ZyntaSearchBar(
                query = state.searchQuery,
                onQueryChange = { onIntent(CustomerIntent.SearchQueryChanged(it)) },
                onClear = { onIntent(CustomerIntent.SearchQueryChanged("")) },
                onScanToggle = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // ── Group filter chips ─────────────────────────────────────────
            if (state.customerGroups.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedGroupId == null,
                            onClick = { onIntent(CustomerIntent.FilterByGroup(null)) },
                            label = { Text("All") },
                        )
                    }
                    items(state.customerGroups) { group ->
                        FilterChip(
                            selected = state.selectedGroupId == group.id,
                            onClick = { onIntent(CustomerIntent.FilterByGroup(group.id)) },
                            label = { Text(group.name) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Content ────────────────────────────────────────────────────
            when {
                state.isLoading -> ZyntaLoadingSkeleton(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                )
                state.customers.isEmpty() -> ZyntaEmptyState(
                    title = "No Customers Found",
                    subtitle = if (state.searchQuery.isNotBlank()) {
                        "No results for \"${state.searchQuery}\""
                    } else {
                        "Add your first customer using the + button"
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                else -> CustomerTable(
                    customers = state.customers,
                    groups = state.customerGroups,
                    currentLoyaltyTier = state.currentLoyaltyTier,
                    sortColumn = state.sortColumn,
                    sortDirection = state.sortDirection,
                    onSortColumn = { onIntent(CustomerIntent.SortByColumn(it)) },
                    onCustomerClick = { onNavigateToDetail(it.id) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun CustomerTable(
    customers: List<Customer>,
    groups: List<CustomerGroup>,
    currentLoyaltyTier: LoyaltyTier?,
    sortColumn: String,
    sortDirection: SortDir,
    onSortColumn: (String) -> Unit,
    onCustomerClick: (Customer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groupMap = remember(groups) { groups.associateBy { it.id } }

    LazyColumn(modifier = modifier) {
        // Header row
        item {
            CustomerTableHeader(
                sortColumn = sortColumn,
                sortDirection = sortDirection,
                onSort = onSortColumn,
            )
            HorizontalDivider()
        }
        items(customers, key = { it.id }) { customer ->
            CustomerTableRow(
                customer = customer,
                groupName = groupMap[customer.groupId]?.name,
                loyaltyTier = currentLoyaltyTier,
                onClick = { onCustomerClick(customer) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun CustomerTableHeader(
    sortColumn: String,
    sortDirection: SortDir,
    onSort: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SortableHeaderCell("Name", "name", sortColumn, sortDirection, onSort, Modifier.weight(2f))
        SortableHeaderCell("Phone", "phone", sortColumn, sortDirection, onSort, Modifier.weight(1.5f))
        SortableHeaderCell("Points", "points", sortColumn, sortDirection, onSort, Modifier.weight(1f))
        Text("Group", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1.5f))
    }
}

@Composable
private fun SortableHeaderCell(
    label: String,
    columnKey: String,
    activeColumn: String,
    direction: SortDir,
    onSort: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = activeColumn == columnKey
    Row(
        modifier = modifier.clickable { onSort(columnKey) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label + if (isActive) (if (direction == SortDir.ASC) " ↑" else " ↓") else "",
            style = MaterialTheme.typography.labelMedium,
            color = if (isActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CustomerTableRow(
    customer: Customer,
    groupName: String?,
    loyaltyTier: LoyaltyTier?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(2f)) {
            Text(customer.name, style = MaterialTheme.typography.bodyMedium)
            if (!customer.email.isNullOrBlank()) {
                Text(
                    customer.email!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(customer.phone, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.5f))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(customer.loyaltyPoints.toString(), style = MaterialTheme.typography.bodySmall)
            if (loyaltyTier != null) {
                ZyntaLoyaltyTierBadge(tierName = loyaltyTier.name)
            }
        }
        Text(groupName ?: "\u2014", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.5f))
    }
}
