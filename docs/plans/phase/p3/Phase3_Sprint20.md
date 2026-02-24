# ZyntaPOS — Phase 3 Sprint 20: E-Invoice — IRD Submission, Compliance Reports & POS Integration

> **Document ID:** ZYNTA-PLAN-PHASE3-SPRINT20-v1.0
> **Phase:** 3 — Enterprise (Months 13–18)
> **Sprint:** 20 of 24 | Week 20
> **Module(s):** `:composeApp:feature:settings`, `:composeApp:feature:pos`
> **Author:** Senior KMP Architect & Lead Engineer
> **Reference:** ZYNTA-MASTER-PLAN-v1.0 | ADR-001

---

## Goal

Complete the E-Invoice module: implement the full IRD API submission with retry logic, four E-Invoice screens in `:composeApp:feature:settings`, POS post-payment auto-trigger, and compliance reporting. The receipt is updated to show the IRD invoice number when e-invoicing is enabled.

---

## New Screen Files

**Location:** `composeApp/feature/settings/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/settings/screen/`

```
screen/
├── EInvoiceSettingsScreen.kt    # Certificate upload, API endpoint, test connection
├── EInvoiceListScreen.kt        # Submitted invoices with status
├── EInvoiceDetailScreen.kt      # Full invoice view + re-submit on rejection
└── ComplianceReportScreen.kt    # Tax summary by period
```

---

## `SubmitEInvoiceUseCaseImpl.kt` — Full Implementation

Replace Sprint 19 stub with real IRD API integration:

```kotlin
package com.zyntasolutions.zyntapos.domain.usecase.einvoice

import com.zyntasolutions.zyntapos.data.remote.IrdApiClient
import com.zyntasolutions.zyntapos.data.remote.dto.toSubmitRequest
import com.zyntasolutions.zyntapos.domain.model.*
import com.zyntasolutions.zyntapos.domain.repository.EInvoiceRepository
import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay

/**
 * Full IRD submission with retry and ECDSA signature.
 *
 * Retry policy:
 * - IrdApiException (network/5xx): up to 3 retries, exponential backoff 1s/2s/4s
 * - IrdAuthException (401/cert): no retry — fail immediately and log audit
 * - IrdApiException with 400 (bad payload): no retry — update status to REJECTED
 */
class SubmitEInvoiceUseCaseImpl(
    private val eInvoiceRepository: EInvoiceRepository,
    private val irdApiClient: IrdApiClient,
    private val certificateManager: com.zyntasolutions.zyntapos.security.IrdCertificateManager
) : SubmitEInvoiceUseCase {

    private val logger = Logger.withTag("SubmitEInvoiceUseCase")
    private val maxRetries = 3
    private val baseDelayMs = 1_000L

    override suspend fun invoke(invoiceId: String): Result<IrdSubmissionResult> = runCatching {
        val invoice = eInvoiceRepository.getById(invoiceId)
            ?: throw IllegalArgumentException("Invoice not found: $invoiceId")

        if (invoice.status == EInvoiceStatus.ACCEPTED) {
            throw IllegalStateException("Invoice ${invoice.invoiceNumber} already accepted")
        }

        // Sign with ECDSA (replaces SHA-256 stub from Sprint 19)
        val payloadBytes = invoice.toSubmitRequest().toString().encodeToByteArray()
        val signatureHash = certificateManager.sign(payloadBytes).getOrThrow()
        val signedInvoice = invoice.copy(signatureHash = signatureHash)
        eInvoiceRepository.save(signedInvoice)

        // Update to SUBMITTED
        eInvoiceRepository.updateStatus(invoiceId, EInvoiceStatus.SUBMITTED, null, null)

        // Submit with retry
        var lastException: Throwable? = null
        var delayMs = baseDelayMs
        repeat(maxRetries) { attempt ->
            val result = irdApiClient.submitInvoice(signedInvoice.toSubmitRequest())
            result.fold(
                onSuccess = { response ->
                    val finalStatus = if (response.success) EInvoiceStatus.ACCEPTED else EInvoiceStatus.REJECTED
                    eInvoiceRepository.updateStatus(
                        id           = invoiceId,
                        status       = finalStatus,
                        responseCode = response.responseCode,
                        message      = response.message
                    )
                    logger.i { "Invoice ${invoice.invoiceNumber} → ${finalStatus.name} (${response.responseCode})" }
                    return@runCatching response.toDomain()
                },
                onFailure = { ex ->
                    when (ex) {
                        is com.zyntasolutions.zyntapos.data.remote.IrdAuthException -> {
                            // Certificate failure — fail immediately
                            eInvoiceRepository.updateStatus(invoiceId, EInvoiceStatus.REJECTED, "AUTH_FAILED", ex.message)
                            throw ex
                        }
                        is com.zyntasolutions.zyntapos.data.remote.IrdApiException -> {
                            if (ex.code.startsWith("4")) {
                                // 4xx: bad payload, no retry
                                eInvoiceRepository.updateStatus(invoiceId, EInvoiceStatus.REJECTED, ex.code, ex.message)
                                throw ex
                            }
                        }
                        else -> {}
                    }
                    lastException = ex
                    logger.w { "Submit attempt ${attempt + 1} failed: ${ex.message}" }
                    if (attempt < maxRetries - 1) {
                        delay(delayMs)
                        delayMs *= 2
                    }
                }
            )
        }
        throw lastException ?: IllegalStateException("All $maxRetries submission attempts failed")
    }

    private fun IrdSubmissionResult.toDomain(): IrdSubmissionResult = this

    private fun com.zyntasolutions.zyntapos.data.remote.dto.EInvoiceSubmitResponse.toDomain(): IrdSubmissionResult =
        IrdSubmissionResult(
            success      = success,
            invoiceId    = invoiceId,
            responseCode = responseCode,
            message      = message,
            timestamp    = timestamp
        )
}
```

---

## E-Invoice Settings Screen

### `EInvoiceSettingsScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.settings.screen

/**
 * IRD e-invoice configuration screen.
 *
 * Sections:
 * 1. Enable/Disable toggle ("Enable E-Invoicing")
 *    - Reads/writes "einvoice.enabled" from settings table
 *    - Warn: "E-Invoicing requires IRD certificate and valid API endpoint"
 *
 * 2. Certificate Status card:
 *    - Status: "Certificate Loaded" (green) or "No Certificate" (red)
 *    - Certificate path (read-only from BuildConfig)
 *    - "Test Certificate" button → calls IrdCertificateManager.initialize()
 *
 * 3. API Endpoint card:
 *    - Endpoint URL (read-only from BuildConfig.ZYNTA_IRD_API_ENDPOINT)
 *    - "Test Connection" button → calls IrdCertificateManager.testConnection()
 *    - Shows latency: "Connected in 123ms" or "Connection failed"
 *
 * 4. Invoice Settings:
 *    - Default currency (always "LKR" for Sri Lanka)
 *    - Store VAT registration number (editable, stored in store settings)
 *
 * 5. "View Submitted Invoices" → EInvoiceListScreen
 * 6. "View Compliance Report" → ComplianceReportScreen
 */
@Composable
fun EInvoiceSettingsScreen(
    settingsViewModel: SettingsViewModel,
    onNavigateToInvoiceList: () -> Unit,
    onNavigateToComplianceReport: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
)
```

---

## E-Invoice List Screen

### `EInvoiceListScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.settings.screen

/**
 * Submitted invoice list screen.
 *
 * Layout:
 * - Filter chips: All / Draft / Submitted / Accepted / Rejected / Cancelled
 * - LazyColumn of EInvoiceCard sorted by createdAt descending
 *
 * Each card:
 * - Invoice number (e.g. "IRD-ZYN01-20260224-0042")
 * - Status badge (color-coded)
 * - Buyer name
 * - Total amount (LKR formatted)
 * - Date
 *
 * Tap → EInvoiceDetailScreen
 */
@Composable
fun EInvoiceListScreen(
    storeId: String,
    onNavigateToDetail: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Collects from EInvoiceRepository.getByStore(storeId) via SettingsViewModel or dedicated EInvoiceViewModel
}
```

---

## E-Invoice Detail Screen

### `EInvoiceDetailScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.settings.screen

/**
 * Invoice detail screen.
 *
 * Layout (matches paper invoice format):
 * - Header: invoice number, date, status badge
 * - Store info (seller) + Buyer info
 * - Line items table: Product | Qty | Unit Price | Tax | Total
 * - Footer totals: Subtotal | Tax Total | Net Total
 * - IRD Response: response code + message (if submitted/accepted/rejected)
 * - Signature hash (truncated, last 8 chars shown)
 *
 * Actions:
 * - DRAFT: "Submit to IRD" button → calls SubmitEInvoiceUseCase
 * - REJECTED: "Re-submit" button (re-generates signature + submits)
 * - ACCEPTED: "Cancel Invoice" button → calls CancelEInvoiceUseCase
 * - All statuses: "Download PDF" action (platform share intent)
 */
@Composable
fun EInvoiceDetailScreen(
    invoiceId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
)
```

---

## Compliance Report Screen

### `ComplianceReportScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.settings.screen

/**
 * Tax compliance report screen for IRD.
 *
 * Layout:
 * - Period selector (month/year)
 * - Summary card:
 *     Total Invoices | Accepted | Rejected | Pending
 *     Total Tax Collected: LKR XX,XXX.XX
 * - Tax breakdown table:
 *     Tax Group | Rate | Taxable Amount | Tax Amount
 *     (sorted by tax rate)
 * - "Export Report" button (CSV download)
 *
 * Uses GetComplianceReportUseCase.
 */
@Composable
fun ComplianceReportScreen(
    storeId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
)
```

---

## POS Integration

After `ProcessPayment` succeeds in `PosViewModel`, auto-trigger e-invoice generation if enabled:

```kotlin
// In PosViewModel.kt — add after successful payment processing:
private suspend fun triggerEInvoiceIfEnabled(orderId: String) {
    val isEnabled = settingsRepository.getString("einvoice.enabled") == "true"
    if (!isEnabled) return

    // Fire-and-forget in background coroutine (does not block POS flow)
    viewModelScope.launch {
        logger.d { "Auto-generating e-invoice for order: $orderId" }
        generateEInvoice(orderId).fold(
            onSuccess = { invoice ->
                submitEInvoice(invoice.id).fold(
                    onSuccess = { result ->
                        logger.i { "E-Invoice auto-submitted: ${invoice.invoiceNumber} → ${result.responseCode}" }
                        // Update POS state with IRD invoice number for receipt display
                        updateState { state ->
                            state.copy(lastInvoiceNumber = invoice.invoiceNumber)
                        }
                    },
                    onFailure = { ex ->
                        // Non-blocking failure: log but don't disrupt POS flow
                        logger.w(ex) { "E-invoice auto-submit failed: ${ex.message}" }
                    }
                )
            },
            onFailure = { ex ->
                logger.w(ex) { "E-invoice generation failed: ${ex.message}" }
            }
        )
    }
}
```

The receipt template adds the IRD invoice number when available:

```kotlin
// In ReceiptPreview composable (or ESC/POS template):
state.lastInvoiceNumber?.let { invoiceNumber ->
    Divider()
    Text("IRD Invoice: $invoiceNumber", style = MaterialTheme.typography.bodySmall)
}
```

---

## PosState Update

```kotlin
// Add to PosState.kt:
val lastInvoiceNumber: String? = null    // IRD e-invoice number, set after auto-submission
```

---

## SettingsViewModel Updates

Add E-Invoice use cases to `SettingsViewModel`:

```kotlin
// Additional use cases injected:
private val generateEInvoice: GenerateEInvoiceUseCase
private val submitEInvoice: SubmitEInvoiceUseCase
private val getComplianceReport: GetComplianceReportUseCase

// Additional state in SettingsState:
val eInvoiceEnabled: Boolean = false
val einvoiceList: List<EInvoice> = emptyList()
val complianceReport: ComplianceReport? = null
val isCertificateLoaded: Boolean = false
val connectionTestResult: String? = null
```

---

## Tasks

- [ ] **20.1** Replace `SubmitEInvoiceUseCaseImpl` stub with full IRD API submission + retry logic (3 retries, exponential backoff)
- [ ] **20.2** Integrate ECDSA signing via `IrdCertificateManager.sign()` in submission flow
- [ ] **20.3** Implement `EInvoiceSettingsScreen.kt` with certificate status, API test, and toggle
- [ ] **20.4** Implement `EInvoiceListScreen.kt` with status filter chips
- [ ] **20.5** Implement `EInvoiceDetailScreen.kt` with line items table and action buttons
- [ ] **20.6** Implement `ComplianceReportScreen.kt` with period selector and tax breakdown
- [ ] **20.7** Add `triggerEInvoiceIfEnabled()` to `PosViewModel` — fires after successful payment
- [ ] **20.8** Add `lastInvoiceNumber` to `PosState` and update receipt template
- [ ] **20.9** Wire E-Invoice routes in `SettingsGraph` within `MainNavGraph.kt`
- [ ] **20.10** Write `SubmitEInvoiceUseCaseTest` — test retry on 5xx, no-retry on 4xx, auth failure flow
- [ ] **20.11** Verify: `./gradlew :composeApp:feature:settings:assemble && :composeApp:feature:pos:assemble`

---

## Verification

```bash
./gradlew :composeApp:feature:settings:assemble
./gradlew :composeApp:feature:pos:assemble
./gradlew :shared:domain:test
./gradlew :composeApp:feature:settings:detekt
```

---

## Definition of Done

- [ ] `SubmitEInvoiceUseCaseImpl` retries 3× on network failure, stops on auth/4xx failure
- [ ] ECDSA signing via `IrdCertificateManager` integrated in submission flow
- [ ] All 4 E-Invoice screens implemented in `:composeApp:feature:settings`
- [ ] POS receipt shows IRD invoice number after successful e-invoice auto-submission
- [ ] Retry logic tests pass (5xx retry, 4xx no-retry, auth fail)
- [ ] Commit: `feat(einvoice): implement IRD submission with retry, compliance reports, and POS integration`
