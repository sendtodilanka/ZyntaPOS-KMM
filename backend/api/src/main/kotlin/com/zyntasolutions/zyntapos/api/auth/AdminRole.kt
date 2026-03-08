package com.zyntasolutions.zyntapos.api.auth

enum class AdminRole {
    ADMIN,
    OPERATOR,
    FINANCE,
    AUDITOR,
    HELPDESK;

    companion object {
        fun fromString(value: String): AdminRole? =
            entries.firstOrNull { it.name == value.uppercase() }
    }
}
