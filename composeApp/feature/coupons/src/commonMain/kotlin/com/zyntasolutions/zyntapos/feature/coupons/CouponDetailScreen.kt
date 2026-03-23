package com.zyntasolutions.zyntapos.feature.coupons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.domain.model.Coupon
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

/**
 * Create or edit a single [Coupon].
 *
 * G12: Extended with BOGO discount type, coupon scope targeting (CART/PRODUCT/CATEGORY/CUSTOMER),
 * category picker for scope IDs, coupon code auto-generation, and date picker dialogs.
 *
 * @param couponId null → create mode; non-null → edit mode.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CouponDetailScreen(
    couponId: String?,
    onNavigateUp: () -> Unit,
    viewModel: CouponViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val form = state.formState
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showFromDatePicker by remember { mutableStateOf(false) }
    var showToDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(couponId) {
        viewModel.dispatch(CouponIntent.SelectCoupon(couponId))
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CouponEffect.NavigateToList -> onNavigateUp()
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (couponId == null) "New Coupon" else "Edit Coupon") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (couponId != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Coupon Code + Auto-Generate ──────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                OutlinedTextField(
                    value = form.code,
                    onValueChange = { viewModel.dispatch(CouponIntent.UpdateFormField("code", it)) },
                    label = { Text("Coupon Code *") },
                    isError = form.validationErrors.containsKey("code"),
                    supportingText = form.validationErrors["code"]?.let { { Text(it) } },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                IconButton(
                    onClick = { viewModel.dispatch(CouponIntent.GenerateCode) },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "Auto-generate code",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            OutlinedTextField(
                value = form.name,
                onValueChange = { viewModel.dispatch(CouponIntent.UpdateFormField("name", it)) },
                label = { Text("Name *") },
                isError = form.validationErrors.containsKey("name"),
                supportingText = form.validationErrors["name"]?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // ── Discount Type Dropdown (FIXED, PERCENT, BOGO) ───────────────
            var discountTypeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = discountTypeExpanded,
                onExpandedChange = { discountTypeExpanded = !discountTypeExpanded },
            ) {
                OutlinedTextField(
                    value = discountTypeDisplayName(form.discountType),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Discount Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(discountTypeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = discountTypeExpanded,
                    onDismissRequest = { discountTypeExpanded = false },
                ) {
                    DiscountType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(discountTypeDisplayName(type.name)) },
                            onClick = {
                                viewModel.dispatch(CouponIntent.UpdateFormField("discountType", type.name))
                                discountTypeExpanded = false
                            },
                        )
                    }
                }
            }

            // ── Discount Value (hidden for BOGO) ────────────────────────────
            if (form.discountType != DiscountType.BOGO.name) {
                OutlinedTextField(
                    value = form.discountValue,
                    onValueChange = { viewModel.dispatch(CouponIntent.UpdateFormField("discountValue", it)) },
                    label = { Text("Discount Value *") },
                    isError = form.validationErrors.containsKey("discountValue"),
                    supportingText = form.validationErrors["discountValue"]?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            OutlinedTextField(
                value = form.minimumPurchase,
                onValueChange = { viewModel.dispatch(CouponIntent.UpdateFormField("minimumPurchase", it)) },
                label = { Text("Minimum Purchase (LKR)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            if (form.discountType == DiscountType.PERCENT.name) {
                OutlinedTextField(
                    value = form.maximumDiscount,
                    onValueChange = { viewModel.dispatch(CouponIntent.UpdateFormField("maximumDiscount", it)) },
                    label = { Text("Maximum Discount Cap (LKR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            // ── Coupon Scope (G12: CART / PRODUCT / CATEGORY / CUSTOMER) ─────
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Coupon Scope", style = MaterialTheme.typography.titleSmall)

            var scopeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = scopeExpanded,
                onExpandedChange = { scopeExpanded = !scopeExpanded },
            ) {
                OutlinedTextField(
                    value = scopeDisplayName(form.scope),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Applies To") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(scopeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = scopeExpanded,
                    onDismissRequest = { scopeExpanded = false },
                ) {
                    Coupon.CouponScope.entries.forEach { scope ->
                        DropdownMenuItem(
                            text = { Text(scopeDisplayName(scope.name)) },
                            onClick = {
                                viewModel.dispatch(CouponIntent.UpdateScope(scope.name))
                                scopeExpanded = false
                            },
                        )
                    }
                }
            }

            // ── Category Picker (when scope = CATEGORY) ─────────────────────
            if (form.scope == Coupon.CouponScope.CATEGORY.name && state.availableCategories.isNotEmpty()) {
                Text(
                    "Select Categories",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.availableCategories.forEach { category ->
                        FilterChip(
                            selected = category.id in form.scopeIds,
                            onClick = { viewModel.dispatch(CouponIntent.ToggleScopeId(category.id)) },
                            label = { Text(category.name) },
                        )
                    }
                }
                if (form.validationErrors.containsKey("scopeIds")) {
                    Text(
                        form.validationErrors["scopeIds"] ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // ── Scope IDs manual entry for PRODUCT/CUSTOMER scopes ──────────
            if (form.scope == Coupon.CouponScope.PRODUCT.name ||
                form.scope == Coupon.CouponScope.CUSTOMER.name
            ) {
                val label = if (form.scope == Coupon.CouponScope.PRODUCT.name) "Product IDs" else "Customer IDs"
                OutlinedTextField(
                    value = form.scopeIds.joinToString(", "),
                    onValueChange = { text ->
                        val ids = text.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        // Clear existing and set new IDs through form state
                        viewModel.dispatch(CouponIntent.UpdateScope(form.scope))
                        ids.forEach { id -> viewModel.dispatch(CouponIntent.ToggleScopeId(id)) }
                    },
                    label = { Text("$label (comma-separated)") },
                    isError = form.validationErrors.containsKey("scopeIds"),
                    supportingText = form.validationErrors["scopeIds"]?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── Usage Limits ─────────────────────────────────────────────────
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Usage Limits", style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = form.usageLimit,
                onValueChange = { viewModel.dispatch(CouponIntent.UpdateFormField("usageLimit", it)) },
                label = { Text("Total Usage Limit") },
                placeholder = { Text("Unlimited") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = form.perCustomerLimit,
                onValueChange = { viewModel.dispatch(CouponIntent.UpdateFormField("perCustomerLimit", it)) },
                label = { Text("Per-Customer Limit") },
                placeholder = { Text("Unlimited") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // ── Validity Period (date pickers instead of raw epoch ms) ───────
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Validity Period", style = MaterialTheme.typography.titleSmall)

            // Valid From
            OutlinedTextField(
                value = formatEpochMillis(form.validFrom),
                onValueChange = {},
                readOnly = true,
                label = { Text("Valid From *") },
                isError = form.validationErrors.containsKey("validFrom"),
                supportingText = form.validationErrors["validFrom"]?.let { { Text(it) } },
                trailingIcon = {
                    IconButton(onClick = { showFromDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Select start date")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // Valid To
            OutlinedTextField(
                value = formatEpochMillis(form.validTo),
                onValueChange = {},
                readOnly = true,
                label = { Text("Valid To *") },
                isError = form.validationErrors.containsKey("validTo"),
                supportingText = form.validationErrors["validTo"]?.let { { Text(it) } },
                trailingIcon = {
                    IconButton(onClick = { showToDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Select end date")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // ── Active Toggle ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Active", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = form.isActive,
                    onCheckedChange = { viewModel.dispatch(CouponIntent.UpdateIsActive(it)) },
                )
            }

            Spacer(Modifier.height(8.dp))

            ZyntaButton(
                text = if (form.isEditing) "Update Coupon" else "Create Coupon",
                onClick = { viewModel.dispatch(CouponIntent.SaveCoupon) },
                modifier = Modifier.fillMaxWidth(),
                isLoading = state.isLoading,
            )
        }
    }

    // ── Delete Confirmation Dialog ───────────────────────────────────────
    if (showDeleteDialog && couponId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Coupon") },
            text = { Text("Delete \"${state.selectedCoupon?.name ?: couponId}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.dispatch(CouponIntent.DeleteCoupon(couponId))
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    // ── Date Picker Dialogs ──────────────────────────────────────────────
    if (showFromDatePicker) {
        val initialMs = form.validFrom.toLongOrNull()
        val dateState = rememberDatePickerState(initialSelectedDateMillis = initialMs)
        DatePickerDialog(
            onDismissRequest = { showFromDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { ms ->
                        viewModel.dispatch(CouponIntent.UpdateFormField("validFrom", ms.toString()))
                    }
                    showFromDatePicker = false
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showFromDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = dateState, title = { Text("Valid From", modifier = Modifier.padding(16.dp)) })
        }
    }

    if (showToDatePicker) {
        val initialMs = form.validTo.toLongOrNull()
        val dateState = rememberDatePickerState(initialSelectedDateMillis = initialMs)
        DatePickerDialog(
            onDismissRequest = { showToDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { ms ->
                        viewModel.dispatch(CouponIntent.UpdateFormField("validTo", ms.toString()))
                    }
                    showToDatePicker = false
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showToDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = dateState, title = { Text("Valid To", modifier = Modifier.padding(16.dp)) })
        }
    }
}

// ── Display helpers ──────────────────────────────────────────────────────────

private fun discountTypeDisplayName(typeName: String): String = when (typeName) {
    DiscountType.FIXED.name -> "Fixed Amount (LKR)"
    DiscountType.PERCENT.name -> "Percentage (%)"
    DiscountType.BOGO.name -> "Buy One Get One"
    else -> typeName
}

private fun scopeDisplayName(scopeName: String): String = when (scopeName) {
    Coupon.CouponScope.CART.name -> "Entire Cart"
    Coupon.CouponScope.PRODUCT.name -> "Specific Products"
    Coupon.CouponScope.CATEGORY.name -> "Specific Categories"
    Coupon.CouponScope.CUSTOMER.name -> "Specific Customers"
    else -> scopeName
}

private fun formatEpochMillis(epochStr: String): String {
    val ms = epochStr.toLongOrNull() ?: return ""
    if (ms <= 0L) return ""
    return runCatching {
        val instant = Instant.fromEpochMilliseconds(ms)
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}-${dt.dayOfMonth.toString().padStart(2, '0')}"
    }.getOrDefault(epochStr)
}
