package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.EInvoice
import com.zyntasolutions.zyntapos.domain.model.EInvoiceLineItem
import com.zyntasolutions.zyntapos.domain.model.EInvoiceStatus
import com.zyntasolutions.zyntapos.domain.model.IrdSubmissionResult
import com.zyntasolutions.zyntapos.domain.model.TaxBreakdownItem
import com.zyntasolutions.zyntapos.domain.repository.EInvoiceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

// ─────────────────────────────────────────────────────────────────────────────
// EInvoice Fixtures
// ─────────────────────────────────────────────────────────────────────────────

fun buildEInvoiceLineItem(
    productId: String = "prod-01",
    description: String = "Test Product",
    quantity: Double = 1.0,
    unitPrice: Double = 100.0,
    taxRate: Double = 0.0,
    taxAmount: Double = 0.0,
    lineTotal: Double = 100.0,
) = EInvoiceLineItem(
    productId = productId,
    description = description,
    quantity = quantity,
    unitPrice = unitPrice,
    taxRate = taxRate,
    taxAmount = taxAmount,
    lineTotal = lineTotal,
)

fun buildEInvoice(
    id: String = "inv-01",
    orderId: String = "order-01",
    storeId: String = "store-01",
    invoiceNumber: String = "INV-0001",
    invoiceDate: String = "2026-01-01",
    customerName: String = "Test Customer",
    lineItems: List<EInvoiceLineItem> = listOf(buildEInvoiceLineItem()),
    subtotal: Double = 100.0,
    totalTax: Double = 0.0,
    total: Double = 100.0,
    status: EInvoiceStatus = EInvoiceStatus.DRAFT,
) = EInvoice(
    id = id,
    orderId = orderId,
    storeId = storeId,
    invoiceNumber = invoiceNumber,
    invoiceDate = invoiceDate,
    customerName = customerName,
    lineItems = lineItems,
    subtotal = subtotal,
    taxBreakdown = emptyList(),
    totalTax = totalTax,
    total = total,
    status = status,
    createdAt = Clock.System.now().toEpochMilliseconds(),
    updatedAt = Clock.System.now().toEpochMilliseconds(),
)

// ─────────────────────────────────────────────────────────────────────────────
// Fake EInvoiceRepository
// ─────────────────────────────────────────────────────────────────────────────

/**
 * In-memory fake for [EInvoiceRepository].
 */
class FakeEInvoiceRepository : EInvoiceRepository {
    val invoices = mutableListOf<EInvoice>()
    var shouldFail = false
    var lastCancelledId: String? = null
    var lastCancelledAt: Long? = null

    private val _invoicesFlow = MutableStateFlow<List<EInvoice>>(emptyList())

    override fun getAll(storeId: String): Flow<List<EInvoice>> =
        _invoicesFlow.map { list -> list.filter { it.storeId == storeId } }

    override fun getByStatus(storeId: String, status: EInvoiceStatus): Flow<List<EInvoice>> =
        _invoicesFlow.map { list -> list.filter { it.storeId == storeId && it.status == status } }

    override suspend fun getById(id: String): Result<EInvoice> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        return invoices.find { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("EInvoice not found: $id"))
    }

    override suspend fun getByOrderId(orderId: String): Result<EInvoice?> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        return Result.Success(invoices.find { it.orderId == orderId })
    }

    override suspend fun insert(invoice: EInvoice): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        invoices.add(invoice)
        _invoicesFlow.value = invoices.toList()
        return Result.Success(Unit)
    }

    override suspend fun submitToIrd(id: String, submittedAt: Long): Result<IrdSubmissionResult> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        val idx = invoices.indexOfFirst { it.id == id }
        if (idx < 0) return Result.Error(DatabaseException("EInvoice not found: $id"))
        invoices[idx] = invoices[idx].copy(status = EInvoiceStatus.SUBMITTED, submittedAt = submittedAt)
        _invoicesFlow.value = invoices.toList()
        return Result.Success(
            IrdSubmissionResult(success = true, referenceNumber = "IRD-REF-001", submittedAt = submittedAt),
        )
    }

    override suspend fun updateStatus(
        id: String,
        status: EInvoiceStatus,
        irdReferenceNumber: String?,
        rejectionReason: String?,
        updatedAt: Long,
    ): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        val idx = invoices.indexOfFirst { it.id == id }
        if (idx < 0) return Result.Error(DatabaseException("EInvoice not found: $id"))
        invoices[idx] = invoices[idx].copy(
            status = status,
            irdReferenceNumber = irdReferenceNumber,
            rejectionReason = rejectionReason,
            updatedAt = updatedAt,
        )
        _invoicesFlow.value = invoices.toList()
        return Result.Success(Unit)
    }

    override suspend fun cancel(id: String, updatedAt: Long): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        val idx = invoices.indexOfFirst { it.id == id }
        if (idx < 0) return Result.Error(DatabaseException("EInvoice not found: $id"))
        invoices[idx] = invoices[idx].copy(status = EInvoiceStatus.CANCELLED, updatedAt = updatedAt)
        _invoicesFlow.value = invoices.toList()
        lastCancelledId = id
        lastCancelledAt = updatedAt
        return Result.Success(Unit)
    }
}
