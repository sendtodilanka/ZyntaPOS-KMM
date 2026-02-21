package com.zyntasolutions.zyntapos.feature.register

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyntasolutions.zyntapos.designsystem.components.NumericPadMode
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaNumericPad
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen for closing the active cash register session (Sprint 21, task 11.1.6).
 *
 * ## Layout (Expanded / Desktop)
 * Left pane (45 %): Session summary showing expected balance (read-only),
 * discrepancy display (green if within threshold, red if exceeds), and closing notes.
 *
 * Right pane (55 %): [ZyntaNumericPad] (PRICE mode) for entering the actual
 * counted cash, with the formatted amount displayed prominently above the pad.
 *
 * ## Layout (Compact / Phone)
 * Single-column scroll: session summary → actual balance pad → notes → CTA.
 *
 * ## Discrepancy Logic
 * - `discrepancy = actualBalance − expectedBalance`
 * - Green text when `|discrepancy| ≤ threshold` (configurable, default 10.00).
 * - Red text + warning icon when `|discrepancy| > threshold`.
 *
 * ## Close Flow
 * 1. Operator counts physical cash and enters amount via numeric pad.
 * 2. System shows discrepancy in real-time.
 * 3. Operator taps "Close Register" → confirmation dialog appears.
 * 4. On confirm → [CloseRegisterSessionUseCase] called → navigate to Z-Report.
 *
 * @param viewModel Shared [RegisterViewModel]; default resolved by Koin.
 * @param onBack    Called when the back button is pressed (navigate to Dashboard).
 * @param onClosed  Called after the register is successfully closed (navigate to Z-Report).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloseRegisterScreen(
    viewModel: RegisterViewModel = koinViewModel(),
    onBack: () -> Unit = {},
    onClosed: (sessionId: String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val closeForm = state.closeRegisterForm

    // Load expected balance on first composition
    LaunchedEffect(Unit) {
        viewModel.dispatch(RegisterIntent.LoadCloseRegisterData)
    }

    // Collect one-shot effects
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RegisterEffect.NavigateToZReport -> onClosed(effect.sessionId)
                is RegisterEffect.ShowSuccess -> snackbarHost.showSnackbar(effect.message)
                is RegisterEffect.ShowError -> snackbarHost.showSnackbar(effect.message)
                else -> Unit
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("Close Register") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val isExpanded = maxWidth > 720.dp

            if (isExpanded) {
                // ── Expanded: Two-pane layout ──────────────────────────────
                Row(
                    modifier = Modifier.fillMaxSize().padding(ZyntaSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.lg),
                ) {
                    // Left pane: Session summary + discrepancy
                    Column(
                        modifier = Modifier.weight(0.45f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
                    ) {
                        SessionSummarySection(state)
                        DiscrepancySection(closeForm)
                        ClosingNotesSection(
                            notes = closeForm.closingNotes,
                            onNotesChanged = { viewModel.dispatch(RegisterIntent.ClosingNotesChanged(it)) },
                        )
                        CloseRegisterButton(
                            isLoading = state.isLoading,
                            isDiscrepancyWarning = closeForm.isDiscrepancyWarning,
                            onClick = { viewModel.dispatch(RegisterIntent.ShowCloseConfirmation) },
                        )
                    }
                    // Right pane: Actual balance entry
                    Column(
                        modifier = Modifier.weight(0.55f),
                        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ActualBalanceDisplay(closeForm.actualBalanceDouble)
                        ActualBalanceNumericPad(viewModel)
                    }
                }
            } else {
                // ── Compact: Single-column scroll ──────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(ZyntaSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
                ) {
                    SessionSummarySection(state)
                    ActualBalanceDisplay(closeForm.actualBalanceDouble)
                    ActualBalanceNumericPad(viewModel)
                    DiscrepancySection(closeForm)
                    ClosingNotesSection(
                        notes = closeForm.closingNotes,
                        onNotesChanged = { viewModel.dispatch(RegisterIntent.ClosingNotesChanged(it)) },
                    )
                    CloseRegisterButton(
                        isLoading = state.isLoading,
                        isDiscrepancyWarning = closeForm.isDiscrepancyWarning,
                        onClick = { viewModel.dispatch(RegisterIntent.ShowCloseConfirmation) },
                    )
                }
            }
        }

        // ── Close Confirmation Dialog ──────────────────────────────────────
        if (closeForm.showConfirmation) {
            CloseConfirmationDialog(
                closeForm = closeForm,
                onConfirm = { viewModel.dispatch(RegisterIntent.ConfirmCloseRegister) },
                onDismiss = { viewModel.dispatch(RegisterIntent.DismissCloseConfirmation) },
            )
        }
    }
}

// ─── Private Composable Sections ─────────────────────────────────────────────

/**
 * Displays the current session info: register name, opened by, opening balance,
 * and the system-calculated expected balance.
 */
@Composable
private fun SessionSummarySection(state: RegisterState) {
    val session = state.activeSession ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            Text(
                text = "Session Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            HorizontalDivider()
            SummaryRow("Session ID", session.id.takeLast(8))
            SummaryRow("Opened At", session.openedAt.toString())
            SummaryRow("Opening Balance", formatCurrency(session.openingBalance))
            SummaryRow(
                label = "Expected Balance",
                value = formatCurrency(session.expectedBalance),
                valueColor = MaterialTheme.colorScheme.primary,
                isBold = true,
            )
        }
    }
}

/**
 * Row within the summary card: left-aligned label, right-aligned value.
 */
@Composable
private fun SummaryRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    isBold: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = valueColor,
        )
    }
}

/**
 * Displays the discrepancy between actual and expected balances.
 * Green when within threshold, red with warning icon when exceeding.
 */
@Composable
private fun DiscrepancySection(closeForm: CloseRegisterFormState) {
    val discrepancy = closeForm.discrepancy
    val isWarning = closeForm.isDiscrepancyWarning
    val discrepancyColor = if (isWarning) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.tertiary // green success
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isWarning) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Discrepancy",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isWarning) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    },
                )
                Text(
                    text = when {
                        discrepancy > 0.0 -> "+${formatCurrency(discrepancy)} (over)"
                        discrepancy < 0.0 -> "${formatCurrency(discrepancy)} (short)"
                        else -> formatCurrency(0.0) + " (exact)"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = discrepancyColor,
                )
            }
            if (isWarning) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Discrepancy warning",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

/**
 * Large formatted display of the entered actual balance amount.
 */
@Composable
private fun ActualBalanceDisplay(actualBalance: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Actual Cash Count",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(ZyntaSpacing.sm))
            Text(
                text = formatCurrency(actualBalance),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * ZyntaNumericPad wired to actual balance intents for the close-register flow.
 */
@Composable
private fun ActualBalanceNumericPad(viewModel: RegisterViewModel) {
    ZyntaNumericPad(
        mode = NumericPadMode.PRICE,
        onDigit = { viewModel.dispatch(RegisterIntent.ActualBalanceDigit(it)) },
        onDoubleZero = { viewModel.dispatch(RegisterIntent.ActualBalanceDoubleZero) },
        onDecimal = { /* no-op in PRICE mode */ },
        onBackspace = { viewModel.dispatch(RegisterIntent.ActualBalanceBackspace) },
        onClear = { viewModel.dispatch(RegisterIntent.ActualBalanceClear) },
    )
}

/**
 * Optional notes text field for the closing procedure.
 */
@Composable
private fun ClosingNotesSection(
    notes: String,
    onNotesChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = notes,
        onValueChange = onNotesChanged,
        label = { Text("Closing Notes (optional)") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 4,
    )
}

/**
 * Danger-styled button for closing the register.
 * Shows a circular progress indicator while loading.
 */
@Composable
private fun CloseRegisterButton(
    isLoading: Boolean,
    isDiscrepancyWarning: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onError,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = if (isDiscrepancyWarning) {
                    "Close Register (Discrepancy!)"
                } else {
                    "Close Register"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Confirmation dialog shown before finalising the register close.
 * Displays the actual vs expected balance and discrepancy for final review.
 */
@Composable
private fun CloseConfirmationDialog(
    closeForm: CloseRegisterFormState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            if (closeForm.isDiscrepancyWarning) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
        title = {
            Text(
                text = "Confirm Close Register",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                Text("Expected: ${formatCurrency(closeForm.expectedBalance)}")
                Text("Actual: ${formatCurrency(closeForm.actualBalanceDouble)}")

                val discrepancy = closeForm.discrepancy
                val discText = when {
                    discrepancy > 0.0 -> "+${formatCurrency(discrepancy)} (over)"
                    discrepancy < 0.0 -> "${formatCurrency(discrepancy)} (short)"
                    else -> formatCurrency(0.0) + " (exact)"
                }
                Text(
                    text = "Discrepancy: $discText",
                    fontWeight = FontWeight.Bold,
                    color = if (closeForm.isDiscrepancyWarning) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                )

                if (closeForm.isDiscrepancyWarning) {
                    Text(
                        text = "⚠ The discrepancy exceeds the threshold of ${formatCurrency(closeForm.discrepancyThreshold)}. " +
                            "Manager review may be required.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Text(
                    text = "This action cannot be undone. The register session will be permanently closed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Close Register")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// ─── Formatting Helpers ──────────────────────────────────────────────────────

/**
 * Formats a [Double] as a currency string with 2 decimal places.
 * Uses locale-neutral formatting suitable for display.
 */
private fun formatCurrency(amount: Double): String {
    val abs = kotlin.math.abs(amount)
    val int = abs.toLong()
    val frac = ((abs - int) * 100).toLong()
    val formatted = "$int.${frac.toString().padStart(2, '0')}"
    return if (amount < 0.0) "-$formatted" else formatted
}
