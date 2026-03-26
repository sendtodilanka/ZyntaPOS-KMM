package com.zyntasolutions.zyntapos.feature.customers

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaLoadingSkeleton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTextField
import com.zyntasolutions.zyntapos.domain.model.WalletTransaction
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Customer wallet screen — shows balance, transaction history, and loyalty points.
 *
 * Displays a top-up sheet to manually credit a customer's wallet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerWalletScreen(
    customerId: String,
    state: CustomerState,
    onIntent: (CustomerIntent) -> Unit,
    onNavigateUp: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val s = LocalStrings.current
    LaunchedEffect(customerId) {
        onIntent(CustomerIntent.LoadWallet(customerId))
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showTopUpSheet by remember { mutableStateOf(false) }
    var topUpAmount by remember { mutableStateOf("") }
    var topUpNote by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s[StringResource.CUSTOMERS_WALLET_LOYALTY]) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = s[StringResource.COMMON_BACK])
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isWalletLoading) {
            ZyntaLoadingSkeleton(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Balance Cards ───────────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WalletBalanceCard(
                        label = s[StringResource.CUSTOMERS_WALLET_BALANCE],
                        value = "LKR ${state.wallet?.balance?.let { "%.2f".format(it) } ?: "0.00"}",
                        modifier = Modifier.weight(1f),
                    )
                    WalletBalanceCard(
                        label = s[StringResource.CUSTOMERS_LOYALTY_POINTS],
                        value = "${state.pointsBalance} pts",
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { showTopUpSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(s[StringResource.CUSTOMERS_TOP_UP_WALLET])
                }
                Spacer(Modifier.height(16.dp))
                Text(s[StringResource.CUSTOMERS_TRANSACTION_HISTORY], style = MaterialTheme.typography.titleSmall)
                HorizontalDivider()
            }

            // ── Wallet Transactions ─────────────────────────────────────────
            if (state.walletTransactions.isEmpty()) {
                item {
                    Text(
                        s[StringResource.CUSTOMERS_NO_TRANSACTIONS],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                items(state.walletTransactions, key = { it.id }) { tx ->
                    WalletTransactionRow(tx)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            // ── Reward Points History ───────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                Text(s[StringResource.CUSTOMERS_REWARD_HISTORY], style = MaterialTheme.typography.titleSmall)
                HorizontalDivider()
            }
            if (state.rewardHistory.isEmpty()) {
                item {
                    Text(
                        s[StringResource.CUSTOMERS_NO_REWARD_HISTORY],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                items(state.rewardHistory, key = { it.id }) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.type.name, style = MaterialTheme.typography.bodySmall)
                            Text(
                                formatEpoch(entry.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "${if (entry.points >= 0) "+" else ""}${entry.points} pts",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (entry.points >= 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    // ── Top-Up Sheet ───────────────────────────────────────────────────────────
    if (showTopUpSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTopUpSheet = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(s[StringResource.CUSTOMERS_TOP_UP_WALLET], style = MaterialTheme.typography.titleMedium)
                ZyntaTextField(
                    value = topUpAmount,
                    onValueChange = { topUpAmount = it },
                    label = s[StringResource.CUSTOMERS_AMOUNT_LKR],
                    modifier = Modifier.fillMaxWidth(),
                )
                ZyntaTextField(
                    value = topUpNote,
                    onValueChange = { topUpNote = it },
                    label = s[StringResource.CUSTOMERS_NOTE_OPTIONAL],
                    modifier = Modifier.fillMaxWidth(),
                )
                ZyntaButton(
                    text = s[StringResource.CUSTOMERS_CONFIRM_TOP_UP],
                    onClick = {
                        val amount = topUpAmount.toDoubleOrNull()
                        if (amount != null && amount > 0) {
                            onIntent(CustomerIntent.TopUpWallet(customerId, amount, topUpNote))
                            showTopUpSheet = false
                            topUpAmount = ""
                            topUpNote = ""
                        }
                    },
                    isLoading = state.isWalletLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun WalletBalanceCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun WalletTransactionRow(tx: WalletTransaction) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(tx.type.name, style = MaterialTheme.typography.bodySmall)
            if (!tx.note.isNullOrBlank()) {
                Text(tx.note!!, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatEpoch(tx.createdAt), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = "${if (tx.amount >= 0) "+" else ""}${"%.2f".format(tx.amount)}",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = if (tx.amount >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

private fun formatEpoch(millis: Long): String {
    val dt = Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dt.date}"
}
