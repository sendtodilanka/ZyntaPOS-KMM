package com.zyntasolutions.zyntapos.data.analytics

import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker

/**
 * Cross-platform analytics service for TODO-011 (Firebase Analytics + GA4 Measurement Protocol).
 *
 * Implements [AnalyticsTracker] from `:shared:core` so feature modules can depend on the
 * interface without pulling in `:shared:data`.
 *
 * Platform implementations:
 * - **Android:** Firebase Analytics SDK (direct event logging)
 * - **Desktop JVM:** GA4 Measurement Protocol (HTTP POST to Google Analytics)
 *
 * Architecture constraint (TODO-011 rule #2): GA4 Measurement Protocol calls from Desktop
 * go through this wrapper in `:shared:data` — no direct HTTP in feature modules.
 */
expect class AnalyticsService : AnalyticsTracker {

    override fun logEvent(name: String, params: Map<String, String>)

    override fun logScreenView(screenName: String, screenClass: String)

    override fun setUserId(userId: String?)

    override fun setUserProperty(name: String, value: String)
}
