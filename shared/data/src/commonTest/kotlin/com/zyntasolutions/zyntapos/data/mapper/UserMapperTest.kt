package com.zyntasolutions.zyntapos.data.mapper

import com.zyntasolutions.zyntapos.data.local.mapper.UserMapper
import com.zyntasolutions.zyntapos.db.Users
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.User
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [UserMapper].
 *
 * Coverage:
 * - [UserMapper.toDomain]: all fields mapped correctly, both for isSystemAdmin=true and false
 * - [UserMapper.toInsertParams]: all fields mapped correctly, isSystemAdmin Long encoding
 * - Nullable fields (pinHash, customRoleId) handled correctly in both directions
 * - Role enum round-tripping (stored as String, parsed back to enum)
 * - Boolean-to-Long and Long-to-Boolean encoding for isActive and isSystemAdmin
 */
class UserMapperTest {

    // ── toDomain ──────────────────────────────────────────────────────────────

    private fun buildRow(
        id: String = "user-1",
        name: String = "Alice",
        email: String = "alice@test.com",
        passwordHash: String = "bcrypt-hash",
        role: String = "ADMIN",
        pinHash: String? = null,
        storeId: String = "store-1",
        isActive: Long = 1L,
        createdAt: Long = 1_000_000L,
        updatedAt: Long = 2_000_000L,
        syncStatus: String = "SYNCED",
        customRoleId: String? = null,
        isSystemAdmin: Long = 0L,
    ) = Users(
        id = id,
        name = name,
        email = email,
        password_hash = passwordHash,
        role = role,
        pin_hash = pinHash,
        store_id = storeId,
        is_active = isActive,
        created_at = createdAt,
        updated_at = updatedAt,
        sync_status = syncStatus,
        custom_role_id = customRoleId,
        is_system_admin = isSystemAdmin,
    )

    @Test
    fun `toDomain maps id correctly`() {
        val row = buildRow(id = "test-id-123")
        val user = UserMapper.toDomain(row)
        assertEquals("test-id-123", user.id)
    }

    @Test
    fun `toDomain maps name correctly`() {
        val row = buildRow(name = "Bob Smith")
        val user = UserMapper.toDomain(row)
        assertEquals("Bob Smith", user.name)
    }

    @Test
    fun `toDomain maps email correctly`() {
        val row = buildRow(email = "bob@store.com")
        val user = UserMapper.toDomain(row)
        assertEquals("bob@store.com", user.email)
    }

    @Test
    fun `toDomain maps role ADMIN correctly`() {
        val row = buildRow(role = "ADMIN")
        val user = UserMapper.toDomain(row)
        assertEquals(Role.ADMIN, user.role)
    }

    @Test
    fun `toDomain maps role CASHIER correctly`() {
        val row = buildRow(role = "CASHIER")
        val user = UserMapper.toDomain(row)
        assertEquals(Role.CASHIER, user.role)
    }

    @Test
    fun `toDomain maps role STORE_MANAGER correctly`() {
        val row = buildRow(role = "STORE_MANAGER")
        val user = UserMapper.toDomain(row)
        assertEquals(Role.STORE_MANAGER, user.role)
    }

    @Test
    fun `toDomain maps role ACCOUNTANT correctly`() {
        val row = buildRow(role = "ACCOUNTANT")
        val user = UserMapper.toDomain(row)
        assertEquals(Role.ACCOUNTANT, user.role)
    }

    @Test
    fun `toDomain maps role STOCK_MANAGER correctly`() {
        val row = buildRow(role = "STOCK_MANAGER")
        val user = UserMapper.toDomain(row)
        assertEquals(Role.STOCK_MANAGER, user.role)
    }

    @Test
    fun `toDomain maps storeId correctly`() {
        val row = buildRow(storeId = "store-xyz")
        val user = UserMapper.toDomain(row)
        assertEquals("store-xyz", user.storeId)
    }

    @Test
    fun `toDomain maps isActive 1L to true`() {
        val row = buildRow(isActive = 1L)
        val user = UserMapper.toDomain(row)
        assertTrue(user.isActive)
    }

    @Test
    fun `toDomain maps isActive 0L to false`() {
        val row = buildRow(isActive = 0L)
        val user = UserMapper.toDomain(row)
        assertFalse(user.isActive)
    }

    @Test
    fun `toDomain maps pinHash null correctly`() {
        val row = buildRow(pinHash = null)
        val user = UserMapper.toDomain(row)
        assertNull(user.pinHash)
    }

    @Test
    fun `toDomain maps pinHash non-null correctly`() {
        val row = buildRow(pinHash = "sha256-salt:hash")
        val user = UserMapper.toDomain(row)
        assertEquals("sha256-salt:hash", user.pinHash)
    }

    @Test
    fun `toDomain maps customRoleId null correctly`() {
        val row = buildRow(customRoleId = null)
        val user = UserMapper.toDomain(row)
        assertNull(user.customRoleId)
    }

    @Test
    fun `toDomain maps customRoleId non-null correctly`() {
        val row = buildRow(customRoleId = "custom-role-99")
        val user = UserMapper.toDomain(row)
        assertEquals("custom-role-99", user.customRoleId)
    }

    @Test
    fun `toDomain maps is_system_admin 1L to isSystemAdmin true`() {
        val row = buildRow(isSystemAdmin = 1L)
        val user = UserMapper.toDomain(row)
        assertTrue(user.isSystemAdmin, "is_system_admin = 1 must map to isSystemAdmin = true")
    }

    @Test
    fun `toDomain maps is_system_admin 0L to isSystemAdmin false`() {
        val row = buildRow(isSystemAdmin = 0L)
        val user = UserMapper.toDomain(row)
        assertFalse(user.isSystemAdmin, "is_system_admin = 0 must map to isSystemAdmin = false")
    }

    @Test
    fun `toDomain maps createdAt epoch millis correctly`() {
        val row = buildRow(createdAt = 1_700_000_000_000L)
        val user = UserMapper.toDomain(row)
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), user.createdAt)
    }

    @Test
    fun `toDomain maps updatedAt epoch millis correctly`() {
        val row = buildRow(updatedAt = 1_700_100_000_000L)
        val user = UserMapper.toDomain(row)
        assertEquals(Instant.fromEpochMilliseconds(1_700_100_000_000L), user.updatedAt)
    }

    // ── toInsertParams ────────────────────────────────────────────────────────

    private fun buildDomainUser(
        id: String = "user-1",
        name: String = "Alice",
        email: String = "alice@test.com",
        role: Role = Role.ADMIN,
        storeId: String = "store-1",
        isActive: Boolean = true,
        pinHash: String? = null,
        customRoleId: String? = null,
        isSystemAdmin: Boolean = false,
    ) = User(
        id = id,
        name = name,
        email = email,
        role = role,
        storeId = storeId,
        isActive = isActive,
        pinHash = pinHash,
        customRoleId = customRoleId,
        isSystemAdmin = isSystemAdmin,
        createdAt = Instant.fromEpochMilliseconds(1_000_000L),
        updatedAt = Instant.fromEpochMilliseconds(2_000_000L),
    )

    @Test
    fun `toInsertParams maps id correctly`() {
        val params = UserMapper.toInsertParams(buildDomainUser(id = "param-id"), "hashed-pw")
        assertEquals("param-id", params.id)
    }

    @Test
    fun `toInsertParams maps passwordHash correctly`() {
        val params = UserMapper.toInsertParams(buildDomainUser(), "my-bcrypt-hash")
        assertEquals("my-bcrypt-hash", params.passwordHash)
    }

    @Test
    fun `toInsertParams maps role as string`() {
        val params = UserMapper.toInsertParams(buildDomainUser(role = Role.CASHIER), "hash")
        assertEquals("CASHIER", params.role)
    }

    @Test
    fun `toInsertParams maps isActive true to 1L`() {
        val params = UserMapper.toInsertParams(buildDomainUser(isActive = true), "hash")
        assertEquals(1L, params.isActive)
    }

    @Test
    fun `toInsertParams maps isActive false to 0L`() {
        val params = UserMapper.toInsertParams(buildDomainUser(isActive = false), "hash")
        assertEquals(0L, params.isActive)
    }

    @Test
    fun `toInsertParams maps isSystemAdmin true to 1L`() {
        val params = UserMapper.toInsertParams(buildDomainUser(isSystemAdmin = true), "hash")
        assertEquals(1L, params.isSystemAdmin, "isSystemAdmin = true must encode to 1L")
    }

    @Test
    fun `toInsertParams maps isSystemAdmin false to 0L`() {
        val params = UserMapper.toInsertParams(buildDomainUser(isSystemAdmin = false), "hash")
        assertEquals(0L, params.isSystemAdmin, "isSystemAdmin = false must encode to 0L")
    }

    @Test
    fun `toInsertParams maps pinHash null correctly`() {
        val params = UserMapper.toInsertParams(buildDomainUser(pinHash = null), "hash")
        assertNull(params.pinHash)
    }

    @Test
    fun `toInsertParams maps pinHash non-null correctly`() {
        val params = UserMapper.toInsertParams(buildDomainUser(pinHash = "sha256-data"), "hash")
        assertEquals("sha256-data", params.pinHash)
    }

    @Test
    fun `toInsertParams maps customRoleId null correctly`() {
        val params = UserMapper.toInsertParams(buildDomainUser(customRoleId = null), "hash")
        assertNull(params.customRoleId)
    }

    @Test
    fun `toInsertParams maps customRoleId non-null correctly`() {
        val params = UserMapper.toInsertParams(buildDomainUser(customRoleId = "cr-123"), "hash")
        assertEquals("cr-123", params.customRoleId)
    }

    @Test
    fun `toInsertParams uses PENDING syncStatus by default`() {
        val params = UserMapper.toInsertParams(buildDomainUser(), "hash")
        assertEquals("PENDING", params.syncStatus)
    }

    @Test
    fun `toInsertParams accepts custom syncStatus`() {
        val params = UserMapper.toInsertParams(buildDomainUser(), "hash", syncStatus = "SYNCED")
        assertEquals("SYNCED", params.syncStatus)
    }

    @Test
    fun `toInsertParams maps createdAt to epoch millis`() {
        val params = UserMapper.toInsertParams(buildDomainUser(), "hash")
        assertEquals(1_000_000L, params.createdAt)
    }

    @Test
    fun `toInsertParams maps updatedAt to epoch millis`() {
        val params = UserMapper.toInsertParams(buildDomainUser(), "hash")
        assertEquals(2_000_000L, params.updatedAt)
    }

    // ── Round-trip: toDomain then toInsertParams ───────────────────────────────

    @Test
    fun `round-trip preserves isSystemAdmin true`() {
        val row = buildRow(isSystemAdmin = 1L)
        val domain = UserMapper.toDomain(row)
        val params = UserMapper.toInsertParams(domain, "any-hash")
        assertEquals(1L, params.isSystemAdmin)
    }

    @Test
    fun `round-trip preserves isSystemAdmin false`() {
        val row = buildRow(isSystemAdmin = 0L)
        val domain = UserMapper.toDomain(row)
        val params = UserMapper.toInsertParams(domain, "any-hash")
        assertEquals(0L, params.isSystemAdmin)
    }
}
