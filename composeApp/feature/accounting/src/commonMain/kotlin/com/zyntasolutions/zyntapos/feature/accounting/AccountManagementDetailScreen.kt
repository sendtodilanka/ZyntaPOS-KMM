package com.zyntasolutions.zyntapos.feature.accounting

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.domain.model.AccountType
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

/**
 * Account Management Detail screen — create or edit an account in the Chart of Accounts.
 *
 * In edit mode ([accountId] non-null) the account is loaded from the repository and
 * the form fields are pre-populated. In create mode ([accountId] null) the form starts blank.
 *
 * @param accountId       UUID of an existing account for edit; `null` for create mode.
 * @param storeId         Store scope required for persisting the account.
 * @param viewModel       Provided by Koin via [koinViewModel].
 * @param onNavigateBack  Back navigation callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagementDetailScreen(
    accountId: String?,
    storeId: String,
    viewModel: AccountDetailViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
) {
    val s = LocalStrings.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Initial load ───────────────────────────────────────────────────────
    LaunchedEffect(accountId) {
        if (accountId != null) {
            viewModel.dispatch(AccountDetailIntent.Load(accountId))
        } else {
            viewModel.dispatch(AccountDetailIntent.StartNew(storeId))
        }
    }

    // ── Effect collection ──────────────────────────────────────────────────
    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AccountDetailEffect.ShowError ->
                    snackbarHostState.showSnackbar(effect.message, duration = SnackbarDuration.Short)
                is AccountDetailEffect.SavedSuccessfully -> {
                    snackbarHostState.showSnackbar(s[StringResource.ACCOUNTING_ACCOUNT_SAVED], duration = SnackbarDuration.Short)
                    onNavigateBack()
                }
                is AccountDetailEffect.NavigateBack -> onNavigateBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (accountId != null) s[StringResource.ACCOUNTING_EDIT_ACCOUNT] else s[StringResource.ACCOUNTING_NEW_ACCOUNT])
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s[StringResource.COMMON_BACK])
                    }
                },
                actions = {
                    if (!state.isSaving) {
                        TextButton(onClick = { viewModel.dispatch(AccountDetailIntent.Save(storeId)) }) {
                            Text(s[StringResource.COMMON_SAVE])
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Account code
                    OutlinedTextField(
                        value = state.accountCode,
                        onValueChange = { viewModel.dispatch(AccountDetailIntent.UpdateCode(it)) },
                        label = { Text(s[StringResource.ACCOUNTING_ACCOUNT_CODE]) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = state.error != null,
                    )

                    // Account name
                    OutlinedTextField(
                        value = state.accountName,
                        onValueChange = { viewModel.dispatch(AccountDetailIntent.UpdateName(it)) },
                        label = { Text(s[StringResource.ACCOUNTING_ACCOUNT_NAME]) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = state.error != null,
                    )

                    // Account type
                    Text(s[StringResource.ACCOUNTING_ACCOUNT_TYPE], style = MaterialTheme.typography.labelMedium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AccountType.entries.forEach { type ->
                            FilterChip(
                                selected = state.accountType == type,
                                onClick = { viewModel.dispatch(AccountDetailIntent.UpdateType(type)) },
                                label = { Text(type.name, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }

                    // Sub-category
                    OutlinedTextField(
                        value = state.subCategory,
                        onValueChange = { viewModel.dispatch(AccountDetailIntent.UpdateSubCategory(it)) },
                        label = { Text(s[StringResource.ACCOUNTING_SUB_CATEGORY]) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    // Description
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = { viewModel.dispatch(AccountDetailIntent.UpdateDescription(it)) },
                        label = { Text(s[StringResource.ACCOUNTING_DESCRIPTION_OPTIONAL]) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )

                    state.error?.let { errorMsg ->
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (state.isSaving) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
