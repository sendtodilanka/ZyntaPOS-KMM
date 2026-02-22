package com.zyntasolutions.zyntapos.feature.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.components.SortDirection
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaSearchBar
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTable
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTableColumn
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.designsystem.util.WindowSize
import com.zyntasolutions.zyntapos.designsystem.util.currentWindowSize
import com.zyntasolutions.zyntapos.domain.model.Supplier

/**
 * Supplier list management screen — Sprint 19, Step 10.1.9.
 *
 * Renders a searchable [ZyntaTable] of all active suppliers with columns for
 * name, contact person, phone, and email. A FAB opens the supplier detail
 * screen for creating a new supplier.
 *
 * ### Layout
 * - **Expanded:** Full-width table with all columns visible.
 * - **Medium:** Table with name + phone + email columns.
 * - **Compact:** Card list (name + phone only).
 *
 * ### Search
 * Real-time client-side filter on name, contactPerson, phone, and email fields.
 *
 * @param suppliers              Full list of active suppliers from domain layer.
 * @param isLoading              True while async load is in-flight.
 * @param onNavigateToDetail     Callback with supplier ID (edit) or null (create).
 * @param modifier               Optional root modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierListScreen(
    suppliers: List<Supplier>,
    isLoading: Boolean = false,
    onNavigateToDetail: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowSize = currentWindowSize()

    // ── Local search state ────────────────────────────────────────────────
    var searchQuery by remember { mutableStateOf("") }
    val filteredSuppliers = remember(suppliers, searchQuery) {
        if (searchQuery.isBlank()) suppliers
        else {
            val q = searchQuery.lowercase()
            suppliers.filter { s ->
                s.name.lowercase().contains(q) ||
                    s.contactPerson?.lowercase()?.contains(q) == true ||
                    s.phone?.lowercase()?.contains(q) == true ||
                    s.email?.lowercase()?.contains(q) == true
            }
        }
    }

    // ── Sort state ────────────────────────────────────────────────────────
    var sortColumnKey by remember { mutableStateOf("name") }
    var sortDir by remember { mutableStateOf(SortDir.ASC) }
    val sortedSuppliers = remember(filteredSuppliers, sortColumnKey, sortDir) {
        val comparator: Comparator<Supplier> = when (sortColumnKey) {
            "contactPerson" -> compareBy { it.contactPerson ?: "" }
            "phone" -> compareBy { it.phone ?: "" }
            "email" -> compareBy { it.email ?: "" }
            else -> compareBy { it.name }
        }
        if (sortDir == SortDir.ASC) filteredSuppliers.sortedWith(comparator)
        else filteredSuppliers.sortedWith(comparator.reversed())
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Suppliers") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigateToDetail(null) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Supplier") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = ZyntaSpacing.md),
        ) {
            Spacer(Modifier.height(ZyntaSpacing.sm))
            // ── Search Bar ────────────────────────────────────────────────
            ZyntaSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onClear = { searchQuery = "" },
                onScanToggle = {},
                placeholder = "Search suppliers by name, contact, phone…",
            )
            Spacer(Modifier.height(ZyntaSpacing.sm))

            // ── Content ───────────────────────────────────────────────────
            if (windowSize == WindowSize.COMPACT) {
                SupplierCardList(
                    suppliers = sortedSuppliers,
                    isLoading = isLoading,
                    onSupplierClick = { onNavigateToDetail(it.id) },
                )
            } else {
                SupplierTableView(
                    suppliers = sortedSuppliers,
                    isLoading = isLoading,
                    sortColumnKey = sortColumnKey,
                    sortDir = sortDir,
                    windowSize = windowSize,
                    onSort = { key ->
                        if (key == sortColumnKey) {
                            sortDir = if (sortDir == SortDir.ASC) SortDir.DESC else SortDir.ASC
                        } else {
                            sortColumnKey = key
                            sortDir = SortDir.ASC
                        }
                    },
                    onSupplierClick = { onNavigateToDetail(it.id) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private: Table View
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupplierTableView(
    suppliers: List<Supplier>,
    isLoading: Boolean,
    sortColumnKey: String,
    sortDir: SortDir,
    windowSize: WindowSize,
    onSort: (String) -> Unit,
    onSupplierClick: (Supplier) -> Unit,
) {
    val columns = buildList {
        add(ZyntaTableColumn("name", "Supplier Name", weight = 2f))
        if (windowSize == WindowSize.EXPANDED)
            add(ZyntaTableColumn("contactPerson", "Contact", weight = 1.5f))
        add(ZyntaTableColumn("phone", "Phone", weight = 1.4f))
        add(ZyntaTableColumn("email", "Email", weight = 1.8f))
    }

    val tableSortDir = if (sortDir == SortDir.ASC) SortDirection.Ascending else SortDirection.Descending

    ZyntaTable(
        columns = columns,
        items = suppliers,
        sortColumnKey = sortColumnKey,
        sortDirection = tableSortDir,
        onSort = onSort,
        isLoading = isLoading,
        modifier = Modifier.fillMaxSize(),
        rowKey = { it.id },
        emptyContent = { ZyntaEmptyState(
                    title = "No suppliers found",
                    icon = Icons.Default.Business,
                    subtitle = "Add your first supplier using the + button",
                ) },
        rowContent = { supplier: Supplier ->
        Text(
            supplier.name,
            modifier = Modifier.weight(2f).clickable { onSupplierClick(supplier) },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (windowSize == WindowSize.EXPANDED) {
            Text(
                supplier.contactPerson ?: "—",
                modifier = Modifier.weight(1.5f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            supplier.phone ?: "—",
            modifier = Modifier.weight(1.4f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            supplier.email ?: "—",
            modifier = Modifier.weight(1.8f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    },
)
}

// ─────────────────────────────────────────────────────────────────────────────
// Private: Card List (compact)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SupplierCardList(
    suppliers: List<Supplier>,
    isLoading: Boolean,
    onSupplierClick: (Supplier) -> Unit,
) {
    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (suppliers.isEmpty()) { ZyntaEmptyState(
                    title = "No suppliers found",
                    icon = Icons.Default.Business,
                    subtitle = "Add your first supplier using the + button",
                ); return }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
    ) {
        items(suppliers, key = { it.id }) { supplier ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSupplierClick(supplier) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Row(
                    modifier = Modifier.padding(ZyntaSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Business, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.width(ZyntaSpacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(supplier.name, style = MaterialTheme.typography.titleSmall)
                        supplier.contactPerson?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        supplier.phone?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

