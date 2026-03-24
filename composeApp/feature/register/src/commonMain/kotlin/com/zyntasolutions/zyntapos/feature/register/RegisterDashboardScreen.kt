package com.zyntasolutions.zyntapos.feature.register

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.CashMovement
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

/**
 * Register Dashboard — shown immediately after a session is opened.
 *
 * ## Layout (Expanded / Desktop)
 * Two-column layout:
 * - Left (40%): Status banner + session info card + quick-stats + cash movement summary + action buttons.
 * - Right (60%): [CashMovementHistory] showing the full movement log for the active session.
 *
 * ## Layout (Compact / Phone)
 * Single-column scroll: status banner → session info → stats → cash summary → actions → movements.
 *
 * ## Key Components
 * - **Register Status Banner** — green (OPEN) or amber (CLOSED) with session duration / last closed time.
 * - **Session Info Card** — opened-by, opened-at, opening balance, running expected balance.
 * - **Professional Stat Cards** — total orders today, revenue today with accent icons and helper text.
 * - **Cash Movement Summary** — total cash in, total cash out, expected balance (colored metric cards).
 * - **Action Buttons** — "Open Register" / "Close Register" with prominent styling and state-aware disabling.
 * - **[CashMovementHistory]** — reactive list of movements for the current session.
 *
 * @param viewModel      Shared [RegisterViewModel]; resolved by Koin.
 * @param onNavigateBack Optional back navigation callback (e.g., to Dashboard or POS).
 */
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

    ZyntaPageScaffold(
        title = "Register",
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHost,
        actions = {
            if (state.storeName.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = ZyntaSpacing.sm),
                ) {
                    Icon(
                        imageVector = Icons.Default.Store,
                        contentDescription = "Current store",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = state.storeName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
            return@ZyntaPageScaffold
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(ZyntaSpacing.md),
        ) {
            val isWide = maxWidth > 720.dp

            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.lg),
                ) {
                    // -- Left panel: status + info + stats + actions --
                    Column(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
                    ) {
                        RegisterStatusBanner(session = session, registerName = state.activeRegister?.name)
                        SessionInfoCard(session = session, registerName = state.activeRegister?.name)

                        // Quick Stats Section
                        SectionCard(title = "Today's Performance", icon = Icons.Default.Insights) {
                            QuickStatsRow(
                                orderCount = state.todayOrderCount,
                                revenue = state.todayRevenue,
                            )
                        }

                        // Cash Movement Summary
                        CashMovementSummarySection(
                            movements = state.movements,
                            expectedBalance = session.expectedBalance,
                        )

                        // Action Buttons Section
                        SectionCard(title = "Actions", icon = Icons.Default.TouchApp) {
                            RegisterActionButtons(
                                isOpen = session.status == RegisterSession.Status.OPEN,
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
                                onOpenDrawer = {
                                    viewModel.dispatch(RegisterIntent.OpenCashDrawer)
                                },
                            )
                        }
                    }

                    // -- Right panel: movement history --
                    Card(
                        modifier = Modifier.weight(0.6f).fillMaxHeight(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        CashMovementHistory(
                            movements = state.movements,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(ZyntaSpacing.md),
                        )
                    }
                }
            } else {
                // Compact: single column
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
                ) {
                    RegisterStatusBanner(session = session, registerName = state.activeRegister?.name)
                    SessionInfoCard(session = session, registerName = state.activeRegister?.name)

                    SectionCard(title = "Today's Performance", icon = Icons.Default.Insights) {
                        QuickStatsRow(
                            orderCount = state.todayOrderCount,
                            revenue = state.todayRevenue,
                        )
                    }

                    CashMovementSummarySection(
                        movements = state.movements,
                        expectedBalance = session.expectedBalance,
                    )

                    SectionCard(title = "Actions", icon = Icons.Default.TouchApp) {
                        RegisterActionButtons(
                            isOpen = session.status == RegisterSession.Status.OPEN,
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
                            onOpenDrawer = {
                                viewModel.dispatch(RegisterIntent.OpenCashDrawer)
                            },
                        )
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        CashMovementHistory(
                            movements = state.movements,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp)
                                .padding(ZyntaSpacing.md),
                        )
                    }
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

// ---------------------------------------------------------------------------
// Register Status Banner
// ---------------------------------------------------------------------------

@Composable
private fun RegisterStatusBanner(session: RegisterSession, registerName: String? = null) {
    val isOpen = session.status == RegisterSession.Status.OPEN

    val bannerContainerColor: Color
    val bannerContentColor: Color
    val bannerIcon: ImageVector
    val bannerTitle: String
    val bannerSubtitle: String

    if (isOpen) {
        bannerContainerColor = MaterialTheme.colorScheme.primaryContainer
        bannerContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        bannerIcon = Icons.Default.CheckCircle
        bannerTitle = if (registerName != null) "$registerName — OPEN" else "Register is OPEN"
        bannerSubtitle = run {
            val openedLocal = session.openedAt.toLocalDateTime(TimeZone.currentSystemDefault())
            val durationMs = Clock.System.now().toEpochMilliseconds() - session.openedAt.toEpochMilliseconds()
            val hours = durationMs / 3_600_000
            val minutes = (durationMs % 3_600_000) / 60_000
            val openedHH = openedLocal.hour.toString().padStart(2, '0')
            val openedMM = openedLocal.minute.toString().padStart(2, '0')
            "Session active for ${hours}h ${minutes}m  \u2022  Opened $openedHH:$openedMM"
        }
    } else {
        bannerContainerColor = MaterialTheme.colorScheme.tertiaryContainer
        bannerContentColor = MaterialTheme.colorScheme.onTertiaryContainer
        bannerIcon = Icons.Default.Lock
        bannerTitle = if (registerName != null) "$registerName — CLOSED" else "Register is CLOSED"
        bannerSubtitle = if (session.closedAt != null) {
            val closedLocal = session.closedAt!!.toLocalDateTime(TimeZone.currentSystemDefault())
            "Last closed: ${closedLocal.date} at ${closedLocal.hour.toString().padStart(2, '0')}:${closedLocal.minute.toString().padStart(2, '0')}"
        } else {
            "No active session"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bannerContainerColor),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ZyntaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            Icon(
                imageVector = bannerIcon,
                contentDescription = null,
                tint = bannerContentColor,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bannerTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = bannerContentColor,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = bannerSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = bannerContentColor.copy(alpha = 0.8f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Session Info Card
// ---------------------------------------------------------------------------

@Composable
private fun SessionInfoCard(session: RegisterSession, registerName: String? = null) {
    val openedAtFormatted = remember(session.openedAt) {
        val local = session.openedAt.toLocalDateTime(TimeZone.currentSystemDefault())
        "${local.date} ${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column {
                    Text(
                        text = "Active Session",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Current register session details",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            if (registerName != null) {
                SessionRow(label = "Register", value = registerName)
            }
            SessionRow(label = "Opened at", value = openedAtFormatted)
            SessionRow(label = "Opened by", value = session.openedBy)
            SessionRow(label = "Opening balance", value = "%.2f".format(session.openingBalance))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Expected balance",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "%.2f".format(session.expectedBalance),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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

// ---------------------------------------------------------------------------
// Section Card Wrapper
// ---------------------------------------------------------------------------

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(ZyntaSpacing.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(ZyntaSpacing.sm))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(ZyntaSpacing.sm))
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// Quick Stats Row — Professional styled stat cards
// ---------------------------------------------------------------------------

@Composable
private fun QuickStatsRow(orderCount: Int, revenue: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
    ) {
        ProfessionalStatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.ShoppingCart,
            accentColor = MaterialTheme.colorScheme.primary,
            title = "Orders Today",
            value = orderCount.toString(),
            helperText = "Total completed orders",
        )
        ProfessionalStatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.AttachMoney,
            accentColor = MaterialTheme.colorScheme.tertiary,
            title = "Revenue Today",
            value = "%.2f".format(revenue),
            helperText = "Gross sales amount",
        )
    }
}

@Composable
private fun ProfessionalStatCard(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    value: String,
    helperText: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Colored accent strip at the top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(accentColor),
            )
            Column(
                modifier = Modifier.padding(ZyntaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
            ) {
                // Icon in tonal container
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = accentColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.height(ZyntaSpacing.xs))
                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Value
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // Helper text
                Text(
                    text = helperText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Cash Movement Summary Section
// ---------------------------------------------------------------------------

@Composable
private fun CashMovementSummarySection(
    movements: List<CashMovement>,
    expectedBalance: Double,
) {
    val totalCashIn = remember(movements) {
        movements.filter { it.type == CashMovement.Type.IN }.sumOf { it.amount }
    }
    val totalCashOut = remember(movements) {
        movements.filter { it.type == CashMovement.Type.OUT }.sumOf { it.amount }
    }

    SectionCard(title = "Cash Movement Summary", icon = Icons.Default.SwapVert) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            // Cash In metric
            CashMetricCard(
                modifier = Modifier.weight(1f),
                label = "Total Cash In",
                value = "+%.2f".format(totalCashIn),
                color = MaterialTheme.colorScheme.tertiary,
                icon = Icons.Default.ArrowUpward,
            )
            // Cash Out metric
            CashMetricCard(
                modifier = Modifier.weight(1f),
                label = "Total Cash Out",
                value = "-%.2f".format(totalCashOut),
                color = MaterialTheme.colorScheme.error,
                icon = Icons.Default.ArrowDownward,
            )
        }

        Spacer(Modifier.height(ZyntaSpacing.sm))

        // Expected balance highlight
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            ),
            shape = MaterialTheme.shapes.small,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Expected Balance",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "Based on opening + movements",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
                Text(
                    text = "%.2f".format(expectedBalance),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun CashMetricCard(
    label: String,
    value: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.08f),
    ) {
        Row(
            modifier = Modifier.padding(ZyntaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Action Buttons — Prominent, state-aware
// ---------------------------------------------------------------------------

@Composable
private fun RegisterActionButtons(
    isOpen: Boolean,
    onCashIn: () -> Unit,
    onCashOut: () -> Unit,
    onOpenDrawer: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
    ) {
        // Cash In / Cash Out buttons (enabled only when register is open)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            Button(
                onClick = onCashIn,
                enabled = isOpen,
                modifier = Modifier
                    .weight(1f)
                    .height(ZyntaSpacing.touchPreferred),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(ZyntaSpacing.xs))
                Text(
                    text = "Cash In",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            OutlinedButton(
                onClick = onCashOut,
                enabled = isOpen,
                modifier = Modifier
                    .weight(1f)
                    .height(ZyntaSpacing.touchPreferred),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(ZyntaSpacing.xs))
                Text(
                    text = "Cash Out",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Open Drawer button
        OutlinedButton(
            onClick = onOpenDrawer,
            enabled = isOpen,
            modifier = Modifier
                .fillMaxWidth()
                .height(ZyntaSpacing.touchPreferred),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(
                Icons.Default.LockOpen,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(ZyntaSpacing.xs))
            Text(
                text = "Open Drawer",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
