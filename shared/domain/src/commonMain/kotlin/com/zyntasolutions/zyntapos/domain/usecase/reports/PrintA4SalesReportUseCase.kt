package com.zyntasolutions.zyntapos.domain.usecase.reports

import com.zyntasolutions.zyntapos.core.result.AuthException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.printer.A4InvoicePrinterPort
import com.zyntasolutions.zyntapos.domain.usecase.auth.CheckPermissionUseCase

/**
 * Prints or exports an A4 sales report PDF.
 *
 * RBAC gated: the active user must hold [Permission.EXPORT_REPORTS].
 *
 * Delegates rendering and delivery to [A4InvoicePrinterPort.printA4SalesReport].
 * On desktop this opens the system print dialog; on Android it triggers the
 * share sheet.
 *
 * @param printerPort     Infrastructure adapter for A4 PDF printing/export.
 * @param checkPermission RBAC check use case.
 */
class PrintA4SalesReportUseCase(
    private val printerPort: A4InvoicePrinterPort,
    private val checkPermission: CheckPermissionUseCase,
) {

    /**
     * Prints or exports the given [report].
     *
     * @param report  The [GenerateSalesReportUseCase.SalesReport] to render.
     * @param userId  Active user requesting the export; must have [Permission.EXPORT_REPORTS].
     * @return [Result.Success] on delivery; [Result.Error] on permission or print failure.
     */
    suspend fun execute(report: GenerateSalesReportUseCase.SalesReport, userId: String): Result<Unit> {
        if (!checkPermission(userId, Permission.EXPORT_REPORTS)) {
            return Result.Error(
                AuthException("User $userId does not have EXPORT_REPORTS permission")
            )
        }
        return printerPort.printA4SalesReport(report)
    }
}
