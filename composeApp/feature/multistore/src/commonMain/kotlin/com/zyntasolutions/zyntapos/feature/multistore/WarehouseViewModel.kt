package com.zyntasolutions.zyntapos.feature.multistore

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.StockTransfer
import com.zyntasolutions.zyntapos.domain.model.Warehouse
import com.zyntasolutions.zyntapos.domain.model.WarehouseRack
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.WarehouseRepository
import com.zyntasolutions.zyntapos.domain.usecase.multistore.CommitStockTransferUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.DeleteWarehouseRackUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.GetWarehouseRacksUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.SaveWarehouseRackUseCase
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * ViewModel for the Multi-store / Warehouse feature — Sprints 14–16 + 18.
 *
 * Manages warehouse list, warehouse CRUD, stock transfers, and warehouse
 * rack management (Sprint 18).
 *
 * @param warehouseRepository      Warehouse and stock transfer CRUD.
 * @param commitTransferUseCase    Validates and commits a pending transfer.
 * @param getWarehouseRacksUseCase Reactive rack list for a warehouse.
 * @param saveWarehouseRackUseCase Insert or update a rack record.
 * @param deleteWarehouseRackUseCase Soft-delete a rack record.
 * @param authRepository           Provides the active auth session for resolving storeId and userId.
 */
class WarehouseViewModel(
    private val warehouseRepository: WarehouseRepository,
    private val commitTransferUseCase: CommitStockTransferUseCase,
    private val getWarehouseRacksUseCase: GetWarehouseRacksUseCase,
    private val saveWarehouseRackUseCase: SaveWarehouseRackUseCase,
    private val deleteWarehouseRackUseCase: DeleteWarehouseRackUseCase,
    private val authRepository: AuthRepository,
) : BaseViewModel<WarehouseState, WarehouseIntent, WarehouseEffect>(WarehouseState()) {

    private var currentStoreId: String = "default"
    private var currentUserId: String = "unknown"

    init {
        viewModelScope.launch {
            val session = authRepository.getSession().first()
            currentStoreId = session?.storeId ?: "default"
            currentUserId = session?.id ?: "unknown"
            observeWarehouses()
            loadPendingTransfers()
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
            is WarehouseIntent.SubmitTransfer -> onSubmitTransfer()

            is WarehouseIntent.CommitTransfer -> onCommitTransfer(intent.transferId)
            is WarehouseIntent.CancelTransfer -> onCancelTransfer(intent.transferId)

            is WarehouseIntent.LoadRacks -> onLoadRacks(intent.warehouseId)
            is WarehouseIntent.SelectRack -> onSelectRack(intent.rackId, intent.warehouseId)
            is WarehouseIntent.UpdateRackField -> onUpdateRackField(intent.field, intent.value)
            is WarehouseIntent.SaveRack -> onSaveRack()
            is WarehouseIntent.RequestDeleteRack -> updateState { copy(showDeleteRackConfirm = intent.rack) }
            is WarehouseIntent.ConfirmDeleteRack -> onConfirmDeleteRack()
            is WarehouseIntent.CancelDeleteRack -> updateState { copy(showDeleteRackConfirm = null) }

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
