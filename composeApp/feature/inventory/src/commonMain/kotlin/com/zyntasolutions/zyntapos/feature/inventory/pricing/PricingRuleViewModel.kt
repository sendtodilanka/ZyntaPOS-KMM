package com.zyntasolutions.zyntapos.feature.inventory.pricing

import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.core.result.onError
import com.zyntasolutions.zyntapos.core.result.onSuccess
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.PricingRule
import com.zyntasolutions.zyntapos.domain.repository.PricingRuleRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * ViewModel for pricing rule management (C2.1 Region-Based Pricing).
 *
 * Manages CRUD operations for store-specific, time-bounded pricing rules.
 */
class PricingRuleViewModel(
    private val pricingRuleRepository: PricingRuleRepository,
    private val productRepository: ProductRepository,
) : BaseViewModel<PricingRuleState, PricingRuleIntent, PricingRuleEffect>(
    initialState = PricingRuleState()
) {

    init {
        observeRules()
        loadProducts()
    }

    override suspend fun handleIntent(intent: PricingRuleIntent) {
        when (intent) {
            is PricingRuleIntent.LoadRules           -> { /* Rules are observed reactively */ }
            is PricingRuleIntent.FilterByProduct     -> updateState { copy(filterProductId = intent.productId) }
            is PricingRuleIntent.SetActiveOnlyFilter -> updateState { copy(filterActiveOnly = intent.activeOnly) }
            is PricingRuleIntent.OpenDialog          -> onOpenDialog(intent.rule)
            is PricingRuleIntent.DismissDialog       -> updateState { copy(showDialog = false, editingRule = null) }
            is PricingRuleIntent.UpdateField         -> onUpdateField(intent.field, intent.value)
            is PricingRuleIntent.SetActive           -> updateState { copy(formIsActive = intent.active) }
            is PricingRuleIntent.SaveRule            -> onSaveRule()
            is PricingRuleIntent.ConfirmDelete       -> updateState { copy(deleteTarget = intent.rule) }
            is PricingRuleIntent.DismissDelete       -> updateState { copy(deleteTarget = null) }
            is PricingRuleIntent.ExecuteDelete       -> onExecuteDelete()
            is PricingRuleIntent.DismissError        -> updateState { copy(error = null) }
            is PricingRuleIntent.DismissSuccess      -> updateState { copy(successMessage = null) }
        }
    }

    private fun observeRules() {
        pricingRuleRepository.getAllRules()
            .onEach { rules -> updateState { copy(rules = rules, isLoadingRules = false) } }
            .catch { e -> updateState { copy(error = e.message, isLoadingRules = false) } }
            .launchIn(viewModelScope)
        updateState { copy(isLoadingRules = true) }
    }

    private fun loadProducts() {
        viewModelScope.launch {
            productRepository.getAll()
                .catch { /* Ignore — products are optional reference data */ }
                .onEach { products -> updateState { copy(products = products) } }
                .launchIn(viewModelScope)
        }
    }

    private fun onOpenDialog(rule: PricingRule?) {
        updateState {
            copy(
                showDialog = true,
                editingRule = rule,
                formProductId = rule?.productId ?: "",
                formStoreId = rule?.storeId ?: "",
                formPrice = rule?.price?.toString() ?: "",
                formCostPrice = rule?.costPrice?.toString() ?: "",
                formPriority = (rule?.priority ?: 0).toString(),
                formValidFrom = rule?.validFrom?.toString() ?: "",
                formValidTo = rule?.validTo?.toString() ?: "",
                formDescription = rule?.description ?: "",
                formIsActive = rule?.isActive ?: true,
            )
        }
    }

    private fun onUpdateField(field: PricingField, value: String) {
        updateState {
            when (field) {
                PricingField.PRODUCT_ID   -> copy(formProductId = value)
                PricingField.STORE_ID     -> copy(formStoreId = value)
                PricingField.PRICE        -> copy(formPrice = value)
                PricingField.COST_PRICE   -> copy(formCostPrice = value)
                PricingField.PRIORITY     -> copy(formPriority = value)
                PricingField.VALID_FROM   -> copy(formValidFrom = value)
                PricingField.VALID_TO     -> copy(formValidTo = value)
                PricingField.DESCRIPTION  -> copy(formDescription = value)
            }
        }
    }

    private fun onSaveRule() {
        val state = state.value
        val price = state.formPrice.toDoubleOrNull()
        if (state.formProductId.isBlank()) {
            updateState { copy(error = "Product is required") }
            return
        }
        if (price == null || price < 0) {
            updateState { copy(error = "Price must be a non-negative number") }
            return
        }

        updateState { copy(isSaving = true) }
        val now = Clock.System.now().toEpochMilliseconds()

        val rule = PricingRule(
            id = state.editingRule?.id ?: IdGenerator.uuid(),
            productId = state.formProductId,
            storeId = state.formStoreId.takeIf { it.isNotBlank() },
            price = price,
            costPrice = state.formCostPrice.toDoubleOrNull(),
            priority = state.formPriority.toIntOrNull() ?: 0,
            validFrom = state.formValidFrom.toLongOrNull(),
            validTo = state.formValidTo.toLongOrNull(),
            isActive = state.formIsActive,
            description = state.formDescription,
            createdAt = state.editingRule?.createdAt ?: now,
            updatedAt = now,
        )

        viewModelScope.launch {
            val isUpdate = state.editingRule != null
            pricingRuleRepository.upsert(rule)
                .onSuccess {
                    val action = if (isUpdate) "updated" else "created"
                    updateState { copy(isSaving = false, showDialog = false, editingRule = null, successMessage = "Pricing rule $action") }
                }
                .onError { ex ->
                    updateState { copy(isSaving = false, error = ex.message) }
                }
        }
    }

    private fun onExecuteDelete() {
        val target = state.value.deleteTarget ?: return
        viewModelScope.launch {
            pricingRuleRepository.delete(target.id)
                .onSuccess {
                    updateState { copy(deleteTarget = null, successMessage = "Pricing rule deleted") }
                }
                .onError { ex ->
                    updateState { copy(deleteTarget = null, error = ex.message) }
                }
        }
    }
}
