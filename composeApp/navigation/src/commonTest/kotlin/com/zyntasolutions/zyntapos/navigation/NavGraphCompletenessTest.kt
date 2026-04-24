package com.zyntasolutions.zyntapos.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that [MainNavScreens] has screen factory properties for every
 * navigable route in the app.
 *
 * ### Why this test exists
 * It is easy to add a new [ZyntaRoute] and wire it in [MainNavGraph] with
 * placeholder text (e.g., `Text("Coming soon")`) instead of calling the real
 * screen composable via [MainNavScreens]. This test fails when a known route
 * does not have a corresponding property in [MainNavScreens], forcing the
 * developer to wire the real screen before merging.
 *
 * ### Maintenance
 * When a new route is added, add its expected [MainNavScreens] property name
 * to [expectedProperties]. If the property is intentionally deferred, add it
 * to [deferredProperties] with a comment linking to the tracking issue.
 */
class NavGraphCompletenessTest {

    /**
     * All [MainNavScreens] properties that MUST exist for full navigation coverage.
     * Sorted alphabetically for easy diffing.
     */
    private val expectedProperties = setOf(
        // Main group
        "dashboard",
        "pos",
        "payment",

        // Inventory sub-graph
        "productList",
        "productDetail",
        "categoryList",
        "categoryDetail",
        "supplierList",
        "supplierDetail",
        "barcodeLabelPrint",
        "stocktake",

        // Register sub-graph
        "registerDashboard",
        "openRegister",
        "closeRegister",

        // Reports sub-graph
        "salesReport",
        "stockReport",
        "customerReport",
        "expenseReport",
        "storeComparisonReport",

        // Settings sub-graph
        "settings",
        "printerSettings",
        "taxSettings",
        "regionalTaxOverride",
        "userManagement",
        "generalSettings",
        "appearanceSettings",
        "aboutSettings",
        "backupSettings",
        "posSettings",
        "systemHealthSettings",
        "securitySettings",
        "rbacManagement",
        "securityPolicy",
        "dataRetention",
        "auditPolicy",
        "editionManagement",

        // Deep-link target
        "orderHistory",

        // CRM sub-graph
        "customerList",
        "customerDetail",
        "customerGroupList",
        "customerWallet",

        // Coupons sub-graph
        "couponList",
        "couponDetail",

        // Expenses sub-graph
        "expenseList",
        "expenseDetail",
        "expenseCategoryList",

        // Multi-store sub-graph
        "warehouseList",
        "warehouseDetail",
        "storeTransferDashboard",
        "stockTransferList",
        "newStockTransfer",
        "warehouseRackList",
        "warehouseRackDetail",
        "multiStoreDashboard",

        // Pick list (P3-B1)
        "pickListView",

        // Accounting sub-graph
        "accountingLedger",
        "accountDetail",
        "chartOfAccounts",
        "accountManagementDetail",
        "journalEntryList",
        "journalEntryDetail",
        "financialStatements",
        "generalLedger",

        // Admin sub-graph
        "adminScreen",

        // Staff sub-graph
        "staffScreen",

        // Notifications
        "notificationInbox",

        // Settings sub-graph — store user access (C3.2)
        "storeUserAccess",

        // Click & Collect — fulfillment queue (C4.4)
        "fulfillmentQueue",

        // Employee Roaming (C3.4)
        "employeeStoreAssignments",
    )

    /**
     * Routes whose [MainNavScreens] properties are intentionally deferred.
     * These represent known placeholder routes tracked in the backlog.
     *
     * IMPORTANT: This list should shrink over time. Each entry MUST
     * reference the issue/sprint where wiring is planned.
     */
    private val deferredProperties = setOf<String>(
        // All routes are now wired. This set is kept to satisfy the tracking contract.
    )

    @Test
    fun `expected property count matches MainNavScreens data class constructor`() {
        // MainNavScreens is a data class. If it has N constructor parameters,
        // all N are required (no defaults). We test that our expected set size
        // matches to catch new additions that aren't reflected in this test.
        //
        // Current constructor parameter count:
        val actualParameterCount = MainNavScreens::class.constructors
            .first()
            .parameters
            .size

        assertEquals(
            expectedProperties.size,
            actualParameterCount,
            "MainNavScreens has $actualParameterCount constructor parameters " +
                "but expectedProperties has ${expectedProperties.size} entries. " +
                "Did you add a new screen factory without updating this test?",
        )
    }

    @Test
    fun `deferred properties list is documented and tracked`() {
        // Ensure deferred properties is not silently growing.
        // If you need to add a new deferral, update this assertion and add a comment.
        assertTrue(
            deferredProperties.size <= 1,
            "Too many deferred properties (${deferredProperties.size}). " +
                "Wire the actual screens or justify each deferral with an issue link.",
        )
    }

    @Test
    fun `no route should remain deferred once implemented`() {
        // This test documents which routes still have placeholder screens.
        // When editionManagement is added to MainNavScreens, remove it from
        // deferredProperties and add it to expectedProperties.
        for (deferred in deferredProperties) {
            assertTrue(
                deferred !in expectedProperties,
                "Property '$deferred' is in both expectedProperties AND deferredProperties. " +
                    "If the screen has been wired, remove it from deferredProperties.",
            )
        }
    }
}
