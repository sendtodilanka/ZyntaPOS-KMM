package com.zyntasolutions.zyntapos.feature.register

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.CashMovement
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

/**
 * Register Dashboard — shown immediately after a session is opened.
 *
 * ## Layout (Expanded / Desktop)
 * Two-column layout:
 * - Left (35%): Session info card + quick-stats row + Cash In / Cash Out action buttons.
 * - Right (65%): [CashMovementHistory] showing the full movement log for the active session.
 *
 * ## Layout (Compact / Phone)
 * Single-column scroll: session info card → stats row → action buttons → movements.
 *
 * ## Key Components
 * - **Session Info Card** — opened-by, opened-at, opening balance, running expected balance.
 * - **Quick Stats** — total orders today, revenue today (placeholder from ViewModel).
 * - **Action Buttons** — "Cash In" / "Cash Out" (opens [CashInOutDialog]).
 * - **[CashMovementHistory]** — reactive list of movements for the current session.
 *
 * @param viewModel      Shared [RegisterViewModel]; resolved by Koin.
 * @param onNavigateBack Optional back navigation callback (e.g., to Dashboard or POS).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterDashboardScreen(
    viewModel: RegisterViewModel = koinViewModel(),
    onNavigateBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    // Load stats on entry
    LaunchedEffect(Unit) {
        viewModel.dispatch(RegisterIntent.LoadDashboardStats)
    }

    // Collect effects
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RegisterEffect.ShowSuccess -> snackbarHost.showSnackbar(effect.message)
                is RegisterEffect.ShowError -> snackbarHost.showSnackbar(effect.message)
                else -> Unit
            }
        }
    }

    val session = state.activeSession

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("Register") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (session == null) {
            // Guard re-routes before this is shown, but handle null defensively
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(ZyntaSpacing.md),
        ) {
            val isWide = maxWidth >= 700.dp

            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.lg),
                ) {
                    // ── Left panel ───────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .weight(0.35f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
                    ) {
                        SessionInfoCard(session = session)
                        QuickStatsRow(
                            orderCount = state.todayOrderCount,
                            revenue = state.todayRevenue,
                        )
                        CashActionButtons(
                            onCashIn = {
                                viewModel.dispatch(
                                    RegisterIntent.ShowCashInOutDialog(CashMovement.Type.IN)
                                )
                            },
                            onCashOut = {
                                viewModel.dispatch(
                                    RegisterIntent.ShowCashInOutDialog(CashMovement.Type.OUT)
                                )
                            },
                        )
                    }

                    // ── Right panel: movement history ─────────────────────
                    CashMovementHistory(
                        movements = state.movements,
                        modifier = Modifier.weight(0.65f).fillMaxHeight(),
                    )
                }
            } else {
                // Compact: single column
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
                ) {
                    SessionInfoCard(session = session)
                    QuickStatsRow(
                        orderCount = state.todayOrderCount,
                        revenue = state.todayRevenue,
                    )
                    CashActionButtons(
                        onCashIn = {
                            viewModel.dispatch(
                                RegisterIntent.ShowCashInOutDialog(CashMovement.Type.IN)
                            )
                        },
                        onCashOut = {
                            viewModel.dispatch(
                                RegisterIntent.ShowCashInOutDialog(CashMovement.Type.OUT)
                            )
                        },
                    )
                    CashMovementHistory(
                        movements = state.movements,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    )
                }
            }
        }

        // Cash In/Out Dialog — rendered above scaffold content
        val dialog = state.cashInOutDialog
        if (dialog != null) {
            CashInOutDialog(
                dialogState = dialog,
                isLoading = state.isLoading,
                onTypeChange = { viewModel.dispatch(RegisterIntent.SetCashInOutType(it)) },
                onDigit = { viewModel.dispatch(RegisterIntent.CashInOutAmountDigit(it)) },
                onDoubleZero = { viewModel.dispatch(RegisterIntent.CashInOutAmountDoubleZero) },
                onDecimal = { viewModel.dispatch(RegisterIntent.CashInOutAmountDecimal) },
                onBackspace = { viewModel.dispatch(RegisterIntent.CashInOutAmountBackspace) },
                onClear = { viewModel.dispatch(RegisterIntent.CashInOutAmountClear) },
                onReasonChanged = { viewModel.dispatch(RegisterIntent.CashInOutReasonChanged(it)) },
                onConfirm = { viewModel.dispatch(RegisterIntent.ConfirmCashInOut) },
                onDismiss = { viewModel.dispatch(RegisterIntent.DismissCashInOut) },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Session Info Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SessionInfoCard(session: RegisterSession) {
    val openedAtFormatted = remember(session.openedAt) {
        val local = session.openedAt.toLocalDateTime(TimeZone.currentSystemDefault())
        "${local.date} ${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Active Session",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            HorizontalDivider()
            SessionRow(label = "Opened at", value = openedAtFormatted)
            SessionRow(label = "Opened by", value = session.openedBy)
            SessionRow(label = "Opening balance", value = "%.2f".format(session.openingBalance))
            SessionRow(label = "Expected balance", value = "%.2f".format(session.expectedBalance))
        }
    }
}

@Composable
private fun SessionRow(label: String, value: String) {
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
            fontWeight = FontWeight.Medium,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Quick Stats Row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QuickStatsRow(orderCount: Int, revenue: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.ShoppingCart,
            label = "Orders Today",
            value = orderCount.toString(),
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.AttachMoney,
            label = "Revenue Today",
            value = "%.2f".format(revenue),
        )
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Action Buttons
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CashActionButtons(
    onCashIn: () -> Unit,
    onCashOut: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        Button(
            onClick = onCashIn,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(ZyntaSpacing.xs))
            Text("Cash In")
        }
        OutlinedButton(
            onClick = onCashOut,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.Remove, contentDescription = null)
            Spacer(Modifier.width(ZyntaSpacing.xs))
            Text("Cash Out")
        }
    }
}
