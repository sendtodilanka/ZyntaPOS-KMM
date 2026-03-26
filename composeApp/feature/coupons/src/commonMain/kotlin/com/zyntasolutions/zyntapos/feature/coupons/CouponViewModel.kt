package com.zyntasolutions.zyntapos.feature.coupons

import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.Coupon
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.repository.CategoryRepository
import com.zyntasolutions.zyntapos.domain.repository.CouponRepository
import com.zyntasolutions.zyntapos.domain.usecase.coupons.SaveCouponUseCase
import kotlinx.coroutines.flow.first
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Clock

/**
 * ViewModel for the Coupons feature — Sprints 12–13.
 *
 * Manages:
 * - Coupon list with active/all filter
 * - Coupon create/edit form lifecycle
 * - Quick active toggle from list row
 * - Coupon usage history display
 *
 * @param couponRepository CRUD, toggle, and usage queries for [Coupon].
 * @param saveCouponUseCase Validates and persists coupons.
 */
class CouponViewModel(
    private val couponRepository: CouponRepository,
    private val saveCouponUseCase: SaveCouponUseCase,
    private val categoryRepository: CategoryRepository,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<CouponState, CouponIntent, CouponEffect>(CouponState()) {

    init {
        analytics.logScreenView("Coupons", "CouponViewModel")
        observeCoupons()
        observeCategories()
    }

    private fun observeCoupons() {
        couponRepository.getAll()
            .onEach { coupons ->
                val filtered = if (currentState.showActiveOnly) {
                    val now = Clock.System.now().toEpochMilliseconds()
                    coupons.filter { it.isActive && it.validFrom <= now && it.validTo >= now }
                } else {
                    coupons
                }
                updateState { copy(coupons = filtered, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeCategories() {
        categoryRepository.getAll()
            .onEach { categories ->
                updateState { copy(availableCategories = categories) }
            }
            .launchIn(viewModelScope)
    }

    override suspend fun handleIntent(intent: CouponIntent) {
        when (intent) {
            is CouponIntent.LoadCoupons -> updateState { copy(isLoading = true) }

            is CouponIntent.ToggleActiveFilter -> {
                updateState { copy(showActiveOnly = intent.showActiveOnly) }
                observeCoupons()
            }

            is CouponIntent.SelectCoupon -> onSelectCoupon(intent.couponId)
            is CouponIntent.UpdateFormField -> onUpdateFormField(intent.field, intent.value)
            is CouponIntent.UpdateIsActive -> updateState {
                copy(formState = formState.copy(isActive = intent.isActive))
            }
            is CouponIntent.UpdateScope -> updateState {
                copy(formState = formState.copy(scope = intent.scope, scopeIds = emptyList()))
            }
            is CouponIntent.ToggleScopeId -> updateState {
                val updated = if (intent.id in formState.scopeIds) {
                    formState.scopeIds - intent.id
                } else {
                    formState.scopeIds + intent.id
                }
                copy(formState = formState.copy(scopeIds = updated))
            }
            is CouponIntent.GenerateCode -> updateState {
                copy(formState = formState.copy(
                    code = generateCouponCode(),
                    validationErrors = formState.validationErrors - "code",
                ))
            }
            is CouponIntent.SaveCoupon -> onSaveCoupon()
            is CouponIntent.DeleteCoupon -> onDeleteCoupon(intent.couponId)
            is CouponIntent.ToggleCouponActive -> onToggleCouponActive(intent.couponId, intent.isActive)
            is CouponIntent.DismissMessage -> updateState { copy(error = null, successMessage = null) }
            is CouponIntent.LoadAnalytics -> loadCouponAnalytics()
        }
    }

    // ── Coupon CRUD ────────────────────────────────────────────────────────

    private suspend fun onSelectCoupon(couponId: String?) {
        if (couponId == null) {
            updateState {
                copy(
                    selectedCoupon = null,
                    formState = CouponFormState(isEditing = false),
                    usageHistory = emptyList(),
                )
            }
            sendEffect(CouponEffect.NavigateToDetail(null))
            return
        }
        updateState { copy(isLoading = true) }
        when (val result = couponRepository.getById(couponId)) {
            is Result.Success -> {
                val c = result.data
                val history = couponRepository.getUsageByCoupon(couponId).first()
                updateState {
                    copy(
                        selectedCoupon = c,
                        isLoading = false,
                        usageHistory = history,
                        formState = CouponFormState(
                            id = c.id,
                            code = c.code,
                            name = c.name,
                            discountType = c.discountType.name,
                            discountValue = c.discountValue.toString(),
                            minimumPurchase = c.minimumPurchase.toString(),
                            maximumDiscount = c.maximumDiscount?.toString() ?: "",
                            usageLimit = c.usageLimit?.toString() ?: "",
                            perCustomerLimit = c.perCustomerLimit?.toString() ?: "",
                            validFrom = c.validFrom.toString(),
                            validTo = c.validTo.toString(),
                            isActive = c.isActive,
                            storeId = c.storeId,
                            scope = c.scope.name,
                            scopeIds = c.scopeIds,
                            isEditing = true,
                        ),
                    )
                }
                sendEffect(CouponEffect.NavigateToDetail(couponId))
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(CouponEffect.ShowError(result.exception.message ?: "Failed to load coupon"))
            }
            is Result.Loading -> {}
        }
    }

    private fun onUpdateFormField(field: String, value: String) {
        updateState {
            copy(
                formState = when (field) {
                    "code" -> formState.copy(code = value, validationErrors = formState.validationErrors - "code")
                    "name" -> formState.copy(name = value, validationErrors = formState.validationErrors - "name")
                    "discountType" -> formState.copy(discountType = value)
                    "discountValue" -> formState.copy(discountValue = value, validationErrors = formState.validationErrors - "discountValue")
                    "minimumPurchase" -> formState.copy(minimumPurchase = value)
                    "maximumDiscount" -> formState.copy(maximumDiscount = value)
                    "usageLimit" -> formState.copy(usageLimit = value)
                    "perCustomerLimit" -> formState.copy(perCustomerLimit = value)
                    "validFrom" -> formState.copy(validFrom = value, validationErrors = formState.validationErrors - "validFrom")
                    "validTo" -> formState.copy(validTo = value, validationErrors = formState.validationErrors - "validTo")
                    "storeId" -> formState.copy(storeId = value.ifBlank { null })
                    else -> formState
                },
            )
        }
    }

    private suspend fun onSaveCoupon() {
        val form = currentState.formState
        val errors = validateCouponForm(form)
        if (errors.isNotEmpty()) {
            updateState { copy(formState = formState.copy(validationErrors = errors)) }
            return
        }

        updateState { copy(isLoading = true) }
        val coupon = Coupon(
            id = form.id ?: IdGenerator.newId(),
            code = form.code.trim().uppercase(),
            name = form.name.trim(),
            discountType = runCatching { DiscountType.valueOf(form.discountType) }.getOrDefault(DiscountType.PERCENT),
            discountValue = form.discountValue.toDoubleOrNull() ?: 0.0,
            minimumPurchase = form.minimumPurchase.toDoubleOrNull() ?: 0.0,
            maximumDiscount = form.maximumDiscount.toDoubleOrNull(),
            usageLimit = form.usageLimit.toIntOrNull(),
            perCustomerLimit = form.perCustomerLimit.toIntOrNull(),
            scope = runCatching { Coupon.CouponScope.valueOf(form.scope) }.getOrDefault(Coupon.CouponScope.CART),
            scopeIds = form.scopeIds,
            validFrom = form.validFrom.toLongOrNull() ?: 0L,
            validTo = form.validTo.toLongOrNull() ?: 0L,
            isActive = form.isActive,
            storeId = form.storeId,
        )

        when (val result = saveCouponUseCase(coupon, isNew = !form.isEditing)) {
            is Result.Success -> {
                val msg = if (form.isEditing) "Coupon updated" else "Coupon created"
                updateState {
                    copy(
                        isLoading = false,
                        successMessage = msg,
                        formState = CouponFormState(),
                        selectedCoupon = null,
                    )
                }
                sendEffect(CouponEffect.ShowSuccess(msg))
                sendEffect(CouponEffect.NavigateToList)
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(CouponEffect.ShowError(result.exception.message ?: "Save failed"))
            }
            is Result.Loading -> {}
        }
    }

    private suspend fun onDeleteCoupon(couponId: String) {
        updateState { copy(isLoading = true) }
        when (val result = couponRepository.delete(couponId)) {
            is Result.Success -> {
                updateState { copy(isLoading = false, selectedCoupon = null) }
                sendEffect(CouponEffect.ShowSuccess("Coupon deleted"))
                sendEffect(CouponEffect.NavigateToList)
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(CouponEffect.ShowError(result.exception.message ?: "Delete failed"))
            }
            is Result.Loading -> {}
        }
    }

    private suspend fun onToggleCouponActive(couponId: String, isActive: Boolean) {
        when (val result = couponRepository.toggleActive(couponId, isActive)) {
            is Result.Success -> {
                val msg = if (isActive) "Coupon enabled" else "Coupon disabled"
                sendEffect(CouponEffect.ShowSuccess(msg))
            }
            is Result.Error -> sendEffect(CouponEffect.ShowError(result.exception.message ?: "Toggle failed"))
            is Result.Loading -> {}
        }
    }

    // ── Analytics (G12) ────────────────────────────────────────────────────

    /**
     * Loads coupon redemption analytics by aggregating usage history across all coupons.
     * Computes total redemptions, total discount given, and top-5 most redeemed coupons.
     */
    private suspend fun loadCouponAnalytics() {
        updateState { copy(isAnalyticsLoading = true) }
        val allCoupons = currentState.coupons.ifEmpty {
            couponRepository.getAll().first()
        }
        var totalRedemptions = 0
        var totalDiscount = 0.0
        val stats = mutableListOf<CouponRedemptionStat>()
        for (coupon in allCoupons) {
            val usage = couponRepository.getUsageByCoupon(coupon.id).first()
            if (usage.isNotEmpty()) {
                val couponDiscount = usage.sumOf { it.discountAmount }
                totalRedemptions += usage.size
                totalDiscount += couponDiscount
                stats.add(
                    CouponRedemptionStat(
                        couponId = coupon.id,
                        couponCode = coupon.code,
                        redemptionCount = usage.size,
                        totalDiscount = couponDiscount,
                    )
                )
            }
        }
        val topRedeemed = stats.sortedByDescending { it.redemptionCount }.take(5)
        updateState {
            copy(
                isAnalyticsLoading = false,
                totalRedemptions = totalRedemptions,
                totalDiscountGiven = totalDiscount,
                topRedeemedCoupons = topRedeemed,
            )
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun validateCouponForm(form: CouponFormState): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (form.code.isBlank()) errors["code"] = "Coupon code is required"
        if (form.name.isBlank()) errors["name"] = "Coupon name is required"
        val discountType = runCatching { DiscountType.valueOf(form.discountType) }.getOrNull()
        if (discountType != DiscountType.BOGO) {
            val value = form.discountValue.toDoubleOrNull()
            if (value == null || value <= 0) errors["discountValue"] = "Discount value must be positive"
        }
        val from = form.validFrom.toLongOrNull()
        if (from == null) errors["validFrom"] = "Valid-from date is required"
        val to = form.validTo.toLongOrNull()
        if (to == null) errors["validTo"] = "Valid-to date is required"
        if (from != null && to != null && from >= to) {
            errors["validTo"] = "Valid-to must be after valid-from"
        }
        val scope = runCatching { Coupon.CouponScope.valueOf(form.scope) }.getOrDefault(Coupon.CouponScope.CART)
        if (scope != Coupon.CouponScope.CART && form.scopeIds.isEmpty()) {
            errors["scopeIds"] = "Select at least one ${scope.name.lowercase()} for this scope"
        }
        return errors
    }

    private fun generateCouponCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString {
            repeat(8) { append(chars.random()) }
        }
    }
}
