package com.zyntasolutions.zyntapos.navigation

import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.ZyntaFeature
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [RbacNavFilter.forRoleAndFeatures] — verifies that nav items are correctly
 * filtered by both RBAC role permissions and the active feature set.
 */
class RbacNavFilterFeatureTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** All known [ZyntaFeature] values — simulates a fully-unlocked Enterprise store. */
    private val allFeatures: Set<ZyntaFeature> = ZyntaFeature.entries.toSet()

    /** Standard-edition features only (STANDARD edition items). */
    private val standardFeatures: Set<ZyntaFeature> =
        ZyntaFeature.entries.filter { it.edition == com.zyntasolutions.zyntapos.domain.model.ZyntaEdition.STANDARD }.toSet()

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `ADMIN with all features enabled sees all nav items`() {
        val items = RbacNavFilter.forRoleAndFeatures(
            role = Role.ADMIN,
            enabledFeatures = allFeatures,
        )

        // ADMIN has every permission, and all features are enabled — nothing should be filtered out.
        val allItemsForAdmin = RbacNavFilter.forRole(Role.ADMIN)
        assertTrue(
            actual = items.size == allItemsForAdmin.size,
            message = "Expected ${allItemsForAdmin.size} items for ADMIN+allFeatures, got ${items.size}",
        )
        assertTrue(
            actual = items.containsAll(allItemsForAdmin),
            message = "ADMIN with all features should see all nav items visible to ADMIN by role.",
        )
    }

    @Test
    fun `CASHIER with STAFF_HR disabled does not see Staff nav item`() {
        // CASHIER already lacks MANAGE_STAFF permission, so Staff is filtered by RBAC.
        // Disabling STAFF_HR additionally ensures the feature gate also blocks it.
        val featuresWithoutStaffHr = allFeatures - ZyntaFeature.STAFF_HR

        val items = RbacNavFilter.forRoleAndFeatures(
            role = Role.CASHIER,
            enabledFeatures = featuresWithoutStaffHr,
        )

        val staffItem = items.firstOrNull { it.label == "Staff" }
        assertFalse(
            actual = staffItem != null,
            message = "Staff nav item should NOT be visible to CASHIER when STAFF_HR feature is disabled.",
        )
    }

    @Test
    fun `CASHIER with STAFF_HR enabled but lacking MANAGE_STAFF permission still does not see Staff nav item`() {
        // Even if the STAFF_HR feature is enabled, CASHIER has no MANAGE_STAFF permission.
        // The RBAC filter (forRole) should exclude it before feature-gating is applied.
        val items = RbacNavFilter.forRoleAndFeatures(
            role = Role.CASHIER,
            enabledFeatures = allFeatures,
        )

        val staffItem = items.firstOrNull { it.label == "Staff" }
        assertFalse(
            actual = staffItem != null,
            message = "Staff nav item should NOT be visible to CASHIER even when STAFF_HR feature is enabled, " +
                "because CASHIER lacks the MANAGE_STAFF permission.",
        )
    }

    @Test
    fun `empty enabledFeatures set shows only items with null featureGate`() {
        // With an empty feature set, only items with featureGate == null should be visible.
        // Based on AllNavItems: Notifications has featureGate = null.
        val items = RbacNavFilter.forRoleAndFeatures(
            role = Role.ADMIN,
            enabledFeatures = emptySet(),
        )

        // Every returned item must either have no feature gate.
        assertTrue(
            actual = items.all { it.featureGate == null },
            message = "With an empty feature set, only items with featureGate == null should survive.",
        )

        // Notifications is the only null-gated item in AllNavItems, so it must appear.
        val notificationsItem = items.firstOrNull { it.label == "Notifications" }
        assertTrue(
            actual = notificationsItem != null,
            message = "Notifications item (featureGate = null) must appear for ADMIN even with empty feature set.",
        )
    }

    @Test
    fun `STORE_MANAGER with only standard features sees only STANDARD-edition items`() {
        val items = RbacNavFilter.forRoleAndFeatures(
            role = Role.STORE_MANAGER,
            enabledFeatures = standardFeatures,
        )

        // No item returned should require a PREMIUM or ENTERPRISE feature gate.
        val nonStandardItems = items.filter { item ->
            item.featureGate != null &&
                item.featureGate!!.edition != com.zyntasolutions.zyntapos.domain.model.ZyntaEdition.STANDARD
        }
        assertTrue(
            actual = nonStandardItems.isEmpty(),
            message = "STORE_MANAGER with only STANDARD features should not see PREMIUM/ENTERPRISE-gated items. " +
                "Found: ${nonStandardItems.map { it.label }}",
        )
    }
}
