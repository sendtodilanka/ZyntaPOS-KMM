package com.zyntasolutions.zyntapos.feature.register

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.CashMovement
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Z-Report screen displaying a printable end-of-shift summary (Sprint 21, task 11.1.8).
 *
 * ## Layout
 * Single-column scrollable report designed to mirror the thermal printout layout:
 * 1. **Store info header** — store name, register ID (centered).
 * 2. **Session info** — session ID, opened/closed by, open/close timestamps.
 * 3. **Opening balance** — the float entered when the register was opened.
 * 4. **Cash movements** — chronological list of cash in/out with reasons.
 * 5. **Sales summary** — total sales by payment method (placeholder until POS wired).
 * 6. **Expected vs Actual** — system-calculated vs physically counted balance.
 * 7. **Discrepancy line** — over/short indication with color coding.
 * 8. **Signature line** — blank line for manager sign-off.
 * 9. **Print button** — triggers [PrintZReportUseCase] via thermal printer.
 *
 * ## Responsive
 * - **Expanded:** Report is centered with max-width 600dp for readability.
 * - **Compact:** Full-width with standard padding.
 *
 * @param sessionId The closed session ID to display.
 * @param viewModel Shared [RegisterViewModel]; default resolved by Koin.
 * @param onBack    Navigate back (to Dashboard or home).
 */
@Composable
fun ZReportScreen(
    sessionId: String,
    viewModel: RegisterViewModel = koinViewModel(),
    onBack: () -> Unit = {},
    currencyFormatter: CurrencyFormatter = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val session = state.zReportSession
    val movements = state.zReportMovements
    val fmt: (Double) -> String = { currencyFormatter.formatPlain(it) }

    // Load Z-report data on first composition
    LaunchedEffect(sessionId) {
        viewModel.dispatch(RegisterIntent.LoadZReport(sessionId))
    }

    // Collect one-shot effects
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RegisterEffect.ShowSuccess -> snackbarHost.showSnackbar(effect.message)
                is RegisterEffect.ShowError -> snackbarHost.showSnackbar(effect.message)
                else -> Unit
            }
        }
    }

    ZyntaPageScaffold(
        title = "Z-Report",
        onNavigateBack = onBack,
        snackbarHostState = snackbarHost,
        actions = {
            // Print button in toolbar
            IconButton(
                onClick = { viewModel.dispatch(RegisterIntent.PrintZReport(sessionId)) },
                enabled = !state.isPrintingZReport && session != null,
            ) {
                if (state.isPrintingZReport) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = "Print Z-Report",
                    )
                }
            }
        },
    ) { padding ->
        if (session == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        text = "No Z-report data available.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@ZyntaPageScaffold
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            val maxReportWidth = if (maxWidth > 720.dp) 600.dp else maxWidth

            Column(
                modifier = Modifier
                    .widthIn(max = maxReportWidth)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(ZyntaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            ) {
                // ── 1. Store header ──────────────────────────────────────
                ZReportHeader()

                ReportDivider()

                // ── 2. Session info ──────────────────────────────────────
                ZReportSessionInfo(session)

                ReportDivider()

                // ── 3. Opening balance ───────────────────────────────────
                ReportRow("Opening Balance", fmt(session.openingBalance))

                ReportDivider()

                // ── 4. Cash movements ────────────────────────────────────
                ZReportCashMovements(movements, fmt)

                ReportDivider()

                // ── 5. Sales summary (placeholder) ───────────────────────
                ZReportSalesSummary(fmt)

                ReportDivider()

                // ── 6 & 7. Expected vs Actual + Discrepancy ──────────────
                ZReportBalanceReconciliation(session, fmt)

                ReportDivider()

                // ── 8. Signature line ────────────────────────────────────
                ZReportSignatureLine()

                Spacer(Modifier.height(ZyntaSpacing.xl))

                // ── Print button (bottom of report) ──────────────────────
                Button(
                    onClick = { viewModel.dispatch(RegisterIntent.PrintZReport(sessionId)) },
                    enabled = !state.isPrintingZReport,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    if (state.isPrintingZReport) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(ZyntaSpacing.sm))
                        Text("Print Z-Report", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(ZyntaSpacing.lg))
            }
        }
    }
}

// ─── Z-Report Composable Sections ────────────────────────────────────────────

/**
 * Centered store header banner mirroring the thermal printout.
 */
@Composable
private fun ZReportHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Z-REPORT",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "End of Shift Summary",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Session details: ID, opened/closed by, timestamps.
 */
@Composable
private fun ZReportSessionInfo(session: RegisterSession) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Session Details",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        ReportRow("Session ID", session.id.takeLast(8))
        ReportRow("Opened By", session.openedBy.takeLast(16))
        ReportRow("Opened At", session.openedAt.toString())
        session.closedBy?.let { ReportRow("Closed By", it.takeLast(16)) }
        session.closedAt?.let { ReportRow("Closed At", it.toString()) }
        ReportRow("Status", session.status.name)
    }
}

/**
 * Chronological list of cash in/out movements during the session.
 */
@Composable
private fun ZReportCashMovements(movements: List<CashMovement>, fmt: (Double) -> String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Cash Movements",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )

        if (movements.isEmpty()) {
            Text(
                text = "No cash movements recorded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val totalIn = movements.filter { it.type == CashMovement.Type.IN }.sumOf { it.amount }
            val totalOut = movements.filter { it.type == CashMovement.Type.OUT }.sumOf { it.amount }

            movements.forEach { movement ->
                val prefix = if (movement.type == CashMovement.Type.IN) "+" else "-"
                val label = "${movement.type.name}: ${movement.reason}"
                ReportRow(label, "$prefix${fmt(movement.amount)}")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            ReportRow("Total Cash In", "+${fmt(totalIn)}")
            ReportRow("Total Cash Out", "-${fmt(totalOut)}")
            ReportRow("Net Movement", fmt(totalIn - totalOut))
        }
    }
}

/**
 * Sales summary by payment method.
 * Phase 1 placeholder — will be wired to OrderRepository in Phase 2.
 */
@Composable
private fun ZReportSalesSummary(fmt: (Double) -> String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Sales Summary",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Sales breakdown by payment method will be available when POS order data is integrated.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Placeholder totals — these will be populated from OrderRepository
        ReportRow("Cash Sales", fmt(0.0))
        ReportRow("Card Sales", fmt(0.0))
        ReportRow("Mobile Sales", fmt(0.0))
        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
        ReportRow("Total Sales", fmt(0.0))
    }
}

/**
 * Expected vs Actual balance reconciliation with discrepancy.
 */
@Composable
private fun ZReportBalanceReconciliation(session: RegisterSession, fmt: (Double) -> String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Balance Reconciliation",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        ReportRow("Expected Balance", fmt(session.expectedBalance))
        session.actualBalance?.let { actual ->
            ReportRow("Actual Balance", fmt(actual))

            val variance = actual - session.expectedBalance
            val isWarning = kotlin.math.abs(variance) > 10.0 // matches default threshold

            val varianceText = when {
                variance > 0.0 -> "+${fmt(variance)} (OVER)"
                variance < 0.0 -> "${fmt(variance)} (SHORT)"
                else -> "${fmt(0.0)} (EXACT)"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Discrepancy",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = varianceText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isWarning) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                )
            }
        }
    }
}

/**
 * Blank signature line for manager sign-off.
 */
@Composable
private fun ZReportSignatureLine() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = ZyntaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
    ) {
        Text(
            text = "Operator Signature",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(ZyntaSpacing.lg))
        Text(
            text = "Manager Signature",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

// ─── Shared Report Composables ───────────────────────────────────────────────

/**
 * A full-width divider styled as a report separator.
 */
@Composable
private fun ReportDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = ZyntaSpacing.sm),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * Left-aligned label, right-aligned value — mirrors the thermal printout row format.
 */
@Composable
private fun ReportRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

