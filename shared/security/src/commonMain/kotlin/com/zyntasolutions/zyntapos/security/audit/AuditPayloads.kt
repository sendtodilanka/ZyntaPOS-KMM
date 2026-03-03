package com.zyntasolutions.zyntapos.security.audit

import kotlinx.serialization.Serializable

/**
 * Strongly-typed, `@Serializable` payload data classes for [SecurityAuditLogger].
 *
 * Using kotlinx.serialization instead of manual string interpolation eliminates:
 * - JSON injection risk from unescaped field values
 * - Silent breakage from structural changes
 * - The bespoke [String.escapeJson] helper
 *
 * All classes are package-private (internal) — only [SecurityAuditLogger] constructs them.
 */

@Serializable
internal data class LoginPayload(val source: String)

@Serializable
internal data class LogoutPayload(val reason: String = "explicit")

@Serializable
internal data class PermissionDeniedPayload(val permission: String, val screen: String)

@Serializable
internal data class OrderCreatedPayload(
    val orderId: String,
    val totalAmount: Double,
    val itemCount: Int,
    val paymentMethod: String,
)

@Serializable
internal data class OrderVoidedPayload(val orderId: String, val reason: String)

@Serializable
internal data class PaymentProcessedPayload(val orderId: String, val amount: Double, val method: String)

@Serializable
internal data class DiscountAppliedPayload(val orderId: String, val amount: Double, val type: String)

@Serializable
internal data class StockAdjustedPayload(val productId: String, val qty: Double, val reason: String)

/** Minimal before/after snapshot used in [AuditEntry.previousValue] / [AuditEntry.newValue] for stock changes. */
@Serializable
internal data class StockQtySnapshot(val qty: Double)

@Serializable
internal data class ProductCreatedPayload(val productId: String, val name: String)

@Serializable
internal data class ProductModifiedPayload(val productId: String)

@Serializable
internal data class RegisterOpenPayload(val registerId: String, val openingBalance: Double)

@Serializable
internal data class RegisterClosePayload(val registerId: String, val variance: Double)

@Serializable
internal data class CashMovementPayload(val registerId: String, val amount: Double, val reason: String)

@Serializable
internal data class DataExportedPayload(val action: String, val entityId: String)

@Serializable
internal data class SettingsChangedPayload(val key: String)

@Serializable
internal data class DiagnosticSessionPayload(val debugAction: String, val detail: String)
