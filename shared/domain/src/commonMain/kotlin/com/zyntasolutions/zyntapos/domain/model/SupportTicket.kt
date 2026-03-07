package com.zyntasolutions.zyntapos.domain.model

import kotlinx.datetime.Instant

/**
 * A customer support ticket raised by helpdesk staff in response to a complaint or inquiry.
 *
 * ### Flow
 * ```
 * Helpdesk staff (CUSTOMER_SERVICE role) creates ticket
 *         │  linked to: [customerId] + optional [orderId]
 *         ▼
 * Unique [tokenNumber] generated  (e.g. TKT-20240307-0042)
 *         │
 *         ▼
 * Assigned to operator via [assignedToId]
 *         │
 *         ▼
 * OPEN → IN_PROGRESS → PENDING_CUSTOMER → RESOLVED → CLOSED
 *         │  (each step recorded as a [TicketActivity])
 *         ▼
 * Resolution note saved → [resolvedAt] stamped → ticket CLOSED
 * ```
 *
 * @see TicketStatus   for the full lifecycle state machine
 * @see TicketPriority for queue ordering
 * @see TicketActivity for the immutable event log
 */
data class SupportTicket(
    /** Unique ticket identifier (UUID). */
    val id: String,

    /**
     * Human-readable token displayed to the customer and staff.
     * Format: `TKT-YYYYMMDD-NNNN` (e.g. `TKT-20240307-0042`).
     * Generated on creation; immutable thereafter.
     */
    val tokenNumber: String,

    /** One-line summary of the complaint or inquiry. */
    val subject: String,

    /** Full description of the customer's issue, provided by helpdesk staff at creation. */
    val description: String,

    /** Current lifecycle state. */
    val status: TicketStatus,

    /** Priority level — determines display ordering in the helpdesk queue. */
    val priority: TicketPriority,

    /** The customer this ticket is raised for. */
    val customerId: String,

    /** Display name of the customer (denormalised for quick display). */
    val customerName: String,

    /**
     * Optional reference to the order that triggered the complaint.
     * Null for general inquiries not tied to a specific transaction.
     */
    val orderId: String? = null,

    /** Staff member who created the ticket ([Role.CUSTOMER_SERVICE] or above). */
    val createdById: String,

    /**
     * Staff member currently assigned to resolve the ticket.
     * Null until explicitly assigned.
     */
    val assignedToId: String? = null,

    /** Display name of the assigned staff member (denormalised). */
    val assignedToName: String? = null,

    /**
     * Resolution note written by staff when marking the ticket as [TicketStatus.RESOLVED].
     * Describes the solution provided to the customer.
     */
    val resolutionNote: String? = null,

    /** Category tag to help filter and report on ticket types (e.g. "Refund", "Product Issue"). */
    val category: String? = null,

    /** Full activity timeline — ordered oldest-first. Populated from the repository. */
    val activities: List<TicketActivity> = emptyList(),

    /** When the ticket was created. */
    val createdAt: Instant,

    /** When the ticket was last updated (any field change or activity added). */
    val updatedAt: Instant,

    /** When the ticket reached [TicketStatus.RESOLVED] state. Null until resolved. */
    val resolvedAt: Instant? = null,
)
