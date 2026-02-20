package com.zyntasolutions.zyntapos.data.local.mapper

import com.zyntasolutions.zyntapos.db.Suppliers
import com.zyntasolutions.zyntapos.domain.model.Supplier

/**
 * Maps between the SQLDelight-generated [Suppliers] entity and the domain [Supplier] model.
 */
object SupplierMapper {

    fun toDomain(row: Suppliers): Supplier = Supplier(
        id            = row.id,
        name          = row.name,
        contactPerson = row.contact_person,
        phone         = row.phone,
        email         = row.email,
        address       = row.address,
        notes         = row.notes,
        isActive      = row.is_active == 1L,
    )

    fun toInsertParams(s: Supplier, syncStatus: String = "PENDING") = InsertParams(
        id            = s.id,
        name          = s.name,
        contactPerson = s.contactPerson,
        phone         = s.phone,
        email         = s.email,
        address       = s.address,
        notes         = s.notes,
        isActive      = if (s.isActive) 1L else 0L,
        syncStatus    = syncStatus,
    )

    data class InsertParams(
        val id: String,
        val name: String,
        val contactPerson: String?,
        val phone: String?,
        val email: String?,
        val address: String?,
        val notes: String?,
        val isActive: Long,
        val syncStatus: String,
    )
}
