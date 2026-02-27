package com.zyntasolutions.zyntapos.domain.usecase.auth

import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [CheckPermissionUseCase].
 *
 * Covers:
 * - Null session → all checks return false
 * - UserId mismatch vs active session → returns false
 * - ADMIN role wildcard grant (all permissions)
 * - CASHIER role: has POS permissions, lacks management permissions
 * - ACCOUNTANT role: has financial permissions, lacks POS operations
 * - STOCK_MANAGER role: has inventory permissions, lacks POS operations
 * - STORE_MANAGER role: has all operational permissions
 * - Role overload: invoke(Role, Permission) without needing a session
 * - Unknown/missing role in rolePermissions map → returns false
 */
class CheckPermissionUseCaseTest {

    // ─── No active session ────────────────────────────────────────────────────

    @Test
    fun `returns false when session snapshot is null`() {
        val sessionFlow = MutableStateFlow<com.zyntasolutions.zyntapos.domain.model.User?>(null)
        val useCase = CheckPermissionUseCase(sessionFlow)
        // Do not call updateSession — snapshot remains null

        assertFalse(useCase(userId = "u1", permission = Permission.PROCESS_SALE))
        assertFalse(useCase(userId = "u1", permission = Permission.MANAGE_USERS))
        assertFalse(useCase(userId = "u1", permission = Permission.ADMIN_ACCESS))
    }

    @Test
    fun `returns false when updateSession called with null`() {
        val sessionFlow = MutableStateFlow<com.zyntasolutions.zyntapos.domain.model.User?>(null)
        val useCase = CheckPermissionUseCase(sessionFlow)
        useCase.updateSession(null)

        assertFalse(useCase(userId = "any-user", permission = Permission.PROCESS_SALE))
    }

    @Test
    fun `returns false after session is explicitly cleared`() {
        val user = buildUser(id = "cashier-01", role = Role.CASHIER)
        val sessionFlow = MutableStateFlow<com.zyntasolutions.zyntapos.domain.model.User?>(null)
        val useCase = CheckPermissionUseCase(sessionFlow)

        // Establish session, confirm permission
        useCase.updateSession(user)
        assertTrue(useCase(userId = "cashier-01", permission = Permission.PROCESS_SALE))

        // Clear session, confirm permission is revoked
        useCase.updateSession(null)
        assertFalse(useCase(userId = "cashier-01", permission = Permission.PROCESS_SALE))
    }

    // ─── UserId mismatch ──────────────────────────────────────────────────────

    @Test
    fun `returns false when userId does not match session user id`() {
        val user = buildUser(id = "cashier-01", role = Role.ADMIN)
        val sessionFlow = MutableStateFlow<com.zyntasolutions.zyntapos.domain.model.User?>(null)
        val useCase = CheckPermissionUseCase(sessionFlow)
        useCase.updateSession(user)

        // Even ADMIN session: wrong userId → denied
        assertFalse(useCase(userId = "other-user-99", permission = Permission.MANAGE_USERS))
        assertFalse(useCase(userId = "", permission = Permission.PROCESS_SALE))
    }

    @Test
    fun `returns true when userId exactly matches session user id`() {
        val user = buildUser(id = "mgr-01", role = Role.STORE_MANAGER)
        val sessionFlow = MutableStateFlow<com.zyntasolutions.zyntapos.domain.model.User?>(null)
        val useCase = CheckPermissionUseCase(sessionFlow)
        useCase.updateSession(user)

        assertTrue(useCase(userId = "mgr-01", permission = Permission.MANAGE_PRODUCTS))
    }

    // ─── ADMIN role ───────────────────────────────────────────────────────────

    @Test
    fun `ADMIN role has all permissions via role overload`() {
        val sessionFlow = MutableStateFlow<com.zyntasolutions.zyntapos.domain.model.User?>(null)
        val useCase = CheckPermissionUseCase(sessionFlow)

        Permission.entries.forEach { permission ->
            assertTrue(
                useCase(Role.ADMIN, permission),
                "ADMIN should have permission: $permission",
            )
        }
    }

    @Test
    fun `ADMIN user session grants all permissions via userId overload`() {
        val user = buildUser(id = "admin-01", role = Role.ADMIN)
        val sessionFlow = MutableStateFlow<com.zyntasolutions.zyntapos.domain.model.User?>(null)
        val useCase = CheckPermissionUseCase(sessionFlow)
        useCase.updateSession(user)

        Permission.entries.forEach { permission ->
            assertTrue(
                useCase(userId = "admin-01", permission = permission),
                "ADMIN user should have permission: $permission",
            )
        }
    }

    // ─── CASHIER role ─────────────────────────────────────────────────────────

    @Test
    fun `CASHIER role has POS operations permissions`() {
        val sessionFlow = MutableStateFlow<com.zyntasolutions.zyntapos.domain.model.User?>(null)
        val useCase = CheckPermissionUseCase(sessionFlow)

        assertTrue(useCase(Role.CASHIER, Permission.PROCESS_SALE))
        assertTrue(useCase(Role.CASHIER, Permission.VOID_ORDER))
        assertTrue(useCase(Role.CASHIER, Permission.APPLY_DISCOUNT))
        assertTrue(useCase(Role.CASHIER, Permission.HOLD_ORDER))
        assertTrue(useCase(Role.CASHIER, Permission.PROCESS_REFUND))
        assertTrue(useCase(Role.CASHIER, Permission.OPEN_REGISTER))
        assertTrue(useCase(Role.CASHIER, Permission.CLOSE_REGISTER))
        assertTrue(useCase(Role.CASHIER, Permission.RECORD_CASH_MOVEMENT))
        assertTrue(useCase(Role.CASHIER, Permission.MANAGE_CUSTOMERS))
    }

    @Test
    fun `CASHIER role does not have management permissions`() {
        val sessionFlow = MutableStateFlow<com.zyntasolutions.zyntapos.domain.model.User?>(null)
        val useCase = CheckPermissionUseCase(sessionFlow)

        assertFalse(useCase(Role.CASHIER, Permission.MANAGE_USERS))
        assertFalse(useCase(Role.CASHIER, Permission.MANAGE_SETTINGS))
        assertFalse(useCase(Role.CASHIER, Permission.MANAGE_PRODUCTS))
        assertFalse(useCase(Role.CASHIER, Permission.MANAGE_CATEGORIES))
        assertFalse(useCase(Role.CASHIER, Permission.VIEW_REPORTS))
        assertFalse(useCase(Role.CASHIER, Permission.ADMIN_ACCESS))
        assertFalse(useCase(Role.CASHIER, Permission.MANAGE_ACCOUNTING))
    }

    @Test
    fun `CASHIER user session - correct userId grants PROCESS_SALE`() {
        val user = buildUser(id = "cashier-99", role = Role.CASHIER)
        val sessionFlow = MutableStateFlow<com.zyntasolutions.zyntapos.domain.model.User?>(null)
        val useCase = CheckPermissionUseCase(sessionFlow)
        useCase.updateSession(user)

        assertTrue(useCase(userId = "cashier-99", permission = Permission.PROCESS_SALE))
        assertFalse(useCase(userId = "cashier-99", permission = Permission.MANAGE_USERS))
    }

    // ─── ACCOUNTANT role ──────────────────────────────────────────────────────

    @Test
    fun `ACCOUNTANT role has financial view and accounting permissions`() {
        val sessionFlow = MutableStateFlow<com.zyntasolutions.zyntapos.domain.model.User?>(null)
        val useCase = CheckPermissionUseCase(sessionFlow)

        assertTrue(useCase(Role.ACCOUNTANT, Permission.VIEW_REPORTS))
        assertTrue(useCase(Role.ACCOUNTANT, Permission.EXPORT_REPORTS))
        assertTrue(useCase(Role.ACCOUNTANT, Permission.VIEW_AUDIT_LOG))
        assertTrue(useCase(Role.ACCOUNTANT, Permission.MANAGE_EXPENSES))
        assertTrue(useCase(Role.ACCOUNTANT, Permission.APPROVE_EXPENSES))
        assertTrue(useCase(Role.ACCOUNTANT, Permission.MANAGE_ACCOUNTING))
    }

    @Test
    fun `ACCOUNTANT role does not have POS operations permissions`() {
        val sessionFlow = MutableStateFlow<com.zyntasolutions.zyntapos.domain.model.User?>(null)
        val useCase = CheckPermissionUseCase(sessionFlow)

        assertFalse(useCase(Role.ACCOUNTANT, Permission.PROCESS_SALE))
        assertFalse(useCase(Role.ACCOUNTANT, Permission.VOID_ORDER))
        assertFalse(useCase(Role.ACCOUNTANT, Permission.APPLY_DISCOUNT))
        assertFalse(useCase(Role.ACCOUNTANT, Permission.MANAGE_USERS))
        assertFalse(useCase(Role.ACCOUNTANT, Permission.ADMIN_ACCESS))
    }

    // ─── STOCK_MANAGER role ───────────────────────────────────────────────────

    @Test
    fun `STOCK_MANAGER role has inventory permissions`() {
        val sessionFlow = MutableStateFlow<com.zyntasolutions.zyntapos.domain.model.User?>(null)
        val useCase = CheckPermissionUseCase(sessionFlow)

        assertTrue(useCase(Role.STOCK_MANAGER, Permission.MANAGE_PRODUCTS))
        assertTrue(useCase(Role.STOCK_MANAGER, Permission.MANAGE_CATEGORIES))
        assertTrue(useCase(Role.STOCK_MANAGER, Permission.ADJUST_STOCK))
        assertTrue(useCase(Role.STOCK_MANAGER, Permission.MANAGE_SUPPLIERS))
        assertTrue(useCase(Role.STOCK_MANAGER, Permission.VIEW_REPORTS))
        assertTrue(useCase(Role.STOCK_MANAGER, Permission.MANAGE_WAREHOUSES))
        assertTrue(useCase(Role.STOCK_MANAGER, Permission.MANAGE_STOCK_TRANSFERS))
    }

    @Test
    fun `STOCK_MANAGER role does not have POS or user management permissions`() {
        val sessionFlow = MutableStateFlow<com.zyntasolutions.zyntapos.domain.model.User?>(null)
        val useCase = CheckPermissionUseCase(sessionFlow)

        assertFalse(useCase(Role.STOCK_MANAGER, Permission.PROCESS_SALE))
        assertFalse(useCase(Role.STOCK_MANAGER, Permission.VOID_ORDER))
        assertFalse(useCase(Role.STOCK_MANAGER, Permission.MANAGE_USERS))
        assertFalse(useCase(Role.STOCK_MANAGER, Permission.CLOSE_REGISTER))
        assertFalse(useCase(Role.STOCK_MANAGER, Permission.ADMIN_ACCESS))
    }

    // ─── STORE_MANAGER role ───────────────────────────────────────────────────

    @Test
    fun `STORE_MANAGER role has all cashier and management permissions`() {
        val sessionFlow = MutableStateFlow<com.zyntasolutions.zyntapos.domain.model.User?>(null)
        val useCase = CheckPermissionUseCase(sessionFlow)

        assertTrue(useCase(Role.STORE_MANAGER, Permission.PROCESS_SALE))
        assertTrue(useCase(Role.STORE_MANAGER, Permission.MANAGE_PRODUCTS))
        assertTrue(useCase(Role.STORE_MANAGER, Permission.MANAGE_USERS))
        assertTrue(useCase(Role.STORE_MANAGER, Permission.VIEW_REPORTS))
        assertTrue(useCase(Role.STORE_MANAGER, Permission.MANAGE_SETTINGS))
        assertTrue(useCase(Role.STORE_MANAGER, Permission.MANAGE_STAFF))
        assertTrue(useCase(Role.STORE_MANAGER, Permission.MANAGE_ACCOUNTING))
    }

    @Test
    fun `STORE_MANAGER does not have ADMIN_ACCESS permission`() {
        val sessionFlow = MutableStateFlow<com.zyntasolutions.zyntapos.domain.model.User?>(null)
        val useCase = CheckPermissionUseCase(sessionFlow)

        // ADMIN_ACCESS is exclusively in the ADMIN role's permission set
        assertFalse(useCase(Role.STORE_MANAGER, Permission.ADMIN_ACCESS))
    }

    // ─── Role overload (no session required) ─────────────────────────────────

    @Test
    fun `role overload works independently of session state`() {
        val sessionFlow = MutableStateFlow<com.zyntasolutions.zyntapos.domain.model.User?>(null)
        val useCase = CheckPermissionUseCase(sessionFlow)
        // No updateSession call - session snapshot is null

        // Role-only overload should still evaluate correctly
        assertTrue(useCase(Role.CASHIER, Permission.PROCESS_SALE))
        assertFalse(useCase(Role.CASHIER, Permission.MANAGE_USERS))
    }

    @Test
    fun `role overload returns false for permission not in role set`() {
        val sessionFlow = MutableStateFlow<com.zyntasolutions.zyntapos.domain.model.User?>(null)
        val useCase = CheckPermissionUseCase(sessionFlow)

        assertFalse(useCase(Role.ACCOUNTANT, Permission.PROCESS_SALE))
        assertFalse(useCase(Role.STOCK_MANAGER, Permission.VOID_ORDER))
    }
}
