package com.zyntasolutions.zyntapos.domain.usecase.register

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.usecase.auth.CheckPermissionUseCase
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeA4InvoicePrinterPort
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeAuthRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeRegisterRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildRegisterSession
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildUser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [PrintA4ZReportUseCase].
 */
class PrintA4ZReportUseCaseTest {

    private fun makeCheckPermission(role: Role): CheckPermissionUseCase {
        val authRepo = FakeAuthRepository()
        val useCase = CheckPermissionUseCase(authRepo.getSession())
        useCase.updateSession(buildUser(id = "user-01", role = role))
        return useCase
    }

    @Test
    fun `print A4 Z-report - user with CLOSE_REGISTER - delegates to printer port`() = runTest {
        val registerRepo = FakeRegisterRepository()
        val printerPort = FakeA4InvoicePrinterPort()
        val checkPermission = makeCheckPermission(Role.CASHIER) // CASHIER has CLOSE_REGISTER
        val session = buildRegisterSession(id = "session-01")
        registerRepo.sessions.add(session)
        val useCase = PrintA4ZReportUseCase(registerRepo, printerPort, checkPermission)

        val result = useCase.execute("session-01", "user-01")

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, printerPort.zReportJobs.size)
        assertEquals("session-01", printerPort.zReportJobs.first().id)
    }

    @Test
    fun `print A4 Z-report - user without CLOSE_REGISTER - returns auth error`() = runTest {
        val registerRepo = FakeRegisterRepository()
        val printerPort = FakeA4InvoicePrinterPort()
        // STOCK_MANAGER does NOT have CLOSE_REGISTER
        val checkPermission = makeCheckPermission(Role.STOCK_MANAGER)
        val useCase = PrintA4ZReportUseCase(registerRepo, printerPort, checkPermission)

        val result = useCase.execute("session-01", "user-01")

        assertIs<Result.Error>(result)
        assertTrue(printerPort.zReportJobs.isEmpty())
    }

    @Test
    fun `print A4 Z-report - session not found - returns error`() = runTest {
        val registerRepo = FakeRegisterRepository()
        val printerPort = FakeA4InvoicePrinterPort()
        val checkPermission = makeCheckPermission(Role.STORE_MANAGER)
        val useCase = PrintA4ZReportUseCase(registerRepo, printerPort, checkPermission)

        val result = useCase.execute("non-existent", "user-01")

        assertIs<Result.Error>(result)
        assertTrue(printerPort.zReportJobs.isEmpty())
    }

    @Test
    fun `print A4 Z-report - printer fails - returns error`() = runTest {
        val registerRepo = FakeRegisterRepository()
        val printerPort = FakeA4InvoicePrinterPort().apply { shouldFail = true }
        val checkPermission = makeCheckPermission(Role.STORE_MANAGER)
        val session = buildRegisterSession(id = "session-01")
        registerRepo.sessions.add(session)
        val useCase = PrintA4ZReportUseCase(registerRepo, printerPort, checkPermission)

        val result = useCase.execute("session-01", "user-01")

        assertIs<Result.Error>(result)
    }
}
