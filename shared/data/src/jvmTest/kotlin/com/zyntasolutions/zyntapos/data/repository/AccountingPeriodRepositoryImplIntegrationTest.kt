package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.AccountingPeriod
import com.zyntasolutions.zyntapos.domain.model.PeriodStatus
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — AccountingPeriodRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [AccountingPeriodRepositoryImpl] against a real in-memory SQLite database.
 * accounting_periods has no external FK constraints.
 *
 * Coverage:
 *  A. create → getById round-trip preserves all fields
 *  B. getAll emits all periods ordered by start_date via Turbine
 *  C. getPeriodForDate returns matching period
 *  D. getPeriodForDate returns null when no period covers the date
 *  E. getOpenPeriods returns only OPEN periods
 *  F. closePeriod changes status to CLOSED
 *  G. lockPeriod changes status to LOCKED and sets lockedBy/lockedAt
 *  H. reopenPeriod changes CLOSED back to OPEN
 */
class AccountingPeriodRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: AccountingPeriodRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = AccountingPeriodRepositoryImpl(db, SyncEnqueuer(db))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makePeriod(
        id: String = "period-01",
        periodName: String = "April 2026",
        startDate: String = "2026-04-01",
        endDate: String = "2026-04-30",
        status: PeriodStatus = PeriodStatus.OPEN,
        fiscalYearStart: String = "2026-01-01",
        isAdjustment: Boolean = false,
    ) = AccountingPeriod(
        id = id,
        periodName = periodName,
        startDate = startDate,
        endDate = endDate,
        status = status,
        fiscalYearStart = fiscalYearStart,
        isAdjustment = isAdjustment,
        createdAt = now,
        updatedAt = now,
        lockedAt = null,
        lockedBy = null,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - create then getById round-trip preserves all fields`() = runTest {
        val period = makePeriod(
            id = "period-01",
            periodName = "April 2026",
            startDate = "2026-04-01",
            endDate = "2026-04-30",
            status = PeriodStatus.OPEN,
            fiscalYearStart = "2026-01-01",
        )
        val createResult = repo.create(period)
        assertIs<Result.Success<Unit>>(createResult)

        val fetchResult = repo.getById("period-01")
        assertIs<Result.Success<AccountingPeriod?>>(fetchResult)
        val fetched = fetchResult.data
        assertNotNull(fetched)
        assertEquals("period-01", fetched.id)
        assertEquals("April 2026", fetched.periodName)
        assertEquals("2026-04-01", fetched.startDate)
        assertEquals("2026-04-30", fetched.endDate)
        assertEquals(PeriodStatus.OPEN, fetched.status)
        assertEquals("2026-01-01", fetched.fiscalYearStart)
        assertFalse(fetched.isAdjustment)
        assertNull(fetched.lockedBy)
    }

    @Test
    fun `B - getAll emits all periods via Turbine`() = runTest {
        repo.create(makePeriod(id = "period-jan", periodName = "January 2026",
            startDate = "2026-01-01", endDate = "2026-01-31"))
        repo.create(makePeriod(id = "period-apr", periodName = "April 2026",
            startDate = "2026-04-01", endDate = "2026-04-30"))

        repo.getAll("store-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getPeriodForDate returns matching period`() = runTest {
        repo.create(makePeriod(id = "period-01", startDate = "2026-04-01", endDate = "2026-04-30"))

        val result = repo.getPeriodForDate("store-01", "2026-04-15")
        assertIs<Result.Success<AccountingPeriod?>>(result)
        assertNotNull(result.data)
        assertEquals("period-01", result.data!!.id)
    }

    @Test
    fun `D - getPeriodForDate returns null when no period covers the date`() = runTest {
        repo.create(makePeriod(id = "period-01", startDate = "2026-04-01", endDate = "2026-04-30"))

        val result = repo.getPeriodForDate("store-01", "2026-06-15")
        assertIs<Result.Success<AccountingPeriod?>>(result)
        assertNull(result.data)
    }

    @Test
    fun `E - getOpenPeriods returns only OPEN periods`() = runTest {
        repo.create(makePeriod(id = "period-open", startDate = "2026-04-01", endDate = "2026-04-30",
            status = PeriodStatus.OPEN))
        repo.create(makePeriod(id = "period-closed", startDate = "2026-03-01", endDate = "2026-03-31",
            periodName = "March 2026", status = PeriodStatus.CLOSED))

        val result = repo.getOpenPeriods("store-01")
        assertIs<Result.Success<List<AccountingPeriod>>>(result)
        assertEquals(1, result.data.size)
        assertEquals("period-open", result.data.first().id)
    }

    @Test
    fun `F - closePeriod changes status to CLOSED`() = runTest {
        repo.create(makePeriod(id = "period-01", status = PeriodStatus.OPEN))

        val closeResult = repo.closePeriod("period-01", now)
        assertIs<Result.Success<Unit>>(closeResult)

        val fetched = (repo.getById("period-01") as Result.Success).data
        assertNotNull(fetched)
        assertEquals(PeriodStatus.CLOSED, fetched.status)
    }

    @Test
    fun `G - lockPeriod changes status to LOCKED and sets lockedBy and lockedAt`() = runTest {
        repo.create(makePeriod(id = "period-01", status = PeriodStatus.CLOSED))

        val lockResult = repo.lockPeriod("period-01", "manager-01", now)
        assertIs<Result.Success<Unit>>(lockResult)

        val fetched = (repo.getById("period-01") as Result.Success).data
        assertNotNull(fetched)
        assertEquals(PeriodStatus.LOCKED, fetched.status)
        assertEquals("manager-01", fetched.lockedBy)
        assertNotNull(fetched.lockedAt)
    }

    @Test
    fun `H - reopenPeriod changes CLOSED back to OPEN`() = runTest {
        repo.create(makePeriod(id = "period-01", status = PeriodStatus.OPEN))
        repo.closePeriod("period-01", now)

        val reopenResult = repo.reopenPeriod("period-01", now)
        assertIs<Result.Success<Unit>>(reopenResult)

        val fetched = (repo.getById("period-01") as Result.Success).data
        assertNotNull(fetched)
        assertEquals(PeriodStatus.OPEN, fetched.status)
    }
}
