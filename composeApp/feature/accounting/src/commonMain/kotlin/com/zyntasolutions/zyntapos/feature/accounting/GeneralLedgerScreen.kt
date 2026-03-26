package com.zyntasolutions.zyntapos.feature.accounting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.Account
import com.zyntasolutions.zyntapos.domain.model.GeneralLedgerEntry
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

/**
 * General Ledger screen — shows all postings for a selected account within a date range.
 *
 * The user selects an account from a dropdown and sets a date range. The ledger entries
 * are displayed in chronological order with a running balance column.
 *
 * @param storeId                Store scope for loading accounts and ledger entries.
 * @param initialAccountId       If non-null, pre-select this account on first load.
 * @param viewModel              Provided by Koin via [koinViewModel].
 * @param onNavigateBack         Back navigation callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralLedgerScreen(
    storeId: String,
    initialAccountId: String? = null,
    viewModel: GeneralLedgerViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
) {
    val s = LocalStrings.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var accountDropdownExpanded by remember { mutableStateOf(false) }

    // Initial load
    LaunchedEffect(storeId) {
        viewModel.dispatch(GeneralLedgerIntent.LoadAccounts(storeId))
    }
    LaunchedEffect(initialAccountId) {
        if (initialAccountId != null) {
            viewModel.dispatch(GeneralLedgerIntent.SelectAccount(initialAccountId))
        }
    }

    // Effect collection
    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is GeneralLedgerEffect.ShowError ->
                    snackbarHostState.showSnackbar(effect.message, duration = SnackbarDuration.Short)
            }
        }
    }

    // Helper to find selected account display name
    val selectedAccount = state.accounts.find { it.id == state.selectedAccountId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s[StringResource.ACCOUNTING_GENERAL_LEDGER]) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s[StringResource.COMMON_BACK])
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.dispatch(GeneralLedgerIntent.Refresh) },
                        enabled = state.selectedAccountId != null && !state.isLoading,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = s[StringResource.COMMON_REFRESH])
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
            // Account selector dropdown
            ExposedDropdownMenuBox(
                expanded = accountDropdownExpanded,
                onExpandedChange = { accountDropdownExpanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
            ) {
                OutlinedTextField(
                    value = selectedAccount?.let { "${it.accountCode} — ${it.accountName}" }
                        ?: if (state.isLoading) s[StringResource.COMMON_LOADING] else s[StringResource.ACCOUNTING_SELECT_ACCOUNT],
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(s[StringResource.ACCOUNTING_ACCOUNT]) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountDropdownExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = accountDropdownExpanded,
                    onDismissRequest = { accountDropdownExpanded = false },
                ) {
                    if (state.accounts.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(s[StringResource.ACCOUNTING_NO_ACCOUNTS]) },
                            onClick = { accountDropdownExpanded = false },
                        )
                    } else {
                        state.accounts.forEach { account ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            "${account.accountCode} — ${account.accountName}",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            account.accountType.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.dispatch(GeneralLedgerIntent.SelectAccount(account.id))
                                    accountDropdownExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            // Date range row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            ) {
                OutlinedTextField(
                    value = state.fromDate,
                    onValueChange = { viewModel.dispatch(GeneralLedgerIntent.SetDateRange(it, state.toDate)) },
                    label = { Text(s[StringResource.ACCOUNTING_FROM]) },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.toDate,
                    onValueChange = { viewModel.dispatch(GeneralLedgerIntent.SetDateRange(state.fromDate, it)) },
                    label = { Text(s[StringResource.ACCOUNTING_TO]) },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider()

            // Content area
            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    state.selectedAccountId == null -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                        ) {
                            Text(
                                s[StringResource.ACCOUNTING_SELECT_ACCOUNT_PROMPT],
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    state.entries.isEmpty() -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                        ) {
                            Text(
                                s[StringResource.ACCOUNTING_NO_LEDGER_ENTRIES],
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Table header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.xs),
                            ) {
                                Text(
                                    s[StringResource.COMMON_DATE],
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(ZyntaSpacing.xxl + ZyntaSpacing.md),
                                )
                                Text(
                                    s[StringResource.ACCOUNTING_DESCRIPTION],
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    s[StringResource.ACCOUNTING_DEBIT],
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(ZyntaSpacing.xxl + ZyntaSpacing.sm),
                                )
                                Text(
                                    s[StringResource.ACCOUNTING_CREDIT],
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(ZyntaSpacing.xxl + ZyntaSpacing.sm),
                                )
                                Text(
                                    s[StringResource.ACCOUNTING_BALANCE],
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(ZyntaSpacing.xxl + ZyntaSpacing.md),
                                )
                            }
                            HorizontalDivider()

                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = ZyntaSpacing.md),
                            ) {
                                items(state.entries, key = { it.lineId }) { entry ->
                                    GeneralLedgerEntryRow(entry = entry)
                                }
                                item { Spacer(Modifier.height(ZyntaSpacing.xl)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Private composables ────────────────────────────────────────────────────────

@Composable
private fun GeneralLedgerEntryRow(entry: GeneralLedgerEntry) {
    val balanceColor = if (entry.runningBalance >= 0) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.error

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ZyntaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entry.entryDate,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(ZyntaSpacing.xxl + ZyntaSpacing.md),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.description.ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
            Text(
                entry.referenceType,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (entry.debit > 0) "%.2f".format(entry.debit) else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(ZyntaSpacing.xxl + ZyntaSpacing.sm),
        )
        Text(
            if (entry.credit > 0) "%.2f".format(entry.credit) else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.width(ZyntaSpacing.xxl + ZyntaSpacing.sm),
        )
        Text(
            "%.2f".format(entry.runningBalance),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = balanceColor,
            modifier = Modifier.width(ZyntaSpacing.xxl + ZyntaSpacing.md),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
