package com.zyntasolutions.zyntapos.domain.model

/**
 * Defines a per-product auto-replenishment rule for a warehouse (C1.5).
 *
 * When [autoApprove] is true, the [AutoReplenishmentUseCase] will automatically
 * create a PENDING [PurchaseOrder] when stock falls at or below [reorderPoint].
 *
 * @property id           Unique identifier (UUID v4).
 * @property productId    FK to the product this rule governs.
 * @property warehouseId  FK to the warehouse whose stock level is monitored.
 * @property supplierId   FK to the default supplier used when generating a PO.
 * @property reorderPoint Stock quantity threshold that triggers replenishment.
 * @property reorderQty   Quantity to order when the threshold is reached.
 * @property autoApprove  When true, a PO is created automatically without human intervention.
 * @property isActive     When false the rule is paused and no auto-POs are created.
 * @property createdAt    Epoch millis of rule creation.
 * @property updatedAt    Epoch millis of last modification.
 */
data class ReplenishmentRule(
    val id: String,
    val productId: String,
    val warehouseId: String,
    val supplierId: String,
    val reorderPoint: Double,
    val reorderQty: Double,
    val autoApprove: Boolean = false,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    // Denormalized display fields (populated by joins)
    val productName: String? = null,
    val warehouseName: String? = null,
    val supplierName: String? = null,
)
