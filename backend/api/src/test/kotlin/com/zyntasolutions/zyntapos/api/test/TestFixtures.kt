package com.zyntasolutions.zyntapos.api.test

import com.zyntasolutions.zyntapos.api.auth.AdminRole
import com.zyntasolutions.zyntapos.api.db.AdminUsers
import com.zyntasolutions.zyntapos.api.db.Stores
import com.zyntasolutions.zyntapos.api.db.Users
import com.zyntasolutions.zyntapos.api.db.WarehouseStock
import com.zyntasolutions.zyntapos.api.service.Products
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Factory for creating test data in the database.
 *
 * All methods execute inside an Exposed transaction and return the created entity's ID.
 */
object TestFixtures {

    fun insertStore(
        id: String = "store-${UUID.randomUUID().toString().take(8)}",
        name: String = "Test Store",
        licenseKey: String = "LK-${UUID.randomUUID().toString().take(8)}",
        timezone: String = "Asia/Colombo",
        currency: String = "LKR",
        isActive: Boolean = true,
    ): String {
        transaction {
            Stores.insert {
                it[Stores.id] = id
                it[Stores.name] = name
                it[Stores.licenseKey] = licenseKey
                it[Stores.timezone] = timezone
                it[Stores.currency] = currency
                it[Stores.isActive] = isActive
                it[Stores.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
                it[Stores.updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }
        }
        return id
    }

    fun insertProduct(
        id: String = "prod-${UUID.randomUUID().toString().take(8)}",
        storeId: String,
        name: String = "Test Product",
        sku: String? = "SKU-${UUID.randomUUID().toString().take(6)}",
        barcode: String? = null,
        price: BigDecimal = BigDecimal("9.9900"),
        costPrice: BigDecimal = BigDecimal("5.0000"),
        stockQty: BigDecimal = BigDecimal("100.0000"),
        categoryId: String? = null,
        unitId: String? = null,
        taxGroupId: String? = null,
        minStockQty: BigDecimal? = null,
        imageUrl: String? = null,
        description: String? = null,
        isActive: Boolean = true,
        syncVersion: Long = 1L,
        updatedAt: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
        createdAt: OffsetDateTime? = OffsetDateTime.now(ZoneOffset.UTC),
    ): String {
        transaction {
            Products.insert {
                it[Products.id] = id
                it[Products.storeId] = storeId
                it[Products.name] = name
                it[Products.sku] = sku
                it[Products.barcode] = barcode
                it[Products.price] = price
                it[Products.costPrice] = costPrice
                it[Products.stockQty] = stockQty
                it[Products.categoryId] = categoryId
                it[Products.unitId] = unitId
                it[Products.taxGroupId] = taxGroupId
                it[Products.minStockQty] = minStockQty
                it[Products.imageUrl] = imageUrl
                it[Products.description] = description
                it[Products.isActive] = isActive
                it[Products.syncVersion] = syncVersion
                it[Products.updatedAt] = updatedAt
                it[Products.createdAt] = createdAt
            }
        }
        return id
    }

    fun insertUser(
        id: String = "user-${UUID.randomUUID().toString().take(8)}",
        storeId: String,
        username: String = "cashier_${UUID.randomUUID().toString().take(4)}",
        email: String? = null,
        name: String? = "Test User",
        passwordHash: String = "dGVzdHNhbHQ=:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        role: String = "CASHIER",
        isActive: Boolean = true,
        failedAttempts: Int = 0,
        lockedUntil: OffsetDateTime? = null,
    ): String {
        transaction {
            Users.insert {
                it[Users.id] = id
                it[Users.storeId] = storeId
                it[Users.username] = username
                it[Users.email] = email
                it[Users.name] = name
                it[Users.passwordHash] = passwordHash
                it[Users.role] = role
                it[Users.isActive] = isActive
                it[Users.failedAttempts] = failedAttempts
                it[Users.lockedUntil] = lockedUntil
                it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
                it[Users.updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }
        }
        return id
    }

    fun insertWarehouseStock(
        id: String = "ws-${UUID.randomUUID().toString().take(8)}",
        warehouseId: String,
        productId: String,
        storeId: String,
        quantity: BigDecimal = BigDecimal("10.0000"),
        minQuantity: BigDecimal = BigDecimal("0.0000"),
        syncVersion: Long = 1L,
    ): String {
        transaction {
            // Ensure parent store exists to satisfy the FK constraint on warehouse_stock.store_id
            if (Stores.selectAll().where { Stores.id eq storeId }.empty()) {
                Stores.insert {
                    it[Stores.id]         = storeId
                    it[Stores.name]       = "Test Store"
                    it[Stores.licenseKey] = "LK-test-$storeId"
                    it[Stores.timezone]   = "Asia/Colombo"
                    it[Stores.currency]   = "LKR"
                    it[Stores.isActive]   = true
                    it[Stores.createdAt]  = OffsetDateTime.now(ZoneOffset.UTC)
                    it[Stores.updatedAt]  = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            WarehouseStock.insert {
                it[WarehouseStock.id]          = id
                it[WarehouseStock.warehouseId] = warehouseId
                it[WarehouseStock.productId]   = productId
                it[WarehouseStock.storeId]     = storeId
                it[WarehouseStock.quantity]    = quantity
                it[WarehouseStock.minQuantity] = minQuantity
                it[WarehouseStock.syncVersion] = syncVersion
                it[WarehouseStock.updatedAt]   = OffsetDateTime.now(ZoneOffset.UTC)
            }
        }
        return id
    }

    fun insertAdminUser(
        id: UUID = UUID.randomUUID(),
        email: String = "admin_${UUID.randomUUID().toString().take(4)}@test.local",
        name: String = "Test Admin",
        role: AdminRole = AdminRole.ADMIN,
        passwordHash: String? = "\$2a\$12\$testhashedpasswordvalue000000000000000000000000000000",
        mfaEnabled: Boolean = false,
        isActive: Boolean = true,
        failedAttempts: Int = 0,
        lockedUntil: Long? = null,
        lastLoginAt: Long? = null,
        createdAt: Long = System.currentTimeMillis(),
    ): UUID {
        transaction {
            AdminUsers.insert {
                it[AdminUsers.id] = id
                it[AdminUsers.email] = email
                it[AdminUsers.name] = name
                it[AdminUsers.role] = role.name
                it[AdminUsers.passwordHash] = passwordHash
                it[AdminUsers.mfaEnabled] = mfaEnabled
                it[AdminUsers.isActive] = isActive
                it[AdminUsers.failedAttempts] = failedAttempts
                it[AdminUsers.lockedUntil] = lockedUntil
                it[AdminUsers.lastLoginAt] = lastLoginAt
                it[AdminUsers.createdAt] = createdAt
            }
        }
        return id
    }
}
