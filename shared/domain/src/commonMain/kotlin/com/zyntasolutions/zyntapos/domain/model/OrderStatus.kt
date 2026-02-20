package com.zyntasolutions.zyntapos.domain.model

/** Lifecycle state of an [Order]. */
enum class OrderStatus {
    /** Cart is being built but not yet paid. */
    IN_PROGRESS,

    /** Payment has been collected and the order is finalised. */
    COMPLETED,

    /** Order was voided after completion. Stock has been reversed. */
    VOIDED,

    /** Order has been placed on hold and can be retrieved. */
    HELD,
}
