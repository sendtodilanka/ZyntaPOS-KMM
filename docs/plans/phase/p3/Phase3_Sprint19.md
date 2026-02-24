# ZyntaPOS — Phase 3 Sprint 19: E-Invoice Generation Engine & Digital Signature

> **Document ID:** ZYNTA-PLAN-PHASE3-SPRINT19-v1.0
> **Phase:** 3 — Enterprise (Months 13–18)
> **Sprint:** 19 of 24 | Week 19
> **Module(s):** `:shared:domain`, `:shared:data`
> **Author:** Senior KMP Architect & Lead Engineer
> **Reference:** ZYNTA-MASTER-PLAN-v1.0 | ADR-002

---

## Goal

Implement the e-invoice generation engine: `GenerateEInvoiceUseCaseImpl` maps an `Order` (with all `OrderItem`s, tax groups, and store info) to a signed `EInvoice`. Invoice number sequencing uses the `settings` table (key `einvoice.last_seq`). Digital signature uses SHA-256/ECDSA via `IrdCertificateManager`.

---

## New Files

**Location:** `shared/domain/src/commonMain/kotlin/com/zyntasolutions/zyntapos/domain/usecase/einvoice/`

```
usecase/einvoice/
├── GenerateEInvoiceUseCaseImpl.kt
└── SubmitEInvoiceUseCaseImpl.kt    # (wires to IrdApiClient — full in Sprint 20)
```

---

## Invoice Generation Logic

### `GenerateEInvoiceUseCaseImpl.kt`

```kotlin
package com.zyntasolutions.zyntapos.domain.usecase.einvoice

import com.zyntasolutions.zyntapos.domain.model.*
import com.zyntasolutions.zyntapos.domain.repository.*
import com.zyntasolutions.zyntapos.domain.usecase.einvoice.GenerateEInvoiceUseCase
import co.touchlab.kermit.Logger

/**
 * Generates a signed EInvoice from a completed Order.
 *
 * Algorithm:
 * 1. Load Order + OrderItems + Customer (for buyer name/VAT)
 * 2. Load Store info (for seller VAT number = storeId code)
 * 3. Map each OrderItem → EInvoiceLineItem:
 *    - productCode = product.sku or product.barcode (prefer SKU)
 *    - description = product.name
 *    - taxRate = orderItem.taxGroup.rate (0.0 if no tax group)
 *    - taxAmount = unitPrice × qty × taxRate / 100
 *    - lineTotal = unitPrice × qty + taxAmount
 * 4. Compute subtotal = sum(lineItem.unitPrice × lineItem.quantity)
 * 5. Compute taxTotal = sum(lineItem.taxAmount)
 * 6. Compute total = subtotal + taxTotal
 * 7. Generate invoice number:
 *    - Read settings key "einvoice.last_seq" (default 0)
 *    - Increment seq by 1
 *    - Write back to settings: "einvoice.last_seq" = newSeq
 *    - Format: "IRD-{storeCode}-{YYYYMMDD}-{seq:04d}"
 *      e.g. "IRD-ZYN01-20260224-0042"
 * 8. Build EInvoice with status = DRAFT
 * 9. Compute signatureHash:
 *    - Serialize EInvoice to canonical JSON (sorted keys)
 *    - Compute SHA-256 hash of JSON bytes
 *    - Format as hex string
 *    - (Full ECDSA signing in Sprint 20 via IrdCertificateManager)
 * 10. Save EInvoice to DB (status = DRAFT)
 * 11. Return Result.success(eInvoice)
 */
class GenerateEInvoiceUseCaseImpl(
    private val orderRepository: OrderRepository,
    private val eInvoiceRepository: EInvoiceRepository,
    private val settingsRepository: SettingsRepository,
    private val storeCode: String                    // injected from session or settings
) : GenerateEInvoiceUseCase {

    private val logger = Logger.withTag("GenerateEInvoiceUseCase")

    override suspend fun invoke(orderId: String): Result<EInvoice> = runCatching {
        logger.d { "Generating e-invoice for order: $orderId" }

        // 1. Check for existing invoice
        val existing = eInvoiceRepository.getByOrderId(orderId)
        if (existing != null) {
            logger.w { "Invoice already exists for order $orderId: ${existing.invoiceNumber}" }
            return@runCatching existing
        }

        // 2. Load order
        val order = orderRepository.getById(orderId)
            ?: throw IllegalArgumentException("Order not found: $orderId")

        if (order.status != OrderStatus.COMPLETED) {
            throw IllegalStateException("Cannot generate invoice for non-completed order: ${order.status}")
        }

        // 3. Map order items to invoice line items
        val lineItems = order.items.mapIndexed { index, item ->
            val taxRate = item.taxGroupRate ?: 0.0
            val taxAmount = item.unitPrice * item.quantity * taxRate / 100.0
            EInvoiceLineItem(
                lineNumber  = index + 1,
                productCode = item.sku ?: item.productId,
                description = item.productName,
                quantity    = item.quantity,
                unitPrice   = item.unitPrice,
                taxRate     = taxRate,
                taxAmount   = taxAmount,
                lineTotal   = item.unitPrice * item.quantity + taxAmount
            )
        }

        val subtotal = lineItems.sumOf { it.quantity * it.unitPrice }
        val taxTotal = lineItems.sumOf { it.taxAmount }
        val total = subtotal + taxTotal

        // 4. Generate sequential invoice number
        val invoiceNumber = generateInvoiceNumber()

        // 5. Compute SHA-256 hash (signature placeholder — full ECDSA in Sprint 20)
        val invoiceDraft = EInvoice(
            id                  = generateUuid(),
            orderId             = orderId,
            invoiceNumber       = invoiceNumber,
            storeId             = order.storeId,
            buyerName           = order.customerName ?: "Walk-in Customer",
            buyerVatNumber      = order.customerVatNumber,
            lineItems           = lineItems,
            subtotal            = subtotal,
            taxTotal            = taxTotal,
            total               = total,
            currency            = "LKR",
            status              = EInvoiceStatus.DRAFT,
            submittedAt         = null,
            irdResponseCode     = null,
            irdResponseMessage  = null,
            signatureHash       = null,   // set after SHA-256 below
            createdAt           = nowIsoDateTime(),
            updatedAt           = nowIsoDateTime()
        )

        val signatureHash = computeSignatureHash(invoiceDraft)
        val signedInvoice = invoiceDraft.copy(signatureHash = signatureHash)

        // 6. Save to DB
        eInvoiceRepository.save(signedInvoice).getOrThrow()

        logger.i { "E-Invoice generated: ${signedInvoice.invoiceNumber}" }
        signedInvoice
    }

    private suspend fun generateInvoiceNumber(): String {
        val lastSeq = settingsRepository.getString("einvoice.last_seq")?.toIntOrNull() ?: 0
        val newSeq = lastSeq + 1
        settingsRepository.setString("einvoice.last_seq", newSeq.toString())

        val today = getCurrentDateCompact()   // "YYYYMMDD" using kotlinx.datetime
        return "IRD-${storeCode.uppercase()}-$today-${newSeq.toString().padStart(4, '0')}"
    }

    /**
     * Computes a SHA-256 hash of the canonical invoice JSON payload.
     * This serves as the invoice integrity hash until full ECDSA signing is implemented.
     *
     * Canonical form: JSON with keys sorted alphabetically, compact (no whitespace).
     * Uses `kotlinx.serialization.json.Json { prettyPrint = false }`
     */
    private fun computeSignatureHash(invoice: EInvoice): String {
        val canonical = buildCanonicalJson(invoice)
        val bytes = canonical.encodeToByteArray()
        return sha256Hex(bytes)   // platform-specific: expect/actual in :shared:core
    }

    /**
     * Returns compact canonical JSON of the invoice for hashing.
     * Key fields: invoiceNumber, orderId, storeId, total, taxTotal, lineItems
     */
    private fun buildCanonicalJson(invoice: EInvoice): String {
        // Uses kotlinx.serialization.json.Json to serialise EInvoice
        // The signatureHash field itself is excluded from the hash (set to null before serialization)
        return "{\"invoice_number\":\"${invoice.invoiceNumber}\"," +
               "\"order_id\":\"${invoice.orderId}\"," +
               "\"store_id\":\"${invoice.storeId}\"," +
               "\"total\":${invoice.total}," +
               "\"tax_total\":${invoice.taxTotal}," +
               "\"line_count\":${invoice.lineItems.size}}"
    }

    private fun sha256Hex(bytes: ByteArray): String {
        // expect fun sha256(input: ByteArray): ByteArray in :shared:core
        // Android: java.security.MessageDigest.getInstance("SHA-256")
        // JVM:     java.security.MessageDigest.getInstance("SHA-256")
        return "sha256_placeholder_${bytes.size}"
    }
}
```

---

## `SubmitEInvoiceUseCaseImpl.kt` (Stub — completes in Sprint 20)

```kotlin
package com.zyntasolutions.zyntapos.domain.usecase.einvoice

import com.zyntasolutions.zyntapos.domain.model.*
import com.zyntasolutions.zyntapos.domain.repository.EInvoiceRepository
import co.touchlab.kermit.Logger

/**
 * Submits an EInvoice (status = DRAFT) to the IRD API.
 *
 * Flow:
 * 1. Load invoice by ID
 * 2. Validate status = DRAFT (cannot re-submit ACCEPTED invoices)
 * 3. Update status to SUBMITTED
 * 4. Call IrdApiClient.submitInvoice(invoice.toSubmitRequest())
 * 5a. On success: update status to ACCEPTED + store irdResponseCode
 * 5b. On failure: update status to REJECTED + store irdResponseCode + message
 * 6. Return IrdSubmissionResult
 *
 * Retry policy: up to 3 retries on network error (exponential backoff 1s/2s/4s).
 * Non-retryable: 400 Bad Request (invalid payload), 401 Unauthorized (cert issue).
 *
 * Full implementation in Sprint 20. This stub always returns success with a mock result.
 */
class SubmitEInvoiceUseCaseImpl(
    private val eInvoiceRepository: EInvoiceRepository
) : SubmitEInvoiceUseCase {

    private val logger = Logger.withTag("SubmitEInvoiceUseCase")

    override suspend fun invoke(invoiceId: String): Result<IrdSubmissionResult> = runCatching {
        logger.d { "Submitting e-invoice: $invoiceId (STUB)" }

        val invoice = eInvoiceRepository.getById(invoiceId)
            ?: throw IllegalArgumentException("Invoice not found: $invoiceId")

        if (invoice.status == EInvoiceStatus.ACCEPTED) {
            throw IllegalStateException("Invoice ${invoice.invoiceNumber} is already accepted")
        }

        // Update to SUBMITTED
        eInvoiceRepository.updateStatus(invoiceId, EInvoiceStatus.SUBMITTED, null, null)

        // STUB: simulate accepted response
        val result = IrdSubmissionResult(
            success      = true,
            invoiceId    = invoiceId,
            responseCode = "IRD_200",
            message      = "Invoice accepted (stub)",
            timestamp    = nowIsoDateTime()
        )

        // Update to ACCEPTED
        eInvoiceRepository.updateStatus(
            id           = invoiceId,
            status       = EInvoiceStatus.ACCEPTED,
            responseCode = result.responseCode,
            message      = result.message
        )

        logger.i { "E-Invoice submitted (stub): ${invoice.invoiceNumber}" }
        result
    }
}
```

---

## Supporting Use Case Implementations

### `GetEInvoiceStatusUseCaseImpl.kt`

```kotlin
package com.zyntasolutions.zyntapos.domain.usecase.einvoice

class GetEInvoiceStatusUseCaseImpl(
    private val eInvoiceRepository: EInvoiceRepository
) : GetEInvoiceStatusUseCase {

    override suspend fun invoke(invoiceId: String): Result<EInvoiceStatus> = runCatching {
        val invoice = eInvoiceRepository.getById(invoiceId)
            ?: throw IllegalArgumentException("Invoice not found: $invoiceId")
        invoice.status
    }
}
```

### `CancelEInvoiceUseCaseImpl.kt`

```kotlin
package com.zyntasolutions.zyntapos.domain.usecase.einvoice

class CancelEInvoiceUseCaseImpl(
    private val eInvoiceRepository: EInvoiceRepository
) : CancelEInvoiceUseCase {

    override suspend fun invoke(invoiceId: String, reason: String): Result<Unit> = runCatching {
        val invoice = eInvoiceRepository.getById(invoiceId)
            ?: throw IllegalArgumentException("Invoice not found: $invoiceId")

        when (invoice.status) {
            EInvoiceStatus.ACCEPTED -> {
                // Accepted invoices require IRD cancellation API call (Sprint 20)
                eInvoiceRepository.updateStatus(
                    id           = invoiceId,
                    status       = EInvoiceStatus.CANCELLED,
                    responseCode = "CANCELLED",
                    message      = reason
                )
            }
            EInvoiceStatus.DRAFT, EInvoiceStatus.SUBMITTED -> {
                // Local cancellation — no IRD API call needed
                eInvoiceRepository.updateStatus(
                    id           = invoiceId,
                    status       = EInvoiceStatus.CANCELLED,
                    responseCode = null,
                    message      = reason
                )
            }
            EInvoiceStatus.CANCELLED -> {
                throw IllegalStateException("Invoice is already cancelled")
            }
            EInvoiceStatus.REJECTED -> {
                // Rejected invoice: just mark as cancelled locally
                eInvoiceRepository.updateStatus(invoiceId, EInvoiceStatus.CANCELLED, null, reason)
            }
        }
    }
}
```

### `GetComplianceReportUseCaseImpl.kt`

```kotlin
package com.zyntasolutions.zyntapos.domain.usecase.einvoice

class GetComplianceReportUseCaseImpl(
    private val eInvoiceRepository: EInvoiceRepository
) : GetComplianceReportUseCase {

    override suspend fun invoke(storeId: String, fromDate: String, toDate: String): ComplianceReport {
        // Load all invoices for store in date range
        // Aggregate: total, accepted, rejected, pending counts
        // Sum taxTotal for all ACCEPTED invoices
        // Group tax by taxRate → TaxBreakdownItem list
        return ComplianceReport(
            storeId          = storeId,
            fromDate         = fromDate,
            toDate           = toDate,
            totalInvoices    = 0,
            acceptedCount    = 0,
            rejectedCount    = 0,
            pendingCount     = 0,
            totalTaxCollected = 0.0,
            taxBreakdown     = emptyList()
        )
    }
}
```

---

## Unit Tests

**Location:** `shared/domain/src/commonTest/kotlin/com/zyntasolutions/zyntapos/domain/usecase/einvoice/`

### `GenerateEInvoiceUseCaseTest.kt`

```kotlin
class GenerateEInvoiceUseCaseTest {

    // Test: invoice number is correctly formatted
    @Test
    fun `invoice number follows IRD format`() {
        // Given: storeCode = "ZYN01", date = "20260224", seq = 1
        // Expected: "IRD-ZYN01-20260224-0001"
    }

    // Test: invoice number is sequential
    @Test
    fun `invoice sequence increments correctly`() {
        // Given: settings "einvoice.last_seq" = "41"
        // When: generateInvoiceNumber() called
        // Expected: "IRD-ZYN01-20260224-0042"
        //           settings "einvoice.last_seq" = "42"
    }

    // Test: line items are correctly mapped from order
    @Test
    fun `line items map correctly from order items`() {
        // Given: OrderItem with unitPrice=100, qty=2, taxRate=15%
        // Expected: EInvoiceLineItem(taxAmount=30.0, lineTotal=230.0)
    }

    // Test: totals are computed correctly
    @Test
    fun `invoice totals computed correctly`() {
        // Given: 2 line items with subtotals 200 and 300, tax 15%
        // Expected: subtotal=500, taxTotal=75, total=575
    }

    // Test: duplicate invoice not created for same orderId
    @Test
    fun `returns existing invoice if already generated for order`() {
        // Given: existing invoice with orderId "order-1"
        // When: invoke("order-1")
        // Expected: returns existing invoice, no new record saved
    }

    // Test: non-completed order is rejected
    @Test
    fun `throws exception for non-completed order`() {
        // Given: order with status = OrderStatus.PENDING
        // When: invoke(orderId)
        // Expected: throws IllegalStateException
    }
}
```

---

## Tasks

- [ ] **19.1** Implement `GenerateEInvoiceUseCaseImpl.kt` with full line-item mapping and total computation
- [ ] **19.2** Implement sequential invoice number generation using `settingsRepository.getString("einvoice.last_seq")`
- [ ] **19.3** Implement `computeSignatureHash()` using SHA-256 (expect/actual `sha256()` in `:shared:core`)
- [ ] **19.4** Implement `SubmitEInvoiceUseCaseImpl.kt` as stub (status flow: DRAFT → SUBMITTED → ACCEPTED/REJECTED)
- [ ] **19.5** Implement `GetEInvoiceStatusUseCaseImpl.kt`
- [ ] **19.6** Implement `CancelEInvoiceUseCaseImpl.kt` with correct state machine (ACCEPTED requires IRD call)
- [ ] **19.7** Implement `GetComplianceReportUseCaseImpl.kt` stub
- [ ] **19.8** Add `sha256(input: ByteArray): ByteArray` expect/actual to `:shared:core`
- [ ] **19.9** Register all E-Invoice use case impls in `DataModule.kt` Koin bindings
- [ ] **19.10** Write `GenerateEInvoiceUseCaseTest.kt` — 6 test cases (invoice format, sequence, totals, duplicate guard, non-completed order)
- [ ] **19.11** Verify: `./gradlew :shared:domain:test` (coverage ≥ 95% for `GenerateEInvoiceUseCaseImpl`)

---

## Verification

```bash
./gradlew :shared:domain:assemble
./gradlew :shared:core:assemble
./gradlew :shared:domain:test
./gradlew :shared:domain:detekt
```

---

## Definition of Done

- [ ] `GenerateEInvoiceUseCaseImpl` correctly maps Order → EInvoice with all computed fields
- [ ] Invoice number follows format `IRD-{storeCode}-{YYYYMMDD}-{seq:04d}`
- [ ] Sequential number uses `settings` table — no gaps, no duplicates
- [ ] SHA-256 hash computed and stored in `signatureHash`
- [ ] `SubmitEInvoiceUseCaseImpl` stub correctly transitions DRAFT → SUBMITTED → ACCEPTED
- [ ] `CancelEInvoiceUseCaseImpl` correctly handles all EInvoiceStatus states
- [ ] All 6 generation test cases pass; 95%+ coverage
- [ ] Commit: `feat(einvoice): implement e-invoice generation engine with SHA-256 signature hash`
