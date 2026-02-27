package com.zyntasolutions.zyntapos.domain.usecase.reports

import com.zyntasolutions.zyntapos.domain.model.report.CouponUsageData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant

/** Returns aggregated coupon redemption totals for a given date range. */
class GenerateCouponUsageReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting [CouponUsageData] containing total redemptions,
     *         total discount given, and a breakdown of redemption counts by coupon code.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<CouponUsageData> = flow {
        emit(reportRepository.getCouponUsage(from, to))
    }
}
