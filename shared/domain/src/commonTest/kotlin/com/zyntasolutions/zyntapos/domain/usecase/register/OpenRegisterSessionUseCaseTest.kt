package com.zyntasolutions.zyntapos.domain.usecase.register

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeRegisterRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — OpenRegisterSessionUseCase Unit Tests (commonTest)
 *
 * Coverage:
 *  A.  Negative opening balance returns MIN_VALUE ValidationException before repository call
 *  B.  Zero opening balance is valid (passes to repository)
 *  C.  Positive opening balance is valid (passes to repository, session returned)
 *  D.  Repository SESSION_ALREADY_OPEN error propagates correctly
 *  E.  Repository failure propagates (shouldFailOpen)
 */
class OpenRegisterSessionUseCaseTest {

    private fun makeUseCase(
        shouldFailOpen: Boolean = false,
        activeSessionAlreadyExists: Boolean = false,
    ): Pair<OpenRegisterSessionUseCase, FakeRegisterRepository> {
        val repo = FakeRegisterRepository().also {
            it.shouldFailOpen = shouldFailOpen
            it.activeSessionAlreadyExists = activeSessionAlreadyExists
        }
        return OpenRegisterSessionUseCase(repo) to repo
    }

    @Test
    fun `A - negative opening balance returns MIN_VALUE error before repository call`() = runTest {
        val (useCase, repo) = makeUseCase()

        val result = useCase(registerId = "reg-01", openingBalance = -0.01, userId = "user-1")

        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("MIN_VALUE", ex.rule)
        assertEquals("openingBalance", ex.field)
        // Repository must NOT have been called
        assertEquals(0, repo.sessions.size)
    }

    @Test
    fun `B - zero opening balance is valid and creates session`() = runTest {
        val (useCase, repo) = makeUseCase()

        val result = useCase(registerId = "reg-01", openingBalance = 0.0, userId = "user-1")

        assertIs<Result.Success<RegisterSession>>(result)
        assertEquals(1, repo.sessions.size)
        assertEquals(0.0, result.data.openingBalance)
    }

    @Test
    fun `C - positive opening balance creates session with correct balance`() = runTest {
        val (useCase, repo) = makeUseCase()

        val result = useCase(registerId = "reg-01", openingBalance = 500.0, userId = "cashier-1")

        assertIs<Result.Success<RegisterSession>>(result)
        assertEquals(500.0, result.data.openingBalance)
        assertEquals("reg-01", result.data.registerId)
        assertNotNull(repo.activeSession)
    }

    @Test
    fun `D - SESSION_ALREADY_OPEN error from repository propagates`() = runTest {
        val (useCase, _) = makeUseCase(activeSessionAlreadyExists = true)

        val result = useCase(registerId = "reg-01", openingBalance = 100.0, userId = "user-1")

        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("SESSION_ALREADY_OPEN", ex.rule)
    }

    @Test
    fun `E - repository database failure propagates`() = runTest {
        val (useCase, _) = makeUseCase(shouldFailOpen = true)

        val result = useCase(registerId = "reg-01", openingBalance = 100.0, userId = "user-1")

        assertIs<Result.Error>(result)
    }
}
