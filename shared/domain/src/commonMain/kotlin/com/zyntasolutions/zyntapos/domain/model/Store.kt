package com.zyntasolutions.zyntapos.domain.model

import kotlinx.datetime.Instant

/**
 * Domain model representing a store / branch location.
 *
 * Plain name per ADR-002 — no *Entity suffix.
 */
data class Store(
    val id: String,
    val name: String,
    val address: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val currency: String = "LKR",
    val timezone: String = "Asia/Colombo",
    val isActive: Boolean = true,
    val isHeadquarters: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
    /**
     * Maximum discount percentage allowed at this store (C2.4).
     * Null = no limit (any discount up to 100% is allowed).
     * E.g., 20.0 means max 20% discount at this store.
     */
    val maxDiscountPercent: Double? = null,
    /**
     * Maximum fixed discount amount allowed at this store (C2.4).
     * Null = no limit. Applied independently from [maxDiscountPercent].
     */
    val maxDiscountAmount: Double? = null,
    /**
     * Policy controlling where returned stock goes during a cross-store
     * refund (C4.2). Defaults to [ReturnStockPolicy.RETURN_TO_CURRENT_STORE].
     */
    val returnStockPolicy: ReturnStockPolicy = ReturnStockPolicy.RETURN_TO_CURRENT_STORE,
)
