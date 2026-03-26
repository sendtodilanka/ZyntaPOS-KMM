package com.zyntasolutions.zyntapos.feature.accounting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.domain.model.AccountSummary

/**
 * Accounting Ledger — aggregated account balances for a fiscal period.
 *
 * Shows DEBIT and CREDIT totals per account code. Tapping an account row
 * navigates to [AccountDetailScreen] for full entry-level detail.
 *
 * @param state            Current [AccountingState].
 * @param onIntent         Intent dispatcher.
 * @param onNavigateToDetail Navigates to account detail (accountCode, period).
 * @param onNavigateUp     Back navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountingLedgerScreen(
    state: AccountingState,
    onIntent: (AccountingIntent) -> Unit,
    onNavigateToDetail: (accountCode: String, fiscalPeriod: String) -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${s[StringResource.ACCOUNTING_LEDGER]} — ${state.period}") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s[StringResource.COMMON_BACK])
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (state.summaries.isEmpty()) {
                ZyntaEmptyState(
                    title = "${s[StringResource.ACCOUNTING_NO_ENTRIES_FOR]} ${state.period}",
                    icon = Icons.Default.AccountBalance,
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.summaries, key = { "${it.accountCode}-${it.entryType}" }) { summary ->
                        AccountSummaryCard(
                            summary = summary,
                            onClick = { onNavigateToDetail(summary.accountCode, state.period) },
                        )
                    }
                }
            }

            state.error?.let { msg ->
                LaunchedEffect(msg) { onIntent(AccountingIntent.DismissError) }
            }
        }
    }
}

@Composable
private fun AccountSummaryCard(
    summary: AccountSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${summary.accountCode} — ${summary.accountName}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = summary.entryType.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "%.2f".format(summary.total),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
