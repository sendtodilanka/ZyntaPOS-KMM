package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.EInvoice
import com.zyntasolutions.zyntapos.domain.model.EInvoiceStatus
import com.zyntasolutions.zyntapos.domain.model.IrdSubmissionResult
import com.zyntasolutions.zyntapos.domain.repository.EInvoiceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * [EInvoiceRepository] implementation.
 *
 * Phase 3 Sprint 5-7: In-memory store backed by [MutableStateFlow]. The IRD e-invoicing
 * schema (`e_invoices` table, IRD API client) is planned for Sprint 18 with a dedicated
 * SQLDelight migration. This stub provides the reactive contract needed by the domain
 * layer and feature UI while the full infrastructure is built.
 *
 * All data survives the current session but is not persisted across app restarts.
 */
class EInvoiceRepositoryImpl : EInvoiceRepository {

    private val _invoices = MutableStateFlow<List<EInvoice>>(emptyList())

    override fun getAll(storeId: String): Flow<List<EInvoice>> =
        _invoices.asStateFlow().map { list ->
            list.filter { it.storeId == storeId }.sortedByDescending { it.createdAt }
        }

    override fun getByStatus(storeId: String, status: EInvoiceStatus): Flow<List<EInvoice>> =
        _invoices.asStateFlow().map { list ->
            list.filter { it.storeId == storeId && it.status == status }
                .sortedByDescending { it.createdAt }
        }

    override suspend fun getById(id: String): Result<EInvoice> {
        val invoice = _invoices.value.find { it.id == id }
            ?: return Result.Error(DatabaseException("E-Invoice not found: $id"))
        return Result.Success(invoice)
    }

    override suspend fun getByOrderId(orderId: String): Result<EInvoice?> =
        Result.Success(_invoices.value.find { it.orderId == orderId })

    override suspend fun insert(invoice: EInvoice): Result<Unit> {
        return runCatching {
            _invoices.value = _invoices.value + invoice
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override suspend fun submitToIrd(id: String, submittedAt: Long): Result<IrdSubmissionResult> {
        val invoice = _invoices.value.find { it.id == id }
            ?: return Result.Error(DatabaseException("E-Invoice not found: $id"))

        if (invoice.status != EInvoiceStatus.DRAFT) {
            return Result.Error(
                DatabaseException("E-Invoice $id is already ${invoice.status} — cannot submit")
            )
        }

        // Full IRD API integration implemented in Sprint 18.
        // For now, transition to SUBMITTED with a placeholder reference.
        val updated = invoice.copy(
            status = EInvoiceStatus.SUBMITTED,
            submittedAt = submittedAt,
        )
        _invoices.value = _invoices.value.map { if (it.id == id) updated else it }

        return Result.Success(
            IrdSubmissionResult(
                success = true,
                referenceNumber = null,
                errorCode = null,
                errorMessage = "IRD API integration pending (Sprint 18)",
                submittedAt = submittedAt,
            )
        )
    }

    override suspend fun updateStatus(
        id: String,
        status: EInvoiceStatus,
        irdReferenceNumber: String?,
        rejectionReason: String?,
        updatedAt: Long,
    ): Result<Unit> {
        return runCatching {
            _invoices.value = _invoices.value.map { invoice ->
                if (invoice.id == id) {
                    invoice.copy(
                        status = status,
                        irdReferenceNumber = irdReferenceNumber ?: invoice.irdReferenceNumber,
                        rejectionReason = rejectionReason ?: invoice.rejectionReason,
                        updatedAt = updatedAt,
                    )
                } else {
                    invoice
                }
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "updateStatus failed", cause = t)) },
        )
    }

    override suspend fun cancel(id: String, updatedAt: Long): Result<Unit> {
        val invoice = _invoices.value.find { it.id == id }
            ?: return Result.Error(DatabaseException("E-Invoice not found: $id"))

        if (invoice.status != EInvoiceStatus.DRAFT && invoice.status != EInvoiceStatus.SUBMITTED) {
            return Result.Error(
                DatabaseException("Cannot cancel e-invoice with status ${invoice.status}")
            )
        }

        return runCatching {
            _invoices.value = _invoices.value.map { inv ->
                if (inv.id == id) inv.copy(status = EInvoiceStatus.CANCELLED, updatedAt = updatedAt) else inv
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "cancel failed", cause = t)) },
        )
    }
}
