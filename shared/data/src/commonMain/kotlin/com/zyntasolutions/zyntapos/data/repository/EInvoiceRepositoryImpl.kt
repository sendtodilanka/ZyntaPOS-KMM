package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.data.remote.ird.IrdApiClient
import com.zyntasolutions.zyntapos.data.remote.ird.IrdApiResponse
import com.zyntasolutions.zyntapos.data.remote.ird.IrdInvoicePayload
import com.zyntasolutions.zyntapos.db.E_invoices
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.EInvoice
import com.zyntasolutions.zyntapos.domain.model.EInvoiceLineItem
import com.zyntasolutions.zyntapos.domain.model.EInvoiceStatus
import com.zyntasolutions.zyntapos.domain.model.IrdSubmissionResult
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.model.TaxBreakdownItem
import com.zyntasolutions.zyntapos.domain.repository.EInvoiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SQLDelight-backed [EInvoiceRepository] implementation (Sprint 18 + Phase 3).
 *
 * Replaces the in-memory [MutableStateFlow] stub introduced in Sprint 5-7.
 * `lineItems` and `taxBreakdown` are stored as JSON TEXT columns because e-invoices
 * are effectively immutable after submission — no need for separate join tables.
 *
 * JSON serialisation uses local `@Serializable` DTOs to keep the domain layer free
 * of framework annotations (architecture guard: domain layer is pure Kotlin).
 *
 * ## IRD API integration (Phase 3)
 * [submitToIrd] calls the actual Sri Lanka IRD API via [IrdApiClient] with mTLS.
 * On success, the invoice status is updated to [EInvoiceStatus.ACCEPTED] with the
 * IRD-assigned reference number. On failure, the status is updated to [EInvoiceStatus.REJECTED].
 */
class EInvoiceRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
    private val irdApiClient: IrdApiClient,
) : EInvoiceRepository {

    private val q get() = db.e_invoicesQueries

    // ── Read ────────────────────────────────────────────────────────────────────

    override fun getAll(storeId: String): Flow<List<EInvoice>> =
        q.selectAll(storeId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override fun getByStatus(storeId: String, status: EInvoiceStatus): Flow<List<EInvoice>> =
        q.selectByStatus(storeId, status.name)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun getById(id: String): Result<EInvoice> = withContext(Dispatchers.IO) {
        runCatching {
            q.selectById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("E-Invoice not found: $id"))
        }.fold(
            onSuccess = { Result.Success(toDomain(it)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getByOrderId(orderId: String): Result<EInvoice?> =
        withContext(Dispatchers.IO) {
            runCatching {
                q.selectByOrderId(orderId).executeAsOneOrNull()?.let(::toDomain)
            }.fold(
                onSuccess = { Result.Success(it) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
            )
        }

    // ── Write ───────────────────────────────────────────────────────────────────

    override suspend fun insert(invoice: EInvoice): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.transaction {
                q.insertInvoice(
                    id = invoice.id,
                    order_id = invoice.orderId,
                    store_id = invoice.storeId,
                    invoice_number = invoice.invoiceNumber,
                    invoice_date = invoice.invoiceDate,
                    customer_name = invoice.customerName,
                    customer_tax_id = invoice.customerTaxId,
                    line_items_json = invoice.lineItems.toLineItemsJson(),
                    subtotal = invoice.subtotal,
                    tax_breakdown_json = invoice.taxBreakdown.toTaxBreakdownJson(),
                    total_tax = invoice.totalTax,
                    total = invoice.total,
                    currency = invoice.currency,
                    status = invoice.status.name,
                    ird_reference_number = invoice.irdReferenceNumber,
                    submitted_at = invoice.submittedAt,
                    accepted_at = invoice.acceptedAt,
                    rejection_reason = invoice.rejectionReason,
                    created_at = invoice.createdAt,
                    updated_at = invoice.updatedAt,
                    sync_status = "PENDING",
                )
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.E_INVOICE,
                    invoice.id,
                    SyncOperation.Operation.INSERT,
                )
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override suspend fun submitToIrd(id: String, submittedAt: Long): Result<IrdSubmissionResult> =
        withContext(Dispatchers.IO) {
            val invoice = q.selectById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("E-Invoice not found: $id"))

            if (invoice.status != EInvoiceStatus.DRAFT.name) {
                return@withContext Result.Error(
                    DatabaseException("E-Invoice $id is already ${invoice.status} — cannot submit")
                )
            }

            val now = Clock.System.now().toEpochMilliseconds()

            // Mark as SUBMITTED before calling the API (optimistic update)
            runCatching {
                q.updateStatus(
                    status               = EInvoiceStatus.SUBMITTED.name,
                    ird_reference_number = null,
                    submitted_at         = submittedAt,
                    accepted_at          = null,
                    rejection_reason     = null,
                    updated_at           = now,
                    id                   = id,
                )
            }.onFailure { t ->
                return@withContext Result.Error(DatabaseException("DB update failed: ${t.message}", cause = t))
            }

            // Call the IRD API
            val payload = IrdInvoicePayload(
                invoiceId       = invoice.id,
                invoiceNumber   = invoice.invoice_number,
                invoiceDate     = invoice.invoice_date,
                customerName    = invoice.customer_name,
                customerTaxId   = invoice.customer_tax_id,
                lineItemsJson   = invoice.line_items_json,
                taxBreakdownJson= invoice.tax_breakdown_json,
                subtotal        = invoice.subtotal,
                totalTax        = invoice.total_tax,
                total           = invoice.total,
                currency        = invoice.currency,
                storeId         = invoice.store_id,
            )

            // Retry on transient network exceptions (not on API-level rejections which return IrdApiResponse)
            val maxAttempts = 3
            val baseDelayMs = 1_000L
            var lastNetworkError: Throwable? = null
            var apiResponse: IrdApiResponse? = null
            for (attempt in 0 until maxAttempts) {
                val attempt_result = runCatching { irdApiClient.submitInvoice(payload) }
                if (attempt_result.isSuccess) {
                    apiResponse = attempt_result.getOrThrow()
                    break
                }
                lastNetworkError = attempt_result.exceptionOrNull()
                if (attempt < maxAttempts - 1) {
                    delay(baseDelayMs * (1L shl attempt)) // 1s → 2s → 4s
                }
            }
            val finalApiResponse = apiResponse ?: IrdApiResponse(
                success      = false,
                errorMessage = lastNetworkError?.message ?: "Network error after $maxAttempts attempts",
            )

            // Update DB status based on IRD API response
            val finalStatus = if (finalApiResponse.success) EInvoiceStatus.ACCEPTED else EInvoiceStatus.REJECTED
            runCatching {
                q.updateStatus(
                    status               = finalStatus.name,
                    ird_reference_number = finalApiResponse.referenceNumber,
                    submitted_at         = submittedAt,
                    accepted_at          = if (finalApiResponse.success) now else null,
                    rejection_reason     = if (!finalApiResponse.success)
                        "[${finalApiResponse.errorCode}] ${finalApiResponse.errorMessage}" else null,
                    updated_at           = now,
                    id                   = id,
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.E_INVOICE, id, SyncOperation.Operation.UPDATE)
            }

            Result.Success(
                IrdSubmissionResult(
                    success         = finalApiResponse.success,
                    referenceNumber = finalApiResponse.referenceNumber,
                    errorCode       = finalApiResponse.errorCode,
                    errorMessage    = finalApiResponse.errorMessage,
                    submittedAt     = submittedAt,
                )
            )
        }

    override suspend fun updateStatus(
        id: String,
        status: EInvoiceStatus,
        irdReferenceNumber: String?,
        rejectionReason: String?,
        updatedAt: Long,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val current = q.selectById(id).executeAsOneOrNull()
        runCatching {
            q.updateStatus(
                status = status.name,
                ird_reference_number = irdReferenceNumber,
                submitted_at = current?.submitted_at,
                accepted_at = if (status == EInvoiceStatus.ACCEPTED) updatedAt else current?.accepted_at,
                rejection_reason = rejectionReason,
                updated_at = updatedAt,
                id = id,
            )
            syncEnqueuer.enqueue(SyncOperation.EntityType.E_INVOICE, id, SyncOperation.Operation.UPDATE)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "updateStatus failed", cause = t)) },
        )
    }

    override suspend fun cancel(id: String, updatedAt: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            val invoice = q.selectById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("E-Invoice not found: $id"))

            val status = runCatching { EInvoiceStatus.valueOf(invoice.status) }
                .getOrDefault(EInvoiceStatus.DRAFT)
            if (status != EInvoiceStatus.DRAFT && status != EInvoiceStatus.SUBMITTED) {
                return@withContext Result.Error(
                    DatabaseException("Cannot cancel e-invoice with status $status")
                )
            }

            runCatching {
                q.cancelInvoice(updated_at = updatedAt, id = id)
                syncEnqueuer.enqueue(SyncOperation.EntityType.E_INVOICE, id, SyncOperation.Operation.UPDATE)
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Cancel failed", cause = t)) },
            )
        }

    // ── JSON serialisation helpers ────────────────────────────────────────────

    @Serializable
    private data class LineItemDto(
        val productId: String,
        val description: String,
        val quantity: Double,
        val unitPrice: Double,
        val taxRate: Double,
        val taxAmount: Double,
        val lineTotal: Double,
    )

    @Serializable
    private data class TaxBreakdownDto(
        val taxRate: Double,
        val taxablAmount: Double,
        val taxAmount: Double,
    )

    private fun List<EInvoiceLineItem>.toLineItemsJson(): String =
        jsonSerializer.encodeToString(map {
            LineItemDto(it.productId, it.description, it.quantity, it.unitPrice,
                it.taxRate, it.taxAmount, it.lineTotal)
        })

    private fun String.parseLineItems(): List<EInvoiceLineItem> =
        runCatching {
            jsonSerializer.decodeFromString<List<LineItemDto>>(this).map {
                EInvoiceLineItem(it.productId, it.description, it.quantity, it.unitPrice,
                    it.taxRate, it.taxAmount, it.lineTotal)
            }
        }.getOrDefault(emptyList())

    private fun List<TaxBreakdownItem>.toTaxBreakdownJson(): String =
        jsonSerializer.encodeToString(map {
            TaxBreakdownDto(it.taxRate, it.taxablAmount, it.taxAmount)
        })

    private fun String.parseTaxBreakdown(): List<TaxBreakdownItem> =
        runCatching {
            jsonSerializer.decodeFromString<List<TaxBreakdownDto>>(this).map {
                TaxBreakdownItem(it.taxRate, it.taxablAmount, it.taxAmount)
            }
        }.getOrDefault(emptyList())

    // ── Row → domain mapping ──────────────────────────────────────────────────

    private fun toDomain(row: E_invoices) = EInvoice(
        id = row.id,
        orderId = row.order_id,
        storeId = row.store_id,
        invoiceNumber = row.invoice_number,
        invoiceDate = row.invoice_date,
        customerName = row.customer_name,
        customerTaxId = row.customer_tax_id,
        lineItems = row.line_items_json.parseLineItems(),
        subtotal = row.subtotal,
        taxBreakdown = row.tax_breakdown_json.parseTaxBreakdown(),
        totalTax = row.total_tax,
        total = row.total,
        currency = row.currency,
        status = runCatching { EInvoiceStatus.valueOf(row.status) }.getOrDefault(EInvoiceStatus.DRAFT),
        irdReferenceNumber = row.ird_reference_number,
        submittedAt = row.submitted_at,
        acceptedAt = row.accepted_at,
        rejectionReason = row.rejection_reason,
        createdAt = row.created_at,
        updatedAt = row.updated_at,
    )

    private companion object {
        val jsonSerializer = Json { ignoreUnknownKeys = true }
    }
}
