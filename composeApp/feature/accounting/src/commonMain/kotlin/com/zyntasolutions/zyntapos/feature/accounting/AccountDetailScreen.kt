package com.zyntasolutions.zyntapos.feature.accounting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.AccountType
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

/**
 * Account Detail screen — create or edit a chart-of-accounts entry.
 *
 * When [accountId] is null the screen opens in "New Account" mode; otherwise it
 * loads the existing account for editing.
 *
 * @param accountId              UUID of the account to edit, or null to create a new one.
 * @param storeId                Store scope for the save operation.
 * @param viewModel              Provided by Koin via [koinViewModel].
 * @param onNavigateBack         Back navigation callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    accountId: String?,
    storeId: String,
    viewModel: AccountDetailViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
) {
    val s = LocalStrings.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Load or initialise ─────────────────────────────────────────────────
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
                is AccountDetailEffect.SavedSuccessfully -> onNavigateBack()
                is AccountDetailEffect.NavigateBack -> onNavigateBack()
            }
        }
    }

    // ── Account type dropdown state ────────────────────────────────────────
    var typeDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (accountId == null) s[StringResource.ACCOUNTING_NEW_ACCOUNT] else s[StringResource.ACCOUNTING_EDIT_ACCOUNT]) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s[StringResource.COMMON_BACK])
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.dispatch(AccountDetailIntent.Save(storeId)) },
                        enabled = !state.isSaving,
                    ) {
                        Icon(Icons.Default.Check, contentDescription = s[StringResource.COMMON_SAVE])
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(ZyntaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
            ) {
                // ── Account Code ──────────────────────────────────────────
                OutlinedTextField(
                    value = state.accountCode,
                    onValueChange = { viewModel.dispatch(AccountDetailIntent.UpdateCode(it)) },
                    label = { Text(s[StringResource.ACCOUNTING_ACCOUNT_CODE]) },
                    placeholder = { Text(s[StringResource.ACCOUNTING_ACCOUNT_CODE_HINT]) },
                    singleLine = true,
                    isError = state.error != null && state.accountCode.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )

                // ── Account Name ──────────────────────────────────────────
                OutlinedTextField(
                    value = state.accountName,
                    onValueChange = { viewModel.dispatch(AccountDetailIntent.UpdateName(it)) },
                    label = { Text(s[StringResource.ACCOUNTING_ACCOUNT_NAME]) },
                    placeholder = { Text(s[StringResource.ACCOUNTING_ACCOUNT_NAME_HINT]) },
                    singleLine = true,
                    isError = state.error != null && state.accountName.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )

                // ── Account Type dropdown ─────────────────────────────────
                ExposedDropdownMenuBox(
                    expanded = typeDropdownExpanded,
                    onExpandedChange = { typeDropdownExpanded = it },
                ) {
                    OutlinedTextField(
                        value = state.accountType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(s[StringResource.ACCOUNTING_ACCOUNT_TYPE]) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeDropdownExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = typeDropdownExpanded,
                        onDismissRequest = { typeDropdownExpanded = false },
                    ) {
                        AccountType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    viewModel.dispatch(AccountDetailIntent.UpdateType(type))
                                    typeDropdownExpanded = false
                                },
                            )
                        }
                    }
                }

                // ── Sub Category ──────────────────────────────────────────
                OutlinedTextField(
                    value = state.subCategory,
                    onValueChange = { viewModel.dispatch(AccountDetailIntent.UpdateSubCategory(it)) },
                    label = { Text(s[StringResource.ACCOUNTING_SUB_CATEGORY]) },
                    placeholder = { Text(s[StringResource.ACCOUNTING_SUB_CATEGORY_HINT]) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // ── Description ────────────────────────────────────────────
                OutlinedTextField(
                    value = state.description,
                    onValueChange = { viewModel.dispatch(AccountDetailIntent.UpdateDescription(it)) },
                    label = { Text(s[StringResource.ACCOUNTING_DESCRIPTION_OPTIONAL]) },
                    placeholder = { Text(s[StringResource.ACCOUNTING_DESCRIPTION_HINT]) },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )

                // ── Validation error ──────────────────────────────────────
                state.error?.let { errorMsg ->
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                // ── Save button ────────────────────────────────────────────
                Button(
                    onClick = { viewModel.dispatch(AccountDetailIntent.Save(storeId)) },
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(ZyntaSpacing.md),
                            strokeWidth = ZyntaSpacing.xs,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(ZyntaSpacing.sm))
                    }
                    Text(if (accountId == null) s[StringResource.ACCOUNTING_CREATE_ACCOUNT] else s[StringResource.ACCOUNTING_SAVE_CHANGES])
                }
            }

            // ── Full-screen saving overlay ─────────────────────────────────
            if (state.isSaving) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
