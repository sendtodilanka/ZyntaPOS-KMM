package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.PayrollEntryStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ZyntaPOS — CalculatePayrollUseCase Unit Tests (commonTest)
 *
 * Pure computation class (no-arg constructor, no I/O). Tests confirm:
 *  A.  Negative baseSalary returns NON_NEGATIVE error on "baseSalary" field
 *  B.  Negative overtimeHours returns NON_NEGATIVE error on "overtimeHours" field
 *  C.  Negative overtimeRate returns NON_NEGATIVE error on "overtimeRate" field
 *  D.  Negative deductions returns NON_NEGATIVE error on "deductions" field
 *  E.  Zero values for all numeric params are valid
 *  F.  Overtime pay calculated as overtimeHours * overtimeRate
 *  G.  Net pay calculated as baseSalary + overtimePay - deductions
 *  H.  Resulting entry has DRAFT status
 *  I.  Entry has non-blank id and matching employeeId / period strings
 *  J.  deductionNotes null preserved in result
 *  K.  deductionNotes non-null preserved in result
 */
class CalculatePayrollUseCaseTest {

    private val useCase = CalculatePayrollUseCase()

    // ── Validation guards ─────────────────────────────────────────────────────

    @Test
    fun `A - negative baseSalary returns NON_NEGATIVE error`() {
        val result = useCase(
            employeeId = "e1", periodStart = "2026-03-01", periodEnd = "2026-03-31",
            baseSalary = -1.0, overtimeHours = 0.0, overtimeRate = 0.0, deductions = 0.0,
        )
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("NON_NEGATIVE", ex.rule)
        assertEquals("baseSalary", ex.field)
    }

    @Test
    fun `B - negative overtimeHours returns NON_NEGATIVE error`() {
        val result = useCase(
            employeeId = "e1", periodStart = "2026-03-01", periodEnd = "2026-03-31",
            baseSalary = 1000.0, overtimeHours = -1.0, overtimeRate = 0.0, deductions = 0.0,
        )
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("NON_NEGATIVE", ex.rule)
        assertEquals("overtimeHours", ex.field)
    }

    @Test
    fun `C - negative overtimeRate returns NON_NEGATIVE error`() {
        val result = useCase(
            employeeId = "e1", periodStart = "2026-03-01", periodEnd = "2026-03-31",
            baseSalary = 1000.0, overtimeHours = 0.0, overtimeRate = -5.0, deductions = 0.0,
        )
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("NON_NEGATIVE", ex.rule)
        assertEquals("overtimeRate", ex.field)
    }

    @Test
    fun `D - negative deductions returns NON_NEGATIVE error`() {
        val result = useCase(
            employeeId = "e1", periodStart = "2026-03-01", periodEnd = "2026-03-31",
            baseSalary = 1000.0, overtimeHours = 0.0, overtimeRate = 0.0, deductions = -10.0,
        )
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("NON_NEGATIVE", ex.rule)
        assertEquals("deductions", ex.field)
    }

    // ── Calculation accuracy ──────────────────────────────────────────────────

    @Test
    fun `E - all zero values produce zero netPay and succeed`() {
        val result = useCase(
            employeeId = "e1", periodStart = "2026-03-01", periodEnd = "2026-03-31",
            baseSalary = 0.0, overtimeHours = 0.0, overtimeRate = 0.0, deductions = 0.0,
        )
        assertIs<Result.Success<*>>(result)
        assertEquals(0.0, (result as Result.Success).data.netPay)
    }

    @Test
    fun `F - overtimePay equals overtimeHours times overtimeRate`() {
        val result = useCase(
            employeeId = "e1", periodStart = "2026-03-01", periodEnd = "2026-03-31",
            baseSalary = 0.0, overtimeHours = 10.0, overtimeRate = 250.0, deductions = 0.0,
        ) as Result.Success
        assertEquals(2500.0, result.data.overtimePay)
    }

    @Test
    fun `G - netPay equals baseSalary plus overtimePay minus deductions`() {
        val result = useCase(
            employeeId = "e1", periodStart = "2026-03-01", periodEnd = "2026-03-31",
            baseSalary = 80000.0, overtimeHours = 5.0, overtimeRate = 500.0, deductions = 1000.0,
        ) as Result.Success
        // overtimePay = 5 * 500 = 2500
        // netPay = 80000 + 2500 - 1000 = 81500
        assertEquals(2500.0, result.data.overtimePay)
        assertEquals(81500.0, result.data.netPay)
    }

    // ── Result fields ─────────────────────────────────────────────────────────

    @Test
    fun `H - resulting entry has DRAFT status`() {
        val result = useCase(
            employeeId = "e1", periodStart = "2026-03-01", periodEnd = "2026-03-31",
            baseSalary = 50000.0, overtimeHours = 0.0, overtimeRate = 0.0, deductions = 0.0,
        ) as Result.Success
        assertEquals(PayrollEntryStatus.DRAFT, result.data.status)
    }

    @Test
    fun `I - entry has non-blank id and matching employee and period`() {
        val result = useCase(
            employeeId = "emp-42", periodStart = "2026-03-01", periodEnd = "2026-03-31",
            baseSalary = 50000.0, overtimeHours = 0.0, overtimeRate = 0.0, deductions = 0.0,
        ) as Result.Success
        assertTrue(result.data.id.isNotBlank())
        assertEquals("emp-42", result.data.employeeId)
        assertEquals("2026-03-01", result.data.periodStart)
        assertEquals("2026-03-31", result.data.periodEnd)
    }

    @Test
    fun `J - null deductionNotes preserved in result`() {
        val result = useCase(
            employeeId = "e1", periodStart = "2026-03-01", periodEnd = "2026-03-31",
            baseSalary = 50000.0, overtimeHours = 0.0, overtimeRate = 0.0, deductions = 0.0,
            deductionNotes = null,
        ) as Result.Success
        assertNull(result.data.deductionNotes)
    }

    @Test
    fun `K - non-null deductionNotes preserved in result`() {
        val result = useCase(
            employeeId = "e1", periodStart = "2026-03-01", periodEnd = "2026-03-31",
            baseSalary = 50000.0, overtimeHours = 0.0, overtimeRate = 0.0, deductions = 500.0,
            deductionNotes = "EPF/ETF deductions",
        ) as Result.Success
        assertNotNull(result.data.deductionNotes)
        assertEquals("EPF/ETF deductions", result.data.deductionNotes)
    }
}
