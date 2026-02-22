package com.zyntasolutions.zyntapos.feature.pos

import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.OrderTotals
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.repository.CategoryRepository
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.usecase.pos.AddItemToCartUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.ApplyItemDiscountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.ApplyOrderDiscountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.CalculateOrderTotalsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.HoldOrderUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.PrintReceiptUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.ProcessPaymentUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.RemoveItemFromCartUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.RetrieveHeldOrderUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.UpdateCartItemQuantityUseCase
import com.zyntasolutions.zyntapos.domain.formatter.ReceiptFormatter
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Root ViewModel for the POS checkout screen (Sprint 14, task 9.1.1).
 *
 * ### Responsibilities
 * - Subscribes to [ProductRepository.getAll] and [CategoryRepository.getAll] as reactive
 *   `Flow`s, pushing filtered snapshots into [PosState.products] and [PosState.categories].
 * - Maintains a **debounced search** pipeline: [PosState.searchQuery] is debounced 300 ms
 *   before calling [ProductRepository.search], satisfying the sub-200 ms display SLA.
 * - Delegates every cart mutation to dedicated use cases ([AddItemToCartUseCase],
 *   [RemoveItemFromCartUseCase], [UpdateCartItemQuantityUseCase], [ApplyItemDiscountUseCase],
 *   [ApplyOrderDiscountUseCase]); recalculates totals via [CalculateOrderTotalsUseCase]
 *   after each mutation.
 * - Handles barcode scanning: [PosIntent.ScanBarcode] → [ProductRepository.getByBarcode]
 *   → auto-[AddItemToCartUseCase] on unique match or [PosEffect.BarcodeNotFound].
 * - Implements hold/retrieve: [PosIntent.HoldOrder] → [HoldOrderUseCase];
 *   [PosIntent.RetrieveHeld] → [RetrieveHeldOrderUseCase].
 * - Processes payment: [PosIntent.ProcessPayment] → [ProcessPaymentUseCase]
 *   → [PosEffect.ShowReceiptScreen] / [PosEffect.OpenCashDrawer] / [PosEffect.PrintReceipt].
 *
 * ### Session context
 * [cashierId], [storeId], and [registerSessionId] are injected at construction time
 * from the authenticated session managed by the auth module. They are required by
 * [ProcessPaymentUseCase] and [HoldOrderUseCase].
 *
 * @param productRepository         Live product catalogue source.
 * @param categoryRepository        Live category list source.
 * @param orderRepository           Held-order retrieval and listing.
 * @param addItemUseCase            Cart addition with stock validation.
 * @param removeItemUseCase         Cart line removal.
 * @param updateQtyUseCase          Cart line quantity update.
 * @param applyItemDiscountUseCase  Line-level discount (role-gated).
 * @param applyOrderDiscountUseCase Order-level discount.
 * @param calculateTotalsUseCase    Order total / tax calculation.
 * @param holdOrderUseCase          Cart → held order serialisation.
 * @param retrieveHeldUseCase       Held order → cart restoration.
 * @param processPaymentUseCase     Checkout finalisation + stock decrement.
 * @param cashierId                 Authenticated cashier's user ID.
 * @param storeId                   Active store ID.
 * @param registerSessionId         Active register session ID.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class PosViewModel(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val orderRepository: OrderRepository,
    private val addItemUseCase: AddItemToCartUseCase,
    private val removeItemUseCase: RemoveItemFromCartUseCase,
    private val updateQtyUseCase: UpdateCartItemQuantityUseCase,
    private val applyItemDiscountUseCase: ApplyItemDiscountUseCase,
    private val applyOrderDiscountUseCase: ApplyOrderDiscountUseCase,
    private val calculateTotalsUseCase: CalculateOrderTotalsUseCase,
    private val holdOrderUseCase: HoldOrderUseCase,
    private val retrieveHeldUseCase: RetrieveHeldOrderUseCase,
    private val processPaymentUseCase: ProcessPaymentUseCase,
    /** Domain use case for thermal receipt printing; called when cashier taps "Print" in [ReceiptScreen]. */
    private val printReceiptUseCase: PrintReceiptUseCase,
    /** Pure-domain formatter that converts an [Order] into a monospace preview string for [ReceiptScreen]. */
    private val receiptFormatter: ReceiptFormatter,
    val cashierId: String,
    val storeId: String,
    val registerSessionId: String,
) : BaseViewModel<PosState, PosIntent, PosEffect>(PosState()) {

    // ── Internal search / category filter state flows ─────────────────────────

    /** Mirrors [PosState.searchQuery] for reactive debounce pipeline. */
    private val _searchQuery = MutableStateFlow("")

    /** Mirrors [PosState.selectedCategoryId] for reactive filter pipeline. */
    private val _selectedCategoryId = MutableStateFlow<String?>(null)

    // ── Reactive product pipeline ─────────────────────────────────────────────

    init {
        observeCategories()
        observeProducts()
        observeHeldOrders()
    }

    /**
     * Subscribes to the live category list and pushes it into state.
     */
    private fun observeCategories() {
        categoryRepository.getAll()
            .onEach { cats -> updateState { copy(categories = cats) } }
            .launchIn(viewModelScope)
    }

    /**
     * Combines search query (debounced 300 ms) and selected category into a
     * reactive [ProductRepository.search] call. Falls back to [ProductRepository.getAll]
     * when query is blank and no category is selected for maximum performance.
     *
     * flatMapLatest cancels any in-flight search when the query/category changes,
     * preventing stale results from appearing in the grid.
     */
    private fun observeProducts() {
        combine(_searchQuery.debounce(300L), _selectedCategoryId) { q, cat -> q to cat }
            .distinctUntilChanged()
            .flatMapLatest { (q, cat) ->
                if (q.isBlank() && cat == null) {
                    productRepository.getAll()
                } else {
                    productRepository.search(q, cat)
                }
            }
            .onEach { products -> updateState { copy(products = products, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    /**
     * Subscribes to held orders (status = HELD) and keeps [PosState.heldOrders] current.
     */
    private fun observeHeldOrders() {
        orderRepository.getAll(mapOf("status" to OrderStatus.HELD.name))
            .onEach { held -> updateState { copy(heldOrders = held) } }
            .launchIn(viewModelScope)
    }

    // ── Intent handler ────────────────────────────────────────────────────────

    override suspend fun handleIntent(intent: PosIntent) {
        when (intent) {
            is PosIntent.LoadProducts  -> onLoadProducts()
            is PosIntent.SelectCategory -> onSelectCategory(intent.id)
            is PosIntent.SearchQueryChanged -> onSearchQueryChanged(intent.query)
            is PosIntent.SearchFocusChanged -> updateState { copy(isSearchFocused = intent.focused) }
            is PosIntent.AddToCart -> onAddToCart(intent.product.id)
            is PosIntent.RemoveFromCart -> onRemoveFromCart(intent.productId)
            is PosIntent.UpdateQty -> onUpdateQty(intent.productId, intent.qty)
            is PosIntent.ApplyItemDiscount -> onApplyItemDiscount(intent.productId, intent.discount, intent.type)
            is PosIntent.ApplyOrderDiscount -> onApplyOrderDiscount(intent.discount, intent.type)
            is PosIntent.ClearCart -> onClearCart()
            is PosIntent.SetNotes -> updateState { copy(/* notes stored at payment time */ error = null) }
            is PosIntent.SelectCustomer -> updateState { copy(selectedCustomer = intent.customer) }
            is PosIntent.ClearCustomer -> updateState { copy(selectedCustomer = null) }
            is PosIntent.ScanBarcode -> onScanBarcode(intent.barcode)
            is PosIntent.SetScannerActive -> updateState { copy(scannerActive = intent.active) }
            is PosIntent.HoldOrder -> onHoldOrder()
            is PosIntent.RetrieveHeld -> onRetrieveHeld(intent.holdId)
            is PosIntent.ProcessPayment -> onProcessPayment(intent)
            is PosIntent.PrintCurrentReceipt -> onPrintCurrentReceipt()
            is PosIntent.DismissPrintError -> updateState { copy(printError = null) }
        }
    }

    // ── Intent handlers ───────────────────────────────────────────────────────

    private fun onLoadProducts() {
        updateState { copy(isLoading = true, error = null) }
        // Triggers are reactive — emitting an empty query restarts the pipeline.
        _searchQuery.value = currentState.searchQuery
        _selectedCategoryId.value = currentState.selectedCategoryId
    }

    private fun onSelectCategory(id: String?) {
        updateState { copy(selectedCategoryId = id) }
        _selectedCategoryId.value = id
    }

    private fun onSearchQueryChanged(query: String) {
        updateState { copy(searchQuery = query) }
        _searchQuery.value = query
    }

    private suspend fun onAddToCart(productId: String) {
        val result = addItemUseCase(currentState.cartItems, productId)
        when (result) {
            is Result.Success -> {
                val updatedCart = result.data
                val totalsResult = calculateTotalsUseCase(
                    updatedCart,
                    currentState.orderDiscount,
                    currentState.orderDiscountType,
                )
                val totals = (totalsResult as? Result.Success)?.data ?: currentState.orderTotals
                updateState { copy(cartItems = updatedCart, orderTotals = totals, error = null) }
            }
            is Result.Error -> sendEffect(PosEffect.ShowError(result.exception.message ?: "Failed to add item"))
            is Result.Loading -> Unit
        }
    }

    private fun onRemoveFromCart(productId: String) {
        val result = removeItemUseCase(currentState.cartItems, productId)
        if (result is Result.Success) {
            val updatedCart = result.data
            val totalsResult = calculateTotalsUseCase(
                updatedCart,
                currentState.orderDiscount,
                currentState.orderDiscountType,
            )
            val totals = (totalsResult as? Result.Success)?.data ?: OrderTotals.EMPTY
            updateState { copy(cartItems = updatedCart, orderTotals = totals) }
        }
    }

    private suspend fun onUpdateQty(productId: String, qty: Double) {
        if (qty <= 0.0) {
            onRemoveFromCart(productId)
            return
        }
        val result = updateQtyUseCase(currentState.cartItems, productId, qty)
        when (result) {
            is Result.Success -> {
                val updatedCart = result.data
                val totalsResult = calculateTotalsUseCase(
                    updatedCart,
                    currentState.orderDiscount,
                    currentState.orderDiscountType,
                )
                val totals = (totalsResult as? Result.Success)?.data ?: currentState.orderTotals
                updateState { copy(cartItems = updatedCart, orderTotals = totals) }
            }
            is Result.Error -> sendEffect(PosEffect.ShowError(result.exception.message ?: "Invalid quantity"))
            is Result.Loading -> Unit
        }
    }

    private fun onApplyItemDiscount(productId: String, discount: Double, type: DiscountType) {
        val result = applyItemDiscountUseCase(currentState.cartItems, productId, discount, type, cashierId)
        if (result is Result.Success) {
            val totalsResult = calculateTotalsUseCase(result.data, currentState.orderDiscount, currentState.orderDiscountType)
            val totals = (totalsResult as? Result.Success)?.data ?: currentState.orderTotals
            updateState { copy(cartItems = result.data, orderTotals = totals) }
        } else if (result is Result.Error) {
            sendEffect(PosEffect.ShowError(result.exception.message ?: "Cannot apply discount"))
        }
    }

    private suspend fun onApplyOrderDiscount(discount: Double, type: DiscountType) {
        val result = applyOrderDiscountUseCase(currentState.cartItems, discount, type)
        when (result) {
            is Result.Success -> {
                updateState { copy(orderDiscount = discount, orderDiscountType = type, orderTotals = result.data) }
            }
            is Result.Error -> sendEffect(PosEffect.ShowError(result.exception.message ?: "Cannot apply discount"))
            is Result.Loading -> Unit
        }
    }

    private fun onClearCart() {
        updateState {
            copy(
                cartItems = emptyList(),
                orderDiscount = 0.0,
                orderDiscountType = DiscountType.FIXED,
                selectedCustomer = null,
                orderTotals = OrderTotals.EMPTY,
                error = null,
            )
        }
    }

    private suspend fun onScanBarcode(barcode: String) {
        when (val result = productRepository.getByBarcode(barcode)) {
            is Result.Success -> onAddToCart(result.data.id)
            is Result.Error -> sendEffect(PosEffect.BarcodeNotFound(barcode))
            is Result.Loading -> Unit
        }
    }

    private suspend fun onHoldOrder() {
        if (currentState.cartItems.isEmpty()) {
            sendEffect(PosEffect.ShowError("Cart is empty — nothing to hold."))
            return
        }
        when (val result = holdOrderUseCase(currentState.cartItems)) {
            is Result.Success -> onClearCart()
            is Result.Error -> sendEffect(PosEffect.ShowError(result.exception.message ?: "Failed to hold order"))
            is Result.Loading -> Unit
        }
    }

    private suspend fun onRetrieveHeld(holdId: String) {
        when (val result = retrieveHeldUseCase(holdId)) {
            is Result.Success -> {
                val totalsResult = calculateTotalsUseCase(result.data, 0.0, DiscountType.FIXED)
                val totals = (totalsResult as? Result.Success)?.data ?: OrderTotals.EMPTY
                updateState {
                    copy(
                        cartItems = result.data,
                        orderDiscount = 0.0,
                        orderDiscountType = DiscountType.FIXED,
                        orderTotals = totals,
                        error = null,
                    )
                }
            }
            is Result.Error -> sendEffect(PosEffect.ShowError(result.exception.message ?: "Failed to retrieve order"))
            is Result.Loading -> Unit
        }
    }

    private suspend fun onProcessPayment(intent: PosIntent.ProcessPayment) {
        updateState { copy(isLoading = true) }
        val result = processPaymentUseCase(
            items = currentState.cartItems,
            paymentMethod = intent.method,
            paymentSplits = intent.splits,
            amountTendered = intent.tendered,
            customerId = currentState.selectedCustomer?.id,
            cashierId = cashierId,
            storeId = storeId,
            registerSessionId = registerSessionId,
            orderDiscount = currentState.orderDiscount,
            orderDiscountType = currentState.orderDiscountType,
        )
        updateState { copy(isLoading = false) }
        when (result) {
            is Result.Success -> {
                val order = result.data
                // Build receipt preview text in domain — no HAL bytes in state
                val previewText = receiptFormatter.format(order)
                if (intent.method == PaymentMethod.CASH) {
                    sendEffect(PosEffect.OpenCashDrawer(registerSessionId))
                }
                sendEffect(PosEffect.PrintReceipt(order.id))
                sendEffect(PosEffect.ShowReceiptScreen(order.id))
                // Persist order + preview text so ReceiptScreen can display and reprint
                updateState {
                    copy(
                        receiptPreviewText = previewText,
                        currentReceiptOrder = order,
                    )
                }
                onClearCart()
            }
            is Result.Error -> sendEffect(PosEffect.ShowError(result.exception.message ?: "Payment failed"))
            is Result.Loading -> Unit
        }
    }

    /**
     * Handles [PosIntent.PrintCurrentReceipt]: calls [PrintReceiptUseCase] with the order
     * stored in [PosState.currentReceiptOrder] and updates [PosState.isPrinting] /
     * [PosState.printError] accordingly.
     *
     * No-ops silently when [PosState.currentReceiptOrder] is null (guards against stale taps).
     */
    private suspend fun onPrintCurrentReceipt() {
        val order = currentState.currentReceiptOrder ?: return
        updateState { copy(isPrinting = true, printError = null) }
        when (val result = printReceiptUseCase(order, cashierId)) {
            is Result.Success -> updateState { copy(isPrinting = false) }
            is Result.Error   -> updateState {
                copy(
                    isPrinting = false,
                    printError = result.exception.message ?: "Print failed — please retry",
                )
            }
            is Result.Loading -> Unit
        }
    }
}
