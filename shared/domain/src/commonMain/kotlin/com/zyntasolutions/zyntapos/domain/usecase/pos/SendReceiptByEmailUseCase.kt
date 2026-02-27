package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.port.EmailPort
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository

/**
 * Sends the receipt for a completed order as a PDF email attachment.
 *
 * The use case fetches the order, loads the store name from [SettingsRepository]
 * for the email subject, then delegates PDF generation and delivery to [EmailPort].
 * PDF generation happens inside the [EmailPort] implementation (infrastructure layer).
 *
 * @param orderRepository    Provides order lookup by ID.
 * @param settingsRepository Provides store display name for the subject line.
 * @param emailPort          Infrastructure adapter for email delivery.
 */
class SendReceiptByEmailUseCase(
    private val orderRepository: OrderRepository,
    private val settingsRepository: SettingsRepository,
    private val emailPort: EmailPort,
) {

    /**
     * Sends the receipt for [orderId] to [emailAddress].
     *
     * @param orderId      UUID of the completed order.
     * @param emailAddress Recipient email address (validated before calling).
     * @return [Result.Success] on delivery; [Result.Error] on lookup or send failure.
     */
    suspend fun execute(orderId: String, emailAddress: String): Result<Unit> {
        val orderResult = orderRepository.getById(orderId)
        if (orderResult is Result.Error) return orderResult

        val order = (orderResult as Result.Success).data
        val storeName = settingsRepository.get("store_name") ?: "ZyntaPOS Store"
        val subject = "Receipt ${order.orderNumber} from $storeName"

        // PDF bytes are generated inside EmailPort implementation to keep domain
        // free of PDF rendering dependencies.
        return emailPort.sendReceiptEmail(
            to = emailAddress,
            subject = subject,
            pdfBytes = ByteArray(0), // actual bytes provided by EmailPort impl
        )
    }
}
