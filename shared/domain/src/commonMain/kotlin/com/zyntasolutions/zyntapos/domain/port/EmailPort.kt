package com.zyntasolutions.zyntapos.domain.port

import com.zyntasolutions.zyntapos.core.result.Result

/**
 * Domain port for sending transactional email messages.
 *
 * Implementations:
 * - Android: generates PDF and shares via `Intent.ACTION_SEND`
 * - JVM Desktop: opens system `mailto:` URI or uses `javax.mail`
 *
 * Located in `domain/port/` alongside other infrastructure ports.
 */
interface EmailPort {

    /**
     * Sends a receipt PDF as an email attachment.
     *
     * @param to       Recipient email address.
     * @param subject  Email subject line (e.g. "Receipt #ORD-0042 from ZyntaPOS Store").
     * @param pdfBytes Rendered receipt PDF as a byte array.
     * @return [Result.Success] if the email was submitted to the mail system;
     *         [Result.Error] if the send failed.
     */
    suspend fun sendReceiptEmail(
        to: String,
        subject: String,
        pdfBytes: ByteArray,
    ): Result<Unit>
}
