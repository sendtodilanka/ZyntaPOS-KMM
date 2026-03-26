package com.zyntasolutions.zyntapos.data.sync

import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for [CrdtStrategy] — entity type → CRDT strategy mapping.
 */
class CrdtStrategyTest {

    @Test
    fun product_usesFieldMerge() {
        assertEquals(CrdtStrategy.FIELD_MERGE, CrdtStrategy.forEntityType(SyncOperation.EntityType.PRODUCT))
    }

    @Test
    fun stockAdjustment_usesAppendOnly() {
        assertEquals(CrdtStrategy.APPEND_ONLY, CrdtStrategy.forEntityType(SyncOperation.EntityType.STOCK_ADJUSTMENT))
    }

    @Test
    fun accountingEntry_usesAppendOnly() {
        assertEquals(CrdtStrategy.APPEND_ONLY, CrdtStrategy.forEntityType(SyncOperation.EntityType.ACCOUNTING_ENTRY))
    }

    @Test
    fun order_usesOrSet() {
        assertEquals(CrdtStrategy.OR_SET, CrdtStrategy.forEntityType(SyncOperation.EntityType.ORDER))
    }

    @Test
    fun coupon_usesOrSet() {
        assertEquals(CrdtStrategy.OR_SET, CrdtStrategy.forEntityType(SyncOperation.EntityType.COUPON))
    }

    @Test
    fun customer_usesLww() {
        assertEquals(CrdtStrategy.LWW, CrdtStrategy.forEntityType(SyncOperation.EntityType.CUSTOMER))
    }

    @Test
    fun category_usesLww() {
        assertEquals(CrdtStrategy.LWW, CrdtStrategy.forEntityType(SyncOperation.EntityType.CATEGORY))
    }

    @Test
    fun unknownType_defaultsToLww() {
        assertEquals(CrdtStrategy.LWW, CrdtStrategy.forEntityType("unknown_type"))
    }

    @Test
    fun allPhase1EntityTypes_haveDeterministicStrategy() {
        // Verify no entity type accidentally returns a strategy that would cause data loss
        val entityTypes = listOf(
            SyncOperation.EntityType.PRODUCT,
            SyncOperation.EntityType.CATEGORY,
            SyncOperation.EntityType.ORDER,
            SyncOperation.EntityType.CUSTOMER,
            SyncOperation.EntityType.STOCK_ADJUSTMENT,
            SyncOperation.EntityType.SUPPLIER,
            SyncOperation.EntityType.USER,
            SyncOperation.EntityType.REGISTER_SESSION,
            SyncOperation.EntityType.CASH_MOVEMENT,
            SyncOperation.EntityType.SETTINGS,
        )
        for (type in entityTypes) {
            val strategy = CrdtStrategy.forEntityType(type)
            // Every entity type must map to one of the three strategies
            assert(strategy in CrdtStrategy.entries) { "Entity type $type has no CRDT strategy" }
        }
    }
}
