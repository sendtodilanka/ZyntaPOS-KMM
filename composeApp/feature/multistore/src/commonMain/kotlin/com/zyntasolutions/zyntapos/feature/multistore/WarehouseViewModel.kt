package com.zyntasolutions.zyntapos.feature.multistore

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.StockTransfer
import com.zyntasolutions.zyntapos.domain.model.Warehouse
import com.zyntasolutions.zyntapos.domain.repository.WarehouseRepository
import com.zyntasolutions.zyntapos.domain.usecase.multistore.CommitStockTransferUseCase
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.Clock

/**
 * ViewModel for the Multi-store / Warehouse feature — Sprints 14–16.
 *
 * Manages:
 * - Warehouse list for the current store
 * - Warehouse create/edit form
 * - Stock transfer list per warehouse
 * - New stock transfer creation
 * - Transfer commit / cancel lifecycle
 *
 * @param warehouseRepository  Warehouse and stock transfer CRUD.
 * @param commitTransferUseCase Validates and commits a pending transfer.
 * @param currentStoreId        Resolved from the active auth session at DI time.
 * @param currentUserId         Resolved from the active auth session at DI time.
 */
class WarehouseViewModel(
    private val warehouseRepository: WarehouseRepository,
    private val commitTransferUseCase: CommitStockTransferUseCase,
    private val currentStoreId: String,
    private val currentUserId: String,
) : BaseViewModel<WarehouseState, WarehouseIntent, WarehouseEffect>(WarehouseState()) {

    init {
        observeWarehouses()
    }

    private fun observeWarehouses() {
        warehouseRepository.getByStore(currentStoreId)
            .onEach { warehouses -> updateState { copy(warehouses = warehouses, isLoading = false) } }
            .launchIn(viewModelScope)
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

            is WarehouseIntent.DismissMessage -> updateState { copy(error = null, successMessage = null) }
        }
    }

    // ── Warehouse CRUD ─────────────────────────────────────────────────────

    private suspend fun onSelectWarehouse(warehouseId: String?) {
        if (warehouseId == null) {
            updateState {
                copy(
                    selectedWarehouse = null,
                    warehouseForm = WarehouseFormState(isEditing = false),
                )
            }
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
        val result = if (form.isEditing) {
            warehouseRepository.update(warehouse)
        } else {
            warehouseRepository.insert(warehouse)
        }
        when (result) {
            is Result.Success -> {
                val msg = if (form.isEditing) "Warehouse updated" else "Warehouse created"
                updateState {
                    copy(isLoading = false, successMessage = msg, warehouseForm = WarehouseFormState())
                }
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
        updateState {
            copy(
                transferForm = TransferFormState(
                    sourceWarehouseId = sourceWarehouseId ?: "",
                ),
            )
        }
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
                updateState {
                    copy(isLoading = false, transferForm = TransferFormState(), successMessage = "Transfer created")
                }
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
