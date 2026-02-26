package com.zyntasolutions.zyntapos.feature.auth.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButtonSize
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaSnackbarHost
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTextField
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaSplitPane
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.designsystem.util.WindowSize
import com.zyntasolutions.zyntapos.designsystem.util.currentWindowSize
import com.zyntasolutions.zyntapos.feature.auth.SignUpEffect
import com.zyntasolutions.zyntapos.feature.auth.SignUpIntent
import com.zyntasolutions.zyntapos.feature.auth.SignUpState
import com.zyntasolutions.zyntapos.feature.auth.SignUpViewModel
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SignUpScreen(
    viewModel: SignUpViewModel = koinViewModel(),
    onNavigateToLogin: () -> Unit = {},
    onSignUpSuccess: () -> Unit = {},
    windowSize: WindowSize = currentWindowSize(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is SignUpEffect.NavigateToLogin -> onSignUpSuccess()
                is SignUpEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { ZyntaSnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (windowSize) {
            WindowSize.EXPANDED -> ExpandedSignUpLayout(
                state = state,
                onDispatch = viewModel::dispatch,
                onNavigateToLogin = onNavigateToLogin,
                modifier = Modifier.padding(padding),
            )
            else -> CompactSignUpLayout(
                state = state,
                onDispatch = viewModel::dispatch,
                onNavigateToLogin = onNavigateToLogin,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EXPANDED layout — ZyntaSplitPane: illustration (left 40%) + form (right 60%)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExpandedSignUpLayout(
    state: SignUpState,
    onDispatch: (SignUpIntent) -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ZyntaSplitPane(
        primaryWeight = 0.40f,
        modifier = modifier.fillMaxSize(),
        primaryContent = { SignUpIllustrationPanel() },
        secondaryContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .padding(ZyntaSpacing.xl),
                    shape = MaterialTheme.shapes.large,
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    SignUpFormContent(
                        state = state,
                        onDispatch = onDispatch,
                        onNavigateToLogin = onNavigateToLogin,
                        modifier = Modifier.padding(ZyntaSpacing.xl),
                    )
                }
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPACT / MEDIUM layout — single pane with logo + centered form
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompactSignUpLayout(
    state: SignUpState,
    onDispatch: (SignUpIntent) -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(ZyntaSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Logo / Brand header for compact mode
            SignUpLogoHeader()
            Spacer(Modifier.height(ZyntaSpacing.xl))
            Card(
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                SignUpFormContent(
                    state = state,
                    onDispatch = onDispatch,
                    onNavigateToLogin = onNavigateToLogin,
                    modifier = Modifier.padding(ZyntaSpacing.lg),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared sign-up form — stateless; renders purely from SignUpState
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SignUpFormContent(
    state: SignUpState,
    onDispatch: (SignUpIntent) -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {

        Text(
            text = "Create Account",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Sign up for a new ZyntaPOS account",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(ZyntaSpacing.xl))

        // Name field
        ZyntaTextField(
            label = "Full Name",
            value = state.name,
            onValueChange = { onDispatch(SignUpIntent.NameChanged(it)) },
            leadingIcon = Icons.Default.Person,
            error = state.nameError,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(ZyntaSpacing.md))

        // Email field
        ZyntaTextField(
            label = "Email Address",
            value = state.email,
            onValueChange = { onDispatch(SignUpIntent.EmailChanged(it)) },
            leadingIcon = Icons.Default.Email,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            error = state.emailError,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(ZyntaSpacing.md))

        // Password field
        ZyntaTextField(
            label = "Password",
            value = state.password,
            onValueChange = { onDispatch(SignUpIntent.PasswordChanged(it)) },
            leadingIcon = Icons.Default.Lock,
            trailingIcon = {
                IconButton(onClick = { onDispatch(SignUpIntent.TogglePasswordVisibility) }) {
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

        Spacer(Modifier.height(ZyntaSpacing.md))

        // Confirm password field
        ZyntaTextField(
            label = "Confirm Password",
            value = state.confirmPassword,
            onValueChange = { onDispatch(SignUpIntent.ConfirmPasswordChanged(it)) },
            leadingIcon = Icons.Default.Lock,
            visualTransformation = if (state.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            error = state.confirmPasswordError,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )

        // Error banner
        if (state.error != null) {
            Spacer(Modifier.height(ZyntaSpacing.sm))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = state.error ?: "",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(ZyntaSpacing.md),
                )
            }
        }

        Spacer(Modifier.height(ZyntaSpacing.lg))

        ZyntaButton(
            text = "Create Account",
            onClick = { onDispatch(SignUpIntent.SignUpClicked) },
            isLoading = state.isLoading,
            size = ZyntaButtonSize.Large,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(ZyntaSpacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Already have an account?",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onNavigateToLogin) {
                Text("Sign In")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Illustration panel (EXPANDED layout left side) — gradient branded panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SignUpIllustrationPanel(modifier: Modifier = Modifier) {
    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary,
        ),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = gradientBrush),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PersonAdd,
                contentDescription = "Create Account",
                tint = Color.White,
                modifier = Modifier.size(120.dp),
            )

            Spacer(Modifier.height(ZyntaSpacing.xl))

            Text(
                text = "ZyntaPOS",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )

            Spacer(Modifier.height(ZyntaSpacing.sm))

            Text(
                text = "Create Your Account",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.8f),
            )

            Spacer(Modifier.height(ZyntaSpacing.md))

            Text(
                text = "Fast. Reliable. Intelligent.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Zynta logo / brand header (compact layout top)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SignUpLogoHeader(modifier: Modifier = Modifier) {
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
