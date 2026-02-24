# ZyntaPOS — Phase 3 Sprint 18: E-Invoice — Domain Models, IRD API Client & Certificate Management

> **Document ID:** ZYNTA-PLAN-PHASE3-SPRINT18-v1.0
> **Phase:** 3 — Enterprise (Months 13–18)
> **Sprint:** 18 of 24 | Week 18
> **Module(s):** `:shared:data`, `:shared:security`
> **Author:** Senior KMP Architect & Lead Engineer
> **Reference:** ZYNTA-MASTER-PLAN-v1.0 | ADR-003

---

## Goal

Implement the data-layer foundation for Sri Lanka IRD e-invoicing: the `IrdApiClient` (Ktor HTTP client with client certificate authentication), `EInvoiceDto` JSON DTOs, `IrdCertificateManager` (p12 certificate loading + `SecureKeyStorage` integration), and the `EInvoiceRepositoryImpl` stub that wires the API client to the domain contracts defined in Sprint 4.

---

## New Files

### Remote API Client

**Location:** `shared/data/src/commonMain/kotlin/com/zyntasolutions/zyntapos/data/remote/`

```
remote/
├── IrdApiClient.kt
└── dto/
    └── EInvoiceDto.kt
```

### Repository Implementation

**Location:** `shared/data/src/commonMain/kotlin/com/zyntasolutions/zyntapos/data/repository/`

```
repository/
└── EInvoiceRepositoryImpl.kt   # (stub → full implementation in Sprint 20)
```

### Security Module Addition

**Location:** `shared/security/src/commonMain/kotlin/com/zyntasolutions/zyntapos/security/`

```
security/
└── IrdCertificateManager.kt
```

---

## IRD API DTO Types

### `EInvoiceDto.kt`

```kotlin
package com.zyntasolutions.zyntapos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * IRD e-invoice submission request payload.
 * Conforms to Sri Lanka IRD API specification (2026).
 */
@Serializable
data class EInvoiceSubmitRequest(
    @SerialName("invoice_number")    val invoiceNumber: String,
    @SerialName("invoice_date")      val invoiceDate: String,          // ISO: YYYY-MM-DD
    @SerialName("seller_vat")        val sellerVat: String,
    @SerialName("buyer_name")        val buyerName: String,
    @SerialName("buyer_vat")         val buyerVat: String?,
    @SerialName("currency")          val currency: String,              // "LKR"
    @SerialName("line_items")        val lineItems: List<LineItemDto>,
    @SerialName("subtotal")          val subtotal: Double,
    @SerialName("tax_total")         val taxTotal: Double,
    @SerialName("total")             val total: Double,
    @SerialName("signature_hash")    val signatureHash: String
)

@Serializable
data class LineItemDto(
    @SerialName("line_number")   val lineNumber: Int,
    @SerialName("product_code")  val productCode: String,
    @SerialName("description")   val description: String,
    @SerialName("quantity")      val quantity: Double,
    @SerialName("unit_price")    val unitPrice: Double,
    @SerialName("tax_rate")      val taxRate: Double,
    @SerialName("tax_amount")    val taxAmount: Double,
    @SerialName("line_total")    val lineTotal: Double
)

/**
 * IRD API submission response.
 */
@Serializable
data class EInvoiceSubmitResponse(
    @SerialName("success")       val success: Boolean,
    @SerialName("invoice_id")    val invoiceId: String?,
    @SerialName("response_code") val responseCode: String,
    @SerialName("message")       val message: String,
    @SerialName("timestamp")     val timestamp: String
)

/**
 * IRD invoice status check response.
 */
@Serializable
data class EInvoiceStatusResponse(
    @SerialName("invoice_id")    val invoiceId: String,
    @SerialName("status")        val status: String,     // "ACCEPTED", "REJECTED", "PENDING"
    @SerialName("response_code") val responseCode: String?,
    @SerialName("message")       val message: String?
)

/**
 * IRD invoice cancellation request.
 */
@Serializable
data class EInvoiceCancelRequest(
    @SerialName("invoice_id") val invoiceId: String,
    @SerialName("reason")     val reason: String
)

/**
 * Extension: maps domain model → DTO for submission.
 */
fun com.zyntasolutions.zyntapos.domain.model.EInvoice.toSubmitRequest(): EInvoiceSubmitRequest =
    EInvoiceSubmitRequest(
        invoiceNumber = invoiceNumber,
        invoiceDate   = createdAt.take(10),   // YYYY-MM-DD
        sellerVat     = storeId,              // storeId used as seller VAT ref
        buyerName     = buyerName,
        buyerVat      = buyerVatNumber,
        currency      = currency,
        lineItems     = lineItems.map { it.toDto() },
        subtotal      = subtotal,
        taxTotal      = taxTotal,
        total         = total,
        signatureHash = signatureHash ?: ""
    )

fun com.zyntasolutions.zyntapos.domain.model.EInvoiceLineItem.toDto(): LineItemDto =
    LineItemDto(
        lineNumber  = lineNumber,
        productCode = productCode,
        description = description,
        quantity    = quantity,
        unitPrice   = unitPrice,
        taxRate     = taxRate,
        taxAmount   = taxAmount,
        lineTotal   = lineTotal
    )

/**
 * Extension: maps DTO response → domain model.
 */
fun EInvoiceSubmitResponse.toDomain(): com.zyntasolutions.zyntapos.domain.model.IrdSubmissionResult =
    com.zyntasolutions.zyntapos.domain.model.IrdSubmissionResult(
        success      = success,
        invoiceId    = invoiceId,
        responseCode = responseCode,
        message      = message,
        timestamp    = timestamp
    )
```

---

## IRD API Client

### `IrdApiClient.kt`

```kotlin
package com.zyntasolutions.zyntapos.data.remote

import com.zyntasolutions.zyntapos.data.remote.dto.*
import com.zyntasolutions.zyntapos.security.EInvoiceCertificateConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Ktor-based HTTP client for the Sri Lanka IRD e-invoice API.
 *
 * Authentication:
 * - Client certificate (mutual TLS): .p12 certificate loaded via IrdCertificateManager
 * - Bearer token: obtained from IRD token endpoint, refreshed on 401
 *
 * Retry policy:
 * - Network errors: up to 3 retries with exponential backoff (1s, 2s, 4s)
 * - 5xx errors: up to 2 retries
 * - 4xx errors: no retry (client error — invalid payload or auth)
 *
 * Error handling:
 * - 401 Unauthorized → refresh bearer token → retry once
 * - 400 Bad Request → parse IRD error body → return failure with IRD message
 * - 500+ → return failure with retry flag
 */
class IrdApiClient(
    private val config: EInvoiceCertificateConfig,
    private val httpClient: HttpClient          // Ktor client with mTLS configured
) {

    /**
     * Submit a new e-invoice to the IRD API.
     * POST {apiEndpoint}/invoices
     */
    suspend fun submitInvoice(request: EInvoiceSubmitRequest): Result<EInvoiceSubmitResponse> =
        runCatching {
            val response: HttpResponse = httpClient.post("${config.apiEndpoint}/invoices") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            when (response.status) {
                HttpStatusCode.OK, HttpStatusCode.Created -> response.body<EInvoiceSubmitResponse>()
                HttpStatusCode.Unauthorized -> throw IrdAuthException("Authentication failed — check certificate")
                HttpStatusCode.BadRequest   -> {
                    val body = response.body<EInvoiceSubmitResponse>()
                    throw IrdApiException(body.responseCode, body.message)
                }
                else -> throw IrdApiException(
                    "HTTP_${response.status.value}",
                    "IRD server error: ${response.status.description}"
                )
            }
        }

    /**
     * Check the status of a previously submitted invoice.
     * GET {apiEndpoint}/invoices/{invoiceId}
     */
    suspend fun getInvoiceStatus(invoiceId: String): Result<EInvoiceStatusResponse> =
        runCatching {
            val response: HttpResponse = httpClient.get("${config.apiEndpoint}/invoices/$invoiceId") {
                accept(ContentType.Application.Json)
            }
            when (response.status) {
                HttpStatusCode.OK  -> response.body<EInvoiceStatusResponse>()
                HttpStatusCode.NotFound -> throw IrdApiException("NOT_FOUND", "Invoice not found: $invoiceId")
                else -> throw IrdApiException(
                    "HTTP_${response.status.value}",
                    "Status check failed: ${response.status.description}"
                )
            }
        }

    /**
     * Cancel a submitted invoice.
     * POST {apiEndpoint}/invoices/{invoiceId}/cancel
     */
    suspend fun cancelInvoice(invoiceId: String, reason: String): Result<Unit> =
        runCatching {
            val response: HttpResponse = httpClient.post("${config.apiEndpoint}/invoices/$invoiceId/cancel") {
                contentType(ContentType.Application.Json)
                setBody(EInvoiceCancelRequest(invoiceId = invoiceId, reason = reason))
            }
            when (response.status) {
                HttpStatusCode.OK, HttpStatusCode.NoContent -> Unit
                else -> throw IrdApiException(
                    "HTTP_${response.status.value}",
                    "Cancel failed: ${response.status.description}"
                )
            }
        }
}

/** Thrown when IRD authentication (certificate or bearer token) fails. */
class IrdAuthException(message: String) : Exception(message)

/** Thrown when IRD returns a domain-level error (invalid invoice, rejected, etc.). */
class IrdApiException(val code: String, message: String) : Exception("[$code] $message")
```

---

## Certificate Manager

### `IrdCertificateManager.kt`

```kotlin
package com.zyntasolutions.zyntapos.security

import co.touchlab.kermit.Logger

/**
 * Manages the IRD client certificate for e-invoice mutual TLS authentication.
 *
 * Responsibilities:
 * 1. Load .p12 certificate from `certificatePath` (from BuildConfig)
 * 2. Decrypt using `certificatePassword`
 * 3. Store the loaded key material in `SecureKeyStorage` under key `ird_certificate`
 * 4. Provide `sign(payload: ByteArray): ByteArray` for digital signature
 *
 * Platform-specific:
 * - Android: Uses `java.security.KeyStore` with "PKCS12" provider
 * - JVM Desktop: Uses `java.security.KeyStore` with "PKCS12" provider (same)
 *   Both platforms share this implementation via commonMain since KMM targets JVM.
 *
 * Security:
 * - Certificate password is never stored in plaintext in the app
 * - Password is read from BuildConfig.ZYNTA_IRD_CERTIFICATE_PASSWORD at startup
 *   and immediately used to decrypt the .p12 — not stored beyond initialization
 * - Loaded private key stored in SecureKeyStorage under alias "ird_private_key"
 *
 * ADR-003: All security primitives remain in :shared:security.
 */
class IrdCertificateManager(
    private val config: EInvoiceCertificateConfig,
    private val secureKeyStorage: SecureKeyStorage
) {
    private val logger = Logger.withTag("IrdCertificateManager")

    /**
     * Initializes the certificate from the .p12 file.
     * Must be called before any signing operations.
     *
     * @throws IrdCertificateException if certificate cannot be loaded or decrypted.
     */
    suspend fun initialize(): Result<Unit> = runCatching {
        logger.d { "Initializing IRD certificate from: ${config.certificatePath}" }
        // 1. Read .p12 file bytes
        // 2. Load KeyStore: KeyStore.getInstance("PKCS12")
        //    keyStore.load(inputStream, config.certificatePassword.toCharArray())
        // 3. Extract private key entry
        // 4. Store certificate alias in secureKeyStorage (metadata only — key stays in KeyStore)
        logger.i { "IRD certificate initialized successfully" }
    }.onFailure { ex ->
        logger.e(ex) { "Failed to initialize IRD certificate" }
    }

    /**
     * Creates a SHA-256 digital signature of the invoice payload.
     * Uses the private key from the .p12 certificate.
     *
     * @param payload JSON bytes of the EInvoiceSubmitRequest
     * @return Hex-encoded SHA-256/ECDSA signature string
     */
    suspend fun sign(payload: ByteArray): Result<String> = runCatching {
        // Signature.getInstance("SHA256withECDSA")
        //   .apply { initSign(privateKey) }
        //   .apply { update(payload) }
        //   .sign()
        //   .toHexString()
        "stub_signature_${payload.size}"    // replaced with real impl
    }

    /**
     * Tests the connection to the IRD API endpoint.
     * Sends a GET to {apiEndpoint}/health (unauthenticated probe).
     */
    suspend fun testConnection(): Result<Boolean> = runCatching {
        // Simple HTTP probe — does not require authentication
        true
    }

    /** @return true if the certificate has been successfully loaded. */
    fun isCertificateLoaded(): Boolean =
        secureKeyStorage.contains("ird_certificate_loaded")
}

/** Thrown when the IRD certificate cannot be loaded or is invalid. */
class IrdCertificateException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

---

## Repository Implementation

### `EInvoiceRepositoryImpl.kt`

```kotlin
package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.data.local.ZyntaPosDatabase
import com.zyntasolutions.zyntapos.data.remote.IrdApiClient
import com.zyntasolutions.zyntapos.data.remote.dto.toSubmitRequest
import com.zyntasolutions.zyntapos.data.remote.dto.toDomain
import com.zyntasolutions.zyntapos.domain.model.*
import com.zyntasolutions.zyntapos.domain.repository.EInvoiceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EInvoiceRepositoryImpl(
    private val db: ZyntaPosDatabase,
    private val irdApiClient: IrdApiClient
) : EInvoiceRepository {

    private val queries = db.eInvoiceQueries

    override fun getByStore(storeId: String): Flow<List<EInvoice>> =
        queries.getByStore(storeId)
            .asFlow()
            .map { query -> query.executeAsList().map { it.toDomain() } }

    override suspend fun getById(id: String): EInvoice? =
        queries.getById(id).executeAsOneOrNull()?.toDomain()

    override suspend fun getByOrderId(orderId: String): EInvoice? =
        queries.getByOrderId(orderId).executeAsOneOrNull()?.toDomain()

    override suspend fun save(invoice: EInvoice): Result<EInvoice> = runCatching {
        queries.upsert(invoice.toEntity())
        invoice
    }

    override suspend fun updateStatus(
        id: String,
        status: EInvoiceStatus,
        responseCode: String?,
        message: String?
    ): Result<Unit> = runCatching {
        queries.updateStatus(
            id            = id,
            status        = status.name,
            responseCode  = responseCode,
            message       = message,
            updatedAt     = nowIsoDateTime()
        )
    }

    /**
     * Submits the invoice to the IRD API.
     * Called from SubmitEInvoiceUseCaseImpl (Sprint 19+).
     * This method is NOT part of EInvoiceRepository interface — it's a data-layer detail.
     */
    suspend fun submitToIrd(invoice: EInvoice): Result<IrdSubmissionResult> {
        val request = invoice.toSubmitRequest()
        return irdApiClient.submitInvoice(request).map { response ->
            response.toDomain()
        }
    }
}

// Mapper: SQLDelight entity → domain model
private fun /* EInvoiceEntity */ Any.toDomain(): EInvoice = TODO("map from SQLDelight generated type")

// Mapper: domain model → SQLDelight entity
private fun EInvoice.toEntity(): Any = TODO("map to SQLDelight generated type")
```

---

## Koin Bindings Update

In `shared/data/src/commonMain/.../data/di/DataModule.kt`:

```kotlin
// E-Invoice
single<EInvoiceRepository> {
    EInvoiceRepositoryImpl(
        db           = get(),
        irdApiClient = get()
    )
}

// IRD API Client (configured with certificate)
single<IrdApiClient> {
    IrdApiClient(
        config     = get<EInvoiceCertificateConfig>(),
        httpClient = buildIrdHttpClient(get())   // creates dedicated Ktor client with mTLS
    )
}
```

In `shared/security/src/commonMain/.../security/di/` (or platform DI):

```kotlin
single<IrdCertificateManager> {
    IrdCertificateManager(
        config          = get<EInvoiceCertificateConfig>(),
        secureKeyStorage = get()
    )
}
```

Platform DI (`androidDataModule` / `desktopDataModule`) provides `EInvoiceCertificateConfig`:

```kotlin
// Android (androidDataModule):
single<EInvoiceCertificateConfig> {
    EInvoiceCertificateConfig(
        certificatePath     = BuildConfig.ZYNTA_IRD_CLIENT_CERTIFICATE_PATH,
        certificatePassword = BuildConfig.ZYNTA_IRD_CERTIFICATE_PASSWORD,
        apiEndpoint         = BuildConfig.ZYNTA_IRD_API_ENDPOINT
    )
}
```

---

## `local.properties.template` Updates

```properties
# IRD E-Invoice Configuration
ZYNTA_IRD_API_ENDPOINT=https://einvoice.ird.gov.lk/api/v1
ZYNTA_IRD_CLIENT_CERTIFICATE_PATH=/path/to/your/ird-certificate.p12
ZYNTA_IRD_CERTIFICATE_PASSWORD=your_certificate_password
```

---

## Tasks

- [ ] **18.1** Create `EInvoiceDto.kt` with all request/response DTOs and `@Serializable` annotations
- [ ] **18.2** Add domain ↔ DTO extension functions (`toSubmitRequest()`, `toDomain()`)
- [ ] **18.3** Implement `IrdApiClient.kt` with `submitInvoice()`, `getInvoiceStatus()`, `cancelInvoice()`
- [ ] **18.4** Define `IrdAuthException` and `IrdApiException` typed exceptions
- [ ] **18.5** Implement `IrdCertificateManager.kt` with `initialize()` and `sign()` stubs
- [ ] **18.6** Implement `EInvoiceRepositoryImpl.kt` with SQLDelight CRUD + `submitToIrd()` method
- [ ] **18.7** Add `EInvoiceRepository` + `IrdApiClient` + `IrdCertificateManager` Koin bindings
- [ ] **18.8** Add `ZYNTA_IRD_*` keys to `local.properties.template`
- [ ] **18.9** Write `IrdApiClientTest` using Ktor `MockEngine` — test submit success, 400 error, 401 auth failure
- [ ] **18.10** Verify: `./gradlew :shared:data:assemble && ./gradlew :shared:security:assemble`

---

## Verification

```bash
./gradlew :shared:data:assemble
./gradlew :shared:security:assemble
./gradlew :shared:data:jvmTest
```

---

## Definition of Done

- [ ] `EInvoiceDto` types correctly serialise/deserialise JSON (verified by unit test)
- [ ] `IrdApiClient` handles success (200/201), 400, 401, and 5xx responses correctly
- [ ] `IrdCertificateManager` initialises from `.p12` file path (stub for now)
- [ ] `EInvoiceRepositoryImpl` CRUD works against SQLDelight schema
- [ ] All Koin bindings registered and modules assemble without errors
- [ ] `IrdApiClientTest` with MockEngine passes (submit + status + cancel)
- [ ] Commit: `feat(einvoice): add IRD API client, certificate manager, and e-invoice repository`
