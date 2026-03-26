package com.zyntasolutions.zyntapos.feature.accounting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.Account
import com.zyntasolutions.zyntapos.domain.model.AccountType
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Chart of Accounts screen — lists all accounts for a store with search,
 * type filtering, and actions to create, deactivate, or seed defaults.
 *
 * @param viewModel              Provided by Koin via [koinViewModel].
 * @param onNavigateToAccountDetail Callback for navigating to create (null) or edit an account.
 * @param onNavigateBack         Back navigation callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartOfAccountsScreen(
    viewModel: ChartOfAccountsViewModel = koinViewModel(),
    onNavigateToAccountDetail: (accountId: String?) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val s = LocalStrings.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showDeactivateDialog by remember { mutableStateOf<String?>(null) }

    // ── Effect collection ──────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ChartOfAccountsEffect.ShowError ->
                    snackbarHostState.showSnackbar(effect.message, duration = SnackbarDuration.Short)
                is ChartOfAccountsEffect.ShowSuccess ->
                    snackbarHostState.showSnackbar(effect.message, duration = SnackbarDuration.Short)
                is ChartOfAccountsEffect.NavigateToAccountDetail ->
                    onNavigateToAccountDetail(effect.accountId)
            }
        }
    }

    // ── Deactivate confirmation dialog ─────────────────────────────────────
    showDeactivateDialog?.let { accountId ->
        AlertDialog(
            onDismissRequest = { showDeactivateDialog = null },
            title = { Text(s[StringResource.ACCOUNTING_DEACTIVATE_ACCOUNT]) },
            text = {
                Text(s[StringResource.ACCOUNTING_DEACTIVATE_CONFIRM])
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dispatch(ChartOfAccountsIntent.DeactivateAccount(accountId))
                        showDeactivateDialog = null
                    },
                ) { Text(s[StringResource.ACCOUNTING_DEACTIVATE]) }
            },
            dismissButton = {
                TextButton(onClick = { showDeactivateDialog = null }) { Text(s[StringResource.COMMON_CANCEL]) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s[StringResource.ACCOUNTING_CHART_OF_ACCOUNTS]) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s[StringResource.COMMON_BACK])
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        viewModel.dispatch(ChartOfAccountsEffect.NavigateToAccountDetail(null).let {
                            ChartOfAccountsIntent.LoadAccounts // trigger nav via effect
                        })
                    }
                    onNavigateToAccountDetail(null)
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = s[StringResource.ACCOUNTING_ADD_ACCOUNT])
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Search field ────────────────────────────────────────────────
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.dispatch(ChartOfAccountsIntent.SearchAccounts(it)) },
                label = { Text(s[StringResource.ACCOUNTING_SEARCH_ACCOUNTS]) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.dispatch(ChartOfAccountsIntent.SearchAccounts("")) }) {
                            Icon(Icons.Default.Clear, contentDescription = s[StringResource.COMMON_CLEAR])
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
            )

            // ── Type filter chips ───────────────────────────────────────────
            LazyRow(
                contentPadding = PaddingValues(horizontal = ZyntaSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                modifier = Modifier.padding(bottom = ZyntaSpacing.sm),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedType == null,
                        onClick = { viewModel.dispatch(ChartOfAccountsIntent.FilterByType(null)) },
                        label = { Text(s[StringResource.COMMON_ALL]) },
                    )
                }
                items(AccountType.entries.toList()) { type ->
                    FilterChip(
                        selected = state.selectedType == type,
                        onClick = { viewModel.dispatch(ChartOfAccountsIntent.FilterByType(type)) },
                        label = { Text(type.name) },
                    )
                }
            }

            HorizontalDivider()

            // ── Content area ────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    state.accounts.isEmpty() -> {
                        ZyntaEmptyState(
                            title = s[StringResource.ACCOUNTING_NO_ACCOUNTS],
                            icon = Icons.Default.AccountBalance,
                            subtitle = s[StringResource.ACCOUNTING_SEED_PROMPT],
                            ctaLabel = s[StringResource.ACCOUNTING_LOAD_DEFAULTS],
                            onCtaClick = { viewModel.dispatch(ChartOfAccountsIntent.SeedDefaultAccounts) },
                        )
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(ZyntaSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                        ) {
                            items(state.accounts, key = { it.id }) { account ->
                                AccountCard(
                                    account = account,
                                    onClick = { onNavigateToAccountDetail(account.id) },
                                    onDeactivate = { showDeactivateDialog = account.id },
                                )
                            }
                            item { Spacer(Modifier.height(ZyntaSpacing.xl)) }
                        }
                    }
                }
            }
        }
    }
}

// ── Private composables ────────────────────────────────────────────────────────

@Composable
private fun AccountCard(
    account: Account,
    onClick: () -> Unit,
    onDeactivate: () -> Unit,
) {
    val s = LocalStrings.current
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(ZyntaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            // Account code badge
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    account.accountCode,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = ZyntaSpacing.sm, vertical = ZyntaSpacing.xs),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    account.accountName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    account.subCategory,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Account type chip
            SuggestionChip(
                onClick = {},
                label = {
                    Text(
                        account.accountType.name,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )

            // Active/inactive indicator
            Surface(
                color = if (account.isActive) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                Text(
                    text = if (account.isActive) s[StringResource.COMMON_ACTIVE] else s[StringResource.COMMON_INACTIVE],
                    style = MaterialTheme.typography.labelSmall,
                    color = if (account.isActive) MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            // Overflow menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = s[StringResource.COMMON_MORE_OPTIONS])
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(s[StringResource.COMMON_EDIT]) },
                        onClick = {
                            showMenu = false
                            onClick()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    )
                    if (account.isActive && !account.isSystemAccount) {
                        DropdownMenuItem(
                            text = { Text(s[StringResource.ACCOUNTING_DEACTIVATE]) },
                            onClick = {
                                showMenu = false
                                onDeactivate()
                            },
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                        )
                    }
                }
            }
        }
    }
}
