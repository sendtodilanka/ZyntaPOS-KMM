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
)
