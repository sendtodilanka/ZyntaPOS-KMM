package com.zyntasolutions.zyntapos.debug.model

/**
 * Named seed profiles selectable from the Seeds tab.
 *
 * Each profile maps to a [com.zyntasolutions.zyntapos.seed.SeedDataSet].
 * Additional profiles (Retail, Restaurant) reuse [DefaultSeedDataSet] for Phase 1;
 * custom datasets can be added per-profile in subsequent sprints.
 */
enum class SeedProfile(val displayName: String, val description: String) {
    Demo(
        displayName = "Demo Store",
        description = "Generic 8 categories, 25 products, 15 customers",
    ),
    Retail(
        displayName = "Retail",
        description = "Retail-oriented product mix with apparel and electronics",
    ),
    Restaurant(
        displayName = "Restaurant",
        description = "Food & beverage items with combo products",
    ),
}
