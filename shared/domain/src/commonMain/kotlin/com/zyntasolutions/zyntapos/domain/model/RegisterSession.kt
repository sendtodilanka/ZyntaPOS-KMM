package com.zyntasolutions.zyntapos.domain.model

import kotlinx.datetime.Instant

/**
 * A timed working session for a [CashRegister].
 *
 * The session lifecycle is: **OPEN** → **CLOSED**.
 * All [Order]s and [CashMovement]s are linked to the session in which they occurred.
 *
 * @property id Unique identifier (UUID v4).
 * @property registerId FK to the [CashRegister] this session belongs to.
 * @property openedBy FK to the [User] who opened this session.
 * @property closedBy FK to the [User] who closed this session. Null if still open.
 * @property openingBalance Cash float placed in the drawer at session open.
 * @property closingBalance Amount entered by the operator when closing. Null if still open.
 * @property expectedBalance System-calculated expected cash: `openingBalance + cashIn - cashOut + cashSales`.
 * @property actualBalance Physically counted balance entered at close. Null if still open.
 * @property openedAt UTC timestamp when the session was opened.
 * @property closedAt UTC timestamp when the session was closed. Null if still open.
 * @property status Current state of the session.
 */
data class RegisterSession(
    val id: String,
    val registerId: String,
    val openedBy: String,
    val closedBy: String? = null,
    val openingBalance: Double,
    val closingBalance: Double? = null,
    val expectedBalance: Double,
    val actualBalance: Double? = null,
    val openedAt: Instant,
    val closedAt: Instant? = null,
    val status: Status,
) {
    /** Lifecycle state of a [RegisterSession]. */
    enum class Status { OPEN, CLOSED }
}
