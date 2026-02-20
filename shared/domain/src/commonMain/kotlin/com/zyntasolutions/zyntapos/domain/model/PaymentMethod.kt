package com.zyntasolutions.zyntapos.domain.model

/** Payment tender methods accepted by the POS system. */
enum class PaymentMethod {
    /** Physical currency tendered at the counter. */
    CASH,

    /** Credit or debit card via a connected card terminal. */
    CARD,

    /** Mobile wallet payment (e.g., QR code scan or NFC tap). */
    MOBILE,

    /** Direct bank transfer (usually used for B2B orders). */
    BANK_TRANSFER,

    /** Order paid using a combination of two or more methods. See [PaymentSplit]. */
    SPLIT,
}
