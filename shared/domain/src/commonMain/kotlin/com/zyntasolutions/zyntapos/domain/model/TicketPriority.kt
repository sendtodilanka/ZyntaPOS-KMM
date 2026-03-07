package com.zyntasolutions.zyntapos.domain.model

/** Priority level of a [SupportTicket]. Determines queue ordering for assigned staff. */
enum class TicketPriority {
    /** Standard inquiry — no urgency. */
    LOW,

    /** Routine complaint that requires a response within the business day. */
    MEDIUM,

    /** Customer-impacting issue requiring prompt attention. */
    HIGH,

    /** Critical issue (e.g., payment error, data concern) — immediate action required. */
    URGENT,
}
