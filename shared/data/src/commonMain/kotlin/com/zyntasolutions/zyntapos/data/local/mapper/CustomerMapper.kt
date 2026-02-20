package com.zyntasolutions.zyntapos.data.local.mapper

import com.zyntasolutions.zyntapos.db.Customers
import com.zyntasolutions.zyntapos.domain.model.Customer

/**
 * Maps between the SQLDelight-generated [Customers] entity and the domain [Customer] model.
 */
object CustomerMapper {

    fun toDomain(row: Customers): Customer = Customer(
        id            = row.id,
        name          = row.name,
        phone         = row.phone ?: "",
        email         = row.email,
        address       = row.address,
        groupId       = row.group_id,
        loyaltyPoints = row.loyalty_points.toInt(),
        notes         = row.notes,
        isActive      = row.is_active == 1L,
    )

    fun toInsertParams(c: Customer, syncStatus: String = "PENDING") = InsertParams(
        id            = c.id,
        name          = c.name,
        phone         = c.phone.ifBlank { null },
        email         = c.email,
        address       = c.address,
        groupId       = c.groupId,
        loyaltyPoints = c.loyaltyPoints.toLong(),
        notes         = c.notes,
        isActive      = if (c.isActive) 1L else 0L,
        syncStatus    = syncStatus,
    )

    data class InsertParams(
        val id: String,
        val name: String,
        val phone: String?,
        val email: String?,
        val address: String?,
        val groupId: String?,
        val loyaltyPoints: Long,
        val notes: String?,
        val isActive: Long,
        val syncStatus: String,
    )
}
