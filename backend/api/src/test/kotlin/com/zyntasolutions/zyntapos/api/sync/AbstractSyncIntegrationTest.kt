package com.zyntasolutions.zyntapos.api.sync

import com.zyntasolutions.zyntapos.api.test.AbstractIntegrationTest
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach

/**
 * Base class for sync pipeline integration tests.
 *
 * Extends [AbstractIntegrationTest] (PostgreSQL + Flyway) and additionally
 * truncates all sync-related and normalized-entity tables before each test
 * so that the inherited [cleanTables] (which only covers core tables) is
 * supplemented for the full sync schema.
 */
abstract class AbstractSyncIntegrationTest : AbstractIntegrationTest() {

    @BeforeEach
    fun cleanSyncTables() {
        transaction(database) {
            exec(
                """
                TRUNCATE TABLE
                    sync_operations,
                    sync_cursors,
                    sync_conflict_log,
                    sync_dead_letters,
                    entity_snapshots,
                    categories,
                    customers,
                    suppliers,
                    orders,
                    order_items,
                    audit_entries,
                    stock_adjustments,
                    cash_registers,
                    register_sessions,
                    cash_movements,
                    tax_groups,
                    units_of_measure,
                    payment_splits,
                    coupons,
                    expenses,
                    settings,
                    employees,
                    expense_categories,
                    coupon_usages,
                    promotions,
                    customer_groups
                CASCADE
                """.trimIndent()
            )
        }
    }
}
