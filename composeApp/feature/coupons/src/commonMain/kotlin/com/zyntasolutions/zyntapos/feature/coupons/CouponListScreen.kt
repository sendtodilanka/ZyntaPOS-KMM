package com.zyntasolutions.zyntapos.feature.coupons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.domain.model.Coupon
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import org.koin.compose.viewmodel.koinViewModel

/**
 * Displays the list of coupons with filter for active-only and quick
 * active toggle per row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CouponListScreen(
    onNavigateToDetail: (couponId: String?) -> Unit,
    viewModel: CouponViewModel = koinViewModel(),
) {
    val s = LocalStrings.current
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.dispatch(CouponIntent.LoadCoupons)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(s[StringResource.COUPONS_TITLE]) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToDetail(null) }) {
                Icon(Icons.Default.Add, contentDescription = s[StringResource.COUPONS_NEW])
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !state.showActiveOnly,
                    onClick = { viewModel.dispatch(CouponIntent.ToggleActiveFilter(false)) },
                    label = { Text(s[StringResource.COUPONS_ALL]) },
                )
                FilterChip(
                    selected = state.showActiveOnly,
                    onClick = { viewModel.dispatch(CouponIntent.ToggleActiveFilter(true)) },
                    label = { Text(s[StringResource.COUPONS_ACTIVE]) },
                )
            }

            if (state.coupons.isEmpty() && !state.isLoading) {
                ZyntaEmptyState(
                    title = if (state.showActiveOnly) s[StringResource.COUPONS_NO_ACTIVE] else s[StringResource.COUPONS_NO_COUPONS],
                    icon = Icons.Default.LocalOffer,
                    subtitle = s[StringResource.COUPONS_TAP_CREATE],
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.coupons, key = { it.id }) { coupon ->
                        CouponListItem(
                            coupon = coupon,
                            onEdit = { onNavigateToDetail(coupon.id) },
                            onToggleActive = { isActive ->
                                viewModel.dispatch(CouponIntent.ToggleCouponActive(coupon.id, isActive))
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CouponListItem(
    coupon: Coupon,
    onEdit: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
) {
    val s = LocalStrings.current
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = coupon.code,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = coupon.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = when (coupon.discountType) {
                        DiscountType.FIXED -> "LKR ${coupon.discountValue} ${s[StringResource.COUPONS_OFF]}"
                        DiscountType.PERCENT -> "${coupon.discountValue}% ${s[StringResource.COUPONS_OFF]}"
                        DiscountType.BOGO -> s[StringResource.COUPONS_BOGO]
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Used: ${coupon.usageCount}${coupon.usageLimit?.let { " / $it" } ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = coupon.isActive,
                onCheckedChange = onToggleActive,
            )
        }
    }
}
