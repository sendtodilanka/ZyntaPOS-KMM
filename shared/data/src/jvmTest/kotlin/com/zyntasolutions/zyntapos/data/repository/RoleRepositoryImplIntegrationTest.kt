package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.CustomRole
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Role
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ZyntaPOS — RoleRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [RoleRepositoryImpl] against a real in-memory SQLite database
 * ([createTestDatabase]) — no mocking. Exercises the full SQL round-trip for
 * custom-role CRUD and built-in role permission overrides stored via
 * [SettingsRepositoryImpl].
 *
 * Coverage:
 *  1. createCustomRole → getCustomRoleById round-trip preserves all fields
 *  2. updateCustomRole persists changes to name and permissions
 *  3. deleteCustomRole removes the role from the database
 *  4. getAllCustomRoles emits updated list when a new role is created (Turbine)
 *  5. setBuiltInRolePermissions persists and getBuiltInRolePermissions retrieves them
 *  6. resetBuiltInRolePermissions removes the override (returns null)
 *  7. setBuiltInRolePermissions for ADMIN returns Result.Error with ValidationException
 */
class RoleRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var settingsRepo: SettingsRepositoryImpl
    private lateinit var repo: RoleRepositoryImpl

    @BeforeTest
    fun setUp() {
        db = createTestDatabase()
        settingsRepo = SettingsRepositoryImpl(db)
        repo = RoleRepositoryImpl(db, settingsRepo)
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private fun makeRole(
        id: String = "cr-01",
        name: String = "Kitchen Staff",
        permissions: Set<Permission> = setOf(Permission.MANAGE_PRODUCTS, Permission.VIEW_REPORTS),
    ) = CustomRole(
        id = id, name = name, description = "Test",
        permissions = permissions,
        createdAt = Instant.fromEpochSeconds(0),
        updatedAt = Instant.fromEpochSeconds(0),
    )

    // ── 1. createCustomRole → getCustomRoleById round-trip ───────────────────

    @Test
    fun createCustomRole_and_getCustomRoleById_round_trip_preserves_all_fields() = runTest {
        val role = makeRole(
            id = "cr-01",
            name = "Kitchen Staff",
            permissions = setOf(Permission.MANAGE_PRODUCTS, Permission.VIEW_REPORTS),
        )

        val createResult = repo.createCustomRole(role)
        assertIs<Result.Success<Unit>>(createResult)

        val getResult = repo.getCustomRoleById("cr-01")
        assertIs<Result.Success<CustomRole>>(getResult)

        val retrieved = getResult.data
        assertEquals(role.id, retrieved.id)
        assertEquals(role.name, retrieved.name)
        assertEquals(role.description, retrieved.description)
        assertEquals(role.permissions, retrieved.permissions)
    }

    // ── 2. updateCustomRole persists changes ─────────────────────────────────

    @Test
    fun updateCustomRole_persists_changes() = runTest {
        val original = makeRole(id = "cr-02", name = "Barista")
        repo.createCustomRole(original)

        val updated = original.copy(
            name = "Senior Barista",
            permissions = setOf(Permission.PROCESS_SALE, Permission.HOLD_ORDER, Permission.VIEW_REPORTS),
        )
        val updateResult = repo.updateCustomRole(updated)
        assertIs<Result.Success<Unit>>(updateResult)

        val roles = repo.getAllCustomRoles().first()
        assertEquals(1, roles.size)
        val persisted = roles[0]
        assertEquals("Senior Barista", persisted.name)
        assertEquals(setOf(Permission.PROCESS_SALE, Permission.HOLD_ORDER, Permission.VIEW_REPORTS), persisted.permissions)
    }

    // ── 3. deleteCustomRole removes the role ─────────────────────────────────

    @Test
    fun deleteCustomRole_removes_the_role() = runTest {
        val role = makeRole(id = "cr-03", name = "Floor Staff")
        repo.createCustomRole(role)

        val deleteResult = repo.deleteCustomRole("cr-03")
        assertIs<Result.Success<Unit>>(deleteResult)

        val roles = repo.getAllCustomRoles().first()
        assertTrue(roles.isEmpty(), "getAllCustomRoles should emit an empty list after deletion")
    }

    // ── 4. getAllCustomRoles emits updated list on create (Turbine) ───────────

    @Test
    fun getAllCustomRoles_emits_updated_list_on_create() = runTest {
        repo.getAllCustomRoles().test {
            // Initial emission — database is empty
            val initial = awaitItem()
            assertTrue(initial.isEmpty(), "Initial emission should be an empty list")

            // Create a role — should trigger a new emission
            val role = makeRole(id = "cr-04", name = "Counter Staff")
            repo.createCustomRole(role)

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("cr-04", updated[0].id)
            assertEquals("Counter Staff", updated[0].name)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── 5. setBuiltInRolePermissions persists; getBuiltInRolePermissions retrieves ──

    @Test
    fun setBuiltInRolePermissions_persists_and_getBuiltInRolePermissions_retrieves_them() = runTest {
        val customPermissions = setOf(
            Permission.PROCESS_SALE,
            Permission.HOLD_ORDER,
            Permission.MANAGE_CUSTOMERS,
        )

        val setResult = repo.setBuiltInRolePermissions(Role.CASHIER, customPermissions)
        assertIs<Result.Success<Unit>>(setResult)

        val retrieved = repo.getBuiltInRolePermissions(Role.CASHIER)
        assertEquals(customPermissions, retrieved)
    }

    // ── 6. resetBuiltInRolePermissions removes the override ──────────────────

    @Test
    fun resetBuiltInRolePermissions_removes_the_override() = runTest {
        val customPermissions = setOf(Permission.VIEW_REPORTS, Permission.EXPORT_REPORTS)

        // Set an override first
        repo.setBuiltInRolePermissions(Role.CASHIER, customPermissions)

        // Confirm the override is present
        val beforeReset = repo.getBuiltInRolePermissions(Role.CASHIER)
        assertEquals(customPermissions, beforeReset)

        // Reset — should remove the override
        val resetResult = repo.resetBuiltInRolePermissions(Role.CASHIER)
        assertIs<Result.Success<Unit>>(resetResult)

        val afterReset = repo.getBuiltInRolePermissions(Role.CASHIER)
        assertNull(afterReset, "After reset, getBuiltInRolePermissions should return null")
    }

    // ── 7. setBuiltInRolePermissions for ADMIN returns ValidationException ───

    @Test
    fun setBuiltInRolePermissions_for_ADMIN_returns_ValidationException() = runTest {
        val result = repo.setBuiltInRolePermissions(
            Role.ADMIN,
            setOf(Permission.PROCESS_SALE),
        )

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertTrue(
            result.exception.message.contains("ADMIN"),
            "Error message should reference ADMIN, but was: '${result.exception.message}'",
        )
    }
}
