package com.zyntasolutions.zyntapos.domain.model

/**
 * A hierarchical product grouping used to organise the POS catalogue.
 *
 * Categories support one level of nesting (parent → child) for Phase 1.
 * The UI renders them as a filterable chip row on the POS screen and as
 * a tree-view in the Inventory management section.
 *
 * @property id Unique identifier (UUID v4).
 * @property name Display name (e.g., "Beverages", "Hot Drinks").
 * @property parentId FK to the parent [Category]. Null for root categories.
 * @property imageUrl Optional icon/banner image URL loaded via Coil.
 * @property displayOrder Integer used to sort categories within the same level.
 *                        Lower values appear first. Default 0.
 * @property isActive If false, the category and its products are hidden from POS.
 */
data class Category(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val imageUrl: String? = null,
    val displayOrder: Int = 0,
    val isActive: Boolean = true,
)
