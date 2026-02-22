package com.zyntasolutions.zyntapos.feature.auth.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyntasolutions.zyntapos.designsystem.components.*
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaSplitPane
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.designsystem.util.WindowSize
import com.zyntasolutions.zyntapos.designsystem.util.currentWindowSize
import com.zyntasolutions.zyntapos.feature.auth.AuthViewModel
import com.zyntasolutions.zyntapos.feature.auth.mvi.AuthEffect
import com.zyntasolutions.zyntapos.feature.auth.mvi.AuthIntent
import com.zyntasolutions.zyntapos.feature.auth.mvi.AuthState
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

/**
 * Root login screen — adapts layout based on [WindowSize].
 *
 * - **EXPANDED** (Desktop / landscape tablet ≥840dp): [ZyntaSplitPane] with
 *   illustration panel (40%) + login form (60%) per UI/UX plan §5.1.
 * - **COMPACT / MEDIUM** (phone, small tablet): Single-pane with centered logo + form.
 *
 * This composable is stateless — it collects state from [AuthViewModel] and
 * delegates all events back as [AuthIntent] dispatches.
 *
 * @param viewModel  Provided by Koin via `koinViewModel()` — no constructor injection here.
 * @param onNavigateToDashboard  Callback invoked on successful login → dashboard navigation.
 * @param onNavigateToRegisterGuard Callback when no open register session exists.
 * @param windowSize Override for preview/testing; defaults to the current window.
 */
@Composable
fun LoginScreen(
    viewModel: AuthViewModel = koinViewModel(),
    onNavigateToDashboard: () -> Unit = {},
    onNavigateToRegisterGuard: () -> Unit = {},
    windowSize: WindowSize = currentWindowSize(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Effect collection ─────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is AuthEffect.NavigateToDashboard    -> onNavigateToDashboard()
                is AuthEffect.NavigateToRegisterGuard -> onNavigateToRegisterGuard()
                is AuthEffect.ShowError              -> snackbarHostState.showSnackbar(effect.message)
                is AuthEffect.ShowPinLock            -> { /* PinLock triggered by SessionManager */ }
            }
        }
    }

    Scaffold(
        snackbarHost = { ZyntaSnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (windowSize) {
            WindowSize.EXPANDED -> ExpandedLoginLayout(
                state = state,
                onIntent = viewModel::dispatch,
                modifier = Modifier.padding(padding),
            )
            else -> CompactLoginLayout(
                state = state,
                onIntent = viewModel::dispatch,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EXPANDED layout — ZyntaSplitPane: illustration (left 40%) + form (right 60%)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExpandedLoginLayout(
    state: AuthState,
    onIntent: (AuthIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    ZyntaSplitPane(
        primaryWeight = 0.40f,
        modifier = modifier.fillMaxSize(),
        primaryContent = { LoginIllustrationPanel() },
        secondaryContent = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoginFormContent(
                    state = state,
                    onIntent = onIntent,
                    modifier = Modifier.widthIn(max = 480.dp).padding(ZyntaSpacing.xl),
                )
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPACT / MEDIUM layout — single pane with logo + centered form
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompactLoginLayout(
    state: AuthState,
    onIntent: (AuthIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(ZyntaSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 400.dp),
        ) {
            // Logo / Brand header
            ZyntaLogoHeader()
            Spacer(Modifier.height(ZyntaSpacing.xl))
            LoginFormContent(state = state, onIntent = onIntent)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared login form — stateless; renders purely from AuthState
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LoginFormContent(
    state: AuthState,
    onIntent: (AuthIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {

        Text(
            text = "Welcome Back",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Sign in to your ZyntaPOS account",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(ZyntaSpacing.xl))

        // Email field
        ZyntaTextField(
            label = "Email Address",
            value = state.email,
            onValueChange = { onIntent(AuthIntent.EmailChanged(it)) },
            leadingIcon = Icons.Default.Email,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            error = state.emailError,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(ZyntaSpacing.md))

        // Password field with visibility toggle
        ZyntaTextField(
            label = "Password",
            value = state.password,
            onValueChange = { onIntent(AuthIntent.PasswordChanged(it)) },
            leadingIcon = Icons.Default.Lock,
            trailingIcon = {
                IconButton(onClick = { onIntent(AuthIntent.TogglePasswordVisibility) }) {
                    Icon(
                        imageVector = if (state.isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (state.isPasswordVisible) "Hide password" else "Show password",
                    )
                }
            },
            visualTransformation = if (state.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            error = state.passwordError,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(ZyntaSpacing.sm))

        // Remember me + forgot password row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.rememberMe,
                    onCheckedChange = { onIntent(AuthIntent.RememberMeToggled(it)) },
                )
                Text("Remember Me", style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = { onIntent(AuthIntent.ForgotPasswordClicked) }) {
                Text("Forgot Password?")
            }
        }

        // Inline error banner
        if (state.error != null) {
            Spacer(Modifier.height(ZyntaSpacing.sm))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(ZyntaSpacing.md),
                )
            }
        }

        Spacer(Modifier.height(ZyntaSpacing.lg))

        ZyntaButton(
            text = "Login to Dashboard",
            onClick = { onIntent(AuthIntent.LoginClicked) },
            isLoading = state.isLoading,
            size = ZyntaButtonSize.Large,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Illustration panel (EXPANDED layout left side)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LoginIllustrationPanel(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(ZyntaSpacing.xxl),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Logo mark
            ZyntaLogoHeader()
            Spacer(Modifier.height(ZyntaSpacing.xl))
            Text(
                text = "Secure Point of Sale",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(ZyntaSpacing.md))
            Text(
                text = "Fast, reliable, and offline-ready POS for modern retail.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Zynta logo / brand header (shared between layouts)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ZyntaLogoHeader(modifier: Modifier = Modifier) {
    // TODO: Replace with SVG vector asset in Sprint 9 design-system polish pass.
    // For now, render a text-based logotype consistent with Material 3 brand guidance.
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Z",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
        Spacer(Modifier.width(ZyntaSpacing.sm))
        Text(
            text = "ZyntaPOS",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}
