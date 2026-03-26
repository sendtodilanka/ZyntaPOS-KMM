package com.zyntasolutions.zyntapos.feature.onboarding

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import com.zyntasolutions.zyntapos.domain.repository.TaxGroupRepository
import com.zyntasolutions.zyntapos.domain.repository.UserRepository
import com.zyntasolutions.zyntapos.domain.usecase.accounting.SeedDefaultChartOfAccountsUseCase
import com.zyntasolutions.zyntapos.feature.onboarding.mvi.OnboardingEffect
import com.zyntasolutions.zyntapos.feature.onboarding.mvi.OnboardingIntent
import com.zyntasolutions.zyntapos.feature.onboarding.mvi.OnboardingState
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlin.time.Clock

/**
 * MVI ViewModel for the first-run onboarding wizard.
 *
 * ### Responsibilities
 * - Validates each wizard step inline before advancing.
 * - Persists the business name to [SettingsRepository] under key
 *   `"general.business_name"`.
 * - Creates the initial ADMIN user account via [UserRepository.create].
 * - Marks onboarding complete via [SettingsRepository] key
 *   `"onboarding.completed"` set to `"true"`.
 * - Emits [OnboardingEffect.NavigateToLogin] on success so the navigation
 *   layer redirects to the login screen.
 *
 * ### Security
 * Passwords are passed directly to [UserRepository.create] which hashes them
 * before storage — the plain-text password is never persisted by this ViewModel.
 *
 * @param userRepository          Creates the admin user account.
 * @param settingsRepository      Persists business name and completion flag.
 * @param seedChartOfAccountsUseCase Seeds the default Chart of Accounts for the new store (best-effort).
 */
class OnboardingViewModel(
    private val userRepository: UserRepository,
    private val settingsRepository: SettingsRepository,
    private val taxGroupRepository: TaxGroupRepository,
    private val seedChartOfAccountsUseCase: SeedDefaultChartOfAccountsUseCase,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<OnboardingState, OnboardingIntent, OnboardingEffect>(OnboardingState()) {

    init {
        analytics.logScreenView("Onboarding", "OnboardingViewModel")
    }

    override suspend fun handleIntent(intent: OnboardingIntent) {
        when (intent) {
            is OnboardingIntent.BusinessNameChanged -> onBusinessNameChanged(intent.name)
            is OnboardingIntent.NextStep -> onNextStep()
            is OnboardingIntent.AdminNameChanged -> onAdminNameChanged(intent.name)
            is OnboardingIntent.AdminEmailChanged -> onAdminEmailChanged(intent.email)
            is OnboardingIntent.AdminPasswordChanged -> onAdminPasswordChanged(intent.password)
            is OnboardingIntent.AdminConfirmPasswordChanged -> onAdminConfirmPasswordChanged(intent.confirmPassword)
            is OnboardingIntent.TogglePasswordVisibility -> updateState { copy(isPasswordVisible = !isPasswordVisible) }
            is OnboardingIntent.CurrencyChanged -> updateState { copy(currencyCode = intent.currencyCode) }
            is OnboardingIntent.TimezoneChanged -> updateState { copy(timezoneId = intent.timezoneId) }
            is OnboardingIntent.TaxGroupNameChanged -> updateState { copy(taxGroupName = intent.name) }
            is OnboardingIntent.TaxRateChanged -> {
                val rateError = if (intent.rate.isNotBlank()) {
                    val v = intent.rate.toDoubleOrNull()
                    when {
                        v == null -> "Enter a valid number"
                        v < 0.0 || v > 100.0 -> "Rate must be between 0 and 100"
                        else -> null
                    }
                } else null
                updateState { copy(taxRate = intent.rate, taxRateError = rateError) }
            }
            is OnboardingIntent.TaxIsInclusiveChanged -> updateState { copy(taxIsInclusive = intent.inclusive) }
            is OnboardingIntent.SkipTaxSetup -> updateState { copy(currentStep = OnboardingState.Step.RECEIPT_FORMAT) }
            is OnboardingIntent.ReceiptHeaderChanged -> updateState { copy(receiptHeader = intent.header) }
            is OnboardingIntent.ReceiptFooterChanged -> updateState { copy(receiptFooter = intent.footer) }
            is OnboardingIntent.ReceiptPaperWidthChanged -> updateState { copy(receiptPaperWidthMm = intent.widthMm) }
            is OnboardingIntent.ReceiptAutoPrintChanged -> updateState { copy(receiptAutoPrint = intent.enabled) }
            is OnboardingIntent.SkipReceiptFormat -> updateState { copy(currentStep = OnboardingState.Step.MULTI_STORE_SETUP) }
            // ── Step 6: Multi-store setup (G2) ─────────────────────────────
            is OnboardingIntent.NewStoreNameChanged -> updateState {
                copy(newStoreName = intent.name, newStoreNameError = null)
            }
            is OnboardingIntent.AddAdditionalStore -> onAddAdditionalStore()
            is OnboardingIntent.RemoveAdditionalStore -> updateState {
                copy(additionalStores = additionalStores.filterIndexed { i, _ -> i != intent.index })
            }
            is OnboardingIntent.SkipMultiStoreSetup -> onCompleteOnboarding(saveReceipt = true)
            is OnboardingIntent.CompleteOnboarding -> onCompleteOnboarding(saveReceipt = true)
            is OnboardingIntent.BackStep -> onBackStep()
            is OnboardingIntent.DismissError -> updateState { copy(error = null) }
        }
    }

    // ── Step 1: Business info ──────────────────────────────────────────────

    private fun onBusinessNameChanged(name: String) {
        updateState {
            copy(
                businessName = name,
                businessNameError = if (name.isNotBlank() && name.trim().length < 2)
                    "Business name must be at least 2 characters" else null,
                error = null,
            )
        }
    }

    private fun onNextStep() {
        val s = currentState
        when (s.currentStep) {
            OnboardingState.Step.BUSINESS_INFO -> {
                val nameError = validateBusinessName(s.businessName)
                if (nameError != null) {
                    updateState { copy(businessNameError = nameError) }
                    return
                }
                updateState { copy(currentStep = OnboardingState.Step.ADMIN_ACCOUNT, businessNameError = null) }
            }
            OnboardingState.Step.ADMIN_ACCOUNT -> {
                // Validate admin fields before advancing to Step 3
                val nameError = if (s.adminName.isBlank()) "Name is required"
                    else if (s.adminName.trim().length < 2) "Name is too short" else null
                val emailError = when {
                    s.adminEmail.isBlank() -> "Email is required"
                    !s.adminEmail.isValidEmail() -> "Enter a valid email address"
                    else -> null
                }
                val passwordError = when {
                    s.adminPassword.isBlank() -> "Password is required"
                    s.adminPassword.length < 8 -> "Password must be at least 8 characters"
                    else -> null
                }
                val confirmError = when {
                    s.adminConfirmPassword.isBlank() -> "Please confirm your password"
                    s.adminConfirmPassword != s.adminPassword -> "Passwords do not match"
                    else -> null
                }
                if (nameError != null || emailError != null || passwordError != null || confirmError != null) {
                    updateState {
                        copy(
                            adminNameError = nameError,
                            adminEmailError = emailError,
                            adminPasswordError = passwordError,
                            adminConfirmPasswordError = confirmError,
                        )
                    }
                    return
                }
                updateState { copy(currentStep = OnboardingState.Step.STORE_SETTINGS) }
            }
            OnboardingState.Step.STORE_SETTINGS -> {
                // Advance to optional tax setup step
                updateState { copy(currentStep = OnboardingState.Step.TAX_SETUP) }
            }
            OnboardingState.Step.TAX_SETUP -> {
                // Advance to optional receipt format step
                updateState { copy(currentStep = OnboardingState.Step.RECEIPT_FORMAT) }
            }
            OnboardingState.Step.RECEIPT_FORMAT -> {
                // Advance to optional multi-store setup step (G2)
                updateState { copy(currentStep = OnboardingState.Step.MULTI_STORE_SETUP) }
            }
            OnboardingState.Step.MULTI_STORE_SETUP -> {
                // Last step — no-op; user uses CompleteOnboarding or SkipMultiStoreSetup
            }
        }
    }

    private fun onBackStep() {
        when (currentState.currentStep) {
            OnboardingState.Step.ADMIN_ACCOUNT -> updateState { copy(currentStep = OnboardingState.Step.BUSINESS_INFO) }
            OnboardingState.Step.STORE_SETTINGS -> updateState { copy(currentStep = OnboardingState.Step.ADMIN_ACCOUNT) }
            OnboardingState.Step.TAX_SETUP -> updateState { copy(currentStep = OnboardingState.Step.STORE_SETTINGS) }
            OnboardingState.Step.RECEIPT_FORMAT -> updateState { copy(currentStep = OnboardingState.Step.TAX_SETUP) }
            OnboardingState.Step.MULTI_STORE_SETUP -> updateState { copy(currentStep = OnboardingState.Step.RECEIPT_FORMAT) }
            OnboardingState.Step.BUSINESS_INFO -> Unit // no-op
        }
    }

    // ── Step 6: Multi-store setup (G2) ─────────────────────────────────────

    private fun onAddAdditionalStore() {
        val name = currentState.newStoreName.trim()
        if (name.isBlank()) {
            updateState { copy(newStoreNameError = "Store name is required") }
            return
        }
        if (name.length < 2) {
            updateState { copy(newStoreNameError = "Store name must be at least 2 characters") }
            return
        }
        if (currentState.additionalStores.any { it.name.equals(name, ignoreCase = true) }) {
            updateState { copy(newStoreNameError = "Store name already added") }
            return
        }
        val entry = AdditionalStoreEntry(
            name = name,
            currencyCode = currentState.currencyCode,
            timezoneId = currentState.timezoneId,
        )
        updateState {
            copy(additionalStores = additionalStores + entry, newStoreName = "", newStoreNameError = null)
        }
    }

    // ── Step 2: Admin account ──────────────────────────────────────────────

    private fun onAdminNameChanged(name: String) {
        updateState {
            copy(
                adminName = name,
                adminNameError = if (name.isNotBlank() && name.trim().length < 2) "Name is too short" else null,
                error = null,
            )
        }
    }

    private fun onAdminEmailChanged(email: String) {
        updateState {
            copy(
                adminEmail = email,
                adminEmailError = if (email.isNotBlank() && !email.isValidEmail()) "Enter a valid email address" else null,
                error = null,
            )
        }
    }

    private fun onAdminPasswordChanged(password: String) {
        updateState {
            copy(
                adminPassword = password,
                adminPasswordError = if (password.isNotBlank() && password.length < 8)
                    "Password must be at least 8 characters" else null,
                error = null,
            )
        }
    }

    private fun onAdminConfirmPasswordChanged(confirmPassword: String) {
        updateState {
            copy(
                adminConfirmPassword = confirmPassword,
                adminConfirmPasswordError = if (confirmPassword.isNotBlank() && confirmPassword != adminPassword)
                    "Passwords do not match" else null,
                error = null,
            )
        }
    }

    // ── Completion ────────────────────────────────────────────────────────

    private suspend fun onCompleteOnboarding(saveReceipt: Boolean = true) {
        val s = currentState

        updateState { copy(isLoading = true, error = null) }

        // ── Persist business name ─────────────────────────────────────────
        val settingsResult = settingsRepository.set("general.business_name", s.businessName.trim())
        if (settingsResult is Result.Error) {
            updateState {
                copy(isLoading = false, error = settingsResult.exception.message ?: "Failed to save business name")
            }
            return
        }

        // ── Persist currency & timezone (Step 3) ─────────────────────────
        settingsRepository.set("general.currency", s.currencyCode)
        settingsRepository.set("general.timezone", s.timezoneId)

        // ── Create admin user ─────────────────────────────────────────────
        val now = Clock.System.now()
        val adminUser = User(
            id = generateId(),
            name = s.adminName.trim(),
            email = s.adminEmail.trim().lowercase(),
            role = Role.ADMIN,
            storeId = DEFAULT_STORE_ID,
            isActive = true,
            pinHash = null,
            isSystemAdmin = true,
            createdAt = now,
            updatedAt = now,
        )
        when (val createResult = userRepository.create(adminUser, s.adminPassword)) {
            is Result.Error -> {
                updateState {
                    copy(isLoading = false, error = createResult.exception.message ?: "Failed to create admin account")
                }
                return
            }
            else -> Unit
        }

        // ── Persist tax group if user configured one (Step 4) ────────────
        if (s.taxGroupName.isNotBlank()) {
            val rateVal = s.taxRate.toDoubleOrNull()
            if (rateVal == null || rateVal < 0.0 || rateVal > 100.0) {
                updateState { copy(isLoading = false, taxRateError = "Enter a valid rate between 0 and 100") }
                return
            }
            val taxGroup = TaxGroup(
                id = IdGenerator.newId(),
                name = s.taxGroupName.trim(),
                rate = rateVal,
                isInclusive = s.taxIsInclusive,
                isActive = true,
            )
            taxGroupRepository.insert(taxGroup)
            // Ignore error — user can create tax groups from Settings later
        }

        // ── Persist receipt format preferences (Step 5) ──────────────────
        if (saveReceipt) {
            settingsRepository.set("pos.receipt_header", s.receiptHeader.trim())
            settingsRepository.set("pos.receipt_footer", s.receiptFooter.trim())
            settingsRepository.set("printer.paper_width_mm", s.receiptPaperWidthMm.toString())
            settingsRepository.set("pos.auto_print_receipt", if (s.receiptAutoPrint) "true" else "false")
            // Ignore errors — user can configure receipt format from Settings later
        }

        // ── Seed Chart of Accounts (best-effort, non-blocking) ────────────
        seedChartOfAccountsUseCase.execute(_storeId = DEFAULT_STORE_ID, now = now.toEpochMilliseconds())
        // Ignore errors — COA can be manually seeded from Settings later

        // ── Mark onboarding complete ──────────────────────────────────────
        settingsRepository.set(ONBOARDING_COMPLETED_KEY, "true")

        updateState { copy(isLoading = false) }
        sendEffect(OnboardingEffect.NavigateToLogin)
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun validateBusinessName(name: String): String? = when {
        name.isBlank() -> "Business name is required"
        name.trim().length < 2 -> "Business name must be at least 2 characters"
        name.trim().length > 100 -> "Business name must be 100 characters or fewer"
        else -> null
    }

    private fun String.isValidEmail(): Boolean =
        contains("@") && contains(".") && length >= 5

    /** Simple ID generator — replaced by UUID in production data layer. */
    private fun generateId(): String =
        "user-${Clock.System.now().toEpochMilliseconds()}"

    companion object {
        /** SettingsRepository key indicating onboarding has been completed. */
        const val ONBOARDING_COMPLETED_KEY = "onboarding.completed"

        /** Default store ID used when no multi-store setup has occurred yet. */
        const val DEFAULT_STORE_ID = "store-default"
    }
}
