package com.zyntasolutions.zyntapos.domain.model

/**
 * A physical rack or shelf location within a warehouse.
 *
 * @property id Unique identifier (UUID v4).
 * @property warehouseId FK to [Warehouse].
 * @property name Human-readable rack label (e.g., 'A1', 'B3', 'Cold-Storage-01').
 * @property description Optional description of the rack's purpose.
 * @property capacity Max units storable. Null = unlimited.
 * @property createdAt Epoch millis of record creation.
 * @property updatedAt Epoch millis of last update.
 */
data class WarehouseRack(
    val id: String,
    val warehouseId: String,
    val name: String,
    val description: String? = null,
    val capacity: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
