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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButtonVariant
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaCurrencyPicker
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaLoadingOverlay
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTextField
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTimezonePicker
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.feature.onboarding.OnboardingViewModel
import com.zyntasolutions.zyntapos.feature.onboarding.mvi.AdditionalStoreEntry
import com.zyntasolutions.zyntapos.feature.onboarding.mvi.OnboardingEffect
import com.zyntasolutions.zyntapos.feature.onboarding.mvi.OnboardingIntent
import com.zyntasolutions.zyntapos.feature.onboarding.mvi.OnboardingState
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

/**
 * First-run onboarding wizard screen.
 *
 * Guides the operator through four steps:
 * 1. **Business Info** — sets the store/business name.
 * 2. **Admin Account** — creates the first ADMIN user.
 * 3. **Store Settings** — selects currency and timezone.
 * 4. **Tax Setup** — optionally creates a default tax group (can be skipped).
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

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is OnboardingEffect.NavigateToLogin -> onOnboardingComplete()
                is OnboardingEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    val s = LocalStrings.current
    ZyntaPageScaffold(
        title = s[StringResource.ONBOARDING_WELCOME_TITLE],
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
                    OnboardingState.Step.TAX_SETUP -> TaxSetupStep(
                        state = state,
                        onIntent = viewModel::dispatch,
                        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                    )
                    OnboardingState.Step.RECEIPT_FORMAT -> ReceiptFormatStep(
                        state = state,
                        onIntent = viewModel::dispatch,
                        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                    )
                    OnboardingState.Step.MULTI_STORE_SETUP -> MultiStoreSetupStep(
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
    val s = LocalStrings.current
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
                    OnboardingState.Step.BUSINESS_INFO -> s[StringResource.ONBOARDING_STEP_BUSINESS]
                    OnboardingState.Step.ADMIN_ACCOUNT -> s[StringResource.ONBOARDING_STEP_ADMIN]
                    OnboardingState.Step.STORE_SETTINGS -> s[StringResource.ONBOARDING_STEP_STORE_SETTINGS]
                    OnboardingState.Step.TAX_SETUP -> s[StringResource.ONBOARDING_STEP_TAX_SETUP]
                    OnboardingState.Step.RECEIPT_FORMAT -> s[StringResource.ONBOARDING_STEP_RECEIPT_FORMAT]
                    OnboardingState.Step.MULTI_STORE_SETUP -> s[StringResource.ONBOARDING_STEP_MULTI_STORE]
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
    val s = LocalStrings.current
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
            s[StringResource.ONBOARDING_SETUP_BUSINESS],
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            s[StringResource.ONBOARDING_BUSINESS_NAME_HINT],
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(ZyntaSpacing.sm))

        ZyntaTextField(
            value = state.businessName,
            onValueChange = { onIntent(OnboardingIntent.BusinessNameChanged(it)) },
            label = s[StringResource.ONBOARDING_BUSINESS_NAME],
            placeholder = s[StringResource.ONBOARDING_BUSINESS_NAME_PLACEHOLDER],
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
            text = s[StringResource.ONBOARDING_NEXT],
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
    val s = LocalStrings.current
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
            s[StringResource.ONBOARDING_CREATE_ADMIN],
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            s[StringResource.ONBOARDING_ADMIN_HINT],
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(ZyntaSpacing.sm))

        ZyntaTextField(
            value = state.adminName,
            onValueChange = { onIntent(OnboardingIntent.AdminNameChanged(it)) },
            label = s[StringResource.ONBOARDING_ADMIN_NAME],
            placeholder = s[StringResource.ONBOARDING_ADMIN_NAME_PLACEHOLDER],
            error = state.adminNameError,
            leadingIcon = Icons.Default.Person,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaTextField(
            value = state.adminEmail,
            onValueChange = { onIntent(OnboardingIntent.AdminEmailChanged(it)) },
            label = s[StringResource.ONBOARDING_ADMIN_EMAIL],
            placeholder = s[StringResource.ONBOARDING_ADMIN_EMAIL_PLACEHOLDER],
            error = state.adminEmailError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaTextField(
            value = state.adminPassword,
            onValueChange = { onIntent(OnboardingIntent.AdminPasswordChanged(it)) },
            label = s[StringResource.ONBOARDING_ADMIN_PASSWORD],
            placeholder = s[StringResource.ONBOARDING_PASSWORD_PLACEHOLDER],
            error = state.adminPasswordError,
            leadingIcon = Icons.Default.Lock,
            trailingIcon = {
                IconButton(onClick = { onIntent(OnboardingIntent.TogglePasswordVisibility) }) {
                    Icon(
                        if (state.isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (state.isPasswordVisible) s[StringResource.ONBOARDING_HIDE_PASSWORD] else s[StringResource.ONBOARDING_SHOW_PASSWORD],
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
            label = s[StringResource.ONBOARDING_CONFIRM_PASSWORD],
            placeholder = s[StringResource.ONBOARDING_CONFIRM_PASSWORD_PLACEHOLDER],
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
            text = s[StringResource.ONBOARDING_NEXT],
            onClick = { onIntent(OnboardingIntent.NextStep) },
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaButton(
            text = s[StringResource.COMMON_BACK],
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
    val s = LocalStrings.current
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
            s[StringResource.ONBOARDING_STORE_SETTINGS_TITLE],
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            s[StringResource.ONBOARDING_STORE_SETTINGS_HINT],
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(ZyntaSpacing.sm))

        ZyntaCurrencyPicker(
            selectedCode = state.currencyCode,
            onCurrencySelected = { onIntent(OnboardingIntent.CurrencyChanged(it.code)) },
            label = s[StringResource.ONBOARDING_STORE_CURRENCY],
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaTimezonePicker(
            selectedTimezoneId = state.timezoneId,
            onTimezoneSelected = { onIntent(OnboardingIntent.TimezoneChanged(it.id)) },
            label = s[StringResource.ONBOARDING_STORE_TIMEZONE],
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
            text = s[StringResource.ONBOARDING_NEXT],
            onClick = { onIntent(OnboardingIntent.NextStep) },
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaButton(
            text = s[StringResource.COMMON_BACK],
            onClick = { onIntent(OnboardingIntent.BackStep) },
            variant = ZyntaButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Step 4: Tax Setup (optional) ──────────────────────────────────────────

@Composable
private fun TaxSetupStep(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        Icon(
            Icons.Default.Percent, null,
            modifier = Modifier.size(56.dp).align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary,
        )

        Text(
            s[StringResource.ONBOARDING_TAX_SETUP_TITLE],
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            s[StringResource.ONBOARDING_TAX_SETUP_HINT],
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(ZyntaSpacing.sm))

        ZyntaTextField(
            value = state.taxGroupName,
            onValueChange = { onIntent(OnboardingIntent.TaxGroupNameChanged(it)) },
            label = s[StringResource.ONBOARDING_TAX_GROUP_NAME],
            placeholder = s[StringResource.ONBOARDING_TAX_GROUP_PLACEHOLDER],
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaTextField(
            value = state.taxRate,
            onValueChange = { onIntent(OnboardingIntent.TaxRateChanged(it)) },
            label = s[StringResource.ONBOARDING_TAX_RATE],
            placeholder = s[StringResource.ONBOARDING_TAX_RATE_PLACEHOLDER],
            error = state.taxRateError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    s[StringResource.ONBOARDING_TAX_INCLUSIVE],
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    s[StringResource.ONBOARDING_TAX_INCLUSIVE_HINT],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.taxIsInclusive,
                onCheckedChange = { onIntent(OnboardingIntent.TaxIsInclusiveChanged(it)) },
            )
        }

        if (state.error != null) {
            Text(
                state.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(ZyntaSpacing.sm))

        ZyntaButton(
            text = s[StringResource.ONBOARDING_NEXT],
            onClick = { onIntent(OnboardingIntent.NextStep) },
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaButton(
            text = s[StringResource.ONBOARDING_SKIP_TAX_SETUP],
            onClick = { onIntent(OnboardingIntent.SkipTaxSetup) },
            variant = ZyntaButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaButton(
            text = s[StringResource.COMMON_BACK],
            onClick = { onIntent(OnboardingIntent.BackStep) },
            variant = ZyntaButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Step 5: Receipt Format (optional) ─────────────────────────────────────

@Composable
private fun ReceiptFormatStep(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        Icon(
            Icons.Default.Print, null,
            modifier = Modifier.size(56.dp).align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary,
        )

        Text(
            s[StringResource.ONBOARDING_RECEIPT_FORMAT_TITLE],
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            s[StringResource.ONBOARDING_RECEIPT_FORMAT_HINT],
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(ZyntaSpacing.sm))

        ZyntaTextField(
            value = state.receiptHeader,
            onValueChange = { onIntent(OnboardingIntent.ReceiptHeaderChanged(it)) },
            label = s[StringResource.ONBOARDING_RECEIPT_HEADER],
            placeholder = s[StringResource.ONBOARDING_RECEIPT_HEADER_PLACEHOLDER],
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaTextField(
            value = state.receiptFooter,
            onValueChange = { onIntent(OnboardingIntent.ReceiptFooterChanged(it)) },
            label = s[StringResource.ONBOARDING_RECEIPT_FOOTER],
            placeholder = s[StringResource.ONBOARDING_RECEIPT_FOOTER_PLACEHOLDER],
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        // Paper width selector
        Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs)) {
            Text(
                s[StringResource.ONBOARDING_PAPER_WIDTH],
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                listOf(58 to s[StringResource.ONBOARDING_PAPER_58MM], 80 to s[StringResource.ONBOARDING_PAPER_80MM]).forEach { (widthMm, label) ->
                    FilterChip(
                        selected = state.receiptPaperWidthMm == widthMm,
                        onClick = { onIntent(OnboardingIntent.ReceiptPaperWidthChanged(widthMm)) },
                        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    s[StringResource.ONBOARDING_AUTO_PRINT],
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    s[StringResource.ONBOARDING_AUTO_PRINT_HINT],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.receiptAutoPrint,
                onCheckedChange = { onIntent(OnboardingIntent.ReceiptAutoPrintChanged(it)) },
            )
        }

        if (state.error != null) {
            Text(
                state.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(ZyntaSpacing.sm))

        ZyntaButton(
            text = s[StringResource.ONBOARDING_NEXT],
            onClick = { onIntent(OnboardingIntent.NextStep) },
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaButton(
            text = s[StringResource.ONBOARDING_SKIP_RECEIPT_SETUP],
            onClick = { onIntent(OnboardingIntent.SkipReceiptFormat) },
            variant = ZyntaButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaButton(
            text = s[StringResource.COMMON_BACK],
            onClick = { onIntent(OnboardingIntent.BackStep) },
            variant = ZyntaButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Step 6: Multi-Store Setup (optional) ─────────────────────────────────

@Composable
private fun MultiStoreSetupStep(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        Icon(
            Icons.Default.Store, null,
            modifier = Modifier.size(56.dp).align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary,
        )

        Text(
            s[StringResource.ONBOARDING_MULTI_STORE_TITLE],
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            s[StringResource.ONBOARDING_MULTI_STORE_HINT],
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(ZyntaSpacing.sm))

        // Add store input
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            ZyntaTextField(
                value = state.newStoreName,
                onValueChange = { onIntent(OnboardingIntent.NewStoreNameChanged(it)) },
                label = s[StringResource.ONBOARDING_STORE_NAME],
                placeholder = s[StringResource.ONBOARDING_STORE_NAME_PLACEHOLDER],
                error = state.newStoreNameError,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f),
            )
            ZyntaButton(
                text = s[StringResource.ONBOARDING_ADD],
                onClick = { onIntent(OnboardingIntent.AddAdditionalStore) },
                modifier = Modifier.padding(top = ZyntaSpacing.md),
            )
        }

        // Added stores list
        state.additionalStores.forEachIndexed { index, store ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    store.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onIntent(OnboardingIntent.RemoveAdditionalStore(index)) }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "${s[StringResource.ONBOARDING_REMOVE]} ${store.name}",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        if (state.error != null) {
            Text(
                state.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(ZyntaSpacing.sm))

        ZyntaButton(
            text = s[StringResource.ONBOARDING_COMPLETE_SETUP],
            onClick = { onIntent(OnboardingIntent.CompleteOnboarding) },
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaButton(
            text = s[StringResource.ONBOARDING_SKIP_MULTI_STORE],
            onClick = { onIntent(OnboardingIntent.SkipMultiStoreSetup) },
            variant = ZyntaButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaButton(
            text = s[StringResource.COMMON_BACK],
            onClick = { onIntent(OnboardingIntent.BackStep) },
            variant = ZyntaButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
