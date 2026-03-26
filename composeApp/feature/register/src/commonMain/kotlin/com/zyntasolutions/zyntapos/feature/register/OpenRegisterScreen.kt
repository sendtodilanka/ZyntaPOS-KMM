package com.zyntasolutions.zyntapos.feature.register

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyntasolutions.zyntapos.designsystem.components.NumericPadMode
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaNumericPad
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.domain.model.CashRegister
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen for selecting and opening a cash register before the POS is accessible.
 *
 * ## Layout (Expanded / Desktop)
 * Left pane (40 %): Scrollable list of available registers — each is a selectable card
 * showing register name and status badge. Already-open registers show an error chip.
 *
 * Right pane (60 %): ZyntaNumericPad (PRICE mode) for entering the opening balance,
 * optional notes field, and a primary "Open Register" button.
 *
 * ## Layout (Compact / Phone)
 * Single-column scroll: register list → numeric pad → notes → CTA.
 *
 * ## Error Handling
 * - No register selected → validation error above the register list.
 * - Backend returns SESSION_ALREADY_OPEN → snackbar + register card re-highlighted in red.
 *
 * @param viewModel   Shared [RegisterViewModel]; default resolved by Koin.
 * @param onOpened    Called after a session is successfully opened (navigate to Dashboard).
 */
@Composable
fun OpenRegisterScreen(
    viewModel: RegisterViewModel = koinViewModel(),
    onOpened: () -> Unit = {},
) {
    val s = LocalStrings.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    // Load available registers on first composition
    LaunchedEffect(Unit) {
        viewModel.dispatch(RegisterIntent.LoadAvailableRegisters)
    }

    // Collect one-shot effects
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RegisterEffect.NavigateToDashboard -> onOpened()
                is RegisterEffect.ShowSuccess -> snackbarHost.showSnackbar(effect.message)
                is RegisterEffect.ShowError -> snackbarHost.showSnackbar(effect.message)
                else -> Unit
            }
        }
    }

    ZyntaPageScaffold(
        title = s[StringResource.REGISTER_OPEN_REGISTER],
        snackbarHostState = snackbarHost,
    ) { innerPadding ->
        val form = state.openRegisterForm

        // Responsive layout: row on wide screens, column on compact
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(ZyntaSpacing.md),
        ) {
            val isWide = maxWidth >= 600.dp

            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.lg),
                ) {
                    // ── Left: Register selector ──────────────────────────
                    RegisterSelectorPanel(
                        modifier = Modifier.weight(0.4f).fillMaxHeight(),
                        registers = state.availableRegisters,
                        selectedId = form.selectedRegisterId,
                        error = form.validationErrors["register"],
                        onSelect = { viewModel.dispatch(RegisterIntent.SelectRegister(it)) },
                    )

                    // ── Right: Opening balance + CTA ─────────────────────
                    OpeningBalancePanel(
                        modifier = Modifier.weight(0.6f).fillMaxHeight(),
                        form = form,
                        isLoading = state.isLoading,
                        onDigit = { viewModel.dispatch(RegisterIntent.OpeningBalanceDigit(it)) },
                        onDoubleZero = { viewModel.dispatch(RegisterIntent.OpeningBalanceDoubleZero) },
                        onDecimal = { viewModel.dispatch(RegisterIntent.OpeningBalanceDecimal) },
                        onBackspace = { viewModel.dispatch(RegisterIntent.OpeningBalanceBackspace) },
                        onClear = { viewModel.dispatch(RegisterIntent.OpeningBalanceClear) },
                        onNotesChanged = { viewModel.dispatch(RegisterIntent.OpeningNotesChanged(it)) },
                        onConfirm = { viewModel.dispatch(RegisterIntent.ConfirmOpenRegister) },
                    )
                }
            } else {
                // Compact: single scrollable column
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
                ) {
                    item {
                        RegisterSelectorPanel(
                            modifier = Modifier.fillMaxWidth(),
                            registers = state.availableRegisters,
                            selectedId = form.selectedRegisterId,
                            error = form.validationErrors["register"],
                            onSelect = { viewModel.dispatch(RegisterIntent.SelectRegister(it)) },
                        )
                    }
                    item {
                        OpeningBalancePanel(
                            modifier = Modifier.fillMaxWidth(),
                            form = form,
                            isLoading = state.isLoading,
                            onDigit = { viewModel.dispatch(RegisterIntent.OpeningBalanceDigit(it)) },
                            onDoubleZero = { viewModel.dispatch(RegisterIntent.OpeningBalanceDoubleZero) },
                            onDecimal = { viewModel.dispatch(RegisterIntent.OpeningBalanceDecimal) },
                            onBackspace = { viewModel.dispatch(RegisterIntent.OpeningBalanceBackspace) },
                            onClear = { viewModel.dispatch(RegisterIntent.OpeningBalanceClear) },
                            onNotesChanged = { viewModel.dispatch(RegisterIntent.OpeningNotesChanged(it)) },
                            onConfirm = { viewModel.dispatch(RegisterIntent.ConfirmOpenRegister) },
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Register Selector Panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RegisterSelectorPanel(
    registers: List<CashRegister>,
    selectedId: String?,
    error: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    Column(modifier = modifier) {
        Text(
            text = s[StringResource.REGISTER_SELECT_REGISTER],
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(ZyntaSpacing.sm))

        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(ZyntaSpacing.xs))
        }

        if (registers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = ZyntaSpacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = s[StringResource.REGISTER_NO_REGISTERS_AVAILABLE],
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(registers, key = { it.id }) { register ->
                    RegisterCard(
                        register = register,
                        isSelected = register.id == selectedId,
                        onClick = { onSelect(register.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RegisterCard(
    register: CashRegister,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val isAlreadyOpen = register.currentSessionId != null
    val borderColor = when {
        isAlreadyOpen -> MaterialTheme.colorScheme.error
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val containerColor = when {
        isSelected && !isAlreadyOpen -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isAlreadyOpen, onClick = onClick),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor,
        ),
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier.padding(ZyntaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = register.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                if (isAlreadyOpen) {
                    Text(
                        text = "Already open",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        text = "Available",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (isSelected && !isAlreadyOpen) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Opening Balance Panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OpeningBalancePanel(
    form: OpenRegisterFormState,
    isLoading: Boolean,
    onDigit: (String) -> Unit,
    onDoubleZero: () -> Unit,
    onDecimal: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onNotesChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Opening Balance",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(ZyntaSpacing.md))

        // Format: right-to-left → "12345" shows as "123.45"
        val displayValue = buildString {
            val raw = form.openingBalanceRaw.padStart(3, '0')
            append(raw.dropLast(2))
            append(".")
            append(raw.takeLast(2))
        }

        ZyntaNumericPad(
            displayValue = displayValue,
            onDigit = onDigit,
            onDoubleZero = onDoubleZero,
            onDecimal = onDecimal,
            onBackspace = onBackspace,
            onClear = onClear,
            mode = NumericPadMode.PRICE,
        )

        Spacer(Modifier.height(ZyntaSpacing.md))

        // Opening notes (optional)
        OutlinedTextField(
            value = form.openingNotes,
            onValueChange = onNotesChanged,
            label = { Text("Opening Notes (optional)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
        )

        Spacer(Modifier.height(ZyntaSpacing.lg))

        // Confirm button
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Open Register", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
