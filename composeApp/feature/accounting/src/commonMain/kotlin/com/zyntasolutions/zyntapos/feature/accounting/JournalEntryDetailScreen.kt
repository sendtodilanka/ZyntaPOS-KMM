package com.zyntasolutions.zyntapos.feature.accounting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.Account
import com.zyntasolutions.zyntapos.domain.model.JournalEntryLine
import com.zyntasolutions.zyntapos.domain.model.JournalReferenceType
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Clock
import org.koin.compose.viewmodel.koinViewModel

/**
 * Journal Entry Detail screen — create, edit, post, and reverse journal entries.
 *
 * When [entryId] is null the screen opens in "New Entry" mode; otherwise it
 * loads the existing entry for editing or viewing.
 *
 * @param entryId                UUID of the entry to load, or null to create a new one.
 * @param storeId                Store scope for the entry.
 * @param createdBy              User ID attributed to this entry.
 * @param viewModel              Provided by Koin via [koinViewModel].
 * @param onNavigateBack         Back navigation callback.
 * @param onNavigateToEntry      Callback to navigate to the reversal entry created.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalEntryDetailScreen(
    entryId: String?,
    storeId: String,
    createdBy: String,
    viewModel: JournalEntryDetailViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToEntry: (entryId: String) -> Unit,
) {
    val s = LocalStrings.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showReverseDialog by remember { mutableStateOf(false) }
    var reversalDate by remember { mutableStateOf("") }
    var showAddLineForm by remember { mutableStateOf(false) }
    var refTypeDropdownExpanded by remember { mutableStateOf(false) }

    // Load or initialise
    LaunchedEffect(entryId) {
        if (entryId != null) {
            viewModel.dispatch(JournalEntryDetailIntent.Load(entryId))
        } else {
            viewModel.dispatch(JournalEntryDetailIntent.NewEntry(storeId, createdBy))
        }
    }

    // Effect collection
    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is JournalEntryDetailEffect.ShowError ->
                    snackbarHostState.showSnackbar(effect.message, duration = SnackbarDuration.Short)
                is JournalEntryDetailEffect.ShowSuccess ->
                    snackbarHostState.showSnackbar(effect.message, duration = SnackbarDuration.Short)
                is JournalEntryDetailEffect.NavigateBack -> onNavigateBack()
                is JournalEntryDetailEffect.NavigateToEntry ->
                    onNavigateToEntry(effect.entryId)
            }
        }
    }

    // Reverse confirmation dialog
    if (showReverseDialog) {
        AlertDialog(
            onDismissRequest = { showReverseDialog = false },
            title = { Text(s[StringResource.ACCOUNTING_REVERSE_ENTRY]) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                    Text("Create a reversal entry for Journal Entry #${state.entry?.entryNumber}?")
                    OutlinedTextField(
                        value = reversalDate,
                        onValueChange = { reversalDate = it },
                        label = { Text(s[StringResource.ACCOUNTING_REVERSAL_DATE]) },
                        placeholder = { Text(s[StringResource.COMMON_DATE_FORMAT_PLACEHOLDER]) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = state.entry?.id
                        if (id != null && reversalDate.isNotBlank()) {
                            viewModel.dispatch(JournalEntryDetailIntent.Reverse(id, reversalDate))
                        }
                        showReverseDialog = false
                    },
                    enabled = reversalDate.isNotBlank(),
                ) { Text(s[StringResource.ACCOUNTING_REVERSE]) }
            },
            dismissButton = {
                TextButton(onClick = { showReverseDialog = false }) { Text(s[StringResource.COMMON_CANCEL]) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (entryId == null) s[StringResource.ACCOUNTING_NEW_JOURNAL_ENTRY]
                        else "${s[StringResource.ACCOUNTING_JOURNAL_ENTRY]} #${state.entry?.entryNumber ?: ""}",
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s[StringResource.COMMON_BACK])
                    }
                },
                actions = {
                    if (state.entry?.isPosted != true) {
                        IconButton(
                            onClick = { viewModel.dispatch(JournalEntryDetailIntent.Save) },
                            enabled = !state.isSaving && !state.isPosting,
                        ) {
                            Icon(Icons.Default.Save, contentDescription = s[StringResource.ACCOUNTING_SAVE_DRAFT])
                        }
                        IconButton(
                            onClick = {
                                val id = state.entry?.id ?: return@IconButton
                                viewModel.dispatch(JournalEntryDetailIntent.Post(id))
                            },
                            enabled = state.isBalanced && !state.isSaving && !state.isPosting && state.entry != null,
                        ) {
                            Icon(Icons.Default.Publish, contentDescription = s[StringResource.ACCOUNTING_POST_ENTRY])
                        }
                    }
                    if (state.entry?.isPosted == true) {
                        IconButton(
                            onClick = {
                                reversalDate = ""
                                showReverseDialog = true
                            },
                            enabled = !state.isReversing,
                        ) {
                            Icon(Icons.Default.Undo, contentDescription = s[StringResource.ACCOUNTING_REVERSE_ENTRY])
                        }
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
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header form
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(ZyntaSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                    ) {
                        OutlinedTextField(
                            value = state.description,
                            onValueChange = { viewModel.dispatch(JournalEntryDetailIntent.UpdateDescription(it)) },
                            label = { Text(s[StringResource.ACCOUNTING_DESCRIPTION]) },
                            singleLine = true,
                            enabled = state.entry?.isPosted != true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                            OutlinedTextField(
                                value = state.entryDate,
                                onValueChange = { viewModel.dispatch(JournalEntryDetailIntent.UpdateDate(it)) },
                                label = { Text(s[StringResource.COMMON_DATE]) },
                                placeholder = { Text(s[StringResource.COMMON_DATE_FORMAT_PLACEHOLDER]) },
                                singleLine = true,
                                enabled = state.entry?.isPosted != true,
                                modifier = Modifier.weight(1f),
                            )
                            ExposedDropdownMenuBox(
                                expanded = refTypeDropdownExpanded,
                                onExpandedChange = {
                                    if (state.entry?.isPosted != true) refTypeDropdownExpanded = it
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                OutlinedTextField(
                                    value = state.referenceType.name,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text(s[StringResource.ACCOUNTING_REFERENCE_TYPE]) },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = refTypeDropdownExpanded)
                                    },
                                    enabled = state.entry?.isPosted != true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                )
                                ExposedDropdownMenu(
                                    expanded = refTypeDropdownExpanded,
                                    onDismissRequest = { refTypeDropdownExpanded = false },
                                ) {
                                    JournalReferenceType.entries.forEach { type ->
                                        DropdownMenuItem(
                                            text = { Text(type.name) },
                                            onClick = {
                                                viewModel.dispatch(JournalEntryDetailIntent.UpdateReferenceType(type))
                                                refTypeDropdownExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // Lines section
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(ZyntaSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                    ) {
                        item {
                            Text(
                                s[StringResource.ACCOUNTING_LINES],
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        items(state.lines, key = { it.id }) { line ->
                            JournalLineRow(
                                line = line,
                                onDelete = if (state.entry?.isPosted != true) {
                                    { viewModel.dispatch(JournalEntryDetailIntent.RemoveLine(line.id)) }
                                } else null,
                            )
                        }
                        if (state.entry?.isPosted != true) {
                            item {
                                if (showAddLineForm) {
                                    AddLineForm(
                                        onAdd = { line ->
                                            viewModel.dispatch(JournalEntryDetailIntent.AddLine(line))
                                            showAddLineForm = false
                                        },
                                        onCancel = { showAddLineForm = false },
                                    )
                                } else {
                                    OutlinedButton(
                                        onClick = { showAddLineForm = true },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Spacer(Modifier.width(ZyntaSpacing.sm))
                                        Text(s[StringResource.ACCOUNTING_ADD_LINE])
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(ZyntaSpacing.xl)) }
                    }

                    HorizontalDivider()

                    // Balance status row
                    BalanceStatusRow(state = state)
                }
            }
        }
    }
}

@Composable
private fun JournalLineRow(
    line: JournalEntryLine,
    onDelete: (() -> Unit)?,
) {
    val s = LocalStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ZyntaSpacing.sm, vertical = ZyntaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        if (!line.accountCode.isNullOrBlank()) append("${line.accountCode} — ")
                        append(line.accountName ?: line.accountId)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                line.lineDescription?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (line.debitAmount > 0) {
                    Text(
                        "Dr: ${"%.2f".format(line.debitAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (line.creditAmount > 0) {
                    Text(
                        "Cr: ${"%.2f".format(line.creditAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = s[StringResource.ACCOUNTING_REMOVE_LINE],
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddLineForm(
    onAdd: (JournalEntryLine) -> Unit,
    onCancel: () -> Unit,
) {
    val s = LocalStrings.current
    var accountCode by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }
    var debitAmount by remember { mutableStateOf("") }
    var creditAmount by remember { mutableStateOf("") }
    var lineDescription by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(ZyntaSpacing.md), verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
            Text(s[StringResource.ACCOUNTING_ADD_LINE], style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = accountCode,
                onValueChange = { accountCode = it },
                label = { Text(s[StringResource.ACCOUNTING_ACCOUNT_CODE]) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = accountName,
                onValueChange = { accountName = it },
                label = { Text(s[StringResource.ACCOUNTING_ACCOUNT_NAME]) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                OutlinedTextField(
                    value = debitAmount,
                    onValueChange = { debitAmount = it },
                    label = { Text(s[StringResource.ACCOUNTING_DEBIT]) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = creditAmount,
                    onValueChange = { creditAmount = it },
                    label = { Text(s[StringResource.ACCOUNTING_CREDIT]) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = lineDescription,
                onValueChange = { lineDescription = it },
                label = { Text(s[StringResource.ACCOUNTING_DESCRIPTION_OPTIONAL]) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(s[StringResource.COMMON_CANCEL]) }
                Button(
                    onClick = {
                        val debit = debitAmount.toDoubleOrNull() ?: 0.0
                        val credit = creditAmount.toDoubleOrNull() ?: 0.0
                        val now = Clock.System.now().toEpochMilliseconds()
                        onAdd(
                            JournalEntryLine(
                                id = "line-$now-${(0..9999).random()}",
                                journalEntryId = "",
                                accountId = accountCode.ifBlank { accountName },
                                debitAmount = debit,
                                creditAmount = credit,
                                lineDescription = lineDescription.takeIf { it.isNotBlank() },
                                lineOrder = 0,
                                createdAt = now,
                                accountCode = accountCode.takeIf { it.isNotBlank() },
                                accountName = accountName.takeIf { it.isNotBlank() },
                            ),
                        )
                    },
                    enabled = (debitAmount.isNotBlank() || creditAmount.isNotBlank()) &&
                        (accountCode.isNotBlank() || accountName.isNotBlank()),
                    modifier = Modifier.weight(1f),
                ) { Text(s[StringResource.ACCOUNTING_ADD]) }
            }
        }
    }
}

@Composable
private fun BalanceStatusRow(state: JournalEntryDetailState) {
    val s = LocalStrings.current
    val debitTotal = state.lines.sumOf { it.debitAmount }
    val creditTotal = state.lines.sumOf { it.creditAmount }

    Surface(
        color = if (state.isBalanced) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(ZyntaSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.lg)) {
                Text("Debits: ${"%.2f".format(debitTotal)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text("Credits: ${"%.2f".format(creditTotal)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs)) {
                Icon(
                    imageVector = if (state.isBalanced) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (state.isBalanced) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = if (state.isBalanced) s[StringResource.ACCOUNTING_BALANCED] else s[StringResource.ACCOUNTING_UNBALANCED],
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (state.isBalanced) MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
