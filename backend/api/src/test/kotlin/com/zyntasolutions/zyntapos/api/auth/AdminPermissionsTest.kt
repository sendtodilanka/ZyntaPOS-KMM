package com.zyntasolutions.zyntapos.api.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Unit tests for AdminPermissions and AdminRole.
 */
class AdminPermissionsTest {

    // ── AdminRole.fromString ─────────────────────────────────────────────

    @Test
    fun `fromString parses all valid roles`() {
        AdminRole.entries.forEach { role ->
            assertEquals(role, AdminRole.fromString(role.name))
        }
    }

    @Test
    fun `fromString is case-insensitive`() {
        assertEquals(AdminRole.ADMIN, AdminRole.fromString("admin"))
        assertEquals(AdminRole.OPERATOR, AdminRole.fromString("operator"))
        assertEquals(AdminRole.FINANCE, AdminRole.fromString("Finance"))
        assertEquals(AdminRole.AUDITOR, AdminRole.fromString("AUDITOR"))
        assertEquals(AdminRole.HELPDESK, AdminRole.fromString("helpdesk"))
    }

    @Test
    fun `fromString returns null for unknown role`() {
        assertEquals(null, AdminRole.fromString("SUPERADMIN"))
        assertEquals(null, AdminRole.fromString(""))
        assertEquals(null, AdminRole.fromString("manager"))
    }

    // ── AdminPermissions.check ───────────────────────────────────────────

    @Test
    fun `ADMIN has all dashboard permissions`() {
        assertTrue(AdminPermissions.check(AdminRole.ADMIN, "dashboard:ops"))
        assertTrue(AdminPermissions.check(AdminRole.ADMIN, "dashboard:financial"))
        assertTrue(AdminPermissions.check(AdminRole.ADMIN, "dashboard:support"))
    }

    @Test
    fun `OPERATOR has ops dashboard but not financial`() {
        assertTrue(AdminPermissions.check(AdminRole.OPERATOR, "dashboard:ops"))
        assertFalse(AdminPermissions.check(AdminRole.OPERATOR, "dashboard:financial"))
        assertTrue(AdminPermissions.check(AdminRole.OPERATOR, "dashboard:support"))
    }

    @Test
    fun `FINANCE has financial dashboard but not ops`() {
        assertFalse(AdminPermissions.check(AdminRole.FINANCE, "dashboard:ops"))
        assertTrue(AdminPermissions.check(AdminRole.FINANCE, "dashboard:financial"))
    }

    @Test
    fun `HELPDESK has support dashboard but not financial or ops`() {
        assertFalse(AdminPermissions.check(AdminRole.HELPDESK, "dashboard:ops"))
        assertFalse(AdminPermissions.check(AdminRole.HELPDESK, "dashboard:financial"))
        assertTrue(AdminPermissions.check(AdminRole.HELPDESK, "dashboard:support"))
    }

    @Test
    fun `AUDITOR has audit permissions only`() {
        assertTrue(AdminPermissions.check(AdminRole.AUDITOR, "audit:read"))
        assertTrue(AdminPermissions.check(AdminRole.AUDITOR, "audit:export"))
        assertFalse(AdminPermissions.check(AdminRole.AUDITOR, "dashboard:ops"))
        assertFalse(AdminPermissions.check(AdminRole.AUDITOR, "users:write"))
    }

    @Test
    fun `only ADMIN can write licenses`() {
        assertTrue(AdminPermissions.check(AdminRole.ADMIN, "license:write"))
        assertFalse(AdminPermissions.check(AdminRole.OPERATOR, "license:write"))
        assertFalse(AdminPermissions.check(AdminRole.FINANCE, "license:write"))
        assertFalse(AdminPermissions.check(AdminRole.AUDITOR, "license:write"))
        assertFalse(AdminPermissions.check(AdminRole.HELPDESK, "license:write"))
    }

    @Test
    fun `all roles can read licenses`() {
        AdminRole.entries.forEach { role ->
            assertTrue(
                AdminPermissions.check(role, "license:read"),
                "Role $role should have license:read"
            )
        }
    }

    @Test
    fun `only ADMIN can manage users`() {
        assertTrue(AdminPermissions.check(AdminRole.ADMIN, "users:read"))
        assertTrue(AdminPermissions.check(AdminRole.ADMIN, "users:write"))
        assertTrue(AdminPermissions.check(AdminRole.ADMIN, "users:deactivate"))
        assertTrue(AdminPermissions.check(AdminRole.ADMIN, "users:sessions:revoke"))

        listOf(AdminRole.OPERATOR, AdminRole.FINANCE, AdminRole.AUDITOR, AdminRole.HELPDESK).forEach { role ->
            assertFalse(AdminPermissions.check(role, "users:write"), "$role should not have users:write")
        }
    }

    @Test
    fun `HELPDESK cannot resolve tickets`() {
        assertTrue(AdminPermissions.check(AdminRole.HELPDESK, "tickets:read"))
        assertTrue(AdminPermissions.check(AdminRole.HELPDESK, "tickets:create"))
        assertFalse(AdminPermissions.check(AdminRole.HELPDESK, "tickets:resolve"))
    }

    @Test
    fun `check returns false for unknown permission`() {
        assertFalse(AdminPermissions.check(AdminRole.ADMIN, "unknown:permission"))
        assertFalse(AdminPermissions.check(AdminRole.ADMIN, ""))
    }

    // ── AdminPermissions.requirePermission ───────────────────────────────

    @Test
    fun `requirePermission does not throw for allowed permission`() {
        AdminPermissions.requirePermission(AdminRole.ADMIN, "users:write")
    }

    @Test
    fun `requirePermission throws AdminAuthorizationException for denied permission`() {
        val ex = assertFailsWith<AdminAuthorizationException> {
            AdminPermissions.requirePermission(AdminRole.HELPDESK, "users:write")
        }
        assertTrue(ex.message!!.contains("HELPDESK"))
        assertTrue(ex.message!!.contains("users:write"))
    }

    // ── AdminPermissions.allForRole ──────────────────────────────────────

    @Test
    fun `allForRole returns all permissions for ADMIN`() {
        val adminPerms = AdminPermissions.allForRole(AdminRole.ADMIN)
        // ADMIN should have the most permissions of any role
        assertTrue(adminPerms.size > 30, "ADMIN should have 30+ permissions, got ${adminPerms.size}")
        assertTrue("users:write" in adminPerms)
        assertTrue("system:settings" in adminPerms)
        assertTrue("license:revoke" in adminPerms)
    }

    @Test
    fun `allForRole returns subset for HELPDESK`() {
        val helpdeskPerms = AdminPermissions.allForRole(AdminRole.HELPDESK)
        val adminPerms = AdminPermissions.allForRole(AdminRole.ADMIN)
        assertTrue(helpdeskPerms.size < adminPerms.size, "HELPDESK should have fewer permissions than ADMIN")
        assertTrue("tickets:read" in helpdeskPerms)
        assertFalse("users:write" in helpdeskPerms)
    }

    @Test
    fun `allForRole returns consistent results with check`() {
        AdminRole.entries.forEach { role ->
            val allPerms = AdminPermissions.allForRole(role)
            allPerms.forEach { perm ->
                assertTrue(
                    AdminPermissions.check(role, perm),
                    "check($role, $perm) should be true since allForRole($role) includes it"
                )
            }
        }
    }

    @Test
    fun `all five admin roles exist`() {
        assertEquals(5, AdminRole.entries.size)
        assertNotNull(AdminRole.fromString("ADMIN"))
        assertNotNull(AdminRole.fromString("OPERATOR"))
        assertNotNull(AdminRole.fromString("FINANCE"))
        assertNotNull(AdminRole.fromString("AUDITOR"))
        assertNotNull(AdminRole.fromString("HELPDESK"))
    }
}
