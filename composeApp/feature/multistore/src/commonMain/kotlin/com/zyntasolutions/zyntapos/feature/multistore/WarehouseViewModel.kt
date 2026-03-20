package com.zyntasolutions.zyntapos.feature.multistore

import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.StockTransfer
import com.zyntasolutions.zyntapos.domain.model.Warehouse
import com.zyntasolutions.zyntapos.domain.model.WarehouseRack
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.WarehouseRepository
import com.zyntasolutions.zyntapos.domain.usecase.multistore.ApproveStockTransferUseCase
import com.zyntasolutions.zyntapos.domain.usecase.multistore.CommitStockTransferUseCase
import com.zyntasolutions.zyntapos.domain.usecase.multistore.DispatchStockTransferUseCase
import com.zyntasolutions.zyntapos.domain.usecase.multistore.GetLowStockByWarehouseUseCase
import com.zyntasolutions.zyntapos.domain.usecase.multistore.GetWarehouseStockUseCase
import com.zyntasolutions.zyntapos.domain.usecase.multistore.ReceiveStockTransferUseCase
import com.zyntasolutions.zyntapos.domain.usecase.multistore.SetWarehouseStockUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.DeleteRackProductUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.DeleteWarehouseRackUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.GetRackProductsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.GetWarehouseRacksUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.SaveRackProductUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.SaveWarehouseRackUseCase
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * ViewModel for the Multi-store / Warehouse feature — Sprints 14–16 + 18 + C1.2.
 *
 * Manages warehouse list, warehouse CRUD, stock transfers, warehouse rack
 * management (Sprint 18), and per-warehouse stock levels (C1.2).
 *
 * @param warehouseRepository        Warehouse and stock transfer CRUD.
 * @param commitTransferUseCase      Validates and commits a pending transfer.
 * @param getWarehouseRacksUseCase   Reactive rack list for a warehouse.
 * @param saveWarehouseRackUseCase   Insert or update a rack record.
 * @param deleteWarehouseRackUseCase Soft-delete a rack record.
 * @param getWarehouseStockUseCase      Live stock list per warehouse (C1.2).
 * @param setWarehouseStockUseCase      Set absolute stock quantity (C1.2).
 * @param getLowStockByWarehouseUseCase Low-stock alerts per warehouse (C1.2).
 * @param getRackProductsUseCase        Products in a specific rack bin (C1.2).
 * @param saveRackProductUseCase        Assign/update product at rack bin (C1.2).
 * @param deleteRackProductUseCase      Remove product from rack bin (C1.2).
 * @param authRepository                Provides the active auth session for storeId and userId.
 */
class WarehouseViewModel(
    private val warehouseRepository: WarehouseRepository,
    private val productRepository: ProductRepository,
    private val commitTransferUseCase: CommitStockTransferUseCase,
    private val approveTransferUseCase: ApproveStockTransferUseCase,
    private val dispatchTransferUseCase: DispatchStockTransferUseCase,
    private val receiveTransferUseCase: ReceiveStockTransferUseCase,
    private val getWarehouseRacksUseCase: GetWarehouseRacksUseCase,
    private val saveWarehouseRackUseCase: SaveWarehouseRackUseCase,
    private val deleteWarehouseRackUseCase: DeleteWarehouseRackUseCase,
    private val getWarehouseStockUseCase: GetWarehouseStockUseCase,
    private val setWarehouseStockUseCase: SetWarehouseStockUseCase,
    private val getLowStockByWarehouseUseCase: GetLowStockByWarehouseUseCase,
    private val getRackProductsUseCase: GetRackProductsUseCase,
    private val saveRackProductUseCase: SaveRackProductUseCase,
    private val deleteRackProductUseCase: DeleteRackProductUseCase,
    private val authRepository: AuthRepository,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<WarehouseState, WarehouseIntent, WarehouseEffect>(WarehouseState()) {

    private var currentStoreId: String = "default"
    private var currentUserId: String = "unknown"
    private var productSearchJob: Job? = null

    init {
        analytics.logScreenView("Warehouse", "WarehouseViewModel")
        viewModelScope.launch {
            val session = authRepository.getSession().first()
            currentStoreId = session?.storeId ?: "default"
            currentUserId = session?.id ?: "unknown"
            observeWarehouses()
            loadPendingTransfers()
            loadApprovedTransfers()
            loadInTransitTransfers()
        }
    }

    private fun observeWarehouses() {
        warehouseRepository.getByStore(currentStoreId)
            .onEach { warehouses -> updateState { copy(warehouses = warehouses, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    private suspend fun loadPendingTransfers() {
        when (val result = warehouseRepository.getPendingTransfers()) {
            is Result.Success -> updateState { copy(pendingTransfers = result.data) }
            is Result.Error   -> sendEffect(WarehouseEffect.ShowError(result.exception.message ?: "Failed to load pending transfers"))
            is Result.Loading -> Unit
        }
    }

    override suspend fun handleIntent(intent: WarehouseIntent) {
        when (intent) {
            is WarehouseIntent.LoadWarehouses -> updateState { copy(isLoading = true) }

            is WarehouseIntent.SelectWarehouse -> onSelectWarehouse(intent.warehouseId)
            is WarehouseIntent.UpdateWarehouseField -> onUpdateWarehouseField(intent.field, intent.value)
            is WarehouseIntent.UpdateIsDefault -> updateState {
                copy(warehouseForm = warehouseForm.copy(isDefault = intent.isDefault))
            }
            is WarehouseIntent.SaveWarehouse -> onSaveWarehouse()

            is WarehouseIntent.LoadTransfers -> onLoadTransfers(intent.warehouseId)

            is WarehouseIntent.InitTransferForm -> onInitTransferForm(intent.sourceWarehouseId)
            is WarehouseIntent.UpdateTransferField -> onUpdateTransferField(intent.field, intent.value)
            is WarehouseIntent.SearchProducts -> onSearchProducts(intent.query)
            is WarehouseIntent.SelectTransferProduct -> onSelectTransferProduct(intent.product)
            is WarehouseIntent.SubmitTransfer -> onSubmitTransfer()

            is WarehouseIntent.CommitTransfer -> onCommitTransfer(intent.transferId)
            is WarehouseIntent.CancelTransfer -> onCancelTransfer(intent.transferId)

            // IST multi-step workflow (C1.3)
            is WarehouseIntent.ApproveTransfer -> onApproveTransfer(intent.transferId)
            is WarehouseIntent.DispatchTransfer -> onDispatchTransfer(intent.transferId)
            is WarehouseIntent.ReceiveTransfer -> onReceiveTransfer(intent.transferId)
            is WarehouseIntent.LoadTransfersByStatus -> onLoadTransfersByStatus(intent.status)
            is WarehouseIntent.SelectTransfer -> onSelectTransfer(intent.transferId)

            is WarehouseIntent.LoadRacks -> onLoadRacks(intent.warehouseId)
            is WarehouseIntent.SelectRack -> onSelectRack(intent.rackId, intent.warehouseId)
            is WarehouseIntent.UpdateRackField -> onUpdateRackField(intent.field, intent.value)
            is WarehouseIntent.SaveRack -> onSaveRack()
            is WarehouseIntent.RequestDeleteRack -> updateState { copy(showDeleteRackConfirm = intent.rack) }
            is WarehouseIntent.ConfirmDeleteRack -> onConfirmDeleteRack()
            is WarehouseIntent.CancelDeleteRack -> updateState { copy(showDeleteRackConfirm = null) }

            // ── Rack Products / C1.2 ───────────────────────────────────────────
            is WarehouseIntent.LoadRackProducts -> onLoadRackProducts(intent.rackId)
            is WarehouseIntent.OpenRackProductEntry -> onOpenRackProductEntry(intent.rackId, intent.productId)
            is WarehouseIntent.UpdateRackProductField -> onUpdateRackProductField(intent.field, intent.value)
            is WarehouseIntent.SaveRackProduct -> onSaveRackProduct()
            is WarehouseIntent.CancelRackProductEntry -> updateState {
                copy(rackProductForm = RackProductFormState())
            }
            is WarehouseIntent.RequestDeleteRackProduct -> updateState {
                copy(showDeleteRackProductConfirm = intent.entry)
            }
            is WarehouseIntent.ConfirmDeleteRackProduct -> onConfirmDeleteRackProduct()
            is WarehouseIntent.CancelDeleteRackProduct -> updateState {
                copy(showDeleteRackProductConfirm = null)
            }

            // ── Warehouse Stock / C1.2 ─────────────────────────────────────────
            is WarehouseIntent.LoadWarehouseStock -> onLoadWarehouseStock(intent.warehouseId)
            is WarehouseIntent.SearchStock -> onSearchStock(intent.query)
            is WarehouseIntent.OpenStockEntry -> onOpenStockEntry(intent.warehouseId, intent.productId)
            is WarehouseIntent.UpdateStockField -> onUpdateStockField(intent.field, intent.value)
            is WarehouseIntent.SaveStockEntry -> onSaveStockEntry()
            is WarehouseIntent.CancelStockEntry -> updateState {
                copy(stockEntryForm = StockEntryFormState())
            }

            is WarehouseIntent.DismissMessage -> updateState { copy(error = null, successMessage = null) }
        }
    }

    // ── Warehouse CRUD ─────────────────────────────────────────────────────

    private suspend fun onSelectWarehouse(warehouseId: String?) {
        if (warehouseId == null) {
            updateState { copy(selectedWarehouse = null, warehouseForm = WarehouseFormState()) }
            sendEffect(WarehouseEffect.NavigateToDetail(null))
            return
        }
        updateState { copy(isLoading = true) }
        when (val result = warehouseRepository.getById(warehouseId)) {
            is Result.Success -> {
                val w = result.data
                updateState {
                    copy(
                        selectedWarehouse = w,
                        isLoading = false,
                        warehouseForm = WarehouseFormState(
                            id = w.id,
                            name = w.name,
                            address = w.address ?: "",
                            isDefault = w.isDefault,
                            isEditing = true,
                        ),
                    )
                }
                sendEffect(WarehouseEffect.NavigateToDetail(warehouseId))
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(WarehouseEffect.ShowError(result.exception.message ?: "Failed to load warehouse"))
            }
            is Result.Loading -> {}
        }
    }

    private fun onUpdateWarehouseField(field: String, value: String) {
        updateState {
            copy(
                warehouseForm = when (field) {
                    "name" -> warehouseForm.copy(name = value, validationErrors = warehouseForm.validationErrors - "name")
                    "address" -> warehouseForm.copy(address = value)
                    else -> warehouseForm
                },
            )
        }
    }

    private suspend fun onSaveWarehouse() {
        val form = currentState.warehouseForm
        if (form.name.isBlank()) {
            updateState {
                copy(warehouseForm = warehouseForm.copy(validationErrors = mapOf("name" to "Name is required")))
            }
            return
        }
        updateState { copy(isLoading = true) }
        val warehouse = Warehouse(
            id = form.id ?: IdGenerator.newId(),
            storeId = currentStoreId,
            name = form.name.trim(),
            address = form.address.trim().takeIf { it.isNotBlank() },
            isDefault = form.isDefault,
        )
        val result = if (form.isEditing) warehouseRepository.update(warehouse)
                     else warehouseRepository.insert(warehouse)
        when (result) {
            is Result.Success -> {
                val msg = if (form.isEditing) "Warehouse updated" else "Warehouse created"
                updateState { copy(isLoading = false, successMessage = msg, warehouseForm = WarehouseFormState()) }
                sendEffect(WarehouseEffect.ShowSuccess(msg))
                sendEffect(WarehouseEffect.NavigateToList)
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(WarehouseEffect.ShowError(result.exception.message ?: "Save failed"))
            }
            is Result.Loading -> {}
        }
    }

    // ── Stock Transfers ────────────────────────────────────────────────────

    private fun onLoadTransfers(warehouseId: String) {
        warehouseRepository.getTransfersByWarehouse(warehouseId)
            .onEach { transfers -> updateState { copy(transfers = transfers) } }
            .launchIn(viewModelScope)
    }

    private fun onInitTransferForm(sourceWarehouseId: String?) {
        updateState { copy(transferForm = TransferFormState(sourceWarehouseId = sourceWarehouseId ?: "")) }
    }

    private fun onUpdateTransferField(field: String, value: String) {
        updateState {
            copy(
                transferForm = when (field) {
                    "sourceWarehouseId" -> transferForm.copy(
                        sourceWarehouseId = value,
                        validationErrors = transferForm.validationErrors - "sourceWarehouseId",
                    )
                    "destWarehouseId" -> transferForm.copy(
                        destWarehouseId = value,
                        validationErrors = transferForm.validationErrors - "destWarehouseId",
                    )
                    "productId" -> transferForm.copy(productId = value)
                    "productName" -> transferForm.copy(productName = value)
                    "quantity" -> transferForm.copy(
                        quantity = value,
                        validationErrors = transferForm.validationErrors - "quantity",
                    )
                    "notes" -> transferForm.copy(notes = value)
                    else -> transferForm
                },
            )
        }
    }

    private suspend fun onSubmitTransfer() {
        val form = currentState.transferForm
        val errors = validateTransferForm(form)
        if (errors.isNotEmpty()) {
            updateState { copy(transferForm = transferForm.copy(validationErrors = errors)) }
            return
        }
        updateState { copy(isLoading = true) }
        val transfer = StockTransfer(
            id = IdGenerator.newId(),
            sourceWarehouseId = form.sourceWarehouseId,
            destWarehouseId = form.destWarehouseId,
            productId = form.productId,
            quantity = form.quantity.toDoubleOrNull() ?: 1.0,
            notes = form.notes.trim().takeIf { it.isNotBlank() },
            status = StockTransfer.Status.PENDING,
        )
        when (val result = warehouseRepository.createTransfer(transfer)) {
            is Result.Success -> {
                updateState { copy(isLoading = false, transferForm = TransferFormState(), successMessage = "Transfer created") }
                sendEffect(WarehouseEffect.ShowSuccess("Transfer created"))
                sendEffect(WarehouseEffect.TransferComplete)
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(WarehouseEffect.ShowError(result.exception.message ?: "Transfer creation failed"))
            }
            is Result.Loading -> {}
        }
    }

    private suspend fun onCommitTransfer(transferId: String) {
        updateState { copy(isLoading = true) }
        when (val result = commitTransferUseCase(transferId, currentUserId)) {
            is Result.Success -> {
                updateState { copy(isLoading = false) }
                sendEffect(WarehouseEffect.ShowSuccess("Transfer committed successfully"))
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(WarehouseEffect.ShowError(result.exception.message ?: "Commit failed"))
            }
            is Result.Loading -> {}
        }
    }

    private suspend fun onCancelTransfer(transferId: String) {
        updateState { copy(isLoading = true) }
        when (val result = warehouseRepository.cancelTransfer(transferId)) {
            is Result.Success -> {
                updateState { copy(isLoading = false) }
                sendEffect(WarehouseEffect.ShowSuccess("Transfer cancelled"))
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(WarehouseEffect.ShowError(result.exception.message ?: "Cancel failed"))
            }
            is Result.Loading -> {}
        }
    }

    // ── IST Multi-step workflow handlers (C1.3) ───────────────────────────

    private suspend fun loadApprovedTransfers() {
        when (val result = warehouseRepository.getTransfersByStatus(com.zyntasolutions.zyntapos.domain.model.StockTransfer.Status.APPROVED)) {
            is Result.Success -> updateState { copy(approvedTransfers = result.data) }
            is Result.Error -> Unit // non-critical — don't surface on init
            is Result.Loading -> Unit
        }
    }

    private suspend fun loadInTransitTransfers() {
        when (val result = warehouseRepository.getTransfersByStatus(com.zyntasolutions.zyntapos.domain.model.StockTransfer.Status.IN_TRANSIT)) {
            is Result.Success -> updateState { copy(inTransitTransfers = result.data) }
            is Result.Error -> Unit
            is Result.Loading -> Unit
        }
    }

    private suspend fun onApproveTransfer(transferId: String) {
        updateState { copy(isLoading = true) }
        when (val result = approveTransferUseCase(transferId, currentUserId)) {
            is Result.Success -> {
                updateState { copy(isLoading = false) }
                loadPendingTransfers()
                loadApprovedTransfers()
                sendEffect(WarehouseEffect.ShowSuccess("Transfer approved"))
                sendEffect(WarehouseEffect.TransferApproved)
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(WarehouseEffect.ShowError(result.exception.message ?: "Approve failed"))
            }
            is Result.Loading -> {}
        }
    }

    private suspend fun onDispatchTransfer(transferId: String) {
        updateState { copy(isLoading = true) }
        when (val result = dispatchTransferUseCase(transferId, currentUserId)) {
            is Result.Success -> {
                updateState { copy(isLoading = false) }
                loadApprovedTransfers()
                loadInTransitTransfers()
                sendEffect(WarehouseEffect.ShowSuccess("Transfer dispatched — now in transit"))
                sendEffect(WarehouseEffect.TransferDispatched)
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(WarehouseEffect.ShowError(result.exception.message ?: "Dispatch failed"))
            }
            is Result.Loading -> {}
        }
    }

    private suspend fun onReceiveTransfer(transferId: String) {
        updateState { copy(isLoading = true) }
        when (val result = receiveTransferUseCase(transferId, currentUserId)) {
            is Result.Success -> {
                updateState { copy(isLoading = false) }
                loadInTransitTransfers()
                sendEffect(WarehouseEffect.ShowSuccess("Transfer received — stock updated"))
                sendEffect(WarehouseEffect.TransferReceived)
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(WarehouseEffect.ShowError(result.exception.message ?: "Receive failed"))
            }
            is Result.Loading -> {}
        }
    }

    private suspend fun onLoadTransfersByStatus(status: com.zyntasolutions.zyntapos.domain.model.StockTransfer.Status) {
        when (val result = warehouseRepository.getTransfersByStatus(status)) {
            is Result.Success -> updateState {
                when (status) {
                    com.zyntasolutions.zyntapos.domain.model.StockTransfer.Status.APPROVED -> copy(approvedTransfers = result.data)
                    com.zyntasolutions.zyntapos.domain.model.StockTransfer.Status.IN_TRANSIT -> copy(inTransitTransfers = result.data)
                    com.zyntasolutions.zyntapos.domain.model.StockTransfer.Status.PENDING -> copy(pendingTransfers = result.data)
                    else -> copy(transfers = result.data)
                }
            }
            is Result.Error -> sendEffect(WarehouseEffect.ShowError(result.exception.message ?: "Failed to load transfers"))
            is Result.Loading -> {}
        }
    }

    private suspend fun onSelectTransfer(transferId: String?) {
        if (transferId == null) {
            updateState { copy(selectedTransfer = null) }
            return
        }
        when (val result = warehouseRepository.getTransferById(transferId)) {
            is Result.Success -> {
                updateState { copy(selectedTransfer = result.data) }
                sendEffect(WarehouseEffect.NavigateToTransferDetail(transferId))
            }
            is Result.Error -> sendEffect(WarehouseEffect.ShowError(result.exception.message ?: "Transfer not found"))
            is Result.Loading -> {}
        }
    }

    // ── Rack CRUD ──────────────────────────────────────────────────────────

    private fun onLoadRacks(warehouseId: String) {
        getWarehouseRacksUseCase(warehouseId)
            .onEach { racks -> updateState { copy(racks = racks) } }
            .launchIn(viewModelScope)
    }

    private fun onSelectRack(rackId: String?, warehouseId: String) {
        if (rackId == null) {
            updateState {
                copy(
                    selectedRack = null,
                    rackForm = RackFormState(warehouseId = warehouseId, isEditing = false),
                )
            }
            sendEffect(WarehouseEffect.NavigateToRackDetail(null, warehouseId))
            return
        }
        // Look up from already-loaded racks list to avoid extra DB call
        val rack = currentState.racks.find { it.id == rackId }
        if (rack != null) {
            updateState {
                copy(
                    selectedRack = rack,
                    rackForm = RackFormState(
                        id = rack.id,
                        warehouseId = rack.warehouseId,
                        name = rack.name,
                        description = rack.description ?: "",
                        capacity = rack.capacity?.toString() ?: "",
                        isEditing = true,
                    ),
                )
            }
            sendEffect(WarehouseEffect.NavigateToRackDetail(rackId, warehouseId))
        } else {
            sendEffect(WarehouseEffect.ShowError("Rack not found"))
        }
    }

    private fun onUpdateRackField(field: String, value: String) {
        updateState {
            copy(
                rackForm = when (field) {
                    "name" -> rackForm.copy(name = value, validationErrors = rackForm.validationErrors - "name")
                    "description" -> rackForm.copy(description = value)
                    "capacity" -> rackForm.copy(capacity = value, validationErrors = rackForm.validationErrors - "capacity")
                    else -> rackForm
                },
            )
        }
    }

    private suspend fun onSaveRack() {
        val form = currentState.rackForm
        val errors = mutableMapOf<String, String>()
        if (form.name.isBlank()) errors["name"] = "Rack name is required"
        val parsedCapacity: Int? = if (form.capacity.isNotBlank()) {
            val v = form.capacity.toIntOrNull()
            if (v == null || v <= 0) {
                errors["capacity"] = "Capacity must be a positive number"
                null
            } else v
        } else null
        if (errors.isNotEmpty()) {
            updateState { copy(rackForm = rackForm.copy(validationErrors = errors)) }
            return
        }
        updateState { copy(isLoading = true) }
        val now = Clock.System.now().toEpochMilliseconds()
        val rack = WarehouseRack(
            id = form.id ?: IdGenerator.newId(),
            warehouseId = form.warehouseId,
            name = form.name.trim(),
            description = form.description.trim().takeIf { it.isNotBlank() },
            capacity = parsedCapacity,
            createdAt = now,
            updatedAt = now,
        )
        when (val result = saveWarehouseRackUseCase(rack, isUpdate = form.isEditing)) {
            is Result.Success -> {
                val msg = if (form.isEditing) "Rack updated" else "Rack created"
                updateState { copy(isLoading = false, successMessage = msg, rackForm = RackFormState()) }
                sendEffect(WarehouseEffect.ShowSuccess(msg))
                sendEffect(WarehouseEffect.NavigateToRackList)
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(WarehouseEffect.ShowError(result.exception.message ?: "Save failed"))
            }
            is Result.Loading -> {}
        }
    }

    private suspend fun onConfirmDeleteRack() {
        val rack = currentState.showDeleteRackConfirm ?: return
        updateState { copy(showDeleteRackConfirm = null, isLoading = true) }
        val now = Clock.System.now().toEpochMilliseconds()
        when (val result = deleteWarehouseRackUseCase(rack.id, now, now)) {
            is Result.Success -> {
                updateState { copy(isLoading = false) }
                sendEffect(WarehouseEffect.ShowSuccess("Rack \"${rack.name}\" deleted"))
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(WarehouseEffect.ShowError(result.exception.message ?: "Delete failed"))
            }
            is Result.Loading -> {}
        }
    }

    // ── Product Search (MS-1) ────────────────────────────────────────────

    private fun onSearchProducts(query: String) {
        updateState { copy(productSearchQuery = query) }
        productSearchJob?.cancel()
        if (query.isBlank()) {
            updateState { copy(productSearchResults = emptyList()) }
            return
        }
        productSearchJob = viewModelScope.launch {
            delay(300L) // debounce
            productRepository.search(query).first().let { results ->
                updateState { copy(productSearchResults = results.take(10)) }
            }
        }
    }

    private fun onSelectTransferProduct(product: Product) {
        updateState {
            copy(
                transferForm = transferForm.copy(
                    productId = product.id,
                    productName = product.name,
                    validationErrors = transferForm.validationErrors - "productId",
                ),
                productSearchQuery = product.name,
                productSearchResults = emptyList(),
            )
        }
    }

    // ── Rack Products / C1.2 ─────────────────────────────────────────────

    private fun onLoadRackProducts(rackId: String) {
        getRackProductsUseCase(rackId)
            .onEach { products -> updateState { copy(rackProducts = products) } }
            .launchIn(viewModelScope)
    }

    private fun onOpenRackProductEntry(rackId: String, productId: String?) {
        if (productId == null) {
            updateState {
                copy(rackProductForm = RackProductFormState(rackId = rackId))
            }
            sendEffect(WarehouseEffect.NavigateToRackProductDetail(rackId, null))
            return
        }
        val existing = currentState.rackProducts.find { it.productId == productId }
        updateState {
            copy(
                rackProductForm = RackProductFormState(
                    rackId = rackId,
                    productId = productId,
                    productName = existing?.productName ?: "",
                    quantity = existing?.quantity?.let {
                        if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
                    } ?: "0",
                    binLocation = existing?.binLocation ?: "",
                    isEditing = existing != null,
                ),
            )
        }
        sendEffect(WarehouseEffect.NavigateToRackProductDetail(rackId, productId))
    }

    private fun onUpdateRackProductField(field: String, value: String) {
        updateState {
            copy(
                rackProductForm = when (field) {
                    "productId" -> rackProductForm.copy(
                        productId = value,
                        validationErrors = rackProductForm.validationErrors - "productId",
                    )
                    "productName" -> rackProductForm.copy(productName = value)
                    "quantity" -> rackProductForm.copy(
                        quantity = value,
                        validationErrors = rackProductForm.validationErrors - "quantity",
                    )
                    "binLocation" -> rackProductForm.copy(binLocation = value)
                    else -> rackProductForm
                },
            )
        }
    }

    private suspend fun onSaveRackProduct() {
        val form = currentState.rackProductForm
        val errors = mutableMapOf<String, String>()
        if (form.rackId.isBlank()) errors["rackId"] = "Rack required"
        if (form.productId.isBlank()) errors["productId"] = "Product required"
        val qty = form.quantity.toDoubleOrNull()
        if (qty == null || qty < 0) errors["quantity"] = "Quantity must be 0 or more"

        if (errors.isNotEmpty()) {
            updateState { copy(rackProductForm = rackProductForm.copy(validationErrors = errors)) }
            return
        }

        updateState { copy(isLoading = true) }
        when (val result = saveRackProductUseCase(
            rackId = form.rackId,
            productId = form.productId,
            quantity = qty!!,
            binLocation = form.binLocation.takeIf { it.isNotBlank() },
        )) {
            is Result.Success -> {
                val msg = if (form.isEditing) "Bin location updated" else "Product assigned to rack"
                updateState {
                    copy(isLoading = false, successMessage = msg, rackProductForm = RackProductFormState())
                }
                sendEffect(WarehouseEffect.ShowSuccess(msg))
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(WarehouseEffect.ShowError(result.exception.message ?: "Save failed"))
            }
            is Result.Loading -> {}
        }
    }

    private suspend fun onConfirmDeleteRackProduct() {
        val entry = currentState.showDeleteRackProductConfirm ?: return
        updateState { copy(showDeleteRackProductConfirm = null, isLoading = true) }
        when (val result = deleteRackProductUseCase(entry.rackId, entry.productId)) {
            is Result.Success -> {
                updateState { copy(isLoading = false) }
                sendEffect(WarehouseEffect.ShowSuccess("Product removed from rack"))
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(WarehouseEffect.ShowError(result.exception.message ?: "Delete failed"))
            }
            is Result.Loading -> {}
        }
    }

    // ── Warehouse Stock / C1.2 ────────────────────────────────────────────

    private fun onLoadWarehouseStock(warehouseId: String) {
        getWarehouseStockUseCase(warehouseId)
            .onEach { stock ->
                val query = currentState.stockSearchQuery
                val filtered = if (query.isBlank()) stock
                else stock.filter { entry ->
                    entry.productName?.contains(query, ignoreCase = true) == true ||
                        entry.productSku?.contains(query, ignoreCase = true) == true ||
                        entry.productBarcode?.contains(query, ignoreCase = true) == true
                }
                updateState { copy(warehouseStock = filtered) }
            }
            .launchIn(viewModelScope)

        getLowStockByWarehouseUseCase(warehouseId)
            .onEach { lowStock -> updateState { copy(lowStockItems = lowStock) } }
            .launchIn(viewModelScope)
    }

    private fun onSearchStock(query: String) {
        updateState { copy(stockSearchQuery = query) }
    }

    private fun onOpenStockEntry(warehouseId: String, productId: String?) {
        if (productId == null) {
            updateState {
                copy(stockEntryForm = StockEntryFormState(warehouseId = warehouseId))
            }
            sendEffect(WarehouseEffect.NavigateToStockEntry(warehouseId, null))
            return
        }
        val existing = currentState.warehouseStock.find { it.productId == productId }
        updateState {
            copy(
                stockEntryForm = StockEntryFormState(
                    warehouseId = warehouseId,
                    productId = productId,
                    productName = existing?.productName ?: "",
                    quantity = existing?.quantity?.let {
                        if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
                    } ?: "0",
                    minQuantity = existing?.minQuantity?.let {
                        if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
                    } ?: "0",
                    isEditing = existing != null,
                ),
            )
        }
        sendEffect(WarehouseEffect.NavigateToStockEntry(warehouseId, productId))
    }

    private fun onUpdateStockField(field: String, value: String) {
        updateState {
            copy(
                stockEntryForm = when (field) {
                    "productId" -> stockEntryForm.copy(
                        productId = value,
                        validationErrors = stockEntryForm.validationErrors - "productId",
                    )
                    "productName" -> stockEntryForm.copy(productName = value)
                    "quantity" -> stockEntryForm.copy(
                        quantity = value,
                        validationErrors = stockEntryForm.validationErrors - "quantity",
                    )
                    "minQuantity" -> stockEntryForm.copy(
                        minQuantity = value,
                        validationErrors = stockEntryForm.validationErrors - "minQuantity",
                    )
                    else -> stockEntryForm
                },
            )
        }
    }

    private suspend fun onSaveStockEntry() {
        val form = currentState.stockEntryForm
        val errors = mutableMapOf<String, String>()
        if (form.warehouseId.isBlank()) errors["warehouseId"] = "Warehouse required"
        if (form.productId.isBlank()) errors["productId"] = "Product required"
        val qty = form.quantity.toDoubleOrNull()
        if (qty == null || qty < 0) errors["quantity"] = "Quantity must be 0 or more"
        val minQty = form.minQuantity.toDoubleOrNull() ?: 0.0
        if (minQty < 0) errors["minQuantity"] = "Min quantity must be 0 or more"

        if (errors.isNotEmpty()) {
            updateState { copy(stockEntryForm = stockEntryForm.copy(validationErrors = errors)) }
            return
        }

        updateState { copy(isLoading = true) }
        when (val result = setWarehouseStockUseCase(
            warehouseId = form.warehouseId,
            productId = form.productId,
            quantity = qty!!,
            minQuantity = minQty,
        )) {
            is Result.Success -> {
                val msg = if (form.isEditing) "Stock level updated" else "Stock level set"
                updateState {
                    copy(isLoading = false, successMessage = msg, stockEntryForm = StockEntryFormState())
                }
                sendEffect(WarehouseEffect.ShowSuccess(msg))
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(WarehouseEffect.ShowError(result.exception.message ?: "Save failed"))
            }
            is Result.Loading -> {}
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun validateTransferForm(form: TransferFormState): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (form.sourceWarehouseId.isBlank()) errors["sourceWarehouseId"] = "Source warehouse required"
        if (form.destWarehouseId.isBlank()) errors["destWarehouseId"] = "Destination warehouse required"
        if (form.sourceWarehouseId == form.destWarehouseId && form.sourceWarehouseId.isNotBlank()) {
            errors["destWarehouseId"] = "Source and destination must differ"
        }
        if (form.productId.isBlank()) errors["productId"] = "Product required"
        val qty = form.quantity.toDoubleOrNull()
        if (qty == null || qty <= 0) errors["quantity"] = "Quantity must be positive"
        return errors
    }
}
