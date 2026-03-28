package com.zyntasolutions.zyntapos.data.analytics

import co.touchlab.kermit.Logger
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker

/**
 * Cross-platform analytics service backed by Kermit structured logging.
 *
 * Implements [AnalyticsTracker] from `:shared:core` so feature modules can depend
 * on the interface without pulling in `:shared:data`.
 *
 * All events are written to the Kermit log pipeline, which fans out to:
 * - Console (debug builds)
 * - SQLite operational log via [KermitSqliteAdapter] (all builds)
 * - Sentry breadcrumbs via the SentryAndroid integration (release builds)
 *
 * Firebase Analytics and GA4 Measurement Protocol have been removed (ADR-012).
 * Crash reporting is handled exclusively by Sentry; structured event tracking
 * is handled via Kermit.
 */
class AnalyticsService : AnalyticsTracker {

    private val log = Logger.withTag("Analytics")

    override fun logEvent(name: String, params: Map<String, String>) {
        log.d { "event: $name params=$params" }
    }

    override fun logScreenView(screenName: String, screenClass: String) {
        log.d { "screen: $screenName class=$screenClass" }
    }

    override fun setUserId(userId: String?) {
        log.d { "userId: ${userId ?: "(cleared)"}" }
    }

    override fun setUserProperty(name: String, value: String) {
        log.d { "property: $name=$value" }
    }
}
