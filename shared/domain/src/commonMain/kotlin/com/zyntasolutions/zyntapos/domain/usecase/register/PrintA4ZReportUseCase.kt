package com.zyntasolutions.zyntapos.domain.usecase.register

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.printer.A4InvoicePrinterPort
import com.zyntasolutions.zyntapos.domain.repository.RegisterRepository
import com.zyntasolutions.zyntapos.domain.usecase.auth.CheckPermissionUseCase

/**
 * Generates and delivers an A4 Z-report PDF for a completed register session.
 *
 * RBAC gated: the active user must hold [Permission.CLOSE_REGISTER].
 */
class PrintA4ZReportUseCase(
    private val registerRepository: RegisterRepository,
    private val printerPort: A4InvoicePrinterPort,
    private val checkPermission: CheckPermissionUseCase,
) {

    /**
     * Prints the A4 Z-report for [sessionId].
     *
     * @param sessionId UUID of the completed [RegisterSession].
     * @param userId    Active user requesting the print.
     * @return [Result.Success] on delivery; [Result.Error] on permission or print failure.
     */
    suspend fun execute(sessionId: String, userId: String): Result<Unit> {
        if (!checkPermission(userId, Permission.CLOSE_REGISTER)) {
            return Result.Error(
                com.zyntasolutions.zyntapos.core.result.AuthException(
                    "User $userId does not have CLOSE_REGISTER permission",
                )
            )
        }

        return when (val sessionResult = registerRepository.getSession(sessionId)) {
            is Result.Success -> printerPort.printA4ZReport(sessionResult.data)
            is Result.Error   -> sessionResult
            else              -> Result.Loading
        }
    }
}
