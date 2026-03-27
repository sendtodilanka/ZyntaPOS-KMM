package com.zyntasolutions.zyntapos.domain.usecase.register

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import com.zyntasolutions.zyntapos.domain.printer.ZReportPrinterPort
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildRegisterSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

// ─────────────────────────────────────────────────────────────────────────────
// Fakes
// ─────────────────────────────────────────────────────────────────────────────

private class FakeZReportPrinterPort : ZReportPrinterPort {
    var lastPrintedSession: RegisterSession? = null
    var shouldFail = false

    override suspend fun printZReport(session: RegisterSession): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("Printer error"))
        lastPrintedSession = session
        return Result.Success(Unit)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PrintZReportUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class PrintZReportUseCaseTest {

    @Test
    fun `invoke_delegatesToZReportPrinterPort`() = runTest {
        val port = FakeZReportPrinterPort()
        val session = buildRegisterSession(id = "session-01")

        val result = PrintZReportUseCase(port).invoke(session)

        assertIs<Result.Success<*>>(result)
        assert(port.lastPrintedSession?.id == "session-01")
    }

    @Test
    fun `printerFailure_returnsError`() = runTest {
        val port = FakeZReportPrinterPort().apply { shouldFail = true }

        val result = PrintZReportUseCase(port).invoke(buildRegisterSession())

        assertIs<Result.Error>(result)
    }
}
