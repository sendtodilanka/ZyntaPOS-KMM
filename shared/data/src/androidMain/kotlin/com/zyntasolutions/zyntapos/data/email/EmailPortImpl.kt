package com.zyntasolutions.zyntapos.data.email

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.port.EmailPort

/**
 * Android implementation of [EmailPort].
 *
 * Opens the system email chooser pre-filled with the recipient address and
 * subject line. Uses [Intent.ACTION_SENDTO] with a `mailto:` URI so Android
 * routes the intent only to email clients (not generic share targets).
 *
 * The intent is started with [Intent.FLAG_ACTIVITY_NEW_TASK] so it can be
 * launched from the Application context without an Activity reference.
 *
 * PDF attachment is deferred to a future sprint that integrates a PDF
 * rendering library. For now, the mail client is opened with the subject
 * pre-filled and the user completes the draft.
 *
 * @param context Application context — injected by Koin via [androidContext()].
 */
class EmailPortImpl(private val context: Context) : EmailPort {

    override suspend fun sendReceiptEmail(
        to: String,
        subject: String,
        pdfBytes: ByteArray,
    ): Result<Unit> {
        return try {
            val mailtoUri = Uri.parse("mailto:$to?subject=${Uri.encode(subject)}")
            val intent = Intent(Intent.ACTION_SENDTO, mailtoUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
