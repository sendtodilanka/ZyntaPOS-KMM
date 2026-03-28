package com.zyntasolutions.zyntapos.data.mapper

import com.zyntasolutions.zyntapos.data.local.mapper.ProductMapper
import com.zyntasolutions.zyntapos.db.Products
import com.zyntasolutions.zyntapos.domain.model.Product
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.Instant

/**
 * ZyntaPOS — ProductMapper Unit Tests (commonTest)
 *
 * Coverage (toDomain):
 *  A. all required fields mapped correctly
 *  B. null category_id maps to empty string categoryId
 *  C. non-null category_id preserved
 *  D. all nullable fields null when absent
 *  E. all nullable fields present when provided
 *  F. is_active=1 → isActive=true
 *  G. is_active=0 → isActive=false
 *  H. created_at and updated_at Long convert to Instant
 *
 * Coverage (toInsertParams):
 *  I. all fields mapped correctly
 *  K. blank categoryId maps to null in params
 *  L. non-blank categoryId preserved
 *  M. isActive=true encodes as 1L
 *  N. isActive=false encodes as 0L
 *  O. timestamps convert to epoch millis
 *  P. default syncStatus is PENDING
 *  Q. custom syncStatus used when provided
 */
class ProductMapperTest {

    private fun buildRow(
        id: String = "prod-1",
        name: String = "Espresso",
        barcode: String? = null,
        sku: String? = null,
        categoryId: String? = "cat-1",
        unitId: String = "unit-1",
        price: Double = 3.50,
        costPrice: Double = 1.20,
        taxGroupId: String? = null,
        stockQty: Double = 100.0,
        minStockQty: Double = 10.0,
        imageUrl: String? = null,
        description: String? = null,
        isActive: Long = 1L,
        createdAt: Long = 1_000_000L,
        updatedAt: Long = 2_000_000L,
        syncStatus: String = "SYNCED",
        masterProductId: String? = null,
    ) = Products(
        id = id,
        name = name,
        barcode = barcode,
        sku = sku,
        category_id = categoryId,
        unit_id = unitId,
        price = price,
        cost_price = costPrice,
        tax_group_id = taxGroupId,
        stock_qty = stockQty,
        min_stock_qty = minStockQty,
        image_url = imageUrl,
        description = description,
        is_active = isActive,
        created_at = createdAt,
        updated_at = updatedAt,
        sync_status = syncStatus,
        master_product_id = masterProductId,
    )

    private fun buildProduct(
        id: String = "prod-1",
        name: String = "Espresso",
        categoryId: String = "cat-1",
        unitId: String = "unit-1",
        price: Double = 3.50,
        isActive: Boolean = true,
        createdAt: Instant = Instant.fromEpochMilliseconds(1_000_000L),
        updatedAt: Instant = Instant.fromEpochMilliseconds(2_000_000L),
    ) = Product(
        id = id,
        name = name,
        categoryId = categoryId,
        unitId = unitId,
        price = price,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    // ── toDomain ──────────────────────────────────────────────────────────────

    @Test
    fun `A - toDomain maps all required fields correctly`() {
        val domain = ProductMapper.toDomain(
            buildRow(id = "prod-99", name = "Latte", unitId = "unit-5", price = 4.50, stockQty = 50.0)
        )
        assertEquals("prod-99", domain.id)
        assertEquals("Latte", domain.name)
        assertEquals("unit-5", domain.unitId)
        assertEquals(4.50, domain.price)
        assertEquals(50.0, domain.stockQty)
    }

    @Test
    fun `B - toDomain maps null category_id to empty string`() {
        val domain = ProductMapper.toDomain(buildRow(categoryId = null))
        assertEquals("", domain.categoryId)
    }

    @Test
    fun `C - toDomain preserves non-null category_id`() {
        val domain = ProductMapper.toDomain(buildRow(categoryId = "cat-5"))
        assertEquals("cat-5", domain.categoryId)
    }

    @Test
    fun `D - toDomain maps all nullable fields as null when absent`() {
        val domain = ProductMapper.toDomain(buildRow(barcode = null, sku = null, taxGroupId = null, imageUrl = null, description = null, masterProductId = null))
        assertNull(domain.barcode)
        assertNull(domain.sku)
        assertNull(domain.taxGroupId)
        assertNull(domain.imageUrl)
        assertNull(domain.description)
        assertNull(domain.masterProductId)
    }

    @Test
    fun `E - toDomain maps all nullable fields when present`() {
        val domain = ProductMapper.toDomain(
            buildRow(
                barcode = "123456789",
                sku = "ESP-001",
                taxGroupId = "tax-1",
                imageUrl = "https://example.com/img.jpg",
                description = "Rich espresso shot",
                masterProductId = "master-1",
            )
        )
        assertEquals("123456789", domain.barcode)
        assertEquals("ESP-001", domain.sku)
        assertEquals("tax-1", domain.taxGroupId)
        assertEquals("https://example.com/img.jpg", domain.imageUrl)
        assertEquals("Rich espresso shot", domain.description)
        assertEquals("master-1", domain.masterProductId)
    }

    @Test
    fun `F - toDomain maps is_active=1 to isActive=true`() {
        assertTrue(ProductMapper.toDomain(buildRow(isActive = 1L)).isActive)
    }

    @Test
    fun `G - toDomain maps is_active=0 to isActive=false`() {
        assertFalse(ProductMapper.toDomain(buildRow(isActive = 0L)).isActive)
    }

    @Test
    fun `H - toDomain converts created_at and updated_at Long to Instant`() {
        val domain = ProductMapper.toDomain(buildRow(createdAt = 1_700_000_000_000L, updatedAt = 1_700_001_000_000L))
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), domain.createdAt)
        assertEquals(Instant.fromEpochMilliseconds(1_700_001_000_000L), domain.updatedAt)
    }

    // ── toInsertParams ────────────────────────────────────────────────────────

    @Test
    fun `J - toInsertParams maps all fields correctly`() {
        val product = buildProduct(id = "prod-42", name = "Cappuccino", price = 5.00)
        val params = ProductMapper.toInsertParams(product)
        assertEquals("prod-42", params.id)
        assertEquals("Cappuccino", params.name)
        assertEquals(5.00, params.price)
    }

    @Test
    fun `K - toInsertParams stores blank categoryId as null`() {
        val params = ProductMapper.toInsertParams(buildProduct(categoryId = ""))
        assertNull(params.categoryId)
    }

    @Test
    fun `L - toInsertParams preserves non-blank categoryId`() {
        val params = ProductMapper.toInsertParams(buildProduct(categoryId = "cat-7"))
        assertEquals("cat-7", params.categoryId)
    }

    @Test
    fun `M - toInsertParams encodes isActive=true as 1L`() {
        assertEquals(1L, ProductMapper.toInsertParams(buildProduct(isActive = true)).isActive)
    }

    @Test
    fun `N - toInsertParams encodes isActive=false as 0L`() {
        assertEquals(0L, ProductMapper.toInsertParams(buildProduct(isActive = false)).isActive)
    }

    @Test
    fun `O - toInsertParams converts Instant timestamps to epoch millis`() {
        val created = Instant.fromEpochMilliseconds(1_700_000_000_000L)
        val updated = Instant.fromEpochMilliseconds(1_700_001_000_000L)
        val params = ProductMapper.toInsertParams(buildProduct(createdAt = created, updatedAt = updated))
        assertEquals(1_700_000_000_000L, params.createdAt)
        assertEquals(1_700_001_000_000L, params.updatedAt)
    }

    @Test
    fun `P - toInsertParams uses PENDING as default syncStatus`() {
        assertEquals("PENDING", ProductMapper.toInsertParams(buildProduct()).syncStatus)
    }

    @Test
    fun `Q - toInsertParams uses provided syncStatus`() {
        assertEquals("SYNCED", ProductMapper.toInsertParams(buildProduct(), syncStatus = "SYNCED").syncStatus)
    }
}
