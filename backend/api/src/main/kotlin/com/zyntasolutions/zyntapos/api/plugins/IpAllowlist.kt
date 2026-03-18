package com.zyntasolutions.zyntapos.api.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

/**
 * Route-scoped plugin that restricts access to admin routes by client IP.
 *
 * When `ADMIN_IP_ALLOWLIST` env var is set (comma-separated CIDRs or IPs),
 * only requests from those IPs are allowed. When the env var is empty or unset,
 * all IPs are permitted (open access — suitable for dev environments).
 *
 * The client IP is resolved from `X-Forwarded-For` (set by Caddy reverse proxy)
 * with a fallback to the direct remote address.
 *
 * Example:
 * ```
 * ADMIN_IP_ALLOWLIST=10.0.0.0/8,192.168.1.100,2001:db8::/32
 * ```
 */
val IpAllowlistPlugin = createRouteScopedPlugin("IpAllowlist") {
    val logger = LoggerFactory.getLogger("IpAllowlist")
    val raw = System.getenv("ADMIN_IP_ALLOWLIST") ?: ""
    val entries = raw.split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }

    if (entries.isEmpty()) {
        logger.info("ADMIN_IP_ALLOWLIST not set — admin IP restriction disabled")
    } else {
        logger.info("Admin IP allowlist active: ${entries.size} entries")
    }

    val matchers = entries.mapNotNull { entry ->
        try {
            IpMatcher.parse(entry)
        } catch (e: Exception) {
            logger.warn("Invalid ADMIN_IP_ALLOWLIST entry '$entry': ${e.message}")
            null
        }
    }

    onCall { call ->
        if (matchers.isEmpty()) return@onCall // no restriction

        val ip = call.request.headers["X-Forwarded-For"]
            ?.split(",")?.firstOrNull()?.trim()
            ?: call.request.local.remoteAddress

        if (ip.isNullOrBlank() || matchers.none { it.matches(ip) }) {
            logger.warn("Admin access denied for IP: $ip")
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "IP_NOT_ALLOWED"))
        }
    }
}

/**
 * Matches an IP address against a single IP or CIDR range.
 */
sealed class IpMatcher {
    abstract fun matches(ip: String): Boolean

    /** Exact IP match (e.g., "192.168.1.100") */
    class Exact(private val allowed: String) : IpMatcher() {
        override fun matches(ip: String): Boolean = ip == allowed
    }

    /** CIDR range match (e.g., "10.0.0.0/8") */
    class Cidr(private val network: Long, private val mask: Long) : IpMatcher() {
        override fun matches(ip: String): Boolean {
            val addr = ipv4ToLong(ip) ?: return false
            return (addr and mask) == (network and mask)
        }
    }

    companion object {
        fun parse(entry: String): IpMatcher {
            return if ("/" in entry) {
                val (ipPart, prefixLen) = entry.split("/", limit = 2)
                val bits = prefixLen.toInt()
                require(bits in 0..32) { "CIDR prefix must be 0-32" }
                val network = ipv4ToLong(ipPart)
                    ?: throw IllegalArgumentException("Invalid IPv4 in CIDR: $ipPart")
                val mask = if (bits == 0) 0L else (-1L shl (32 - bits)) and 0xFFFFFFFFL
                Cidr(network, mask)
            } else {
                Exact(entry)
            }
        }

        private fun ipv4ToLong(ip: String): Long? {
            val parts = ip.split(".")
            if (parts.size != 4) return null
            return try {
                parts.fold(0L) { acc, part ->
                    val octet = part.toInt()
                    require(octet in 0..255)
                    (acc shl 8) or octet.toLong()
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
