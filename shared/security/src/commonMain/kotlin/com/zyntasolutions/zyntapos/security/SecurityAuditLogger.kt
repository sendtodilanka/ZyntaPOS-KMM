@file:Suppress("UNUSED")
package com.zyntasolutions.zyntapos.security

/**
 * @deprecated Moved to [com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger].
 *
 * This file is kept to avoid breaking references from IDE caches.
 * Use the `audit` sub-package class directly or inject via Koin `securityModule`.
 */
@Deprecated(
    message = "Use com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger",
    replaceWith = ReplaceWith(
        "SecurityAuditLogger",
        imports = ["com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger"],
    ),
    level = DeprecationLevel.ERROR,
)
typealias SecurityAuditLogger =
    com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger
