package com.zyntasolutions.zyntapos.security

import com.zyntasolutions.zyntapos.domain.model.CustomRole
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.security.rbac.RbacEngine
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// RbacEngine Tests
// ─────────────────────────────────────────────────────────────────────────────

class RbacEngineTest {

    private val rbac = RbacEngine()

    // ── ADMIN ─────────────────────────────────────────────────────────────────

    @Test
    fun `ADMIN has all permissions`() {
        val adminPermissions = rbac.getPermissions(Role.ADMIN)
        assertEquals(Permission.entries.toSet(), adminPermissions)
    }

    @Test
    fun `ADMIN user passes hasPermission for all permissions`() {
        val admin = buildUser(Role.ADMIN)
        Permission.entries.forEach { perm ->
            assertTrue(rbac.hasPermission(admin, perm), "ADMIN missing $perm")
        }
    }

    // ── CASHIER ───────────────────────────────────────────────────────────────

    @Test
    fun `CASHIER can process sales`() {
        assertTrue(rbac.hasPermission(Role.CASHIER, Permission.PROCESS_SALE))
    }

    @Test
    fun `CASHIER cannot manage users`() {
        assertFalse(rbac.hasPermission(Role.CASHIER, Permission.MANAGE_USERS))
    }

    @Test
    fun `CASHIER cannot view audit log`() {
        assertFalse(rbac.hasPermission(Role.CASHIER, Permission.VIEW_AUDIT_LOG))
    }

    @Test
    fun `CASHIER cannot export reports`() {
        assertFalse(rbac.hasPermission(Role.CASHIER, Permission.EXPORT_REPORTS))
    }

    // ── STORE_MANAGER ─────────────────────────────────────────────────────────

    @Test
    fun `STORE_MANAGER can manage products`() {
        assertTrue(rbac.hasPermission(Role.STORE_MANAGER, Permission.MANAGE_PRODUCTS))
    }

    @Test
    fun `STORE_MANAGER can view audit log`() {
        assertTrue(rbac.hasPermission(Role.STORE_MANAGER, Permission.VIEW_AUDIT_LOG))
    }

    @Test
    fun `STORE_MANAGER cannot manage backup (ADMIN only)`() {
        assertFalse(rbac.hasPermission(Role.STORE_MANAGER, Permission.MANAGE_BACKUP))
    }

    // ── ACCOUNTANT ────────────────────────────────────────────────────────────

    @Test
    fun `ACCOUNTANT can view and export reports`() {
        assertTrue(rbac.hasPermission(Role.ACCOUNTANT, Permission.VIEW_REPORTS))
        assertTrue(rbac.hasPermission(Role.ACCOUNTANT, Permission.EXPORT_REPORTS))
    }

    @Test
    fun `ACCOUNTANT cannot process sales`() {
        assertFalse(rbac.hasPermission(Role.ACCOUNTANT, Permission.PROCESS_SALE))
    }

    // ── STOCK_MANAGER ─────────────────────────────────────────────────────────

    @Test
    fun `STOCK_MANAGER can adjust stock`() {
        assertTrue(rbac.hasPermission(Role.STOCK_MANAGER, Permission.ADJUST_STOCK))
    }

    @Test
    fun `STOCK_MANAGER cannot void orders`() {
        assertFalse(rbac.hasPermission(Role.STOCK_MANAGER, Permission.VOID_ORDER))
    }

    // ── Full matrix: no role has MORE permissions than ADMIN ──────────────────

    @Test
    fun `no role has more permissions than ADMIN`() {
        val adminSet = rbac.getPermissions(Role.ADMIN)
        Role.entries.filter { it != Role.ADMIN }.forEach { role ->
            val rolePerms = rbac.getPermissions(role)
            assertTrue(
                adminSet.containsAll(rolePerms),
                "$role has permissions not present in ADMIN: ${rolePerms - adminSet}",
            )
        }
    }

    @Test
    fun `getDeniedPermissions returns complement of granted permissions`() {
        Role.entries.forEach { role ->
            val granted = rbac.getPermissions(role)
            val denied = rbac.getDeniedPermissions(role)
            assertEquals(Permission.entries.toSet(), granted + denied)
            assertTrue((granted intersect denied).isEmpty(), "$role: granted ∩ denied must be empty")
        }
    }

    // ── Dynamic RBAC overloads ─────────────────────────────────────────────────

    @Test
    fun `hasPermission with builtIn override uses override set`() {
        val engine = RbacEngine()
        val user = buildUser(Role.CASHIER)
        val override = mapOf(Role.CASHIER to setOf(Permission.VIEW_REPORTS))
        // Override grants only VIEW_REPORTS → must be true
        assertTrue(engine.hasPermission(user, Permission.VIEW_REPORTS, override, emptyList()))
        // CASHIER normally has PROCESS_SALE, but the override removes it → must be false
        assertFalse(engine.hasPermission(user, Permission.PROCESS_SALE, override, emptyList()))
    }

    @Test
    fun `hasPermission with customRole uses customRole permissions`() {
        val engine = RbacEngine()
        val customRole = CustomRole(
            id = "cr1",
            name = "Kitchen",
            permissions = setOf(Permission.MANAGE_PRODUCTS),
            createdAt = Instant.fromEpochSeconds(0),
            updatedAt = Instant.fromEpochSeconds(0),
        )
        val user = buildUserWithCustomRole(Role.CASHIER, customRoleId = "cr1")
        // Custom role grants MANAGE_PRODUCTS → must be true
        assertTrue(engine.hasPermission(user, Permission.MANAGE_PRODUCTS, emptyMap(), listOf(customRole)))
        // Custom role does NOT grant PROCESS_SALE → must be false (even though CASHIER normally would)
        assertFalse(engine.hasPermission(user, Permission.PROCESS_SALE, emptyMap(), listOf(customRole)))
    }

    @Test
    fun `hasPermission with no override falls back to static defaults`() {
        val engine = RbacEngine()
        val user = buildUser(Role.CASHIER)
        val cashierDefaults = Permission.rolePermissions[Role.CASHIER] ?: emptySet()
        // Every default CASHIER permission must be granted when no overrides are provided
        cashierDefaults.forEach { perm ->
            assertTrue(
                engine.hasPermission(user, perm, emptyMap(), emptyList()),
                "CASHIER should have $perm with no overrides",
            )
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildUser(role: Role) = User(
        id = "test-user-id",
        storeId = "store-1",
        name = "Test User",
        email = "test@example.com",
        role = role,
        isActive = true,
        pinHash = null,
        createdAt = Instant.fromEpochSeconds(0),
        updatedAt = Instant.fromEpochSeconds(0),
    )

    private fun buildUserWithCustomRole(role: Role, customRoleId: String) = User(
        id = "test-user-id",
        storeId = "store-1",
        name = "Test User",
        email = "test@example.com",
        role = role,
        isActive = true,
        pinHash = null,
        customRoleId = customRoleId,
        createdAt = Instant.fromEpochSeconds(0),
        updatedAt = Instant.fromEpochSeconds(0),
    )
}
