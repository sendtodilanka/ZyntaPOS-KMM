package com.zyntasolutions.zyntapos.feature.accounting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.CashFlowLine
import com.zyntasolutions.zyntapos.domain.model.FinancialStatement
import com.zyntasolutions.zyntapos.domain.model.FinancialStatementLine
import com.zyntasolutions.zyntapos.domain.model.TrialBalanceLine
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

/**
 * Financial Statements screen — Profit & Loss, Balance Sheet, and Trial Balance.
 *
 * Hosts a TabRow with three tabs. Each tab has its own date inputs and "Generate" button
 * that triggers the corresponding load intent.
 *
 * @param storeId                Store scope for all statements.
 * @param initialTab             The tab shown initially (defaults to PROFIT_LOSS).
 * @param viewModel              Provided by Koin via [koinViewModel].
 * @param onNavigateBack         Back navigation callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialStatementsScreen(
    storeId: String,
    initialTab: FinancialStatementTab = FinancialStatementTab.PROFIT_LOSS,
    viewModel: FinancialStatementsViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialTab) {
        viewModel.dispatch(FinancialStatementsIntent.SwitchTab(initialTab))
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is FinancialStatementsEffect.ShowError ->
                    snackbarHostState.showSnackbar(effect.message, duration = SnackbarDuration.Short)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financial Statements") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Tab row
            TabRow(selectedTabIndex = state.activeTab.ordinal) {
                FinancialStatementTab.entries.forEach { tab ->
                    Tab(
                        selected = state.activeTab == tab,
                        onClick = { viewModel.dispatch(FinancialStatementsIntent.SwitchTab(tab)) },
                        text = { Text(tab.label()) },
                    )
                }
            }

            // Tab content
            when (state.activeTab) {
                FinancialStatementTab.PROFIT_LOSS -> PandLTabContent(
                    state = state,
                    storeId = storeId,
                    onIntent = viewModel::dispatch,
                    modifier = Modifier.weight(1f),
                )
                FinancialStatementTab.BALANCE_SHEET -> BalanceSheetTabContent(
                    state = state,
                    storeId = storeId,
                    onIntent = viewModel::dispatch,
                    modifier = Modifier.weight(1f),
                )
                FinancialStatementTab.TRIAL_BALANCE -> TrialBalanceTabContent(
                    state = state,
                    storeId = storeId,
                    onIntent = viewModel::dispatch,
                    modifier = Modifier.weight(1f),
                )
                FinancialStatementTab.CASH_FLOW -> CashFlowTabContent(
                    state = state,
                    storeId = storeId,
                    onIntent = viewModel::dispatch,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ── Tab content composables ────────────────────────────────────────────────────

@Composable
private fun PandLTabContent(
    state: FinancialStatementsState,
    storeId: String,
    onIntent: (FinancialStatementsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Date inputs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ZyntaSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.fromDate,
                onValueChange = { onIntent(FinancialStatementsIntent.SetDateRange(it, state.toDate)) },
                label = { Text("From") },
                placeholder = { Text("YYYY-MM-DD") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.toDate,
                onValueChange = { onIntent(FinancialStatementsIntent.SetDateRange(state.fromDate, it)) },
                label = { Text("To") },
                placeholder = { Text("YYYY-MM-DD") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    onIntent(FinancialStatementsIntent.LoadPandL(storeId, state.fromDate, state.toDate))
                },
                enabled = state.fromDate.isNotBlank() && state.toDate.isNotBlank() && !state.isLoading,
            ) { Text("Generate") }
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val pAndL = state.pAndL
        if (pAndL == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Set a date range and tap Generate.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            // Revenue
            item {
                StatementSectionHeader("Revenue", color = MaterialTheme.colorScheme.tertiary)
            }
            items(pAndL.revenueLines, key = { it.accountId }) { line ->
                StatementLineRow(line = line)
            }
            item {
                StatementSubtotal("Total Revenue", pAndL.totalRevenue, color = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.height(ZyntaSpacing.sm))
            }

            // COGS
            item {
                StatementSectionHeader("Cost of Goods Sold", color = Color(0xFFE65100))
            }
            items(pAndL.cogsLines, key = { it.accountId }) { line ->
                StatementLineRow(line = line)
            }
            item {
                StatementSubtotal("Total COGS", pAndL.totalCogs, color = Color(0xFFE65100))
                Spacer(Modifier.height(ZyntaSpacing.sm))
            }

            // Gross profit
            item {
                StatementSubtotal("Gross Profit", pAndL.grossProfit, highlight = true)
                Spacer(Modifier.height(ZyntaSpacing.sm))
            }

            // Expenses
            item {
                StatementSectionHeader("Expenses", color = MaterialTheme.colorScheme.error)
            }
            items(pAndL.expenseLines, key = { it.accountId }) { line ->
                StatementLineRow(line = line)
            }
            item {
                StatementSubtotal("Total Expenses", pAndL.totalExpenses, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(ZyntaSpacing.sm))
            }

            // Net profit
            item {
                StatementSubtotal("Net Profit", pAndL.netProfit, highlight = true)
                HorizontalDivider(modifier = Modifier.padding(vertical = ZyntaSpacing.sm))
                Text(
                    "Gross Margin: ${"%.1f".format(pAndL.grossMarginPct)}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Spacer(Modifier.height(ZyntaSpacing.xl)) }
        }
    }
}

@Composable
private fun BalanceSheetTabContent(
    state: FinancialStatementsState,
    storeId: String,
    onIntent: (FinancialStatementsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // As-of date input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ZyntaSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.asOfDate,
                onValueChange = { onIntent(FinancialStatementsIntent.SetAsOfDate(it)) },
                label = { Text("As Of") },
                placeholder = { Text("YYYY-MM-DD") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { onIntent(FinancialStatementsIntent.LoadBalanceSheet(storeId, state.asOfDate)) },
                enabled = state.asOfDate.isNotBlank() && !state.isLoading,
            ) { Text("Generate") }
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val bs = state.balanceSheet
        if (bs == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Set an as-of date and tap Generate.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            // Assets
            item { StatementSectionHeader("Assets", color = MaterialTheme.colorScheme.primary) }
            items(bs.assetLines, key = { it.accountId }) { line ->
                StatementLineRow(line = line)
            }
            item {
                StatementSubtotal("Total Assets", bs.totalAssets, highlight = true)
                Spacer(Modifier.height(ZyntaSpacing.sm))
            }

            // Liabilities
            item { StatementSectionHeader("Liabilities", color = MaterialTheme.colorScheme.error) }
            items(bs.liabilityLines, key = { it.accountId }) { line ->
                StatementLineRow(line = line)
            }
            item {
                StatementSubtotal("Total Liabilities", bs.totalLiabilities, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(ZyntaSpacing.sm))
            }

            // Equity
            item { StatementSectionHeader("Equity", color = MaterialTheme.colorScheme.tertiary) }
            items(bs.equityLines, key = { it.accountId }) { line ->
                StatementLineRow(line = line)
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = ZyntaSpacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Retained Earnings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "%.2f".format(bs.retainedEarnings),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatementSubtotal("Total Equity", bs.totalEquity, color = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.height(ZyntaSpacing.md))
            }

            // Accounting equation
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(ZyntaSpacing.md)) {
                        Text(
                            "Accounting Equation",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Assets (${"%.2f".format(bs.totalAssets)}) = Liabilities (${"%.2f".format(bs.totalLiabilities)})" +
                                " + Equity (${"%.2f".format(bs.totalEquity)})",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(ZyntaSpacing.xl)) }
        }
    }
}

@Composable
private fun TrialBalanceTabContent(
    state: FinancialStatementsState,
    storeId: String,
    onIntent: (FinancialStatementsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // As-of date + generate
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ZyntaSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.asOfDate,
                onValueChange = { onIntent(FinancialStatementsIntent.SetAsOfDate(it)) },
                label = { Text("As Of") },
                placeholder = { Text("YYYY-MM-DD") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { onIntent(FinancialStatementsIntent.LoadTrialBalance(storeId, state.asOfDate)) },
                enabled = state.asOfDate.isNotBlank() && !state.isLoading,
            ) { Text("Generate") }
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val tb = state.trialBalance
        if (tb == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Set an as-of date and tap Generate.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        // Balanced / Unbalanced banner
        Surface(
            color = if (tb.isBalanced) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (tb.isBalanced) "BALANCED" else "UNBALANCED — Ledger has errors",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (tb.isBalanced) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(ZyntaSpacing.sm).fillMaxWidth(),
            )
        }

        // Table header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
        ) {
            Text("Code", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
            Text("Account", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Debit", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
            Text("Credit", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
        }
        HorizontalDivider()

        LazyColumn(
            contentPadding = PaddingValues(horizontal = ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(tb.lines, key = { it.accountId }) { line ->
                TrialBalanceRow(line = line)
            }

            // Totals row
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = ZyntaSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("", modifier = Modifier.width(60.dp))
                    Text(
                        "TOTALS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "%.2f".format(tb.totalDebits),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(80.dp),
                    )
                    Text(
                        "%.2f".format(tb.totalCredits),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(80.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(ZyntaSpacing.xl)) }
        }
    }
}

// ── Shared statement UI helpers ─────────────────────────────────────────────────

@Composable
private fun StatementSectionHeader(title: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.padding(vertical = ZyntaSpacing.xs),
    )
}

@Composable
private fun StatementLineRow(line: FinancialStatementLine) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "${line.accountCode} — ${line.accountName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            "%.2f".format(line.amount),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun StatementSubtotal(
    label: String,
    amount: Double,
    color: Color = MaterialTheme.colorScheme.onSurface,
    highlight: Boolean = false,
) {
    Surface(
        color = if (highlight) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ZyntaSpacing.sm, vertical = ZyntaSpacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.SemiBold,
                color = color,
            )
            Text(
                "%.2f".format(amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.SemiBold,
                color = color,
            )
        }
    }
}

@Composable
private fun TrialBalanceRow(line: TrialBalanceLine) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(line.accountCode, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(60.dp))
        Text(line.accountName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(
            if (line.totalDebits > 0) "%.2f".format(line.totalDebits) else "",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(80.dp),
        )
        Text(
            if (line.totalCredits > 0) "%.2f".format(line.totalCredits) else "",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(80.dp),
        )
    }
}

@Composable
private fun CashFlowTabContent(
    state: FinancialStatementsState,
    storeId: String,
    onIntent: (FinancialStatementsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Date range inputs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ZyntaSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.fromDate,
                onValueChange = { onIntent(FinancialStatementsIntent.SetDateRange(it, state.toDate)) },
                label = { Text("From") },
                placeholder = { Text("YYYY-MM-DD") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.toDate,
                onValueChange = { onIntent(FinancialStatementsIntent.SetDateRange(state.fromDate, it)) },
                label = { Text("To") },
                placeholder = { Text("YYYY-MM-DD") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    onIntent(FinancialStatementsIntent.LoadCashFlow(storeId, state.fromDate, state.toDate))
                },
                enabled = state.fromDate.isNotBlank() && state.toDate.isNotBlank() && !state.isLoading,
            ) { Text("Generate") }
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val cf = state.cashFlow
        if (cf == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Set a date range and tap Generate.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            // Opening cash
            item {
                CashFlowSummaryRow("Opening Cash Balance", cf.openingCash, highlight = false)
            }

            // Operating activities
            item { StatementSectionHeader("Operating Activities", color = MaterialTheme.colorScheme.tertiary) }
            items(cf.operatingLines, key = { "op-${it.label}" }) { line ->
                CashFlowLineRow(line)
            }
            item {
                StatementSubtotal("Net Cash from Operations", cf.netOperating,
                    color = if (cf.netOperating >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(ZyntaSpacing.sm))
            }

            // Investing activities
            item { StatementSectionHeader("Investing Activities", color = MaterialTheme.colorScheme.primary) }
            items(cf.investingLines, key = { "inv-${it.label}" }) { line ->
                CashFlowLineRow(line)
            }
            item {
                StatementSubtotal("Net Cash from Investing", cf.netInvesting,
                    color = if (cf.netInvesting >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(ZyntaSpacing.sm))
            }

            // Financing activities
            item { StatementSectionHeader("Financing Activities", color = Color(0xFF7B1FA2)) }
            items(cf.financingLines, key = { "fin-${it.label}" }) { line ->
                CashFlowLineRow(line)
            }
            item {
                StatementSubtotal("Net Cash from Financing", cf.netFinancing,
                    color = if (cf.netFinancing >= 0) Color(0xFF7B1FA2) else MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(ZyntaSpacing.sm))
            }

            // Net change + closing
            item {
                HorizontalDivider()
                Spacer(Modifier.height(ZyntaSpacing.sm))
                CashFlowSummaryRow("Net Change in Cash", cf.netChange, highlight = true)
                Spacer(Modifier.height(ZyntaSpacing.xs))
                CashFlowSummaryRow("Closing Cash Balance", cf.closingCash, highlight = true)
            }
            item { Spacer(Modifier.height(ZyntaSpacing.xl)) }
        }
    }
}

@Composable
private fun CashFlowLineRow(line: CashFlowLine) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            line.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            "%.2f".format(line.net),
            style = MaterialTheme.typography.bodySmall,
            color = if (line.net >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun CashFlowSummaryRow(label: String, amount: Double, highlight: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ZyntaSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = if (highlight) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            "%.2f".format(amount),
            style = if (highlight) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (amount >= 0 || !highlight) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.error,
        )
    }
}

private fun FinancialStatementTab.label(): String = when (this) {
    FinancialStatementTab.PROFIT_LOSS -> "P&L"
    FinancialStatementTab.BALANCE_SHEET -> "Balance Sheet"
    FinancialStatementTab.TRIAL_BALANCE -> "Trial Balance"
    FinancialStatementTab.CASH_FLOW -> "Cash Flow"
}
