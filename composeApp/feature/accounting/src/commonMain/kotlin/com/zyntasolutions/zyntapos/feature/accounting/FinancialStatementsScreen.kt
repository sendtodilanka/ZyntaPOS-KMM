package com.zyntasolutions.zyntapos.feature.accounting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.StringResolver
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.CashFlowLine
import com.zyntasolutions.zyntapos.domain.model.FinancialStatement
import com.zyntasolutions.zyntapos.domain.model.FinancialStatementLine
import com.zyntasolutions.zyntapos.domain.model.TrialBalanceLine
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

/**
 * Financial Statements screen — Profit & Loss, Balance Sheet, Trial Balance, Cash Flow.
 *
 * Hosts a TabRow with four tabs. Each tab has date picker inputs and a "Generate" button.
 * The TopAppBar provides a CSV export action for the currently loaded statement.
 *
 * @param storeId                Store scope for all statements.
 * @param initialTab             The tab shown initially (defaults to PROFIT_LOSS).
 * @param viewModel              Provided by Koin via [koinViewModel].
 * @param onNavigateBack         Back navigation callback.
 * @param onShareExport          Callback to display or share exported CSV content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialStatementsScreen(
    storeId: String,
    initialTab: FinancialStatementTab = FinancialStatementTab.PROFIT_LOSS,
    viewModel: FinancialStatementsViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    onShareExport: (content: String, fileName: String) -> Unit = { _, _ -> },
) {
    val s = LocalStrings.current
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
                is FinancialStatementsEffect.ShareExport ->
                    onShareExport(effect.content, effect.fileName)
            }
        }
    }

    // Date picker dialogs
    DatePickerDialogs(state = state, onIntent = viewModel::dispatch)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s[StringResource.ACCOUNTING_FINANCIAL_STATEMENTS]) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s[StringResource.COMMON_BACK])
                    }
                },
                actions = {
                    // Export CSV button — enabled only when the active tab has data
                    val hasData = when (state.activeTab) {
                        FinancialStatementTab.PROFIT_LOSS -> state.pAndL != null
                        FinancialStatementTab.BALANCE_SHEET -> state.balanceSheet != null
                        FinancialStatementTab.TRIAL_BALANCE -> state.trialBalance != null
                        FinancialStatementTab.CASH_FLOW -> state.cashFlow != null
                    }
                    IconButton(
                        onClick = { viewModel.dispatch(FinancialStatementsIntent.ExportCsv) },
                        enabled = hasData,
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = s[StringResource.ACCOUNTING_EXPORT_CSV],
                            tint = if (hasData) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                        text = { Text(tab.label(s)) },
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

// ── Date picker dialogs ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialogs(
    state: FinancialStatementsState,
    onIntent: (FinancialStatementsIntent) -> Unit,
) {
    val s = LocalStrings.current
    when (state.activeDatePicker) {
        DatePickerField.NONE -> Unit

        DatePickerField.FROM -> {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = state.fromDate.toEpochMillisOrNull(),
            )
            DatePickerDialog(
                onDismissRequest = { onIntent(FinancialStatementsIntent.HideDatePicker) },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onIntent(
                                FinancialStatementsIntent.SetDateRange(
                                    fromDate = millis.toLocalDateString(),
                                    toDate = state.toDate,
                                ),
                            )
                        }
                        onIntent(FinancialStatementsIntent.HideDatePicker)
                    }) { Text(s[StringResource.COMMON_OK]) }
                },
                dismissButton = {
                    TextButton(onClick = { onIntent(FinancialStatementsIntent.HideDatePicker) }) {
                        Text(s[StringResource.COMMON_CANCEL])
                    }
                },
            ) {
                DatePicker(state = datePickerState)
            }
        }

        DatePickerField.TO -> {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = state.toDate.toEpochMillisOrNull(),
            )
            DatePickerDialog(
                onDismissRequest = { onIntent(FinancialStatementsIntent.HideDatePicker) },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onIntent(
                                FinancialStatementsIntent.SetDateRange(
                                    fromDate = state.fromDate,
                                    toDate = millis.toLocalDateString(),
                                ),
                            )
                        }
                        onIntent(FinancialStatementsIntent.HideDatePicker)
                    }) { Text(s[StringResource.COMMON_OK]) }
                },
                dismissButton = {
                    TextButton(onClick = { onIntent(FinancialStatementsIntent.HideDatePicker) }) {
                        Text(s[StringResource.COMMON_CANCEL])
                    }
                },
            ) {
                DatePicker(state = datePickerState)
            }
        }

        DatePickerField.AS_OF -> {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = state.asOfDate.toEpochMillisOrNull(),
            )
            DatePickerDialog(
                onDismissRequest = { onIntent(FinancialStatementsIntent.HideDatePicker) },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onIntent(FinancialStatementsIntent.SetAsOfDate(millis.toLocalDateString()))
                        }
                        onIntent(FinancialStatementsIntent.HideDatePicker)
                    }) { Text(s[StringResource.COMMON_OK]) }
                },
                dismissButton = {
                    TextButton(onClick = { onIntent(FinancialStatementsIntent.HideDatePicker) }) {
                        Text(s[StringResource.COMMON_CANCEL])
                    }
                },
            ) {
                DatePicker(state = datePickerState)
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
    val s = LocalStrings.current
    Column(modifier = modifier.fillMaxSize()) {
        // Date inputs with picker buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ZyntaSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DateInputField(
                label = s[StringResource.ACCOUNTING_FROM],
                value = state.fromDate,
                onPickerClick = { onIntent(FinancialStatementsIntent.ShowDatePicker(DatePickerField.FROM)) },
                modifier = Modifier.weight(1f),
            )
            DateInputField(
                label = s[StringResource.ACCOUNTING_TO],
                value = state.toDate,
                onPickerClick = { onIntent(FinancialStatementsIntent.ShowDatePicker(DatePickerField.TO)) },
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    onIntent(FinancialStatementsIntent.LoadPandL(storeId, state.fromDate, state.toDate))
                },
                enabled = state.fromDate.isNotBlank() && state.toDate.isNotBlank() && !state.isLoading,
            ) { Text(s[StringResource.ACCOUNTING_GENERATE]) }
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
                Text(s[StringResource.ACCOUNTING_SET_DATE_RANGE], color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            // Revenue
            item {
                StatementSectionHeader(s[StringResource.ACCOUNTING_REVENUE], color = MaterialTheme.colorScheme.tertiary)
            }
            items(pAndL.revenueLines, key = { it.accountId }) { line ->
                StatementLineRow(line = line)
            }
            item {
                StatementSubtotal(s[StringResource.ACCOUNTING_TOTAL_REVENUE], pAndL.totalRevenue, color = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.height(ZyntaSpacing.sm))
            }

            // COGS
            item {
                StatementSectionHeader(s[StringResource.ACCOUNTING_COST_OF_GOODS_SOLD], color = Color(0xFFE65100))
            }
            items(pAndL.cogsLines, key = { it.accountId }) { line ->
                StatementLineRow(line = line)
            }
            item {
                StatementSubtotal(s[StringResource.ACCOUNTING_TOTAL_COGS], pAndL.totalCogs, color = Color(0xFFE65100))
                Spacer(Modifier.height(ZyntaSpacing.sm))
            }

            // Gross profit
            item {
                StatementSubtotal(s[StringResource.ACCOUNTING_GROSS_PROFIT], pAndL.grossProfit, highlight = true)
                Spacer(Modifier.height(ZyntaSpacing.sm))
            }

            // Expenses
            item {
                StatementSectionHeader(s[StringResource.ACCOUNTING_EXPENSES], color = MaterialTheme.colorScheme.error)
            }
            items(pAndL.expenseLines, key = { it.accountId }) { line ->
                StatementLineRow(line = line)
            }
            item {
                StatementSubtotal(s[StringResource.ACCOUNTING_TOTAL_EXPENSES], pAndL.totalExpenses, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(ZyntaSpacing.sm))
            }

            // Net profit
            item {
                StatementSubtotal(s[StringResource.ACCOUNTING_NET_PROFIT], pAndL.netProfit, highlight = true)
                HorizontalDivider(modifier = Modifier.padding(vertical = ZyntaSpacing.sm))
                Text(
                    "${s[StringResource.ACCOUNTING_GROSS_MARGIN]}: ${"%.1f".format(pAndL.grossMarginPct)}%",
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
    val s = LocalStrings.current
    Column(modifier = modifier.fillMaxSize()) {
        // As-of date input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ZyntaSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DateInputField(
                label = s[StringResource.ACCOUNTING_AS_OF],
                value = state.asOfDate,
                onPickerClick = { onIntent(FinancialStatementsIntent.ShowDatePicker(DatePickerField.AS_OF)) },
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { onIntent(FinancialStatementsIntent.LoadBalanceSheet(storeId, state.asOfDate)) },
                enabled = state.asOfDate.isNotBlank() && !state.isLoading,
            ) { Text(s[StringResource.ACCOUNTING_GENERATE]) }
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
                Text(s[StringResource.ACCOUNTING_SET_AS_OF_DATE], color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            // Assets
            item { StatementSectionHeader(s[StringResource.ACCOUNTING_ASSETS], color = MaterialTheme.colorScheme.primary) }
            items(bs.assetLines, key = { it.accountId }) { line ->
                StatementLineRow(line = line)
            }
            item {
                StatementSubtotal(s[StringResource.ACCOUNTING_TOTAL_ASSETS], bs.totalAssets, highlight = true)
                Spacer(Modifier.height(ZyntaSpacing.sm))
            }

            // Liabilities
            item { StatementSectionHeader(s[StringResource.ACCOUNTING_LIABILITIES], color = MaterialTheme.colorScheme.error) }
            items(bs.liabilityLines, key = { it.accountId }) { line ->
                StatementLineRow(line = line)
            }
            item {
                StatementSubtotal(s[StringResource.ACCOUNTING_TOTAL_LIABILITIES], bs.totalLiabilities, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(ZyntaSpacing.sm))
            }

            // Equity
            item { StatementSectionHeader(s[StringResource.ACCOUNTING_EQUITY], color = MaterialTheme.colorScheme.tertiary) }
            items(bs.equityLines, key = { it.accountId }) { line ->
                StatementLineRow(line = line)
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = ZyntaSpacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        s[StringResource.ACCOUNTING_RETAINED_EARNINGS],
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "%.2f".format(bs.retainedEarnings),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatementSubtotal(s[StringResource.ACCOUNTING_TOTAL_EQUITY], bs.totalEquity, color = MaterialTheme.colorScheme.tertiary)
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
                            s[StringResource.ACCOUNTING_ACCOUNTING_EQUATION],
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${s[StringResource.ACCOUNTING_ASSETS]} (${"%.2f".format(bs.totalAssets)}) = ${s[StringResource.ACCOUNTING_LIABILITIES]} (${"%.2f".format(bs.totalLiabilities)})" +
                                " + ${s[StringResource.ACCOUNTING_EQUITY]} (${"%.2f".format(bs.totalEquity)})",
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
    val s = LocalStrings.current
    Column(modifier = modifier.fillMaxSize()) {
        // As-of date + generate
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ZyntaSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DateInputField(
                label = s[StringResource.ACCOUNTING_AS_OF],
                value = state.asOfDate,
                onPickerClick = { onIntent(FinancialStatementsIntent.ShowDatePicker(DatePickerField.AS_OF)) },
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { onIntent(FinancialStatementsIntent.LoadTrialBalance(storeId, state.asOfDate)) },
                enabled = state.asOfDate.isNotBlank() && !state.isLoading,
            ) { Text(s[StringResource.ACCOUNTING_GENERATE]) }
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
                Text(s[StringResource.ACCOUNTING_SET_AS_OF_DATE], color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                text = if (tb.isBalanced) s[StringResource.ACCOUNTING_BALANCED] else s[StringResource.ACCOUNTING_UNBALANCED_LEDGER],
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
            Text(s[StringResource.ACCOUNTING_CODE], style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
            Text(s[StringResource.ACCOUNTING_ACCOUNT], style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(s[StringResource.ACCOUNTING_DEBIT], style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
            Text(s[StringResource.ACCOUNTING_CREDIT], style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
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
                        s[StringResource.ACCOUNTING_TOTALS],
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

@Composable
private fun CashFlowTabContent(
    state: FinancialStatementsState,
    storeId: String,
    onIntent: (FinancialStatementsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    Column(modifier = modifier.fillMaxSize()) {
        // Date range inputs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ZyntaSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DateInputField(
                label = s[StringResource.ACCOUNTING_FROM],
                value = state.fromDate,
                onPickerClick = { onIntent(FinancialStatementsIntent.ShowDatePicker(DatePickerField.FROM)) },
                modifier = Modifier.weight(1f),
            )
            DateInputField(
                label = s[StringResource.ACCOUNTING_TO],
                value = state.toDate,
                onPickerClick = { onIntent(FinancialStatementsIntent.ShowDatePicker(DatePickerField.TO)) },
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    onIntent(FinancialStatementsIntent.LoadCashFlow(storeId, state.fromDate, state.toDate))
                },
                enabled = state.fromDate.isNotBlank() && state.toDate.isNotBlank() && !state.isLoading,
            ) { Text(s[StringResource.ACCOUNTING_GENERATE]) }
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
                Text(s[StringResource.ACCOUNTING_SET_DATE_RANGE], color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            // Opening cash
            item {
                CashFlowSummaryRow(s[StringResource.ACCOUNTING_OPENING_CASH_BALANCE], cf.openingCash, highlight = false)
            }

            // Operating activities
            item { StatementSectionHeader(s[StringResource.ACCOUNTING_OPERATING_ACTIVITIES], color = MaterialTheme.colorScheme.tertiary) }
            items(cf.operatingLines, key = { "op-${it.label}" }) { line ->
                CashFlowLineRow(line)
            }
            item {
                StatementSubtotal(s[StringResource.ACCOUNTING_NET_CASH_OPERATIONS], cf.netOperating,
                    color = if (cf.netOperating >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(ZyntaSpacing.sm))
            }

            // Investing activities
            item { StatementSectionHeader(s[StringResource.ACCOUNTING_INVESTING_ACTIVITIES], color = MaterialTheme.colorScheme.primary) }
            items(cf.investingLines, key = { "inv-${it.label}" }) { line ->
                CashFlowLineRow(line)
            }
            item {
                StatementSubtotal(s[StringResource.ACCOUNTING_NET_CASH_INVESTING], cf.netInvesting,
                    color = if (cf.netInvesting >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(ZyntaSpacing.sm))
            }

            // Financing activities
            item { StatementSectionHeader(s[StringResource.ACCOUNTING_FINANCING_ACTIVITIES], color = Color(0xFF7B1FA2)) }
            items(cf.financingLines, key = { "fin-${it.label}" }) { line ->
                CashFlowLineRow(line)
            }
            item {
                StatementSubtotal(s[StringResource.ACCOUNTING_NET_CASH_FINANCING], cf.netFinancing,
                    color = if (cf.netFinancing >= 0) Color(0xFF7B1FA2) else MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(ZyntaSpacing.sm))
            }

            // Net change + closing
            item {
                HorizontalDivider()
                Spacer(Modifier.height(ZyntaSpacing.sm))
                CashFlowSummaryRow(s[StringResource.ACCOUNTING_NET_CHANGE_CASH], cf.netChange, highlight = true)
                Spacer(Modifier.height(ZyntaSpacing.xs))
                CashFlowSummaryRow(s[StringResource.ACCOUNTING_CLOSING_CASH_BALANCE], cf.closingCash, highlight = true)
            }
            item { Spacer(Modifier.height(ZyntaSpacing.xl)) }
        }
    }
}

// ── Shared statement UI helpers ─────────────────────────────────────────────────

/**
 * Read-only date display field with a calendar icon that opens the date picker.
 * Replaces direct OutlinedTextField YYYY-MM-DD text entry.
 */
@Composable
private fun DateInputField(
    label: String,
    value: String,
    onPickerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = { Text("YYYY-MM-DD") },
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = onPickerClick) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = s[StringResource.ACCOUNTING_PICK_DATE],
                )
            }
        },
        modifier = modifier,
    )
}

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
    val s = LocalStrings.current
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

private fun FinancialStatementTab.label(s: StringResolver): String = when (this) {
    FinancialStatementTab.PROFIT_LOSS -> s[StringResource.ACCOUNTING_TAB_PNL]
    FinancialStatementTab.BALANCE_SHEET -> s[StringResource.ACCOUNTING_TAB_BALANCE_SHEET]
    FinancialStatementTab.TRIAL_BALANCE -> s[StringResource.ACCOUNTING_TAB_TRIAL_BALANCE]
    FinancialStatementTab.CASH_FLOW -> s[StringResource.ACCOUNTING_TAB_CASH_FLOW]
}
