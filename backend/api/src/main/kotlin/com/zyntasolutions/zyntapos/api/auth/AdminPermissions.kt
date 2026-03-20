package com.zyntasolutions.zyntapos.api.auth

import com.zyntasolutions.zyntapos.api.auth.AdminRole.*

/**
 * 39 atomic permissions for the admin panel.
 * Each permission maps to exactly one capability.
 * Backend enforces via [check]; frontend mirrors this in permissions.ts.
 */
object AdminPermissions {
    private val permissions: Map<String, Set<AdminRole>> = mapOf(
        // ── Dashboard ──────────────────────────────────────────────────────────
        "dashboard:ops"             to setOf(ADMIN, OPERATOR),
        "dashboard:financial"       to setOf(ADMIN, FINANCE),
        "dashboard:support"         to setOf(ADMIN, OPERATOR, HELPDESK),

        // ── Licenses ───────────────────────────────────────────────────────────
        "license:read"              to setOf(ADMIN, OPERATOR, FINANCE, AUDITOR, HELPDESK),
        "license:write"             to setOf(ADMIN),
        "license:revoke"            to setOf(ADMIN),
        "license:export"            to setOf(ADMIN, FINANCE),

        // ── Stores ─────────────────────────────────────────────────────────────
        "store:read"                to setOf(ADMIN, OPERATOR, HELPDESK),
        "store:sync:manage"         to setOf(ADMIN, OPERATOR),
        "store:config:read"         to setOf(ADMIN, OPERATOR),

        // ── Remote Operations ──────────────────────────────────────────────────
        "diagnostics:access"        to setOf(ADMIN, OPERATOR),
        "diagnostics:read"          to setOf(ADMIN, OPERATOR, HELPDESK),
        "config:push"               to setOf(ADMIN),

        // ── Reports ────────────────────────────────────────────────────────────
        "reports:financial"         to setOf(ADMIN, FINANCE),
        "reports:operational"       to setOf(ADMIN, OPERATOR),
        "reports:support"           to setOf(ADMIN, OPERATOR, HELPDESK),
        "reports:read"              to setOf(ADMIN, OPERATOR, FINANCE, AUDITOR, HELPDESK),
        "reports:export"            to setOf(ADMIN, FINANCE),

        // ── Alerts ─────────────────────────────────────────────────────────────
        "alerts:read"               to setOf(ADMIN, OPERATOR),
        "alerts:acknowledge"        to setOf(ADMIN, OPERATOR),
        "alerts:configure"          to setOf(ADMIN),

        // ── Audit Logs ─────────────────────────────────────────────────────────
        "audit:read"                to setOf(ADMIN, AUDITOR),
        "audit:export"              to setOf(ADMIN, AUDITOR),

        // ── Support Tickets ────────────────────────────────────────────────────
        "tickets:read"              to setOf(ADMIN, OPERATOR, HELPDESK),
        "tickets:create"            to setOf(ADMIN, OPERATOR, HELPDESK),
        "tickets:update"            to setOf(ADMIN, OPERATOR, HELPDESK),
        "tickets:assign"            to setOf(ADMIN, OPERATOR, HELPDESK),
        "tickets:resolve"           to setOf(ADMIN, OPERATOR),   // HELPDESK cannot resolve
        "tickets:close"             to setOf(ADMIN, OPERATOR, HELPDESK),
        "tickets:comment"           to setOf(ADMIN, OPERATOR, HELPDESK),

        // ── Admin User Management ──────────────────────────────────────────────
        "users:read"                to setOf(ADMIN),
        "users:write"               to setOf(ADMIN),
        "users:deactivate"          to setOf(ADMIN),
        "users:sessions:revoke"     to setOf(ADMIN),

        // ── Email (TODO-008a) ────────────────────────────────────────────────
        "email:settings"            to setOf(ADMIN),
        "email:logs"                to setOf(ADMIN, OPERATOR),

        // ── Inventory (C1.2 / C1.5) ──────────────────────────────────────────────
        "inventory:read"            to setOf(ADMIN, OPERATOR, FINANCE),
        "inventory:write"           to setOf(ADMIN, OPERATOR),

        // ── System ─────────────────────────────────────────────────────────────
        "system:settings"           to setOf(ADMIN),
        "system:health"             to setOf(ADMIN, OPERATOR),
        "system:backup"             to setOf(ADMIN),
    )

    fun check(role: AdminRole, permission: String): Boolean =
        permissions[permission]?.contains(role) ?: false

    fun requirePermission(role: AdminRole, permission: String) {
        if (!check(role, permission)) {
            throw AdminAuthorizationException(
                "Role $role does not have permission '$permission'"
            )
        }
    }

    fun allForRole(role: AdminRole): Set<String> =
        permissions.entries
            .filter { role in it.value }
            .map { it.key }
            .toSet()
}

class AdminAuthorizationException(message: String) : RuntimeException(message)
