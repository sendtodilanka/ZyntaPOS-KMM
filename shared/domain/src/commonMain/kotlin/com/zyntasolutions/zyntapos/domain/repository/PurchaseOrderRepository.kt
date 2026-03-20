package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.PurchaseOrder
import com.zyntasolutions.zyntapos.domain.model.PurchaseOrderItem
import kotlinx.coroutines.flow.Flow

/**
 * Contract for purchase order management (C1.3 / C1.5).
 *
 * POs are created when inventory needs replenishment from a supplier.
 * Items can be partially or fully received over time.
 */
interface PurchaseOrderRepository {

    /** Emits all purchase orders, most recent first. */
    fun getAll(): Flow<List<PurchaseOrder>>

    /** Returns a single PO by [id] including its line items. */
    suspend fun getById(id: String): Result<PurchaseOrder>

    /** Returns all POs created within [startDate]..[endDate] (epoch millis). */
    suspend fun getByDateRange(startDate: Long, endDate: Long): Result<List<PurchaseOrder>>

    /** Returns all POs for a given supplier. */
    suspend fun getBySupplierId(supplierId: String): Result<List<PurchaseOrder>>

    /** Returns all POs with a given [status]. */
    suspend fun getByStatus(status: PurchaseOrder.Status): Result<List<PurchaseOrder>>

    /**
     * Creates a new PO with its line items.
     * Status is set to PENDING automatically.
     */
    suspend fun create(order: PurchaseOrder): Result<Unit>

    /**
     * Marks a PO as RECEIVED or PARTIAL.
     * Updates [PurchaseOrderItem.quantityReceived] for each item.
     * When all items are fully received, status becomes RECEIVED; otherwise PARTIAL.
     */
    suspend fun receiveItems(
        purchaseOrderId: String,
        receivedItems: Map<String, Double>,  // itemId → quantityReceived
        receivedBy: String,
    ): Result<Unit>

    /** Cancels a PO in PENDING or PARTIAL status. */
    suspend fun cancel(purchaseOrderId: String): Result<Unit>
}
