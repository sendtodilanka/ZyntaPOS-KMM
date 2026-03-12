package com.zyntasolutions.zyntapos.data.analytics

import co.touchlab.kermit.Logger
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker

/**
 * Desktop JVM implementation of [AnalyticsService].
 *
 * Phase 1: Logs events locally only (no-op network calls).
 * Phase 2: Will use GA4 Measurement Protocol (HTTP POST) to send events
 * to the same GA4 property that Firebase Android uses — unified dashboard.
 *
 * GA4 Measurement Protocol reference:
 * https://developers.google.com/analytics/devguides/collection/protocol/ga4
 */
actual class AnalyticsService : AnalyticsTracker {

    private val log = Logger.withTag("AnalyticsService")

    private var currentUserId: String? = null

    actual override fun logEvent(name: String, params: Map<String, String>) {
        // Phase 2: POST to https://www.google-analytics.com/mp/collect
        // with api_secret + measurement_id + client_id + event payload
        log.d { "Analytics event (desktop): $name params=$params" }
    }

    actual override fun logScreenView(screenName: String, screenClass: String) {
        log.d { "Analytics screen (desktop): $screenName" }
    }

    actual override fun setUserId(userId: String?) {
        currentUserId = userId
        log.d { "Analytics userId (desktop): ${userId ?: "(cleared)"}" }
    }

    actual override fun setUserProperty(name: String, value: String) {
        log.d { "Analytics property (desktop): $name=$value" }
    }
}
