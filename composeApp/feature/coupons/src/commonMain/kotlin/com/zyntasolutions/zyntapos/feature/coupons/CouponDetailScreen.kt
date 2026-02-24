package com.zyntasolutions.zyntapos.feature.coupons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import org.koin.compose.viewmodel.koinViewModel

/**
 * Create or edit a single [Coupon].
 *
 * @param couponId null → create mode; non-null → edit mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CouponDetailScreen(
    couponId: String?,
    onNavigateUp: () -> Unit,
    viewModel: CouponViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val form = state.formState
    var showDeleteDialog by remember { mutableStateOf(false) }

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
            OutlinedTextField(
                value = form.code,
                onValueChange = { viewModel.dispatch(CouponIntent.UpdateFormField("code", it)) },
                label = { Text("Coupon Code *") },
                isError = form.validationErrors.containsKey("code"),
                supportingText = form.validationErrors["code"]?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = form.name,
                onValueChange = { viewModel.dispatch(CouponIntent.UpdateFormField("name", it)) },
                label = { Text("Name *") },
                isError = form.validationErrors.containsKey("name"),
                supportingText = form.validationErrors["name"]?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // Discount Type Dropdown
            var discountTypeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = discountTypeExpanded,
                onExpandedChange = { discountTypeExpanded = !discountTypeExpanded },
            ) {
                OutlinedTextField(
                    value = form.discountType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Discount Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(discountTypeExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = discountTypeExpanded,
                    onDismissRequest = { discountTypeExpanded = false },
                ) {
                    listOf(DiscountType.FIXED, DiscountType.PERCENT).forEach { type ->
                        DropdownMenuItem(
                            text = { Text(if (type == DiscountType.FIXED) "Fixed Amount (LKR)" else "Percentage (%)") },
                            onClick = {
                                viewModel.dispatch(CouponIntent.UpdateFormField("discountType", type.name))
                                discountTypeExpanded = false
                            },
                        )
                    }
                }
            }

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

            OutlinedTextField(
                value = form.validFrom,
                onValueChange = { viewModel.dispatch(CouponIntent.UpdateFormField("validFrom", it)) },
                label = { Text("Valid From (epoch ms) *") },
                isError = form.validationErrors.containsKey("validFrom"),
                supportingText = form.validationErrors["validFrom"]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = form.validTo,
                onValueChange = { viewModel.dispatch(CouponIntent.UpdateFormField("validTo", it)) },
                label = { Text("Valid To (epoch ms) *") },
                isError = form.validationErrors.containsKey("validTo"),
                supportingText = form.validationErrors["validTo"]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

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
}
