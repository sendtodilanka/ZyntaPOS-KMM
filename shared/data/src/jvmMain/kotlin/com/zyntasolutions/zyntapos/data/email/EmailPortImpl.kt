package com.zyntasolutions.zyntapos.data.email

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.port.EmailPort
import java.awt.Desktop
import java.net.URI

/**
 * JVM Desktop implementation of [EmailPort].
 *
 * Opens the OS default email client via [Desktop.mail] with the recipient
 * address and subject pre-filled in a `mailto:` URI. The user completes
 * and sends the draft in their own mail client.
 *
 * PDF attachment is deferred to a future sprint. For now the mail client
 * is opened with only the recipient and subject set.
 *
 * Falls back gracefully when [Desktop] is not supported (headless JVM
 * environments) — returns [Result.Error] with a descriptive message.
 */
class EmailPortImpl : EmailPort {

    override suspend fun sendReceiptEmail(
        to: String,
        subject: String,
        pdfBytes: ByteArray,
    ): Result<Unit> {
        return try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
                return Result.Error(UnsupportedOperationException(
                    "Desktop mail is not supported in this environment"
                ))
            }
            val encodedSubject = subject.replace(" ", "%20")
                .replace("&", "%26")
                .replace("#", "%23")
            val mailtoUri = URI("mailto:$to?subject=$encodedSubject")
            Desktop.getDesktop().mail(mailtoUri)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
