package com.zyntasolutions.zyntapos.license.config

import com.zyntasolutions.zyntapos.common.JwtDefaults
import java.security.PublicKey

data class LicenseConfig(
    val jwtIssuer: String,
    val jwtAudience: String,
    val jwtPublicKey: PublicKey,
    // D8: Named business-rule constants — configurable via environment variables
    val gracePeriodDays: Int,
    val maxDevicesPerLicense: Int,
    val heartbeatIntervalMinutes: Int,
) {
    companion object {
        fun fromEnvironment(): LicenseConfig {
            // S2-1: Use centralized defaults and PEM parsing from common module
            val publicKeyPem = JwtDefaults.readKeyFile("RS256_PUBLIC_KEY_PATH")
                ?: System.getenv("RS256_PUBLIC_KEY")
                ?: error("RS256_PUBLIC_KEY_PATH or RS256_PUBLIC_KEY must be set")

            return LicenseConfig(
                jwtIssuer = System.getenv("JWT_ISSUER") ?: JwtDefaults.POS_ISSUER,
                jwtAudience = System.getenv("JWT_AUDIENCE") ?: JwtDefaults.POS_AUDIENCE,
                jwtPublicKey = JwtDefaults.parseRsaPublicKey(publicKeyPem),
                gracePeriodDays        = System.getenv("LICENSE_GRACE_PERIOD_DAYS")?.toIntOrNull() ?: 7,
                maxDevicesPerLicense   = System.getenv("LICENSE_MAX_DEVICES")?.toIntOrNull() ?: 100,
                heartbeatIntervalMinutes = System.getenv("LICENSE_HEARTBEAT_INTERVAL_MIN")?.toIntOrNull() ?: 60,
            )
        }
    }
}
