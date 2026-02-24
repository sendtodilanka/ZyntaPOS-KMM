package com.zyntasolutions.zyntapos.seed

/**
 * Default seed dataset for UI/UX testing.
 *
 * Provides a realistic set of categories, suppliers, products, and customers
 * that exercises the full breadth of the dashboard and POS screens:
 * - 8 product categories
 * - 5 suppliers
 * - 25 products spread across categories (some with low stock for alerts)
 * - 15 customers
 *
 * All IDs use a predictable `seed-*` prefix to distinguish them from
 * production records.
 */
object DefaultSeedDataSet {

    fun build(): SeedDataSet = SeedDataSet(
        categories = categories,
        suppliers = suppliers,
        products = products,
        customers = customers,
    )

    // ── Categories ─────────────────────────────────────────────────────────────

    private val categories = listOf(
        SeedCategory("seed-cat-001", "Coffee & Espresso", displayOrder = 1),
        SeedCategory("seed-cat-002", "Tea & Herbal", displayOrder = 2),
        SeedCategory("seed-cat-003", "Pastries & Bakery", displayOrder = 3),
        SeedCategory("seed-cat-004", "Sandwiches & Wraps", displayOrder = 4),
        SeedCategory("seed-cat-005", "Cold Drinks", displayOrder = 5),
        SeedCategory("seed-cat-006", "Merchandise", displayOrder = 6),
        SeedCategory("seed-cat-007", "Desserts", displayOrder = 7),
        SeedCategory("seed-cat-008", "Breakfast", displayOrder = 8),
    )

    // ── Suppliers ──────────────────────────────────────────────────────────────

    private val suppliers = listOf(
        SeedSupplier("seed-sup-001", "Arabica Premium Roasters", contactPerson = "Carlos Mendez", email = "carlos@arabica.com", phone = "+1-555-0101"),
        SeedSupplier("seed-sup-002", "Golden Grain Bakery Supply", contactPerson = "Emma Wilson", email = "emma@goldengrain.com", phone = "+1-555-0102"),
        SeedSupplier("seed-sup-003", "Fresh Valley Farms", contactPerson = "Tom Green", email = "tom@freshvalley.com", phone = "+1-555-0103"),
        SeedSupplier("seed-sup-004", "Global Tea Imports", contactPerson = "Li Wei", email = "li.wei@gti.com", phone = "+1-555-0104"),
        SeedSupplier("seed-sup-005", "Zyntara Merchandise Co.", contactPerson = "Sarah Park", email = "sarah@zyntara-merch.com", phone = "+1-555-0105"),
    )

    // ── Products ───────────────────────────────────────────────────────────────

    private val products = listOf(
        // Coffee & Espresso (cat-001)
        SeedProduct("seed-prod-001", "Espresso Single Shot", "SKU-ESP-01", "4901100001001", "seed-cat-001", price = 2.50, costPrice = 0.40, stockQty = 999.0, minStockQty = 0.0),
        SeedProduct("seed-prod-002", "Espresso Double Shot", "SKU-ESP-02", "4901100001002", "seed-cat-001", price = 3.50, costPrice = 0.70, stockQty = 999.0, minStockQty = 0.0),
        SeedProduct("seed-prod-003", "Flat White", "SKU-FW-01", "4901100001003", "seed-cat-001", price = 4.20, costPrice = 0.80, stockQty = 999.0, minStockQty = 0.0),
        SeedProduct("seed-prod-004", "Cappuccino", "SKU-CAP-01", "4901100001004", "seed-cat-001", price = 4.00, costPrice = 0.75, stockQty = 999.0, minStockQty = 0.0),
        SeedProduct("seed-prod-005", "Caramel Macchiato", "SKU-MAC-01", "4901100001005", "seed-cat-001", price = 5.20, costPrice = 1.10, stockQty = 999.0, minStockQty = 0.0),

        // Tea & Herbal (cat-002)
        SeedProduct("seed-prod-006", "English Breakfast Tea", "SKU-TEA-01", "4901100002001", "seed-cat-002", price = 3.00, costPrice = 0.30, stockQty = 80.0, minStockQty = 20.0),
        SeedProduct("seed-prod-007", "Chamomile Herbal Tea", "SKU-TEA-02", "4901100002002", "seed-cat-002", price = 3.20, costPrice = 0.35, stockQty = 3.0, minStockQty = 10.0),  // LOW STOCK

        // Pastries & Bakery (cat-003)
        SeedProduct("seed-prod-008", "Butter Croissant", "SKU-BAK-01", "4901100003001", "seed-cat-003", price = 3.50, costPrice = 0.90, stockQty = 24.0, minStockQty = 5.0),
        SeedProduct("seed-prod-009", "Blueberry Muffin", "SKU-BAK-02", "4901100003002", "seed-cat-003", price = 3.80, costPrice = 0.95, stockQty = 2.0, minStockQty = 8.0),   // LOW STOCK
        SeedProduct("seed-prod-010", "Almond Danish", "SKU-BAK-03", "4901100003003", "seed-cat-003", price = 4.20, costPrice = 1.10, stockQty = 18.0, minStockQty = 5.0),

        // Sandwiches & Wraps (cat-004)
        SeedProduct("seed-prod-011", "BLT Sandwich", "SKU-SAN-01", "4901100004001", "seed-cat-004", price = 7.50, costPrice = 2.80, stockQty = 15.0, minStockQty = 5.0),
        SeedProduct("seed-prod-012", "Veggie Wrap", "SKU-SAN-02", "4901100004002", "seed-cat-004", price = 6.90, costPrice = 2.20, stockQty = 4.0, minStockQty = 5.0),        // LOW STOCK
        SeedProduct("seed-prod-013", "Club Sandwich", "SKU-SAN-03", "4901100004003", "seed-cat-004", price = 8.50, costPrice = 3.10, stockQty = 10.0, minStockQty = 3.0),

        // Cold Drinks (cat-005)
        SeedProduct("seed-prod-014", "Sparkling Water 500ml", "SKU-DRK-01", "4901100005001", "seed-cat-005", price = 2.00, costPrice = 0.50, stockQty = 48.0, minStockQty = 12.0),
        SeedProduct("seed-prod-015", "Fresh Orange Juice", "SKU-DRK-02", "4901100005002", "seed-cat-005", price = 4.50, costPrice = 1.20, stockQty = 20.0, minStockQty = 8.0),
        SeedProduct("seed-prod-016", "Iced Coffee Frappe", "SKU-DRK-03", "4901100005003", "seed-cat-005", price = 5.90, costPrice = 1.40, stockQty = 999.0, minStockQty = 0.0),

        // Merchandise (cat-006)
        SeedProduct("seed-prod-017", "ZyntaPOS Branded Mug 12oz", "SKU-MRC-01", "4901100006001", "seed-cat-006", price = 14.99, costPrice = 4.50, stockQty = 30.0, minStockQty = 5.0),
        SeedProduct("seed-prod-018", "Reusable Coffee Cup 16oz", "SKU-MRC-02", "4901100006002", "seed-cat-006", price = 19.99, costPrice = 6.00, stockQty = 0.0, minStockQty = 5.0),  // OUT OF STOCK

        // Desserts (cat-007)
        SeedProduct("seed-prod-019", "Chocolate Brownie", "SKU-DES-01", "4901100007001", "seed-cat-007", price = 4.50, costPrice = 1.20, stockQty = 12.0, minStockQty = 5.0),
        SeedProduct("seed-prod-020", "New York Cheesecake Slice", "SKU-DES-02", "4901100007002", "seed-cat-007", price = 6.90, costPrice = 2.00, stockQty = 8.0, minStockQty = 3.0),
        SeedProduct("seed-prod-021", "Macaron Assortment 3pc", "SKU-DES-03", "4901100007003", "seed-cat-007", price = 5.50, costPrice = 1.80, stockQty = 1.0, minStockQty = 4.0),   // LOW STOCK

        // Breakfast (cat-008)
        SeedProduct("seed-prod-022", "Full English Breakfast", "SKU-BRK-01", "4901100008001", "seed-cat-008", price = 12.90, costPrice = 4.50, stockQty = 20.0, minStockQty = 5.0),
        SeedProduct("seed-prod-023", "Avocado Toast", "SKU-BRK-02", "4901100008002", "seed-cat-008", price = 9.50, costPrice = 3.00, stockQty = 15.0, minStockQty = 5.0),
        SeedProduct("seed-prod-024", "Granola Bowl", "SKU-BRK-03", "4901100008003", "seed-cat-008", price = 7.50, costPrice = 2.10, stockQty = 3.0, minStockQty = 5.0),            // LOW STOCK
        SeedProduct("seed-prod-025", "Pancake Stack", "SKU-BRK-04", "4901100008004", "seed-cat-008", price = 10.50, costPrice = 2.80, stockQty = 10.0, minStockQty = 3.0),
    )

    // ── Customers ──────────────────────────────────────────────────────────────

    private val customers = listOf(
        SeedCustomer("seed-cust-001", "Alice Johnson", phone = "+1-555-1001", email = "alice.j@email.com", loyaltyPoints = 250),
        SeedCustomer("seed-cust-002", "Bob Martinez", phone = "+1-555-1002", email = "bob.m@email.com", loyaltyPoints = 150),
        SeedCustomer("seed-cust-003", "Carol Chen", phone = "+1-555-1003", email = "carol.c@email.com", loyaltyPoints = 500),
        SeedCustomer("seed-cust-004", "David Kim", phone = "+1-555-1004", email = "david.k@email.com", loyaltyPoints = 75),
        SeedCustomer("seed-cust-005", "Eva Garcia", phone = "+1-555-1005", email = "eva.g@email.com", loyaltyPoints = 320),
        SeedCustomer("seed-cust-006", "Frank Lee", phone = "+1-555-1006", email = "frank.l@email.com", loyaltyPoints = 0),
        SeedCustomer("seed-cust-007", "Grace Williams", phone = "+1-555-1007", email = "grace.w@email.com", loyaltyPoints = 180),
        SeedCustomer("seed-cust-008", "Henry Brown", phone = "+1-555-1008", loyaltyPoints = 0),
        SeedCustomer("seed-cust-009", "Isabel Taylor", phone = "+1-555-1009", email = "isabel.t@email.com", loyaltyPoints = 90),
        SeedCustomer("seed-cust-010", "James Wilson", phone = "+1-555-1010", email = "james.w@email.com", loyaltyPoints = 440),
        SeedCustomer("seed-cust-011", "Karen Anderson", phone = "+1-555-1011", email = "karen.a@email.com", loyaltyPoints = 220),
        SeedCustomer("seed-cust-012", "Liam Thomas", phone = "+1-555-1012", loyaltyPoints = 0),
        SeedCustomer("seed-cust-013", "Mia Jackson", phone = "+1-555-1013", email = "mia.j@email.com", loyaltyPoints = 380),
        SeedCustomer("seed-cust-014", "Noah White", phone = "+1-555-1014", email = "noah.w@email.com", loyaltyPoints = 110),
        SeedCustomer("seed-cust-015", "Olivia Harris", phone = "+1-555-1015", email = "olivia.h@email.com", loyaltyPoints = 670),
    )
}
