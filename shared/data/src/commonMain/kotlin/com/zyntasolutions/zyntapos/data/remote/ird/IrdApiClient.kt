package com.zyntasolutions.zyntapos.data.remote.ird

import kotlinx.serialization.Serializable

/**
 * Payload submitted to the Sri Lanka IRD e-invoice API.
 * Serialized as JSON for the HTTP request body.
 */
@Serializable
data class IrdInvoicePayload(
    val invoiceId: String,
    val invoiceNumber: String,
    val invoiceDate: String,
    val customerName: String,
    val customerTaxId: String?,
    val lineItemsJson: String,
    val taxBreakdownJson: String,
    val subtotal: Double,
    val totalTax: Double,
    val total: Double,
    val currency: String,
    val storeId: String,
)

/**
 * Response from the IRD e-invoice API.
 * Maps to the IRD API JSON response body.
 */
@Serializable
data class IrdApiResponse(
    val success: Boolean,
    val referenceNumber: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

/**
 * Platform-specific IRD (Sri Lanka Inland Revenue Department) e-invoice API client.
 *
 * Implements mTLS (mutual TLS) using the client certificate from
 * [endpoint], [certPath], and [certPassword] configuration parameters.
 *
 * ## Platform implementations
 * - **Android actual:** Ktor with OkHttp engine; certificate loaded via JVM `KeyStore` API.
 * - **JVM actual:** Ktor with CIO engine; certificate loaded via JVM `KeyStore` API.
 *
 * ## Security notes
 * - Certificate file must be a PKCS12 (.p12) file.
 * - If [certPath] is blank or the file doesn't exist, the client falls back to
 *   standard HTTPS without a client certificate (suitable for testing/staging).
 * - Never log [certPassword] or certificate bytes.
 *
 * ## Koin registration (platform-specific)
 * ```kotlin
 * // androidDataModule / desktopDataModule
 * single { IrdApiClient(
 *     endpoint     = BuildConfig.ZYNTA_IRD_API_ENDPOINT,
 *     certPath     = BuildConfig.ZYNTA_IRD_CLIENT_CERTIFICATE_PATH,
 *     certPassword = BuildConfig.ZYNTA_IRD_CERTIFICATE_PASSWORD,
 * ) }
 * ```
 *
 * @param endpoint     Full base URL of the IRD API (e.g. `https://einvoice.ird.gov.lk/api/v1`).
 * @param certPath     Absolute path to the PKCS12 client certificate (.p12 file).
 * @param certPassword Password for the PKCS12 client certificate.
 */
expect class IrdApiClient(
    endpoint: String,
    certPath: String,
    certPassword: String,
) {
    /**
     * Submits an e-invoice to the IRD API endpoint `POST {endpoint}/invoices`.
     *
     * On HTTP success (2xx), returns [IrdApiResponse] with `success = true` and
     * the IRD-assigned `referenceNumber`.
     *
     * On failure (network error, 4xx, 5xx), returns [IrdApiResponse] with
     * `success = false` and the appropriate `errorCode` / `errorMessage`.
     *
     * @param payload Serialized invoice data to submit.
     * @return [IrdApiResponse] with outcome details.
     */
    suspend fun submitInvoice(payload: IrdInvoicePayload): IrdApiResponse

    /** Releases HTTP client resources. Must be called when the client is no longer needed. */
    fun close()
}
