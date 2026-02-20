package com.zyntasolutions.zyntapos.domain.model

/** Classifies the nature of a transaction. */
enum class OrderType {
    /** A standard forward sale to a customer. */
    SALE,

    /** A return/refund transaction reversing a previous [SALE]. */
    REFUND,

    /** A cart that has been temporarily parked to be retrieved later. */
    HOLD,
}
