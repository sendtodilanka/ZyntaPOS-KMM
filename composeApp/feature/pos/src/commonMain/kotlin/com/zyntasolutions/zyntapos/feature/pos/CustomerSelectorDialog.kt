package com.zyntasolutions.zyntapos.feature.pos

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
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZentaSearchBar
import com.zyntasolutions.zyntapos.designsystem.tokens.ZentaSpacing
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.repository.CustomerRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

// ─────────────────────────────────────────────────────────────────────────────
// CustomerSelectorDialog — Search customers with debounced FTS5 query.
// Walk-in option always visible at the top. Quick-add button opens a form.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Customer search and selection dialog for POS checkout.
 *
 * Search is debounced (300 ms) and delegated to
 * [CustomerRepository.search] which uses FTS5 on name, phone, and email.
 * An empty query shows all customers.
 *
 * "Walk-in Customer" is always pinned at the top of the list as a no-customer
 * option — selecting it clears any previously attached customer.
 *
 * The quick-add button (➕ icon) navigates the user to the customer creation
 * form. The caller is responsible for navigation; [onQuickAdd] is the callback.
 *
 * @param onCustomerSelected  Invoked with the selected [Customer]. Null means walk-in.
 * @param onDismiss           Invoked when the dialog is dismissed.
 * @param customerRepository  [CustomerRepository] injected from Koin in the caller.
 * @param onQuickAdd          Invoked when the quick-add button is tapped.
 */
@OptIn(FlowPreview::class, ExperimentalMaterial3Api::class)
@Composable
fun CustomerSelectorDialog(
    onCustomerSelected: (Customer?) -> Unit,
    onDismiss: () -> Unit,
    customerRepository: CustomerRepository,
    onQuickAdd: () -> Unit,
) {
    var query by remember { mutableStateOf("") }

    // Debounced customer search flow
    val customers by remember(customerRepository) {
        snapshotFlow { query }
            .debounce(300L)
            .flatMapLatest { q ->
                if (q.isBlank()) customerRepository.getAll()
                else customerRepository.search(q)
            }
    }.collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Select Customer",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = onQuickAdd) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Add new customer",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        text = {
            Column {
                // ── Search bar ───────────────────────────────────────────
                ZentaSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    onClear = { query = "" },
                    placeholder = "Search by name, phone or email",
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(ZentaSpacing.sm))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                ) {
                    // Walk-in (always first)
                    item {
                        CustomerRow(
                            name = "Walk-in Customer",
                            subtitle = "Anonymous sale",
                            icon = Icons.Default.PersonOutline,
                            onClick = { onCustomerSelected(null) },
                        )
                        HorizontalDivider()
                    }

                    // Search results
                    items(
                        items = customers,
                        key = { it.id },
                    ) { customer ->
                        CustomerRow(
                            name = customer.name,
                            subtitle = customer.phone.ifBlank { customer.email ?: "" },
                            onClick = { onCustomerSelected(customer) },
                        )
                        HorizontalDivider()
                    }

                    // Empty state when search returns nothing
                    if (customers.isEmpty() && query.isNotBlank()) {
                        item {
                            Text(
                                text = "No customers found for \"$query\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(ZentaSpacing.md),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun CustomerRow(
    name: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Person,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ZentaSpacing.sm, vertical = ZentaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp).padding(4.dp),
        )
        Spacer(Modifier.width(ZentaSpacing.sm))
        Column {
            Text(text = name, style = MaterialTheme.typography.bodyMedium)
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
