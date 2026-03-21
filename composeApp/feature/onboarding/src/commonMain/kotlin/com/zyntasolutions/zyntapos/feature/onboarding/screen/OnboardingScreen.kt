package com.zyntasolutions.zyntapos.feature.onboarding.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButtonVariant
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaCurrencyPicker
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaLoadingOverlay
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTextField
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTimezonePicker
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.feature.onboarding.OnboardingViewModel
import com.zyntasolutions.zyntapos.feature.onboarding.mvi.OnboardingEffect
import com.zyntasolutions.zyntapos.feature.onboarding.mvi.OnboardingIntent
import com.zyntasolutions.zyntapos.feature.onboarding.mvi.OnboardingState
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

/**
 * First-run onboarding wizard screen.
 *
 * Guides the operator through three steps:
 * 1. **Business Info** — sets the store/business name.
 * 2. **Admin Account** — creates the first ADMIN user.
 * 3. **Store Settings** — selects currency and timezone.
 *
 * Uses [ZyntaPageScaffold] for consistent top-bar design and [AnimatedContent]
 * for smooth step transitions. The wizard is shown only once on first launch;
 * completion is tracked via [OnboardingViewModel.ONBOARDING_COMPLETED_KEY].
 *
 * @param onOnboardingComplete Called after successful setup — navigates to login.
 */
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is OnboardingEffect.NavigateToLogin -> onOnboardingComplete()
                is OnboardingEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    ZyntaPageScaffold(
        title = "Welcome to ZyntaPOS",
        snackbarHostState = snackbarHostState,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = ZyntaSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(ZyntaSpacing.md))

            // Progress indicator
            StepProgress(
                currentStep = state.currentStep,
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            )

            Spacer(Modifier.height(ZyntaSpacing.lg))

            // Animated step content
            AnimatedContent(
                targetState = state.currentStep,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    slideInHorizontally { if (forward) it else -it } togetherWith
                        slideOutHorizontally { if (forward) -it else it }
                },
                label = "onboarding_step",
            ) { step ->
                when (step) {
                    OnboardingState.Step.BUSINESS_INFO -> BusinessInfoStep(
                        state = state,
                        onIntent = viewModel::dispatch,
                        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                    )
                    OnboardingState.Step.ADMIN_ACCOUNT -> AdminAccountStep(
                        state = state,
                        onIntent = viewModel::dispatch,
                        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                    )
                    OnboardingState.Step.STORE_SETTINGS -> StoreSettingsStep(
                        state = state,
                        onIntent = viewModel::dispatch,
                        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                    )
                }
            }
        }

        if (state.isLoading) {
            ZyntaLoadingOverlay(isLoading = true)
        }
    }
}

// ── Step progress indicator ───────────────────────────────────────────────

@Composable
private fun StepProgress(currentStep: OnboardingState.Step, modifier: Modifier = Modifier) {
    val totalSteps = OnboardingState.Step.entries.size
    val progress = (currentStep.ordinal + 1).toFloat() / totalSteps
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Step ${currentStep.ordinal + 1} of $totalSteps",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                when (currentStep) {
                    OnboardingState.Step.BUSINESS_INFO -> "Business Info"
                    OnboardingState.Step.ADMIN_ACCOUNT -> "Admin Account"
                    OnboardingState.Step.STORE_SETTINGS -> "Store Settings"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(ZyntaSpacing.xs))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ── Step 1: Business Info ─────────────────────────────────────────────────

@Composable
private fun BusinessInfoStep(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        Icon(
            Icons.Default.Business, null,
            modifier = Modifier.size(56.dp).align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary,
        )

        Text(
            "Set up your business",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            "Enter the name of your store or business. This appears on receipts and reports.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(ZyntaSpacing.sm))

        ZyntaTextField(
            value = state.businessName,
            onValueChange = { onIntent(OnboardingIntent.BusinessNameChanged(it)) },
            label = "Business Name",
            placeholder = "e.g. Zyntara Coffee Shop",
            error = state.businessNameError,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.error != null) {
            Text(
                state.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(ZyntaSpacing.sm))

        ZyntaButton(
            text = "Next",
            onClick = { onIntent(OnboardingIntent.NextStep) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Step 2: Admin Account ─────────────────────────────────────────────────

@Composable
private fun AdminAccountStep(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        Icon(
            Icons.Default.Person, null,
            modifier = Modifier.size(56.dp).align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary,
        )

        Text(
            "Create admin account",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            "This is the owner/administrator account. You can add more staff accounts later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(ZyntaSpacing.sm))

        ZyntaTextField(
            value = state.adminName,
            onValueChange = { onIntent(OnboardingIntent.AdminNameChanged(it)) },
            label = "Full Name",
            placeholder = "e.g. Jane Smith",
            error = state.adminNameError,
            leadingIcon = Icons.Default.Person,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaTextField(
            value = state.adminEmail,
            onValueChange = { onIntent(OnboardingIntent.AdminEmailChanged(it)) },
            label = "Email Address",
            placeholder = "admin@yourbusiness.com",
            error = state.adminEmailError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaTextField(
            value = state.adminPassword,
            onValueChange = { onIntent(OnboardingIntent.AdminPasswordChanged(it)) },
            label = "Password",
            placeholder = "Minimum 8 characters",
            error = state.adminPasswordError,
            leadingIcon = Icons.Default.Lock,
            trailingIcon = {
                IconButton(onClick = { onIntent(OnboardingIntent.TogglePasswordVisibility) }) {
                    Icon(
                        if (state.isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (state.isPasswordVisible) "Hide password" else "Show password",
                    )
                }
            },
            visualTransformation = if (state.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaTextField(
            value = state.adminConfirmPassword,
            onValueChange = { onIntent(OnboardingIntent.AdminConfirmPasswordChanged(it)) },
            label = "Confirm Password",
            placeholder = "Re-enter your password",
            error = state.adminConfirmPasswordError,
            leadingIcon = Icons.Default.Lock,
            visualTransformation = if (state.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.error != null) {
            Text(
                state.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(ZyntaSpacing.sm))

        ZyntaButton(
            text = "Next",
            onClick = { onIntent(OnboardingIntent.NextStep) },
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaButton(
            text = "Back",
            onClick = { onIntent(OnboardingIntent.BackStep) },
            variant = ZyntaButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Step 3: Store Settings (Currency & Timezone) ────────────────────────

@Composable
private fun StoreSettingsStep(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        Icon(
            Icons.Default.Settings, null,
            modifier = Modifier.size(56.dp).align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary,
        )

        Text(
            "Store settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            "Select your store's currency and timezone. You can change these later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(ZyntaSpacing.sm))

        ZyntaCurrencyPicker(
            selectedCode = state.currencyCode,
            onCurrencySelected = { onIntent(OnboardingIntent.CurrencyChanged(it.code)) },
            label = "Store Currency",
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaTimezonePicker(
            selectedTimezoneId = state.timezoneId,
            onTimezoneSelected = { onIntent(OnboardingIntent.TimezoneChanged(it.id)) },
            label = "Store Timezone",
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.error != null) {
            Text(
                state.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(ZyntaSpacing.sm))

        ZyntaButton(
            text = "Complete Setup",
            onClick = { onIntent(OnboardingIntent.CompleteOnboarding) },
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaButton(
            text = "Back",
            onClick = { onIntent(OnboardingIntent.BackStep) },
            variant = ZyntaButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
