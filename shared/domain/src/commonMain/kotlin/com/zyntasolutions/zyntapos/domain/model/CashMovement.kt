package com.zyntasolutions.zyntapos.domain.model

import kotlinx.datetime.Instant

/**
 * A single cash-in or cash-out event recorded within a [RegisterSession].
 *
 * Cash movements are used to track petty cash, float additions, and till
 * withdrawals. They are included in the expected-balance calculation when
 * the session is closed.
 *
 * @property id Unique identifier (UUID v4).
 * @property sessionId FK to the [RegisterSession] this movement belongs to.
 * @property type Direction of the cash flow. See [Type].
 * @property amount Absolute value of the movement. Must be > 0.
 * @property reason Human-readable explanation (e.g., "Petty cash for cleaning supplies").
 * @property recordedBy FK to the [User] who recorded this movement.
 * @property timestamp UTC timestamp when the movement was recorded.
 */
data class CashMovement(
    val id: String,
    val sessionId: String,
    val type: Type,
    val amount: Double,
    val reason: String,
    val recordedBy: String,
    val timestamp: Instant,
) {
    init {
        require(amount > 0.0) { "CashMovement amount must be positive, got $amount" }
    }

    /** Direction of the cash movement relative to the drawer. */
    enum class Type {
        /** Cash added to the drawer (e.g., float top-up, petty cash in). */
        IN,

        /** Cash removed from the drawer (e.g., bank drop, petty cash out). */
        OUT,
    }
}
