package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.report.WalletBalanceData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Generates a customer wallet balance report showing current balances for all wallets.
 *
 * Lists every active customer wallet with current balance, total credits, total
 * debits, and last transaction date. Useful for financial reconciliation and
 * identifying dormant wallet balances.
 *
 * @param reportRepository Source for wallet balance data.
 */
class GenerateWalletBalanceReportUseCase(
    private val reportRepository: ReportRepository,
) {
    /**
     * @return A [Flow] emitting the list of [WalletBalanceData] for all active customer wallets.
     */
    operator fun invoke(): Flow<List<WalletBalanceData>> = flow {
        emit(reportRepository.getWalletBalances())
    }
}
