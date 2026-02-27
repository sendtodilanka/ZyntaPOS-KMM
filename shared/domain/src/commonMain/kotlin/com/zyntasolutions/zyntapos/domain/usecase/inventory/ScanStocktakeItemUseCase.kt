package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.StocktakeCount
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.StocktakeRepository
import kotlin.time.Clock

/**
 * Processes a barcode scan during an active stocktake session.
 *
 * Resolves the scanned [barcode] to a [com.zyntasolutions.zyntapos.domain.model.Product]
 * via [ProductRepository], increments the counted quantity by 1 in the active
 * [com.zyntasolutions.zyntapos.domain.model.StocktakeSession], and returns the
 * updated [StocktakeCount].
 *
 * To manually set an explicit quantity (rather than scan-increment), call
 * [StocktakeRepository.updateCount] directly.
 *
 * @param productRepository   Barcode-to-product lookup.
 * @param stocktakeRepository Stocktake session and count persistence.
 */
class ScanStocktakeItemUseCase(
    private val productRepository: ProductRepository,
    private val stocktakeRepository: StocktakeRepository,
) {

    /**
     * Records one scanned unit of [barcode] against [sessionId].
     *
     * @param sessionId UUID of the active [com.zyntasolutions.zyntapos.domain.model.StocktakeSession].
     * @param barcode   Raw barcode value read by the scanner.
     * @return [Result.Success] with the updated [StocktakeCount];
     *         [Result.Error] if the barcode is blank, product not found, or session invalid.
     */
    suspend fun execute(sessionId: String, barcode: String): Result<StocktakeCount> {
        if (barcode.isBlank()) {
            return Result.Error(
                ValidationException("Barcode must not be blank.", field = "barcode", rule = "BARCODE_BLANK")
            )
        }

        // Resolve product from barcode
        val productResult = productRepository.getByBarcode(barcode)
        if (productResult is Result.Error) return productResult

        val product = (productResult as Result.Success).data

        // Determine current count for this barcode (may be 0 if first scan)
        val existingCountsResult = stocktakeRepository.getCountsForSession(sessionId)
        val currentQty = if (existingCountsResult is Result.Success) {
            existingCountsResult.data.firstOrNull { it.barcode == barcode }?.countedQty ?: 0
        } else {
            0
        }

        val newQty = currentQty + 1

        // Persist the incremented count
        val updateResult = stocktakeRepository.updateCount(sessionId, barcode, newQty)
        if (updateResult is Result.Error) return updateResult

        // Build and return the updated count record
        val updatedCount = StocktakeCount(
            productId = product.id,
            barcode = barcode,
            productName = product.name,
            systemQty = product.stockQty.toInt(),
            countedQty = newQty,
            scannedAt = Clock.System.now().toEpochMilliseconds(),
        )
        return Result.Success(updatedCount)
    }
}
