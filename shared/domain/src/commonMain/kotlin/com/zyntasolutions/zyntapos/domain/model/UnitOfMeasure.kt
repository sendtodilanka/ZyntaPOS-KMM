package com.zyntasolutions.zyntapos.domain.model

/**
 * Describes how a [Product]'s quantity is measured and sold.
 *
 * Supports unit conversion for products sold in multiples of a base unit
 * (e.g., 1 case = 12 bottles).
 *
 * @property id Unique identifier (UUID v4).
 * @property name Full unit name (e.g., "Kilogram", "Bottle", "Case").
 * @property abbreviation Short display symbol (e.g., "kg", "btl", "cs").
 * @property isBaseUnit True if this is the smallest indivisible unit in its group.
 *                      Each conversion group must have exactly one base unit.
 * @property conversionRate Multiplier relative to the group's base unit.
 *                          For base units this is 1.0.
 *                          Example: if base = "bottle" (1.0), then "case" = 12.0.
 */
data class UnitOfMeasure(
    val id: String,
    val name: String,
    val abbreviation: String,
    val isBaseUnit: Boolean = false,
    val conversionRate: Double = 1.0,
)
