package com.zyntasolutions.zyntapos.feature.pos

import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.OrderTotals
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.CategoryRepository
import com.zyntasolutions.zyntapos.domain.repository.CustomerRepository
import com.zyntasolutions.zyntapos.domain.repository.CustomerWalletRepository
import com.zyntasolutions.zyntapos.domain.repository.LoyaltyRepository
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.RegisterRepository
import com.zyntasolutions.zyntapos.domain.usecase.coupons.CalculateCouponDiscountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.coupons.ValidateCouponUseCase
import com.zyntasolutions.zyntapos.domain.usecase.crm.EarnRewardPointsUseCase
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
import com.zyntasolutions.zyntapos.domain.usecase.accounting.PostSaleJournalEntryUseCase
import com.zyntasolutions.zyntapos.domain.formatter.ReceiptFormatter
import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Root ViewModel for the POS checkout screen (Sprint 14, extended Sprint 22).
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
 * ### Sprint 22 extensions
 * - **Wallet**: Loads wallet balance when a customer is selected; debits wallet
 *   after payment if [PosState.walletPaymentAmount] > 0.
 * - **Loyalty**: Earns reward points via [EarnRewardPointsUseCase] after payment.
 * - **Coupon**: Validates coupon codes via [ValidateCouponUseCase]; applies the
 *   monetary coupon discount on top of any existing order-level discount.
 *
 * ### Session context
 * [cashierId], [storeId], and [registerSessionId] are injected at construction time
 * from the authenticated session managed by the auth module. They are required by
 * [ProcessPaymentUseCase] and [HoldOrderUseCase].
 *
 * @param productRepository              Live product catalogue source.
 * @param categoryRepository             Live category list source.
 * @param orderRepository                Held-order retrieval and listing.
 * @param addItemUseCase                 Cart addition with stock validation.
 * @param removeItemUseCase              Cart line removal.
 * @param updateQtyUseCase               Cart line quantity update.
 * @param applyItemDiscountUseCase       Line-level discount (role-gated).
 * @param applyOrderDiscountUseCase      Order-level discount.
 * @param calculateTotalsUseCase         Order total / tax calculation.
 * @param holdOrderUseCase               Cart → held order serialisation.
 * @param retrieveHeldUseCase            Held order → cart restoration.
 * @param processPaymentUseCase          Checkout finalisation + stock decrement.
 * @param printReceiptUseCase            Thermal receipt printing.
 * @param receiptFormatter               Monospace receipt text builder.
 * @param walletRepository               Store-credit wallet balance + debit.
 * @param loyaltyRepository              Reward points balance + tier lookup.
 * @param validateCouponUseCase          Validates coupon codes against the catalogue.
 * @param calculateCouponDiscountUseCase Computes monetary coupon discount (pure).
 * @param earnRewardPointsUseCase        Awards loyalty points after a successful sale.
 * @param customerRepository             Customer directory — used for the customer picker dialog.
 * @param registerRepository             Register session source — active session ID is resolved in [init].
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
    private val walletRepository: CustomerWalletRepository,
    private val loyaltyRepository: LoyaltyRepository,
    private val validateCouponUseCase: ValidateCouponUseCase,
    private val calculateCouponDiscountUseCase: CalculateCouponDiscountUseCase,
    private val earnRewardPointsUseCase: EarnRewardPointsUseCase,
    private val customerRepository: CustomerRepository,
    private val registerRepository: RegisterRepository,
    private val authRepository: AuthRepository,
    private val postSaleJournalEntryUseCase: PostSaleJournalEntryUseCase,
) : BaseViewModel<PosState, PosIntent, PosEffect>(PosState()) {

    private var cashierId: String = "unknown"
    private var storeId: String = "default-store"
    private var registerSessionId: String = ""

    // ── Internal search / category filter state flows ─────────────────────────

    /** Mirrors [PosState.searchQuery] for reactive debounce pipeline. */
    private val _searchQuery = MutableStateFlow("")

    /** Mirrors [PosState.selectedCategoryId] for reactive filter pipeline. */
    private val _selectedCategoryId = MutableStateFlow<String?>(null)

    // ── Reactive product pipeline ─────────────────────────────────────────────

    init {
        viewModelScope.launch {
            val session = authRepository.getSession().first()
            cashierId = session?.id ?: "unknown"
            storeId = session?.storeId ?: "default-store"
            registerSessionId = registerRepository.getActive().first()?.id ?: ""
        }
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
            is PosIntent.LoadProducts       -> onLoadProducts()
            is PosIntent.SelectCategory     -> onSelectCategory(intent.id)
            is PosIntent.SearchQueryChanged -> onSearchQueryChanged(intent.query)
            is PosIntent.SearchFocusChanged -> updateState { copy(isSearchFocused = intent.focused) }
            is PosIntent.AddToCart          -> onAddToCart(intent.product.id)
            is PosIntent.RemoveFromCart     -> onRemoveFromCart(intent.productId)
            is PosIntent.UpdateQty          -> onUpdateQty(intent.productId, intent.qty)
            is PosIntent.ApplyItemDiscount  -> onApplyItemDiscount(intent.productId, intent.discount, intent.type)
            is PosIntent.ApplyOrderDiscount -> onApplyOrderDiscount(intent.discount, intent.type)
            is PosIntent.ClearCart              -> onClearCart()
            is PosIntent.SetNotes               -> updateState { copy(orderNotes = intent.notes) }
            is PosIntent.SelectCustomer         -> onSelectCustomer(intent)
            is PosIntent.ClearCustomer          -> onClearCustomer()
            is PosIntent.RequestCustomerSelect  -> onRequestCustomerSelect()
            is PosIntent.SearchCustomers        -> onSearchCustomers(intent.query)
            is PosIntent.ScanBarcode        -> onScanBarcode(intent.barcode)
            is PosIntent.SetScannerActive   -> updateState { copy(scannerActive = intent.active) }
            is PosIntent.HoldOrder          -> onHoldOrder()
            is PosIntent.RetrieveHeld       -> onRetrieveHeld(intent.holdId)
            is PosIntent.RequestPayment     -> onRequestPayment()
            is PosIntent.ProcessPayment     -> onProcessPayment(intent)
            is PosIntent.PrintCurrentReceipt -> onPrintCurrentReceipt()
            is PosIntent.DismissPrintError  -> updateState { copy(printError = null) }
            // ── Sprint 22: wallet + coupon ──────────────────────────────────
            is PosIntent.LoadCustomerWallet      -> currentState.selectedCustomer?.let { onLoadCustomerWallet(it.id) }
            is PosIntent.SetWalletPaymentAmount  -> updateState { copy(walletPaymentAmount = intent.amount) }
            is PosIntent.EnterCouponCode         -> updateState { copy(couponCode = intent.code, couponError = null) }
            is PosIntent.ValidateCoupon          -> onValidateCoupon()
            is PosIntent.ClearCoupon             -> updateState {
                copy(appliedCoupon = null, couponDiscount = 0.0, couponCode = "", couponError = null)
            }
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
                orderNotes = "",
                selectedCustomer = null,
                orderTotals = OrderTotals.EMPTY,
                error = null,
                // ── wallet & loyalty ──────────────────────────────────────────
                walletBalance = null,
                loyaltyPointsBalance = null,
                walletPaymentAmount = 0.0,
                // ── coupon ────────────────────────────────────────────────────
                couponCode = "",
                couponValidating = false,
                appliedCoupon = null,
                couponDiscount = 0.0,
                couponError = null,
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

    private suspend fun onRequestPayment() {
        if (currentState.cartItems.isEmpty()) {
            sendEffect(PosEffect.ShowError("Cannot open payment: the cart is empty."))
            return
        }
        sendEffect(PosEffect.OpenPaymentSheet)
    }

    private suspend fun onProcessPayment(intent: PosIntent.ProcessPayment) {
        updateState { copy(isLoading = true) }
        // Combine base order discount (monetary) + coupon discount as a single FIXED amount.
        // orderTotals.discountAmount is already the monetary value regardless of original type.
        val combinedDiscount = currentState.orderTotals.discountAmount + currentState.couponDiscount
        // Add wallet payment to tendered amount so the payment validator is satisfied.
        val effectiveTendered = intent.tendered + currentState.walletPaymentAmount
        val result = processPaymentUseCase(
            items = currentState.cartItems,
            paymentMethod = intent.method,
            paymentSplits = intent.splits,
            amountTendered = effectiveTendered,
            customerId = currentState.selectedCustomer?.id,
            cashierId = cashierId,
            storeId = storeId,
            registerSessionId = registerSessionId,
            orderDiscount = combinedDiscount,
            orderDiscountType = DiscountType.FIXED,
            notes = currentState.orderNotes.ifBlank { null },
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
                // ── Post-payment: earn loyalty points + debit wallet ──────────
                val customer = currentState.selectedCustomer
                if (customer != null) {
                    onEarnLoyaltyPoints(customerId = customer.id, orderTotal = order.total, orderId = order.id)
                    val walletAmt = currentState.walletPaymentAmount
                    if (walletAmt > 0.0) {
                        onDebitWallet(customerId = customer.id, amount = walletAmt, orderId = order.id)
                    }
                }
                onClearCart()
                // ── Post-payment: auto-post accounting journal entry (best-effort) ──
                val now = Clock.System.now()
                val entryDate = now.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                @Suppress("TooGenericExceptionCaught")
                try {
                    val journalResult = postSaleJournalEntryUseCase.execute(
                        storeId = storeId,
                        orderId = order.id,
                        totalAmount = order.total,
                        subtotal = order.subtotal,
                        taxAmount = order.taxAmount,
                        cashierId = cashierId,
                        entryDate = entryDate,
                        now = now.toEpochMilliseconds(),
                    )
                    if (journalResult is Result.Error) {
                        ZyntaLogger.w("POS", "Journal entry failed: ${journalResult.exception.message}")
                    }
                } catch (e: Exception) {
                    ZyntaLogger.w("POS", "Journal entry threw unexpectedly: ${e.message}")
                }
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

    // ── Sprint 22: customer wallet & loyalty ──────────────────────────────────

    /**
     * Attaches a customer to the current order and immediately loads their wallet
     * balance and loyalty points balance so the cashier can offer wallet payment
     * or inform the customer of available points.
     */
    private suspend fun onSelectCustomer(intent: PosIntent.SelectCustomer) {
        updateState { copy(selectedCustomer = intent.customer) }
        onLoadCustomerWallet(intent.customer.id)
    }

    /**
     * Removes the customer association and clears all wallet/loyalty state.
     */
    private fun onClearCustomer() {
        updateState {
            copy(
                selectedCustomer = null,
                walletBalance = null,
                loyaltyPointsBalance = null,
                walletPaymentAmount = 0.0,
            )
        }
    }

    /**
     * Pre-loads all customers into [PosState.customerPickerResults] and emits
     * [PosEffect.OpenCustomerPicker] so the UI can show the picker dialog.
     */
    private suspend fun onRequestCustomerSelect() {
        val customers = customerRepository.getAll().first()
        updateState { copy(customerPickerResults = customers, customerSearchQuery = "") }
        sendEffect(PosEffect.OpenCustomerPicker)
    }

    /**
     * Filters the customer picker list by [query] via a live [CustomerRepository.search] call.
     * Results replace [PosState.customerPickerResults] on each emission.
     */
    private fun onSearchCustomers(query: String) {
        updateState { copy(customerSearchQuery = query) }
        if (query.isBlank()) {
            customerRepository.getAll()
        } else {
            customerRepository.search(query)
        }
            .onEach { results -> updateState { copy(customerPickerResults = results) } }
            .launchIn(viewModelScope)
    }

    /**
     * Fetches the wallet balance and current loyalty points for [customerId] and
     * updates [PosState.walletBalance] and [PosState.loyaltyPointsBalance].
     * Errors are silently ignored — the cashier can still proceed without the values.
     */
    private suspend fun onLoadCustomerWallet(customerId: String) {
        val walletResult = walletRepository.getOrCreate(customerId)
        val loyaltyResult = loyaltyRepository.getBalance(customerId)
        updateState {
            copy(
                walletBalance = (walletResult as? Result.Success)?.data?.balance,
                loyaltyPointsBalance = (loyaltyResult as? Result.Success)?.data,
            )
        }
    }

    // ── Sprint 22: coupon validation ──────────────────────────────────────────

    /**
     * Validates [PosState.couponCode] against the repository.
     * On success: populates [PosState.appliedCoupon] and [PosState.couponDiscount].
     * On failure: sets [PosState.couponError].
     */
    private suspend fun onValidateCoupon() {
        val code = currentState.couponCode.trim()
        if (code.isBlank()) return
        updateState { copy(couponValidating = true, couponError = null) }
        val result = validateCouponUseCase(
            code = code,
            cartTotal = currentState.orderTotals.total,
            customerId = currentState.selectedCustomer?.id,
        )
        when (result) {
            is Result.Success -> {
                val discount = calculateCouponDiscountUseCase(result.data, currentState.orderTotals.total)
                updateState {
                    copy(
                        couponValidating = false,
                        appliedCoupon = result.data,
                        couponDiscount = discount,
                    )
                }
            }
            is Result.Error -> {
                updateState {
                    copy(
                        couponValidating = false,
                        couponError = result.exception.message ?: "Invalid coupon code",
                    )
                }
            }
            is Result.Loading -> Unit
        }
    }

    // ── Sprint 22: post-payment loyalty & wallet ──────────────────────────────

    /**
     * Earns loyalty points for [customerId] after a successful payment.
     * Awards 1 point per 10 currency units (configurable in production via settings).
     * Silently no-ops on error — loyalty failure must not block the receipt flow.
     */
    private suspend fun onEarnLoyaltyPoints(customerId: String, orderTotal: Double, orderId: String) {
        val basePoints = (orderTotal / 10.0).toInt().coerceAtLeast(0)
        if (basePoints <= 0) return
        val tier = (loyaltyRepository.getTierForPoints(
            currentState.loyaltyPointsBalance ?: 0,
        ) as? Result.Success)?.data
        earnRewardPointsUseCase(
            customerId = customerId,
            basePoints = basePoints,
            orderId = orderId,
            tier = tier,
        )
    }

    /**
     * Debits [amount] from the customer's store-credit wallet after a successful payment.
     * Silently no-ops on error — wallet debit failure should be surfaced through
     * a separate reconciliation process, not the cashier's receipt flow.
     */
    private suspend fun onDebitWallet(customerId: String, amount: Double, orderId: String) {
        val walletResult = walletRepository.getOrCreate(customerId)
        if (walletResult is Result.Success) {
            walletRepository.debit(
                walletId = walletResult.data.id,
                amount = amount,
                referenceType = "ORDER",
                referenceId = orderId,
                note = "POS wallet payment",
            )
        }
    }
}
