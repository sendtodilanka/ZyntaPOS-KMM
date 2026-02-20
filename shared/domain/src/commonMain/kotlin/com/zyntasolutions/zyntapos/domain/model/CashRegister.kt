package com.zyntasolutions.zyntapos.domain.model

/**
 * A physical or virtual cash register terminal.
 *
 * Each register belongs to one store and can have at most one active
 * [RegisterSession] at a time.
 *
 * @property id Unique identifier (UUID v4).
 * @property name User-visible terminal name (e.g., "Counter 1", "Drive-Through").
 * @property storeId FK to the store this register belongs to.
 * @property currentSessionId FK to the currently active [RegisterSession]. Null when closed.
 * @property isActive If false the register is decommissioned and cannot be opened.
 */
data class CashRegister(
    val id: String,
    val name: String,
    val storeId: String,
    val currentSessionId: String? = null,
    val isActive: Boolean = true,
)
