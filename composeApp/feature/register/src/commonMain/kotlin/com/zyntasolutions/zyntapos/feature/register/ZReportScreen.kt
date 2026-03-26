package com.zyntasolutions.zyntapos.feature.register

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
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
    val s = LocalStrings.current
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
                is RegisterEffect.A4ZReportPrinted -> snackbarHost.showSnackbar(s[StringResource.REGISTER_ZREPORT_SENT])
                else -> Unit
            }
        }
    }

    ZyntaPageScaffold(
        title = s[StringResource.REGISTER_ZREPORT_TITLE],
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
                        contentDescription = s[StringResource.REGISTER_ZREPORT_PRINT_DESC],
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
                        text = s[StringResource.REGISTER_NO_ZREPORT],
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
                ReportRow(s[StringResource.REGISTER_OPENING_TITLE], fmt(session.openingBalance))

                ReportDivider()

                // ── 4. Cash movements ────────────────────────────────────
                ZReportCashMovements(movements, fmt)

                ReportDivider()

                // ── 5. Sales summary ────────────────────────────────────
                ZReportSalesSummary(state.zReportSalesByPayment, fmt)

                ReportDivider()

                // ── 6 & 7. Expected vs Actual + Discrepancy ──────────────
                ZReportBalanceReconciliation(session, fmt)

                ReportDivider()

                // ── 8. Signature line ────────────────────────────────────
                ZReportSignatureLine()

                Spacer(Modifier.height(ZyntaSpacing.xl))

                // ── Print buttons (bottom of report) ─────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                ) {
                    // Thermal receipt printer
                    Button(
                        onClick = { viewModel.dispatch(RegisterIntent.PrintZReport(sessionId)) },
                        enabled = !state.isPrintingZReport && !state.isPrintingA4ZReport,
                        modifier = Modifier.weight(1f).height(56.dp),
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
                            Text(s[StringResource.REGISTER_THERMAL_PRINT], fontWeight = FontWeight.Bold)
                        }
                    }

                    // A4 PDF via system print dialog
                    OutlinedButton(
                        onClick = { viewModel.dispatch(RegisterIntent.PrintA4ZReport(sessionId)) },
                        enabled = !state.isPrintingZReport && !state.isPrintingA4ZReport,
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        if (state.isPrintingA4ZReport) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(ZyntaSpacing.sm))
                            Text(s[StringResource.REGISTER_ZREPORT_A4], fontWeight = FontWeight.Bold)
                        }
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
    val s = LocalStrings.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = s[StringResource.REGISTER_ZREPORT_HEADER],
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = s[StringResource.REGISTER_END_OF_SHIFT],
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
    val s = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = s[StringResource.REGISTER_SESSION_DETAILS],
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        ReportRow(s[StringResource.REGISTER_SESSION_ID_LABEL], session.id.takeLast(8))
        ReportRow(s[StringResource.REGISTER_OPENED_BY], session.openedBy.takeLast(16))
        ReportRow(s[StringResource.REGISTER_OPENED_AT], session.openedAt.toString())
        session.closedBy?.let { ReportRow(s[StringResource.REGISTER_CLOSED_BY], it.takeLast(16)) }
        session.closedAt?.let { ReportRow(s[StringResource.REGISTER_CLOSED_AT], it.toString()) }
        ReportRow(s[StringResource.REGISTER_STATUS], session.status.name)
    }
}

/**
 * Chronological list of cash in/out movements during the session.
 */
@Composable
private fun ZReportCashMovements(movements: List<CashMovement>, fmt: (Double) -> String) {
    val s = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = s[StringResource.REGISTER_CASH_MOVEMENTS_TITLE],
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )

        if (movements.isEmpty()) {
            Text(
                text = s[StringResource.REGISTER_NO_CASH_MOVEMENTS],
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
            ReportRow(s[StringResource.REGISTER_TOTAL_CASH_IN], "+${fmt(totalIn)}")
            ReportRow(s[StringResource.REGISTER_TOTAL_CASH_OUT], "-${fmt(totalOut)}")
            ReportRow(s[StringResource.REGISTER_NET_MOVEMENT], fmt(totalIn - totalOut))
        }
    }
}

/**
 * Sales summary by payment method, populated from OrderRepository.
 */
@Composable
private fun ZReportSalesSummary(salesByPayment: Map<String, Double>, fmt: (Double) -> String) {
    val s = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = s[StringResource.REGISTER_SALES_SUMMARY],
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        if (salesByPayment.isEmpty()) {
            Text(
                text = s[StringResource.REGISTER_NO_SALES_RECORDED],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            salesByPayment.forEach { (method, amount) ->
                val label = method.lowercase().replaceFirstChar { it.uppercase() } + s[StringResource.REGISTER_SALES_SUFFIX]
                ReportRow(label, fmt(amount))
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
        ReportRow(s[StringResource.REGISTER_TOTAL_SALES], fmt(salesByPayment.values.sum()))
    }
}

/**
 * Expected vs Actual balance reconciliation with discrepancy.
 */
@Composable
private fun ZReportBalanceReconciliation(session: RegisterSession, fmt: (Double) -> String) {
    val s = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = s[StringResource.REGISTER_BALANCE_RECONCILIATION],
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        ReportRow(s[StringResource.REGISTER_EXPECTED_BALANCE], fmt(session.expectedBalance))
        session.actualBalance?.let { actual ->
            ReportRow(s[StringResource.REGISTER_ACTUAL_BALANCE], fmt(actual))

            val variance = actual - session.expectedBalance
            val isWarning = kotlin.math.abs(variance) > 10.0 // matches default threshold

            val varianceText = when {
                variance > 0.0 -> "+${fmt(variance)}${s[StringResource.REGISTER_DISCREPANCY_OVER]}"
                variance < 0.0 -> "${fmt(variance)}${s[StringResource.REGISTER_DISCREPANCY_SHORT]}"
                else -> "${fmt(0.0)}${s[StringResource.REGISTER_DISCREPANCY_EXACT]}"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = s[StringResource.REGISTER_DISCREPANCY],
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
    val s = LocalStrings.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = ZyntaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
    ) {
        Text(
            text = s[StringResource.REGISTER_OPERATOR_SIGNATURE],
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
            text = s[StringResource.REGISTER_MANAGER_SIGNATURE],
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

