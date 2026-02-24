package com.zyntasolutions.zyntapos.seed

import kotlinx.serialization.Serializable

/**
 * Seed data model for a [com.zyntasolutions.zyntapos.domain.model.Category].
 */
@Serializable
data class SeedCategory(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val displayOrder: Int = 0,
)

/**
 * Seed data model for a [com.zyntasolutions.zyntapos.domain.model.Supplier].
 */
@Serializable
data class SeedSupplier(
    val id: String,
    val name: String,
    val contactPerson: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val notes: String? = null,
)

/**
 * Seed data model for a [Product].
 */
@Serializable
data class SeedProduct(
    val id: String,
    val name: String,
    val sku: String? = null,
    val barcode: String? = null,
    val categoryId: String,
    val unitId: String = "unit-pcs",
    val price: Double,
    val costPrice: Double = 0.0,
    val taxGroupId: String? = null,
    val stockQty: Double = 0.0,
    val minStockQty: Double = 5.0,
    val description: String? = null,
)

/**
 * Seed data model for a [com.zyntasolutions.zyntapos.domain.model.Customer].
 *
 * [phone] is required because it is the unique lookup key in [Customer].
 */
@Serializable
data class SeedCustomer(
    val id: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val loyaltyPoints: Int = 0,
)

/**
 * Complete seed dataset loaded from JSON files.
 */
@Serializable
data class SeedDataSet(
    val categories: List<SeedCategory> = emptyList(),
    val suppliers: List<SeedSupplier> = emptyList(),
    val products: List<SeedProduct> = emptyList(),
    val customers: List<SeedCustomer> = emptyList(),
)
