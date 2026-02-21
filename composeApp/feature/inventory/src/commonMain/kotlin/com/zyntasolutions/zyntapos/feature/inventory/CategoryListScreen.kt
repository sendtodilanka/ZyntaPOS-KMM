package com.zyntasolutions.zyntapos.feature.inventory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import com.zyntasolutions.zyntapos.designsystem.tokens.ZentaSpacing
import com.zyntasolutions.zyntapos.domain.model.Category

/**
 * Category tree-view management screen — Sprint 19, Step 10.1.7.
 *
 * Renders the full category hierarchy as an indented [LazyColumn].
 * Root categories (parentId == null) are rendered at depth 0; their
 * children are rendered at depth 1, indented by [CHILD_INDENT_DP].
 *
 * ### Features
 * - Expand / collapse parent nodes via a chevron icon
 * - Edit icon on every row → [onNavigateToDetail] with the category ID
 * - FAB → [onNavigateToDetail] with null (new category)
 * - Empty state with call-to-action
 * - Loading skeleton while [isLoading] is true
 *
 * @param categories         Flat list of all categories (roots + children) from domain layer.
 * @param isLoading          True while async category load is in-flight.
 * @param onNavigateToDetail Callback invoked with category ID (edit) or null (create).
 * @param onDeleteCategory   Callback invoked with category ID to soft-delete.
 * @param modifier           Optional root modifier.
 */
@Composable
fun CategoryListScreen(
    categories: List<Category>,
    isLoading: Boolean = false,
    onNavigateToDetail: (String?) -> Unit,
    onDeleteCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Compute tree structure: roots and their children maps
    val roots = remember(categories) {
        categories.filter { it.parentId == null }.sortedBy { it.displayOrder }
    }
    val childrenMap = remember(categories) {
        categories
            .filter { it.parentId != null }
            .groupBy { it.parentId!! }
            .mapValues { (_, v) -> v.sortedBy { it.displayOrder } }
    }

    // Track which root IDs are expanded; all expanded by default
    val expandedIds = remember(roots) { mutableStateSetOf<String>().apply { addAll(roots.map { it.id }) } }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToDetail(null) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Category")
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                isLoading -> CategoryLoadingSkeleton()
                categories.isEmpty() -> CategoryEmptyState(onAdd = { onNavigateToDetail(null) })
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp), // FAB clearance
                ) {
                    roots.forEach { root ->
                        val children = childrenMap[root.id] ?: emptyList()
                        val isExpanded = root.id in expandedIds

                        // Root row
                        item(key = root.id) {
                            CategoryRow(
                                category = root,
                                depth = 0,
                                hasChildren = children.isNotEmpty(),
                                isExpanded = isExpanded,
                                onToggleExpand = {
                                    if (isExpanded) expandedIds.remove(root.id)
                                    else expandedIds.add(root.id)
                                },
                                onEdit = { onNavigateToDetail(root.id) },
                                onDelete = { onDeleteCategory(root.id) },
                            )
                        }

                        // Child rows (animated show/hide)
                        if (children.isNotEmpty()) {
                            item(key = "${root.id}_children") {
                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically(),
                                    exit = shrinkVertically(),
                                ) {
                                    Column {
                                        children.forEach { child ->
                                            CategoryRow(
                                                category = child,
                                                depth = 1,
                                                hasChildren = false,
                                                isExpanded = false,
                                                onToggleExpand = {},
                                                onEdit = { onNavigateToDetail(child.id) },
                                                onDelete = { onDeleteCategory(child.id) },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Divider after each root group
                        item(key = "${root.id}_divider") {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = ZentaSpacing.md),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private: Row composable
// ─────────────────────────────────────────────────────────────────────────────

private val CHILD_INDENT_DP = 32.dp

/**
 * A single category row in the tree list.
 *
 * @param category      The category data to display.
 * @param depth         Tree depth (0 = root, 1 = child). Drives left indent.
 * @param hasChildren   Whether this node has child categories.
 * @param isExpanded    Whether child rows are currently visible.
 * @param onToggleExpand Callback to toggle expand/collapse state.
 * @param onEdit        Callback to open the edit screen.
 * @param onDelete      Callback to soft-delete this category.
 */
@Composable
private fun CategoryRow(
    category: Category,
    depth: Int,
    hasChildren: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .padding(
                start = ZentaSpacing.md + (CHILD_INDENT_DP * depth),
                end = ZentaSpacing.sm,
                top = ZentaSpacing.sm,
                bottom = ZentaSpacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Expand / collapse toggle (only for root nodes with children)
        if (hasChildren) {
            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            // Spacer to align child rows with root rows that have toggle icons
            if (depth > 0) {
                Spacer(Modifier.width(28.dp))
            } else {
                Spacer(Modifier.width(28.dp))
            }
        }

        Spacer(Modifier.width(ZentaSpacing.xs))

        // Category icon
        Icon(
            imageVector = if (depth == 0) Icons.Default.Folder else Icons.Default.FolderOpen,
            contentDescription = null,
            tint = if (depth == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(20.dp),
        )

        Spacer(Modifier.width(ZentaSpacing.sm))

        // Category name + child count badge
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.name,
                style = if (depth == 0) MaterialTheme.typography.bodyLarge
                else MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (category.isActive) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            if (!category.isActive) {
                Text(
                    text = "Inactive",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        // Display order badge
        Text(
            text = "#${category.displayOrder}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = ZentaSpacing.sm),
        )

        // Edit icon
        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Edit ${category.name}",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        // Delete icon
        IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete ${category.name}",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Category?") },
            text = { Text("\"${category.name}\" will be deactivated. Active products in this category will be unaffected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private: Empty & Loading states
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CategoryEmptyState(onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Category,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(ZentaSpacing.md))
            Text("No categories yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(ZentaSpacing.xs))
            Text(
                "Create your first category to organise products",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(ZentaSpacing.lg))
            Button(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(ZentaSpacing.xs))
                Text("Add Category")
            }
        }
    }
}

@Composable
private fun CategoryLoadingSkeleton() {
    Column(Modifier.fillMaxSize().padding(ZentaSpacing.md)) {
        repeat(6) {
            Row(Modifier.fillMaxWidth().padding(vertical = ZentaSpacing.sm)) {
                Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small))
                Spacer(Modifier.width(ZentaSpacing.sm))
                Box(Modifier.height(20.dp).fillMaxWidth(0.6f).background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** Mutable set helper for [remember] in Compose. */
private fun <T> mutableStateSetOf(vararg elements: T): MutableSet<T> =
    mutableSetOf<T>().also { it.addAll(elements) }.let { set ->
        object : MutableSet<T> by set {
            override fun add(element: T) = set.add(element).also { }
            override fun remove(element: T) = set.remove(element).also { }
        }
    }
