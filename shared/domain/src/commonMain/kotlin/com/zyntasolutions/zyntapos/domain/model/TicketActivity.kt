package com.zyntasolutions.zyntapos.domain.model

import kotlinx.datetime.Instant

/**
 * A single immutable event in the lifecycle of a [SupportTicket].
 *
 * Every status change, comment, assignment, or resolution is recorded as a
 * [TicketActivity] entry, forming a full audit trail for the ticket.
 */
data class TicketActivity(
    /** Unique activity identifier. */
    val id: String,

    /** The ticket this activity belongs to. */
    val ticketId: String,

    /** The staff member who performed this action. */
    val authorId: String,

    /** Display name of the author (denormalised for read performance). */
    val authorName: String,

    /** Type of activity recorded. */
    val type: ActivityType,

    /**
     * Human-readable comment or note attached to this activity.
     * Required for [ActivityType.COMMENT] and [ActivityType.RESOLUTION].
     * Optional for status-change events.
     */
    val note: String? = null,

    /** Previous status (populated for [ActivityType.STATUS_CHANGED] events). */
    val fromStatus: TicketStatus? = null,

    /** New status (populated for [ActivityType.STATUS_CHANGED] events). */
    val toStatus: TicketStatus? = null,

    /** Staff member the ticket was assigned to (populated for [ActivityType.ASSIGNED] events). */
    val assignedToId: String? = null,

    /** Timestamp when this activity was recorded. */
    val createdAt: Instant,
) {
    /** Categories of activity that can appear on a ticket timeline. */
    enum class ActivityType {
        /** Ticket was first created by helpdesk staff. */
        CREATED,

        /** A comment or internal note was added by staff. */
        COMMENT,

        /** Ticket status changed (see [fromStatus] / [toStatus]). */
        STATUS_CHANGED,

        /** Ticket was assigned or reassigned to a staff member. */
        ASSIGNED,

        /** Staff recorded the resolution details for the customer. */
        RESOLUTION,

        /** Ticket was reopened after being resolved or closed. */
        REOPENED,
    }
}
