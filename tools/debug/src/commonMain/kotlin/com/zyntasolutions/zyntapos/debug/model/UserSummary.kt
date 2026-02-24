package com.zyntasolutions.zyntapos.debug.model

/**
 * Lightweight user record for the Auth tab user list.
 *
 * Contains no password hash or sensitive security material.
 */
data class UserSummary(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val isActive: Boolean,
)
