package com.zyntasolutions.zyntapos.domain.usecase.auth

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeAuthRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Unit tests for [SetPinUseCase].
 *
 * Covers all business rules:
 * 1. Valid 4-digit PIN succeeds and delegates to [AuthRepository.updatePin].
 * 2. Valid 6-digit PIN succeeds and delegates to [AuthRepository.updatePin].
 * 3. PIN containing non-digit characters → [ValidationException] (INVALID_PIN_FORMAT).
 * 4. PIN shorter than 4 digits → [ValidationException] (INVALID_PIN_FORMAT).
 * 5. PIN longer than 6 digits → [ValidationException] (INVALID_PIN_FORMAT).
 * 6. Mismatched newPin / confirmPin → [ValidationException] (PIN_MISMATCH, field = "confirmPin").
 * 7. Valid PIN → delegates with the exact userId and newPin arguments.
 */
class SetPinUseCaseTest {

    // ─── Valid PINs ───────────────────────────────────────────────────────────

    @Test
    fun `valid 4-digit PIN - returns Success and calls updatePin`() = runTest {
        val repo = FakeAuthRepository()
        val result = SetPinUseCase(repo)(userId = "user-01", newPin = "1234", confirmPin = "1234")
        assertIs<Result.Success<Unit>>(result)
        assertEquals("1234", repo.pinToAccept)
    }

    @Test
    fun `valid 6-digit PIN - returns Success and calls updatePin`() = runTest {
        val repo = FakeAuthRepository()
        val result = SetPinUseCase(repo)(userId = "user-01", newPin = "123456", confirmPin = "123456")
        assertIs<Result.Success<Unit>>(result)
        assertEquals("123456", repo.pinToAccept)
    }

    // ─── Invalid PIN format ───────────────────────────────────────────────────

    @Test
    fun `PIN with letters - returns ValidationException with INVALID_PIN_FORMAT`() = runTest {
        val repo = FakeAuthRepository()
        val result = SetPinUseCase(repo)(userId = "user-01", newPin = "123a", confirmPin = "123a")
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("INVALID_PIN_FORMAT", ex.rule)
        assertEquals("newPin", ex.field)
        assertNull(repo.updatePinCalledWith, "updatePin must NOT be called on invalid format")
    }

    @Test
    fun `PIN too short (3 digits) - returns ValidationException with INVALID_PIN_FORMAT`() = runTest {
        val repo = FakeAuthRepository()
        val result = SetPinUseCase(repo)(userId = "user-01", newPin = "123", confirmPin = "123")
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("INVALID_PIN_FORMAT", ex.rule)
        assertEquals("newPin", ex.field)
        assertNull(repo.updatePinCalledWith, "updatePin must NOT be called when PIN is too short")
    }

    @Test
    fun `PIN too long (7 digits) - returns ValidationException with INVALID_PIN_FORMAT`() = runTest {
        val repo = FakeAuthRepository()
        val result = SetPinUseCase(repo)(userId = "user-01", newPin = "1234567", confirmPin = "1234567")
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("INVALID_PIN_FORMAT", ex.rule)
        assertEquals("newPin", ex.field)
        assertNull(repo.updatePinCalledWith, "updatePin must NOT be called when PIN is too long")
    }

    // ─── PIN mismatch ─────────────────────────────────────────────────────────

    @Test
    fun `mismatched PINs - returns ValidationException with PIN_MISMATCH on confirmPin field`() = runTest {
        val repo = FakeAuthRepository()
        val result = SetPinUseCase(repo)(userId = "user-01", newPin = "1234", confirmPin = "5678")
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("PIN_MISMATCH", ex.rule)
        assertEquals("confirmPin", ex.field)
        assertNull(repo.updatePinCalledWith, "updatePin must NOT be called on PIN mismatch")
    }

    // ─── Delegation ───────────────────────────────────────────────────────────

    @Test
    fun `valid PIN - delegates to authRepository updatePin with exact userId and newPin`() = runTest {
        val repo = FakeAuthRepository()
        SetPinUseCase(repo)(userId = "user-42", newPin = "9876", confirmPin = "9876")
        assertEquals(Pair("user-42", "9876"), repo.updatePinCalledWith)
    }
}
