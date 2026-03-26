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
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.JournalEntry
import kotlin.time.Clock
import kotlinx.coroutines.flow.collectLatest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

/**
 * Journal Entry List screen — displays all journal entries for a store with
 * date-range filtering, draft-only toggle, and swipe-to-delete for drafts.
 *
 * @param storeId                Store scope for loading entries.
 * @param viewModel              Provided by Koin via [koinViewModel].
 * @param onNavigateToEntry      Callback to navigate to a journal entry (null = new entry).
 * @param onNavigateBack         Back navigation callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalEntryListScreen(
    storeId: String,
    viewModel: JournalEntryListViewModel = koinViewModel(),
    onNavigateToEntry: (entryId: String?) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val s = LocalStrings.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var deleteConfirmEntryId by remember { mutableStateOf<String?>(null) }

    // ── Initial load — dispatch unposted filter so ViewModel populates date range ──
    LaunchedEffect(storeId) {
        viewModel.dispatch(JournalEntryListIntent.LoadUnposted(storeId))
        // Switch back to all entries so the full list is visible by default
        viewModel.dispatch(
            JournalEntryListIntent.Load(
                storeId = storeId,
                fromDate = state.fromDate,
                toDate = state.toDate,
            ),
        )
    }

    // ── Effect collection ──────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is JournalEntryListEffect.ShowError ->
                    snackbarHostState.showSnackbar(effect.message, duration = SnackbarDuration.Short)
                is JournalEntryListEffect.ShowSuccess ->
                    snackbarHostState.showSnackbar(effect.message, duration = SnackbarDuration.Short)
                is JournalEntryListEffect.NavigateToEntry ->
                    onNavigateToEntry(effect.entryId)
            }
        }
    }

    // ── Delete confirmation dialog ─────────────────────────────────────────
    deleteConfirmEntryId?.let { entryId ->
        AlertDialog(
            onDismissRequest = { deleteConfirmEntryId = null },
            title = { Text(s[StringResource.ACCOUNTING_DELETE_DRAFT_ENTRY]) },
            text = { Text(s[StringResource.ACCOUNTING_DELETE_DRAFT_CONFIRM]) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dispatch(JournalEntryListIntent.DeleteDraft(entryId))
                        deleteConfirmEntryId = null
                    },
                ) { Text(s[StringResource.COMMON_DELETE], color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmEntryId = null }) { Text(s[StringResource.COMMON_CANCEL]) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s[StringResource.ACCOUNTING_JOURNAL_ENTRIES]) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s[StringResource.COMMON_BACK])
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToEntry(null) }) {
                Icon(Icons.Default.Add, contentDescription = s[StringResource.ACCOUNTING_NEW_ENTRY])
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Date range row ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.fromDate,
                    onValueChange = {
                        viewModel.dispatch(JournalEntryListIntent.SetDateRange(it, state.toDate))
                    },
                    label = { Text(s[StringResource.ACCOUNTING_FROM]) },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.toDate,
                    onValueChange = {
                        viewModel.dispatch(JournalEntryListIntent.SetDateRange(state.fromDate, it))
                    },
                    label = { Text(s[StringResource.ACCOUNTING_TO]) },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            // ── Draft-only toggle ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            ) {
                FilterChip(
                    selected = !state.showUnpostedOnly,
                    onClick = { viewModel.dispatch(JournalEntryListIntent.ToggleUnpostedFilter(false)) },
                    label = { Text(s[StringResource.COMMON_ALL]) },
                )
                FilterChip(
                    selected = state.showUnpostedOnly,
                    onClick = { viewModel.dispatch(JournalEntryListIntent.ToggleUnpostedFilter(true)) },
                    label = { Text(s[StringResource.ACCOUNTING_DRAFTS_ONLY]) },
                )
            }

            HorizontalDivider()

            // ── Content area ────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    state.entries.isEmpty() -> {
                        ZyntaEmptyState(
                            title = s[StringResource.ACCOUNTING_NO_JOURNAL_ENTRIES],
                            icon = Icons.Default.Book,
                            subtitle = s[StringResource.ACCOUNTING_TAP_NEW_ENTRY],
                        )
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(ZyntaSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                        ) {
                            items(state.entries, key = { it.id }) { entry ->
                                JournalEntryCard(
                                    entry = entry,
                                    onClick = { onNavigateToEntry(entry.id) },
                                    onDeleteDraft = if (!entry.isPosted) {
                                        { deleteConfirmEntryId = entry.id }
                                    } else null,
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
private fun JournalEntryCard(
    entry: JournalEntry,
    onClick: () -> Unit,
    onDeleteDraft: (() -> Unit)?,
) {
    val s = LocalStrings.current
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
            // Entry number badge
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    "#${entry.entryNumber}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = ZyntaSpacing.sm, vertical = ZyntaSpacing.xs),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.description.ifBlank { s[StringResource.ACCOUNTING_NO_DESCRIPTION] },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        entry.entryDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Reference type chip
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                entry.referenceType.name,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
                // Debit / credit totals
                val debitTotal = entry.lines.sumOf { it.debitAmount }
                val creditTotal = entry.lines.sumOf { it.creditAmount }
                if (debitTotal > 0 || creditTotal > 0) {
                    Text(
                        "Dr: ${"%.2f".format(debitTotal)}  Cr: ${"%.2f".format(creditTotal)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
            ) {
                // Posted / draft status badge
                Surface(
                    color = if (entry.isPosted) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        text = if (entry.isPosted) s[StringResource.ACCOUNTING_POSTED] else s[StringResource.ACCOUNTING_DRAFT],
                        style = MaterialTheme.typography.labelSmall,
                        color = if (entry.isPosted) MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                // Delete button for drafts
                if (onDeleteDraft != null) {
                    IconButton(
                        onClick = onDeleteDraft,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = s[StringResource.ACCOUNTING_DELETE_DRAFT],
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
