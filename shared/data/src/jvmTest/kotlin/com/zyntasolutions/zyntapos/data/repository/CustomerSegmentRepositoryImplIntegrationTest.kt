package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.CustomerSegment
import com.zyntasolutions.zyntapos.domain.model.SegmentField
import com.zyntasolutions.zyntapos.domain.model.SegmentOperator
import com.zyntasolutions.zyntapos.domain.model.SegmentRule
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — CustomerSegmentRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [CustomerSegmentRepositoryImpl] against a real in-memory SQLite database.
 * No FK constraints on customer_segments — no pre-seeding required.
 *
 * Coverage:
 *  A. insert → getById round-trip preserves all fields including rules JSON
 *  B. getAll via Turbine returns all segments ordered by name
 *  C. getByName returns correct segment
 *  D. update changes name, description, rules, and customer count
 *  E. delete removes the segment
 *  F. getById unknown id returns error
 *  G. rules JSON round-trip with multiple rules
 *  H. segment with empty rules list
 */
class CustomerSegmentRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: CustomerSegmentRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = CustomerSegmentRepositoryImpl(db, SyncEnqueuer(db))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun makeSegment(
        id: String = "seg-01",
        name: String = "High Spenders",
        description: String = "Customers with high lifetime spend",
        rules: List<SegmentRule> = listOf(
            SegmentRule(SegmentField.TOTAL_SPEND, SegmentOperator.GREATER_THAN, "50000"),
        ),
        isAutomatic: Boolean = true,
        customerCount: Int = 0,
    ) = CustomerSegment(
        id = id,
        name = name,
        description = description,
        rules = rules,
        isAutomatic = isAutomatic,
        customerCount = customerCount,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - insert then getById preserves all fields including rules JSON`() = runTest {
        val rules = listOf(
            SegmentRule(SegmentField.TOTAL_SPEND, SegmentOperator.GREATER_THAN, "50000"),
        )
        val segment = makeSegment(
            id = "seg-01",
            name = "High Spenders",
            description = "Top customers by spend",
            rules = rules,
            isAutomatic = true,
            customerCount = 42,
        )
        val insertResult = repo.insert(segment)
        assertIs<Result.Success<Unit>>(insertResult)

        val fetchResult = repo.getById("seg-01")
        assertIs<Result.Success<CustomerSegment>>(fetchResult)
        val fetched = fetchResult.data
        assertEquals("seg-01", fetched.id)
        assertEquals("High Spenders", fetched.name)
        assertEquals("Top customers by spend", fetched.description)
        assertEquals(true, fetched.isAutomatic)
        assertEquals(42, fetched.customerCount)
        assertEquals(1, fetched.rules.size)
        assertEquals(SegmentField.TOTAL_SPEND, fetched.rules[0].field)
        assertEquals(SegmentOperator.GREATER_THAN, fetched.rules[0].operator)
        assertEquals("50000", fetched.rules[0].value)
    }

    @Test
    fun `B - getAll via Turbine returns all segments ordered by name`() = runTest {
        repo.insert(makeSegment(id = "seg-01", name = "Zebra Segment"))
        repo.insert(makeSegment(id = "seg-02", name = "Alpha Segment"))
        repo.insert(makeSegment(id = "seg-03", name = "Middle Segment"))

        repo.getAll().test {
            val list = awaitItem()
            assertEquals(3, list.size)
            assertEquals("Alpha Segment", list[0].name)
            assertEquals("Middle Segment", list[1].name)
            assertEquals("Zebra Segment", list[2].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getByName returns correct segment`() = runTest {
        repo.insert(makeSegment(id = "seg-01", name = "VIP Members"))
        repo.insert(makeSegment(id = "seg-02", name = "New Customers"))

        val result = repo.getByName("VIP Members")
        assertIs<Result.Success<CustomerSegment>>(result)
        assertEquals("seg-01", result.data.id)
        assertEquals("VIP Members", result.data.name)
    }

    @Test
    fun `D - update changes name, description, rules, and customer count`() = runTest {
        repo.insert(makeSegment(id = "seg-01", name = "Old Name", description = "Old desc",
            rules = emptyList(), customerCount = 0))

        val newRules = listOf(
            SegmentRule(SegmentField.ORDER_COUNT, SegmentOperator.GREATER_THAN, "10"),
        )
        val updateResult = repo.update(
            makeSegment(id = "seg-01", name = "New Name", description = "New desc",
                rules = newRules, customerCount = 99)
        )
        assertIs<Result.Success<Unit>>(updateResult)

        val fetched = (repo.getById("seg-01") as Result.Success).data
        assertEquals("New Name", fetched.name)
        assertEquals("New desc", fetched.description)
        assertEquals(99, fetched.customerCount)
        assertEquals(1, fetched.rules.size)
        assertEquals(SegmentField.ORDER_COUNT, fetched.rules[0].field)
    }

    @Test
    fun `E - delete removes the segment`() = runTest {
        repo.insert(makeSegment(id = "seg-01", name = "Keep Me"))
        repo.insert(makeSegment(id = "seg-02", name = "Delete Me"))

        val deleteResult = repo.delete("seg-02")
        assertIs<Result.Success<Unit>>(deleteResult)

        repo.getAll().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("Keep Me", list.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `F - getById unknown id returns error`() = runTest {
        val result = repo.getById("non-existent")
        assertIs<Result.Error>(result)
        assertNotNull(result.exception)
    }

    @Test
    fun `G - rules JSON round-trip with multiple rules`() = runTest {
        val rules = listOf(
            SegmentRule(SegmentField.TOTAL_SPEND, SegmentOperator.GREATER_THAN, "10000"),
            SegmentRule(SegmentField.ORDER_COUNT, SegmentOperator.GREATER_THAN, "5"),
        )
        repo.insert(makeSegment(id = "seg-multi", rules = rules))

        val fetched = (repo.getById("seg-multi") as Result.Success).data
        assertEquals(2, fetched.rules.size)
        assertTrue(fetched.rules.any { it.field == SegmentField.TOTAL_SPEND && it.value == "10000" })
        assertTrue(fetched.rules.any { it.field == SegmentField.ORDER_COUNT && it.value == "5" })
    }

    @Test
    fun `H - segment with empty rules list preserves empty list`() = runTest {
        repo.insert(makeSegment(id = "seg-empty", rules = emptyList()))

        val fetched = (repo.getById("seg-empty") as Result.Success).data
        assertTrue(fetched.rules.isEmpty())
    }
}
