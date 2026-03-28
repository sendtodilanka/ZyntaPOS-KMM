package com.zyntasolutions.zyntapos.data.mapper

import com.zyntasolutions.zyntapos.data.local.mapper.SupplierMapper
import com.zyntasolutions.zyntapos.db.Suppliers
import com.zyntasolutions.zyntapos.domain.model.Supplier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ZyntaPOS — SupplierMapper Unit Tests (commonTest)
 *
 * Validates bidirectional mapping between the SQLDelight [Suppliers] row type
 * and the domain [Supplier] model.
 *
 * Coverage (toDomain):
 *  A. all required fields mapped correctly
 *  B. nullable contactPerson, phone, email, address, notes all null when absent
 *  C. nullable fields mapped when present
 *  D. is_active=1 maps to isActive=true
 *  E. is_active=0 maps to isActive=false
 *
 * Coverage (toInsertParams):
 *  F. all fields from domain Supplier mapped correctly
 *  G. isActive=true encodes as 1L
 *  H. isActive=false encodes as 0L
 *  I. default syncStatus is PENDING
 *  J. custom syncStatus is used when provided
 */
class SupplierMapperTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildRow(
        id: String = "sup-1",
        name: String = "ACME Corp",
        contactPerson: String? = null,
        phone: String? = null,
        email: String? = null,
        address: String? = null,
        notes: String? = null,
        isActive: Long = 1L,
        createdAt: Long = 1_000_000L,
        updatedAt: Long = 2_000_000L,
        syncStatus: String = "SYNCED",
    ) = Suppliers(
        id = id,
        name = name,
        contact_person = contactPerson,
        phone = phone,
        email = email,
        address = address,
        notes = notes,
        is_active = isActive,
        created_at = createdAt,
        updated_at = updatedAt,
        sync_status = syncStatus,
    )

    private fun buildSupplier(
        id: String = "sup-1",
        name: String = "ACME Corp",
        contactPerson: String? = null,
        phone: String? = null,
        email: String? = null,
        address: String? = null,
        notes: String? = null,
        isActive: Boolean = true,
    ) = Supplier(
        id = id,
        name = name,
        contactPerson = contactPerson,
        phone = phone,
        email = email,
        address = address,
        notes = notes,
        isActive = isActive,
    )

    // ── toDomain ──────────────────────────────────────────────────────────────

    @Test
    fun `A - toDomain maps all required fields correctly`() {
        val row = buildRow(id = "sup-99", name = "Global Supplies")
        val domain = SupplierMapper.toDomain(row)
        assertEquals("sup-99", domain.id)
        assertEquals("Global Supplies", domain.name)
    }

    @Test
    fun `B - toDomain maps all nullable fields as null when absent`() {
        val domain = SupplierMapper.toDomain(buildRow())
        assertNull(domain.contactPerson)
        assertNull(domain.phone)
        assertNull(domain.email)
        assertNull(domain.address)
        assertNull(domain.notes)
    }

    @Test
    fun `C - toDomain maps all nullable fields when present`() {
        val domain = SupplierMapper.toDomain(
            buildRow(
                contactPerson = "John Doe",
                phone = "+94771234567",
                email = "john@acme.com",
                address = "123 Main St",
                notes = "Preferred vendor",
            )
        )
        assertEquals("John Doe", domain.contactPerson)
        assertEquals("+94771234567", domain.phone)
        assertEquals("john@acme.com", domain.email)
        assertEquals("123 Main St", domain.address)
        assertEquals("Preferred vendor", domain.notes)
    }

    @Test
    fun `D - toDomain maps is_active=1 to isActive=true`() {
        assertTrue(SupplierMapper.toDomain(buildRow(isActive = 1L)).isActive)
    }

    @Test
    fun `E - toDomain maps is_active=0 to isActive=false`() {
        assertFalse(SupplierMapper.toDomain(buildRow(isActive = 0L)).isActive)
    }

    // ── toInsertParams ────────────────────────────────────────────────────────

    @Test
    fun `F - toInsertParams maps all fields from domain Supplier correctly`() {
        val supplier = buildSupplier(id = "sup-42", name = "Best Vendor", phone = "+1234567890")
        val params = SupplierMapper.toInsertParams(supplier)
        assertEquals("sup-42", params.id)
        assertEquals("Best Vendor", params.name)
        assertEquals("+1234567890", params.phone)
    }

    @Test
    fun `G - toInsertParams encodes isActive=true as 1L`() {
        assertEquals(1L, SupplierMapper.toInsertParams(buildSupplier(isActive = true)).isActive)
    }

    @Test
    fun `H - toInsertParams encodes isActive=false as 0L`() {
        assertEquals(0L, SupplierMapper.toInsertParams(buildSupplier(isActive = false)).isActive)
    }

    @Test
    fun `I - toInsertParams uses PENDING as default syncStatus`() {
        assertEquals("PENDING", SupplierMapper.toInsertParams(buildSupplier()).syncStatus)
    }

    @Test
    fun `J - toInsertParams uses provided syncStatus`() {
        assertEquals("SYNCED", SupplierMapper.toInsertParams(buildSupplier(), syncStatus = "SYNCED").syncStatus)
    }
}
