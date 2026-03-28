package com.zyntasolutions.zyntapos.domain.model

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * ZyntaPOS — DomainModelInvariantsTest Unit Tests (commonTest)
 *
 * Validates the `init { require() }` guards in core domain models.
 * These tests document and enforce the business invariants that the domain layer
 * guarantees at construction time, acting as a safety net against accidental
 * relaxation of constraints.
 *
 * Coverage:
 *  CartItem        — quantity must be ≥ 1
 *  CashMovement    — amount must be > 0
 *  CustomerWallet  — balance must be ≥ 0
 *  WalletTransaction — amount must be > 0
 *  LoyaltyTier     — minPoints ≥ 0, discountPercent in [0,100], multiplier > 0
 *  Budget          — budgetAmount > 0, spentAmount ≥ 0
 *  CompoundTaxComponent — componentRate in [0,100]
 */
class DomainModelInvariantsTest {

    // ═══════════════════════════════════════════════════════════════════════
    // CartItem
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `CartItem - quantity less than 1 throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            CartItem(productId = "p1", productName = "Coffee", unitPrice = 5.0, quantity = 0.5)
        }
    }

    @Test
    fun `CartItem - zero quantity throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            CartItem(productId = "p1", productName = "Coffee", unitPrice = 5.0, quantity = 0.0)
        }
    }

    @Test
    fun `CartItem - quantity exactly 1 is valid`() {
        // Should not throw
        CartItem(productId = "p1", productName = "Coffee", unitPrice = 5.0, quantity = 1.0)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CashMovement
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `CashMovement - zero amount throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            CashMovement(
                id = "cm-1", sessionId = "s-1",
                type = CashMovement.Type.IN, amount = 0.0,
                reason = "test", recordedBy = "u-1",
                timestamp = Instant.fromEpochMilliseconds(1_000_000L),
            )
        }
    }

    @Test
    fun `CashMovement - negative amount throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            CashMovement(
                id = "cm-1", sessionId = "s-1",
                type = CashMovement.Type.OUT, amount = -50.0,
                reason = "test", recordedBy = "u-1",
                timestamp = Instant.fromEpochMilliseconds(1_000_000L),
            )
        }
    }

    @Test
    fun `CashMovement - positive amount is valid`() {
        // Should not throw
        CashMovement(
            id = "cm-1", sessionId = "s-1",
            type = CashMovement.Type.IN, amount = 100.0,
            reason = "Float top-up", recordedBy = "u-1",
            timestamp = Instant.fromEpochMilliseconds(1_000_000L),
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CustomerWallet
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `CustomerWallet - negative balance throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            CustomerWallet(id = "w-1", customerId = "c-1", balance = -0.01)
        }
    }

    @Test
    fun `CustomerWallet - zero balance is valid`() {
        // Zero balance (new wallet) must be accepted
        CustomerWallet(id = "w-1", customerId = "c-1", balance = 0.0)
    }

    @Test
    fun `CustomerWallet - positive balance is valid`() {
        CustomerWallet(id = "w-1", customerId = "c-1", balance = 250.50)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // WalletTransaction
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `WalletTransaction - zero amount throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            WalletTransaction(
                id = "wt-1", walletId = "w-1",
                type = WalletTransaction.TransactionType.CREDIT,
                amount = 0.0, balanceAfter = 0.0, createdAt = 1_000_000L,
            )
        }
    }

    @Test
    fun `WalletTransaction - negative amount throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            WalletTransaction(
                id = "wt-1", walletId = "w-1",
                type = WalletTransaction.TransactionType.DEBIT,
                amount = -10.0, balanceAfter = 90.0, createdAt = 1_000_000L,
            )
        }
    }

    @Test
    fun `WalletTransaction - positive amount is valid`() {
        WalletTransaction(
            id = "wt-1", walletId = "w-1",
            type = WalletTransaction.TransactionType.CREDIT,
            amount = 50.0, balanceAfter = 150.0, createdAt = 1_000_000L,
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LoyaltyTier
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `LoyaltyTier - negative minPoints throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            LoyaltyTier(id = "lt-1", name = "Silver", minPoints = -1)
        }
    }

    @Test
    fun `LoyaltyTier - discountPercent above 100 throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            LoyaltyTier(id = "lt-1", name = "Gold", discountPercent = 101.0)
        }
    }

    @Test
    fun `LoyaltyTier - negative discountPercent throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            LoyaltyTier(id = "lt-1", name = "Gold", discountPercent = -5.0)
        }
    }

    @Test
    fun `LoyaltyTier - zero pointsMultiplier throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            LoyaltyTier(id = "lt-1", name = "Platinum", pointsMultiplier = 0.0)
        }
    }

    @Test
    fun `LoyaltyTier - negative pointsMultiplier throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            LoyaltyTier(id = "lt-1", name = "Platinum", pointsMultiplier = -1.5)
        }
    }

    @Test
    fun `LoyaltyTier - valid tier constructs successfully`() {
        // Should not throw
        LoyaltyTier(
            id = "lt-1", name = "Gold",
            minPoints = 1000, discountPercent = 10.0, pointsMultiplier = 2.0,
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Budget
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `Budget - zero budgetAmount throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            Budget(
                id = "b-1", storeId = "s-1",
                periodStart = "2025-01-01", periodEnd = "2025-12-31",
                budgetAmount = 0.0, name = "Annual", createdAt = 1_000L, updatedAt = 1_000L,
            )
        }
    }

    @Test
    fun `Budget - negative budgetAmount throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            Budget(
                id = "b-1", storeId = "s-1",
                periodStart = "2025-01-01", periodEnd = "2025-12-31",
                budgetAmount = -500.0, name = "Annual", createdAt = 1_000L, updatedAt = 1_000L,
            )
        }
    }

    @Test
    fun `Budget - negative spentAmount throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            Budget(
                id = "b-1", storeId = "s-1",
                periodStart = "2025-01-01", periodEnd = "2025-12-31",
                budgetAmount = 1000.0, spentAmount = -1.0,
                name = "Annual", createdAt = 1_000L, updatedAt = 1_000L,
            )
        }
    }

    @Test
    fun `Budget - valid budget constructs successfully`() {
        Budget(
            id = "b-1", storeId = "s-1",
            periodStart = "2025-01-01", periodEnd = "2025-12-31",
            budgetAmount = 5000.0, spentAmount = 1200.0,
            name = "Q4 Marketing", createdAt = 1_000L, updatedAt = 1_000L,
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CompoundTaxComponent
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `CompoundTaxComponent - rate above 100 throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            CompoundTaxComponent(
                id = "ctc-1", parentTaxGroupId = "tax-1",
                componentTaxGroupId = "tax-2", componentRate = 100.01,
            )
        }
    }

    @Test
    fun `CompoundTaxComponent - negative rate throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            CompoundTaxComponent(
                id = "ctc-1", parentTaxGroupId = "tax-1",
                componentTaxGroupId = "tax-2", componentRate = -1.0,
            )
        }
    }

    @Test
    fun `CompoundTaxComponent - rate 0 is valid`() {
        CompoundTaxComponent(
            id = "ctc-1", parentTaxGroupId = "tax-1",
            componentTaxGroupId = "tax-2", componentRate = 0.0,
        )
    }

    @Test
    fun `CompoundTaxComponent - rate 100 is valid`() {
        CompoundTaxComponent(
            id = "ctc-1", parentTaxGroupId = "tax-1",
            componentTaxGroupId = "tax-2", componentRate = 100.0,
        )
    }
}
