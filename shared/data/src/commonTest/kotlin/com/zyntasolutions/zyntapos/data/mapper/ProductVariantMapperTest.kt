package com.zyntasolutions.zyntapos.data.mapper

import com.zyntasolutions.zyntapos.data.local.mapper.ProductVariantMapper
import com.zyntasolutions.zyntapos.db.Product_variants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ZyntaPOS — ProductVariantMapper Unit Tests (commonTest)
 *
 * Coverage (toDomain):
 *  A. all required fields mapped correctly
 *  B. blank attributes string maps to empty map
 *  C. empty JSON object "{}" maps to empty map
 *  D. valid JSON attributes parsed to map correctly
 *  E. malformed JSON attributes falls back to empty map
 *  F. null price maps to null domain price
 *  G. non-null price preserved
 *  H. null barcode maps to null domain barcode
 *
 * Coverage (attributesToJson):
 *  I. empty map serialises to "{}"
 *  J. single-entry map serialises correctly
 *  K. multi-entry map serialised as JSON object
 */
class ProductVariantMapperTest {

    private fun buildRow(
        id: String = "var-1",
        productId: String = "prod-1",
        name: String = "Blue XL",
        attributes: String = "{}",
        price: Double? = null,
        stock: Double = 5.0,
        barcode: String? = null,
        createdAt: Long = 1_000_000L,
        updatedAt: Long = 2_000_000L,
        syncStatus: String = "SYNCED",
    ) = Product_variants(
        id = id,
        product_id = productId,
        name = name,
        attributes = attributes,
        price = price,
        stock = stock,
        barcode = barcode,
        created_at = createdAt,
        updated_at = updatedAt,
        sync_status = syncStatus,
    )

    // ── toDomain ──────────────────────────────────────────────────────────────

    @Test
    fun `A - toDomain maps all required fields correctly`() {
        val domain = ProductVariantMapper.toDomain(
            buildRow(id = "var-99", productId = "prod-42", name = "Red M", stock = 12.0)
        )
        assertEquals("var-99", domain.id)
        assertEquals("prod-42", domain.productId)
        assertEquals("Red M", domain.name)
        assertEquals(12.0, domain.stock)
    }

    @Test
    fun `B - toDomain maps blank attributes to empty map`() {
        val domain = ProductVariantMapper.toDomain(buildRow(attributes = "   "))
        assertTrue(domain.attributes.isEmpty())
    }

    @Test
    fun `C - toDomain maps empty JSON object to empty map`() {
        val domain = ProductVariantMapper.toDomain(buildRow(attributes = "{}"))
        assertTrue(domain.attributes.isEmpty())
    }

    @Test
    fun `D - toDomain parses valid JSON attributes to map`() {
        val domain = ProductVariantMapper.toDomain(
            buildRow(attributes = """{"Color":"Blue","Size":"XL"}""")
        )
        assertEquals("Blue", domain.attributes["Color"])
        assertEquals("XL", domain.attributes["Size"])
    }

    @Test
    fun `E - toDomain falls back to empty map for malformed JSON`() {
        val domain = ProductVariantMapper.toDomain(buildRow(attributes = "not-valid-json"))
        assertTrue(domain.attributes.isEmpty())
    }

    @Test
    fun `F - toDomain maps null price to null domain price`() {
        assertNull(ProductVariantMapper.toDomain(buildRow(price = null)).price)
    }

    @Test
    fun `G - toDomain preserves non-null price`() {
        assertEquals(29.99, ProductVariantMapper.toDomain(buildRow(price = 29.99)).price)
    }

    @Test
    fun `H - toDomain maps null barcode to null domain barcode`() {
        assertNull(ProductVariantMapper.toDomain(buildRow(barcode = null)).barcode)
    }

    // ── attributesToJson ──────────────────────────────────────────────────────

    @Test
    fun `I - attributesToJson empty map serialises to empty JSON object`() {
        val json = ProductVariantMapper.attributesToJson(emptyMap())
        assertEquals("{}", json)
    }

    @Test
    fun `J - attributesToJson single entry map serialises correctly`() {
        val json = ProductVariantMapper.attributesToJson(mapOf("Color" to "Red"))
        // Parse back to verify round-trip
        val domain = ProductVariantMapper.toDomain(buildRow(attributes = json))
        assertEquals("Red", domain.attributes["Color"])
    }

    @Test
    fun `K - attributesToJson multi-entry map serialises all entries`() {
        val map = mapOf("Color" to "Blue", "Size" to "XL")
        val json = ProductVariantMapper.attributesToJson(map)
        val domain = ProductVariantMapper.toDomain(buildRow(attributes = json))
        assertEquals("Blue", domain.attributes["Color"])
        assertEquals("XL", domain.attributes["Size"])
    }
}
