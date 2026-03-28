package com.zyntasolutions.zyntapos.data.mapper

import com.zyntasolutions.zyntapos.data.local.mapper.CustomerMapper
import com.zyntasolutions.zyntapos.db.Customers
import com.zyntasolutions.zyntapos.domain.model.Customer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ZyntaPOS — CustomerMapper Unit Tests (commonTest)
 *
 * Coverage (toDomain):
 *  A. all required fields mapped correctly
 *  B. null phone row maps to empty string domain phone
 *  C. non-null phone preserved
 *  D. all nullable fields null when absent
 *  E. all nullable fields present when provided
 *  F. is_active=1 → isActive=true
 *  G. is_active=0 → isActive=false
 *  H. credit_enabled=1 → creditEnabled=true
 *  I. is_walk_in=1 → isWalkIn=true
 *  J. loyalty_points Long → Int
 *
 * Coverage (toInsertParams):
 *  K. all fields mapped correctly
 *  L. blank phone stored as null
 *  M. non-blank phone preserved
 *  N. isActive=true encodes as 1L
 *  O. isActive=false encodes as 0L
 *  P. default syncStatus is PENDING
 *  Q. custom syncStatus used when provided
 */
class CustomerMapperTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildRow(
        id: String = "cust-1",
        name: String = "Alice Smith",
        phone: String? = null,
        email: String? = null,
        address: String? = null,
        groupId: String? = null,
        loyaltyPoints: Long = 0L,
        notes: String? = null,
        isActive: Long = 1L,
        creditLimit: Double = 0.0,
        creditEnabled: Long = 0L,
        gender: String? = null,
        birthday: String? = null,
        isWalkIn: Long = 0L,
        storeId: String? = null,
        createdAt: Long = 1_000_000L,
        updatedAt: Long = 2_000_000L,
        syncStatus: String = "SYNCED",
    ) = Customers(
        id = id,
        name = name,
        phone = phone,
        email = email,
        address = address,
        group_id = groupId,
        loyalty_points = loyaltyPoints,
        notes = notes,
        is_active = isActive,
        credit_limit = creditLimit,
        credit_enabled = creditEnabled,
        gender = gender,
        birthday = birthday,
        is_walk_in = isWalkIn,
        store_id = storeId,
        created_at = createdAt,
        updated_at = updatedAt,
        sync_status = syncStatus,
    )

    private fun buildCustomer(
        id: String = "cust-1",
        name: String = "Alice Smith",
        phone: String = "",
        email: String? = null,
        isActive: Boolean = true,
        creditEnabled: Boolean = false,
        isWalkIn: Boolean = false,
        loyaltyPoints: Int = 0,
        creditLimit: Double = 0.0,
    ) = Customer(
        id = id,
        name = name,
        phone = phone,
        email = email,
        isActive = isActive,
        creditEnabled = creditEnabled,
        isWalkIn = isWalkIn,
        loyaltyPoints = loyaltyPoints,
        creditLimit = creditLimit,
    )

    // ── toDomain ──────────────────────────────────────────────────────────────

    @Test
    fun `A - toDomain maps required id and name correctly`() {
        val domain = CustomerMapper.toDomain(buildRow(id = "cust-99", name = "Bob Jones"))
        assertEquals("cust-99", domain.id)
        assertEquals("Bob Jones", domain.name)
    }

    @Test
    fun `B - toDomain maps null phone to empty string`() {
        val domain = CustomerMapper.toDomain(buildRow(phone = null))
        assertEquals("", domain.phone)
    }

    @Test
    fun `C - toDomain preserves non-null phone`() {
        val domain = CustomerMapper.toDomain(buildRow(phone = "+94771234567"))
        assertEquals("+94771234567", domain.phone)
    }

    @Test
    fun `D - toDomain maps all nullable fields as null when absent`() {
        val domain = CustomerMapper.toDomain(buildRow())
        assertNull(domain.email)
        assertNull(domain.address)
        assertNull(domain.groupId)
        assertNull(domain.notes)
        assertNull(domain.gender)
        assertNull(domain.birthday)
        assertNull(domain.storeId)
    }

    @Test
    fun `E - toDomain maps all nullable fields when present`() {
        val domain = CustomerMapper.toDomain(
            buildRow(
                email = "alice@example.com",
                address = "42 Main St",
                groupId = "grp-1",
                notes = "VIP customer",
                gender = "F",
                birthday = "1990-01-01",
                storeId = "store-1",
            )
        )
        assertEquals("alice@example.com", domain.email)
        assertEquals("42 Main St", domain.address)
        assertEquals("grp-1", domain.groupId)
        assertEquals("VIP customer", domain.notes)
        assertEquals("F", domain.gender)
        assertEquals("1990-01-01", domain.birthday)
        assertEquals("store-1", domain.storeId)
    }

    @Test
    fun `F - toDomain maps is_active=1 to isActive=true`() {
        assertTrue(CustomerMapper.toDomain(buildRow(isActive = 1L)).isActive)
    }

    @Test
    fun `G - toDomain maps is_active=0 to isActive=false`() {
        assertFalse(CustomerMapper.toDomain(buildRow(isActive = 0L)).isActive)
    }

    @Test
    fun `H - toDomain maps credit_enabled=1 to creditEnabled=true`() {
        assertTrue(CustomerMapper.toDomain(buildRow(creditEnabled = 1L)).creditEnabled)
    }

    @Test
    fun `I - toDomain maps is_walk_in=1 to isWalkIn=true`() {
        assertTrue(CustomerMapper.toDomain(buildRow(isWalkIn = 1L)).isWalkIn)
    }

    @Test
    fun `J - toDomain converts loyalty_points Long to Int`() {
        val domain = CustomerMapper.toDomain(buildRow(loyaltyPoints = 500L))
        assertEquals(500, domain.loyaltyPoints)
    }

    // ── toInsertParams ────────────────────────────────────────────────────────

    @Test
    fun `K - toInsertParams maps all fields correctly`() {
        val c = buildCustomer(id = "cust-42", name = "Carol White", phone = "+1234567890")
        val params = CustomerMapper.toInsertParams(c)
        assertEquals("cust-42", params.id)
        assertEquals("Carol White", params.name)
        assertEquals("+1234567890", params.phone)
    }

    @Test
    fun `L - toInsertParams stores blank phone as null`() {
        val params = CustomerMapper.toInsertParams(buildCustomer(phone = ""))
        assertNull(params.phone)
    }

    @Test
    fun `M - toInsertParams preserves non-blank phone`() {
        val params = CustomerMapper.toInsertParams(buildCustomer(phone = "+94771234567"))
        assertEquals("+94771234567", params.phone)
    }

    @Test
    fun `N - toInsertParams encodes isActive=true as 1L`() {
        assertEquals(1L, CustomerMapper.toInsertParams(buildCustomer(isActive = true)).isActive)
    }

    @Test
    fun `O - toInsertParams encodes isActive=false as 0L`() {
        assertEquals(0L, CustomerMapper.toInsertParams(buildCustomer(isActive = false)).isActive)
    }

    @Test
    fun `P - toInsertParams uses PENDING as default syncStatus`() {
        assertEquals("PENDING", CustomerMapper.toInsertParams(buildCustomer()).syncStatus)
    }

    @Test
    fun `Q - toInsertParams uses provided syncStatus`() {
        assertEquals("SYNCED", CustomerMapper.toInsertParams(buildCustomer(), syncStatus = "SYNCED").syncStatus)
    }
}
