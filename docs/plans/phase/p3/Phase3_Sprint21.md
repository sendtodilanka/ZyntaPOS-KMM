# ZyntaPOS — Phase 3 Sprint 21: Warehouse Racks Manager

> **Document ID:** ZYNTA-PLAN-PHASE3-SPRINT21-v1.0
> **Phase:** 3 — Enterprise (Months 13–18)
> **Sprint:** 21 of 24 | Week 21
> **Module(s):** `:shared:data`, `:composeApp:feature:multistore`
> **Author:** Senior KMP Architect & Lead Engineer
> **Reference:** ZYNTA-MASTER-PLAN-v1.0 | ADR-002

---

## Goal

Implement the warehouse racks manager: DB migration v7 (adds `rack_id` column to `stock_entries`), full rack CRUD screens embedded in the multi-store feature, product-to-rack assignment, and printable pick list generation. Uses `GeneratePickListUseCase` (Sprint 4 interface) to resolve warehouse locations.

---

## Database Migration

### Migration `7.sqm`

**Location:** `shared/data/src/commonMain/sqldelight/com/zyntasolutions/zyntapos/db/migrations/7.sqm`

```sql
-- Migration 7: Add rack_id to stock_entries for warehouse rack assignment
-- Phase 3 Sprint 21
-- Previous version: 6

ALTER TABLE stock_entries ADD COLUMN rack_id TEXT REFERENCES warehouse_racks(id);

-- Index for rack-based stock queries
CREATE INDEX IF NOT EXISTS idx_stock_entries_rack
    ON stock_entries(rack_id)
    WHERE rack_id IS NOT NULL;
```

**Note:** `ALTER TABLE ADD COLUMN` with `DEFAULT NULL` is a zero-downtime SQLite operation — existing rows get `rack_id = NULL` automatically.

---

## New Use Case Implementations

**Location:** `shared/domain/src/commonMain/kotlin/com/zyntasolutions/zyntapos/domain/usecase/warehouse/`

### `GetProductLocationUseCaseImpl.kt`

```kotlin
package com.zyntasolutions.zyntapos.domain.usecase.warehouse

import com.zyntasolutions.zyntapos.domain.model.ProductLocation
import com.zyntasolutions.zyntapos.domain.repository.StockRepository
import com.zyntasolutions.zyntapos.domain.repository.WarehouseRackRepository

class GetProductLocationUseCaseImpl(
    private val stockRepository: StockRepository,
    private val rackRepository: WarehouseRackRepository
) : GetProductLocationUseCase {

    override suspend fun invoke(productId: String, warehouseId: String): ProductLocation? {
        val stock = stockRepository.getByProductAndWarehouse(productId, warehouseId) ?: return null
        val rack = stock.rackId?.let { rackRepository.getById(it) }
        return ProductLocation(
            productId     = productId,
            productName   = stock.productName,
            warehouseId   = warehouseId,
            warehouseName = stock.warehouseName,
            rackId        = rack?.id,
            rackName      = rack?.name,
            quantity      = stock.quantity,
            lastUpdated   = stock.updatedAt
        )
    }
}
```

### `GeneratePickListUseCaseImpl.kt`

```kotlin
package com.zyntasolutions.zyntapos.domain.usecase.warehouse

import com.zyntasolutions.zyntapos.domain.model.PickListItem
import com.zyntasolutions.zyntapos.domain.repository.StockRepository
import com.zyntasolutions.zyntapos.domain.repository.WarehouseRackRepository

/**
 * Generates a pick list for a set of product-quantity pairs within a warehouse.
 *
 * Algorithm:
 * 1. For each (productId, requestedQty) pair:
 *    a. Query stock_entries for product in warehouse (with rack info)
 *    b. Create PickListItem with rack name and availability
 * 2. Sort by rack name (alphanumeric) to optimise physical pick path:
 *    "A1", "A2", "B1", "B2", ... (alphabetical + numeric sort)
 * 3. Return sorted list
 */
class GeneratePickListUseCaseImpl(
    private val stockRepository: StockRepository,
    private val rackRepository: WarehouseRackRepository
) : GeneratePickListUseCase {

    override suspend fun invoke(
        requests: List<Pair<String, Double>>,
        warehouseId: String
    ): List<PickListItem> {
        return requests.mapNotNull { (productId, requestedQty) ->
            val stock = stockRepository.getByProductAndWarehouse(productId, warehouseId)
                ?: return@mapNotNull null    // product not found in this warehouse

            val rack = stock.rackId?.let { rackRepository.getById(it) }

            PickListItem(
                productId         = productId,
                productName       = stock.productName,
                sku               = stock.sku,
                requestedQuantity = requestedQty,
                availableQuantity = stock.quantity,
                rackName          = rack?.name,
                warehouseId       = warehouseId,
                warehouseName     = stock.warehouseName
            )
        }.sortedWith(
            compareBy(
                { it.rackName?.firstOrNull()?.code ?: Int.MAX_VALUE },   // alphabetical first letter
                { extractRackNumber(it.rackName) }                        // then numeric part
            )
        )
    }

    private fun extractRackNumber(rackName: String?): Int {
        if (rackName == null) return Int.MAX_VALUE
        return rackName.filter { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
    }
}
```

---

## New Screen Files

**Location:** `composeApp/feature/multistore/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/multistore/screen/`

```
screen/
├── WarehouseRackListScreen.kt         # Grid of racks for a warehouse
├── WarehouseRackDetailScreen.kt       # Rack detail + product list in rack
├── ProductLocationPickerScreen.kt     # Assign a rack to a product (dialog-style)
└── PickListScreen.kt                  # Printable pick list
```

---

## Rack List Screen

### `WarehouseRackListScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.multistore.screen

/**
 * Warehouse rack list screen.
 *
 * Layout:
 * - Warehouse name in top bar
 * - Rack grid (2 columns of RackCard):
 *   Each card: rack name (large, bold), description, capacity (if set), product count
 * - FAB: "Add Rack" → opens RackFormDialog
 *
 * Rack card colors:
 * - Green: capacity not set (unlimited) or used < 80%
 * - Amber: 80-99% full
 * - Red: at capacity (quantity >= capacity)
 *
 * Tap → WarehouseRackDetailScreen
 * Long-press → Delete rack confirmation (disabled if rack has products assigned)
 */
@Composable
fun WarehouseRackListScreen(
    warehouseId: String,
    warehouseName: String,
    viewModel: MultistoreViewModel,     // extends existing multistore ViewModel
    onNavigateToDetail: (rackId: String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(warehouseId) {
        viewModel.handleIntent(MultistoreIntent.LoadWarehouseRacks(warehouseId))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$warehouseName — Racks") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { /* back */ } }
            )
        },
        floatingActionButton = {
            ZyntaFab(text = "Add Rack", onClick = { showAddDialog = true })
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier.padding(padding)
        ) {
            items(
                state.warehouseRacks.sortedBy { it.name },
                key = { it.id }
            ) { rack ->
                val productCount = state.rackProductCounts[rack.id] ?: 0
                RackCard(
                    rack = rack,
                    productCount = productCount,
                    onClick = { onNavigateToDetail(rack.id) },
                    onLongClick = { deleteTarget = rack.id }
                )
            }
        }
    }

    if (showAddDialog) {
        RackFormDialog(
            warehouseId = warehouseId,
            existing = null,
            onSave = { rack ->
                viewModel.handleIntent(MultistoreIntent.SaveWarehouseRack(rack))
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    deleteTarget?.let { rackId ->
        val rack = state.warehouseRacks.find { it.id == rackId }
        val count = state.rackProductCounts[rackId] ?: 0
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Rack?") },
            text = {
                if (count > 0)
                    Text("Cannot delete rack '${rack?.name}' — it has $count product(s) assigned. Reassign products first.")
                else
                    Text("Delete rack '${rack?.name}'? This cannot be undone.")
            },
            confirmButton = {
                if (count == 0) {
                    TextButton(onClick = {
                        viewModel.handleIntent(MultistoreIntent.DeleteWarehouseRack(rackId))
                        deleteTarget = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(if (count > 0) "OK" else "Cancel") } }
        )
    }
}

@Composable
private fun RackCard(
    rack: com.zyntasolutions.zyntapos.domain.model.WarehouseRack,
    productCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fillPercent = rack.capacity?.let { cap ->
        if (cap > 0) (productCount.toFloat() / cap) else 0f
    }
    val cardColor = when {
        fillPercent == null || fillPercent < 0.8f -> MaterialTheme.colorScheme.primaryContainer
        fillPercent < 1f                          -> Color(0xFFFEF3C7)   // amber
        else                                      -> Color(0xFFFEE2E2)   // red
    }

    ZyntaCard(
        onClick = onClick,
        onLongClick = onLongClick,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        modifier = modifier.fillMaxWidth().height(120.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(rack.name, style = MaterialTheme.typography.headlineMedium)
            rack.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(
                text = "$productCount products${rack.capacity?.let { " / $it capacity" } ?: ""}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
```

---

## Rack Detail Screen

### `WarehouseRackDetailScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.multistore.screen

/**
 * Rack detail screen.
 *
 * Shows all products stored in this rack with their quantities.
 * Products can be re-assigned to a different rack from here.
 * "Generate Pick List" button for selected products.
 *
 * Layout:
 * - Rack name + description header card
 * - Capacity bar (if capacity set)
 * - Product list: name | SKU | quantity | "Move" button
 * - FAB: "Generate Pick List" for all products in rack
 */
@Composable
fun WarehouseRackDetailScreen(
    rackId: String,
    viewModel: MultistoreViewModel,
    onNavigateToPickList: (warehouseId: String, requests: String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
)
```

---

## Product Location Picker

### `ProductLocationPickerScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.multistore.screen

/**
 * Assign a rack to a product (shown as a bottom sheet from inventory or rack detail).
 *
 * Layout:
 * - Product name (read-only header)
 * - Current rack assignment (if any)
 * - Rack list (same warehouse): selectable rows
 * - "Assign" button
 * - "Remove from rack" option (sets rack_id = NULL)
 *
 * On assign: calls SetProductRack use case → updates stock_entries.rack_id
 */
@Composable
fun ProductLocationPickerScreen(
    productId: String,
    warehouseId: String,
    viewModel: MultistoreViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
)
```

---

## Pick List Screen

### `PickListScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.multistore.screen

/**
 * Printable pick list screen.
 *
 * Layout:
 * - Header: warehouse name + date + generated-by user
 * - Pick list table:
 *   Rack | Product Name | SKU | Requested Qty | Available Qty | Status
 *   Sorted by rack name (optimised pick path)
 * - Items with canFulfill=false shown in red with ⚠ warning
 * - Footer: total items + fulfillable count
 * - "Print" button → ReceiptPrinterPort.printPickList(items)
 * - "Export CSV" button
 *
 * RBAC: MANAGE_STOCK permission
 */
@Composable
fun PickListScreen(
    warehouseId: String,
    requests: List<Pair<String, Double>>,    // (productId, qty) pairs
    viewModel: MultistoreViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(warehouseId, requests) {
        viewModel.handleIntent(MultistoreIntent.GeneratePickList(warehouseId, requests))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pick List") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { /* back */ } },
                actions = {
                    IconButton(onClick = { /* print */ }) { /* print icon */ }
                    IconButton(onClick = { /* CSV export */ }) { /* export icon */ }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier.padding(padding)
        ) {
            // Header
            item {
                PickListHeader(warehouseId = warehouseId)
            }

            // Column headers
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Rack", Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall)
                    Text("Product", Modifier.weight(2f), style = MaterialTheme.typography.labelSmall)
                    Text("SKU", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    Text("Req.", Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall)
                    Text("Avail.", Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall)
                }
            }

            // Pick list items
            items(state.pickList, key = { it.productId }) { item ->
                PickListRow(item = item)
            }

            // Summary footer
            item {
                val fulfillable = state.pickList.count { it.canFulfill }
                val total = state.pickList.size
                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = "Total: $total items | Fulfillable: $fulfillable | Not available: ${total - fulfillable}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PickListRow(
    item: com.zyntasolutions.zyntapos.domain.model.PickListItem,
    modifier: Modifier = Modifier
) {
    val bgColor = if (!item.canFulfill) Color(0xFFFEE2E2) else MaterialTheme.colorScheme.surface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(item.rackName ?: "—", Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall)
        Text(item.productName, Modifier.weight(2f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
        Text(item.sku ?: "—", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text("${item.requestedQuantity.toInt()}", Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall)
        Text(
            text = "${item.availableQuantity.toInt()}${if (!item.canFulfill) " ⚠" else ""}",
            modifier = Modifier.weight(0.8f),
            style = MaterialTheme.typography.bodySmall,
            color = if (!item.canFulfill) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}
```

---

## MultistoreViewModel & State Updates

Add rack-related state and intents to the existing multistore feature:

```kotlin
// Additions to MultistoreState:
val warehouseRacks: List<WarehouseRack> = emptyList()
val rackProductCounts: Map<String, Int> = emptyMap()
val pickList: List<PickListItem> = emptyList()

// Additions to MultistoreIntent:
data class LoadWarehouseRacks(val warehouseId: String) : MultistoreIntent
data class SaveWarehouseRack(val rack: WarehouseRack) : MultistoreIntent
data class DeleteWarehouseRack(val rackId: String) : MultistoreIntent
data class GeneratePickList(val warehouseId: String, val requests: List<Pair<String, Double>>) : MultistoreIntent
data class AssignProductToRack(val productId: String, val warehouseId: String, val rackId: String?) : MultistoreIntent
```

---

## Navigation

```kotlin
// In MainNavGraph.kt
composable<ZyntaRoute.WarehouseRackList> { backStackEntry ->
    val route: ZyntaRoute.WarehouseRackList = backStackEntry.toRoute()
    WarehouseRackListScreen(
        warehouseId = route.warehouseId,
        warehouseName = "Warehouse",  // loaded from state
        viewModel = koinViewModel<MultistoreViewModel>(),
        onNavigateToDetail = { id -> navController.navigate(ZyntaRoute.WarehouseRackDetail(id)) },
        onNavigateBack = { navController.popBackStack() }
    )
}
composable<ZyntaRoute.WarehouseRackDetail> { backStackEntry ->
    val route: ZyntaRoute.WarehouseRackDetail = backStackEntry.toRoute()
    WarehouseRackDetailScreen(/* ... */)
}
```

---

## Tasks

- [ ] **21.1** Write `7.sqm` migration: `ALTER TABLE stock_entries ADD COLUMN rack_id TEXT`
- [ ] **21.2** Update `DatabaseMigrations.kt` to apply migration 7
- [ ] **21.3** Implement `GetProductLocationUseCaseImpl.kt`
- [ ] **21.4** Implement `GeneratePickListUseCaseImpl.kt` with rack-name sort (alphanumeric)
- [ ] **21.5** Update `WarehouseRackRepositoryImpl.kt` to support `getById()` (Sprint 7 stub)
- [ ] **21.6** Implement `WarehouseRackListScreen.kt` with 2-column grid and add/delete actions
- [ ] **21.7** Implement `WarehouseRackDetailScreen.kt` with product list
- [ ] **21.8** Implement `ProductLocationPickerScreen.kt` (bottom sheet)
- [ ] **21.9** Implement `PickListScreen.kt` with sorted table and print/export actions
- [ ] **21.10** Add rack intents/state to `MultistoreViewModel`
- [ ] **21.11** Wire `WarehouseRackList`, `WarehouseRackDetail` routes in `MainNavGraph.kt`
- [ ] **21.12** Write `GeneratePickListUseCaseTest` — test sorting, canFulfill flag, missing products
- [ ] **21.13** Verify: `./gradlew verifySqlDelightMigration && ./gradlew :shared:domain:test`

---

## Verification

```bash
./gradlew generateSqlDelightInterface
./gradlew verifySqlDelightMigration
./gradlew :shared:domain:test
./gradlew :composeApp:feature:multistore:assemble
```

---

## Definition of Done

- [ ] Migration `7.sqm` passes `verifySqlDelightMigration`
- [ ] `GeneratePickListUseCaseImpl` sorts items by rack name (A1, A2, B1, B2...)
- [ ] `PickListScreen` highlights unavailable items in red
- [ ] Rack CRUD works: add/edit/delete with product count guard on delete
- [ ] Product-to-rack assignment updates `stock_entries.rack_id`
- [ ] Pick list generation test passes (sorting + canFulfill flag)
- [ ] Commit: `feat(racks): add warehouse rack CRUD and pick list generation`
