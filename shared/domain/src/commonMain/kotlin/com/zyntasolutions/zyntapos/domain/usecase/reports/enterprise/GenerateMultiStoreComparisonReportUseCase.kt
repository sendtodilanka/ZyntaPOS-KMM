package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.StoreSalesData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * Generates a multi-store sales comparison report across all stores for a date range.
 *
 * Allows head-office managers to compare revenue, transaction count, average
 * order value, and top-selling categories side-by-side across store locations.
 *
 * Computes growth percentages by comparing the selected period with the
 * immediately preceding period of equal duration. For example, if the selected
 * range is "this month" (March 1–31), the previous period is Feb 1–28.
 *
 * @param reportRepository Source for multi-store sales comparison data.
 */
class GenerateMultiStoreComparisonReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @param from Start of the reporting window (inclusive).
     * @param to   End of the reporting window (inclusive).
     * @return A [Flow] emitting the list of [StoreSalesData] per store location,
     *         enriched with growth percentages vs the previous period.
     */
    operator fun invoke(from: Instant, to: Instant): Flow<List<StoreSalesData>> = flow {
        val currentPeriod = reportRepository.getMultiStoreComparison(from, to)

        // Compute the previous period of equal duration
        val duration: Duration = to - from
        val prevFrom = from - duration
        val prevTo = from

        val previousPeriod = runCatching {
            reportRepository.getMultiStoreComparison(prevFrom, prevTo)
        }.getOrDefault(emptyList())

        val prevByStore = previousPeriod.associateBy { it.storeId }

        val enriched = currentPeriod.map { current ->
            val prev = prevByStore[current.storeId]
            current.copy(
                revenueGrowthPercent = computeGrowth(current.totalRevenue, prev?.totalRevenue),
                orderGrowthPercent = computeGrowth(current.orderCount.toDouble(), prev?.orderCount?.toDouble()),
            )
        }

        emit(enriched)
    }

    private fun computeGrowth(current: Double, previous: Double?): Double? {
        if (previous == null || previous == 0.0) return null
        return ((current - previous) / previous) * 100.0
    }
}
