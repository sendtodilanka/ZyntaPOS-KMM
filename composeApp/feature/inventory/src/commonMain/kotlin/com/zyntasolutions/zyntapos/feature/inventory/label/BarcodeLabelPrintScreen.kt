package com.zyntasolutions.zyntapos.feature.inventory.label

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.designsystem.util.WindowSize
import com.zyntasolutions.zyntapos.designsystem.util.currentWindowSize
import com.zyntasolutions.zyntapos.domain.model.LabelTemplate
import com.zyntasolutions.zyntapos.domain.model.Product

// ─────────────────────────────────────────────────────────────────────────────
// BarcodeLabelPrintScreen — Adaptive 3-panel batch label printing UI
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Adaptive label-print screen for the Barcode Label Printing Engine (Sprint 18).
 *
 * Layout adapts to window width:
 * - **COMPACT** (phone) — single scrollable column
 * - **MEDIUM** (tablet portrait) — two-column split
 * - **EXPANDED** (desktop / tablet landscape) — three-panel: template | queue | PDF preview
 *
 * @param state          MVI state snapshot from [BarcodeLabelPrintViewModel].
 * @param onIntent       Dispatches [BarcodeLabelPrintIntent] to the ViewModel.
 * @param onNavigateBack Back navigation callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeLabelPrintScreen(
    state: BarcodeLabelPrintState,
    onIntent: (BarcodeLabelPrintIntent) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val windowSize = currentWindowSize()

    // ── Template editor dialog ───────────────────────────────────────────────
    if (state.isTemplateEditorOpen) {
        TemplateEditorDialog(
            template = state.editingTemplate,
            onSave   = { onIntent(BarcodeLabelPrintIntent.SaveTemplate(it)) },
            onDismiss = { onIntent(BarcodeLabelPrintIntent.DismissTemplateEditor) },
        )
    }

    // ── Error snackbar ───────────────────────────────────────────────────────
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            onIntent(BarcodeLabelPrintIntent.DismissError)
        }
    }
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            onIntent(BarcodeLabelPrintIntent.DismissSuccess)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Print Labels") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.queue.isNotEmpty()) {
                        IconButton(onClick = { onIntent(BarcodeLabelPrintIntent.ClearQueue) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear queue")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (windowSize) {
            WindowSize.EXPANDED -> ExpandedLayout(state, onIntent, Modifier.padding(padding))
            WindowSize.MEDIUM   -> MediumLayout(state, onIntent, Modifier.padding(padding))
            WindowSize.COMPACT  -> CompactLayout(state, onIntent, Modifier.padding(padding))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Layout variants
// ─────────────────────────────────────────────────────────────────────────────

/** Desktop 3-panel: [Template | Queue | PDF Preview]. */
@Composable
private fun ExpandedLayout(
    state: BarcodeLabelPrintState,
    onIntent: (BarcodeLabelPrintIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxSize()) {
        // Panel 1 — Template selector (22%)
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.22f)
                .padding(ZyntaSpacing.sm)
                .verticalScroll(rememberScrollState()),
        ) {
            TemplateSelectorPanel(state, onIntent)
        }

        VerticalDivider()

        // Panel 2 — Product search + Queue (45%)
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.45f)
                .padding(ZyntaSpacing.sm),
        ) {
            ProductSearchPanel(state, onIntent, showPrintButton = false)
            Spacer(Modifier.height(ZyntaSpacing.sm))
            PrintQueuePanel(state, onIntent)
        }

        VerticalDivider()

        // Panel 3 — PDF Preview (33%)
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.33f)
                .padding(ZyntaSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PdfPreviewPanel(state, onIntent)
        }
    }
}

/** Tablet 2-panel: [Left: Template + Search | Right: Queue + Preview]. */
@Composable
private fun MediumLayout(
    state: BarcodeLabelPrintState,
    onIntent: (BarcodeLabelPrintIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.45f)
                .padding(ZyntaSpacing.sm),
        ) {
            TemplateSelectorPanel(state, onIntent)
            Spacer(Modifier.height(ZyntaSpacing.sm))
            ProductSearchPanel(state, onIntent, showPrintButton = false)
        }

        VerticalDivider()

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.55f)
                .padding(ZyntaSpacing.sm),
        ) {
            PrintQueuePanel(state, onIntent)
            Spacer(Modifier.height(ZyntaSpacing.sm))
            PrintActionsRow(state, onIntent)
        }
    }
}

/** Phone single column. */
@Composable
private fun CompactLayout(
    state: BarcodeLabelPrintState,
    onIntent: (BarcodeLabelPrintIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ZyntaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
    ) {
        Spacer(Modifier.height(ZyntaSpacing.xs))
        TemplateSelectorPanel(state, onIntent)
        HorizontalDivider()
        ProductSearchPanel(state, onIntent, showPrintButton = true)
        HorizontalDivider()
        PrintQueuePanel(state, onIntent)
        PrintActionsRow(state, onIntent)
        Spacer(Modifier.height(ZyntaSpacing.md))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Panel composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TemplateSelectorPanel(
    state: BarcodeLabelPrintState,
    onIntent: (BarcodeLabelPrintIntent) -> Unit,
) {
    Text("Label Template", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(ZyntaSpacing.xs))

    state.templates.forEach { template ->
        val isSelected = state.selectedTemplate?.id == template.id
        Surface(
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clickable { onIntent(BarcodeLabelPrintIntent.SelectTemplate(template)) },
        ) {
            Row(
                modifier = Modifier.padding(ZyntaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = if (template.paperType == LabelTemplate.PaperType.CONTINUOUS_ROLL)
                        "Roll ${template.paperWidthMm.toInt()}mm × ${template.labelHeightMm.toInt()}mm"
                    else
                        "A4 ${template.columns}×${template.rows}"
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!template.isDefault) {
                    IconButton(
                        onClick = { onIntent(BarcodeLabelPrintIntent.OpenEditTemplateEditor(template)) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(ZyntaSpacing.xs))
    OutlinedButton(
        onClick = { onIntent(BarcodeLabelPrintIntent.OpenNewTemplateEditor) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(ZyntaSpacing.xs))
        Text("New Template")
    }
}

@Composable
private fun ProductSearchPanel(
    state: BarcodeLabelPrintState,
    onIntent: (BarcodeLabelPrintIntent) -> Unit,
    showPrintButton: Boolean,
) {
    Text("Add Products", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(ZyntaSpacing.xs))

    OutlinedTextField(
        value = state.searchQuery,
        onValueChange = {
            onIntent(BarcodeLabelPrintIntent.SearchProducts(it))
        },
        placeholder = { Text("Search by name, barcode, SKU…") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (state.searchQuery.isNotBlank()) {{
            IconButton(onClick = { onIntent(BarcodeLabelPrintIntent.SearchProducts("")) }) {
                Icon(Icons.Default.Clear, contentDescription = "Clear search")
            }
        }} else null,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )

    if (state.products.isNotEmpty()) {
        Spacer(Modifier.height(ZyntaSpacing.xs))
        // Show up to 6 results inline
        state.products.take(6).forEach { product ->
            ProductSearchResultRow(product = product, onClick = {
                onIntent(BarcodeLabelPrintIntent.AddToQueue(product))
            })
        }
    }

    if (showPrintButton) {
        Spacer(Modifier.height(ZyntaSpacing.sm))
        PrintActionsRow(state, onIntent)
    }
}

@Composable
private fun ProductSearchResultRow(product: Product, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ZyntaSpacing.sm, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!product.sku.isNullOrBlank()) {
                    Text(
                        text = product.sku.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                Icons.Default.AddShoppingCart,
                contentDescription = "Add to queue",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PrintQueuePanel(
    state: BarcodeLabelPrintState,
    onIntent: (BarcodeLabelPrintIntent) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Print Queue", style = MaterialTheme.typography.titleSmall)
        if (state.totalLabelCount > 0) {
            Spacer(Modifier.width(ZyntaSpacing.xs))
            Badge { Text("${state.totalLabelCount}") }
        }
    }
    Spacer(Modifier.height(ZyntaSpacing.xs))

    if (state.queue.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Queue is empty — search products above",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        state.queue.forEach { item ->
            PrintQueueItemRow(
                item = item,
                onRemove  = { onIntent(BarcodeLabelPrintIntent.RemoveFromQueue(item.id)) },
                onSetQty  = { qty -> onIntent(BarcodeLabelPrintIntent.SetQuantity(item.id, qty)) },
            )
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun PrintQueueItemRow(
    item: PrintQueueItem,
    onRemove: () -> Unit,
    onSetQty: (Int) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ZyntaSpacing.sm, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.productName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.barcode,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Quantity stepper
            IconButton(onClick = { onSetQty(item.quantity - 1) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
            }
            Text(
                text = "${item.quantity}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.widthIn(min = 24.dp),
            )
            IconButton(onClick = { onSetQty(item.quantity + 1) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun PrintActionsRow(
    state: BarcodeLabelPrintState,
    onIntent: (BarcodeLabelPrintIntent) -> Unit,
) {
    val canAct = state.canPrint && !state.isPrinting && !state.isGeneratingPreview

    Row(
        horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedButton(
            onClick = { onIntent(BarcodeLabelPrintIntent.RefreshPreview) },
            enabled = canAct,
            modifier = Modifier.weight(1f),
        ) {
            if (state.isGeneratingPreview) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(ZyntaSpacing.xs))
            Text("Preview")
        }

        FilledTonalButton(
            onClick = { onIntent(BarcodeLabelPrintIntent.PrintLabels) },
            enabled = canAct,
            modifier = Modifier.weight(1f),
        ) {
            if (state.isPrinting) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(ZyntaSpacing.xs))
            Text("Print / Export")
        }
    }

    if (state.selectedTemplate == null && state.queue.isNotEmpty()) {
        Text(
            "Select a template above to enable printing",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun PdfPreviewPanel(
    state: BarcodeLabelPrintState,
    onIntent: (BarcodeLabelPrintIntent) -> Unit,
) {
    Text("PDF Preview", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(ZyntaSpacing.xs))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.isGeneratingPreview -> CircularProgressIndicator()
            state.pdfPreviewBytes != null -> {
                // On desktop, show placeholder text — actual PDF rendering requires platform-specific viewer
                Text(
                    "PDF ready (${state.pdfPreviewBytes.size / 1024} KB)\nClick Export to save",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
                ) {
                    Icon(
                        Icons.Default.Print,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Click Preview to generate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(ZyntaSpacing.sm))
    PrintActionsRow(state, onIntent)
}
