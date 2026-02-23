package com.zyntasolutions.zyntapos.feature.auth.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
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
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButtonSize
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaSnackbarHost
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTextField
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.feature.auth.SignUpEffect
import com.zyntasolutions.zyntapos.feature.auth.SignUpIntent
import com.zyntasolutions.zyntapos.feature.auth.SignUpViewModel
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SignUpScreen(
    viewModel: SignUpViewModel = koinViewModel(),
    onNavigateToLogin: () -> Unit = {},
    onSignUpSuccess: () -> Unit = {},
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(ZyntaSpacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
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
                    onValueChange = { viewModel.dispatch(SignUpIntent.NameChanged(it)) },
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
                    onValueChange = { viewModel.dispatch(SignUpIntent.EmailChanged(it)) },
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
                    onValueChange = { viewModel.dispatch(SignUpIntent.PasswordChanged(it)) },
                    leadingIcon = Icons.Default.Lock,
                    trailingIcon = {
                        IconButton(onClick = { viewModel.dispatch(SignUpIntent.TogglePasswordVisibility) }) {
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
                    onValueChange = { viewModel.dispatch(SignUpIntent.ConfirmPasswordChanged(it)) },
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
                            text = state.error!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(ZyntaSpacing.md),
                        )
                    }
                }

                Spacer(Modifier.height(ZyntaSpacing.lg))

                ZyntaButton(
                    text = "Create Account",
                    onClick = { viewModel.dispatch(SignUpIntent.SignUpClicked) },
                    isLoading = state.isLoading,
                    size = ZyntaButtonSize.Large,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(ZyntaSpacing.md))

                Row(
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
    }
}
