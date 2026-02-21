package com.zyntasolutions.zyntapos.feature.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZentaSpacing
import com.zyntasolutions.zyntapos.domain.model.Supplier

/**
 * Supplier create/edit screen — Sprint 19, Step 10.1.10.
 *
 * Renders a two-section layout:
 *
 * **Section 1 — Contact Form**
 * Fields: name*, contactPerson, phone, email, address, notes.
 *
 * **Section 2 — Purchase History** (read-only)
 * A placeholder [LazyColumn] of orders associated with this supplier.
 * In Phase 1 this is a static list passed from the caller; full procurement
 * integration ships in Phase 2 (Sprint 20+).
 *
 * @param existingSupplier  The supplier being edited, or null when creating new.
 * @param purchaseHistory   Read-only list of recent purchase order summaries.
 * @param isLoading         True while a save/load operation is in-flight.
 * @param errorMessage      Transient validation/server error to display.
 * @param onConfirm         Called with the updated [Supplier] when the user confirms.
 * @param onNavigateBack    Called on Back / Cancel tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierDetailScreen(
    existingSupplier: Supplier? = null,
    purchaseHistory: List<PurchaseOrderSummary> = emptyList(),
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onConfirm: (Supplier) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEditing = existingSupplier != null

    // ── Form State ───────────────────────────────────────────────────────────
    var name by remember(existingSupplier) { mutableStateOf(existingSupplier?.name ?: "") }
    var contactPerson by remember(existingSupplier) { mutableStateOf(existingSupplier?.contactPerson ?: "") }
    var phone by remember(existingSupplier) { mutableStateOf(existingSupplier?.phone ?: "") }
    var email by remember(existingSupplier) { mutableStateOf(existingSupplier?.email ?: "") }
    var address by remember(existingSupplier) { mutableStateOf(existingSupplier?.address ?: "") }
    var notes by remember(existingSupplier) { mutableStateOf(existingSupplier?.notes ?: "") }
    var isActive by remember(existingSupplier) { mutableStateOf(existingSupplier?.isActive ?: true) }

    var nameError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit Supplier" else "New Supplier") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Error Banner ─────────────────────────────────────────────
            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(ZentaSpacing.md),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Row(Modifier.padding(ZentaSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(ZentaSpacing.sm))
                        Text(errorMessage, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // ──────────────────────────────────────────────────────────────
            // Section 1: Contact Info
            // ──────────────────────────────────────────────────────────────
            SectionHeader("Contact Information")

            Column(
                modifier = Modifier.padding(horizontal = ZentaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(ZentaSpacing.sm),
            ) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = if (it.isBlank()) "Name is required" else null },
                    label = { Text("Supplier Name *") },
                    placeholder = { Text("e.g. Lanka Distributors Ltd.") },
                    leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Contact Person
                OutlinedTextField(
                    value = contactPerson,
                    onValueChange = { contactPerson = it },
                    label = { Text("Contact Person") },
                    placeholder = { Text("e.g. Kamal Perera") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Phone
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone") },
                    placeholder = { Text("+94 11 234 5678") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Email
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    placeholder = { Text("supplier@example.com") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Address
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    placeholder = { Text("Street, City, Province") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                    ),
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (internal)") },
                    placeholder = { Text("Payment terms, lead time, special instructions…") },
                    leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Active toggle
                Row(
                    Modifier.fillMaxWidth().padding(vertical = ZentaSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Active", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (isActive) "Available for purchase orders" else "Archived supplier",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }

                Spacer(Modifier.height(ZentaSpacing.sm))

                // Action buttons
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ZentaSpacing.sm)) {
                    OutlinedButton(onClick = onNavigateBack, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = {
                            if (name.isBlank()) { nameError = "Name is required"; return@Button }
                            onConfirm(
                                Supplier(
                                    id = existingSupplier?.id ?: "",
                                    name = name.trim(),
                                    contactPerson = contactPerson.trim().ifBlank { null },
                                    phone = phone.trim().ifBlank { null },
                                    email = email.trim().ifBlank { null },
                                    address = address.trim().ifBlank { null },
                                    notes = notes.trim().ifBlank { null },
                                    isActive = isActive,
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading,
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(ZentaSpacing.sm))
                        }
                        Text(if (isEditing) "Update" else "Create")
                    }
                }
            }

            // ──────────────────────────────────────────────────────────────
            // Section 2: Purchase History (read-only, Phase 1 stub)
            // ──────────────────────────────────────────────────────────────
            if (isEditing) {
                Spacer(Modifier.height(ZentaSpacing.lg))
                SectionHeader("Purchase History")

                if (purchaseHistory.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = ZentaSpacing.lg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Receipt, contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(ZentaSpacing.sm))
                            Text("No purchase orders yet", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(horizontal = ZentaSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(ZentaSpacing.xs),
                    ) {
                        purchaseHistory.forEach { order ->
                            PurchaseHistoryRow(order)
                        }
                    }
                }

                Spacer(Modifier.height(ZentaSpacing.xl))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private: Purchase History Row
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A lightweight read-only summary of one purchase order linked to a supplier.
 *
 * @property orderId   Unique procurement reference number.
 * @property date      ISO date string (e.g. "2025-12-01").
 * @property totalAmount Total order value in local currency.
 * @property status    Delivery status label (e.g. "Delivered", "Pending").
 */
data class PurchaseOrderSummary(
    val orderId: String,
    val date: String,
    val totalAmount: Double,
    val status: String,
)

@Composable
private fun PurchaseHistoryRow(order: PurchaseOrderSummary) {
    ListItem(
        headlineContent = { Text(order.orderId, style = MaterialTheme.typography.bodyMedium) },
        supportingContent = { Text(order.date, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "LKR ${"%.2f".format(order.totalAmount)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                AssistChip(
                    onClick = {},
                    label = { Text(order.status, style = MaterialTheme.typography.labelSmall) },
                )
            }
        },
        leadingContent = {
            Icon(Icons.Default.ShoppingCart, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary)
        },
        modifier = Modifier.fillMaxWidth(),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

// ─────────────────────────────────────────────────────────────────────────────
// Private: Section Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(
                start = ZentaSpacing.md,
                end = ZentaSpacing.md,
                top = ZentaSpacing.lg,
                bottom = ZentaSpacing.xs,
            ),
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = ZentaSpacing.md),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Spacer(Modifier.height(ZentaSpacing.sm))
    }
}
