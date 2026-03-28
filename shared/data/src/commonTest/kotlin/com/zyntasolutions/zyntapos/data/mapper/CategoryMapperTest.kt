package com.zyntasolutions.zyntapos.data.mapper

import com.zyntasolutions.zyntapos.data.local.mapper.CategoryMapper
import com.zyntasolutions.zyntapos.db.Categories
import com.zyntasolutions.zyntapos.domain.model.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ZyntaPOS — CategoryMapper Unit Tests (commonTest)
 *
 * Validates bidirectional mapping between the SQLDelight [Categories] row type
 * and the domain [Category] model.
 *
 * Coverage (toDomain):
 *  A. all required fields mapped correctly
 *  B. nullable parentId null when absent
 *  C. nullable imageUrl null when absent
 *  D. is_active=1 maps to isActive=true
 *  E. is_active=0 maps to isActive=false
 *  F. display_order Long is converted to Int correctly
 *
 * Coverage (toInsertParams):
 *  G. all fields mapped from domain Category correctly
 *  H. isActive=true encodes as 1L
 *  I. isActive=false encodes as 0L
 *  J. default syncStatus is PENDING
 *  K. custom syncStatus is used when provided
 */
class CategoryMapperTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildRow(
        id: String = "cat-1",
        name: String = "Beverages",
        parentId: String? = null,
        imageUrl: String? = null,
        displayOrder: Long = 0L,
        isActive: Long = 1L,
        createdAt: Long = 1_000_000L,
        updatedAt: Long = 2_000_000L,
        syncStatus: String = "SYNCED",
    ) = Categories(
        id = id,
        name = name,
        parent_id = parentId,
        image_url = imageUrl,
        display_order = displayOrder,
        is_active = isActive,
        created_at = createdAt,
        updated_at = updatedAt,
        sync_status = syncStatus,
    )

    private fun buildCategory(
        id: String = "cat-1",
        name: String = "Beverages",
        parentId: String? = null,
        imageUrl: String? = null,
        displayOrder: Int = 0,
        isActive: Boolean = true,
    ) = Category(
        id = id,
        name = name,
        parentId = parentId,
        imageUrl = imageUrl,
        displayOrder = displayOrder,
        isActive = isActive,
    )

    // ── toDomain ──────────────────────────────────────────────────────────────

    @Test
    fun `A - toDomain maps all required fields correctly`() {
        val row = buildRow(id = "cat-99", name = "Dairy")
        val domain = CategoryMapper.toDomain(row)
        assertEquals("cat-99", domain.id)
        assertEquals("Dairy", domain.name)
    }

    @Test
    fun `B - toDomain maps null parentId correctly`() {
        val domain = CategoryMapper.toDomain(buildRow(parentId = null))
        assertNull(domain.parentId)
    }

    @Test
    fun `B2 - toDomain maps non-null parentId correctly`() {
        val domain = CategoryMapper.toDomain(buildRow(parentId = "cat-parent"))
        assertEquals("cat-parent", domain.parentId)
    }

    @Test
    fun `C - toDomain maps null imageUrl correctly`() {
        val domain = CategoryMapper.toDomain(buildRow(imageUrl = null))
        assertNull(domain.imageUrl)
    }

    @Test
    fun `C2 - toDomain maps non-null imageUrl correctly`() {
        val domain = CategoryMapper.toDomain(buildRow(imageUrl = "https://example.com/img.png"))
        assertEquals("https://example.com/img.png", domain.imageUrl)
    }

    @Test
    fun `D - toDomain maps is_active=1 to isActive=true`() {
        val domain = CategoryMapper.toDomain(buildRow(isActive = 1L))
        assertTrue(domain.isActive)
    }

    @Test
    fun `E - toDomain maps is_active=0 to isActive=false`() {
        val domain = CategoryMapper.toDomain(buildRow(isActive = 0L))
        assertFalse(domain.isActive)
    }

    @Test
    fun `F - toDomain converts display_order Long to Int`() {
        val domain = CategoryMapper.toDomain(buildRow(displayOrder = 5L))
        assertEquals(5, domain.displayOrder)
    }

    // ── toInsertParams ────────────────────────────────────────────────────────

    @Test
    fun `G - toInsertParams maps all fields from domain Category correctly`() {
        val category = buildCategory(id = "cat-42", name = "Snacks", displayOrder = 3)
        val params = CategoryMapper.toInsertParams(category)
        assertEquals("cat-42", params.id)
        assertEquals("Snacks", params.name)
        assertEquals(3L, params.displayOrder)
    }

    @Test
    fun `H - toInsertParams encodes isActive=true as 1L`() {
        val params = CategoryMapper.toInsertParams(buildCategory(isActive = true))
        assertEquals(1L, params.isActive)
    }

    @Test
    fun `I - toInsertParams encodes isActive=false as 0L`() {
        val params = CategoryMapper.toInsertParams(buildCategory(isActive = false))
        assertEquals(0L, params.isActive)
    }

    @Test
    fun `J - toInsertParams uses PENDING as default syncStatus`() {
        val params = CategoryMapper.toInsertParams(buildCategory())
        assertEquals("PENDING", params.syncStatus)
    }

    @Test
    fun `K - toInsertParams uses provided syncStatus`() {
        val params = CategoryMapper.toInsertParams(buildCategory(), syncStatus = "SYNCED")
        assertEquals("SYNCED", params.syncStatus)
    }
}
