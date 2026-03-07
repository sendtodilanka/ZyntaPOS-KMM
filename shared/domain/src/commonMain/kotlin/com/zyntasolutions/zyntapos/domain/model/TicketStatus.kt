package com.zyntasolutions.zyntapos.domain.model

/** Lifecycle state of a [SupportTicket]. */
enum class TicketStatus {
    /** Ticket has been created and is awaiting assignment or first response. */
    OPEN,

    /** Ticket is actively being investigated or worked on by assigned staff. */
    IN_PROGRESS,

    /** Staff has responded and is waiting for further information from the customer. */
    PENDING_CUSTOMER,

    /** A solution has been provided; ticket will auto-close if customer does not reopen. */
    RESOLVED,

    /** Ticket is fully closed — no further action required. */
    CLOSED,
}
